package com.spacesim.persistence;

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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Stage228SmallCraftPersistenceTest {

    @Test
    void roundTripPreservesIndividualMaterialStateAndAllocatorWatermark() {
        SmallCraftRegistry registry = SmallCraftRegistry.empty();
        SmallCraftId firstId = registry.reserveIdentityForCompletedProduction();
        SmallCraftState original = craft(firstId);
        registry.registerProducedCraft(original);
        SmallCraftId intentionallyReservedButNotYetRegistered =
                registry.reserveIdentityForCompletedProduction();
        assertEquals(2L, intentionallyReservedButNotYetRegistered.value());

        Stage228SmallCraftPersistentState saved = Stage228SmallCraftPersistenceMapper.capture(registry);
        SmallCraftRegistry restored = Stage228SmallCraftPersistenceMapper.restore(saved);

        assertEquals(saved, Stage228SmallCraftPersistenceMapper.capture(restored));
        assertEquals(List.of(original), restored.snapshot());
        assertEquals(3L, restored.reserveIdentityForCompletedProduction().value(),
                "save/load must not reuse an identity that was already reserved before capture");

        SmallCraftState roundTripped = restored.find(firstId).orElseThrow();
        assertEquals(18L, roundTripped.runtimeState().consumables().ammunitionCount());
        assertEquals(72d, roundTripped.runtimeState().consumables().ammunitionMassKg());
        assertEquals(310d, roundTripped.runtimeState().consumables().reactionMassKg());
        assertEquals(0.73d,
                roundTripped.instanceState().damage().moduleDamage()
                        .moduleIntegrityByMount().get("mount.weapon"));
        assertEquals(91d,
                roundTripped.instanceState().maintenance()
                        .secondsSinceServiceByMount().get("mount.drive"));
    }

    @Test
    void persistentStateRejectsDuplicateIdentityAndWatermarkReuse() {
        SmallCraftState value = craft(new SmallCraftId(4L));
        Stage228SmallCraftPersistentState.CraftState row =
                Stage228SmallCraftPersistenceMapper.capture(
                        SmallCraftRegistry.restore(5L, List.of(value)))
                        .craft().get(0);

        assertThrows(IllegalArgumentException.class, () -> new Stage228SmallCraftPersistentState(
                Stage228SmallCraftPersistentState.CURRENT_VERSION,
                Stage228SmallCraftPersistentState.CURRENT_RUNTIME_VERSION,
                Stage228SmallCraftPersistentState.CURRENT_SEMANTIC_CONTRACT,
                5L,
                List.of(row, row)));
        assertThrows(IllegalArgumentException.class, () -> new Stage228SmallCraftPersistentState(
                Stage228SmallCraftPersistentState.CURRENT_VERSION,
                Stage228SmallCraftPersistentState.CURRENT_RUNTIME_VERSION,
                Stage228SmallCraftPersistentState.CURRENT_SEMANTIC_CONTRACT,
                4L,
                List.of(row)));
    }

    private static SmallCraftState craft(SmallCraftId id) {
        ConsumableState consumables = new ConsumableState(
                0d,
                14d,
                0d,
                0d,
                List.of(
                        new ConsumableLoad(
                                "mount.weapon", "feed.main", InterfaceKind.AMMUNITION,
                                18d, 72d, 18L),
                        new ConsumableLoad(
                                "mount.drive", "tank.main", InterfaceKind.REACTION_MASS,
                                310d, 310d, 0L)));
        RuntimeState runtime = new RuntimeState(
                consumables,
                25_000d,
                1_200d,
                Map.of("mount.drive", 240d),
                Map.of("mount.drive", 18_000d),
                8_000d,
                Map.of());
        ShipInstanceRuntimeState instance = new ShipInstanceRuntimeState(
                new ShipDamageRuntime.Snapshot(
                        Map.of("compartment.core", 0.84d),
                        new DamageState(Map.of("mount.weapon", 0.73d))),
                Map.of(),
                new MaintenanceState(Map.of("mount.drive", 91d)),
                WeaponLoadoutState.empty(),
                WeaponMountRuntime.RuntimeState.empty());
        return new SmallCraftState(
                id,
                "faction.empire",
                "smallcraft.interceptor.test-v1",
                new InstalledFit("hull.smallcraft.interceptor.test", List.of()),
                runtime,
                instance);
    }
}
