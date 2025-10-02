package com.fadehq.fsync.events;

import com.fadehq.fsync.config.Config;
import com.fadehq.fsync.playerprofile.PlayerProfile;
import com.fadehq.fsync.playerprofile.PlayerProfileManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class QuitEvent implements Listener {

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        PlayerProfile profile = PlayerProfileManager.get().getProfile(uuid);
        if (profile != null) {
            profile.save(Config.server).thenRun(() ->
                    PlayerProfileManager.get().removeProfile(uuid));
        }
    }
}
