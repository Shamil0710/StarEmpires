package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.FlightCommandComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.PlayableWorldStateCodec;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldSimulation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage15FleetOrdersAcceptanceTest {
    @Test
    void inactiveOwnedFleetMovesWithFiniteSharedInertiaAndOrderPersists() {
        Fixture fixture = fixture(15_101L);
        PlayerFleetOrderService orders = new PlayerFleetOrderService(fixture.runtime());
        FleetPlacementState delegated = fixture.delegated();
        Entity ship = entity(fixture.runtime().world(), delegated);
        TransformComponent transform = ship.getComponent(TransformComponent.class);
        float startX = transform.position.x;
        float startY = transform.position.y;

        assertTrue(orders.issue(PlayerFleetOrderState.move(
                delegated.id(), delegated.systemId(), startX + 120f, startY)));
        assertEquals(FleetOrderType.MOVE, orders.order(delegated.id()).orElseThrow().type());

        fixture.runtime().advanceFrame(0.1f);
        FlightCommandComponent command = ship.getComponent(FlightCommandComponent.class);
        assertNotNull(command);
        assertTrue(transform.velocity.x > 0f);
        assertTrue(transform.position.x > startX);
        assertEquals(startY, transform.position.y, 0.001f);
        assertTrue(transform.velocity.len() < command.speedCap,
                "first tick must accelerate toward the requested cap instead of assigning it instantly");

        PlayableWorldState decoded = PlayableWorldStateCodec.decode(
                PlayableWorldStateCodec.encode(fixture.runtime().snapshot()));
        assertEquals(PlayableWorldState.CURRENT_VERSION, decoded.schemaVersion());
        assertEquals(1, decoded.playerState().fleetOrders().size());
        assertEquals(delegated.id(), decoded.playerState().fleetOrders().get(0).fleetId());

        PlayerRuntime restored = PlayerRuntime.restore(
                decoded, fixture.content(), fixture.active().systemId());
        PlayerFleetOrderState restoredOrder = new PlayerFleetOrderService(restored)
                .order(delegated.id()).orElseThrow();
        assertEquals(FleetOrderType.MOVE, restoredOrder.type());
        assertEquals(startX + 120f, restoredOrder.targetX(), 0.001f);
    }

    @Test
    void inactiveOwnedFleetDefaultsToHoldAndDoesNotResumeLegacyAutonomy() {
        Fixture fixture = fixture(15_102L);
        Entity ship = entity(fixture.runtime().world(), fixture.delegated());
        TransformComponent transform = ship.getComponent(TransformComponent.class);
        transform.velocity.set(30f, 0f);
        float before = transform.velocity.len();

        fixture.runtime().advanceFrame(0.1f);
        float afterFirst = transform.velocity.len();
        assertTrue(afterFirst < before);
        assertTrue(afterFirst > 0f, "HOLD must brake physically rather than zero velocity instantly");

        for (int step = 0; step < 200 && transform.velocity.len2() > 0.01f; step++) {
            fixture.runtime().advanceFrame(0.1f);
        }
        assertTrue(transform.velocity.len() < 0.25f);
        assertNotNull(ship.getComponent(FlightCommandComponent.class));
    }

    @Test
    void interSystemMoveUsesExistingJumpFsmAndKeepsSameFleetId() {
        Fixture fixture = fixture(15_103L);
        PlayerFleetOrderService orders = new PlayerFleetOrderService(fixture.runtime());
        FleetId delegatedId = fixture.delegated().id();
        assertTrue(orders.issue(PlayerFleetOrderState.move(
                delegatedId, DemoGalaxyFactory.INNER_SYSTEM_ID, 40f, 25f)));

        boolean observedTransit = false;
        for (int step = 0; step < 1200; step++) {
            fixture.runtime().advanceFrame(0.1f);
            if (fixture.runtime().world().findFleetJump(delegatedId).isPresent()) {
                observedTransit = true;
            }
            FleetPlacementState placement = fixture.runtime().world().findFleet(delegatedId).orElseThrow();
            if (placement.locationKind() == FleetLocationKind.IN_SYSTEM
                    && DemoGalaxyFactory.INNER_SYSTEM_ID.equals(placement.systemId())
                    && fixture.runtime().world().findFleetJump(delegatedId).isEmpty()) {
                break;
            }
        }

        FleetPlacementState arrived = fixture.runtime().world().findFleet(delegatedId).orElseThrow();
        assertTrue(observedTransit);
        assertEquals(delegatedId, arrived.id());
        assertEquals(FleetLocationKind.IN_SYSTEM, arrived.locationKind());
        assertEquals(DemoGalaxyFactory.INNER_SYSTEM_ID, arrived.systemId());
        assertTrue(fixture.runtime().player().ownedFleetIds().contains(delegatedId));
        assertEquals(FleetOrderType.MOVE, orders.order(delegatedId).orElseThrow().type());
    }

    @Test
    void allStage15OrderShapesAreDurableAndCanonicalByFleetId() {
        Fixture fixture = fixture(15_104L);
        PlayerState source = fixture.runtime().player();
        FleetId fleet = fixture.delegated().id();
        FleetId active = fixture.active().id();
        StarSystemId anchor = DemoGalaxyFactory.ACTIVE_SYSTEM_ID;
        StarSystemId inner = DemoGalaxyFactory.INNER_SYSTEM_ID;

        List<PlayerFleetOrderState> shapes = List.of(
                PlayerFleetOrderState.hold(fleet),
                PlayerFleetOrderState.move(fleet, anchor, 10f, 20f),
                PlayerFleetOrderState.escort(fleet, active),
                PlayerFleetOrderState.follow(fleet, active),
                PlayerFleetOrderState.patrol(fleet, List.of(anchor, inner)));
        for (PlayerFleetOrderState shape : shapes) {
            PlayerState state = new PlayerState(
                    source.walletMilliCredits(),
                    source.factionContentId(),
                    source.reputations(),
                    source.ownedFleetIds(),
                    source.activeFleetId(),
                    source.discoveredSystemIds(),
                    source.discoveredObjects(),
                    source.homeSystemId(),
                    source.dockedAt(),
                    List.of(shape));
            PlayableWorldState decoded = PlayableWorldStateCodec.decode(PlayableWorldStateCodec.encode(
                    new PlayableWorldState(PlayableWorldState.CURRENT_VERSION,
                            fixture.runtime().world().snapshot(), state)));
            assertEquals(shape, decoded.playerState().fleetOrders().get(0));
        }
    }

    private static Fixture fixture(long seed) {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(seed);
        List<FleetPlacementState> local = controllableFleets(world);
        if (local.size() < 2) {
            throw new AssertionError("Demo active system needs at least two physical fleets");
        }
        FleetPlacementState active = local.get(0);
        FleetPlacementState delegated = local.get(1);
        PlayerState player = new PlayerState(
                5_000_000L,
                null,
                List.of(),
                List.of(active.id(), delegated.id()),
                active.id(),
                List.of(
                        DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                        DemoGalaxyFactory.INNER_SYSTEM_ID,
                        DemoGalaxyFactory.FRONTIER_SYSTEM_ID),
                List.of(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        return new Fixture(content, active, delegated, PlayerRuntime.create(world, content, player));
    }

    private static List<FleetPlacementState> controllableFleets(WorldSimulation world) {
        SimulationSession session = world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow();
        List<FleetPlacementState> result = new ArrayList<>();
        for (FleetPlacementState placement : world.getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !DemoGalaxyFactory.ACTIVE_SYSTEM_ID.equals(placement.systemId())) {
                continue;
            }
            Entity entity = session.getEntityRegistry().find(placement.localEntityId());
            if (entity != null
                    && entity.getComponent(TransformComponent.class) != null
                    && entity.getComponent(ShipComponent.class) != null) {
                result.add(placement);
            }
        }
        result.sort((left, right) -> left.id().compareTo(right.id()));
        return result;
    }

    private static Entity entity(WorldSimulation world, FleetPlacementState placement) {
        SimulationSession session = world.findSession(placement.systemId()).orElseThrow();
        Entity entity = session.getEntityRegistry().find(placement.localEntityId());
        assertNotNull(entity);
        return entity;
    }

    private record Fixture(
            ContentCatalog content,
            FleetPlacementState active,
            FleetPlacementState delegated,
            PlayerRuntime runtime) {
    }
}
