package com.spacesim.ship;

import com.spacesim.ship.LiveTacticalBattleScenario.CombatantSpec;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTacticalInitialReadinessServiceTest {
    @Test
    void moduleDamageAndReactionMassDepletionMutateOnlyAuthoritativePhysicalInputs() {
        LiveTacticalBattleRuntimeState battle = battle();
        var combatant = battle.requireCombatant(199_971L);
        LiveTacticalInitialReadinessService service = new LiveTacticalInitialReadinessService();
        double originalReactionMass = combatant.engineering().runtimeState.consumables().reactionMassKg();
        long originalAmmunition = combatant.engineering().runtimeState.consumables().ammunitionCount();

        service.setModuleIntegrity(combatant, "utility_datalink", 0.10d);
        service.retainReactionMassFraction(combatant, 0.25d);

        assertEquals(0.10d,
                combatant.engineering().instanceState.damage().moduleDamage()
                        .moduleIntegrityByMount().get("utility_datalink"));
        assertEquals(originalReactionMass * 0.25d,
                combatant.engineering().runtimeState.consumables().reactionMassKg(),
                1e-6d);
        assertEquals(originalAmmunition,
                combatant.engineering().runtimeState.consumables().ammunitionCount(),
                "reaction-mass authoring must not mutate finite weapon stores");
        assertTrue(combatant.engineering().instanceState.damage().compartmentIntegrityById().values().stream()
                .allMatch(value -> value == 1d));
    }

    @Test
    void invalidMountAndInvalidFractionAreRejected() {
        LiveTacticalBattleRuntimeState battle = battle();
        var combatant = battle.requireCombatant(199_971L);
        LiveTacticalInitialReadinessService service = new LiveTacticalInitialReadinessService();

        assertThrows(IllegalArgumentException.class,
                () -> service.setModuleIntegrity(combatant, "missing_mount", 0.5d));
        assertThrows(IllegalArgumentException.class,
                () -> service.setModuleIntegrity(combatant, "utility_datalink", -0.1d));
        assertThrows(IllegalArgumentException.class,
                () -> service.retainReactionMassFraction(combatant, 1.1d));
    }

    private static LiveTacticalBattleRuntimeState battle() {
        return new LiveTacticalBattleRuntimeState(new LiveTacticalBattleScenario(List.of(
                new CombatantSpec(199_971L, Side.ALPHA, DoctrineId.E_BALANCED_CONTROL, 0d, 0d),
                new CombatantSpec(199_972L, Side.BETA, DoctrineId.E_BALANCED_CONTROL, 1_000d, 0d))));
    }
}
