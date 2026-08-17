package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.content.weapon.Stage175ICombatTestWeaponPack;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog;
import com.spacesim.content.weapon.WeaponLauncherCatalog;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.Doctrine;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage175IFleetDoctrineCatalogTest {
    @Test
    void exposesExactlyTheFiveRequiredAcceptanceDoctrinesWithoutNumericBonuses() {
        var doctrines = Stage175IFleetDoctrineCatalog.all();
        Set<DoctrineId> ids = doctrines.stream().map(Doctrine::id).collect(Collectors.toSet());

        assertEquals(5, doctrines.size());
        assertEquals(EnumSet.allOf(DoctrineId.class), ids);
        assertEquals(5, doctrines.stream().map(Doctrine::fitId).distinct().count());
    }

    @Test
    void everyDoctrineFitRemainsValidWithItsRealReactionMassAndAmmunitionLoaded() {
        ShipEngineeringCatalog catalog = Stage175ICombatTestContentPack.loadDoctrines();
        ShipFittingValidator validator = new ShipFittingValidator(catalog);
        var hull = catalog.findHull("hull.test_doctrine_destroyer_v1");

        for (Doctrine doctrine : Stage175IFleetDoctrineCatalog.all()) {
            InstalledFit fit = InstalledFit.fromDemonstrator(catalog.findDemonstratorFit(doctrine.fitId()));
            var result = validator.validate(
                    hull,
                    fit,
                    doctrine.initialConsumables(),
                    DamageState.pristine());
            assertTrue(result.isValid(), () -> doctrine.id() + " errors=" + result.issues());
        }
    }

    @Test
    void physicalStoresProduceDifferentLoadedMassAndDeltaVWithoutDoctrineMultipliers() {
        ShipEngineeringCatalog catalog = Stage175ICombatTestContentPack.loadDoctrines();
        DerivedShipCalculator calculator = new DerivedShipCalculator(catalog);
        var hull = catalog.findHull("hull.test_doctrine_destroyer_v1");

        Doctrine kineticDoctrine = Stage175IFleetDoctrineCatalog.get(DoctrineId.A_KINETIC_LINE);
        Doctrine missileDoctrine = Stage175IFleetDoctrineCatalog.get(DoctrineId.B_MISSILE_STRIKE);
        Doctrine beamDoctrine = Stage175IFleetDoctrineCatalog.get(DoctrineId.C_HIGH_MOBILITY_BEAM);

        var kinetic = calculator.derive(
                hull,
                InstalledFit.fromDemonstrator(catalog.findDemonstratorFit(kineticDoctrine.fitId())),
                kineticDoctrine.initialConsumables(),
                DamageState.pristine());
        var missile = calculator.derive(
                hull,
                InstalledFit.fromDemonstrator(catalog.findDemonstratorFit(missileDoctrine.fitId())),
                missileDoctrine.initialConsumables(),
                DamageState.pristine());
        var beam = calculator.derive(
                hull,
                InstalledFit.fromDemonstrator(catalog.findDemonstratorFit(beamDoctrine.fitId())),
                beamDoctrine.initialConsumables(),
                DamageState.pristine());

        assertNotEquals(kinetic.ammunitionMassKg(), missile.ammunitionMassKg());
        assertTrue(beam.availableThrustN() > kinetic.availableThrustN());
        assertNotEquals(beam.deltaVMps(), kinetic.deltaVMps());
    }

    @Test
    void launcherDoctrinesResolvePhysicalKineticFeedsAndConsumeRealRounds() {
        ShipEngineeringCatalog engineering = Stage175ICombatTestContentPack.loadDoctrines();
        WeaponAmmunitionCatalog ammunition = Stage175ICombatTestWeaponPack.loadAmmunition();
        WeaponLauncherCatalog launchers = Stage175ICombatTestWeaponPack.loadLaunchers();
        DerivedShipCalculator calculator = new DerivedShipCalculator(engineering);
        ShipWeaponEngineeringAdapter adapter = new ShipWeaponEngineeringAdapter();
        var hull = engineering.findHull("hull.test_doctrine_destroyer_v1");

        Doctrine kineticDoctrine = Stage175IFleetDoctrineCatalog.get(DoctrineId.A_KINETIC_LINE);
        var kineticDerived = calculator.derive(
                hull,
                InstalledFit.fromDemonstrator(engineering.findDemonstratorFit(kineticDoctrine.fitId())),
                kineticDoctrine.initialConsumables(),
                DamageState.pristine());
        var kineticMounts = adapter.deriveKineticMounts(
                kineticDerived, ammunition, launchers, kineticDoctrine.weaponLoadout());
        assertEquals(3, kineticMounts.size());

        var primary = kineticMounts.stream()
                .filter(value -> value.mountId().equals("weapon_primary"))
                .findFirst()
                .orElseThrow();
        AmmunitionRuntime runtime = new AmmunitionRuntime();
        long before = kineticDoctrine.initialConsumables().ammunitionCount();
        var consumed = runtime.consumeOne(
                kineticDoctrine.initialConsumables(),
                primary.mountId(),
                primary.launcher(),
                primary.round().massKg());
        assertEquals(before - 1L, consumed.consumables().ammunitionCount());
        assertEquals(150d, consumed.consumedMassKg());
    }
}
