package com.spacesim.campaign;

import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistentState;
import com.spacesim.world.ProductionSmallCraftFixture;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
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
        var footprint = original.smallCraft().physicalFootprint(id);
        BayDefinition bay = new BayDefinition(
                new BayId("carrier:authority-test", "mission_primary"),
                HostKind.SHIP,
                new Dimensions3d(1_000d, 1_000d, 1_000d),
                footprint.envelopeVolumeM3() * 2d,
                footprint.currentMassKg() * 2d,
                1d);
        original.hangars().assign(id, bay, OccupancyState.SERVICING);

        Stage228GeneratedCampaignPersistentState saved = original.captureState();
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
