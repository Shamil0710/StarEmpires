package com.spacesim;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.CombatComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.PriceHistoryComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.components.ReputationComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.economy.Money;
import com.spacesim.model.Recipe;
import com.spacesim.model.ShipType;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoWorldFactoryTest {
    @Test
    void создаётСтабильныйНаборИзШестиСтанцийИСемиКораблей() {
        List<Entity> entities = DemoWorldFactory.createEntities();

        assertEquals(13, entities.size());
        Set<String> names = new HashSet<>();
        int stations = 0;
        int fleets = 0;
        for (Entity entity : entities) {
            IdentityComponent identity = entity.getComponent(IdentityComponent.class);
            assertNotNull(identity);
            assertTrue(names.add(identity.name));
            assertNotNull(entity.getComponent(TransformComponent.class));
            assertNotNull(entity.getComponent(InventoryComponent.class));
            if (identity.kind == IdentityComponent.Kind.STATION) {
                stations++;
                assertStationInfrastructure(entity);
            } else if (identity.kind == IdentityComponent.Kind.FLEET) {
                fleets++;
            }
        }
        assertEquals(6, stations);
        assertEquals(7, fleets);
    }

    @Test
    void станцииИмеютКонечныйAuthoritativeКапитал() {
        for (Entity entity : DemoWorldFactory.createEntities()) {
            IdentityComponent identity = entity.getComponent(IdentityComponent.class);
            if (identity.kind != IdentityComponent.Kind.STATION) {
                continue;
            }
            WalletComponent wallet = entity.getComponent(WalletComponent.class);
            assertNotNull(wallet, identity.name);
            assertEquals(Money.fromCredits(250_000d), wallet.getBalanceMilliCredits(), identity.name);
        }
    }

    @Test
    void производственнаяЦепочкаСодержитВсеЧетыреЗвенаИКонечныйСпрос() {
        List<Entity> entities = DemoWorldFactory.createEntities();
        Entity power = byName(entities, "Энергоузел Корона");
        Entity farm = byName(entities, "Агрокупол Аврора");
        Entity foundry = byName(entities, "Кузница Гелиос");
        Entity arsenal = byName(entities, "Арсенал Титан");
        Entity colony = byName(entities, "Колония Фронтир");
        Entity mine = byName(entities, "Шахтёрская база Ковчег");

        assertRecipe(power, "Генерация энергии", Constants.ITEM_ENERGY, 7);
        assertRecipe(farm, "Выращивание продовольствия", Constants.ITEM_FOOD, 6);
        assertRecipe(foundry, "Выплавка стали", Constants.ITEM_STEEL, 2);
        assertRecipe(arsenal, "Сборка вооружения", Constants.ITEM_WEAPONS, 1);

        assertTradable(mine, Constants.ITEM_ORE);
        assertTradable(power, Constants.ITEM_ENERGY);
        assertTradable(farm, Constants.ITEM_ENERGY);
        assertTradable(farm, Constants.ITEM_FOOD);
        assertTradable(foundry, Constants.ITEM_ORE);
        assertTradable(foundry, Constants.ITEM_ENERGY);
        assertTradable(foundry, Constants.ITEM_STEEL);
        assertTradable(arsenal, Constants.ITEM_ENERGY);
        assertTradable(arsenal, Constants.ITEM_STEEL);
        assertTradable(arsenal, Constants.ITEM_WEAPONS);

        MarketComponent colonyMarket = colony.getComponent(MarketComponent.class);
        assertTrue(colonyMarket.baseConsumption[Constants.ITEM_ENERGY] > 0f);
        assertTrue(colonyMarket.baseConsumption[Constants.ITEM_FOOD] > 0f);
        assertTrue(colonyMarket.baseConsumption[Constants.ITEM_STEEL] > 0f);
        assertTrue(colonyMarket.baseConsumption[Constants.ITEM_WEAPONS] > 0f);
    }

    @Test
    void пятьТорговыхКораблейИмеютСовместимуюСпециализациюИКошелёк() {
        List<Entity> entities = DemoWorldFactory.createEntities();
        int traders = 0;
        Set<Integer> specializedItems = new HashSet<>();

        for (Entity entity : entities) {
            TradeAIComponent ai = entity.getComponent(TradeAIComponent.class);
            if (ai == null) {
                continue;
            }
            traders++;
            ShipComponent ship = entity.getComponent(ShipComponent.class);
            InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
            WalletComponent wallet = entity.getComponent(WalletComponent.class);
            ReputationComponent reputation = entity.getComponent(ReputationComponent.class);

            assertNotNull(ship);
            assertNotNull(wallet);
            assertNotNull(reputation);
            assertTrue(ai.specializedItem >= 0 && ai.specializedItem < Constants.MAX_ITEMS);
            assertTrue(ship.canPurchaseItem(ai.specializedItem));
            assertTrue(specializedItems.add(ai.specializedItem));
            assertEquals(ai.cargoSpace, inventory.capacity);
            assertEquals(Money.fromCredits(12_000d), wallet.getBalanceMilliCredits());
            assertTrue(ai.movementSpeed > 0f);
            assertEquals(TradeAIComponent.State.IDLE, ai.state);
            assertEquals(0L, ai.expectedProfitMilliCredits);
        }

        assertEquals(5, traders);
        assertEquals(Constants.MAX_ITEMS, specializedItems.size());
    }

    @Test
    void добытчикИмеетОтдельныйКошелёкДомашнююБазуИНетTradeAI() {
        List<Entity> entities = DemoWorldFactory.createEntities();
        Entity miner = byName(entities, "Добытчик Старатель");
        Entity mine = byName(entities, "Шахтёрская база Ковчег");

        MiningComponent mining = miner.getComponent(MiningComponent.class);
        ShipComponent ship = miner.getComponent(ShipComponent.class);
        WalletComponent wallet = miner.getComponent(WalletComponent.class);

        assertNotNull(mining);
        assertSame(ShipType.MINING_SHIP, ship.type);
        assertSame(mine, mining.homeBase);
        assertEquals(Constants.ITEM_ORE, mining.resourceItem);
        assertEquals(2f, mining.extractionPerSecond, 0f);
        assertEquals(Money.fromCredits(1_000d), wallet.getBalanceMilliCredits());
        assertNull(miner.getComponent(TradeAIComponent.class));
    }

    @Test
    void боевойКорабльНеПолучаетКоммерческийКошелёкИлиТорговыйАвтомат() {
        Entity fighter = byName(DemoWorldFactory.createEntities(), "Фрегат Страж");

        assertSame(ShipType.COMBAT_SHIP, fighter.getComponent(ShipComponent.class).type);
        assertNotNull(fighter.getComponent(CombatComponent.class));
        assertNull(fighter.getComponent(WalletComponent.class));
        assertNull(fighter.getComponent(TradeAIComponent.class));
        assertNull(fighter.getComponent(MiningComponent.class));
        assertEquals(0, fighter.getComponent(InventoryComponent.class).capacity);
    }

    @Test
    void фабрикаКаждыйРазВозвращаетНезависимыйГрафСущностей() {
        List<Entity> first = DemoWorldFactory.createEntities();
        List<Entity> second = DemoWorldFactory.createEntities();

        assertFalse(first == second);
        for (int index = 0; index < first.size(); index++) {
            assertFalse(first.get(index) == second.get(index));
            assertFalse(first.get(index).getComponent(InventoryComponent.class)
                    == second.get(index).getComponent(InventoryComponent.class));
        }
    }

    private void assertStationInfrastructure(Entity station) {
        assertNotNull(station.getComponent(MarketComponent.class));
        assertNotNull(station.getComponent(PriceHistoryComponent.class));
        assertNotNull(station.getComponent(FactionComponent.class));
        assertNotNull(station.getComponent(WalletComponent.class));
        assertEquals(2_500, station.getComponent(InventoryComponent.class).capacity);
    }

    private void assertRecipe(Entity station, String name, int outputItem, int outputAmount) {
        ProductionComponent production = station.getComponent(ProductionComponent.class);
        assertNotNull(production);
        Recipe recipe = production.getActiveRecipe();
        assertNotNull(recipe);
        assertEquals(name, recipe.name);
        assertEquals(outputAmount, recipe.getOutputAmount(outputItem));
    }

    private void assertTradable(Entity station, int itemId) {
        MarketComponent market = station.getComponent(MarketComponent.class);
        assertTrue(market.isTradable(itemId));
        assertTrue(market.targetStock[itemId] > 0);
    }

    private Entity byName(List<Entity> entities, String name) {
        for (Entity entity : entities) {
            IdentityComponent identity = entity.getComponent(IdentityComponent.class);
            if (identity != null && name.equals(identity.name)) {
                return entity;
            }
        }
        throw new AssertionError("Не найдена сущность: " + name);
    }
}
