package com.spacesim.economy;

import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ManufacturingProductRegistry.Provenance;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage18ShipyardCatalog;
import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.content.Stage22EmpireProductionCatalogs;
import com.spacesim.content.Stage22EmpireShipyardCatalogLoader;
import com.spacesim.content.Stage22ShipConsumableCatalogLoader;
import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage22CorePairProtectionCatalogLoader;
import com.spacesim.content.ship.Stage22CorePairShipyardIndustrialCatalogLoader;
import com.spacesim.content.weapon.Stage22CorePairWeaponRuntimeCatalogLoader;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipyardEngineeringService;
import com.spacesim.ship.ShipyardEngineeringService.ShipyardCapability;
import com.spacesim.world.SmallCraftFitAuthority;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.SmallCraftHangarCapacity.HostKind;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import com.spacesim.world.SmallCraftHangarRegistry;
import com.spacesim.world.SmallCraftProductionLogisticsService;
import com.spacesim.world.SmallCraftProductionLogisticsService.BuildStatus;
import com.spacesim.world.SmallCraftRegistry;
import com.spacesim.world.SmallCraftStationSupplyService;
import com.spacesim.world.SmallCraftStationSupplyService.SupplyStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SmallCraftProductionLogisticsServiceTest {
    private static final String DESIGN = "fit.empire.corvette.line_v1";
    private static final String FACTION = "faction.empire";
    private static final String STATION_ID = "station.m22_8g.shipyard";

    @Test
    void realStage18SettlementCreatesFreshUnsuppliedCraftOnlyAtPhysicalStationBay() {
        Fixture fixture = fixture();
        loadBuildInputs(fixture);

        var result = fixture.service.buildAtStation(
                FACTION,
                DESIGN,
                fixture.station,
                fixture.bay,
                fixture.yard,
                fixture.yard.openInterval(2_000_000d));

        assertTrue(result.completed());
        assertEquals(BuildStatus.COMPLETED_AT_STATION, result.status());
        assertEquals(1L, result.craftId().value());
        assertEquals(2L, result.nextIdValue());
        assertNotNull(result.settlement());
        assertTrue(result.settlement().settled());

        var craft = fixture.registry.find(result.craftId()).orElseThrow();
        assertEquals(0d, craft.runtimeState().consumables().reactionMassKg(), 0d);
        assertEquals(0L, craft.runtimeState().consumables().ammunitionCount());
        assertTrue(craft.instanceState().weaponLoadout().feeds().isEmpty());
        assertTrue(craft.instanceState().shieldStatesByMount().isEmpty(),
                "build completion cannot grant charged shields outside an explicit initialization/service authority");

        var assignment = fixture.hangars.find(result.craftId()).orElseThrow();
        assertEquals(OccupancyState.PARKED, assignment.state());
        assertEquals(HostKind.STATION, assignment.hostKind());
        assertEquals(STATION_ID, assignment.bayId().hostStableId());

        fixture.fit.installedModules().forEach(module ->
                assertEquals(0, fixture.station.storage().productCount(module.moduleId())));
        fixture.shipyards.findHullProfile(fixture.fit.hullId()).buildInputsKg().forEach(input ->
                assertEquals(0d, fixture.station.storage().commodityMassKg(input.commodityId()), 1e-9d));
    }

    @Test
    void stationSupplyConsumesPhysicalWaterAndManufacturedRoundsIntoSameCraftIdentity() {
        Fixture fixture = fixture();
        loadBuildInputs(fixture);
        var built = fixture.service.buildAtStation(
                FACTION,
                DESIGN,
                fixture.station,
                fixture.bay,
                fixture.yard,
                fixture.yard.openInterval(2_000_000d));
        var craftId = built.craftId();

        fixture.station.storage().addCommodity("commodity.material.purified_water", 2_000d);
        fixture.station.storage().addProduct("ammo.empire_axial_dart_150kg_v1", 5);

        var propellant = fixture.supply.loadCommodityAtStation(
                craftId,
                fixture.station,
                fixture.bay,
                "ship_consumable.reaction_mass.empire_endurance_water_v1",
                "core_drive",
                1_000d);
        assertEquals(SupplyStatus.LOADED, propellant.status());
        assertEquals(1_000d, propellant.loadedMassKg(), 0d);
        assertEquals(1_000d, fixture.station.storage()
                .commodityMassKg("commodity.material.purified_water"), 0d);

        var ammunition = fixture.supply.loadAmmunitionAtStation(
                craftId,
                fixture.station,
                fixture.bay,
                "ammo.empire_axial_dart_150kg_v1",
                "weapon_primary",
                3);
        assertEquals(SupplyStatus.LOADED, ammunition.status());
        assertEquals(3, ammunition.loadedRounds());
        assertEquals(450d, ammunition.loadedMassKg(), 0d);
        assertEquals(2, fixture.station.storage()
                .productCount("ammo.empire_axial_dart_150kg_v1"));

        var supplied = fixture.registry.find(craftId).orElseThrow();
        assertEquals(1_000d, supplied.runtimeState().consumables().reactionMassKg(), 0d);
        assertEquals(3L, supplied.runtimeState().consumables().ammunitionCount());
        assertEquals(
                "ammo.empire_axial_dart_150kg_v1",
                supplied.instanceState().weaponLoadout()
                        .ammunitionContentId("weapon_primary", "kinetic_feed")
                        .orElseThrow());
        assertEquals(craftId, supplied.id());
    }

    @Test
    void supplyInterruptionDoesNotCreatePropellantOrAmmunition() {
        Fixture fixture = fixture();
        loadBuildInputs(fixture);
        var built = fixture.service.buildAtStation(
                FACTION,
                DESIGN,
                fixture.station,
                fixture.bay,
                fixture.yard,
                fixture.yard.openInterval(2_000_000d));
        var before = fixture.registry.find(built.craftId()).orElseThrow();

        var propellant = fixture.supply.loadCommodityAtStation(
                built.craftId(),
                fixture.station,
                fixture.bay,
                "ship_consumable.reaction_mass.empire_endurance_water_v1",
                "core_drive",
                1_000d);
        var ammunition = fixture.supply.loadAmmunitionAtStation(
                built.craftId(),
                fixture.station,
                fixture.bay,
                "ammo.empire_axial_dart_150kg_v1",
                "weapon_primary",
                2);

        assertEquals(SupplyStatus.PHYSICAL_STOCK_OR_INTERFACE_REJECTED, propellant.status());
        assertEquals(SupplyStatus.PHYSICAL_STOCK_OR_INTERFACE_REJECTED, ammunition.status());
        assertEquals(before, fixture.registry.find(built.craftId()).orElseThrow());
        assertEquals(0d, fixture.station.storage()
                .commodityMassKg("commodity.material.purified_water"), 0d);
        assertEquals(0, fixture.station.storage()
                .productCount("ammo.empire_axial_dart_150kg_v1"));
    }

    @Test
    void stationSupplyPreflightsHullAndBayMassBeforeTouchingStock() {
        Fixture fixture = fixture();
        loadBuildInputs(fixture);
        var built = fixture.service.buildAtStation(
                FACTION,
                DESIGN,
                fixture.station,
                fixture.bay,
                fixture.yard,
                fixture.yard.openInterval(2_000_000d));
        fixture.station.storage().addCommodity("commodity.material.purified_water", 20_000_000d);
        double stockBefore = fixture.station.storage()
                .commodityMassKg("commodity.material.purified_water");

        BayDefinition constrained = new BayDefinition(
                fixture.bay.id(),
                HostKind.STATION,
                fixture.bay.singleCraftEnvelopeM(),
                fixture.bay.pristineUsableVolumeM3(),
                fixture.registry.physicalFootprint(built.craftId()).currentMassKg() + 100d,
                1d);

        var result = fixture.supply.loadCommodityAtStation(
                built.craftId(),
                fixture.station,
                constrained,
                "ship_consumable.reaction_mass.empire_endurance_water_v1",
                "core_drive",
                1_000d);

        assertEquals(SupplyStatus.BAY_MASS_LIMIT, result.status());
        assertEquals(stockBefore, fixture.station.storage()
                .commodityMassKg("commodity.material.purified_water"), 0d);
        assertEquals(0d, fixture.registry.find(built.craftId()).orElseThrow()
                .runtimeState().consumables().reactionMassKg(), 0d);
    }

    @Test
    void insufficientStage18StockConsumesNothingAndDoesNotAllocateIdentity() {
        Fixture fixture = fixture();
        loadBuildInputs(fixture);
        String missing = fixture.shipyards.findHullProfile(fixture.fit.hullId())
                .buildInputsKg().get(0).commodityId();
        fixture.station.storage().removeCommodity(missing,
                fixture.station.storage().commodityMassKg(missing));
        var before = fixture.station.storage().snapshot();
        long idBefore = fixture.registry.nextIdValue();

        var result = fixture.service.buildAtStation(
                FACTION,
                DESIGN,
                fixture.station,
                fixture.bay,
                fixture.yard,
                fixture.yard.openInterval(2_000_000d));

        assertEquals(BuildStatus.STAGE18_SETTLEMENT_REJECTED, result.status());
        assertEquals(idBefore, fixture.registry.nextIdValue());
        assertEquals(0, fixture.registry.size());
        assertEquals(before, fixture.station.storage().snapshot());
        assertTrue(fixture.hangars.snapshot().isEmpty());
    }

    @Test
    void incompatibleStationBayBlocksBuildBeforeMaterialOrWorkSettlement() {
        Fixture fixture = fixture();
        loadBuildInputs(fixture);
        var before = fixture.station.storage().snapshot();
        var budget = fixture.yard.openInterval(2_000_000d);
        double workBefore = budget.remainingWorkSeconds();
        BayDefinition tooSmall = new BayDefinition(
                new BayId(STATION_ID, "bay.too-small"),
                HostKind.STATION,
                new com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d(10d, 10d, 10d),
                1_000d,
                1_000d,
                1d);

        var result = fixture.service.buildAtStation(
                FACTION,
                DESIGN,
                fixture.station,
                tooSmall,
                fixture.yard,
                budget);

        assertEquals(BuildStatus.STATION_BAY_CAPACITY_BLOCKED, result.status());
        assertEquals(1L, fixture.registry.nextIdValue());
        assertEquals(before, fixture.station.storage().snapshot());
        assertEquals(workBefore, budget.remainingWorkSeconds(), 0d);
        assertEquals(0, fixture.registry.size());
    }

    @Test
    void buildCannotTeleportDirectlyIntoCarrierOrDifferentStation() {
        Fixture fixture = fixture();
        loadBuildInputs(fixture);
        BayDefinition carrierBay = new BayDefinition(
                new BayId("carrier.alpha", "bay.flight"),
                HostKind.SHIP,
                fixture.bay.singleCraftEnvelopeM(),
                fixture.bay.pristineUsableVolumeM3(),
                fixture.bay.pristineSupportedMassKg(),
                1d);
        BayDefinition otherStation = new BayDefinition(
                new BayId("station.other", "bay.flight"),
                HostKind.STATION,
                fixture.bay.singleCraftEnvelopeM(),
                fixture.bay.pristineUsableVolumeM3(),
                fixture.bay.pristineSupportedMassKg(),
                1d);

        assertThrows(IllegalArgumentException.class, () -> fixture.service.buildAtStation(
                FACTION, DESIGN, fixture.station, carrierBay, fixture.yard,
                fixture.yard.openInterval(2_000_000d)));
        assertThrows(IllegalArgumentException.class, () -> fixture.service.buildAtStation(
                FACTION, DESIGN, fixture.station, otherStation, fixture.yard,
                fixture.yard.openInterval(2_000_000d)));

        assertEquals(1L, fixture.registry.nextIdValue());
        assertEquals(0, fixture.registry.size());
    }

    private static Fixture fixture() {
        var engineering = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        var fit = InstalledFit.fromDemonstrator(engineering.findDemonstratorFit(DESIGN));
        var weaponContent = Stage22CorePairWeaponRuntimeCatalogLoader.loadCombined();
        var products = Stage18ManufacturingProductRegistry.loadDefault()
                .withEngineeringCatalog(engineering, Provenance.STAGE22_AUTHORED)
                .withAmmunitionCatalog(
                        weaponContent.ammunition(),
                        Provenance.STAGE22_AUTHORED);
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        Stage18ShipyardCatalog shipyards = Stage22EmpireShipyardCatalogLoader.loadDefault();

        var infrastructure = Stage18StationInfrastructureCatalogLoader.loadDefault()
                .findArchetype("station.infrastructure.industrial_station");
        var station = Stage18StationIndustrialNode.instantiate(
                STATION_ID,
                "location.orbital_station",
                infrastructure,
                ontology,
                products);

        var definition = shipyards.findYard(Stage22EmpireProductionCatalogs.YARD_ID);
        ShipyardCapability planner = new ShipyardCapability(
                "yard.m22_8g.instance",
                definition.berthDimensionsM(),
                definition.maxServiceMassKg(),
                definition.stage175FabricationCapabilities(),
                definition.stage175HandledRequirementIds(),
                definition.toolingTags(),
                definition.precisionCapability(),
                definition.ratedEngineeringWorkRate(),
                definition.laborCapacity(),
                definition.automationCapacity(),
                definition.ratedIntegrationPowerW());
        var yard = new Stage18ShipyardRuntime.YardCapabilitySnapshot(
                "yard.m22_8g.instance",
                definition.id(),
                Stage18ShipyardRuntime.YardStatus.ACTIVE,
                planner,
                definition.handledStorageClassIds(),
                definition.maxHandledUnitMassKg());

        var registry = SmallCraftRegistry.empty(new SmallCraftFitAuthority(engineering));
        var hangars = SmallCraftHangarRegistry.empty(registry);
        var bay = new BayDefinition(
                new BayId(STATION_ID, "bay.production"),
                HostKind.STATION,
                new com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d(200d, 100d, 100d),
                100_000_000d,
                100_000_000d,
                1d);

        var engineeringService = new ShipyardEngineeringService(
                engineering,
                Stage22CorePairShipyardIndustrialCatalogLoader.loadEmpireDefault());
        var runtime = new Stage18ShipyardRuntime(shipyards, ontology, products);
        var service = new SmallCraftProductionLogisticsService(
                registry,
                hangars,
                engineering,
                Stage22CorePairProtectionCatalogLoader.project(engineering),
                engineeringService,
                runtime);
        var supply = new SmallCraftStationSupplyService(
                registry,
                hangars,
                engineering,
                new Stage18ShipConsumableService(
                        Stage22ShipConsumableCatalogLoader.loadDefault(),
                        engineering),
                products,
                weaponContent.launchers(),
                weaponContent.ammunition());
        return new Fixture(
                service, supply, registry, hangars, station, yard, bay, shipyards, fit);
    }

    private static void loadBuildInputs(Fixture fixture) {
        fixture.shipyards.findHullProfile(fixture.fit.hullId()).buildInputsKg().forEach(input ->
                fixture.station.storage().addCommodity(input.commodityId(), input.massKg()));
        fixture.fit.installedModules().forEach(module ->
                fixture.station.storage().addProduct(module.moduleId(), 1));
    }

    private record Fixture(
            SmallCraftProductionLogisticsService service,
            SmallCraftStationSupplyService supply,
            SmallCraftRegistry registry,
            SmallCraftHangarRegistry hangars,
            Stage18StationIndustrialNode station,
            Stage18ShipyardRuntime.YardCapabilitySnapshot yard,
            BayDefinition bay,
            Stage18ShipyardCatalog shipyards,
            InstalledFit fit) { }
}
