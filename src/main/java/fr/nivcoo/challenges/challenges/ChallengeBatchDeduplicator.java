package fr.nivcoo.challenges.challenges;

import fr.nivcoo.challenges.messaging.model.ChallengeProgressMutation;
import fr.nivcoo.challenges.messaging.response.ChallengeProgressBatchResponse;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class ChallengeBatchDeduplicator {
    static final int MAX_PARTICIPANTS = 1_024;

    private final Map<String, Receipt> latestReceipts = new HashMap<>();

    Check inspect(String participantInstanceId, long sequence, UUID batchId,
                  List<ChallengeProgressMutation> mutations) {
        if (participantInstanceId == null || participantInstanceId.isBlank()
                || sequence <= 0L || batchId == null || mutations == null || mutations.isEmpty()) {
            return new Check(Registration.INVALID, null);
        }
        Set<UUID> unique = new HashSet<>();
        for (ChallengeProgressMutation mutation : mutations) {
            if (mutation == null || mutation.observationId() == null
                    || !unique.add(mutation.observationId())) {
                return new Check(Registration.INVALID, null);
            }
        }

        Receipt latest = latestReceipts.get(participantInstanceId);
        String hash = payloadHash(mutations);
        if (latest == null) {
            if (latestReceipts.size() >= MAX_PARTICIPANTS) {
                return new Check(Registration.CAPACITY_REACHED, null);
            }
            return sequence == 1L
                    ? new Check(Registration.NEW, null)
                    : new Check(Registration.OUT_OF_ORDER, null);
        }
        if (sequence < latest.sequence()) return new Check(Registration.STALE_SEQUENCE, null);
        if (sequence == latest.sequence()) {
            return latest.batchId().equals(batchId) && latest.payloadHash().equals(hash)
                    ? new Check(Registration.DUPLICATE, latest.response())
                    : new Check(Registration.TAMPERED, null);
        }
        return sequence == latest.sequence() + 1L
                ? new Check(Registration.NEW, null)
                : new Check(Registration.OUT_OF_ORDER, null);
    }

    void remember(String participantInstanceId, long sequence, UUID batchId,
                  List<ChallengeProgressMutation> mutations,
                  ChallengeProgressBatchResponse response) {
        if (response == null) throw new IllegalArgumentException("response is required");
        latestReceipts.put(participantInstanceId,
                new Receipt(sequence, batchId, payloadHash(mutations), response));
    }

    void clear() {
        latestReceipts.clear();
    }

    int participantCount() {
        return latestReceipts.size();
    }

    long nextExpectedSequence(String participantInstanceId) {
        Receipt latest = latestReceipts.get(participantInstanceId);
        return latest == null ? 1L : Math.addExact(latest.sequence(), 1L);
    }

    enum Registration {
        NEW,
        DUPLICATE,
        STALE_SEQUENCE,
        OUT_OF_ORDER,
        TAMPERED,
        CAPACITY_REACHED,
        INVALID
    }

    record Check(Registration registration, ChallengeProgressBatchResponse cachedResponse) {
    }

    private static String payloadHash(List<ChallengeProgressMutation> mutations) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (ChallengeProgressMutation mutation : mutations) {
                update(digest, mutation.observationId().toString());
                update(digest, mutation.playerId() == null ? null : mutation.playerId().toString());
                update(digest, mutation.signedDelta());
                digest.update(ByteBuffer.allocate(Long.BYTES).putLong(mutation.observedAt()).array());
                update(digest, mutation.world());
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        if (value == null) {
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private record Receipt(long sequence, UUID batchId, String payloadHash,
                           ChallengeProgressBatchResponse response) {
    }
}
