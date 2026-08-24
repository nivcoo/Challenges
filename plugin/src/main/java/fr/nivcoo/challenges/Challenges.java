package fr.nivcoo.challenges;

import fr.nivcoo.challenges.api.AChallenges;
import fr.nivcoo.challenges.api.ChallengesAPI;
import fr.nivcoo.challenges.api.service.ChallengeReadService;
import fr.nivcoo.challenges.cache.CacheManager;
import fr.nivcoo.challenges.catalog.ChallengeCatalog;
import fr.nivcoo.challenges.challenges.ChallengeRole;
import fr.nivcoo.challenges.challenges.ChallengesManager;
import fr.nivcoo.challenges.command.commands.DeleteDatasCMD;
import fr.nivcoo.challenges.command.commands.EndCMD;
import fr.nivcoo.challenges.command.commands.ReloadCMD;
import fr.nivcoo.challenges.command.commands.StartCMD;
import fr.nivcoo.challenges.command.commands.StartIntervalCMD;
import fr.nivcoo.challenges.command.commands.StopCMD;
import fr.nivcoo.challenges.command.commands.StopIntervalCMD;
import fr.nivcoo.challenges.config.MainConfig;
import fr.nivcoo.challenges.hook.core.HookContext;
import fr.nivcoo.challenges.hook.integration.EdenQuestsHook;
import fr.nivcoo.challenges.hook.integration.PlaceholderApiHook;
import fr.nivcoo.challenges.messaging.action.ChallengeStateAction;
import fr.nivcoo.challenges.messaging.rpc.ChallengeProgressBatchRequest;
import fr.nivcoo.challenges.messaging.rpc.ChallengeStateRequest;
import fr.nivcoo.challenges.placeholder.PlaceHolderAPI;
import fr.nivcoo.challenges.service.tracking.ChallengeTrackingService;
import fr.nivcoo.challenges.service.CachedChallengeReadService;
import fr.nivcoo.challenges.service.ChallengeStateWakeupListener;
import fr.nivcoo.challenges.service.BoundedStorageExecutor;
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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Callable;

public class Challenges extends JavaPlugin implements AChallenges {
    private static final Logger LOGGER = LoggerFactory.getLogger(Challenges.class);
    private static Challenges INSTANCE;

    private MainConfig config;
    private ConfigManager configManager;
    private ChallengeCatalog catalog;
    private ChallengesManager challengesManager;
    private Database database;
    private CacheManager cacheManager;
    private TimeUtil timeUtil;
    private CommandManager commandManager;
    private DatabaseManager dbManager;
    private MessageBus bus;
    private HookContext hookContext;
    private boolean placeholdersRegistered;
    private BoundedStorageExecutor storageExecutor;
    private CachedChallengeReadService readService;

