package fr.nivcoo.challenges;

import java.io.File;

import org.bukkit.plugin.java.JavaPlugin;

import fr.nivcoo.challenges.challenges.ChallengesManager;
import fr.nivcoo.challenges.utils.Config;

public class Challenges extends JavaPlugin {

	private static Challenges INSTANCE;
	private Config config;
	private ChallengesManager challengesManager;

	@Override
	public void onEnable() {
		INSTANCE = this;
		File configFile = new File(getDataFolder(), "config.yml");
		if (!configFile.exists()) {
			configFile.getParentFile().mkdirs();
			saveResource("config.yml", false);
		}

		config = new Config(new File(getDataFolder() + File.separator + "config.yml"));
		// messages = new Config(new File("plugins" + File.separator + "ASphere" +
		// File.separator + "messages.yml"));
		// getCommand("asphere").setExecutor(new Commands(this));
		// Bukkit.getPluginManager().registerEvents(new BlockBreak(), this);

		challengesManager = new ChallengesManager();

	}

	@Override
	public void onDisable() {
		getChallengesManager().stopChallengesInterval();
		getChallengesManager().stopActionBarInterval();

	}

	public Config getConfiguration() {
		return config;
	}

	public ChallengesManager getChallengesManager() {
		return challengesManager;
	}

	public static Challenges get() {
		return INSTANCE;
	}

}
