package com.spacesim.model;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipTypeTest {
    @Test
    void каждыйПеревозчикПринимаетИПокупаетТолькоСвоюКатегорию() {
        assertCargoMatrix(
                ShipType.FINISHED_GOODS_CARRIER,
                EnumSet.of(ItemType.FOOD, ItemType.WEAPONS),
                true
        );
        assertCargoMatrix(
                ShipType.MATERIAL_CARRIER,
                EnumSet.of(ItemType.ORE, ItemType.STEEL),
                true
        );
        assertCargoMatrix(
                ShipType.GAS_LIQUID_CARRIER,
                EnumSet.of(ItemType.ENERGY),
                true
        );
    }

    @Test
    void добывающийКорабльХранитТолькоДобываемоеНоНичегоНеПокупает() {
        assertCargoMatrix(ShipType.MINING_SHIP, EnumSet.of(ItemType.ORE), false);
        assertTrue(ShipType.MINING_SHIP.isMining());
        assertFalse(ShipType.MINING_SHIP.isCarrier());
        assertFalse(ShipType.MINING_SHIP.isCombat());
    }

    @Test
    void боевойКорабльНеИмеетКоммерческогоГрузовогоНазначения() {
        assertCargoMatrix(ShipType.COMBAT_SHIP, EnumSet.noneOf(ItemType.class), false);
        assertTrue(ShipType.COMBAT_SHIP.isCombat());
        assertFalse(ShipType.COMBAT_SHIP.isCarrier());
        assertFalse(ShipType.COMBAT_SHIP.isMining());
    }

    @Test
    void ролиВзаимоисключающиеАИменаЗаполнены() {
        int carriers = 0;
        int miners = 0;
        int combatShips = 0;

        for (ShipType type : ShipType.values()) {
            assertFalse(type.getDisplayName().isBlank());
            assertFalse(type.canCarry(null));
            assertFalse(type.canPurchase(null));
            int roles = (type.isCarrier() ? 1 : 0)
                    + (type.isMining() ? 1 : 0)
                    + (type.isCombat() ? 1 : 0);
            assertEquals(1, roles, type.name());
            carriers += type.isCarrier() ? 1 : 0;
            miners += type.isMining() ? 1 : 0;
            combatShips += type.isCombat() ? 1 : 0;
        }

        assertEquals(3, carriers);
        assertEquals(1, miners);
        assertEquals(1, combatShips);
    }

    private static void assertCargoMatrix(
            ShipType shipType,
            EnumSet<ItemType> acceptedItems,
            boolean purchasesAcceptedItems) {
        for (ItemType item : ItemType.values()) {
            boolean accepted = acceptedItems.contains(item);
            assertEquals(accepted, shipType.canCarry(item), shipType + " / " + item);
            assertEquals(accepted && purchasesAcceptedItems,
                    shipType.canPurchase(item), shipType + " / " + item);
        }
    }
}
