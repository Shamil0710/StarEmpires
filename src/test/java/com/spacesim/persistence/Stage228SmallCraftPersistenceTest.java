package com.spacesim.persistence;

import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.world.ProductionSmallCraftFixture;
import com.spacesim.world.SmallCraftFitAuthority;
import com.spacesim.world.SmallCraftId;
import com.spacesim.world.SmallCraftRegistry;
import com.spacesim.world.SmallCraftState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Stage228SmallCraftPersistenceTest {

    @Test
    void roundTripPreservesIndividualMaterialStateAndAllocatorWatermark() {
        SmallCraftFitAuthority fitAuthority = ProductionSmallCraftFixture.fitAuthority();
        SmallCraftRegistry registry = SmallCraftRegistry.empty(fitAuthority);
        SmallCraftId firstId = registry.reserveIdentityForCompletedProduction();
        SmallCraftState original = ProductionSmallCraftFixture.craft(
                firstId, 18L, 72d, 310d, 0.73d, 91d);
        registry.registerProducedCraft(original);
        SmallCraftId intentionallyReservedButNotYetRegistered =
                registry.reserveIdentityForCompletedProduction();
        assertEquals(2L, intentionallyReservedButNotYetRegistered.value());

        Stage228SmallCraftPersistentState saved = Stage228SmallCraftPersistenceMapper.capture(registry);
        SmallCraftRegistry restored = Stage228SmallCraftPersistenceMapper.restore(saved, fitAuthority);

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
                        .moduleIntegrityByMount().get("weapon_primary"));
        assertEquals(91d,
                roundTripped.instanceState().maintenance()
                        .secondsSinceServiceByMount().get("core_drive"));
    }

    @Test
    void persistentStateRejectsDuplicateIdentityAndWatermarkReuse() {
        SmallCraftFitAuthority fitAuthority = ProductionSmallCraftFixture.fitAuthority();
        SmallCraftState value = ProductionSmallCraftFixture.craft(
                new SmallCraftId(4L), 18L, 72d, 310d, 0.73d, 91d);
        Stage228SmallCraftPersistentState.CraftState row =
                Stage228SmallCraftPersistenceMapper.capture(
                        SmallCraftRegistry.restore(5L, List.of(value), fitAuthority))
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

    @Test
    void restoreAndReplacementFailClosedOnUnknownOrDriftedProductionFit() {
        SmallCraftFitAuthority fitAuthority = ProductionSmallCraftFixture.fitAuthority();
        SmallCraftRegistry registry = SmallCraftRegistry.empty(fitAuthority);
        SmallCraftId id = registry.reserveIdentityForCompletedProduction();
        SmallCraftState valid = ProductionSmallCraftFixture.craft(
                id, 18L, 72d, 310d, 0.73d, 91d);
        registry.registerProducedCraft(valid);

        SmallCraftState driftedFit = new SmallCraftState(
                valid.id(),
                valid.stableFactionId(),
                valid.designId(),
                new InstalledFit(valid.fit().hullId(), List.of()),
                valid.runtimeState(),
                valid.instanceState());
        assertThrows(IllegalArgumentException.class, () -> registry.replacePhysicalState(driftedFit));

        Stage228SmallCraftPersistentState saved = Stage228SmallCraftPersistenceMapper.capture(registry);
        Stage228SmallCraftPersistentState.CraftState row = saved.craft().get(0);
        Stage228SmallCraftPersistentState malformed = new Stage228SmallCraftPersistentState(
                Stage228SmallCraftPersistentState.CURRENT_VERSION,
                Stage228SmallCraftPersistentState.CURRENT_RUNTIME_VERSION,
                Stage228SmallCraftPersistentState.CURRENT_SEMANTIC_CONTRACT,
                saved.nextCraftId(),
                List.of(new Stage228SmallCraftPersistentState.CraftState(
                        row.id(),
                        row.stableFactionId(),
                        "fit.missing.smallcraft.v1",
                        row.engineering())));

        assertThrows(IllegalArgumentException.class,
                () -> Stage228SmallCraftPersistenceMapper.restore(malformed, fitAuthority));
    }
}
