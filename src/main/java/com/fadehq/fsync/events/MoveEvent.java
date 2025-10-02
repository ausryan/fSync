package com.fadehq.fsync.events;

import com.fadehq.fsync.config.messages.Messages;
import com.fadehq.fsync.playerprofile.PlayerProfileManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class MoveEvent implements Listener {

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (!PlayerProfileManager.get().isCached(player.getUniqueId())) {
            player.sendActionBar(Messages.profileLoading());
            event.setCancelled(true);
        }
    }
}
