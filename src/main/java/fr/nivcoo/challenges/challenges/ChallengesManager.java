package fr.nivcoo.challenges.challenges;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.challenges.challenges.Types;
import fr.nivcoo.challenges.challenges.challenges.types.internal.*;
import fr.nivcoo.challenges.config.MainConfig;
import fr.nivcoo.challenges.messaging.action.ChallengeEndAction;
import fr.nivcoo.challenges.messaging.action.ChallengeScoreAction;
import fr.nivcoo.challenges.messaging.action.ChallengeStartAction;
import fr.nivcoo.challenges.messaging.action.ChallengeStopAction;
import fr.nivcoo.challenges.utils.time.TimePair;
import fr.nivcoo.utilsz.core.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ChallengesManager {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private Challenges challenges;
    private MainConfig config;
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
        config = challenges.cfg();
        interval = config.interval;
        timeout = config.timeout;
        countdownNumber = config.countdownNumber;
        playerNeeded = config.playersNeeded;
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
    }


    private Sound sound(String name) {
        if ("add".equals(name)) return config.sound.add;
        if ("remove".equals(name)) return config.sound.remove;
        return config.sound.messages;
    }


    public void registerEvent(Listener type) {
        Bukkit.getPluginManager().registerEvents(type, challenges);
    }

    public void registerChallenges() {
        challengesList = new ArrayList<>();

        if (config.challenges.isEmpty()) {
            challenges.getLogger().info("No challenges found in the configuration file.");
            return;
        }

        List<TopReward> globalTopRewards = new ArrayList<>();

        for (Map.Entry<String, MainConfig.RewardGroup> entry : config.rewards.top.entrySet()) {
            int place = Integer.parseInt(entry.getKey());
            MainConfig.RewardGroup reward = entry.getValue();
            globalTopRewards.add(new TopReward(place, legacy(reward.message), reward.commands));
        }

        for (MainConfig.ChallengeEntry entry : config.challenges.values()) {
            Types type = entry.challenge;
            List<String> requirements = entry.requirements;
            String message = legacy(entry.message);

            String forAllMessage = legacy(config.rewards.forAll.message);
            List<String> forAllCommands = config.rewards.forAll.commands;
            boolean giveToTop = config.rewards.giveForAllRewardToTop;

            Challenge challenge = new Challenge(type, requirements, message, entry.countPreviousBlocks, globalTopRewards,
                    forAllMessage, forAllCommands, giveToTop);

            challengesList.add(challenge);
        }
    }


    public void startChallengeInterval() {
        stopChallengeTasks();
        if (interval <= 0)
            return;
        List<Integer> whitelistedHours = config.whitelistedHours;
        int countdownNumber = config.countdownNumber;
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

        Challenges.get().getBus().publish(action);

        startCountdownFromBus(c, timeout, countdown, now, true);
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
        List<String> blacklistedWorld = config.blacklistedWorld;
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
        Component titleComponent = LEGACY.deserialize(title);
        Component subtitleComponent = LEGACY.deserialize(subtitle);

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
        String message = format(config.messages.actionBar.running.message,
                selectedChallenge.message(), String.valueOf(getScoreOfPlayer(p.getUniqueId())), String.valueOf(number), type, "{4}");
        int place = getPlaceOfPlayer(p);
        if (place == 0)
            message = message.replace("{4} ", "");
        else {
            message = message.replace("{4}", format(config.messages.actionBar.running.place, String.valueOf(place)));
        }
        sendActionBarMessage(p, message);
    }

    public TimePair<Long, String> getCountdown() {
        if (selectedChallenge == null)
            return null;
        Date date = new Date();
        long now = date.getTime();
        int timeout = config.timeout;
        long s = (timeout) - ((now - startedTimestamp) / 1000);

        return challenges.getTimeUtil().getTimeAndTypeBySecond(s);
    }

    public void sendActionBarMessage(Player p, String message) {
        Component component = LEGACY.deserialize(message);
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
            sendGlobalMessage(legacy(config.messages.chat.noPlayer));
            return;
        }

        String message = buildTopMessage(sorted);
        sendGlobalMessage(message);

        if (selectedChallenge != null && selectedChallenge.forAllMessage() != null) {
            String forAllMessage = selectedChallenge.forAllMessage();
            boolean giveToTop = selectedChallenge.giveForAllRewardToTop();
            List<UUID> eligiblePlayers = sorted.keySet().stream()
                    .filter(integer -> giveToTop || !selectedChallenge.topRewards().stream().map(TopReward::place).toList().contains(getPlaceOfUUID(integer)))
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

    private <T> List<T> topN(Collection<T> src, int n) {
        if (n <= 0) return List.of();
        List<T> list = new ArrayList<>(src);
        return list.subList(0, Math.min(n, list.size()));
    }

    private int topDisplayLimit() {
        if (selectedChallenge == null) return 0;
        List<TopReward> r = Optional.ofNullable(selectedChallenge.topRewards())
                .orElse(Collections.emptyList());
        return r.isEmpty() ? 10 : r.size();
    }


    public String buildTopMessage(Map<UUID, Integer> sorted) {
        List<TopReward> rewards = Optional.ofNullable(selectedChallenge.topRewards()).orElse(Collections.emptyList());
        Map<Integer, TopReward> rewardMap = rewards.stream()
                .collect(Collectors.toMap(TopReward::place, r -> r));

        StringBuilder globalTop = new StringBuilder();

        int place = 0;

        for (Map.Entry<UUID, Integer> entry : topN(sorted.entrySet(), topDisplayLimit())) {
            place++;
            UUID uuid = entry.getKey();
            int score = entry.getValue();

            String baseMessage = format(config.messages.chat.top.template,
                    String.valueOf(place),
                    challenges.getCacheManager().resolvePlayerName(uuid),
                    String.valueOf(score));

            TopReward reward = rewardMap.get(place);

            if (reward != null) {
                baseMessage = baseMessage.replace("{4}", reward.message());

                boolean addAllTop = config.rewards.addAllTopIntoDb;
                int addNumber = addAllTop ? rewards.size() - place + 1 : (place == 1 ? 1 : 0);

                if (addNumber > 0) {
                    String label = (addNumber > 1)
                            ? config.messages.chat.top.templatePoints.points
                            : config.messages.chat.top.templatePoints.point;
                    String pointText = format(config.messages.chat.top.templatePoints.display, String.valueOf(addNumber), label);
                    baseMessage = baseMessage.replace("{3}", pointText);
                } else {
                    baseMessage = baseMessage.replace("{3}", legacy(config.messages.chat.top.templatePoints.defaultValue));
                }

            } else {
                baseMessage = baseMessage.replace("{3}", legacy(config.messages.chat.top.templatePoints.defaultValue));
                baseMessage = baseMessage.replace("{4}", "");
            }

            globalTop.append(baseMessage);
            if (place < sorted.size()) globalTop.append("§r \n");
        }

        StringBuilder finalMessage = new StringBuilder();
        List<Component> format = config.messages.chat.top.message;
        int i = 0;
        for (Component line : format) {
            finalMessage.append(format(line, selectedChallenge.message(), globalTop.toString()));
            if (i++ < format.size() - 1) finalMessage.append("§r \n");
        }

        return finalMessage.toString();
    }


    public void distributeTopRewards(Map<UUID, Integer> sorted) {
        List<TopReward> rewards = Optional.ofNullable(selectedChallenge.topRewards()).orElse(Collections.emptyList());
        Map<Integer, TopReward> rewardMap = rewards.stream()
                .collect(Collectors.toMap(TopReward::place, r -> r));

        boolean addAllTop = config.rewards.addAllTopIntoDb;
        List<String> forAllCommands = Optional.ofNullable(selectedChallenge.forAllCommands()).orElse(Collections.emptyList());
        boolean giveToTop = selectedChallenge.giveForAllRewardToTop();
        String forAllMsg = selectedChallenge.forAllMessage();

        int place = 0;
        for (Map.Entry<UUID, Integer> entry : sorted.entrySet()) {
            place++;
            UUID uuid = entry.getKey();
            Player online = Bukkit.getPlayer(uuid);

            boolean isTop = rewardMap.containsKey(place);

            if (!isTop || giveToTop) {
                for (String cmd : forAllCommands) {
                    sendConsoleCommand(cmd, uuid);
                }
                if (online != null) {
                    online.sendMessage(ConfigManager.fmt(config.messages.rewards.forAll, Map.of("0", forAllMsg)));
                }
            }

            if (!isTop) continue;

            TopReward reward = rewardMap.get(place);
            for (String cmd : reward.commands()) {
                sendConsoleCommand(cmd, uuid);
            }

            if (online != null) {
                online.sendMessage(ConfigManager.fmt(config.messages.rewards.top,
                        Map.of("0", String.valueOf(place), "1", reward.message())));
            }

            if (addAllTop || place == 1) {
                int addPoints = addAllTop ? rewards.size() - place + 1 : 1;
                challenges.getCacheManager().updatePlayerScore(uuid, Math.max(addPoints, 0));
            }
        }
    }


    public void sendConsoleCommand(String command, UUID uuid) {
        if (uuid == null) return;

        String name = challenges.getCacheManager().resolvePlayerName(uuid);
        if (name == null || name.isEmpty()) return;

        String cmd = command.replace("%player%", name);
        Bukkit.getScheduler().runTask(challenges, () ->
                Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), cmd)
        );
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

        Sound sound = sound("add");
        if (sound != null) {
            p.playSound(p.getLocation(), sound, 0.4f, 1.7f);
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
        setScoreToPlayer(p, -number);
        Sound sound = sound("remove");
        if (sound != null) p.playSound(p.getLocation(), sound, .4f, 1.7f);
    }


    public void setScoreToPlayer(Player p, int value) {
        if (!isChallengeStarted()) return;

        UUID uuid = p.getUniqueId();

        int newScore = playersProgress.getOrDefault(uuid, 0) + value;
        playersProgress.put(uuid, newScore);

        Challenges.get().getBus().publish(new ChallengeScoreAction(uuid, newScore));

        sendActionBarMessage(p);
    }


    public Challenge getSelectedChallenge() {
        return selectedChallenge;
    }

    public void sendGlobalMessage(String message) {
        if (selectedChallenge == null) return;
        Sound sound = sound("messages");
        for (Player p : Bukkit.getServer().getOnlinePlayers()) {
            p.sendMessage(message);
            if (sound != null) p.playSound(p.getLocation(), sound, .4f, 1.7f);
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
            Challenges.get().getBus().publish(new ChallengeEndAction());
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
        if (playerProgress == null) return legacy(config.messages.global.none);
        return challenges.getCacheManager().resolvePlayerName(playerProgress.getKey());
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

    public void startCountdownFromBus(Challenge challenge, int timeout, int countdown, long timestamp, boolean isOrigin) {
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
                            format(config.messages.title.countdown.title, String.valueOf(getTimePair.getFirst()), getTimePair.getSecond()),
                            format(config.messages.title.countdown.subtitle, String.valueOf(getTimePair.getFirst()), getTimePair.getSecond()),
                            2, 0, 0
                    );
                    sendActionBarMessage(format(config.messages.actionBar.countdown, String.valueOf(getTimePair.getFirst()), getTimePair.getSecond()));
                    Thread.sleep(1000);
                }

                this.challengeStarted = true;
                this.startedTimestamp = System.currentTimeMillis();

                TimePair<Long, String> getTimePair = challenges.getTimeUtil().getTimeAndTypeBySecond(finalTimeout);
                String message = finalChallenge.message();

                sendTitleMessage(
                        format(config.messages.title.start.title, String.valueOf(getTimePair.getFirst()), getTimePair.getSecond(), message),
                        format(config.messages.title.start.subtitle, String.valueOf(getTimePair.getFirst()), getTimePair.getSecond(), message),
                        config.messages.title.start.stay,
                        config.messages.title.start.fadeInTick,
                        config.messages.title.start.fadeOutTick
                );

                sendGlobalMessage(format(config.messages.chat.startMessage, String.valueOf(getTimePair.getFirst()), getTimePair.getSecond(), message));
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
        Challenges.get().getBus().publish(new ChallengeStopAction());
        stopCurrentChallenge();
    }

    public void endChallengeGlobally() {
        Challenges.get().getBus().publish(new ChallengeEndAction());
        finishChallenge();
    }

    private String format(Component template, String... args) {
        Map<String, Object> vars = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            vars.put(String.valueOf(i), args[i]);
        }
        return legacy(ConfigManager.fmt(template, vars));
    }

    private String legacy(Component component) {
        return LEGACY.serialize(component == null ? Component.empty() : component);
    }

}
