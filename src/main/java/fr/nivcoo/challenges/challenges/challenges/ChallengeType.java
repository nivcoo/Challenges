package fr.nivcoo.challenges.challenges.challenges;

import org.bukkit.entity.Player;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.challenges.Challenge;

public class ChallengeType {
	protected Types type;

	private Challenges challenge = Challenges.get();

	protected void addScoreToPlayer(Player p) {
		challenge.getChallengesManager().addScoreToPlayer(type, p);
	}

	protected boolean checkRequirements() {
		Challenge selectedChallenge = getSeletedChallenge();
		if (selectedChallenge == null || (!selectedChallenge.getAllowedType().equals(type)
				&& (selectedChallenge.getAllowedType().equals(Types.BLOCK_BREAK) && !type.equals(Types.BLOCK_PLACE)
						&& !type.equals(Types.BLOCK_BREAK)
						&& selectedChallenge.getAllowedType().equals(Types.BLOCK_PLACE))))
			return false;
		return true;
	}

	protected Challenge getSeletedChallenge() {
		return challenge.getChallengesManager().getSelectedChallenge();
	}

}
