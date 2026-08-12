package com.spacesim.model;

import com.spacesim.constants.Constants;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemTypeTest {
    @Test
    void legacyКаталогЗанимаетНачальныеSlotsНеТребуяЗаполнитьВсюCapacity() {
        ItemType[] items = ItemType.values();
        boolean[] seenIds = new boolean[Constants.MAX_ITEMS];

        assertTrue(items.length < Constants.MAX_ITEMS);
        for (ItemType item : items) {
            int id = item.getId();
            assertTrue(id >= 0 && id < Constants.MAX_ITEMS, item.name());
            assertFalse(seenIds[id], "Повторяющийся id=" + id);
            seenIds[id] = true;

            assertSame(item, ItemType.fromId(id));
            assertSame(item, Constants.getItemType(id));
            assertEquals(item.getCodeName(), Constants.ITEM_NAMES[id]);
            assertEquals(item.getBasePrice(), Constants.BASE_PRICES[id], 0f);
            assertFalse(item.getDisplayName().isBlank());
            assertTrue(Float.isFinite(item.getBasePrice()));
            assertTrue(item.getBasePrice() > 0f);
        }

        for (int itemId = 0; itemId < items.length; itemId++) {
            assertTrue(seenIds[itemId]);
        }
        for (int itemId = items.length; itemId < Constants.MAX_ITEMS; itemId++) {
            assertFalse(seenIds[itemId]);
            assertNull(Constants.ITEM_NAMES[itemId]);
            assertEquals(0f, Constants.BASE_PRICES[itemId], 0f);
            assertNull(Constants.getItemType(itemId));
        }
    }

    @Test
    void lookupБезопасноОтклоняетИдентификаторыВнеLegacyКаталога() {
        assertNull(ItemType.fromId(-1));
        assertNull(ItemType.fromId(Integer.MIN_VALUE));
        assertNull(ItemType.fromId(ItemType.values().length));
        assertNull(ItemType.fromId(Constants.MAX_ITEMS));
        assertNull(ItemType.fromId(Integer.MAX_VALUE));
        assertNull(Constants.getItemType(-1));
        assertNull(Constants.getItemType(ItemType.values().length));
        assertNull(Constants.getItemType(Constants.MAX_ITEMS));
    }

    @Test
    void товарыИмеютОжидаемыеКатегорииИДобываемость() {
        assertEquals(ItemCategory.MATERIAL, ItemType.ORE.getCategory());
        assertEquals(ItemCategory.GAS_LIQUID, ItemType.ENERGY.getCategory());
        assertEquals(ItemCategory.FINISHED_GOODS, ItemType.FOOD.getCategory());
        assertEquals(ItemCategory.MATERIAL, ItemType.STEEL.getCategory());
        assertEquals(ItemCategory.FINISHED_GOODS, ItemType.WEAPONS.getCategory());

        for (ItemType item : ItemType.values()) {
            assertEquals(item == ItemType.ORE, item.isMineable(), item.name());
        }

        EnumSet<ItemCategory> representedCategories = EnumSet.noneOf(ItemCategory.class);
        for (ItemType item : ItemType.values()) {
            representedCategories.add(item.getCategory());
        }
        assertEquals(EnumSet.allOf(ItemCategory.class), representedCategories);
        for (ItemCategory category : ItemCategory.values()) {
            assertFalse(category.getDisplayName().isBlank());
        }
    }
}
