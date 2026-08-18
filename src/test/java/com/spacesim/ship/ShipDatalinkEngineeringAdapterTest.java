package com.spacesim.ship;

import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.ship.LiveTacticalBattleScenario.CombatantSpec;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShipDatalinkEngineeringAdapterTest {
    @Test
    void fittedDatalinkChannelsFollowPhysicalModuleIntegrity() {
        LiveTacticalBattleRuntimeState battle = new LiveTacticalBattleRuntimeState(
                new LiveTacticalBattleScenario(List.of(
                        new CombatantSpec(199_961L, Side.ALPHA, DoctrineId.B_MISSILE_STRIKE, 0d, 0d),
                        new CombatantSpec(199_962L, Side.BETA, DoctrineId.A_KINETIC_LINE, 1_000d, 0d))));
        var combatant = battle.requireCombatant(199_961L);
        var calculator = new DerivedShipCalculator(Stage175ICombatTestContentPack.loadDoctrines());
        var adapter = new ShipDatalinkEngineeringAdapter();

        var pristine = calculator.derive(
                combatant.hull(),
                combatant.engineering().fit,
                combatant.engineering().runtimeState.consumables(),
                DamageState.pristine());
        var half = calculator.derive(
                combatant.hull(),
                combatant.engineering().fit,
                combatant.engineering().runtimeState.consumables(),
                new DamageState(Map.of("utility_datalink", 0.5d)));
        var destroyed = calculator.derive(
                combatant.hull(),
                combatant.engineering().fit,
                combatant.engineering().runtimeState.consumables(),
                new DamageState(Map.of("utility_datalink", 0d)));

        assertEquals(64, adapter.totalSupportChannels(pristine));
        assertEquals(32, adapter.totalSupportChannels(half));
        assertEquals(0, adapter.totalSupportChannels(destroyed));
    }
}
