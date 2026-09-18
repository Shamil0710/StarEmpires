package com.spacesim.campaign;

import com.spacesim.persistence.Stage228GeneratedCampaignPersistentState;
import com.spacesim.persistence.Stage228HangarPersistentState;
import com.spacesim.world.ProductionSmallCraftFixture;
import com.spacesim.world.SmallCraftHangarCapacity.HostKind;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import com.spacesim.world.SmallCraftId;
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
        assertEquals(0, created.hangars().size());

        Stage228CampaignAuthority adopted = Stage228CampaignAuthority.restoreStage21(
                created.coordinator().captureState());
        assertEquals(0, adopted.smallCraft().size());
        assertEquals(1L, adopted.smallCraft().nextIdValue());
        assertTrue(adopted.captureState().smallCraft().craft().isEmpty());
        assertTrue(adopted.captureState().hangars().assignments().isEmpty());
    }

    @Test
    void campaignRoundTripPreservesIndividualPhysicalHangarOccupancy() {
        Stage228CampaignAuthority original = Stage228CampaignAuthority.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        SmallCraftId id = original.smallCraft().reserveIdentityForCompletedProduction();
        original.smallCraft().registerProducedCraft(ProductionSmallCraftFixture.craft(
                id, 8L, 80d, 4_000d, 1d, 100d));
        Stage228GeneratedCampaignPersistentState base = original.captureState();
        Stage228HangarPersistentState occupied = new Stage228HangarPersistentState(
                Stage228HangarPersistentState.CURRENT_VERSION,
                Stage228HangarPersistentState.CURRENT_RUNTIME_VERSION,
                Stage228HangarPersistentState.CURRENT_SEMANTIC_CONTRACT,
                java.util.List.of(new Stage228HangarPersistentState.AssignmentState(
                        id,
                        "carrier:authority-test",
                        "mission_primary",
                        HostKind.SHIP,
                        OccupancyState.SERVICING)));
        Stage228GeneratedCampaignPersistentState saved =
                Stage228GeneratedCampaignPersistentState.compose(
                        base.stage21Runtime(), base.smallCraft(), occupied);

        Stage228CampaignAuthority restored = Stage228CampaignAuthority.restore(saved);

        assertEquals(saved, restored.captureState());
        assertEquals(id, restored.hangars().snapshot().get(0).craftId());
        assertEquals(OccupancyState.SERVICING,
                restored.hangars().find(id).orElseThrow().state());
    }

    @Test
    void currentEnvelopeRoundTripPreservesAcceptedStage21AndEmptySidecarExactly() {
        Stage228CampaignAuthority original = Stage228CampaignAuthority.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        Stage228GeneratedCampaignPersistentState saved = original.captureState();
        Stage228CampaignAuthority restored = Stage228CampaignAuthority.restore(saved);

        assertEquals(saved, restored.captureState());
        assertEquals(original.coordinator().rootSeed(), restored.coordinator().rootSeed());
        assertEquals(0, restored.smallCraft().size());
        assertEquals(0, restored.hangars().size());
    }
}
