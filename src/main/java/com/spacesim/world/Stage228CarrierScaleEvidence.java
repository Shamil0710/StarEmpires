package com.spacesim.world;

import com.spacesim.content.Stage228CarrierCounterplayEvidence;
import com.spacesim.content.Stage228CarrierCounterplayEvidence.CarrierBayVector;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * M22.8L deterministic scale/performance evidence for carrier small-craft operations.
 *
 * <p>The profile deliberately separates persistent craft count from per-tick work. Dormant craft
 * remain persistent rows and receive no render-rate or exact-tactical update merely because they
 * exist. Flight-deck scheduling is bounded by one pass over current deck profiles plus one pass over
 * queued operations. Exact Stage-19 materialization remains bounded to the explicitly selected local
 * participant set.</p>
 */
public final class Stage228CarrierScaleEvidence {
    /** Semantic version of the L scale evidence surface. */
    public static final String VERSION = "m22.8l.carrier_scale_evidence.v1";

    private Stage228CarrierScaleEvidence() {
        throw new AssertionError("utility class");
    }

    /**
     * Derives small, medium and dense deterministic carrier scenarios from accepted K physical
     * carrier capacity.
     *
     * @return immutable ascending scale scenarios
     */
    public static List<ScaleScenario> deriveCurrent() {
        var balance = Stage228CarrierCounterplayEvidence.deriveCurrent();
        int craftPerCarrier = conservativeCraftPerCarrier(
                balance.carrierBayVectors().values().stream().toList());
        return List.of(
                scenario(ScenarioSize.SMALL, 1, craftPerCarrier, 1, 1, 2),
                scenario(ScenarioSize.MEDIUM, 8, craftPerCarrier, 8, 8, 12),
                scenario(ScenarioSize.DENSE, 32, craftPerCarrier, 32, 32, 32));
    }

    private static int conservativeCraftPerCarrier(List<CarrierBayVector> carriers) {
        int value = carriers.stream()
                .flatMap(carrier -> carrier.boundedCapacityByRole().values().stream())
                .filter(count -> count > 0)
                .min(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException(
                        "M22.8L requires at least one physically embarkable production craft"));
        if (value <= 0) {
            throw new IllegalStateException("conservative carrier capacity must be positive");
        }
        return value;
    }

    private static ScaleScenario scenario(
            ScenarioSize size,
            int carrierCount,
            int craftPerCarrier,
            int deckProfiles,
            int queuedDeckOperations,
            int requestedLocalTacticalCraft) {
        int persistentCraft = Math.multiplyExact(carrierCount, craftPerCarrier);
        int localTacticalCraft = Math.min(persistentCraft, requestedLocalTacticalCraft);
        return new ScaleScenario(
                size,
                carrierCount,
                craftPerCarrier,
                persistentCraft,
                deckProfiles,
                queuedDeckOperations,
                Math.addExact(deckProfiles, queuedDeckOperations),
                localTacticalCraft,
                Math.subtractExact(persistentCraft, localTacticalCraft),
                0);
    }

    /** Deterministic representative scale class. */
    public enum ScenarioSize {
        /** One production carrier and its physically bounded embarked wing. */ SMALL,
        /** Multi-carrier task force. */ MEDIUM,
        /** Dense multi-carrier force used for bounded-work acceptance. */ DENSE
    }

    /**
     * One deterministic L scale scenario.
     *
     * @param size representative scale class
     * @param carrierCount production carrier count
     * @param craftPerCarrier conservative physical craft capacity per carrier
     * @param persistentCraftCount total persistent individual craft rows
     * @param deckProfileCount physical flight-deck profiles visited per authoritative tick
     * @param queuedDeckOperationCount queued launch/recovery rows scanned once per tick
     * @param deckSchedulingWorkUpperBound exact linear profile-plus-queue work-unit bound
     * @param localTacticalCraftCount exact-local craft admitted to Stage-19 materialization
     * @param dormantCraftCount persistent craft not in the local tactical participant set
     * @param dormantRenderRateUpdates world-wide render-rate updates granted to dormant craft
     */
    public record ScaleScenario(
            ScenarioSize size,
            int carrierCount,
            int craftPerCarrier,
            int persistentCraftCount,
            int deckProfileCount,
            int queuedDeckOperationCount,
            int deckSchedulingWorkUpperBound,
            int localTacticalCraftCount,
            int dormantCraftCount,
            int dormantRenderRateUpdates) {
        /**
         * Validates one immutable scale scenario.
         *
         * @param size representative scale class
         * @param carrierCount production carrier count
         * @param craftPerCarrier conservative physical craft capacity per carrier
         * @param persistentCraftCount total persistent individual craft rows
         * @param deckProfileCount physical flight-deck profiles
         * @param queuedDeckOperationCount queued launch/recovery rows
         * @param deckSchedulingWorkUpperBound linear scheduler work bound
         * @param localTacticalCraftCount exact-local Stage-19 participant count
         * @param dormantCraftCount persistent non-local craft count
         * @param dormantRenderRateUpdates render-rate updates granted to dormant craft
         */
        public ScaleScenario {
            Objects.requireNonNull(size, "size");
            if (carrierCount <= 0
                    || craftPerCarrier <= 0
                    || persistentCraftCount <= 0
                    || deckProfileCount < 0
                    || queuedDeckOperationCount < 0
                    || deckSchedulingWorkUpperBound < 0
                    || localTacticalCraftCount < 0
                    || dormantCraftCount < 0
                    || dormantRenderRateUpdates < 0) {
                throw new IllegalArgumentException("invalid M22.8L scale scenario");
            }
            if (persistentCraftCount != Math.multiplyExact(carrierCount, craftPerCarrier)) {
                throw new IllegalArgumentException(
                        "persistent craft count must follow physical carrier capacity");
            }
            if (deckSchedulingWorkUpperBound
                    != Math.addExact(deckProfileCount, queuedDeckOperationCount)) {
                throw new IllegalArgumentException(
                        "deck scheduling bound must remain profiles + queued operations");
            }
            if (localTacticalCraftCount + dormantCraftCount != persistentCraftCount) {
                throw new IllegalArgumentException(
                        "local plus dormant craft must equal persistent craft count");
            }
            if (dormantRenderRateUpdates != 0) {
                throw new IllegalArgumentException(
                        "M22.8L forbids world-wide render-rate updates for dormant craft");
            }
        }
    }
}
