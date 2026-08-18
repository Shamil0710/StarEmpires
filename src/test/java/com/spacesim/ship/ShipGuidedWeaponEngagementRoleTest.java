package com.spacesim.ship;

import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.content.weapon.Stage175ICombatTestWeaponPack;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog.GuidedEngagementRole;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipGuidedWeaponEngagementRoleTest {
    @Test
    void defaultGuidedProjectionIsStrikeOnlyAndExplicitInterceptorProjectionIsSeparate() {
        var engineeringCatalog = Stage175ICombatTestContentPack.loadDoctrines();
        var ammunition = Stage175ICombatTestWeaponPack.loadAmmunition();
        var launchers = Stage175ICombatTestWeaponPack.loadLaunchers();
        var doctrine = Stage175IFleetDoctrineCatalog.get(DoctrineId.B_MISSILE_STRIKE);
        var fit = ShipEngineeringState.InstalledFit.fromDemonstrator(
                engineeringCatalog.findDemonstratorFit(doctrine.fitId()));
        var calculator = new DerivedShipCalculator(engineeringCatalog);
        var adapter = new ShipGuidedWeaponEngineeringAdapter();
        var strikeDerived = calculator.derive(
                engineeringCatalog.findHull(fit.hullId()),
                fit,
                doctrine.initialConsumables(),
                ShipEngineeringState.DamageState.pristine());

        var strikeMounts = adapter.deriveGuidedMounts(
                strikeDerived,
                ammunition,
                launchers,
                doctrine.weaponLoadout());

        assertEquals(2, strikeMounts.size());
        assertTrue(strikeMounts.stream()
                .allMatch(value -> value.ammunition().engagementRole() == GuidedEngagementRole.STRIKE));
        assertTrue(adapter.deriveGuidedMounts(
                strikeDerived,
                ammunition,
                launchers,
                doctrine.weaponLoadout(),
                GuidedEngagementRole.INTERCEPTOR).isEmpty());
    }
}
