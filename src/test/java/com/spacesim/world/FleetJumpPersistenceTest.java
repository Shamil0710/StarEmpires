package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.WorldStateCodec;
import com.spacesim.ship.ShipEngineeringRuntime.JumpFailure;
import com.spacesim.ship.ShipEngineeringRuntime.JumpPlan;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FleetJumpPersistenceTest {
    private static final ContentCatalog CONTENT = ContentCatalogLoader.loadDefault();
    private static final StarSystemId ALPHA = new StarSystemId(1L);
    private static final StarSystemId BETA = new StarSystemId(2L);
    private static final InstalledFit TEST_FIT = new InstalledFit("hull.persistence_ftl", List.of());

    @Test
    void activeTransitPhaseHasDeterministicSchemaV7RoundTrip() {
        WorldState base = world();
        Map<StarSystemId, SimulationSession> sessions = restoreSessions(base);
        FleetWorldService fleets = new FleetWorldService(
                sessions, base.nextFleetIdValue(), base.fleets());
        FleetJumpService jumps = new FleetJumpService(
                base.topology(), sessions, fleets,
                new JumpTransitTiming(1L, 1L, 1L, 10d), List.of());
        FleetPlacementState source = fleets.snapshots().stream()
                .filter(placement -> ALPHA.equals(placement.systemId()))
                .findFirst().orElseThrow();

        jumps.requestJump(source.id(), BETA, 0L, 40f, -15f);
        jumps.advance(2L);
        assertEquals(FleetJumpPhase.IN_TRANSIT, jumps.find(source.id()).orElseThrow().phase());
        assertEquals(FleetLocationKind.IN_TRANSIT, fleets.find(source.id()).orElseThrow().locationKind());
        assertFalse(sessions.get(ALPHA).getEntityRegistry().contains(source.localEntityId()));

        WorldState transit = snapshot(base, sessions, fleets, jumps);
        byte[] first = WorldStateCodec.encode(transit);
        WorldState decoded = WorldStateCodec.decode(first);

        assertEquals(transit, decoded);
        assertEquals(List.of(jumps.find(source.id()).orElseThrow()), decoded.fleetJumps());
        assertEquals(FleetLocationKind.IN_TRANSIT,
                decoded.fleets().stream()
                        .filter(placement -> placement.id().equals(source.id()))
                        .findFirst().orElseThrow().locationKind());
        assertArrayEquals(first, WorldStateCodec.encode(decoded));
    }

    @Test
    void fittedTransitSaveLoadKeepsCommittedEnergyHeatCooldownAndDoesNotCommitAgain() {
        WorldState base = world();
        Map<StarSystemId, SimulationSession> sessions = restoreSessions(base);
        FleetWorldService fleets = new FleetWorldService(
                sessions, base.nextFleetIdValue(), base.fleets());
        PersistenceResolver resolver = new PersistenceResolver();
        JumpTransitTiming timing = new JumpTransitTiming(1L, 1L, 1L, 0.01d);
        FleetJumpService jumps = new FleetJumpService(
                base.topology(), sessions, fleets, timing, resolver, List.of());
        FleetPlacementState source = fleets.snapshots().stream()
                .filter(placement -> ALPHA.equals(placement.systemId()))
                .findFirst().orElseThrow();
        Entity sourceEntity = sessions.get(ALPHA).getEntityRegistry().find(source.localEntityId());
        sourceEntity.add(new EngineeringComponent(TEST_FIT, healthyState()));

        FleetJumpState requested = jumps.requestJump(source.id(), BETA, 0L, 40f, -15f);
        jumps.advance(requested.phaseEndsTick());
        FleetJumpState pending = jumps.find(source.id()).orElseThrow();
        assertEquals(FleetJumpPhase.JUMP_PENDING, pending.phase());
        jumps.advance(pending.phaseEndsTick());
        FleetJumpState transitJump = jumps.find(source.id()).orElseThrow();
        assertEquals(FleetJumpPhase.IN_TRANSIT, transitJump.phase());
        assertEquals(1, resolver.commitCalls);

        FleetPlacementState committedTransit = fleets.find(source.id()).orElseThrow();
        assertEquals(62_000_000d,
                committedTransit.transitState().entityState().engineering().sharedBusEnergyJ(), 0d);

        WorldState transitState = snapshot(base, sessions, fleets, jumps);
        byte[] encoded = WorldStateCodec.encode(transitState);
        WorldState decoded = WorldStateCodec.decode(encoded);
        assertArrayEquals(encoded, WorldStateCodec.encode(decoded));

        Map<StarSystemId, SimulationSession> restoredSessions = restoreSessions(decoded);
        FleetWorldService restoredFleets = new FleetWorldService(
                restoredSessions, decoded.nextFleetIdValue(), decoded.fleets());
        PersistenceResolver restoredResolver = new PersistenceResolver();
        FleetJumpService restoredJumps = new FleetJumpService(
                decoded.topology(),
                restoredSessions,
                restoredFleets,
                timing,
                restoredResolver,
                decoded.fleetJumps());

        FleetPlacementState restoredTransit = restoredFleets.find(source.id()).orElseThrow();
        assertEquals(FleetLocationKind.IN_TRANSIT, restoredTransit.locationKind());
        assertEquals(62_000_000d,
                restoredTransit.transitState().entityState().engineering().sharedBusEnergyJ(), 0d);
        assertEquals(0, restoredResolver.commitCalls,
                "restoring an already committed IN_TRANSIT jump must not charge it again");

        restoredJumps.advance(transitJump.phaseEndsTick());
        FleetPlacementState arrived = restoredFleets.find(source.id()).orElseThrow();
        assertEquals(FleetLocationKind.IN_SYSTEM, arrived.locationKind());
        assertEquals(BETA, arrived.systemId());
        Entity arrivedEntity = restoredSessions.get(BETA).getEntityRegistry().find(arrived.localEntityId());
        EngineeringComponent arrivedEngineering = arrivedEntity.getComponent(EngineeringComponent.class);
        assertEquals(62_000_000d, arrivedEngineering.runtimeState.sharedBusEnergyJ(), 0d);
        assertEquals(10_000_000d,
                arrivedEngineering.runtimeState.localHeatJByMount().get("core_ftl"), 0d);
        assertEquals(60d,
                arrivedEngineering.runtimeState.ftlCooldownSecondsByMount().get("core_ftl"), 0d);
        assertEquals(0, restoredResolver.commitCalls);
    }

    private static RuntimeState healthyState() {
        return new RuntimeState(
                ConsumableState.empty(),
                100_000_000d,
                0d,
                Map.of("core_ftl", 0d),
                Map.of(),
                0d,
                Map.of("core_ftl", 0d));
    }

    private static WorldState snapshot(
            WorldState template,
            Map<StarSystemId, SimulationSession> sessions,
            FleetWorldService fleets,
            FleetJumpService jumps) {
        List<StarSystemSimulationState> systems = new ArrayList<>();
        for (StarSystemSimulationState system : template.systems()) {
            systems.add(new StarSystemSimulationState(
                    system.systemId(), sessions.get(system.systemId()).snapshot()));
        }
        return new WorldState(
                WorldState.CURRENT_VERSION,
                template.topology(),
                List.copyOf(systems),
                template.factions(),
                template.factionStrategies(),
                template.nextConstructionProjectIdValue(),
                template.constructionProjects(),
                template.factionEconomicPressures(),
                fleets.nextIdValue(),
                fleets.snapshots(),
                jumps.snapshots());
    }

    private static Map<StarSystemId, SimulationSession> restoreSessions(WorldState state) {
        Map<StarSystemId, SimulationSession> sessions = new HashMap<>();
        for (StarSystemSimulationState system : state.systems()) {
            sessions.put(system.systemId(), SimulationSession.restore(system.simulationState(), CONTENT));
        }
        return sessions;
    }

    private static WorldState world() {
        StarSystemNode alpha = new StarSystemNode(ALPHA, "Alpha", 0d, 0d);
        StarSystemNode beta = new StarSystemNode(BETA, "Beta", 100d, 0d);
        GalaxyTopology topology = new GalaxyTopology(
                new GalaxyId(1L),
                "Jump Persistence Galaxy",
                List.of(new SectorNode(new SectorId(1L), "Core", List.of(alpha, beta))),
                List.of(new JumpConnection(ALPHA, BETA)));
        return new WorldState(
                WorldState.CURRENT_VERSION,
                topology,
                List.of(
                        new StarSystemSimulationState(
                                ALPHA, SimulationSession.createDemo(0xA110L, CONTENT).snapshot()),
                        new StarSystemSimulationState(
                                BETA, SimulationSession.createDemo(0xBE70L, CONTENT).snapshot())));
    }

    private static final class PersistenceResolver implements FleetJumpService.FittedJumpResolver {
        private int commitCalls;

        @Override
        public JumpPlan plan(EngineeringComponent component) {
            return new JumpPlan(
                    true,
                    JumpFailure.NONE,
                    "core_ftl",
                    1_500d,
                    40_000_000d,
                    2_000_000d,
                    38_000_000d,
                    4_000_000d,
                    10d,
                    30d,
                    60d,
                    10_000_000d);
        }

        @Override
        public RuntimeState commit(EngineeringComponent component, JumpPlan plan) {
            commitCalls++;
            RuntimeState state = component.runtimeState;
            TreeMap<String, Double> localHeat = new TreeMap<>(state.localHeatJByMount());
            localHeat.merge(plan.mountId(), plan.jumpHeatJ(), Double::sum);
            TreeMap<String, Double> cooldowns = new TreeMap<>(state.ftlCooldownSecondsByMount());
            cooldowns.put(plan.mountId(), plan.cooldownSeconds());
            return new RuntimeState(
                    state.consumables(),
                    state.sharedBusEnergyJ() - plan.storedEnergyDrawJ(),
                    state.shipHeatStoredJ(),
                    localHeat,
                    state.thrustLimitNByMount(),
                    state.coolantBusCapacityW(),
                    cooldowns);
        }
    }
}
