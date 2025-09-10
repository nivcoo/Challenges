package fr.nivcoo.challenges;

import fr.nivcoo.challenges.actions.*;
import fr.nivcoo.challenges.adapter.ChallengeAdapter;
import fr.nivcoo.challenges.adapter.TopRewardAdapter;
import fr.nivcoo.challenges.cache.CacheManager;
import fr.nivcoo.challenges.challenges.Challenge;
import fr.nivcoo.challenges.challenges.ChallengesManager;
import fr.nivcoo.challenges.challenges.TopReward;
import fr.nivcoo.challenges.command.commands.*;
import fr.nivcoo.challenges.placeholder.PlaceHolderAPI;
import fr.nivcoo.challenges.utils.DatabaseChallenges;
import fr.nivcoo.challenges.utils.time.TimeUtil;
import fr.nivcoo.utilsz.commands.CommandManager;
import fr.nivcoo.utilsz.config.Config;
import fr.nivcoo.utilsz.database.DatabaseManager;
import fr.nivcoo.utilsz.database.DatabaseType;
import fr.nivcoo.utilsz.redis.RedisAdapterRegistry;
import fr.nivcoo.utilsz.redis.RedisChannelRegistry;
import fr.nivcoo.utilsz.redis.RedisManager;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;

public class Challenges extends JavaPlugin {

    private static Challenges INSTANCE;
    private Config config;
    private ChallengesManager challengesManager;
    private DatabaseChallenges databaseChallenges;
    private CacheManager cacheManager;
    private TimeUtil timeUtil;
    private CommandManager commandManager;
    private RedisChannelRegistry redisChannelRegistry;
    private RedisManager redisManager;

    @Override
    public void onEnable() {
        INSTANCE = this;

        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            saveResource("config.yml", false);
        }

        config = new Config(configFile);

        setupDatabase();
        setupRedis();

        loadTimeUtil();
        loadCacheManager();

        challengesManager = new ChallengesManager();

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PlaceHolderAPI().register();
        }

        commandManager = new CommandManager(this, config, "clgs", "challenges.commands");
        commandManager.addCommand(new StartCMD());
        commandManager.addCommand(new StopCMD());
        commandManager.addCommand(new EndCMD());
        commandManager.addCommand(new StartIntervalCMD());
        commandManager.addCommand(new StopIntervalCMD());
        commandManager.addCommand(new DeleteDatasCMD());
        commandManager.addCommand(new ReloadCMD());
    }

    private void setupDatabase() {
        String type = config.getString("database.type").toLowerCase();
        String dbName = config.getString("database.mysql.database");

        DatabaseType dbType = switch (type) {
            case "mysql" -> DatabaseType.MYSQL;
            case "mariadb" -> DatabaseType.MARIADB;
            default -> DatabaseType.SQLITE;
        };

        String sqlitePath = new File(getDataFolder(), config.getString("database.sqlite.path")).getPath();

        DatabaseManager databaseManager = new DatabaseManager(dbType, config.getString("database.mysql.host"), config.getInt("database.mysql.port"), dbName, config.getString("database.mysql.username"), config.getString("database.mysql.password"), sqlitePath);

        databaseChallenges = new DatabaseChallenges(databaseManager);
        try {
            databaseChallenges.initDB();
        } catch (SQLException e) {
            getLogger().warning("Erreur lors de la création de la table challenge_ranking : " + e.getMessage());
        }
    }

    private void setupRedis() {
        if (config.getBoolean("redis.enabled")) {
            redisManager = new RedisManager(this, config.getString("redis.host"), config.getInt("redis.port"), config.getString("redis.username"), config.getString("redis.password"));

            redisChannelRegistry = redisManager.createRegistry("challenges");
            redisChannelRegistry.register(RankingUpdateAction.class);
            redisChannelRegistry.register(GlobalResetAction.class);
            redisChannelRegistry.register(ChallengeStartAction.class);
            redisChannelRegistry.register(ChallengeScoreAction.class);
            redisChannelRegistry.register(ChallengeStopAction.class);
            redisChannelRegistry.register(ChallengeEndAction.class);

            RedisAdapterRegistry.register(TopReward.class, new TopRewardAdapter());
            RedisAdapterRegistry.register(Challenge.class, new ChallengeAdapter());


            getLogger().info("Redis activé pour Challenges.");
        }
    }

    @Override
    public void onDisable() {
        if (challengesManager != null) {
            challengesManager.disablePlugin();
        }

        if (redisManager != null) redisManager.close();
    }

    public void reload() {

        if (challengesManager != null) {
            challengesManager.disablePlugin();
        }

        HandlerList.unregisterAll(this);
        loadCacheManager();
        config.loadConfig();
        loadTimeUtil();
        challengesManager.reload();
    }

    public void loadCacheManager() {
        cacheManager = new CacheManager();
    }

    public void loadTimeUtil() {
        String timePath = "messages.global.";
        timeUtil = new TimeUtil(config.getString(timePath + "second"), config.getString(timePath + "seconds"), config.getString(timePath + "minute"), config.getString(timePath + "minutes"), config.getString(timePath + "hour"), config.getString(timePath + "hours"));
    }

    public Config getConfiguration() {
        return config;
    }

    public ChallengesManager getChallengesManager() {
        return challengesManager;
    }

    public DatabaseChallenges getDatabaseChallenges() {
        return databaseChallenges;
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

    public RedisChannelRegistry getRedisChannelRegistry() {
        return redisChannelRegistry;
    }


    public static Challenges get() {
        return INSTANCE;
    }
}
