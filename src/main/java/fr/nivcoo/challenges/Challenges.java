package fr.nivcoo.challenges;

import fr.nivcoo.challenges.cache.CacheManager;
import fr.nivcoo.challenges.challenges.ChallengesManager;
import fr.nivcoo.challenges.command.commands.*;
import fr.nivcoo.challenges.placeholder.PlaceHolderAPI;
import fr.nivcoo.challenges.utils.Database;
import fr.nivcoo.challenges.utils.time.TimeUtil;
import fr.nivcoo.utilsz.commands.CommandManager;
import fr.nivcoo.utilsz.config.Config;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class Challenges extends JavaPlugin {

    private static Challenges INSTANCE;
    private Config config;
    private ChallengesManager challengesManager;
    private Database db;
    private CacheManager cacheManager;
    private TimeUtil timeUtil;
    private CommandManager commandManager;

    @Override
    public void onEnable() {
        INSTANCE = this;
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            saveResource("config.yml", false);
        }

        File database = new File(getDataFolder(), "database.db");
        if (!database.exists()) {
            try {
                database.createNewFile();
            } catch (IOException ignored) {
            }
        }
        db = new Database(database.getPath());
        db.initDB();

        config = new Config(configFile);

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

    @Override
    public void onDisable() {
        getChallengesManager().disablePlugin();
    }

    public void reload() {
        HandlerList.unregisterAll(this);
        loadCacheManager();
        config.loadConfig();
        loadTimeUtil();
        challengesManager.reload();
    }

    public void loadCacheManager() {
        cacheManager = new CacheManager();
        Bukkit.getPluginManager().registerEvents(cacheManager, this);
    }

    public void loadTimeUtil() {
        String timePath = "messages.global.";
        timeUtil = new TimeUtil(config.getString(timePath + "second"), config.getString(timePath + "seconds"),
                config.getString(timePath + "minute"), config.getString(timePath + "minutes"),
                config.getString(timePath + "hour"), config.getString(timePath + "hours"));
    }

    public Config getConfiguration() {
        return config;
    }

    public ChallengesManager getChallengesManager() {
        return challengesManager;
    }

    public Database getDatabase() {
        return db;
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

    public static Challenges get() {
        return INSTANCE;
    }

}
