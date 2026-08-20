package com.spacesim.world;

import com.spacesim.world.Stage20LocalInfrastructureLayout.CalibratedConnection;
import com.spacesim.world.Stage20LocalInfrastructureLayout.InfrastructurePlacement;
import com.spacesim.world.Stage20LocalInfrastructureLayout.PlacementKind;
import com.spacesim.world.Stage20LocalInfrastructureLayoutGenerator.PlacementRequest;
import com.spacesim.world.Stage20LocalInfrastructureLayoutGenerator.TargetKind;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalog.BandId;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticCalibrationProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20LocalInfrastructureLayoutGeneratorTest {
    private static final String HUB_ARCHETYPE = "station.infrastructure.trade_logistics_hub";

    @Test
    void generationIsDeterministicAndIndependentOfRequestOrdering() {
        Stage20SystemGeometry geometry = Stage20SystemGeometryGenerator.generate(0x20C5EEDL, new StarSystemId(31L));
        LocalPhysicalPosition hub = geometry.centralReference().translated(120_000_000d, -45_000_000d);
        List<PlacementRequest> requests = List.of(
                PlacementRequest.independentStation("station-alpha", "station.infrastructure.industrial_station"),
                PlacementRequest.resourceFieldAnchor("resource-ice-7"),
                PlacementRequest.jumpArrivalAnchor("jump-west"),
                PlacementRequest.independentStation("station-beta", "station.infrastructure.naval_ordnance_depot"));
        List<PlacementRequest> reversed = new ArrayList<>(requests);
        Collections.reverse(reversed);

        Stage20LocalInfrastructureLayout first = Stage20LocalInfrastructureLayoutGenerator.generate(
                geometry, hub, "hub-main", HUB_ARCHETYPE, requests);
        Stage20LocalInfrastructureLayout second = Stage20LocalInfrastructureLayoutGenerator.generate(
                geometry, hub, "hub-main", HUB_ARCHETYPE, reversed);

        assertEquals(first, second);
        assertEquals(Stage20LocalInfrastructureLayout.CURRENT_VERSION, first.version());
        assertEquals(geometry.systemId(), first.systemId());
        assertEquals(geometry.rootSeed(), first.rootSeed());
        assertEquals(Stage20SystemGeometry.CURRENT_VERSION, first.systemGeometryVersion());
        assertEquals(Stage20LocalRouteSemanticCalibrationProfile.CURRENT_VERSION, first.routeCalibrationVersion());
    }

    @Test
    void targetRolesResolveToAcceptedSemanticBandsAndAuthoritativePhysicalDistance() {
        Stage20SystemGeometry geometry = Stage20SystemGeometryGenerator.generate(771L, new StarSystemId(5L));
        LocalPhysicalPosition hub = geometry.centralReference().translated(10_000_000d, 20_000_000d);
        Stage20LocalInfrastructureLayout layout = Stage20LocalInfrastructureLayoutGenerator.generate(
                geometry,
                hub,
                "hub",
                HUB_ARCHETYPE,
                List.of(
                        PlacementRequest.independentStation(
                                "factory", "station.infrastructure.high_tech_hub"),
                        PlacementRequest.resourceFieldAnchor("ore-field"),
                        PlacementRequest.jumpArrivalAnchor("arrival")));

        CalibratedConnection station = connection(layout, BandId.STATION_TO_STATION);
        CalibratedConnection resource = connection(layout, BandId.STATION_TO_RESOURCE_FIELD);
        CalibratedConnection jump = connection(layout, BandId.JUMP_ARRIVAL_TO_MAJOR_HUB);

        assertEquals("hub", station.fromId());
        assertEquals("factory", station.toId());
        assertEquals("hub", resource.fromId());
        assertEquals("ore-field", resource.toId());
        assertEquals("arrival", jump.fromId());
        assertEquals("hub", jump.toId());
        assertConnectionMatchesPositions(layout, station);
        assertConnectionMatchesPositions(layout, resource);
        assertConnectionMatchesPositions(layout, jump);
        assertTrue(station.distanceM() >= 10_000_000d && station.distanceM() <= 100_000_000d);
        assertTrue(resource.distanceM() >= 50_000_000d && resource.distanceM() <= 500_000_000d);
        assertTrue(jump.distanceM() >= 100_000_000d && jump.distanceM() <= 1_000_000_000d);
    }

    @Test
    void independentStationsRespectLogisticsOperationalAndDefensiveSeparation() {
        Stage20SystemGeometry geometry = Stage20SystemGeometryGenerator.generate(90210L, new StarSystemId(19L));
        Stage20LocalInfrastructureLayout layout = Stage20LocalInfrastructureLayoutGenerator.generate(
                geometry,
                geometry.centralReference().translated(-100_000_000d, 80_000_000d),
                "hub",
                HUB_ARCHETYPE,
                List.of(
                        PlacementRequest.independentStation(
                                "station-a", "station.infrastructure.mining_outpost"),
                        PlacementRequest.independentStation(
                                "station-b", "station.infrastructure.refinery_complex"),
                        PlacementRequest.independentStation(
                                "station-c", "station.infrastructure.naval_ordnance_depot"),
                        PlacementRequest.independentStation(
                                "station-d", "station.infrastructure.frontier_multipurpose")));

        List<InfrastructurePlacement> stations = layout.placements().stream()
                .filter(InfrastructurePlacement::isStation)
                .toList();
        for (int left = 0; left < stations.size(); left++) {
            for (int right = left + 1; right < stations.size(); right++) {
                InfrastructurePlacement first = stations.get(left);
                InfrastructurePlacement second = stations.get(right);
                double separationM = first.position().distanceTo(second.position());
                assertTrue(separationM >= 10_000_000d);
                assertTrue(separationM >= first.operationalRadiusM() + second.operationalRadiusM());
                assertTrue(separationM >= first.defensiveExclusionReferenceM());
                assertTrue(separationM >= second.defensiveExclusionReferenceM());
            }
        }
    }

    @Test
    void calibratedConnectionsCarryPhysicalLogisticsConsequencesInsteadOfOnlyLabels() {
        Stage20SystemGeometry geometry = Stage20SystemGeometryGenerator.generate(18L, new StarSystemId(8L));
        Stage20LocalInfrastructureLayout layout = Stage20LocalInfrastructureLayoutGenerator.generate(
                geometry,
                geometry.centralReference().translated(30_000_000d, 0d),
                "hub",
                HUB_ARCHETYPE,
                List.of(
                        PlacementRequest.resourceFieldAnchor("resource"),
                        PlacementRequest.jumpArrivalAnchor("jump")));

        for (CalibratedConnection connection : layout.connections()) {
            Stage20LocalInfrastructureLayout.LogisticsConsequenceEnvelope consequences =
                    connection.logisticsConsequences();
            assertEquals(Stage20LocalRouteSemanticCalibrationProfile.CURRENT_VERSION,
                    consequences.sourceProfileVersion());
            assertTrue(consequences.civilianRoutineTravelTimeMinS() > 0d);
            assertTrue(consequences.civilianRoutineTravelTimeMaxS()
                    >= consequences.civilianRoutineTravelTimeMinS());
            assertTrue(consequences.militaryResponseTimeMinS() > 0d);
            assertTrue(consequences.militaryResponseTimeMaxS()
                    >= consequences.militaryResponseTimeMinS());
            assertTrue(consequences.civilianRoundTripDeltaVMinMps() > 0d);
            assertTrue(consequences.civilianRoundTripDeltaVMaxMps()
                    >= consequences.civilianRoundTripDeltaVMinMps());
            assertEquals(
                    consequences.civilianRoutineTravelTimeMinS() * 2d,
                    consequences.civilianTransitOnlyCargoCycleMinS(),
                    0d);
            assertEquals(
                    consequences.civilianRoutineTravelTimeMaxS() * 2d,
                    consequences.civilianTransitOnlyCargoCycleMaxS(),
                    0d);
        }
    }

    @Test
    void resourceAndJumpRowsRemainPointAnchorsUntilTheirExtentIsAuthored() {
        Stage20SystemGeometry geometry = Stage20SystemGeometryGenerator.generate(444L, new StarSystemId(44L));
        Stage20LocalInfrastructureLayout layout = Stage20LocalInfrastructureLayoutGenerator.generate(
                geometry,
                geometry.centralReference().translated(2_000_000d, 3_000_000d),
                "hub",
                HUB_ARCHETYPE,
                List.of(
                        PlacementRequest.resourceFieldAnchor("resource"),
                        PlacementRequest.jumpArrivalAnchor("jump")));

        InfrastructurePlacement resource = layout.placement("resource");
        InfrastructurePlacement jump = layout.placement("jump");
        assertEquals(PlacementKind.RESOURCE_FIELD_ANCHOR, resource.kind());
        assertEquals(PlacementKind.JUMP_ARRIVAL_ANCHOR, jump.kind());
        assertTrue(resource.stationArchetypeId().isEmpty());
        assertTrue(jump.stationArchetypeId().isEmpty());
        assertEquals(0d, resource.operationalRadiusM(), 0d);
        assertEquals(0d, jump.operationalRadiusM(), 0d);
        assertEquals(0d, resource.defensiveExclusionReferenceM(), 0d);
        assertEquals(0d, jump.defensiveExclusionReferenceM(), 0d);
    }

    @Test
    void descriptiveSystemEnvelopeNeverBecomesAPlacementClamp() {
        Stage20SystemGeometry geometry = Stage20SystemGeometryGenerator.generate(121L, new StarSystemId(12L));
        double envelopeM = geometry.operationalEnvelope().radiusM();
        LocalPhysicalPosition outsideHub = geometry.centralReference().translated(envelopeM * 2d, envelopeM * 0.1d);

        Stage20LocalInfrastructureLayout layout = Stage20LocalInfrastructureLayoutGenerator.generate(
                geometry,
                outsideHub,
                "hub",
                HUB_ARCHETYPE,
                List.of(PlacementRequest.resourceFieldAnchor("remote-resource")));

        assertEquals(outsideHub, layout.placement("hub").position());
        assertTrue(geometry.centralReference().distanceTo(layout.placement("hub").position()) > envelopeM);
        assertFalse(geometry.operationalEnvelope().hardBoundary());
        assertFalse(geometry.operationalEnvelope().clampAllowed());
    }

    @Test
    void invalidSemanticRequestsFailInsteadOfFallingBackToRawGeometry() {
        Stage20SystemGeometry geometry = Stage20SystemGeometryGenerator.generate(1L, new StarSystemId(1L));
        LocalPhysicalPosition hub = geometry.centralReference().translated(1_000_000d, 0d);

        assertThrows(IllegalArgumentException.class, () -> new PlacementRequest(
                "resource",
                TargetKind.RESOURCE_FIELD_ANCHOR,
                Optional.of("station.infrastructure.mining_outpost")));
        assertThrows(IllegalArgumentException.class, () -> Stage20LocalInfrastructureLayoutGenerator.generate(
                geometry,
                hub,
                "hub",
                HUB_ARCHETYPE,
                List.of(
                        PlacementRequest.resourceFieldAnchor("duplicate"),
                        PlacementRequest.jumpArrivalAnchor("duplicate"))));
        assertThrows(IllegalArgumentException.class, () -> Stage20LocalInfrastructureLayoutGenerator.generate(
                geometry,
                hub,
                "hub",
                HUB_ARCHETYPE,
                List.of(PlacementRequest.independentStation("bad-station", "station.unknown"))));
    }

    private static CalibratedConnection connection(Stage20LocalInfrastructureLayout layout, BandId bandId) {
        return layout.connections().stream()
                .filter(value -> value.bandId() == bandId)
                .findFirst()
                .orElseThrow();
    }

    private static void assertConnectionMatchesPositions(
            Stage20LocalInfrastructureLayout layout,
            CalibratedConnection connection) {
        double actualM = layout.placement(connection.fromId()).position()
                .distanceTo(layout.placement(connection.toId()).position());
        assertEquals(actualM, connection.distanceM(), Math.max(1e-5d, actualM * 1e-12d));
        assertTrue(connection.distanceM() >= connection.minDistanceM());
        assertTrue(connection.distanceM() <= connection.maxDistanceM());
    }
}
