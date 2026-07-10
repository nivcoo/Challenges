package fr.nivcoo.challenges.messaging;

public final class MessageActionName {
    public static final String RANKING_UPDATE = "ranking_update";
    public static final String RANKING_GLOBAL_RESET = "ranking_global_reset";
    public static final String CHALLENGE_START = "challenge_start";
    public static final String CHALLENGE_SCORE = "challenge_score";
    public static final String CHALLENGE_STOP = "challenge_stop";
    public static final String CHALLENGE_END = "challenge_end";

    private MessageActionName() {
    }
}
