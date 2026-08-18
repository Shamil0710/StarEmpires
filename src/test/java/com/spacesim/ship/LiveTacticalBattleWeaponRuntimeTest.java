package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTacticalBattleWeaponRuntimeTest {
    @Test
    void allEight4v4CombatantsEventuallyFirePhysicalKineticBodiesFromVisibleTracks() {
        LiveTacticalBattleRuntimeState battle =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.balanced4v4());
        LiveTacticalBattleWeaponRuntime runtime = new LiveTacticalBattleWeaponRuntime(
                new LiveTacticalBattleControlRuntime(battle));
        Map<Long, Long> initialAmmunition = ammunitionByEntity(battle);

        for (int index = 0; index < 800 && !allCombatantsHaveFired(runtime); index++) {
            runtime.advanceOneTick();
        }

        assertTrue(allCombatantsHaveFired(runtime),
                "every combatant must reach production track/fire-control and materialize a physical shot");
        assertFalse(runtime.projectiles().isEmpty());
        for (var combatant : battle.combatants()) {
            long entityId = combatant.spec().entityId();
            long shots = runtime.shotsFired(entityId);
            long remaining = ammunitionRounds(combatant);
            assertTrue(shots > 0L);
            assertEquals(initialAmmunition.get(entityId) - shots, remaining,
                    "each materialized kinetic body must remove one itemized physical round");
            assertTrue(runtime.projectiles().stream().anyMatch(body -> body.sourceEntityId() == entityId),
                    "shared body set must retain the stable physical source identity");
            assertTrue(runtime.controlRuntime().controlState(entityId).intent().targetSelected());
        }
    }

    @Test
    void launcherCyclePreventsFreeEveryTickRefireAndPersistsPerCombatant() {
        LiveTacticalBattleRuntimeState battle =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.balanced4v4());
        LiveTacticalBattleWeaponRuntime runtime = new LiveTacticalBattleWeaponRuntime(
                new LiveTacticalBattleControlRuntime(battle));

        while (runtime.tick() < 800L && runtime.projectiles().isEmpty()) {
            runtime.advanceOneTick();
        }
        assertFalse(runtime.projectiles().isEmpty(), "at least one production shot should become possible");
        ProjectileBody first = runtime.projectiles().get(0);
        long sourceId = first.sourceEntityId();
        long shotsBefore = runtime.shotsFired(sourceId);
        var source = battle.requireCombatant(sourceId);
        assertFalse(source.engineering().instanceState.weaponMountRuntime().cooldownSecondsByMount().isEmpty(),
                "committed shot must start physical launcher cycle continuity");

        runtime.advanceOneTick();

        assertEquals(shotsBefore, runtime.shotsFired(sourceId),
                "a launcher still inside its physical cycle must not create a free next-tick round");
    }

    @Test
    void physicalProjectileBodiesMoveIndependentlyAfterMaterialization() {
        LiveTacticalBattleWeaponRuntime runtime = new LiveTacticalBattleWeaponRuntime(
                new LiveTacticalBattleControlRuntime(
                        new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.balanced4v4())));

        while (runtime.tick() < 800L && runtime.projectiles().isEmpty()) {
            runtime.advanceOneTick();
        }
        ProjectileBody observed = runtime.projectiles().stream().findFirst().orElse(null);
        assertNotNull(observed);
        long projectileId = observed.projectileId();
        double xBefore = observed.xM();
        double yBefore = observed.yM();

        runtime.advanceOneTick();
        ProjectileBody after = runtime.projectiles().stream()
                .filter(body -> body.projectileId() == projectileId)
                .findFirst()
                .orElseThrow();

        assertTrue(Math.hypot(after.xM() - xBefore, after.yM() - yBefore) > 0d,
                "shared battle projectile must advance as an independent production physical body");
    }

    @Test
    void identical4v4WeaponSessionsProduceIdenticalPhysicalBodyFingerprints() {
        LiveTacticalBattleWeaponRuntime first = new LiveTacticalBattleWeaponRuntime(
                new LiveTacticalBattleControlRuntime(
                        new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.balanced4v4())));
        LiveTacticalBattleWeaponRuntime second = new LiveTacticalBattleWeaponRuntime(
                new LiveTacticalBattleControlRuntime(
                        new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.balanced4v4())));

        for (int index = 0; index < 400; index++) {
            first.advanceOneTick();
            second.advanceOneTick();
        }

        assertEquals(first.fingerprint(), second.fingerprint(),
                "same 4v4 scenario/ticks must preserve deterministic AI, finite stores, cooldowns and bodies");
    }

    private static boolean allCombatantsHaveFired(LiveTacticalBattleWeaponRuntime runtime) {
        return runtime.battleState().combatants().stream()
                .allMatch(combatant -> runtime.shotsFired(combatant.spec().entityId()) > 0L);
    }

    private static Map<Long, Long> ammunitionByEntity(LiveTacticalBattleRuntimeState battle) {
        TreeMap<Long, Long> values = new TreeMap<>();
        for (var combatant : battle.combatants()) {
            values.put(combatant.spec().entityId(), ammunitionRounds(combatant));
        }
        return Map.copyOf(values);
    }

    private static long ammunitionRounds(LiveTacticalBattleRuntimeState.CombatantRuntime combatant) {
        return combatant.engineering().runtimeState.consumables().interfaceLoads().stream()
                .filter(value -> value.kind() == InterfaceKind.AMMUNITION)
                .mapToLong(ShipEngineeringState.ConsumableLoad::itemCount)
                .sum();
    }
}
