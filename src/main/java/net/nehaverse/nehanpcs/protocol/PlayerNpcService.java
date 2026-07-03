package net.nehaverse.nehanpcs.protocol;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import com.google.common.collect.Multimap;
import net.nehaverse.nehanpcs.action.ActionExecutor;
import net.nehaverse.nehanpcs.conversation.ConversationManager;
import net.nehaverse.nehanpcs.npc.NpcData;
import net.nehaverse.nehanpcs.npc.NpcManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class PlayerNpcService implements Listener {
    private static final AtomicInteger ENTITY_IDS = new AtomicInteger(2_000_000);

    private final Plugin plugin;
    private final ProtocolManager protocolManager;
    private final NpcManager npcManager;
    private final ActionExecutor actionExecutor;
    private final ConversationManager conversationManager;

    public PlayerNpcService(Plugin plugin, ProtocolLibSupport protocolLibSupport, NpcManager npcManager,
                            ActionExecutor actionExecutor, ConversationManager conversationManager) {
        this.plugin = plugin;
        this.protocolManager = protocolLibSupport.protocolManager();
        this.npcManager = npcManager;
        this.actionExecutor = actionExecutor;
        this.conversationManager = conversationManager;
        registerClickListener();
    }

    public boolean isPlayerNpc(NpcData npc) {
        return npc.type() == EntityType.PLAYER;
    }

    public void spawnForAll(NpcData npc) {
        if (npc.fakeEntityId() == 0) {
            npc.fakeEntityId(ENTITY_IDS.incrementAndGet());
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            spawnFor(player, npc);
        }
    }

    public void despawnForAll(NpcData npc) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            despawnFor(player, npc);
        }
    }

    public void respawnForAll(NpcData npc) {
        despawnForAll(npc);
        spawnForAll(npc);
    }

    public void teleportForAll(NpcData npc) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            teleportFor(player, npc);
        }
    }

    public void spawnFor(Player viewer, NpcData npc) {
        try {
            WrappedGameProfile profile = profileFor(viewer, npc);
            sendPlayerInfoAdd(viewer, npc, profile);
            sendNamedSpawn(viewer, npc, profile.getUUID());
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> sendPlayerInfoRemove(viewer, profile.getUUID()), 40L);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Failed to spawn player NPC " + npc.id() + " for " + viewer.getName() + ": " + ex.getMessage());
        }
    }

    public void despawnFor(Player viewer, NpcData npc) {
        try {
            PacketContainer destroy = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            destroy.getIntLists().write(0, List.of(npc.fakeEntityId()));
            protocolManager.sendServerPacket(viewer, destroy);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Failed to despawn player NPC " + npc.id() + " for " + viewer.getName() + ": " + ex.getMessage());
        }
    }

    public void teleportFor(Player viewer, NpcData npc) {
        try {
            Location location = npc.toLocation(Bukkit.getWorld(npc.worldName()));
            PacketContainer teleport = protocolManager.createPacket(PacketType.Play.Server.ENTITY_TELEPORT);
            teleport.getIntegers().write(0, npc.fakeEntityId());
            teleport.getDoubles().write(0, location.getX());
            teleport.getDoubles().write(1, location.getY());
            teleport.getDoubles().write(2, location.getZ());
            teleport.getBytes().write(0, angle(location.getYaw()));
            teleport.getBytes().write(1, angle(location.getPitch()));
            teleport.getBooleans().write(0, true);
            protocolManager.sendServerPacket(viewer, teleport);
        } catch (RuntimeException ex) {
            respawnForAll(npc);
        }
    }

    private void sendPlayerInfoAdd(Player viewer, NpcData npc, WrappedGameProfile profile) {
        PacketContainer info = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO);
        PlayerInfoData data = new PlayerInfoData(
                profile,
                0,
                EnumWrappers.NativeGameMode.SURVIVAL,
                WrappedChatComponent.fromText(npc.name())
        );
        info.getPlayerInfoActions().write(0, EnumSet.of(EnumWrappers.PlayerInfoAction.ADD_PLAYER));
        info.getPlayerInfoDataLists().write(1, List.of(data));
        protocolManager.sendServerPacket(viewer, info);
    }

    private void sendPlayerInfoRemove(Player viewer, UUID profileUuid) {
        try {
            PacketContainer remove = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
            remove.getUUIDLists().write(0, List.of(profileUuid));
            protocolManager.sendServerPacket(viewer, remove);
        } catch (RuntimeException ex) {
            plugin.getLogger().fine("Failed to remove player NPC from tab list: " + ex.getMessage());
        }
    }

    private void sendNamedSpawn(Player viewer, NpcData npc, UUID profileUuid) {
        Location location = npc.toLocation(Bukkit.getWorld(npc.worldName()));
        PacketContainer spawn = protocolManager.createPacket(PacketType.Play.Server.NAMED_ENTITY_SPAWN);
        spawn.getIntegers().write(0, npc.fakeEntityId());
        spawn.getUUIDs().write(0, profileUuid);
        spawn.getDoubles().write(0, location.getX());
        spawn.getDoubles().write(1, location.getY());
        spawn.getDoubles().write(2, location.getZ());
        spawn.getBytes().write(0, angle(location.getYaw()));
        spawn.getBytes().write(1, angle(location.getPitch()));
        protocolManager.sendServerPacket(viewer, spawn);
    }

    private WrappedGameProfile profileFor(Player viewer, NpcData npc) {
        if (npc.mirror()) {
            return WrappedGameProfile.fromPlayer(viewer).withName(npc.name());
        }
        WrappedGameProfile profile = new WrappedGameProfile(npc.profileUuid(), npc.name());
        if (!npc.skinValue().isBlank() && !npc.skinSignature().isBlank()) {
            Multimap<String, WrappedSignedProperty> properties = profile.getProperties();
            properties.put("textures", new WrappedSignedProperty("textures", npc.skinValue(), npc.skinSignature()));
        }
        return profile;
    }

    private void registerClickListener() {
        protocolManager.addPacketListener(new PacketAdapter(plugin, PacketType.Play.Client.USE_ENTITY) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                int entityId = event.getPacket().getIntegers().read(0);
                Optional<NpcData> npc = npcManager.getByFakeEntityId(entityId);
                if (npc.isEmpty()) {
                    return;
                }
                event.setCancelled(true);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    NpcData npcData = npc.get();
                    Player player = event.getPlayer();
                    actionExecutor.execute(player, npcData);
                    if ("CLICK".equalsIgnoreCase(npcData.conversationTrigger())) {
                        conversationManager.play(player, npcData);
                    }
                });
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (NpcData npc : npcManager.all()) {
                if (isPlayerNpc(npc)) {
                    spawnFor(event.getPlayer(), npc);
                }
            }
        }, 20L);
    }

    private byte angle(float angle) {
        return (byte) Math.floor(angle * 256.0F / 360.0F);
    }
}
