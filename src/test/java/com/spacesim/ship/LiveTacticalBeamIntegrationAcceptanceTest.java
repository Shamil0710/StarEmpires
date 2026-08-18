package com.spacesim.ship;

import com.spacesim.ship.LiveTacticalBattleScenario.CombatantSpec;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTacticalBeamIntegrationAcceptanceTest {
    private static final long BEAM_SHIP = 192_001L;
    private static final long TARGET_SHIP = 192_002L;

    @Test
    void fittedBeamUsesActorLocalFireControlAndCommitsPhysicalPowerHeatAndExposure() {
        LiveTacticalBattleDeceptionRuntime runtime = createBeamDuel();
        double beforeHeat = runtime.battleState().requireCombatant(BEAM_SHIP)
                .engineering().runtimeState.localHeatJByMount().getOrDefault("weapon_primary", 0d);

        for (int tick = 0; tick < 160 && runtime.beamRuntime().dwellsFired(BEAM_SHIP) == 0L; tick++) {
            runtime.advanceOneTick();
        }

        assertTrue(runtime.beamRuntime().dwellsFired(BEAM_SHIP) > 0L);
        assertTrue(runtime.beamRuntime().deliveredEnergyJ(BEAM_SHIP) > 0d);
        assertFalse(runtime.beamRuntime().lastExposures().isEmpty());
        LiveTacticalBattleBeamRuntime.BeamExposure exposure = runtime.beamRuntime().lastExposures().stream()
                .filter(value -> value.sourceEntityId() == BEAM_SHIP)
                .findFirst()
                .orElseThrow();
        assertEquals(TARGET_SHIP, exposure.targetEntityId());
        assertTrue(exposure.rangeM() > 0d);
        assertTrue(exposure.deliveredBeamEnergyJ() > 0d);
        assertTrue(exposure.electricalEnergyDemandJ() > 0d);
        assertTrue(exposure.wasteHeatJ() > 0d);
        assertTrue(runtime.battleState().requireCombatant(BEAM_SHIP)
                .engineering().runtimeState.localHeatJByMount().getOrDefault("weapon_primary", 0d) > beforeHeat);
    }

    @Test
    void fittedBeamExecutionIsPartOfWholeRuntimeDeterministicFingerprint() {
        LiveTacticalBattleDeceptionRuntime first = createBeamDuel();
        LiveTacticalBattleDeceptionRuntime second = createBeamDuel();
        for (int tick = 0; tick < 80; tick++) {
            first.advanceOneTick();
            second.advanceOneTick();
            assertEquals(first.fingerprint(), second.fingerprint());
        }
        assertTrue(first.beamRuntime().dwellsFired(BEAM_SHIP) > 0L);
    }

    private static LiveTacticalBattleDeceptionRuntime createBeamDuel() {
        LiveTacticalBattleScenario scenario = new LiveTacticalBattleScenario(List.of(
                new CombatantSpec(
                        BEAM_SHIP,
                        Side.ALPHA,
                        DoctrineId.C_HIGH_MOBILITY_BEAM,
                        260d,
                        700d),
                new CombatantSpec(
                        TARGET_SHIP,
                        Side.BETA,
                        DoctrineId.E_BALANCED_CONTROL,
                        1_690d,
                        700d)));
        LiveTacticalBattleRuntimeState battle = new LiveTacticalBattleRuntimeState(scenario);
        LiveTacticalBattleControlRuntime control = new LiveTacticalBattleControlRuntime(battle);
        LiveTacticalBattleWeaponRuntime weapons = new LiveTacticalBattleWeaponRuntime(control);
        return new LiveTacticalBattleDeceptionRuntime(new LiveTacticalBattleOrdnanceRuntime(weapons));
    }
}