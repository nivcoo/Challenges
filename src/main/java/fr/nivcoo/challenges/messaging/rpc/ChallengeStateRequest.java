package fr.nivcoo.challenges.messaging.rpc;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.challenges.ChallengeRole;
import fr.nivcoo.challenges.challenges.ChallengeRunPhase;
import fr.nivcoo.challenges.messaging.MessageActionName;
import fr.nivcoo.challenges.messaging.response.ChallengeStateSnapshot;
import fr.nivcoo.utilsz.core.messaging.BusAction;
import fr.nivcoo.utilsz.core.messaging.RpcMessage;

import java.util.UUID;

@BusAction(value = MessageActionName.CHALLENGE_STATE_REQUEST,
        response = ChallengeStateSnapshot.class, runOnMainThread = true)
public record ChallengeStateRequest(String targetAuthorityInstanceId, String participantInstanceId,
                                    int onlinePlayerCount,
                                    UUID knownRunId, long knownGeneration,
                                    ChallengeRunPhase knownPhase, long knownStateRevision,
                                    long knownRankingRevision, UUID drainRunId,
                                    long drainGeneration) implements RpcMessage {
    public ChallengeStateRequest {
        targetAuthorityInstanceId = targetAuthorityInstanceId == null ? "" : targetAuthorityInstanceId;
        participantInstanceId = participantInstanceId == null ? "" : participantInstanceId;
    }

    @Override
    public Object handle() {
        if (targetAuthorityInstanceId.length() > 128 || participantInstanceId.isBlank()
                || participantInstanceId.length() > 128 || onlinePlayerCount < 0
                || onlinePlayerCount > 100_000) return null;
        Challenges plugin = Challenges.get();
        if (plugin == null || plugin.getChallengesManager() == null
                || plugin.getChallengesManager().role() != ChallengeRole.COORDINATOR) return null;
        if (!targetAuthorityInstanceId.isEmpty()
                && !targetAuthorityInstanceId.equals(plugin.getBus().instanceId())) return null;
        return plugin.getChallengesManager().handleStateRequest(this);
    }
}
