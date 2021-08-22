package fr.nivcoo.challenges.challenges.challenges;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.challenges.Challenge;

public class ChallengeType {
	protected Types type;

	private Challenges challenge = Challenges.get();

	protected void addScoreToPlayer(Player p) {
		addScoreToPlayer(p, null);
	}

	protected void addScoreToPlayer(Player p, Location location) {
		challenge.getChallengesManager().addScoreToPlayer(type, p, location);
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
		return challenge.getChallengesManager().getSelectedChallenge();
	}

}
