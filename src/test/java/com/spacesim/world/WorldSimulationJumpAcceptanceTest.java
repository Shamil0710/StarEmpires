package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldSimulationJumpAcceptanceTest {
    private static final ContentCatalog CONTENT = ContentCatalogLoader.loadDefault();
    private static final StarSystemId ALPHA = new StarSystemId(1L);
    private static final StarSystemId BETA = new StarSystemId(2L);

    @TempDir
    Path temporaryDirectory;

    @Test
    void jumpSurvivesMidTransitSaveLoadAndLargeRenderFrame() throws IOException {
        WorldSimulation world = WorldSimulation.restore(initialWorld(), CONTENT, ALPHA, 10, 2);
        FleetPlacementState source = transferableFleet(world, ALPHA);
        SimulationSession origin = world.findSession(ALPHA).orElseThrow();
        Entity sourceEntity = origin.getEntityRegistry().require(source.localEntityId());
        InventoryComponent inventory = sourceEntity.getComponent(InventoryComponent.class);
        inventory.stock[0] += Math.min(5, inventory.getFreeCapacity());
        EntityState expectedPayload = FleetTransferStateMapper.sanitize(EntityStateMapper.capture(sourceEntity));
        EntityId oldLocalId = source.localEntityId();

        world.requestFleetJump(source.id(), BETA, 320f, -48f);
        world.advanceFrame(0.2f);

        FleetJumpState transitJump = world.findFleetJump(source.id()).orElseThrow();
        assertEquals(FleetJumpPhase.IN_TRANSIT, transitJump.phase());
        assertEquals(FleetLocationKind.IN_TRANSIT, world.findFleet(source.id()).orElseThrow().locationKind());
        assertFalse(origin.getEntityRegistry().contains(oldLocalId));

        Path save = temporaryDirectory.resolve("stage10b-mid-jump.starsave");
        WorldPersistence.save(save, world);
        WorldSimulation loaded = WorldPersistence.load(save, CONTENT);
        assertEquals(transitJump, loaded.findFleetJump(source.id()).orElseThrow());
        assertEquals(FleetLocationKind.IN_TRANSIT, loaded.findFleet(source.id()).orElseThrow().locationKind());

        loaded.advanceFrame(6.0f);

        assertTrue(loaded.findFleetJump(source.id()).isEmpty());
        FleetPlacementState arrived = loaded.findFleet(source.id()).orElseThrow();
        assertEquals(FleetLocationKind.IN_SYSTEM, arrived.locationKind());
        assertEquals(BETA, arrived.systemId());
        assertFalse(loaded.findSession(ALPHA).orElseThrow().getEntityRegistry().contains(oldLocalId));
        assertEquals(source.id(), loaded.findFleetByLocal(BETA, arrived.localEntityId()).orElseThrow());
        assertEquals(1L, loaded.getFleetPlacements().stream()
                .filter(placement -> placement.id().equals(source.id()))
                .count());

        Entity arrivedEntity = loaded.findSession(BETA).orElseThrow()
                .getEntityRegistry().require(arrived.localEntityId());
        EntityState actualPayload = FleetTransferStateMapper.sanitize(EntityStateMapper.capture(arrivedEntity));
        assertEquals(expectedPayload.inventory(), actualPayload.inventory());
        assertEquals(expectedPayload.wallet(), actualPayload.wallet());
        assertEquals(expectedPayload.faction(), actualPayload.faction());
        assertEquals(expectedPayload.ship(), actualPayload.ship());
    }

    @Test
    void switchingActiveSystemDoesNotResetTransitTimeline() {
        WorldSimulation world = WorldSimulation.restore(initialWorld(), CONTENT, ALPHA, 10, 2);
        FleetPlacementState source = transferableFleet(world, ALPHA);
        EntityId oldLocalId = source.localEntityId();

        world.requestFleetJump(source.id(), BETA, 75f, 12f);
        world.advanceFrame(0.2f);
        FleetJumpState beforeSwitch = world.findFleetJump(source.id()).orElseThrow();
        assertEquals(FleetJumpPhase.IN_TRANSIT, beforeSwitch.phase());

        world.activateSystem(BETA);

        assertEquals(BETA, world.getActiveSystemId());
        assertEquals(beforeSwitch, world.findFleetJump(source.id()).orElseThrow());
        assertEquals(FleetLocationKind.IN_TRANSIT, world.findFleet(source.id()).orElseThrow().locationKind());
        assertFalse(world.findSession(ALPHA).orElseThrow().getEntityRegistry().contains(oldLocalId));

        world.advanceFrame(6.0f);

        assertTrue(world.findFleetJump(source.id()).isEmpty());
        FleetPlacementState arrived = world.findFleet(source.id()).orElseThrow();
        assertEquals(BETA, arrived.systemId());
        assertEquals(FleetLocationKind.IN_SYSTEM, arrived.locationKind());
        assertEquals(source.id(), world.findFleetByLocal(BETA, arrived.localEntityId()).orElseThrow());
        assertTrue(world.findFleetByLocal(ALPHA, oldLocalId).isEmpty());
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
                            && inventory.getFreeCapacity() >= 5
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
                "Stage 10B Jump Acceptance Galaxy",
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
