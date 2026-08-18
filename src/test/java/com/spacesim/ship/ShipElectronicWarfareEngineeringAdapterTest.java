package com.spacesim.ship;

import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.CombatantRuntime;
import com.spacesim.ship.LiveTacticalBattleScenario.CombatantSpec;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.ShipDamageRuntime.Snapshot;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipElectronicWarfareEngineeringAdapterTest {
    private static final long EW_ENTITY_ID = 199_401L;

    @Test
    void fittedEwSignatureBecomesPhysicalNoiseEmitterAndDamageRemovesIt() {
        LiveTacticalBattleRuntimeState battle = new LiveTacticalBattleRuntimeState(
                new LiveTacticalBattleScenario(List.of(
                        new CombatantSpec(EW_ENTITY_ID, Side.ALPHA, DoctrineId.D_DEFENSIVE_EW, 600d, 700d),
                        new CombatantSpec(199_402L, Side.BETA, DoctrineId.B_MISSILE_STRIKE, 1_600d, 700d))));
        CombatantRuntime emitter = battle.requireCombatant(EW_ENTITY_ID);
        ShipElectronicWarfareEngineeringAdapter adapter = new ShipElectronicWarfareEngineeringAdapter();

        var pristine = adapter.deriveNoiseJammer(
                EW_ENTITY_ID,
                emitter.transform().position.x,
                emitter.transform().position.y,
                derive(emitter)).orElseThrow();
        assertEquals(EW_ENTITY_ID, pristine.emitterId());
        assertTrue(pristine.radiatedPowerW() > 0d,
                "authored jammer_w must project as real radiated interference power");
        assertEquals(1d, pristine.gainLinear(), 0d);
        assertEquals(1d, pristine.waveformOverlapFraction(), 0d);

        destroySensorEwMount(emitter);

        assertTrue(adapter.deriveNoiseJammer(
                        EW_ENTITY_ID,
                        emitter.transform().position.x,
                        emitter.transform().position.y,
                        derive(emitter)).isEmpty(),
                "destroyed fitted EW/sensor mount must not keep radiating a virtual jammer");
    }

    private static ShipEngineeringState.DerivedShipState derive(CombatantRuntime combatant) {
        var engineering = combatant.engineering();
        return new DerivedShipCalculator(Stage175ICombatTestContentPack.loadDoctrines()).derive(
                combatant.hull(),
                engineering.fit,
                engineering.runtimeState.consumables(),
                engineering.instanceState.damage().moduleDamage());
    }

    private static void destroySensorEwMount(CombatantRuntime combatant) {
        var engineering = combatant.engineering();
        var instance = engineering.instanceState;
        engineering.setInstanceState(new ShipInstanceRuntimeState(
                new Snapshot(
                        instance.damage().compartmentIntegrityById(),
                        new DamageState(Map.of("utility_sensor", 0d))),
                instance.shieldStatesByMount(),
                instance.maintenance(),
                instance.weaponLoadout(),
                instance.weaponMountRuntime()));
    }
}
