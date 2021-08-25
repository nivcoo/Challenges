package fr.nivcoo.challenges.challenges.challenges;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.challenges.Challenge;

public class ChallengeType {
	protected Types type;

	private Challenges challenge = Challenges.get();

	protected Types getType() {
		return type;
	}

	protected void addScoreToPlayer(Player p) {
		editScoreToPlayer(p, null, false);
	}

	protected void addScoreToPlayer(Player p, Location location) {
		editScoreToPlayer(p, location, false);
	}

	protected void removeScoreToPlayer(Player p) {
		editScoreToPlayer(p, null, true);
	}

	protected void removeScoreToPlayer(Player p, Location location) {
		editScoreToPlayer(p, location, true);
	}

	protected void editScoreToPlayer(Player p, Location location, boolean remove) {
		challenge.getChallengesManager().editScoreToPlayer(getType(), p, location, remove);
	}

	protected boolean checkRequirements() {
		Challenge selectedChallenge = getSeletedChallenge();
		Types type = getType();

		boolean correctType = selectedChallenge != null && (selectedChallenge.getChallengeType().equals(type)
				|| (selectedChallenge.getChallengeType().equals(Types.BLOCK_BREAK) && type.equals(Types.BLOCK_PLACE)
						|| type.equals(Types.BLOCK_BREAK)
								&& selectedChallenge.getChallengeType().equals(Types.BLOCK_PLACE)));
		return correctType;
	}

	protected Challenge getSeletedChallenge() {
		return challenge.getChallengesManager().getSelectedChallenge();
	}

}
