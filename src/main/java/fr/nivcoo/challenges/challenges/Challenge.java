package fr.nivcoo.challenges.challenges;

import fr.nivcoo.challenges.challenges.challenges.Types;
import org.bukkit.Material;

import java.util.List;

public record Challenge(Types challengeType, List<String> requirements, String message, boolean countPreviousBlocks,
                        List<TopReward> topRewards, String forAllMessage, List<String> forAllCommands,
                        boolean giveForAllRewardToTop) {

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

    public boolean isInRequirements(String value) {
        return requirements.isEmpty() || requirements.contains(value);
    }

}
