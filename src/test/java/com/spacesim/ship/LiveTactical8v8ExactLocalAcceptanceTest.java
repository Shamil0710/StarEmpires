package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.LiveTacticalInitialOrdnanceService.FeedLoad;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTactical8v8ExactLocalAcceptanceTest {
    private static final String STRIKE_ID = "ammo.test_anti_ship_missile_2t_v1";
    private static final String INTERCEPTOR_ID = "ammo.test_interceptor_750kg_v1";
    private static final String DECOY_ID = "ammo.test_radar_repeater_decoy_300kg_v1";

    private static final long ALPHA_STRIKE_DECOY = 191_301L;
    private static final long ALPHA_INTERCEPTOR = 191_302L;
    private static final long BETA_STRIKE_DECOY = 191_403L;
    private static final long BETA_INTERCEPTOR = 191_406L;

    @Test
    void mixed8v8UsesOneProductionRuntimeWithActorBoundedCombatAndFiniteDefenseResources() {
        Fixture fixture = fixture();
        Map<Long, Double> initialReactionMass = reactionMassByEntity(fixture.battle());
        Map<Long, Long> initialGuidedRounds = guidedRoundsForSpecialists(fixture.battle());

        while (fixture.runtime().tick() < 800L && !physicalFeatureSetObserved(fixture.runtime())) {
            fixture.runtime().advanceOneTick();
        }

        assertEquals(16, fixture.battle().combatants().size());
        assertEquals(8, fixture.battle().scenario().combatantsFor(Side.ALPHA).size());
        assertEquals(8, fixture.battle().scenario().combatantsFor(Side.BETA).size());
        assertTrue(physicalFeatureSetObserved(fixture.runtime()),
                () -> "8v8 physical feature set incomplete at tick " + fixture.runtime().tick()
                        + ": " + physicalFeatureDiagnostics(fixture.runtime()));

        for (var combatant : fixture.battle().combatants()) {
            long entityId = combatant.spec().entityId();
            var contacts = fixture.battle().visibleContacts(entityId);
            var control = fixture.runtime().ordnanceRuntime().weaponRuntime().controlRuntime().controlState(entityId);

            assertTrue(!contacts.isEmpty(), "every 8v8 combatant must acquire actor-local hostile information");
            assertTrue(contacts.stream().allMatch(contact ->
                            fixture.battle().requireCombatant(contact.track().targetId()).spec().side()
                                    != combatant.spec().side()),
                    "8v8 actor-visible target domain must contain no friendly/self hostile contacts");
            assertTrue(control.intent().targetSelected(),
                    "production tactical AI must select a target from its own 8v8 information domain");
            assertTrue(contacts.stream().anyMatch(contact -> contact.track().targetId() == control.intent().targetId()),
                    "selected target must exist in the selecting actor's TrackState domain");

            double displacement = Math.hypot(
                    combatant.transform().position.x - combatant.spec().xM(),
                    combatant.transform().position.y - combatant.spec().yM());
            assertTrue(displacement > 0d || combatant.transform().velocity.len2() > 0f,
                    "every 8v8 actor must reach the common physical flight integrator");
            assertTrue(reactionMassKg(combatant) < initialReactionMass.get(entityId),
                    "every moving combatant must spend its own finite reaction mass");
        }

        assertTrue(distinctSelectedTargets(fixture, Side.ALPHA) > 1L,
                "8v8 alpha actors should not collapse onto one universal target hypothesis");
        assertTrue(distinctSelectedTargets(fixture, Side.BETA) > 1L,
                "8v8 beta actors should not collapse onto one universal target hypothesis");

        assertEquals(
                initialGuidedRounds.get(ALPHA_STRIKE_DECOY)
                        - fixture.runtime().ordnanceRuntime().guidedLaunches(ALPHA_STRIKE_DECOY)
                        - fixture.runtime().automaticDeployments(ALPHA_STRIKE_DECOY),
                guidedRounds(fixture.battle().requireCombatant(ALPHA_STRIKE_DECOY)),
                "alpha strike/decoy specialist must pay one physical guided round per launch/deployment");
        assertEquals(
                initialGuidedRounds.get(BETA_STRIKE_DECOY)
                        - fixture.runtime().ordnanceRuntime().guidedLaunches(BETA_STRIKE_DECOY)
                        - fixture.runtime().automaticDeployments(BETA_STRIKE_DECOY),
                guidedRounds(fixture.battle().requireCombatant(BETA_STRIKE_DECOY)),
                "beta strike/decoy specialist must pay one physical guided round per launch/deployment");
        assertEquals(
                initialGuidedRounds.get(ALPHA_INTERCEPTOR)
                        - fixture.runtime().defenseRuntime().interceptorLaunches(ALPHA_INTERCEPTOR),
                guidedRounds(fixture.battle().requireCombatant(ALPHA_INTERCEPTOR)),
                "alpha interceptor screen must consume finite itemized interceptor stores");
        assertEquals(
                initialGuidedRounds.get(BETA_INTERCEPTOR)
                        - fixture.runtime().defenseRuntime().interceptorLaunches(BETA_INTERCEPTOR),
                guidedRounds(fixture.battle().requireCombatant(BETA_INTERCEPTOR)),
                "beta interceptor screen must consume finite itemized interceptor stores");
    }

    @Test
    void sameMixed8v8FixedTickScheduleProducesIdenticalWholeRuntimeFingerprint() {
        Fixture first = fixture();
        Fixture second = fixture();

        for (int index = 0; index < 260; index++) {
            first.runtime().advanceOneTick();
            second.runtime().advanceOneTick();
        }

        assertEquals(first.runtime().fingerprint(), second.runtime().fingerprint(),
                "same mixed 8v8 initial state and fixed tick schedule must replay identically");
    }

    private static Fixture fixture() {
        LiveTacticalBattleRuntimeState battle =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.mixed8v8());
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

    private static boolean physicalFeatureSetObserved(LiveTacticalBattleDeceptionRuntime runtime) {
        return totalKineticShots(runtime) > 0L
                && totalGuidedLaunches(runtime) > 0L
                && totalDecoyDeployments(runtime) > 0L
                && totalInterceptorLaunches(runtime) > 0L
                && runtime.ordnanceRuntime().weaponRuntime().totalImpacts() > 0L;
    }

    private static String physicalFeatureDiagnostics(LiveTacticalBattleDeceptionRuntime runtime) {
        return "kineticShots=" + totalKineticShots(runtime)
                + ", guidedLaunches=" + totalGuidedLaunches(runtime)
                + ", decoyDeployments=" + totalDecoyDeployments(runtime)
                + ", interceptorLaunches=" + totalInterceptorLaunches(runtime)
                + ", impacts=" + runtime.ordnanceRuntime().weaponRuntime().totalImpacts()
                + ", alphaInterceptor={" + interceptorDiagnostics(runtime, ALPHA_INTERCEPTOR) + "}"
                + ", betaInterceptor={" + interceptorDiagnostics(runtime, BETA_INTERCEPTOR) + "}";
    }

    private static String interceptorDiagnostics(LiveTacticalBattleDeceptionRuntime runtime, long entityId) {
        LiveTacticalOrdnanceObservationRuntime observation = runtime.defenseRuntime().observationRuntime();
        List<LiveTacticalOrdnanceObservationRuntime.ObservedOrdnanceTrack> tracks =
                observation.tracksForObserver(entityId);
        long velocityKnown = tracks.stream()
                .filter(LiveTacticalOrdnanceObservationRuntime.ObservedOrdnanceTrack::velocityKnown)
                .count();
        long actionable = tracks.stream().filter(LiveTactical8v8ExactLocalAcceptanceTest::actionable).count();
        Map<TrackState.InformationState, Long> states = tracks.stream().collect(Collectors.groupingBy(
                value -> value.track().informationState(),
                () -> new java.util.EnumMap<>(TrackState.InformationState.class),
                Collectors.counting()));
        return "scan=" + observation.lastScanDiagnostics(entityId)
                + ", tracks=" + tracks.size()
                + ", velocityKnown=" + velocityKnown
                + ", actionable=" + actionable
                + ", states=" + states
                + ", rounds=" + guidedRounds(runtime.battleState().requireCombatant(entityId));
    }

    private static boolean actionable(LiveTacticalOrdnanceObservationRuntime.ObservedOrdnanceTrack observed) {
        return observed.velocityKnown()
                && observed.track().positionKnown()
                && (observed.track().informationState() == TrackState.InformationState.TRACKED
                || observed.track().informationState() == TrackState.InformationState.FIRE_CONTROL);
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
        return combatant.engineering().runtimeState.consumables().interfaceLoads().stream()
                .filter(value -> value.kind() == InterfaceKind.REACTION_MASS)
                .mapToDouble(ShipEngineeringState.ConsumableLoad::massKg)
                .sum();
    }

    private static long guidedRounds(LiveTacticalBattleRuntimeState.CombatantRuntime combatant) {
        return combatant.engineering().runtimeState.consumables().interfaceLoads().stream()
                .filter(value -> value.kind() == InterfaceKind.AMMUNITION)
                .filter(value -> "guided_feed".equals(value.interfaceId()))
                .mapToLong(ShipEngineeringState.ConsumableLoad::itemCount)
                .sum();
    }

    private record Fixture(
            LiveTacticalBattleRuntimeState battle,
            LiveTacticalBattleDeceptionRuntime runtime) {
    }
}
