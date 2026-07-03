package net.nehaverse.nehanpcs.util;

import org.bukkit.command.CommandSender;

public final class PermissionUtil {
    private PermissionUtil() {
    }

    public static boolean has(CommandSender sender, String node) {
        return sender.hasPermission("nehanpcs.admin") || sender.hasPermission(node);
    }
}
