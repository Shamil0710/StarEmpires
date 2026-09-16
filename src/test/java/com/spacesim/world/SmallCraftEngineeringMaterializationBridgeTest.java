package com.spacesim.world;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmallCraftEngineeringMaterializationBridgeTest {

    @Test
    void materializeAndCommitPreserveIdentityAndExactPhysicalContinuity() {
        SmallCraftRegistry registry = SmallCraftRegistry.empty(ProductionSmallCraftFixture.fitAuthority());
        SmallCraftId id = registry.reserveIdentityForCompletedProduction();
        SmallCraftState original = ProductionSmallCraftFixture.craft(
                id, 18L, 72d, 310d, 0.73d, 91d);
        registry.registerProducedCraft(original);

        EngineeringComponent materialized = registry.materializeEngineering(id);
        assertEquals(original.fit(), materialized.fit);
        assertEquals(original.runtimeState(), materialized.runtimeState);
        assertEquals(original.instanceState(), materialized.instanceState);
        assertEquals(original, registry.find(id).orElseThrow(),
                "materialization must not remove or mutate persistent identity state");

        SmallCraftState simulated = ProductionSmallCraftFixture.craft(
                id, 9L, 36d, 155d, 0.51d, 123d);
        materialized.setRuntimeState(simulated.runtimeState());
        materialized.setInstanceState(simulated.instanceState());
        registry.commitMaterializedEngineering(id, materialized);

        SmallCraftState committed = registry.find(id).orElseThrow();
        assertEquals(original.id(), committed.id());
        assertEquals(original.stableFactionId(), committed.stableFactionId());
        assertEquals(original.designId(), committed.designId());
        assertEquals(original.fit(), committed.fit());
        assertEquals(9L, committed.runtimeState().consumables().ammunitionCount());
        assertEquals(36d, committed.runtimeState().consumables().ammunitionMassKg());
        assertEquals(155d, committed.runtimeState().consumables().reactionMassKg());
        assertEquals(0.51d,
                committed.instanceState().damage().moduleDamage()
                        .moduleIntegrityByMount().get("weapon_primary"));
        assertEquals(123d,
                committed.instanceState().maintenance()
                        .secondsSinceServiceByMount().get("core_drive"));
    }

    @Test
    void materializedCommitFailsClosedOnUnknownIdentityOrFitDrift() {
        SmallCraftRegistry registry = SmallCraftRegistry.empty(ProductionSmallCraftFixture.fitAuthority());
        SmallCraftId id = registry.reserveIdentityForCompletedProduction();
        SmallCraftState original = ProductionSmallCraftFixture.craft(
                id, 18L, 72d, 310d, 0.73d, 91d);
        registry.registerProducedCraft(original);

        EngineeringComponent materialized = registry.materializeEngineering(id);
        materialized.fit = new InstalledFit(original.fit().hullId(), List.of());
        assertThrows(IllegalArgumentException.class,
                () -> registry.commitMaterializedEngineering(id, materialized));
        assertEquals(original, registry.find(id).orElseThrow(),
                "failed commit must leave persistent craft state unchanged");

        SmallCraftId unknown = new SmallCraftId(999L);
        assertThrows(IllegalArgumentException.class, () -> registry.materializeEngineering(unknown));
        assertThrows(IllegalArgumentException.class,
                () -> registry.commitMaterializedEngineering(unknown, new EngineeringComponent(
                        original.fit(), original.runtimeState(), original.instanceState())));
    }
}
