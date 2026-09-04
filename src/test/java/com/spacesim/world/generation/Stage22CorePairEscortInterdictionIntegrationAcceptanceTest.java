package com.spacesim.world.generation;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.content.Stage22CorePairExperimentProtocol.Permutation;
import com.spacesim.persistence.EntityState;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase;
import com.spacesim.persistence.Stage20FreightPersistentState.FreighterState;
import com.spacesim.persistence.Stage20FreightPersistentState.TransportOrderState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage20SourceOutpostMaterializer.MaterializedExtractionOutpost;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver;
import com.spacesim.ship.Stage22CorePairTacticalFactory;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.GeneratedWorldFtlTestSupport;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.Stage21ETacticalMaterializationService.CombatSide;
import com.spacesim.world.Stage21ETacticalMaterializationService.PhysicalCombatant;
import com.spacesim.world.Stage21ETacticalMaterializationService.TacticalMaterializationRequest;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StrategicOperationState;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 B08 production causal-seam acceptance.
 *
 * <p>The freight order remains owned by the ordinary Stage-20 freight runtime. A persistent
 * Stage-21E interception operation denies its exact next topology edge only while its ordinary
 * military FleetId is physically active. The escort/interdictor exchange is committed by the exact
 * Stage-19 generated-world authority. No combat result is copied into a Stage-22 logistics counter:
 * the same traffic policy simply observes whether the real interdictor FleetId still exists.</p>
 */
class Stage22CorePairEscortInterdictionIntegrationAcceptanceTest {
    private static final long OPERATION_ID = 22_608L;
    private static final long TACTICAL_TICKS = 1_200L;
    private static final double HANDLING_SECONDS = 3_600d;

    @Test
    void exactStage19OutcomeCausallyGatesTheSamePhysicalFreightOrder() {
        ScenarioResult defaultFirst = run(Permutation.DEFAULT);
        ScenarioResult defaultSecond = run(Permutation.DEFAULT);
        ScenarioResult mirroredFirst = run(Permutation.MIRRORED);
        ScenarioResult mirroredSecond = run(Permutation.MIRRORED);

        assertArrayEquals(defaultFirst.checkpoint(), defaultSecond.checkpoint(),
                "default escort/interdiction replay must be byte deterministic");
        assertArrayEquals(mirroredFirst.checkpoint(), mirroredSecond.checkpoint(),
                "mirrored escort/interdiction replay must be byte deterministic");
        assertEquals(defaultFirst.interdictorAlive(), defaultSecond.interdictorAlive());
        assertEquals(mirroredFirst.interdictorAlive(), mirroredSecond.interdictorAlive());
        assertEquals(defaultFirst.routeAdmitted(), !defaultFirst.interdictorAlive(),
                "freight admission must be derived from the physical interdictor outcome");
        assertEquals(mirroredFirst.routeAdmitted(), !mirroredFirst.interdictorAlive(),
                "mirrored freight admission must be derived from the physical interdictor outcome");
        assertTrue(!defaultFirst.interdictorAlive() || !mirroredFirst.interdictorAlive(),
                "paired B08 evidence must include a real destroyed-interdictor/unblocked route branch");
        assertEquals(defaultFirst.orderIdBefore(), defaultFirst.orderIdAfter(),
                "combat must gate the same persistent freight order rather than replacing it");
        assertEquals(mirroredFirst.orderIdBefore(), mirroredFirst.orderIdAfter(),
                "mirrored combat must gate the same persistent freight order rather than replacing it");
    }

