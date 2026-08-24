package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.content.ship.Stage175ICombatTestProtectionPack;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.ImportedCombatantState;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.ShipDamageRuntime.Snapshot;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipyardEngineeringService.MaintenanceState;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.Doctrine;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LiveTacticalBattleStage21StrategicImportTest {
    @Test
    void exactImportAcceptsBaseAndRegisteredStrategicVariantWithoutSubstitution() {
        ShipEngineeringCatalog catalog = Stage175ICombatTestContentPack.loadStage21StrategicDoctrines();
        Doctrine alphaDoctrine = Stage175IFleetDoctrineCatalog.get(DoctrineId.A_KINETIC_LINE);
        Doctrine betaDoctrine = Stage175IFleetDoctrineCatalog.get(DoctrineId.B_MISSILE_STRIKE);
        InstalledFit alphaStrategic = InstalledFit.fromDemonstrator(catalog.findDemonstratorFit(
                Stage175ICombatTestContentPack.stage21StrategicFitId(alphaDoctrine.fitId())));
        InstalledFit betaBase = InstalledFit.fromDemonstrator(
                catalog.findDemonstratorFit(betaDoctrine.fitId()));
        EngineeringComponent alpha = component(catalog, alphaDoctrine, alphaStrategic);
        EngineeringComponent beta = component(catalog, betaDoctrine, betaBase);

        LiveTacticalBattleRuntimeState imported = LiveTacticalBattleRuntimeState.importExact(List.of(
                new ImportedCombatantState(101L, Side.ALPHA, alpha, 0d, 0d, 0d, 0d),
                new ImportedCombatantState(202L, Side.BETA, beta, 20_000d, 0d, 0d, 0d)));

        assertEquals(alphaStrategic, imported.requireCombatant(101L).engineering().fit);
        assertEquals(betaBase, imported.requireCombatant(202L).engineering().fit);
        assertEquals(alpha.runtimeState, imported.requireCombatant(101L).engineering().runtimeState);
        assertEquals(beta.instanceState, imported.requireCombatant(202L).engineering().instanceState);
    }

    @Test
    void exactImportRejectsArbitrarySameHullFitEvenWhenItsPhysicalSnapshotIsOtherwiseValid() {
        ShipEngineeringCatalog catalog = Stage175ICombatTestContentPack.loadStage21StrategicDoctrines();
        Doctrine alphaDoctrine = Stage175IFleetDoctrineCatalog.get(DoctrineId.A_KINETIC_LINE);
        Doctrine betaDoctrine = Stage175IFleetDoctrineCatalog.get(DoctrineId.B_MISSILE_STRIKE);
        InstalledFit registered = InstalledFit.fromDemonstrator(catalog.findDemonstratorFit(
                Stage175ICombatTestContentPack.stage21StrategicFitId(alphaDoctrine.fitId())));
        EngineeringComponent validAlpha = component(catalog, alphaDoctrine, registered);
        EngineeringComponent beta = component(
                catalog,
                betaDoctrine,
                InstalledFit.fromDemonstrator(catalog.findDemonstratorFit(betaDoctrine.fitId())));

        ArrayList<com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition> modifiedModules =
                new ArrayList<>(registered.installedModules());
        modifiedModules.remove(0);
        InstalledFit arbitrarySameHull = new InstalledFit(registered.hullId(), modifiedModules);
        EngineeringComponent arbitrary = new EngineeringComponent(
                arbitrarySameHull, validAlpha.runtimeState, validAlpha.instanceState);

        assertThrows(IllegalArgumentException.class, () -> LiveTacticalBattleRuntimeState.importExact(List.of(
                new ImportedCombatantState(101L, Side.ALPHA, arbitrary, 0d, 0d, 0d, 0d),
                new ImportedCombatantState(202L, Side.BETA, beta, 20_000d, 0d, 0d, 0d))));
    }

    private static EngineeringComponent component(
            ShipEngineeringCatalog catalog,
            Doctrine doctrine,
            InstalledFit fit) {
        var hull = catalog.findHull(fit.hullId());
        var layout = Stage175ICombatTestProtectionPack.load().findHullDamageLayout(hull.id());
        Snapshot damage = Snapshot.pristine(hull, layout);
        var runtimeState = new ShipEngineeringRuntime(catalog)
                .initialize(fit, doctrine.initialConsumables(), damage.moduleDamage());
        var instanceState = new ShipInstanceRuntimeState(
                damage,
                Map.of(),
                new MaintenanceState(Map.of()),
                doctrine.weaponLoadout(),
                WeaponMountRuntime.RuntimeState.empty());
        return new EngineeringComponent(fit, runtimeState, instanceState);
    }
}
