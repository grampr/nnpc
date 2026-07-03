package net.nehaverse.nehanpcs.action;

import net.nehaverse.nehanpcs.npc.NpcData;
import net.nehaverse.nehanpcs.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;

public final class ActionExecutor {
    private final Plugin plugin;
    private final MessageUtil messages;
    private final CooldownManager cooldownManager;

    public ActionExecutor(Plugin plugin, MessageUtil messages, CooldownManager cooldownManager) {
        this.plugin = plugin;
        this.messages = messages;
        this.cooldownManager = cooldownManager;
    }

    public void execute(Player player, NpcData npc) {
        for (int index = 0; index < npc.actions().size(); index++) {
            NpcAction action = npc.actions().get(index);
            long remaining = cooldownManager.remainingSeconds(player.getUniqueId(), npc.id(), index);
            if (remaining > 0) {
                messages.send(player, "action-cooldown", Map.of("%seconds%", String.valueOf(remaining)));
                continue;
            }

            executeSingle(player, npc, action);
            cooldownManager.mark(player.getUniqueId(), npc.id(), index, action.cooldownSeconds());
        }
    }

    private void executeSingle(Player player, NpcData npc, NpcAction action) {
        String content = PlaceholderUtil.apply(action.content(), player, npc);
        switch (action.type()) {
            case MESSAGE -> player.sendMessage(messages.component(content));
            case CHAT -> player.chat(content);
            case CMD -> player.performCommand(stripSlash(content));
            case CONSOLE -> plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), stripSlash(content));
            case SERVER -> sendToServer(player, content);
        }
    }

    private String stripSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    private void sendToServer(Player player, String server) {
        if (!plugin.getConfig().getBoolean("server-transfer.enabled", true)) {
            messages.send(player, "server-transfer-disabled");
            return;
        }
        String channel = plugin.getConfig().getString("server-transfer.channel", "BungeeCord");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeUTF("Connect");
            output.writeUTF(server);
            player.sendPluginMessage(plugin, channel, bytes.toByteArray());
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to send player to server '" + server + "': " + ex.getMessage());
            messages.send(player, "server-transfer-failed");
        }
    }
}
