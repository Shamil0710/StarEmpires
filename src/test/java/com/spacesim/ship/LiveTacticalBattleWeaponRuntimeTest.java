package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
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
        for (var combatant : battle.combatants()) {
            long entityId = combatant.spec().entityId();
            long shots = runtime.shotsFired(entityId);
            long remaining = ammunitionRounds(combatant);
            assertTrue(shots > 0L);
            assertEquals(initialAmmunition.get(entityId) - shots, remaining,
                    "each materialized kinetic body must remove one itemized physical round");
            assertTrue(runtime.controlRuntime().controlState(entityId).intent().targetSelected());
        }
        assertTrue(runtime.projectiles().stream().allMatch(body ->
                        battle.requireCombatant(body.sourceEntityId()) != null),
                "every surviving body must retain a valid stable physical source identity");
    }

    @Test
    void launcherCyclePreventsFreeEveryTickRefireAndPersistsPerCombatant() {
        LiveTacticalBattleRuntimeState battle =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.balanced4v4());
        LiveTacticalBattleWeaponRuntime runtime = new LiveTacticalBattleWeaponRuntime(
                new LiveTacticalBattleControlRuntime(battle));

        while (runtime.tick() < 800L && totalShots(runtime) == 0L) {
            runtime.advanceOneTick();
        }
        assertTrue(totalShots(runtime) > 0L, "at least one production shot should become possible");
        long sourceId = battle.combatants().stream()
                .mapToLong(combatant -> combatant.spec().entityId())
                .filter(entityId -> runtime.shotsFired(entityId) > 0L)
                .findFirst()
                .orElseThrow();
        long shotsBefore = runtime.shotsFired(sourceId);
        var source = battle.requireCombatant(sourceId);
        assertFalse(source.engineering().instanceState.weaponMountRuntime().cooldownSecondsByMount().isEmpty(),
                "committed shot must start physical launcher cycle continuity");

        runtime.advanceOneTick();

        assertEquals(shotsBefore, runtime.shotsFired(sourceId),
                "a launcher still inside its physical cycle must not create a free next-tick round");
    }

    @Test
    void physicalProjectileBodiesMoveOrResolveARealImpactAfterMaterialization() {
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
        long impactsBefore = runtime.totalImpacts();

        runtime.advanceOneTick();
        ProjectileBody after = runtime.projectiles().stream()
                .filter(body -> body.projectileId() == projectileId)
                .findFirst()
                .orElse(null);

        if (after != null) {
            assertTrue(Math.hypot(after.xM() - xBefore, after.yM() - yBefore) > 0d,
                    "surviving shared battle projectile must advance as an independent physical body");
        } else {
            assertTrue(runtime.totalImpacts() > impactsBefore,
                    "a body may disappear only through the shared physical protection path");
        }
    }

    @Test
    void shared4v4SweptCollisionReachesProductionProtectionOnBothSides() {
        LiveTacticalBattleRuntimeState battle =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.balanced4v4());
        LiveTacticalBattleWeaponRuntime runtime = new LiveTacticalBattleWeaponRuntime(
                new LiveTacticalBattleControlRuntime(battle));
        Map<Long, Double> initialShieldReserve = shieldReserveByEntity(battle);

        for (int index = 0; index < 1_600 && !bothSidesHaveImpacts(runtime); index++) {
            runtime.advanceOneTick();
        }

        assertTrue(runtime.totalImpacts() > 0L,
                "shared physical projectile bodies must intersect production hull geometry");
        assertTrue(bothSidesHaveImpacts(runtime),
                "symmetric 4v4 acceptance must resolve physical incoming fire on both battle sides");
        assertTrue(battle.combatants().stream().anyMatch(combatant ->
                        shieldReserve(combatant) < initialShieldReserve.get(combatant.spec().entityId())
                                || meanIntegrity(combatant) < 1d),
                "production shield/material/damage state must change after shared physical impacts");
    }

    @Test
    void identical4v4ImpactSessionsProduceIdenticalProtectionAndBodyFingerprints() {
        LiveTacticalBattleWeaponRuntime first = new LiveTacticalBattleWeaponRuntime(
                new LiveTacticalBattleControlRuntime(
                        new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.balanced4v4())));
        LiveTacticalBattleWeaponRuntime second = new LiveTacticalBattleWeaponRuntime(
                new LiveTacticalBattleControlRuntime(
                        new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.balanced4v4())));

        for (int index = 0; index < 600; index++) {
            first.advanceOneTick();
            second.advanceOneTick();
        }

        assertEquals(first.fingerprint(), second.fingerprint(),
                "same 4v4 ticks must preserve deterministic AI, stores, cooldowns, bodies and protection state");
    }

    private static boolean allCombatantsHaveFired(LiveTacticalBattleWeaponRuntime runtime) {
        return runtime.battleState().combatants().stream()
                .allMatch(combatant -> runtime.shotsFired(combatant.spec().entityId()) > 0L);
    }

    private static long totalShots(LiveTacticalBattleWeaponRuntime runtime) {
        return runtime.battleState().combatants().stream()
                .mapToLong(combatant -> runtime.shotsFired(combatant.spec().entityId()))
                .sum();
    }

    private static boolean bothSidesHaveImpacts(LiveTacticalBattleWeaponRuntime runtime) {
        boolean alpha = runtime.battleState().scenario().combatantsFor(Side.ALPHA).stream()
                .anyMatch(spec -> runtime.impactsOn(spec.entityId()) > 0L);
        boolean beta = runtime.battleState().scenario().combatantsFor(Side.BETA).stream()
                .anyMatch(spec -> runtime.impactsOn(spec.entityId()) > 0L);
        return alpha && beta;
    }

    private static Map<Long, Long> ammunitionByEntity(LiveTacticalBattleRuntimeState battle) {
        TreeMap<Long, Long> values = new TreeMap<>();
        for (var combatant : battle.combatants()) {
            values.put(combatant.spec().entityId(), ammunitionRounds(combatant));
        }
        return Map.copyOf(values);
    }

    private static Map<Long, Double> shieldReserveByEntity(LiveTacticalBattleRuntimeState battle) {
        TreeMap<Long, Double> values = new TreeMap<>();
        for (var combatant : battle.combatants()) {
            values.put(combatant.spec().entityId(), shieldReserve(combatant));
        }
        return Map.copyOf(values);
    }

    private static long ammunitionRounds(LiveTacticalBattleRuntimeState.CombatantRuntime combatant) {
        return combatant.engineering().runtimeState.consumables().interfaceLoads().stream()
                .filter(value -> value.kind() == InterfaceKind.AMMUNITION)
                .mapToLong(ShipEngineeringState.ConsumableLoad::itemCount)
                .sum();
    }

    private static double shieldReserve(LiveTacticalBattleRuntimeState.CombatantRuntime combatant) {
        return combatant.engineering().instanceState.shieldStatesByMount().values().stream()
                .mapToDouble(ShieldFieldRuntime.State::reserveJ)
                .sum();
    }

    private static double meanIntegrity(LiveTacticalBattleRuntimeState.CombatantRuntime combatant) {
        var damage = combatant.engineering().instanceState.damage();
        return combatant.hull().compartments().stream()
                .mapToDouble(compartment -> damage.compartmentIntegrityById()
                        .getOrDefault(compartment.id(), 1d))
                .average()
                .orElse(1d);
    }
}
