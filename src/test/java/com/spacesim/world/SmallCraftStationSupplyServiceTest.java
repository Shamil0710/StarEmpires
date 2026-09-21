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
import com.spacesim.world.SmallCraftStationSupplyService.SupplyStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SmallCraftStationSupplyServiceTest {
    private static final String STATION_ID = "station.m22_8g.supply";
    private static final String WATER_ID = "commodity.material.purified_water";
    private static final String AMMO_ID = "ammo.empire_axial_dart_150kg_v1";

    @Test
    void stationSupplyConsumesPhysicalWaterAndManufacturedRoundsIntoSameCraftIdentity() {
        Fixture fixture = fixture();
        fixture.station.storage().addCommodity(WATER_ID, 2_000d);
        fixture.station.storage().addProduct(AMMO_ID, 5);

        var propellant = fixture.supply.loadCommodityAtStation(
                fixture.craftId,
                fixture.station,
                fixture.bay,
                "ship_consumable.reaction_mass.empire_endurance_water_v1",
                "core_drive",
                1_000d);
        assertEquals(SupplyStatus.LOADED, propellant.status());
        assertEquals(1_000d, propellant.loadedMassKg(), 0d);
        assertEquals(1_000d, fixture.station.storage().commodityMassKg(WATER_ID), 0d);

        var ammunition = fixture.supply.loadAmmunitionAtStation(
                fixture.craftId,
                fixture.station,
                fixture.bay,
                AMMO_ID,
                "weapon_primary",
                3);
        assertEquals(SupplyStatus.LOADED, ammunition.status());
        assertEquals(3, ammunition.loadedRounds());
        assertEquals(450d, ammunition.loadedMassKg(), 0d);
        assertEquals(2, fixture.station.storage().productCount(AMMO_ID));

        SmallCraftState supplied = fixture.registry.find(fixture.craftId).orElseThrow();
        assertEquals(fixture.craftId, supplied.id());
        assertEquals(1_000d, supplied.runtimeState().consumables().reactionMassKg(), 0d);
        assertEquals(3L, supplied.runtimeState().consumables().ammunitionCount());
        assertEquals(
                AMMO_ID,
                supplied.instanceState().weaponLoadout()
                        .ammunitionContentId("weapon_primary", "kinetic_feed")
                        .orElseThrow());
    }

    @Test
    void supplyInterruptionLeavesCraftAndCanonicalStorageUnchanged() {
        Fixture fixture = fixture();
        SmallCraftState beforeCraft = fixture.registry.find(fixture.craftId).orElseThrow();
        var beforeStorage = fixture.station.storage().snapshot();

        var propellant = fixture.supply.loadCommodityAtStation(
                fixture.craftId,
                fixture.station,
                fixture.bay,
                "ship_consumable.reaction_mass.empire_endurance_water_v1",
                "core_drive",
                1_000d);
        var ammunition = fixture.supply.loadAmmunitionAtStation(
                fixture.craftId,
                fixture.station,
                fixture.bay,
                AMMO_ID,
                "weapon_primary",
                2);

        assertEquals(SupplyStatus.PHYSICAL_STOCK_OR_INTERFACE_REJECTED, propellant.status());
        assertEquals(SupplyStatus.PHYSICAL_STOCK_OR_INTERFACE_REJECTED, ammunition.status());
        assertEquals(beforeCraft, fixture.registry.find(fixture.craftId).orElseThrow());
        assertEquals(beforeStorage, fixture.station.storage().snapshot());
    }

    @Test
    void degradedBayMassBlocksSupplyBeforeTouchingStage18Stock() {
        Fixture fixture = fixture();
        fixture.station.storage().addCommodity(WATER_ID, 20_000d);
        double stockBefore = fixture.station.storage().commodityMassKg(WATER_ID);
        double currentMassKg = fixture.registry.physicalFootprint(fixture.craftId).currentMassKg();

        BayDefinition constrained = new BayDefinition(
                fixture.bay.id(),
                HostKind.STATION,
                fixture.bay.singleCraftEnvelopeM(),
                fixture.bay.pristineUsableVolumeM3(),
                currentMassKg + 100d,
                1d);

        var result = fixture.supply.loadCommodityAtStation(
                fixture.craftId,
                fixture.station,
                constrained,
                "ship_consumable.reaction_mass.empire_endurance_water_v1",
                "core_drive",
                1_000d);

        assertEquals(SupplyStatus.BAY_MASS_LIMIT, result.status());
        assertEquals(stockBefore, fixture.station.storage().commodityMassKg(WATER_ID), 0d);
        assertEquals(
                0d,
                fixture.registry.find(fixture.craftId).orElseThrow()
                        .runtimeState().consumables().reactionMassKg(),
                0d);
    }

    @Test
    void destroyedCraftCannotBeResuppliedOrReappearThroughSupply() {
        Fixture fixture = fixture();
        fixture.station.storage().addCommodity(WATER_ID, 2_000d);
        fixture.hangars.release(fixture.craftId);
        fixture.registry.removeDestroyedCraft(fixture.craftId);
        long allocatorWatermark = fixture.registry.nextIdValue();

        assertThrows(IllegalArgumentException.class, () ->
                fixture.supply.loadCommodityAtStation(
                        fixture.craftId,
                        fixture.station,
                        fixture.bay,
                        "ship_consumable.reaction_mass.empire_endurance_water_v1",
                        "core_drive",
                        1_000d));

        assertTrue(fixture.registry.find(fixture.craftId).isEmpty());
        assertEquals(allocatorWatermark, fixture.registry.nextIdValue());
        assertEquals(2_000d, fixture.station.storage().commodityMassKg(WATER_ID), 0d);
    }

    private static Fixture fixture() {
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
                STATION_ID,
                "location.orbital_station",
                infrastructure,
                ontology,
                products);

        SmallCraftRegistry registry =
                SmallCraftRegistry.empty(new SmallCraftFitAuthority(engineering));
        SmallCraftId craftId = registry.reserveIdentityForCompletedProduction();
        registry.registerProducedCraft(ProductionSmallCraftFixture.craft(
                craftId,
                0L,
                0d,
                0d,
                1d,
                0d));

        SmallCraftHangarRegistry hangars = SmallCraftHangarRegistry.empty(registry);
        BayDefinition bay = new BayDefinition(
                new BayId(STATION_ID, "bay.service"),
                HostKind.STATION,
                new Dimensions3d(500d, 500d, 500d),
                100_000_000d,
                100_000_000d,
                1d);
        hangars.assign(craftId, bay, OccupancyState.SERVICING);

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
        return new Fixture(registry, hangars, station, bay, craftId, supply);
    }

    private record Fixture(
            SmallCraftRegistry registry,
            SmallCraftHangarRegistry hangars,
            Stage18StationIndustrialNode station,
            BayDefinition bay,
            SmallCraftId craftId,
            SmallCraftStationSupplyService supply) { }
}
