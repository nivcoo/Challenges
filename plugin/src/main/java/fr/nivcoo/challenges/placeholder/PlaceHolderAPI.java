package fr.nivcoo.challenges.placeholder;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.challenges.Challenge;
import fr.nivcoo.challenges.challenges.ChallengeAmount;
import fr.nivcoo.challenges.challenges.TopReward;
import fr.nivcoo.challenges.config.MainConfig;
import fr.nivcoo.challenges.utils.time.TimePair;
import fr.nivcoo.utilsz.core.config.ConfigManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.*;
import java.util.Map.Entry;

public class PlaceHolderAPI extends PlaceholderExpansion {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final Challenges challenges = Challenges.get();

    @Override
    public @NotNull String getIdentifier() {
        return "challenges";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", challenges.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return challenges.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String identifier) {
        MainConfig config = challenges.cfg();
        Player p = player == null ? null : player.getPlayer();

        return switch (identifier) {
            case "get_classement_score" -> player == null ? "0"
                    : String.valueOf(challenges.getCacheManager().getPlayerScore(player.getUniqueId()));
            case "is_started" -> String.valueOf(challenges.getChallengesManager().isChallengeStarted());
            case "current_challenge_message" -> {
                Challenge challenge = challenges.getChallengesManager().getSelectedChallenge();
                yield challenge != null ? challenge.message() : legacy(config.messages.global.none);
            }
            case "current_challenge_score" -> p != null
                    ? ChallengeAmount.canonical(
                    challenges.getChallengesManager().getScoreOfPlayer(p.getUniqueId()))
                    : "0";
            case "current_challenge_place" -> {
                if (p == null) yield legacy(config.messages.placeholders.currentChallengePlace.none);
                int place = challenges.getChallengesManager().getPlaceOfPlayer(p);
                yield place == 0
                        ? legacy(config.messages.placeholders.currentChallengePlace.none)
                        : String.valueOf(place);
            }
            case "current_challenge_countdown" -> {
                TimePair<Long, String> countdown = challenges.getChallengesManager().getCountdown();
                yield countdown == null
                        ? legacy(config.messages.placeholders.currentChallengeCountdown.stop)
                        : format(config.messages.placeholders.currentChallengeCountdown.started,
                        String.valueOf(countdown.getFirst()), countdown.getSecond());
            }

            case "current_challenge_reward" -> {
                if (p == null) yield legacy(config.messages.global.none);

                int place = challenges.getChallengesManager().getPlaceOfPlayer(p);
                if (place == 0 || !challenges.getChallengesManager().isTopRewardEligible(p.getUniqueId())) {
                    yield legacy(config.messages.global.none);
                }

                Challenge challenge = challenges.getChallengesManager().getSelectedChallenge();
                if (challenge == null) yield legacy(config.messages.global.none);

                yield getRewardMessageForPlace(challenge, place);
            }

            case "current_challenge_for_all_message" -> {
                Challenge challenge = challenges.getChallengesManager().getSelectedChallenge();
                yield challenge != null && challenge.forAllMessage() != null && !challenge.forAllMessage().isBlank()
                        ? challenge.forAllMessage()
                        : legacy(config.messages.global.none);
            }

            default -> {
                if (identifier.startsWith("current_challenge_reward_")) {
                    int place = parsePlace(identifier, "current_challenge_reward_");
                    Challenge challenge = challenges.getChallengesManager().getSelectedChallenge();
                    if (challenge == null || place <= 0)
                        yield legacy(config.messages.global.none);
                    yield getRewardMessageForPlace(challenge, place);
                }

                yield handleTopRequest(identifier);
            }
        };
    }

    private String handleTopRequest(String identifier) {
        if (identifier.startsWith("top_rank_")) {
            int row = parsePlace(identifier, "top_rank_");
            return String.valueOf(challenges.getChallengesManager().getPlayerRankByRow(row));
        }
        if (identifier.startsWith("top_global_rank_")) {
            int row = parsePlace(identifier, "top_global_rank_");
            List<Entry<UUID, Integer>> entries = sortGlobalTop();
            if (row < 1 || row > entries.size()) return "0";
            int score = entries.get(row - 1).getValue();
            int rank = row;
            while (rank > 1 && entries.get(rank - 2).getValue() == score) rank--;
            return String.valueOf(rank + ((row - rank) / 2) * 2);
        }
        if (identifier.startsWith("top_reward_eligible_")) {
            int row = parsePlace(identifier, "top_reward_eligible_");
            Entry<UUID, BigDecimal> entry = challenges.getChallengesManager().getPlayerProgressByPlace(row);
            return String.valueOf(entry != null
                    && challenges.getChallengesManager().isTopRewardEligible(entry.getKey()));
        }
        if (identifier.startsWith("top_reward_")) {
            return challenges.getChallengesManager().getTopRewardMessageByRow(parsePlace(identifier, "top_reward_"));
        }
        if (identifier.startsWith("top_points_")) {
            return String.valueOf(challenges.getChallengesManager().getTopPointsByRow(parsePlace(identifier, "top_points_")));
        }
        if (identifier.startsWith("top_username_")) {
            int place = parsePlace(identifier, "top_username_");
            return challenges.getChallengesManager().getPlayerNameProgressByPlace(place);
        }

        if (identifier.startsWith("top_score_")) {
            int place = parsePlace(identifier, "top_score_");
            return challenges.getChallengesManager().getPlayerCountProgressByPlace(place);
        }

        if (identifier.startsWith("top_global_username_")) {
            int place = parsePlace(identifier, "top_global_username_");
            return getGlobalTopName(place);
        }

        if (identifier.startsWith("top_global_score_")) {
            int place = parsePlace(identifier, "top_global_score_");
            return getGlobalTopCount(place);
        }

        return null;
    }

    private int parsePlace(String input, String prefix) {
        try {
            return Integer.parseInt(input.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String getGlobalTopName(int place) {
        List<Entry<UUID, Integer>> sorted = sortGlobalTop();
        if (place < 1 || place > sorted.size())
            return legacy(challenges.cfg().messages.global.none);
        UUID uuid = sorted.get(place - 1).getKey();
        return challenges.getCacheManager().resolvePlayerName(uuid);
    }


    private String getGlobalTopCount(int place) {
        List<Entry<UUID, Integer>> sorted = sortGlobalTop();
        if (place < 1 || place > sorted.size())
            return "0";
        return String.valueOf(sorted.get(place - 1).getValue());
    }

    private List<Entry<UUID, Integer>> sortGlobalTop() {
        return challenges.getCacheManager().rankingEntries();
    }

    private String getRewardMessageForPlace(Challenge challenge, int place) {
        return challenge.topRewards().stream()
                .filter(r -> r.place() == place)
                .map(TopReward::message)
                .findFirst()
                .orElse(legacy(challenges.cfg().messages.global.none));
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
