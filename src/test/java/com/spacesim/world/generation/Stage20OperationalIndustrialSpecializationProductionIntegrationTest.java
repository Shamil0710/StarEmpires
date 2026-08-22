package com.spacesim.world.generation;

import com.spacesim.content.Stage18FacilityCatalog.FacilityDefinition;
import com.spacesim.content.Stage18ShipyardCatalog.YardDefinition;
import com.spacesim.content.Stage18ShipyardCatalogLoader;
import com.spacesim.economy.Stage18FacilityRuntime.InstalledFacilityState;
import com.spacesim.economy.Stage18ShipyardRuntime.InstalledYardState;
import com.spacesim.economy.Stage18StationStorage.StationStorageSnapshot;
import com.spacesim.world.Stage20BootstrapFreightOwnershipPlan;
import com.spacesim.world.Stage20BootstrapProductionCapacityCalculator.ProcessKind;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.FacilitySlotKey;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.FacilityStateAssignment;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.OperatingReport;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.OperatingStateAuthority;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.StationKey;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.StationServiceAllocation;
import com.spacesim.world.Stage20IndustrialInitialInventoryPlan;
import com.spacesim.world.Stage20IndustrialInitialInventoryPlan.InitialInventoryAuthority;
import com.spacesim.world.Stage20IndustrialInitialInventoryPlan.InventoryReport;
import com.spacesim.world.Stage20IndustrialInitialInventoryPlan.StationInventoryAssignment;
import com.spacesim.world.Stage20IndustrialInputFreightOwnershipPlan;
import com.spacesim.world.Stage20IndustrialInputFreightOwnershipPlan.IndustrialFreightReport;
import com.spacesim.world.Stage20IndustrialInputFreightOwnershipPlan.ProcessOwnerAssignment;
import com.spacesim.world.Stage20IndustrialInputFreightOwnershipPlan.ProcessOwnershipAuthority;
import com.spacesim.world.Stage20IndustrialInputReservationPlan;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.ProcessOutputRequest;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.ProcessSelectionKey;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.SelectionAuthority;
import com.spacesim.world.Stage20IndustrialInputRouteEvidencePlan;
import com.spacesim.world.Stage20IndustrialShipyardInstallationPlan;
import com.spacesim.world.Stage20IndustrialShipyardInstallationPlan.InstalledYardAssignment;
import com.spacesim.world.Stage20IndustrialShipyardInstallationPlan.ShipyardInstallationAuthority;
import com.spacesim.world.Stage20IndustrialShipyardInstallationPlan.StationYardAuthority;
import com.spacesim.world.Stage20IndustrialShipyardInstallationPlan.YardReport;
import com.spacesim.world.Stage20IndustrialShipyardInstallationPlan.YardSlotKey;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.CandidateReport;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.MissingAuthority;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.StationCandidate;
import com.spacesim.world.Stage20OperationalIndustrialSpecializationPlan;
import com.spacesim.world.Stage20OperationalIndustrialSpecializationPlan.IndustrialRole;
import com.spacesim.world.Stage20OperationalIndustrialSpecializationPlan.RuntimeBridgeRequirement;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.RouteAdmissionStatus;
import com.spacesim.world.generation.Stage20ResolvedGeneratedWorldProductionProbe.ResolvedProbeResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20OperationalIndustrialSpecializationProductionIntegrationTest {
    @Test
    void realIndustrialStationProjectsActiveYardAndFinalPhysicalRoles() {
        Fixture fixture = fixture();
        ShipyardInstallationAuthority authority = yardAuthority(fixture, YardVariant.ACTIVE);

        YardReport yards = Stage20IndustrialShipyardInstallationPlan.plan(
                fixture.resolved(), fixture.inventory(), authority);
        YardReport repeatedYards = Stage20IndustrialShipyardInstallationPlan.plan(
                fixture.resolved(), fixture.inventory(), authority);
        var specialization = Stage20OperationalIndustrialSpecializationPlan.derive(
                fixture.resolved(), yards);
        var repeatedSpecialization = Stage20OperationalIndustrialSpecializationPlan.derive(
                fixture.resolved(), yards);

        assertEquals(Stage20IndustrialShipyardInstallationPlan.Status.ACCEPTED, yards.status());
        assertEquals(yards, repeatedYards);
        assertTrue(yards.installedYardsAuthoritative());
        assertTrue(yards.operationallyAuthoritative());
        assertEquals(1, yards.activeYardCount());
        assertTrue(yards.missingAuthorities().isEmpty());
        assertEquals(specialization, repeatedSpecialization);
        assertTrue(specialization.operationallyAuthoritative());
        assertTrue(specialization.readyForRuntimeBridge());
        assertEquals(4, specialization.runtimeBridgeRequirements().size());
        assertTrue(specialization.runtimeBridgeRequirements().contains(
                RuntimeBridgeRequirement.SOURCE_SUPPLY_MATERIALIZATION));
        assertTrue(specialization.runtimeBridgeRequirements().contains(
                RuntimeBridgeRequirement.FREIGHT_FLEET_MATERIALIZATION));
        assertTrue(specialization.runtimeBridgeRequirements().contains(
                RuntimeBridgeRequirement.CARGO_ORDER_AND_LOT_MATERIALIZATION));
        assertTrue(specialization.runtimeBridgeRequirements().contains(
                RuntimeBridgeRequirement.INDUSTRIAL_ENTITY_MATERIALIZATION));
        assertEquals(1, specialization.activeYardCount());
        assertTrue(specialization.totalSelectedOutputKgPerSecond() > 0d);
        assertEquals(1, specialization.specializations().size());
        assertTrue(specialization.specializations().get(0).roles().contains(
                IndustrialRole.REFINING));
        assertTrue(specialization.specializations().get(0).roles().contains(
                IndustrialRole.SHIPBUILDING));
        assertFalse(specialization.specializations().get(0).roles().contains(
                IndustrialRole.COMPONENT_MANUFACTURING));
    }

    @Test
    void explicitEmptyYardAuthorityClosesAbsenceWithoutGrantingShipbuilding() {
        Fixture fixture = fixture();
        ShipyardInstallationAuthority authority = yardAuthority(fixture, YardVariant.EMPTY);

        YardReport yards = Stage20IndustrialShipyardInstallationPlan.plan(
                fixture.resolved(), fixture.inventory(), authority);
        var specialization = Stage20OperationalIndustrialSpecializationPlan.derive(
                fixture.resolved(), yards);

        assertEquals(Stage20IndustrialShipyardInstallationPlan.Status.ACCEPTED, yards.status());
        assertTrue(yards.operationallyAuthoritative());
        assertEquals(0, yards.activeYardCount());
        assertEquals(0, specialization.activeYardCount());
        assertTrue(specialization.specializations().get(0).roles().contains(
                IndustrialRole.REFINING));
        assertFalse(specialization.specializations().get(0).roles().contains(
                IndustrialRole.SHIPBUILDING));
    }

    @Test
    void disabledSupportAndSharedPowerOverclaimRejectWithoutClosingYards() {
        Fixture fixture = fixture();

        YardReport disabled = Stage20IndustrialShipyardInstallationPlan.plan(
                fixture.resolved(),
                fixture.inventory(),
                yardAuthority(fixture, YardVariant.DISABLED_SUPPORT));
        YardReport overclaim = Stage20IndustrialShipyardInstallationPlan.plan(
                fixture.resolved(),
                fixture.inventory(),
                yardAuthority(fixture, YardVariant.POWER_OVERCLAIM));

        assertEquals(
                Stage20IndustrialShipyardInstallationPlan.Status
                        .INSUFFICIENT_INSTALLED_YARD_CAPABILITY,
                disabled.status());
        assertEquals(
                Stage20IndustrialShipyardInstallationPlan.Status
                        .INSUFFICIENT_INSTALLED_YARD_CAPABILITY,
                overclaim.status());
        assertTrue(disabled.missingAuthorities().contains(MissingAuthority.INSTALLED_SHIPYARDS));
        assertTrue(overclaim.missingAuthorities().contains(MissingAuthority.INSTALLED_SHIPYARDS));
        assertFalse(disabled.installedYardsAuthoritative());
        assertFalse(overclaim.installedYardsAuthoritative());
        assertThrows(IllegalArgumentException.class, () ->
                Stage20OperationalIndustrialSpecializationPlan.derive(
                        fixture.resolved(), overclaim));
    }

    @Test
    void nonCanonicalYardIdentityAndOwnerMismatchFailClosed() {
        Fixture fixture = fixture();

        assertThrows(IllegalArgumentException.class, () ->
                Stage20IndustrialShipyardInstallationPlan.plan(
                        fixture.resolved(),
                        fixture.inventory(),
                        yardAuthority(fixture, YardVariant.NONCANONICAL_ID)));
        assertThrows(IllegalArgumentException.class, () ->
                Stage20IndustrialShipyardInstallationPlan.plan(
                        fixture.resolved(),
                        fixture.inventory(),
                        yardAuthority(fixture, YardVariant.OWNER_MISMATCH)));
    }

    private static Fixture fixture() {
        ResolvedProbeResult resolved = Stage20ResolvedGeneratedWorldProductionProbe.runCurrent(1L);
        CandidateReport candidates = Stage20IndustrialSpecializationCandidatePlan.reconstruct(resolved);
        var routes = Stage20IndustrialInputRouteEvidencePlan.reconstruct(resolved);
        var selected = routes.processes().stream()
                .filter(value -> value.candidate().capacity().processKind() == ProcessKind.REFINING)
                .filter(value -> value.candidate().throughput().inputLimitedOutputKgPerSecond() > 0d)
                .filter(value -> value.inputs().size() == 1)
                .filter(value -> stationFor(candidates, ProcessSelectionKey.from(value.candidate()))
                        .facilitySlots().stream().map(slot -> slot.definition().id()).toList()
                        .containsAll(List.of(
                                "facility.fabrication.heavy",
                                "facility.fabrication.assembly")))
                .filter(value -> value.inputs().get(0).supplyRoutes().stream()
                        .filter(route -> route.status() == RouteAdmissionStatus.ADMITTED)
                        .noneMatch(route -> route.supplyKey().systemId().equals(
                                value.candidate().capacity().systemId())))
                .filter(value -> value.inputs().get(0).supplyRoutes().stream()
                        .anyMatch(route -> route.status() == RouteAdmissionStatus.ADMITTED))
                .findFirst()
                .orElseThrow();
        ProcessSelectionKey process = ProcessSelectionKey.from(selected.candidate());
        StationCandidate station = stationFor(candidates, process);
        double output = selected.candidate().throughput()
                .inputLimitedOutputKgPerSecond() * 0.0001d;
        var reservation = Stage20IndustrialInputReservationPlan.reserve(
                resolved,
                new SelectionAuthority(
                        "selection.production-seed-1.final-stage20f.test.v1",
                        resolved.rootSeed(),
                        List.of(new ProcessOutputRequest(process, output))));
        var bootstrapOwnership = Stage20BootstrapFreightOwnershipPlan.plan(resolved);
        var owner = bootstrapOwnership.factions().stream()
                .filter(value -> value.reserveFreighterCount() > 0)
                .max(java.util.Comparator.comparingInt(value -> value.reserveFreighterCount()))
                .orElseThrow();
        IndustrialFreightReport freight = Stage20IndustrialInputFreightOwnershipPlan.planCurrent(
                resolved,
                reservation,
                new ProcessOwnershipAuthority(
                        "process-owners.production-seed-1.final-stage20f.test.v1",
                        resolved.rootSeed(),
                        List.of(new ProcessOwnerAssignment(process, owner.stableFactionId()))));

        var selectedSlot = station.facilitySlots().stream()
                .filter(value -> value.definition().id().equals(process.facilityDefinitionId()))
                .findFirst()
                .orElseThrow();
        var heavySlot = station.facilitySlots().stream()
                .filter(value -> value.definition().id().equals("facility.fabrication.heavy"))
                .findFirst()
                .orElseThrow();
        var assemblySlot = station.facilitySlots().stream()
                .filter(value -> value.definition().id().equals("facility.fabrication.assembly"))
                .findFirst()
                .orElseThrow();
        StationKey stationKey = new StationKey(process.systemId(), process.stationPlacementId());
        InstalledFacilityState selectedState = fullState(
                station, selectedSlot.facilityOrdinal(), selectedSlot.definition(), true);
        InstalledFacilityState heavyState = fullState(
                station, heavySlot.facilityOrdinal(), heavySlot.definition(), true);
        InstalledFacilityState assemblyState = fullState(
                station, assemblySlot.facilityOrdinal(), assemblySlot.definition(), true);
        YardDefinition yardDefinition = Stage18ShipyardCatalogLoader.loadDefault()
                .findYard("yard.orbital_escort_v1");
        double servicePower = selectedState.allocatedProcessPowerW()
                + heavyState.allocatedProcessPowerW()
                + assemblyState.allocatedProcessPowerW()
                + yardDefinition.ratedIntegrationPowerW();
        double serviceHeat = selectedState.availableHeatRejectionW()
                + heavyState.availableHeatRejectionW()
                + assemblyState.availableHeatRejectionW();
        double serviceLabor = selectedState.availableLaborUnits()
                + heavyState.availableLaborUnits()
                + assemblyState.availableLaborUnits()
                + yardDefinition.laborCapacity();
        double serviceMaintenance = selectedState.availableMaintenanceWorkRate()
                + heavyState.availableMaintenanceWorkRate()
                + assemblyState.availableMaintenanceWorkRate();
        OperatingStateAuthority operatingAuthority = new OperatingStateAuthority(
                "operating.production-seed-1.final-stage20f.test.v1",
                resolved.rootSeed(),
                List.of(new StationServiceAllocation(
                        stationKey,
                        servicePower,
                        serviceHeat,
                        serviceLabor,
                        serviceMaintenance)),
                List.of(new FacilityStateAssignment(
                        new FacilitySlotKey(stationKey, selectedSlot.definition().id()),
                        owner.stableFactionId(),
                        selectedState)));
        OperatingReport operating = Stage20IndustrialFacilityOperatingPlan.plan(
                resolved, freight, operatingAuthority);
        InventoryReport inventory = Stage20IndustrialInitialInventoryPlan.plan(
                resolved, operating, inventoryAuthority(resolved, candidates, freight, operating));
        return new Fixture(
                resolved,
                inventory,
                station,
                stationKey,
                owner.stableFactionId(),
                heavySlot.facilityOrdinal(),
                heavySlot.definition(),
                assemblyState,
                yardDefinition);
    }

    private static ShipyardInstallationAuthority yardAuthority(
            Fixture fixture,
            YardVariant variant) {
        if (variant == YardVariant.EMPTY) {
            return new ShipyardInstallationAuthority(
                    "yards.production-seed-1.explicit-empty.test.v1",
                    fixture.resolved().rootSeed(),
                    List.of(new StationYardAuthority(
                            fixture.stationKey(), List.of(), List.of())));
        }
        boolean heavyEnabled = variant != YardVariant.DISABLED_SUPPORT;
        InstalledFacilityState heavyState = fullState(
                fixture.station(),
                fixture.heavyOrdinal(),
                fixture.heavyDefinition(),
                heavyEnabled);
        String yardId = variant == YardVariant.NONCANONICAL_ID
                ? "noncanonical.yard.instance"
                : Stage20IndustrialShipyardInstallationPlan.canonicalYardInstanceId(
                fixture.station().placement().id(), 0);
        double yardPower = fixture.yardDefinition().ratedIntegrationPowerW()
                + (variant == YardVariant.POWER_OVERCLAIM ? 1d : 0d);
        String yardOwner = variant == YardVariant.OWNER_MISMATCH
                ? fixture.owner() + ".other"
                : fixture.owner();
        InstalledYardState yardState = new InstalledYardState(
                yardId,
                fixture.yardDefinition().id(),
                1d,
                yardPower,
                fixture.yardDefinition().ratedEngineeringWorkRate(),
                fixture.yardDefinition().laborCapacity(),
                fixture.yardDefinition().automationCapacity(),
                true);
        List<FacilityStateAssignment> supports = List.of(
                new FacilityStateAssignment(
                        new FacilitySlotKey(
                                fixture.stationKey(), "facility.fabrication.heavy"),
                        fixture.owner(),
                        heavyState),
                new FacilityStateAssignment(
                        new FacilitySlotKey(
                                fixture.stationKey(), "facility.fabrication.assembly"),
                        fixture.owner(),
                        fixture.assemblyState()));
        return new ShipyardInstallationAuthority(
                "yards.production-seed-1.final-stage20f.test.v1",
                fixture.resolved().rootSeed(),
                List.of(new StationYardAuthority(
                        fixture.stationKey(),
                        supports,
                        List.of(new InstalledYardAssignment(
                                new YardSlotKey(fixture.stationKey(), 0),
                                yardOwner,
                                yardState)))));
    }

    private static InitialInventoryAuthority inventoryAuthority(
            ResolvedProbeResult resolved,
            CandidateReport candidates,
            IndustrialFreightReport freight,
            OperatingReport operating) {
        TreeMap<StationKey, TreeMap<String, Double>> requiredByStation = new TreeMap<>();
        for (var reservation : freight.reservation().reservations()) {
            StationKey station = new StationKey(
                    reservation.process().systemId(),
                    reservation.process().stationPlacementId());
            double mass = reservation.reservedInputKgPerSecond()
                    * reservation.route().travelTimeS();
            requiredByStation.computeIfAbsent(station, ignored -> new TreeMap<>())
                    .merge(reservation.inputCommodityId(), mass, Double::sum);
        }
        ArrayList<StationInventoryAssignment> assignments = new ArrayList<>();
        for (var station : operating.stations()) {
            StationCandidate candidate = stationFor(candidates, new ProcessSelectionKey(
                    station.station().systemId(),
                    station.station().stationPlacementId(),
                    operating.processes().get(0).process().facilityDefinitionId(),
                    operating.processes().get(0).process().processId(),
                    operating.processes().get(0).process().outputCommodityId()));
            assignments.add(new StationInventoryAssignment(
                    station.station(),
                    new StationStorageSnapshot(
                            station.station().stationPlacementId(),
                            candidate.archetype().storageCapacityByClassKg(),
                            requiredByStation.get(station.station()),
                            Map.of())));
        }
        return new InitialInventoryAuthority(
                "initial-inventory.production-seed-1.final-stage20f.test.v1",
                resolved.rootSeed(),
                assignments);
    }

    private static InstalledFacilityState fullState(
            StationCandidate station,
            int ordinal,
            FacilityDefinition definition,
            boolean enabled) {
        return new InstalledFacilityState(
                Stage20IndustrialFacilityOperatingPlan.canonicalFacilityInstanceId(
                        station.placement().id(), ordinal),
                definition.id(),
                1d,
                definition.ratedProcessPowerW(),
                definition.ratedProcessPowerW() * definition.heatRejectionWPerProcessW(),
                definition.requiredLaborUnitsAtFullRate(),
                definition.maintenanceWorkRate(),
                Stage20IndustrialFacilityOperatingPlan.GENERATED_STATION_LOCATION_TAG,
                enabled);
    }

    private static StationCandidate stationFor(
            CandidateReport candidates,
            ProcessSelectionKey process) {
        return candidates.systems().stream()
                .filter(value -> value.systemId().equals(process.systemId()))
                .flatMap(value -> value.stations().stream())
                .filter(value -> value.placement().id().equals(process.stationPlacementId()))
                .findFirst()
                .orElseThrow();
    }

    private enum YardVariant {
        ACTIVE,
        EMPTY,
        DISABLED_SUPPORT,
        POWER_OVERCLAIM,
        NONCANONICAL_ID,
        OWNER_MISMATCH
    }

    private record Fixture(
            ResolvedProbeResult resolved,
            InventoryReport inventory,
            StationCandidate station,
            StationKey stationKey,
            String owner,
            int heavyOrdinal,
            FacilityDefinition heavyDefinition,
            InstalledFacilityState assemblyState,
            YardDefinition yardDefinition) {}
}
