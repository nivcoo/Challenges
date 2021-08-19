package fr.nivcoo.challenges.challenges;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.challenges.challenges.Types;
import fr.nivcoo.challenges.challenges.challenges.types.BlockBreakType;
import fr.nivcoo.challenges.challenges.challenges.types.BlockPlaceType;
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

	public ChallengesManager() {
		registerEvents();
		registerChallenges();
		selectedChallenge = challengesList.get(0);
		playersProgress = new HashMap<>();
		startChallengeInterval();

	}

	public void registerEvents() {
		registerEvent(new BlockBreakType());
		registerEvent(new BlockPlaceType());
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
			Types type = Types.valueOfIgnoreCase(challengeType);
			Challenge challenge = new Challenge(type);
			challengesList.add(challenge);

			if (type.equals(Types.BLOCK_BREAK) || type.equals(Types.BLOCK_PLACE)) {
				challenge.setRequirementMaterials(config.getStringList(challengePath + ".requirement"));

			}

		}

	}

	public void startChallengeInterval() {
		challengeIntervalRun = true;
		int interval = config.getInt("interval") * 10;
		int timeout = config.getInt("timeout") * 10;
		List<Integer> whitelistedHours = config.getIntegerList("whitelisted_hours");

		Thread t = new Thread(() -> {
			while (challengeIntervalRun && !Thread.interrupted()) {

				try {
					Thread.sleep(interval * 1000);
				} catch (InterruptedException ex) {
				}
				if (!challengeIntervalRun)
					return;
				Calendar rightNow = Calendar.getInstance();
				int hour = rightNow.get(Calendar.HOUR_OF_DAY);
				if (!whitelistedHours.contains(hour))
					return;
				clearProgress();
				startActionBarInterval();
				Bukkit.broadcastMessage("Start");
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

				try {
					Thread.sleep(1000);
				} catch (InterruptedException ex) {
				}
				if (!actionBarIntervalRun)
					return;
				for (Player p : Bukkit.getOnlinePlayers()) {
					if (blacklistedWorld.contains(p.getWorld().getName()))
						continue;
					sendActionBarMessage(p, "Test : " + getScoreOfPlayer(p));
				}
			}

		}, "Challenges actionbar interval Thread");
		t.start();

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

	}

	public int getScoreOfPlayer(Player p) {
		Integer score = playersProgress.get(p);
		return score == null ? 0 : score;
	}

	public void sendTop() {
		String noPlayerMessage = config.getString("messages.chat.no_player");
		List<String> keys = config.getKeys("rewards");
		int place = 0;
		String globalTemplateMessage = "";
		for (Player player : playersProgress.keySet()) {
			place++;

			if (place > keys.size())
				continue;
			String templateMessage = config.getString("messages.chat.top.template", String.valueOf(place),
					player.getName(), String.valueOf(getScoreOfPlayer(player)));

			globalTemplateMessage += templateMessage;

			if (place + 1 <= keys.size())
				globalTemplateMessage += " \n";
			List<String> commands = config.getStringList("rewards." + place);
			for (String c : commands)
				Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), c);

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

		for (Player p : Bukkit.getServer().getOnlinePlayers()) {
			if (playersProgress.size() == 0) {
				p.sendMessage(noPlayerMessage);
			} else {
				p.sendMessage(globalMessage);
			}

		}

	}

	public void addScoreToPlayer(Types type, Player p) {
		if (selectedChallenge == null)
			return;

		if (selectedChallenge.getAllowedType().equals(Types.BLOCK_BREAK) && type.equals(Types.BLOCK_PLACE)
				|| type.equals(Types.BLOCK_BREAK) && selectedChallenge.getAllowedType().equals(Types.BLOCK_PLACE)) {
			removeScoreToPlayer(p);
			return;
		}

		if (!selectedChallenge.getAllowedType().equals(type))
			return;

		Integer score = playersProgress.get(p);
		if (score == null) {
			playersProgress.put(p, 1);

		} else {
			int newScore = score + 1;
			playersProgress.put(p, newScore);

		}

	}

	public void removeScoreToPlayer(Player p) {

		Integer score = playersProgress.get(p);
		if (score != null) {
			int newScore = score - 1;
			playersProgress.put(p, newScore);
		}

	}

	public Challenge getSelectedChallenge() {
		return selectedChallenge;
	}

}
