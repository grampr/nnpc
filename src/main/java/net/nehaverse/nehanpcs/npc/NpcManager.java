package net.nehaverse.nehanpcs.npc;

import net.nehaverse.nehanpcs.conversation.Conversation;
import net.nehaverse.nehanpcs.conversation.ConversationManager;
import net.nehaverse.nehanpcs.path.NpcPath;
import net.nehaverse.nehanpcs.path.PathManager;
import net.nehaverse.nehanpcs.path.PathPoint;
import net.nehaverse.nehanpcs.protocol.PlayerNpcService;
import net.nehaverse.nehanpcs.storage.YamlStorageManager;
import net.nehaverse.nehanpcs.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fox;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Slime;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.Display;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class NpcManager {
    private final Plugin plugin;
    private final YamlStorageManager storageManager;
    private final MessageUtil messages;
    private final Map<Integer, NpcData> npcsById = new LinkedHashMap<>();
    private final Map<Integer, Integer> pathTargetIndexByNpcId = new LinkedHashMap<>();
    private PlayerNpcService playerNpcService;

    public NpcManager(Plugin plugin, YamlStorageManager storageManager, MessageUtil messages) {
        this.plugin = plugin;
        this.storageManager = storageManager;
        this.messages = messages;
    }

    public void playerNpcService(PlayerNpcService playerNpcService) {
        this.playerNpcService = playerNpcService;
    }

    public Collection<NpcData> all() {
        return Collections.unmodifiableCollection(npcsById.values().stream()
                .sorted(Comparator.comparingInt(NpcData::id))
                .toList());
    }

    public Optional<NpcData> get(int id) {
        return Optional.ofNullable(npcsById.get(id));
    }

    public Optional<NpcData> getByEntityUuid(java.util.UUID entityUuid) {
        return npcsById.values().stream()
                .filter(npc -> entityUuid.equals(npc.entityUuid()))
                .findFirst();
    }

    public Optional<NpcData> getByFakeEntityId(int fakeEntityId) {
        return npcsById.values().stream()
                .filter(npc -> npc.fakeEntityId() == fakeEntityId)
                .findFirst();
    }

    public boolean exists(int id) {
        return npcsById.containsKey(id);
    }

    public CreateResult create(int id, EntityType type, String name, Location location) {
        if (exists(id)) {
            return CreateResult.DUPLICATE_ID;
        }
        if (type == EntityType.PLAYER && playerNpcService == null) {
            return CreateResult.PLAYER_UNSUPPORTED;
        }
        if (type != EntityType.PLAYER && (!type.isSpawnable() || !type.isAlive())) {
            return CreateResult.UNSPAWNABLE_TYPE;
        }

        NpcData npc = new NpcData(id, type, name, location);
        npcsById.put(id, npc);
        spawn(npc);
        save();
        return CreateResult.CREATED;
    }

    public boolean delete(int id) {
        NpcData npc = npcsById.remove(id);
        if (npc == null) {
            return false;
        }
        despawn(npc);
        if (npc.type() == EntityType.PLAYER && playerNpcService != null) {
            playerNpcService.removeNameTagTeam(npc);
        }
        save();
        return true;
    }

    public boolean move(int id, Location location) {
        NpcData npc = npcsById.get(id);
        if (npc == null) {
            return false;
        }
        npc.updateLocation(location);
        despawn(npc);
        spawn(npc);
        save();
        return true;
    }

    public boolean changeType(int id, EntityType type) {
        NpcData npc = npcsById.get(id);
        if (npc == null || (type == EntityType.PLAYER && playerNpcService == null) || (type != EntityType.PLAYER && (!type.isSpawnable() || !type.isAlive()))) {
            return false;
        }
        boolean wasPlayer = npc.type() == EntityType.PLAYER;
        despawn(npc);
        if (wasPlayer && type != EntityType.PLAYER && playerNpcService != null) {
            playerNpcService.removeNameTagTeam(npc);
        }
        npc.type(type);
        if (type == EntityType.PLAYER
                && Math.abs(npc.hologramHeight() - NpcData.LEGACY_HOLOGRAM_HEIGHT) < 0.0001D) {
            npc.hologramHeight(NpcData.PLAYER_HOLOGRAM_HEIGHT);
        }
        spawn(npc);
        save();
        return true;
    }

    public boolean setPath(int id, String pathName) {
        NpcData npc = npcsById.get(id);
        if (npc == null) {
            return false;
        }
        npc.pathName(pathName);
        pathTargetIndexByNpcId.remove(id);
        save();
        return true;
    }

    public void refresh(NpcData npc) {
        if (npc.type() == EntityType.PLAYER) {
            if (playerNpcService != null) {
                playerNpcService.respawnForAll(npc);
            }
            spawnHologram(npc);
            return;
        }
        Entity entity = entityOf(npc).orElse(null);
        if (entity != null) {
            applyAppearance(npc, entity);
        }
        spawnHologram(npc);
    }

    public void loadAndSpawnAll() {
        npcsById.clear();
        for (NpcData npc : storageManager.loadNpcs()) {
            npcsById.put(npc.id(), npc);
            spawn(npc);
        }
    }

    public void ensureSpawnedAll() {
        for (NpcData npc : npcsById.values()) {
            if (npc.type() == EntityType.PLAYER) {
                continue;
            }
            if (entityOf(npc).isEmpty()) {
                spawn(npc);
            } else if (npc.hologramUuid() == null && npc.showHologram()) {
                spawnHologram(npc);
            }
        }
        if (playerNpcService != null) {
            playerNpcService.synchronizeAllViewers();
        }
    }

    public void save() {
        storageManager.saveNpcs(npcsById.values());
    }

    public void despawnAll() {
        for (NpcData npc : npcsById.values()) {
            despawn(npc);
        }
        pathTargetIndexByNpcId.clear();
    }

    public void spawn(NpcData npc) {
        World world = Bukkit.getWorld(npc.worldName());
        if (world == null) {
            plugin.getLogger().warning(messages.raw("npc-world-missing").replace("%world%", npc.worldName()));
            return;
        }
        Location spawnLocation = npc.toLocation(world);
        spawnLocation.getChunk().load();
        if (npc.type() == EntityType.PLAYER) {
            if (playerNpcService != null) {
                playerNpcService.spawnForAll(npc);
                spawnHologram(npc);
            }
            return;
        }
        if (entityOf(npc).isPresent()) {
            refresh(npc);
            return;
        }

        Entity entity = world.spawnEntity(spawnLocation, npc.type());
        npc.entityUuid(entity.getUniqueId());
        applyAppearance(npc, entity);
        spawnHologram(npc);
    }

    private void applyAppearance(NpcData npc, Entity entity) {
        entity.setPersistent(false);
        entity.setCustomNameVisible(false);
        entity.customName(messages.component(displayName(npc)));
        entity.setGravity(false);
        entity.setSilent(true);
        entity.setInvulnerable(true);
        entity.setGlowing(npc.glowing());

        if (entity instanceof LivingEntity livingEntity) {
            livingEntity.setCollidable(npc.collidable());
            livingEntity.setAI(false);
            livingEntity.setRemoveWhenFarAway(false);
            applyEquipment(npc, livingEntity);
            applyCustomizations(npc, livingEntity);
        }
    }

    private String displayName(NpcData npc) {
        if (npc.lines().isEmpty()) {
            return npc.name();
        }
        return String.join("\n", npc.lines());
    }

    private void spawnHologram(NpcData npc) {
        removeHologram(npc);
        if (!npc.showHologram()) {
            return;
        }
        World world = Bukkit.getWorld(npc.worldName());
        if (world == null) {
            return;
        }
        Location location = npc.toLocation(world).add(0.0D, npc.hologramHeight(), 0.0D);
        location.getChunk().load();
        TextDisplay display = world.spawn(location, TextDisplay.class);
        display.text(messages.component(displayName(npc)));
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(false);
        display.setShadowed(true);
        display.setPersistent(false);
        npc.hologramUuid(display.getUniqueId());
    }

    private void removeHologram(NpcData npc) {
        if (npc.hologramUuid() == null) {
            return;
        }
        Entity hologram = Bukkit.getEntity(npc.hologramUuid());
        if (hologram != null) {
            hologram.remove();
        }
        npc.hologramUuid(null);
    }

    private void moveHologramToNpc(NpcData npc) {
        if (npc.hologramUuid() == null) {
            if (npc.showHologram()) {
                spawnHologram(npc);
            }
            return;
        }
        Entity hologram = Bukkit.getEntity(npc.hologramUuid());
        World world = Bukkit.getWorld(npc.worldName());
        if (hologram == null || world == null) {
            spawnHologram(npc);
            return;
        }
        hologram.teleport(npc.toLocation(world).add(0.0D, npc.hologramHeight(), 0.0D));
    }

    private void applyEquipment(NpcData npc, LivingEntity entity) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }
        for (Map.Entry<String, ItemStack> entry : npc.equipment().entrySet()) {
            switch (entry.getKey()) {
                case "HAND" -> equipment.setItemInMainHand(entry.getValue());
                case "OFFHAND" -> equipment.setItemInOffHand(entry.getValue());
                case "HELMET" -> equipment.setHelmet(entry.getValue());
                case "CHESTPLATE" -> equipment.setChestplate(entry.getValue());
                case "LEGGINGS" -> equipment.setLeggings(entry.getValue());
                case "BOOTS" -> equipment.setBoots(entry.getValue());
                default -> {
                }
            }
        }
    }

    private void applyCustomizations(NpcData npc, LivingEntity entity) {
        for (Map.Entry<String, String> entry : npc.customize().entrySet()) {
            applyCustomization(entity, entry.getKey(), entry.getValue());
        }
    }

    @SuppressWarnings("deprecation")
    public boolean applyCustomization(LivingEntity entity, String key, String value) {
        try {
            switch (key) {
                case "setSmall" -> {
                    if (entity instanceof ArmorStand armorStand) {
                        armorStand.setSmall(Boolean.parseBoolean(value));
                        return true;
                    }
                }
                case "setArms" -> {
                    if (entity instanceof ArmorStand armorStand) {
                        armorStand.setArms(Boolean.parseBoolean(value));
                        return true;
                    }
                }
                case "setBasePlate" -> {
                    if (entity instanceof ArmorStand armorStand) {
                        armorStand.setBasePlate(Boolean.parseBoolean(value));
                        return true;
                    }
                }
                case "setInvisible" -> {
                    if (entity instanceof ArmorStand armorStand) {
                        armorStand.setInvisible(Boolean.parseBoolean(value));
                        return true;
                    }
                }
                case "setPowered" -> {
                    if (entity instanceof Creeper creeper) {
                        creeper.setPowered(Boolean.parseBoolean(value));
                        return true;
                    }
                }
                case "setBaby" -> {
                    if (entity instanceof Zombie zombie) {
                        zombie.setBaby(Boolean.parseBoolean(value));
                        return true;
                    }
                    if (entity instanceof Ageable ageable) {
                        if (Boolean.parseBoolean(value)) {
                            ageable.setBaby();
                        } else {
                            ageable.setAdult();
                        }
                        return true;
                    }
                }
                case "setSize" -> {
                    int size = Integer.parseInt(value);
                    if (entity instanceof Slime slime) {
                        slime.setSize(size);
                        return true;
                    }
                    if (entity instanceof MagmaCube magmaCube) {
                        magmaCube.setSize(size);
                        return true;
                    }
                }
                case "setColor" -> {
                    if (entity instanceof Sheep sheep) {
                        sheep.setColor(DyeColor.valueOf(value.toUpperCase(java.util.Locale.ROOT)));
                        return true;
                    }
                }
                case "setSheared" -> {
                    if (entity instanceof Sheep sheep) {
                        sheep.setSheared(Boolean.parseBoolean(value));
                        return true;
                    }
                }
                case "setProfession" -> {
                    if (entity instanceof Villager villager) {
                        villager.setProfession(Villager.Profession.valueOf(value.toUpperCase(java.util.Locale.ROOT)));
                        return true;
                    }
                }
                case "setVillagerType" -> {
                    if (entity instanceof Villager villager) {
                        villager.setVillagerType(Villager.Type.valueOf(value.toUpperCase(java.util.Locale.ROOT)));
                        return true;
                    }
                }
                case "setSitting" -> {
                    if (entity instanceof Wolf wolf) {
                        wolf.setSitting(Boolean.parseBoolean(value));
                        return true;
                    }
                    if (entity instanceof Fox fox) {
                        fox.setSitting(Boolean.parseBoolean(value));
                        return true;
                    }
                }
                case "setTamed" -> {
                    if (entity instanceof Wolf wolf) {
                        wolf.setTamed(Boolean.parseBoolean(value));
                        return true;
                    }
                }
                case "setAngry" -> {
                    if (entity instanceof Wolf wolf) {
                        wolf.setAngry(Boolean.parseBoolean(value));
                        return true;
                    }
                }
                case "setCollarColor" -> {
                    if (entity instanceof Wolf wolf) {
                        wolf.setCollarColor(DyeColor.valueOf(value.toUpperCase(java.util.Locale.ROOT)));
                        return true;
                    }
                }
                case "setFoxType" -> {
                    if (entity instanceof Fox fox) {
                        fox.setFoxType(Fox.Type.valueOf(value.toUpperCase(java.util.Locale.ROOT)));
                        return true;
                    }
                }
                case "setSleeping" -> {
                    if (entity instanceof Fox fox) {
                        fox.setSleeping(Boolean.parseBoolean(value));
                        return true;
                    }
                }
                case "setCrouching" -> {
                    if (entity instanceof Fox fox) {
                        fox.setCrouching(Boolean.parseBoolean(value));
                        return true;
                    }
                }
                case "setVariant" -> {
                    if (entity instanceof Axolotl axolotl) {
                        axolotl.setVariant(Axolotl.Variant.valueOf(value.toUpperCase(java.util.Locale.ROOT)));
                        return true;
                    }
                }
                default -> {
                }
            }
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        return false;
    }

    public void despawn(NpcData npc) {
        if (npc.type() == EntityType.PLAYER) {
            if (playerNpcService != null) {
                playerNpcService.despawnForAll(npc);
            }
            removeHologram(npc);
            return;
        }
        if (npc.entityUuid() == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(npc.entityUuid());
        if (entity != null) {
            entity.remove();
        }
        removeHologram(npc);
        npc.entityUuid(null);
    }

    public Optional<Location> locationOf(NpcData npc) {
        World world = Bukkit.getWorld(npc.worldName());
        if (world == null) {
            return Optional.empty();
        }
        return Optional.of(npc.toLocation(world));
    }

    public Optional<Entity> entityOf(NpcData npc) {
        if (npc.type() == EntityType.PLAYER) {
            return Optional.empty();
        }
        if (npc.entityUuid() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(Bukkit.getEntity(npc.entityUuid()));
    }

    public void tickLookAtPlayers() {
        for (NpcData npc : npcsById.values()) {
            if (!npc.lookAtPlayer()) {
                continue;
            }
            Entity entity = entityOf(npc).orElse(null);
            if (npc.type() == EntityType.PLAYER) {
                continue;
            }
            if (entity == null || entity.getWorld().getPlayers().isEmpty()) {
                continue;
            }
            double radius = plugin.getConfig().getDouble("settings.look-at-player.radius", 8.0D);
            Player target = null;
            double bestDistanceSquared = radius * radius;
            for (Player player : entity.getWorld().getPlayers()) {
                double distanceSquared = player.getLocation().distanceSquared(entity.getLocation());
                if (distanceSquared <= bestDistanceSquared) {
                    bestDistanceSquared = distanceSquared;
                    target = player;
                }
            }
            if (target == null) {
                continue;
            }
            Location location = entity.getLocation();
            location.setDirection(target.getEyeLocation().subtract(location).toVector());
            entity.teleport(location);
            npc.updateLocation(location);
        }
    }

    public void tickRadiusConversations(ConversationManager conversationManager) {
        for (NpcData npc : npcsById.values()) {
            if (!"RADIUS".equalsIgnoreCase(npc.conversationTrigger())) {
                continue;
            }
            Entity entity = entityOf(npc).orElse(null);
            Conversation conversation = conversationManager.get(npc.conversationName()).orElse(null);
            if (entity == null || conversation == null) {
                continue;
            }
            double radiusSquared = conversation.radius() * conversation.radius();
            for (Player player : entity.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(entity.getLocation()) <= radiusSquared) {
                    conversationManager.play(player, npc);
                }
            }
        }
    }

    public void tickPathMovement(PathManager pathManager) {
        for (NpcData npc : npcsById.values()) {
            if (npc.pathName().isBlank()) {
                continue;
            }
            NpcPath path = pathManager.get(npc.pathName()).orElse(null);
            Entity entity = entityOf(npc).orElse(null);
            if (path == null || path.points().isEmpty()) {
                continue;
            }
            if (npc.type() != EntityType.PLAYER && entity == null) {
                continue;
            }
            int targetIndex = pathTargetIndexByNpcId.getOrDefault(npc.id(), 0);
            if (targetIndex >= path.points().size()) {
                targetIndex = 0;
            }
            PathPoint point = path.points().get(targetIndex);
            World world = Bukkit.getWorld(point.worldName());
            if (world == null) {
                continue;
            }
            Location current = npc.type() == EntityType.PLAYER
                    ? npc.toLocation(Bukkit.getWorld(npc.worldName()))
                    : entity.getLocation();
            Location target = point.toLocation(world);
            if (!current.getWorld().equals(world)) {
                if (npc.type() == EntityType.PLAYER) {
                    npc.updateLocation(target);
                    if (playerNpcService != null) {
                        playerNpcService.respawnForAll(npc);
                    }
                } else {
                    entity.teleport(target);
                    npc.updateLocation(target);
                }
                moveHologramToNpc(npc);
                advancePathTarget(npc, path, targetIndex);
                continue;
            }
            double distance = current.distance(target);
            if (distance <= Math.max(0.15D, path.speed())) {
                npc.updateLocation(target);
                if (npc.type() == EntityType.PLAYER) {
                    if (playerNpcService != null) {
                        playerNpcService.teleportForAll(npc);
                    }
                } else {
                    entity.teleport(target);
                }
                moveHologramToNpc(npc);
                advancePathTarget(npc, path, targetIndex);
                continue;
            }
            Vector direction = target.toVector().subtract(current.toVector()).normalize();
            Location next = current.add(direction.multiply(path.speed()));
            next.setDirection(direction);
            npc.updateLocation(next);
            if (npc.type() == EntityType.PLAYER) {
                if (playerNpcService != null) {
                    playerNpcService.teleportForAll(npc);
                }
            } else {
                entity.teleport(next);
            }
            moveHologramToNpc(npc);
        }
    }

    private void advancePathTarget(NpcData npc, NpcPath path, int currentIndex) {
        int nextIndex = currentIndex + 1;
        if (nextIndex >= path.points().size()) {
            nextIndex = path.loop() ? 0 : path.points().size() - 1;
        }
        pathTargetIndexByNpcId.put(npc.id(), nextIndex);
    }

    public enum CreateResult {
        CREATED,
        DUPLICATE_ID,
        PLAYER_UNSUPPORTED,
        UNSPAWNABLE_TYPE
    }
}
