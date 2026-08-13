package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityState;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.persistence.FleetTransferStateMapper;
import com.spacesim.persistence.WorldPersistence;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldSimulationFleetIntegrationTest {
    private static final ContentCatalog CONTENT = ContentCatalogLoader.loadDefault();
    private static final StarSystemId ALPHA = new StarSystemId(1L);
    private static final StarSystemId BETA = new StarSystemId(2L);

    @TempDir
    Path temporaryDirectory;

    @Test
    void worldPersistenceKeepsSameFleetIdentityAcrossMidTransitSaveLoad() throws IOException {
        WorldSimulation world = WorldSimulation.restore(initialWorld(), CONTENT, ALPHA, 10, 2);
        FleetPlacementState source = transferableFleet(world, ALPHA);
        SimulationSession origin = world.findSession(ALPHA).orElseThrow();
        Entity sourceEntity = origin.getEntityRegistry().require(source.localEntityId());
        InventoryComponent inventory = sourceEntity.getComponent(InventoryComponent.class);
        int addedCargo = Math.min(7, inventory.getFreeCapacity());
        assertTrue(addedCargo > 0);
        inventory.stock[0] += addedCargo;

        EntityState expectedPayload = FleetTransferStateMapper.sanitize(EntityStateMapper.capture(sourceEntity));
        assertNotNull(expectedPayload.wallet());
        assertTrue(expectedPayload.wallet().balanceMilliCredits() > 0L);
        assertNotNull(expectedPayload.inventory());
        assertTrue(expectedPayload.inventory().stock().stream().mapToInt(Integer::intValue).sum() > 0);

        int originLedgerSize = origin.getLedger().size();
        int destinationLedgerSize = world.findSession(BETA).orElseThrow().getLedger().size();
        EntityId oldLocalId = source.localEntityId();

        FleetPlacementState transit = world.beginFleetTransfer(source.id(), BETA);
        assertEquals(FleetLocationKind.IN_TRANSIT, transit.locationKind());
        assertFalse(origin.getEntityRegistry().contains(oldLocalId));
        assertEquals(expectedPayload, transit.transitState().entityState());
        assertEquals(originLedgerSize, origin.getLedger().size());

        WorldState midTransit = world.snapshot();
        assertEquals(FleetLocationKind.IN_TRANSIT, world.findFleet(source.id()).orElseThrow().locationKind());
        assertEquals(1L, world.getFleetPlacements().stream()
                .filter(placement -> placement.id().equals(source.id()))
                .count());

        Path save = temporaryDirectory.resolve("mid-transit.starsave");
        WorldPersistence.save(save, world);
        WorldSimulation loaded = WorldPersistence.load(save, CONTENT);

        assertEquals(midTransit, loaded.snapshot());
        assertEquals(FleetLocationKind.IN_TRANSIT, loaded.findFleet(source.id()).orElseThrow().locationKind());
        assertFalse(loaded.findSession(ALPHA).orElseThrow().getEntityRegistry().contains(oldLocalId));

        SimulationSession destination = loaded.findSession(BETA).orElseThrow();
        long expectedDestinationId = destination.getNextEntityIdValue();
        FleetPlacementState arrived = loaded.completeFleetTransfer(source.id(), 410.5f, -32.75f);

        assertEquals(source.id(), arrived.id());
        assertEquals(FleetLocationKind.IN_SYSTEM, arrived.locationKind());
        assertEquals(BETA, arrived.systemId());
        assertEquals(expectedDestinationId, arrived.localEntityId().value());
        assertEquals(source.id(), loaded.findFleetByLocal(BETA, arrived.localEntityId()).orElseThrow());
        assertTrue(loaded.findFleetByLocal(ALPHA, oldLocalId).isEmpty());
        assertEquals(1L, loaded.getFleetPlacements().stream()
                .filter(placement -> placement.id().equals(source.id()))
                .count());

        Entity arrivedEntity = destination.getEntityRegistry().require(arrived.localEntityId());
        EntityState actualPayload = FleetTransferStateMapper.sanitize(EntityStateMapper.capture(arrivedEntity));
        assertEquals(expectedPayload.inventory(), actualPayload.inventory());
        assertEquals(expectedPayload.wallet(), actualPayload.wallet());
        assertEquals(expectedPayload.faction(), actualPayload.faction());
        assertEquals(expectedPayload.ship(), actualPayload.ship());
        assertEquals(expectedPayload.combat(), actualPayload.combat());
        assertEquals(originLedgerSize, loaded.findSession(ALPHA).orElseThrow().getLedger().size());
        assertEquals(destinationLedgerSize, destination.getLedger().size());

        Path arrivedSave = temporaryDirectory.resolve("arrived.starsave");
        WorldPersistence.save(arrivedSave, loaded);
        WorldSimulation reloaded = WorldPersistence.load(arrivedSave, CONTENT);
        assertEquals(loaded.snapshot(), reloaded.snapshot());
        assertEquals(source.id(), reloaded.findFleetByLocal(BETA, arrived.localEntityId()).orElseThrow());
    }

    @Test
    void lifecycleAndDestructionKeepFleetWorldIndexConsistent() {
        WorldSimulation world = WorldSimulation.restore(initialWorld(), CONTENT, ALPHA, 10, 2);

        Entity emptyFleet = new Entity().add(new IdentityComponent(
                "Lifecycle fleet", IdentityComponent.Kind.FLEET));
        EntityId createdId = world.createEntity(ALPHA, emptyFleet);
        FleetId createdFleetId = world.findFleetByLocal(ALPHA, createdId).orElseThrow();
        assertEquals(createdFleetId, world.findFleet(createdFleetId).orElseThrow().id());
        world.snapshot();

        assertTrue(world.removeEntity(ALPHA, createdId));
        assertTrue(world.findFleet(createdFleetId).isEmpty());
        assertTrue(world.findFleetByLocal(ALPHA, createdId).isEmpty());
        world.snapshot();

        FleetPlacementState destroyed = transferableFleet(world, ALPHA);
        world.destroyEntity(ALPHA, destroyed.localEntityId(), DestructionPolicy.destroyAll());

        assertTrue(world.findFleet(destroyed.id()).isEmpty());
        assertTrue(world.findFleetByLocal(ALPHA, destroyed.localEntityId()).isEmpty());
        assertFalse(world.findSession(ALPHA).orElseThrow()
                .getEntityRegistry().contains(destroyed.localEntityId()));
        world.snapshot();
    }

    private static FleetPlacementState transferableFleet(WorldSimulation world, StarSystemId systemId) {
        SimulationSession session = world.findSession(systemId).orElseThrow();
        return world.getFleetPlacements().stream()
                .filter(placement -> placement.locationKind() == FleetLocationKind.IN_SYSTEM)
                .filter(placement -> systemId.equals(placement.systemId()))
                .filter(placement -> {
                    Entity entity = session.getEntityRegistry().find(placement.localEntityId());
                    if (entity == null) {
                        return false;
                    }
                    InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
                    WalletComponent wallet = entity.getComponent(WalletComponent.class);
                    return inventory != null
                            && inventory.getFreeCapacity() > 0
                            && wallet != null
                            && wallet.getBalanceMilliCredits() > 0L;
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("Demo world must contain a transferable fleet"));
    }

    private static WorldState initialWorld() {
        StarSystemNode alpha = new StarSystemNode(ALPHA, "Alpha", 0d, 0d);
        StarSystemNode beta = new StarSystemNode(BETA, "Beta", 100d, 0d);
        GalaxyTopology topology = new GalaxyTopology(
                new GalaxyId(1L),
                "World Fleet Test Galaxy",
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
}
