package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTacticalBattleControlRuntimeTest {
    @Test
    void balanced4v4UsesOneSharedActorBoundedControlAndPhysicalFlightTick() {
        LiveTacticalBattleRuntimeState battle =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.balanced4v4());
        LiveTacticalBattleControlRuntime runtime = new LiveTacticalBattleControlRuntime(battle);
        Map<Long, Double> initialReactionMass = reactionMassByEntity(battle);

        for (int index = 0; index < 120; index++) {
            runtime.advanceOneTick();
        }

        assertEquals(120L, runtime.tick());
        for (var combatant : battle.combatants()) {
            long entityId = combatant.spec().entityId();
            var contacts = battle.visibleContacts(entityId);
            var control = runtime.controlState(entityId);

            assertTrue(!contacts.isEmpty(),
                    "every balanced-control combatant should acquire at least one production hostile track");
            assertTrue(contacts.stream().allMatch(contact ->
                            battle.requireCombatant(contact.track().targetId()).spec().side()
                                    != combatant.spec().side()),
                    "actor-local information must not promote friendly/self contacts into hostile targets");
            assertTrue(control.intent().targetSelected(),
                    "production tactical policy should select from the actor-visible hostile domain");
            assertTrue(contacts.stream().anyMatch(contact ->
                            contact.track().targetId() == control.intent().targetId()),
                    "selected target must be present in the actor-visible TrackState domain");

            double displacement = Math.hypot(
                    combatant.transform().position.x - combatant.spec().xM(),
                    combatant.transform().position.y - combatant.spec().yM());
            assertTrue(displacement > 0d || combatant.transform().velocity.len2() > 0f,
                    "AI intent must reach the shared production flight integrator");
            assertTrue(reactionMassKg(combatant) < initialReactionMass.get(entityId),
                    "physical maneuver must consume that combatant's finite reaction mass");
        }
    }

    @Test
    void same4v4FixedTicksProduceIdenticalWholeBattleControlFingerprint() {
        LiveTacticalBattleControlRuntime first = new LiveTacticalBattleControlRuntime(
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.balanced4v4()));
        LiveTacticalBattleControlRuntime second = new LiveTacticalBattleControlRuntime(
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.balanced4v4()));

        for (int index = 0; index < 240; index++) {
            first.advanceOneTick();
            second.advanceOneTick();
        }

        assertEquals(first.fingerprint(), second.fingerprint(),
                "same scenario and fixed-tick schedule must preserve deterministic 4v4 control/flight state");
    }

    @Test
    void readOnlyControlQueriesDoNotAdvanceBattleTimeOrPhysicalState() {
        LiveTacticalBattleControlRuntime runtime = new LiveTacticalBattleControlRuntime(
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.balanced4v4()));
        var before = runtime.fingerprint();

        runtime.battleState();
        for (var combatant : runtime.battleState().combatants()) {
            runtime.controlState(combatant.spec().entityId());
            runtime.battleState().visibleContacts(combatant.spec().entityId());
        }

        assertEquals(0L, runtime.tick());
        assertEquals(before, runtime.fingerprint());
    }

    private static Map<Long, Double> reactionMassByEntity(LiveTacticalBattleRuntimeState battle) {
        TreeMap<Long, Double> values = new TreeMap<>();
        for (var combatant : battle.combatants()) {
            values.put(combatant.spec().entityId(), reactionMassKg(combatant));
        }
        return Map.copyOf(values);
    }

    private static double reactionMassKg(LiveTacticalBattleRuntimeState.CombatantRuntime combatant) {
        return combatant.engineering().runtimeState.consumables().interfaceLoads().stream()
                .filter(value -> value.kind() == InterfaceKind.REACTION_MASS)
                .mapToDouble(ShipEngineeringState.ConsumableLoad::massKg)
                .sum();
    }
}
