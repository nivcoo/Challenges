package fr.nivcoo.challenges.messaging.rpc;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.challenges.ChallengeRole;
import fr.nivcoo.challenges.messaging.MessageActionName;
import fr.nivcoo.challenges.messaging.model.ChallengeProgressMutation;
import fr.nivcoo.challenges.messaging.response.ChallengeProgressBatchResponse;
import fr.nivcoo.utilsz.core.messaging.BusAction;
import fr.nivcoo.utilsz.core.messaging.RpcMessage;

import java.util.List;
import java.util.UUID;

@BusAction(value = MessageActionName.CHALLENGE_PROGRESS_BATCH_REQUEST,
        response = ChallengeProgressBatchResponse.class, runOnMainThread = true)
public record ChallengeProgressBatchRequest(String authorityInstanceId, UUID runId, long generation,
                                            String participantInstanceId, long batchSequence,
                                            UUID batchId, List<ChallengeProgressMutation> mutations)
        implements RpcMessage {
    public ChallengeProgressBatchRequest {
        authorityInstanceId = authorityInstanceId == null ? "" : authorityInstanceId;
        participantInstanceId = participantInstanceId == null ? "" : participantInstanceId;
        mutations = mutations == null ? List.of() : List.copyOf(mutations);
    }

    @Override
    public Object handle() {
        Challenges plugin = Challenges.get();
        if (plugin == null || plugin.getChallengesManager() == null
                || plugin.getChallengesManager().role() != ChallengeRole.COORDINATOR) return null;
        return plugin.getChallengesManager().acceptProgressBatch(this);
    }
}
