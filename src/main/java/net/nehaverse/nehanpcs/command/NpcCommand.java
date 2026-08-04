package net.nehaverse.nehanpcs.command;

import net.nehaverse.nehanpcs.NehaNPCsPlugin;
import net.nehaverse.nehanpcs.action.ActionType;
import net.nehaverse.nehanpcs.action.NpcAction;
import net.nehaverse.nehanpcs.conversation.Conversation;
import net.nehaverse.nehanpcs.conversation.ConversationLine;
import net.nehaverse.nehanpcs.conversation.ConversationManager;
import net.nehaverse.nehanpcs.npc.NpcData;
import net.nehaverse.nehanpcs.npc.NpcManager;
import net.nehaverse.nehanpcs.path.NpcPath;
import net.nehaverse.nehanpcs.path.PathManager;
import net.nehaverse.nehanpcs.path.PathPoint;
import net.nehaverse.nehanpcs.protocol.PlayerNpcService;
import net.nehaverse.nehanpcs.protocol.SkinService;
import net.nehaverse.nehanpcs.util.MessageUtil;
import net.nehaverse.nehanpcs.util.PermissionUtil;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class NpcCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("create", "delete", "list", "teleport", "move", "skin", "lines", "height", "equip", "type", "customize", "toggle", "action", "conversation", "path", "save", "reload");
    private static final List<String> ACTION_SUBCOMMANDS = List.of("add", "list", "remove", "cooldown");
    private static final List<String> CONVERSATION_SUBCOMMANDS = List.of("create", "remove", "set", "cooldown", "radius", "text", "list");
    private static final List<String> CONVERSATION_TEXT_SUBCOMMANDS = List.of("add", "remove", "list");
    private static final List<String> CONVERSATION_TRIGGERS = List.of("CLICK", "RADIUS");
    private static final List<String> PATH_SUBCOMMANDS = List.of("create", "point", "set", "speed", "loop", "delete", "list");
    private static final List<String> EQUIPMENT_SLOTS = List.of("HAND", "OFFHAND", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");
    private static final List<String> TOGGLES = List.of("look", "holo", "glow", "mirror", "collision");
    private static final List<String> CUSTOMIZE_KEYS = List.of(
            "setSmall", "setArms", "setBasePlate", "setInvisible", "setPowered", "setBaby", "setSize",
            "setColor", "setSheared", "setProfession", "setVillagerType", "setSitting", "setTamed",
            "setAngry", "setCollarColor", "setFoxType", "setSleeping", "setCrouching", "setVariant"
    );

    private final NehaNPCsPlugin plugin;
    private final NpcManager npcManager;
    private final ConversationManager conversationManager;
    private final PathManager pathManager;
    private final SkinService skinService;
    private final PlayerNpcService playerNpcService;
    private final MessageUtil messages;

    public NpcCommand(NehaNPCsPlugin plugin, NpcManager npcManager, ConversationManager conversationManager,
                      PathManager pathManager, SkinService skinService, PlayerNpcService playerNpcService, MessageUtil messages) {
        this.plugin = plugin;
        this.npcManager = npcManager;
        this.conversationManager = conversationManager;
        this.pathManager = pathManager;
        this.skinService = skinService;
        this.playerNpcService = playerNpcService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "usage-subcommand");
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(sender, args);
            case "delete" -> delete(sender, args);
            case "list" -> list(sender);
            case "teleport", "tp" -> teleport(sender, args);
            case "move" -> move(sender, args);
            case "skin" -> skin(sender, args);
            case "lines" -> lines(sender, args);
            case "height" -> height(sender, args);
            case "equip" -> equip(sender, args);
            case "type" -> type(sender, args);
            case "customize" -> customize(sender, args);
            case "toggle" -> toggle(sender, args);
            case "action" -> action(sender, args);
            case "conversation" -> conversation(sender, args);
            case "path" -> path(sender, args);
            case "save" -> save(sender);
            case "reload" -> reload(sender);
            default -> {
                messages.send(sender, "usage-subcommand");
                yield true;
            }
        };
    }

    private boolean create(CommandSender sender, String[] args) {
        if (!check(sender, "nehanpcs.cmd.create")) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }
        if (args.length < 4) {
            messages.send(sender, "usage-create");
            return true;
        }

        Integer id = parseId(sender, args[1]);
        if (id == null) {
            return true;
        }

        EntityType type;
        try {
            type = EntityType.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            messages.send(sender, "usage-type", Map.of("%type%", args[2]));
            return true;
        }

        String name = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        NpcManager.CreateResult result = npcManager.create(id, type, name, player.getLocation());
        switch (result) {
            case CREATED -> messages.send(sender, "npc-created", Map.of("%id%", String.valueOf(id)));
            case DUPLICATE_ID -> messages.send(sender, "npc-id-already-exists", Map.of("%id%", String.valueOf(id)));
            case PLAYER_UNSUPPORTED -> messages.send(sender, "npc-player-unsupported");
            case UNSPAWNABLE_TYPE -> messages.send(sender, "usage-type", Map.of("%type%", args[2]));
        }
        return true;
    }

    private boolean delete(CommandSender sender, String[] args) {
        if (!check(sender, "nehanpcs.cmd.delete")) {
            return true;
        }
        Integer id = requireId(sender, args);
        if (id == null) {
            return true;
        }
        if (npcManager.delete(id)) {
            messages.send(sender, "npc-deleted", Map.of("%id%", String.valueOf(id)));
        } else {
            messages.send(sender, "npc-not-found", Map.of("%id%", String.valueOf(id)));
        }
        return true;
    }

    private boolean list(CommandSender sender) {
        if (!check(sender, "nehanpcs.cmd.list")) {
            return true;
        }
        if (npcManager.all().isEmpty()) {
            messages.send(sender, "list-empty");
            return true;
        }

        messages.send(sender, "list-header", Map.of("%count%", String.valueOf(npcManager.all().size())));
        for (NpcData npc : npcManager.all()) {
            String line = messages.raw(sender, "list-entry")
                    .replace("%id%", String.valueOf(npc.id()))
                    .replace("%type%", npc.type().name())
                    .replace("%name%", npc.name())
                    .replace("%actions%", String.valueOf(npc.actions().size()))
                    .replace("%world%", npc.worldName())
                    .replace("%.1f%x%", String.format(Locale.ROOT, "%.1f", npc.x()))
                    .replace("%.1f%y%", String.format(Locale.ROOT, "%.1f", npc.y()))
                    .replace("%.1f%z%", String.format(Locale.ROOT, "%.1f", npc.z()));
            sender.sendMessage(messages.component(messages.raw(sender, "prefix") + line));
        }
        return true;
    }

    private boolean teleport(CommandSender sender, String[] args) {
        if (!check(sender, "nehanpcs.cmd.teleport")) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }
        Integer id = requireId(sender, args);
        if (id == null) {
            return true;
        }
        NpcData npc = npcManager.get(id).orElse(null);
        if (npc == null) {
            messages.send(sender, "npc-not-found", Map.of("%id%", String.valueOf(id)));
            return true;
        }
        Location location = npcManager.locationOf(npc).orElse(null);
        if (location == null) {
            messages.send(sender, "npc-world-missing", Map.of("%world%", npc.worldName()));
            return true;
        }
        player.teleport(location);
        messages.send(sender, "npc-teleported", Map.of("%id%", String.valueOf(id)));
        return true;
    }

    private boolean move(CommandSender sender, String[] args) {
        if (!check(sender, "nehanpcs.cmd.move")) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }
        Integer id = requireId(sender, args);
        if (id == null) {
            return true;
        }
        if (npcManager.move(id, player.getLocation())) {
            messages.send(sender, "npc-moved", Map.of("%id%", String.valueOf(id)));
        } else {
            messages.send(sender, "npc-not-found", Map.of("%id%", String.valueOf(id)));
        }
        return true;
    }

    private boolean skin(CommandSender sender, String[] args) {
        if (!check(sender, "nehanpcs.cmd.skin")) {
            return true;
        }
        if (args.length < 3) {
            messages.send(sender, "usage-skin");
            return true;
        }
        NpcData npc = requireNpc(sender, args[1]);
        if (npc == null) {
            return true;
        }
        if (npc.type() != EntityType.PLAYER) {
            messages.send(sender, "skin-player-only");
            return true;
        }
        String username = args[2];
        messages.send(sender, "skin-fetching", Map.of("%source%", username));
        skinService.fetchByUsername(username).thenAccept(result -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (result.isEmpty()) {
                messages.send(sender, "skin-fetch-failed", Map.of("%source%", username));
                return;
            }
            SkinService.SkinData skin = result.get();
            npc.skinSource(skin.source());
            npc.skinValue(skin.value());
            npc.skinSignature(skin.signature());
            npc.mirror(false);
            npcManager.refresh(npc);
            npcManager.save();
            messages.send(sender, "skin-set", Map.of("%id%", String.valueOf(npc.id()), "%source%", username));
        }));
        return true;
    }

    private boolean unsupportedSkin(CommandSender sender) {
        messages.send(sender, "npc-player-unsupported");
        return true;
    }

    private boolean lines(CommandSender sender, String[] args) {
        if (!check(sender, "nehanpcs.cmd.lines")) {
            return true;
        }
        if (args.length < 3) {
            messages.send(sender, "usage-lines");
            return true;
        }
        NpcData npc = requireNpc(sender, args[1]);
        if (npc == null) {
            return true;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        npc.lines().clear();
        npc.lines().addAll(Arrays.stream(text.split("\\\\n|\\|")).filter(line -> !line.isBlank()).toList());
        npc.name(text);
        npcManager.refresh(npc);
        npcManager.save();
        messages.send(sender, "npc-lines-set", Map.of("%id%", String.valueOf(npc.id())));
        return true;
    }

    private boolean height(CommandSender sender, String[] args) {
        if (!check(sender, "nehanpcs.cmd.height")) {
            return true;
        }
        if (args.length < 3) {
            messages.send(sender, "usage-height");
            return true;
        }
        NpcData npc = requireNpc(sender, args[1]);
        if (npc == null) {
            return true;
        }
        Double height = parseDouble(sender, args[2], "usage-height-number");
        if (height == null) {
            return true;
        }
        npc.hologramHeight(height);
        npcManager.refresh(npc);
        npcManager.save();
        messages.send(sender, "npc-height-set", Map.of("%id%", String.valueOf(npc.id()), "%height%", String.valueOf(height)));
        return true;
    }

    private boolean equip(CommandSender sender, String[] args) {
        if (!check(sender, "nehanpcs.cmd.equip")) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }
        if (args.length < 3) {
            messages.send(sender, "usage-equip");
            return true;
        }
        NpcData npc = requireNpc(sender, args[1]);
        if (npc == null) {
            return true;
        }
        String slot = args[2].toUpperCase(Locale.ROOT);
        if (!EQUIPMENT_SLOTS.contains(slot)) {
            messages.send(sender, "usage-equip-slot", Map.of("%slot%", args[2]));
            return true;
        }
        ItemStack item = player.getInventory().getItemInMainHand().clone();
        npc.equipment().put(slot, item);
        npcManager.refresh(npc);
        npcManager.save();
        messages.send(sender, "npc-equipped", Map.of("%id%", String.valueOf(npc.id()), "%slot%", slot));
        return true;
    }

    private boolean type(CommandSender sender, String[] args) {
        if (!check(sender, "nehanpcs.cmd.type")) {
            return true;
        }
        if (args.length < 3) {
            messages.send(sender, "usage-type-command");
            return true;
        }
        NpcData npc = requireNpc(sender, args[1]);
        if (npc == null) {
            return true;
        }
        EntityType entityType;
        try {
            entityType = EntityType.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            messages.send(sender, "usage-type", Map.of("%type%", args[2]));
            return true;
        }
        if (!npcManager.changeType(npc.id(), entityType)) {
            messages.send(sender, "usage-type", Map.of("%type%", args[2]));
            return true;
        }
        messages.send(sender, "npc-type-set", Map.of("%id%", String.valueOf(npc.id()), "%type%", entityType.name()));
        return true;
    }

    private boolean customize(CommandSender sender, String[] args) {
        if (!check(sender, "nehanpcs.cmd.customize")) {
            return true;
        }
        if (args.length < 4) {
            messages.send(sender, "usage-customize");
            return true;
        }
        NpcData npc = requireNpc(sender, args[1]);
        if (npc == null) {
            return true;
        }
        Entity entity = npcManager.entityOf(npc).orElse(null);
        if (!(entity instanceof LivingEntity livingEntity)) {
            messages.send(sender, "customize-unsupported");
            return true;
        }
        String key = args[2];
        String value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        if (!npcManager.applyCustomization(livingEntity, key, value)) {
            messages.send(sender, "customize-unsupported");
            return true;
        }
        npc.customize().put(key, value);
        npcManager.save();
        messages.send(sender, "customize-set", Map.of("%id%", String.valueOf(npc.id()), "%key%", key, "%value%", value));
        return true;
    }

    private boolean toggle(CommandSender sender, String[] args) {
        if (!check(sender, "nehanpcs.cmd.toggle")) {
            return true;
        }
        if (args.length < 3) {
            messages.send(sender, "usage-toggle");
            return true;
        }
        NpcData npc = requireNpc(sender, args[1]);
        if (npc == null) {
            return true;
        }
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "look" -> {
                npc.lookAtPlayer(!npc.lookAtPlayer());
                npcManager.save();
                messages.send(sender, "toggle-set", Map.of("%id%", String.valueOf(npc.id()), "%key%", "look", "%value%", String.valueOf(npc.lookAtPlayer())));
            }
            case "holo" -> {
                npc.showHologram(!npc.showHologram());
                npcManager.refresh(npc);
                npcManager.save();
                messages.send(sender, "toggle-set", Map.of("%id%", String.valueOf(npc.id()), "%key%", "holo", "%value%", String.valueOf(npc.showHologram())));
            }
            case "collision" -> {
                npc.collidable(!npc.collidable());
                npcManager.refresh(npc);
                npcManager.save();
                messages.send(sender, "toggle-set", Map.of("%id%", String.valueOf(npc.id()), "%key%", "collision", "%value%", String.valueOf(npc.collidable())));
            }
            case "glow" -> {
                npc.glowing(!npc.glowing());
                if (args.length >= 4) {
                    npc.glowColor(args[3].toUpperCase(Locale.ROOT));
                }
                npcManager.refresh(npc);
                npcManager.save();
                messages.send(sender, "toggle-set", Map.of("%id%", String.valueOf(npc.id()), "%key%", "glow", "%value%", String.valueOf(npc.glowing())));
            }
            case "mirror" -> {
                if (npc.type() != EntityType.PLAYER) {
                    messages.send(sender, "skin-player-only");
                    return true;
                }
                npc.mirror(!npc.mirror());
                npcManager.refresh(npc);
                npcManager.save();
                messages.send(sender, "toggle-set", Map.of("%id%", String.valueOf(npc.id()), "%key%", "mirror", "%value%", String.valueOf(npc.mirror())));
            }
            default -> messages.send(sender, "usage-toggle");
        }
        return true;
    }

    private boolean save(CommandSender sender) {
        if (!check(sender, "nehanpcs.cmd.save")) {
            return true;
        }
        npcManager.save();
        conversationManager.save();
        pathManager.save();
        messages.send(sender, "save-complete");
        return true;
    }

    private boolean action(CommandSender sender, String[] args) {
        if (!check(sender, "nehanpcs.cmd.action")) {
            return true;
        }
        if (args.length < 3) {
            messages.send(sender, "usage-action");
            return true;
        }

        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> actionAdd(sender, args);
            case "list" -> actionList(sender, args);
            case "remove" -> actionRemove(sender, args);
            case "cooldown" -> actionCooldown(sender, args);
            default -> {
                messages.send(sender, "usage-action");
                yield true;
            }
        };
    }

    private boolean actionAdd(CommandSender sender, String[] args) {
        if (args.length < 5) {
            messages.send(sender, "usage-action-add");
            return true;
        }
        Integer id = parseId(sender, args[2]);
        if (id == null) {
            return true;
        }
        NpcData npc = npcManager.get(id).orElse(null);
        if (npc == null) {
            messages.send(sender, "npc-not-found", Map.of("%id%", String.valueOf(id)));
            return true;
        }

        ActionType type;
        try {
            type = ActionType.valueOf(args[3].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            messages.send(sender, "usage-action-type", Map.of("%type%", args[3]));
            return true;
        }

        String content = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
        npc.actions().add(new NpcAction(type, content, 0));
        npcManager.save();
        messages.send(sender, "action-added", Map.of("%id%", String.valueOf(id), "%action%", String.valueOf(npc.actions().size() - 1)));
        return true;
    }

    private boolean actionList(CommandSender sender, String[] args) {
        Integer id = parseId(sender, args[2]);
        if (id == null) {
            return true;
        }
        NpcData npc = npcManager.get(id).orElse(null);
        if (npc == null) {
            messages.send(sender, "npc-not-found", Map.of("%id%", String.valueOf(id)));
            return true;
        }
        if (npc.actions().isEmpty()) {
            messages.send(sender, "action-list-empty", Map.of("%id%", String.valueOf(id)));
            return true;
        }
        messages.send(sender, "action-list-header", Map.of("%id%", String.valueOf(id), "%count%", String.valueOf(npc.actions().size())));
        for (int index = 0; index < npc.actions().size(); index++) {
            NpcAction action = npc.actions().get(index);
            String line = messages.raw(sender, "action-list-entry")
                    .replace("%action%", String.valueOf(index))
                    .replace("%type%", action.type().name())
                    .replace("%cooldown%", String.valueOf(action.cooldownSeconds()))
                    .replace("%content%", action.content());
            sender.sendMessage(messages.component(messages.raw(sender, "prefix") + line));
        }
        return true;
    }

    private boolean actionRemove(CommandSender sender, String[] args) {
        if (args.length < 4) {
            messages.send(sender, "usage-action-remove");
            return true;
        }
        Integer id = parseId(sender, args[2]);
        Integer actionIndex = parseActionIndex(sender, args[3]);
        if (id == null || actionIndex == null) {
            return true;
        }
        NpcData npc = npcManager.get(id).orElse(null);
        if (npc == null) {
            messages.send(sender, "npc-not-found", Map.of("%id%", String.valueOf(id)));
            return true;
        }
        if (actionIndex < 0 || actionIndex >= npc.actions().size()) {
            messages.send(sender, "action-not-found", Map.of("%action%", String.valueOf(actionIndex)));
            return true;
        }
        npc.actions().remove((int) actionIndex);
        npcManager.save();
        messages.send(sender, "action-removed", Map.of("%id%", String.valueOf(id), "%action%", String.valueOf(actionIndex)));
        return true;
    }

    private boolean actionCooldown(CommandSender sender, String[] args) {
        if (args.length < 5) {
            messages.send(sender, "usage-action-cooldown");
            return true;
        }
        Integer id = parseId(sender, args[2]);
        Integer actionIndex = parseActionIndex(sender, args[3]);
        Integer seconds = parseSeconds(sender, args[4]);
        if (id == null || actionIndex == null || seconds == null) {
            return true;
        }
        NpcData npc = npcManager.get(id).orElse(null);
        if (npc == null) {
            messages.send(sender, "npc-not-found", Map.of("%id%", String.valueOf(id)));
            return true;
        }
        if (actionIndex < 0 || actionIndex >= npc.actions().size()) {
            messages.send(sender, "action-not-found", Map.of("%action%", String.valueOf(actionIndex)));
            return true;
        }
        npc.actions().get(actionIndex).cooldownSeconds(seconds);
        npcManager.save();
        messages.send(sender, "action-cooldown-set", Map.of(
                "%id%", String.valueOf(id),
                "%action%", String.valueOf(actionIndex),
                "%seconds%", String.valueOf(seconds)
        ));
        return true;
    }

    private boolean conversation(CommandSender sender, String[] args) {
        if (!check(sender, "nehanpcs.cmd.conversation")) {
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, "usage-conversation");
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> conversationCreate(sender, args);
            case "remove" -> conversationRemove(sender, args);
            case "set" -> conversationSet(sender, args);
            case "cooldown" -> conversationCooldown(sender, args);
            case "radius" -> conversationRadius(sender, args);
            case "text" -> conversationText(sender, args);
            case "list" -> conversationList(sender);
            default -> {
                messages.send(sender, "usage-conversation");
                yield true;
            }
        };
    }

    private boolean conversationCreate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "usage-conversation-create");
            return true;
        }
        if (conversationManager.create(args[2])) {
            messages.send(sender, "conversation-created", Map.of("%name%", args[2]));
        } else {
            messages.send(sender, "conversation-exists", Map.of("%name%", args[2]));
        }
        return true;
    }

    private boolean conversationRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "usage-conversation-remove");
            return true;
        }
        if (conversationManager.remove(args[2])) {
            for (NpcData npc : npcManager.all()) {
                if (npc.conversationName().equalsIgnoreCase(args[2])) {
                    npc.conversationName("");
                    npc.conversationTrigger("");
                }
            }
            npcManager.save();
            messages.send(sender, "conversation-removed", Map.of("%name%", args[2]));
        } else {
            messages.send(sender, "conversation-not-found", Map.of("%name%", args[2]));
        }
        return true;
    }

    private boolean conversationSet(CommandSender sender, String[] args) {
        if (args.length < 5) {
            messages.send(sender, "usage-conversation-set");
            return true;
        }
        NpcData npc = requireNpc(sender, args[2]);
        if (npc == null) {
            return true;
        }
        Conversation conversation = conversationManager.get(args[3]).orElse(null);
        if (conversation == null) {
            messages.send(sender, "conversation-not-found", Map.of("%name%", args[3]));
            return true;
        }
        String trigger = args[4].toUpperCase(Locale.ROOT);
        if (!CONVERSATION_TRIGGERS.contains(trigger)) {
            messages.send(sender, "usage-conversation-trigger");
            return true;
        }
        npc.conversationName(conversation.name());
        npc.conversationTrigger(trigger);
        npcManager.save();
        messages.send(sender, "conversation-set", Map.of("%id%", String.valueOf(npc.id()), "%name%", conversation.name(), "%trigger%", trigger));
        return true;
    }

    private boolean conversationCooldown(CommandSender sender, String[] args) {
        if (args.length < 4) {
            messages.send(sender, "usage-conversation-cooldown");
            return true;
        }
        Conversation conversation = conversationManager.get(args[2]).orElse(null);
        Integer seconds = parseSeconds(sender, args[3]);
        if (conversation == null) {
            messages.send(sender, "conversation-not-found", Map.of("%name%", args[2]));
            return true;
        }
        if (seconds == null) {
            return true;
        }
        conversation.cooldownSeconds(seconds);
        conversationManager.save();
        messages.send(sender, "conversation-cooldown-set", Map.of("%name%", conversation.name(), "%seconds%", String.valueOf(seconds)));
        return true;
    }

    private boolean conversationRadius(CommandSender sender, String[] args) {
        if (args.length < 4) {
            messages.send(sender, "usage-conversation-radius");
            return true;
        }
        Conversation conversation = conversationManager.get(args[2]).orElse(null);
        Double radius = parseDouble(sender, args[3], "usage-radius-number");
        if (conversation == null) {
            messages.send(sender, "conversation-not-found", Map.of("%name%", args[2]));
            return true;
        }
        if (radius == null) {
            return true;
        }
        conversation.radius(radius);
        conversationManager.save();
        messages.send(sender, "conversation-radius-set", Map.of("%name%", conversation.name(), "%radius%", String.valueOf(radius)));
        return true;
    }

    private boolean conversationText(CommandSender sender, String[] args) {
        if (args.length < 4) {
            messages.send(sender, "usage-conversation-text");
            return true;
        }
        return switch (args[2].toLowerCase(Locale.ROOT)) {
            case "add" -> conversationTextAdd(sender, args);
            case "remove" -> conversationTextRemove(sender, args);
            case "list" -> conversationTextList(sender, args);
            default -> {
                messages.send(sender, "usage-conversation-text");
                yield true;
            }
        };
    }

    private boolean conversationTextAdd(CommandSender sender, String[] args) {
        if (args.length < 6) {
            messages.send(sender, "usage-conversation-text-add");
            return true;
        }
        Conversation conversation = conversationManager.get(args[3]).orElse(null);
        if (conversation == null) {
            messages.send(sender, "conversation-not-found", Map.of("%name%", args[3]));
            return true;
        }
        Integer delay = parseSeconds(sender, args[4]);
        if (delay == null) {
            return true;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 5, args.length));
        conversation.lines().add(new ConversationLine(delay, text));
        conversationManager.save();
        messages.send(sender, "conversation-text-added", Map.of("%name%", conversation.name(), "%index%", String.valueOf(conversation.lines().size() - 1)));
        return true;
    }

    private boolean conversationTextRemove(CommandSender sender, String[] args) {
        if (args.length < 5) {
            messages.send(sender, "usage-conversation-text-remove");
            return true;
        }
        Conversation conversation = conversationManager.get(args[3]).orElse(null);
        Integer index = parseActionIndex(sender, args[4]);
        if (conversation == null) {
            messages.send(sender, "conversation-not-found", Map.of("%name%", args[3]));
            return true;
        }
        if (index == null || index < 0 || index >= conversation.lines().size()) {
            messages.send(sender, "conversation-line-not-found", Map.of("%index%", args[4]));
            return true;
        }
        conversation.lines().remove((int) index);
        conversationManager.save();
        messages.send(sender, "conversation-text-removed", Map.of("%name%", conversation.name(), "%index%", String.valueOf(index)));
        return true;
    }

    private boolean conversationTextList(CommandSender sender, String[] args) {
        Conversation conversation = conversationManager.get(args[3]).orElse(null);
        if (conversation == null) {
            messages.send(sender, "conversation-not-found", Map.of("%name%", args[3]));
            return true;
        }
        messages.send(sender, "conversation-text-header", Map.of("%name%", conversation.name(), "%count%", String.valueOf(conversation.lines().size())));
        for (int index = 0; index < conversation.lines().size(); index++) {
            ConversationLine line = conversation.lines().get(index);
            sender.sendMessage(messages.component(messages.raw(sender, "prefix") + messages.raw(sender, "conversation-text-entry")
                    .replace("%index%", String.valueOf(index))
                    .replace("%delay%", String.valueOf(line.delayTicks()))
                    .replace("%text%", line.text())));
        }
        return true;
    }

    private boolean conversationList(CommandSender sender) {
        if (conversationManager.all().isEmpty()) {
            messages.send(sender, "conversation-list-empty");
            return true;
        }
        messages.send(sender, "conversation-list-header", Map.of("%count%", String.valueOf(conversationManager.all().size())));
        for (Conversation conversation : conversationManager.all()) {
            sender.sendMessage(messages.component(messages.raw(sender, "prefix") + messages.raw(sender, "conversation-list-entry")
                    .replace("%name%", conversation.name())
                    .replace("%lines%", String.valueOf(conversation.lines().size()))
                    .replace("%cooldown%", String.valueOf(conversation.cooldownSeconds()))
                    .replace("%radius%", String.valueOf(conversation.radius()))));
        }
        return true;
    }

    private boolean path(CommandSender sender, String[] args) {
        if (!check(sender, "nehanpcs.cmd.path")) {
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, "usage-path");
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> pathCreate(sender, args);
            case "point" -> pathPoint(sender, args);
            case "set" -> pathSet(sender, args);
            case "speed" -> pathSpeed(sender, args);
            case "loop" -> pathLoop(sender, args);
            case "delete" -> pathDelete(sender, args);
            case "list" -> pathList(sender);
            default -> {
                messages.send(sender, "usage-path");
                yield true;
            }
        };
    }

    private boolean pathCreate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "usage-path-create");
            return true;
        }
        if (pathManager.create(args[2])) {
            messages.send(sender, "path-created", Map.of("%name%", args[2]));
        } else {
            messages.send(sender, "path-exists", Map.of("%name%", args[2]));
        }
        return true;
    }

    private boolean pathPoint(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }
        if (args.length < 3) {
            messages.send(sender, "usage-path-point");
            return true;
        }
        NpcPath path = pathManager.get(args[2]).orElse(null);
        if (path == null) {
            messages.send(sender, "path-not-found", Map.of("%name%", args[2]));
            return true;
        }
        path.points().add(PathPoint.from(player.getLocation()));
        pathManager.save();
        messages.send(sender, "path-point-added", Map.of("%name%", path.name(), "%count%", String.valueOf(path.points().size())));
        return true;
    }

    private boolean pathSet(CommandSender sender, String[] args) {
        if (args.length < 4) {
            messages.send(sender, "usage-path-set");
            return true;
        }
        NpcData npc = requireNpc(sender, args[2]);
        if (npc == null) {
            return true;
        }
        NpcPath path = pathManager.get(args[3]).orElse(null);
        if (path == null) {
            messages.send(sender, "path-not-found", Map.of("%name%", args[3]));
            return true;
        }
        npcManager.setPath(npc.id(), path.name());
        messages.send(sender, "path-set", Map.of("%id%", String.valueOf(npc.id()), "%name%", path.name()));
        return true;
    }

    private boolean pathSpeed(CommandSender sender, String[] args) {
        if (args.length < 4) {
            messages.send(sender, "usage-path-speed");
            return true;
        }
        NpcPath path = pathManager.get(args[2]).orElse(null);
        Double speed = parseDouble(sender, args[3], "usage-speed-number");
        if (path == null) {
            messages.send(sender, "path-not-found", Map.of("%name%", args[2]));
            return true;
        }
        if (speed == null) {
            return true;
        }
        path.speed(speed);
        pathManager.save();
        messages.send(sender, "path-speed-set", Map.of("%name%", path.name(), "%speed%", String.valueOf(path.speed())));
        return true;
    }

    private boolean pathLoop(CommandSender sender, String[] args) {
        if (args.length < 4) {
            messages.send(sender, "usage-path-loop");
            return true;
        }
        NpcPath path = pathManager.get(args[2]).orElse(null);
        if (path == null) {
            messages.send(sender, "path-not-found", Map.of("%name%", args[2]));
            return true;
        }
        path.loop(Boolean.parseBoolean(args[3]));
        pathManager.save();
        messages.send(sender, "path-loop-set", Map.of("%name%", path.name(), "%loop%", String.valueOf(path.loop())));
        return true;
    }

    private boolean pathDelete(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "usage-path-delete");
            return true;
        }
        if (pathManager.delete(args[2])) {
            for (NpcData npc : npcManager.all()) {
                if (npc.pathName().equalsIgnoreCase(args[2])) {
                    npc.pathName("");
                }
            }
            npcManager.save();
            messages.send(sender, "path-deleted", Map.of("%name%", args[2]));
        } else {
            messages.send(sender, "path-not-found", Map.of("%name%", args[2]));
        }
        return true;
    }

    private boolean pathList(CommandSender sender) {
        if (pathManager.all().isEmpty()) {
            messages.send(sender, "path-list-empty");
            return true;
        }
        messages.send(sender, "path-list-header", Map.of("%count%", String.valueOf(pathManager.all().size())));
        for (NpcPath path : pathManager.all()) {
            sender.sendMessage(messages.component(messages.raw(sender, "prefix") + messages.raw(sender, "path-list-entry")
                    .replace("%name%", path.name())
                    .replace("%points%", String.valueOf(path.points().size()))
                    .replace("%speed%", String.valueOf(path.speed()))));
        }
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!check(sender, "nehanpcs.cmd.reload")) {
            return true;
        }
        plugin.reloadPlugin();
        messages.send(sender, "reload-complete");
        return true;
    }

    private boolean check(CommandSender sender, String permission) {
        if (PermissionUtil.has(sender, permission)) {
            return true;
        }
        messages.send(sender, "no-permission");
        return false;
    }

    private Integer requireId(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "usage-id");
            return null;
        }
        return parseId(sender, args[1]);
    }

    private Integer parseId(CommandSender sender, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            messages.send(sender, "usage-id");
            return null;
        }
    }

    private Integer parseActionIndex(CommandSender sender, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            messages.send(sender, "usage-action-id");
            return null;
        }
    }

    private Integer parseSeconds(CommandSender sender, String value) {
        try {
            int seconds = Integer.parseInt(value);
            if (seconds < 0) {
                messages.send(sender, "usage-seconds");
                return null;
            }
            return seconds;
        } catch (NumberFormatException ex) {
            messages.send(sender, "usage-seconds");
            return null;
        }
    }

    private Double parseDouble(CommandSender sender, String value, String messageKey) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            messages.send(sender, messageKey);
            return null;
        }
    }

    private NpcData requireNpc(CommandSender sender, String idValue) {
        Integer id = parseId(sender, idValue);
        if (id == null) {
            return null;
        }
        NpcData npc = npcManager.get(id).orElse(null);
        if (npc == null) {
            messages.send(sender, "npc-not-found", Map.of("%id%", String.valueOf(id)));
            return null;
        }
        return npc;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return startsWith(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && List.of("delete", "teleport", "tp", "move", "lines", "height", "equip", "type", "customize", "toggle").contains(args[0].toLowerCase(Locale.ROOT))) {
            return startsWith(npcManager.all().stream().map(npc -> String.valueOf(npc.id())).toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("conversation")) {
            return startsWith(CONVERSATION_SUBCOMMANDS, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("conversation") && args[1].equalsIgnoreCase("text")) {
            return startsWith(CONVERSATION_TEXT_SUBCOMMANDS, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("conversation") && List.of("remove", "set", "cooldown", "radius").contains(args[1].toLowerCase(Locale.ROOT))) {
            return startsWith(conversationManager.all().stream().map(Conversation::name).toList(), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("conversation") && args[1].equalsIgnoreCase("set")) {
            return startsWith(conversationManager.all().stream().map(Conversation::name).toList(), args[3]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("conversation") && args[1].equalsIgnoreCase("set")) {
            return startsWith(CONVERSATION_TRIGGERS.stream().map(String::toLowerCase).toList(), args[4]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("conversation") && args[1].equalsIgnoreCase("text")) {
            return startsWith(conversationManager.all().stream().map(Conversation::name).toList(), args[3]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("path")) {
            return startsWith(PATH_SUBCOMMANDS, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("path") && List.of("point", "delete", "set", "speed", "loop").contains(args[1].toLowerCase(Locale.ROOT))) {
            if (args[1].equalsIgnoreCase("set")) {
                return startsWith(npcManager.all().stream().map(npc -> String.valueOf(npc.id())).toList(), args[2]);
            }
            return startsWith(pathManager.all().stream().map(NpcPath::name).toList(), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("path") && args[1].equalsIgnoreCase("set")) {
            return startsWith(pathManager.all().stream().map(NpcPath::name).toList(), args[3]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("equip")) {
            return startsWith(EQUIPMENT_SLOTS.stream().map(String::toLowerCase).toList(), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("toggle")) {
            return startsWith(TOGGLES, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("customize")) {
            return startsWith(CUSTOMIZE_KEYS, args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("action")) {
            return startsWith(ACTION_SUBCOMMANDS, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("action")) {
            return startsWith(npcManager.all().stream().map(npc -> String.valueOf(npc.id())).toList(), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("action") && args[1].equalsIgnoreCase("add")) {
            return startsWith(Arrays.stream(ActionType.values()).map(type -> type.name().toLowerCase(Locale.ROOT)).toList(), args[3]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("action")
                && List.of("remove", "cooldown").contains(args[1].toLowerCase(Locale.ROOT))) {
            NpcData npc = npcManager.get(parseIntOrMinusOne(args[2])).orElse(null);
            if (npc == null) {
                return List.of();
            }
            List<String> actionIds = new ArrayList<>();
            for (int index = 0; index < npc.actions().size(); index++) {
                actionIds.add(String.valueOf(index));
            }
            return startsWith(actionIds, args[3]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            return entityTypeSuggestions(args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("type")) {
            return entityTypeSuggestions(args[2]);
        }
        return List.of();
    }

    private List<String> entityTypeSuggestions(String input) {
        return startsWith(Arrays.stream(EntityType.values())
                .filter(type -> type == EntityType.PLAYER || (type.isAlive() && type.isSpawnable()))
                .map(type -> type.name().toLowerCase(Locale.ROOT))
                .toList(), input);
    }

    private List<String> startsWith(List<String> values, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(value);
            }
        }
        return matches;
    }

    private int parseIntOrMinusOne(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
