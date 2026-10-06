package com.spacesim.world.generation;

import com.spacesim.persistence.Stage18IndustrialState;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistence;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState;
import com.spacesim.persistence.Stage20IndustrialEntityMaterializer;
import com.spacesim.persistence.Stage20MaterializationPersistence;
import com.spacesim.persistence.Stage20MaterializationPersistentState;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.simulation.Stage20MaterializationService;
import com.spacesim.world.Stage20DiscoveryKnowledgeState;
import com.spacesim.world.Stage20SpecialLocationGenerator;
import com.spacesim.world.generation.Stage20OperationalIndustrialSpecializationProductionIntegrationTest.CadenceFixture;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import com.spacesim.content.*;
import com.spacesim.economy.Stage18FacilityConstructionRuntime;
import com.spacesim.economy.Stage18FacilityRuntime;
import com.spacesim.economy.Stage18StationStorage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@org.junit.jupiter.api.Tag("slow")
class Stage20IndustrialEntityMaterializerTest {
    private static volatile CadenceFixture sharedFixture;

    @Test
    void completedYardAdoptionPreservesExistingResourcesAndRejectsUnpaidOrConflictingStructures() {
        var fixture = fixture();
        var initial = savedState(fixture, Stage18IndustrialState.empty(0L));
        var registry = Stage20IndustrialEntityMaterializer.materializeBootstrap(initial, fixture.specialization());
        var station = registry.stations().stream()
                .filter(s -> s.stationNode().locationTag().equals("location.orbital_station") && !s.yards().isEmpty()).findFirst().orElseThrow();
        var catalog = Stage23YardConstructionCatalog.loadDefault();
        var spec = catalog.find("yard.orbital_escort_v1");
        // Explicit completed-work fixture tests admission; real material custody is covered by the queue test.
        var completed = new com.spacesim.economy.Stage23YardConstructionWorkQueue.Order("yard-admission", "new-yard",
                spec.yardDefinitionId(), station.stationId(), station.stationNode().locationTag(), 1, spec.requiredWorkSeconds());
        var queue = new com.spacesim.economy.Stage23YardConstructionWorkQueue(catalog, Stage18ResourceOntologyLoader.loadDefault(),
                new com.spacesim.economy.Stage23YardConstructionWorkQueue.State(catalog.fingerprint(), 2, List.of(completed)));
        var updated = registry.adoptCompletedYardConstruction(List.of(completed), queue);
        var installedStation = updated.station(station.stationId());
        assertEquals(station.storage().snapshot(), installedStation.storage().snapshot());
        assertEquals(station.facilities(), installedStation.facilities());
        assertEquals(station.facilityCapabilities(), installedStation.facilityCapabilities());
        assertEquals(station.yards().size() + 1, installedStation.yards().size());
        var installed = installedStation.yards().stream().filter(y -> y.yardInstanceId().equals("new-yard")).findFirst().orElseThrow();
        assertFalse(installed.enabled()); assertEquals(1d, installed.conditionFraction());
        assertEquals(0d, installed.allocatedIntegrationPowerW()); assertEquals(0d, installed.availableIntegrationWorkRate());
        assertEquals(0, installed.availableLaborCapacity()); assertEquals(0, installed.availableAutomationCapacity());
        assertFalse(installedStation.yardCapabilities().stream().filter(y -> y.yardInstanceId().equals("new-yard")).findFirst().orElseThrow().active());
        assertEquals(updated, updated.adoptCompletedYardConstruction(List.of(completed), queue));
        var working = updated.allocateYardResources(station.stationId(), "new-yard");
        var workingStation = working.station(station.stationId());
        assertTrue(workingStation.yardCapabilities().stream().filter(y -> y.yardInstanceId().equals("new-yard")).findFirst().orElseThrow().active());
        assertEquals(installedStation.yards().stream().mapToDouble(y -> y.allocatedIntegrationPowerW()).sum(),
                workingStation.yards().stream().mapToDouble(y -> y.allocatedIntegrationPowerW()).sum(), 1e-6);
        assertEquals(installedStation.yards().stream().mapToDouble(y -> y.availableIntegrationWorkRate()).sum(),
                workingStation.yards().stream().mapToDouble(y -> y.availableIntegrationWorkRate()).sum(), 1e-6);
        assertEquals(installedStation.yards().stream().mapToLong(y -> y.availableLaborCapacity()).sum(),
                workingStation.yards().stream().mapToLong(y -> y.availableLaborCapacity()).sum());
        assertEquals(installedStation.yards().stream().mapToLong(y -> y.availableAutomationCapacity()).sum(),
                workingStation.yards().stream().mapToLong(y -> y.availableAutomationCapacity()).sum());
        assertEquals(working, working.adoptCompletedYardConstruction(List.of(completed), queue), "Re-admission must retain allocated operating resources");
        var workingCheckpoint = working.captureIndustrialState(initial.industrialState());
        assertEquals(workingCheckpoint, Stage20IndustrialEntityMaterializer.restore(replaceIndustry(initial, workingCheckpoint))
                .captureIndustrialState(initial.industrialState()));
        assertEquals(updated.captureIndustrialState(initial.industrialState()),
                Stage20IndustrialEntityMaterializer.restore(replaceIndustry(initial,
                        updated.captureIndustrialState(initial.industrialState()))).captureIndustrialState(initial.industrialState()));
        var partial = new com.spacesim.economy.Stage23YardConstructionWorkQueue.Order(completed.orderId(), completed.yardInstanceId(),
                completed.yardDefinitionId(), completed.stationId(), completed.locationTag(), 1, 0);
        assertThrows(IllegalArgumentException.class, () -> registry.adoptCompletedYardConstruction(List.of(partial), queue));
        var existing = station.yards().get(0);
        {
            var otherDesign = catalog.specifications().stream()
                    .filter(s -> !s.yardDefinitionId().equals(existing.yardDefinitionId())).findFirst().orElseThrow();
            var conflicting = new com.spacesim.economy.Stage23YardConstructionWorkQueue.Order("conflict", existing.yardInstanceId(),
                    otherDesign.yardDefinitionId(), station.stationId(), station.stationNode().locationTag(), 1,
                    otherDesign.requiredWorkSeconds());
            var conflictQueue = new com.spacesim.economy.Stage23YardConstructionWorkQueue(catalog, Stage18ResourceOntologyLoader.loadDefault(),
                    new com.spacesim.economy.Stage23YardConstructionWorkQueue.State(catalog.fingerprint(), 2, List.of(conflicting)));
            assertThrows(IllegalArgumentException.class, () -> updated.adoptCompletedYardConstruction(List.of(conflicting), conflictQueue));
        }
    }

