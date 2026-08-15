package com.spacesim.persistence;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoWorldFactory;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.components.CombatComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.PriceHistoryComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.constants.Constants;
import com.spacesim.economy.Money;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityStateMapperTest {
    @Test
    void всеBootstrapКомпонентыДаютExactValueRoundTrip() {
        for (Entity entity : DemoWorldFactory.createEntities()) {
            EntityState captured = EntityStateMapper.capture(entity);
            Entity restored = EntityStateMapper.restore(captured);

            assertEquals(captured, EntityStateMapper.capture(restored));
            assertEquals(
                    entity.getComponent(EntityIdComponent.class).id,
                    restored.getComponent(EntityIdComponent.class).id);
            ArchetypeComponent originalArchetype = entity.getComponent(ArchetypeComponent.class);
            if (originalArchetype != null) {
                ArchetypeComponent restoredArchetype = restored.getComponent(ArchetypeComponent.class);
                assertNotNull(restoredArchetype);
                assertEquals(originalArchetype.contentId, restoredArchetype.contentId);
            }
        }
    }

    @Test
    void mapperСохраняетMutableИОпциональныеПоляВсехStatefulКомпонентов() {
        Entity entity = DemoWorldFactory.createEntities().get(6);
        TransformComponent transform = entity.getComponent(TransformComponent.class);
        transform.velocity.set(-12.5f, 4.25f);
        InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
        inventory.stock[Constants.ITEM_FOOD] = 7;
        MarketComponent market = new MarketComponent();
        market.configuredTargetStock[Constants.ITEM_FOOD] = 17;
        market.targetStock[Constants.ITEM_FOOD] = 33;
        market.baseConsumption[Constants.ITEM_FOOD] = 0.75f;
        market.sellPrices[Constants.ITEM_FOOD] = 12.5f;
        market.buyPrices[Constants.ITEM_FOOD] = 11.5f;
        market.consumptionRemainder[Constants.ITEM_FOOD] = 0.333d;
        market.tradableItems[Constants.ITEM_FOOD] = true;
        market.isDirty = false;
        entity.add(market);
        PriceHistoryComponent history = new PriceHistoryComponent();
        history.maxPoints = 17;
        history.history[Constants.ITEM_FOOD].add(9.5f);
        history.history[Constants.ITEM_FOOD].add(10.25f);
        entity.add(history);
        TradeAIComponent trade = entity.getComponent(TradeAIComponent.class);
        trade.state = TradeAIComponent.State.TRAVEL_TO_SELL;
        trade.buyStationId = new EntityId(1L);
        trade.sellStationId = new EntityId(2L);
        trade.targetStationId = new EntityId(2L);
        trade.targetAmount = 3;
        trade.expectedProfitMilliCredits = Money.fromCredits(15.25d);
        trade.routeSearchCooldown = 0.37f;

        EntityState captured = EntityStateMapper.capture(entity);
        EntityState.MarketState capturedMarket = captured.market();
        assertEquals(17, capturedMarket.configuredTargetStock().get(Constants.ITEM_FOOD));
        assertEquals(33, capturedMarket.targetStock().get(Constants.ITEM_FOOD));
        assertEquals(captured, EntityStateMapper.capture(EntityStateMapper.restore(captured)));
    }

    @Test
    void mapperСохраняетMiningCombatИАстероидныеПоля() {
        Entity entity = DemoWorldFactory.createEntities().get(11);
        MiningComponent mining = entity.getComponent(MiningComponent.class);
        mining.state = MiningComponent.State.MINING;
        mining.targetAsteroidId = new EntityId(99L);
        mining.extractionRemainder = 0.625d;
        mining.totalMined = 123L;
        mining.totalDelivered = 77L;

        EntityState minerState = EntityStateMapper.capture(entity);
        assertEquals(minerState, EntityStateMapper.capture(EntityStateMapper.restore(minerState)));

        Entity fighter = DemoWorldFactory.createEntities().get(12);
        CombatComponent combat = fighter.getComponent(CombatComponent.class);
        combat.hull = 17f;
        combat.shields = 3f;
        EntityState fighterState = EntityStateMapper.capture(fighter);
        assertEquals(fighterState, EntityStateMapper.capture(EntityStateMapper.restore(fighterState)));

        Entity asteroid = new Entity()
                .add(new EntityIdComponent(new EntityId(100L)))
                .add(new AsteroidComponent("X-1", Constants.ITEM_ORE, 80L));
        asteroid.getComponent(AsteroidComponent.class).remainingResource = 13L;
        EntityState asteroidState = EntityStateMapper.capture(asteroid);
        assertEquals(asteroidState, EntityStateMapper.capture(EntityStateMapper.restore(asteroidState)));
    }

    @Test
    void mapperОтклоняетEntityБезPersistentIdИПовреждённыеРазмеры() {
        assertThrows(IllegalArgumentException.class,
                () -> EntityStateMapper.capture(new Entity()));
        assertThrows(NullPointerException.class,
                () -> EntityStateMapper.capture(null));
        assertThrows(NullPointerException.class,
                () -> EntityStateMapper.restore(null));

        EntityState malformed = new EntityState(
                new EntityId(1L),
                null,
                null,
                new EntityState.InventoryState(10, List.of(1, 2)),
                null, null, null, null, null, null, null, null, null, null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> EntityStateMapper.restore(malformed));
    }
}
