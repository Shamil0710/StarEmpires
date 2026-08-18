package com.spacesim.ship;

import com.spacesim.ship.TacticalSurvivalPlanner.DecisionReason;
import com.spacesim.ship.TacticalSurvivalPlanner.SurvivalAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTacticalDamagedDepleted8v8AcceptanceTest {
    private static final long ALPHA_DAMAGED_E = 191_304L;
    private static final long ALPHA_FRESH_E = 191_305L;
    private static final long BETA_DEPLETED_E = 191_400L;
    private static final long BETA_FRESH_E = 191_402L;

    @Test
    void physicalPreDamageAndFullReactionMassDepletionChange8v8SurvivalBehavior() {
        Fixture fixture = fixture();
        var control = fixture.runtime().ordnanceRuntime().weaponRuntime().controlRuntime();
        var damaged = fixture.battle().requireCombatant(ALPHA_DAMAGED_E);
        var freshAlpha = fixture.battle().requireCombatant(ALPHA_FRESH_E);
        var depleted = fixture.battle().requireCombatant(BETA_DEPLETED_E);
        var freshBeta = fixture.battle().requireCombatant(BETA_FRESH_E);
        double freshAlphaReactionMassBefore = reactionMassKg(freshAlpha);
        double freshBetaReactionMassBefore = reactionMassKg(freshBeta);

        fixture.runtime().advanceOneTick();

        var damagedControl = control.controlState(ALPHA_DAMAGED_E);
        var freshAlphaControl = control.controlState(ALPHA_FRESH_E);
        var depletedControl = control.controlState(BETA_DEPLETED_E);
        var freshBetaControl = control.controlState(BETA_FRESH_E);

        assertTrue(damagedControl.intent().targetSelected(),
                "damaged ship must still possess an actor-local target before survival policy overrides combat intent");
        assertEquals(SurvivalAction.DISENGAGE, damagedControl.survivalDecision().action());
        assertEquals(DecisionReason.SUBSYSTEM_DAMAGE, damagedControl.survivalDecision().reason());
        assertFalse(damagedControl.fireAuthorized(),
                "real subsystem degradation below the accepted readiness floor must suppress continued fire");
        assertEquals(0d, damagedControl.movementAxisX(), 0d);
        assertEquals(0d, damagedControl.movementAxisY(), 0d);
        assertEquals(0.10d,
                damaged.engineering().instanceState.damage().moduleDamage()
                        .moduleIntegrityByMount().get("utility_datalink"),
                0d);

        assertEquals(SurvivalAction.DISENGAGE, depletedControl.survivalDecision().action());
        assertEquals(DecisionReason.CANNOT_MANEUVER, depletedControl.survivalDecision().reason());
        assertFalse(depletedControl.fireAuthorized(),
                "physically empty maneuver reserves must not continue an engagement as if propulsion were available");
        assertEquals(0d, reactionMassKg(depleted), 0d);
        assertEquals(0d, depleted.transform().velocity.len2(), 0d);
        assertEquals(depleted.spec().xM(), depleted.transform().position.x, 0d);
        assertEquals(depleted.spec().yM(), depleted.transform().position.y, 0d);

        assertEquals(SurvivalAction.CONTINUE, freshAlphaControl.survivalDecision().action());
        assertEquals(DecisionReason.READY, freshAlphaControl.survivalDecision().reason());
        assertTrue(freshAlphaControl.intent().targetSelected());
        assertTrue(freshAlphaControl.movementAxisX() * freshAlphaControl.movementAxisX()
                        + freshAlphaControl.movementAxisY() * freshAlphaControl.movementAxisY() > 0d,
                "otherwise equivalent fresh E-fit must retain its ordinary intercept movement");
        assertTrue(reactionMassKg(freshAlpha) < freshAlphaReactionMassBefore);

        assertEquals(SurvivalAction.CONTINUE, freshBetaControl.survivalDecision().action());
        assertEquals(DecisionReason.READY, freshBetaControl.survivalDecision().reason());
        assertTrue(freshBetaControl.intent().targetSelected());
        assertTrue(reactionMassKg(freshBeta) < freshBetaReactionMassBefore);
    }

    @Test
    void sameDamagedAndDepleted8v8InitialStateReplaysDeterministically() {
        Fixture first = fixture();
        Fixture second = fixture();

        for (int index = 0; index < 80; index++) {
            first.runtime().advanceOneTick();
            second.runtime().advanceOneTick();
        }

        assertEquals(first.runtime().fingerprint(), second.runtime().fingerprint());
        assertEquals(
                first.battle().requireCombatant(ALPHA_DAMAGED_E).engineering().instanceState.damage(),
                second.battle().requireCombatant(ALPHA_DAMAGED_E).engineering().instanceState.damage());
        assertEquals(
                reactionMassKg(first.battle().requireCombatant(BETA_DEPLETED_E)),
                reactionMassKg(second.battle().requireCombatant(BETA_DEPLETED_E)),
                0d);
    }

    private static Fixture fixture() {
        LiveTacticalBattleRuntimeState battle =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.mixed8v8());
        LiveTacticalInitialReadinessService readiness = new LiveTacticalInitialReadinessService();
        readiness.setModuleIntegrity(battle.requireCombatant(ALPHA_DAMAGED_E), "utility_datalink", 0.10d);
        readiness.retainReactionMassFraction(battle.requireCombatant(BETA_DEPLETED_E), 0d);

        LiveTacticalBattleOrdnanceRuntime ordnance = new LiveTacticalBattleOrdnanceRuntime(
                new LiveTacticalBattleWeaponRuntime(
                        new LiveTacticalBattleControlRuntime(battle)));
        return new Fixture(battle, new LiveTacticalBattleDeceptionRuntime(ordnance));
    }

    private static double reactionMassKg(LiveTacticalBattleRuntimeState.CombatantRuntime combatant) {
        return combatant.engineering().runtimeState.consumables().reactionMassKg();
    }

    private record Fixture(
            LiveTacticalBattleRuntimeState battle,
            LiveTacticalBattleDeceptionRuntime runtime) {
    }
}
