package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20TopologyQualityCalibrationProfileTest {
    @Test
    void currentProfileIsDeterministicAndClosesAllRequiredTopologyBands() {
        Stage20TopologyQualityCalibrationProfile first = Stage20TopologyQualityCalibrationProfile.deriveCurrent();
        Stage20TopologyQualityCalibrationProfile second = Stage20TopologyQualityCalibrationProfile.deriveCurrent();

        assertEquals(first, second);
        assertEquals(Stage20TopologyQualityCalibrationProfile.CURRENT_VERSION, first.version());
        assertEquals(CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE, first.authority());
        assertEquals(Stage20IntersystemCadenceCalibrationProfile.CURRENT_VERSION, first.cadenceProfileVersion());
        assertTrue(first.stage22ReviewRequired());
        assertTrue(first.closesStage20BEntryCoverage());
    }

    @Test
    void regionalHopBandIsInheritedFromAcceptedThreeToFiveHopCadence() {
        Stage20TopologyQualityCalibrationProfile profile = Stage20TopologyQualityCalibrationProfile.deriveCurrent();

        assertEquals(3, profile.regionalHopDistanceBand().minInclusive());
        assertEquals(5, profile.regionalHopDistanceBand().maxInclusive());
        assertTrue(profile.provenance().stream()
                .anyMatch(value -> value.contains(Stage20IntersystemCadenceCalibrationProfile.CURRENT_VERSION)));
    }

    @Test
    void structuralBudgetsPreserveAlternatesWithoutEliminatingFrontierChokepoints() {
        Stage20TopologyQualityCalibrationProfile profile = Stage20TopologyQualityCalibrationProfile.deriveCurrent();
        var budget = profile.structuralBudget();

        assertEquals(3, budget.maxLinearCorridorLengthEdges());
        assertEquals(0.20d, budget.maxDegreeOneFraction(), 0d);
        assertEquals(0.50d, budget.minRegionalCycleCoverage(), 0d);
        assertEquals(2, budget.minCoreEdgeDisjointRoutes());
        assertEquals(0.45d, budget.maxSingleGatewayDependency(), 0d);
        assertEquals(2, profile.sectorExitBand().minInclusive());
        assertEquals(4, profile.sectorExitBand().maxInclusive());
        assertEquals(3, profile.hubDegreeBand().minInclusive());
        assertEquals(6, profile.hubDegreeBand().maxInclusive());
    }
}
