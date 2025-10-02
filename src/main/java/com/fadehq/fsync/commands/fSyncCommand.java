package com.fadehq.fsync.commands;

import com.fadehq.fsync.fSync;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class fSyncCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = fSync.getPlugin().getPluginMeta().getName();
        String authors = String.join(", ", fSync.getPlugin().getPluginMeta().getAuthors());
        String description = fSync.getPlugin().getPluginMeta().getDescription();
        String website = fSync.getPlugin().getPluginMeta().getWebsite();

        Component msg = MiniMessage.miniMessage().deserialize(
                "<gray>[<aqua>" + name + "</aqua>]\n" +
                        "<gray>Author: <white>" + authors + "\n" +
                        "<gray>Description: <white>" + description + "\n" +
                        "<gray>Website: <white><click:open_url:'" + website + "'>" + website + "</click>"
        );
        sender.sendMessage(msg);
        return true;
    }
}