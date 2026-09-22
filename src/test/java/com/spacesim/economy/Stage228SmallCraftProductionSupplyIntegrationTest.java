package com.spacesim.economy;

import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ManufacturingProductRegistry.Provenance;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage18ShipyardCatalog;
import com.spacesim.content.Stage18StationInfrastructureCatalog.StationArchetypeDefinition;
import com.spacesim.content.Stage228SmallCraftShipConsumableCatalogLoader;
import com.spacesim.content.Stage228SmallCraftShipyardCatalogLoader;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.Stage228SmallCraftEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage228SmallCraftProductionProjection;
import com.spacesim.content.ship.Stage228SmallCraftShipyardIndustrialCatalogLoader;
import com.spacesim.content.ship.Stage22CorePairProtectionCatalogLoader;
import com.spacesim.content.weapon.Stage228SmallCraftWeaponRuntimeCatalogLoader;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipyardEngineeringService;
import com.spacesim.world.SmallCraftFitAuthority;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.SmallCraftHangarCapacity.HostKind;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import com.spacesim.world.SmallCraftHangarRegistry;
import com.spacesim.world.SmallCraftId;
import com.spacesim.world.SmallCraftPhysicalLogisticsService;
import com.spacesim.world.SmallCraftPhysicalLogisticsService.DeliveryReceipt;
import com.spacesim.world.SmallCraftPhysicalLogisticsService.LogisticsState;
import com.spacesim.world.SmallCraftRegistry;
import com.spacesim.world.SmallCraftStationSupplyService;
import com.spacesim.world.SmallCraftStationSupplyService.SupplyStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Stage228SmallCraftProductionSupplyIntegrationTest {
    private static final String STATION_ID = "station.m22_8j.production_supply";

    @Test
    void productionSmallCraftBuildDeliveryAndRefuelConsumePhysicalStage18Stock() {
        Fixture fixture = fixture();
        InstalledFit fit = InstalledFit.fromDemonstrator(
                fixture.engineering.findDemonstratorFit(
                        Stage228SmallCraftProductionProjection.EMPIRE_INTERCEPTOR_FIT_ID));
        var plan = fixture.logistics.planBuild(
                "faction.imperial_directorate",
                Stage228SmallCraftProductionProjection.EMPIRE_INTERCEPTOR_FIT_ID,
                STATION_ID,
                fit,
                fixture.yard);
        loadBuildInputs(fixture, fit);

        var budget = fixture.yard.openInterval(
                plan.workPlan().requirements().totalWorkSeconds()
                        / fixture.yard.plannerCapability().workRate() + 5d);
        var built = fixture.logistics.settleBuild(
                LogisticsState.empty(),
                plan,
                fixture.station.storage(),
                budget);

        assertTrue(built.settlement().settled());
        SmallCraftId id = built.producedCraftOptional().orElseThrow().id();
        assertEquals(0d,
                fixture.registry.find(id).orElseThrow()
                        .runtimeState().consumables().reactionMassKg(),
                0d);

        BayDefinition bay = new BayDefinition(
                new BayId(STATION_ID, "bay.small_craft_service"),
                HostKind.STATION,
                new Dimensions3d(100d, 100d, 40d),
                10_000_000d,
                10_000_000d,
                1d);
        var delivered = fixture.logistics.confirmDelivery(
                built.logisticsState(), id, bay, 10L);
        assertTrue(delivered.assigned());
        assertEquals(
                OccupancyState.SERVICING,
                fixture.hangars.find(id).orElseThrow().state());

        fixture.station.storage().addCommodity(
                "commodity.material.purified_water", 5_000d);
        double waterBefore = fixture.station.storage().commodityMassKg(
                "commodity.material.purified_water");

        var loaded = fixture.supply.loadCommodityAtStation(
                id,
                fixture.station,
                bay,
                Stage228SmallCraftShipConsumableCatalogLoader.REACTION_MASS_BINDING_ID,
                "core_drive",
                1_000d);

        assertEquals(SupplyStatus.LOADED, loaded.status());
        assertEquals(1_000d, loaded.loadedMassKg(), 0d);
        assertEquals(
                waterBefore - 1_000d,
                fixture.station.storage().commodityMassKg(
                        "commodity.material.purified_water"),
                0d);
        assertEquals(
                1_000d,
                fixture.registry.find(id).orElseThrow()
                        .runtimeState().consumables().reactionMassKg(),
                0d);
        assertEquals(id, fixture.registry.find(id).orElseThrow().id());
    }

    private static Fixture fixture() {
        ShipEngineeringCatalog engineering =
                Stage228SmallCraftEngineeringCatalogLoader.loadDefault();
        var protection = Stage22CorePairProtectionCatalogLoader.project(engineering);
        var industrial =
                Stage228SmallCraftShipyardIndustrialCatalogLoader.loadEmpireDefault();
        ShipyardEngineeringService engineeringService =
                new ShipyardEngineeringService(engineering, industrial);
        Stage18ResourceOntologyCatalog ontology =
                Stage18ResourceOntologyLoader.loadDefault();
        var weaponContent =
                Stage228SmallCraftWeaponRuntimeCatalogLoader.loadCombined();
        Stage18ManufacturingProductRegistry products =
                Stage18ManufacturingProductRegistry.loadDefault()
                        .withEngineeringCatalog(engineering, Provenance.STAGE22_AUTHORED)
                        .withAmmunitionCatalog(
                                weaponContent.ammunition(),
                                Provenance.STAGE22_AUTHORED);
        Stage18ShipyardCatalog physicalShipyard =
                Stage228SmallCraftShipyardCatalogLoader.loadEmpireDefault();
        Stage18ShipyardRuntime shipyardRuntime =
                new Stage18ShipyardRuntime(physicalShipyard, ontology, products);

        StationArchetypeDefinition stationDefinition =
                new StationArchetypeDefinition(
                        "station.infrastructure.m22_8j_supply_test",
                        "M22.8J small-craft supply integration station",
                        List.of(
                                "facility.fabrication.heavy",
                                "facility.fabrication.electrical",
                                "facility.fabrication.precision",
                                "facility.fabrication.assembly"),
                        Map.of(
                                "storage.dry_bulk", 200_000_000d,
                                "storage.general_container", 100_000_000d,
                                "storage.hazardous_controlled", 50_000_000d,
                                "storage.high_value_controlled", 100_000_000d,
                                "storage.oversized", 100_000_000d),
                        Set.of(
                                "storage.dry_bulk",
                                "storage.general_container",
                                "storage.hazardous_controlled",
                                "storage.high_value_controlled",
                                "storage.oversized"),
                        2_000_000d,
                        20_000_000d,
                        Set.of("location.orbital_station"));
        Stage18StationIndustrialNode station =
                Stage18StationIndustrialNode.instantiate(
                        STATION_ID,
                        "location.orbital_station",
                        stationDefinition,
                        ontology,
                        products);

        Stage18FacilityRuntime facilities =
                new Stage18FacilityRuntime(Stage18FacilityCatalogLoader.loadDefault());
        List<Stage18FacilityRuntime.FacilityCapabilitySnapshot> support =
                new ArrayList<>();
        for (var reference : station.installedFacilities()) {
            support.add(facilities.project(
                    new Stage18FacilityRuntime.InstalledFacilityState(
                            reference.facilityInstanceId(),
                            reference.facilityDefinitionId(),
                            1d,
                            10_000_000_000d,
                            10_000_000_000d,
                            10_000d,
                            1_000d,
                            station.locationTag(),
                            true)));
        }
        var yard = shipyardRuntime.projectYard(
                new Stage18ShipyardRuntime.InstalledYardState(
                        "yard.instance.m22_8j_supply",
                        "yard.empire_capital_service_v1",
                        1d,
                        3_500_000_000d,
                        22d,
                        1_200,
                        700,
                        true),
                station,
                support);
        assertTrue(yard.active());

        SmallCraftRegistry registry =
                SmallCraftRegistry.empty(new SmallCraftFitAuthority(engineering));
        SmallCraftHangarRegistry hangars =
                SmallCraftHangarRegistry.empty(registry);
        SmallCraftPhysicalLogisticsService logistics =
                new SmallCraftPhysicalLogisticsService(
                        registry,
                        hangars,
                        engineering,
                        protection,
                        engineeringService,
                        shipyardRuntime,
                        (pending, destination, tick) -> new DeliveryReceipt(
                                pending.craftId(),
                                pending.sourceStationId(),
                                destination.id().hostStableId(),
                                "transport.m22_8j." + pending.craftId().value(),
                                tick,
                                true));

        SmallCraftStationSupplyService supply =
                new SmallCraftStationSupplyService(
                        registry,
                        hangars,
                        engineering,
                        new Stage18ShipConsumableService(
                                Stage228SmallCraftShipConsumableCatalogLoader.loadDefault(),
                                engineering),
                        products,
                        weaponContent.launchers(),
                        weaponContent.ammunition());

        return new Fixture(
                engineering,
                physicalShipyard,
                station,
                yard,
                registry,
                hangars,
                logistics,
                supply);
    }

    private static void loadBuildInputs(Fixture fixture, InstalledFit fit) {
        Stage18ShipyardCatalog.HullPhysicalProfile hull =
                fixture.physicalShipyard.findHullProfile(fit.hullId());
        hull.buildInputsKg().forEach(input ->
                fixture.station.storage().addCommodity(
                        input.commodityId(), input.massKg()));
        for (InstalledModuleDefinition installed : fit.installedModules()) {
            fixture.station.storage().addProduct(installed.moduleId(), 1);
        }
    }

    private record Fixture(
            ShipEngineeringCatalog engineering,
            Stage18ShipyardCatalog physicalShipyard,
            Stage18StationIndustrialNode station,
            Stage18ShipyardRuntime.YardCapabilitySnapshot yard,
            SmallCraftRegistry registry,
            SmallCraftHangarRegistry hangars,
            SmallCraftPhysicalLogisticsService logistics,
            SmallCraftStationSupplyService supply) { }
}
