package com.spacesim.components;

import com.spacesim.constants.Constants;
import com.spacesim.model.ItemType;
import com.spacesim.model.ShipType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipRoleComponentsTest {
    @Test
    void корабельныйКомпонентБезопасноПроверяетЧисловыеИдентификаторы() {
        ShipComponent unconfigured = new ShipComponent();
        assertFalse(unconfigured.canCarryItem(Constants.ITEM_ORE));
        assertFalse(unconfigured.canPurchaseItem(Constants.ITEM_ORE));

        ShipComponent materialCarrier = new ShipComponent(ShipType.MATERIAL_CARRIER);
        assertTrue(materialCarrier.canCarryItem(Constants.ITEM_ORE));
        assertTrue(materialCarrier.canPurchaseItem(Constants.ITEM_STEEL));
        assertFalse(materialCarrier.canCarryItem(Constants.ITEM_FOOD));
        assertFalse(materialCarrier.canPurchaseItem(Constants.ITEM_WEAPONS));
        assertFalse(materialCarrier.canCarryItem(-1));
        assertFalse(materialCarrier.canPurchaseItem(Constants.MAX_ITEMS));

        materialCarrier.type = ShipType.MINING_SHIP;
        assertTrue(materialCarrier.canCarryItem(Constants.ITEM_ORE));
        assertFalse(materialCarrier.canPurchaseItem(Constants.ITEM_ORE));
        assertThrows(NullPointerException.class, () -> new ShipComponent(null));
    }

    @Test
    void добывающийКомпонентИмеетКорректныеЗначенияПоУмолчанию() {
        MiningComponent mining = new MiningComponent();

        assertEquals(Constants.ITEM_ORE, mining.resourceItem);
        assertTrue(ItemType.fromId(mining.resourceItem).isMineable());
        assertEquals(0.5f, mining.extractionPerSecond, 0f);
        assertEquals(0d, mining.extractionRemainder, 0d);
        assertEquals(0L, mining.totalMined);
        assertEquals(0L, mining.totalDelivered);
        assertEquals(85f, mining.movementSpeed, 0f);
        assertEquals(14f, mining.extractionRange, 0f);
        assertEquals(10f, mining.dockingRange, 0f);
        assertEquals(MiningComponent.State.SEARCHING, mining.state);
        assertTrue(mining.active);
    }

    @Test
    void добывающийКомпонентПроверяетРесурсИПроизводительность() {
        MiningComponent mining = new MiningComponent(Constants.ITEM_ORE, 2.5f);
        assertEquals(Constants.ITEM_ORE, mining.resourceItem);
        assertEquals(2.5f, mining.extractionPerSecond, 0f);

        assertThrows(IllegalArgumentException.class,
                () -> new MiningComponent(Constants.ITEM_FOOD, 1f));
        assertThrows(IllegalArgumentException.class,
                () -> new MiningComponent(-1, 1f));
        assertThrows(IllegalArgumentException.class,
                () -> new MiningComponent(Constants.MAX_ITEMS, 1f));
        assertThrows(IllegalArgumentException.class,
                () -> new MiningComponent(Constants.ITEM_ORE, 0f));
        assertThrows(IllegalArgumentException.class,
                () -> new MiningComponent(Constants.ITEM_ORE, -1f));
        assertThrows(IllegalArgumentException.class,
                () -> new MiningComponent(Constants.ITEM_ORE, Float.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new MiningComponent(Constants.ITEM_ORE, Float.POSITIVE_INFINITY));
    }

    @Test
    void боевойКомпонентОтличаетИсправностьОтДопустимогоНебоевогоСостояния() {
        CombatComponent standard = new CombatComponent();
        assertTrue(standard.isOperational());

        assertFalse(new CombatComponent(0f, 100f, 0f, 0f, 10f, 50f).isOperational());
        assertFalse(new CombatComponent(100f, 100f, 0f, 0f, 0f, 50f).isOperational());
        assertFalse(new CombatComponent(100f, 100f, 0f, 0f, 10f, 0f).isOperational());

        CombatComponent damaged = new CombatComponent(25f, 100f, 5f, 20f, 8f, 75f);
        assertTrue(damaged.isOperational());
        damaged.hull = Float.NaN;
        assertFalse(damaged.isOperational());
        damaged.hull = 101f;
        assertFalse(damaged.isOperational());
        damaged.hull = 25f;
        damaged.shields = -1f;
        assertFalse(damaged.isOperational());
    }

    @Test
    void боевойКонструкторОтклоняетПовреждённыеЧислаИГраницы() {
        assertThrows(IllegalArgumentException.class,
                () -> new CombatComponent(-1f, 100f, 0f, 0f, 1f, 1f));
        assertThrows(IllegalArgumentException.class,
                () -> new CombatComponent(101f, 100f, 0f, 0f, 1f, 1f));
        assertThrows(IllegalArgumentException.class,
                () -> new CombatComponent(0f, 0f, 0f, 0f, 1f, 1f));
        assertThrows(IllegalArgumentException.class,
                () -> new CombatComponent(1f, 100f, -1f, 0f, 1f, 1f));
        assertThrows(IllegalArgumentException.class,
                () -> new CombatComponent(1f, 100f, 2f, 1f, 1f, 1f));
        assertThrows(IllegalArgumentException.class,
                () -> new CombatComponent(1f, 100f, 0f, 0f, -1f, 1f));
        assertThrows(IllegalArgumentException.class,
                () -> new CombatComponent(1f, 100f, 0f, 0f, 1f, -1f));
        assertThrows(IllegalArgumentException.class,
                () -> new CombatComponent(Float.NaN, 100f, 0f, 0f, 1f, 1f));
        assertThrows(IllegalArgumentException.class,
                () -> new CombatComponent(1f, Float.POSITIVE_INFINITY, 0f, 0f, 1f, 1f));
        assertThrows(IllegalArgumentException.class,
                () -> new CombatComponent(1f, 100f, 0f, Float.NaN, 1f, 1f));
    }
}
