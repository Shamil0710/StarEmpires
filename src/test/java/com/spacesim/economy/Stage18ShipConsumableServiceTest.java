package com.spacesim.economy;

import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage18ShipConsumableCatalogLoader;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage18ShipConsumableServiceTest {
    @Test
    void reactionMassLoadsOnlyByConsumingBoundStationCommodity() {
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var products = Stage18ManufacturingProductRegistry.loadDefault();
        var engineering = ShipEngineeringCatalogLoader.loadDefault();
        InstalledFit fit = InstalledFit.fromDemonstrator(
                engineering.findDemonstratorFit("fit.escort_destroyer_schema_v1"));
        Stage18StationStorage storage = new Stage18StationStorage(
                ontology,
                products,
                "station.servicing.test",
                Map.of("storage.liquid_tank", 100_000d),
                Map.of("commodity.material.purified_water", 50_000d),
                Map.of());
        Stage18ShipConsumableService service = new Stage18ShipConsumableService(
                Stage18ShipConsumableCatalogLoader.loadDefault(), engineering);

        Stage18ShipConsumableService.LoadResult result = service.load(
                "ship_consumable.reaction_mass.escort_water_v1",
                "core_drive",
                20_000d,
                fit,
                ConsumableState.empty(),
                storage);

        assertTrue(result.committed());
        assertEquals(30_000d, storage.commodityMassKg("commodity.material.purified_water"), 1e-9d);
        assertEquals(20_000d, result.consumables().interfaceLoadMassKg(), 1e-9d);
        assertEquals(1, result.consumables().interfaceLoads().size());
        assertEquals(20_000d, result.consumables().interfaceLoads().get(0).amount(), 1e-9d);
    }

    @Test
    void insufficientStationStockRejectsWithoutFreeDockingRefill() {
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var products = Stage18ManufacturingProductRegistry.loadDefault();
        var engineering = ShipEngineeringCatalogLoader.loadDefault();
        InstalledFit fit = InstalledFit.fromDemonstrator(
                engineering.findDemonstratorFit("fit.escort_destroyer_schema_v1"));
        Stage18StationStorage storage = new Stage18StationStorage(
                ontology,
                products,
                "station.servicing.empty",
                Map.of("storage.liquid_tank", 100_000d),
                Map.of(),
                Map.of());
        Stage18ShipConsumableService service = new Stage18ShipConsumableService(
                Stage18ShipConsumableCatalogLoader.loadDefault(), engineering);

        Stage18ShipConsumableService.LoadResult result = service.load(
                "ship_consumable.reaction_mass.escort_water_v1",
                "core_drive",
                20_000d,
                fit,
                ConsumableState.empty(),
                storage);

        assertEquals(Stage18ShipConsumableService.Status.INSUFFICIENT_STOCK, result.status());
        assertEquals(0d, result.loadedMassKg(), 0d);
        assertTrue(result.consumables().interfaceLoads().isEmpty());
    }
}
