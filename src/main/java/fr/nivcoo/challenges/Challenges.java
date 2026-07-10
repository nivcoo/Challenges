package fr.nivcoo.challenges;

import fr.nivcoo.challenges.cache.CacheManager;
import fr.nivcoo.challenges.challenges.Challenge;
import fr.nivcoo.challenges.challenges.ChallengesManager;
import fr.nivcoo.challenges.challenges.TopReward;
import fr.nivcoo.challenges.command.commands.DeleteDatasCMD;
import fr.nivcoo.challenges.command.commands.EndCMD;
import fr.nivcoo.challenges.command.commands.ReloadCMD;
import fr.nivcoo.challenges.command.commands.StartCMD;
import fr.nivcoo.challenges.command.commands.StartIntervalCMD;
import fr.nivcoo.challenges.command.commands.StopCMD;
import fr.nivcoo.challenges.command.commands.StopIntervalCMD;
import fr.nivcoo.challenges.config.MainConfig;
import fr.nivcoo.challenges.hook.core.HookContext;
import fr.nivcoo.challenges.hook.integration.PlaceholderApiHook;
import fr.nivcoo.challenges.hook.integration.WildStackerHook;
import fr.nivcoo.challenges.hook.integration.WildToolsHook;
import fr.nivcoo.challenges.messaging.action.ChallengeEndAction;
import fr.nivcoo.challenges.messaging.action.ChallengeScoreAction;
import fr.nivcoo.challenges.messaging.action.ChallengeStartAction;
import fr.nivcoo.challenges.messaging.action.ChallengeStopAction;
import fr.nivcoo.challenges.messaging.action.GlobalResetAction;
import fr.nivcoo.challenges.messaging.action.RankingUpdateAction;
import fr.nivcoo.challenges.messaging.adapter.ChallengeAdapter;
import fr.nivcoo.challenges.messaging.adapter.TopRewardAdapter;
import fr.nivcoo.challenges.service.integration.EntityStackService;
import fr.nivcoo.challenges.storage.Database;
import fr.nivcoo.challenges.utils.time.TimeUtil;
import fr.nivcoo.utilsz.core.commands.CommandManager;
import fr.nivcoo.utilsz.core.commands.CommandsConfigProvider;
import fr.nivcoo.utilsz.core.commands.SimpleCommandsConfig;
import fr.nivcoo.utilsz.core.config.ConfigManager;
import fr.nivcoo.utilsz.core.database.DatabaseManager;
import fr.nivcoo.utilsz.core.messaging.BusAdapterRegistry;
import fr.nivcoo.utilsz.core.messaging.MessageBus;
import fr.nivcoo.utilsz.core.messaging.NoopMessageBus;
import fr.nivcoo.utilsz.platform.bukkit.commands.BukkitCommandRegistrar;
import fr.nivcoo.utilsz.platform.bukkit.hook.BukkitHook;
import fr.nivcoo.utilsz.platform.bukkit.hook.BukkitHookRegistry;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Function;

public class Challenges extends JavaPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger(Challenges.class);
    private static Challenges INSTANCE;

    private MainConfig config;
    private ChallengesManager challengesManager;
    private Database database;
    private CacheManager cacheManager;
    private TimeUtil timeUtil;
    private CommandManager commandManager;
    private DatabaseManager dbManager;
    private MessageBus bus;
    private EntityStackService entityStacks;
    private HookContext hookContext;
    private boolean placeholdersRegistered;

    @Override
    public void onEnable() {
        INSTANCE = this;

        config = new ConfigManager(getDataFolder()).load("config.yml", MainConfig.class);

        setupDatabase();
        setupMessaging();
        loadTimeUtil();
        loadCacheManager();
        entityStacks = new EntityStackService();

        challengesManager = new ChallengesManager();
        registerHooks();

        CommandsConfigProvider provider = new SimpleCommandsConfig(
                config.messages.commands.noPermission,
                config.messages.commands.incorrectUsage,
                config.messages.commands.help
        );
        commandManager = new CommandManager(new BukkitCommandRegistrar(this), provider, "clgs", "challenges.commands");
        commandManager.addCommand(new StartCMD());
        commandManager.addCommand(new StopCMD());
        commandManager.addCommand(new EndCMD());
        commandManager.addCommand(new StartIntervalCMD());
        commandManager.addCommand(new StopIntervalCMD());
        commandManager.addCommand(new DeleteDatasCMD());
        commandManager.addCommand(new ReloadCMD());
    }

    private void setupDatabase() {
        dbManager = config.database.createManager(getDataFolder());
        database = new Database(dbManager);
        database.initDB();
    }

    private void setupMessaging() {
        try {
            BusAdapterRegistry.registerBuiltins();
            BusAdapterRegistry.register(TopReward.class, new TopRewardAdapter());
            BusAdapterRegistry.register(Challenge.class, new ChallengeAdapter());
            bus = config.messaging.createBus(runnable -> Bukkit.getScheduler().runTask(this, runnable), LOGGER);
            for (Class<?> action : List.of(
                    RankingUpdateAction.class,
                    GlobalResetAction.class,
                    ChallengeStartAction.class,
                    ChallengeScoreAction.class,
                    ChallengeStopAction.class,
                    ChallengeEndAction.class
            )) {
                bus.register(action);
            }
            bus.start();
        } catch (Throwable throwable) {
            getLogger().warning("Messaging indisponible, Challenges continue en local-only: " + throwable.getMessage());
            bus = new NoopMessageBus();
        }
    }

    @Override
    public void onDisable() {
        if (challengesManager != null) {
            challengesManager.disablePlugin();
        }

        if (bus != null) bus.close();
        if (dbManager != null) dbManager.closeConnection();
        if (hookContext != null) hookContext.cancelTasks();
    }

    public void reload() {
        if (challengesManager != null) {
            challengesManager.disablePlugin();
        }

        HandlerList.unregisterAll(this);
        config = new ConfigManager(getDataFolder()).load("config.yml", MainConfig.class);
        loadTimeUtil();
        loadCacheManager();
        if (entityStacks != null) entityStacks.reset();
        challengesManager.reload();
        registerHooks();
    }

    private void registerHooks() {
        hookContext = new HookContext(this);

        List<Function<HookContext, BukkitHook<HookContext>>> hooks = new ArrayList<>();
        hooks.add(PlaceholderApiHook::new);
        if (Bukkit.getPluginManager().isPluginEnabled("WildStacker")) {
            hooks.add(WildStackerHook::new);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("WildTools")) {
            hooks.add(WildToolsHook::new);
        }

        new BukkitHookRegistry<>(hooks).loadAll(hookContext);
    }

    public void registerPlaceholders() {
        if (placeholdersRegistered) return;
        new fr.nivcoo.challenges.placeholder.PlaceHolderAPI().register();
        placeholdersRegistered = true;
    }

    public void loadCacheManager() {
        cacheManager = new CacheManager();
    }

    public void loadTimeUtil() {
        MainConfig.Global global = config.messages.global;
        timeUtil = new TimeUtil(global.second, global.seconds, global.minute, global.minutes, global.hour, global.hours);
    }

    public MainConfig cfg() {
        return config;
    }

    public ChallengesManager getChallengesManager() {
        return challengesManager;
    }

    public Database getDatabaseChallenges() {
        return database;
    }

    public CacheManager getCacheManager() {
        return cacheManager;
    }

    public TimeUtil getTimeUtil() {
        return timeUtil;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public MessageBus getBus() {
        return bus;
    }

    public EntityStackService entityStacks() {
        return entityStacks;
    }

    public static Challenges get() {
        return INSTANCE;
    }
}
