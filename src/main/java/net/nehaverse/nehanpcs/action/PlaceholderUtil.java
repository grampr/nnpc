package net.nehaverse.nehanpcs.action;

import net.nehaverse.nehanpcs.npc.NpcData;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class PlaceholderUtil {
    private PlaceholderUtil() {
    }

    public static String apply(String input, Player player, NpcData npc) {
        return input
                .replace("%player%", player.getName())
                .replace("%uuid%", player.getUniqueId().toString())
                .replace("%world%", player.getWorld().getName())
                .replace("%x%", String.format(Locale.ROOT, "%.2f", player.getLocation().getX()))
                .replace("%y%", String.format(Locale.ROOT, "%.2f", player.getLocation().getY()))
                .replace("%z%", String.format(Locale.ROOT, "%.2f", player.getLocation().getZ()))
                .replace("%npc_id%", String.valueOf(npc.id()))
                .replace("%npc_name%", npc.name());
    }
}
