package com.fadehq.fsync.events;

import com.fadehq.fsync.playerprofile.PlayerProfileManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDropItemEvent;

public class DropEvent implements Listener {

    @EventHandler
    public void onDrop(EntityDropItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (!PlayerProfileManager.get().isCached(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }
}
