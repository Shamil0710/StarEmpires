package com.spacesim.ship;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.flight.EngineeringFlightProfileAdapter;
import com.spacesim.flight.FlightDynamics;
import com.spacesim.persistence.EntityId;
import com.spacesim.ship.DerivedShipCalculator.InvalidShipFitException;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipEngineeringState.ValidationCode;
import com.spacesim.ship.ShipEngineeringState.ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerivedShipCalculatorTest {
    private static final String DEMO_FIT = "fit.escort_destroyer_schema_v1";

    @Test
    void productionDemonstratorDerivesCommonBudgetsWithoutClassBonuses() {
        ShipEngineeringCatalog catalog = ShipEngineeringCatalogLoader.loadDefault();
        DerivedShipCalculator calculator = new DerivedShipCalculator(catalog);

        DerivedShipState state = calculator.deriveDemonstrator(
                DEMO_FIT, ConsumableState.empty(), DamageState.pristine());

        assertEquals("hull.escort_destroyer_v1", state.hullId());
        assertEquals(19_520_000d, state.installedDryMassKg(), 0.001d);
        assertEquals(19_520_000d, state.totalMassKg(), 0.001d);
        assertEquals(32_920d, state.usedInternalVolumeM3(), 0.001d);
        assertEquals(177_080d, state.remainingIntegrationVolumeM3(), 0.001d);
        assertEquals(5_000_000_000d, state.continuousPowerSupplyW(), 0.001d);
        assertEquals(2_233_000_000d, state.continuousPowerDemandW(), 0.001d);
        assertEquals(2_767_000_000d, state.continuousPowerMarginW(), 0.001d);
        assertEquals(4_510_000_000d, state.peakPowerDemandW(), 0.001d);
        assertEquals(48_000_000_000d, state.storedEnergyAvailableJ(), 0.001d);
        assertEquals(1_923_000_000d, state.wasteHeatW(), 0.001d);
        assertEquals(1_500_000_000d, state.heatRejectionW(), 0.001d);
        assertEquals(-423_000_000d, state.continuousHeatMarginW(), 0.001d);
        assertEquals(180, state.crewRequired());
        assertEquals(240, state.crewSupported());
        assertEquals(60, state.automationRequired());
        assertEquals(13_200_000d, state.availableThrustN(), 0.001d);
        assertEquals(13_200_000d / 65_000d, state.massFlowKgPerS(), 1e-9);
        assertEquals(13_200_000d / 19_520_000d, state.accelerationMps2(), 1e-12);
        assertEquals(65_000d, state.effectiveExhaustVelocityMps(), 1e-9);
        assertEquals(0d, state.deltaVMps(), 0d);
        assertEquals(5, state.installedCapabilities().size());
        assertEquals(5, state.maintenanceDemands().size());
        assertEquals(950_000_000_000d, state.signatureContributions().get("plume_w"), 0.001d);
        assertTrue(state.validation().isValid());
        assertTrue(state.validation().issues().stream()
                .anyMatch(issue -> issue.code() == ValidationCode.THERMAL_ENDURANCE_LIMITED));
        assertTrue(state.validation().issues().stream()
                .anyMatch(issue -> issue.code() == ValidationCode.AUTOMATION_CAPACITY_UNMODELED));
        assertTrue(state.validation().issues().stream()
                .anyMatch(issue -> issue.code() == ValidationCode.COOLANT_TRANSFER_CAPACITY_UNMODELED));
        assertTrue(state.validation().issues().stream()
                .anyMatch(issue -> issue.code() == ValidationCode.MOUNT_STRENGTH_CAPACITY_UNMODELED));
    }

    @Test
    void cargoAmmunitionStoresAndReactionMassEnterCommonFlightMass() {
        ShipEngineeringCatalog catalog = ShipEngineeringCatalogLoader.loadDefault();
        DerivedShipCalculator calculator = new DerivedShipCalculator(catalog);
        ConsumableState loaded = new ConsumableState(
                1_000_000d,
                500_000d,
                200_000d,
                500d,
                List.of(
                        new ConsumableLoad(
                                "core_drive", "propellant_feed", InterfaceKind.REACTION_MASS,
                                1_800_000d, 1_800_000d, 0L),
                        new ConsumableLoad(
                                "weapon_spinal", "kinetic_magazine_feed", InterfaceKind.AMMUNITION,
                                120_000d, 120_000d, 800L)));

        DerivedShipState loadedState = calculator.deriveDemonstrator(DEMO_FIT, loaded, DamageState.pristine());
        DerivedShipState emptyState = calculator.deriveDemonstrator(
                DEMO_FIT, ConsumableState.empty(), DamageState.pristine());

        assertEquals(3_620_000d, loadedState.consumableMassKg(), 0.001d);
        assertEquals(23_140_000d, loadedState.totalMassKg(), 0.001d);
        assertEquals(120_000d, loadedState.ammunitionMassKg(), 0.001d);
        assertEquals(800L, loadedState.ammunitionCount());
        assertEquals(1_800_000d, loadedState.reactionMassKg(), 0.001d);
        assertEquals(1_000_000d, loadedState.cargoMassKg(), 0.001d);
        assertEquals(500_000d, loadedState.storesMassKg(), 0.001d);
        assertEquals(200_000d, loadedState.missionPayloadMassKg(), 0.001d);
        assertTrue(loadedState.accelerationMps2() < emptyState.accelerationMps2());
        assertEquals(
                65_000d * Math.log(23_140_000d / 21_340_000d),
                loadedState.deltaVMps(),
                1e-9);

        FlightDynamics.Profile loadedFlight = EngineeringFlightProfileAdapter.profile(loadedState, 1_000f);
        FlightDynamics.Profile emptyFlight = EngineeringFlightProfileAdapter.profile(emptyState, 1_000f);
        assertEquals((float) loadedState.totalMassKg(), loadedFlight.totalMass());
        assertEquals((float) loadedState.consumableMassKg(), loadedFlight.cargoMass());
        assertEquals((float) loadedState.availableThrustN(), loadedFlight.thrust());

        TransformComponent loadedTransform = new TransformComponent();
        TransformComponent emptyTransform = new TransformComponent();
        FlightDynamics.advance(loadedTransform, loadedFlight, 1f, 0f, 1f);
        FlightDynamics.advance(emptyTransform, emptyFlight, 1f, 0f, 1f);
        assertTrue(emptyTransform.velocity.x > loadedTransform.velocity.x);
    }

    @Test
    void invalidPhysicalLoadsRejectDeterministicallyAndDamageDegradesCapabilities() {
        ShipEngineeringCatalog catalog = ShipEngineeringCatalogLoader.loadDefault();
        ShipEngineeringCatalog.DemonstratorFitDefinition definition = catalog.findDemonstratorFit(DEMO_FIT);
        ShipEngineeringCatalog.HullDefinition hull = catalog.findHull(definition.hullId());
        InstalledFit fit = InstalledFit.fromDemonstrator(definition);
        ShipFittingValidator validator = new ShipFittingValidator(catalog);

        ConsumableState overCapacity = new ConsumableState(
                10_000_000d,
                0d,
                0d,
                0d,
                List.of(new ConsumableLoad(
                        "core_drive", "propellant_feed", InterfaceKind.REACTION_MASS,
                        1_800_001d, 1_800_000d, 0L)));
        ValidationResult first = validator.validate(hull, fit, overCapacity, DamageState.pristine());
        ValidationResult second = validator.validate(hull, fit, overCapacity, DamageState.pristine());

        assertEquals(first, second);
        assertFalse(first.isValid());
        assertTrue(first.issues().stream()
                .anyMatch(issue -> issue.code() == ValidationCode.CONSUMABLE_CAPACITY_EXCEEDED));
        assertTrue(first.issues().stream()
                .anyMatch(issue -> issue.code() == ValidationCode.OPERATIONAL_MASS_EXCEEDED));

        InvalidShipFitException capacityFailure = assertThrows(
                InvalidShipFitException.class,
                () -> new DerivedShipCalculator(catalog).derive(hull, fit, overCapacity, DamageState.pristine()));
        assertEquals(first, capacityFailure.getValidation());

        DamageState damaged = new DamageState(Map.of("core_drive", 0.5d));
        ValidationResult damageValidation = validator.validate(hull, fit, ConsumableState.empty(), damaged);
        assertTrue(damageValidation.isValid());
        DerivedShipCalculator calculator = new DerivedShipCalculator(catalog);
        DerivedShipState pristineState = calculator.derive(hull, fit, ConsumableState.empty(), DamageState.pristine());
        DerivedShipState damagedState = calculator.derive(hull, fit, ConsumableState.empty(), damaged);
        assertEquals(pristineState.totalMassKg(), damagedState.totalMassKg(), 0d);
        assertEquals(pristineState.availableThrustN() * 0.5d, damagedState.availableThrustN(), 1e-9d);
        assertEquals(pristineState.accelerationMps2() * 0.5d, damagedState.accelerationMps2(), 1e-12d);
    }

    @Test
    void fiveAcceptedReferenceDesignsMatchAccelerationAndDeltaV() {
        List<ReferenceDesign> designs = List.of(
                new ReferenceDesign("torpedo_corvette", 1_316_000d, 0d, 204_000d, 20_000d, 600_000d,
                        2_140_000d, 2_200_000d, 100_000d, 1.02803738317757d, 32_902.34126082223d, 36),
                new ReferenceDesign("escort_destroyer", 14_305_000d, 0d, 372_000d, 250_000d, 7_000_000d,
                        21_927_000d, 13_200_000d, 100_000d, 0.601997537282802d, 38_454.71005152617d, 160),
                new ReferenceDesign("battleship", 338_789_000d, 0d, 976_000d, 6_000_000d, 200_000_000d,
                        545_765_000d, 137_500_000d, 100_000d, 0.2519399375188955d, 45_642.912661282484d, 1000),
                new ReferenceDesign("bulk_freighter_loaded", 28_000_000d, 90_000_000d, 0d, 0d, 25_000_000d,
                        143_000_000d, 12_000_000d, 80_000d, 0.08391608391608392d, 15_372.800463539408d, 48),
                new ReferenceDesign("fleet_tanker_loaded", 40_000_000d, 90_000_000d, 10_000_000d, 0d, 30_000_000d,
                        170_000_000d, 25_000_000d, 100_000d, 0.14705882352941177d, 19_415.60144409574d, 72));

        for (ReferenceDesign design : designs) {
            ShipEngineeringCatalog catalog = ShipEngineeringCatalogLoader.parse(referenceCatalogJson(design));
            DerivedShipCalculator calculator = new DerivedShipCalculator(catalog);
            ConsumableState loads = new ConsumableState(
                    design.cargoMassKg(),
                    design.storesMassKg(),
                    design.missionPayloadMassKg(),
                    0d,
                    List.of(new ConsumableLoad(
                            "core_drive", "propellant_feed", InterfaceKind.REACTION_MASS,
                            design.reactionMassKg(), design.reactionMassKg(), 0L)));
            DerivedShipState state = calculator.deriveDemonstrator(
                    "fit." + design.id(), loads, DamageState.pristine());

            assertEquals(design.departureMassKg(), state.totalMassKg(), design.departureMassKg() * 1e-12,
                    design.id() + " mass");
            assertEquals(design.expectedAccelerationMps2(), state.accelerationMps2(), 1e-12,
                    design.id() + " acceleration");
            assertEquals(design.expectedDeltaVMps(), state.deltaVMps(), 1e-6,
                    design.id() + " delta-v");
            assertTrue(state.validation().isValid(), design.id() + " validation");
        }
    }

    @Test
    void legacyArchetypeAdapterDoesNotReplacePersistentEntityIdentity() {
        ShipEngineeringCatalog catalog = ShipEngineeringCatalogLoader.loadDefault();
        LegacyShipEngineeringAdapter adapter = new LegacyShipEngineeringAdapter(
                catalog, Map.of("ship.legacy_escort", DEMO_FIT));
        EntityIdComponent persistentId = new EntityIdComponent(new EntityId(42L));
        Entity entity = new Entity()
                .add(new ArchetypeComponent("ship.legacy_escort"))
                .add(persistentId);

        InstalledFit resolved = adapter.resolve(entity).orElseThrow();

        assertEquals("hull.escort_destroyer_v1", resolved.hullId());
        assertSame(persistentId, entity.getComponent(EntityIdComponent.class));
        assertEquals(new EntityId(42L), entity.getComponent(EntityIdComponent.class).id);
        assertEquals(DEMO_FIT, adapter.getMappings().get("ship.legacy_escort"));
        assertThrows(UnsupportedOperationException.class,
                () -> adapter.getMappings().put("ship.other", DEMO_FIT));
        assertTrue(adapter.resolve(new Entity()).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> new LegacyShipEngineeringAdapter(catalog, Map.of("ship.bad", "fit.missing")));
    }

    @Test
    void runtimeStateInputsAreImmutableAndRejectNonsensicalValues() {
        List<ConsumableLoad> mutable = new ArrayList<>();
        mutable.add(new ConsumableLoad(
                "core_drive", "propellant_feed", InterfaceKind.REACTION_MASS, 10d, 10d, 0L));
        ConsumableState state = new ConsumableState(1d, 2d, 3d, 4d, mutable);
        mutable.clear();
        assertEquals(1, state.interfaceLoads().size());
        assertThrows(UnsupportedOperationException.class, () -> state.interfaceLoads().clear());
        assertThrows(IllegalArgumentException.class,
                () -> new ConsumableState(-1d, 0d, 0d, 0d, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ConsumableLoad("m", "i", InterfaceKind.AMMUNITION, 1d, -1d, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new DamageState(Map.of("m", 1.1d)));
        assertThrows(IllegalArgumentException.class,
                () -> EngineeringFlightProfileAdapter.profile(
                        ShipEngineeringCatalogLoader.loadDefault() == null ? null : null, 1f));
    }

    private static String referenceCatalogJson(ReferenceDesign design) {
        double bareHullMassKg = design.designDryMassKg() - 1d;
        double maxOperationalMassKg = design.departureMassKg() * 1.10d;
        double reactionCapacityKg = design.reactionMassKg() + 1d;
        return ("""
                {
                  "schemaVersion": 1,
                  "migrationVersion": 1,
                  "responseSurfaces": [],
                  "materials": [{
                    "id": "material.test_v1",
                    "densityKgPerM3": 1000.0,
                    "tags": ["test"],
                    "thermalConductivityWPerMK": 1.0,
                    "specificHeatJPerKgK": 1.0,
                    "emissivity": 0.5,
                    "radarReflectivity": 0.5,
                    "heavyImpactResponseSurfaceId": null,
                    "constructionMaterialFamilyId": null,
                    "repairMaterialFamilyId": null
                  }],
                  "protectionStacks": [{
                    "id": "protection.test_v1",
                    "mountMassKg": 0.0,
                    "layers": [{
                      "materialId": "material.test_v1",
                      "thicknessM": 0.01,
                      "spacingAfterM": 0.0,
                      "orientationRad": 0.0,
                      "coverageFraction": 1.0,
                      "responseSurfaceId": null
                    }]
                  }],
                  "hulls": [{
                    "id": "hull.%s",
                    "displayName": "Reference %s",
                    "architecture": "FRAME",
                    "boundingDimensionsM": {"lengthM": 10.0, "widthM": 5.0, "heightM": 3.0},
                    "bareHullMassKg": %s,
                    "internalVolumeM3": 1000000.0,
                    "slots": [{
                      "id": "core_drive",
                      "category": "CORE",
                      "maxDimensionsM": {"lengthM": 2.0, "widthM": 2.0, "heightM": 2.0},
                      "maxMassKg": 2.0
                    }],
                    "hardpoints": [],
                    "compartments": [{
                      "id": "engineering",
                      "volumeM3": 1.0,
                      "centerM": {"xM": 0.0, "yM": 0.0, "zM": 0.0},
                      "protectionStackId": "protection.test_v1",
                      "tags": ["test"]
                    }],
                    "crewBaseline": %d,
                    "lifeSupportCapacity": %d,
                    "baseSignatureGeometryAreaM2": 1.0,
                    "structuralProtectionStackId": "protection.test_v1",
                    "maxOperationalMassKg": %s,
                    "thrustMountCompatibility": ["MAIN_DRIVE"]
                  }],
                  "modules": [{
                    "id": "module.%s_drive",
                    "displayName": "Reference drive",
                    "family": "MAIN_DRIVE",
                    "integrationCategories": ["CORE"],
                    "compatibleHardpointSizes": [],
                    "physicalDimensionsM": {"lengthM": 1.0, "widthM": 1.0, "heightM": 1.0},
                    "massKg": 1.0,
                    "occupiedVolumeM3": 1.0,
                    "requiredMountStrengthN": 0.0,
                    "continuousPowerSupplyW": 0.0,
                    "continuousPowerDemandW": 0.0,
                    "peakPowerDemandW": 0.0,
                    "storedEnergyCapacityJ": 0.0,
                    "wasteHeatW": 0.0,
                    "localThermalCapacityJ": 0.0,
                    "coolantTransferDemandW": 0.0,
                    "heatRejectionW": 0.0,
                    "crewRequirement": 0,
                    "automationRequirement": 0,
                    "interfaces": [{"kind": "REACTION_MASS", "id": "propellant_feed", "capacity": %s}],
                    "signatureContributions": {},
                    "constructionInputs": [],
                    "maintenance": {"serviceIntervalSeconds": 1.0, "maintenanceWorkSeconds": 1.0, "repairComplexity": 0.0},
                    "capabilityParameters": {"thrust_n": %s, "exhaust_velocity_mps": %s}
                  }],
                  "demonstratorFits": [{
                    "id": "fit.%s",
                    "hullId": "hull.%s",
                    "installedModules": [{"mountId": "core_drive", "moduleId": "module.%s_drive"}]
                  }]
                }
                """).formatted(
                design.id(), design.id(), Double.toString(bareHullMassKg),
                design.crew(), design.crew(), Double.toString(maxOperationalMassKg),
                design.id(), Double.toString(reactionCapacityKg), Double.toString(design.thrustN()),
                Double.toString(design.exhaustVelocityMps()), design.id(), design.id(), design.id());
    }

    private record ReferenceDesign(
            String id,
            double designDryMassKg,
            double cargoMassKg,
            double storesMassKg,
            double missionPayloadMassKg,
            double reactionMassKg,
            double departureMassKg,
            double thrustN,
            double exhaustVelocityMps,
            double expectedAccelerationMps2,
            double expectedDeltaVMps,
            int crew) {
    }
}