    private static ScenarioResult run(Permutation permutation) {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        FreighterState source = assignedSourceFreighter(runtime);
        prepareOutboundCargo(runtime, source);
        FreighterState outbound = runtime.freight().findFreighter(source.fleetId()).orElseThrow();
        TransportOrderState orderBefore = runtime.freight().findOrder(outbound.activeOrderId()).orElseThrow();
        StarSystemId from = outbound.currentSystemId();
        StarSystemId to = orderBefore.orderedSystems().get(outbound.routeIndex() + 1);
        int trafficFaction = runtime.world().findFactionRuntimeId(outbound.stableFactionId()).orElseThrow();

        MilitaryFleet escort = militaryFleets(runtime).stream()
                .filter(value -> value.factionId() == trafficFaction)
                .findFirst().orElseThrow(() -> new AssertionError("B08 world lacks a friendly escort"));
        MilitaryFleet interdictor = militaryFleets(runtime).stream()
                .filter(value -> value.factionId() != trafficFaction)
                .findFirst().orElseThrow(() -> new AssertionError("B08 world lacks a hostile interdictor"));
        moveFleetByOrdinaryRoute(runtime, escort.fleetId(), from);
        moveFleetByOrdinaryRoute(runtime, interdictor.fleetId(), from);

        StrategicOperationState operations = interception(interdictor, from, to,
                runtime.world().getAuthoritativeWorldTick());
        Stage21EGeneratedWorldTrafficRuntime traffic = new Stage21EGeneratedWorldTrafficRuntime(runtime);
        assertThrows(IllegalStateException.class, () -> traffic.requestNextRouteHop(operations, outbound.fleetId()),
                "the real interdictor must deny the same freight edge before tactical resolution");
        assertTrue(runtime.world().findFleetJump(outbound.fleetId()).isEmpty());

        var core = Stage22CorePairTacticalFactory.createDestroyerDuel(permutation);
        EngineeringComponent empire = engineeringFor(core, Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID);
        EngineeringComponent union = engineeringFor(core, Stage22CorePairTacticalFactory.UNION_ENTITY_ID);
        EngineeringComponent escortEngineering = permutation == Permutation.DEFAULT ? empire : union;
        EngineeringComponent interdictorEngineering = permutation == Permutation.DEFAULT ? union : empire;

        FleetPlacementState escortPlacement = runtime.world().findFleet(escort.fleetId()).orElseThrow();
        FleetPlacementState interdictorPlacement = runtime.world().findFleet(interdictor.fleetId()).orElseThrow();
        Entity escortEntity = entity(runtime, escortPlacement);
        Entity interdictorEntity = entity(runtime, interdictorPlacement);
        escortEntity.add(copy(escortEngineering));
        interdictorEntity.add(copy(interdictorEngineering));

        LocalPhysicalKinematics anchor = runtime.arrival().materialization(from)
                .physicalState(escortPlacement.localEntityId()).orElseThrow();
        runtime.arrival().materialization(from).updatePhysicalState(
                interdictorPlacement.localEntityId(),
                new LocalPhysicalKinematics(anchor.position().translated(1_430d, 0d), 0d, 0d));

        EntityState escortState = EntityStateMapper.capture(escortEntity);
        EntityState interdictorState = EntityStateMapper.capture(interdictorEntity);
        List<PhysicalCombatant> combatants = new ArrayList<>(List.of(
                new PhysicalCombatant(escort.fleetId(), CombatSide.CONTACT, escort.factionId(), escortState),
                new PhysicalCombatant(interdictor.fleetId(), CombatSide.OPERATION,
                        interdictor.factionId(), interdictorState)));
        combatants.sort(Comparator.comparing(PhysicalCombatant::fleetId));

        long now = runtime.world().getAuthoritativeWorldTick();
        var resolver = new Stage19ExactTacticalEncounterResolver(
                core.content().engineering(), core.protection(), core.content().ammunition(), core.content().launchers());
        new Stage21EGeneratedWorldStage19Authority(runtime, resolver, TACTICAL_TICKS).materializeExact(
                new TacticalMaterializationRequest(OPERATION_ID, from, now, List.copyOf(combatants)));

        boolean interdictorAlive = runtime.world().findFleet(interdictor.fleetId()).isPresent();
        boolean escortAlive = runtime.world().findFleet(escort.fleetId()).isPresent();
        boolean routeAdmitted;
        if (interdictorAlive) {
            assertThrows(IllegalStateException.class,
                    () -> traffic.requestNextRouteHop(operations, outbound.fleetId()),
                    "surviving physical interdictor must continue denying the same freight edge");
            routeAdmitted = false;
            assertTrue(runtime.world().findFleetJump(outbound.fleetId()).isEmpty());
        } else {
            traffic.requestNextRouteHop(operations, outbound.fleetId());
            routeAdmitted = true;
            assertTrue(runtime.world().findFleetJump(outbound.fleetId()).isPresent(),
                    "destroying the real interdictor must immediately admit the same ordinary freight hop");
        }

        FreighterState afterFreighter = runtime.freight().findFreighter(outbound.fleetId()).orElseThrow();
        TransportOrderState orderAfter = runtime.freight().findOrder(afterFreighter.activeOrderId()).orElseThrow();
        assertEquals(orderBefore.orderId(), orderAfter.orderId());
        assertEquals(orderBefore.deliveredMassKg(), orderAfter.deliveredMassKg(), 1.0e-9,
                "admission alone cannot fabricate delivered cargo");
        assertEquals(outbound.cargoMassKg(), afterFreighter.cargoMassKg(), 1.0e-9,
                "tactical escort resolution cannot mutate physical cargo mass directly");

        byte[] checkpoint = Stage20GeneratedWorldRuntimePersistenceCodec.encode(runtime.captureState());
        assertArrayEquals(checkpoint, Stage20GeneratedWorldRuntimePersistenceCodec.encode(
                        Stage20GeneratedWorldRuntimePersistenceCodec.decode(checkpoint)),
                "post-B08 generated-world checkpoint must be byte stable");
        assertFalse(orderAfter.orderId().isBlank());
        return new ScenarioResult(checkpoint, interdictorAlive, escortAlive, routeAdmitted,
                orderBefore.orderId(), orderAfter.orderId());
    }

