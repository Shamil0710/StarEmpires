package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.ShipEngineeringState.InstalledCapability;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipEngineeringState.MaintenanceDemand;
import com.spacesim.ship.ShipEngineeringState.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Central authoritative Stage-17.5 common-budget ship calculator.
 *
 * <p>The calculator resolves shared mass, integration volume, power, energy, heat, crew, physical
 * carried mass and propulsion equations from content definitions. Stage 17.5F additionally applies
 * local module integrity produced by the compartment/subsystem damage model. Hull base projected
 * signature geometry seeds the current scalar radar-cross-section channel; Stage 20 may replace that
 * midpoint seed with aspect/frequency-aware geometry without changing this common signature budget.
 * No result depends on {@code ShipType}, doctrine class or player/AI ownership.</p>
 */
public final class DerivedShipCalculator {
    /** Runtime-only installed-capability key exposing local subsystem integrity to specialized adapters. */
    public static final String RUNTIME_INTEGRITY = "runtime_integrity";

    private static final String THRUST_N = "thrust_n";
    private static final String EXHAUST_VELOCITY_MPS = "exhaust_velocity_mps";
    private static final String RADAR_CROSS_SECTION_M2 = "radar_cross_section_m2";

    private final ShipEngineeringCatalog catalog;
    private final ShipFittingValidator validator;

    /**
     * Creates one calculator over an immutable engineering catalog.
     *
     * @param catalog production engineering catalog
     */
    public DerivedShipCalculator(ShipEngineeringCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.validator = new ShipFittingValidator(catalog);
    }

    /**
     * Derives the common physical state for one fitted hull.
     *
     * @param hull authoritative hull definition
     * @param fit installed fit
     * @param consumables physical cargo/stores/ammunition/reaction-mass state
     * @param damage local Stage-17.5F module-integrity state
     * @return immutable derived state with deterministic warnings
     * @throws InvalidShipFitException when the fit violates a hard fitting/budget rule
     */
    public DerivedShipState derive(
            HullDefinition hull,
            InstalledFit fit,
            ConsumableState consumables,
            DamageState damage) {
        HullDefinition checkedHull = Objects.requireNonNull(hull, "hull");
        InstalledFit checkedFit = Objects.requireNonNull(fit, "fit");
        ConsumableState checkedConsumables = Objects.requireNonNull(consumables, "consumables");
        DamageState checkedDamage = Objects.requireNonNull(damage, "damage");

        ValidationResult validation = validator.validate(
                checkedHull, checkedFit, checkedConsumables, checkedDamage);
        if (!validation.isValid()) {
            throw new InvalidShipFitException(validation);
        }

        double moduleMassKg = 0d;
        double usedVolumeM3 = checkedConsumables.missionIntegrationVolumeM3();
        double powerSupplyW = 0d;
        double powerDemandW = 0d;
        double peakPowerDemandW = 0d;
        double storedEnergyJ = 0d;
        double wasteHeatW = 0d;
        double heatRejectionW = 0d;
        double localThermalCapacityJ = 0d;
        double coolantTransferDemandW = 0d;
        int moduleCrewRequired = 0;
        int automationRequired = 0;
        double thrustN = 0d;
        double massFlowKgPerS = 0d;
        Map<String, Double> signatures = new TreeMap<>();
        signatures.put(RADAR_CROSS_SECTION_M2, checkedHull.baseSignatureGeometryAreaM2());
        List<InstalledCapability> capabilities = new ArrayList<>();
        List<MaintenanceDemand> maintenance = new ArrayList<>();

        for (InstalledModuleDefinition assignment : checkedFit.installedModules()) {
            ModuleDefinition module = catalog.findModule(assignment.moduleId());
            if (module == null) {
                throw new IllegalStateException("Validated fit lost module: " + assignment.moduleId());
            }
            double integrity = checkedDamage.moduleIntegrityByMount().getOrDefault(assignment.mountId(), 1d);
            moduleMassKg += module.massKg();
            usedVolumeM3 += module.occupiedVolumeM3();
            powerSupplyW += module.continuousPowerSupplyW() * integrity;
            powerDemandW += module.continuousPowerDemandW() * integrity;
            peakPowerDemandW += module.peakPowerDemandW() * integrity;
            storedEnergyJ += module.storedEnergyCapacityJ() * integrity;
            wasteHeatW += module.wasteHeatW() * integrity;
            heatRejectionW += module.heatRejectionW() * integrity;
            localThermalCapacityJ += module.localThermalCapacityJ() * integrity;
            coolantTransferDemandW += module.coolantTransferDemandW() * integrity;
            moduleCrewRequired = Math.addExact(moduleCrewRequired, module.crewRequirement());
            automationRequired = Math.addExact(automationRequired, module.automationRequirement());

            for (Map.Entry<String, Double> signature : module.signatureContributions().entrySet()) {
                signatures.merge(signature.getKey(), signature.getValue() * integrity, Double::sum);
            }
            Map<String, Double> runtimeParameters = new TreeMap<>(module.capabilityParameters());
            runtimeParameters.put(RUNTIME_INTEGRITY, integrity);
            capabilities.add(new InstalledCapability(
                    assignment.mountId(), module.id(), module.family(), runtimeParameters));
            maintenance.add(new MaintenanceDemand(assignment.mountId(), module.id(), module.maintenance()));

            if (module.family() == ModuleFamily.MAIN_DRIVE
                    || module.family() == ModuleFamily.MANEUVER_THRUSTERS) {
                double moduleThrustN = module.capabilityParameters().get(THRUST_N) * integrity;
                double exhaustVelocityMps = module.capabilityParameters().get(EXHAUST_VELOCITY_MPS);
                thrustN += moduleThrustN;
                massFlowKgPerS += moduleThrustN / exhaustVelocityMps;
            }
        }

        double installedDryMassKg = checkedHull.bareHullMassKg() + moduleMassKg;
        double consumableMassKg = checkedConsumables.totalCarriedMassKg();
        double totalMassKg = installedDryMassKg + consumableMassKg;
        double effectiveExhaustVelocityMps = massFlowKgPerS > 0d ? thrustN / massFlowKgPerS : 0d;
        double accelerationMps2 = totalMassKg > 0d ? thrustN / totalMassKg : 0d;
        double reactionMassKg = checkedConsumables.reactionMassKg();
        double deltaVMps = deriveDeltaV(totalMassKg, reactionMassKg, effectiveExhaustVelocityMps);
        int crewRequired = Math.max(checkedHull.crewBaseline(), moduleCrewRequired);

        requireFinite("installedDryMassKg", installedDryMassKg);
        requireFinite("consumableMassKg", consumableMassKg);
        requireFinite("totalMassKg", totalMassKg);
        requireFinite("usedVolumeM3", usedVolumeM3);
        requireFinite("powerSupplyW", powerSupplyW);
        requireFinite("powerDemandW", powerDemandW);
        requireFinite("peakPowerDemandW", peakPowerDemandW);
        requireFinite("storedEnergyJ", storedEnergyJ);
        requireFinite("wasteHeatW", wasteHeatW);
        requireFinite("heatRejectionW", heatRejectionW);
        requireFinite("localThermalCapacityJ", localThermalCapacityJ);
        requireFinite("coolantTransferDemandW", coolantTransferDemandW);
        requireFinite("thrustN", thrustN);
        requireFinite("massFlowKgPerS", massFlowKgPerS);
        requireFinite("effectiveExhaustVelocityMps", effectiveExhaustVelocityMps);
        requireFinite("accelerationMps2", accelerationMps2);
        requireFinite("deltaVMps", deltaVMps);

        return new DerivedShipState(
                checkedHull.id(),
                installedDryMassKg,
                consumableMassKg,
                totalMassKg,
                usedVolumeM3,
                checkedHull.internalVolumeM3() - usedVolumeM3,
                powerSupplyW,
                powerDemandW,
                powerSupplyW - powerDemandW,
                peakPowerDemandW,
                storedEnergyJ,
                wasteHeatW,
                heatRejectionW,
                heatRejectionW - wasteHeatW,
                localThermalCapacityJ,
                coolantTransferDemandW,
                crewRequired,
                checkedHull.lifeSupportCapacity(),
                automationRequired,
                checkedConsumables.ammunitionMassKg(),
                checkedConsumables.ammunitionCount(),
                checkedConsumables.storesMassKg(),
                checkedConsumables.cargoMassKg(),
                checkedConsumables.missionPayloadMassKg(),
                reactionMassKg,
                thrustN,
                massFlowKgPerS,
                accelerationMps2,
                effectiveExhaustVelocityMps,
                deltaVMps,
                checkedHull.structuralProtectionStackId(),
                checkedHull.compartments(),
                signatures,
                capabilities,
                maintenance,
                validation);
    }

