package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage18ShipConsumableCatalogLoader;
import com.spacesim.content.Stage18ShipyardCatalog;
import com.spacesim.content.Stage18ShipyardCatalogLoader;
import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.content.ship.ShipyardIndustrialCatalogLoader;
import com.spacesim.economy.Stage18FacilityRuntime;
import com.spacesim.economy.Stage18FacilityRuntime.FacilityCapabilitySnapshot;
import com.spacesim.economy.Stage18FacilityRuntime.InstalledFacilityState;
import com.spacesim.economy.Stage18ShipConsumableService;
import com.spacesim.economy.Stage18ShipyardRuntime;
import com.spacesim.economy.Stage18StationIndustrialNode;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.economy.Stage19WarfareSupplyService;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityState;
import com.spacesim.ship.ShipDamageRuntime;
import com.spacesim.ship.ShipEngineeringRuntime;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipInstanceRuntimeState;
import com.spacesim.ship.ShipyardEngineeringService;
import com.spacesim.ship.ShipyardEngineeringService.MaintenanceState;
import com.spacesim.ship.WeaponDefinition.Launcher;
import com.spacesim.ship.WeaponLoadoutState;
import com.spacesim.ship.WeaponMountRuntime;
import com.spacesim.world.FleetCommandState.OrderType;
import com.spacesim.world.FleetOrderExecutionService.ServiceOperation;
import com.spacesim.world.SettlementRecoveryState.FleetLossRecord;
import com.spacesim.world.SettlementRecoveryState.ReplacementDemand;
import com.spacesim.world.SettlementRecoveryState.ReplacementStatus;
import com.spacesim.world.SettlementRecoveryState.Settlement;
import com.spacesim.world.SettlementRecoveryState.SettlementStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage21GPhysicalRecoveryServiceTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";
    private static final String MINERS = "faction.miners";
    private static final String AMMO_ID = "ammo.rail_dart_150kg_v1";

    private Stage18ResourceOntologyCatalog ontology;
    private Stage18ManufacturingProductRegistry products;
    private ShipEngineeringCatalog engineering;
    private Stage18ShipyardCatalog shipyardCatalog;
    private ShipyardEngineeringService engineeringService;
    private Stage18ShipyardRuntime shipyardRuntime;
    private Stage18StationIndustrialNode industrialStation;
    private Stage18ShipyardRuntime.YardCapabilitySnapshot yard;
    private InstalledFit fit;
    private Stage21GPhysicalRecoveryService service;

    @BeforeEach
    void setUp() {
        ontology = Stage18ResourceOntologyLoader.loadDefault();
        products = Stage18ManufacturingProductRegistry.loadDefault();
        engineering = ShipEngineeringCatalogLoader.loadDefault();
        shipyardCatalog = Stage18ShipyardCatalogLoader.loadDefault();
        engineeringService = new ShipyardEngineeringService(
                engineering, ShipyardIndustrialCatalogLoader.loadDefault(engineering));
        shipyardRuntime = new Stage18ShipyardRuntime(shipyardCatalog, ontology, products);
        fit = InstalledFit.fromDemonstrator(
                engineering.findDemonstratorFit("fit.escort_destroyer_schema_v1"));
        service = new Stage21GPhysicalRecoveryService(
                Stage18ShipConsumableCatalogLoader.loadDefault(),
                new Stage18ShipConsumableService(Stage18ShipConsumableCatalogLoader.loadDefault(), engineering),
                new Stage19WarfareSupplyService(products),
                engineeringService,
                shipyardRuntime,
                engineering);

        var infrastructure = Stage18StationInfrastructureCatalogLoader.loadDefault()
                .findArchetype("station.infrastructure.industrial_station");
        industrialStation = Stage18StationIndustrialNode.instantiate(
                "station.stage21g.shipyard",
                "location.orbital_station",
                infrastructure,
                ontology,
                products);
        Stage18FacilityRuntime facilityRuntime = new Stage18FacilityRuntime(
                Stage18FacilityCatalogLoader.loadDefault());
        yard = shipyardRuntime.projectYard(
                installedYard(),
                industrialStation,
                activeSupportFacilities(industrialStation, facilityRuntime));
        assertTrue(yard.active());
    }

    @Test
    void refuelConsumesPhysicalWaterAndMutatesOnlyExistingShipConsumables() {
        EngineeringComponent ship = engineeringComponent(pristineDamage(), ConsumableState.empty());
        double storedEnergyBefore = ship.runtimeState.sharedBusEnergyJ();
        Stage18StationStorage storage = new Stage18StationStorage(
                ontology,
                products,
                "station.stage21g.refuel",
                Map.of("storage.liquid_tank", 100_000d),
                Map.of("commodity.material.purified_water", 50_000d),
                Map.of());
        ServiceOperation operation = new ServiceOperation(
                new FleetId(1L), new StarSystemId(1L), OrderType.REFUEL);

        var result = service.refuel(
                operation,
                "ship_consumable.reaction_mass.escort_water_v1",
                "core_drive",
                20_000d,
                ship,
                storage);

        assertTrue(result.committed());
        assertEquals(30_000d, storage.commodityMassKg("commodity.material.purified_water"), 0d);
        assertEquals(20_000d, ship.runtimeState.consumables().reactionMassKg(), 0d);
        assertEquals(storedEnergyBefore, ship.runtimeState.sharedBusEnergyJ(), 0d);
        assertTrue(ship.instanceState.damage().moduleDamage().isPristine());
        assertThrows(IllegalArgumentException.class, () -> service.refuel(
                new ServiceOperation(new FleetId(1L), new StarSystemId(1L), OrderType.REARM),
                "ship_consumable.reaction_mass.escort_water_v1",
                "core_drive",
                1d,
                ship,
                storage));
    }

    @Test
    void rearmConsumesManufacturedRoundsAndPersistsFeedIdentityWithoutMixing() {
        EngineeringComponent ship = engineeringComponent(pristineDamage(), ConsumableState.empty());
        Stage18StationStorage storage = new Stage18StationStorage(
                ontology,
                products,
                "station.stage21g.rearm",
                Map.of("storage.hazardous_controlled", 100_000d),
                Map.of(),
                Map.of(AMMO_ID, 4));
        ServiceOperation operation = new ServiceOperation(
                new FleetId(2L), new StarSystemId(1L), OrderType.REARM);
        Launcher launcher = new Launcher(
                "launcher.stage21g.rail", "kinetic_magazine_feed", 1d, 1d, 1);

        var result = service.rearm(operation, AMMO_ID, "weapon_spinal", 3, launcher, ship, storage);

        assertTrue(result.committed());
        assertEquals(1, storage.productCount(AMMO_ID));
        assertEquals(3L, ship.runtimeState.consumables().ammunitionCount());
        assertEquals(AMMO_ID, ship.instanceState.weaponLoadout()
                .ammunitionContentId("weapon_spinal", "kinetic_magazine_feed").orElseThrow());
        assertThrows(IllegalStateException.class, () -> service.rearm(
                operation, "ammo.incompatible", "weapon_spinal", 1, launcher, ship, storage));
        assertEquals(1, storage.productCount(AMMO_ID));
        assertThrows(IllegalArgumentException.class, () -> service.rearm(
                operation, AMMO_ID, "utility_sensor", 1, launcher, ship, storage));
    }

    @Test
    void repairConsumesFiniteYardInputsAndPreservesAllNonRepairContinuity() {
        ShipDamageRuntime.Snapshot damaged = damagedSnapshot(Map.of("core_drive", 0.5d), 0.5d);
        ConsumableState carried = new ConsumableState(100d, 50d, 0d, 0d, List.of());
        EngineeringComponent ship = engineeringComponent(damaged, carried);
        var beforeRuntime = ship.runtimeState;
        var beforeMaintenance = ship.instanceState.maintenance();
        var beforeWeaponRuntime = ship.instanceState.weaponMountRuntime();
        EntityId assetId = new EntityId(21_001L);
        loadRepairInputs(industrialStation.storage(), fit, damaged);
        var plan = engineeringService.planRepair(
                assetId, fit, carried, damaged, yard.plannerCapability());
        var budget = yard.openInterval(
                plan.requirements().totalWorkSeconds() / yard.plannerCapability().workRate() + 1d);
        ServiceOperation operation = new ServiceOperation(
                new FleetId(3L), new StarSystemId(1L), OrderType.REPAIR);

        var result = service.repair(
                operation, assetId, ship, industrialStation.storage(), yard, budget);

        assertTrue(result.settlement().settled());
        assertEquals(assetId, result.completion().assetId());
        assertTrue(ship.instanceState.damage().moduleDamage().isPristine());
        assertTrue(ship.instanceState.damage().compartmentIntegrityById().values().stream()
                .allMatch(value -> value == 1d));
        assertEquals(carried, ship.runtimeState.consumables());
        assertEquals(beforeRuntime.sharedBusEnergyJ(), ship.runtimeState.sharedBusEnergyJ(), 0d);
        assertEquals(beforeMaintenance, ship.instanceState.maintenance());
        assertEquals(beforeWeaponRuntime, ship.instanceState.weaponMountRuntime());
        assertTrue(result.settlement().consumedCommodityMassKg().values().stream()
                .mapToDouble(Double::doubleValue).sum() > 0d);
    }

    @Test
    void replacementNeedsOrdinaryYardSettlementThenReturnsFreshEmptyFleet() {
        WorldSimulation world = DemoGalaxyFactory.create(21_790L);
        long now = world.getAuthoritativeWorldTick();
        FleetId lostFleet = new FleetId(9_999_999L);
        Settlement settlement = new Settlement(
                1L, "proposal.stage21g.build", "war.stage21g.build",
                TRADE_LEAGUE, MINERS, now, now, SettlementStatus.EXECUTING, false);
        FleetLossRecord loss = new FleetLossRecord(1L, 1L, lostFleet, TRADE_LEAGUE, now);
        ReplacementDemand demand = new ReplacementDemand(
                1L, 1L, lostFleet, TRADE_LEAGUE,
                SettlementRecoveryService.fitFingerprint(fit),
                now, now, ReplacementStatus.DEMANDED, null, 0L, null);
        SettlementRecoveryService recovery = new SettlementRecoveryService(new SettlementRecoveryState(
                SettlementRecoveryState.CURRENT_VERSION,
                now,
                2L,
                2L,
                List.of(settlement),
                List.of(),
                List.of(),
                List.of(loss),
                List.of(demand)));
        FactionIdentityResolver identities = FactionIdentityResolver.createDefault(
                ContentCatalogLoader.loadDefault(), world.snapshot().factionIdentities());
        StarSystemId buildSystem = world.getActiveSystemId();
        var plan = engineeringService.planBuild(fit, yard.plannerCapability());
        int fleetsBefore = world.getFleetPlacements().size();
        var failedBudget = yard.openInterval(
                plan.requirements().totalWorkSeconds() / yard.plannerCapability().workRate() + 1d);

        var failed = service.buildReplacement(
                recovery, 1L, world, identities, buildSystem, "Replacement Test",
                50f, -25f, fit, industrialStation.storage(), yard, failedBudget, now);

        assertFalse(failed.settlement().settled());
        assertEquals(fleetsBefore, world.getFleetPlacements().size());
        assertEquals(ReplacementStatus.DEMANDED,
                recovery.snapshot().requireReplacementDemand(1L).status());

        loadBuildInputs(industrialStation.storage(), fit);
        var successBudget = yard.openInterval(
                plan.requirements().totalWorkSeconds() / yard.plannerCapability().workRate() + 1d);
        var built = service.buildReplacement(
                recovery, 1L, world, identities, buildSystem, "Replacement Test",
                50f, -25f, fit, industrialStation.storage(), yard, successBudget, now);

        assertTrue(built.settlement().settled());
        assertNotNull(built.commissionedFleetId());
        assertNotEquals(lostFleet, built.commissionedFleetId());
        assertEquals(fleetsBefore + 1, world.getFleetPlacements().size());
        assertEquals(ReplacementStatus.COMMISSIONED,
                recovery.snapshot().requireReplacementDemand(1L).status());
        FleetPlacementState placement = world.findFleet(built.commissionedFleetId()).orElseThrow();
        assertEquals(buildSystem, placement.systemId());
        EntityState persisted = world.snapshot().systems().stream()
                .filter(system -> system.systemId().equals(buildSystem))
                .flatMap(system -> system.simulationState().entities().stream())
                .filter(entity -> entity.id().equals(placement.localEntityId()))
                .findFirst().orElseThrow();
        assertNotNull(persisted.engineering());
        assertEquals(fit.hullId(), persisted.engineering().hullId());
        assertTrue(persisted.engineering().consumables().interfaceLoads().isEmpty());
        assertEquals(0d, persisted.engineering().sharedBusEnergyJ(), 0d);
        assertTrue(persisted.engineering().instanceState().shieldsByMount().stream()
                .allMatch(shield -> shield.reserveJ() == 0d));
        assertTrue(persisted.engineering().instanceState().weaponFeeds().isEmpty());
    }

    private EngineeringComponent engineeringComponent(
            ShipDamageRuntime.Snapshot damage,
            ConsumableState consumables) {
        ShipEngineeringRuntime runtime = new ShipEngineeringRuntime(engineering);
        var operating = runtime.initialize(fit, consumables, damage.moduleDamage());
        return new EngineeringComponent(
                fit,
                operating,
                new ShipInstanceRuntimeState(
                        damage,
                        Map.of(),
                        new MaintenanceState(Map.of("core_drive", 123d)),
                        WeaponLoadoutState.empty(),
                        new WeaponMountRuntime.RuntimeState(Map.of("weapon_spinal", 7d))));
    }

    private Stage18ShipyardRuntime.InstalledYardState installedYard() {
        return new Stage18ShipyardRuntime.InstalledYardState(
                "yard.instance.stage21g",
                "yard.orbital_escort_v1",
                1d,
                1_200_000_000d,
                12d,
                500,
                500,
                true);
    }

    private static List<FacilityCapabilitySnapshot> activeSupportFacilities(
            Stage18StationIndustrialNode node,
            Stage18FacilityRuntime facilityRuntime) {
        List<FacilityCapabilitySnapshot> result = new ArrayList<>();
        for (var reference : node.installedFacilities()) {
            if (reference.facilityDefinitionId().equals("facility.fabrication.heavy")) {
                result.add(facilityRuntime.project(new InstalledFacilityState(
                        reference.facilityInstanceId(), reference.facilityDefinitionId(),
                        1d, 80_000_000d, 44_000_000d, 80d, 4d,
                        node.locationTag(), true)));
            } else if (reference.facilityDefinitionId().equals("facility.fabrication.assembly")) {
                result.add(facilityRuntime.project(new InstalledFacilityState(
                        reference.facilityInstanceId(), reference.facilityDefinitionId(),
                        1d, 200_000_000d, 120_000_000d, 150d, 6d,
                        node.locationTag(), true)));
            }
        }
        return result;
    }

    private void loadBuildInputs(Stage18StationStorage storage, InstalledFit targetFit) {
        var hull = shipyardCatalog.findHullProfile(targetFit.hullId());
        hull.buildInputsKg().forEach(input -> storage.addCommodity(input.commodityId(), input.massKg()));
        targetFit.installedModules().forEach(assignment -> storage.addProduct(assignment.moduleId(), 1));
    }

    private void loadRepairInputs(
            Stage18StationStorage storage,
            InstalledFit targetFit,
            ShipDamageRuntime.Snapshot damage) {
        Map<String, Double> required = new LinkedHashMap<>();
        var hull = shipyardCatalog.findHullProfile(targetFit.hullId());
        for (Map.Entry<String, Double> entry : damage.compartmentIntegrityById().entrySet()) {
            double loss = 1d - entry.getValue();
            if (loss <= 0d) continue;
            var profile = hull.findCompartmentRepair(entry.getKey());
            profile.inputsAtFullLossKg().forEach(input ->
                    required.merge(input.commodityId(), input.massKg() * loss, Double::sum));
        }
        Map<String, String> moduleByMount = new LinkedHashMap<>();
        targetFit.installedModules().forEach(value -> moduleByMount.put(value.mountId(), value.moduleId()));
        for (Map.Entry<String, Double> entry : damage.moduleDamage().moduleIntegrityByMount().entrySet()) {
            double loss = 1d - entry.getValue();
            String moduleId = moduleByMount.get(entry.getKey());
            if (loss <= 0d || moduleId == null) continue;
            shipyardCatalog.findModuleProfile(moduleId).repairInputsAtFullLossKg().forEach(input ->
                    required.merge(input.commodityId(), input.massKg() * loss, Double::sum));
        }
        required.forEach(storage::addCommodity);
    }

    private static ShipDamageRuntime.Snapshot pristineDamage() {
        return damagedSnapshot(Map.of(), 1d);
    }

    private static ShipDamageRuntime.Snapshot damagedSnapshot(
            Map<String, Double> moduleIntegrity,
            double engineeringIntegrity) {
        Map<String, Double> compartments = new LinkedHashMap<>();
        compartments.put("engineering", engineeringIntegrity);
        compartments.put("mission_core", 1d);
        compartments.put("weapons", 1d);
        return new ShipDamageRuntime.Snapshot(compartments, new DamageState(moduleIntegrity));
    }
}