    private static EngineeringComponent engineeringFor(
            Stage22CorePairTacticalFactory.Duel core,
            long entityId) {
        return core.weapons().battleState().combatants().stream()
                .filter(value -> value.spec().entityId() == entityId)
                .findFirst().orElseThrow().engineering();
    }

    private static EngineeringComponent copy(EngineeringComponent source) {
        return new EngineeringComponent(source.fit, source.runtimeState, source.instanceState);
    }

    private static Entity entity(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetPlacementState placement) {
        return runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().require(placement.localEntityId());
    }

    private static FreighterState assignedSourceFreighter(Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime) {
        return runtime.freight().capture().freighters().stream()
                .filter(value -> value.phase() == FreightPhase.AT_SOURCE)
                .filter(value -> !value.activeOrderId().isBlank())
                .filter(value -> runtime.freight().findOrder(value.activeOrderId())
                        .map(order -> order.orderedSystems().size() > 1).orElse(false))
                .filter(value -> matchingOutpost(runtime, value) != null)
                .findFirst().orElseThrow(() -> new AssertionError(
                        "generated B08 world lacks routed source freight with a physical outpost"));
    }

    private static MaterializedExtractionOutpost matchingOutpost(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FreighterState freighter) {
        TransportOrderState order = runtime.freight().findOrder(freighter.activeOrderId()).orElse(null);
        if (order == null) return null;
        StarSystemId source = order.orderedSystems().get(0);
        return runtime.industry().sourceOutposts().outposts().stream()
                .filter(value -> value.site().systemId().equals(source))
                .filter(value -> value.source().sourceState().outputCommodityId().equals(order.commodityId()))
                .findFirst().orElse(null);
    }

    private static void prepareOutboundCargo(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FreighterState freighter) {
        MaterializedExtractionOutpost outpost = matchingOutpost(runtime, freighter);
        if (outpost == null) throw new AssertionError("freight source has no matching extraction outpost");
        var extraction = runtime.extract(outpost.site().siteId(), 10_000d, HANDLING_SECONDS);
        assertTrue(extraction.committed());
        double mass = Math.min(extraction.outputMassStoredKg(), 1_000d);
        assertTrue(mass > 0d);
        assertTrue(runtime.transferOutpostToOrderSource(
                freighter.fleetId(), outpost.site().siteId(), mass, HANDLING_SECONDS).transferred());
        assertTrue(runtime.loadAtOrderSource(
                freighter.fleetId(), mass, 0d, HANDLING_SECONDS).transferred());
        runtime.freight().dispatchOutbound(freighter.fleetId(), 0d);
    }

