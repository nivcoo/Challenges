package fr.nivcoo.challenges.challenges;

import fr.nivcoo.challenges.challenges.challenges.Types;
import org.bukkit.Material;

import java.util.List;

public class Challenge {

    private Types challengeType;

    private List<String> requirements;
    private String message;
    private boolean countPreviousBlocks;

    public Challenge(Types challengeType) {
        this.challengeType = challengeType;
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

    public void setRequirements(List<String> requirements) {
        this.requirements = requirements;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setCountPreviousBlocks(boolean countPreviousBlocks) {
        this.countPreviousBlocks = countPreviousBlocks;
    }

    public boolean countPreviousBlocks() {
        return countPreviousBlocks;
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
        return requirements.size() == 0;
    }

    public boolean isInRequirements(String value) {
        return requirements.size() == 0 || requirements.contains(value);
    }

}
