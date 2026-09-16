package com.spacesim.campaign;

import com.spacesim.persistence.Stage228GeneratedCampaignPersistentState;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage228CampaignAuthorityTest {

    @Test
    void newCampaignAndStage21AdoptionNeverSeedFreeSmallCraft() {
        Stage228CampaignAuthority created = Stage228CampaignAuthority.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        assertEquals(0, created.smallCraft().size());
        assertEquals(1L, created.smallCraft().nextIdValue());

        Stage228CampaignAuthority adopted = Stage228CampaignAuthority.restoreStage21(
                created.coordinator().captureState());
        assertEquals(0, adopted.smallCraft().size());
        assertEquals(1L, adopted.smallCraft().nextIdValue());
        assertTrue(adopted.captureState().smallCraft().craft().isEmpty());
    }

    @Test
    void currentEnvelopeRoundTripPreservesAcceptedStage21AndEmptySidecarExactly() {
        Stage228CampaignAuthority original = Stage228CampaignAuthority.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        Stage228GeneratedCampaignPersistentState saved = original.captureState();
        Stage228CampaignAuthority restored = Stage228CampaignAuthority.restore(saved);

        assertEquals(saved, restored.captureState());
        assertEquals(original.rootSeed(), restored.rootSeed());
        assertEquals(0, restored.smallCraft().size());
    }
}
