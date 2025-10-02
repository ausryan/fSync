package com.fadehq.fsync.events;

import com.fadehq.fsync.playerprofile.PlayerProfileManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;

public class PickupEvent implements Listener {

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (!PlayerProfileManager.get().isCached(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }
}