    @Test
    void acceptedSpecializationMaterializesExactStage18StationFacilityStorageAndYardState() {
        CadenceFixture fixture = fixture();
        Stage20GeneratedCampaignPersistentState saved = savedState(
                fixture, Stage18IndustrialState.empty(0L));

        var registry = Stage20IndustrialEntityMaterializer.materializeBootstrap(
                saved, fixture.specialization());
        var yardReport = fixture.specialization().yardInstallation();
        var inventory = yardReport.inventory();

        assertEquals(inventory.stations().size(), registry.stations().size());
        assertEquals(fixture.resolved().rootSeed(), registry.rootSeed());
        assertEquals(saved.materializedWorld().worldFingerprint(), registry.worldFingerprint());
        assertTrue(registry.stations().stream().allMatch(value ->
                value.stationId().equals(value.storage().stationId())
                        && value.stationId().equals(value.stationNode().stationId())));
        assertTrue(registry.stations().stream().allMatch(value -> value.position() != null));

        for (var accepted : inventory.stations()) {
            var live = registry.station(accepted.assignment().station().stationPlacementId());
            assertEquals(accepted.stationArchetypeId(), live.stationArchetypeId());
            assertEquals(accepted.assignment().storage(), live.storage().snapshot());
            assertFalse(live.facilities().isEmpty());
            assertTrue(live.facilityCapabilities().stream().allMatch(value ->
                    value.status() == com.spacesim.economy.Stage18FacilityRuntime.Status.ACTIVE));
        }
        assertEquals(
                fixture.specialization().activeYardCount(),
                registry.stations().stream().mapToInt(value -> value.yards().size()).sum());
        assertTrue(registry.stations().stream().flatMap(value -> value.yardCapabilities().stream())
                .allMatch(value -> value.active()));
    }

