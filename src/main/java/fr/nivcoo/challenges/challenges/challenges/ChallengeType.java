package fr.nivcoo.challenges.challenges.challenges;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.challenges.Challenge;

public class ChallengeType {
	protected Types type;

	protected String blacklistMeta = "challenges_blacklist";

	protected Challenges challenges = Challenges.get();

	protected void addScoreToPlayer(Player p) {
		editScoreToPlayer(p, null, false, 1);
	}

	protected void addScoreToPlayer(Player p, int number) {
		editScoreToPlayer(p, null, false, number);
	}

	protected void addScoreToPlayer(Player p, Location location) {
		editScoreToPlayer(p, location, false, 1);
	}

	protected void addScoreToPlayer(Player p, Location location, int number) {
		editScoreToPlayer(p, location, false, number);
	}

	protected void removeScoreToPlayer(Player p) {
		editScoreToPlayer(p, null, true, 1);
	}

	protected void removeScoreToPlayer(Player p, Location location) {
		editScoreToPlayer(p, location, true, 1);
	}

	protected void editScoreToPlayer(Player p, Location location, boolean remove, int number) {
		challenges.getChallengesManager().editScoreToPlayer(type, p, location, remove, number);
	}

	protected boolean checkRequirements() {
		Challenge selectedChallenge = getSeletedChallenge();

		boolean correctType = selectedChallenge != null && (selectedChallenge.getChallengeType().equals(type)
				|| (selectedChallenge.getChallengeType().equals(Types.BLOCK_BREAK) && type.equals(Types.BLOCK_PLACE)
						|| type.equals(Types.BLOCK_BREAK)
								&& selectedChallenge.getChallengeType().equals(Types.BLOCK_PLACE)));
		return correctType;
	}

	protected Challenge getSeletedChallenge() {
		return challenges.getChallengesManager().getSelectedChallenge();
	}

}
