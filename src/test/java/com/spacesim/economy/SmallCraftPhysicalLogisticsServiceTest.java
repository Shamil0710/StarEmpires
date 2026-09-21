package com.spacesim.economy;

import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ManufacturingProductRegistry.Provenance;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage18ShipyardCatalog;
import com.spacesim.content.Stage22EmpireShipyardCatalogLoader;
import com.spacesim.content.Stage18StationInfrastructureCatalog.StationArchetypeDefinition;
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
import com.spacesim.world.ProductionSmallCraftFixture;
import com.spacesim.world.SmallCraftFitAuthority;
import com.spacesim.world.SmallCraftHangarRegistry;
import com.spacesim.world.SmallCraftId;
import com.spacesim.world.SmallCraftPhysicalLogisticsService;
import com.spacesim.world.SmallCraftRegistry;
import com.spacesim.world.SmallCraftState;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.SmallCraftHangarCapacity.HostKind;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import com.spacesim.world.SmallCraftPhysicalLogisticsService.DeliveryReceipt;
import com.spacesim.world.SmallCraftPhysicalLogisticsService.LogisticsState;
import com.spacesim.world.SmallCraftTurnaroundService.ConsumableTransfer;
import com.spacesim.world.SmallCraftTurnaroundService.TurnaroundPlan;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SmallCraftPhysicalLogisticsServiceTest {
    private static final double EPSILON = 1e-9d;

    @Test
    void failedPhysicalBuildConsumesNothingAndAllocatesNoCraftIdentity() {
        Fixture fixture = fixture((pending, bay, tick) -> new DeliveryReceipt(
                pending.craftId(),
                pending.sourceStationId(),
                bay.id().hostStableId(),
                "transport.test",
                tick,
                true));
        var plan = fixture.service.planBuild(
                "faction.empire",
                ProductionSmallCraftFixture.DESIGN_ID,
                fixture.station.stationId(),
                fixture.fit,
                fixture.yard);
        long allocatorBefore = fixture.registry.nextIdValue();
        var storageBefore = fixture.station.storage().snapshot();
        var budget = fixture.yard.openInterval(
                plan.workPlan().requirements().totalWorkSeconds()
                        / fixture.yard.plannerCapability().workRate()
                        + 1d);
        double workBefore = budget.remainingWorkSeconds();

        var result = fixture.service.settleBuild(
                LogisticsState.empty(),
                plan,
                fixture.station.storage(),
                budget);

        assertFalse(result.settlement().settled());
        assertTrue(result.producedCraftOptional().isEmpty());
        assertEquals(allocatorBefore, fixture.registry.nextIdValue());
        assertEquals(0, fixture.registry.size());
        assertEquals(storageBefore, fixture.station.storage().snapshot());
        assertEquals(workBefore, budget.remainingWorkSeconds(), EPSILON);
        assertTrue(result.logisticsState().pendingDeliveries().isEmpty());
    }

    @Test
    void settledStage18BuildCreatesFreshUnservicedIdentityAndPendingDelivery() {
        Fixture fixture = fixture((pending, bay, tick) -> new DeliveryReceipt(
                pending.craftId(),
                pending.sourceStationId(),
                bay.id().hostStableId(),
                "transport.test",
                tick,
                true));
        SmallCraftId previousReserved = fixture.registry.reserveIdentityForCompletedProduction();

        var plan = fixture.service.planBuild(
                "faction.empire",
                ProductionSmallCraftFixture.DESIGN_ID,
                fixture.station.stationId(),
                fixture.fit,
                fixture.yard);
        loadBuildInputs(fixture);
        Map<String, Double> commodityBefore =
                fixture.station.storage().snapshotCommodityMassByIdKg();
        Map<String, Integer> productsBefore =
                fixture.station.storage().snapshotProductCountById();
        var budget = fixture.yard.openInterval(
                plan.workPlan().requirements().totalWorkSeconds()
                        / fixture.yard.plannerCapability().workRate()
                        + 5d);

        var result = fixture.service.settleBuild(
                LogisticsState.empty(),
                plan,
                fixture.station.storage(),
                budget);

        assertTrue(result.settlement().settled());
        SmallCraftState produced = result.producedCraftOptional().orElseThrow();
        assertEquals(previousReserved.value() + 1L, produced.id().value());
        assertTrue(fixture.registry.find(previousReserved).isEmpty());
        assertTrue(fixture.registry.find(produced.id()).isPresent());
        assertTrue(fixture.hangars.find(produced.id()).isEmpty());
        assertEquals(1, result.logisticsState().pendingDeliveries().size());
        assertEquals(produced.id(),
                result.logisticsState().pendingDeliveries().get(0).craftId());
        assertTrue(produced.runtimeState().consumables().interfaceLoads().isEmpty(),
                "physical construction must not synthesize ammunition or reaction mass");
        assertTrue(produced.instanceState().weaponLoadout().feeds().isEmpty(),
                "new hull has no free ammunition identity before ordinary supply");
        assertTrue(produced.instanceState().shieldStatesByMount().isEmpty(),
                "new hull has no free charged shield reserve before ordinary operation/service");
        assertTrue(result.settlement().consumedCommodityMassKg().values().stream()
                .mapToDouble(Double::doubleValue).sum() > 0d);
        assertEquals(fixture.fit.installedModules().size(),
                result.settlement().consumedProductCount().values().stream()
                        .mapToInt(Integer::intValue).sum());
        assertFalse(commodityBefore.equals(
                fixture.station.storage().snapshotCommodityMassByIdKg()));
        assertFalse(productsBefore.equals(
                fixture.station.storage().snapshotProductCountById()));
    }

    @Test
    void deliveryReceiptIsRequiredAndArrivalEntersServicingNotReady() {
        Fixture fixture = fixture((pending, bay, tick) -> new DeliveryReceipt(
                pending.craftId(),
                pending.sourceStationId(),
                bay.id().hostStableId(),
                "transport.pending",
                tick,
                false));
        var built = buildOne(fixture);
        SmallCraftId craft = built.logisticsState().pendingDeliveries().get(0).craftId();
        BayDefinition bay = deliveryBay();

        var blocked = fixture.service.confirmDelivery(
                built.logisticsState(), craft, bay, 100L);
        assertFalse(blocked.assigned());
        assertTrue(fixture.hangars.find(craft).isEmpty());
        assertEquals(1, blocked.logisticsState().pendingDeliveries().size());

        SmallCraftPhysicalLogisticsService accepting = fixture.serviceWithDelivery(
                (pending, destination, tick) -> new DeliveryReceipt(
                        pending.craftId(),
                        pending.sourceStationId(),
                        destination.id().hostStableId(),
                        "transport.arrived",
                        tick,
                        true));
        var delivered = accepting.confirmDelivery(
                blocked.logisticsState(), craft, bay, 101L);

        assertTrue(delivered.assigned());
        assertTrue(delivered.logisticsState().pendingDeliveries().isEmpty());
        assertEquals(
                OccupancyState.SERVICING,
                fixture.hangars.find(craft).orElseThrow().state());
    }

    @Test
    void mismatchedDeliveryReceiptFailsClosedWithoutBayAssignment() {
        Fixture fixture = fixture((pending, bay, tick) -> new DeliveryReceipt(
                pending.craftId(),
                pending.sourceStationId(),
                "carrier.wrong",
                "transport.invalid",
                tick,
                true));
        var built = buildOne(fixture);
        SmallCraftId craft = built.logisticsState().pendingDeliveries().get(0).craftId();

        assertThrows(IllegalArgumentException.class, () -> fixture.service.confirmDelivery(
                built.logisticsState(), craft, deliveryBay(), 200L));
        assertTrue(fixture.hangars.find(craft).isEmpty());
        assertEquals(1, built.logisticsState().pendingDeliveries().size());
    }

    @Test
    void destinationCapacityIsPreflightedBeforePhysicalDeliveryAuthorityCanCommit() {
        java.util.concurrent.atomic.AtomicBoolean invoked =
                new java.util.concurrent.atomic.AtomicBoolean();
        Fixture fixture = fixture((pending, bay, tick) -> {
            invoked.set(true);
            return new DeliveryReceipt(
                    pending.craftId(),
                    pending.sourceStationId(),
                    bay.id().hostStableId(),
                    "transport.must-not-run",
                    tick,
                    true);
        });
        var built = buildOne(fixture);
        SmallCraftId craft = built.logisticsState().pendingDeliveries().get(0).craftId();
        BayDefinition tooSmall = new BayDefinition(
                new BayId("carrier.alpha", "bay.blocked"),
                HostKind.SHIP,
                new Dimensions3d(1d, 1d, 1d),
                1d,
                1d,
                1d);

        assertThrows(IllegalStateException.class, () -> fixture.service.confirmDelivery(
                built.logisticsState(), craft, tooSmall, 250L));

        assertFalse(invoked.get(),
                "delivery authority must not commit transport before destination capacity preflight");
        assertTrue(fixture.hangars.find(craft).isEmpty());
        assertEquals(1, built.logisticsState().pendingDeliveries().size());
    }

    @Test
    void turnaroundDemandPreservesExactFiniteTransfersAndOrdinaryWorkPlans() {
        Fixture fixture = fixture((pending, bay, tick) -> new DeliveryReceipt(
                pending.craftId(),
                pending.sourceStationId(),
                bay.id().hostStableId(),
                "transport.test",
                tick,
                true));
        SmallCraftId craft = fixture.registry.reserveIdentityForCompletedProduction();
        fixture.registry.registerProducedCraft(ProductionSmallCraftFixture.craft(
                craft, 1L, 100d, 500d, 0.6d, 200_000d));
        ConsumableTransfer reactionMass = new ConsumableTransfer(
                "core_drive", "propellant_feed",
                ShipEngineeringCatalog.InterfaceKind.REACTION_MASS,
                1_000d, 1_000d, 0L);
        TurnaroundPlan plan = new TurnaroundPlan(
                craft,
                new BayId("carrier.alpha", "bay.flight"),
                fixture.registry.find(craft).orElseThrow(),
                fixture.registry.find(craft).orElseThrow(),
                List.of(reactionMass),
                25d,
                null,
                null);

        var demand = fixture.service.projectTurnaroundDemand(plan);

        assertEquals(craft, demand.craftId());
        assertEquals(List.of(reactionMass), demand.consumableTransfers());
        assertEquals(25d, demand.handlingWorkSeconds(), 0d);
        assertFalse(demand.repairRequired());
        assertFalse(demand.maintenanceRequired());
    }

    private static SmallCraftPhysicalLogisticsService.BuildResult buildOne(Fixture fixture) {
        var plan = fixture.service.planBuild(
                "faction.empire",
                ProductionSmallCraftFixture.DESIGN_ID,
                fixture.station.stationId(),
                fixture.fit,
                fixture.yard);
        loadBuildInputs(fixture);
        var budget = fixture.yard.openInterval(
                plan.workPlan().requirements().totalWorkSeconds()
                        / fixture.yard.plannerCapability().workRate()
                        + 5d);
        return fixture.service.settleBuild(
                LogisticsState.empty(),
                plan,
                fixture.station.storage(),
                budget);
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

    private static Fixture fixture(
            SmallCraftPhysicalLogisticsService.PhysicalDeliveryAuthority deliveryAuthority) {
        ShipEngineeringCatalog engineering = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        var protection = Stage22CorePairProtectionCatalogLoader.project(engineering);
        var industrial = Stage22CorePairShipyardIndustrialCatalogLoader.loadEmpireDefault();
        ShipyardEngineeringService engineeringService =
                new ShipyardEngineeringService(engineering, industrial);
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        Stage18ManufacturingProductRegistry products =
                Stage18ManufacturingProductRegistry.loadDefault()
                        .withEngineeringCatalog(engineering, Provenance.STAGE22_AUTHORED);
        Stage18ShipyardCatalog physicalShipyard =
                Stage22EmpireShipyardCatalogLoader.loadDefault();
        Stage18ShipyardRuntime runtime =
                new Stage18ShipyardRuntime(physicalShipyard, ontology, products);

        StationArchetypeDefinition stationDefinition = new StationArchetypeDefinition(
                "station.infrastructure.m22_8g_test",
                "M22.8G physical production acceptance station",
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
                "station.m22_8g.shipyard",
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
        var yard = runtime.projectYard(
                new Stage18ShipyardRuntime.InstalledYardState(
                        "yard.instance.m22_8g",
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

        InstalledFit fit = InstalledFit.fromDemonstrator(
                engineering.findDemonstratorFit(ProductionSmallCraftFixture.DESIGN_ID));
        SmallCraftRegistry registry =
                SmallCraftRegistry.empty(new SmallCraftFitAuthority(engineering));
        SmallCraftHangarRegistry hangars = SmallCraftHangarRegistry.empty(registry);
        SmallCraftPhysicalLogisticsService service =
                new SmallCraftPhysicalLogisticsService(
                        registry,
                        hangars,
                        engineering,
                        protection,
                        engineeringService,
                        runtime,
                        deliveryAuthority);
        return new Fixture(
                registry,
                hangars,
                service,
                engineering,
                protection,
                engineeringService,
                runtime,
                physicalShipyard,
                station,
                yard,
                fit);
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
            SmallCraftPhysicalLogisticsService service,
            ShipEngineeringCatalog engineering,
            com.spacesim.content.ship.ShipProtectionCatalog protection,
            ShipyardEngineeringService engineeringService,
            Stage18ShipyardRuntime shipyardRuntime,
            Stage18ShipyardCatalog physicalShipyard,
            Stage18StationIndustrialNode station,
            Stage18ShipyardRuntime.YardCapabilitySnapshot yard,
            InstalledFit fit) {
        SmallCraftPhysicalLogisticsService serviceWithDelivery(
                SmallCraftPhysicalLogisticsService.PhysicalDeliveryAuthority delivery) {
            return new SmallCraftPhysicalLogisticsService(
                    registry,
                    hangars,
                    engineering,
                    protection,
                    engineeringService,
                    shipyardRuntime,
                    delivery);
        }
    }
}
