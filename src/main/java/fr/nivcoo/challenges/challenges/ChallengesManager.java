package fr.nivcoo.challenges.challenges;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.challenges.challenges.Types;
import fr.nivcoo.challenges.challenges.challenges.types.external.wildtools.WildToolsBuilderType;
import fr.nivcoo.challenges.challenges.challenges.types.internal.*;
import fr.nivcoo.challenges.utils.Config;
import fr.nivcoo.challenges.utils.time.TimePair;
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

        if (Bukkit.getPluginManager().isPluginEnabled("WildTools"))
            registerEvent(new WildToolsBuilderType());
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

            challenge.setCountPreviousBlocks(config.getBoolean(challengePath + ".count_previous_blocks"));
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
                            || (whitelistedHours.size() > 0 && !whitelistedHours.contains(hour) && interval > 0)
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

        String countdownMessageActionBar = config.getString("messages.action_bar.countdown");
        String countdownMessageTitle = config.getString("messages.title.countdown.title");
        String countdownMessageSubtitle = config.getString("messages.title.countdown.subtitle");
        String threadName = "Challenges Start Thread";
        challengeThread = new Thread(() -> {
            try {

                for (int i = 0; i < countdownNumber; i++) {
                    int timeleft = countdownNumber - i;
                    TimePair<Long, String> getTimePair = challenges.getTimeUtil().getTimeAndTypeBySecond(timeleft);

                    long number = getTimePair.getFirst();

                    String type = getTimePair.getSecond();

                    sendActionBarMessage(
                            countdownMessageActionBar.replace("{0}", String.valueOf(number)).replace("{1}", type));
                    sendTitleMessage(countdownMessageTitle.replace("{0}", String.valueOf(number)).replace("{1}", type),
                            countdownMessageSubtitle.replace("{0}", String.valueOf(number)).replace("{1}", type), 2, 0,
                            0);

                    Thread.sleep(1000);
                }

                challengeStarted = true;

                Random rand = new Random();

                TimePair<Long, String> getTimePair = challenges.getTimeUtil().getTimeAndTypeBySecond(timeout);

                long number = getTimePair.getFirst();

                String type = getTimePair.getSecond();
                selectedChallenge = challengesList.get(rand.nextInt(challengesList.size()));
                sendTitleMessage(
                        config.getString("messages.title.start.title", String.valueOf(number), type,
                                selectedChallenge.getMessage()),
                        config.getString("messages.title.start.subtitle", String.valueOf(number), type,
                                selectedChallenge.getMessage()),
                        config.getInt("messages.title.start.stay"), config.getInt("messages.title.start.fadeInTick"),
                        config.getInt("messages.title.start.fadeOutTick"));
                sendGlobalMessage(config.getString("messages.chat.start_message", String.valueOf(number), type,
                        selectedChallenge.getMessage()));
                Date date = new Date();
                startedTimestamp = date.getTime();
                startActionBarInterval();

                startFinishTimer(threadName, timeout);

            } catch (InterruptedException ex) {
                challengeStarted = false;
            }

        }, threadName);
        challengeThread.start();

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
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.resetTitle();
            p.sendTitle(title, subtitle, fadeInTick, time * 20, fadeOutTick);
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
        p.sendActionBar(message);
    }

    public int getScoreOfPlayer(UUID uuid) {
        Integer score = playersProgress.get(uuid);
        return score == null ? 0 : score;
    }

    public void sendTop() {
        if (getSelectedChallenge() == null)
            return;
        String noPlayerMessage = config.getString("messages.chat.no_player");
        List<String> keys = config.getKeys("rewards.top");
        int place = 0;
        StringBuilder globalTemplateMessage = new StringBuilder();
        boolean sendTop = false;
        List<String> commandsForAll = config.getStringList("rewards.for_all");
        boolean giveForAllRewardToTop = config.getBoolean("rewards.give_for_all_reward_to_top");
        Map<UUID, Integer> filteredPlayersProgress = getSortPlayersProgress();
        Set<UUID> filteredPlayers = filteredPlayersProgress.keySet();
        for (UUID uuid : filteredPlayersProgress.keySet()) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            Player p = offlinePlayer.getPlayer();
            place++;
            int score = getScoreOfPlayer(uuid);
            int numberOfWinner = keys.size();
            boolean outOfTop = place > numberOfWinner;
            for (String c : commandsForAll) {
                if (!outOfTop && giveForAllRewardToTop)
                    sendConsoleCommand(c, offlinePlayer);
                if (outOfTop && p != null)
                    p.sendMessage(
                            config.getString("messages.rewards.for_all", config.getString("rewards.for_all.message")));
            }
            if (outOfTop)
                continue;
            sendTop = true;

            String rewardsTopPath = "rewards.top." + place;
            List<String> commandsTop = config.getStringList(rewardsTopPath + ".commands");
            String messageTop = config.getString(rewardsTopPath + ".message");
            for (String c : commandsTop) {
                sendConsoleCommand(c, offlinePlayer);
                if (p != null)
                    p.sendMessage(config.getString("messages.rewards.top", String.valueOf(place), messageTop));
            }
            String templateMessage = config.getString("messages.chat.top.template", String.valueOf(place),
                    offlinePlayer.getName(), String.valueOf(score));

            boolean addAllTop = config.getBoolean("rewards.add_all_top_into_db");
            String templatePointPath = "messages.chat.top.template_points.";
            if (addAllTop || place == 1) {
                int addNumber = 1;
                if (addAllTop)
                    addNumber = numberOfWinner - place + 1;
                String point = config.getString(templatePointPath + "point");
                String points = config.getString(templatePointPath + "points");
                String type = point;
                if (addNumber > 1)
                    type = points;
                String pointMessage = config.getString(templatePointPath + "display", String.valueOf(addNumber), type);
                templateMessage = templateMessage.replace("{3}", pointMessage);
                challenges.getCacheManager().updatePlayerCount(uuid, addNumber);
            } else {
                templateMessage = templateMessage.replace("{3}", config.getString(templatePointPath + "default"));
            }

            templateMessage = templateMessage.replace("{4}", messageTop);

            globalTemplateMessage.append(templateMessage);

            if (place + 1 <= numberOfWinner && filteredPlayers.size() > place)
                globalTemplateMessage.append("§r \n");

        }
        List<String> globalMessagesList = config.getStringList("messages.chat.top.message");
        StringBuilder globalMessage = new StringBuilder();
        int i = 0;
        for (String m : globalMessagesList) {
            globalMessage.append(m.replace("{0}", getSelectedChallenge().getMessage()).replace("{1}",
                    globalTemplateMessage.toString()));
            if (globalMessagesList.size() - 1 != i)
                globalMessage.append("§r \n");
            i++;
        }

        if (sendTop) {
            sendGlobalMessage(globalMessage.toString());
        } else {
            sendGlobalMessage(noPlayerMessage);
        }

    }

    public void sendConsoleCommand(String command, OfflinePlayer player) {
        Bukkit.getScheduler().runTask(challenges, () -> Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(),
                command.replaceAll("%player%", player.getName())));

    }

    public void editScoreToPlayer(Types type, Player p, Location loc) {
        editScoreToPlayer(type, p, loc, false, 1);
    }

    public void editScoreToPlayer(Types type, Player p, Location loc, boolean remove, int number) {
        if (selectedChallenge == null)
            return;
        Types selectedChallengeType = selectedChallenge.getChallengeType();

        if (remove || (selectedChallengeType.equals(Types.BLOCK_BREAK) && type.equals(Types.BLOCK_PLACE)
                || type.equals(Types.BLOCK_BREAK) && selectedChallengeType.equals(Types.BLOCK_PLACE))) {
            if (loc != null && selectedChallengeType.equals(Types.BLOCK_BREAK))
                addLocationToBlacklist(loc, p);
            removeScoreToPlayer(p, number);
            return;
        }

        if (!selectedChallengeType.equals(type) || (loc != null && locationIsBlacklistedForPlayer(loc, p)))
            return;
        Sound sound = Sound.valueOf(config.getString("sound.add"));

        setScoreToPlayer(p, number);

        p.playSound(p.getLocation(), sound, .4f, 1.7f);
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
        Integer score = playersProgress.get(p.getUniqueId());
        if (score == null) {
            playersProgress.put(p.getUniqueId(), value);

        } else {
            int newScore = score + value;
            playersProgress.put(p.getUniqueId(), newScore);
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
        finishChallenge();
        stopChallengeTasks();
    }

    public boolean isChallengeStarted() {
        return challengeStarted;
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

}
