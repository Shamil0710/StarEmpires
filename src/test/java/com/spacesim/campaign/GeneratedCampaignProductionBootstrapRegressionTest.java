package com.spacesim.campaign;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static com.spacesim.world.GeneratedWorldFtlTestSupport.advanceOrdinaryJumpToCompletion;
import static com.spacesim.world.GeneratedWorldFtlTestSupport.placeAtOutgoingEndpoint;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedCampaignProductionBootstrapRegressionTest {
    @Test
    void legacyFreightAlreadyInTransitReceivesFiniteEngineeringExactlyOnArrival() {
        GeneratedCampaignCoordinator campaign = GeneratedCampaignCoordinator.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        var runtime = campaign.runtime();
        var freighter = runtime.freight().capture().freighters().stream()
                .filter(value -> !value.activeOrderId().isBlank())
                .findFirst().orElseThrow();
        var order = runtime.freight().findOrder(freighter.activeOrderId()).orElseThrow();
        int nextIndex = switch (freighter.phase()) {
            case OUTBOUND -> freighter.routeIndex() + 1;
            case RETURNING -> freighter.routeIndex() - 1;
            default -> throw new AssertionError("active bootstrap freighter is not moving");
        };
        var destination = order.orderedSystems().get(nextIndex);
        var placement = runtime.world().findFleet(freighter.fleetId()).orElseThrow();
        var entity = runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().require(placement.localEntityId());
        entity.remove(EngineeringComponent.class);
        assertNull(entity.getComponent(EngineeringComponent.class));

        placeAtOutgoingEndpoint(runtime, freighter.fleetId(), destination);
        runtime.requestNextRouteHop(freighter.fleetId());
        advanceOrdinaryJumpToCompletion(runtime, freighter.fleetId());

        var arrived = runtime.world().findFleet(freighter.fleetId()).orElseThrow();
        var arrivedEntity = runtime.world().findSession(arrived.systemId()).orElseThrow()
                .getEntityRegistry().require(arrived.localEntityId());
        EngineeringComponent migrated = arrivedEntity.getComponent(EngineeringComponent.class);
        assertNotNull(migrated, "legacy detached freight must gain finite engineering on first arrival");
        assertTrue(migrated.runtimeState.consumables().reactionMassKg() > 0d);
    }

    @Test
    void ordinaryCampaignSurvivesFreightPlanningAndUsesCanonicalPublicFactionNames() {
        long seed = Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED;

        GeneratedCampaignCoordinator campaign = assertDoesNotThrow(
                () -> GeneratedCampaignCoordinator.create(seed),
                "ordinary desktop launch path must retain the accepted Stage-20 profile, survive freight planning and adopt Stage-21 authorities");

        Map<String, String> names = campaign.runtime().world().getWorldFactionIdentities().stream()
                .collect(Collectors.toMap(
                        identity -> identity.stableFactionId(),
                        identity -> identity.displayName()));
        assertEquals("Империя", names.get("faction.alpha"));
        assertEquals("Индустриальный Союз", names.get("faction.beta"));
        assertFalse(names.containsValue("Alpha"));
        assertFalse(names.containsValue("Beta"));

        var runtime = campaign.runtime();
        var freighter = runtime.freight().capture().freighters().stream()
                .filter(value -> !value.activeOrderId().isBlank())
                .findFirst().orElseThrow();
        var placement = runtime.world().findFleet(freighter.fleetId()).orElseThrow();
        var entity = runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().require(placement.localEntityId());
        EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
        assertNotNull(engineering, "generated freight must have finite fitted propulsion");
        assertTrue(engineering.runtimeState.consumables().reactionMassKg() > 0d,
                "generated freight must start with explicit finite reaction mass");

        var order = runtime.freight().findOrder(freighter.activeOrderId()).orElseThrow();
        var fuelPlan = runtime.world().planFleetRouteFuel(freighter.fleetId(), order.orderedSystems());
        assertTrue(fuelPlan.supported(), "generated freight route must use physical fuel planning");
        assertTrue(fuelPlan.feasible(),
                () -> "bootstrap must not accept a route that strands its freighter: " + fuelPlan);
    }
}
