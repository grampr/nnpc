package net.nehaverse.nehanpcs.protocol;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import org.bukkit.plugin.Plugin;

public final class ProtocolLibSupport {
    private final ProtocolManager protocolManager;

    public ProtocolLibSupport(Plugin plugin) {
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        plugin.getLogger().info("ProtocolLib hooked: " + protocolManager.getClass().getSimpleName());
    }

    public ProtocolManager protocolManager() {
        return protocolManager;
    }
}
