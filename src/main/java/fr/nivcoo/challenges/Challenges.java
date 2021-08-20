package fr.nivcoo.challenges;

import java.io.File;
import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import fr.nivcoo.challenges.cache.CacheManager;
import fr.nivcoo.challenges.challenges.ChallengesManager;
import fr.nivcoo.challenges.command.Commands;
import fr.nivcoo.challenges.placeholder.PlaceHolderAPI;
import fr.nivcoo.challenges.utils.Config;
import fr.nivcoo.challenges.utils.Database;

public class Challenges extends JavaPlugin {

	private static Challenges INSTANCE;
	private Config config;
	private ChallengesManager challengesManager;
	private Database db;
	private CacheManager cacheManager;

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

		cacheManager = new CacheManager();
		Bukkit.getPluginManager().registerEvents(cacheManager, this);

		if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			new PlaceHolderAPI().register();
		}

		config = new Config(new File(getDataFolder() + File.separator + "config.yml"));

		challengesManager = new ChallengesManager();

		getCommand("clgs").setExecutor(new Commands());

	}

	@Override
	public void onDisable() {
		getChallengesManager().stopChallengeTasks();
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

	public static Challenges get() {
		return INSTANCE;
	}

}
