package fr.nivcoo.challenges.challenges;

import java.util.List;

import org.bukkit.Material;

import fr.nivcoo.challenges.challenges.challenges.Types;

public class Challenge {

	private Types challengeType;

	private List<String> requirements;
	private String message;

	public Challenge(Types allowedType) {
		this.challengeType = allowedType;
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

	public boolean isInMaterialsRequirement(Material material, int data) {
		for (String materialData : requirements) {
			Material m = null;
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
