package com.spacesim.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class Stage22ShipConsumableCatalogLoaderTest {
    @Test
    void stage22OverlayAddsProductionDrivesWithoutChangingStage18DefaultVocabulary() {
        Stage18ShipConsumableCatalog legacy = Stage18ShipConsumableCatalogLoader.loadDefault();
        Stage18ShipConsumableCatalog production = Stage22ShipConsumableCatalogLoader.loadDefault();

        assertEquals(1, legacy.getBindings().size(),
                "Stage-18 default vocabulary participates in the persisted industrial fingerprint");
        assertNotNull(legacy.findBinding("ship_consumable.reaction_mass.escort_water_v1"));
        assertNull(legacy.findBinding("ship_consumable.reaction_mass.empire_longhaul_freight_water_v1"));

        assertNotNull(production.findBinding("ship_consumable.reaction_mass.escort_water_v1"));
        assertNotNull(production.findBinding("ship_consumable.reaction_mass.empire_endurance_water_v1"));
        assertNotNull(production.findBinding(
                "ship_consumable.reaction_mass.industrial_union_drive_bank_water_v1"));
        assertNotNull(production.findBinding(
                "ship_consumable.reaction_mass.empire_longhaul_freight_water_v1"));
        assertNotNull(production.findBinding(
                "ship_consumable.reaction_mass.industrial_union_longhaul_freight_water_v1"));
    }
}
