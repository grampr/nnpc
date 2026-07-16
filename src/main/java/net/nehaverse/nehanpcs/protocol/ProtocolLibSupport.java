package net.nehaverse.nehanpcs.protocol;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class ProtocolLibSupport {
    private final ProtocolManager protocolManager;

    public ProtocolLibSupport(Plugin plugin) {
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        Plugin protocolLib = plugin.getServer().getPluginManager().getPlugin("ProtocolLib");
        String version = protocolLib == null ? "unknown" : protocolLib.getPluginMeta().getVersion();
        plugin.getLogger().info("ProtocolLib hooked: " + version + " on Minecraft " + Bukkit.getMinecraftVersion());
        if (Bukkit.getMinecraftVersion().equals("1.21.11") && version.startsWith("5.4.0")) {
            plugin.getLogger().severe(
                    "Minecraft 1.21.11 requires the ProtocolLib development build. ProtocolLib 5.4.x cannot create PLAYER NPC profiles.");
        }
        if (Runtime.version().feature() > 25) {
            plugin.getLogger().severe(
                    "Paper 1.21.11 and ProtocolLib PLAYER NPC support require Java 25 or older. Java 26 can break ProtocolLib proxy generation.");
        }
    }

    public ProtocolManager protocolManager() {
        return protocolManager;
    }
}
