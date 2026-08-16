package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.CompartmentDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalog.MaintenanceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable Stage-17.5 runtime inputs and derived outputs shared by ship fitting and flight seams.
 *
 * <p>Definitions in {@code content.ship} describe authored content. The records here describe one
 * fitted physical state and never infer performance from a doctrine/class name.</p>
 */
public final class ShipEngineeringState {
    private ShipEngineeringState() {
        throw new AssertionError("utility namespace");
    }

    /** Severity of one deterministic fitting diagnostic. */
    public enum ValidationSeverity {
        /** The fit cannot be derived as an authoritative operational configuration. */ ERROR,
        /** The fit is physically derivable, but a later Stage-17.5 subsystem must close the budget. */ WARNING
    }

    /** Stable machine-readable fitting diagnostic code. */
    public enum ValidationCode {
        /** Fit and requested hull IDs differ. */ HULL_ID_MISMATCH,
        /** Referenced module does not exist. */ UNKNOWN_MODULE,
        /** Referenced slot/hardpoint does not exist. */ UNKNOWN_MOUNT,
        /** One physical mount is used more than once. */ DUPLICATE_MOUNT,
        /** Internal slot category and module category are incompatible. */ SLOT_CATEGORY_INCOMPATIBLE,
        /** Module exceeds an internal slot mass/dimension envelope. */ SLOT_ENVELOPE_EXCEEDED,
        /** External hardpoint does not accept the module family. */ HARDPOINT_FAMILY_INCOMPATIBLE,
        /** External hardpoint size and module size are incompatible. */ HARDPOINT_SIZE_INCOMPATIBLE,
        /** Module exceeds an external hardpoint mass/dimension envelope. */ HARDPOINT_ENVELOPE_EXCEEDED,
        /** Weapon recoil exceeds the authored hardpoint impulse limit. */ HARDPOINT_RECOIL_EXCEEDED,
        /** Propulsion module family is not supported by the hull thrust-mount contract. */ THRUST_MOUNT_INCOMPATIBLE,
        /** Installed mass plus physical carried mass exceeds the hull operating limit. */ OPERATIONAL_MASS_EXCEEDED,
        /** Installed integration volume plus explicit mission-space use exceeds hull volume. */ INTERNAL_VOLUME_EXCEEDED,
        /** Continuous electrical demand exceeds continuous supply. */ CONTINUOUS_POWER_DEFICIT,
        /** Peak demand exceeds supply and no stored energy is fitted. */ PEAK_POWER_DEFICIT,
        /** Peak demand exceeds supply and therefore has finite stored-energy endurance. */ PEAK_POWER_STORAGE_LIMITED,
        /** Continuous waste heat exceeds rejection and no thermal buffer exists. */ CONTINUOUS_HEAT_DEFICIT,
        /** Continuous waste heat exceeds rejection and therefore has finite thermal endurance. */ THERMAL_ENDURANCE_LIMITED,
        /** Required crew exceeds supported life-support capacity. */ CREW_CAPACITY_EXCEEDED,
        /** A consumable load references a missing/uninstalled interface. */ UNKNOWN_CONSUMABLE_INTERFACE,
        /** Consumable load kind differs from the authored interface kind. */ CONSUMABLE_KIND_MISMATCH,
        /** Consumable load exceeds the authored interface capacity. */ CONSUMABLE_CAPACITY_EXCEEDED,
        /** Main/maneuver drive lacks the physical thrust/exhaust parameters required for derivation. */ PROPULSION_PARAMETERS_MISSING,
        /** Non-pristine damage is reserved for the Stage-17.5F subsystem-damage model. */ DAMAGE_MODEL_NOT_ACTIVE,
        /** Module mount-strength demand has no symmetric hull capacity axis in schema v1. */ MOUNT_STRENGTH_CAPACITY_UNMODELED,
        /** Automation demand is known but schema v1 has no ship automation-capacity budget. */ AUTOMATION_CAPACITY_UNMODELED,
        /** Coolant demand is known but schema v1 has no explicit ship-bus transfer-capacity topology. */ COOLANT_TRANSFER_CAPACITY_UNMODELED
    }

