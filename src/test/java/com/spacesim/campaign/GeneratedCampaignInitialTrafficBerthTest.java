package com.spacesim.campaign;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.persistence.Stage20FreightPersistentState.FreighterState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistentState.LocalFleetPhysicalState;
import com.spacesim.world.FleetId;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalog.BandId;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalogLoader;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedCampaignInitialTrafficBerthTest {
    @Test
    void operationalFreightUsesHullScaleBerthsRatherThanStationTravelDistance() {
        var rawRuntime = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        var raw = rawRuntime.captureState();
        var berthed = GeneratedCampaignInitialTrafficBerth.apply(raw);
        ShipEngineeringCatalog engineering = Stage175ICombatTestContentPack.load();

        double stationTravelMinM = Stage20LocalRouteSemanticBandCatalogLoader.loadDefault().bands().stream()
                .filter(band -> band.id() == BandId.STATION_TO_STATION)
                .findFirst().orElseThrow().minDistanceM();
        Map<FleetId, FreighterState> before = raw.freight().freighters().stream()
                .collect(Collectors.toMap(FreighterState::fleetId, Function.identity()));
        Map<FleetId, LocalFleetPhysicalState> physicalByFleet = berthed.localFleetPhysicalStates().stream()
                .collect(Collectors.toMap(LocalFleetPhysicalState::fleetId, Function.identity()));

        for (FreighterState after : berthed.freight().freighters()) {
            FreighterState original = before.get(after.fleetId());
            if (!after.operational()) {
                assertEquals(original, after);
                continue;
            }
            double hullRadiusM = topDownBoundingRadiusM(engineering, after);
            double displacementM = original.physicalState().position()
                    .distanceTo(after.physicalState().position());
            assertTrue(displacementM >= hullRadiusM * 2d,
                    "first traffic shell must retain at least one hull radius of center clearance");
            assertTrue(displacementM < stationTravelMinM,
                    "traffic berth must not reuse the inter-facility travel-distance band");
            assertNotEquals(original.physicalState().position(), after.physicalState().position());

            LocalFleetPhysicalState physical = physicalByFleet.get(after.fleetId());
            assertEquals(after.currentSystemId(), physical.systemId());
            assertEquals(after.physicalState(), physical.physicalState(),
                    "freight mirror and authoritative local physical state must remain identical");
        }

        List<FreighterState> operational = berthed.freight().freighters().stream()
                .filter(FreighterState::operational)
                .toList();
        assertTrue(!operational.isEmpty());
        for (int firstIndex = 0; firstIndex < operational.size(); firstIndex++) {
            FreighterState first = operational.get(firstIndex);
            for (int secondIndex = firstIndex + 1; secondIndex < operational.size(); secondIndex++) {
                FreighterState second = operational.get(secondIndex);
                if (!first.currentSystemId().equals(second.currentSystemId())) {
                    continue;
                }
                double minimumSeparationM = topDownBoundingRadiusM(engineering, first)
                        + topDownBoundingRadiusM(engineering, second);
                double actualSeparationM = first.physicalState().position()
                        .distanceTo(second.physicalState().position());
                assertTrue(actualSeparationM + 1.0e-9d >= minimumSeparationM,
                        "same-system freight berths must not overlap physical hull envelopes");
            }
        }

        long distinctPositions = operational.stream()
                .map(fleet -> fleet.physicalState().position())
                .distinct()
                .count();
        assertEquals(operational.size(), distinctPositions,
                "stable FleetId berth assignment must keep generated freighters physically distinct");
        assertEquals(berthed, GeneratedCampaignInitialTrafficBerth.apply(raw),
                "same physical checkpoint must receive the same deterministic berth layout");
    }

    private static double topDownBoundingRadiusM(
            ShipEngineeringCatalog engineering,
            FreighterState fleet) {
        var hull = engineering.findHull(fleet.hullId());
        double halfLengthM = hull.boundingDimensionsM().lengthM() * 0.5d;
        double halfWidthM = hull.boundingDimensionsM().widthM() * 0.5d;
        return StrictMath.hypot(halfLengthM, halfWidthM);
    }
}
