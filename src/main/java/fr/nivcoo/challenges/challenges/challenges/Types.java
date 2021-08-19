package fr.nivcoo.challenges.challenges.challenges;

public enum Types {

	BLOCK_BREAK("block_break"), BLOCK_PLACE("block_place"), ENTITY_DEATH("entity_death"), FISHING("fishing"),
	ENCHANT_ALL("enchant_all");

	private String challengeType;

	private Types(String challengeType) {
		this.challengeType = challengeType;
	}

	public String getChallengeType() {
		return this.challengeType;
	}


}
