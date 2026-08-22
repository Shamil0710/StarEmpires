package com.spacesim.world.generation;

import com.spacesim.content.Stage18ExtractionCatalogLoader;
import com.spacesim.persistence.Stage18IndustrialState;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistence;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState;
import com.spacesim.persistence.Stage20IndustrialEntityMaterializer;
import com.spacesim.persistence.Stage20MaterializationPersistence;
import com.spacesim.persistence.Stage20MaterializationPersistentState;
import com.spacesim.persistence.Stage20SourceOutpostMaterializer;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.simulation.Stage20MaterializationService;
import com.spacesim.world.Stage20DiscoveryKnowledgeState;
import com.spacesim.world.Stage20SpecialLocationGenerator;
import com.spacesim.world.generation.Stage20OperationalIndustrialSpecializationProductionIntegrationTest.CadenceFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20SourceOutpostMaterializerTest {
    private static volatile CadenceFixture sharedFixture;

    @Test
    void canonicalExtractionSitesMaterializeEmptyOrdinaryStage18Outposts() {
        CadenceFixture fixture = fixture();
        Stage20GeneratedCampaignPersistentState saved = savedState(
                fixture, Stage18IndustrialState.empty(0L));

        var registry = Stage20SourceOutpostMaterializer.materialize(saved);
        var sites = fixture.resolved().generation().resourceWorld().orElseThrow().initialExtractionSites();

        assertEquals(sites.size(), registry.outposts().size());
        assertFalse(registry.outposts().isEmpty());
        assertTrue(registry.outposts().stream().allMatch(value ->
                value.stationId().equals(Stage20SourceOutpostMaterializer.outpostStationId(value.site().siteId()))
                        && value.stationId().equals(value.storage().stationId())
                        && value.storage().snapshotCommodityMassByIdKg().isEmpty()
                        && value.storage().snapshotProductCountById().isEmpty()));
    }

    @Test
    void extractionConsumesFiniteSourceBeforeCommodityAppearsInOrdinaryStationStorage() {
        CadenceFixture fixture = fixture();
        Stage20GeneratedCampaignPersistentState saved = savedState(
                fixture, Stage18IndustrialState.empty(0L));
        var registry = Stage20SourceOutpostMaterializer.materialize(saved);
        var outpost = registry.outposts().get(0);
        double beforeReserve = outpost.source().sourceState().remainingAccessibleMassKg();
        double beforeCargo = outpost.storage().snapshotCommodityMassByIdKg()
                .getOrDefault(outpost.source().sourceState().outputCommodityId(), 0d);
        double requestedMassKg = Math.min(1d, beforeReserve * 0.001d);

        var result = registry.extract(outpost.site().siteId(), requestedMassKg, 60d);

        assertTrue(result.committed());
        assertEquals(beforeReserve - requestedMassKg,
                outpost.source().sourceState().remainingAccessibleMassKg(), 1e-9d);
        assertTrue(outpost.storage().snapshotCommodityMassByIdKg()
                .getOrDefault(outpost.source().sourceState().outputCommodityId(), 0d) > beforeCargo);
    }

    @Test
    void rejectedExtractionDoesNotMutateSourceOrStorage() {
        CadenceFixture fixture = fixture();
        Stage20GeneratedCampaignPersistentState saved = savedState(
                fixture, Stage18IndustrialState.empty(0L));
        var registry = Stage20SourceOutpostMaterializer.materialize(saved);
        var outpost = registry.outposts().get(0);
        var method = Stage18ExtractionCatalogLoader.loadDefault().findMethod(outpost.site().extractionMethodId());
        double beforeReserve = outpost.source().sourceState().remainingAccessibleMassKg();
        var beforeStorage = outpost.storage().snapshot();

        var result = registry.extract(
                outpost.site().siteId(),
                method.maxSourceKgPerSecond() * 100d,
                1d);

        assertFalse(result.committed());
        assertEquals(beforeReserve, outpost.source().sourceState().remainingAccessibleMassKg(), 0d);
        assertEquals(beforeStorage, outpost.storage().snapshot());
    }

    @Test
    void sourceOutpostAndIndustrialStationStateCoexistInOneStage18Snapshot() {
        CadenceFixture fixture = fixture();
        Stage20GeneratedCampaignPersistentState saved = savedState(
                fixture, Stage18IndustrialState.empty(0L));
        var industry = Stage20IndustrialEntityMaterializer.materializeBootstrap(
                saved, fixture.specialization());
        Stage18IndustrialState industrialState = industry.captureIndustrialState(saved.industrialState());
        Stage20GeneratedCampaignPersistentState withIndustry = replaceIndustry(saved, industrialState);

        var sourceOutposts = Stage20SourceOutpostMaterializer.materialize(withIndustry);
        Stage18IndustrialState combined = sourceOutposts.captureIndustrialState(industrialState);

        assertTrue(combined.stationStorages().size() > industrialState.stationStorages().size());
        assertTrue(combined.facilities().size() > industrialState.facilities().size());
        assertEquals(industry.stations().size(), combined.stationStorages().stream()
                .filter(value -> industry.stations().stream()
                        .anyMatch(station -> station.stationId().equals(value.stationId())))
                .count());
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
                        "faction.stage20_5.source-outpost-materialization",
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