    private static StrategicOperationState interception(
            MilitaryFleet actor,
            StarSystemId from,
            StarSystemId to,
            long tick) {
        OperationState operation = new OperationState(
                OPERATION_ID,
                OperationType.INTERCEPTION,
                1L,
                1L,
                actor.factionId(),
                List.of(actor.fleetId()),
                from,
                to,
                "system:" + to.value(),
                RulesOfEngagement.DECLARED_HOSTILES,
                new SupplyPolicy(0, 0, 100L),
                new WithdrawalPolicy(from, 0, true, true),
                OperationStatus.ACTIVE,
                tick,
                tick,
                -1L,
                null,
                null);
        return new StrategicOperationState(OPERATION_ID + 1L, List.of(operation));
    }

    private static List<MilitaryFleet> militaryFleets(Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime) {
        ArrayList<MilitaryFleet> result = new ArrayList<>();
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) continue;
            Entity entity = entity(runtime, placement);
            EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            if (engineering != null && faction != null) {
                result.add(new MilitaryFleet(placement.id(), faction.factionId));
            }
        }
        result.sort(Comparator.comparing(MilitaryFleet::fleetId));
        return List.copyOf(result);
    }

    private static void moveFleetByOrdinaryRoute(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId,
            StarSystemId destination) {
        FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
        List<StarSystemId> route = route(runtime, placement.systemId(), destination);
        for (int index = 1; index < route.size(); index++) {
            GeneratedWorldFtlTestSupport.placeAtOutgoingEndpoint(runtime, fleetId, route.get(index));
            runtime.world().requestFleetJump(fleetId, route.get(index));
            for (int attempt = 0; attempt < 400 && runtime.world().findFleetJump(fleetId).isPresent(); attempt++) {
                runtime.advanceFrame(0.25f);
            }
            assertTrue(runtime.world().findFleetJump(fleetId).isEmpty());
            if (index + 1 < route.size()) awaitFittedCooldown(runtime, fleetId);
        }
    }

    private static void awaitFittedCooldown(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId) {
        for (int attempt = 0; attempt < 400; attempt++) {
            FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
            EngineeringComponent engineering = entity(runtime, placement).getComponent(EngineeringComponent.class);
            if (engineering == null || engineering.runtimeState.ftlCooldownSecondsByMount().values().stream()
                    .noneMatch(value -> value > 0d)) return;
            runtime.advanceFrame(0.25f);
        }
        throw new AssertionError("B08 military FTL cooldown did not clear");
    }

    private static List<StarSystemId> route(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            StarSystemId origin,
            StarSystemId destination) {
        if (origin.equals(destination)) return List.of(origin);
        ArrayDeque<StarSystemId> queue = new ArrayDeque<>();
        Map<StarSystemId, StarSystemId> previous = new HashMap<>();
        queue.add(origin);
        previous.put(origin, null);
        while (!queue.isEmpty()) {
            StarSystemId current = queue.removeFirst();
            for (StarSystemId neighbor : runtime.world().getTopology().neighbors(current)) {
                if (previous.containsKey(neighbor)) continue;
                previous.put(neighbor, current);
                if (neighbor.equals(destination)) {
                    ArrayList<StarSystemId> reverse = new ArrayList<>();
                    StarSystemId cursor = destination;
                    while (cursor != null) {
                        reverse.add(cursor);
                        cursor = previous.get(cursor);
                    }
                    java.util.Collections.reverse(reverse);
                    return List.copyOf(reverse);
                }
                queue.addLast(neighbor);
            }
        }
        throw new AssertionError("generated topology has no B08 military route");
    }

    private record MilitaryFleet(FleetId fleetId, int factionId) { }

    private record ScenarioResult(
            byte[] checkpoint,
            boolean interdictorAlive,
            boolean escortAlive,
            boolean routeAdmitted,
            String orderIdBefore,
            String orderIdAfter) {
        private ScenarioResult {
            checkpoint = Arrays.copyOf(checkpoint, checkpoint.length);
        }
    }
}
