package com.spacesim.ship;

import com.spacesim.ship.LiveTacticalInitialOrdnanceService.FeedLoad;

import java.util.List;

/**
 * Single source of truth for the Stage-19I 32-ship saturation runtime used by headless and live paths.
 *
 * <p>The factory authors only the already accepted scenario and physical initial guided-feed contents.
 * Both headless acceptance and presentation sessions receive the exact same production runtime chain;
 * there is no viewer-specific combat setup or simplified large-battle engine.</p>
 */
public final class Stage19ScaledLiveTacticalFactory {
    private static final String STRIKE_ID = "ammo.test_anti_ship_missile_2t_v1";
    private static final String INTERCEPTOR_ID = "ammo.test_interceptor_750kg_v1";
    private static final String DECOY_ID = "ammo.test_radar_repeater_decoy_300kg_v1";

    private static final List<Long> STRIKE_DECOY_SPECIALISTS = List.of(
            191_501L, 191_509L, 191_601L, 191_610L);
    private static final List<Long> INTERCEPTOR_SPECIALISTS = List.of(
            191_506L, 191_514L, 191_605L, 191_613L);

    private Stage19ScaledLiveTacticalFactory() {
    }

    /**
     * Creates one fresh authoritative 32-ship saturation runtime.
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
        LiveTacticalBattleOrdnanceRuntime ordnance = new LiveTacticalBattleOrdnanceRuntime(
                new LiveTacticalBattleWeaponRuntime(
                        new LiveTacticalBattleControlRuntime(battle)));
        return new LiveTacticalBattleDeceptionRuntime(ordnance);
    }
}
