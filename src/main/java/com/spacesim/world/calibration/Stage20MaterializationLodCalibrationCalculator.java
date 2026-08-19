package com.spacesim.world.calibration;

import com.spacesim.ship.LiveTacticalSimulationSession;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationProfile.DistanceBandAuthority;
import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationProfile.DistanceBandClosure;
import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationProfile.InteractionActivationBand;
import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationProfile.InteractionActivationInput;
import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationProfile.RelevanceInput;
import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationProfile.RenderCullingDecision;
import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationProfile.RepresentationLevel;
import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationProfile.RepresentationPolicy;
import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationProfile.RuntimeCadenceEvidence;

import java.util.List;
import java.util.OptionalDouble;

/** Derives Stage-20A.9 materialization/LOD evidence without introducing a physical map edge. */
public final class Stage20MaterializationLodCalibrationCalculator {
    private Stage20MaterializationLodCalibrationCalculator() {
        throw new AssertionError("No instances");
    }

    /**
     * Builds the deterministic current materialization/LOD calibration profile.
     *
     * <p>Production provides fixed local/tactical cadence evidence and strategic reduced stepping,
     * but it does not yet provide a persistent↔local materialization scheduler with an accepted
     * maximum wake latency. Therefore current numeric ACTIVE_LOCAL/TACTICAL distance bands remain
     * absent rather than receiving a viewport- or weapon-probe-derived fallback radius.</p>
     *
     * @return current immutable Stage-20A.9 calibration profile
     */
    public static Stage20MaterializationLodCalibrationProfile calibrate() {
        List<RepresentationPolicy> policies = List.of(
                new RepresentationPolicy(
                        RepresentationLevel.DORMANT,
                        true,
                        false,
                        false,
                        false,
                        "no_current_relevance_event_or_due_work_persistent_event_driven_authority_only"),
                new RepresentationPolicy(
                        RepresentationLevel.STRATEGIC,
                        true,
                        false,
                        false,
                        false,
                        "strategic_relevance_or_due_authoritative_event"),
                new RepresentationPolicy(
                        RepresentationLevel.ACTIVE_LOCAL,
                        true,
                        false,
                        true,
                        false,
                        "local_operational_relevance_without_direct_tactical_interaction"),
                new RepresentationPolicy(
                        RepresentationLevel.TACTICAL,
                        true,
                        true,
                        true,
                        false,
                        "direct_tactical_sensor_weapon_combat_or_docking_interaction"));

        double localFixedStepSeconds = Double.parseDouble(
                Float.toString(SimulationSession.DEFAULT_FIXED_STEP_SECONDS));
        RuntimeCadenceEvidence cadence = new RuntimeCadenceEvidence(
                LiveTacticalSimulationSession.TICK_SECONDS,
                localFixedStepSeconds,
                true,
                "LiveTacticalSimulationSession.TICK_SECONDS",
                "SimulationSession.DEFAULT_FIXED_STEP_SECONDS+SimulationClock decimal canonicalization semantics",
                "SimulationClock.advanceStrategicSteps");

        List<DistanceBandClosure> closures = List.of(
                new DistanceBandClosure(
                        RepresentationLevel.ACTIVE_LOCAL,
                        DistanceBandAuthority.UNRESOLVED,
                        OptionalDouble.empty(),
                        "no_accepted_local_relevance_envelope_or_materialization_wake_latency"),
                new DistanceBandClosure(
                        RepresentationLevel.TACTICAL,
                        DistanceBandAuthority.UNRESOLVED,
                        OptionalDouble.empty(),
                        "sensor_weapon_docking_interaction_envelopes_are_context_dependent_and_no_materialization_wake_latency_is_authored"));

        return new Stage20MaterializationLodCalibrationProfile(
                Stage20MaterializationLodCalibrationProfile.CURRENT_VERSION,
                policies,
                cadence,
                closures,
                List.of(
                        "no_production_persistent_to_local_materialization_scheduler_with_bounded_wake_latency",
                        "no_production_lossless_local_to_persistent_dematerialization_service",
                        "entity_lifecycle_remove_is_structural_deletion_not_dematerialization_and_must_not_be_reused_for_lod",
                        "stage20a4_sensor_and_stage20a5_weapon_probe_distances_are_not_universal_materialization_radii",
                        "beam_and_passive_sensor_interaction_envelopes_are_target_state_dependent_not_hard_range_walls",
                        "render_culling_distance_is_presentation_policy_and_remains_separate_from_simulation_relevance",
                        "station_docking_traffic_geometry_remains_unresolved_from_stage20a6_for_station_specific_local_activation"));
    }

    /**
     * Selects the minimum representation required by current authoritative relevance.
     *
     * <p>Priority is monotonic in detail: direct tactical interaction wins over local relevance,
     * which wins over strategic/due-event relevance. Distance and render visibility are deliberately
     * absent from this method, so an off-screen due event or combat interaction cannot disappear.</p>
     *
     * @param input current authoritative relevance facts
     * @return minimum required canonical representation level
     */
    public static RepresentationLevel requiredRepresentation(RelevanceInput input) {
        RelevanceInput checked = java.util.Objects.requireNonNull(input, "input");
        if (checked.directTacticalInteraction()) {
            return RepresentationLevel.TACTICAL;
        }
        if (checked.localOperationalRelevance()) {
            return RepresentationLevel.ACTIVE_LOCAL;
        }
        if (checked.strategicRelevance() || checked.dueAuthoritativeEvent()) {
            return RepresentationLevel.STRATEGIC;
        }
        return RepresentationLevel.DORMANT;
    }

    /**
     * Derives a promotion distance from explicit physical interaction and scheduler wake-up inputs.
     *
     * <p>The result is a look-ahead threshold, not a world boundary: promotion must happen early
     * enough that an actor closing at the accepted maximum speed cannot enter the detailed physical
     * interaction envelope before the materializer's accepted wake latency expires.</p>
     *
     * @param input explicit physical interaction/wake-up authority
     * @return derived physical promotion-distance evidence
     */
    public static InteractionActivationBand deriveActivationBand(InteractionActivationInput input) {
        InteractionActivationInput checked = java.util.Objects.requireNonNull(input, "input");
        double closingDuringWake = checked.maximumClosingSpeedMps() * checked.maximumWakeLatencyS();
        if (!Double.isFinite(closingDuringWake)) {
            throw new IllegalArgumentException("Closing distance exceeds finite double range");
        }
        double activationDistance = checked.interactionEnvelopeRadiusM() + closingDuringWake;
        if (!Double.isFinite(activationDistance)) {
            throw new IllegalArgumentException("Activation distance exceeds finite double range");
        }
        return new InteractionActivationBand(
                checked.interactionEnvelopeRadiusM(),
                closingDuringWake,
                activationDistance,
                DistanceBandAuthority.EXPLICIT_PHYSICAL_INPUT,
                checked.provenance());
    }

    /**
     * Applies presentation visibility without changing the required simulation representation.
     *
     * @param relevance authoritative simulation relevance
     * @param insideRenderWindow whether current presentation chooses to render the object
     * @return rendering decision retaining physical/persistent authority in all cases
     */
    public static RenderCullingDecision decideRendering(RelevanceInput relevance, boolean insideRenderWindow) {
        return new RenderCullingDecision(
                requiredRepresentation(relevance),
                insideRenderWindow,
                true);
    }
}
