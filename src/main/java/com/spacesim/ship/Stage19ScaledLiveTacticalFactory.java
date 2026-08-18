package com.spacesim.ship;

import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.LiveTacticalInitialOrdnanceService.FeedLoad;
import com.spacesim.ship.TacticalFormationPlanner.FormationMode;
import com.spacesim.ship.TacticalFormationPlanner.Objective;

import java.util.List;
import java.util.Map;

/**
 * Single source of truth for the Stage-19 exact-local tactical validation runtimes.
 *
 * <p>The factory authors only accepted scenario initial state, physical guided-feed contents,
 * readiness degradation and explicit formation geometry. Headless acceptance and presentation
 * sessions receive the exact same production runtime chain; there is no viewer-specific combat
 * setup or simplified large-battle engine.</p>
 */
public final class Stage19ScaledLiveTacticalFactory {
    private static final String STRIKE_ID = "ammo.test_anti_ship_missile_2t_v1";
    private static final String INTERCEPTOR_ID = "ammo.test_interceptor_750kg_v1";
    private static final String DECOY_ID = "ammo.test_radar_repeater_decoy_300kg_v1";

    private static final long DAMAGED_8V8_ENTITY_ID = 191_304L;
    private static final long DEPLETED_8V8_ENTITY_ID = 191_400L;

    private static final Objective COMPACT_FORMATION =
            new Objective(FormationMode.COMPACT, 710d, 100d, 5d, 80d);

    private static final List<Long> MIXED_8V8_STRIKE_DECOY_SPECIALISTS = List.of(191_301L, 191_403L);
    private static final List<Long> MIXED_8V8_INTERCEPTOR_SPECIALISTS = List.of(191_302L, 191_406L);
    private static final List<Long> MIXED_16V16_STRIKE_DECOY_SPECIALISTS = List.of(191_501L, 191_601L);
    private static final List<Long> MIXED_16V16_INTERCEPTOR_SPECIALISTS = List.of(191_506L, 191_605L);
    private static final List<Long> SATURATION_STRIKE_DECOY_SPECIALISTS = List.of(
            191_501L, 191_509L, 191_601L, 191_610L);
    private static final List<Long> SATURATION_INTERCEPTOR_SPECIALISTS = List.of(
            191_506L, 191_514L, 191_605L, 191_613L);

    private Stage19ScaledLiveTacticalFactory() {
    }

    /** @return fresh authoritative Stage-17.5/19 1v1 regression runtime */
    public static LiveTacticalBattleDeceptionRuntime createLegacyDuel() {
        return createPlainRuntime(LiveTacticalBattleScenario.legacyDuel());
    }

    /** @return fresh authoritative shared 4v4 balanced runtime */
    public static LiveTacticalBattleDeceptionRuntime createBalanced4v4() {
        return createPlainRuntime(LiveTacticalBattleScenario.balanced4v4());
    }

