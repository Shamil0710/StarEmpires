package com.spacesim.combat.acceptance;

import com.spacesim.combat.acceptance.Stage175IShipMaterializer.MaterializedShip;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage175IShipMaterializerTest {
    @Test
    void everyFleetFitMaterializesInsideOrdinaryPhysicalBudgets() {
        Stage175ICombatTestContentPack pack = Stage175ICombatTestContentPack.loadDefault();
        Stage175IShipMaterializer materializer = new Stage175IShipMaterializer(pack);
        var baseline = pack.manifest().findVariation("variation.ct_baseline_v1");
        Set<String> fitIds = new TreeSet<>();
        pack.manifest().fleets().forEach(fleet -> fleet.ships().forEach(row -> fitIds.add(row.fitId())));

        for (String fitId : fitIds) {
            MaterializedShip ship = materializer.materialize(fitId, baseline);
            var hull = pack.engineering().findHull(ship.derived().hullId());
            assertTrue(ship.derived().validation().isValid(), fitId);
            assertTrue(ship.derived().totalMassKg() <= hull.maxOperationalMassKg() + 1e-6d, fitId);
            assertTrue(ship.derived().reactionMassKg() > 0d, fitId + " reaction mass");
            assertEquals(
                    ship.engineering().runtimeState.consumables(),
                    ship.engineering().runtimeState.consumables());
            assertFalse(ship.engineering().fit.installedModules().isEmpty(), fitId);
        }
    }

    @Test
    void ammoDamageAndThermalVariationsChangeOnlyPhysicalInitialState() {
        Stage175ICombatTestContentPack pack = Stage175ICombatTestContentPack.loadDefault();
        Stage175IShipMaterializer materializer = new Stage175IShipMaterializer(pack);
        String fitId = "fit.ct_destroyer_missile_v1";

        MaterializedShip baseline = materializer.materialize(
                fitId, pack.manifest().findVariation("variation.ct_baseline_v1"));
        MaterializedShip halfAmmo = materializer.materialize(
                fitId, pack.manifest().findVariation("variation.ct_ammo_half_v1"));
        MaterializedShip damaged = materializer.materialize(
                fitId, pack.manifest().findVariation("variation.ct_predamaged_v1"));
        MaterializedShip hot = materializer.materialize(
                fitId, pack.manifest().findVariation("variation.ct_thermal_stress_v1"));

        assertTrue(baseline.derived().ammunitionCount() > 0L);
        assertTrue(halfAmmo.derived().ammunitionCount() > 0L);
        assertTrue(halfAmmo.derived().ammunitionCount() < baseline.derived().ammunitionCount());
        assertTrue(halfAmmo.derived().ammunitionMassKg() < baseline.derived().ammunitionMassKg());

        assertTrue(damaged.derived().availableThrustN() < baseline.derived().availableThrustN());
        assertTrue(damaged.engineering().instanceState.damage().moduleDamage().moduleIntegrityByMount()
                .values().stream().allMatch(value -> Math.abs(value - 0.72d) < 1e-12d));
        assertTrue(damaged.engineering().instanceState.damage().compartmentIntegrityById()
                .values().stream().allMatch(value -> Math.abs(value - 0.72d) < 1e-12d));

        assertTrue(hot.engineering().runtimeState.localHeatJByMount().values().stream()
                .anyMatch(value -> value > 0d));
        assertEquals(
                baseline.engineering().instanceState.weaponLoadout().feeds(),
                hot.engineering().instanceState.weaponLoadout().feeds());
    }
}
