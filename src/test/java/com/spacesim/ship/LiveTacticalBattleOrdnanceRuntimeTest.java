package com.spacesim.ship;

import com.spacesim.ship.LiveTacticalBattleScenario.CombatantSpec;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTacticalBattleOrdnanceRuntimeTest {
    @Test
    void allEightMissileCombatantsLaunchPhysicalGuidedBodiesFromActorVisibleTracks() {
        LiveTacticalBattleRuntimeState battle = new LiveTacticalBattleRuntimeState(missile4v4());
        LiveTacticalBattleOrdnanceRuntime runtime = runtime(battle);
        Map<Long, Long> initialGuidedRounds = guidedRoundsByEntity(battle);

        for (int index = 0; index < 800 && !allCombatantsHaveLaunched(runtime); index++) {
            runtime.advanceOneTick();
        }

        assertTrue(allCombatantsHaveLaunched(runtime),
                "every missile combatant must reach production track/fire authorization and launch a physical body");
        assertFalse(runtime.guidedBodies().isEmpty());
        for (var combatant : battle.combatants()) {
            long entityId = combatant.spec().entityId();
            long launches = runtime.guidedLaunches(entityId);
            assertTrue(launches > 0L);
            assertEquals(initialGuidedRounds.get(entityId) - launches, guidedRounds(combatant),
                    "each physical guided launch must remove exactly one itemized guided-feed round");
            assertTrue(runtime.guidedBodies().stream().anyMatch(body -> body.sourceEntityId() == entityId));
        }
    }

    @Test
    void guidanceConsumesPhysicalPropellantAndChangesBodyMotion() {
        LiveTacticalBattleOrdnanceRuntime runtime = runtime(
                new LiveTacticalBattleRuntimeState(missile4v4()));

        while (runtime.tick() < 800L && runtime.guidedBodies().isEmpty()) {
            runtime.advanceOneTick();
        }
        GuidedWeaponBody first = runtime.guidedBodies().stream().findFirst().orElseThrow();
        double propellantBefore = first.remainingPropellantKg();
        double xBefore = first.xM();
        double yBefore = first.yM();
        long bodyId = first.bodyId();

        runtime.advanceOneTick();

        GuidedWeaponBody after = runtime.guidedBodies().stream()
                .filter(body -> body.bodyId() == bodyId)
                .findFirst()
                .orElseThrow();
        assertTrue(after.remainingPropellantKg() < propellantBefore,
                "production guidance must consume real onboard propellant");
        assertTrue(Math.hypot(after.xM() - xBefore, after.yM() - yBefore) > 0d,
                "guided body must propagate as independent physical mass");
        assertTrue(after.speedMps() > 0d);
    }

    @Test
    void physicalLauncherCycleAndSupportChannelsBoundGuidedSalvoGrowth() {
        LiveTacticalBattleRuntimeState battle = new LiveTacticalBattleRuntimeState(missile4v4());
        LiveTacticalBattleOrdnanceRuntime runtime = runtime(battle);

        for (int index = 0; index < 2_000; index++) {
            runtime.advanceOneTick();
        }

        Map<String, Integer> activeBySourceAndMount = new HashMap<>();
        for (var body : runtime.fingerprint().bodies()) {
            String key = body.sourceEntityId() + "|" + body.launchMountId();
            activeBySourceAndMount.merge(key, 1, Integer::sum);
        }
        assertFalse(activeBySourceAndMount.isEmpty());
        assertTrue(activeBySourceAndMount.values().stream().allMatch(count -> count <= 12),
                "authored launcher support channels must cap simultaneous datalink-guided bodies per mount");
        for (var combatant : battle.combatants()) {
            assertTrue(runtime.guidedLaunches(combatant.spec().entityId()) <= 24L,
                    "two 12-channel missile mounts cannot accumulate unlimited supported guided bodies");
            assertFalse(combatant.engineering().instanceState.weaponMountRuntime()
                    .cooldownSecondsByMount().isEmpty(),
                    "guided launch must use persistent production launcher-cycle continuity");
        }
    }

    @Test
    void identicalGuided4v4SessionsProduceIdenticalPhysicalFingerprints() {
        LiveTacticalBattleOrdnanceRuntime first = runtime(
                new LiveTacticalBattleRuntimeState(missile4v4()));
        LiveTacticalBattleOrdnanceRuntime second = runtime(
                new LiveTacticalBattleRuntimeState(missile4v4()));

        for (int index = 0; index < 500; index++) {
            first.advanceOneTick();
            second.advanceOneTick();
        }

        assertEquals(first.fingerprint(), second.fingerprint(),
                "same scenario/ticks must preserve deterministic ship combat, guided stores, bodies and propulsion");
    }

    private static LiveTacticalBattleOrdnanceRuntime runtime(LiveTacticalBattleRuntimeState battle) {
        return new LiveTacticalBattleOrdnanceRuntime(
                new LiveTacticalBattleWeaponRuntime(
                        new LiveTacticalBattleControlRuntime(battle)));
    }

    private static LiveTacticalBattleScenario missile4v4() {
        ArrayList<CombatantSpec> roster = new ArrayList<>(8);
        double[] yPositions = {520d, 640d, 760d, 880d};
        for (int index = 0; index < yPositions.length; index++) {
            roster.add(new CombatantSpec(
                    193_100L + index,
                    Side.ALPHA,
                    DoctrineId.B_MISSILE_STRIKE,
                    260d,
                    yPositions[index]));
            roster.add(new CombatantSpec(
                    193_200L + index,
                    Side.BETA,
                    DoctrineId.B_MISSILE_STRIKE,
                    1_690d,
                    yPositions[index]));
        }
        return new LiveTacticalBattleScenario(roster);
    }

    private static boolean allCombatantsHaveLaunched(LiveTacticalBattleOrdnanceRuntime runtime) {
        return runtime.battleState().combatants().stream()
                .allMatch(combatant -> runtime.guidedLaunches(combatant.spec().entityId()) > 0L);
    }

    private static Map<Long, Long> guidedRoundsByEntity(LiveTacticalBattleRuntimeState battle) {
        TreeMap<Long, Long> values = new TreeMap<>();
        for (var combatant : battle.combatants()) {
            values.put(combatant.spec().entityId(), guidedRounds(combatant));
        }
        return Map.copyOf(values);
    }

    private static long guidedRounds(LiveTacticalBattleRuntimeState.CombatantRuntime combatant) {
        return combatant.engineering().runtimeState.consumables().interfaceLoads().stream()
                .filter(value -> value.kind() == com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind.AMMUNITION)
                .filter(value -> "guided_feed".equals(value.interfaceId()))
                .mapToLong(ShipEngineeringState.ConsumableLoad::itemCount)
                .sum();
    }
}