    @Override
    public void onEnable() {
        INSTANCE = this;
        try {
            storageExecutor = new BoundedStorageExecutor("Challenges-Storage", 2_048);
            loadConfiguration();
            storageExecutor.submit(this::setupDatabase).join();
            setupMessaging();
            loadTimeUtil();
            loadCacheManager();

            readService = new CachedChallengeReadService(this);
            challengesManager = new ChallengesManager(catalog, readService::invalidate);
            ChallengesAPI.register(this);
            registerHooks();
            bus.start();
            challengesManager.enable();
            if (config.cluster.role == ChallengeRole.COORDINATOR) {
                bus.publish(ChallengeStateAction.coordinatorOnline(bus.instanceId(), challengesManager.rankingRevision()));
            }
            registerRoleListeners();
            registerCommands();
        } catch (Throwable throwable) {
            getLogger().severe("Challenges cannot start safely: " + throwable.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void loadConfiguration() {
        configManager = new ConfigManager(getDataFolder());
        config = configManager.load("config.yml", MainConfig.class);
        catalog = ChallengeCatalog.load(configManager, config);
    }

    private void registerCommands() {
        List<Component> help = config.messages.commands.help;
        if (config.cluster.role == ChallengeRole.PARTICIPANT) {
            LegacyComponentSerializer serializer = LegacyComponentSerializer.legacySection();
            help = help.stream().filter(line -> {
                String text = serializer.serialize(line).toLowerCase(Locale.ROOT);
                return !text.contains("/clgs start") && !text.contains("/clgs stop")
                        && !text.contains("/clgs end") && !text.contains("/clgs delete_datas");
            }).toList();
        }
        CommandsConfigProvider provider = new SimpleCommandsConfig(
                config.messages.commands.noPermission,
                config.messages.commands.incorrectUsage,
                help
        );
        commandManager = new CommandManager(new BukkitCommandRegistrar(this), provider, "clgs", "challenges.commands");
        if (config.cluster.role == ChallengeRole.COORDINATOR) {
            commandManager.addCommand(new StartCMD());
            commandManager.addCommand(new StopCMD());
            commandManager.addCommand(new EndCMD());
            commandManager.addCommand(new StartIntervalCMD());
            commandManager.addCommand(new StopIntervalCMD());
            commandManager.addCommand(new DeleteDatasCMD());
        }
        commandManager.addCommand(new ReloadCMD());
    }

    private void setupDatabase() {
        dbManager = config.database.createManager(getDataFolder());
        database = new Database(dbManager);
        database.initDB();
    }

    private void setupMessaging() {
        if (!config.messaging.enabled) {
            throw new IllegalStateException("Messaging must be enabled for multi-server Challenges.");
        }
        try {
            BusAdapterRegistry.registerBuiltins();
            bus = config.messaging.createBus(runnable -> Bukkit.getScheduler().runTask(this, runnable), LOGGER);
            if (bus instanceof NoopMessageBus) {
                throw new IllegalStateException("Messaging resolved to NoopMessageBus.");
            }
            List<Class<?>> actions = config.cluster.role == ChallengeRole.COORDINATOR
                    ? List.of(ChallengeStateRequest.class, ChallengeProgressBatchRequest.class)
                    : List.of(ChallengeStateAction.class);
            for (Class<?> action : actions) {
                bus.register(action);
            }
        } catch (Throwable throwable) {
            throw new IllegalStateException("Messaging is unavailable; refusing unsafe local-only mode.", throwable);
        }
    }

    @Override
    public void onDisable() {
        ChallengesAPI.unregister(this);
        if (readService != null) {
            readService.close();
            readService = null;
        }
        if (challengesManager != null) {
            challengesManager.disablePlugin();
        }

        if (bus != null) bus.close();
        if (dbManager != null && storageExecutor != null) {
            try {
                storageExecutor.submit(dbManager::closeConnection).join();
            } catch (RuntimeException exception) {
                getLogger().warning("Unable to close Challenges storage cleanly: " + exception.getMessage());
            }
        }
        if (storageExecutor != null) storageExecutor.close();
        if (hookContext != null) hookContext.cancelTasks();
    }

    public void reload() {
        ConfigManager reloadedManager = new ConfigManager(getDataFolder());
        MainConfig reloadedConfig = reloadedManager.load("config.yml", MainConfig.class);
        ChallengeCatalog reloadedCatalog = ChallengeCatalog.load(reloadedManager, reloadedConfig);
        if (config.cluster.role != reloadedConfig.cluster.role
                || !config.messaging.runtimeSettings().equals(reloadedConfig.messaging.runtimeSettings())
                || !config.database.runtimeSettings(getDataFolder())
                .equals(reloadedConfig.database.runtimeSettings(getDataFolder()))) {
            throw new IllegalStateException("Role, messaging and database changes require a full server restart.");
        }

        long inheritedRankingRevision = challengesManager == null ? 0L : challengesManager.rankingRevision();
        if (challengesManager != null) {
            challengesManager.disablePlugin();
        }

        HandlerList.unregisterAll(this);
        configManager = reloadedManager;
        config = reloadedConfig;
        catalog = reloadedCatalog;
        loadTimeUtil();
        if (hookContext != null) hookContext.cancelTasks();
        challengesManager = new ChallengesManager(catalog, inheritedRankingRevision, readService::invalidate);
        registerHooks();
        challengesManager.enable();
        if (config.cluster.role == ChallengeRole.COORDINATOR) {
            bus.publish(ChallengeStateAction.coordinatorOnline(bus.instanceId(), challengesManager.rankingRevision()));
        }
        registerRoleListeners();
    }

    private void registerRoleListeners() {
        if (config.cluster.role == ChallengeRole.PARTICIPANT) {
            getServer().getPluginManager().registerEvents(new ChallengeStateWakeupListener(this), this);
        }
    }

    private void registerHooks() {
        hookContext = new HookContext(this);

        List<Function<HookContext, BukkitHook<HookContext>>> hooks = new ArrayList<>();
        hooks.add(PlaceholderApiHook::new);
        if (config.cluster.role == ChallengeRole.PARTICIPANT
                && Bukkit.getPluginManager().isPluginEnabled("EdenQuests")) {
            hooks.add(EdenQuestsHook::new);
        }

        new BukkitHookRegistry<>(hooks).loadAll(hookContext);
    }

    public void registerPlaceholders() {
        if (placeholdersRegistered) return;
        new PlaceHolderAPI().register();
        placeholdersRegistered = true;
    }

    public void loadCacheManager() {
        cacheManager = storageExecutor.submit(CacheManager::new).join();
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

    @Override
    public ChallengeReadService read() {
        return readService;
    }

    public <T> CompletableFuture<T> submitStorage(Callable<T> operation) {
        return storageExecutor.submit(operation);
    }

    public CompletableFuture<Void> submitStorage(BoundedStorageExecutor.CheckedRunnable operation) {
        return storageExecutor.submit(operation);
    }

    public CompletableFuture<Map<UUID, Integer>> loadRankingAsync() {
        return submitStorage(database::getAllPlayersScoreStrict);
    }

    public CompletableFuture<Void> applyRankingPointsAsync(Map<UUID, Integer> additions) {
        Map<UUID, Integer> immutableAdditions = additions == null ? Map.of() : Map.copyOf(additions);
        CompletableFuture<Void> completion = new CompletableFuture<>();
        submitStorage(() -> database.addPlayerScores(immutableAdditions)).whenComplete((updates, error) -> {
            if (error != null) {
                completion.completeExceptionally(error);
                return;
            }
            runOnMain(() -> {
                cacheManager.applyRankingUpdates(updates);
                if (challengesManager != null && challengesManager.role() == ChallengeRole.COORDINATOR) {
                    challengesManager.announceRankingChanged();
                }
                completion.complete(null);
            }, completion);
        });
        return completion;
    }

    public CompletableFuture<Void> resetRankingAsync() {
        if (!Bukkit.isPrimaryThread()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Ranking reset must start on main thread."));
        }
        if (challengesManager != null && challengesManager.role() == ChallengeRole.COORDINATOR) {
            challengesManager.stopChallengeGlobally();
        }
        CompletableFuture<Void> completion = new CompletableFuture<>();
        submitStorage(database::clearDBStrict).whenComplete((ignored, error) -> {
            if (error != null) {
                completion.completeExceptionally(error);
                return;
            }
            runOnMain(() -> {
                cacheManager.clearRanking();
                if (challengesManager != null && challengesManager.role() == ChallengeRole.COORDINATOR) {
                    challengesManager.announceRankingChanged();
                }
                completion.complete(null);
            }, completion);
        });
        return completion;
    }

    private void runOnMain(Runnable operation, CompletableFuture<?> completion) {
        try {
            Bukkit.getScheduler().runTask(this, () -> {
                try {
                    operation.run();
                } catch (Throwable throwable) {
                    completion.completeExceptionally(throwable);
                }
            });
        } catch (RuntimeException exception) {
            completion.completeExceptionally(exception);
        }
    }

    public void bindTrackingService(ChallengeTrackingService service) {
        challengesManager.bindTrackingService(service);
    }

    public static Challenges get() {
        return INSTANCE;
    }
}
