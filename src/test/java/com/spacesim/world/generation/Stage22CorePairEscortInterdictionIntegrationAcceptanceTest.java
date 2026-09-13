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
import com.spacesim.ship.ShipDamageRuntime;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipInstanceRuntimeState;
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
import java.util.TreeMap;

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
 *
 * <p>Two real physical branches are required. The pristine control demonstrates that an unresolved
 * surviving interdictor continues to block traffic. The catastrophic-loss branch starts from a
 * heavily damaged but explicitly still-alive exact M22.6 fit, mirroring the already accepted
 * Stage-21E physical-loss protocol; only Stage 19 is allowed to apply the final destructive effect.
 * This is a physical initial condition, not a synthetic combat-result flag or manual fleet removal.</p>
 */
class Stage22CorePairEscortInterdictionIntegrationAcceptanceTest {
    private static final long OPERATION_ID = 22_608L;
    private static final long TACTICAL_TICKS = 1_200L;
    private static final double HANDLING_SECONDS = 3_600d;
    private static final double CRITICAL_INTEGRITY = 1.0e-6d;
    private static final double PRISTINE_SEPARATION_M = 1_430d;
    private static final double CRITICAL_SEPARATION_M = 600d;
    private static final float SIMULATION_WAIT_FRAME_SECONDS = 10f;
    private static final int MAX_COOLDOWN_WAIT_FRAMES = 40;

    @Test
    void exactStage19OutcomeCausallyGatesTheSamePhysicalFreightOrder() {
        ScenarioResult pristineFirst = run(Permutation.DEFAULT, InterdictorCondition.PRISTINE);
        ScenarioResult pristineSecond = run(Permutation.DEFAULT, InterdictorCondition.PRISTINE);
        ScenarioResult criticalDefaultFirst = run(Permutation.DEFAULT, InterdictorCondition.CRITICAL_BUT_ALIVE);
        ScenarioResult criticalDefaultSecond = run(Permutation.DEFAULT, InterdictorCondition.CRITICAL_BUT_ALIVE);
        ScenarioResult criticalMirroredFirst = run(Permutation.MIRRORED, InterdictorCondition.CRITICAL_BUT_ALIVE);
        ScenarioResult criticalMirroredSecond = run(Permutation.MIRRORED, InterdictorCondition.CRITICAL_BUT_ALIVE);

        assertArrayEquals(pristineFirst.checkpoint(), pristineSecond.checkpoint(),
                "pristine B08 control must replay byte deterministically");
        assertArrayEquals(criticalDefaultFirst.checkpoint(), criticalDefaultSecond.checkpoint(),
                "default catastrophic-loss B08 branch must replay byte deterministically");
        assertArrayEquals(criticalMirroredFirst.checkpoint(), criticalMirroredSecond.checkpoint(),
                "mirrored catastrophic-loss B08 branch must replay byte deterministically");

        assertTrue(pristineFirst.interdictorAlive(),
                "pristine control must preserve a real physical denied-route branch");
        assertFalse(pristineFirst.routeAdmitted(),
                "same freight hop must remain denied while the real interdictor survives");
        assertFalse(criticalDefaultFirst.interdictorAlive(),
                "Stage 19 must destroy the critically damaged default interdictor itself");
        assertTrue(criticalDefaultFirst.routeAdmitted(),
                "destroying the default interdictor must admit the same physical freight hop");
        assertFalse(criticalMirroredFirst.interdictorAlive(),
                "Stage 19 must destroy the critically damaged mirrored interdictor itself");
        assertTrue(criticalMirroredFirst.routeAdmitted(),
                "destroying the mirrored interdictor must admit the same physical freight hop");

        for (ScenarioResult result : List.of(pristineFirst, criticalDefaultFirst, criticalMirroredFirst)) {
            assertEquals(result.routeAdmitted(), !result.interdictorAlive(),
                    "freight admission must be derived only from physical interdictor survival");
            assertEquals(result.orderIdBefore(), result.orderIdAfter(),
                    "combat must gate the same persistent freight order rather than replacing it");
        }
    }

