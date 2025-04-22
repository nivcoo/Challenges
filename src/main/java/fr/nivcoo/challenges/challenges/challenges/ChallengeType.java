package fr.nivcoo.challenges.challenges.challenges;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.challenges.Challenge;
import org.bukkit.Location;
import org.bukkit.entity.Player;

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

    protected boolean shouldIgnore() {
        Challenge selectedChallenge = getSelectedChallenge();
        return selectedChallenge == null ||
                !selectedChallenge.getChallengeType().equals(type);
    }


    protected Challenge getSelectedChallenge() {
        return challenges.getChallengesManager().getSelectedChallenge();
    }

}
