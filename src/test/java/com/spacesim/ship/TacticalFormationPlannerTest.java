package com.spacesim.ship;

import com.spacesim.ship.TacticalFormationPlanner.FormationMode;
import com.spacesim.ship.TacticalFormationPlanner.FormationReason;
import com.spacesim.ship.TacticalFormationPlanner.FormationStatus;
import com.spacesim.ship.TacticalFormationPlanner.Objective;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalFormationPlannerTest {
    private final TacticalFormationPlanner planner = new TacticalFormationPlanner();
    private final Objective compact = new Objective(FormationMode.COMPACT, 700d, 120d, 5d, 80d);

    @Test
    void stableSlotsAreCenteredFromCanonicalRosterIndex() {
        var first = planner.plan(compact, 0, 4, 520d, 0d, 4d, true);
        var second = planner.plan(compact, 1, 4, 640d, 0d, 4d, true);
        var third = planner.plan(compact, 2, 4, 760d, 0d, 4d, true);
        var fourth = planner.plan(compact, 3, 4, 880d, 0d, 4d, true);

        assertEquals(520d, first.desiredYM(), 0d);
        assertEquals(640d, second.desiredYM(), 0d);
        assertEquals(760d, third.desiredYM(), 0d);
        assertEquals(880d, fourth.desiredYM(), 0d);
        assertEquals(FormationStatus.KEEPING, first.status());
        assertEquals(FormationStatus.KEEPING, fourth.status());
    }

    @Test
    void largeErrorIsBrokenButRequestsPhysicalRecoveryTowardSlot() {
        var command = planner.plan(compact, 1, 4, 760d, 0d, 4d, true);

        assertEquals(FormationStatus.BROKEN, command.status());
        assertEquals(FormationReason.LARGE_SLOT_ERROR, command.reason());
        assertEquals(-120d, command.errorM(), 0d);
        assertEquals(-1d, command.correctionAxisY(), 0d);
    }

    @Test
    void brakingUsesPhysicalStoppingDistanceInsteadOfFlippingPastSlot() {
        var command = planner.plan(compact, 1, 4, 650d, -20d, 4d, true);

        assertEquals(FormationStatus.RECOVERING, command.status());
        assertEquals(1d, command.correctionAxisY(), 0d,
                "ship already moving toward lower-Y slot must accelerate opposite its velocity when stopping distance exceeds remaining error");
    }

    @Test
    void survivalOverrideBreaksFormationWithoutGrantingFormationThrust() {
        var command = planner.plan(compact, 1, 4, 760d, 0d, 4d, false);

        assertEquals(FormationStatus.BROKEN, command.status());
        assertEquals(FormationReason.SURVIVAL_OVERRIDE, command.reason());
        assertEquals(0d, command.correctionAxisY(), 0d);
    }

    @Test
    void dispersedObjectiveAuthorsGreaterPhysicalSlotSpacing() {
        Objective dispersed = new Objective(FormationMode.DISPERSED, 700d, 240d, 5d, 80d);
        var compactLeft = planner.plan(compact, 0, 4, 520d, 0d, 4d, true);
        var compactRight = planner.plan(compact, 3, 4, 880d, 0d, 4d, true);
        var dispersedLeft = planner.plan(dispersed, 0, 4, 520d, 0d, 4d, true);
        var dispersedRight = planner.plan(dispersed, 3, 4, 880d, 0d, 4d, true);

        double compactSpan = compactRight.desiredYM() - compactLeft.desiredYM();
        double dispersedSpan = dispersedRight.desiredYM() - dispersedLeft.desiredYM();
        assertTrue(dispersedSpan > compactSpan);
        assertEquals(360d, compactSpan, 0d);
        assertEquals(720d, dispersedSpan, 0d);
    }
}
