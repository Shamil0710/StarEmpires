package com.spacesim.ship;

import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.TacticalFormationPlanner.FormationMode;
import com.spacesim.ship.TacticalFormationPlanner.FormationReason;
import com.spacesim.ship.TacticalFormationPlanner.FormationStatus;
import com.spacesim.ship.TacticalFormationPlanner.Objective;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTacticalFormationAcceptanceTest {
    private static final long RECOVERING_ALPHA = 191_101L;
    private static final long SURVIVAL_BREAK_ALPHA = 191_103L;
    private static final Objective COMPACT =
            new Objective(FormationMode.COMPACT, 700d, 120d, 5d, 80d);
    private static final Objective DISPERSED =
            new Objective(FormationMode.DISPERSED, 700d, 240d, 5d, 80d);

    @Test
    void shared4v4PhysicallyRecoversBrokenSlotWhileSurvivalOverrideBreaksAnotherActor() {
        LiveTacticalBattleRuntimeState battle =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.balanced4v4());
        new LiveTacticalInitialReadinessService().setModuleIntegrity(
                battle.requireCombatant(SURVIVAL_BREAK_ALPHA),
                "utility_datalink",
                0.10d);
        battle.requireCombatant(RECOVERING_ALPHA).transform().position.y += 90f;

        LiveTacticalBattleControlRuntime control = control(battle, COMPACT);
        double initialError = Math.abs(controlSlotErrorAfterOneTick(control, RECOVERING_ALPHA));

        var initiallyBroken = control.formationState(RECOVERING_ALPHA);
        assertEquals(FormationStatus.BROKEN, initiallyBroken.status());
        assertEquals(FormationReason.LARGE_SLOT_ERROR, initiallyBroken.reason());
        assertTrue(initiallyBroken.correctionAxisY() < 0d);

        var survivalBreak = control.formationState(SURVIVAL_BREAK_ALPHA);
        assertEquals(FormationStatus.BROKEN, survivalBreak.status());
        assertEquals(FormationReason.SURVIVAL_OVERRIDE, survivalBreak.reason());
        assertEquals(0d, survivalBreak.correctionAxisY(), 0d,
                "formation layer must not inject thrust while survival policy owns maneuver authority");

        boolean observedRecovering = false;
        boolean observedKeeping = false;
        double maximumLateError = 0d;
        for (int tick = 0; tick < 900; tick++) {
            control.advanceOneTick();
            var formation = control.formationState(RECOVERING_ALPHA);
            observedRecovering |= formation.status() == FormationStatus.RECOVERING;
            observedKeeping |= formation.status() == FormationStatus.KEEPING;
            if (tick >= 800) {
                maximumLateError = Math.max(maximumLateError, Math.abs(formation.errorM()));
            }
        }

        assertTrue(observedRecovering, "broken actor must pass through a physical recovery state");
        assertTrue(observedKeeping, "healthy actor must be able to regain its authored slot");
        assertTrue(Math.abs(control.formationState(RECOVERING_ALPHA).errorM()) < initialError,
                "formation recovery must reduce physical slot error rather than deadlock");
        assertTrue(maximumLateError <= COMPACT.breakDistanceM(),
                "late formation motion must stay bounded below the authored break distance rather than enter a large oscillation");
    }

    @Test
    void dispersed4v4ProducesWiderPhysicalFormationThanCompactThroughSameFlightRuntime() {
        LiveTacticalBattleRuntimeState compactBattle =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.balanced4v4());
        LiveTacticalBattleRuntimeState dispersedBattle =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.balanced4v4());
        LiveTacticalBattleControlRuntime compact = control(compactBattle, COMPACT);
        LiveTacticalBattleControlRuntime dispersed = control(dispersedBattle, DISPERSED);

        for (int tick = 0; tick < 240; tick++) {
            compact.advanceOneTick();
            dispersed.advanceOneTick();
        }

        double compactSpan = sideSpanY(compactBattle, Side.ALPHA);
        double dispersedSpan = sideSpanY(dispersedBattle, Side.ALPHA);
        assertTrue(dispersedSpan > compactSpan + 1d,
                "dispersed authored slots must create a materially wider physical line through production thrust/flight");
        assertTrue(dispersedBattle.requireCombatant(191_100L).transform().position.y < 520d);
        assertTrue(dispersedBattle.requireCombatant(191_103L).transform().position.y > 880d);
    }

    @Test
    void identicalFormationRunHasIdenticalControlAndFormationFingerprints() {
        LiveTacticalBattleRuntimeState firstBattle =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.balanced4v4());
        LiveTacticalBattleRuntimeState secondBattle =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.balanced4v4());
        firstBattle.requireCombatant(RECOVERING_ALPHA).transform().position.y += 90f;
        secondBattle.requireCombatant(RECOVERING_ALPHA).transform().position.y += 90f;
        LiveTacticalBattleControlRuntime first = control(firstBattle, COMPACT);
        LiveTacticalBattleControlRuntime second = control(secondBattle, COMPACT);

        for (int tick = 0; tick < 240; tick++) {
            first.advanceOneTick();
            second.advanceOneTick();
            assertEquals(first.fingerprint(), second.fingerprint());
            assertEquals(first.formationFingerprint(), second.formationFingerprint());
        }
    }

    private static double controlSlotErrorAfterOneTick(LiveTacticalBattleControlRuntime control, long entityId) {
        control.advanceOneTick();
        return control.formationState(entityId).errorM();
    }

    private static LiveTacticalBattleControlRuntime control(
            LiveTacticalBattleRuntimeState battle,
            Objective objective) {
        return new LiveTacticalBattleControlRuntime(
                battle,
                Map.of(Side.ALPHA, objective, Side.BETA, objective));
    }

    private static double sideSpanY(LiveTacticalBattleRuntimeState battle, Side side) {
        double minimum = battle.combatants().stream()
                .filter(value -> value.spec().side() == side)
                .mapToDouble(value -> value.transform().position.y)
                .min()
                .orElseThrow();
        double maximum = battle.combatants().stream()
                .filter(value -> value.spec().side() == side)
                .mapToDouble(value -> value.transform().position.y)
                .max()
                .orElseThrow();
        return maximum - minimum;
    }
}
