package fr.nivcoo.challenges.challenges;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.catalog.ChallengeCatalog;
import fr.nivcoo.challenges.config.MainConfig;
import fr.nivcoo.challenges.messaging.action.ChallengeStateAction;
import fr.nivcoo.challenges.messaging.model.ChallengeProgressMutation;
import fr.nivcoo.challenges.messaging.model.ChallengeScoreEntry;
import fr.nivcoo.challenges.messaging.response.ChallengeProgressBatchResponse;
import fr.nivcoo.challenges.messaging.response.ChallengeStateSnapshot;
import fr.nivcoo.challenges.messaging.rpc.ChallengeProgressBatchRequest;
import fr.nivcoo.challenges.messaging.rpc.ChallengeStateRequest;
import fr.nivcoo.challenges.service.tracking.ChallengeTrackingDecision;
import fr.nivcoo.challenges.service.tracking.ChallengeTrackingDirection;
import fr.nivcoo.challenges.service.tracking.ChallengeTrackingService;
import fr.nivcoo.challenges.service.tracking.ChallengeTrackingSession;
import fr.nivcoo.challenges.utils.time.TimePair;
import fr.nivcoo.utilsz.core.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public final class ChallengesManager {

    public record ReadScore(UUID playerUuid, BigDecimal score) {
    }

    public record ActiveReadPage(ChallengeRun run, ChallengeRunPhase phase,
                                 long effectiveEndsAt, String message,
                                 long stateRevision, long rankingRevision,
                                 int total, boolean resyncRequired,
                                 List<ReadScore> scores) {
        public ActiveReadPage {
            message = message == null ? "" : message;
            scores = List.copyOf(scores);
        }
    }

    public record LifetimeReadPage(long rankingRevision, int total,
                                   boolean resyncRequired, List<ReadScore> scores) {
        public LifetimeReadPage {
            scores = List.copyOf(scores);
        }
    }
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final int PROGRESS_BATCH_SIZE = 256;
    private static final int MAX_IN_FLIGHT_BATCHES = 1;
    private static final int MAX_BUFFERED_PROGRESS = 32_768;
    private static final int MAX_PROTOCOL_STRING = 128;

    private final Challenges plugin;
    private final MainConfig config;
    private final ChallengeCatalog catalog;
    private final Set<String> blacklistedWorlds;
    private final ChallengeScoreLedger ledger = new ChallengeScoreLedger();
    private final Set<UUID> closedRuns = new LinkedHashSet<>();
    private final Set<String> drainAcknowledgements = new HashSet<>();
    private final Map<String, Long> participantLastSeen = new HashMap<>();
    private final Map<String, Integer> participantOnlinePlayers = new HashMap<>();
    private final Set<String> expectedParticipants = new HashSet<>();
    private final ChallengeBatchDeduplicator batchDeduplicator = new ChallengeBatchDeduplicator();
    private final List<PendingProgress> progressBuffer = new ArrayList<>();
    private final Map<UUID, PendingBatch> pendingBatches = new LinkedHashMap<>();

    private ChallengeTrackingService trackingService = ChallengeTrackingService.unavailable();
    private ChallengeTrackingSession trackingSession = ChallengeTrackingSession.NOOP;
    private ChallengeRun activeRun;
    private Challenge activeChallenge;
    private String knownAuthorityInstanceId;
    private long latestGeneration;
    private long rankingRevision;
    private long rankingRefreshTarget;
    private boolean rankingRefreshInFlight;
    private long rankingRefreshEpoch;
    private ChallengeStateAction lastFinalizedState;
    private long effectiveEndsAt;
    private boolean draining;
    private ChallengeRunPhase runPhase = ChallengeRunPhase.IDLE;
    private boolean trackingDrainComplete;
    private boolean startAnnouncementSent;
    private boolean stateSynchronized;
    private UUID synchronizedRunId;
    private long synchronizedStateRevision = -1L;
    private long lastAuthoritySnapshotAt;
    private long nextBatchSequence = 1L;
    private UUID sequenceRunId;
    private long stateSyncAttempt;
    private long appliedStateSyncAttempt;
    private CompletableFuture<ChallengeStateSnapshot> stateSyncInFlight;
    private String stateSyncTarget = "";
    private final Set<String> retiredAuthorityInstanceIds = new LinkedHashSet<>();
    private long lastCountdownValue = Long.MIN_VALUE;
    private long sortedScoresRevision;
    private LinkedHashMap<UUID, BigDecimal> sortedScoresCache = new LinkedHashMap<>();
    private List<Map.Entry<UUID, BigDecimal>> sortedScoreEntriesCache = List.of();
    private Map<UUID, Integer> placeCache = Map.of();

    private BukkitTask intervalTask;
    private BukkitTask runTicker;
    private BukkitTask stateSyncTask;
    private BukkitTask finalizationTask;
    private BukkitTask earliestFinalizationTask;
    private BukkitTask drainReminderTask;
    private BukkitTask progressFlushTask;
    private long earliestFinalizationAt;
    private long lastBackpressureWarningAt;

    public ChallengesManager(ChallengeCatalog catalog) {
        this(catalog, 0L);
    }

    public ChallengesManager(ChallengeCatalog catalog, long initialRankingRevision) {
        this.plugin = Challenges.get();
        this.config = plugin.cfg();
        this.catalog = catalog;
        this.rankingRevision = Math.max(0L, initialRankingRevision);
        this.rankingRefreshTarget = this.rankingRevision;
        this.blacklistedWorlds = config.blacklistedWorld.stream()
                .filter(Objects::nonNull)
                .map(world -> world.trim().toLowerCase(Locale.ROOT))
                .filter(world -> !world.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public void bindTrackingService(ChallengeTrackingService service) {
        if (service == null || !service.available()) return;
        if (trackingService.available()) trackingService.close();
        trackingService = service;
    }

    public void enable() {
        ensureMainThread();
        if (config.cluster.role == ChallengeRole.PARTICIPANT) {
            if (!trackingService.available()) {
                throw new IllegalStateException("EdenQuests is required when role is PARTICIPANT.");
            }
            trackingService.registerCatalog(catalog.all());
            startStateSynchronization();
            requestState();
            return;
        }
        latestGeneration = Math.max(latestGeneration, System.currentTimeMillis());
        rankingRevision = Math.max(rankingRevision, System.currentTimeMillis());
        rankingRefreshTarget = rankingRevision;
        plugin.getLogger().info("Coordinator boot generation is " + latestGeneration
                + "; any unfinished pre-restart challenge is fenced closed.");
        startIntervalLocal(false);
    }

    public ChallengeRole role() {
        return config.cluster.role;
    }

    public void handleStateAction(ChallengeStateAction action) {
        ensureMainThread();
        if (config.cluster.role != ChallengeRole.PARTICIPANT || action == null
                || action.kind() == null || !bounded(action.authorityInstanceId())) return;
        if (action.kind() == ChallengeStateAction.Kind.COORDINATOR_ONLINE) {
            handleCoordinatorWakeup(action.authorityInstanceId());
            return;
        }
        if (!isKnownAuthority(action.authorityInstanceId())) {
            if (!retiredAuthorityInstanceIds.contains(action.authorityInstanceId())) {
                requestState(action.authorityInstanceId());
            }
            return;
        }
        refreshRankingIfNeeded(action.rankingRevision(), action.authorityInstanceId());
        switch (action.kind()) {
            case RANKING -> {
            }
            case START -> applyStartFromBus(action.run());
            case SCORE -> applyScoreFromBus(action);
            case DRAIN -> applyDrainFromBus(action);
            case END -> applyFinalizedFromBus(action);
            case STOP -> applyStopFromBus(action);
            case COORDINATOR_ONLINE -> {
            }
        }
    }

    public long rankingRevision() {
        return rankingRevision;
    }

    public ActiveReadPage activeReadPage(int offset, int limit, long expectedStateRevision) {
        ensureMainThread();
        long revision = sortedScoresRevision;
        if (expectedStateRevision > 0L && expectedStateRevision != revision) {
            return new ActiveReadPage(activeRun, activeRun == null ? ChallengeRunPhase.IDLE : runPhase,
                    effectiveEndsAt, activeChallenge == null ? "" : activeChallenge.message(),
                    revision, rankingRevision, sortedScoresCache.size(), true, List.of());
        }

        List<Map.Entry<UUID, BigDecimal>> entries = sortedScoreEntriesCache;
        int from = Math.max(0, Math.min(offset, entries.size()));
        int to = Math.min(entries.size(), from + Math.max(1, limit));
        List<ReadScore> page = new ArrayList<>(to - from);
        for (int i = from; i < to; i++) {
            Map.Entry<UUID, BigDecimal> entry = entries.get(i);
            page.add(new ReadScore(entry.getKey(), entry.getValue()));
        }
        return new ActiveReadPage(activeRun, activeRun == null ? ChallengeRunPhase.IDLE : runPhase,
                effectiveEndsAt, activeChallenge == null ? "" : activeChallenge.message(),
                revision, rankingRevision, entries.size(), false, page);
    }

    public LifetimeReadPage lifetimeReadPage(int offset, int limit, long expectedRankingRevision) {
        ensureMainThread();
        List<Map.Entry<UUID, Integer>> entries = plugin.getCacheManager().rankingEntries();
        if (expectedRankingRevision > 0L && expectedRankingRevision != rankingRevision) {
            return new LifetimeReadPage(rankingRevision, entries.size(), true, List.of());
        }
        int from = Math.max(0, Math.min(offset, entries.size()));
        int to = Math.min(entries.size(), from + Math.max(1, limit));
        List<ReadScore> page = new ArrayList<>(to - from);
        for (int i = from; i < to; i++) {
            Map.Entry<UUID, Integer> entry = entries.get(i);
            page.add(new ReadScore(entry.getKey(), BigDecimal.valueOf(entry.getValue())));
        }
        return new LifetimeReadPage(rankingRevision, entries.size(), false, page);
    }

    public void announceRankingChanged() {
        ensureMainThread();
        if (config.cluster.role != ChallengeRole.COORDINATOR) return;
        rankingRevision = Math.addExact(rankingRevision, 1L);
        plugin.getBus().publish(ChallengeStateAction.ranking(plugin.getBus().instanceId(), rankingRevision));
    }

    private void refreshRankingIfNeeded(long incomingRevision, String authorityInstanceId) {
        refreshRankingIfNeeded(incomingRevision, authorityInstanceId, false);
    }

    private void refreshRankingIfNeeded(long incomingRevision, String authorityInstanceId, boolean force) {
        if ((!force && incomingRevision <= rankingRevision) || !isKnownAuthority(authorityInstanceId)) return;
        rankingRefreshTarget = force ? incomingRevision : Math.max(rankingRefreshTarget, incomingRevision);
        if (rankingRefreshInFlight) return;
        rankingRefreshInFlight = true;
        long requestedRevision = rankingRefreshTarget;
        long refreshEpoch = rankingRefreshEpoch;
        plugin.loadRankingAsync().whenComplete((scores, error) -> runOnMain(() -> {
            if (refreshEpoch != rankingRefreshEpoch) return;
            rankingRefreshInFlight = false;
            if (error != null) {
                plugin.getLogger().warning("Unable to resynchronize challenge ranking: " + error.getMessage());
                return;
            }
            if (!isKnownAuthority(authorityInstanceId)) return;
            plugin.getCacheManager().replaceRanking(scores);
            rankingRevision = Math.max(rankingRevision, requestedRevision);
            if (rankingRefreshTarget > rankingRevision) {
                refreshRankingIfNeeded(rankingRefreshTarget, authorityInstanceId, false);
            }
        }));
    }

    public boolean startChallenge() {
        ensureMainThread();
        if (config.cluster.role != ChallengeRole.COORDINATOR) return rejectParticipantControl();
        return startCoordinatorRun();
    }

    private boolean startCoordinatorRun() {
        if (activeRun != null) return false;
        List<Challenge> available = new ArrayList<>(catalog.all());
        if (available.isEmpty()) {
            return false;
        }

        long now = System.currentTimeMillis();
        long generation = Math.max(latestGeneration + 1L, now);
        Challenge challenge = available.get(ThreadLocalRandom.current().nextInt(available.size()));
        long startsAt = now + Math.max(0L, config.countdownNumber) * 1000L;
        long endsAt = startsAt + Math.max(1L, config.timeout) * 1000L;
        ChallengeRun run = new ChallengeRun(
                UUID.randomUUID(),
                generation,
                plugin.getBus().instanceId(),
                challenge.id(),
                catalog.digest(challenge.id()),
                startsAt,
                endsAt
        );

        applyStartInternal(run, false);
        plugin.getBus().publish(ChallengeStateAction.start(run, rankingRevision));
        return true;
    }

    public void applyStartFromBus(ChallengeRun incoming) {
        ensureMainThread();
        if (config.cluster.role != ChallengeRole.PARTICIPANT || incoming == null
                || knownAuthorityInstanceId == null
                || !knownAuthorityInstanceId.equals(incoming.authorityInstanceId())) return;
        applyStartInternal(incoming, false);
        requestState();
    }

    private void applyStartInternal(ChallengeRun incoming, boolean recoveredFromSnapshot) {
        ensureMainThread();
        if (incoming == null || closedRuns.contains(incoming.runId())) return;
        if (incoming.generation() < latestGeneration) return;
        if (incoming.generation() == latestGeneration && activeRun != null
                && !activeRun.runId().equals(incoming.runId())) {
            return;
        }
        if (activeRun != null && activeRun.matches(incoming.runId(), incoming.generation())) {
            return;
        }

        boolean continuingParticipantSequence = incoming.runId().equals(sequenceRunId);
        Optional<Challenge> localDefinition = catalog.find(incoming.challengeId());
        if (localDefinition.isEmpty() || !catalog.matches(incoming.challengeId(), incoming.challengeDigest())) {
            plugin.getLogger().severe("Challenge catalogue mismatch for '" + incoming.challengeId()
                    + "' (run " + incoming.runId() + "). Tracking refused.");
            latestGeneration = Math.max(latestGeneration, incoming.generation());
            clearActiveRun();
            resetParticipantSequence();
            return;
        }

        clearActiveRun();
        lastFinalizedState = null;
        latestGeneration = incoming.generation();
        Challenge challenge = localDefinition.get();
        activeRun = incoming;
        activeChallenge = challenge;
        effectiveEndsAt = incoming.endsAt();
        draining = false;
        runPhase = ChallengeRunPhase.ACTIVE;
        startAnnouncementSent = recoveredFromSnapshot && System.currentTimeMillis() >= incoming.startsAt();
        lastCountdownValue = Long.MIN_VALUE;
        drainAcknowledgements.clear();
        batchDeduplicator.clear();
        if (!continuingParticipantSequence) nextBatchSequence = 1L;
        sequenceRunId = incoming.runId();
        ledger.clear();
        stateSynchronized = config.cluster.role == ChallengeRole.COORDINATOR;
        if (config.cluster.role == ChallengeRole.COORDINATOR) {
            pruneParticipants();
            expectedParticipants.clear();
            expectedParticipants.addAll(participantLastSeen.keySet());
        }

        if (config.cluster.role == ChallengeRole.PARTICIPANT) {
            try {
                trackingSession = trackingService.activate(
                        challenge.id(),
                        incoming.runId(),
                        Instant.ofEpochMilli(incoming.startsAt()),
                        Instant.ofEpochMilli(incoming.endsAt()),
                        this::submitTrackingDecision
                );
            } catch (RuntimeException exception) {
                plugin.getLogger().severe("Unable to activate EdenQuests tracking for '" + challenge.id()
                        + "': " + exception.getMessage());
                markRunClosed(incoming.runId());
                clearActiveRun();
                resetParticipantSequence();
                return;
            }
        }

        runTicker = Bukkit.getScheduler().runTaskTimer(plugin, this::tickRun, 1L, 20L);
        tickRun();
    }

    private void tickRun() {
        ensureMainThread();
        refreshSortedScoresCache();
        ChallengeRun run = activeRun;
        if (run == null || draining) return;

        long now = System.currentTimeMillis();
        if (now < run.startsAt()) {
            long remaining = Math.max(1L, (run.startsAt() - now + 999L) / 1000L);
            if (remaining != lastCountdownValue) {
                lastCountdownValue = remaining;
                TimePair<Long, String> time = plugin.getTimeUtil().getTimeAndTypeBySecond(remaining);
                sendTitleMessage(
                        format(config.messages.title.countdown.title, String.valueOf(time.getFirst()), time.getSecond()),
                        format(config.messages.title.countdown.subtitle, String.valueOf(time.getFirst()), time.getSecond()),
                        2, 0, 0
                );
                sendActionBarMessage(format(config.messages.actionBar.countdown,
                        String.valueOf(time.getFirst()), time.getSecond()));
            }
            return;
        }

        if (!startAnnouncementSent) {
            startAnnouncementSent = true;
            announceStart(run);
        }

        if (now >= effectiveEndsAt) {
            if (config.cluster.role == ChallengeRole.COORDINATOR) {
                initiateCoordinatorDrain(effectiveEndsAt);
            } else {
                beginDraining(run.runId(), run.generation(), effectiveEndsAt);
            }
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isBlacklistedWorld(player.getWorld().getName())) {
                sendActionBarMessage(player);
            }
        }
    }

    private void announceStart(ChallengeRun run) {
        long durationSeconds = Math.max(1L, (run.endsAt() - run.startsAt()) / 1000L);
        TimePair<Long, String> time = plugin.getTimeUtil().getTimeAndTypeBySecond(durationSeconds);
        String message = activeChallenge.message();
        sendTitleMessage(
                format(config.messages.title.start.title, String.valueOf(time.getFirst()), time.getSecond(), message),
                format(config.messages.title.start.subtitle, String.valueOf(time.getFirst()), time.getSecond(), message),
                config.messages.title.start.stay,
                config.messages.title.start.fadeInTick,
                config.messages.title.start.fadeOutTick
        );
        sendGlobalMessage(format(config.messages.chat.startMessage,
                String.valueOf(time.getFirst()), time.getSecond(), message));
    }

    private void applyDrainFromBus(ChallengeStateAction action) {
        ensureMainThread();
        if (config.cluster.role != ChallengeRole.PARTICIPANT || action == null
                || !isActiveAuthority(action.authorityInstanceId())) return;
        if (draining) {
            if (trackingDrainComplete) publishDrainAck();
            return;
        }
        applyDrainInternal(action.runId(), action.generation(), action.effectiveEndsAt());
    }

    private void applyDrainInternal(UUID runId, long generation, long cutoffAt) {
        ensureMainThread();
        if (!matchesActive(runId, generation)) return;
        if (draining) return;
        effectiveEndsAt = Math.min(effectiveEndsAt, Math.max(activeRun.startsAt(), cutoffAt));
        beginDraining(runId, generation, effectiveEndsAt);
    }

    private void beginDraining(UUID runId, long generation, long cutoffAt) {
        if (!matchesActive(runId, generation) || draining) return;
        draining = true;
        runPhase = ChallengeRunPhase.DRAINING;
        effectiveEndsAt = cutoffAt;
        cancel(runTicker);
        runTicker = null;

        ChallengeTrackingSession session = trackingSession;
        trackingSession = ChallengeTrackingSession.NOOP;
        CompletionStage<Void> drained;
        try {
            drained = session.drainAndClose();
        } catch (RuntimeException exception) {
            drained = CompletableFuture.failedFuture(exception);
        }
        drained.whenComplete((ignored, error) -> runOnMain(() -> {
            if (!matchesActive(runId, generation)) return;
            if (error != null) {
                plugin.getLogger().warning("Challenge tracking drain failed for run " + runId + ": " + error.getMessage());
            } else if (config.cluster.role == ChallengeRole.PARTICIPANT) {
                trackingDrainComplete = true;
                publishDrainAck();
            }
        }));

        if (config.cluster.role == ChallengeRole.COORDINATOR) {
            pruneParticipants();
            cancel(finalizationTask);
            cancel(earliestFinalizationTask);
            cancel(drainReminderTask);
            long minimumSeconds = Math.max(1L, config.cluster.settlementGrace);
            earliestFinalizationAt = System.currentTimeMillis() + minimumSeconds * 1000L;
            earliestFinalizationTask = Bukkit.getScheduler().runTaskLater(plugin,
                    () -> finalizeIfSettled(runId, generation), minimumSeconds * 20L);
            long deadlineSeconds = Math.max(minimumSeconds, config.cluster.settlementTimeout);
            finalizationTask = Bukkit.getScheduler().runTaskLater(plugin,
                    () -> finalizeCoordinatorRun(runId, generation), deadlineSeconds * 20L);
            drainReminderTask = Bukkit.getScheduler().runTaskTimer(plugin,
                    () -> remindParticipantsToDrain(runId, generation), 100L, 100L);
        }
    }

    private void remindParticipantsToDrain(UUID runId, long generation) {
        ensureMainThread();
        if (!draining || !matchesActive(runId, generation)) {
            cancel(drainReminderTask);
            drainReminderTask = null;
            return;
        }
        pruneParticipants();
        finalizeIfSettled(runId, generation);
        if (!draining || !matchesActive(runId, generation)) return;
        ChallengeStateAction action = ChallengeStateAction.drain(
                activeRun, effectiveEndsAt, ledger.stateRevision(), rankingRevision);
        for (String participant : expectedParticipants) {
            if (!drainAcknowledgements.contains(participant)) plugin.getBus().publishTo(participant, action);
        }
    }

    private void acceptDrainAck(ChallengeStateRequest action) {
        ensureMainThread();
        if (config.cluster.role != ChallengeRole.COORDINATOR || action == null
                || !draining || !matchesActive(action.drainRunId(), action.drainGeneration())) {
            return;
        }
        if (action.participantInstanceId() != null
                && expectedParticipants.contains(action.participantInstanceId())) {
            drainAcknowledgements.add(action.participantInstanceId());
            finalizeIfSettled(action.drainRunId(), action.drainGeneration());
        }
    }

    private void finalizeIfSettled(UUID runId, long generation) {
        ensureMainThread();
        if (!matchesActive(runId, generation) || !draining
                || System.currentTimeMillis() < earliestFinalizationAt) {
            return;
        }
        if (drainAcknowledgements.containsAll(expectedParticipants)) {
            finalizeCoordinatorRun(runId, generation);
        }
    }

    private void finalizeCoordinatorRun(UUID runId, long generation) {
        ensureMainThread();
        if (config.cluster.role != ChallengeRole.COORDINATOR || !matchesActive(runId, generation)) return;
        ChallengeStateAction action = ChallengeStateAction.end(
                activeRun, ledger.entries(), ledger.stateRevision(), rankingRevision);
        lastFinalizedState = action;
        try {
            applyFinalizedInternal(action);
        } finally {
            plugin.getBus().publish(action);
        }
    }

    private void applyFinalizedFromBus(ChallengeStateAction action) {
        ensureMainThread();
        if (config.cluster.role != ChallengeRole.PARTICIPANT || action == null
                || !isActiveAuthority(action.authorityInstanceId())) return;
        applyFinalizedInternal(action);
    }

    private void applyFinalizedInternal(ChallengeStateAction action) {
        ensureMainThread();
        if (action == null || !matchesActive(action.runId(), action.generation())
                || closedRuns.contains(action.runId())) {
            return;
        }
        if (action.stateRevision() < ledger.stateRevision()) return;
        if (!ledger.restore(action.scores(), action.stateRevision())) {
            plugin.getLogger().warning("Ignored invalid final score snapshot for run " + action.runId() + ".");
            return;
        }
        markRunClosed(action.runId());
        closeTrackingImmediately();
        cancel(runTicker);
        cancel(finalizationTask);
        cancel(earliestFinalizationTask);
        runTicker = null;
        finalizationTask = null;
        earliestFinalizationTask = null;
        try {
            announceFinalResults();
        } catch (Throwable throwable) {
            plugin.getLogger().severe("Challenge finalization failed for run " + action.runId()
                    + ": " + throwable.getMessage());
        } finally {
            clearActiveRun();
            resetParticipantSequence();
            if (config.cluster.role == ChallengeRole.PARTICIPANT) {
                synchronizedRunId = null;
                synchronizedStateRevision = -1L;
                stateSynchronized = true;
            }
        }
    }

    private void applyStopFromBus(ChallengeStateAction action) {
        ensureMainThread();
        if (config.cluster.role != ChallengeRole.PARTICIPANT || action == null
                || !isActiveAuthority(action.authorityInstanceId())) return;
        applyStopInternal(action.runId(), action.generation());
    }

    private void applyStopInternal(UUID runId, long generation) {
        ensureMainThread();
        if (!matchesActive(runId, generation)) return;
        markRunClosed(runId);
        clearActiveRun();
        resetParticipantSequence();
        if (config.cluster.role == ChallengeRole.PARTICIPANT) {
            synchronizedRunId = null;
            synchronizedStateRevision = -1L;
            stateSynchronized = true;
        }
    }

    public boolean stopChallengeGlobally() {
        ensureMainThread();
        if (config.cluster.role != ChallengeRole.COORDINATOR) return rejectParticipantControl();
        return stopCoordinatorRun();
    }

    private boolean stopCoordinatorRun() {
        if (activeRun == null) return false;
        ChallengeStateAction action = ChallengeStateAction.stop(activeRun, rankingRevision);
        applyStopInternal(action.runId(), action.generation());
        plugin.getBus().publish(action);
        return true;
    }

    public boolean endChallengeGlobally() {
        ensureMainThread();
        if (config.cluster.role != ChallengeRole.COORDINATOR) return rejectParticipantControl();
        return requestCoordinatorDrain();
    }

    private boolean requestCoordinatorDrain() {
        if (activeRun == null) return false;
        if (draining) return true;
        long cutoff = System.currentTimeMillis();
        initiateCoordinatorDrain(cutoff);
        return true;
    }

    private void initiateCoordinatorDrain(long cutoff) {
        if (activeRun == null || draining) return;
        ChallengeStateAction action = ChallengeStateAction.drain(
                activeRun, cutoff, ledger.stateRevision(), rankingRevision);
        applyDrainInternal(action.runId(), action.generation(), action.effectiveEndsAt());
        plugin.getBus().publish(action);
    }

    public boolean startChallengeInterval() {
        ensureMainThread();
        if (config.cluster.role != ChallengeRole.COORDINATOR) return rejectParticipantControl();
        startIntervalLocal(true);
        return true;
    }

    private void startIntervalLocal(boolean stopActiveRun) {
        cancel(intervalTask);
        intervalTask = null;
        if (stopActiveRun && activeRun != null) stopCoordinatorRun();
        if (config.interval <= 0) return;

        long periodTicks = Math.max(1L, config.interval) * 20L;
        long initialSeconds = Math.max(0L, (long) config.interval - config.countdownNumber);
        intervalTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeRun != null) return;
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            if (!config.whitelistedHours.isEmpty() && !config.whitelistedHours.contains(hour)) return;
            if (config.playersNeeded > onlinePlayersAcrossParticipants()) return;
            startCoordinatorRun();
        }, Math.max(1L, initialSeconds * 20L), periodTicks);
    }

    public boolean stopChallengeTasks() {
        ensureMainThread();
        if (config.cluster.role != ChallengeRole.COORDINATOR) return rejectParticipantControl();
        stopIntervalLocal(true);
        return true;
    }

    private void stopIntervalLocal(boolean stopActiveRun) {
        cancel(intervalTask);
        intervalTask = null;
        if (stopActiveRun && activeRun != null) stopCoordinatorRun();
    }

    private CompletionStage<Void> submitTrackingDecision(ChallengeTrackingDecision decision) {
        if (decision == null || decision.observationId() == null || decision.playerId() == null
                || decision.direction() == null || decision.observedAt() == null || decision.signedDelta() == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (decision.world() == null || decision.world().isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Tracking world is missing."));
        }
        if (decision.world().length() > MAX_PROTOCOL_STRING
                || decision.definitionId() == null || decision.definitionId().length() > MAX_PROTOCOL_STRING
                || decision.objectiveId() == null || decision.objectiveId().length() > MAX_PROTOCOL_STRING) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Tracking context is too large."));
        }
        if (isBlacklistedWorld(decision.world())) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> completion = new CompletableFuture<>();
        if (!runOnMain(() -> {
            try {
                queueProgress(decision, completion);
            } catch (Throwable throwable) {
                completion.completeExceptionally(throwable);
            }
        })) {
            completion.completeExceptionally(new IllegalStateException(
                    "Unable to schedule challenge progress on the Bukkit thread."));
        }
        return completion;
    }

    private void queueProgress(ChallengeTrackingDecision decision, CompletableFuture<Void> completion) {
        ChallengeRun run = activeRun;
        if (run == null || !run.runId().equals(decision.runId())
                || !run.challengeId().equals(decision.definitionId())) {
            completion.completeExceptionally(new IllegalStateException("Challenge run is no longer active."));
            return;
        }
        boolean knownObjective = activeChallenge.objective().id().equals(decision.objectiveId());
        final String signedDelta;
        try {
            signedDelta = ChallengeAmount.canonical(decision.signedDelta());
            ChallengeAmount.parseDelta(signedDelta);
        } catch (IllegalArgumentException exception) {
            completion.completeExceptionally(exception);
            return;
        }
        if (!knownObjective || !directionMatches(decision.direction(), decision.signedDelta())) {
            completion.completeExceptionally(new IllegalArgumentException("Invalid challenge tracking decision."));
            return;
        }
        long observedAt = decision.observedAt().toEpochMilli();
        if (observedAt < run.startsAt() || observedAt >= effectiveEndsAt) {
            completion.complete(null);
            return;
        }

        ChallengeProgressMutation mutation = new ChallengeProgressMutation(
                decision.observationId(),
                decision.playerId(),
                signedDelta,
                observedAt,
                decision.world()
        );
        if (progressBuffer.size() >= MAX_BUFFERED_PROGRESS) {
            long now = System.currentTimeMillis();
            if (now - lastBackpressureWarningAt >= 10_000L) {
                lastBackpressureWarningAt = now;
                plugin.getLogger().severe("Challenge progress buffer is saturated; refusing new observations.");
            }
            completion.completeExceptionally(new IllegalStateException("Challenge progress buffer is saturated."));
            return;
        }
        progressBuffer.add(new PendingProgress(mutation, completion));
        if (progressBuffer.size() >= PROGRESS_BATCH_SIZE) {
            flushProgressBuffer();
        } else if (progressFlushTask == null) {
            progressFlushTask = Bukkit.getScheduler().runTask(plugin, this::flushProgressBuffer);
        }
    }

    private void flushProgressBuffer() {
        ensureMainThread();
        progressFlushTask = null;
        ChallengeRun run = activeRun;
        if (run == null) {
            failBufferedProgress(new IllegalStateException("Challenge run is no longer active."));
            return;
        }

        while (!progressBuffer.isEmpty() && pendingBatches.size() < MAX_IN_FLIGHT_BATCHES) {
            int size = Math.min(PROGRESS_BATCH_SIZE, progressBuffer.size());
            List<PendingProgress> entries = new ArrayList<>(progressBuffer.subList(0, size));
            progressBuffer.subList(0, size).clear();
            UUID batchId = UUID.randomUUID();
            long sequence = nextBatchSequence++;
            ChallengeProgressBatchRequest request = new ChallengeProgressBatchRequest(
                    run.authorityInstanceId(),
                    run.runId(),
                    run.generation(),
                    plugin.getBus().instanceId(),
                    sequence,
                    batchId,
                    entries.stream().map(PendingProgress::mutation).toList()
            );
            PendingBatch batch = new PendingBatch(run, request, entries);
            pendingBatches.put(batchId, batch);
            sendProgressBatch(batch, 1);
        }
    }

    private void sendProgressBatch(PendingBatch batch, int attempt) {
        if (pendingBatches.get(batch.request().batchId()) != batch) return;
        if (batch.inFlight != null && !batch.inFlight.isDone()) return;
        ChallengeRun run = batch.run();
        if (!matchesActive(run.runId(), run.generation())
                || !activeRun.authorityInstanceId().equals(run.authorityInstanceId())) {
            failBatch(batch, new IllegalStateException("Challenge run is no longer active."));
            return;
        }

        CompletableFuture<ChallengeProgressBatchResponse> call;
        try {
            call = plugin.getBus().callTo(run.authorityInstanceId(), batch.request(),
                    ChallengeProgressBatchResponse.class);
        } catch (RuntimeException exception) {
            scheduleBatchRetry(batch, attempt, exception);
            return;
        }
        batch.inFlight = call;
        call.whenComplete((response, error) -> runOnMain(() -> {
            if (pendingBatches.get(batch.request().batchId()) != batch || batch.inFlight != call) return;
            batch.inFlight = null;
            if (error != null || response == null) {
                scheduleBatchRetry(batch, attempt,
                        error == null ? new IllegalStateException("Authority returned no batch response.") : error);
                return;
            }
            receiveProgressBatchResponse(response);
        }));
    }

    private void receiveProgressBatchResponse(ChallengeProgressBatchResponse result) {
        ensureMainThread();
        if (config.cluster.role != ChallengeRole.PARTICIPANT || result == null || result.batchId() == null) return;
        PendingBatch batch = pendingBatches.get(result.batchId());
        boolean ownBatch = batch != null && result.participantInstanceId().equals(plugin.getBus().instanceId())
                && result.batchSequence() == batch.request().batchSequence()
                && pendingBatches.get(result.batchId()) == batch;
        if (ownBatch && !result.accepted() && ("stale_authority".equals(result.reason())
                || "stale_run".equals(result.reason()))) {
            requestState();
            scheduleBatchRetry(batch, batch.attempt + 1,
                    new IllegalStateException(result.reason()));
            return;
        }
        if (!isActiveAuthority(result.authorityInstanceId())
                || !matchesActive(result.runId(), result.generation())) return;
        if (!result.accepted()) {
            if (!ownBatch) return;
            failBatch(batch, new IllegalStateException("Challenge progress batch was rejected: " + result.reason()));
            return;
        }
        applyBatchResponseScores(result);
        if (!ownBatch) return;
        Set<UUID> appliedObservations = Set.copyOf(result.appliedObservationIds());
        for (PendingProgress pending : batch.entries()) {
            if (appliedObservations.contains(pending.mutation().observationId())) {
                playProgressSound(pending.mutation().playerId(), pending.mutation().signedDelta());
            }
            pending.completion().complete(null);
        }
        pendingBatches.remove(batch.request().batchId(), batch);
        resumeProgressFlush();
    }

    private void scheduleBatchRetry(PendingBatch batch, int attempt, Throwable failure) {
        if (pendingBatches.get(batch.request().batchId()) != batch) return;
        batch.attempt = Math.max(batch.attempt, attempt);
        if (attempt == 1 || attempt == 3 || attempt % 20 == 0) {
            plugin.getLogger().warning("Challenge progress batch is waiting for authority (attempt "
                    + attempt + "): " + failure.getMessage());
        }
        if (attempt >= 3) requestState();
        try {
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> sendProgressBatch(batch, attempt + 1), 20L);
        } catch (RuntimeException exception) {
            failBatch(batch, new IllegalStateException(
                    "Unable to schedule challenge progress retry.", exception));
        }
    }

    public ChallengeProgressBatchResponse acceptProgressBatch(ChallengeProgressBatchRequest request) {
        ensureMainThread();
        if (config.cluster.role != ChallengeRole.COORDINATOR) return null;
        ChallengeRun run = activeRun;
        if (run == null || request == null || !run.matches(request.runId(), request.generation())) {
            return rejectedCommit("stale_run", request);
        }
        if (!request.authorityInstanceId().equals(run.authorityInstanceId())) {
            return rejectedCommit("stale_authority", request);
        }
        if (closedRuns.contains(run.runId())) {
            return rejectedCommit("closed_run", request);
        }
        if (request.batchId() == null || request.participantInstanceId() == null
                || request.participantInstanceId().isBlank() || request.mutations().isEmpty()
                || request.batchSequence() <= 0L || request.batchSequence() == Long.MAX_VALUE
                || request.participantInstanceId().length() > MAX_PROTOCOL_STRING
                || request.mutations().size() > PROGRESS_BATCH_SIZE
                || !validBatchShape(request.mutations())) {
            return rejectedCommit("invalid_batch", request);
        }
        if (!acceptParticipantReady(request.participantInstanceId())) {
            return rejectedCommit("participant_capacity", request);
        }
        ChallengeBatchDeduplicator.Check batchCheck = batchDeduplicator.inspect(
                request.participantInstanceId(), request.batchSequence(), request.batchId(), request.mutations());
        if (batchCheck.registration() == ChallengeBatchDeduplicator.Registration.INVALID) {
            return rejectedCommit("invalid_batch", request);
        }
        if (batchCheck.registration() == ChallengeBatchDeduplicator.Registration.TAMPERED) {
            return rejectedCommit("tampered_batch", request);
        }
        if (batchCheck.registration() == ChallengeBatchDeduplicator.Registration.CAPACITY_REACHED) {
            return rejectedCommit("participant_capacity", request);
        }
        if (batchCheck.registration() == ChallengeBatchDeduplicator.Registration.STALE_SEQUENCE) {
            return rejectedCommit("stale_sequence", request);
        }
        if (batchCheck.registration() == ChallengeBatchDeduplicator.Registration.OUT_OF_ORDER) {
            return rejectedCommit("out_of_order", request);
        }
        if (batchCheck.registration() == ChallengeBatchDeduplicator.Registration.DUPLICATE) {
            ChallengeProgressBatchResponse cached = batchCheck.cachedResponse();
            publishScoreDelta(run, cached);
            return cached;
        }

        expectedParticipants.add(request.participantInstanceId());
        long baseStateRevision = ledger.stateRevision();
        Map<UUID, ChallengeScoreEntry> updates = new LinkedHashMap<>();
        List<ChallengeProgressMutation> countableMutations = new ArrayList<>();
        List<ChallengeScoreLedger.Delta> countableDeltas = new ArrayList<>();

        for (ChallengeProgressMutation mutation : request.mutations()) {
            if (!isCountableMutation(run, effectiveEndsAt, blacklistedWorlds, mutation)) continue;
            countableMutations.add(mutation);
            countableDeltas.add(new ChallengeScoreLedger.Delta(
                    mutation.playerId(), ChallengeAmount.parseDelta(mutation.signedDelta())));
        }

        final List<ChallengeScoreLedger.Result> appliedDeltas;
        try {
            appliedDeltas = ledger.applyBatch(countableDeltas);
        } catch (ChallengeScoreLedger.CapacityExceededException exception) {
            ChallengeProgressBatchResponse response = rejectedCommit("score_capacity", request);
            batchDeduplicator.remember(request.participantInstanceId(), request.batchSequence(), request.batchId(),
                    request.mutations(), response);
            return response;
        }
        for (ChallengeScoreLedger.Result applied : appliedDeltas) {
            updates.put(applied.playerId(), new ChallengeScoreEntry(
                    applied.playerId(), ChallengeAmount.canonical(applied.rawBalance()), applied.playerRevision()));
        }

        ChallengeProgressBatchResponse response = new ChallengeProgressBatchResponse(run.authorityInstanceId(),
                run.runId(), run.generation(), request.participantInstanceId(), request.batchSequence(), request.batchId(),
                true, "accepted", countableMutations.stream()
                .map(ChallengeProgressMutation::observationId).toList(),
                List.copyOf(updates.values()), baseStateRevision, ledger.stateRevision());
        batchDeduplicator.remember(request.participantInstanceId(), request.batchSequence(), request.batchId(),
                request.mutations(), response);
        publishScoreDelta(run, response);
        return response;
    }

    private void publishScoreDelta(ChallengeRun run, ChallengeProgressBatchResponse response) {
        if (response.accepted() && !response.scores().isEmpty()) {
            plugin.getBus().publish(ChallengeStateAction.score(run, response.scores(),
                    response.baseStateRevision(), response.stateRevision(), rankingRevision));
        }
    }

    private ChallengeProgressBatchResponse rejectedCommit(String reason, ChallengeProgressBatchRequest request) {
        return new ChallengeProgressBatchResponse(plugin.getBus().instanceId(),
                request == null ? null : request.runId(), request == null ? 0L : request.generation(),
                request == null ? "" : request.participantInstanceId(),
                request == null ? 0L : request.batchSequence(), request == null ? null : request.batchId(), false, reason,
                List.of(), List.of(), -1L, ledger.stateRevision());
    }

    private boolean validBatchShape(List<ChallengeProgressMutation> mutations) {
        Set<UUID> observations = new HashSet<>();
        for (ChallengeProgressMutation mutation : mutations) {
            if (mutation == null || mutation.observationId() == null || mutation.playerId() == null
                    || !observations.add(mutation.observationId())
                    || !bounded(mutation.world())
                    || mutation.signedDelta() == null || mutation.signedDelta().isBlank()
                    || mutation.signedDelta().length() > 96) return false;
            try {
                ChallengeAmount.parseDelta(mutation.signedDelta());
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
        return true;
    }

    private boolean bounded(String value) {
        return value != null && !value.isBlank() && value.length() <= MAX_PROTOCOL_STRING;
    }

    static boolean isCountableMutation(ChallengeRun run, long cutoffAt, Set<String> blacklistedWorlds,
                                       ChallengeProgressMutation mutation) {
        if (run == null || mutation == null || mutation.world() == null) return false;
        String normalizedWorld = mutation.world().trim().toLowerCase(Locale.ROOT);
        return mutation.observedAt() >= run.startsAt() && mutation.observedAt() < cutoffAt
                && !blacklistedWorlds.contains(normalizedWorld);
    }

    private boolean directionMatches(ChallengeTrackingDirection direction, BigDecimal signedDelta) {
        return direction == ChallengeTrackingDirection.COUNT && signedDelta.signum() > 0
                || direction == ChallengeTrackingDirection.REVERSE && signedDelta.signum() < 0;
    }

    private boolean isBlacklistedWorld(String world) {
        return world != null && blacklistedWorlds.contains(world.trim().toLowerCase(Locale.ROOT));
    }

    private Sound resolveSound(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Sound.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
        }
        NamespacedKey key = soundKey(value);
        return key == null ? null : Registry.SOUNDS.get(key);
    }

    static NamespacedKey soundKey(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.contains(":")
                ? NamespacedKey.fromString(normalized)
                : NamespacedKey.minecraft(normalized);
    }

    private void applyScoreFromBus(ChallengeStateAction action) {
        if (!matchesActive(action.runId(), action.generation())) return;
        applyScoreDelta(action.scores(), action.baseStateRevision(), action.stateRevision());
    }

    private void applyBatchResponseScores(ChallengeProgressBatchResponse response) {
        if (config.cluster.role != ChallengeRole.PARTICIPANT) return;
        applyScoreDelta(response.scores(), response.baseStateRevision(), response.stateRevision());
    }

    private void applyScoreDelta(List<ChallengeScoreEntry> scores, long baseRevision, long revision) {
        if (revision <= synchronizedStateRevision) return;
        if (!stateSynchronized || baseRevision != synchronizedStateRevision
                || !applyScoreUpdates(scores, revision)) {
            stateSynchronized = false;
            requestState();
            return;
        }
        synchronizedStateRevision = revision;
    }

    private boolean applyScoreUpdates(List<ChallengeScoreEntry> updates, long stateRevision) {
        boolean valid = true;
        for (ChallengeScoreEntry update : updates) {
            if (!ledger.applyRemote(update, stateRevision)) {
                valid = false;
                continue;
            }
            Player player = Bukkit.getPlayer(update.playerId());
            if (player != null) sendActionBarMessage(player);
        }
        return valid;
    }

    private void failBatch(PendingBatch batch, Throwable failure) {
        if (!pendingBatches.remove(batch.request().batchId(), batch)) return;
        completeBatchExceptionally(batch, failure);
        resumeProgressFlush();
    }

    private void completeBatchExceptionally(PendingBatch batch, Throwable failure) {
        for (PendingProgress pending : batch.entries()) {
            pending.completion().completeExceptionally(failure);
        }
    }

    private void resumeProgressFlush() {
        if (progressBuffer.isEmpty() || progressFlushTask != null) return;
        progressFlushTask = Bukkit.getScheduler().runTask(plugin, this::flushProgressBuffer);
    }

    private void failBufferedProgress(Throwable failure) {
        for (PendingProgress pending : progressBuffer) {
            pending.completion().completeExceptionally(failure);
        }
        progressBuffer.clear();
    }

    private void playProgressSound(UUID playerId, String delta) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;
        final BigDecimal amount;
        try {
            amount = ChallengeAmount.parseDelta(delta);
        } catch (IllegalArgumentException exception) {
            return;
        }
        Sound sound = resolveSound(amount.signum() < 0 ? config.sound.remove : config.sound.add);
        if (sound != null) player.playSound(player.getLocation(), sound, .4f, 1.7f);
    }

    private void startStateSynchronization() {
        cancel(stateSyncTask);
        long period = Math.max(1L, config.cluster.heartbeatInterval) * 20L;
        stateSyncTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            enforceAuthorityTimeout();
            requestState();
        }, period, period);
    }

    private void requestState() {
        requestState(knownAuthorityInstanceId, false);
    }

    private void requestState(String expectedAuthorityInstanceId) {
        requestState(expectedAuthorityInstanceId, false);
    }

    private void requestState(String expectedAuthorityInstanceId, boolean force) {
        if (config.cluster.role != ChallengeRole.PARTICIPANT) return;
        String target = expectedAuthorityInstanceId == null ? "" : expectedAuthorityInstanceId;
        if (stateSyncInFlight != null && !stateSyncInFlight.isDone()) {
            if (!force && stateSyncTarget.equals(target)) return;
            stateSyncInFlight.cancel(false);
        }
        long attempt = ++stateSyncAttempt;
        ChallengeStateRequest request = new ChallengeStateRequest(target, plugin.getBus().instanceId(),
                Bukkit.getOnlinePlayers().size(),
                synchronizedRunId, latestGeneration, activeRun == null ? ChallengeRunPhase.IDLE : runPhase,
                synchronizedStateRevision, rankingRevision,
                draining && trackingDrainComplete && activeRun != null ? activeRun.runId() : null,
                draining && trackingDrainComplete && activeRun != null ? activeRun.generation() : 0L);
        CompletableFuture<ChallengeStateSnapshot> call;
        try {
            call = target.isEmpty()
                    ? plugin.getBus().call(request, ChallengeStateSnapshot.class)
                    : plugin.getBus().callTo(target, request, ChallengeStateSnapshot.class);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Unable to request challenge state: " + exception.getMessage());
            return;
        }
        stateSyncTarget = target;
        stateSyncInFlight = call;
        call.whenComplete((snapshot, error) -> runOnMain(() -> {
            if (attempt != stateSyncAttempt || stateSyncInFlight != call) return;
            stateSyncInFlight = null;
            if (error != null || snapshot == null) return;
            applyStateSnapshot(snapshot, attempt, target);
        }));
    }

    public void wakeupStateSynchronization() {
        ensureMainThread();
        requestState();
    }

    public void handleCoordinatorWakeup(String authorityInstanceId) {
        ensureMainThread();
        if (config.cluster.role != ChallengeRole.PARTICIPANT || !bounded(authorityInstanceId)) return;
        if (retiredAuthorityInstanceIds.contains(authorityInstanceId)) return;
        requestState(authorityInstanceId);
    }

    private void enforceAuthorityTimeout() {
        if (config.cluster.role != ChallengeRole.PARTICIPANT
                || knownAuthorityInstanceId == null || lastAuthoritySnapshotAt <= 0L) return;
        long timeoutMillis = Math.max(1L, config.cluster.participantTimeout) * 1000L;
        if (System.currentTimeMillis() - lastAuthoritySnapshotAt < timeoutMillis) return;
        UUID abandonedRunId = activeRun == null ? null : activeRun.runId();
        if (activeRun != null) clearActiveRun();
        knownAuthorityInstanceId = null;
        stateSynchronized = false;
        synchronizedRunId = null;
        synchronizedStateRevision = -1L;
        if (stateSyncInFlight != null) stateSyncInFlight.cancel(false);
        stateSyncInFlight = null;
        stateSyncAttempt++;
        plugin.getLogger().warning(abandonedRunId == null
                ? "Challenge coordinator heartbeat timed out; authority binding was cleared."
                : "Challenge run " + abandonedRunId
                + " was abandoned because the coordinator heartbeat timed out.");
    }

    private boolean acceptParticipantReady(String participantInstanceId) {
        return acceptParticipantReady(participantInstanceId, null);
    }

    private boolean acceptParticipantReady(String participantInstanceId, Integer onlinePlayers) {
        ensureMainThread();
        if (config.cluster.role != ChallengeRole.COORDINATOR || participantInstanceId == null
                || participantInstanceId.isBlank() || participantInstanceId.length() > MAX_PROTOCOL_STRING) return false;
        if (onlinePlayers != null && (onlinePlayers < 0 || onlinePlayers > 100_000)) return false;
        pruneParticipants();
        if (!participantLastSeen.containsKey(participantInstanceId) && participantLastSeen.size() >= 1_024) {
            return false;
        }
        participantLastSeen.put(participantInstanceId, System.currentTimeMillis());
        if (onlinePlayers != null) participantOnlinePlayers.put(participantInstanceId, onlinePlayers);
        if (activeRun != null) expectedParticipants.add(participantInstanceId);
        return true;
    }

    private void pruneParticipants() {
        Set<String> liveParticipants = liveParticipantIds(participantLastSeen, System.currentTimeMillis(),
                Math.max(1L, config.cluster.participantTimeout) * 1000L);
        participantLastSeen.keySet().retainAll(liveParticipants);
        participantOnlinePlayers.keySet().retainAll(participantLastSeen.keySet());
        expectedParticipants.retainAll(participantLastSeen.keySet());
        drainAcknowledgements.retainAll(participantLastSeen.keySet());
    }

    static Set<String> liveParticipantIds(Map<String, Long> lastSeen, long now, long timeoutMillis) {
        if (lastSeen == null || lastSeen.isEmpty()) return Set.of();
        long cutoff = now - Math.max(1L, timeoutMillis);
        return lastSeen.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null && entry.getValue() >= cutoff)
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    private int onlinePlayersAcrossParticipants() {
        pruneParticipants();
        long total = Bukkit.getOnlinePlayers().size();
        for (int count : participantOnlinePlayers.values()) total += count;
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public ChallengeStateSnapshot handleStateRequest(ChallengeStateRequest request) {
        ensureMainThread();
        if (config.cluster.role != ChallengeRole.COORDINATOR || request == null
                || request.participantInstanceId().isBlank()
                || request.participantInstanceId().length() > MAX_PROTOCOL_STRING) return null;
        if (!acceptParticipantReady(request.participantInstanceId(), request.onlinePlayerCount())) return null;
        if (request.drainRunId() != null) acceptDrainAck(request);
        boolean sameRun = activeRun == null
                ? request.knownRunId() == null && request.knownGeneration() == latestGeneration
                : activeRun.runId().equals(request.knownRunId())
                && activeRun.generation() == request.knownGeneration();
        boolean includeScores = !sameRun || request.knownStateRevision() != ledger.stateRevision();
        ChallengeStateSnapshot snapshot;
        if (activeRun == null && lastFinalizedState != null
                && lastFinalizedState.runId().equals(request.knownRunId())) {
            snapshot = new ChallengeStateSnapshot(plugin.getBus().instanceId(),
                    lastFinalizedState.generation(), lastFinalizedState.run(), ChallengeRunPhase.FINALIZED,
                    lastFinalizedState.effectiveEndsAt(), true, lastFinalizedState.scores(),
                    lastFinalizedState.stateRevision(), rankingRevision,
                    batchDeduplicator.nextExpectedSequence(request.participantInstanceId()));
        } else {
            snapshot = new ChallengeStateSnapshot(plugin.getBus().instanceId(), latestGeneration,
                    activeRun, activeRun == null ? ChallengeRunPhase.IDLE : runPhase,
                    effectiveEndsAt, includeScores, includeScores ? ledger.entries() : List.of(),
                    ledger.stateRevision(), rankingRevision,
                    batchDeduplicator.nextExpectedSequence(request.participantInstanceId()));
        }
        if (draining && activeRun != null) finalizeIfSettled(activeRun.runId(), activeRun.generation());
        return snapshot;
    }

    private void applyStateSnapshot(ChallengeStateSnapshot snapshot, long attempt, String expectedAuthority) {
        ensureMainThread();
        if (config.cluster.role != ChallengeRole.PARTICIPANT || snapshot == null
                || attempt <= appliedStateSyncAttempt || !bounded(snapshot.authorityInstanceId())
                || snapshot.phase() == null) return;
        if (!expectedAuthority.isEmpty() && !expectedAuthority.equals(snapshot.authorityInstanceId())) return;
        if (snapshot.run() == null && snapshot.phase() != ChallengeRunPhase.IDLE) return;
        if (snapshot.run() != null && (!snapshot.authorityInstanceId().equals(snapshot.run().authorityInstanceId())
                || !catalog.matches(snapshot.run().challengeId(), snapshot.run().challengeDigest()))) return;
        if (!snapshot.scoresIncluded() && !snapshot.scores().isEmpty()) return;
        if (snapshot.nextExpectedBatchSequence() <= 0L) return;
        boolean authorityChanged = knownAuthorityInstanceId == null
                || !knownAuthorityInstanceId.equals(snapshot.authorityInstanceId());
        if (!authorityChanged && snapshot.generation() < latestGeneration) return;
        appliedStateSyncAttempt = attempt;
        if (authorityChanged) {
            rememberRetiredAuthority(knownAuthorityInstanceId);
            rankingRevision = -1L;
            rankingRefreshTarget = -1L;
            rankingRefreshInFlight = false;
            rankingRefreshEpoch++;
        }
        knownAuthorityInstanceId = snapshot.authorityInstanceId();
        lastAuthoritySnapshotAt = System.currentTimeMillis();
        refreshRankingIfNeeded(snapshot.rankingRevision(), snapshot.authorityInstanceId(), authorityChanged);
        if (snapshot.run() == null) {
            if (snapshot.phase() != ChallengeRunPhase.IDLE) return;
            latestGeneration = authorityChanged ? snapshot.generation()
                    : Math.max(latestGeneration, snapshot.generation());
            if (activeRun != null && (authorityChanged
                    || activeRun.generation() <= snapshot.generation())) {
                markRunClosed(activeRun.runId());
                clearActiveRun();
                resetParticipantSequence();
            }
            synchronizedRunId = null;
            synchronizedStateRevision = snapshot.stateRevision();
            stateSynchronized = true;
            return;
        }
        if (snapshot.phase() == ChallengeRunPhase.IDLE) return;
        if (activeRun == null || !activeRun.matches(snapshot.run().runId(), snapshot.run().generation())) {
            if (authorityChanged) latestGeneration = snapshot.run().generation();
            if (snapshot.phase() == ChallengeRunPhase.FINALIZED) return;
            applyStartInternal(snapshot.run(), true);
        }
        if (snapshot.stateRevision() < ledger.stateRevision()) {
            if (!stateSynchronized) requestState();
            return;
        }
        if (activeRun != null && activeRun.matches(snapshot.run().runId(), snapshot.run().generation())
                && snapshot.scoresIncluded()
                && snapshot.stateRevision() >= ledger.stateRevision()) {
            if (!ledger.restore(snapshot.scores(), snapshot.stateRevision())) {
                plugin.getLogger().warning("Ignored invalid score snapshot for run " + snapshot.run().runId() + ".");
                return;
            }
        }
        if (activeRun != null && activeRun.matches(snapshot.run().runId(), snapshot.run().generation())) {
            reconcileBatchSequence(snapshot.nextExpectedBatchSequence());
            synchronizedRunId = activeRun.runId();
            synchronizedStateRevision = snapshot.stateRevision();
            stateSynchronized = true;
        }
        if (activeRun != null && activeRun.matches(snapshot.run().runId(), snapshot.run().generation())
                && snapshot.phase() == ChallengeRunPhase.FINALIZED) {
            applyFinalizedInternal(ChallengeStateAction.end(activeRun, snapshot.scores(),
                    snapshot.stateRevision(), snapshot.rankingRevision()));
            return;
        }
        if (activeRun != null && activeRun.matches(snapshot.run().runId(), snapshot.run().generation())
                && snapshot.phase() == ChallengeRunPhase.DRAINING) {
            applyDrainInternal(activeRun.runId(), activeRun.generation(), snapshot.effectiveEndsAt());
        }
    }

    private void reconcileBatchSequence(long authorityNextExpected) {
        if (activeRun == null || authorityNextExpected <= 0L) return;
        PendingBatch pending = pendingBatches.values().stream().findFirst().orElse(null);
        if (pending != null && pending.run().matches(activeRun.runId(), activeRun.generation())) {
            nextBatchSequence = reconciledNextSequence(authorityNextExpected, pending.request().batchSequence());
        } else {
            nextBatchSequence = reconciledNextSequence(authorityNextExpected, null);
        }
        sequenceRunId = activeRun.runId();
    }

    static long reconciledNextSequence(long authorityNextExpected, Long pendingSequence) {
        if (authorityNextExpected <= 0L) throw new IllegalArgumentException("Expected sequence must be positive.");
        if (pendingSequence == null) return authorityNextExpected;
        if (pendingSequence <= 0L || pendingSequence == Long.MAX_VALUE) {
            throw new IllegalArgumentException("Pending sequence is invalid.");
        }
        return Math.max(authorityNextExpected, pendingSequence + 1L);
    }

    private void rememberRetiredAuthority(String authorityInstanceId) {
        if (authorityInstanceId == null || authorityInstanceId.isBlank()) return;
        retiredAuthorityInstanceIds.add(authorityInstanceId);
        while (retiredAuthorityInstanceIds.size() > 16) {
            Iterator<String> iterator = retiredAuthorityInstanceIds.iterator();
            if (!iterator.hasNext()) break;
            iterator.next();
            iterator.remove();
        }
    }

    public boolean isChallengeStarted() {
        ChallengeRun run = activeRun;
        if (run == null || runPhase != ChallengeRunPhase.ACTIVE
                || (config.cluster.role == ChallengeRole.PARTICIPANT && !stateSynchronized)) {
            return false;
        }
        long now = System.currentTimeMillis();
        return now >= run.startsAt() && now < effectiveEndsAt;
    }

    public Challenge getSelectedChallenge() {
        return activeRun == null ? null : activeChallenge;
    }

    public boolean isReloadUnsafe() {
        return activeRun != null;
    }

    private void markRunClosed(UUID runId) {
        if (runId == null) return;
        closedRuns.add(runId);
        while (closedRuns.size() > 64) {
            Iterator<UUID> iterator = closedRuns.iterator();
            if (!iterator.hasNext()) break;
            iterator.next();
            iterator.remove();
        }
    }

    public BigDecimal getScoreOfPlayer(UUID uuid) {
        return ledger.score(uuid);
    }

    public TimePair<Long, String> getCountdown() {
        ChallengeRun run = activeRun;
        if (run == null) return null;
        long now = System.currentTimeMillis();
        long target = now < run.startsAt() ? run.startsAt() : effectiveEndsAt;
        long remaining = Math.max(0L, (target - now + 999L) / 1000L);
        return plugin.getTimeUtil().getTimeAndTypeBySecond(remaining);
    }

    public void sendActionBarMessage(Player player) {
        if (player == null || isBlacklistedWorld(player.getWorld().getName())
                || !isChallengeStarted()) return;
        TimePair<Long, String> countdown = getCountdown();
        if (countdown == null) return;
        String message = format(config.messages.actionBar.running.message,
                activeChallenge.message(),
                ChallengeAmount.canonical(getScoreOfPlayer(player.getUniqueId())),
                String.valueOf(countdown.getFirst()),
                countdown.getSecond(),
                "{4}");
        int place = getPlaceOfPlayer(player);
        if (place == 0) {
            message = message.replace("{4} ", "");
        } else {
            message = message.replace("{4}", format(config.messages.actionBar.running.place, String.valueOf(place)));
        }
        sendActionBarMessage(player, message);
    }

    public void sendActionBarMessage(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isBlacklistedWorld(player.getWorld().getName())) {
                sendActionBarMessage(player, message);
            }
        }
    }

    public void sendActionBarMessage(Player player, String message) {
        player.sendActionBar(LEGACY.deserialize(message));
    }

    public void sendTitleMessage(String title, String subtitle, int time, int fadeInTick, int fadeOutTick) {
        Component titleComponent = LEGACY.deserialize(title);
        Component subtitleComponent = LEGACY.deserialize(subtitle);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(Title.title(
                    titleComponent,
                    subtitleComponent,
                    Title.Times.times(
                            Duration.ofMillis(fadeInTick * 50L),
                            Duration.ofSeconds(time),
                            Duration.ofMillis(fadeOutTick * 50L)
                    )
            ));
        }
    }

    public void sendGlobalMessage(String message) {
        if (activeRun == null) return;
        Sound sound = resolveSound(config.sound.messages);
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                player.sendMessage(message);
                if (sound != null) player.playSound(player.getLocation(), sound, .4f, 1.7f);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Unable to notify player " + player.getUniqueId()
                        + ": " + exception.getMessage());
            }
        }
    }

    public LinkedHashMap<UUID, BigDecimal> getSortPlayersProgress() {
        refreshSortedScoresCache();
        return new LinkedHashMap<>(sortedScoresCache);
    }

    public int getPlaceOfUUID(UUID uuid) {
        if (uuid == null) return 0;
        refreshSortedScoresCache();
        return placeCache.getOrDefault(uuid, 0);
    }

    public int getPlaceOfPlayer(Player player) {
        return player == null ? 0 : getPlaceOfUUID(player.getUniqueId());
    }

    public Map.Entry<UUID, BigDecimal> getPlayerProgressByPlace(int place) {
        refreshSortedScoresCache();
        int current = 0;
        for (Map.Entry<UUID, BigDecimal> entry : sortedScoresCache.entrySet()) {
            if (++current == place) return entry;
        }
        return null;
    }

    private void refreshSortedScoresCache() {
        if (sortedScoresRevision == ledger.stateRevision()) return;
        sortedScoresCache = ledger.sortedScores();
        sortedScoreEntriesCache = sortedScoresCache.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), entry.getValue()))
                .toList();
        Map<UUID, Integer> places = new HashMap<>();
        int place = 0;
        for (UUID playerId : sortedScoresCache.keySet()) places.put(playerId, ++place);
        placeCache = Map.copyOf(places);
        sortedScoresRevision = ledger.stateRevision();
    }

    public String getPlayerNameProgressByPlace(int place) {
        Map.Entry<UUID, BigDecimal> progress = getPlayerProgressByPlace(place);
        if (progress == null) return legacy(config.messages.global.none);
        return plugin.getCacheManager().resolvePlayerName(progress.getKey());
    }

    public String getPlayerCountProgressByPlace(int place) {
        Map.Entry<UUID, BigDecimal> progress = getPlayerProgressByPlace(place);
        return progress == null ? "0" : ChallengeAmount.canonical(progress.getValue());
    }

    private void announceFinalResults() {
        Challenge selectedChallenge = getSelectedChallenge();
        if (selectedChallenge == null) return;
        Map<UUID, BigDecimal> sorted = getSortPlayersProgress();
        if (sorted.isEmpty()) {
            sendGlobalMessage(legacy(config.messages.chat.noPlayer));
            return;
        }

        try {
            sendGlobalMessage(buildTopMessage(sorted));
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Unable to announce the final challenge ranking: " + exception.getMessage());
        }
        try {
            notifyLocalRewards(selectedChallenge);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Unable to notify local challenge rewards: " + exception.getMessage());
        }
        if (config.cluster.role == ChallengeRole.COORDINATOR) {
            try {
                distributeTopRewards(sorted);
            } catch (RuntimeException exception) {
                plugin.getLogger().severe("Unable to distribute challenge rewards: " + exception.getMessage());
            }
        }
    }

    private void notifyLocalRewards(Challenge challenge) {
        Map<Integer, TopReward> rewards = challenge.topRewards().stream()
                .collect(Collectors.toMap(TopReward::place, reward -> reward));
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                int place = getPlaceOfUUID(player.getUniqueId());
                if (place <= 0) continue;
                TopReward reward = rewards.get(place);
                if (reward == null || challenge.giveForAllRewardToTop()) {
                    player.sendMessage(ConfigManager.fmt(config.messages.rewards.forAll,
                            Map.of("0", challenge.forAllMessage())));
                }
                if (reward != null) {
                    player.sendMessage(ConfigManager.fmt(config.messages.rewards.top,
                            Map.of("0", String.valueOf(place), "1", reward.message())));
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Unable to send challenge reward message to "
                        + player.getUniqueId() + ": " + exception.getMessage());
            }
        }
    }

    private <T> List<T> topN(Collection<T> source, int count) {
        if (count <= 0) return List.of();
        List<T> list = new ArrayList<>(source);
        return list.subList(0, Math.min(count, list.size()));
    }

    private int topDisplayLimit() {
        Challenge challenge = getSelectedChallenge();
        if (challenge == null) return 0;
        return challenge.topRewards().isEmpty() ? 10 : challenge.topRewards().size();
    }

    public String buildTopMessage(Map<UUID, BigDecimal> sorted) {
        Challenge challenge = getSelectedChallenge();
        if (challenge == null) return "";
        Map<Integer, TopReward> rewards = challenge.topRewards().stream()
                .collect(Collectors.toMap(TopReward::place, reward -> reward));
        StringBuilder globalTop = new StringBuilder();
        int place = 0;
        for (Map.Entry<UUID, BigDecimal> entry : topN(sorted.entrySet(), topDisplayLimit())) {
            place++;
            String line = format(config.messages.chat.top.template,
                    String.valueOf(place),
                    plugin.getCacheManager().resolvePlayerName(entry.getKey()),
                    ChallengeAmount.canonical(entry.getValue()));
            TopReward reward = rewards.get(place);
            if (reward != null) {
                line = line.replace("{4}", reward.message());
                int addNumber = challenge.addAllTopIntoDb()
                        ? challenge.topRewards().size() - place + 1
                        : (place == 1 ? 1 : 0);
                if (addNumber > 0) {
                    String label = addNumber > 1
                            ? config.messages.chat.top.templatePoints.points
                            : config.messages.chat.top.templatePoints.point;
                    line = line.replace("{3}", format(config.messages.chat.top.templatePoints.display,
                            String.valueOf(addNumber), label));
                } else {
                    line = line.replace("{3}", legacy(config.messages.chat.top.templatePoints.defaultValue));
                }
            } else {
                line = line.replace("{3}", legacy(config.messages.chat.top.templatePoints.defaultValue));
                line = line.replace("{4}", "");
            }
            globalTop.append(line);
            if (place < sorted.size()) globalTop.append("§r \n");
        }

        StringBuilder result = new StringBuilder();
        List<Component> template = config.messages.chat.top.message;
        for (int i = 0; i < template.size(); i++) {
            result.append(format(template.get(i), challenge.message(), globalTop.toString()));
            if (i < template.size() - 1) result.append("§r \n");
        }
        return result.toString();
    }

    private void distributeTopRewards(Map<UUID, BigDecimal> sorted) {
        Challenge challenge = getSelectedChallenge();
        if (challenge == null) return;
        Map<Integer, TopReward> rewards = challenge.topRewards().stream()
                .collect(Collectors.toMap(TopReward::place, reward -> reward));
        Map<UUID, Integer> rankingPoints = new LinkedHashMap<>();
        int place = 0;
        for (Map.Entry<UUID, BigDecimal> entry : sorted.entrySet()) {
            place++;
            UUID playerId = entry.getKey();
            TopReward reward = rewards.get(place);
            boolean top = reward != null;

            if (!top || challenge.giveForAllRewardToTop()) {
                for (String command : challenge.forAllCommands()) sendConsoleCommand(command, playerId);
            }
            if (!top) continue;
            for (String command : reward.commands()) sendConsoleCommand(command, playerId);
            if (challenge.addAllTopIntoDb() || place == 1) {
                int points = challenge.addAllTopIntoDb() ? rewards.size() - place + 1 : 1;
                if (points > 0) rankingPoints.merge(playerId, points, Math::addExact);
            }
        }
        if (!rankingPoints.isEmpty()) {
            plugin.applyRankingPointsAsync(rankingPoints).exceptionally(error -> {
                plugin.getLogger().severe("Unable to persist challenge ranking rewards: " + error.getMessage());
                return null;
            });
        }
    }

    public void sendConsoleCommand(String command, UUID playerId) {
        if (playerId == null) return;
        Optional<String> resolvedName = plugin.getCacheManager().resolveRewardPlayerName(playerId);
        if (resolvedName.isEmpty()) {
            plugin.getLogger().severe("Challenge reward command was not executed because EdenPlayers has no safe "
                    + "profile name for " + playerId + ".");
            return;
        }
        String name = resolvedName.get();
        String parsed = command.replace("%player%", name);
        try {
            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), parsed);
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Unable to execute challenge reward command for " + playerId
                    + ": " + exception.getMessage());
        }
    }

    public void disablePlugin() {
        ensureMainThread();
        rankingRefreshEpoch++;
        rankingRefreshInFlight = false;
        if (stateSyncInFlight != null) stateSyncInFlight.cancel(false);
        stateSyncInFlight = null;
        stateSyncAttempt++;
        if (config.cluster.role == ChallengeRole.COORDINATOR && activeRun != null) {
            ChallengeStateAction stop = ChallengeStateAction.stop(activeRun, rankingRevision);
            applyStopInternal(stop.runId(), stop.generation());
            try {
                plugin.getBus().publish(stop);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Unable to publish the clean coordinator shutdown: "
                        + exception.getMessage());
            }
        } else {
            clearActiveRun();
        }
        cancel(intervalTask);
        cancel(stateSyncTask);
        intervalTask = null;
        stateSyncTask = null;
        trackingService.close();
        trackingService = ChallengeTrackingService.unavailable();
    }

    private void clearActiveRun() {
        closeTrackingImmediately();
        cancel(runTicker);
        cancel(finalizationTask);
        cancel(earliestFinalizationTask);
        cancel(drainReminderTask);
        cancel(progressFlushTask);
        runTicker = null;
        finalizationTask = null;
        earliestFinalizationTask = null;
        drainReminderTask = null;
        progressFlushTask = null;
        IllegalStateException closed = new IllegalStateException("Challenge run was closed.");
        failBufferedProgress(closed);
        for (PendingBatch batch : new ArrayList<>(pendingBatches.values())) failBatch(batch, closed);
        activeRun = null;
        activeChallenge = null;
        effectiveEndsAt = 0L;
        draining = false;
        runPhase = ChallengeRunPhase.IDLE;
        trackingDrainComplete = false;
        startAnnouncementSent = false;
        lastCountdownValue = Long.MIN_VALUE;
        drainAcknowledgements.clear();
        expectedParticipants.clear();
        batchDeduplicator.clear();
        ledger.clear();
        sortedScoresRevision = 0L;
        sortedScoresCache = new LinkedHashMap<>();
        sortedScoreEntriesCache = List.of();
        placeCache = Map.of();
    }

    private void resetParticipantSequence() {
        sequenceRunId = null;
        nextBatchSequence = 1L;
    }

    private void closeTrackingImmediately() {
        ChallengeTrackingSession session = trackingSession;
        trackingSession = ChallengeTrackingSession.NOOP;
        try {
            session.close();
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Unable to close challenge tracking session: " + exception.getMessage());
        }
    }

    private boolean matchesActive(UUID runId, long generation) {
        return activeRun != null && runId != null && activeRun.matches(runId, generation);
    }

    private boolean isActiveAuthority(String authorityInstanceId) {
        return activeRun != null && authorityInstanceId != null
                && authorityInstanceId.equals(activeRun.authorityInstanceId());
    }

    private boolean isKnownAuthority(String authorityInstanceId) {
        return knownAuthorityInstanceId != null && knownAuthorityInstanceId.equals(authorityInstanceId);
    }

    private void publishDrainAck() {
        if (activeRun == null) return;
        requestState(activeRun.authorityInstanceId(), true);
    }

    private boolean rejectParticipantControl() {
        plugin.getLogger().warning("Challenge control commands are only accepted on the COORDINATOR server.");
        return false;
    }

    private boolean runOnMain(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return true;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, task);
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Unable to schedule challenge task: " + exception.getMessage());
            return false;
        }
    }

    private void cancel(BukkitTask task) {
        if (task != null) task.cancel();
    }

    private void ensureMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Challenge state must be mutated on the Bukkit main thread.");
        }
    }

    private String format(Component template, String... arguments) {
        Map<String, Object> values = new HashMap<>();
        for (int i = 0; i < arguments.length; i++) values.put(String.valueOf(i), arguments[i]);
        return legacy(ConfigManager.fmt(template, values));
    }

    private String legacy(Component component) {
        return LEGACY.serialize(component == null ? Component.empty() : component);
    }

    private record PendingProgress(ChallengeProgressMutation mutation, CompletableFuture<Void> completion) {
    }

    private static final class PendingBatch {
        private final ChallengeRun run;
        private final ChallengeProgressBatchRequest request;
        private final List<PendingProgress> entries;
        private CompletableFuture<ChallengeProgressBatchResponse> inFlight;
        private int attempt = 1;

        private PendingBatch(ChallengeRun run, ChallengeProgressBatchRequest request,
                             List<PendingProgress> entries) {
            this.run = run;
            this.request = request;
            this.entries = List.copyOf(entries);
        }

        private ChallengeRun run() {
            return run;
        }

        private ChallengeProgressBatchRequest request() {
            return request;
        }

        private List<PendingProgress> entries() {
            return entries;
        }
    }
}
