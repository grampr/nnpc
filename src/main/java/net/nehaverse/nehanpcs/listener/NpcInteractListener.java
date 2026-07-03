package net.nehaverse.nehanpcs.listener;

import net.nehaverse.nehanpcs.action.ActionExecutor;
import net.nehaverse.nehanpcs.conversation.ConversationManager;
import net.nehaverse.nehanpcs.npc.NpcData;
import net.nehaverse.nehanpcs.npc.NpcManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class NpcInteractListener implements Listener {
    private final NpcManager npcManager;
    private final ActionExecutor actionExecutor;
    private final ConversationManager conversationManager;
    private final Map<String, Long> recentClicks = new HashMap<>();

    public NpcInteractListener(NpcManager npcManager, ActionExecutor actionExecutor, ConversationManager conversationManager) {
        this.npcManager = npcManager;
        this.actionExecutor = actionExecutor;
        this.conversationManager = conversationManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (handle(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        if (handle(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    private boolean handle(Player player, Entity entity) {
        Optional<NpcData> npc = npcManager.getByEntityUuid(entity.getUniqueId());
        if (npc.isEmpty()) {
            return false;
        }

        String key = player.getUniqueId() + ":" + entity.getUniqueId();
        long now = System.currentTimeMillis();
        if (now - recentClicks.getOrDefault(key, 0L) < 150L) {
            return true;
        }
        recentClicks.put(key, now);
        recentClicks.entrySet().removeIf(entry -> now - entry.getValue() > 5_000L);

        NpcData npcData = npc.get();
        actionExecutor.execute(player, npcData);
        if ("CLICK".equalsIgnoreCase(npcData.conversationTrigger())) {
            conversationManager.play(player, npcData);
        }
        return true;
    }
}
