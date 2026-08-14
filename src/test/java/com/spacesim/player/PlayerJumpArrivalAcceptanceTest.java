package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.ui.WorldMapLayout;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetJumpPhase;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.LocalSystemCoordinates;
import com.spacesim.world.WorldSimulation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerJumpArrivalAcceptanceTest {
    @Test
    void jStyleJumpDetachesSameFleetThenMaterializesAtScreenCenterInDestination() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(15_160L);
        FleetPlacementState start = controllableFleet(world);
        FleetId fleetId = start.id();
        PlayerRuntime runtime = PlayerRuntime.create(world, content, new PlayerState(
                1_000_000L,
                null,
                List.of(),
                List.of(fleetId),
                fleetId,
                List.of(DemoGalaxyFactory.ACTIVE_SYSTEM_ID),
                List.of(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID));

        assertTrue(runtime.requestJump(DemoGalaxyFactory.INNER_SYSTEM_ID));
        assertEquals(FleetJumpPhase.MOVING_TO_JUMP,
                world.findFleetJump(fleetId).orElseThrow().phase());
        assertEquals(LocalSystemCoordinates.ARRIVAL_X,
                world.findFleetJump(fleetId).orElseThrow().arrivalX(), 0f);
        assertEquals(LocalSystemCoordinates.ARRIVAL_Y,
                world.findFleetJump(fleetId).orElseThrow().arrivalY(), 0f);

        boolean observedDetachedTransit = false;
        for (int step = 0; step < 500 && world.findFleetJump(fleetId).isPresent(); step++) {
            runtime.advanceFrame(0.1f);
            FleetPlacementState placement = world.findFleet(fleetId).orElseThrow();
            if (placement.locationKind() == FleetLocationKind.IN_TRANSIT) {
                observedDetachedTransit = true;
            }
        }

        assertTrue(observedDetachedTransit, "Jump must contain a real detached IN_TRANSIT phase");
        FleetPlacementState arrived = world.findFleet(fleetId).orElseThrow();
        assertEquals(fleetId, arrived.id());
        assertEquals(FleetLocationKind.IN_SYSTEM, arrived.locationKind());
        assertEquals(DemoGalaxyFactory.INNER_SYSTEM_ID, arrived.systemId());
        assertEquals(DemoGalaxyFactory.INNER_SYSTEM_ID, world.getActiveSystemId());

        PlayerShipView ship = runtime.activeShipView().orElseThrow();
        assertEquals(LocalSystemCoordinates.ARRIVAL_X, ship.x(), 0.0001f);
        assertEquals(LocalSystemCoordinates.ARRIVAL_Y, ship.y(), 0.0001f);
        assertEquals(0f, ship.velocityX(), 0.0001f);
        assertEquals(0f, ship.velocityY(), 0.0001f);

        WorldMapLayout camera = new WorldMapLayout(
                0f, 0f, 1_000f, 700f, 0f, ship.x(), ship.y(), 2f);
        Vector2 screen = new Vector2();
        assertTrue(camera.worldToScreen(ship.x(), ship.y(), screen));
        assertEquals(camera.getMapX() + camera.getMapWidth() / 2f, screen.x, 0.001f);
        assertEquals(camera.getMapY() + camera.getMapHeight() / 2f, screen.y, 0.001f);
    }

    private static FleetPlacementState controllableFleet(WorldSimulation world) {
        SimulationSession session = world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow();
        for (FleetPlacementState placement : world.getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !DemoGalaxyFactory.ACTIVE_SYSTEM_ID.equals(placement.systemId())) {
                continue;
            }
            Entity entity = session.getEntityRegistry().find(placement.localEntityId());
            if (entity != null
                    && entity.getComponent(TransformComponent.class) != null
                    && (entity.getComponent(TradeAIComponent.class) != null
                    || entity.getComponent(MiningComponent.class) != null)) {
                return placement;
            }
        }
        throw new AssertionError("Demo active system has no controllable fleet");
    }
}