    /**
     * Creates the accepted mixed 8v8 exact-local runtime with finite strike/decoy/interceptor feeds.
     *
     * @return fresh authoritative mixed 8v8 runtime
     */
    public static LiveTacticalBattleDeceptionRuntime createMixed8v8() {
        LiveTacticalBattleRuntimeState battle =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.mixed8v8());
        applyGuidedSpecialists(
                battle,
                MIXED_8V8_STRIKE_DECOY_SPECIALISTS,
                MIXED_8V8_INTERCEPTOR_SPECIALISTS);
        return createRuntime(new LiveTacticalBattleControlRuntime(battle));
    }

    /**
     * Creates the accepted damaged/depleted 8v8 validation runtime.
     *
     * <p>This is the same initial degradation used by
     * {@code LiveTacticalDamagedDepleted8v8AcceptanceTest}: entity 191304 starts with the
     * {@code utility_datalink} mount at 10% integrity and entity 191400 starts with zero reaction
     * mass. No viewer-only degradation is introduced.</p>
     *
     * @return fresh authoritative damaged/depleted mixed 8v8 runtime
     */
    public static LiveTacticalBattleDeceptionRuntime createDamagedDepleted8v8() {
        LiveTacticalBattleRuntimeState battle =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.mixed8v8());
        LiveTacticalInitialReadinessService readiness = new LiveTacticalInitialReadinessService();
        readiness.setModuleIntegrity(
                battle.requireCombatant(DAMAGED_8V8_ENTITY_ID),
                "utility_datalink",
                0.10d);
        readiness.retainReactionMassFraction(
                battle.requireCombatant(DEPLETED_8V8_ENTITY_ID),
                0d);
        return createRuntime(new LiveTacticalBattleControlRuntime(battle));
    }

    /**
     * Creates the accepted 32-ship exact-local mixed runtime without the maximum saturation loadout.
     *
     * @return fresh authoritative mixed 16v16 runtime
     */
    public static LiveTacticalBattleDeceptionRuntime createMixed16v16() {
        LiveTacticalBattleRuntimeState battle =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.mixed16v16());
        applyGuidedSpecialists(
                battle,
                MIXED_16V16_STRIKE_DECOY_SPECIALISTS,
                MIXED_16V16_INTERCEPTOR_SPECIALISTS);
        return createRuntime(new LiveTacticalBattleControlRuntime(battle));
    }

    /**
     * Creates one fresh authoritative 32-ship saturation runtime.
     *
     * <p>The compact line objective matches the authored 100 m initial cross-axis spacing exactly.
     * It is an acceptance-scenario distance, not a doctrine bonus or final balance constant.</p>
     *
     * @return fresh production deception/defense/ordnance runtime
     */
    public static LiveTacticalBattleDeceptionRuntime createSaturation32() {
        LiveTacticalBattleRuntimeState battle =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.mixed16v16());
        applyGuidedSpecialists(
                battle,
                SATURATION_STRIKE_DECOY_SPECIALISTS,
                SATURATION_INTERCEPTOR_SPECIALISTS);
        LiveTacticalBattleControlRuntime control = new LiveTacticalBattleControlRuntime(
                battle,
                Map.of(Side.ALPHA, COMPACT_FORMATION, Side.BETA, COMPACT_FORMATION));
        return createRuntime(control);
    }

    private static LiveTacticalBattleDeceptionRuntime createPlainRuntime(LiveTacticalBattleScenario scenario) {
        LiveTacticalBattleRuntimeState battle = new LiveTacticalBattleRuntimeState(scenario);
        return createRuntime(new LiveTacticalBattleControlRuntime(battle));
    }

    private static LiveTacticalBattleDeceptionRuntime createRuntime(LiveTacticalBattleControlRuntime control) {
        LiveTacticalBattleOrdnanceRuntime ordnance = new LiveTacticalBattleOrdnanceRuntime(
                new LiveTacticalBattleWeaponRuntime(control));
        return new LiveTacticalBattleDeceptionRuntime(ordnance);
    }

    private static void applyGuidedSpecialists(
            LiveTacticalBattleRuntimeState battle,
            List<Long> strikeDecoySpecialists,
            List<Long> interceptorSpecialists) {
        LiveTacticalInitialOrdnanceService initial = new LiveTacticalInitialOrdnanceService();
        for (long entityId : strikeDecoySpecialists) {
            initial.apply(
                    battle.requireCombatant(entityId),
                    List.of(
                            new FeedLoad("weapon_primary", STRIKE_ID, 8L),
                            new FeedLoad("weapon_secondary", DECOY_ID, 8L)));
        }
        for (long entityId : interceptorSpecialists) {
            initial.apply(
                    battle.requireCombatant(entityId),
                    List.of(
                            new FeedLoad("weapon_primary", INTERCEPTOR_ID, 8L),
                            new FeedLoad("weapon_secondary", INTERCEPTOR_ID, 8L)));
        }
    }
}
