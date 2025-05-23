package fr.nivcoo.challenges.challenges;

import fr.nivcoo.challenges.challenges.challenges.Types;
import org.bukkit.Material;

import java.util.List;

public class Challenge {

    private final Types challengeType;

    private final List<String> requirements;
    private final String message;
    private final boolean countPreviousBlocks;

    private final List<TopReward> topRewards;

    private final String forAllMessage;
    private final List<String> forAllCommands;
    private final boolean giveForAllRewardToTop;

    public Challenge(Types challengeType, List<String> requirements, String message, boolean countPreviousBlocks,
                     List<TopReward> topRewards,
                     String forAllMessage, List<String> forAllCommands, boolean giveForAllRewardToTop) {
        this.challengeType = challengeType;
        this.requirements = requirements;
        this.message = message;
        this.countPreviousBlocks = countPreviousBlocks;
        this.topRewards = topRewards;
        this.forAllMessage = forAllMessage;
        this.forAllCommands = forAllCommands;
        this.giveForAllRewardToTop = giveForAllRewardToTop;
    }

    public Types getChallengeType() {
        return challengeType;
    }

    public List<String> getRequirements() {
        return requirements;
    }

    public String getMessage() {
        return message;
    }

    public boolean isCountPreviousBlocks() {
        return countPreviousBlocks;
    }

    public String getForAllMessage() {
        return forAllMessage;
    }

    public List<String> getForAllCommands() {
        return forAllCommands;
    }

    public boolean isGiveForAllRewardToTop() {
        return giveForAllRewardToTop;
    }

    public boolean isInMaterialsRequirement(Material material, int data) {
        for (String materialData : requirements) {
            Material m;
            Integer d = null;
            if (materialData.contains(":")) {
                String[] result = materialData.split(":");
                m = Material.valueOf(result[0]);
                d = Integer.parseInt(result[1]);
            } else {
                m = Material.valueOf(materialData);
            }
            if ((d == null || data == d) && material.equals(m))
                return true;
        }
        return requirements.isEmpty();
    }

    public List<TopReward> getTopRewards() {
        return topRewards;
    }

    public boolean isInRequirements(String value) {
        return requirements.isEmpty() || requirements.contains(value);
    }

}
