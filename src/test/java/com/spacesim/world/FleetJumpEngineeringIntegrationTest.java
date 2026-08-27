package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.ship.ShipEngineeringRuntime.JumpFailure;
import com.spacesim.ship.ShipEngineeringRuntime.JumpPlan;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FleetJumpEngineeringIntegrationTest {
    private static final ContentCatalog CONTENT = ContentCatalogLoader.loadDefault();
    private static final StarSystemId ALPHA = new StarSystemId(1L);
    private static final StarSystemId BETA = new StarSystemId(2L);
    private static final StarSystemId GAMMA = new StarSystemId(3L);
    private static final InstalledFit TEST_FIT = new InstalledFit("hull.test_ftl", List.of());

    @Test
    void fittedJumpUsesPhysicalSpoolAndTransitAndCommitsExactlyOnce() {
        FakeResolver resolver = new FakeResolver();
        Fixture fixture = fixture(new JumpTransitTiming(2L, 3L, 2L, 0.01d), resolver);
        FleetPlacementState source = fleetIn(fixture.fleets(), ALPHA);
        EngineeringComponent engineering = installEngineering(fixture, source, healthyState());
        float fixedStep = fixture.sessions().get(ALPHA).getClock().getFixedStepSeconds();

        FleetJumpState requested = fixture.jumps().requestJump(source.id(), BETA, 0L, 50f, -25f);
        assertEquals(FleetJumpPhase.MOVING_TO_JUMP, requested.phase());
        assertEquals(2L, requested.phaseEndsTick());
        assertEquals(100_000_000d, engineering.runtimeState.sharedBusEnergyJ(), 0d);
        assertEquals(0, resolver.commitCalls);

        fixture.jumps().advance(2L);
        FleetJumpState pending = fixture.jumps().find(source.id()).orElseThrow();
        long expectedSpoolTicks = secondsToTicks(10d, fixedStep);
        assertEquals(FleetJumpPhase.JUMP_PENDING, pending.phase());
        assertEquals(2L + expectedSpoolTicks, pending.phaseEndsTick());
        assertEquals(100_000_000d, engineering.runtimeState.sharedBusEnergyJ(), 0d,
                "spool planning must not spend stored energy before the commit boundary");
        assertEquals(0, resolver.commitCalls);

        fixture.jumps().advance(pending.phaseEndsTick());
        FleetJumpState transit = fixture.jumps().find(source.id()).orElseThrow();
        long expectedTransitTicks = secondsToTicks(30d, fixedStep);
        assertEquals(FleetJumpPhase.IN_TRANSIT, transit.phase());
        assertEquals(pending.phaseEndsTick() + expectedTransitTicks, transit.phaseEndsTick(),
                "fitted edgeTransitSeconds must replace the deliberately different legacy timing");
        assertEquals(1, resolver.commitCalls);

        FleetPlacementState detached = fixture.fleets().find(source.id()).orElseThrow();
        assertEquals(FleetLocationKind.IN_TRANSIT, detached.locationKind());
        assertFalse(fixture.sessions().get(ALPHA).getEntityRegistry().contains(source.localEntityId()));
        assertEquals(62_000_000d,
                detached.transitState().entityState().engineering().sharedBusEnergyJ(), 0d);
        assertMountValue(
                detached.transitState().entityState().engineering().localHeatJByMount(),
                "core_ftl",
                10_000_000d);
        assertMountValue(
                detached.transitState().entityState().engineering().ftlCooldownSecondsByMount(),
                "core_ftl",
                60d);

        fixture.jumps().advance(pending.phaseEndsTick() + 1L);
        assertEquals(1, resolver.commitCalls, "IN_TRANSIT advance must never charge the same jump twice");
        assertEquals(62_000_000d,
                fixture.fleets().find(source.id()).orElseThrow()
                        .transitState().entityState().engineering().sharedBusEnergyJ(), 0d);

        fixture.jumps().advance(transit.phaseEndsTick());
        FleetPlacementState arrived = fixture.fleets().find(source.id()).orElseThrow();
        assertEquals(FleetLocationKind.IN_SYSTEM, arrived.locationKind());
        assertEquals(BETA, arrived.systemId());
        Entity destinationEntity = fixture.sessions().get(BETA).getEntityRegistry().find(arrived.localEntityId());
        EngineeringComponent destinationEngineering = destinationEntity.getComponent(EngineeringComponent.class);
        assertEquals(62_000_000d, destinationEngineering.runtimeState.sharedBusEnergyJ(), 0d);
        assertEquals(10_000_000d,
                destinationEngineering.runtimeState.localHeatJByMount().get("core_ftl"), 0d);
        assertEquals(30d,
                destinationEngineering.runtimeState.ftlCooldownSecondsByMount().get("core_ftl"), 1e-6,
                "the physical 30 second edge transit must consume half of the 60 second cooldown");
        assertEquals(1, resolver.commitCalls);
    }

    @Test
    void cancellingDuringSpoolSpendsNoPhysicalResources() {
        FakeResolver resolver = new FakeResolver();
        Fixture fixture = fixture(new JumpTransitTiming(1L, 1L, 1L, 0.01d), resolver);
        FleetPlacementState source = fleetIn(fixture.fleets(), ALPHA);
        RuntimeState initial = healthyState();
        EngineeringComponent engineering = installEngineering(fixture, source, initial);

        FleetJumpState requested = fixture.jumps().requestJump(source.id(), BETA, 0L, 1f, 2f);
        fixture.jumps().advance(requested.phaseEndsTick());
        assertEquals(FleetJumpPhase.JUMP_PENDING,
                fixture.jumps().find(source.id()).orElseThrow().phase());

        assertTrue(fixture.jumps().remove(source.id()));
        assertTrue(fixture.jumps().find(source.id()).isEmpty());
        assertEquals(initial, engineering.runtimeState);
        assertEquals(0, resolver.commitCalls);
        assertEquals(FleetLocationKind.IN_SYSTEM,
                fixture.fleets().find(source.id()).orElseThrow().locationKind());
        assertEquals(ALPHA, fixture.fleets().find(source.id()).orElseThrow().systemId());
    }

    @Test
    void fittedPhysicalRejectionCannotFallBackToLegacyJump() {
        FakeResolver resolver = new FakeResolver();
        Fixture fixture = fixture(JumpTransitTiming.DEFAULT, resolver);
        FleetPlacementState source = fleetIn(fixture.fleets(), ALPHA);
        RuntimeState emptyBattery = withSharedEnergy(healthyState(), 0d);
        EngineeringComponent engineering = installEngineering(fixture, source, emptyBattery);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> fixture.jumps().requestJump(source.id(), BETA, 0L, 0f, 0f));
        assertTrue(failure.getMessage().contains(JumpFailure.STORED_ENERGY_UNAVAILABLE.name()));
        assertTrue(fixture.jumps().find(source.id()).isEmpty());
        assertEquals(emptyBattery, engineering.runtimeState);
        assertEquals(0, resolver.commitCalls);
        assertEquals(FleetLocationKind.IN_SYSTEM,
                fixture.fleets().find(source.id()).orElseThrow().locationKind());
    }

    @Test
    void ordinaryWorldTimeRecoversTemporaryStoredEnergyFailureWithoutActiveCooldown() {
        FakeResolver resolver = new FakeResolver(100_000_000_000d);
        Fixture fixture = fixture(JumpTransitTiming.DEFAULT, resolver);
        FleetPlacementState source = fleetIn(fixture.fleets(), ALPHA);
        EngineeringComponent engineering = installEngineering(
                fixture, source, withSharedEnergy(healthyState(), 0d));

        fixture.jumps().advance(1L);

        assertEquals(100_000_000d, engineering.runtimeState.sharedBusEnergyJ(), 0d);
        assertEquals(1, resolver.idleAdvanceCalls);
        assertTrue(resolver.plan(engineering).allowed(),
                "ordinary elapsed world time must restore a temporary fitted FTL energy boundary");
    }

    @Test
    void physicalStateChangeDuringApproachCancelsBeforeCommit() {
        FakeResolver resolver = new FakeResolver();
        Fixture fixture = fixture(new JumpTransitTiming(2L, 1L, 1L, 0.01d), resolver);
        FleetPlacementState source = fleetIn(fixture.fleets(), ALPHA);
        EngineeringComponent engineering = installEngineering(fixture, source, healthyState());

        FleetJumpState requested = fixture.jumps().requestJump(source.id(), BETA, 0L, 0f, 0f);
        engineering.setRuntimeState(withSharedEnergy(engineering.runtimeState, 0d));
        fixture.jumps().advance(requested.phaseEndsTick());

        assertTrue(fixture.jumps().find(source.id()).isEmpty());
        assertEquals(0d, engineering.runtimeState.sharedBusEnergyJ(), 0d);
        assertEquals(0, resolver.commitCalls);
        assertEquals(FleetLocationKind.IN_SYSTEM,
                fixture.fleets().find(source.id()).orElseThrow().locationKind());
    }

    private static EngineeringComponent installEngineering(
            Fixture fixture,
            FleetPlacementState placement,
            RuntimeState state) {
        Entity entity = fixture.sessions().get(placement.systemId())
                .getEntityRegistry().find(placement.localEntityId());
        EngineeringComponent component = new EngineeringComponent(TEST_FIT, state);
        entity.add(component);
        return component;
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

    private static RuntimeState withSharedEnergy(RuntimeState state, double sharedEnergyJ) {
        return new RuntimeState(
                state.consumables(),
                sharedEnergyJ,
                state.shipHeatStoredJ(),
                state.localHeatJByMount(),
                state.thrustLimitNByMount(),
                state.coolantBusCapacityW(),
                state.ftlCooldownSecondsByMount());
    }

    private static long secondsToTicks(double seconds, float fixedStepSeconds) {
        return Math.max(1L, (long) StrictMath.ceil(seconds / fixedStepSeconds));
    }

    private static void assertMountValue(
            List<com.spacesim.persistence.EntityState.MountDoubleState> values,
            String mountId,
            double expected) {
        double actual = values.stream()
                .filter(value -> mountId.equals(value.mountId()))
                .findFirst()
                .orElseThrow()
                .value();
        assertEquals(expected, actual, 0d);
    }

    private static FleetPlacementState fleetIn(FleetWorldService fleets, StarSystemId systemId) {
        return fleets.snapshots().stream()
                .filter(placement -> placement.locationKind() == FleetLocationKind.IN_SYSTEM)
                .filter(placement -> systemId.equals(placement.systemId()))
                .findFirst()
                .orElseThrow();
    }

    private static Fixture fixture(JumpTransitTiming timing, FakeResolver resolver) {
        WorldState state = worldState();
        Map<StarSystemId, SimulationSession> sessions = new HashMap<>();
        for (StarSystemSimulationState system : state.systems()) {
            sessions.put(system.systemId(), SimulationSession.restore(system.simulationState(), CONTENT));
        }
        FleetWorldService fleets = new FleetWorldService(
                sessions, state.nextFleetIdValue(), state.fleets());
        FleetJumpService jumps = new FleetJumpService(
                state.topology(), sessions, fleets, timing, resolver, List.of());
        return new Fixture(state, sessions, fleets, jumps);
    }

    private static WorldState worldState() {
        StarSystemNode alpha = new StarSystemNode(ALPHA, "Alpha", 0d, 0d);
        StarSystemNode beta = new StarSystemNode(BETA, "Beta", 100d, 0d);
        StarSystemNode gamma = new StarSystemNode(GAMMA, "Gamma", 200d, 0d);
        GalaxyTopology topology = new GalaxyTopology(
                new GalaxyId(1L),
                "Engineering Jump Test Galaxy",
                List.of(new SectorNode(new SectorId(1L), "Core", List.of(alpha, beta, gamma))),
                List.of(new JumpConnection(ALPHA, BETA), new JumpConnection(BETA, GAMMA)));
        return new WorldState(
                WorldState.CURRENT_VERSION,
                topology,
                List.of(
                        new StarSystemSimulationState(
                                ALPHA, SimulationSession.createDemo(0xA110L, CONTENT).snapshot()),
                        new StarSystemSimulationState(
                                BETA, SimulationSession.createDemo(0xBE70L, CONTENT).snapshot()),
                        new StarSystemSimulationState(
                                GAMMA, SimulationSession.createDemo(0x6A660L, CONTENT).snapshot())));
    }

    private static final class FakeResolver implements FleetJumpService.FittedJumpResolver {
        private final double rechargePowerW;
        private int commitCalls;
        private int idleAdvanceCalls;

        private FakeResolver() {
            this(0d);
        }

        private FakeResolver(double rechargePowerW) {
            if (!Double.isFinite(rechargePowerW) || rechargePowerW < 0d) {
                throw new IllegalArgumentException("rechargePowerW must be finite and non-negative");
            }
            this.rechargePowerW = rechargePowerW;
        }

        @Override
        public JumpPlan plan(EngineeringComponent component) {
            RuntimeState state = component.runtimeState;
            if (state.ftlCooldownSecondsByMount().getOrDefault("core_ftl", 0d) > 0d) {
                return rejected(JumpFailure.COOLDOWN_ACTIVE);
            }
            if (state.sharedBusEnergyJ() < 38_000_000d) {
                return rejected(JumpFailure.STORED_ENERGY_UNAVAILABLE);
            }
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

        @Override
        public RuntimeState advanceIdle(EngineeringComponent component, double deltaSeconds) {
            idleAdvanceCalls++;
            RuntimeState state = component.runtimeState;
            TreeMap<String, Double> cooldowns = new TreeMap<>(state.ftlCooldownSecondsByMount());
            cooldowns.replaceAll((mountId, remaining) -> Math.max(0d, remaining - deltaSeconds));
            double energy = Math.min(
                    100_000_000d,
                    state.sharedBusEnergyJ() + rechargePowerW * deltaSeconds);
            return new RuntimeState(
                    state.consumables(),
                    energy,
                    state.shipHeatStoredJ(),
                    state.localHeatJByMount(),
                    state.thrustLimitNByMount(),
                    state.coolantBusCapacityW(),
                    cooldowns);
        }

        private static JumpPlan rejected(JumpFailure failure) {
            return new JumpPlan(
                    false,
                    failure,
                    "core_ftl",
                    1_500d,
                    0d,
                    0d,
                    0d,
                    0d,
                    0d,
                    0d,
                    0d,
                    0d);
        }
    }

    private record Fixture(
            WorldState state,
            Map<StarSystemId, SimulationSession> sessions,
            FleetWorldService fleets,
            FleetJumpService jumps) {
    }
}