    @Test
    void capturedIndustrialStateRestoresSameStableRuntimeIdentitiesWithoutDuplication() {
        CadenceFixture fixture = fixture();
        Stage20GeneratedCampaignPersistentState initial = savedState(
                fixture, Stage18IndustrialState.empty(0L));
        var first = Stage20IndustrialEntityMaterializer.materializeBootstrap(
                initial, fixture.specialization());
        Stage18IndustrialState captured = first.captureIndustrialState(initial.industrialState());
        Stage20GeneratedCampaignPersistentState persisted = replaceIndustry(initial, captured);

        var restored = Stage20IndustrialEntityMaterializer.restore(persisted);

        assertEquals(
                first.stations().stream().map(value -> value.stationId()).toList(),
                restored.stations().stream().map(value -> value.stationId()).toList());
        assertEquals(
                first.stations().stream().flatMap(value -> value.facilities().stream())
                        .map(value -> value.facilityInstanceId()).sorted().toList(),
                restored.stations().stream().flatMap(value -> value.facilities().stream())
                        .map(value -> value.facilityInstanceId()).sorted().toList());
        assertEquals(
                first.stations().stream().flatMap(value -> value.yards().stream())
                        .map(value -> value.yardInstanceId()).sorted().toList(),
                restored.stations().stream().flatMap(value -> value.yards().stream())
                        .map(value -> value.yardInstanceId()).sorted().toList());
        assertEquals(captured, restored.captureIndustrialState(captured));
    }

    @Test
    void resumeBeforeBootstrapFailsClosedInsteadOfInventingIndustrialEntities() {
        CadenceFixture fixture = fixture();
        Stage20GeneratedCampaignPersistentState empty = savedState(
                fixture, Stage18IndustrialState.empty(0L));

        assertThrows(IllegalArgumentException.class,
                () -> Stage20IndustrialEntityMaterializer.restore(empty));
    }

