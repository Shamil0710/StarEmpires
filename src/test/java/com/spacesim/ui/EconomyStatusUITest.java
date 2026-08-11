package com.spacesim.ui;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.constants.Constants;
import com.spacesim.model.ShipType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyStatusUITest {
    @Test
    void считаетСовременныеКлассыИLegacyПеревозчикБезScene2d() {
        Entity station = new Entity().add(new MarketComponent());

        TradeAIComponent finishedGoodsAi = new TradeAIComponent();
        finishedGoodsAi.state = TradeAIComponent.State.TRAVEL_TO_BUY;
        Entity finishedGoodsCarrier = shipWithCargo(
                ShipType.FINISHED_GOODS_CARRIER, Constants.ITEM_FOOD, 4).add(finishedGoodsAi);
        Entity materialCarrier = shipWithCargo(
                ShipType.MATERIAL_CARRIER, Constants.ITEM_STEEL, 5);
        Entity tanker = shipWithCargo(
                ShipType.GAS_LIQUID_CARRIER, Constants.ITEM_ENERGY, 3);

        Entity miner = shipWithCargo(ShipType.MINING_SHIP, Constants.ITEM_ORE, 6);
        Entity combat = new Entity().add(new ShipComponent(ShipType.COMBAT_SHIP));

        TradeAIComponent legacyAi = new TradeAIComponent();
        Entity legacyCarrier = new Entity()
                .add(inventoryWith(Constants.ITEM_ENERGY, 2))
                .add(legacyAi);

        EconomyStatusUI.Summary summary = EconomyStatusUI.summarize(
                Arrays.asList(
                        station,
                        finishedGoodsCarrier,
                        materialCarrier,
                        tanker,
                        miner,
                        combat,
                        legacyCarrier,
                        null));

        assertEquals(1, summary.stationCount());
        assertEquals(6, summary.shipCount());
        assertEquals(1, summary.travellingCount());
        assertEquals(20, summary.cargoUnits());
        assertEquals(4, summary.carrierCount());
        assertEquals(1, summary.miningCount());
        assertEquals(1, summary.combatCount());

        String text = EconomyStatusUI.formatSummary(summary);
        assertTrue(text.contains("Станции: 1   Корабли: 6"));
        assertTrue(text.contains("В пути: 1   Груз: 20"));
        assertTrue(text.contains("Транспорт: 4   Добыча: 1   Боевые: 1"));
        assertTrue(text.contains("Форма и цвет — класс корабля"));
    }

    @Test
    void насыщаетСуммарныйГрузИНеДобавляетПовреждённыйОтрицательныйОстаток() {
        Entity fullCarrier = shipWithCargo(
                ShipType.MATERIAL_CARRIER, Constants.ITEM_ORE, Integer.MAX_VALUE);
        Entity secondCarrier = shipWithCargo(
                ShipType.GAS_LIQUID_CARRIER, Constants.ITEM_ENERGY, 10);
        Entity unconfiguredShip = new Entity().add(new ShipComponent());
        InventoryComponent invalidInventory = inventoryWith(Constants.ITEM_FOOD, -50);
        unconfiguredShip.add(invalidInventory);

        EconomyStatusUI.Summary summary = EconomyStatusUI.summarize(
                Arrays.asList(fullCarrier, secondCarrier, unconfiguredShip));

        assertEquals(3, summary.shipCount());
        assertEquals(2, summary.carrierCount());
        assertEquals(Integer.MAX_VALUE, summary.cargoUnits());
    }

    @Test
    void отклоняетОтсутствующуюМодельСводки() {
        assertThrows(NullPointerException.class, () -> EconomyStatusUI.summarize(null));
        assertThrows(NullPointerException.class, () -> EconomyStatusUI.formatSummary(null));
    }

    private static Entity shipWithCargo(ShipType type, int itemId, int amount) {
        return new Entity()
                .add(new ShipComponent(type))
                .add(inventoryWith(itemId, amount));
    }

    private static InventoryComponent inventoryWith(int itemId, int amount) {
        InventoryComponent inventory = new InventoryComponent();
        inventory.stock[itemId] = amount;
        return inventory;
    }
}
