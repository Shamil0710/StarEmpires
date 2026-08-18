package com.spacesim.ship;

import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShipDatalinkEngineeringAdapterTest {
    @Test
    void fittedDatalinkChannelsFollowPhysicalModuleIntegrity() {
        var catalog = Stage175ICombatTestContentPack.loadDoctrines();
        var doctrine = new Stage175IFleetDoctrineCatalog().require(DoctrineId.B_MISSILE_STRIKE);
        var hull = catalog.findHull(doctrine.fit().hullId());
        var calculator = new DerivedShipCalculator(catalog);
        var adapter = new ShipDatalinkEngineeringAdapter();

        var pristine = calculator.derive(
                hull,
                doctrine.fit(),
                doctrine.consumables(),
                DamageState.pristine());
        var half = calculator.derive(
                hull,
                doctrine.fit(),
                doctrine.consumables(),
                new DamageState(Map.of("utility_datalink", 0.5d)));
        var destroyed = calculator.derive(
                hull,
                doctrine.fit(),
                doctrine.consumables(),
                new DamageState(Map.of("utility_datalink", 0d)));

        assertEquals(64, adapter.totalSupportChannels(pristine));
        assertEquals(32, adapter.totalSupportChannels(half));
        assertEquals(0, adapter.totalSupportChannels(destroyed));
    }
}
