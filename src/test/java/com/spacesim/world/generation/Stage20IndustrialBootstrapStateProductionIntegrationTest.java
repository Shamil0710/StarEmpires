package com.spacesim.world.generation;

import com.spacesim.content.Stage18FacilityCatalog.FacilityDefinition;
import com.spacesim.economy.Stage18FacilityRuntime.InstalledFacilityState;
import com.spacesim.economy.Stage18StationStorage.StationStorageSnapshot;
import com.spacesim.world.Stage20BootstrapFreightOwnershipPlan;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.FacilitySlotKey;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.FacilityStateAssignment;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.OperatingReport;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.OperatingStateAuthority;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.StationKey;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.StationServiceAllocation;
import com.spacesim.world.Stage20IndustrialInitialInventoryPlan;
import com.spacesim.world.Stage20IndustrialInitialInventoryPlan.CommodityBufferEvidence;
import com.spacesim.world.Stage20IndustrialInitialInventoryPlan.InitialInventoryAuthority;
import com.spacesim.world.Stage20IndustrialInitialInventoryPlan.InventoryReport;
import com.spacesim.world.Stage20IndustrialInitialInventoryPlan.StationInventoryAssignment;
import com.spacesim.world.Stage20IndustrialInitialInventoryPlan.StationInventoryEvidence;
import com.spacesim.world.Stage20IndustrialInputFreightOwnershipPlan;
import com.spacesim.world.Stage20IndustrialInputFreightOwnershipPlan.IndustrialFreightReport;
import com.spacesim.world.Stage20IndustrialInputFreightOwnershipPlan.ProcessOwnerAssignment;
import com.spacesim.world.Stage20IndustrialInputFreightOwnershipPlan.ProcessOwnershipAuthority;
import com.spacesim.world.Stage20IndustrialInputReservationPlan;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.ProcessOutputRequest;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.ProcessSelectionKey;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.SelectionAuthority;
import com.spacesim.world.Stage20IndustrialInputRouteEvidencePlan;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.CandidateReport;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.MissingAuthority;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.StationCandidate;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.RouteAdmissionStatus;
import com.spacesim.world.generation.Stage20ResolvedGeneratedWorldProductionProbe.ResolvedProbeResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20IndustrialBootstrapStateProductionIntegrationTest {
    @Test
    void realSeedClosesFacilityOperationAndPhysicalFirstDeliveryInventoryInOrder() {
        Fixture fixture = fixture();

        OperatingReport operating = Stage20IndustrialFacilityOperatingPlan.plan(
                fixture.resolved(), fixture.freight(), fixture.operatingAuthority());
        OperatingReport repeatedOperating = Stage20IndustrialFacilityOperatingPlan.plan(
                fixture.resolved(), fixture.freight(), fixture.operatingAuthority());

        assertEquals(Stage20IndustrialFacilityOperatingPlan.Status.ACCEPTED, operating.status());
        assertEquals(operating, repeatedOperating);
        assertTrue(operating.facilityOperatingStateAuthoritative());
        assertFalse(operating.operationallyAuthoritative());
        assertFalse(operating.missingAuthorities().contains(
                MissingAuthority.INSTALLED_FACILITY_OPERATING_STATE));
        assertTrue(operating.missingAuthorities().contains(
                MissingAuthority.INITIAL_STATION_INVENTORY));
        assertTrue(operating.missingAuthorities().contains(MissingAuthority.INSTALLED_SHIPYARDS));
        assertEquals(
                Stage20IndustrialFacilityOperatingPlan.canonicalFacilityInstanceId(
                        fixture.station().placement().id(), fixture.facilityOrdinal()),
                operating.facilities().get(0).snapshot().facilityInstanceId());

        InitialInventoryAuthority inventoryAuthority = inventoryAuthority(fixture, operating, true);
        InventoryReport inventory = Stage20IndustrialInitialInventoryPlan.plan(
                fixture.resolved(), operating, inventoryAuthority);
        InventoryReport repeatedInventory = Stage20IndustrialInitialInventoryPlan.plan(
                fixture.resolved(), operating, inventoryAuthority);

        assertEquals(Stage20IndustrialInitialInventoryPlan.Status.ACCEPTED, inventory.status());
        assertEquals(inventory, repeatedInventory);
        assertTrue(inventory.initialInventoryAuthoritative());
        assertFalse(inventory.operationallyAuthoritative());
        assertTrue(inventory.totalRequiredBufferMassKg() > 0d);
        assertEquals(EnumSet.of(MissingAuthority.INSTALLED_SHIPYARDS),
                inventory.missingAuthorities());
    }

    @Test
    void sharedStationServiceShortageRejectsWithoutClosingOperatingAuthority() {
        Fixture fixture = fixture();
        StationServiceAllocation services = fixture.operatingAuthority().stationServices().get(0);
        OperatingStateAuthority starved = new OperatingStateAuthority(
                "operating.production-seed-1.starved.test.v1",
                fixture.resolved().rootSeed(),
                List.of(new StationServiceAllocation(
                        services.station(),
                        0d,
                        services.availableHeatRejectionW(),
                        services.availableLaborUnits(),
                        services.availableMaintenanceWorkRate())),
                fixture.operatingAuthority().facilities());

        OperatingReport report = Stage20IndustrialFacilityOperatingPlan.plan(
                fixture.resolved(), fixture.freight(), starved);

        assertEquals(
                Stage20IndustrialFacilityOperatingPlan.Status.INSUFFICIENT_OPERATING_CAPABILITY,
                report.status());
        assertFalse(report.facilityOperatingStateAuthoritative());
        assertTrue(report.missingAuthorities().contains(
                MissingAuthority.INSTALLED_FACILITY_OPERATING_STATE));
    }

    @Test
    void emptyCanonicalStorageRejectsWithoutClosingInitialInventoryAuthority() {
        Fixture fixture = fixture();
        OperatingReport operating = Stage20IndustrialFacilityOperatingPlan.plan(
                fixture.resolved(), fixture.freight(), fixture.operatingAuthority());
        InitialInventoryAuthority empty = inventoryAuthority(fixture, operating, false);

        InventoryReport report = Stage20IndustrialInitialInventoryPlan.plan(
                fixture.resolved(), operating, empty);

        assertEquals(
                Stage20IndustrialInitialInventoryPlan.Status.INSUFFICIENT_INITIAL_INVENTORY,
                report.status());
        assertFalse(report.initialInventoryAuthoritative());
        assertTrue(report.missingAuthorities().contains(
                MissingAuthority.INITIAL_STATION_INVENTORY));
        assertTrue(report.stations().stream().flatMap(value -> value.buffers().stream())
                .allMatch(value -> value.shortageMassKg() > 0d));
    }

    @Test
    void nonCanonicalFacilityIdentityAndForgedDerivedRowsAreRejected() {
        Fixture fixture = fixture();
        FacilityStateAssignment original = fixture.operatingAuthority().facilities().get(0);
        InstalledFacilityState state = original.state();
        FacilityStateAssignment nonCanonical = new FacilityStateAssignment(
                original.slot(),
                original.stableFactionId(),
                new InstalledFacilityState(
                        "noncanonical.facility.instance",
                        state.definitionId(),
                        state.conditionFraction(),
                        state.allocatedProcessPowerW(),
                        state.availableHeatRejectionW(),
                        state.availableLaborUnits(),
                        state.availableMaintenanceWorkRate(),
                        state.locationTag(),
                        state.enabled()));
        OperatingStateAuthority invalid = new OperatingStateAuthority(
                "operating.production-seed-1.noncanonical.test.v1",
                fixture.resolved().rootSeed(),
                fixture.operatingAuthority().stationServices(),
                List.of(nonCanonical));

        assertThrows(IllegalArgumentException.class, () ->
                Stage20IndustrialFacilityOperatingPlan.plan(
                        fixture.resolved(), fixture.freight(), invalid));

        OperatingReport operating = Stage20IndustrialFacilityOperatingPlan.plan(
                fixture.resolved(), fixture.freight(), fixture.operatingAuthority());
        var facility = operating.facilities().get(0);
        assertThrows(IllegalArgumentException.class, () ->
                new Stage20IndustrialFacilityOperatingPlan.FacilityOperatingEvidence(
                        facility.assignment(),
                        facility.snapshot(),
                        facility.processDemands(),
                        facility.requiredProcessPowerW() + 1d,
                        facility.requiredEngineeringWorkRate(),
                        facility.requiredMaintenanceWorkRate(),
                        facility.status()));

        InitialInventoryAuthority authority = inventoryAuthority(fixture, operating, true);
        InventoryReport inventory = Stage20IndustrialInitialInventoryPlan.plan(
                fixture.resolved(), operating, authority);
        StationInventoryEvidence station = inventory.stations().get(0);
        CommodityBufferEvidence buffer = station.buffers().get(0);
        double forgedRequired = buffer.requiredMassKg() + 1d;
        double forgedShortage = Math.max(0d, forgedRequired - buffer.availableMassKg());
        var forgedBuffer = new CommodityBufferEvidence(
                buffer.commodityId(),
                forgedRequired,
                buffer.availableMassKg(),
                forgedShortage,
                forgedShortage <= 1.0e-9d
                        ? Stage20IndustrialInitialInventoryPlan.Status.ACCEPTED
                        : Stage20IndustrialInitialInventoryPlan.Status.INSUFFICIENT_INITIAL_INVENTORY);
        var forgedStation = new StationInventoryEvidence(
                station.assignment(),
                station.stationArchetypeId(),
                List.of(forgedBuffer),
                forgedBuffer.status());
        assertThrows(IllegalArgumentException.class, () ->
                new InventoryReport(
                        inventory.version(),
                        inventory.rootSeed(),
                        inventory.resolvedProbeVersion(),
                        inventory.candidatePlanVersion(),
                        operating,
                        authority,
                        forgedBuffer.status(),
                        java.util.Optional.of(
                                Stage20IndustrialInitialInventoryPlan.FailureReason
                                        .PIPELINE_BUFFER_SHORTAGE),
                        List.of(forgedStation),
                        operating.missingAuthorities()));
    }

    private static Fixture fixture() {
        ResolvedProbeResult resolved = Stage20ResolvedGeneratedWorldProductionProbe.runCurrent(1L);
        var routes = Stage20IndustrialInputRouteEvidencePlan.reconstruct(resolved);
        var selected = routes.processes().stream()
                .filter(value -> value.candidate().throughput().inputLimitedOutputKgPerSecond() > 0d)
                .filter(value -> value.inputs().size() == 1)
                .filter(value -> value.inputs().get(0).supplyRoutes().stream()
                        .filter(route -> route.status() == RouteAdmissionStatus.ADMITTED)
                        .noneMatch(route -> route.supplyKey().systemId().equals(
                                value.candidate().capacity().systemId())))
                .filter(value -> value.inputs().get(0).supplyRoutes().stream()
                        .anyMatch(route -> route.status() == RouteAdmissionStatus.ADMITTED))
                .findFirst()
                .orElseThrow();
        ProcessSelectionKey process = ProcessSelectionKey.from(selected.candidate());
        double requestedOutput = selected.candidate().throughput()
                .inputLimitedOutputKgPerSecond() * 0.001d;
        var reservation = Stage20IndustrialInputReservationPlan.reserve(
                resolved,
                new SelectionAuthority(
                        "selection.production-seed-1.bootstrap-state.test.v1",
                        resolved.rootSeed(),
                        List.of(new ProcessOutputRequest(process, requestedOutput))));
        var bootstrapOwnership = Stage20BootstrapFreightOwnershipPlan.plan(resolved);
        var owner = bootstrapOwnership.factions().stream()
                .filter(value -> value.reserveFreighterCount() > 0)
                .max(java.util.Comparator.comparingInt(value -> value.reserveFreighterCount()))
                .orElseThrow();
        IndustrialFreightReport freight = Stage20IndustrialInputFreightOwnershipPlan.planCurrent(
                resolved,
                reservation,
                new ProcessOwnershipAuthority(
                        "process-owners.production-seed-1.bootstrap-state.test.v1",
                        resolved.rootSeed(),
                        List.of(new ProcessOwnerAssignment(process, owner.stableFactionId()))));
        CandidateReport candidates = Stage20IndustrialSpecializationCandidatePlan.reconstruct(resolved);
        StationCandidate station = candidates.systems().stream()
                .filter(value -> value.systemId().equals(process.systemId()))
                .flatMap(value -> value.stations().stream())
                .filter(value -> value.placement().id().equals(process.stationPlacementId()))
                .findFirst()
                .orElseThrow();
        var facilitySlot = station.facilitySlots().stream()
                .filter(value -> value.definition().id().equals(process.facilityDefinitionId()))
                .findFirst()
                .orElseThrow();
        FacilityDefinition definition = facilitySlot.definition();
        StationKey stationKey = new StationKey(process.systemId(), process.stationPlacementId());
        FacilitySlotKey slotKey = new FacilitySlotKey(stationKey, definition.id());
        InstalledFacilityState state = new InstalledFacilityState(
                Stage20IndustrialFacilityOperatingPlan.canonicalFacilityInstanceId(
                        station.placement().id(), facilitySlot.facilityOrdinal()),
                definition.id(),
                1d,
                definition.ratedProcessPowerW(),
                definition.ratedProcessPowerW() * definition.heatRejectionWPerProcessW(),
                definition.requiredLaborUnitsAtFullRate(),
                definition.maintenanceWorkRate(),
                Stage20IndustrialFacilityOperatingPlan.GENERATED_STATION_LOCATION_TAG,
                true);
        OperatingStateAuthority operatingAuthority = new OperatingStateAuthority(
                "operating.production-seed-1.bootstrap-state.test.v1",
                resolved.rootSeed(),
                List.of(new StationServiceAllocation(
                        stationKey,
                        state.allocatedProcessPowerW(),
                        state.availableHeatRejectionW(),
                        state.availableLaborUnits(),
                        state.availableMaintenanceWorkRate())),
                List.of(new FacilityStateAssignment(
                        slotKey, owner.stableFactionId(), state)));
        return new Fixture(
                resolved,
                candidates,
                freight,
                station,
                facilitySlot.facilityOrdinal(),
                operatingAuthority);
    }

    private static InitialInventoryAuthority inventoryAuthority(
            Fixture fixture,
            OperatingReport operating,
            boolean includeRequiredMass) {
        TreeMap<StationKey, TreeMap<String, Double>> requiredByStation = new TreeMap<>();
        for (var reservation : fixture.freight().reservation().reservations()) {
            StationKey station = new StationKey(
                    reservation.process().systemId(),
                    reservation.process().stationPlacementId());
            double mass = reservation.reservedInputKgPerSecond()
                    * reservation.route().travelTimeS();
            requiredByStation.computeIfAbsent(station, ignored -> new TreeMap<>())
                    .merge(reservation.inputCommodityId(), mass, Double::sum);
        }
        TreeMap<StationKey, StationCandidate> candidateByStation = new TreeMap<>();
        fixture.candidates().systems().forEach(system -> system.stations().forEach(station ->
                candidateByStation.put(
                        new StationKey(system.systemId(), station.placement().id()), station)));
        ArrayList<StationInventoryAssignment> assignments = new ArrayList<>();
        for (var station : operating.stations()) {
            StationCandidate candidate = candidateByStation.get(station.station());
            Map<String, Double> mass = includeRequiredMass
                    ? requiredByStation.get(station.station())
                    : Map.of();
            assignments.add(new StationInventoryAssignment(
                    station.station(),
                    new StationStorageSnapshot(
                            station.station().stationPlacementId(),
                            candidate.archetype().storageCapacityByClassKg(),
                            mass,
                            Map.of())));
        }
        return new InitialInventoryAuthority(
                "initial-inventory.production-seed-1.bootstrap-state.test.v1",
                fixture.resolved().rootSeed(),
                assignments);
    }

    private record Fixture(
            ResolvedProbeResult resolved,
            CandidateReport candidates,
            IndustrialFreightReport freight,
            StationCandidate station,
            int facilityOrdinal,
            OperatingStateAuthority operatingAuthority) {}
}
