package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.TacticalSurvivalPlanner.DecisionReason;
import com.spacesim.ship.TacticalSurvivalPlanner.SurvivalAction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTacticalExitBehaviorMatrixAcceptanceTest {
    private static final long PARTIAL_AMMO_ALPHA = 191_304L;
    private static final long WITHDRAW_ALPHA = 191_100L;

    @Test
    void partialAmmunitionStartRemainsPhysicalAndIsConsumedByRealShots() {
        LiveTacticalBattleRuntimeState battle =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.mixed8v8());
        var source = battle.requireCombatant(PARTIAL_AMMO_ALPHA);
        LiveTacticalInitialReadinessService initial = new LiveTacticalInitialReadinessService();
        List<ConsumableLoad> originalLoads = ammunitionLoads(source);
        long fullItems = ammunitionCount(source);
        double fullMassKg = ammunitionMassKg(source);

        for (ConsumableLoad load : originalLoads) {
            assertTrue(load.itemCount() > 1L, "partial fixture requires a genuinely reducible physical feed");
            initial.retainAmmunitionItems(
                    source,
                    load.mountId(),
                    load.interfaceId(),
                    Math.max(1L, load.itemCount() / 2L));
        }

        long partialItems = ammunitionCount(source);
        double partialMassKg = ammunitionMassKg(source);
        assertTrue(partialItems > 0L && partialItems < fullItems,
                "partial fixture must start between empty and full finite inventory");
        assertTrue(partialMassKg > 0d && partialMassKg < fullMassKg,
                "partial item count must also reduce authoritative carried ammunition mass");

        LiveTacticalBattleWeaponRuntime weapons = new LiveTacticalBattleWeaponRuntime(
                new LiveTacticalBattleControlRuntime(battle));
        weapons.advanceOneTick();
        assertEquals(DecisionReason.READY,
                weapons.controlRuntime().controlState(PARTIAL_AMMO_ALPHA).survivalDecision().reason(),
                "non-zero partial inventory must not be classified as hard-zero depletion");

        for (int tick = 0; tick < 400 && weapons.shotsFired(PARTIAL_AMMO_ALPHA) == 0L; tick++) {
            weapons.advanceOneTick();
        }

        long fired = weapons.shotsFired(PARTIAL_AMMO_ALPHA);
        assertTrue(fired > 0L, "partial-ammo combatant must still be able to fire its physically retained rounds");
        assertEquals(fired, partialItems - ammunitionCount(source),
                "every source shot must remove exactly one item from the same central physical ammunition state");
        assertTrue(ammunitionMassKg(source) < partialMassKg,
                "firing from a partial start must remove real carried mass rather than only decrement telemetry");
    }

    @Test
    void authoredWithdrawalObjectiveOverridesHealthyEngagementAndMovesPhysicallyTowardSafePoint() {
        LiveTacticalBattleRuntimeState battle =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.balanced4v4());
        LiveTacticalBattleControlRuntime control = new LiveTacticalBattleControlRuntime(
                battle,
                Map.of(),
                Map.of(Side.ALPHA, TacticalBattleObjective.withdrawTo(-2_000d, 520d)));
        var actor = battle.requireCombatant(WITHDRAW_ALPHA);
        double initialX = actor.transform().position.x;
        double initialReactionMass = actor.engineering().runtimeState.consumables().reactionMassKg();

        control.advanceOneTick();
        var decision = control.controlState(WITHDRAW_ALPHA);
        assertEquals(SurvivalAction.RETREAT, decision.survivalDecision().action());
        assertEquals(DecisionReason.MISSION_WITHDRAWAL, decision.survivalDecision().reason());
        assertTrue(decision.movementAxisX() < 0d);
        assertEquals(0d, decision.movementAxisY(), 1e-12d);
        assertFalse(decision.fireAuthorized(),
                "authored withdrawal must suppress engagement fire even when tactical tracking requests it");
        assertEquals(TacticalBattleObjective.Kind.WITHDRAW_TO_POINT,
                control.battleObjective(Side.ALPHA).kind());
        assertEquals(TacticalBattleObjective.Kind.ENGAGE,
                control.battleObjective(Side.BETA).kind());

        for (int tick = 0; tick < 120; tick++) {
            control.advanceOneTick();
        }

        assertTrue(actor.transform().position.x < initialX,
                "withdrawal objective must create real displacement toward its authored safe point");
        assertTrue(actor.engineering().runtimeState.consumables().reactionMassKg() < initialReactionMass,
                "mission withdrawal must consume real reaction mass through production engineering/flight");
        assertEquals(SurvivalAction.RETREAT, control.controlState(WITHDRAW_ALPHA).survivalDecision().action());
        assertEquals(DecisionReason.MISSION_WITHDRAWAL,
                control.controlState(WITHDRAW_ALPHA).survivalDecision().reason());
        assertFalse(control.controlState(WITHDRAW_ALPHA).fireAuthorized());
    }

    @Test
    void authoredWithdrawalReplaysDeterministically() {
        LiveTacticalBattleControlRuntime first = withdrawalControl();
        LiveTacticalBattleControlRuntime second = withdrawalControl();

        for (int tick = 0; tick < 160; tick++) {
            first.advanceOneTick();
            second.advanceOneTick();
            assertEquals(first.fingerprint(), second.fingerprint());
            assertEquals(first.formationFingerprint(), second.formationFingerprint());
        }
    }

    @Test
    void sustainedThirtyTwoShipBattleHasNoImmediatePerTickDecisionPingPong() {
        LiveTacticalBattleDeceptionRuntime runtime = Stage19ScaledLiveTacticalFactory.createSaturation32();
        LiveTacticalBattleControlRuntime control = runtime.ordnanceRuntime().weaponRuntime().controlRuntime();
        Map<Long, ArrayList<OrderSignature>> history = new HashMap<>();
        for (var combatant : runtime.battleState().combatants()) {
            history.put(combatant.spec().entityId(), new ArrayList<>());
        }

        for (int tick = 0; tick < 240; tick++) {
            runtime.advanceOneTick();
            for (var combatant : runtime.battleState().combatants()) {
                long entityId = combatant.spec().entityId();
                var actor = control.controlState(entityId);
                var formation = control.formationState(entityId);
                history.get(entityId).add(new OrderSignature(
                        actor.intent().targetSelected() ? actor.intent().targetId() : 0L,
                        actor.survivalDecision().action(),
                        actor.survivalDecision().reason(),
                        actor.fireAuthorized(),
                        sign(actor.movementAxisX()),
                        sign(actor.movementAxisY()),
                        sign(formation.correctionAxisY())));
            }
        }

        for (Map.Entry<Long, ArrayList<OrderSignature>> entry : history.entrySet()) {
            List<OrderSignature> orders = entry.getValue();
            for (int index = 2; index < orders.size(); index++) {
                OrderSignature a = orders.get(index - 2);
                OrderSignature b = orders.get(index - 1);
                OrderSignature c = orders.get(index);
                String tickRange = " at ticks " + (index - 1) + ".." + (index + 1);
                assertFalse(a.equals(c) && !a.equals(b),
                        "uncontrolled fixed-tick A-B-A order churn for entity " + entry.getKey() + tickRange);
                assertFalse(strictFormationPingPong(a.formationCorrectionSign(),
                                b.formationCorrectionSign(), c.formationCorrectionSign()),
                        "per-tick formation correction ping-pong for entity " + entry.getKey() + tickRange);
            }
        }
    }

    private static LiveTacticalBattleControlRuntime withdrawalControl() {
        return new LiveTacticalBattleControlRuntime(
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.balanced4v4()),
                Map.of(),
                Map.of(Side.ALPHA, TacticalBattleObjective.withdrawTo(-2_000d, 520d)));
    }

    private static List<ConsumableLoad> ammunitionLoads(
            LiveTacticalBattleRuntimeState.CombatantRuntime combatant) {
        return combatant.engineering().runtimeState.consumables().interfaceLoads().stream()
                .filter(value -> value.kind() == InterfaceKind.AMMUNITION)
                .toList();
    }

    private static long ammunitionCount(LiveTacticalBattleRuntimeState.CombatantRuntime combatant) {
        return combatant.engineering().runtimeState.consumables().ammunitionCount();
    }

    private static double ammunitionMassKg(LiveTacticalBattleRuntimeState.CombatantRuntime combatant) {
        return combatant.engineering().runtimeState.consumables().ammunitionMassKg();
    }

    private static int sign(double value) {
        return Double.compare(value, 0d);
    }

    private static boolean strictFormationPingPong(int a, int b, int c) {
        return a != 0 && b != 0 && c != 0 && a == c && a == -b;
    }

    private record OrderSignature(
            long selectedTargetId,
            SurvivalAction survivalAction,
            DecisionReason survivalReason,
            boolean fireAuthorized,
            int movementXSign,
            int movementYSign,
            int formationCorrectionSign) {
    }
}
