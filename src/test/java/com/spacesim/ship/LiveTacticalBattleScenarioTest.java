package com.spacesim.ship;

import com.spacesim.ship.LiveTacticalBattleScenario.CombatantSpec;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTacticalBattleScenarioTest {
    @Test
    void canonicalOrderIsStableRegardlessOfAuthoredInputOrder() {
        CombatantSpec beta = new CombatantSpec(20L, Side.BETA, DoctrineId.E_BALANCED_CONTROL, 2d, 0d);
        CombatantSpec alpha = new CombatantSpec(10L, Side.ALPHA, DoctrineId.E_BALANCED_CONTROL, 1d, 0d);

        LiveTacticalBattleScenario scenario = new LiveTacticalBattleScenario(List.of(beta, alpha));

        assertEquals(List.of(alpha, beta), scenario.combatants());
        assertEquals(List.of(alpha), scenario.combatantsFor(Side.ALPHA));
        assertEquals(List.of(beta), scenario.combatantsFor(Side.BETA));
    }

    @Test
    void invalidOrDuplicateCombatantIdentityIsRejected() {
        CombatantSpec alpha = new CombatantSpec(10L, Side.ALPHA, DoctrineId.E_BALANCED_CONTROL, 1d, 0d);
        CombatantSpec betaWithDuplicateId =
                new CombatantSpec(10L, Side.BETA, DoctrineId.E_BALANCED_CONTROL, 2d, 0d);

        assertThrows(IllegalArgumentException.class,
                () -> new LiveTacticalBattleScenario(List.of(alpha, betaWithDuplicateId)));
        assertThrows(IllegalArgumentException.class,
                () -> new LiveTacticalBattleScenario(List.of(
                        alpha,
                        new CombatantSpec(11L, Side.ALPHA, DoctrineId.E_BALANCED_CONTROL, 2d, 0d))));
    }

    @Test
    void balanced4v4AuthoredRosterIsSymmetricAndDeterministic() {
        LiveTacticalBattleScenario first = LiveTacticalBattleScenario.balanced4v4();
        LiveTacticalBattleScenario second = LiveTacticalBattleScenario.balanced4v4();

        assertEquals(first, second);
        assertEquals(8, first.combatants().size());
        assertEquals(4, first.combatantsFor(Side.ALPHA).size());
        assertEquals(4, first.combatantsFor(Side.BETA).size());
        assertEquals(8L, first.combatants().stream().map(CombatantSpec::entityId).distinct().count());
        assertTrue(first.combatants().stream()
                .allMatch(value -> Double.isFinite(value.xM()) && Double.isFinite(value.yM())));
    }

    @Test
    void legacyDuelPreservesExistingStableViewerIdentities() {
        LiveTacticalBattleScenario scenario = LiveTacticalBattleScenario.legacyDuel();

        assertEquals(2, scenario.combatants().size());
        assertEquals(LiveTacticalSimulationSession.ATTACKER_ENTITY_ID,
                scenario.combatantsFor(Side.ALPHA).get(0).entityId());
        assertEquals(LiveTacticalSimulationSession.TARGET_ENTITY_ID,
                scenario.combatantsFor(Side.BETA).get(0).entityId());
    }
}
