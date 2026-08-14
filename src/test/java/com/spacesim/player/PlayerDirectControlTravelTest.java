package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.PlayableWorldStateCodec;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.WorldSimulation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDirectControlTravelTest {
    @Test
    void inputMovesOnlyOnFixedTickAndHonorsPause() {
        Fixture fixture = fixture(12_101L);
        PlayerShipView before = fixture.runtime.activeShipView().orElseThrow();

        assertTrue(fixture.runtime.setMovementIntent(1f, 0f));
        PlayerShipView beforeTick = fixture.runtime.activeShipView().orElseThrow();
        assertEquals(before.x(), beforeTick.x(), 0.0001f);

        fixture.runtime.setPaused(true);
        fixture.runtime.advanceFrame(0.5f);
        assertEquals(before.x(), fixture.runtime.activeShipView().orElseThrow().x(), 0.0001f);

        fixture.runtime.setPaused(false);
        fixture.runtime.setTimeScale(1d);
        fixture.runtime.advanceFrame(0.1f);
        PlayerShipView after = fixture.runtime.activeShipView().orElseThrow();
        assertTrue(after.x() > before.x());
        assertEquals(before.y(), after.y(), 0.0001f);
    }

    @Test
    void dockingRequiresRangePersistsAndLocksMovement() {
        Fixture fixture = fixture(12_102L);
        SimulationSession session = fixture.runtime.world()
                .findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow();
        Entity ship = session.getEntityRegistry().find(fixture.placement.localEntityId());
        Entity station = firstMarket(session);
        TransformComponent shipTransform = ship.getComponent(TransformComponent.class);
        TransformComponent stationTransform = station.getComponent(TransformComponent.class);
        shipTransform.position.set(stationTransform.position);
        long stationId = station.getComponent(com.spacesim.components.EntityIdComponent.class).id.value();

        assertTrue(fixture.runtime.dockAt(new com.spacesim.persistence.EntityId(stationId)));
        assertTrue(fixture.runtime.player().docked());
        float x = shipTransform.position.x;
        assertFalse(fixture.runtime.setMovementIntent(1f, 0f));
        fixture.runtime.advanceFrame(0.5f);
        assertEquals(x, shipTransform.position.x, 0.0001f);

        PlayableWorldState decoded = PlayableWorldStateCodec.decode(
                PlayableWorldStateCodec.encode(fixture.runtime.snapshot()));
        assertNotNull(decoded.playerState().dockedAt());
        PlayerRuntime restored = PlayerRuntime.restore(
                decoded,
                fixture.content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        assertTrue(restored.player().docked());

        assertTrue(fixture.runtime.undock());
        assertFalse(fixture.runtime.player().docked());
    }

    @Test
    void activeFleetUsesStage10JumpAndWorldFollowsDestination() {
        Fixture fixture = fixture(12_103L);

        assertTrue(fixture.runtime.requestJump(DemoGalaxyFactory.INNER_SYSTEM_ID));
        for (int step = 0; step < 500
                && (fixture.runtime.world().findFleetJump(fixture.placement.id()).isPresent()
                || fixture.runtime.world().findFleet(fixture.placement.id()).orElseThrow().locationKind()
                != FleetLocationKind.IN_SYSTEM
                || !DemoGalaxyFactory.INNER_SYSTEM_ID.equals(
                fixture.runtime.world().findFleet(fixture.placement.id()).orElseThrow().systemId())); step++) {
            fixture.runtime.advanceFrame(0.1f);
        }

        FleetPlacementState arrived = fixture.runtime.world().findFleet(fixture.placement.id()).orElseThrow();
        assertEquals(FleetLocationKind.IN_SYSTEM, arrived.locationKind());
        assertEquals(DemoGalaxyFactory.INNER_SYSTEM_ID, arrived.systemId());
        assertEquals(DemoGalaxyFactory.INNER_SYSTEM_ID, fixture.runtime.world().getActiveSystemId());
        assertTrue(fixture.runtime.player().discoveredSystemIds().contains(DemoGalaxyFactory.INNER_SYSTEM_ID));
        assertEquals(fixture.placement.id(), fixture.runtime.player().activeFleetId());
        assertEquals(DemoGalaxyFactory.INNER_SYSTEM_ID,
                fixture.runtime.activeShipView().orElseThrow().systemId());
    }

    private static Fixture fixture(long seed) {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(seed);
        FleetPlacementState placement = controllableFleetInActiveSystem(world);
        PlayerState player = new PlayerState(
                1_000_000L,
                null,
                List.of(),
                List.of(placement.id()),
                placement.id(),
                List.of(DemoGalaxyFactory.ACTIVE_SYSTEM_ID),
                List.of(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        return new Fixture(content, placement, PlayerRuntime.create(world, content, player));
    }

    private static FleetPlacementState controllableFleetInActiveSystem(WorldSimulation world) {
        SimulationSession session = world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow();
        for (FleetPlacementState placement : world.getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !DemoGalaxyFactory.ACTIVE_SYSTEM_ID.equals(placement.systemId())) {
                continue;
            }
            Entity entity = session.getEntityRegistry().find(placement.localEntityId());
            if (entity != null
                    && (entity.getComponent(TradeAIComponent.class) != null
                    || entity.getComponent(MiningComponent.class) != null)
                    && entity.getComponent(TransformComponent.class) != null) {
                return placement;
            }
        }
        throw new AssertionError("Demo active system has no controllable fleet");
    }

    private static Entity firstMarket(SimulationSession session) {
        for (Entity entity : session.getEngine().getEntities()) {
            if (entity.getComponent(MarketComponent.class) != null
                    && entity.getComponent(TransformComponent.class) != null
                    && entity.getComponent(com.spacesim.components.EntityIdComponent.class) != null) {
                return entity;
            }
        }
        throw new AssertionError("Demo session has no market station");
    }

    private record Fixture(
            ContentCatalog content,
            FleetPlacementState placement,
            PlayerRuntime runtime) {
    }
}
