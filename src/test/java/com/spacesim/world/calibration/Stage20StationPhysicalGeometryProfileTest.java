package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20StationPhysicalGeometryProfileTest {
    @Test
    void currentProfileIsDeterministicAndCoversAllEightStage18StationArchetypes() {
        Stage20StationPhysicalGeometryProfile first = Stage20StationPhysicalGeometryProfile.deriveCurrent();
        Stage20StationPhysicalGeometryProfile second = Stage20StationPhysicalGeometryProfile.deriveCurrent();

        assertEquals(first, second);
        assertEquals(Stage20StationPhysicalGeometryProfile.CURRENT_VERSION, first.version());
        assertEquals(CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE, first.authority());
        assertTrue(first.stage22ReviewRequired());
        assertEquals(8, first.stationDesigns().size());
        assertEquals(8, first.placementEnvelopes().size());
        assertTrue(first.closesStage20BEntryCoverage());
    }

    @Test
    void exactRequiredStationIdsAreAuthoredWithoutCapacityDerivedFallbacks() {
        Stage20StationPhysicalGeometryProfile profile = Stage20StationPhysicalGeometryProfile.deriveCurrent();
        Set<String> ids = profile.stationDesigns().stream()
                .map(Stage20StationPhysicalGeometryProfile.StationGeometryDesign::stationArchetypeId)
                .collect(Collectors.toSet());

        assertEquals(Set.of(
                "station.infrastructure.mining_outpost",
                "station.infrastructure.volatile_depot",
                "station.infrastructure.refinery_complex",
                "station.infrastructure.industrial_station",
                "station.infrastructure.high_tech_hub",
                "station.infrastructure.trade_logistics_hub",
                "station.infrastructure.naval_ordnance_depot",
                "station.infrastructure.frontier_multipurpose"), ids);
        assertTrue(profile.stationDesigns().stream().allMatch(value ->
                value.provenanceId().startsWith(Stage20StationPhysicalGeometryProfile.SOURCE_DOCUMENT + "#")));
        assertTrue(profile.stationDesigns().stream().allMatch(value -> !value.rationaleId().isBlank()));
    }

    @Test
    void placementEnvelopeUsesExistingStage20A6PhysicalFormulaForEveryStation() {
        Stage20StationPhysicalGeometryProfile profile = Stage20StationPhysicalGeometryProfile.deriveCurrent();

        for (var design : profile.stationDesigns()) {
            var envelope = profile.placementEnvelope(design.stationArchetypeId());
            double expectedHalfDiagonal = Math.hypot(design.footprintLengthM(), design.footprintWidthM()) / 2d;
            double expectedClearance = Math.max(design.dockingApproachClearanceM(), design.trafficClearanceM());
            double expectedRadius = expectedHalfDiagonal + expectedClearance;

            assertEquals(expectedHalfDiagonal, envelope.footprintHalfDiagonalM(), 1e-9d);
            assertEquals(expectedClearance, envelope.operationalClearanceM(), 1e-9d);
            assertEquals(expectedRadius, envelope.operationalRadiusM(), 1e-9d);
            assertEquals(expectedRadius * 2d, envelope.sameClassMinimumCenterSeparationM(), 1e-9d);
            assertEquals(design.provenanceId(), envelope.provenance());
        }
    }

    @Test
    void highTrafficAndHazardStationsRetainAuthoredOperationalClearanceInsteadOfSpriteScale() {
        Stage20StationPhysicalGeometryProfile profile = Stage20StationPhysicalGeometryProfile.deriveCurrent();

        var mining = profile.stationDesign("station.infrastructure.mining_outpost");
        var trade = profile.stationDesign("station.infrastructure.trade_logistics_hub");
        var ordnance = profile.stationDesign("station.infrastructure.naval_ordnance_depot");
        var volatileDepot = profile.stationDesign("station.infrastructure.volatile_depot");

        assertEquals(900d, mining.trafficClearanceM());
        assertEquals(3_000d, trade.trafficClearanceM());
        assertEquals(2_800d, ordnance.trafficClearanceM());
        assertEquals(1_400d, volatileDepot.trafficClearanceM());
        assertTrue(trade.trafficClearanceM() > trade.dockingApproachClearanceM());
        assertTrue(ordnance.trafficClearanceM() > ordnance.dockingApproachClearanceM());
    }

    @Test
    void provisionalAuthorityRemainsVisibleForStage22Review() {
        Stage20StationPhysicalGeometryProfile profile = Stage20StationPhysicalGeometryProfile.deriveCurrent();

        assertEquals(CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE, profile.authority());
        assertEquals(Stage20StationPhysicalGeometryProfile.SOURCE_DOCUMENT, profile.sourceDocument());
        assertTrue(profile.stage22ReviewRequired());
    }
}
