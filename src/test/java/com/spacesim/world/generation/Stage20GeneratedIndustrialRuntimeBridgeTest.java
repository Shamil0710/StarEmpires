package com.spacesim.world.generation;

import com.spacesim.persistence.Stage18IndustrialState;
import com.spacesim.persistence.Stage18IndustrialState.FacilityInstallationSnapshot;
import com.spacesim.economy.Stage18FacilityRuntime.InstalledFacilityState;
import com.spacesim.economy.Stage18StationStorage.StationStorageSnapshot;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistence;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState;
import com.spacesim.persistence.Stage20GeneratedIndustrialRuntimeBridge;
import com.spacesim.persistence.Stage20MaterializationPersistence;
import com.spacesim.persistence.Stage20MaterializationPersistentState;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.simulation.Stage20MaterializationService;
import com.spacesim.world.Stage20DiscoveryKnowledgeState;
import com.spacesim.world.Stage20SpecialLocationGenerator;
import com.spacesim.world.generation.Stage20OperationalIndustrialSpecializationProductionIntegrationTest.CadenceFixture;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20GeneratedIndustrialRuntimeBridgeTest {
    private static volatile CadenceFixture sharedFixture;

    @Test
    void bootstrapCaptureAndRestorePreserveBothIndustrialStationFamilies() {
        CadenceFixture fixture = fixture();
        Stage20GeneratedCampaignPersistentState initial = savedState(fixture);
        var runtime = Stage20GeneratedIndustrialRuntimeBridge.materializeBootstrap(
                initial, fixture.specialization());
        var sourceOutpost = runtime.sourceOutposts().outposts().get(0);
        double requestKg = Math.min(
                1d, sourceOutpost.source().sourceState().remainingAccessibleMassKg() * 0.001d);
        assertTrue(runtime.sourceOutposts().extract(
                sourceOutpost.site().siteId(), requestKg, 60d).committed());

        String unrelatedStationId = "station.stage20_5.unrelated";
        StationStorageSnapshot unrelatedStorage = new StationStorageSnapshot(
                unrelatedStationId,
                Map.of("storage.dry_bulk", 1_000d),
                Map.of(),
                Map.of());
        FacilityInstallationSnapshot unrelatedFacility = new FacilityInstallationSnapshot(
                unrelatedStationId,
                new InstalledFacilityState(
                        unrelatedStationId + ".facility.0",
                        "facility.processing.bulk_refinery",
                        1d,
                        0d,
                        0d,
                        0d,
                        0d,
                        "location.orbital_station",
                        false));
        Stage18IndustrialState withUnrelatedIndustry = new Stage18IndustrialState(
                Stage18IndustrialState.CURRENT_VERSION,
                initial.industrialState().contentFingerprint(),
                initial.industrialState().simulationTick(),
                initial.industrialState().sources(),
                List.of(unrelatedStorage),
                List.of(unrelatedFacility),
                initial.industrialState().yards(),
                initial.industrialState().constructionOrders(),
                initial.industrialState().processOrders());
        Stage20GeneratedCampaignPersistentState withUnrelated = replaceIndustry(
                initial, withUnrelatedIndustry);

        Stage20GeneratedCampaignPersistentState captured = runtime.captureCampaignState(withUnrelated);
        var restored = Stage20GeneratedIndustrialRuntimeBridge.restore(captured);

        assertEquals(runtime.industrial().stations().stream().map(value -> value.stationId()).toList(),
                restored.industrial().stations().stream().map(value -> value.stationId()).toList());
        assertEquals(runtime.sourceOutposts().outposts().stream().map(value -> value.stationId()).toList(),
                restored.sourceOutposts().outposts().stream().map(value -> value.stationId()).toList());
        assertEquals(sourceOutpost.source().sourceState().remainingAccessibleMassKg(),
                restored.sourceOutposts().outpost(sourceOutpost.site().siteId())
                        .source().sourceState().remainingAccessibleMassKg(), 0d);
        assertEquals(sourceOutpost.storage().snapshot(),
                restored.sourceOutposts().outpost(sourceOutpost.site().siteId()).storage().snapshot());
        assertTrue(captured.industrialState().stationStorages().contains(unrelatedStorage));
        assertTrue(captured.industrialState().facilities().contains(unrelatedFacility));
        assertEquals(captured.industrialState(),
                restored.captureCampaignState(captured).industrialState());
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

    private static Stage20GeneratedCampaignPersistentState savedState(CadenceFixture fixture) {
        SimulationSession session = SimulationSession.createDemo(fixture.resolved().rootSeed());
        Stage20MaterializationPersistentState physical = Stage20MaterializationPersistence.capture(
                session, Stage20MaterializationService.forSession(session));
        return Stage20GeneratedCampaignPersistence.capture(
                fixture.resolved(),
                Stage20SpecialLocationGenerator.generateCurrent(fixture.resolved()),
                fixture.specialization(),
                physical,
                Stage18IndustrialState.empty(0L),
                List.of(new Stage20DiscoveryKnowledgeState(
                        "faction.stage20_5.generated-industrial-runtime",
                        List.of())));
    }

    private static synchronized CadenceFixture fixture() {
        if (sharedFixture == null) {
            sharedFixture = Stage20OperationalIndustrialSpecializationProductionIntegrationTest.cadenceFixture();
        }
        return sharedFixture;
    }
}
