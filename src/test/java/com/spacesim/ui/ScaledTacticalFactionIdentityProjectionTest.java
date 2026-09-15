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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaledTacticalFactionIdentityProjectionTest {
    @Test
    void exactImportCarriesStableFactionAndInstalledFitIntoScaledGlyphs() {
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

        TacticalPrototypeVisualSnapshot snapshot =
                new ScaledLiveTacticalSimulationProjection().project(runtime(imported));

        assertEquals(2, snapshot.ships().size());
        var alphaGlyph = snapshot.ships().stream()
                .filter(value -> value.side() == TacticalPrototypeVisualSnapshot.TacticalSide.ALPHA)
                .findFirst().orElseThrow();
        var betaGlyph = snapshot.ships().stream()
                .filter(value -> value.side() == TacticalPrototypeVisualSnapshot.TacticalSide.BETA)
                .findFirst().orElseThrow();
        assertEquals(Stage22EmpirePackageCatalog.STABLE_FACTION_ID, alphaGlyph.stableFactionId());
        assertEquals(Stage22IndustrialUnionPackageCatalog.STABLE_FACTION_ID, betaGlyph.stableFactionId());
        assertEquals(alpha.engineering().fit, alphaGlyph.installedFit());
        assertEquals(beta.engineering().fit, betaGlyph.installedFit());
    }

    @Test
    void authoredSideOnlyFixtureDoesNotInventCampaignFactionIdentity() {
        LiveTacticalBattleRuntimeState authored =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.legacyDuel());

        TacticalPrototypeVisualSnapshot snapshot =
                new ScaledLiveTacticalSimulationProjection().project(runtime(authored));

        assertEquals(2, snapshot.ships().size());
        assertTrue(snapshot.ships().stream().allMatch(value -> value.installedFit() != null));
        snapshot.ships().forEach(value -> assertNull(value.stableFactionId(),
                "ALPHA/BETA side must never be promoted to campaign faction identity"));
    }

    private static LiveTacticalBattleDeceptionRuntime runtime(LiveTacticalBattleRuntimeState state) {
        return new LiveTacticalBattleDeceptionRuntime(
                new LiveTacticalBattleOrdnanceRuntime(
                        new LiveTacticalBattleWeaponRuntime(
                                new LiveTacticalBattleControlRuntime(state))));
    }
}
