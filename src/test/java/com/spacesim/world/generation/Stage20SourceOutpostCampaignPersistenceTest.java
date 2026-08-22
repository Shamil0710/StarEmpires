package com.spacesim.world.generation;

import com.spacesim.persistence.Stage18IndustrialState;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistence;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState;
import com.spacesim.persistence.Stage20MaterializationPersistence;
import com.spacesim.persistence.Stage20MaterializationPersistentState;
import com.spacesim.persistence.Stage20SourceOutpostCampaignPersistence;
import com.spacesim.persistence.Stage20SourceOutpostMaterializer;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.simulation.Stage20MaterializationService;
import com.spacesim.world.Stage20DiscoveryKnowledgeState;
import com.spacesim.world.Stage20SpecialLocationGenerator;
import com.spacesim.world.generation.Stage20OperationalIndustrialSpecializationProductionIntegrationTest.CadenceFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20SourceOutpostCampaignPersistenceTest {
    private static volatile CadenceFixture sharedFixture;

    @Test
    void extractionSaveRoundTripPreservesReserveCargoAndFingerprintBindingWithoutRegeneration() {
        CadenceFixture fixture = fixture();
        Stage20GeneratedCampaignPersistentState initial = savedState(fixture);
        var live = Stage20SourceOutpostMaterializer.materialize(initial);
        var outpost = live.outposts().get(0);
        double requestKg = Math.min(1d, outpost.source().sourceState().remainingAccessibleMassKg() * 0.001d);

        assertTrue(live.extract(outpost.site().siteId(), requestKg, 60d).committed());
        double expectedRemaining = outpost.source().sourceState().remainingAccessibleMassKg();
        var expectedStorage = outpost.storage().snapshot();

        Stage20GeneratedCampaignPersistentState captured =
                Stage20SourceOutpostCampaignPersistence.capture(initial, live);
        assertNotEquals(initial.materializedWorld().worldFingerprint(),
                captured.materializedWorld().worldFingerprint());
        assertEquals(captured.materializedWorld().worldFingerprint(),
                captured.discoveryState().worldFingerprint());

        var restored = Stage20SourceOutpostMaterializer.materialize(captured);
        var restoredOutpost = restored.outpost(outpost.site().siteId());
        assertEquals(expectedRemaining,
                restoredOutpost.source().sourceState().remainingAccessibleMassKg(), 0d);
        assertEquals(expectedStorage, restoredOutpost.storage().snapshot());
        assertEquals(captured.industrialState(),
                restored.captureIndustrialState(captured.industrialState()));
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
                        "faction.stage20_5.source-outpost-save",
                        List.of())));
    }

    private static synchronized CadenceFixture fixture() {
        if (sharedFixture == null) {
            sharedFixture = Stage20OperationalIndustrialSpecializationProductionIntegrationTest.cadenceFixture();
        }
        return sharedFixture;
    }
}
