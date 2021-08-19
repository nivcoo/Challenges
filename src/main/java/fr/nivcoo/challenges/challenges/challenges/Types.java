package fr.nivcoo.challenges.challenges.challenges;

public enum Types {

	BLOCK_BREAK("block-break"), BLOCK_PLACE("block-place");

	private String challengeType;

	private Types(String challengeType) {
		this.challengeType = challengeType;
	}

	public String getChallengeType() {
		return this.challengeType;
	}

	public static Types valueOfIgnoreCase(String type) throws IllegalArgumentException {
		try {
			return valueOf(type.toUpperCase());
		} catch (IllegalArgumentException e) {
			return null;
		}

	}

}
