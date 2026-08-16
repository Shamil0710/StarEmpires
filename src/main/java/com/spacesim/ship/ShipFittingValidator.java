package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.content.ship.ShipEngineeringCatalog.HardpointDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.content.ship.ShipEngineeringCatalog.SlotDefinition;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipEngineeringState.ValidationCode;
import com.spacesim.ship.ShipEngineeringState.ValidationIssue;
import com.spacesim.ship.ShipEngineeringState.ValidationResult;
import com.spacesim.ship.ShipEngineeringState.ValidationSeverity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Single deterministic Stage-17.5B fitting validator for authored hull/module definitions and one
 * physical runtime load state.
 *
 * <p>The validator never repairs a fit and never uses doctrine/class-name bonuses. A budget that is
 * not yet closed by schema v1 is surfaced as an explicit warning rather than being silently filled
 * by a guessed capacity or multiplier.</p>
 */
public final class ShipFittingValidator {
    private static final String THRUST_N = "thrust_n";
    private static final String EXHAUST_VELOCITY_MPS = "exhaust_velocity_mps";
    private static final String RECOIL_IMPULSE_NS = "recoil_impulse_ns";

    private final ShipEngineeringCatalog catalog;

    /**
     * Creates a validator over one immutable production engineering catalog.
     *
     * @param catalog engineering content catalog
     */
    public ShipFittingValidator(ShipEngineeringCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    /**
     * Validates one fit and its currently carried physical state.
     *
     * @param hull authoritative hull definition
     * @param fit installed module assignments
     * @param consumables physical carried/load state
     * @param damage damage input seam; 17.5B supports pristine capability only
     * @return deterministic immutable diagnostics
     */
    public ValidationResult validate(
            HullDefinition hull,
            InstalledFit fit,
            ConsumableState consumables,
            DamageState damage) {
        HullDefinition checkedHull = Objects.requireNonNull(hull, "hull");
        InstalledFit checkedFit = Objects.requireNonNull(fit, "fit");
        ConsumableState checkedConsumables = Objects.requireNonNull(consumables, "consumables");
        DamageState checkedDamage = Objects.requireNonNull(damage, "damage");

        List<ValidationIssue> issues = new ArrayList<>();
        if (!checkedHull.id().equals(checkedFit.hullId())) {
            error(issues, ValidationCode.HULL_ID_MISMATCH, checkedFit.hullId(),
                    "expected=" + checkedHull.id());
        }

        Map<String, SlotDefinition> slots = indexSlots(checkedHull);
        Map<String, HardpointDefinition> hardpoints = indexHardpoints(checkedHull);
        Map<String, ModuleDefinition> installedByMount = new LinkedHashMap<>();
        Set<String> usedMounts = new HashSet<>();
        boolean hasMountStrengthDemand = false;

        double moduleMassKg = 0d;
        double moduleVolumeM3 = 0d;
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

        for (InstalledModuleDefinition assignment : checkedFit.installedModules()) {
            String mountId = assignment.mountId();
            if (!usedMounts.add(mountId)) {
                error(issues, ValidationCode.DUPLICATE_MOUNT, mountId, assignment.moduleId());
            }

            ModuleDefinition module = catalog.findModule(assignment.moduleId());
            if (module == null) {
                error(issues, ValidationCode.UNKNOWN_MODULE, mountId, assignment.moduleId());
                continue;
            }
            installedByMount.putIfAbsent(mountId, module);
            moduleMassKg += module.massKg();
            moduleVolumeM3 += module.occupiedVolumeM3();
            powerSupplyW += module.continuousPowerSupplyW();
            powerDemandW += module.continuousPowerDemandW();
            peakPowerDemandW += module.peakPowerDemandW();
            storedEnergyJ += module.storedEnergyCapacityJ();
            wasteHeatW += module.wasteHeatW();
            heatRejectionW += module.heatRejectionW();
            localThermalCapacityJ += module.localThermalCapacityJ();
            coolantTransferDemandW += module.coolantTransferDemandW();
            moduleCrewRequired = addIntExact(moduleCrewRequired, module.crewRequirement(), "crewRequirement");
            automationRequired = addIntExact(
                    automationRequired, module.automationRequirement(), "automationRequirement");
            hasMountStrengthDemand |= module.requiredMountStrengthN() > 0d;

            SlotDefinition slot = slots.get(mountId);
            HardpointDefinition hardpoint = hardpoints.get(mountId);
            if (slot == null && hardpoint == null) {
                error(issues, ValidationCode.UNKNOWN_MOUNT, mountId, module.id());
                continue;
            }
            if (slot != null) {
                validateSlot(issues, slot, module);
            } else {
                validateHardpoint(issues, hardpoint, module);
            }
            validatePropulsion(issues, checkedHull, mountId, module);
        }

        validateConsumables(issues, installedByMount, checkedConsumables);

        double totalMassKg = checkedHull.bareHullMassKg() + moduleMassKg
                + checkedConsumables.totalCarriedMassKg();
        if (totalMassKg > checkedHull.maxOperationalMassKg()) {
            error(issues, ValidationCode.OPERATIONAL_MASS_EXCEEDED, checkedHull.id(),
                    "massKg=" + totalMassKg + ",limitKg=" + checkedHull.maxOperationalMassKg());
        }

        double usedVolumeM3 = moduleVolumeM3 + checkedConsumables.missionIntegrationVolumeM3();
        if (usedVolumeM3 > checkedHull.internalVolumeM3()) {
            error(issues, ValidationCode.INTERNAL_VOLUME_EXCEEDED, checkedHull.id(),
                    "usedM3=" + usedVolumeM3 + ",limitM3=" + checkedHull.internalVolumeM3());
        }

        if (powerDemandW > powerSupplyW) {
            error(issues, ValidationCode.CONTINUOUS_POWER_DEFICIT, checkedHull.id(),
                    "supplyW=" + powerSupplyW + ",demandW=" + powerDemandW);
        }
        if (peakPowerDemandW > powerSupplyW) {
            if (storedEnergyJ > 0d) {
                warning(issues, ValidationCode.PEAK_POWER_STORAGE_LIMITED, checkedHull.id(),
                        "supplyW=" + powerSupplyW + ",peakW=" + peakPowerDemandW
                                + ",storedJ=" + storedEnergyJ);
            } else {
                error(issues, ValidationCode.PEAK_POWER_DEFICIT, checkedHull.id(),
                        "supplyW=" + powerSupplyW + ",peakW=" + peakPowerDemandW);
            }
        }

        if (wasteHeatW > heatRejectionW) {
            double deficitW = wasteHeatW - heatRejectionW;
            if (localThermalCapacityJ > 0d) {
                warning(issues, ValidationCode.THERMAL_ENDURANCE_LIMITED, checkedHull.id(),
                        "deficitW=" + deficitW + ",bufferJ=" + localThermalCapacityJ
                                + ",idealizedBufferSeconds=" + (localThermalCapacityJ / deficitW));
            } else {
                error(issues, ValidationCode.CONTINUOUS_HEAT_DEFICIT, checkedHull.id(),
                        "wasteHeatW=" + wasteHeatW + ",rejectionW=" + heatRejectionW);
            }
        }

        int crewRequired = Math.max(checkedHull.crewBaseline(), moduleCrewRequired);
        if (crewRequired > checkedHull.lifeSupportCapacity()) {
            error(issues, ValidationCode.CREW_CAPACITY_EXCEEDED, checkedHull.id(),
                    "required=" + crewRequired + ",supported=" + checkedHull.lifeSupportCapacity());
        }

        if (!checkedDamage.isPristine()) {
            error(issues, ValidationCode.DAMAGE_MODEL_NOT_ACTIVE, checkedHull.id(),
                    "non-pristine capability degradation belongs to Stage 17.5F");
        }
        for (String mountId : checkedDamage.moduleIntegrityByMount().keySet()) {
            if (!slots.containsKey(mountId) && !hardpoints.containsKey(mountId)) {
                error(issues, ValidationCode.UNKNOWN_MOUNT, mountId, "damage-state mount");
            }
        }

        if (hasMountStrengthDemand) {
            warning(issues, ValidationCode.MOUNT_STRENGTH_CAPACITY_UNMODELED, checkedHull.id(),
                    "schema v1 carries module requiredMountStrengthN but no symmetric mount force capacity");
        }
        if (automationRequired > 0) {
            warning(issues, ValidationCode.AUTOMATION_CAPACITY_UNMODELED, checkedHull.id(),
                    "requiredAutomation=" + automationRequired);
        }
        if (coolantTransferDemandW > 0d) {
            warning(issues, ValidationCode.COOLANT_TRANSFER_CAPACITY_UNMODELED, checkedHull.id(),
                    "coolantTransferDemandW=" + coolantTransferDemandW);
        }

        return new ValidationResult(issues);
    }

    private static void validateSlot(
            List<ValidationIssue> issues, SlotDefinition slot, ModuleDefinition module) {
        if (!module.integrationCategories().contains(slot.category())) {
            error(issues, ValidationCode.SLOT_CATEGORY_INCOMPATIBLE, slot.id(),
                    "module=" + module.id() + ",category=" + slot.category());
        }
        if (module.massKg() > slot.maxMassKg()
                || !fits(module.physicalDimensionsM(), slot.maxDimensionsM())) {
            error(issues, ValidationCode.SLOT_ENVELOPE_EXCEEDED, slot.id(), module.id());
        }
    }

    private static void validateHardpoint(
            List<ValidationIssue> issues, HardpointDefinition hardpoint, ModuleDefinition module) {
        if (!hardpoint.allowedModuleFamilies().contains(module.family())) {
            error(issues, ValidationCode.HARDPOINT_FAMILY_INCOMPATIBLE, hardpoint.id(), module.id());
        }
        if (!module.compatibleHardpointSizes().contains(hardpoint.size())) {
            error(issues, ValidationCode.HARDPOINT_SIZE_INCOMPATIBLE, hardpoint.id(), module.id());
        }
        if (module.massKg() > hardpoint.maxModuleMassKg()
                || !fits(module.physicalDimensionsM(), hardpoint.maxModuleDimensionsM())) {
            error(issues, ValidationCode.HARDPOINT_ENVELOPE_EXCEEDED, hardpoint.id(), module.id());
        }
        Double recoil = module.capabilityParameters().get(RECOIL_IMPULSE_NS);
        if (recoil != null && recoil > hardpoint.maxRecoilImpulseNs()) {
            error(issues, ValidationCode.HARDPOINT_RECOIL_EXCEEDED, hardpoint.id(),
                    "recoilNs=" + recoil + ",limitNs=" + hardpoint.maxRecoilImpulseNs());
        }
    }

    private static void validatePropulsion(
            List<ValidationIssue> issues,
            HullDefinition hull,
            String mountId,
            ModuleDefinition module) {
        if (module.family() != ModuleFamily.MAIN_DRIVE
                && module.family() != ModuleFamily.MANEUVER_THRUSTERS) {
            return;
        }
        if (!hull.thrustMountCompatibility().contains(module.family())) {
            error(issues, ValidationCode.THRUST_MOUNT_INCOMPATIBLE, mountId, module.family().name());
        }
        Double thrust = module.capabilityParameters().get(THRUST_N);
        Double exhaustVelocity = module.capabilityParameters().get(EXHAUST_VELOCITY_MPS);
        if (thrust == null || thrust <= 0d || exhaustVelocity == null || exhaustVelocity <= 0d) {
            error(issues, ValidationCode.PROPULSION_PARAMETERS_MISSING, mountId, module.id());
        }
    }

    private static void validateConsumables(
            List<ValidationIssue> issues,
            Map<String, ModuleDefinition> installedByMount,
            ConsumableState consumables) {
        Map<String, Double> amountByInterface = new HashMap<>();
        Map<String, InterfaceDefinition> interfaceByKey = new HashMap<>();

        for (ConsumableLoad load : consumables.interfaceLoads()) {
            ModuleDefinition module = installedByMount.get(load.mountId());
            if (module == null) {
                error(issues, ValidationCode.UNKNOWN_CONSUMABLE_INTERFACE, load.mountId(), load.interfaceId());
                continue;
            }
            InterfaceDefinition target = null;
            for (InterfaceDefinition definition : module.interfaces()) {
                if (definition.id().equals(load.interfaceId())) {
                    target = definition;
                    break;
                }
            }
            if (target == null) {
                error(issues, ValidationCode.UNKNOWN_CONSUMABLE_INTERFACE, load.mountId(), load.interfaceId());
                continue;
            }
            String key = load.mountId() + "\u0000" + load.interfaceId();
            interfaceByKey.put(key, target);
            if (target.kind() != load.kind()) {
                error(issues, ValidationCode.CONSUMABLE_KIND_MISMATCH, load.mountId() + "." + load.interfaceId(),
                        "expected=" + target.kind() + ",actual=" + load.kind());
            }
            amountByInterface.merge(key, load.amount(), Double::sum);
        }

        for (Map.Entry<String, Double> entry : amountByInterface.entrySet()) {
            InterfaceDefinition target = interfaceByKey.get(entry.getKey());
            if (target != null && entry.getValue() > target.capacity()) {
                String subject = entry.getKey().replace('\u0000', '.');
                error(issues, ValidationCode.CONSUMABLE_CAPACITY_EXCEEDED, subject,
                        "amount=" + entry.getValue() + ",capacity=" + target.capacity());
            }
        }
    }

    private static Map<String, SlotDefinition> indexSlots(HullDefinition hull) {
        Map<String, SlotDefinition> result = new HashMap<>();
        for (SlotDefinition slot : hull.slots()) {
            result.put(slot.id(), slot);
        }
        return result;
    }

    private static Map<String, HardpointDefinition> indexHardpoints(HullDefinition hull) {
        Map<String, HardpointDefinition> result = new HashMap<>();
        for (HardpointDefinition hardpoint : hull.hardpoints()) {
            result.put(hardpoint.id(), hardpoint);
        }
        return result;
    }

    private static boolean fits(Dimensions3d actual, Dimensions3d limit) {
        return actual.lengthM() <= limit.lengthM()
                && actual.widthM() <= limit.widthM()
                && actual.heightM() <= limit.heightM();
    }

    private static int addIntExact(int current, int value, String field) {
        try {
            return Math.addExact(current, value);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(field + " total exceeds int range", exception);
        }
    }

    private static void error(
            List<ValidationIssue> issues, ValidationCode code, String subject, String detail) {
        issues.add(new ValidationIssue(ValidationSeverity.ERROR, code, subject, detail));
    }

    private static void warning(
            List<ValidationIssue> issues, ValidationCode code, String subject, String detail) {
        issues.add(new ValidationIssue(ValidationSeverity.WARNING, code, subject, detail));
    }
}
