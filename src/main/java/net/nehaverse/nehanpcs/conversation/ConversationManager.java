package net.nehaverse.nehanpcs.conversation;

import net.nehaverse.nehanpcs.npc.NpcData;
import net.nehaverse.nehanpcs.storage.YamlStorageManager;
import net.nehaverse.nehanpcs.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ConversationManager {
    private final Plugin plugin;
    private final YamlStorageManager storageManager;
    private final MessageUtil messages;
    private final Map<String, Conversation> conversations = new LinkedHashMap<>();
    private final Map<String, Long> cooldownUntilMillis = new HashMap<>();

    public ConversationManager(Plugin plugin, YamlStorageManager storageManager, MessageUtil messages) {
        this.plugin = plugin;
        this.storageManager = storageManager;
        this.messages = messages;
    }

    public void load() {
        conversations.clear();
        conversations.putAll(storageManager.loadConversations());
    }

    public void save() {
        storageManager.saveConversations(conversations.values());
    }

    public Collection<Conversation> all() {
        return Collections.unmodifiableCollection(conversations.values());
    }

    public Optional<Conversation> get(String name) {
        return Optional.ofNullable(conversations.get(name.toLowerCase(java.util.Locale.ROOT)));
    }

    public boolean create(String name) {
        String key = name.toLowerCase(java.util.Locale.ROOT);
        if (conversations.containsKey(key)) {
            return false;
        }
        conversations.put(key, new Conversation(name));
        save();
        return true;
    }

    public boolean remove(String name) {
        boolean removed = conversations.remove(name.toLowerCase(java.util.Locale.ROOT)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public void play(Player player, NpcData npc) {
        if (npc.conversationName().isBlank()) {
            return;
        }
        Conversation conversation = get(npc.conversationName()).orElse(null);
        if (conversation == null || conversation.lines().isEmpty()) {
            return;
        }
        long remaining = remainingSeconds(player.getUniqueId(), npc.id(), conversation.name());
        if (remaining > 0) {
            messages.send(player, "conversation-cooldown", Map.of("%seconds%", String.valueOf(remaining)));
            return;
        }
        for (ConversationLine line : conversation.lines()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                String text = line.text()
                        .replace("%player%", player.getName())
                        .replace("%npc_id%", String.valueOf(npc.id()))
                        .replace("%npc_name%", npc.name());
                player.sendMessage(messages.component(text));
            }, Math.max(0, line.delayTicks()));
        }
        mark(player.getUniqueId(), npc.id(), conversation.name(), conversation.cooldownSeconds());
    }

    private long remainingSeconds(UUID playerUuid, int npcId, String conversationName) {
        long until = cooldownUntilMillis.getOrDefault(key(playerUuid, npcId, conversationName), 0L);
        long remainingMillis = until - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            return 0;
        }
        return Math.max(1L, (remainingMillis + 999L) / 1000L);
    }

    private void mark(UUID playerUuid, int npcId, String conversationName, int seconds) {
        if (seconds > 0) {
            cooldownUntilMillis.put(key(playerUuid, npcId, conversationName), System.currentTimeMillis() + seconds * 1000L);
        }
    }

    private String key(UUID playerUuid, int npcId, String conversationName) {
        return playerUuid + ":" + npcId + ":" + conversationName.toLowerCase(java.util.Locale.ROOT);
    }
}
