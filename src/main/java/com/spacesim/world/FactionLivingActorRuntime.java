package com.spacesim.world;

import com.spacesim.world.FactionLivingActorKernel.ReviewResult;
import com.spacesim.world.FactionLivingActorScheduler.ScheduleBatch;
import com.spacesim.world.FactionLivingActorScheduler.ScheduledReview;
import com.spacesim.world.FactionLivingActorState.EventWakeup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Mutable Stage-21A lifecycle owner for autonomous faction actors.
 *
 * <p>This runtime owns only {@link FactionLivingActorState} rows. It does not own or expose economy,
 * diplomacy, territory, fleets, combat, generated truth, or command execution. Expensive reviews
 * are selected through {@link FactionLivingActorScheduler}; every selected actor receives exactly
 * one caller-published {@link FactionActorObservationSnapshot}.</p>
 */
public final class FactionLivingActorRuntime {
    private final TreeMap<String, FactionLivingActorState> statesByFaction;

    private FactionLivingActorRuntime(Collection<FactionLivingActorState> states) {
        Objects.requireNonNull(states, "Living actor states not set");
        this.statesByFaction = new TreeMap<>();
        for (FactionLivingActorState state : states) {
            FactionLivingActorState checked = Objects.requireNonNull(state, "Living actor state not set");
            if (statesByFaction.putIfAbsent(checked.factionContentId(), checked) != null) {
                throw new IllegalArgumentException(
                        "Duplicate living actor state: " + checked.factionContentId());
            }
        }
    }

    /**
     * Bootstraps one persistent lifecycle row per explicitly autonomous faction.
     *
     * @param autonomousFactionContentIds stable autonomous faction identities
     * @param startTick authoritative current world tick
     * @param cadenceTicks positive ordinary strategic-review cadence
     * @return independent mutable runtime
     */
    public static FactionLivingActorRuntime bootstrap(
            Collection<String> autonomousFactionContentIds,
            long startTick,
            long cadenceTicks) {
        return new FactionLivingActorRuntime(FactionLivingActorBootstrap.bootstrap(
                autonomousFactionContentIds,
                startTick,
                cadenceTicks));
    }

    /**
     * Restores exact persisted lifecycle rows without recalculating deadlines or wakeups.
     *
     * @param states persisted Stage-21A lifecycle rows
     * @return independent mutable runtime
     */
    public static FactionLivingActorRuntime restore(Collection<FactionLivingActorState> states) {
        return new FactionLivingActorRuntime(states);
    }

    /**
     * Captures all actor lifecycle rows in stable faction-ID order.
     *
     * @return immutable deterministic lifecycle snapshot
     */
    public List<FactionLivingActorState> capture() {
        return List.copyOf(statesByFaction.values());
    }

    /**
     * Finds one autonomous actor lifecycle row.
     *
     * @param factionContentId stable faction identity
     * @return current lifecycle state when that faction is autonomous
     */
    public Optional<FactionLivingActorState> findState(String factionContentId) {
        return Optional.ofNullable(statesByFaction.get(requireText(factionContentId, "Faction content ID")));
    }

    /**
     * Adds one persistent event wakeup to an already autonomous faction.
     *
     * @param factionContentId stable autonomous faction identity
     * @param wakeup event provenance and eligibility
     */
    public void registerWakeup(String factionContentId, EventWakeup wakeup) {
        String factionId = requireText(factionContentId, "Faction content ID");
        FactionLivingActorState state = requireState(factionId);
        statesByFaction.put(factionId, state.withWakeup(Objects.requireNonNull(wakeup, "Wakeup not set")));
    }

    /**
     * Replaces only Stage-21A commitment metadata for one autonomous actor.
     *
     * @param factionContentId stable autonomous faction identity
     * @param commitmentUntilTick new non-negative horizon
     */
    public void setCommitmentUntilTick(String factionContentId, long commitmentUntilTick) {
        String factionId = requireText(factionContentId, "Faction content ID");
        FactionLivingActorState state = requireState(factionId);
        statesByFaction.put(factionId, state.withCommitmentUntilTick(commitmentUntilTick));
    }

    /**
     * Executes one atomic bounded review batch using caller-published actor knowledge only.
     *
     * <p>The snapshot publisher is invoked only for scheduler-selected actors. Every returned
     * snapshot must match the selected faction and exact {@code nowTick}. Lifecycle changes are
     * committed only after every selected pure review succeeds, so a failed publisher or invalid
     * observation cannot leave a partially advanced actor batch.</p>
     *
     * @param nowTick authoritative current world tick
     * @param maxReviews hard cap on expensive actor reviews in this invocation
     * @param cadenceTicks positive ordinary review cadence after completion
     * @param snapshotPublisher bounded-observation publisher keyed by selected faction identity
     * @return deterministic scheduling and review report
     */
    public RuntimeReviewBatch reviewDue(
            long nowTick,
            int maxReviews,
            long cadenceTicks,
            Function<String, FactionActorObservationSnapshot> snapshotPublisher) {
        Function<String, FactionActorObservationSnapshot> publisher = Objects.requireNonNull(
                snapshotPublisher, "Observation snapshot publisher not set");
        ScheduleBatch schedule = FactionLivingActorScheduler.selectDue(
                statesByFaction.values(), nowTick, maxReviews);
        if (schedule.selected().isEmpty()) {
            return new RuntimeReviewBatch(schedule, List.of());
        }

        ArrayList<ReviewResult> reviews = new ArrayList<>(schedule.selected().size());
        TreeMap<String, FactionLivingActorState> updated = new TreeMap<>();
        for (ScheduledReview selected : schedule.selected()) {
            FactionActorObservationSnapshot snapshot = Objects.requireNonNull(
                    publisher.apply(selected.factionContentId()),
                    "Observation snapshot publisher returned null");
            if (!selected.factionContentId().equals(snapshot.factionContentId())) {
                throw new IllegalArgumentException(
                        "Observation snapshot faction differs from scheduled actor");
            }
            if (snapshot.observedAtTick() != nowTick) {
                throw new IllegalArgumentException(
                        "Observation snapshot tick must equal the runtime review tick");
            }
            ReviewResult result = FactionLivingActorKernel.review(
                    requireState(selected.factionContentId()),
                    snapshot,
                    selected,
                    cadenceTicks);
            reviews.add(result);
            updated.put(selected.factionContentId(), result.updatedState());
        }
        updated.forEach(statesByFaction::put);
        return new RuntimeReviewBatch(schedule, reviews);
    }

    /**
     * One atomic runtime review result.
     *
     * @param schedule bounded scheduler report
     * @param reviews completed pure actor reviews in scheduler order
     */
    public record RuntimeReviewBatch(ScheduleBatch schedule, List<ReviewResult> reviews) {
        /** Validates one immutable batch report. */
        public RuntimeReviewBatch {
            Objects.requireNonNull(schedule, "Schedule batch not set");
            reviews = List.copyOf(Objects.requireNonNull(reviews, "Review results not set"));
            if (reviews.size() != schedule.selected().size()) {
                throw new IllegalArgumentException(
                        "Completed review count must match scheduler selection count");
            }
            for (int index = 0; index < reviews.size(); index++) {
                if (!reviews.get(index).scheduledReview().equals(schedule.selected().get(index))) {
                    throw new IllegalArgumentException(
                            "Review results must preserve scheduler selection order");
                }
            }
        }
    }

    private FactionLivingActorState requireState(String factionContentId) {
        FactionLivingActorState state = statesByFaction.get(factionContentId);
        if (state == null) {
            throw new IllegalArgumentException("Unknown autonomous faction: " + factionContentId);
        }
        return state;
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
