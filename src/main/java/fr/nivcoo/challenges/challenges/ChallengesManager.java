package fr.nivcoo.challenges.challenges;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.challenges.challenges.Types;
import fr.nivcoo.challenges.challenges.challenges.types.BlockBreakType;
import fr.nivcoo.challenges.challenges.challenges.types.BlockPlaceType;
import fr.nivcoo.challenges.challenges.challenges.types.ConsumeType;
import fr.nivcoo.challenges.challenges.challenges.types.EnchantAllType;
import fr.nivcoo.challenges.challenges.challenges.types.EntityDeathType;
import fr.nivcoo.challenges.challenges.challenges.types.FishingType;
import fr.nivcoo.challenges.utils.Config;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class ChallengesManager {

	private Challenges challenges = Challenges.get();
	private Config config = challenges.getConfiguration();
	private boolean challengeIntervalRun;
	private boolean actionBarIntervalRun;
	private int delayedCancelTaskID;
	private List<Challenge> challengesList;
	private Challenge selectedChallenge;

	private HashMap<Player, Integer> playersProgress;
	private Long startedTimestamp;

	public ChallengesManager() {
		registerEvents();
		registerChallenges();
		playersProgress = new HashMap<>();
		startChallengeInterval();

	}

	public void registerEvents() {
		registerEvent(new BlockBreakType());
		registerEvent(new BlockPlaceType());
		registerEvent(new EntityDeathType());
		registerEvent(new FishingType());
		registerEvent(new EnchantAllType());
		registerEvent(new ConsumeType());
	}

	public void registerEvent(Listener type) {
		Bukkit.getPluginManager().registerEvents(type, challenges);
	}

	public void registerChallenges() {
		challengesList = new ArrayList<>();

		List<String> keys = config.getKeys("challenges");
		for (String key : keys) {

			String challengePath = "challenges." + key;
			String challengeType = config.getString(challengePath + ".challenge");
			Types type = Types.valueOf(challengeType.toUpperCase());
			Challenge challenge = new Challenge(type);
			challengesList.add(challenge);

			challenge.setRequirements(config.getStringList(challengePath + ".requirement"));

			challenge.setMessage(config.getString(challengePath + ".message"));
		}

	}

	public void startChallengeInterval() {
		challengeIntervalRun = true;
		int interval = config.getInt("interval");
		int timeout = config.getInt("timeout");
		List<Integer> whitelistedHours = config.getIntegerList("whitelisted_hours");
		String countdownMessage = config.getString("messages.action_bar.countdown");
		String second = config.getString("messages.global.second");
		String seconds = config.getString("messages.global.seconds");
		Thread t = new Thread(() -> {
			while (challengeIntervalRun && !Thread.interrupted()) {
				try {
					int countdownNumber = 5;
					Thread.sleep(interval * 1000 - countdownNumber * 1000);
					if (startedTimestamp != null)
						continue;
					Calendar rightNow = Calendar.getInstance();
					int hour = rightNow.get(Calendar.HOUR_OF_DAY);
					if (!whitelistedHours.contains(hour))
						return;
					for (int i = 0; i < countdownNumber; i++) {
						if (!challengeIntervalRun)
							return;
						int timeleft = countdownNumber - i;
						String secondSelect = (timeleft > 1) ? seconds : second;
						sendActionBarMessage(
								countdownMessage.replace("{0}", String.valueOf(timeleft)).replace("{1}", secondSelect));
						Thread.sleep(1000);
					}

				} catch (InterruptedException ex) {
				}
				if (!challengeIntervalRun)
					return;

				clearProgress();

				Random rand = new Random();
				selectedChallenge = challengesList.get(rand.nextInt(challengesList.size()));

				sendGlobalMessage(config.getString("messages.chat.start_message", String.valueOf(timeout),
						selectedChallenge.getMessage()));
				Date date = new Date();
				startedTimestamp = date.getTime();
				startActionBarInterval();
				delayedCancelTaskID = Bukkit.getScheduler().scheduleSyncDelayedTask(challenges, new Runnable() {
					@Override
					public void run() {
						sendTop();
						clearProgress();

					}
				}, 20 * timeout);

			}
		}, "Challenges interval Thread");
		t.start();
	}

	public void stopChallengesInterval() {
		challengeIntervalRun = false;
		Bukkit.getScheduler().cancelTask(delayedCancelTaskID);

	}

	public void startActionBarInterval() {
		actionBarIntervalRun = true;

		List<String> blacklistedWorld = config.getStringList("blacklisted_world");

		Thread t = new Thread(() -> {
			while (actionBarIntervalRun && !Thread.interrupted()) {
				if (!actionBarIntervalRun)
					return;
				for (Player p : Bukkit.getOnlinePlayers()) {
					if (blacklistedWorld.contains(p.getWorld().getName()))
						continue;
					sendActionBarMessage(p);
				}
				try {
					Thread.sleep(1000);
				} catch (InterruptedException ex) {
				}
			}

		}, "Challenges actionbar interval Thread");
		t.start();

	}

	public void sendActionBarMessage(String message) {
		for (Player p : Bukkit.getOnlinePlayers()) {
			sendActionBarMessage(p, message);
		}

	}

	public void sendActionBarMessage(Player p) {
		if (selectedChallenge == null)
			return;

		Date date = new Date();
		long now = date.getTime();
		int timeout = config.getInt("timeout");
		long s = (timeout) - ((now - startedTimestamp) / 1000);
		long m = Math.round(s / 60);

		long number = 0;

		String type = "";

		String second = config.getString("messages.global.second");
		String seconds = config.getString("messages.global.seconds");
		String minute = config.getString("messages.global.minute");
		String minutes = config.getString("messages.global.minutes");
		if (m >= 1) {
			number = m;
			type = minute;
			if (m > 1)
				type = minutes;

		} else {
			number = s;
			type = second;
			if (s > 1)
				type = seconds;

		}

		String message = config.getString("messages.action_bar.message", selectedChallenge.getMessage(),
				String.valueOf(getScoreOfPlayer(p)), String.valueOf(number), type);

		sendActionBarMessage(p, message);

	}

	public void sendActionBarMessage(Player p, String message) {
		p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
	}

	public void stopActionBarInterval() {
		actionBarIntervalRun = false;
	}

	public void clearProgress() {
		stopActionBarInterval();
		playersProgress = new HashMap<>();
		selectedChallenge = null;
		startedTimestamp = null;

	}

	public int getScoreOfPlayer(Player p) {
		Integer score = playersProgress.get(p);
		return score == null ? 0 : score;
	}

	public void sendTop() {
		String noPlayerMessage = config.getString("messages.chat.no_player");
		List<String> keys = config.getKeys("rewards.top");
		int place = 0;
		String globalTemplateMessage = "";
		boolean sendTop = false;
		List<String> commandsForAll = config.getStringList("rewards.for_all");
		boolean giveForAllRewardToTop = config.getBoolean("rewards.give_for_all_reward_to_top");
		Map<Player, Integer> filteredPlayersProgress = playersProgress.entrySet().stream()
				.filter(map -> map.getValue() > 0)
				.collect(Collectors.toMap(map -> map.getKey(), map -> map.getValue()));
		for (Player player : filteredPlayersProgress.keySet()) {
			place++;
			int score = getScoreOfPlayer(player);
			boolean outOfTop = place > keys.size();
			for (String c : commandsForAll) {
				if (!outOfTop && giveForAllRewardToTop)
					sendConsoleCommand(c, player);
				if (outOfTop)
					player.sendMessage(
							config.getString("messages.rewards.for_all", config.getString("rewards.for_all.message")));
			}
			if (outOfTop)
				continue;
			sendTop = true;
			String templateMessage = config.getString("messages.chat.top.template", String.valueOf(place),
					player.getName(), String.valueOf(score));

			globalTemplateMessage += templateMessage;

			if (place + 1 <= keys.size())
				globalTemplateMessage += " \n";
			String rewardsTopPath = "rewards.top." + place;
			List<String> commandsTop = config.getStringList(rewardsTopPath + ".commands");
			String messageTop = config.getString(rewardsTopPath + ".message");
			for (String c : commandsTop) {
				sendConsoleCommand(c, player);
				player.sendMessage(config.getString("messages.rewards.top", String.valueOf(place), messageTop));
			}

			boolean addAllTop = config.getBoolean("rewards.add_all_top_into_db");
			if (addAllTop || place == 1) {
				int addNumber = 1;
				if (addAllTop)
					addNumber = keys.size() - place + 1;
				challenges.getCacheManager().updatePlayerCount(player, addNumber);
			}

		}
		List<String> globalMessagesList = config.getStringList("messages.chat.top.message");
		String globalMessage = "";
		int i = 0;
		for (String m : globalMessagesList) {
			globalMessage += m.replace("{0}", globalTemplateMessage);
			if (globalMessagesList.size() - 1 != i)
				globalMessage += " \n";
			i++;
		}

		if (sendTop) {
			sendGlobalMessage(globalMessage);
		} else {
			sendGlobalMessage(noPlayerMessage);
		}

	}

	public void sendConsoleCommand(String command, Player p) {
		Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(),
				command.replaceAll("%player%", p.getName()));

	}

	public void addScoreToPlayer(Types type, Player p) {
		if (selectedChallenge == null)
			return;

		if (selectedChallenge.getChallengeType().equals(Types.BLOCK_BREAK) && type.equals(Types.BLOCK_PLACE)
				|| type.equals(Types.BLOCK_BREAK) && selectedChallenge.getChallengeType().equals(Types.BLOCK_PLACE)) {
			removeScoreToPlayer(p);
			return;
		}

		if (!selectedChallenge.getChallengeType().equals(type))
			return;
		Sound sound = Sound.valueOf(config.getString("sound.add"));
		setScoreToPlayer(p, 1);
		p.playSound(p.getLocation(), sound, .4f, 1.7f);
	}

	public void removeScoreToPlayer(Player p) {
		Sound sound = Sound.valueOf(config.getString("sound.remove"));
		setScoreToPlayer(p, -1);
		p.playSound(p.getLocation(), sound, .4f, 1.7f);
	}

	public void setScoreToPlayer(Player p, int value) {

		Integer score = playersProgress.get(p);
		if (score == null) {
			playersProgress.put(p, value);

		} else {
			int newScore = score + value;
			playersProgress.put(p, newScore);
		}

		sendActionBarMessage(p);
	}

	public Challenge getSelectedChallenge() {
		return selectedChallenge;
	}

	public void sendGlobalMessage(String message) {
		Sound sound = Sound.valueOf(config.getString("sound.messages"));
		for (Player p : Bukkit.getServer().getOnlinePlayers()) {
			p.sendMessage(message);
			p.playSound(p.getLocation(), sound, .4f, 1.7f);

		}
	}

}
