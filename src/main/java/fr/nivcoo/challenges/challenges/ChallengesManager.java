package fr.nivcoo.challenges.challenges;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.actions.ChallengeEndAction;
import fr.nivcoo.challenges.actions.ChallengeScoreAction;
import fr.nivcoo.challenges.actions.ChallengeStartAction;
import fr.nivcoo.challenges.actions.ChallengeStopAction;
import fr.nivcoo.challenges.challenges.challenges.Types;
import fr.nivcoo.challenges.challenges.challenges.types.external.wildtools.WildToolsBuilderType;
import fr.nivcoo.challenges.challenges.challenges.types.internal.*;
import fr.nivcoo.challenges.utils.time.TimePair;
import fr.nivcoo.utilsz.config.Config;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class ChallengesManager {

    private Challenges challenges;
    private Config config;
    private boolean challengeStarted;
    private Thread challengeThread;
    private Thread challengeIntervalThread;
    private Thread actionBarIntervalThread;
    private Timer delayedCancelTaskTimer;
    private List<Challenge> challengesList;
    private Challenge selectedChallenge;

    private LinkedHashMap<UUID, Integer> playersProgress;
    private Long startedTimestamp;
    private HashMap<Location, UUID> blacklistedBlockLocation;

    private int interval;
    private int timeout;
    private int countdownNumber;
    private int playerNeeded;

    private boolean isChallengeOrigin = false;

    public ChallengesManager() {
        init();
    }

    public void init() {
        challenges = Challenges.get();
        config = challenges.getConfiguration();
        interval = config.getInt("interval");
        timeout = config.getInt("timeout");
        countdownNumber = config.getInt("countdown_number");
        playerNeeded = config.getInt("players_needed");
        registerEvents();
        registerChallenges();
        playersProgress = new LinkedHashMap<>();
        blacklistedBlockLocation = new HashMap<>();
        challengeStarted = false;
        startChallengeInterval();
    }

    public void registerEvents() {
        registerEvent(new BlockBreakType());
        registerEvent(new BlockPlaceType());
        registerEvent(new EntityDeathType());
        registerEvent(new FishingType());
        registerEvent(new EnchantAllType());
        registerEvent(new ConsumeType());
        registerEvent(new WildToolsBuilderType());
    }

    public void registerEvent(Listener type) {
        Bukkit.getPluginManager().registerEvents(type, challenges);
    }

    public void registerChallenges() {
        challengesList = new ArrayList<>();

        List<String> keys = config.getKeys("challenges");
        if (keys.isEmpty()) {
            challenges.getLogger().info("No challenges found in the configuration file.");
            return;
        }

        List<TopReward> globalTopRewards = new ArrayList<>();
        List<String> topKeys = config.getKeys("rewards.top");

        for (String placeKey : topKeys) {
            int place = Integer.parseInt(placeKey);
            String rewardMsg = config.getString("rewards.top." + placeKey + ".message");
            List<String> rewardCmds = config.getStringList("rewards.top." + placeKey + ".commands");
            globalTopRewards.add(new TopReward(place, rewardMsg, rewardCmds));
        }

        for (String key : keys) {
            String challengePath = "challenges." + key;
            String challengeType = config.getString(challengePath + ".challenge");
            Types type = Types.valueOf(challengeType.toUpperCase());
            List<String> requirements = config.getStringList(challengePath + ".requirements");
            String message = config.getString(challengePath + ".message");
            boolean countPreviousBlocks = config.getBoolean(challengePath + ".count_previous_blocks");

            String forAllMessage = config.getString("rewards.for_all.message");
            List<String> forAllCommands = config.getStringList("rewards.for_all.commands");
            boolean giveToTop = config.getBoolean("rewards.give_for_all_reward_to_top");

            Challenge challenge = new Challenge(type, requirements, message, countPreviousBlocks, globalTopRewards,
                    forAllMessage, forAllCommands, giveToTop);

            challengesList.add(challenge);
        }
    }


    public void startChallengeInterval() {
        stopChallengeTasks();
        if (interval <= 0)
            return;
        List<Integer> whitelistedHours = config.getIntegerList("whitelisted_hours");
        int countdownNumber = config.getInt("countdown_number");
        challengeIntervalThread = new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    int sleeptime = interval * 1000 - countdownNumber * 1000;
                    if (sleeptime < 0)
                        sleeptime = 0;
                    Thread.sleep(sleeptime);
                    Calendar rightNow = Calendar.getInstance();
                    int hour = rightNow.get(Calendar.HOUR_OF_DAY);
                    if (isChallengeStarted()
                            || (!whitelistedHours.isEmpty() && !whitelistedHours.contains(hour) && interval > 0)
                            || playerNeeded > Bukkit.getServer().getOnlinePlayers().size())
                        continue;
                    startChallenge();

                } catch (InterruptedException ex) {
                    return;
                }
            }
        }, "Challenges Interval Thread");
        challengeIntervalThread.start();
    }

    public void startChallenge() {
        stopCurrentChallenge();

        Challenge c = challengesList.get(new Random().nextInt(challengesList.size()));
        if (c == null)
            return;

        this.selectedChallenge = c;

        long now = System.currentTimeMillis();
        int countdown = countdownNumber;

        ChallengeStartAction action = new ChallengeStartAction(
                c,
                timeout,
                countdown,
                now
        );

        if (Challenges.get().getRedisChannelRegistry() != null) {
            Challenges.get().getRedisChannelRegistry().publish(action);
        }

        startCountdownFromRedis(c, timeout, countdown, now, true);
    }


    public void startFinishTimer(String threadName, int timeout) {
        delayedCancelTaskTimer = new Timer(threadName);
        TimerTask task = new TimerTask() {
            public void run() {
                finishChallenge();
            }
        };
        delayedCancelTaskTimer.schedule(task, 1000L * timeout);
    }

    public void finishChallenge() {
        if (!isChallengeStarted())
            return;
        sendTop();
        stopCurrentChallenge();
    }

    public void startActionBarInterval() {
        List<String> blacklistedWorld = config.getStringList("blacklisted_world");
        actionBarIntervalThread = new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    for (Player p : Bukkit.getServer().getOnlinePlayers()) {
                        if (blacklistedWorld.contains(p.getWorld().getName()))
                            continue;
                        sendActionBarMessage(p);
                    }

                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    return;
                }
            }

        }, "Challenges ActionBar Interval Thread");
        actionBarIntervalThread.start();

    }

    public void sendActionBarMessage(String message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            sendActionBarMessage(p, message);
        }

    }

    public void sendTitleMessage(String title, String subtitle, int time, int fadeInTick, int fadeOutTick) {
        Component titleComponent = LegacyComponentSerializer.legacySection().deserialize(title);
        Component subtitleComponent = LegacyComponentSerializer.legacySection().deserialize(subtitle);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(net.kyori.adventure.title.Title.title(
                    titleComponent,
                    subtitleComponent,
                    net.kyori.adventure.title.Title.Times.times(
                            java.time.Duration.ofMillis(fadeInTick * 50L),
                            java.time.Duration.ofSeconds(time),
                            java.time.Duration.ofMillis(fadeOutTick * 50L)
                    )
            ));
        }
    }

    public void sendActionBarMessage(Player p) {
        TimePair<Long, String> getTimePair = getCountdown();
        if (getTimePair == null)
            return;
        long number = getTimePair.getFirst();
        String type = getTimePair.getSecond();
        if (number < 0) {
            finishChallenge();
            return;
        }
        String runningPath = "messages.action_bar.running.";
        String message = config.getString(runningPath + "message", selectedChallenge.getMessage(),
                String.valueOf(getScoreOfPlayer(p.getUniqueId())), String.valueOf(number), type);
        int place = getPlaceOfPlayer(p);
        if (place == 0)
            message = message.replace("{4}", "");
        else {
            message = message.replace("{4}", config.getString(runningPath + "place", String.valueOf(place)));
        }
        sendActionBarMessage(p, message);
    }

    public TimePair<Long, String> getCountdown() {
        if (selectedChallenge == null)
            return null;
        Date date = new Date();
        long now = date.getTime();
        int timeout = config.getInt("timeout");
        long s = (timeout) - ((now - startedTimestamp) / 1000);

        return challenges.getTimeUtil().getTimeAndTypeBySecond(s);
    }

    public void sendActionBarMessage(Player p, String message) {
        Component component = LegacyComponentSerializer.legacySection().deserialize(message);
        p.sendActionBar(component);
    }

    public int getScoreOfPlayer(UUID uuid) {
        Integer score = playersProgress.get(uuid);
        return score == null ? 0 : score;
    }

    public void sendTop() {
        if (getSelectedChallenge() == null) return;

        Map<UUID, Integer> sorted = getSortPlayersProgress();
        if (sorted.isEmpty()) {
            sendGlobalMessage(config.getString("messages.chat.no_player"));
            return;
        }

        String message = buildTopMessage(sorted);
        sendGlobalMessage(message);

        if (selectedChallenge != null && selectedChallenge.getForAllMessage() != null) {
            String forAllMessage = selectedChallenge.getForAllMessage();
            boolean giveToTop = selectedChallenge.isGiveForAllRewardToTop();
            List<UUID> eligiblePlayers = sorted.keySet().stream()
                    .filter(integer -> giveToTop || !selectedChallenge.getTopRewards().stream().map(TopReward::place).toList().contains(getPlaceOfUUID(integer)))
                    .toList();

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (eligiblePlayers.contains(player.getUniqueId())) {
                    player.sendMessage(forAllMessage);
                }
            }
        }


        if (!isChallengeOrigin) return;

        distributeTopRewards(sorted);
    }


    public String buildTopMessage(Map<UUID, Integer> sorted) {
        List<TopReward> rewards = selectedChallenge.getTopRewards();
        Map<Integer, TopReward> rewardMap = rewards.stream()
                .collect(Collectors.toMap(TopReward::place, r -> r));

        String templatePath = "messages.chat.top.template_points.";
        StringBuilder globalTop = new StringBuilder();

        int place = 0;
        for (Map.Entry<UUID, Integer> entry : sorted.entrySet()) {
            place++;
            UUID uuid = entry.getKey();
            int score = entry.getValue();
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);

            String baseMessage = config.getString("messages.chat.top.template",
                    String.valueOf(place),
                    player.getName(),
                    String.valueOf(score));

            TopReward reward = rewardMap.get(place);

            if (reward != null) {
                baseMessage = baseMessage.replace("{4}", reward.message());

                boolean addAllTop = config.getBoolean("rewards.add_all_top_into_db");
                int addNumber = addAllTop ? rewards.size() - place + 1 : (place == 1 ? 1 : 0);

                if (addNumber > 0) {
                    String label = (addNumber > 1)
                            ? config.getString(templatePath + "points")
                            : config.getString(templatePath + "point");
                    String pointText = config.getString(templatePath + "display",
                            String.valueOf(addNumber), label);
                    baseMessage = baseMessage.replace("{3}", pointText);
                } else {
                    baseMessage = baseMessage.replace("{3}", config.getString(templatePath + "default", ""));
                }

            } else {
                baseMessage = baseMessage.replace("{3}", config.getString(templatePath + "default", ""));
                baseMessage = baseMessage.replace("{4}", "");
            }

            globalTop.append(baseMessage);
            if (place < sorted.size()) globalTop.append("§r \n");
        }

        StringBuilder finalMessage = new StringBuilder();
        List<String> format = config.getStringList("messages.chat.top.message");
        int i = 0;
        for (String line : format) {
            finalMessage.append(line
                    .replace("{0}", selectedChallenge.getMessage())
                    .replace("{1}", globalTop.toString()));
            if (i++ < format.size() - 1) finalMessage.append("§r \n");
        }

        return finalMessage.toString();
    }


    public void distributeTopRewards(Map<UUID, Integer> sorted) {
        List<TopReward> rewards = selectedChallenge.getTopRewards();
        Map<Integer, TopReward> rewardMap = rewards.stream()
                .collect(Collectors.toMap(TopReward::place, r -> r));

        boolean addAllTop = config.getBoolean("rewards.add_all_top_into_db");
        List<String> forAllCommands = selectedChallenge.getForAllCommands();
        boolean giveToTop = selectedChallenge.isGiveForAllRewardToTop();
        String forAllMsg = selectedChallenge.getForAllMessage();

        int place = 0;
        for (Map.Entry<UUID, Integer> entry : sorted.entrySet()) {
            place++;
            UUID uuid = entry.getKey();
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            Player online = player.getPlayer();

            boolean isTop = rewardMap.containsKey(place);

            if (!isTop || giveToTop) {
                for (String cmd : forAllCommands) {
                    sendConsoleCommand(cmd, player);
                }
                if (online != null) {
                    online.sendMessage(config.getString("messages.rewards.for_all", forAllMsg));
                }
            }

            if (!isTop) continue;

            TopReward reward = rewardMap.get(place);
            for (String cmd : reward.commands()) {
                sendConsoleCommand(cmd, player);
            }

            if (online != null) {
                online.sendMessage(config.getString("messages.rewards.top",
                        String.valueOf(place), reward.message()));
            }

            if (addAllTop || place == 1) {
                int addPoints = addAllTop ? rewards.size() - place + 1 : 1;
                challenges.getCacheManager().updatePlayerScore(uuid, Math.max(addPoints, 0));
            }
        }
    }


    public void sendConsoleCommand(String command, OfflinePlayer player) {
        if (player == null || player.getName() == null)
            return;
        Bukkit.getScheduler().runTask(challenges, () -> Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(),
                command.replaceAll("%player%", player.getName())));

    }

    public void editScoreToPlayer(Types type, Player p, Location loc) {
        editScoreToPlayer(type, p, loc, false, 1);
    }

    public void editScoreToPlayer(Types type, Player p, Location loc, boolean remove, int number) {
        if (selectedChallenge == null) return;

        if (remove && loc != null && type == Types.BLOCK_BREAK) {
            addLocationToBlacklist(loc, p);
        }

        if (remove) {
            removeScoreToPlayer(p, number);
            return;
        }

        if (loc != null && locationIsBlacklistedForPlayer(loc, p)) {
            return;
        }

        setScoreToPlayer(p, number);

        String soundName = config.getString("sound.add");
        if (soundName != null) {
            try {
                Sound sound = Sound.valueOf(soundName);
                p.playSound(p.getLocation(), sound, 0.4f, 1.7f);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }


    public boolean locationIsBlacklistedForPlayer(Location loc, Player p) {
        UUID player = blacklistedBlockLocation.get(loc);
        return player != null && player != p.getUniqueId();
    }

    public void addLocationToBlacklist(Location loc, Player p) {
        blacklistedBlockLocation.put(loc, p.getUniqueId());
    }

    public void removeScoreToPlayer(Player p, int number) {
        Sound sound = Sound.valueOf(config.getString("sound.remove"));
        setScoreToPlayer(p, -number);
        p.playSound(p.getLocation(), sound, .4f, 1.7f);
    }

    public void setScoreToPlayer(Player p, int value) {
        if (selectedChallenge == null) return;
        UUID uuid = p.getUniqueId();
        int newScore = playersProgress.getOrDefault(uuid, 0) + value;
        playersProgress.put(uuid, newScore);

        if (Challenges.get().getRedisChannelRegistry() != null) {
            Challenges.get().getRedisChannelRegistry().publish(new ChallengeScoreAction(uuid, newScore));
        }

        sendActionBarMessage(p);
    }


    public Challenge getSelectedChallenge() {
        return selectedChallenge;
    }

    public void sendGlobalMessage(String message) {
        if (selectedChallenge == null) return;
        Sound sound = Sound.valueOf(config.getString("sound.messages"));
        for (Player p : Bukkit.getServer().getOnlinePlayers()) {
            p.sendMessage(message);
            p.playSound(p.getLocation(), sound, .4f, 1.7f);

        }
    }

    public int getPlaceOfUUID(UUID uuid) {
        int place = 0;
        for (UUID p : getSortPlayersProgress().keySet()) {
            place++;
            if (uuid.equals(p))
                return place;
        }
        return 0;
    }

    public void clearProgress() {
        playersProgress = new LinkedHashMap<>();
        blacklistedBlockLocation = new HashMap<>();
        selectedChallenge = null;
        startedTimestamp = null;
        challengeStarted = false;
    }

    public void stopCurrentChallenge() {
        clearProgress();
        if (challengeThread != null)
            challengeThread.interrupt();
        challengeStarted = false;
        stopActionBarInterval();
        cancelDelayedTask();
    }

    public void stopChallengeTasks() {
        if (challengeIntervalThread != null)
            challengeIntervalThread.interrupt();
        stopCurrentChallenge();
    }

    public void stopActionBarInterval() {
        if (actionBarIntervalThread != null)
            actionBarIntervalThread.interrupt();
    }

    public void cancelDelayedTask() {
        if (delayedCancelTaskTimer != null)
            delayedCancelTaskTimer.cancel();
    }

    public void disablePlugin() {
        if (isChallengeStarted() && isChallengeOrigin) {
            if (Challenges.get().getRedisChannelRegistry() != null) {
                Challenges.get().getRedisChannelRegistry().publish(new ChallengeEndAction());
            }
        }

        finishChallenge();
        stopChallengeTasks();
    }


    public boolean isChallengeStarted() {
        return challengeStarted && selectedChallenge != null;
    }

    public LinkedHashMap<UUID, Integer> getSortPlayersProgress() {
        return playersProgress.entrySet().stream().filter(map -> map.getValue() > 0)
                .sorted(Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    public Entry<UUID, Integer> getPlayerProgressByPlace(int place) {
        int i = 0;
        Entry<UUID, Integer> playerProgress = null;
        for (Entry<UUID, Integer> entry : getSortPlayersProgress().entrySet()) {
            i++;
            if (i == place) {
                playerProgress = entry;
                break;
            }

        }
        return playerProgress;
    }

    public String getPlayerNameProgressByPlace(int place) {
        Entry<UUID, Integer> playerProgress = getPlayerProgressByPlace(place);

        if (playerProgress == null)
            return config.getString("messages.global.none");
        else {
            return Bukkit.getOfflinePlayer(playerProgress.getKey()).getName();
        }

    }

    public String getPlayerCountProgressByPlace(int place) {
        Entry<UUID, Integer> playerProgress = getPlayerProgressByPlace(place);

        if (playerProgress == null)
            return "0";
        else
            return String.valueOf(playerProgress.getValue());
    }

    public int getPlaceOfPlayer(Player player) {
        int place = 0;
        for (UUID p : getSortPlayersProgress().keySet()) {
            place++;
            if (player.getUniqueId().equals(p))
                return place;
        }
        return 0;
    }

    public void reload() {
        stopChallengeTasks();
        init();
    }

    public void startCountdownFromRedis(Challenge challenge, int timeout, int countdown, long timestamp, boolean isOrigin) {
        stopCurrentChallenge();

        this.isChallengeOrigin = isOrigin;

        this.selectedChallenge = challenge;
        this.timeout = timeout;

        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        int secondsPassed = (int) (diff / 1000L);
        int remainingCountdown = countdown - secondsPassed;

        if (remainingCountdown < 0)
            remainingCountdown = 0;

        final int finalTimeout = timeout;
        final Challenge finalChallenge = challenge;
        final int finalRemainingCountdown = remainingCountdown;

        this.challengeThread = new Thread(() -> {
            try {
                for (int i = finalRemainingCountdown; i > 0; i--) {
                    TimePair<Long, String> getTimePair = challenges.getTimeUtil().getTimeAndTypeBySecond(i);
                    sendTitleMessage(
                            config.getString("messages.title.countdown.title", String.valueOf(getTimePair.getFirst()), getTimePair.getSecond()),
                            config.getString("messages.title.countdown.subtitle", String.valueOf(getTimePair.getFirst()), getTimePair.getSecond()),
                            2, 0, 0
                    );
                    sendActionBarMessage(config.getString("messages.action_bar.countdown", String.valueOf(getTimePair.getFirst()), getTimePair.getSecond()));
                    Thread.sleep(1000);
                }

                this.challengeStarted = true;
                this.startedTimestamp = System.currentTimeMillis();

                TimePair<Long, String> getTimePair = challenges.getTimeUtil().getTimeAndTypeBySecond(finalTimeout);
                String message = finalChallenge.getMessage();

                sendTitleMessage(
                        config.getString("messages.title.start.title", String.valueOf(getTimePair.getFirst()), getTimePair.getSecond(), message),
                        config.getString("messages.title.start.subtitle", String.valueOf(getTimePair.getFirst()), getTimePair.getSecond(), message),
                        config.getInt("messages.title.start.stay"),
                        config.getInt("messages.title.start.fadeInTick"),
                        config.getInt("messages.title.start.fadeOutTick")
                );

                sendGlobalMessage(config.getString("messages.chat.start_message", String.valueOf(getTimePair.getFirst()), getTimePair.getSecond(), message));
                startActionBarInterval();
                startFinishTimer("Challenges Sync Start Thread", finalTimeout);

            } catch (InterruptedException e) {
                this.challengeStarted = false;
            }
        }, "Challenges Sync Countdown Thread");

        challengeThread.start();
    }

    public void setRemoteScore(UUID uuid, int score) {
        if (!isChallengeStarted())
            return;
        playersProgress.put(uuid, score);
    }

    public void stopChallengeGlobally() {
        if (Challenges.get().getRedisChannelRegistry() != null) {
            Challenges.get().getRedisChannelRegistry().publish(new ChallengeStopAction());
        }
        stopCurrentChallenge();
    }

    public void endChallengeGlobally() {
        if (Challenges.get().getRedisChannelRegistry() != null) {
            Challenges.get().getRedisChannelRegistry().publish(new ChallengeEndAction());
        }
        finishChallenge();
    }


}
