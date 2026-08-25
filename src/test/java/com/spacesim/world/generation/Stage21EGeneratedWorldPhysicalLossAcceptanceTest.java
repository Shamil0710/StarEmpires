package com.spacesim.world.generation;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.world.DestructionPolicy;
import com.spacesim.world.FleetCommandState;
import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.FleetOrderState;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.FleetCommandState.OrderStatus;
import com.spacesim.world.FleetCommandState.OrderType;
import com.spacesim.world.FleetForceRegistry;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetOperationalAvailability;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.FleetReadinessEvaluator;
import com.spacesim.world.FleetReadinessState;
import com.spacesim.world.Stage21ECommandLossReconciliationService;
import com.spacesim.world.Stage21EPhysicalConsequenceService;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StrategicOperationState;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Non-vacuous Stage-21E loss-path acceptance over the ordinary generated-world fleet authority.
 *
 * <p>The test does not force a bounded tactical exchange to manufacture a kill. Instead it starts
 * from a real generated military {@link FleetId}, removes that exact ordinary physical entity through
 * the existing world destruction authority, and proves that Stage 21E can only report and reconcile
 * the loss from the resulting world truth. No replacement fleet is allocated.</p>
 */
class Stage21EGeneratedWorldPhysicalLossAcceptanceTest {
    @Test
    void ordinaryPhysicalFleetLossIsReportedAndRemovedFromLiveCommandWithoutReplacement() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        MilitaryFleet victim = militaryFleets(runtime).get(0);
        FleetPlacementState placement = runtime.world().findFleet(victim.fleetId()).orElseThrow();
        long now = runtime.world().getAuthoritativeWorldTick();

        FleetReadinessEvaluator evaluator = new FleetReadinessEvaluator(
                Stage175ICombatTestContentPack.loadDoctrines());
        Map<FleetId, FleetOperationalAvailability> availability = Map.of(
                victim.fleetId(), new FleetOperationalAvailability(Integer.MAX_VALUE, FleetReadinessState.FULL));
        FleetForceRegistry before = FleetForceRegistry.reconstruct(
                runtime.world().snapshot(), evaluator, availability);

        CommandGroupState group = new CommandGroupState(
                1L,
                victim.factionId(),
                "Stage21E physical loss acceptance",
                List.of(victim.fleetId()),
                victim.systemId(),
                false,
                false,
                FleetReadinessState.FULL);
        FleetOrderState order = new FleetOrderState(
                1L,
                group.id(),
                OrderType.GUARD,
                OrderSource.AI,
                victim.systemId(),
                List.of(victim.systemId()),
                0,
                now,
                now + 100L,
                OrderStatus.ACTIVE);
        FleetCommandState command = new FleetCommandState(2L, 2L, List.of(group), List.of(order));
        OperationState operation = new OperationState(
                1L,
                OperationType.DEFENSE,
                group.id(),
                order.id(),
                victim.factionId(),
                List.of(victim.fleetId()),
                victim.systemId(),
                victim.systemId(),
                "system:" + victim.systemId().value(),
                RulesOfEngagement.IDENTIFIED_HOSTILES,
                new SupplyPolicy(0, 0, 100L),
                new WithdrawalPolicy(victim.systemId(), 0, true, true),
                OperationStatus.ACTIVE,
                now,
                now,
                -1L,
                null,
                null);
        StrategicOperationState operations = new StrategicOperationState(2L, List.of(operation));

        int beforeFleetCount = runtime.world().getFleetPlacements().size();
        runtime.world().destroyEntity(
                victim.systemId(), placement.localEntityId(), DestructionPolicy.destroyAll());
        runtime.arrival().materialization(victim.systemId())
                .releasePhysicalStateForWorldTransfer(placement.localEntityId());

        assertTrue(runtime.world().findFleet(victim.fleetId()).isEmpty(),
                "reported loss must begin with the exact ordinary FleetId being physically absent");
        assertEquals(beforeFleetCount - 1, runtime.world().getFleetPlacements().size(),
                "ordinary destruction must remove exactly one fleet and allocate no replacement");

        FleetForceRegistry after = FleetForceRegistry.reconstruct(
                runtime.world().snapshot(), evaluator, availability);
        Stage21EPhysicalConsequenceService.ConsequenceReport consequences =
                new Stage21EPhysicalConsequenceService().reconcile(operation, before, after);
        assertEquals(List.of(victim.fleetId()), consequences.losses(),
                "Stage 21E may report only the FleetId actually missing from ordinary world authority");
        assertTrue(consequences.fleets().stream()
                        .filter(row -> row.fleetId().equals(victim.fleetId()))
                        .allMatch(Stage21EPhysicalConsequenceService.FleetConsequence::destroyed),
                "the exact missing ordinary fleet must be represented as a physical loss");

        Stage21ECommandLossReconciliationService.ReconciliationResult cleaned =
                new Stage21ECommandLossReconciliationService().reconcile(
                        command,
                        operations,
                        operation.id(),
                        fleetId -> runtime.world().findFleet(fleetId).isPresent(),
                        now);
        assertTrue(cleaned.owningGroupDestroyed(),
                "a command group with no ordinary surviving FleetId must be removed");
        assertTrue(cleaned.commandState().groups().isEmpty());
        assertTrue(cleaned.commandState().orders().isEmpty());
        OperationState terminal = cleaned.operationState().requireOperation(operation.id());
        assertEquals(OperationStatus.FAILED, terminal.status());
        assertEquals(List.of(victim.fleetId()), terminal.participantFleetIds(),
                "terminal operation history may retain the lost identity without resurrecting it");
    }

    private static List<MilitaryFleet> militaryFleets(Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime) {
        ArrayList<MilitaryFleet> result = new ArrayList<>();
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) continue;
            Entity entity = runtime.world().findSession(placement.systemId()).orElseThrow()
                    .getEntityRegistry().require(placement.localEntityId());
            EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            if (engineering != null && faction != null) {
                result.add(new MilitaryFleet(placement.id(), faction.factionId, placement.systemId()));
            }
        }
        result.sort(java.util.Comparator.comparing(MilitaryFleet::fleetId));
        if (result.isEmpty()) throw new AssertionError("generated acceptance world lacks military fleets");
        return List.copyOf(result);
    }

    private record MilitaryFleet(FleetId fleetId, int factionId, StarSystemId systemId) { }
}
