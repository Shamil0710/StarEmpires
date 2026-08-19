package com.spacesim.simulation;

import com.spacesim.persistence.EntityId;
import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationCalculator;
import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationProfile.InteractionActivationBand;
import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationProfile.InteractionActivationInput;
import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationProfile.RelevanceInput;
import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationProfile.RepresentationLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Production Stage-20 relevance scheduler over the accepted materialization boundary.
 *
 * <p>The scheduler treats only {@link RepresentationLevel#DORMANT} as requiring absence of the
 * Ashley runtime entity. STRATEGIC remains a live persistent ECS representation processed by the
 * existing reduced-rate simulation, so demotion does not create a second off-screen economy.
 * ACTIVE_LOCAL and TACTICAL likewise stay live while downstream systems select their appropriate
 * detail/cadence. Representation level is recomputable and intentionally not persisted.</p>
 */
public final class Stage20RepresentationScheduler {
    private final Stage20MaterializationService materialization;
    private final Map<EntityId, RepresentationLevel> trackedLevels = new HashMap<>();

    /**
     * Creates a relevance scheduler over an existing Stage-20 materialization service.
     *
     * @param materialization reversible materialization boundary
     */
    public Stage20RepresentationScheduler(Stage20MaterializationService materialization) {
        this.materialization = Objects.requireNonNull(materialization, "materialization");
    }

    /**
     * Synchronizes one Stage-20 entity to the representation required by authoritative relevance.
     *
     * <p>A currently live untracked entity is conservatively treated as STRATEGIC because the
     * existing {@link SimulationSession} is already a valid persistent/reduced-rate runtime. A
     * currently dematerialized untracked entity is DORMANT. Direct tactical/local/strategic/due-event
     * relevance promotes synchronously before this method returns.</p>
     *
     * @param id stable persistent entity ID with registered Stage-20 physical state
     * @param relevance current causal relevance facts
     * @return deterministic representation transition result
     */
    public RepresentationTransition synchronize(EntityId id, RelevanceInput relevance) {
        EntityId checkedId = Objects.requireNonNull(id, "id");
        RelevanceInput checkedRelevance = Objects.requireNonNull(relevance, "relevance");
        if (materialization.physicalState(checkedId).isEmpty()) {
            throw new IllegalStateException("Stage-20 representation scheduling requires physical authority: " + checkedId);
        }

        RepresentationLevel required = Stage20MaterializationLodCalibrationCalculator.requiredRepresentation(
                checkedRelevance);
        RepresentationLevel previous = trackedLevels.getOrDefault(
                checkedId,
                materialization.isDematerialized(checkedId)
                        ? RepresentationLevel.DORMANT
                        : RepresentationLevel.STRATEGIC);

        RuntimeRepresentationAction action = RuntimeRepresentationAction.NONE;
        if (required == RepresentationLevel.DORMANT) {
            if (!materialization.isDematerialized(checkedId)) {
                materialization.dematerialize(checkedId);
                action = RuntimeRepresentationAction.DEMATERIALIZED_RUNTIME;
            }
        } else if (materialization.isDematerialized(checkedId)) {
            materialization.materialize(checkedId);
            action = RuntimeRepresentationAction.MATERIALIZED_RUNTIME;
        }

        trackedLevels.put(checkedId, required);
        return new RepresentationTransition(
                checkedId,
                previous,
                required,
                action,
                previous != required,
                action == RuntimeRepresentationAction.MATERIALIZED_RUNTIME
                        ? Stage20MaterializationService.SYNCHRONOUS_WAKE_LATENCY_SIMULATION_SECONDS
                        : 0d);
    }

    /**
     * Returns the last relevance-derived representation level when one has been scheduled.
     *
     * @param id stable persistent entity ID
     * @return tracked level, or null when this scheduler has not processed the entity
     */
    public RepresentationLevel trackedLevel(EntityId id) {
        return id == null ? null : trackedLevels.get(id);
    }

    /**
     * Derives a context-specific promotion threshold using the scheduler's bounded wake latency.
     *
     * <p>The synchronous materialization boundary has zero simulation-time wake latency once
     * invoked. Therefore a physically closed interaction class currently promotes no later than its
     * own explicit interaction envelope. This method does not create a universal LOD radius; callers
     * must supply an accepted interaction envelope and provenance for the concrete interaction class.</p>
     *
     * @param interactionEnvelopeRadiusM accepted physical interaction envelope radius
     * @param maximumClosingSpeedMps accepted maximum relevant closing speed
     * @param provenance exact accepted physical interaction provenance
     * @return physical promotion threshold for that concrete interaction class
     */
    public InteractionActivationBand deriveActivationBand(
            double interactionEnvelopeRadiusM,
            double maximumClosingSpeedMps,
            String provenance) {
        return Stage20MaterializationLodCalibrationCalculator.deriveActivationBand(
                new InteractionActivationInput(
                        interactionEnvelopeRadiusM,
                        maximumClosingSpeedMps,
                        Stage20MaterializationService.SYNCHRONOUS_WAKE_LATENCY_SIMULATION_SECONDS,
                        provenance));
    }

    /** Runtime object action taken while applying a relevance transition. */
    public enum RuntimeRepresentationAction {
        /** No Ashley add/remove was required. */
        NONE,
        /** Runtime Ashley representation was removed while persistent authority remained. */
        DEMATERIALIZED_RUNTIME,
        /** Runtime Ashley representation was synchronously restored. */
        MATERIALIZED_RUNTIME
    }

    /**
     * Result of one relevance synchronization.
     *
     * @param id stable persistent entity ID
     * @param previousLevel prior scheduler/inferred representation level
     * @param requiredLevel current relevance-required representation level
     * @param runtimeAction Ashley runtime action performed
     * @param levelChanged whether the computational representation level changed
     * @param wakeLatencySimulationSeconds simulation-time latency paid by runtime materialization
     */
    public record RepresentationTransition(
            EntityId id,
            RepresentationLevel previousLevel,
            RepresentationLevel requiredLevel,
            RuntimeRepresentationAction runtimeAction,
            boolean levelChanged,
            double wakeLatencySimulationSeconds) {
        /**
         * Validates one representation transition result.
         *
         * @param id stable persistent entity ID
         * @param previousLevel previous representation level
         * @param requiredLevel required representation level
         * @param runtimeAction runtime materialization action
         * @param levelChanged whether the level changed
         * @param wakeLatencySimulationSeconds simulation-time wake latency
         */
        public RepresentationTransition {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(previousLevel, "previousLevel");
            Objects.requireNonNull(requiredLevel, "requiredLevel");
            Objects.requireNonNull(runtimeAction, "runtimeAction");
            if (!Double.isFinite(wakeLatencySimulationSeconds) || wakeLatencySimulationSeconds < 0d) {
                throw new IllegalArgumentException("wakeLatencySimulationSeconds must be non-negative and finite");
            }
            if (levelChanged != (previousLevel != requiredLevel)) {
                throw new IllegalArgumentException("levelChanged must match previous/required level comparison");
            }
        }
    }
}
