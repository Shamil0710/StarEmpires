package com.spacesim.economy;

import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage18ShipConsumableCatalogLoader;
import com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22CorePropellantServicingAcceptanceTest {
    @Test
    void coreFreightMainDrivesRefuelOnlyByConsumingFinitePurifiedWaterStock() {
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var products = Stage18ManufacturingProductRegistry.loadDefault();
        var engineering = Stage22FreightStrategicEngineeringCatalogLoader.loadDefault();
        var bindings = Stage18ShipConsumableCatalogLoader.loadDefault();

        List<Case> cases = List.of(
                new Case(
                        Stage22FreightStrategicEngineeringCatalogLoader.EMPIRE_FREIGHT_STRATEGIC_FIT,
                        "ship_consumable.reaction_mass.empire_endurance_water_v1"),
                new Case(
                        Stage22FreightStrategicEngineeringCatalogLoader.UNION_FREIGHT_STRATEGIC_FIT,
                        "ship_consumable.reaction_mass.industrial_union_drive_bank_water_v1"));

        for (Case value : cases) {
            InstalledFit fit = InstalledFit.fromDemonstrator(
                    engineering.findDemonstratorFit(value.fitId()));
            Stage18StationStorage storage = new Stage18StationStorage(
                    ontology,
                    products,
                    "station.stage22.propellant." + value.bindingId().hashCode(),
                    Map.of("storage.liquid_tank", 10_000d),
                    Map.of("commodity.material.purified_water", 5_000d),
                    Map.of());
            Stage18ShipConsumableService service =
                    new Stage18ShipConsumableService(bindings, engineering);

            var loaded = service.load(
                    value.bindingId(),
                    "core_drive",
                    1_000d,
                    fit,
                    ConsumableState.empty(),
                    storage);

            assertTrue(loaded.committed(), () -> "physical refuel failed for " + value.fitId());
            assertEquals(4_000d,
                    storage.commodityMassKg("commodity.material.purified_water"),
                    1.0e-9d);
            assertEquals(1_000d, loaded.consumables().reactionMassKg(), 1.0e-9d);
        }
    }

    private record Case(String fitId, String bindingId) { }
}
