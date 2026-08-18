package com.spacesim.ship;

import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.LiveTacticalInitialOrdnanceService.FeedLoad;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTactical32ShipExactLocalAcceptanceTest {
    private static final String STRIKE_ID = "ammo.test_anti_ship_missile_2t_v1";
    private static final String INTERCEPTOR_ID = "ammo.test_interceptor_750kg_v1";
    private static final String DECOY_ID = "ammo.test_radar_repeater_decoy_300kg_v1";

    private static final long ALPHA_STRIKE_DECOY = 191_501L;
    private static final long ALPHA_INTERCEPTOR = 191_506L;
    private static final long BETA_STRIKE_DECOY = 191_601L;
    private static final long BETA_INTERCEPTOR = 191_605L;

    @Test
    void thirtyTwoShipsShareOneActorBoundedPhysicalRuntime() {
        Fixture fixture = fixture();
        Map<Long, Double> initialReactionMass = reactionMassByEntity(fixture.battle());
        Map<Long, Long> initialSpecialistRounds = guidedRoundsForSpecialists(fixture.battle());

        for (int index = 0; index < 4; index++) {
            fixture.runtime().advanceOneTick();
        }

        assertEquals(32, fixture.battle().combatants().size());
        assertEquals(16, fixture.battle().scenario().combatantsFor(Side.ALPHA).size());
        assertEquals(16, fixture.battle().scenario().combatantsFor(Side.BETA).size());

        for (var combatant : fixture.battle().combatants()) {
            long entityId = combatant.spec().entityId();
            var contacts = fixture.battle().visibleContacts(entityId);
            var control = fixture.runtime().ordnanceRuntime().weaponRuntime().controlRuntime().controlState(entityId);
            assertTrue(!contacts.isEmpty(),
                    "every one of the 32 ships must acquire at least one actor-local hostile contact");
            assertTrue(contacts.stream().allMatch(contact ->
                            fixture.battle().requireCombatant(contact.track().targetId()).spec().side()
                                    != combatant.spec().side()),
                    "scaled actor-visible combat domains must not leak friendly/self targets");
            assertTrue(control.intent().targetSelected());
            assertTrue(contacts.stream().anyMatch(contact -> contact.track().targetId() == control.intent().targetId()),
                    "each selected target must come from that actor's own TrackState domain");
            assertTrue(reactionMassKg(combatant) < initialReactionMass.get(entityId),
                    "all 32 maneuvering ships must spend their own physical reaction mass");
        }

        assertTrue(distinctSelectedTargets(fixture, Side.ALPHA) > 2L,
                "16 alpha actors must not collapse onto a single universal target hypothesis");
        assertTrue(distinctSelectedTargets(fixture, Side.BETA) > 2L,
                "16 beta actors must not collapse onto a single universal target hypothesis");

        while (fixture.runtime().tick() < 240L && !baselineFeatureSetObserved(fixture.runtime())) {
            fixture.runtime().advanceOneTick();
        }

        assertTrue(baselineFeatureSetObserved(fixture.runtime()),
                () -> "32-ship physical feature set incomplete at tick " + fixture.runtime().tick()
                        + ": " + featureDiagnostics(fixture.runtime()));
        assertEquals(
                initialSpecialistRounds.get(ALPHA_STRIKE_DECOY)
                        - fixture.runtime().ordnanceRuntime().guidedLaunches(ALPHA_STRIKE_DECOY)
                        - fixture.runtime().automaticDeployments(ALPHA_STRIKE_DECOY),
                guidedRounds(fixture.battle().requireCombatant(ALPHA_STRIKE_DECOY)));
        assertEquals(
                initialSpecialistRounds.get(BETA_STRIKE_DECOY)
                        - fixture.runtime().ordnanceRuntime().guidedLaunches(BETA_STRIKE_DECOY)
                        - fixture.runtime().automaticDeployments(BETA_STRIKE_DECOY),
                guidedRounds(fixture.battle().requireCombatant(BETA_STRIKE_DECOY)));
        assertEquals(
                initialSpecialistRounds.get(ALPHA_INTERCEPTOR)
                        - fixture.runtime().defenseRuntime().interceptorLaunches(ALPHA_INTERCEPTOR),
                guidedRounds(fixture.battle().requireCombatant(ALPHA_INTERCEPTOR)));
        assertEquals(
                initialSpecialistRounds.get(BETA_INTERCEPTOR)
                        - fixture.runtime().defenseRuntime().interceptorLaunches(BETA_INTERCEPTOR),
                guidedRounds(fixture.battle().requireCombatant(BETA_INTERCEPTOR)));
    }

    @Test
    void sameThirtyTwoShipInitialStateAndFixedTicksReplayIdentically() {
        Fixture first = fixture();
        Fixture second = fixture();

        for (int index = 0; index < 60; index++) {
            first.runtime().advanceOneTick();
            second.runtime().advanceOneTick();
        }

        assertEquals(first.runtime().fingerprint(), second.runtime().fingerprint());
    }

    private static Fixture fixture() {
        LiveTacticalBattleRuntimeState battle =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.mixed16v16());
        LiveTacticalInitialOrdnanceService initial = new LiveTacticalInitialOrdnanceService();
        initial.apply(
                battle.requireCombatant(ALPHA_STRIKE_DECOY),
                List.of(
                        new FeedLoad("weapon_primary", STRIKE_ID, 8L),
                        new FeedLoad("weapon_secondary", DECOY_ID, 8L)));
        initial.apply(
                battle.requireCombatant(BETA_STRIKE_DECOY),
                List.of(
                        new FeedLoad("weapon_primary", STRIKE_ID, 8L),
                        new FeedLoad("weapon_secondary", DECOY_ID, 8L)));
        initial.apply(
                battle.requireCombatant(ALPHA_INTERCEPTOR),
                List.of(
                        new FeedLoad("weapon_primary", INTERCEPTOR_ID, 8L),
                        new FeedLoad("weapon_secondary", INTERCEPTOR_ID, 8L)));
        initial.apply(
                battle.requireCombatant(BETA_INTERCEPTOR),
                List.of(
                        new FeedLoad("weapon_primary", INTERCEPTOR_ID, 8L),
                        new FeedLoad("weapon_secondary", INTERCEPTOR_ID, 8L)));

        LiveTacticalBattleOrdnanceRuntime ordnance = new LiveTacticalBattleOrdnanceRuntime(
                new LiveTacticalBattleWeaponRuntime(
                        new LiveTacticalBattleControlRuntime(battle)));
        return new Fixture(battle, new LiveTacticalBattleDeceptionRuntime(ordnance));
    }

    private static boolean baselineFeatureSetObserved(LiveTacticalBattleDeceptionRuntime runtime) {
        return totalKineticShots(runtime) > 0L
                && totalGuidedLaunches(runtime) > 0L
                && totalDecoyDeployments(runtime) > 0L
                && totalInterceptorLaunches(runtime) > 0L
                && runtime.ordnanceRuntime().weaponRuntime().totalImpacts() > 0L;
    }

    private static String featureDiagnostics(LiveTacticalBattleDeceptionRuntime runtime) {
        return "kineticShots=" + totalKineticShots(runtime)
                + ", guidedLaunches=" + totalGuidedLaunches(runtime)
                + ", decoyDeployments=" + totalDecoyDeployments(runtime)
                + ", interceptorLaunches=" + totalInterceptorLaunches(runtime)
                + ", impacts=" + runtime.ordnanceRuntime().weaponRuntime().totalImpacts();
    }

    private static long totalKineticShots(LiveTacticalBattleDeceptionRuntime runtime) {
        return runtime.battleState().combatants().stream()
                .mapToLong(value -> runtime.ordnanceRuntime().weaponRuntime().shotsFired(value.spec().entityId()))
                .sum();
    }

    private static long totalGuidedLaunches(LiveTacticalBattleDeceptionRuntime runtime) {
        return runtime.battleState().combatants().stream()
                .mapToLong(value -> runtime.ordnanceRuntime().guidedLaunches(value.spec().entityId()))
                .sum();
    }

    private static long totalDecoyDeployments(LiveTacticalBattleDeceptionRuntime runtime) {
        return runtime.battleState().combatants().stream()
                .mapToLong(value -> runtime.automaticDeployments(value.spec().entityId()))
                .sum();
    }

    private static long totalInterceptorLaunches(LiveTacticalBattleDeceptionRuntime runtime) {
        return runtime.battleState().combatants().stream()
                .mapToLong(value -> runtime.defenseRuntime().interceptorLaunches(value.spec().entityId()))
                .sum();
    }

    private static long distinctSelectedTargets(Fixture fixture, Side side) {
        return fixture.battle().scenario().combatantsFor(side).stream()
                .map(spec -> fixture.runtime().ordnanceRuntime().weaponRuntime().controlRuntime()
                        .controlState(spec.entityId()).intent())
                .filter(ObservedTacticalIntentPlanner.TacticalIntent::targetSelected)
                .mapToLong(ObservedTacticalIntentPlanner.TacticalIntent::targetId)
                .distinct()
                .count();
    }

    private static Map<Long, Double> reactionMassByEntity(LiveTacticalBattleRuntimeState battle) {
        TreeMap<Long, Double> values = new TreeMap<>();
        for (var combatant : battle.combatants()) {
            values.put(combatant.spec().entityId(), reactionMassKg(combatant));
        }
        return Map.copyOf(values);
    }

    private static Map<Long, Long> guidedRoundsForSpecialists(LiveTacticalBattleRuntimeState battle) {
        TreeMap<Long, Long> values = new TreeMap<>();
        for (long entityId : List.of(
                ALPHA_STRIKE_DECOY,
                ALPHA_INTERCEPTOR,
                BETA_STRIKE_DECOY,
                BETA_INTERCEPTOR)) {
            values.put(entityId, guidedRounds(battle.requireCombatant(entityId)));
        }
        return Map.copyOf(values);
    }

    private static double reactionMassKg(LiveTacticalBattleRuntimeState.CombatantRuntime combatant) {
        return combatant.engineering().runtimeState.consumables().reactionMassKg();
    }

    private static long guidedRounds(LiveTacticalBattleRuntimeState.CombatantRuntime combatant) {
        return combatant.engineering().runtimeState.consumables().interfaceLoads().stream()
                .filter(value -> value.kind() == com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind.AMMUNITION)
                .filter(value -> "guided_feed".equals(value.interfaceId()))
                .mapToLong(ShipEngineeringState.ConsumableLoad::itemCount)
                .sum();
    }

    private record Fixture(
            LiveTacticalBattleRuntimeState battle,
            LiveTacticalBattleDeceptionRuntime runtime) {
    }
}
