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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20IndustrialEntityMaterializerTest {
    private static volatile CadenceFixture sharedFixture;

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
