package com.spacesim.world;

import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ManufacturingProductRegistry.Provenance;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.content.Stage22ShipConsumableCatalogLoader;
import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;
import com.spacesim.content.weapon.Stage22CorePairWeaponRuntimeCatalogLoader;
import com.spacesim.economy.Stage18ShipConsumableService;
import com.spacesim.economy.Stage18StationIndustrialNode;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.SmallCraftHangarCapacity.HostKind;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SmallCraftStationSupplyNoResurrectionTest {

    @Test
    void destroyedCraftCannotReappearThroughSupplyAndAllocatorDoesNotRewind() {
        var engineering = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        var weaponContent = Stage22CorePairWeaponRuntimeCatalogLoader.loadCombined();
        var products = Stage18ManufacturingProductRegistry.loadDefault()
                .withEngineeringCatalog(engineering, Provenance.STAGE22_AUTHORED)
                .withAmmunitionCatalog(
                        weaponContent.ammunition(),
                        Provenance.STAGE22_AUTHORED);
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var infrastructure = Stage18StationInfrastructureCatalogLoader.loadDefault()
                .findArchetype("station.infrastructure.frontier_multipurpose");
        Stage18StationIndustrialNode station = Stage18StationIndustrialNode.instantiate(
                "station.m22_8g.no_resurrection",
                "location.orbital_station",
                infrastructure,
                ontology,
                products);

        SmallCraftRegistry registry =
                SmallCraftRegistry.empty(new SmallCraftFitAuthority(engineering));
        SmallCraftId craftId = registry.reserveIdentityForCompletedProduction();
        registry.registerProducedCraft(ProductionSmallCraftFixture.craft(
                craftId, 0L, 0d, 0d, 1d, 0d));
        SmallCraftHangarRegistry hangars = SmallCraftHangarRegistry.empty(registry);
        BayDefinition bay = new BayDefinition(
                new BayId(station.stationId(), "bay.service"),
                HostKind.STATION,
                new Dimensions3d(500d, 500d, 500d),
                100_000_000d,
                100_000_000d,
                1d);
        hangars.assign(craftId, bay, OccupancyState.SERVICING);
        hangars.release(craftId);
        registry.removeDestroyedCraft(craftId);
        long allocatorWatermark = registry.nextIdValue();
        var storageBefore = station.storage().snapshot();

        SmallCraftStationSupplyService supply = new SmallCraftStationSupplyService(
                registry,
                hangars,
                engineering,
                new Stage18ShipConsumableService(
                        Stage22ShipConsumableCatalogLoader.loadDefault(),
                        engineering),
                products,
                weaponContent.launchers(),
                weaponContent.ammunition());

        assertThrows(IllegalArgumentException.class, () ->
                supply.loadCommodityAtStation(
                        craftId,
                        station,
                        bay,
                        "ship_consumable.reaction_mass.empire_endurance_water_v1",
                        "core_drive",
                        1_000d));

        assertTrue(registry.find(craftId).isEmpty());
        assertEquals(allocatorWatermark, registry.nextIdValue());
        assertEquals(storageBefore, station.storage().snapshot());
    }
}
