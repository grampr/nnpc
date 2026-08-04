package net.nehaverse.nehanpcs.storage;

import net.nehaverse.nehanpcs.action.ActionType;
import net.nehaverse.nehanpcs.action.NpcAction;
import net.nehaverse.nehanpcs.conversation.Conversation;
import net.nehaverse.nehanpcs.conversation.ConversationLine;
import net.nehaverse.nehanpcs.npc.NpcData;
import net.nehaverse.nehanpcs.path.NpcPath;
import net.nehaverse.nehanpcs.path.PathPoint;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class YamlStorageManager {
    private final Plugin plugin;
    private final File npcFile;
    private final File conversationFile;
    private final File pathFile;

    public YamlStorageManager(Plugin plugin) {
        this.plugin = plugin;
        this.npcFile = new File(plugin.getDataFolder(), "npcs.yml");
        this.conversationFile = new File(plugin.getDataFolder(), "conversations.yml");
        this.pathFile = new File(plugin.getDataFolder(), "paths.yml");
    }

    public List<NpcData> loadNpcs() {
        if (!npcFile.exists()) {
            return List.of();
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(npcFile);
        ConfigurationSection section = config.getConfigurationSection("npcs");
        if (section == null) {
            return List.of();
        }

        List<NpcData> npcs = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection npcSection = section.getConfigurationSection(key);
            if (npcSection == null) {
                continue;
            }
            try {
                int id = Integer.parseInt(key);
                EntityType type = EntityType.valueOf(npcSection.getString("type", "PIG").toUpperCase(Locale.ROOT));
                String worldName = npcSection.getString("world", "world");
                World world = plugin.getServer().getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("Skipping NPC " + id + ": world not found: " + worldName);
                    continue;
                }
                Location location = new Location(
                        world,
                        npcSection.getDouble("x"),
                        npcSection.getDouble("y"),
                        npcSection.getDouble("z"),
                        (float) npcSection.getDouble("yaw"),
                        (float) npcSection.getDouble("pitch")
                );
                NpcData npc = new NpcData(id, type, npcSection.getString("name", type.name()), location);
                npc.profileUuid(java.util.UUID.fromString(npcSection.getString("profile-uuid", java.util.UUID.randomUUID().toString())));
                npc.skinSource(npcSection.getString("skin.source", ""));
                npc.skinValue(npcSection.getString("skin.value", ""));
                npc.skinSignature(npcSection.getString("skin.signature", ""));
                npc.mirror(npcSection.getBoolean("mirror", false));
                double hologramHeight = npcSection.getDouble(
                        "hologram-height", NpcData.defaultHologramHeight(type));
                if (type == EntityType.PLAYER
                        && Math.abs(hologramHeight - NpcData.LEGACY_HOLOGRAM_HEIGHT) < 0.0001D) {
                    hologramHeight = NpcData.PLAYER_HOLOGRAM_HEIGHT;
                }
                npc.hologramHeight(hologramHeight);
                npc.showHologram(npcSection.getBoolean("show-hologram", true));
                npc.lookAtPlayer(npcSection.getBoolean("look-at-player", false));
                npc.collidable(npcSection.getBoolean("collidable", true));
                npc.glowing(npcSection.getBoolean("glow.enabled", false));
                npc.glowColor(npcSection.getString("glow.color", "WHITE"));
                npc.conversationName(npcSection.getString("conversation.name", ""));
                npc.conversationTrigger(npcSection.getString("conversation.trigger", ""));
                npc.pathName(npcSection.getString("path", ""));
                npc.lines().addAll(npcSection.getStringList("lines"));

                ConfigurationSection equipmentSection = npcSection.getConfigurationSection("equipment");
                if (equipmentSection != null) {
                    for (String slot : equipmentSection.getKeys(false)) {
                        ItemStack item = equipmentSection.getItemStack(slot);
                        if (item != null) {
                            npc.equipment().put(slot, item);
                        }
                    }
                }

                ConfigurationSection customizeSection = npcSection.getConfigurationSection("customize");
                if (customizeSection != null) {
                    for (String customizeKey : customizeSection.getKeys(false)) {
                        npc.customize().put(customizeKey, customizeSection.getString(customizeKey, ""));
                    }
                }

                ConfigurationSection actionsSection = npcSection.getConfigurationSection("actions");
                if (actionsSection != null) {
                    for (String actionKey : actionsSection.getKeys(false)) {
                        ConfigurationSection actionSection = actionsSection.getConfigurationSection(actionKey);
                        if (actionSection == null) {
                            continue;
                        }
                        ActionType actionType = ActionType.valueOf(actionSection.getString("type", "MESSAGE").toUpperCase(Locale.ROOT));
                        String content = actionSection.getString("content", "");
                        int cooldownSeconds = actionSection.getInt("cooldown-seconds", 0);
                        npc.actions().add(new NpcAction(actionType, content, cooldownSeconds));
                    }
                }
                npcs.add(npc);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping malformed NPC entry '" + key + "': " + ex.getMessage());
            }
        }
        return npcs;
    }

    public void saveNpcs(Collection<NpcData> npcs) {
        YamlConfiguration config = new YamlConfiguration();
        for (NpcData npc : npcs) {
            String path = "npcs." + npc.id() + ".";
            config.set(path + "type", npc.type().name());
            config.set(path + "name", npc.name());
            config.set(path + "profile-uuid", npc.profileUuid().toString());
            config.set(path + "skin.source", npc.skinSource());
            config.set(path + "skin.value", npc.skinValue());
            config.set(path + "skin.signature", npc.skinSignature());
            config.set(path + "mirror", npc.mirror());
            config.set(path + "world", npc.worldName());
            config.set(path + "x", npc.x());
            config.set(path + "y", npc.y());
            config.set(path + "z", npc.z());
            config.set(path + "yaw", npc.yaw());
            config.set(path + "pitch", npc.pitch());
            config.set(path + "hologram-height", npc.hologramHeight());
            config.set(path + "show-hologram", npc.showHologram());
            config.set(path + "look-at-player", npc.lookAtPlayer());
            config.set(path + "collidable", npc.collidable());
            config.set(path + "glow.enabled", npc.glowing());
            config.set(path + "glow.color", npc.glowColor());
            config.set(path + "conversation.name", npc.conversationName());
            config.set(path + "conversation.trigger", npc.conversationTrigger());
            config.set(path + "path", npc.pathName());
            config.set(path + "lines", npc.lines());
            for (var entry : npc.equipment().entrySet()) {
                config.set(path + "equipment." + entry.getKey(), entry.getValue());
            }
            for (var entry : npc.customize().entrySet()) {
                config.set(path + "customize." + entry.getKey(), entry.getValue());
            }
            for (int index = 0; index < npc.actions().size(); index++) {
                NpcAction action = npc.actions().get(index);
                String actionPath = path + "actions." + index + ".";
                config.set(actionPath + "type", action.type().name());
                config.set(actionPath + "content", action.content());
                config.set(actionPath + "cooldown-seconds", action.cooldownSeconds());
            }
        }

        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("Failed to create data folder");
            }
            config.save(npcFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save NPC data: " + ex.getMessage());
        }
    }

    public Map<String, Conversation> loadConversations() {
        if (!conversationFile.exists()) {
            return Map.of();
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(conversationFile);
        ConfigurationSection section = config.getConfigurationSection("conversations");
        if (section == null) {
            return Map.of();
        }
        Map<String, Conversation> conversations = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection conversationSection = section.getConfigurationSection(key);
            if (conversationSection == null) {
                continue;
            }
            Conversation conversation = new Conversation(conversationSection.getString("name", key));
            conversation.cooldownSeconds(conversationSection.getInt("cooldown-seconds", 10));
            conversation.radius(conversationSection.getDouble("radius", 5.0D));
            ConfigurationSection linesSection = conversationSection.getConfigurationSection("texts");
            if (linesSection != null) {
                for (String lineKey : linesSection.getKeys(false)) {
                    ConfigurationSection lineSection = linesSection.getConfigurationSection(lineKey);
                    if (lineSection != null) {
                        conversation.lines().add(new ConversationLine(
                                lineSection.getInt("delay-ticks", 0),
                                lineSection.getString("text", "")
                        ));
                    }
                }
            }
            conversations.put(key.toLowerCase(Locale.ROOT), conversation);
        }
        return conversations;
    }

    public void saveConversations(Collection<Conversation> conversations) {
        YamlConfiguration config = new YamlConfiguration();
        for (Conversation conversation : conversations) {
            String path = "conversations." + conversation.name().toLowerCase(Locale.ROOT) + ".";
            config.set(path + "name", conversation.name());
            config.set(path + "cooldown-seconds", conversation.cooldownSeconds());
            config.set(path + "radius", conversation.radius());
            for (int index = 0; index < conversation.lines().size(); index++) {
                ConversationLine line = conversation.lines().get(index);
                config.set(path + "texts." + index + ".delay-ticks", line.delayTicks());
                config.set(path + "texts." + index + ".text", line.text());
            }
        }
        saveFile(config, conversationFile, "conversation");
    }

    public Map<String, NpcPath> loadPaths() {
        if (!pathFile.exists()) {
            return Map.of();
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(pathFile);
        ConfigurationSection section = config.getConfigurationSection("paths");
        if (section == null) {
            return Map.of();
        }
        Map<String, NpcPath> paths = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection pathSection = section.getConfigurationSection(key);
            if (pathSection == null) {
                continue;
            }
            NpcPath npcPath = new NpcPath(pathSection.getString("name", key));
            npcPath.loop(pathSection.getBoolean("loop", true));
            npcPath.speed(pathSection.getDouble("speed", 0.2D));
            ConfigurationSection pointsSection = pathSection.getConfigurationSection("points");
            if (pointsSection != null) {
                for (String pointKey : pointsSection.getKeys(false)) {
                    ConfigurationSection point = pointsSection.getConfigurationSection(pointKey);
                    if (point != null) {
                        npcPath.points().add(new PathPoint(
                                point.getString("world", "world"),
                                point.getDouble("x"),
                                point.getDouble("y"),
                                point.getDouble("z"),
                                (float) point.getDouble("yaw"),
                                (float) point.getDouble("pitch")
                        ));
                    }
                }
            }
            paths.put(key.toLowerCase(Locale.ROOT), npcPath);
        }
        return paths;
    }

    public void savePaths(Collection<NpcPath> paths) {
        YamlConfiguration config = new YamlConfiguration();
        for (NpcPath npcPath : paths) {
            String path = "paths." + npcPath.name().toLowerCase(Locale.ROOT) + ".";
            config.set(path + "name", npcPath.name());
            config.set(path + "loop", npcPath.loop());
            config.set(path + "speed", npcPath.speed());
            for (int index = 0; index < npcPath.points().size(); index++) {
                PathPoint point = npcPath.points().get(index);
                String pointPath = path + "points." + index + ".";
                config.set(pointPath + "world", point.worldName());
                config.set(pointPath + "x", point.x());
                config.set(pointPath + "y", point.y());
                config.set(pointPath + "z", point.z());
                config.set(pointPath + "yaw", point.yaw());
                config.set(pointPath + "pitch", point.pitch());
            }
        }
        saveFile(config, pathFile, "path");
    }

    private void saveFile(YamlConfiguration config, File file, String label) {
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("Failed to create data folder");
            }
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save " + label + " data: " + ex.getMessage());
        }
    }
}
