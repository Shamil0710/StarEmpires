package com.spacesim.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfirmButtonTest {
    @Test
    void noButtonBeforeAPreview() {
        var state = ConfirmButton.hidden();
        assertFalse(state.visible());
        assertFalse(state.enabled());
        assertThrows(IllegalArgumentException.class, () -> new ConfirmButton.State(false, true, "x"));
    }

    @Test
    void allowedPreviewNamesTheOutcomeAndRejectedOnePointsToTheStatus() {
        var allowed = ConfirmButton.previewed(true, "СТЫКОВКА");
        assertTrue(allowed.visible() && allowed.enabled());
        assertEquals("ПОДТВЕРДИТЬ: СТЫКОВКА", allowed.label());

        var rejected = ConfirmButton.previewed(false, "СТЫКОВКА");
        assertTrue(rejected.visible());
        assertFalse(rejected.enabled());
        assertEquals(ConfirmButton.UNAVAILABLE, rejected.label());
    }

    @Test
    void physicalOutcomesCarryAmountAndExactMoney() {
        assertEquals("ПОКУПКА 100 КГ • СПИСАНИЕ 4,800.000 КР", ConfirmButton.physicalOutcome("BUY", 100, -4_800_000L));
        assertEquals("ПРОДАЖА 947 КГ • ПОСТУПЛЕНИЕ 17,046.000 КР", ConfirmButton.physicalOutcome("SELL", 947, 17_046_000L));
        assertEquals("ЗАГРУЗКА БОЕЗАПАСА 3 ШТ.", ConfirmButton.physicalOutcome("LOAD_AMMUNITION", 3, 0L));
        assertEquals("СТЫКОВКА", ConfirmButton.physicalOutcome("DOCK", 100, 0L), "docking ignores the amount");
        assertEquals("ПОКУПКА КАРТЫ МАРШРУТА • СПИСАНИЕ 100.000 КР",
                ConfirmButton.physicalOutcome("BUY_ROUTE_MAP", 0, -100_000L));
        assertEquals("ДЕЙСТВИЕ", ConfirmButton.physicalOutcome("SOMETHING_NEW", 5, 0L));
        // Exact milli-credits, not rounded to hundredths.
        assertEquals("ПОКУПКА 1 КГ • СПИСАНИЕ 1.234 КР", ConfirmButton.physicalOutcome("BUY", 1, -1_234L));
        assertEquals("НАЧАЛО ДОБЫЧИ ДО 10000 КГ", ConfirmButton.physicalOutcome("START_MINING", 10_000, 0L));
    }

    @Test
    void everyPreviewedPhysicalActionHasItsOwnName() {
        // Every action GeneratedWorldCommandGame previews through previewPilotPhysical.
        String[] actions = {"ACKNOWLEDGE_JOURNAL", "ALLOCATE_FACILITY_RESOURCES", "ALLOCATE_FACILITY_RESOURCES_HALF",
                "ALLOCATE_YARD_RESOURCES", "ALLOCATE_YARD_RESOURCES_AVAILABLE", "BUY", "BUY_CONSUMABLE", "BUY_PRODUCT",
                "BUY_RESOURCE_MAP", "BUY_ROUTE_MAP", "BUY_STATION_MAP", "CANCEL_FACILITY_CONSTRUCTION", "CANCEL_MANUFACTURING",
                "CANCEL_MODULE_TRANSFER", "CANCEL_PRODUCT_TRANSFER", "CANCEL_REFIT", "CANCEL_REPAIR", "CANCEL_SMALL_CRAFT_BUILD",
                "CANCEL_YARD_CONSTRUCTION", "CAPITALIZE", "CLAIM_SMALL_CRAFT_BUILD", "DOCK", "JUMP", "LOAD_AMMUNITION",
                "LOAD_COMMODITY", "LOAD_CONSUMABLE", "LOAD_MODULE", "LOAD_PRODUCT", "PURCHASE", "PURCHASE_STATION",
                "REPORT_DISCOVERY", "SCAN", "SELL", "START_COMPONENT_MANUFACTURING", "START_FACILITY_CONSTRUCTION",
                "START_MANUFACTURING", "START_MINING", "START_REFINING", "START_REFIT", "START_REFIT_USED", "START_REPAIR",
                "START_SMALL_CRAFT_BUILD", "START_YARD_CONSTRUCTION", "STOP_MINING", "SWITCH", "UNDOCK", "UNLOAD_COMMODITY",
                "UNLOAD_MODULE", "UNLOAD_PRODUCT", "WITHDRAW"};
        for (String action : actions) {
            assertFalse(ConfirmButton.physicalOutcome(action, 1, 0L).startsWith("ДЕЙСТВИЕ"), action + " has no name");
        }
    }

    @Test
    void contractOutcomesNameTheCommand() {
        assertEquals("ПРИНЯТЬ ПОСТАВКУ 500 КГ • ПРЕМИЯ 1,200.000 КР",
                ConfirmButton.missionOutcome("ACCEPT", 500, 1_200_000L));
        assertEquals("ПРИНЯТЬ КОНТРАКТ", ConfirmButton.missionOutcome("ACCEPT", 0, 0L));
        assertEquals("ОТКЛОНИТЬ ПРЕДЛОЖЕНИЕ", ConfirmButton.missionOutcome("REJECT", 0, 0L));
        assertEquals("ОТМЕНИТЬ КОНТРАКТ", ConfirmButton.missionOutcome("CANCEL", 0, 0L));
    }
}
