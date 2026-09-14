package com.spacesim.campaign;

import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.FleetCommandState.OrderType;
import com.spacesim.world.FleetForceRegistry;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetOrderSubmissionService;
import com.spacesim.world.FleetReadinessEvaluator;
import com.spacesim.world.StrategicOperationService.SupplyDecision;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedCampaignStrategicOperationCompositionTest {
    @Test
    void acceptedFleetOrderBeginsRealOperationAndSurvivesNativeSaveWithoutMovingWorld() {
        GeneratedCampaignCoordinator campaign = GeneratedCampaignCoordinator.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        FleetForceRegistry forces = reconstructForces(campaign);
        var force = forces.entries().stream()
                .filter(entry -> entry.factionId() >= 0)
                .filter(entry -> entry.locationKind() == FleetLocationKind.IN_SYSTEM)
                .filter(entry -> entry.systemId() != null)
                .filter(entry -> entry.readiness().structuralBps() >= 5_000)
                .filter(entry -> entry.readiness().ammunitionBps() > 0)
                .filter(entry -> entry.readiness().crewBps() >= 5_000)
                .filter(entry -> entry.readiness().sensorsBps() > 0)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "default generated campaign needs one combat-ready affiliated in-system fleet"));
        var systemId = force.systemId();

        var group = campaign.formFleetCommandGroup(
                forces,
                force.factionId(),
                "M22.7 D-E integration group",
                List.of(force.fleetId()),
                systemId,
                false,
                false,
                10_000);
        var physicalBeforeOrderAndOperation = campaign.session().captureState().worldState();

        SupplyPolicy supply = new SupplyPolicy(0, 0, 0L);
        WithdrawalPolicy withdrawal = new WithdrawalPolicy(systemId, 0, true, true);
        assertThrows(IllegalStateException.class, () -> campaign.beginStrategicOperation(
                        forces,
                        group.id(),
                        RulesOfEngagement.IDENTIFIED_HOSTILES,
                        supply,
                        withdrawal),
                "Stage-21E must fail closed until Stage-21D accepted an active order");

        var order = campaign.submitFleetOrder(
                forces,
                group.id(),
                OrderType.GUARD,
                OrderSource.PLAYER,
                systemId,
                (factionId, from, to, tick, destination) -> false,
                (factionId, targetSystemId, tick) -> FleetOrderSubmissionService.ServiceCapability.none(),
                (factionId, type, route, tick) -> 0);
        assertEquals(order, campaign.fleetCommands().requireOrder(order.id()));

        var operation = campaign.beginStrategicOperation(
                reconstructForces(campaign),
                group.id(),
                RulesOfEngagement.IDENTIFIED_HOSTILES,
                supply,
                withdrawal);
        assertEquals(OperationStatus.ACTIVE, operation.status(),
                "same-system GUARD must be active immediately without invented fleet movement");
        assertEquals(group.id(), operation.commandGroupId());
        assertEquals(order.id(), operation.sourceOrderId());
        assertEquals(List.of(force.fleetId()), operation.participantFleetIds());
        assertEquals(physicalBeforeOrderAndOperation, campaign.session().captureState().worldState(),
                "D/E metadata composition must not mutate the ordinary physical world");

        GeneratedCampaignCoordinator restored = GeneratedCampaignCoordinator.decodeOrMigrate(campaign.encode());
        assertEquals(campaign.fleetCommands(), restored.fleetCommands());
        assertEquals(campaign.operations(), restored.operations());
        assertEquals(physicalBeforeOrderAndOperation, restored.session().captureState().worldState(),
                "native save/load must retain one physical world authority");
        assertThrows(IllegalStateException.class, () -> restored.beginStrategicOperation(
                        reconstructForces(restored),
                        group.id(),
                        RulesOfEngagement.IDENTIFIED_HOSTILES,
                        supply,
                        withdrawal),
                "one command group must not acquire a second active Stage-21E operation after load");

        long previousTransitionTick = restored.operations().requireOperation(operation.id()).lastTransitionTick();
        restored.advanceFrame(1.0f);
        long reviewTick = restored.session().runtime().world().getAuthoritativeWorldTick();
        assertTrue(reviewTick > previousTransitionTick,
                "ordinary simulation time must advance before post-load operation review");
        var review = restored.reviewStrategicOperationSupply(operation.id(), reconstructForces(restored));
        assertEquals(SupplyDecision.CONTINUE, review.decision());
        assertEquals(reviewTick, restored.operations().requireOperation(operation.id()).lastTransitionTick(),
                "restored Stage-21E authority must remain live at authoritative simulation time");

        GeneratedCampaignCoordinator continued = GeneratedCampaignCoordinator.decodeOrMigrate(restored.encode());
        assertEquals(restored.operations(), continued.operations(),
                "post-load Stage-21E progress must survive the next native save");
    }

    private static FleetForceRegistry reconstructForces(GeneratedCampaignCoordinator campaign) {
        return FleetForceRegistry.reconstruct(
                campaign.session().captureState().worldState(),
                new FleetReadinessEvaluator(ShipEngineeringCatalogLoader.loadDefault()),
                Map.of());
    }
}
