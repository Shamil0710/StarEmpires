package com.spacesim.world.calibration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20CoordinatedFreightAcceptanceProfileTest {
    @Test
    void currentPolicyPreservesDerivedPhysicalCapacityAndExplicitSearchBound() {
        Stage20CoordinatedFreightAcceptanceProfile profile =
                Stage20CoordinatedFreightAcceptanceProfile.deriveCurrent();
        Stage20BootstrapFreightCapacityRequirementProfile capacity =
                Stage20BootstrapFreightCapacityRequirementProfile.deriveCurrent();

        assertEquals(Stage20CoordinatedFreightAcceptanceProfile.CURRENT_VERSION, profile.version());
        assertEquals(capacity, profile.freightCapacityRequirement());
        assertEquals(13, profile.requiredFreighterCountPerFactionStart());
        assertEquals(2_000, profile.searchNodeBudgetPerCommodity());
        assertTrue(profile.stage22ReviewRequired());
        assertTrue(profile.evidenceIds().stream().anyMatch(value -> value.contains("budget-2000:unresolved-0")));
    }

    @Test
    void searchBudgetMustRemainExplicitAndPositive() {
        Stage20BootstrapFreightCapacityRequirementProfile capacity =
                Stage20BootstrapFreightCapacityRequirementProfile.deriveCurrent();
        assertThrows(IllegalArgumentException.class, () -> new Stage20CoordinatedFreightAcceptanceProfile(
                "test",
                capacity,
                0,
                List.of("test:evidence"),
                capacity.stage22ReviewRequired()));
    }

    @Test
    void policyCannotDropCapacityReviewBoundary() {
        Stage20BootstrapFreightCapacityRequirementProfile capacity =
                Stage20BootstrapFreightCapacityRequirementProfile.deriveCurrent();
        assertThrows(IllegalArgumentException.class, () -> new Stage20CoordinatedFreightAcceptanceProfile(
                "test",
                capacity,
                1,
                List.of("test:evidence"),
                !capacity.stage22ReviewRequired()));
    }
}
