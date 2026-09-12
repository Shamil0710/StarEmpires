package com.spacesim.campaign;

import com.spacesim.ui.GeneratedWorldUiModel;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedCampaignPresentationIntegrationTest {
    @Test
    void productionProjectionReadsTheSameRuntimeAdvancedByCampaignLifecycle() {
        GeneratedCampaignSession session = GeneratedCampaignSession.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        GeneratedWorldUiModel model = new GeneratedWorldUiModel(
                session.rootSeed(), session.runtime(), session.content());

        var before = model.capture();
        long beforeTick = session.runtime().world().getAuthoritativeWorldTick();
        assertEquals(beforeTick, before.worldTick());
        assertEquals(freightIds(session), projectedFreightIds(before));

        var report = session.advanceFrame(0.1f);
        var after = model.capture();

        assertTrue(report.fixedTicks() > 0L);
        assertTrue(after.worldTick() > before.worldTick());
        assertEquals(session.runtime().world().getAuthoritativeWorldTick(), after.worldTick());
        assertEquals(freightIds(session), projectedFreightIds(after));
    }

    @Test
    void restoredProductionProjectionKeepsPersistentFreightIdentity() {
        GeneratedCampaignSession original = GeneratedCampaignSession.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        original.advanceFrame(0.3f);
        List<Long> expectedIds = freightIds(original);

        GeneratedCampaignSession restored = GeneratedCampaignSession.restore(original.captureState());
        GeneratedWorldUiModel restoredModel = new GeneratedWorldUiModel(
                restored.rootSeed(), restored.runtime(), restored.content());

        assertEquals(expectedIds, freightIds(restored));
        assertEquals(expectedIds, projectedFreightIds(restoredModel.capture()));
    }

    private static List<Long> freightIds(GeneratedCampaignSession session) {
        return session.runtime().freight().capture().freighters().stream()
                .map(value -> value.fleetId().value())
                .sorted()
                .toList();
    }

    private static List<Long> projectedFreightIds(com.spacesim.ui.GeneratedWorldUiSnapshot snapshot) {
        return snapshot.freight().stream()
                .map(value -> value.fleetId())
                .sorted()
                .toList();
    }
}
