package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityState;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FleetForceRegistryTest {

    @Test
    void liveProjectionMatchesPersistentProjectionWithoutChangingWorld() {
        var world = DemoGalaxyFactory.create(0x21D5EEDL);
        var before = world.snapshot();
        var evaluator = new FleetReadinessEvaluator(ShipEngineeringCatalogLoader.loadDefault());
        var observed = Map.of(before.fleets().get(0).id(), new FleetOperationalAvailability(1, 2500));
        assertEquals(FleetForceRegistry.reconstruct(before, evaluator, observed).entries(),
                FleetForceRegistry.reconstruct(world, evaluator, observed).entries());
        assertEquals(before, world.snapshot());
        var fleet = before.fleets().get(0);
        assertEquals(List.of(FleetForceRegistry.reconstruct(before, evaluator, observed).find(fleet.id()).orElseThrow()),
                FleetForceRegistry.reconstructSelected(world, evaluator, observed, List.of(fleet.id())).entries());
        assertThrows(IllegalStateException.class, () -> FleetForceRegistry.reconstructSelected(world, evaluator,
                observed, List.of(new FleetId(Long.MAX_VALUE))));
        var destination = world.getTopology().neighbors(fleet.systemId()).get(0);
        world.requestFleetJump(fleet.id(), destination, 0f, 0f);
        world.advanceFrame(1f);
        var after = world.snapshot();
        assertEquals(FleetForceRegistry.reconstruct(after, evaluator, observed).entries(),
                FleetForceRegistry.reconstruct(world, evaluator, observed).entries());
        assertEquals(after, world.snapshot());
    }

    @Test
    void reconstructionUsesExactlyTheOrdinaryWorldFleetIdentitiesAndPlacements() {
        WorldState world = DemoGalaxyFactory.create(0x21D5EEDL).snapshot();
        FleetReadinessEvaluator evaluator = new FleetReadinessEvaluator(
                ShipEngineeringCatalogLoader.loadDefault());

        FleetForceRegistry registry = FleetForceRegistry.reconstruct(world, evaluator, Map.of());

        assertEquals(world.fleets().size(), registry.entries().size());
        assertEquals(
                new HashSet<>(world.fleets().stream().map(FleetPlacementState::id).toList()),
                new HashSet<>(registry.entries().stream().map(FleetForceRegistry.Entry::fleetId).toList()));
        for (FleetPlacementState placement : world.fleets()) {
            FleetForceRegistry.Entry entry = registry.find(placement.id()).orElseThrow();
            assertEquals(placement.locationKind(), entry.locationKind());
            assertEquals(placement.systemId(), entry.systemId());
            assertEquals(0, entry.readiness().crewBps(),
                    "missing external crew observation must fail closed rather than invent availability");
            assertEquals(0, entry.readiness().supplyAccessBps(),
                    "missing service-access observation must fail closed");
        }
    }

    @Test
    void duplicateReadModelIdentityIsRejectedInsteadOfCreatingAParallelFleet() {
        FleetId fleetId = new FleetId(1L);
        FleetForceRegistry.Entry first = new FleetForceRegistry.Entry(
                fleetId, 1, FleetLocationKind.IN_SYSTEM, new StarSystemId(1L), null, null,
                dummyEntity(1L), FleetReadinessState.unavailable());
        FleetForceRegistry.Entry duplicate = new FleetForceRegistry.Entry(
                fleetId, 2, FleetLocationKind.IN_SYSTEM, new StarSystemId(2L), null, null,
                dummyEntity(2L), FleetReadinessState.unavailable());

        assertThrows(IllegalArgumentException.class,
                () -> new FleetForceRegistry(List.of(first, duplicate)));
    }

    @Test
    void nullLookupIsEmptyAndFactionProjectionPreservesCanonicalFleetOrder() {
        FleetForceRegistry registry = new FleetForceRegistry(List.of(
                entry(3L, 7), entry(1L, 7), entry(2L, 8)));

        assertTrue(registry.find(null).isEmpty());
        assertEquals(List.of(new FleetId(3L), new FleetId(1L)),
                registry.ownedBy(7).stream().map(FleetForceRegistry.Entry::fleetId).toList());
    }

    private static FleetForceRegistry.Entry entry(long id, int factionId) {
        return new FleetForceRegistry.Entry(
                new FleetId(id), factionId, FleetLocationKind.IN_SYSTEM, new StarSystemId(1L), null, null,
                dummyEntity(id), FleetReadinessState.unavailable());
    }

    private static EntityState dummyEntity(long id) {
        return new EntityState(
                new EntityId(id),
                new EntityState.IdentityState("Fleet " + id, "FLEET"),
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
