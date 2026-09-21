package com.spacesim.world;

import com.spacesim.components.WalletComponent;
import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ManufacturingProductRegistry.Provenance;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage18ShipyardCatalog;
import com.spacesim.content.Stage18StationInfrastructureCatalog.StationArchetypeDefinition;
import com.spacesim.content.Stage22EmpireShipyardCatalogLoader;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage22CorePairProtectionCatalogLoader;
import com.spacesim.content.ship.Stage22CorePairShipyardIndustrialCatalogLoader;
import com.spacesim.economy.Stage18FacilityRuntime;
import com.spacesim.economy.Stage18FacilityRuntime.FacilityCapabilitySnapshot;
import com.spacesim.economy.Stage18FacilityRuntime.InstalledFacilityState;
import com.spacesim.economy.Stage18ShipyardRuntime;
import com.spacesim.economy.Stage18StationIndustrialNode;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipyardEngineeringService;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.SmallCraftHangarCapacity.HostKind;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import com.spacesim.world.SmallCraftPhysicalLogisticsService.DeliveryReceipt;
import com.spacesim.world.SmallCraftPhysicalLogisticsService.LogisticsState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CarrierPostBattleRecoveryServiceTest {

    @Test
    void fundedRecoveryStillRequiresFiniteIndustryAndPhysicalDelivery() {
        Fixture fixture = fixture();
        var plan = fixture.logistics.planBuild(
                "faction.empire",
                ProductionSmallCraftFixture.DESIGN_ID,
                fixture.station.stationId(),
                fixture.fit,
                fixture.yard);
        loadBuildInputs(fixture);
        var budget = fixture.yard.openInterval(
                plan.workPlan().requirements().totalWorkSeconds()
                        / fixture.yard.plannerCapability().workRate() + 5d);

        WalletComponent treasury = new WalletComponent(50_000L);
        WalletComponent procurement = new WalletComponent();
        CarrierPostBattleRecoveryService recovery = new CarrierPostBattleRecoveryService(
                fixture.logistics,
                (faction, destination, ledger, amount) -> treasury.transferTo(destination, amount));

        var funded = recovery.fundAndSettleBuild(
                "faction.empire",
                procurement,
                "station:carrier-yard:procurement",
                12_000L,
                LogisticsState.empty(),
                plan,
                fixture.station.storage(),
                budget);

        assertTrue(funded.treasuryFunded());
        assertTrue(funded.replacementProduced());
        assertEquals(38_000L, treasury.getBalanceMilliCredits());
        assertEquals(12_000L, procurement.getBalanceMilliCredits());
        var craft = funded.build().producedCraftOptional().orElseThrow();
        assertTrue(fixture.hangars.find(craft.id()).isEmpty(),
                "funding and construction cannot teleport the replacement into a carrier");
        assertEquals(craft.id(), funded.logisticsState().pendingDeliveries().get(0).craftId());

        BayDefinition bay = deliveryBay();
        var delivered = recovery.confirmReplacementDelivery(
                funded.logisticsState(), craft.id(), bay, 200L);

        assertTrue(delivered.assigned());
        assertTrue(delivered.logisticsState().pendingDeliveries().isEmpty());
        assertEquals(OccupancyState.SERVICING,
                fixture.hangars.find(craft.id()).orElseThrow().state(),
                "ordinary physical arrival must still enter finite servicing rather than READY");
    }

    @Test
    void deniedTreasuryFundingCannotConsumeIndustryOrAllocateReplacement() {
        Fixture fixture = fixture();
        var plan = fixture.logistics.planBuild(
                "faction.empire",
                ProductionSmallCraftFixture.DESIGN_ID,
                fixture.station.stationId(),
                fixture.fit,
                fixture.yard);
        loadBuildInputs(fixture);
        var storageBefore = fixture.station.storage().snapshot();
        long allocatorBefore = fixture.registry.nextIdValue();
        var budget = fixture.yard.openInterval(
                plan.workPlan().requirements().totalWorkSeconds()
                        / fixture.yard.plannerCapability().workRate() + 5d);
        double workBefore = budget.remainingWorkSeconds();

        CarrierPostBattleRecoveryService recovery = new CarrierPostBattleRecoveryService(
                fixture.logistics,
                (faction, destination, ledger, amount) -> false);
        var denied = recovery.fundAndSettleBuild(
                "faction.empire",
                new WalletComponent(),
                "station:carrier-yard:procurement",
                12_000L,
                LogisticsState.empty(),
                plan,
                fixture.station.storage(),
                budget);

        assertFalse(denied.treasuryFunded());
        assertFalse(denied.replacementProduced());
        assertEquals(storageBefore, fixture.station.storage().snapshot());
        assertEquals(allocatorBefore, fixture.registry.nextIdValue());
        assertEquals(workBefore, budget.remainingWorkSeconds(), 0d);
        assertTrue(denied.logisticsState().pendingDeliveries().isEmpty());
    }

    private static Fixture fixture() {
        ShipEngineeringCatalog engineering = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        var protection = Stage22CorePairProtectionCatalogLoader.project(engineering);
        var industrial = Stage22CorePairShipyardIndustrialCatalogLoader.loadEmpireDefault();
        ShipyardEngineeringService engineeringService =
                new ShipyardEngineeringService(engineering, industrial);
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        Stage18ManufacturingProductRegistry products =
                Stage18ManufacturingProductRegistry.loadDefault()
                        .withEngineeringCatalog(engineering, Provenance.STAGE22_AUTHORED);
        Stage18ShipyardCatalog physicalShipyard = Stage22EmpireShipyardCatalogLoader.loadDefault();
        Stage18ShipyardRuntime shipyardRuntime =
                new Stage18ShipyardRuntime(physicalShipyard, ontology, products);

        StationArchetypeDefinition stationDefinition = new StationArchetypeDefinition(
                "station.infrastructure.m22_8h_recovery",
                "M22.8H carrier recovery station",
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
        Stage18StationIndustrialNode station = Stage18StationIndustrialNode.instantiate(
                "station.m22_8h.carrier-yard",
                "location.orbital_station",
                stationDefinition,
                ontology,
                products);

        Stage18FacilityRuntime facilities =
                new Stage18FacilityRuntime(Stage18FacilityCatalogLoader.loadDefault());
        List<FacilityCapabilitySnapshot> support = new ArrayList<>();
        for (var reference : station.installedFacilities()) {
            support.add(facilities.project(new InstalledFacilityState(
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
                        "yard.instance.m22_8h",
                        "yard.empire_capital_service_v1",
                        1d,
                        3_500_000_000d,
                        22d,
                        1_200,
                        700,
                        true),
                station,
                support);

        InstalledFit fit = InstalledFit.fromDemonstrator(
                engineering.findDemonstratorFit(ProductionSmallCraftFixture.DESIGN_ID));
        SmallCraftRegistry registry =
                SmallCraftRegistry.empty(new SmallCraftFitAuthority(engineering));
        SmallCraftHangarRegistry hangars = SmallCraftHangarRegistry.empty(registry);
        SmallCraftPhysicalLogisticsService logistics = new SmallCraftPhysicalLogisticsService(
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
                        "transport.replacement." + pending.craftId().value(),
                        tick,
                        true));
        return new Fixture(
                registry, hangars, logistics, physicalShipyard, station, yard, fit);
    }

    private static void loadBuildInputs(Fixture fixture) {
        Stage18ShipyardCatalog.HullPhysicalProfile hull =
                fixture.physicalShipyard.findHullProfile(fixture.fit.hullId());
        hull.buildInputsKg().forEach(input ->
                fixture.station.storage().addCommodity(input.commodityId(), input.massKg()));
        for (InstalledModuleDefinition installed : fixture.fit.installedModules()) {
            fixture.station.storage().addProduct(installed.moduleId(), 1);
        }
    }

    private static BayDefinition deliveryBay() {
        return new BayDefinition(
                new BayId("carrier.alpha", "bay.flight"),
                HostKind.SHIP,
                new Dimensions3d(500d, 500d, 500d),
                100_000_000d,
                100_000_000d,
                1d);
    }

    private record Fixture(
            SmallCraftRegistry registry,
            SmallCraftHangarRegistry hangars,
            SmallCraftPhysicalLogisticsService logistics,
            Stage18ShipyardCatalog physicalShipyard,
            Stage18StationIndustrialNode station,
            Stage18ShipyardRuntime.YardCapabilitySnapshot yard,
            InstalledFit fit) { }
}
