package net.nehaverse.nehanpcs.npc;

import net.nehaverse.nehanpcs.action.NpcAction;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NpcData {
    private final int id;
    private EntityType type;
    private String name;
    private String worldName;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private double hologramHeight = 0.3D;
    private boolean showHologram = true;
    private boolean lookAtPlayer = false;
    private boolean collidable = true;
    private boolean glowing = false;
    private String glowColor = "WHITE";
    private UUID profileUuid;
    private String skinSource = "";
    private String skinValue = "";
    private String skinSignature = "";
    private boolean mirror = false;
    private String conversationName = "";
    private String conversationTrigger = "";
    private String pathName = "";
    private final List<String> lines = new ArrayList<>();
    private final Map<String, ItemStack> equipment = new LinkedHashMap<>();
    private final Map<String, String> customize = new LinkedHashMap<>();
    private final List<NpcAction> actions = new ArrayList<>();
    private transient UUID entityUuid;
    private transient UUID hologramUuid;
    private transient int fakeEntityId;

    public NpcData(int id, EntityType type, String name, Location location) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.profileUuid = UUID.randomUUID();
        updateLocation(location);
    }

    public int id() {
        return id;
    }

    public EntityType type() {
        return type;
    }

    public void type(EntityType type) {
        this.type = type;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public String worldName() {
        return worldName;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public UUID entityUuid() {
        return entityUuid;
    }

    public UUID hologramUuid() {
        return hologramUuid;
    }

    public double hologramHeight() {
        return hologramHeight;
    }

    public void hologramHeight(double hologramHeight) {
        this.hologramHeight = hologramHeight;
    }

    public boolean showHologram() {
        return showHologram;
    }

    public void showHologram(boolean showHologram) {
        this.showHologram = showHologram;
    }

    public boolean lookAtPlayer() {
        return lookAtPlayer;
    }

    public void lookAtPlayer(boolean lookAtPlayer) {
        this.lookAtPlayer = lookAtPlayer;
    }

    public boolean collidable() {
        return collidable;
    }

    public void collidable(boolean collidable) {
        this.collidable = collidable;
    }

    public boolean glowing() {
        return glowing;
    }

    public void glowing(boolean glowing) {
        this.glowing = glowing;
    }

    public String glowColor() {
        return glowColor;
    }

    public void glowColor(String glowColor) {
        this.glowColor = glowColor;
    }

    public UUID profileUuid() {
        return profileUuid;
    }

    public void profileUuid(UUID profileUuid) {
        this.profileUuid = profileUuid == null ? UUID.randomUUID() : profileUuid;
    }

    public String skinSource() {
        return skinSource;
    }

    public void skinSource(String skinSource) {
        this.skinSource = skinSource == null ? "" : skinSource;
    }

    public String skinValue() {
        return skinValue;
    }

    public void skinValue(String skinValue) {
        this.skinValue = skinValue == null ? "" : skinValue;
    }

    public String skinSignature() {
        return skinSignature;
    }

    public void skinSignature(String skinSignature) {
        this.skinSignature = skinSignature == null ? "" : skinSignature;
    }

    public boolean mirror() {
        return mirror;
    }

    public void mirror(boolean mirror) {
        this.mirror = mirror;
    }

    public String conversationName() {
        return conversationName;
    }

    public void conversationName(String conversationName) {
        this.conversationName = conversationName == null ? "" : conversationName;
    }

    public String conversationTrigger() {
        return conversationTrigger;
    }

    public void conversationTrigger(String conversationTrigger) {
        this.conversationTrigger = conversationTrigger == null ? "" : conversationTrigger;
    }

    public String pathName() {
        return pathName;
    }

    public void pathName(String pathName) {
        this.pathName = pathName == null ? "" : pathName;
    }

    public List<String> lines() {
        return lines;
    }

    public Map<String, ItemStack> equipment() {
        return equipment;
    }

    public Map<String, String> customize() {
        return customize;
    }

    public List<NpcAction> actions() {
        return actions;
    }

    public void entityUuid(UUID entityUuid) {
        this.entityUuid = entityUuid;
    }

    public void hologramUuid(UUID hologramUuid) {
        this.hologramUuid = hologramUuid;
    }

    public int fakeEntityId() {
        return fakeEntityId;
    }

    public void fakeEntityId(int fakeEntityId) {
        this.fakeEntityId = fakeEntityId;
    }

    public void updateLocation(Location location) {
        this.worldName = location.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
    }

    public Location toLocation(World world) {
        return new Location(world, x, y, z, yaw, pitch);
    }
}
