package com.spacesim.ui;

import com.spacesim.content.Stage22EmpirePackageCatalog;
import com.spacesim.content.Stage22IndustrialUnionPackageCatalog;
import com.spacesim.ship.LiveTacticalBattleControlRuntime;
import com.spacesim.ship.LiveTacticalBattleDeceptionRuntime;
import com.spacesim.ship.LiveTacticalBattleOrdnanceRuntime;
import com.spacesim.ship.LiveTacticalBattleRuntimeState;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.ImportedCombatantState;
import com.spacesim.ship.LiveTacticalBattleScenario;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.LiveTacticalBattleWeaponRuntime;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ShipInspectionFactionIdentityTest {
    @Test
    void exactImportedFactionAndFitIdentityReachInspectionSnapshot() {
        LiveTacticalBattleRuntimeState source =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.legacyDuel());
        var alpha = source.combatants().stream()
                .filter(value -> value.spec().side() == Side.ALPHA)
                .findFirst().orElseThrow();
        var beta = source.combatants().stream()
                .filter(value -> value.spec().side() == Side.BETA)
                .findFirst().orElseThrow();

        LiveTacticalBattleRuntimeState imported = LiveTacticalBattleRuntimeState.importExact(List.of(
                new ImportedCombatantState(
                        alpha.spec().entityId(),
                        Side.ALPHA,
                        Stage22EmpirePackageCatalog.STABLE_FACTION_ID,
                        alpha.engineering(),
                        alpha.transform().position.x,
                        alpha.transform().position.y,
                        alpha.transform().velocity.x,
                        alpha.transform().velocity.y),
                new ImportedCombatantState(
                        beta.spec().entityId(),
                        Side.BETA,
                        Stage22IndustrialUnionPackageCatalog.STABLE_FACTION_ID,
                        beta.engineering(),
                        beta.transform().position.x,
                        beta.transform().position.y,
                        beta.transform().velocity.x,
                        beta.transform().velocity.y)));
        ScaledLiveTacticalSimulationSession session = session(imported);

        ShipInspectionSnapshot inspection = session.inspectionSnapshot(alpha.spec().entityId()).orElseThrow();

        assertEquals(alpha.doctrine().fitId(), inspection.fitId());
        assertEquals(Stage22EmpirePackageCatalog.STABLE_FACTION_ID,
                inspection.visualIdentity().stableFactionId());
        assertEquals(alpha.engineering().fit, inspection.visualIdentity().installedFit());
    }

    @Test
    void sideOnlyFixtureDoesNotInventInspectionFactionIdentity() {
        LiveTacticalBattleRuntimeState authored =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.legacyDuel());
        ScaledLiveTacticalSimulationSession session = session(authored);

        ShipInspectionSnapshot inspection =
                session.inspectionSnapshot(authored.combatants().get(0).spec().entityId()).orElseThrow();

        assertNull(inspection.visualIdentity());
    }

    private static ScaledLiveTacticalSimulationSession session(LiveTacticalBattleRuntimeState state) {
        LiveTacticalBattleDeceptionRuntime runtime = new LiveTacticalBattleDeceptionRuntime(
                new LiveTacticalBattleOrdnanceRuntime(
                        new LiveTacticalBattleWeaponRuntime(
                                new LiveTacticalBattleControlRuntime(state))));
        return new ScaledLiveTacticalSimulationSession(
                runtime,
                new ScaledLiveTacticalSimulationProjection());
    }
}
