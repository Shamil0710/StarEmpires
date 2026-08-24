package com.spacesim.world.generation;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase;
import com.spacesim.persistence.Stage20FreightPersistentState.FreighterState;
import com.spacesim.persistence.Stage20FreightPersistentState.TransportOrderState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage20SourceOutpostMaterializer.MaterializedExtractionOutpost;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage21EGeneratedWorldTrafficRuntimeAcceptanceTest {
    private static final double HANDLING_SECONDS = 3_600d;

    @Test
    void physicalHostileBlockadeDeniesActualSourceHandlingWithoutMutatingFreight() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime = newRuntime();
        FreighterState freighter = assignedSourceFreighter(runtime);
        TransportOrderState order = runtime.freight().findOrder(freighter.activeOrderId()).orElseThrow();
        StarSystemId source = order.orderedSystems().get(0);
        int trafficFactionId = runtime.world().findFactionRuntimeId(freighter.stableFactionId()).orElseThrow();
        MilitaryFleet hostile = hostileMilitary(runtime, trafficFactionId);
        moveFleetByOrdinaryRoute(runtime, hostile.fleetId(), source);

        StrategicOperationState operations = operation(
                OperationType.BLOCKADE,
                hostile,
                source,
                source,
                runtime.world().getAuthoritativeWorldTick());
        var before = runtime.freight().capture();
        Stage21EGeneratedWorldTrafficRuntime traffic = new Stage21EGeneratedWorldTrafficRuntime(runtime);

        IllegalStateException denied = assertThrows(IllegalStateException.class, () ->
                traffic.loadAtOrderSource(operations, freighter.fleetId(), 1d, 0d, 1d));

        assertTrue(denied.getMessage().contains("operation=1"));
        assertTrue(denied.getMessage().contains(hostile.fleetId().toString()));
        assertEquals(before, runtime.freight().capture(),
                "denied blockade handling must not reach the ordinary cargo mutation boundary");
    }

    @Test
    void physicalHostileInterdictionDeniesActualNextHopWithoutStartingJump() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime = newRuntime();
        FreighterState sourceFreighter = assignedSourceFreighter(runtime);
        prepareOutboundCargo(runtime, sourceFreighter);
        FreighterState outbound = runtime.freight().findFreighter(sourceFreighter.fleetId()).orElseThrow();
        assertEquals(FreightPhase.OUTBOUND, outbound.phase());
        TransportOrderState order = runtime.freight().findOrder(outbound.activeOrderId()).orElseThrow();
        StarSystemId from = outbound.currentSystemId();
        StarSystemId to = order.orderedSystems().get(outbound.routeIndex() + 1);
        int trafficFactionId = runtime.world().findFactionRuntimeId(outbound.stableFactionId()).orElseThrow();
        MilitaryFleet hostile = hostileMilitary(runtime, trafficFactionId);
        moveFleetByOrdinaryRoute(runtime, hostile.fleetId(), from);

        StrategicOperationState operations = operation(
                OperationType.INTERCEPTION,
                hostile,
                from,
                to,
                runtime.world().getAuthoritativeWorldTick());
        var before = runtime.freight().capture();
        Stage21EGeneratedWorldTrafficRuntime traffic = new Stage21EGeneratedWorldTrafficRuntime(runtime);

        IllegalStateException denied = assertThrows(IllegalStateException.class, () ->
                traffic.requestNextRouteHop(operations, outbound.fleetId()));

        assertTrue(denied.getMessage().contains("DENIED_BY_PHYSICAL_INTERDICTION"));
        assertEquals(before, runtime.freight().capture(),
                "denied edge admission must not advance ordinary freight route state");
        assertTrue(runtime.world().findFleetJump(outbound.fleetId()).isEmpty(),
                "denied edge admission must not start the ordinary jump FSM");
        FleetPlacementState placement = runtime.world().findFleet(outbound.fleetId()).orElseThrow();
        assertEquals(from, placement.systemId());
    }

    @Test
    void sameFactionOperationCannotBlockItsOwnOrdinaryFreightAdmission() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime = newRuntime();
        FreighterState freighter = assignedSourceFreighter(runtime);
        TransportOrderState order = runtime.freight().findOrder(freighter.activeOrderId()).orElseThrow();
        int trafficFactionId = runtime.world().findFactionRuntimeId(freighter.stableFactionId()).orElseThrow();
        MilitaryFleet friendly = militaryFleets(runtime).stream()
                .filter(value -> value.factionId() == trafficFactionId)
                .findFirst().orElseThrow();
        StarSystemId source = order.orderedSystems().get(0);
        moveFleetByOrdinaryRoute(runtime, friendly.fleetId(), source);
        StrategicOperationState operations = operation(
                OperationType.BLOCKADE,
                friendly,
                source,
                source,
                runtime.world().getAuthoritativeWorldTick());

        var availability = new com.spacesim.world.Stage21EOperationTrafficPolicy(
                new com.spacesim.world.PhysicalWarfareOperationService(runtime.world()))
                .handlingAvailability(operations, source, trafficFactionId);

        assertTrue(availability.available(), "friendly operation must not deny its own handling");
        assertEquals(0L, availability.denyingOperationId());
    }

    private static Stage20GeneratedWorldRuntimeBridge.LiveRuntime newRuntime() {
        return Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
    }

    private static FreighterState assignedSourceFreighter(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime) {
        return runtime.freight().capture().freighters().stream()
                .filter(value -> value.phase() == FreightPhase.AT_SOURCE)
                .filter(value -> !value.activeOrderId().isBlank())
                .filter(value -> runtime.freight().findOrder(value.activeOrderId())
                        .map(order -> order.orderedSystems().size() > 1)
                        .orElse(false))
                .filter(value -> matchingOutpost(runtime, value) != null)
                .findFirst().orElseThrow(() -> new AssertionError(
                        "generated acceptance world lacks routed source freight with a physical outpost"));
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
        assertTrue(extraction.committed(), "acceptance setup must extract finite physical source mass");
        double mass = Math.min(extraction.outputMassStoredKg(), 1_000d);
        assertTrue(mass > 0d);
        var hubTransfer = runtime.transferOutpostToOrderSource(
                freighter.fleetId(), outpost.site().siteId(), mass, HANDLING_SECONDS);
        assertTrue(hubTransfer.transferred(), "acceptance setup must move extracted mass to source hub");
        var load = runtime.loadAtOrderSource(
                freighter.fleetId(), mass, 0d, HANDLING_SECONDS);
        assertTrue(load.transferred(), "acceptance setup must load real source inventory into freight hold");
        runtime.freight().dispatchOutbound(freighter.fleetId(), 0d);
    }

    private static StrategicOperationState operation(
            OperationType type,
            MilitaryFleet actor,
            StarSystemId staging,
            StarSystemId objective,
            long tick) {
        OperationState operation = new OperationState(
                1L,
                type,
                1L,
                1L,
                actor.factionId(),
                List.of(actor.fleetId()),
                staging,
                objective,
                "system:" + objective.value(),
                RulesOfEngagement.DECLARED_HOSTILES,
                new SupplyPolicy(0, 0, 100L),
                new WithdrawalPolicy(staging, 0, true, true),
                OperationStatus.ACTIVE,
                tick,
                tick,
                -1L,
                null,
                null);
        return new StrategicOperationState(2L, List.of(operation));
    }

    private static MilitaryFleet hostileMilitary(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            int trafficFactionId) {
        return militaryFleets(runtime).stream()
                .filter(value -> value.factionId() != trafficFactionId)
                .findFirst().orElseThrow(() -> new AssertionError(
                        "generated acceptance world lacks hostile military fleet"));
    }

    private static List<MilitaryFleet> militaryFleets(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime) {
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
        return List.copyOf(result);
    }

    private static void moveFleetByOrdinaryRoute(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId,
            StarSystemId destination) {
        FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
        List<StarSystemId> route = route(runtime, placement.systemId(), destination);
        for (int index = 1; index < route.size(); index++) {
            runtime.world().requestFleetJump(fleetId, route.get(index));
            for (int attempt = 0; attempt < 400 && runtime.world().findFleetJump(fleetId).isPresent(); attempt++) {
                runtime.advanceFrame(0.25f);
            }
            assertTrue(runtime.world().findFleetJump(fleetId).isEmpty(),
                    "ordinary hostile movement must finish every topology hop");
            assertEquals(route.get(index), runtime.world().findFleet(fleetId).orElseThrow().systemId());
        }
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
        throw new AssertionError("generated topology has no physical route to traffic objective");
    }

    private record MilitaryFleet(FleetId fleetId, int factionId, StarSystemId systemId) { }
}
