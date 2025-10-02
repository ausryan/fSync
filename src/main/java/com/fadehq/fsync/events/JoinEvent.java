package com.fadehq.fsync.events;

import com.fadehq.fsync.config.Config;
import com.fadehq.fsync.fSync;
import com.fadehq.fsync.playerprofile.PlayerProfileManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinEvent implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        fSync.getRedisManager().waitForPlayerSync("playerUuid", Config.region, playerData -> {
            // Player is ready to read data!
            // Safe to load their profile etc
            fSync.getRedisManager().setWithExpirySync("fsync:currentserver:" + player.getUniqueId(), Config.server, 1800);
            PlayerProfileManager.get().loadProfileAsync(player.getUniqueId(), player.getName());
        });
    }
}