    /**
     * Immutable module assignment list for one runtime fit.
     *
     * @param hullId stable hull content ID
     * @param installedModules module-to-mount assignments
     */
    public record InstalledFit(String hullId, List<InstalledModuleDefinition> installedModules) {
        /**
         * Creates a deterministic immutable installed fit.
         *
         * @param hullId stable hull content ID
         * @param installedModules module-to-mount assignments
         */
        public InstalledFit {
            requireNonBlank(hullId, "hullId");
            Objects.requireNonNull(installedModules, "installedModules");
            List<InstalledModuleDefinition> copy = new ArrayList<>(installedModules);
            if (copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("installedModules must not contain null");
            }
            copy.sort(Comparator.comparing(InstalledModuleDefinition::mountId)
                    .thenComparing(InstalledModuleDefinition::moduleId));
            installedModules = List.copyOf(copy);
        }

        /**
         * Converts a Stage-17.5A demonstrator definition into a runtime fitting input.
         *
         * @param fit source demonstrator
         * @return immutable runtime fit preserving hull/mount/module IDs
         */
        public static InstalledFit fromDemonstrator(DemonstratorFitDefinition fit) {
            DemonstratorFitDefinition checked = Objects.requireNonNull(fit, "fit");
            return new InstalledFit(checked.hullId(), checked.installedModules());
        }
    }

    /**
     * One physical load attached to a concrete module interface.
     *
     * <p>{@code amount} uses the interface's authored native capacity unit. {@code massKg} is always
     * explicit SI mass and is what contributes to ship dynamics. This avoids inventing a universal
     * ammunition/propellant unit before Stage 17.5C/E.</p>
     *
     * @param mountId installed module mount ID
     * @param interfaceId module-local interface ID
     * @param kind physical interface kind
     * @param amount amount in the authored interface-capacity unit
     * @param massKg physical loaded mass
     * @param itemCount physical item count where meaningful; zero is allowed
     */
    public record ConsumableLoad(
            String mountId,
            String interfaceId,
            InterfaceKind kind,
            double amount,
            double massKg,
            long itemCount) {
        /**
         * Creates one validated physical interface load.
         *
         * @param mountId installed module mount ID
         * @param interfaceId module-local interface ID
         * @param kind physical interface kind
         * @param amount amount in the authored interface-capacity unit
         * @param massKg physical loaded mass
         * @param itemCount physical item count where meaningful; zero is allowed
         */
        public ConsumableLoad {
            requireNonBlank(mountId, "mountId");
            requireNonBlank(interfaceId, "interfaceId");
            Objects.requireNonNull(kind, "kind");
            requireNonNegativeFinite(amount, "amount");
            requireNonNegativeFinite(massKg, "massKg");
            if (itemCount < 0L) {
                throw new IllegalArgumentException("itemCount must be non-negative");
            }
        }
    }

    /**
     * Physical non-module load state used by the central derived calculator.
     *
     * @param cargoMassKg commercial/mission cargo mass not already counted as module dry mass
     * @param storesMassKg crew/maintenance/general stores mass
     * @param missionPayloadMassKg mission payload mass outside interface loads
     * @param missionIntegrationVolumeM3 explicit generic integration volume occupied by payload
     * @param interfaceLoads ammunition, consumable and reaction-mass loads
     */
    public record ConsumableState(
            double cargoMassKg,
            double storesMassKg,
            double missionPayloadMassKg,
            double missionIntegrationVolumeM3,
            List<ConsumableLoad> interfaceLoads) {
        /**
         * Creates a deterministic immutable physical load state.
         *
         * @param cargoMassKg commercial/mission cargo mass not already counted as module dry mass
         * @param storesMassKg crew/maintenance/general stores mass
         * @param missionPayloadMassKg mission payload mass outside interface loads
         * @param missionIntegrationVolumeM3 explicit generic integration volume occupied by payload
         * @param interfaceLoads ammunition, consumable and reaction-mass loads
         */
        public ConsumableState {
            requireNonNegativeFinite(cargoMassKg, "cargoMassKg");
            requireNonNegativeFinite(storesMassKg, "storesMassKg");
            requireNonNegativeFinite(missionPayloadMassKg, "missionPayloadMassKg");
            requireNonNegativeFinite(missionIntegrationVolumeM3, "missionIntegrationVolumeM3");
            Objects.requireNonNull(interfaceLoads, "interfaceLoads");
            List<ConsumableLoad> copy = new ArrayList<>(interfaceLoads);
            if (copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("interfaceLoads must not contain null");
            }
            copy.sort(Comparator.comparing(ConsumableLoad::mountId)
                    .thenComparing(ConsumableLoad::interfaceId)
                    .thenComparing(load -> load.kind().name()));
            interfaceLoads = List.copyOf(copy);
        }

        /** @return completely empty physical load state */
        public static ConsumableState empty() {
            return new ConsumableState(0d, 0d, 0d, 0d, List.of());
        }

        /** @return total mass of all interface-bound loads */
        public double interfaceLoadMassKg() {
            return interfaceLoads.stream().mapToDouble(ConsumableLoad::massKg).sum();
        }

        /** @return total non-module carried mass */
        public double totalCarriedMassKg() {
            return cargoMassKg + storesMassKg + missionPayloadMassKg + interfaceLoadMassKg();
        }

        /** @return total physical reaction mass */
        public double reactionMassKg() {
            return massFor(InterfaceKind.REACTION_MASS);
        }

        /** @return total physical ammunition mass */
        public double ammunitionMassKg() {
            return massFor(InterfaceKind.AMMUNITION);
        }

        /** @return total authored ammunition item count */
        public long ammunitionCount() {
            long result = 0L;
            for (ConsumableLoad load : interfaceLoads) {
                if (load.kind() == InterfaceKind.AMMUNITION) {
                    result = Math.addExact(result, load.itemCount());
                }
            }
            return result;
        }

        private double massFor(InterfaceKind kind) {
            return interfaceLoads.stream()
                    .filter(load -> load.kind() == kind)
                    .mapToDouble(ConsumableLoad::massKg)
                    .sum();
        }
    }

