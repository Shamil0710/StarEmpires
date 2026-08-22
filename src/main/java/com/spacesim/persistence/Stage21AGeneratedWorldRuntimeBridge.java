package com.spacesim.persistence;

import com.spacesim.world.FactionActorObservationSnapshot;
import com.spacesim.world.FactionLivingActorRuntime;
import com.spacesim.world.FactionLivingActorRuntime.RuntimeReviewBatch;
import com.spacesim.world.FactionLivingActorState;
import com.spacesim.world.FactionLivingActorState.EventWakeup;
import com.spacesim.world.WorldSimulation;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.function.Function;

/**
 * Stage-21A composition boundary joining the accepted Stage-20 live generated world with the
 * autonomous-faction lifecycle runtime.
 *
 * <p>The bridge does not manufacture actor observations from hidden world state. Callers must
 * publish explicit {@link FactionActorObservationSnapshot} instances through allowed observation
 * channels before a strategic review can consume them.</p>
 */
public final class Stage21AGeneratedWorldRuntimeBridge {
    private Stage21AGeneratedWorldRuntimeBridge() {
        throw new AssertionError("No instances");
    }

    /**
     * Adds Stage-21A lifecycle ownership to an already composed live Stage-20 generated world.
     *
     * @param stage20Runtime accepted live Stage-20 generated-world runtime
     * @param autonomousFactionContentIds exact stable faction IDs allowed to act autonomously
     * @param cadenceTicks positive ordinary review cadence
     * @return composed Stage-21A live runtime
     */
    public static LiveRuntime materializeBootstrap(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime stage20Runtime,
            Collection<String> autonomousFactionContentIds,
            long cadenceTicks) {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime stage20 = Objects.requireNonNull(
                stage20Runtime, "Stage-20 runtime not set");
        Collection<String> autonomous = Objects.requireNonNull(
                autonomousFactionContentIds, "Autonomous faction IDs not set");
        HashSet<String> unique = new HashSet<>();
        for (String rawId : autonomous) {
            String factionId = requireText(rawId, "Autonomous faction ID");
            if (!unique.add(factionId)) {
                continue;
            }
            if (stage20.world().findFactionRuntimeId(factionId).isEmpty()) {
                throw new IllegalArgumentException(
                        "Autonomous faction is absent from Stage-20 world authority: " + factionId);
            }
        }
        long currentTick = stage20.world().getAuthoritativeWorldTick();
        FactionLivingActorRuntime actors = FactionLivingActorRuntime.bootstrap(
                unique,
                currentTick,
                cadenceTicks);
        return new LiveRuntime(stage20, actors);
    }

    /**
     * Restores Stage 20 and Stage 21A together without recalculating actor deadlines or wakeups.
     *
     * @param checkpoint exact Stage-21A generated-runtime checkpoint
     * @return independent restored live runtime
     */
    public static LiveRuntime restore(Stage21AGeneratedWorldRuntimePersistentState checkpoint) {
        Stage21AGeneratedWorldRuntimePersistentState saved = Objects.requireNonNull(
                checkpoint, "Stage-21A checkpoint not set");
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime stage20 =
                Stage20GeneratedWorldRuntimeBridge.restore(saved.stage20Runtime());
        FactionLivingActorRuntime actors = FactionLivingActorRuntime.restore(saved.livingActors());
        return new LiveRuntime(stage20, actors);
    }

    /** Live Stage-21A runtime composed over the accepted Stage-20 generated-world authority. */
    public static final class LiveRuntime {
        private final Stage20GeneratedWorldRuntimeBridge.LiveRuntime stage20;
        private final FactionLivingActorRuntime actors;

        private LiveRuntime(
                Stage20GeneratedWorldRuntimeBridge.LiveRuntime stage20,
                FactionLivingActorRuntime actors) {
            this.stage20 = Objects.requireNonNull(stage20, "Stage-20 runtime not set");
            this.actors = Objects.requireNonNull(actors, "Living actor runtime not set");
        }

        /** @return underlying accepted Stage-20 live runtime */
        public Stage20GeneratedWorldRuntimeBridge.LiveRuntime stage20() {
            return stage20;
        }

        /** @return Stage-21A lifecycle owner; it contains no world command authority */
        public FactionLivingActorRuntime actors() {
            return actors;
        }

        /**
         * Advances only the existing Stage-20 physical/economic world.
         *
         * <p>Strategic review is intentionally not hidden inside the frame loop because it requires
         * an explicit actor-bounded observation publication step.</p>
         *
         * @param realDeltaSeconds non-negative render-frame duration
         * @return ordinary Stage-20 world advance report
         */
        public WorldSimulation.AdvanceReport advanceFrame(float realDeltaSeconds) {
            return stage20.advanceFrame(realDeltaSeconds);
        }

        /**
         * Adds one delivered event wakeup to an autonomous actor.
         *
         * @param factionContentId stable autonomous faction identity
         * @param wakeup delivered event provenance and eligibility
         */
        public void registerWakeup(String factionContentId, EventWakeup wakeup) {
            actors.registerWakeup(factionContentId, wakeup);
        }

        /**
         * Runs one bounded strategic review batch at the exact authoritative world tick.
         *
         * @param maxReviews hard cap on expensive actor reviews
         * @param cadenceTicks positive ordinary review cadence
         * @param snapshotPublisher actor-bounded snapshot publisher invoked only for selected actors
         * @return deterministic bounded review report
         */
        public RuntimeReviewBatch reviewDue(
                int maxReviews,
                long cadenceTicks,
                Function<String, FactionActorObservationSnapshot> snapshotPublisher) {
            return actors.reviewDue(
                    stage20.world().getAuthoritativeWorldTick(),
                    maxReviews,
                    cadenceTicks,
                    snapshotPublisher);
        }

        /**
         * Captures Stage 20 and Stage 21A as one atomic persistence composition.
         *
         * @return complete validated current Stage-21A checkpoint
         */
        public Stage21AGeneratedWorldRuntimePersistentState captureState() {
            return new Stage21AGeneratedWorldRuntimePersistentState(
                    Stage21AGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                    Stage21AGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                    stage20.captureState(),
                    actors.capture());
        }

        /**
         * Finds one current Stage-21A lifecycle row without exposing any mutable world authority.
         *
         * @param factionContentId stable faction identity
         * @return actor state when the faction is autonomous
         */
        public java.util.Optional<FactionLivingActorState> findActorState(String factionContentId) {
            return actors.findState(factionContentId);
        }
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