    /**
     * Resolves a catalog demonstrator through the same runtime calculator boundary.
     *
     * @param fitId demonstrator fit ID
     * @param consumables physical load state
     * @param damage damage-state seam
     * @return derived ship state
     */
    public DerivedShipState deriveDemonstrator(
            String fitId, ConsumableState consumables, DamageState damage) {
        ShipEngineeringCatalog.DemonstratorFitDefinition definition = catalog.findDemonstratorFit(fitId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown demonstrator fit: " + fitId);
        }
        HullDefinition hull = catalog.findHull(definition.hullId());
        if (hull == null) {
            throw new IllegalStateException("Demonstrator references missing hull: " + definition.hullId());
        }
        return derive(hull, InstalledFit.fromDemonstrator(definition), consumables, damage);
    }

    private static double deriveDeltaV(
            double initialMassKg, double reactionMassKg, double effectiveExhaustVelocityMps) {
        if (reactionMassKg <= 0d || effectiveExhaustVelocityMps <= 0d) {
            return 0d;
        }
        double finalMassKg = initialMassKg - reactionMassKg;
        if (finalMassKg <= 0d) {
            throw new IllegalArgumentException("Reaction mass must be below total mass");
        }
        return effectiveExhaustVelocityMps * Math.log(initialMassKg / finalMassKg);
    }

    private static void requireFinite(String field, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalStateException("Derived " + field + " is not finite");
        }
    }

    /** Exception carrying the exact deterministic fitting diagnostics that blocked derivation. */
    public static final class InvalidShipFitException extends IllegalArgumentException {
        /** Exact deterministic fitting diagnostics associated with this rejection. */
        private final ValidationResult validation;

        /**
         * Creates an exception for a failed fitting result.
         *
         * @param validation failed result
         */
        public InvalidShipFitException(ValidationResult validation) {
            super("Invalid ship fit: " + Objects.requireNonNull(validation, "validation").issues());
            this.validation = validation;
        }

        /** @return immutable diagnostics that caused rejection */
        public ValidationResult getValidation() {
            return validation;
        }
    }
}
