package com.spacesim.world.generation;

import com.spacesim.persistence.Stage18IndustrialState;
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

        Stage20GeneratedCampaignPersistentState captured = runtime.captureCampaignState(initial);
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
