package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationProfile.DistanceBandAuthority;
import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationProfile.InteractionActivationBand;
import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationProfile.InteractionActivationInput;
import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationProfile.RelevanceInput;
import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationProfile.RepresentationLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20MaterializationLodCalibrationProfileTest {
    @Test
    void currentProfileIsDeterministicAndKeepsCanonicalFourLevelOrder() {
        Stage20MaterializationLodCalibrationProfile first =
                Stage20MaterializationLodCalibrationCalculator.calibrate();
        Stage20MaterializationLodCalibrationProfile second =
                Stage20MaterializationLodCalibrationCalculator.calibrate();

        assertEquals(first, second);
        assertEquals(Stage20MaterializationLodCalibrationProfile.CURRENT_VERSION, first.version());
        assertEquals(4, first.representationPolicies().size());
        assertEquals(RepresentationLevel.DORMANT, first.representationPolicies().get(0).level());
        assertEquals(RepresentationLevel.STRATEGIC, first.representationPolicies().get(1).level());
        assertEquals(RepresentationLevel.ACTIVE_LOCAL, first.representationPolicies().get(2).level());
        assertEquals(RepresentationLevel.TACTICAL, first.representationPolicies().get(3).level());
        assertTrue(first.representationPolicies().stream().allMatch(value -> value.authoritativeStateRetained()));
        assertTrue(first.representationPolicies().stream().noneMatch(value -> value.renderingRequired()));
        assertEquals(0.05d, first.runtimeCadenceEvidence().tacticalTickSeconds(), 1e-12d);
        assertEquals(0.1d, first.runtimeCadenceEvidence().activeLocalFixedStepSeconds(), 1e-12d);
        assertTrue(first.runtimeCadenceEvidence().strategicReducedSteppingAvailable());
    }

    @Test
    void relevancePriorityPromotesAndDemotesWithoutDependingOnRenderDistance() {
        assertEquals(
                RepresentationLevel.DORMANT,
                Stage20MaterializationLodCalibrationCalculator.requiredRepresentation(
                        new RelevanceInput(false, false, false, false)));
        assertEquals(
                RepresentationLevel.STRATEGIC,
                Stage20MaterializationLodCalibrationCalculator.requiredRepresentation(
                        new RelevanceInput(false, false, true, false)));
        assertEquals(
                RepresentationLevel.STRATEGIC,
                Stage20MaterializationLodCalibrationCalculator.requiredRepresentation(
                        new RelevanceInput(false, false, false, true)));
        assertEquals(
                RepresentationLevel.ACTIVE_LOCAL,
                Stage20MaterializationLodCalibrationCalculator.requiredRepresentation(
                        new RelevanceInput(false, true, true, true)));
        assertEquals(
                RepresentationLevel.TACTICAL,
                Stage20MaterializationLodCalibrationCalculator.requiredRepresentation(
                        new RelevanceInput(true, true, true, true)));
    }

    @Test
    void unresolvedCurrentDistanceBandsContainNoFallbackViewportOrWeaponRadius() {
        Stage20MaterializationLodCalibrationProfile profile =
                Stage20MaterializationLodCalibrationCalculator.calibrate();

        assertEquals(2, profile.currentDistanceBandClosures().size());
        assertTrue(profile.currentDistanceBandClosures().stream()
                .allMatch(value -> value.authority() == DistanceBandAuthority.UNRESOLVED));
        assertTrue(profile.currentDistanceBandClosures().stream()
                .allMatch(value -> value.activationDistanceM().isEmpty()));
        assertTrue(profile.unresolvedConstraints().stream()
                .anyMatch(value -> value.contains("entity_lifecycle_remove_is_structural_deletion")));
        assertTrue(profile.unresolvedConstraints().stream()
                .anyMatch(value -> value.contains("probe_distances_are_not_universal_materialization_radii")));
    }

    @Test
    void explicitPhysicalWakeInputsControlPromotionDistance() {
        InteractionActivationBand baseline = Stage20MaterializationLodCalibrationCalculator.deriveActivationBand(
                new InteractionActivationInput(10_000d, 2_000d, 0.5d, "accepted.test.baseline"));
        InteractionActivationBand faster = Stage20MaterializationLodCalibrationCalculator.deriveActivationBand(
                new InteractionActivationInput(10_000d, 4_000d, 0.5d, "accepted.test.faster"));
        InteractionActivationBand slowerWake = Stage20MaterializationLodCalibrationCalculator.deriveActivationBand(
                new InteractionActivationInput(10_000d, 2_000d, 1.0d, "accepted.test.slower_wake"));
        InteractionActivationBand widerEnvelope = Stage20MaterializationLodCalibrationCalculator.deriveActivationBand(
                new InteractionActivationInput(20_000d, 2_000d, 0.5d, "accepted.test.wider"));

        assertEquals(1_000d, baseline.closingDuringWakeM(), 1e-9d);
        assertEquals(11_000d, baseline.activationDistanceM(), 1e-9d);
        assertTrue(faster.activationDistanceM() > baseline.activationDistanceM());
        assertTrue(slowerWake.activationDistanceM() > baseline.activationDistanceM());
        assertTrue(widerEnvelope.activationDistanceM() > baseline.activationDistanceM());
        assertEquals(DistanceBandAuthority.EXPLICIT_PHYSICAL_INPUT, baseline.authority());
    }

    @Test
    void renderCullingNeverChangesSimulationRelevanceOrDeletesAuthority() {
        RelevanceInput tactical = new RelevanceInput(true, false, false, false);
        var visible = Stage20MaterializationLodCalibrationCalculator.decideRendering(tactical, true);
        var culled = Stage20MaterializationLodCalibrationCalculator.decideRendering(tactical, false);

        assertEquals(RepresentationLevel.TACTICAL, visible.representationLevel());
        assertEquals(RepresentationLevel.TACTICAL, culled.representationLevel());
        assertTrue(visible.rendered());
        assertFalse(culled.rendered());
        assertTrue(visible.authoritativeStateRetained());
        assertTrue(culled.authoritativeStateRetained());
    }
}
