package com.spacesim.ui;

import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalScenarioCatalogTest {
    @Test
    void catalogDefinesExactlyTheSixMandatoryStage19jScenariosInStableOrder() {
        var definitions = TacticalScenarioCatalog.definitions();

        assertEquals(6, definitions.size());
        assertEquals(
                List.of("duel", "4v4", "8v8", "8v8-damaged", "16v16", "saturation"),
                definitions.stream().map(value -> value.id().cliKey()).toList());
        assertEquals(
                List.of(2, 8, 16, 16, 32, 32),
                definitions.stream().map(TacticalScenarioDefinition::totalShips).toList());
    }

    @Test
    void everyCatalogEntryCreatesTheDeclaredAuthoritativeCombatantCountsDeterministically() {
        for (TacticalScenarioDefinition definition : TacticalScenarioCatalog.definitions()) {
            var first = definition.createRuntime();
            var second = definition.createRuntime();

            assertEquals(definition.totalShips(), first.battleState().combatants().size(), definition.displayName());
            assertEquals(
                    definition.alphaShips(),
                    first.battleState().scenario().combatantsFor(Side.ALPHA).size(),
                    definition.displayName());
            assertEquals(
                    definition.betaShips(),
                    first.battleState().scenario().combatantsFor(Side.BETA).size(),
                    definition.displayName());
            assertEquals(first.fingerprint(), second.fingerprint(),
                    definition.displayName() + " must start from one deterministic canonical physical state");
        }
    }

    @Test
    void cliLookupIsCaseInsensitiveAndInvalidValuesListCanonicalKeys() {
        assertEquals(
                TacticalScenarioId.SATURATION_16V16,
                TacticalScenarioCatalog.requireByCliKey("  SATURATION ").id());

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> TacticalScenarioCatalog.requireByCliKey("unknown"));
        assertTrue(failure.getMessage().contains("duel"));
        assertTrue(failure.getMessage().contains("8v8-damaged"));
        assertTrue(failure.getMessage().contains("saturation"));
    }
}
