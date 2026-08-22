package com.spacesim.world.generation;

import com.spacesim.content.Stage18ExtractionCatalog.SourceKind;
import com.spacesim.persistence.Stage18IndustrialContentFingerprint;
import com.spacesim.persistence.Stage18IndustrialState;
import com.spacesim.persistence.Stage18IndustrialState.PhysicalSourceSnapshot;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistence;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState;
import com.spacesim.persistence.Stage20MaterializationPersistence;
import com.spacesim.persistence.Stage20MaterializationPersistentState;
import com.spacesim.persistence.Stage20SourceSupplyMaterializer;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.simulation.Stage20MaterializationService;
import com.spacesim.world.Stage20DiscoveryKnowledgeState;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.ResourceOccurrence;
import com.spacesim.world.Stage20SpecialLocationGenerator;
import com.spacesim.world.generation.Stage20OperationalIndustrialSpecializationProductionIntegrationTest
        .CadenceFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20SourceSupplyMaterializerTest {
    private static volatile CadenceFixture sharedFixture;

    @Test
    void canonicalSavedWorldMaterializesGeneratedSourcesAndSitesWithoutCargoGrant() {
        CadenceFixture fixture = fixture();
        Stage18IndustrialState emptyIndustry = Stage18IndustrialState.empty(0L);
        Stage20GeneratedCampaignPersistentState saved = savedState(fixture, emptyIndustry);

        var registry = Stage20SourceSupplyMaterializer.materialize(saved);
        var resourceWorld = fixture.resolved().generation().resourceWorld().orElseThrow();

        assertEquals(fixture.resolved().rootSeed(), registry.rootSeed());
        assertEquals(fixture.resolved().version(), registry.generatorVersion());
        assertEquals(saved.materializedWorld().worldFingerprint(), registry.worldFingerprint());
        assertEquals(resourceWorld.occurrences().size(), registry.sources().size());
        assertEquals(resourceWorld.initialExtractionSites().size(), registry.initialExtractionSites().size());
        assertTrue(saved.industrialState().stationStorages().isEmpty());

        for (ResourceOccurrence occurrence : resourceWorld.occurrences()) {
            var source = registry.source(occurrence.sourceId());
            assertEquals(occurrence.systemId(), source.systemId());
            assertEquals(occurrence.hostAnchorId(), source.hostAnchorId());
            assertEquals(occurrence.hostClassId(), source.hostClassId());
            assertEquals(occurrence.position(), source.position());
            assertEquals(occurrence.generationScore(), source.generationScore(), 0d);
            assertEquals(occurrence.initialAccessibleMassKg(),
                    source.sourceState().initialAccessibleMassKg(), 0d);
            assertEquals(occurrence.initialAccessibleMassKg(),
                    source.sourceState().remainingAccessibleMassKg(), 0d);
            assertEquals(occurrence.outputCommodityId(), source.sourceState().outputCommodityId());
            assertEquals(occurrence.requiredCapabilityTags(), source.sourceState().requiredCapabilityTags());
        }

        assertEquals(
                registry.captureSourceSnapshots(),
                Stage20SourceSupplyMaterializer.materialize(saved).captureSourceSnapshots());
    }

    @Test
    void savedIndustrialReserveRestoresExactRemainingMassWithoutRegeneration() {
        CadenceFixture fixture = fixture();
        ResourceOccurrence occurrence = fixture.resolved().generation().resourceWorld().orElseThrow()
                .occurrences().get(0);
        double remaining = occurrence.initialAccessibleMassKg() * 0.5d;
        PhysicalSourceSnapshot depleted = snapshot(occurrence, occurrence.sourceId(), remaining);
        Stage18IndustrialState industry = industry(List.of(depleted));
        Stage20GeneratedCampaignPersistentState saved = savedState(fixture, industry);

        var first = Stage20SourceSupplyMaterializer.materialize(saved);
        var repeated = Stage20SourceSupplyMaterializer.materialize(saved);

        assertEquals(remaining, first.source(occurrence.sourceId())
                .sourceState().remainingAccessibleMassKg(), 0d);
        assertEquals(first.captureSourceSnapshots(), repeated.captureSourceSnapshots());
        assertEquals(depleted, first.captureSourceSnapshots().stream()
                .filter(value -> value.sourceId().equals(occurrence.sourceId()))
                .findFirst().orElseThrow());
    }

    @Test
    void mismatchedCanonicalAndIndustrialRemainingReserveFailsClosed() {
        CadenceFixture fixture = fixture();
        Stage20GeneratedCampaignPersistentState canonical = savedState(
                fixture, Stage18IndustrialState.empty(0L));
        ResourceOccurrence occurrence = fixture.resolved().generation().resourceWorld().orElseThrow()
                .occurrences().get(0);
        PhysicalSourceSnapshot depleted = snapshot(
                occurrence, occurrence.sourceId(), occurrence.initialAccessibleMassKg() * 0.5d);
        Stage20GeneratedCampaignPersistentState inconsistent = replaceIndustry(
                canonical, industry(List.of(depleted)));

        assertThrows(IllegalArgumentException.class,
                () -> Stage20SourceSupplyMaterializer.materialize(inconsistent));
    }

    @Test
    void extraPersistedNaturalSourceAbsentFromCanonicalWorldFailsClosed() {
        CadenceFixture fixture = fixture();
        Stage20GeneratedCampaignPersistentState canonical = savedState(
                fixture, Stage18IndustrialState.empty(0L));
        ResourceOccurrence occurrence = fixture.resolved().generation().resourceWorld().orElseThrow()
                .occurrences().get(0);
        PhysicalSourceSnapshot forged = snapshot(
                occurrence, occurrence.sourceId() + ".forged", occurrence.initialAccessibleMassKg());
        Stage20GeneratedCampaignPersistentState inconsistent = replaceIndustry(
                canonical, industry(List.of(forged)));

        assertThrows(IllegalArgumentException.class,
                () -> Stage20SourceSupplyMaterializer.materialize(inconsistent));
    }

    private static PhysicalSourceSnapshot snapshot(
            ResourceOccurrence occurrence,
            String sourceId,
            double remainingAccessibleMassKg) {
        return new PhysicalSourceSnapshot(
                sourceId,
                SourceKind.NATURAL_OCCURRENCE,
                occurrence.occurrenceTypeId(),
                occurrence.environment(),
                occurrence.outputCommodityId(),
                occurrence.initialAccessibleMassKg(),
                remainingAccessibleMassKg,
                occurrence.gradeFraction(),
                occurrence.sourceRecoveryFraction(),
                occurrence.requiredCapabilityTags());
    }

    private static Stage18IndustrialState industry(List<PhysicalSourceSnapshot> sources) {
        return new Stage18IndustrialState(
                Stage18IndustrialState.CURRENT_VERSION,
                Stage18IndustrialContentFingerprint.current(),
                0L,
                sources,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
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
                        "faction.stage20_5.source-materialization",
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