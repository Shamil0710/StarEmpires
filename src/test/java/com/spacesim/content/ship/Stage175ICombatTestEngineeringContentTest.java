package com.spacesim.content.ship;

import com.spacesim.ship.DerivedShipCalculator;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage175ICombatTestEngineeringContentTest {
    private static final String RESOURCE = "data/content/stage17_5i-combat-test-engineering-v1.json";

    @Test
    void combatTestEngineeringPackUsesOrdinaryProductionSchemaAndContainsRequiredRepresentatives() {
        ShipEngineeringCatalog catalog = loadPack();

        Set<String> hullIds = catalog.getHulls().stream()
                .map(ShipEngineeringCatalog.HullDefinition::id)
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "hull.ct_corvette_v1",
                "hull.ct_frigate_v1",
                "hull.ct_destroyer_v1",
                "hull.ct_cruiser_v1",
                "hull.ct_bulk_freighter_v1",
                "hull.ct_tanker_v1"), hullIds);
        assertEquals(12, catalog.getDemonstratorFits().size());
        assertTrue(catalog.getModules().size() >= 20);
        assertNotNull(catalog.findProtectionStack("protection.ct_light_v1"));
        assertNotNull(catalog.findProtectionStack("protection.ct_heavy_v1"));

        assertEquals(3, fitsForHull(catalog, "hull.ct_frigate_v1"));
        assertEquals(3, fitsForHull(catalog, "hull.ct_destroyer_v1"));
        assertEquals(3, fitsForHull(catalog, "hull.ct_cruiser_v1"));
    }

    @Test
    void everyRepresentativeFitPassesCentralFittingAndDerivedShipPipeline() {
        ShipEngineeringCatalog catalog = loadPack();
        DerivedShipCalculator calculator = new DerivedShipCalculator(catalog);

        for (ShipEngineeringCatalog.DemonstratorFitDefinition definition : catalog.getDemonstratorFits()) {
            ShipEngineeringCatalog.HullDefinition hull = catalog.findHull(definition.hullId());
            InstalledFit fit = InstalledFit.fromDemonstrator(definition);
            DerivedShipState derived = calculator.derive(
                    hull,
                    fit,
                    ConsumableState.empty(),
                    DamageState.pristine());

            assertTrue(derived.validation().isValid(), definition.id() + " must be production-valid");
            assertTrue(derived.totalMassKg() > 0d, definition.id() + " mass");
            assertTrue(derived.availableThrustN() > 0d, definition.id() + " thrust");
            assertTrue(derived.accelerationMps2() > 0d, definition.id() + " acceleration");
        }
    }

    @Test
    void sharedHullsProduceMateriallyDifferentPhysicalFitsWithoutClassBonuses() {
        ShipEngineeringCatalog catalog = loadPack();
        DerivedShipCalculator calculator = new DerivedShipCalculator(catalog);

        DerivedShipState recon = derive(catalog, calculator, "fit.ct_frigate_recon_v1");
        DerivedShipState escort = derive(catalog, calculator, "fit.ct_frigate_escort_v1");
        DerivedShipState missile = derive(catalog, calculator, "fit.ct_frigate_missile_v1");
        assertEquals(recon.hullId(), escort.hullId());
        assertEquals(recon.hullId(), missile.hullId());
        assertNotEquals(recon.installedDryMassKg(), escort.installedDryMassKg());
        assertNotEquals(recon.availableThrustN(), escort.availableThrustN());
        assertNotEquals(recon.continuousHeatMarginW(), escort.continuousHeatMarginW());

        DerivedShipState kinetic = derive(catalog, calculator, "fit.ct_destroyer_kinetic_v1");
        DerivedShipState strike = derive(catalog, calculator, "fit.ct_destroyer_missile_v1");
        DerivedShipState defense = derive(catalog, calculator, "fit.ct_destroyer_defense_v1");
        assertNotEquals(kinetic.installedDryMassKg(), strike.installedDryMassKg());
        assertNotEquals(strike.continuousPowerDemandW(), defense.continuousPowerDemandW());

        DerivedShipState armorGun = derive(catalog, calculator, "fit.ct_cruiser_armor_gun_v1");
        DerivedShipState beam = derive(catalog, calculator, "fit.ct_cruiser_beam_v1");
        DerivedShipState command = derive(catalog, calculator, "fit.ct_cruiser_command_v1");
        assertTrue(armorGun.installedDryMassKg() > command.installedDryMassKg());
        assertTrue(beam.continuousPowerDemandW() > command.continuousPowerDemandW());
    }

    private static long fitsForHull(ShipEngineeringCatalog catalog, String hullId) {
        return catalog.getDemonstratorFits().stream().filter(fit -> hullId.equals(fit.hullId())).count();
    }

    private static DerivedShipState derive(
            ShipEngineeringCatalog catalog,
            DerivedShipCalculator calculator,
            String fitId) {
        ShipEngineeringCatalog.DemonstratorFitDefinition definition = catalog.findDemonstratorFit(fitId);
        return calculator.derive(
                catalog.findHull(definition.hullId()),
                InstalledFit.fromDemonstrator(definition),
                ConsumableState.empty(),
                DamageState.pristine());
    }

    private static ShipEngineeringCatalog loadPack() {
        ClassLoader classLoader = Stage175ICombatTestEngineeringContentTest.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(RESOURCE)) {
            assertNotNull(stream, "Stage-17.5I engineering pack resource");
            return ShipEngineeringCatalogLoader.parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Stage-17.5I engineering pack", exception);
        }
    }
}
