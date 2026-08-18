package com.spacesim.ship;

import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.LiveTacticalInitialOrdnanceService.FeedLoad;
import com.spacesim.ship.TacticalFormationPlanner.FormationMode;
import com.spacesim.ship.TacticalFormationPlanner.Objective;

import java.util.List;
import java.util.Map;

/**
 * Single source of truth for the Stage-19I 32-ship saturation runtime used by headless and live paths.
 *
 * <p>The factory authors only the accepted scenario, physical initial guided-feed contents and
 * explicit tactical formation geometry. Both headless acceptance and presentation sessions receive
 * the exact same production runtime chain; there is no viewer-specific combat setup or simplified
 * large-battle engine.</p>
 */
public final class Stage19ScaledLiveTacticalFactory {
    private static final String STRIKE_ID = "ammo.test_anti_ship_missile_2t_v1";
    private static final String INTERCEPTOR_ID = "ammo.test_interceptor_750kg_v1";
    private static final String DECOY_ID = "ammo.test_radar_repeater_decoy_300kg_v1";

    private static final Objective COMPACT_FORMATION =
            new Objective(FormationMode.COMPACT, 710d, 100d, 5d, 80d);

    private static final List<Long> STRIKE_DECOY_SPECIALISTS = List.of(
            191_501L, 191_509L, 191_601L, 191_610L);
    private static final List<Long> INTERCEPTOR_SPECIALISTS = List.of(
            191_506L, 191_514L, 191_605L, 191_613L);

    private Stage19ScaledLiveTacticalFactory() {
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
        LiveTacticalInitialOrdnanceService initial = new LiveTacticalInitialOrdnanceService();
        for (long entityId : STRIKE_DECOY_SPECIALISTS) {
            initial.apply(
                    battle.requireCombatant(entityId),
                    List.of(
                            new FeedLoad("weapon_primary", STRIKE_ID, 8L),
                            new FeedLoad("weapon_secondary", DECOY_ID, 8L)));
        }
        for (long entityId : INTERCEPTOR_SPECIALISTS) {
            initial.apply(
                    battle.requireCombatant(entityId),
                    List.of(
                            new FeedLoad("weapon_primary", INTERCEPTOR_ID, 8L),
                            new FeedLoad("weapon_secondary", INTERCEPTOR_ID, 8L)));
        }
        LiveTacticalBattleControlRuntime control = new LiveTacticalBattleControlRuntime(
                battle,
                Map.of(Side.ALPHA, COMPACT_FORMATION, Side.BETA, COMPACT_FORMATION));
        LiveTacticalBattleOrdnanceRuntime ordnance = new LiveTacticalBattleOrdnanceRuntime(
                new LiveTacticalBattleWeaponRuntime(control));
        return new LiveTacticalBattleDeceptionRuntime(ordnance);
    }
}
