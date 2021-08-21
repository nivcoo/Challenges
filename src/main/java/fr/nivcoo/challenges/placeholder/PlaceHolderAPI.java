package fr.nivcoo.challenges.placeholder;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.challenges.Challenge;
import fr.nivcoo.challenges.utils.Config;
import fr.nivcoo.challenges.utils.time.TimePair;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public class PlaceHolderAPI extends PlaceholderExpansion {

	private Challenges challenges;
	private Config config;

	public PlaceHolderAPI() {
		challenges = Challenges.get();
		config = challenges.getConfiguration();
	}

	@Override
	public String getAuthor() {
		return "nivcoo";
	}

	@Override
	public String getIdentifier() {
		return "challenges";
	}

	@Override
	public String getVersion() {
		return "0.0.1";
	}

	@Override
	public String onRequest(OfflinePlayer player, String identifier) {

		if (identifier.equals("get_classement_score")) {
			int count = challenges.getCacheManager().getPlayerCount(player.getUniqueId());
			return String.valueOf(count);
		} else if (identifier.equals("is_started")) {
			return String.valueOf(challenges.getChallengesManager().isChallengeStarted());
		} else if (identifier.equals("current_challenge_message")) {
			Challenge challenge = challenges.getChallengesManager().getSelectedChallenge();
			if (challenge == null)
				return config.getString("messages.global.none");
			return challenge.getMessage();
		} else if (identifier.equals("current_challenge_count")) {
			Player p = player.getPlayer();
			if (p == null)
				return "0";
			return String.valueOf(challenges.getChallengesManager().getScoreOfPlayer(p));
		} else if (identifier.equals("current_challenge_place")) {
			Player p = player.getPlayer();
			String noneMessage = config.getString("messages.global.none2");
			if (p == null)
				return noneMessage;
			int place = challenges.getChallengesManager().getPlaceOfPlayer(p);
			if (place == 0)
				return noneMessage;
			return String.valueOf(place);
		} else if (identifier.startsWith("top_username_")) {
			int place = Integer.parseInt(identifier.replace("top_username_", ""));
			return challenges.getChallengesManager().getPlayerNameProgressByPlace(place);
		} else if (identifier.startsWith("top_count_")) {
			int place = Integer.parseInt(identifier.replace("top_count_", ""));
			return challenges.getChallengesManager().getPlayerCountProgressByPlace(place);
		} else if (identifier.equals("current_challenge_countdown")) {
			TimePair<Long, String> countdown = challenges.getChallengesManager().getCountdown();
			if (countdown == null)
				return config.getString("messages.global.none");
			return config.getString("messages.placeholders.current_challenge_countdown",
					String.valueOf(countdown.getFirst()), countdown.getSecond());
		}

		return null;
	}

}
