package com.fadehq.fsync.config.messages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Messages {

    public static Component profileLoading() {
        return Component.text()
                .append(Component.text("Loading your profile... Relog if you cannot move in 5 seconds.", NamedTextColor.RED))
                .build();
    }
}