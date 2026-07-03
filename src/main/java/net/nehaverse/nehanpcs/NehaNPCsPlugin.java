package net.nehaverse.nehanpcs;

import net.nehaverse.nehanpcs.action.ActionExecutor;
import net.nehaverse.nehanpcs.action.CooldownManager;
import net.nehaverse.nehanpcs.command.NpcCommand;
import net.nehaverse.nehanpcs.conversation.ConversationManager;
import net.nehaverse.nehanpcs.listener.NpcInteractListener;
import net.nehaverse.nehanpcs.npc.NpcManager;
import net.nehaverse.nehanpcs.path.PathManager;
import net.nehaverse.nehanpcs.protocol.PlayerNpcService;
import net.nehaverse.nehanpcs.protocol.ProtocolLibSupport;
import net.nehaverse.nehanpcs.protocol.SkinService;
import net.nehaverse.nehanpcs.storage.YamlStorageManager;
import net.nehaverse.nehanpcs.util.MessageUtil;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class NehaNPCsPlugin extends JavaPlugin {
    private MessageUtil messages;
    private YamlStorageManager storageManager;
    private NpcManager npcManager;
    private ConversationManager conversationManager;
    private PathManager pathManager;
    private ProtocolLibSupport protocolLibSupport;
    private PlayerNpcService playerNpcService;
    private SkinService skinService;
    private CooldownManager cooldownManager;
    private ActionExecutor actionExecutor;
    private BukkitTask autosaveTask;
    private BukkitTask lookTask;
    private BukkitTask radiusConversationTask;
    private BukkitTask pathTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);

        messages = new MessageUtil(this);
        storageManager = new YamlStorageManager(this);
        npcManager = new NpcManager(this, storageManager, messages);
        conversationManager = new ConversationManager(this, storageManager, messages);
        pathManager = new PathManager(storageManager);
        protocolLibSupport = new ProtocolLibSupport(this);
        cooldownManager = new CooldownManager();
        actionExecutor = new ActionExecutor(this, messages, cooldownManager);
        skinService = new SkinService(this);
        playerNpcService = new PlayerNpcService(this, protocolLibSupport, npcManager, actionExecutor, conversationManager);
        npcManager.playerNpcService(playerNpcService);
        conversationManager.load();
        pathManager.load();
        npcManager.loadAndSpawnAll();

        NpcCommand npcCommand = new NpcCommand(this, npcManager, conversationManager, pathManager, skinService, playerNpcService, messages);
        PluginCommand command = getCommand("npc");
        if (command != null) {
            command.setExecutor(npcCommand);
            command.setTabCompleter(npcCommand);
        }
        getServer().getPluginManager().registerEvents(new NpcInteractListener(npcManager, actionExecutor, conversationManager), this);
        getServer().getPluginManager().registerEvents(playerNpcService, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, getConfig().getString("server-transfer.channel", "BungeeCord"));

        startAutosave();
        startLookTask();
        startRadiusConversationTask();
        startPathTask();
        getLogger().info("NehaNPCs enabled.");
    }

    @Override
    public void onDisable() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        if (lookTask != null) {
            lookTask.cancel();
        }
        if (radiusConversationTask != null) {
            radiusConversationTask.cancel();
        }
        if (pathTask != null) {
            pathTask.cancel();
        }
        if (npcManager != null) {
            npcManager.save();
            npcManager.despawnAll();
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        messages.reload();
        conversationManager.load();
        pathManager.load();
        npcManager.despawnAll();
        npcManager.loadAndSpawnAll();
        startAutosave();
        startLookTask();
        startRadiusConversationTask();
        startPathTask();
    }

    private void startAutosave() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        long seconds = Math.max(10, getConfig().getLong("settings.autosave-interval-seconds", 60));
        autosaveTask = getServer().getScheduler().runTaskTimer(this, () -> npcManager.save(), seconds * 20L, seconds * 20L);
    }

    private void startLookTask() {
        if (lookTask != null) {
            lookTask.cancel();
        }
        long interval = Math.max(2L, getConfig().getLong("settings.look-at-player.update-interval-ticks", 10L));
        lookTask = getServer().getScheduler().runTaskTimer(this, () -> npcManager.tickLookAtPlayers(), interval, interval);
    }

    private void startRadiusConversationTask() {
        if (radiusConversationTask != null) {
            radiusConversationTask.cancel();
        }
        radiusConversationTask = getServer().getScheduler().runTaskTimer(this, () -> npcManager.tickRadiusConversations(conversationManager), 20L, 20L);
    }

    private void startPathTask() {
        if (pathTask != null) {
            pathTask.cancel();
        }
        long interval = Math.max(1L, getConfig().getLong("settings.path.update-interval-ticks", 2L));
        pathTask = getServer().getScheduler().runTaskTimer(this, () -> npcManager.tickPathMovement(pathManager), interval, interval);
    }
}