    /**
     * Stage-17.5B damage input seam.
     *
     * <p>Integrity values are carried now so the central API does not need to change later. 17.5B
     * intentionally derives only pristine state; non-pristine capability degradation is rejected
     * until Stage 17.5F can route real compartment/subsystem damage without generic debuffs.</p>
     *
     * @param moduleIntegrityByMount optional mount integrity values in [0,1]
     */
    public record DamageState(Map<String, Double> moduleIntegrityByMount) {
        /**
         * Creates a deterministic immutable damage input.
         *
         * @param moduleIntegrityByMount optional mount integrity values in [0,1]
         */
        public DamageState {
            Objects.requireNonNull(moduleIntegrityByMount, "moduleIntegrityByMount");
            TreeMap<String, Double> copy = new TreeMap<>();
            for (Map.Entry<String, Double> entry : moduleIntegrityByMount.entrySet()) {
                requireNonBlank(entry.getKey(), "damage mount ID");
                Double value = Objects.requireNonNull(entry.getValue(), "damage integrity");
                if (!Double.isFinite(value) || value < 0d || value > 1d) {
                    throw new IllegalArgumentException("damage integrity must be in [0,1]");
                }
                copy.put(entry.getKey(), value);
            }
            moduleIntegrityByMount = Collections.unmodifiableMap(copy);
        }

        /** @return pristine damage state */
        public static DamageState pristine() {
            return new DamageState(Map.of());
        }

        /** @return whether no actual degradation is requested */
        public boolean isPristine() {
            return moduleIntegrityByMount.values().stream().allMatch(value -> value == 1d);
        }
    }

    /**
     * One deterministic validator result row.
     *
     * @param severity diagnostic severity
     * @param code stable diagnostic code
     * @param subject stable mount/module/budget subject
     * @param detail concise deterministic detail
     */
    public record ValidationIssue(
            ValidationSeverity severity, ValidationCode code, String subject, String detail) {
        /**
         * Creates one immutable fitting diagnostic.
         *
         * @param severity diagnostic severity
         * @param code stable diagnostic code
         * @param subject stable mount/module/budget subject
         * @param detail concise deterministic detail
         */
        public ValidationIssue {
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(code, "code");
            subject = subject == null ? "" : subject;
            detail = detail == null ? "" : detail;
        }
    }

    /**
     * Immutable deterministic fitting-validation result.
     *
     * @param issues sorted diagnostics
     */
    public record ValidationResult(List<ValidationIssue> issues) {
        /**
         * Creates one deterministically sorted validation result.
         *
         * @param issues diagnostics to sort and freeze
         */
        public ValidationResult {
            Objects.requireNonNull(issues, "issues");
            List<ValidationIssue> copy = new ArrayList<>(issues);
            copy.sort(Comparator.comparing((ValidationIssue issue) -> issue.severity().name())
                    .thenComparing(issue -> issue.code().name())
                    .thenComparing(ValidationIssue::subject)
                    .thenComparing(ValidationIssue::detail));
            issues = List.copyOf(copy);
        }

        /** @return {@code true} when no hard fitting error exists */
        public boolean isValid() {
            return issues.stream().noneMatch(issue -> issue.severity() == ValidationSeverity.ERROR);
        }

