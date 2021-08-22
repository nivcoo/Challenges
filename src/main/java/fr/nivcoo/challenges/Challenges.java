package fr.nivcoo.challenges;

import java.io.File;
import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import fr.nivcoo.challenges.cache.CacheManager;
import fr.nivcoo.challenges.challenges.ChallengesManager;
import fr.nivcoo.challenges.command.Commands;
import fr.nivcoo.challenges.placeholder.PlaceHolderAPI;
import fr.nivcoo.challenges.utils.Config;
import fr.nivcoo.challenges.utils.Database;
import fr.nivcoo.challenges.utils.time.TimeUtil;

public class Challenges extends JavaPlugin {

	private static Challenges INSTANCE;
	private Config config;
	private ChallengesManager challengesManager;
	private Database db;
	private CacheManager cacheManager;
	private TimeUtil timeUtil;
	private Commands commands;

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
			} catch (IOException e) {
			}
		}
		db = new Database(database.getPath());
		db.initDB();

		config = new Config(new File(getDataFolder() + File.separator + "config.yml"));

		loadTimeUtil();
		loadCacheManager();

		challengesManager = new ChallengesManager();
		commands = new Commands();

		if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			new PlaceHolderAPI().register();
		}

		getCommand("clgs").setExecutor(commands);

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

	public static Challenges get() {
		return INSTANCE;
	}

}
