package com.spacesim.world.generation;

import com.spacesim.world.Stage20BootstrapFreightOwnershipPlan;
import com.spacesim.world.Stage20BootstrapFreightOwnershipPlan.CommitmentSlot;
import com.spacesim.world.Stage20BootstrapFreightOwnershipPlan.OwnershipReport;
import com.spacesim.world.Stage20BootstrapFreightPhysicalPlan;
import com.spacesim.world.Stage20GeneratedWorldSeedAcceptance;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20BootstrapFreightOwnershipPlanProductionIntegrationTest {
    @Test
    void acceptedProductionPhysicalPlanBecomesExactDeterministicOwnershipSlots() {
        var resolved = Stage20ResolvedGeneratedWorldProductionProbe.runCurrent(1L);
        var acceptance = resolved.coordinatedFreightAcceptance().orElseThrow();
        var physical = Stage20BootstrapFreightPhysicalPlan.reconstruct(acceptance);
        var placement = resolved.generation().placement().orElseThrow();

        OwnershipReport ownership = Stage20BootstrapFreightOwnershipPlan.plan(resolved);
        OwnershipReport repeated = Stage20BootstrapFreightOwnershipPlan.plan(resolved);

        assertEquals(Stage20GeneratedWorldSeedAcceptance.Status.ACCEPTED,
                resolved.seedAcceptance().status());
        assertEquals(1L, ownership.rootSeed());
        assertEquals(resolved.rootSeed(), ownership.physicalPlan().rootSeed());
        assertEquals(placement.profileVersion(), ownership.placementProfileVersion());
        assertEquals(physical, ownership.physicalPlan());
        assertEquals(ownership, repeated);
        assertEquals(physical.remoteFreighterBudgetByFaction().values().stream()
                        .mapToInt(Integer::intValue).sum(),
                ownership.totalOwnedFreighters());
        assertEquals(physical.remoteFreightersByFaction().values().stream()
                        .mapToInt(Integer::intValue).sum(),
                ownership.totalCommittedFreighters());

        var allSlots = ownership.factions().stream()
                .flatMap(value -> value.materializationSlots().stream())
                .toList();
        assertEquals(ownership.totalOwnedFreighters(), allSlots.size());
        var committedSlots = allSlots.stream()
                .flatMap(value -> value.commitment().stream())
                .toList();
        assertEquals(ownership.totalCommittedFreighters(), committedSlots.size());
        assertEquals(committedSlots.size(), new HashSet<>(committedSlots).size());
        assertTrue(committedSlots.stream().map(CommitmentSlot::commitmentKey).allMatch(key ->
                physical.commodities().stream().anyMatch(commodity ->
                        commodity.commodityId().equals(key.commodityId())
                                && commodity.frontierVersion().equals(key.frontierVersion())
                                && commodity.optionId().equals(key.optionId()))));
    }
}
