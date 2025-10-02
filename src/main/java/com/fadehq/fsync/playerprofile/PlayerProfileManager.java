package com.fadehq.fsync.playerprofile;

import com.fadehq.fsync.api.ExternalDataProvider;
import com.fadehq.fsync.config.Config;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerProfileManager {

    private static final PlayerProfileManager INSTANCE = new PlayerProfileManager();
    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();

    public static PlayerProfileManager get() {
        return INSTANCE;
    }

    public PlayerProfile getProfile(UUID uuid) {
        return cache.get(uuid);
    }

    public void cacheProfile(PlayerProfile profile) {
        cache.put(profile.getUuid(), profile);
    }

    public void removeProfile(UUID uuid) {
        cache.remove(uuid);

        for (ExternalDataProvider provider: PlayerProfile.getExternalProviders()) {
            provider.remove(uuid);
        }
    }

    public CompletableFuture<PlayerProfile> loadProfileAsync(UUID uuid, String name) {
        return PlayerProfile.load(uuid, name).thenApply(profile -> {
            cache.put(uuid, profile);

            for (ExternalDataProvider provider : PlayerProfile.getExternalProviders()) {
                provider.onJoin(uuid);
            }

            return profile;
        });
    }

    public void saveAll() {
        cache.values().forEach(profile -> profile.save(Config.server));
    }

    public boolean isCached(UUID uuid) {
        return cache.containsKey(uuid);
    }

    public Map<UUID, PlayerProfile> getCache() {
        return cache;
    }
}
