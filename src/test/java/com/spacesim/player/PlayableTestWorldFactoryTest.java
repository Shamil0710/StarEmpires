package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.persistence.PlayableWorldStateCodec;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayableTestWorldFactoryTest {
    @Test
    void curatedWorldStartsNearSourceWithRealProfitableShortageRoute() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(PlayableTestWorldFactory.DEFAULT_TEST_SEED);
        PlayerRuntime runtime = scenario.runtime();
        PlayableTestWorldFactory.Route route = scenario.route();
        ContentCatalog.ItemDefinition item = scenario.content().findItem(route.itemContentId());
        PlayerShipView ship = runtime.activeShipView().orElseThrow();

        assertEquals(route.sourceSystem(), ship.systemId());
        assertEquals(1, runtime.player().ownedFleetIds().size());
        assertEquals(runtime.player().ownedFleetIds().get(0), runtime.player().activeFleetId());
        assertFalse(runtime.player().docked());

        SimulationSession sourceSession = runtime.world().findSession(route.sourceSystem()).orElseThrow();
        SimulationSession destinationSession = runtime.world().findSession(route.destinationSystem()).orElseThrow();
        Entity source = marketByName(sourceSession, route.sourceStationName());
        Entity destination = marketByName(destinationSession, route.destinationStationName());
        TransformComponent sourceTransform = source.getComponent(TransformComponent.class);
        float dx = sourceTransform.position.x - ship.x();
        float dy = sourceTransform.position.y - ship.y();
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        int itemId = item.runtimeId();
        assertTrue(distance > 10f && distance < 100f);
        assertTrue(source.getComponent(InventoryComponent.class).stock[itemId]
                >= PlayableTestWorldFactory.RECOMMENDED_TEST_UNITS);
        assertEquals(1, destination.getComponent(InventoryComponent.class).stock[itemId]);
        assertTrue(source.getComponent(MarketComponent.class).sellPrices[itemId] > 0f);
        assertTrue(destination.getComponent(MarketComponent.class).buyPrices[itemId]
                > source.getComponent(MarketComponent.class).sellPrices[itemId]);
        assertTrue(route.destinationBuyPriceCredits() > route.sourceSellPriceCredits());
    }

    @Test
    void curatedPlayerEnvelopeRoundTripsBeforeManualTesting() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(12_400L);
        PlayableWorldState encoded = PlayableWorldStateCodec.decode(
                PlayableWorldStateCodec.encode(scenario.runtime().snapshot()));
        PlayerRuntime restored = PlayerRuntime.restore(encoded, scenario.content(), scenario.route().sourceSystem());

        assertEquals(scenario.runtime().player().walletMilliCredits(), restored.player().walletMilliCredits());
        assertEquals(scenario.runtime().player().activeFleetId(), restored.player().activeFleetId());
        assertEquals(scenario.route().sourceSystem(), restored.activeShipView().orElseThrow().systemId());
    }

    private static Entity marketByName(SimulationSession session, String name) {
        for (Entity entity : session.getEngine().getEntities()) {
            IdentityComponent identity = entity.getComponent(IdentityComponent.class);
            if (identity != null
                    && identity.kind == IdentityComponent.Kind.STATION
                    && name.equals(identity.name)
                    && entity.getComponent(MarketComponent.class) != null) {
                return entity;
            }
        }
        throw new AssertionError("Market not found: " + name);
    }
}