        /** @return number of hard errors */
        public long errorCount() {
            return issues.stream().filter(issue -> issue.severity() == ValidationSeverity.ERROR).count();
        }

        /** @return number of warnings */
        public long warningCount() {
            return issues.stream().filter(issue -> issue.severity() == ValidationSeverity.WARNING).count();
        }
    }

    /**
     * Resolved family-specific parameters for one installed module.
     *
     * @param mountId hull-local mount ID
     * @param moduleId module content ID
     * @param family module family
     * @param parameters immutable sorted capability map
     */
    public record InstalledCapability(
            String mountId, String moduleId, ModuleFamily family, Map<String, Double> parameters) {
        /**
         * Creates an immutable family-specific capability projection.
         *
         * @param mountId hull-local mount ID
         * @param moduleId module content ID
         * @param family module family
         * @param parameters immutable sorted capability map
         */
        public InstalledCapability {
            requireNonBlank(mountId, "mountId");
            requireNonBlank(moduleId, "moduleId");
            Objects.requireNonNull(family, "family");
            Objects.requireNonNull(parameters, "parameters");
            parameters = Collections.unmodifiableMap(new TreeMap<>(parameters));
        }
    }

    /**
     * Physical maintenance demand preserved per installed module rather than collapsed into a fake tier.
     *
     * @param mountId hull-local mount ID
     * @param moduleId module content ID
     * @param maintenance authored physical work/service metadata
     */
    public record MaintenanceDemand(String mountId, String moduleId, MaintenanceDefinition maintenance) {
        /**
         * Creates one immutable physical maintenance demand.
         *
         * @param mountId hull-local mount ID
         * @param moduleId module content ID
         * @param maintenance authored physical work/service metadata
         */
        public MaintenanceDemand {
            requireNonBlank(mountId, "mountId");
            requireNonBlank(moduleId, "moduleId");
            Objects.requireNonNull(maintenance, "maintenance");
        }
    }

