package com.spacesim.ui;

import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.LiveRuntime;
import com.spacesim.persistence.Stage21AGeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage21BGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21CGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21DGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21HGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimeMigration;
import com.spacesim.warfare.Stage19ConflictRuntime;
import com.spacesim.world.DiplomaticLifecycleState;
import com.spacesim.world.FactionStrategicIntentState;
import com.spacesim.world.FleetCommandGroupService;
import com.spacesim.world.FleetCommandState;
import com.spacesim.world.FleetForceRegistry;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetOperationalAvailability;
import com.spacesim.world.FleetReadinessEvaluator;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage21IFinalLivingWorldUiProjectorAcceptanceTest {

    @Test
    void generatedMilitaryFleetProjectsAuthoritativeReadinessAndExplicitSupplyWithoutMutation() {
        LiveRuntime stage20 = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED + 41L).runtime();
        var world = stage20.captureState().worldState();
        var evaluator = new FleetReadinessEvaluator(ShipEngineeringCatalogLoader.loadDefault());
        FleetForceRegistry bootstrapForces = FleetForceRegistry.reconstruct(world, evaluator, Map.of());
        var selected = bootstrapForces.entries().stream()
                .filter(entry -> entry.factionId() >= 0)
                .filter(entry -> entry.locationKind() == FleetLocationKind.IN_SYSTEM)
                .findFirst()
                .orElseThrow(() -> new AssertionError("generated world must contain an in-system military FleetId"));
        String viewer = world.factionIdentities().stream()
                .filter(identity -> identity.runtimeFactionId() == selected.factionId())
                .map(identity -> identity.stableFactionId())
                .findFirst()
                .orElseThrow();

        Map<com.spacesim.world.FleetId, FleetOperationalAvailability> observed = Map.of(
                selected.fleetId(),
                new FleetOperationalAvailability(1_000_000, 7_500));
        FleetForceRegistry observedForces = FleetForceRegistry.reconstruct(world, evaluator, observed);
        FleetCommandState commands = new FleetCommandGroupService(world.topology()).form(
                FleetCommandState.empty(),
                observedForces,
                selected.factionId(),
                "Generated readiness group",
                List.of(selected.fleetId()),
                selected.systemId(),
                false,
                false,
                5_000).state();

        var checkpoint = checkpoint(stage20, commands);
        byte[] before = Stage21HGeneratedWorldRuntimePersistenceCodec.encode(checkpoint);
        Stage21IFinalLivingWorldUiProjector projector = new Stage21IFinalLivingWorldUiProjector();

        Stage21ILivingWorldUiSnapshot first = projector.project(checkpoint, viewer, observed);
        Stage21ILivingWorldUiSnapshot second = projector.project(checkpoint, viewer, observed);

        assertEquals(first, second);
        assertArrayEquals(before, Stage21HGeneratedWorldRuntimePersistenceCodec.encode(checkpoint));
        assertEquals(1, first.military().size());
        var row = first.military().get(0);
        assertEquals(List.of(selected.fleetId().toString()), row.fleetIds());
        assertFalse(row.readiness().contains("UNOBSERVED"));
        assertTrue(row.readiness().contains("overallBps="));
        assertEquals("accessBps=7500", row.supply());
        assertTrue(row.authorityRef().contains("stage21d.force-registry"));
        assertTrue(row.authorityRef().contains("stage21d.readiness"));
    }

    @Test
    void missingOperationalAvailabilityFailsClosedInsteadOfInventingCrewOrSupply() {
        LiveRuntime stage20 = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED + 43L).runtime();
        var world = stage20.captureState().worldState();
        var evaluator = new FleetReadinessEvaluator(ShipEngineeringCatalogLoader.loadDefault());
        FleetForceRegistry forces = FleetForceRegistry.reconstruct(world, evaluator, Map.of());
        var selected = forces.entries().stream()
                .filter(entry -> entry.factionId() >= 0)
                .filter(entry -> entry.locationKind() == FleetLocationKind.IN_SYSTEM)
                .findFirst()
                .orElseThrow();
        String viewer = world.factionIdentities().stream()
                .filter(identity -> identity.runtimeFactionId() == selected.factionId())
                .map(identity -> identity.stableFactionId())
                .findFirst()
                .orElseThrow();
        FleetCommandState commands = new FleetCommandGroupService(world.topology()).form(
                FleetCommandState.empty(),
                forces,
                selected.factionId(),
                "Fail closed group",
                List.of(selected.fleetId()),
                selected.systemId(),
                false,
                false,
                5_000).state();

        Stage21ILivingWorldUiSnapshot snapshot = new Stage21IFinalLivingWorldUiProjector()
                .project(checkpoint(stage20, commands), viewer);

        assertEquals(1, snapshot.military().size());
        assertEquals("accessBps=0", snapshot.military().get(0).supply());
        assertTrue(snapshot.military().get(0).readiness().startsWith("overallBps=0;"));
    }

    private static com.spacesim.persistence.Stage21HGeneratedWorldRuntimePersistentState checkpoint(
            LiveRuntime stage20,
            FleetCommandState commands) {
        var stage20State = stage20.captureState();
        List<String> actors = stage20State.worldState().factionIdentities().stream()
                .map(identity -> identity.stableFactionId())
                .sorted()
                .toList();
        var stage21A = Stage21AGeneratedWorldRuntimeBridge.materializeBootstrap(stage20, actors, 30L);
        Stage21BGeneratedWorldRuntimePersistentState stage21B = new Stage21BGeneratedWorldRuntimePersistentState(
                Stage21BGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                Stage21BGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                stage21A.captureState(),
                actors.stream().map(FactionStrategicIntentState::initial).toList());
        long now = stage20.world().getAuthoritativeWorldTick();
        Stage21CGeneratedWorldRuntimePersistentState stage21C = new Stage21CGeneratedWorldRuntimePersistentState(
                Stage21CGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                Stage21CGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                stage21B,
                DiplomaticLifecycleState.empty(now),
                new Stage19ConflictRuntime(Stage19ConflictState.empty(now)).snapshot());
        Stage21DGeneratedWorldRuntimePersistentState stage21D = Stage21DGeneratedWorldRuntimePersistentState.compose(
                stage21C,
                commands);
        return Stage21IGeneratedWorldRuntimeMigration.migrate(stage21D).stage21HRuntime();
    }
}
