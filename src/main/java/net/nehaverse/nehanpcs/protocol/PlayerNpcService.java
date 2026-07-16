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
import com.google.common.collect.HashMultimap;
import net.nehaverse.nehanpcs.action.ActionExecutor;
import net.nehaverse.nehanpcs.conversation.ConversationManager;
import net.nehaverse.nehanpcs.npc.NpcData;
import net.nehaverse.nehanpcs.npc.NpcManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public final class PlayerNpcService implements Listener {
    private static final AtomicInteger ENTITY_IDS = new AtomicInteger(2_000_000);
    private static final long TAB_LIST_REMOVE_DELAY = 100L;

    private final Plugin plugin;
    private final ProtocolManager protocolManager;
    private final NpcManager npcManager;
    private final ActionExecutor actionExecutor;
    private final ConversationManager conversationManager;
    private final Map<UUID, Set<Integer>> visibleNpcIdsByViewer = new HashMap<>();

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
        ensureEntityId(npc);
        ensureNameTagHidden(npc);
        for (Player player : Bukkit.getOnlinePlayers()) {
            spawnFor(player, npc, false);
        }
    }

    public void synchronizeAllViewers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            synchronizeViewer(player, false);
        }
    }

    public void despawnForAll(NpcData npc) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            despawnFor(player, npc);
        }
    }

    public void respawnForAll(NpcData npc) {
        ensureEntityId(npc);
        for (Player player : Bukkit.getOnlinePlayers()) {
            spawnFor(player, npc, true);
        }
    }

    public void teleportForAll(NpcData npc) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isInNpcWorld(player, npc)) {
                despawnFor(player, npc);
                continue;
            }
            if (!isVisible(player, npc)) {
                spawnFor(player, npc, false);
                continue;
            }
            teleportFor(player, npc);
        }
    }

    public void spawnFor(Player viewer, NpcData npc) {
        spawnFor(viewer, npc, false);
    }

    private void spawnFor(Player viewer, NpcData npc, boolean force) {
        if (!viewer.isOnline() || !isInNpcWorld(viewer, npc)) {
            despawnFor(viewer, npc);
            return;
        }
        ensureEntityId(npc);
        ensureNameTagHidden(npc);
        if (!force && isVisible(viewer, npc)) {
            return;
        }
        if (force) {
            sendDestroy(viewer, npc);
        }

        try {
            NpcProfile npcProfile = profileFor(viewer, npc);
            sendPlayerInfoAdd(viewer, npc, npcProfile.profile(), npcProfile.uuid());
            sendPlayerSpawn(viewer, npc, npcProfile.uuid());
            markVisible(viewer, npc);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (viewer.isOnline() && isVisible(viewer, npc)) {
                    sendPlayerInfoRemove(viewer, npcProfile.uuid());
                }
            }, TAB_LIST_REMOVE_DELAY);
        } catch (RuntimeException ex) {
            markHidden(viewer, npc);
            plugin.getLogger().log(Level.WARNING,
                    "Failed to spawn PLAYER NPC " + npc.id() + " for " + viewer.getName()
                            + " using " + PacketType.Play.Server.SPAWN_ENTITY.name(), ex);
        }
    }

    public void despawnFor(Player viewer, NpcData npc) {
        if (!isVisible(viewer, npc)) {
            return;
        }
        sendDestroy(viewer, npc);
        markHidden(viewer, npc);
    }

    private void sendDestroy(Player viewer, NpcData npc) {
        if (!viewer.isOnline() || npc.fakeEntityId() == 0) {
            return;
        }
        try {
            PacketContainer destroy = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            destroy.getIntLists().write(0, List.of(npc.fakeEntityId()));
            protocolManager.sendServerPacket(viewer, destroy);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to despawn PLAYER NPC " + npc.id() + " for " + viewer.getName(), ex);
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
            spawnFor(viewer, npc, true);
        }
    }

    private void sendPlayerInfoAdd(Player viewer, NpcData npc, WrappedGameProfile profile, UUID profileUuid) {
        PacketContainer info = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO);
        PlayerInfoData data = new PlayerInfoData(
                profileUuid,
                0,
                true,
                EnumWrappers.NativeGameMode.SURVIVAL,
                profile,
                WrappedChatComponent.fromText(npc.name())
        );
        info.getPlayerInfoActions().write(0, EnumSet.of(
                EnumWrappers.PlayerInfoAction.ADD_PLAYER,
                EnumWrappers.PlayerInfoAction.UPDATE_GAME_MODE,
                EnumWrappers.PlayerInfoAction.UPDATE_LISTED,
                EnumWrappers.PlayerInfoAction.UPDATE_LATENCY,
                EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME
        ));
        info.getLists(PlayerInfoData.getConverter()).write(0, List.of(data));
        protocolManager.sendServerPacket(viewer, info);
    }

    private void sendPlayerInfoRemove(Player viewer, UUID profileUuid) {
        try {
            PacketContainer remove = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
            remove.getUUIDLists().write(0, List.of(profileUuid));
            protocolManager.sendServerPacket(viewer, remove);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to remove a PLAYER NPC from the tab list", ex);
        }
    }

    private void sendPlayerSpawn(Player viewer, NpcData npc, UUID profileUuid) {
        Location location = npc.toLocation(Bukkit.getWorld(npc.worldName()));
        PacketContainer spawn = protocolManager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
        spawn.getIntegers().write(0, npc.fakeEntityId());
        spawn.getUUIDs().write(0, profileUuid);
        spawn.getEntityTypeModifier().write(0, EntityType.PLAYER);
        spawn.getDoubles().write(0, location.getX());
        spawn.getDoubles().write(1, location.getY());
        spawn.getDoubles().write(2, location.getZ());
        spawn.getBytes().write(0, angle(location.getPitch()));
        spawn.getBytes().write(1, angle(location.getYaw()));
        if (spawn.getBytes().size() > 2) {
            spawn.getBytes().write(2, angle(location.getYaw()));
        }
        protocolManager.sendServerPacket(viewer, spawn);
    }

    private NpcProfile profileFor(Player viewer, NpcData npc) {
        if (npc.mirror()) {
            return new NpcProfile(WrappedGameProfile.fromPlayer(viewer), viewer.getUniqueId());
        }
        UUID profileUuid = npc.profileUuid();
        String profileName = internalProfileName(npc);
        Multimap<String, WrappedSignedProperty> properties = HashMultimap.create();
        if (!npc.skinValue().isBlank()) {
            properties.put("textures", new WrappedSignedProperty(
                    "textures",
                    npc.skinValue(),
                    npc.skinSignature().isBlank() ? null : npc.skinSignature()
            ));
        }

        if (!properties.isEmpty()) {
            try {
                var constructor = WrappedGameProfile.class.getConstructor(
                        UUID.class, String.class, Multimap.class);
                return new NpcProfile(constructor.newInstance(profileUuid, profileName, properties), profileUuid);
            } catch (NoSuchMethodException ignored) {
                WrappedGameProfile legacyProfile = new WrappedGameProfile(profileUuid, profileName);
                try {
                    legacyProfile.getProperties().putAll(properties);
                    return new NpcProfile(legacyProfile, profileUuid);
                } catch (RuntimeException ex) {
                    throw new IllegalStateException(
                            "This Minecraft version requires the ProtocolLib development build with 1.21.11 support", ex);
                }
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("Could not create the PLAYER NPC game profile", ex);
            }
        }
        return new NpcProfile(new WrappedGameProfile(profileUuid, profileName), profileUuid);
    }

    private String internalProfileName(NpcData npc) {
        String name = "NNPC_" + npc.id();
        return name.substring(0, Math.min(16, name.length()));
    }

    public void removeNameTagTeam(NpcData npc) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(nameTagTeamName(npc));
        if (team != null) {
            team.unregister();
        }
    }

    private void ensureNameTagHidden(NpcData npc) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = nameTagTeamName(npc);
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        team.addEntry(internalProfileName(npc));
    }

    private String nameTagTeamName(NpcData npc) {
        return "nhnpc_" + Integer.toUnsignedString(npc.id(), 36);
    }

    private void synchronizeViewer(Player viewer, boolean force) {
        Set<Integer> validNpcIds = new HashSet<>();
        for (NpcData npc : npcManager.all()) {
            if (!isPlayerNpc(npc) || !isInNpcWorld(viewer, npc)) {
                continue;
            }
            validNpcIds.add(npc.id());
            spawnFor(viewer, npc, force);
        }

        Set<Integer> visibleIds = new HashSet<>(visibleNpcIdsByViewer.getOrDefault(viewer.getUniqueId(), Set.of()));
        for (Integer npcId : visibleIds) {
            if (!validNpcIds.contains(npcId)) {
                npcManager.get(npcId).ifPresent(npc -> despawnFor(viewer, npc));
            }
        }
    }

    private void scheduleSynchronization(Player player, long delay, boolean force) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                synchronizeViewer(player, force);
            }
        }, delay);
    }

    private void ensureEntityId(NpcData npc) {
        if (npc.fakeEntityId() == 0) {
            npc.fakeEntityId(ENTITY_IDS.incrementAndGet());
        }
    }

    private boolean isInNpcWorld(Player viewer, NpcData npc) {
        return viewer.getWorld().getName().equals(npc.worldName());
    }

    private boolean isVisible(Player viewer, NpcData npc) {
        return visibleNpcIdsByViewer.getOrDefault(viewer.getUniqueId(), Set.of()).contains(npc.id());
    }

    private void markVisible(Player viewer, NpcData npc) {
        visibleNpcIdsByViewer.computeIfAbsent(viewer.getUniqueId(), ignored -> new HashSet<>()).add(npc.id());
    }

    private void markHidden(Player viewer, NpcData npc) {
        Set<Integer> ids = visibleNpcIdsByViewer.get(viewer.getUniqueId());
        if (ids != null) {
            ids.remove(npc.id());
            if (ids.isEmpty()) {
                visibleNpcIdsByViewer.remove(viewer.getUniqueId());
            }
        }
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
        scheduleSynchronization(event.getPlayer(), 5L, false);
        scheduleSynchronization(event.getPlayer(), 40L, true);
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        visibleNpcIdsByViewer.remove(event.getPlayer().getUniqueId());
        scheduleSynchronization(event.getPlayer(), 5L, true);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        visibleNpcIdsByViewer.remove(event.getPlayer().getUniqueId());
        scheduleSynchronization(event.getPlayer(), 5L, true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        scheduleSynchronization(event.getPlayer(), 5L, true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        visibleNpcIdsByViewer.remove(event.getPlayer().getUniqueId());
    }

    private byte angle(float angle) {
        return (byte) Math.floor(angle * 256.0F / 360.0F);
    }

    private record NpcProfile(WrappedGameProfile profile, UUID uuid) {
    }
}
