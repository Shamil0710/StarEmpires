package com.spacesim.content;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.content.Stage18ManufacturingProductRegistry.Provenance;
import com.spacesim.content.ship.*;
import com.spacesim.economy.*;
import com.spacesim.economy.Stage18FacilityConstructionRuntime.ConstructionOrderSnapshot;
import com.spacesim.economy.Stage18FacilityRuntime.FacilityCapabilitySnapshot;
import com.spacesim.economy.Stage18FacilityRuntime.InstalledFacilityState;
import com.spacesim.economy.Stage18ShipyardRuntime.InstalledYardState;
import com.spacesim.persistence.*;
import com.spacesim.ship.*;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.world.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** B03/B14 integration over paid facility construction and ordinary Stage-21G physical repair. */
final class Stage22CorePairRecoveryProbe {
    private static final EntityId ASSET = new EntityId(226_140L);

    private Stage22CorePairRecoveryProbe() { }

    static Result run(boolean empire, double damageFraction) {
        ShipEngineeringCatalog engineering = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        Stage18ShipyardCatalog yards = empire ? Stage22EmpireShipyardCatalogLoader.loadDefault()
                : Stage22IndustrialUnionShipyardCatalogLoader.loadDefault();
        ShipyardIndustrialCatalog industrial = empire ? Stage22EmpireShipyardIndustrialCatalogLoader.loadDefault()
                : Stage22IndustrialUnionShipyardIndustrialCatalogLoader.loadDefault();
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var products = Stage18ManufacturingProductRegistry.loadDefault()
                .withEngineeringCatalog(engineering, Provenance.STAGE22_AUTHORED);
        var facilityCatalog = Stage18FacilityCatalogLoader.loadDefault();
        var facilityRuntime = new Stage18FacilityRuntime(facilityCatalog);
        var construction = new Stage18FacilityConstructionRuntime(
                Stage18FacilityConstructionCatalogLoader.loadDefault(), facilityCatalog, ontology);
        var yardDefinition = yards.getYards().get(0);
        var yardRuntime = new Stage18ShipyardRuntime(yards, ontology, products);
        var station = Stage18StationIndustrialNode.instantiate("b14.station", "location.orbital_station",
                Stage18StationInfrastructureCatalogLoader.loadDefault()
                        .findArchetype("station.infrastructure.industrial_station"), ontology, products);
        var installedYard = new InstalledYardState("b14.yard", yardDefinition.id(), 1d,
                yardDefinition.ratedIntegrationPowerW(), yardDefinition.ratedEngineeringWorkRate(),
                yardDefinition.laborCapacity(), yardDefinition.automationCapacity(), true);
        List<InstalledFacilityState> facilities = new ArrayList<>();
        for (var reference : station.installedFacilities()) facilities.add(allocatedFacility(reference, facilityCatalog));
        List<FacilityCapabilitySnapshot> projections = facilities.stream().map(facilityRuntime::project).toList();
        boolean initiallyBlocked = !yardRuntime.projectYard(installedYard, station, projections).active();
        double constructionMassKg = 0d;
        double constructionSeconds = 0d;
        List<ConstructionOrderSnapshot> completedOrders = new ArrayList<>();
        for (String required : yardDefinition.requiredSupportFacilityDefinitionIds()) {
            if (station.installedFacilities().stream().anyMatch(row -> row.facilityDefinitionId().equals(required))) continue;
            var order = construction.createOrder("b14.build." + required, "b14.extra." + required,
                    required, station.stationId(), station.locationTag());
            // Declared starting construction kit, consumed by the ordinary delivery authority.
            var kit = new Stage18StationStorage(ontology, products, station.stationId(),
                    station.storage().snapshotCapacityByStorageClassKg(), order.requiredMassByCommodityKg(), Map.of());
            constructionMassKg += order.installedMassKg();
            for (var input : order.requiredMassByCommodityKg().entrySet()) {
                order = construction.deliver(order, kit, input.getKey(), input.getValue()).order();
            }
            var capability = construction.projectCapability("b14.existing_constructors", projections);
            double seconds = order.requiredWorkSeconds() / capability.engineeringWorkRate();
            var built = construction.advanceWork(order, capability.openInterval(seconds + 1d));
            if (built.status() != Stage18FacilityConstructionRuntime.WorkStatus.COMPLETED) {
                throw new AssertionError("Existing facilities cannot construct missing yard support: " + built.status());
            }
            constructionSeconds += seconds;
            completedOrders.add(built.order());
            station = station.withCompletedConstruction(built.order(), construction);
            if (station.withCompletedConstruction(built.order(), construction) != station) {
                throw new AssertionError("Completed facility installation was not idempotent");
            }
            facilities.add(allocatedFacility(built.installedFacility(), facilityCatalog));
            projections = facilities.stream().map(facilityRuntime::project).toList();
        }
        var yard = yardRuntime.projectYard(installedYard, station, projections);
        if (!yard.active()) throw new AssertionError("Paid support did not activate yard: " + yard.status());

        // Existing Stage-18 schema stores completion provenance and the separately allocated facility.
        var checkpoint = new Stage18IndustrialState(Stage18IndustrialState.CURRENT_VERSION,
                Stage18IndustrialContentFingerprint.current(), 0L, List.of(), List.of(station.storage().snapshot()),
                facilities.stream().map(row -> new Stage18IndustrialState.FacilityInstallationSnapshot("b14.station", row)).toList(),
                List.of(new Stage18IndustrialState.YardInstallationSnapshot("b14.station", installedYard)),
                completedOrders, List.of());
        byte[] bytes = Stage18IndustrialStateCodec.encode(checkpoint);
        var decoded = Stage18IndustrialStateCodec.decode(bytes);
        var restoredNode = Stage18StationIndustrialNode.instantiate("b14.station", "location.orbital_station",
                Stage18StationInfrastructureCatalogLoader.loadDefault()
                        .findArchetype("station.infrastructure.industrial_station"), ontology, products);
        for (var order : decoded.constructionOrders()) restoredNode = restoredNode.withCompletedConstruction(order, construction);
        boolean facilityPersistence = Arrays.equals(bytes, Stage18IndustrialStateCodec.encode(decoded))
                && restoredNode.installedFacilities().equals(station.installedFacilities())
                && yard.equals(yardRuntime.projectYard(installedYard, restoredNode,
                        decoded.facilities().stream().map(row -> facilityRuntime.project(row.state())).toList()));

        String fitId = empire ? "fit.empire.destroyer.screen_v1" : "fit.industrial_union.destroyer.line_v1";
        InstalledFit fit = InstalledFit.fromDemonstrator(engineering.findDemonstratorFit(fitId));
        var hull = engineering.findHull(fit.hullId());
        Map<String, Double> compartments = new TreeMap<>();
        hull.compartments().forEach(row -> compartments.put(row.id(), 1d - damageFraction));
        var damage = new ShipDamageRuntime.Snapshot(compartments, new DamageState(Map.of("core_drive", 1d - damageFraction)));
        var consumables = new ConsumableState(100d, 50d, 0d, 0d, List.of());
        var ship = new EngineeringComponent(fit, new ShipEngineeringRuntime(engineering).initialize(fit, consumables,
                damage.moduleDamage()), new ShipInstanceRuntimeState(damage, Map.of(),
                new ShipyardEngineeringService.MaintenanceState(Map.of("core_drive", 73d)),
                WeaponLoadoutState.empty(), new WeaponMountRuntime.RuntimeState(Map.of("weapon_primary", 7d))));
        EngineeringComponent replayShip = roundTrip(ship);
        var beforeRuntime = ship.runtimeState;
        var beforeInstance = ship.instanceState;
        var planner = new ShipyardEngineeringService(engineering, industrial);
        var service = new Stage21GPhysicalRecoveryService(Stage18ShipConsumableCatalogLoader.loadDefault(),
                new Stage18ShipConsumableService(Stage18ShipConsumableCatalogLoader.loadDefault(), engineering),
                new Stage19WarfareSupplyService(products), planner, yardRuntime, engineering);
        var plan = planner.planRepair(ASSET, fit, consumables, damage, yard.plannerCapability());
        double seconds = plan.requirements().totalWorkSeconds() / yard.plannerCapability().workRate();
        var operation = new FleetOrderExecutionService.ServiceOperation(new FleetId(226_140),
                new StarSystemId(1), FleetCommandState.OrderType.REPAIR);
        var failed = service.repair(operation, ASSET, ship, station.storage(), yard, yard.openInterval(seconds + 1d));
        boolean noFreeRepair = !failed.settlement().settled() && beforeRuntime.equals(ship.runtimeState)
                && beforeInstance.equals(ship.instanceState);
        Map<String, Double> repairInputs = new TreeMap<>();
        var hullProfile = yards.findHullProfile(fit.hullId());
        damage.compartmentIntegrityById().forEach((id, integrity) -> hullProfile.findCompartmentRepair(id)
                .inputsAtFullLossKg().forEach(input -> repairInputs.merge(input.commodityId(),
                        input.massKg() * (1d - integrity), Double::sum)));
        String driveId = fit.installedModules().stream().filter(row -> row.mountId().equals("core_drive"))
                .findFirst().orElseThrow().moduleId();
        yards.findModuleProfile(driveId).repairInputsAtFullLossKg().forEach(input ->
                repairInputs.merge(input.commodityId(), input.massKg() * damageFraction, Double::sum));
        var supplied = new Stage18StationStorage(ontology, products, station.stationId(),
                station.storage().snapshotCapacityByStorageClassKg(), repairInputs, Map.of());
        var replaySupply = Stage18StationStorage.restore(ontology, products, supplied.snapshot());
        var rawEngineering = empire ? Stage22EmpireEngineeringCatalogLoader.loadDefault()
                : Stage22IndustrialUnionEngineeringCatalogLoader.loadDefault();
        var incompatibleService = new Stage21GPhysicalRecoveryService(Stage18ShipConsumableCatalogLoader.loadDefault(),
                new Stage18ShipConsumableService(Stage18ShipConsumableCatalogLoader.loadDefault(), rawEngineering),
                new Stage19WarfareSupplyService(products), new ShipyardEngineeringService(rawEngineering, industrial),
                yardRuntime, rawEngineering);
        var incompatibleBudget = yard.openInterval(seconds + 1d);
        double workBefore = incompatibleBudget.remainingWorkSeconds();
        boolean incompatibleRejected = false;
        try {
            incompatibleService.repair(operation, ASSET, ship, supplied, yard, incompatibleBudget);
        } catch (IllegalArgumentException expected) {
            incompatibleRejected = supplied.snapshot().equals(replaySupply.snapshot())
                    && beforeRuntime.equals(ship.runtimeState) && beforeInstance.equals(ship.instanceState)
                    && workBefore == incompatibleBudget.remainingWorkSeconds();
        }
        var shortBudget = yard.openInterval(seconds / 2d);
        var tooSoon = service.repair(operation, ASSET, ship, supplied, yard, shortBudget);
        boolean finiteTime = !tooSoon.settlement().settled() && beforeInstance.equals(ship.instanceState)
                && supplied.snapshot().equals(replaySupply.snapshot());
        var repaired = service.repair(operation, ASSET, ship, supplied, yard, yard.openInterval(seconds + 1d));
        var replay = service.repair(operation, ASSET, replayShip, replaySupply, yard, yard.openInterval(seconds + 1d));
        if (!repaired.settlement().settled()) throw new AssertionError("Paid repair failed: " + repaired.settlement());
        boolean continuation = replay.settlement().equals(repaired.settlement())
                && replayShip.runtimeState.equals(ship.runtimeState) && replayShip.instanceState.equals(ship.instanceState)
                && replaySupply.snapshot().equals(supplied.snapshot());
        boolean continuity = consumables.equals(ship.runtimeState.consumables())
                && beforeRuntime.sharedBusEnergyJ() == ship.runtimeState.sharedBusEnergyJ()
                && beforeInstance.maintenance().equals(ship.instanceState.maintenance())
                && beforeInstance.weaponMountRuntime().equals(ship.instanceState.weaponMountRuntime())
                && ship.instanceState.damage().moduleDamage().isPristine()
                && ship.instanceState.damage().compartmentIntegrityById().values().stream().allMatch(v -> v == 1d);
        double consumedKg = repaired.settlement().consumedCommodityMassKg().values().stream()
                .mapToDouble(Double::doubleValue).sum();
        boolean massClosed = Math.abs(consumedKg - repairInputs.values().stream().mapToDouble(Double::doubleValue).sum()) < 1e-6d
                && supplied.snapshotCommodityMassByIdKg().values().stream().mapToDouble(Double::doubleValue).sum() < 1e-6d;
        return new Result(constructionMassKg, constructionSeconds, seconds, consumedKg,
                initiallyBlocked, facilityPersistence, noFreeRepair, finiteTime, continuation, continuity, massClosed,
                incompatibleRejected);
    }

