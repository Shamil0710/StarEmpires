package com.spacesim.persistence;

import com.spacesim.campaign.GeneratedCampaignCoordinator;
import com.spacesim.world.ProductionSmallCraftFixture;
import com.spacesim.world.SmallCraftFitAuthority;
import com.spacesim.world.SmallCraftId;
import com.spacesim.world.SmallCraftRegistry;
import com.spacesim.world.SmallCraftState;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage228GeneratedCampaignPersistenceCodecTest {

    @Test
    void nativeBytesRoundTripNonEmptyIndividualCraftStateExactly() {
        GeneratedCampaignCoordinator coordinator = GeneratedCampaignCoordinator.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        SmallCraftFitAuthority fitAuthority = ProductionSmallCraftFixture.fitAuthority();
        SmallCraftRegistry registry = SmallCraftRegistry.empty(fitAuthority);
        SmallCraftId id = registry.reserveIdentityForCompletedProduction();
        registry.registerProducedCraft(ProductionSmallCraftFixture.craft(
                id, 24L, 96d, 420d, 0.61d, 135d));
        registry.reserveIdentityForCompletedProduction();

        Stage228GeneratedCampaignPersistentState original =
                Stage228GeneratedCampaignPersistentState.compose(
                        coordinator.captureState(),
                        Stage228SmallCraftPersistenceMapper.capture(registry));

        byte[] first = Stage228GeneratedCampaignPersistenceCodec.encode(original);
        Stage228GeneratedCampaignPersistentState decoded =
                Stage228GeneratedCampaignPersistenceCodec.decode(first);
        byte[] second = Stage228GeneratedCampaignPersistenceCodec.encode(decoded);

        assertEquals(original, decoded);
        assertTrue(Arrays.equals(first, second), "native M22.8 encoding must be deterministic");
        SmallCraftRegistry restored = Stage228SmallCraftPersistenceMapper.restore(
                decoded.smallCraft(), fitAuthority);
        SmallCraftState restoredCraft = restored.find(id).orElseThrow();
        assertEquals(24L, restoredCraft.runtimeState().consumables().ammunitionCount());
        assertEquals(96d, restoredCraft.runtimeState().consumables().ammunitionMassKg());
        assertEquals(420d, restoredCraft.runtimeState().consumables().reactionMassKg());
        assertEquals(0.61d,
                restoredCraft.instanceState().damage().moduleDamage()
                        .moduleIntegrityByMount().get("weapon_primary"));
        assertEquals(135d,
                restoredCraft.instanceState().maintenance()
                        .secondsSinceServiceByMount().get("core_drive"));
        assertEquals(3L, restored.reserveIdentityForCompletedProduction().value(),
                "disk round-trip must preserve reserved allocator watermark");
    }

    @Test
    void stage21NativeBytesAdoptWithoutGrantingSmallCraft() {
        GeneratedCampaignCoordinator coordinator = GeneratedCampaignCoordinator.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        Stage21IGeneratedWorldRuntimePersistentState stage21 = coordinator.captureState();
        byte[] legacy = Stage21IGeneratedWorldRuntimePersistenceCodec.encode(stage21);

        Stage228GeneratedCampaignPersistentState adopted =
                Stage228GeneratedCampaignPersistenceCodec.decodeOrMigrate(legacy);

        assertEquals(stage21, adopted.stage21Runtime());
        assertTrue(adopted.smallCraft().craft().isEmpty());
        assertEquals(1L, adopted.smallCraft().nextCraftId());
    }

    @Test
    void nativeCodecRejectsTrailingAndTruncatedPayloads() {
        GeneratedCampaignCoordinator coordinator = GeneratedCampaignCoordinator.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        byte[] valid = Stage228GeneratedCampaignPersistenceCodec.encode(
                Stage228GeneratedCampaignPersistentState.compose(
                        coordinator.captureState(), Stage228SmallCraftPersistentState.empty()));

        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        byte[] truncated = Arrays.copyOf(valid, valid.length - 1);
        assertThrows(IllegalArgumentException.class,
                () -> Stage228GeneratedCampaignPersistenceCodec.decode(trailing));
        assertThrows(IllegalArgumentException.class,
                () -> Stage228GeneratedCampaignPersistenceCodec.decode(truncated));
    }
}