    /**
     * Central Stage-17.5B derived physical state.
     *
     * <p>Specialized sensor/weapon/shield equations are intentionally not guessed here. Their
     * installed capability parameters are exposed through {@link #installedCapabilities()} for
     * Stage 17.5D-F consumers while common mass/power/heat/propulsion budgets are already resolved.</p>
     *
     * @param hullId stable hull content ID
     * @param installedDryMassKg bare hull plus installed module mass
     * @param consumableMassKg all carried non-module physical mass
     * @param totalMassKg current physical ship mass
     * @param usedInternalVolumeM3 installed module plus mission integration volume
     * @param remainingIntegrationVolumeM3 remaining hull integration volume
     * @param continuousPowerSupplyW continuous installed electrical supply
     * @param continuousPowerDemandW continuous installed electrical demand
     * @param continuousPowerMarginW supply minus continuous demand
     * @param peakPowerDemandW simultaneous authored peak demand
     * @param storedEnergyAvailableJ installed stored electrical energy
     * @param wasteHeatW continuous authored waste heat
     * @param heatRejectionW continuous authored heat rejection
     * @param continuousHeatMarginW rejection minus continuous waste heat
     * @param localThermalCapacityJ aggregate local thermal buffer capacity
     * @param coolantTransferDemandW aggregate authored coolant-transfer demand
     * @param crewRequired larger of hull baseline and simultaneous module crew demand
     * @param crewSupported hull life-support capacity
     * @param automationRequired aggregate authored automation demand
     * @param ammunitionMassKg physical ammunition mass
     * @param ammunitionCount authored ammunition item count
     * @param storesMassKg physical general stores mass
     * @param cargoMassKg physical cargo mass
     * @param missionPayloadMassKg physical mission payload mass
     * @param reactionMassKg physical propulsion reaction mass
     * @param availableThrustN aggregate installed propulsion thrust before Stage-17.5C throttling
     * @param massFlowKgPerS aggregate idealized propulsion mass flow
     * @param accelerationMps2 thrust divided by current total physical mass
     * @param effectiveExhaustVelocityMps thrust-weighted effective exhaust velocity
     * @param deltaVMps idealized current rocket-equation delta-v
     * @param structuralProtectionStackId hull structural protection content ID
     * @param compartments immutable compartment geometry/protection definitions
     * @param signatureContributions aggregate authored signature-channel contributions
     * @param installedCapabilities family-specific parameters preserved per mount
     * @param maintenanceDemands physical maintenance metadata preserved per mount
     * @param validation deterministic warnings associated with the accepted fit
     */
    public record DerivedShipState(
            String hullId,
            double installedDryMassKg,
            double consumableMassKg,
            double totalMassKg,
            double usedInternalVolumeM3,
            double remainingIntegrationVolumeM3,
            double continuousPowerSupplyW,
            double continuousPowerDemandW,
            double continuousPowerMarginW,
            double peakPowerDemandW,
            double storedEnergyAvailableJ,
            double wasteHeatW,
            double heatRejectionW,
            double continuousHeatMarginW,
            double localThermalCapacityJ,
            double coolantTransferDemandW,
            int crewRequired,
            int crewSupported,
            int automationRequired,
            double ammunitionMassKg,
            long ammunitionCount,
            double storesMassKg,
            double cargoMassKg,
            double missionPayloadMassKg,
            double reactionMassKg,
            double availableThrustN,
            double massFlowKgPerS,
            double accelerationMps2,
            double effectiveExhaustVelocityMps,
            double deltaVMps,
            String structuralProtectionStackId,
            List<CompartmentDefinition> compartments,
            Map<String, Double> signatureContributions,
            List<InstalledCapability> installedCapabilities,
            List<MaintenanceDemand> maintenanceDemands,
            ValidationResult validation) {
        /**
         * Creates the immutable central derived ship state.
         *
         * @param hullId stable hull content ID
         * @param installedDryMassKg bare hull plus installed module mass
         * @param consumableMassKg all carried non-module physical mass
         * @param totalMassKg current physical ship mass
         * @param usedInternalVolumeM3 installed module plus mission integration volume
         * @param remainingIntegrationVolumeM3 remaining hull integration volume
         * @param continuousPowerSupplyW continuous installed electrical supply
         * @param continuousPowerDemandW continuous installed electrical demand
         * @param continuousPowerMarginW supply minus continuous demand
         * @param peakPowerDemandW simultaneous authored peak demand
         * @param storedEnergyAvailableJ installed stored electrical energy
         * @param wasteHeatW continuous authored waste heat
         * @param heatRejectionW continuous authored heat rejection
         * @param continuousHeatMarginW rejection minus continuous waste heat
         * @param localThermalCapacityJ aggregate local thermal buffer capacity
         * @param coolantTransferDemandW aggregate authored coolant-transfer demand
         * @param crewRequired larger of hull baseline and simultaneous module crew demand
         * @param crewSupported hull life-support capacity
         * @param automationRequired aggregate authored automation demand
         * @param ammunitionMassKg physical ammunition mass
         * @param ammunitionCount authored ammunition item count
         * @param storesMassKg physical general stores mass
         * @param cargoMassKg physical cargo mass
         * @param missionPayloadMassKg physical mission payload mass
         * @param reactionMassKg physical propulsion reaction mass
         * @param availableThrustN aggregate installed propulsion thrust before Stage-17.5C throttling
         * @param massFlowKgPerS aggregate idealized propulsion mass flow
         * @param accelerationMps2 thrust divided by current total physical mass
         * @param effectiveExhaustVelocityMps thrust-weighted effective exhaust velocity
         * @param deltaVMps idealized current rocket-equation delta-v
         * @param structuralProtectionStackId hull structural protection content ID
         * @param compartments immutable compartment geometry/protection definitions
         * @param signatureContributions aggregate authored signature-channel contributions
         * @param installedCapabilities family-specific parameters preserved per mount
         * @param maintenanceDemands physical maintenance metadata preserved per mount
         * @param validation deterministic warnings associated with the accepted fit
         */
        public DerivedShipState {
            requireNonBlank(hullId, "hullId");
            requireNonBlank(structuralProtectionStackId, "structuralProtectionStackId");
            Objects.requireNonNull(compartments, "compartments");
            Objects.requireNonNull(signatureContributions, "signatureContributions");
            Objects.requireNonNull(installedCapabilities, "installedCapabilities");
            Objects.requireNonNull(maintenanceDemands, "maintenanceDemands");
            Objects.requireNonNull(validation, "validation");
            compartments = List.copyOf(compartments);
            signatureContributions = Collections.unmodifiableMap(new TreeMap<>(signatureContributions));
            installedCapabilities = List.copyOf(installedCapabilities);
            maintenanceDemands = List.copyOf(maintenanceDemands);
        }

        /** @return all carried non-module mass that affects common flight dynamics */
        public double carriedMassKg() {
            return consumableMassKg;
        }
    }

    private static void requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void requireNonNegativeFinite(double value, String field) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(field + " must be finite and non-negative");
        }
    }
}
