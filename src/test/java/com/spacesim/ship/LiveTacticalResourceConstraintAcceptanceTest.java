package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.ship.LiveTacticalBattleScenario.CombatantSpec;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import com.spacesim.ship.TacticalSurvivalPlanner.DecisionReason;
import com.spacesim.ship.TacticalSurvivalPlanner.SurvivalAction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTacticalResourceConstraintAcceptanceTest {
    private static final long AMMO_DEPLETED_E = 191_304L;
    private static final long AMMO_FRESH_E = 191_305L;
    private static final long POWER_STARVED_E = 191_100L;
    private static final long POWER_FRESH_E = 191_101L;
    private static final long THERMAL_STRESSED_E = 191_102L;
    private static final long THERMAL_FRESH_E = 191_103L;

    @Test
    void hardZeroFiniteAmmunitionChangesSharedAiDecisionButBeamFitDoesNotFalsePositive() {
        LiveTacticalBattleRuntimeState battle =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.mixed8v8());
        LiveTacticalInitialReadinessService initial = new LiveTacticalInitialReadinessService();
        initial.clearAmmunition(battle.requireCombatant(AMMO_DEPLETED_E));
        LiveTacticalBattleControlRuntime runtime = new LiveTacticalBattleControlRuntime(battle);

        for (int index = 0; index < 12; index++) {
            runtime.advanceOneTick();
        }

        var depleted = runtime.controlState(AMMO_DEPLETED_E);
        var fresh = runtime.controlState(AMMO_FRESH_E);
        assertEquals(DecisionReason.AMMUNITION_DEPLETED, depleted.survivalDecision().reason());
        assertEquals(SurvivalAction.DISENGAGE, depleted.survivalDecision().action(),
                "an ammo-empty ship already at its authored safe point should cease engagement instead of pretending to maneuver");
        assertFalse(depleted.fireAuthorized(),
                "hard-zero finite ammunition must revoke tactical fire authorization");
        assertEquals(DecisionReason.READY, fresh.survivalDecision().reason());
        assertTrue(fresh.intent().targetSelected(),
                "otherwise identical fresh E-fit should still acquire an actor-visible hostile target");
        assertTrue(fresh.fireAuthorized(),
                "otherwise identical fresh E-fit should remain authorized to use its physical ammunition");

        LiveTacticalBattleScenario beamScenario = new LiveTacticalBattleScenario(List.of(
                new CombatantSpec(
                        192_000L,
                        Side.ALPHA,
                        DoctrineId.C_HIGH_MOBILITY_BEAM,
                        260d,
                        700d),
                new CombatantSpec(
                        192_001L,
                        Side.BETA,
                        DoctrineId.E_BALANCED_CONTROL,
                        1_690d,
                        700d)));
        LiveTacticalBattleRuntimeState beamBattle = new LiveTacticalBattleRuntimeState(beamScenario);
        initial.clearAmmunition(beamBattle.requireCombatant(192_000L));
        LiveTacticalBattleControlRuntime beamRuntime = new LiveTacticalBattleControlRuntime(beamBattle);
        beamRuntime.advanceOneTick();

        assertNotEquals(
                DecisionReason.AMMUNITION_DEPLETED,
                beamRuntime.controlState(192_000L).survivalDecision().reason(),
                "empty finite PD feeds must not classify a ship with intact non-ammunition beam weapons as completely disarmed");
    }

    @Test
    void physicalPowerDenialRemovesTracksAndChangesTacticalDecisionWithoutPretendingSubsystemFailure() {
        LiveTacticalBattleRuntimeState battle =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.balanced4v4());
        LiveTacticalInitialReadinessService initial = new LiveTacticalInitialReadinessService();
        var constrained = battle.requireCombatant(POWER_STARVED_E);
        initial.setModuleIntegrity(constrained, "core_reactor", 0.16d);
        initial.setSharedBusEnergyJ(constrained, 0d);
        LiveTacticalBattleControlRuntime runtime = new LiveTacticalBattleControlRuntime(battle);

        for (int index = 0; index < 8; index++) {
            runtime.advanceOneTick();
        }

        var constrainedControl = runtime.controlState(POWER_STARVED_E);
        var freshControl = runtime.controlState(POWER_FRESH_E);
        assertEquals(DecisionReason.READY, constrainedControl.survivalDecision().reason(),
                "reactor integrity remains above the explicit subsystem-retreat threshold; the decision difference must come through engineering/sensing");
        assertTrue(battle.visibleContacts(POWER_STARVED_E).isEmpty(),
                "real power denial must prevent active-radar measurements instead of creating free tracks");
        assertFalse(constrainedControl.intent().targetSelected());
        assertFalse(constrainedControl.fireAuthorized());
        assertTrue(freshControl.intent().targetSelected(),
                "fresh comparator must still acquire a hostile target under the same geometry");
        assertTrue(freshControl.fireAuthorized());
    }

    @Test
    void thermallySaturatedSensorDeniesRadarAndChangesSameTickAiDecision() {
        LiveTacticalBattleRuntimeState battle =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.balanced4v4());
        LiveTacticalInitialReadinessService initial = new LiveTacticalInitialReadinessService();
        var stressed = battle.requireCombatant(THERMAL_STRESSED_E);
        double sensorThermalCapacityJ = moduleAtMount(stressed, "utility_sensor").localThermalCapacityJ();
        initial.setLocalHeatJ(stressed, "utility_sensor", sensorThermalCapacityJ);
        LiveTacticalBattleControlRuntime runtime = new LiveTacticalBattleControlRuntime(battle);

        runtime.advanceOneTick();

        var stressedControl = runtime.controlState(THERMAL_STRESSED_E);
        var freshControl = runtime.controlState(THERMAL_FRESH_E);
        assertEquals(DecisionReason.READY, stressedControl.survivalDecision().reason(),
                "thermal constraint should alter tactical information through the engineering grant path, not masquerade as structural damage");
        assertTrue(battle.visibleContacts(THERMAL_STRESSED_E).isEmpty(),
                "a sensor already at its physical local thermal capacity must not receive a free active-radar operation");
        assertFalse(stressedControl.intent().targetSelected());
        assertFalse(stressedControl.fireAuthorized());
        assertTrue(freshControl.intent().targetSelected(),
                "cold comparator must acquire a hostile target on the same first sensing tick");
        assertTrue(freshControl.fireAuthorized());
    }

    @Test
    void identicalAmmoDepletedBattlesRemainDeterministic() {
        LiveTacticalBattleControlRuntime first = ammoDepleted8v8();
        LiveTacticalBattleControlRuntime second = ammoDepleted8v8();

        for (int index = 0; index < 60; index++) {
            first.advanceOneTick();
            second.advanceOneTick();
        }

        assertEquals(first.fingerprint(), second.fingerprint());
        assertEquals(
                DecisionReason.AMMUNITION_DEPLETED,
                first.controlState(AMMO_DEPLETED_E).survivalDecision().reason());
    }

    private static LiveTacticalBattleControlRuntime ammoDepleted8v8() {
        LiveTacticalBattleRuntimeState battle =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.mixed8v8());
        new LiveTacticalInitialReadinessService().clearAmmunition(
                battle.requireCombatant(AMMO_DEPLETED_E));
        return new LiveTacticalBattleControlRuntime(battle);
    }

    private static ShipEngineeringCatalog.ModuleDefinition moduleAtMount(
            LiveTacticalBattleRuntimeState.CombatantRuntime combatant,
            String mountId) {
        ShipEngineeringCatalog catalog = Stage175ICombatTestContentPack.loadDoctrines();
        String moduleId = combatant.engineering().fit.installedModules().stream()
                .filter(value -> value.mountId().equals(mountId))
                .map(ShipEngineeringCatalog.InstalledModuleDefinition::moduleId)
                .findFirst()
                .orElseThrow();
        return catalog.findModule(moduleId);
    }
}
