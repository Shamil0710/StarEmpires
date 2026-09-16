package com.spacesim.persistence;

import com.spacesim.campaign.GeneratedCampaignCoordinator;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.ship.ShipDamageRuntime;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipInstanceRuntimeState;
import com.spacesim.ship.ShipyardEngineeringService.MaintenanceState;
import com.spacesim.ship.WeaponLoadoutState;
import com.spacesim.ship.WeaponMountRuntime;
import com.spacesim.world.SmallCraftId;
import com.spacesim.world.SmallCraftRegistry;
import com.spacesim.world.SmallCraftState;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage228GeneratedCampaignPersistenceCodecTest {

    @Test
    void nativeBytesRoundTripNonEmptyIndividualCraftStateExactly() {
        GeneratedCampaignCoordinator coordinator = GeneratedCampaignCoordinator.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        SmallCraftRegistry registry = SmallCraftRegistry.empty();
        SmallCraftId id = registry.reserveIdentityForCompletedProduction();
        registry.registerProducedCraft(craft(id));
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
        SmallCraftRegistry restored = Stage228SmallCraftPersistenceMapper.restore(decoded.smallCraft());
        SmallCraftState restoredCraft = restored.find(id).orElseThrow();
        assertEquals(24L, restoredCraft.runtimeState().consumables().ammunitionCount());
        assertEquals(96d, restoredCraft.runtimeState().consumables().ammunitionMassKg());
        assertEquals(420d, restoredCraft.runtimeState().consumables().reactionMassKg());
        assertEquals(0.61d,
                restoredCraft.instanceState().damage().moduleDamage()
                        .moduleIntegrityByMount().get("mount.weapon"));
        assertEquals(135d,
                restoredCraft.instanceState().maintenance()
                        .secondsSinceServiceByMount().get("mount.drive"));
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

    private static SmallCraftState craft(SmallCraftId id) {
        ConsumableState consumables = new ConsumableState(
                0d,
                20d,
                0d,
                0d,
                List.of(
                        new ConsumableLoad(
                                "mount.weapon", "feed.main", InterfaceKind.AMMUNITION,
                                24d, 96d, 24L),
                        new ConsumableLoad(
                                "mount.drive", "tank.main", InterfaceKind.REACTION_MASS,
                                420d, 420d, 0L)));
        RuntimeState runtime = new RuntimeState(
                consumables,
                32_000d,
                1_600d,
                Map.of("mount.drive", 310d),
                Map.of("mount.drive", 22_000d),
                9_000d,
                Map.of());
        ShipInstanceRuntimeState instance = new ShipInstanceRuntimeState(
                new ShipDamageRuntime.Snapshot(
                        Map.of("compartment.core", 0.79d),
                        new DamageState(Map.of("mount.weapon", 0.61d))),
                Map.of(),
                new MaintenanceState(Map.of("mount.drive", 135d)),
                WeaponLoadoutState.empty(),
                WeaponMountRuntime.RuntimeState.empty());
        return new SmallCraftState(
                id,
                "faction.empire",
                "smallcraft.interceptor.disk-test-v1",
                new InstalledFit("hull.smallcraft.interceptor.disk-test", List.of()),
                runtime,
                instance);
    }
}
