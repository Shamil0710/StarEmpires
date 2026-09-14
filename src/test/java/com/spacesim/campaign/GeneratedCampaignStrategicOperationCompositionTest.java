package com.spacesim.campaign;

import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.FleetCommandState.OrderType;
import com.spacesim.world.FleetForceRegistry;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetOrderSubmissionService;
import com.spacesim.world.FleetReadinessEvaluator;
import com.spacesim.world.FleetStrategicRoutePlanner;
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
                .findFirst()
                .orElseThrow(() -> new AssertionError("default generated campaign needs one affiliated in-system fleet"));
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

        FleetOrderSubmissionService.SubmissionResult submitted = new FleetOrderSubmissionService(
                new FleetStrategicRoutePlanner(campaign.session().runtime().world().getTopology())).submit(
                        campaign.fleetCommands(),
                        forces,
                        group.id(),
                        OrderType.GUARD,
                        OrderSource.PLAYER,
                        systemId,
                        campaign.session().runtime().world().getAuthoritativeWorldTick(),
                        (factionId, from, to, tick, destination) -> false,
                        (factionId, targetSystemId, tick) -> FleetOrderSubmissionService.ServiceCapability.none(),
                        (factionId, type, route, tick) -> 0);

        var authorities = campaign.authorities();
        GeneratedCampaignCoordinator ordered = GeneratedCampaignCoordinator.restore(
                GeneratedCampaignAuthorityCheckpoint.capture(
                        authorities.session(),
                        authorities.actors(),
                        authorities.strategicIntents(),
                        authorities.diplomacy(),
                        authorities.warfare(),
                        submitted.state(),
                        campaign.operations(),
                        authorities.transitions(),
                        authorities.recovery(),
                        authorities.npcMissions()));

        var operation = ordered.beginStrategicOperation(
                reconstructForces(ordered),
                group.id(),
                RulesOfEngagement.IDENTIFIED_HOSTILES,
                supply,
                withdrawal);
        assertEquals(OperationStatus.ACTIVE, operation.status(),
                "same-system GUARD must be active immediately without invented fleet movement");
        assertEquals(group.id(), operation.commandGroupId());
        assertEquals(submitted.order().id(), operation.sourceOrderId());
        assertEquals(List.of(force.fleetId()), operation.participantFleetIds());
        assertEquals(physicalBeforeOrderAndOperation, ordered.session().captureState().worldState(),
                "D/E metadata composition must not mutate the ordinary physical world");

        GeneratedCampaignCoordinator restored = GeneratedCampaignCoordinator.decodeOrMigrate(ordered.encode());
        assertEquals(ordered.fleetCommands(), restored.fleetCommands());
        assertEquals(ordered.operations(), restored.operations());
        assertEquals(physicalBeforeOrderAndOperation, restored.session().captureState().worldState(),
                "native save/load must retain one physical world authority");
        assertThrows(IllegalStateException.class, () -> restored.beginStrategicOperation(
                        reconstructForces(restored),
                        group.id(),
                        RulesOfEngagement.IDENTIFIED_HOSTILES,
                        supply,
                        withdrawal),
                "one command group must not acquire a second active Stage-21E operation after load");
    }

    private static FleetForceRegistry reconstructForces(GeneratedCampaignCoordinator campaign) {
        return FleetForceRegistry.reconstruct(
                campaign.session().captureState().worldState(),
                new FleetReadinessEvaluator(ShipEngineeringCatalogLoader.loadDefault()),
                Map.of());
    }
}