    private static InstalledFacilityState allocatedFacility(
            Stage18StationIndustrialNode.InstalledFacilityReference reference, Stage18FacilityCatalog catalog) {
        var definition = catalog.findFacility(reference.facilityDefinitionId());
        return new InstalledFacilityState(reference.facilityInstanceId(), definition.id(), 1d,
                definition.ratedProcessPowerW(), definition.ratedProcessPowerW() * definition.heatRejectionWPerProcessW(),
                definition.requiredLaborUnitsAtFullRate(), definition.maintenanceWorkRate(), "location.orbital_station", true);
    }

    private static EngineeringComponent roundTrip(EngineeringComponent ship) {
        var entity = new Entity().add(new EntityIdComponent(ASSET)).add(ship);
        return EntityStateMapper.restore(EntityStateMapper.capture(entity)).getComponent(EngineeringComponent.class);
    }

    record Result(double constructionMassKg, double constructionSeconds, double repairSeconds, double repairMassKg,
            boolean initiallyBlocked, boolean facilityPersistence, boolean noFreeRepair, boolean finiteTime,
            boolean continuation, boolean continuity, boolean massClosed, boolean incompatibleRejected) {
        boolean valid() { return initiallyBlocked && facilityPersistence && noFreeRepair && finiteTime
                && continuation && continuity && massClosed && incompatibleRejected
                && repairSeconds > 0d && repairMassKg > 0d; }
    }

    public static void main(String[] args) {
        for (boolean empire : new boolean[] { true, false }) {
            for (double damage : new double[] { 0.25d, 0.5d, 0.75d }) {
                Result result = run(empire, damage);
                System.out.println((empire ? "empire" : "union") + "|" + damage + "|" + result);
                if (!result.valid()) throw new AssertionError(result);
            }
        }
    }
}
