package com.fadehq.fsync.events;

import com.fadehq.fsync.playerprofile.PlayerProfileManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.UUID;

public class InteractEvent implements Listener {

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!PlayerProfileManager.get().isCached(uuid)) {
            event.setCancelled(true);
        }
    }
}