    @Test
    void completedPaidFacilitySurvivesGeneratedRestoreWhileMissingCompletionFailsClosed() {
        CadenceFixture fixture = fixture();
        var initial = savedState(fixture, Stage18IndustrialState.empty(0L));
        var registry = Stage20IndustrialEntityMaterializer.materializeBootstrap(initial, fixture.specialization());
        var captured = registry.captureIndustrialState(initial.industrialState());
        var target = registry.stations().stream()
                .filter(station -> station.stationNode().locationTag().equals("location.orbital_station"))
                .findFirst().orElseThrow();
        var construction = new Stage18FacilityConstructionRuntime(Stage18FacilityConstructionCatalogLoader.loadDefault(),
                Stage18FacilityCatalogLoader.loadDefault(), Stage18ResourceOntologyLoader.loadDefault());
        var order = construction.createOrder("m22.6.completed_support", "m22.6.support_instance",
                "facility.fabrication.precision", target.stationId(), target.stationNode().locationTag());
        // The fixture declares and pays a finite construction kit; restore receives no kit or grants.
        Map<String, Double> capacity = new java.util.TreeMap<>();
        order.requiredMassByCommodityKg().forEach((id, mass) -> capacity.merge(
                Stage18ResourceOntologyLoader.loadDefault().findCommodity(id).storageClassId(), mass, Double::sum));
        var kit = new Stage18StationStorage(Stage18ResourceOntologyLoader.loadDefault(),
                Stage18ManufacturingProductRegistry.loadDefault(), target.stationId(), capacity,
                order.requiredMassByCommodityKg(), Map.of());
        for (var input : order.requiredMassByCommodityKg().entrySet()) {
            order = construction.deliver(order, kit, input.getKey(), input.getValue()).order();
        }
        var constructors = new Stage18FacilityConstructionRuntime.ConstructionCapability("fixture.funded_constructors",
                Set.of("capability.fabrication.heavy", "capability.fabrication.assembly",
                        "capability.fabrication.electrical", "capability.fabrication.precision"), 1d);
        var completed = construction.advanceWork(order, constructors.openInterval(order.requiredWorkSeconds() + 1d));
        assertEquals(Stage18FacilityConstructionRuntime.WorkStatus.COMPLETED, completed.status());
        var facilities = new ArrayList<>(captured.facilities());
        facilities.add(new Stage18IndustrialState.FacilityInstallationSnapshot(target.stationId(),
                new Stage18FacilityRuntime.InstalledFacilityState(order.facilityInstanceId(), order.facilityDefinitionId(),
                        1d, 0d, 0d, 0d, 0d, order.locationTag(), true)));
        var constructions = new ArrayList<>(captured.constructionOrders());
        constructions.add(completed.order());
        var extended = new Stage18IndustrialState(captured.schemaVersion(), captured.contentFingerprint(),
                captured.simulationTick(), captured.sources(), captured.stationStorages(), facilities,
                captured.yards(), constructions, captured.processOrders());
        var restored = Stage20IndustrialEntityMaterializer.restore(replaceIndustry(initial, extended));
        assertEquals(extended, restored.captureIndustrialState(extended));
        assertTrue(restored.station(target.stationId()).stationNode().installedFacilities().contains(completed.installedFacility()));
        var added = restored.station(target.stationId()).facilityCapabilities().stream()
                .filter(row -> row.facilityInstanceId().equals("m22.6.support_instance")).findFirst().orElseThrow();
        assertEquals(0d, added.effectiveEngineeringWorkRate(), "load must not grant power or staff to the new facility");
        var unproven = new Stage18IndustrialState(captured.schemaVersion(), captured.contentFingerprint(),
                captured.simulationTick(), captured.sources(), captured.stationStorages(), facilities,
                captured.yards(), captured.constructionOrders(), captured.processOrders());
        assertThrows(IllegalArgumentException.class, () ->
                Stage20IndustrialEntityMaterializer.restore(replaceIndustry(initial, unproven)));
    }

    private static Stage20GeneratedCampaignPersistentState savedState(
            CadenceFixture fixture,
            Stage18IndustrialState industry) {
        SimulationSession session = SimulationSession.createDemo(fixture.resolved().rootSeed());
        Stage20MaterializationPersistentState physical = Stage20MaterializationPersistence.capture(
                session,
                Stage20MaterializationService.forSession(session));
        return Stage20GeneratedCampaignPersistence.capture(
                fixture.resolved(),
                Stage20SpecialLocationGenerator.generateCurrent(fixture.resolved()),
                fixture.specialization(),
                physical,
                industry,
                List.of(new Stage20DiscoveryKnowledgeState(
                        "faction.stage20_5.industrial-materialization",
                        List.of())));
    }

    private static Stage20GeneratedCampaignPersistentState replaceIndustry(
            Stage20GeneratedCampaignPersistentState base,
            Stage18IndustrialState industry) {
        return new Stage20GeneratedCampaignPersistentState(
                base.schemaVersion(),
                base.generationIdentity(),
                base.materializedWorld(),
                base.materializationState(),
                industry,
                base.discoveryState(),
                base.openRuntimeBoundaries());
    }

    private static synchronized CadenceFixture fixture() {
        if (sharedFixture == null) {
            sharedFixture = Stage20OperationalIndustrialSpecializationProductionIntegrationTest.cadenceFixture();
        }
        return sharedFixture;
    }
}
