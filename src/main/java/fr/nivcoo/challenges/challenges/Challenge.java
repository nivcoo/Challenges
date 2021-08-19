package fr.nivcoo.challenges.challenges;

import java.util.List;

import org.bukkit.Material;

import fr.nivcoo.challenges.challenges.challenges.Types;

public class Challenge {

	private Types allowedType;

	private List<String> requirementMaterials;
	private String message;

	public Challenge(Types allowedType) {
		this.allowedType = allowedType;
	}

	public Types getAllowedType() {
		return allowedType;
	}

	public List<String> getRequirementMaterials() {
		return requirementMaterials;
	}

	public String getMessage() {
		return message;
	}

	public void setRequirementMaterials(List<String> requirementMaterials) {
		this.requirementMaterials = requirementMaterials;
	}

	public boolean isInMaterialsRequirement(Material material, int data) {

		for (String materialData : requirementMaterials) {
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
		return false;
	}

	public void setMessage(String message) {
		this.message = message;

	}

}