    private static ScenarioResult run(Permutation permutation, InterdictorCondition condition) {
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

        Stage22CorePairTacticalFactory.Duel core = Stage22CorePairTacticalFactory.createDestroyerDuel(permutation);
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
        EngineeringComponent installedInterdictor = interdictorEntity.getComponent(EngineeringComponent.class);
        if (condition == InterdictorCondition.CRITICAL_BUT_ALIVE) {
            applyCriticalButSurvivingPhysicalState(core, installedInterdictor);
        }

        LocalPhysicalKinematics anchor = runtime.arrival().materialization(from)
                .physicalState(escortPlacement.localEntityId()).orElseThrow();
        LocalPhysicalKinematics targetPhysical = condition == InterdictorCondition.CRITICAL_BUT_ALIVE
                ? LocalPhysicalKinematics.stationary(anchor.position().translated(0d, CRITICAL_SEPARATION_M))
                : new LocalPhysicalKinematics(
                        anchor.position().translated(PRISTINE_SEPARATION_M, 0d), 0d, 0d);
        runtime.arrival().materialization(from).updatePhysicalState(
                interdictorPlacement.localEntityId(), targetPhysical);

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

    private static void applyCriticalButSurvivingPhysicalState(
            Stage22CorePairTacticalFactory.Duel core,
            EngineeringComponent engineering) {
        var hull = core.content().engineering().findHull(engineering.fit.hullId());
        var layout = core.protection().findHullDamageLayout(hull.id());
        if (layout == null) {
            throw new AssertionError("exact M22.6 interdictor hull lacks physical protection layout");
        }

        TreeMap<String, Double> compartmentIntegrity = new TreeMap<>();
        hull.compartments().forEach(compartment -> compartmentIntegrity.put(compartment.id(), 0d));
        String liveCompartment = hull.compartments().stream()
                .filter(compartment -> compartment.id().equals("engineering"))
                .findFirst()
                .orElseGet(() -> hull.compartments().stream().findFirst().orElseThrow())
                .id();
        compartmentIntegrity.put(liveCompartment, CRITICAL_INTEGRITY);

        TreeMap<String, Double> moduleIntegrity = new TreeMap<>();
        engineering.fit.installedModules().forEach(installed -> moduleIntegrity.put(installed.mountId(), 0d));
        ShipDamageRuntime.Snapshot damage = new ShipDamageRuntime.Snapshot(
                compartmentIntegrity, new DamageState(moduleIntegrity));
        assertFalse(ShipDamageRuntime.isFullyDestroyed(hull, engineering.fit, layout, damage),
                "critical B08 interdictor must still be a live physical ship before Stage 19");

        ShipInstanceRuntimeState previous = engineering.instanceState;
        engineering.setInstanceState(new ShipInstanceRuntimeState(
                damage,
                Map.of(),
                previous.maintenance(),
                previous.weaponLoadout(),
                previous.weaponMountRuntime()));
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
            awaitJump(runtime, fleetId);
            if (index + 1 < route.size()) awaitFittedCooldown(runtime, fleetId);
        }
    }

    private static void awaitJump(Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime, FleetId fleetId) {
        int phaseTransitions = 0;
        while (true) {
            var initial = runtime.world().findFleetJump(fleetId);
            if (initial.isEmpty()) return;
            var phase = initial.orElseThrow();
            long phaseStartedTick = phase.phaseStartedTick();
            long phaseDeadlineTick = phase.phaseEndsTick() + 1L;
            while (true) {
                var current = runtime.world().findFleetJump(fleetId);
                if (current.isEmpty()) return;
                var state = current.orElseThrow();
                if (state.phase() != phase.phase() || state.phaseStartedTick() != phaseStartedTick) break;
                long worldTick = runtime.world().getAuthoritativeWorldTick();
                if (worldTick > phaseDeadlineTick) {
                    throw new AssertionError("ordinary FTL phase exceeded its authoritative phaseEndsTick: " + state);
                }
                runtime.advanceFrame(SIMULATION_WAIT_FRAME_SECONDS);
            }
            phaseTransitions++;
            if (phaseTransitions > 8) {
                throw new AssertionError("ordinary FTL jump exceeded bounded canonical phase transitions");
            }
        }
    }

    private static void awaitFittedCooldown(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId) {
        for (int attempt = 0; attempt < MAX_COOLDOWN_WAIT_FRAMES; attempt++) {
            FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
            EngineeringComponent engineering = entity(runtime, placement).getComponent(EngineeringComponent.class);
            if (engineering == null || engineering.runtimeState.ftlCooldownSecondsByMount().values().stream()
                    .noneMatch(value -> value > 0d)) return;
            runtime.advanceFrame(SIMULATION_WAIT_FRAME_SECONDS);
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

    private enum InterdictorCondition {
        PRISTINE,
        CRITICAL_BUT_ALIVE
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
