package com.spacesim.campaign;

import com.spacesim.persistence.Stage20FreightPersistentState.FreighterState;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalog.BandId;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalogLoader;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedCampaignInitialTrafficBerthTest {
    @Test
    void everyOperationalFreighterLeavesTheRawMajorHubCenterByAcceptedBerthRadius() {
        var rawRuntime = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        var raw = rawRuntime.captureState();
        var berthed = GeneratedCampaignInitialTrafficBerth.apply(raw);

        double expectedRadius = Stage20LocalRouteSemanticBandCatalogLoader.loadDefault().bands().stream()
                .filter(band -> band.id() == BandId.STATION_TO_STATION)
                .findFirst().orElseThrow().minDistanceM();
        Map<?, FreighterState> before = raw.freight().freighters().stream()
                .collect(Collectors.toMap(FreighterState::fleetId, Function.identity()));

        for (FreighterState after : berthed.freight().freighters()) {
            FreighterState original = before.get(after.fleetId());
            if (!after.operational()) {
                assertEquals(original, after);
                continue;
            }
            double displacement = original.physicalState().position()
                    .distanceTo(after.physicalState().position());
            assertEquals(expectedRadius, displacement, expectedRadius * 1.0e-9d,
                    "operational freight must be physically berthed away from the station center");
        }

        long distinctPositions = berthed.freight().freighters().stream()
                .filter(FreighterState::operational)
                .map(fleet -> fleet.physicalState().position())
                .distinct()
                .count();
        long operational = berthed.freight().freighters().stream()
                .filter(FreighterState::operational)
                .count();
        assertTrue(operational > 0);
        assertEquals(operational, distinctPositions,
                "stable FleetId azimuth must prevent generated freighters from stacking on one berth");
    }
}
