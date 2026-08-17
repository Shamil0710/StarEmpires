package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.ship.DerivedShipCalculator;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipFittingValidator;
import com.spacesim.ship.ShipSensorEngineeringAdapter;
import com.spacesim.ship.ShipShieldEngineeringAdapter;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage175IDoctrineContentPackTest {
    @Test
    void fiveDoctrineFitsUseTheOrdinaryProductionFittingBoundary() {
        ShipEngineeringCatalog catalog = Stage175ICombatTestContentPack.loadDoctrines();
        HullDefinition hull = catalog.findHull("hull.test_doctrine_destroyer_v1");
        ShipFittingValidator validator = new ShipFittingValidator(catalog);

        assertEquals(1, catalog.getHulls().size());
        assertEquals(5, catalog.getDemonstratorFits().size());
        for (DemonstratorFitDefinition definition : catalog.getDemonstratorFits()) {
            var result = validator.validate(
                    hull,
                    InstalledFit.fromDemonstrator(definition),
                    ConsumableState.empty(),
                    DamageState.pristine());
            assertTrue(result.isValid(), () -> definition.id() + " errors=" + result.issues());
        }
    }

    @Test
    void doctrineLabelsDoNotCreatePerformanceAndPhysicalFitsActuallyDiffer() {
        ShipEngineeringCatalog catalog = Stage175ICombatTestContentPack.loadDoctrines();
        HullDefinition hull = catalog.findHull("hull.test_doctrine_destroyer_v1");
        DerivedShipCalculator calculator = new DerivedShipCalculator(catalog);
        Map<String, DerivedShipState> derived = catalog.getDemonstratorFits().stream()
                .collect(Collectors.toMap(
                        DemonstratorFitDefinition::id,
                        definition -> calculator.derive(
                                hull,
                                InstalledFit.fromDemonstrator(definition),
                                ConsumableState.empty(),
                                DamageState.pristine())));

        DerivedShipState kinetic = derived.get("fit.test_doctrine_a_kinetic_v1");
        DerivedShipState missile = derived.get("fit.test_doctrine_b_missile_v1");
        DerivedShipState beam = derived.get("fit.test_doctrine_c_beam_v1");
        DerivedShipState defensive = derived.get("fit.test_doctrine_d_defensive_ew_v1");
        DerivedShipState balanced = derived.get("fit.test_doctrine_e_balanced_v1");

        assertTrue(beam.availableThrustN() > kinetic.availableThrustN());
        assertTrue(beam.continuousPowerSupplyW() > kinetic.continuousPowerSupplyW());
        assertTrue(beam.heatRejectionW() > kinetic.heatRejectionW());
        assertTrue(beam.wasteHeatW() > kinetic.wasteHeatW());
        assertNotEquals(kinetic.installedDryMassKg(), missile.installedDryMassKg());
        assertNotEquals(missile.installedDryMassKg(), defensive.installedDryMassKg());
        assertNotEquals(defensive.installedDryMassKg(), balanced.installedDryMassKg());
    }

    @Test
    void reconAndDefensiveFitsExposeMateriallyDifferentSensorAndShieldCapabilities() {
        ShipEngineeringCatalog catalog = Stage175ICombatTestContentPack.loadDoctrines();
        HullDefinition hull = catalog.findHull("hull.test_doctrine_destroyer_v1");
        DerivedShipCalculator calculator = new DerivedShipCalculator(catalog);
        Function<String, DerivedShipState> derive = id -> calculator.derive(
                hull,
                InstalledFit.fromDemonstrator(catalog.findDemonstratorFit(id)),
                ConsumableState.empty(),
                DamageState.pristine());

        DerivedShipState kinetic = derive.apply("fit.test_doctrine_a_kinetic_v1");
        DerivedShipState missile = derive.apply("fit.test_doctrine_b_missile_v1");
        DerivedShipState defensive = derive.apply("fit.test_doctrine_d_defensive_ew_v1");

        ShipSensorEngineeringAdapter sensorAdapter = new ShipSensorEngineeringAdapter();
        double kineticAperture = sensorAdapter.derive(kinetic).sensors().get(0).definition().apertureAreaM2();
        double missileAperture = sensorAdapter.derive(missile).sensors().get(0).definition().apertureAreaM2();
        assertTrue(missileAperture > kineticAperture);
        assertTrue(sensorAdapter.derive(defensive).staticSignature().jammerPowerW() > 0d);

        ShipShieldEngineeringAdapter shieldAdapter = new ShipShieldEngineeringAdapter();
        double kineticReserve = shieldAdapter.derive(kinetic).get(0).definition().fieldReserveJ();
        double defensiveReserve = shieldAdapter.derive(defensive).get(0).definition().fieldReserveJ();
        assertTrue(defensiveReserve > kineticReserve);
    }

    @Test
    void doctrineCatalogFingerprintIsStableAndContentRemainsProvisional() {
        ShipEngineeringCatalog first = Stage175ICombatTestContentPack.loadDoctrines();
        ShipEngineeringCatalog second = Stage175ICombatTestContentPack.loadDoctrines();

        assertEquals(first.getFingerprint(), second.getFingerprint());
        assertEquals(64, first.getFingerprint().length());
        assertTrue(first.getMaterials().stream()
                .flatMap(value -> value.tags().stream())
                .anyMatch("content_provisional"::equals));
    }
}
