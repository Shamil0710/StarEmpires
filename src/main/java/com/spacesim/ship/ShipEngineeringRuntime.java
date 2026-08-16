package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Stage-17.5C deterministic propulsion, reaction-mass, power, thermal and FTL runtime core.
 *
 * <p>This class consumes the same Stage-17.5A catalog and Stage-17.5B fit/load state used by the
 * central derived calculator. It does not inspect doctrine class, player ownership or AI ownership.
 * Shared-bus electrical storage is provided only by {@link ModuleFamily#ENERGY_STORAGE}; local
 * energy buffers on sensors or weapons remain local and cannot silently power propulsion or FTL.</p>
 *
 * <p>The runtime keeps physical reaction mass in {@link ConsumableState}, accounts for drive mass
 * flow, models a bounded shared power bus with deterministic load shedding, transports module heat
 * through an explicit coolant-bus capacity into a ship thermal store/radiators, and exposes fitted
 * FTL requirements as a plan that later world integration can bind to the existing jump FSM.</p>
 */
public final class ShipEngineeringRuntime {
    /** Capability key for drive thrust. */
    public static final String THRUST_N = "thrust_n";
    /** Capability key for drive exhaust velocity. */
    public static final String EXHAUST_VELOCITY_MPS = "exhaust_velocity_mps";
    /** Capability key for explicit drive jet power. */
    public static final String JET_POWER_W = "jet_power_w";
    /** Thermal-control capability key for ship coolant-bus transfer capacity. */
    public static final String COOLANT_BUS_CAPACITY_W = "coolant_bus_capacity_w";
    /** Thermal-control capability key for ship-level thermal-store capacity. */
    public static final String SHIP_THERMAL_STORE_CAPACITY_J = "ship_thermal_store_capacity_j";
    /** Energy-storage capability key for maximum shared-bus charge power. */
    public static final String MAX_CHARGE_POWER_W = "max_charge_power_w";
    /** Energy-storage capability key for maximum shared-bus discharge power. */
    public static final String MAX_DISCHARGE_POWER_W = "max_discharge_power_w";
    /** FTL capability key for translated-mass limit. */
    public static final String FTL_TRANSLATED_MASS_MAX_KG = "translated_mass_max_kg";
    /** FTL capability key for one jump charge energy. */
    public static final String FTL_JUMP_ENERGY_J = "jump_energy_j";
    /** FTL capability key for charge power. */
    public static final String FTL_CHARGE_POWER_W = "charge_power_w";
    /** FTL capability key for spool time. */
    public static final String FTL_SPOOL_TIME_S = "spool_time_s";
    /** FTL capability key for current fixture edge-transit time. */
    public static final String FTL_EDGE_TRANSIT_TIME_S = "edge_transit_time_s";
    /** FTL capability key for post-jump cooldown. */
    public static final String FTL_COOLDOWN_S = "cooldown_s";
    /** FTL capability key for local heat deposited by one jump. */
    public static final String FTL_JUMP_HEAT_J = "jump_heat_j";

    private static final double EPSILON = 1e-9;

    private final ShipEngineeringCatalog catalog;
    private final DerivedShipCalculator calculator;

    /**
     * Creates one runtime over an immutable production engineering catalog.
     *
     * @param catalog production engineering catalog
     */
    public ShipEngineeringRuntime(ShipEngineeringCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.calculator = new DerivedShipCalculator(catalog);
    }

    /** Power-bus condition after one authoritative operating step. */
    public enum PowerStatus {
        /** Continuous generation supplied the surviving demand without storage discharge. */ NOMINAL,
        /** Shared ENERGY_STORAGE discharged to cover demand above continuous generation. */ STORAGE_ASSISTED,
        /** One or more explicitly prioritized loads were shed to close the available power budget. */ LOAD_SHEDDING,
        /** Even after deterministic shedding the remaining demand could not be supplied. */ BROWNOUT
    }

    /** Thermal condition after one authoritative operating step. */
    public enum ThermalStatus {
        /** No local or ship-level thermal limit is exceeded and heat is not accumulating. */ NOMINAL,
        /** Heat is accumulating but remains inside authored local/store capacities. */ HEAT_ACCUMULATING,
        /** At least one local module or the ship thermal store is beyond its authored capacity. */ SATURATED,
        /** At least one module was unavailable at tick start because its local thermal state was saturated. */ THERMALLY_LIMITED
    }

    /** Stable reason why a fitted FTL plan cannot currently execute. */
    public enum JumpFailure {
        /** Plan is executable. */ NONE,
        /** No fitted FTL module exists. */ NO_FTL_MODULE,
        /** FTL capability payload is missing or physically inconsistent. */ INVALID_CAPABILITY,
        /** Current translated mass exceeds the fitted envelope. */ TRANSLATED_MASS_EXCEEDED,
        /** FTL module is still cooling down. */ COOLDOWN_ACTIVE,
        /** Shared bus cannot provide the required FTL charge power. */ CHARGE_POWER_UNAVAILABLE,
        /** Shared ENERGY_STORAGE does not contain the required jump energy. */ STORED_ENERGY_UNAVAILABLE,
        /** Jump heat would exceed the fitted module-local thermal capacity. */ THERMAL_LIMIT
    }

    /**
     * Persistent-ready operating state for one fitted ship instance.
     *
     * @param consumables current physical cargo/stores/ammunition/reaction-mass state
     * @param sharedBusEnergyJ current energy in modules of family ENERGY_STORAGE only
     * @param shipHeatStoredJ current heat stored on the ship heat bus after radiator rejection
     * @param localHeatJByMount current module-local thermal energy by mount
     * @param thrustLimitNByMount current physical thrust ceiling by propulsion mount
     * @param coolantBusCapacityW current total ship coolant-transfer capacity; damage may reduce it
     * @param ftlCooldownSecondsByMount remaining FTL cooldown by mount
     */
    public record RuntimeState(
            ConsumableState consumables,
            double sharedBusEnergyJ,
            double shipHeatStoredJ,
            Map<String, Double> localHeatJByMount,
            Map<String, Double> thrustLimitNByMount,
            double coolantBusCapacityW,
            Map<String, Double> ftlCooldownSecondsByMount) {
        /**
         * Creates one immutable deterministic operating state.
         *
         * @param consumables current physical cargo/stores/ammunition/reaction-mass state
         * @param sharedBusEnergyJ current shared-bus electrical energy
         * @param shipHeatStoredJ current ship-bus heat energy
         * @param localHeatJByMount module-local heat by mount
         * @param thrustLimitNByMount physical thrust ceilings by drive mount
         * @param coolantBusCapacityW current coolant-bus transfer capacity
         * @param ftlCooldownSecondsByMount remaining FTL cooldown by mount
         */
        public RuntimeState {
            Objects.requireNonNull(consumables, "consumables");
            requireNonNegativeFinite(sharedBusEnergyJ, "sharedBusEnergyJ");
            requireNonNegativeFinite(shipHeatStoredJ, "shipHeatStoredJ");
            localHeatJByMount = immutableNonNegativeMap(localHeatJByMount, "localHeatJByMount");
            thrustLimitNByMount = immutableNonNegativeMap(thrustLimitNByMount, "thrustLimitNByMount");
            requireNonNegativeFinite(coolantBusCapacityW, "coolantBusCapacityW");
            ftlCooldownSecondsByMount = immutableNonNegativeMap(
                    ftlCooldownSecondsByMount, "ftlCooldownSecondsByMount");
        }
    }

    /**
     * Shared operating command used equally by player and AI callers.
     *
     * <p>Power priority is operational policy, not a performance bonus. Larger numeric values are
     * shed first when physical generation/storage cannot close the requested demand.</p>
     *
     * @param throttleByMount commanded propulsion throttle in [0,1]
     * @param powerPriorityByMount deterministic load-shed priority; larger values shed first
     * @param disabledMounts mounts explicitly commanded off
     */
    public record OperatingCommand(
            Map<String, Double> throttleByMount,
            Map<String, Integer> powerPriorityByMount,
            Set<String> disabledMounts) {
        /**
         * Creates one immutable deterministic operating command.
         *
         * @param throttleByMount commanded propulsion throttle in [0,1]
         * @param powerPriorityByMount load-shed priorities
         * @param disabledMounts explicitly disabled mounts
         */
        public OperatingCommand {
            Objects.requireNonNull(throttleByMount, "throttleByMount");
            TreeMap<String, Double> throttles = new TreeMap<>();
            for (Map.Entry<String, Double> entry : throttleByMount.entrySet()) {
                requireNonBlank(entry.getKey(), "throttle mount");
                Double value = Objects.requireNonNull(entry.getValue(), "throttle value");
                if (!Double.isFinite(value) || value < 0d || value > 1d) {
                    throw new IllegalArgumentException("throttle must be in [0,1]");
                }
                throttles.put(entry.getKey(), value);
            }
            throttleByMount = Collections.unmodifiableMap(throttles);

            Objects.requireNonNull(powerPriorityByMount, "powerPriorityByMount");
            TreeMap<String, Integer> priorities = new TreeMap<>();
            for (Map.Entry<String, Integer> entry : powerPriorityByMount.entrySet()) {
                requireNonBlank(entry.getKey(), "priority mount");
                Integer value = Objects.requireNonNull(entry.getValue(), "priority value");
                if (value < 0) {
                    throw new IllegalArgumentException("power priority must be non-negative");
                }
                priorities.put(entry.getKey(), value);
            }
            powerPriorityByMount = Collections.unmodifiableMap(priorities);

            Objects.requireNonNull(disabledMounts, "disabledMounts");
            TreeSet<String> disabled = new TreeSet<>();
            for (String mount : disabledMounts) {
                requireNonBlank(mount, "disabled mount");
                disabled.add(mount);
            }
            disabledMounts = Collections.unmodifiableSet(disabled);
        }

        /** @return command with all ordinary systems enabled and propulsion at zero throttle */
        public static OperatingCommand idle() {
            return new OperatingCommand(Map.of(), Map.of(), Set.of());
        }
    }

    /**
     * Result of one deterministic operating step.
     *
     * @param state new authoritative operating state
     * @param derivedState central derived state recomputed after physical consumable depletion
     * @param actualThrustN actual surviving propulsion thrust during the step
     * @param massFlowKgPerS actual reaction-mass flow during the step
     * @param powerSupplyW surviving continuous supply
     * @param powerDemandW surviving electrical demand
     * @param storageDischargeW shared storage discharge used during the step
     * @param powerStatus resulting power condition
     * @param generatedHeatW total module heat generated during the step
     * @param coolantTransferW heat transferred from module-local loops into the ship bus
     * @param radiatorRejectionW heat rejected from the ship bus to space
     * @param thermalStatus resulting thermal condition
     * @param shedMounts deterministic list of power-shed mounts
     */
    public record TickResult(
            RuntimeState state,
            DerivedShipState derivedState,
            double actualThrustN,
            double massFlowKgPerS,
            double powerSupplyW,
            double powerDemandW,
            double storageDischargeW,
            PowerStatus powerStatus,
            double generatedHeatW,
            double coolantTransferW,
            double radiatorRejectionW,
            ThermalStatus thermalStatus,
            List<String> shedMounts) {
        /**
         * Creates one immutable tick result.
         *
         * @param state new operating state
         * @param derivedState recomputed central derived state
         * @param actualThrustN actual thrust
         * @param massFlowKgPerS actual mass flow
         * @param powerSupplyW surviving supply
         * @param powerDemandW surviving demand
         * @param storageDischargeW shared-storage discharge
         * @param powerStatus power condition
         * @param generatedHeatW generated heat
         * @param coolantTransferW transferred heat
         * @param radiatorRejectionW rejected heat
         * @param thermalStatus thermal condition
         * @param shedMounts shed mounts
         */
        public TickResult {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(derivedState, "derivedState");
            requireNonNegativeFinite(actualThrustN, "actualThrustN");
            requireNonNegativeFinite(massFlowKgPerS, "massFlowKgPerS");
            requireNonNegativeFinite(powerSupplyW, "powerSupplyW");
            requireNonNegativeFinite(powerDemandW, "powerDemandW");
            requireNonNegativeFinite(storageDischargeW, "storageDischargeW");
            Objects.requireNonNull(powerStatus, "powerStatus");
            requireNonNegativeFinite(generatedHeatW, "generatedHeatW");
            requireNonNegativeFinite(coolantTransferW, "coolantTransferW");
            requireNonNegativeFinite(radiatorRejectionW, "radiatorRejectionW");
            Objects.requireNonNull(thermalStatus, "thermalStatus");
            Objects.requireNonNull(shedMounts, "shedMounts");
            shedMounts = List.copyOf(shedMounts);
        }
    }

    /**
     * Fitted FTL execution plan for one ordinary topology edge.
     *
     * @param allowed whether the current physical state can execute the plan
     * @param failure stable rejection reason, or NONE
     * @param mountId selected fitted FTL mount, empty when unavailable
     * @param translatedMassKg current ship mass translated by the jump
     * @param requiredEnergyJ energy consumed from shared ENERGY_STORAGE when committed
     * @param chargePowerW required charge power
     * @param spoolSeconds fitted spool duration
     * @param edgeTransitSeconds current fixture edge-transit duration
     * @param cooldownSeconds fitted post-jump cooldown
     * @param jumpHeatJ heat deposited into the FTL module-local thermal state
     */
    public record JumpPlan(
            boolean allowed,
            JumpFailure failure,
            String mountId,
            double translatedMassKg,
            double requiredEnergyJ,
            double chargePowerW,
            double spoolSeconds,
            double edgeTransitSeconds,
            double cooldownSeconds,
            double jumpHeatJ) {
        /**
         * Creates one immutable FTL plan.
         *
         * @param allowed whether execution is allowed
         * @param failure rejection reason
         * @param mountId selected FTL mount
         * @param translatedMassKg translated mass
         * @param requiredEnergyJ required stored energy
         * @param chargePowerW charge power
         * @param spoolSeconds spool duration
         * @param edgeTransitSeconds edge-transit duration
         * @param cooldownSeconds cooldown duration
         * @param jumpHeatJ local jump heat
         */
        public JumpPlan {
            Objects.requireNonNull(failure, "failure");
            mountId = mountId == null ? "" : mountId;
            requireNonNegativeFinite(translatedMassKg, "translatedMassKg");
            requireNonNegativeFinite(requiredEnergyJ, "requiredEnergyJ");
            requireNonNegativeFinite(chargePowerW, "chargePowerW");
            requireNonNegativeFinite(spoolSeconds, "spoolSeconds");
            requireNonNegativeFinite(edgeTransitSeconds, "edgeTransitSeconds");
            requireNonNegativeFinite(cooldownSeconds, "cooldownSeconds");
            requireNonNegativeFinite(jumpHeatJ, "jumpHeatJ");
            if (allowed != (failure == JumpFailure.NONE)) {
                throw new IllegalArgumentException("allowed and failure must agree");
            }
        }
    }

    /**
     * Builds a fully charged healthy runtime state from a valid fit and physical load state.
     *
     * <p>Only ENERGY_STORAGE capacity is placed on the shared electrical bus. Local buffers from
     * other module families remain unavailable to the bus by construction.</p>
     *
     * @param fit installed production fit
     * @param consumables initial physical load state
     * @return initialized runtime state
     */
    public RuntimeState initialize(InstalledFit fit, ConsumableState consumables) {
        InstalledFit checkedFit = Objects.requireNonNull(fit, "fit");
        ConsumableState checkedLoads = Objects.requireNonNull(consumables, "consumables");
        derive(checkedFit, checkedLoads);

        double busEnergy = 0d;
        double coolantBus = 0d;
        Map<String, Double> localHeat = new TreeMap<>();
        Map<String, Double> thrustLimits = new TreeMap<>();
        Map<String, Double> cooldowns = new TreeMap<>();
        for (InstalledModuleDefinition assignment : checkedFit.installedModules()) {
            ModuleDefinition module = requireModule(assignment.moduleId());
            localHeat.put(assignment.mountId(), 0d);
            if (module.family() == ModuleFamily.ENERGY_STORAGE) {
                busEnergy += module.storedEnergyCapacityJ();
            }
            if (module.family() == ModuleFamily.THERMAL_CONTROL) {
                coolantBus += optionalParameter(module, COOLANT_BUS_CAPACITY_W, 0d);
            }
            if (isDrive(module)) {
                validateDriveCapability(module);
                thrustLimits.put(assignment.mountId(), requiredPositiveParameter(module, THRUST_N));
            }
            if (module.family() == ModuleFamily.FTL_JUMP) {
                cooldowns.put(assignment.mountId(), 0d);
            }
        }
        return new RuntimeState(
                checkedLoads, busEnergy, 0d, localHeat, thrustLimits, coolantBus, cooldowns);
    }

    /**
     * Advances propulsion, reaction mass, power storage/shedding, heat transport and cooldowns.
     *
     * @param fit installed production fit
     * @param state current authoritative operating state
     * @param command shared player/AI operating command
     * @param deltaSeconds positive simulation duration
     * @return deterministic physical result and next state
     */
    public TickResult advance(
            InstalledFit fit,
            RuntimeState state,
            OperatingCommand command,
            double deltaSeconds) {
        InstalledFit checkedFit = Objects.requireNonNull(fit, "fit");
        RuntimeState checkedState = Objects.requireNonNull(state, "state");
        OperatingCommand checkedCommand = Objects.requireNonNull(command, "command");
        requirePositiveFinite(deltaSeconds, "deltaSeconds");
        derive(checkedFit, checkedState.consumables());

        List<Use> uses = new ArrayList<>();
        boolean thermallyLimitedAtStart = false;
        for (InstalledModuleDefinition assignment : checkedFit.installedModules()) {
            ModuleDefinition module = requireModule(assignment.moduleId());
            double localHeat = checkedState.localHeatJByMount().getOrDefault(assignment.mountId(), 0d);
            boolean overheated = module.localThermalCapacityJ() > 0d
                    && localHeat >= module.localThermalCapacityJ() - EPSILON;
            thermallyLimitedAtStart |= overheated;
            boolean enabled = !checkedCommand.disabledMounts().contains(assignment.mountId()) && !overheated;
            double fraction = enabled ? operatingFraction(
                    assignment.mountId(), module, checkedState, checkedCommand, deltaSeconds) : 0d;
            uses.add(new Use(assignment.mountId(), module, enabled, fraction));
        }
        uses.sort(Comparator.comparing(use -> use.mountId));

        PowerTotals totals = totals(uses);
        double busCapacityJ = sharedBusEnergyCapacityJ(checkedFit);
        double maxDischargeW = sharedBusDischargePowerW(checkedFit);
        double maxChargeW = sharedBusChargePowerW(checkedFit);
        double availableDischargeW = Math.min(
                maxDischargeW,
                checkedState.sharedBusEnergyJ() / deltaSeconds);

        List<String> shedMounts = new ArrayList<>();
        if (totals.demandW > totals.supplyW + availableDischargeW + EPSILON) {
            List<Use> shedCandidates = uses.stream()
                    .filter(use -> use.enabled && use.demandW() > 0d && use.supplyW() <= 0d)
                    .sorted(Comparator
                            .comparingInt((Use use) -> checkedCommand.powerPriorityByMount()
                                    .getOrDefault(use.mountId, 100))
                            .reversed()
                            .thenComparing(use -> use.mountId))
                    .toList();
            for (Use use : shedCandidates) {
                use.enabled = false;
                use.fraction = 0d;
                shedMounts.add(use.mountId);
                totals = totals(uses);
                availableDischargeW = Math.min(
                        maxDischargeW,
                        checkedState.sharedBusEnergyJ() / deltaSeconds);
                if (totals.demandW <= totals.supplyW + availableDischargeW + EPSILON) {
                    break;
                }
            }
        }

        totals = totals(uses);
        double requiredDischargeW = Math.max(0d, totals.demandW - totals.supplyW);
        double storageDischargeW = Math.min(requiredDischargeW, availableDischargeW);
        boolean unresolvedBrownout = requiredDischargeW > storageDischargeW + EPSILON;
        double busEnergyJ = checkedState.sharedBusEnergyJ() - storageDischargeW * deltaSeconds;
        busEnergyJ = Math.max(0d, busEnergyJ);
        if (!unresolvedBrownout && totals.supplyW > totals.demandW) {
            double chargeW = Math.min(totals.supplyW - totals.demandW, maxChargeW);
            chargeW = Math.min(chargeW, Math.max(0d, busCapacityJ - busEnergyJ) / deltaSeconds);
            busEnergyJ += chargeW * deltaSeconds;
        }
        busEnergyJ = Math.min(busCapacityJ, busEnergyJ);

        PowerStatus powerStatus;
        if (unresolvedBrownout) {
            powerStatus = PowerStatus.BROWNOUT;
        } else if (!shedMounts.isEmpty()) {
            powerStatus = PowerStatus.LOAD_SHEDDING;
        } else if (storageDischargeW > EPSILON) {
            powerStatus = PowerStatus.STORAGE_ASSISTED;
        } else {
            powerStatus = PowerStatus.NOMINAL;
        }

        ConsumableState consumables = checkedState.consumables();
        double actualThrustN = 0d;
        double actualMassFlowKgPerS = 0d;
        for (Use use : uses) {
            if (!use.enabled || !isDrive(use.module)) {
                continue;
            }
            double thrustN = use.thrustN();
            double exhaustVelocity = requiredPositiveParameter(use.module, EXHAUST_VELOCITY_MPS);
            double massFlow = thrustN / exhaustVelocity;
            double consumedKg = massFlow * deltaSeconds;
            if (consumedKg > EPSILON) {
                consumables = consumeReactionMass(consumables, use.mountId, consumedKg);
            }
            actualThrustN += thrustN;
            actualMassFlowKgPerS += massFlow;
        }

        Map<String, Double> localHeat = new TreeMap<>();
        double remainingCoolantW = checkedState.coolantBusCapacityW();
        double coolantTransferW = 0d;
        double generatedHeatW = 0d;
        for (Use use : uses) {
            double previousLocalJ = checkedState.localHeatJByMount().getOrDefault(use.mountId, 0d);
            double generatedW = use.heatW();
            generatedHeatW += generatedW;
            double transportableW = generatedW + previousLocalJ / deltaSeconds;
            double transferW = Math.min(
                    Math.min(use.module.coolantTransferDemandW(), transportableW),
                    remainingCoolantW);
            transferW = Math.max(0d, transferW);
            remainingCoolantW -= transferW;
            coolantTransferW += transferW;
            double nextLocalJ = Math.max(0d, previousLocalJ + (generatedW - transferW) * deltaSeconds);
            localHeat.put(use.mountId, nextLocalJ);
        }

        double shipHeatBeforeRejectJ = checkedState.shipHeatStoredJ() + coolantTransferW * deltaSeconds;
        double radiatorRejectionW = totals(uses).heatRejectionW;
        double rejectedJ = Math.min(shipHeatBeforeRejectJ, radiatorRejectionW * deltaSeconds);
        double shipHeatJ = Math.max(0d, shipHeatBeforeRejectJ - rejectedJ);
        double shipHeatCapacityJ = shipThermalStoreCapacityJ(checkedFit);

        boolean saturated = shipHeatCapacityJ > 0d && shipHeatJ > shipHeatCapacityJ + EPSILON;
        double oldLocalTotal = checkedState.localHeatJByMount().values().stream().mapToDouble(Double::doubleValue).sum();
        double newLocalTotal = localHeat.values().stream().mapToDouble(Double::doubleValue).sum();
        for (Use use : uses) {
            double capacityJ = use.module.localThermalCapacityJ();
            if (capacityJ > 0d && localHeat.getOrDefault(use.mountId, 0d) > capacityJ + EPSILON) {
                saturated = true;
            }
        }
        ThermalStatus thermalStatus;
        if (thermallyLimitedAtStart) {
            thermalStatus = ThermalStatus.THERMALLY_LIMITED;
        } else if (saturated) {
            thermalStatus = ThermalStatus.SATURATED;
        } else if (shipHeatJ > checkedState.shipHeatStoredJ() + EPSILON
                || newLocalTotal > oldLocalTotal + EPSILON) {
            thermalStatus = ThermalStatus.HEAT_ACCUMULATING;
        } else {
            thermalStatus = ThermalStatus.NOMINAL;
        }

        Map<String, Double> cooldowns = new TreeMap<>();
        for (Map.Entry<String, Double> entry : checkedState.ftlCooldownSecondsByMount().entrySet()) {
            cooldowns.put(entry.getKey(), Math.max(0d, entry.getValue() - deltaSeconds));
        }

        RuntimeState next = new RuntimeState(
                consumables,
                busEnergyJ,
                shipHeatJ,
                localHeat,
                checkedState.thrustLimitNByMount(),
                checkedState.coolantBusCapacityW(),
                cooldowns);
        DerivedShipState derived = derive(checkedFit, consumables);
        return new TickResult(
                next,
                derived,
                actualThrustN,
                actualMassFlowKgPerS,
                totals.supplyW,
                totals.demandW,
                storageDischargeW,
                powerStatus,
                generatedHeatW,
                coolantTransferW,
                radiatorRejectionW,
                thermalStatus,
                shedMounts);
    }

    /**
     * Plans FTL use from the fitted capability and current physical state.
     *
     * <p>The returned transit time is still a content fixture. Stage 20 later calibrates real edge
     * geometry/time against the same fitted capability instead of replacing this interface.</p>
     *
     * @param fit installed production fit
     * @param state current operating state
     * @return deterministic FTL plan or stable rejection reason
     */
    public JumpPlan planJump(InstalledFit fit, RuntimeState state) {
        InstalledFit checkedFit = Objects.requireNonNull(fit, "fit");
        RuntimeState checkedState = Objects.requireNonNull(state, "state");
        DerivedShipState derived = derive(checkedFit, checkedState.consumables());
        for (InstalledModuleDefinition assignment : checkedFit.installedModules()) {
            ModuleDefinition module = requireModule(assignment.moduleId());
            if (module.family() != ModuleFamily.FTL_JUMP) {
                continue;
            }
            FtlCapability capability;
            try {
                capability = ftlCapability(module);
            } catch (IllegalArgumentException exception) {
                return rejected(JumpFailure.INVALID_CAPABILITY, assignment.mountId(), derived.totalMassKg());
            }
            if (derived.totalMassKg() > capability.translatedMassMaxKg + EPSILON) {
                return rejected(JumpFailure.TRANSLATED_MASS_EXCEEDED, assignment.mountId(), derived.totalMassKg());
            }
            if (checkedState.ftlCooldownSecondsByMount().getOrDefault(assignment.mountId(), 0d) > EPSILON) {
                return rejected(JumpFailure.COOLDOWN_ACTIVE, assignment.mountId(), derived.totalMassKg());
            }
            double maxChargePower = Math.max(0d, derived.continuousPowerMarginW())
                    + sharedBusDischargePowerW(checkedFit);
            if (capability.chargePowerW > maxChargePower + EPSILON) {
                return rejected(JumpFailure.CHARGE_POWER_UNAVAILABLE, assignment.mountId(), derived.totalMassKg());
            }
            if (checkedState.sharedBusEnergyJ() + EPSILON < capability.jumpEnergyJ) {
                return rejected(JumpFailure.STORED_ENERGY_UNAVAILABLE, assignment.mountId(), derived.totalMassKg());
            }
            double localHeatJ = checkedState.localHeatJByMount().getOrDefault(assignment.mountId(), 0d);
            if (module.localThermalCapacityJ() > 0d
                    && localHeatJ + capability.jumpHeatJ > module.localThermalCapacityJ() + EPSILON) {
                return rejected(JumpFailure.THERMAL_LIMIT, assignment.mountId(), derived.totalMassKg());
            }
            return new JumpPlan(
                    true,
                    JumpFailure.NONE,
                    assignment.mountId(),
                    derived.totalMassKg(),
                    capability.jumpEnergyJ,
                    capability.chargePowerW,
                    capability.spoolTimeS,
                    capability.edgeTransitTimeS,
                    capability.cooldownS,
                    capability.jumpHeatJ);
        }
        return rejected(JumpFailure.NO_FTL_MODULE, "", derived.totalMassKg());
    }

    /**
     * Commits the physical energy/heat/cooldown consequences of an already accepted jump plan.
     *
     * @param state current operating state
     * @param plan previously accepted plan
     * @return next state after FTL charge commitment
     */
    public RuntimeState commitJump(RuntimeState state, JumpPlan plan) {
        RuntimeState checked = Objects.requireNonNull(state, "state");
        JumpPlan checkedPlan = Objects.requireNonNull(plan, "plan");
        if (!checkedPlan.allowed()) {
            throw new IllegalArgumentException("cannot commit rejected jump plan: " + checkedPlan.failure());
        }
        if (checked.sharedBusEnergyJ() + EPSILON < checkedPlan.requiredEnergyJ()) {
            throw new IllegalStateException("shared bus energy changed after jump planning");
        }
        Map<String, Double> localHeat = new TreeMap<>(checked.localHeatJByMount());
        localHeat.merge(checkedPlan.mountId(), checkedPlan.jumpHeatJ(), Double::sum);
        Map<String, Double> cooldowns = new TreeMap<>(checked.ftlCooldownSecondsByMount());
        cooldowns.put(checkedPlan.mountId(), checkedPlan.cooldownSeconds());
        return new RuntimeState(
                checked.consumables(),
                checked.sharedBusEnergyJ() - checkedPlan.requiredEnergyJ(),
                checked.shipHeatStoredJ(),
                localHeat,
                checked.thrustLimitNByMount(),
                checked.coolantBusCapacityW(),
                cooldowns);
    }

    private DerivedShipState derive(InstalledFit fit, ConsumableState consumables) {
        ShipEngineeringCatalog.HullDefinition hull = catalog.findHull(fit.hullId());
        if (hull == null) {
            throw new IllegalArgumentException("unknown hull: " + fit.hullId());
        }
        return calculator.derive(hull, fit, consumables, DamageState.pristine());
    }

    private double operatingFraction(
            String mountId,
            ModuleDefinition module,
            RuntimeState state,
            OperatingCommand command,
            double deltaSeconds) {
        if (!isDrive(module)) {
            return 1d;
        }
        validateDriveCapability(module);
        double requested = command.throttleByMount().getOrDefault(mountId, 0d);
        if (requested <= 0d) {
            return 0d;
        }
        double thrustLimit = Math.min(
                requiredPositiveParameter(module, THRUST_N),
                state.thrustLimitNByMount().getOrDefault(mountId, 0d));
        if (thrustLimit <= 0d) {
            return 0d;
        }
        double ratedThrust = requiredPositiveParameter(module, THRUST_N);
        double capabilityFraction = thrustLimit / ratedThrust;
        double massFlowAtRated = ratedThrust / requiredPositiveParameter(module, EXHAUST_VELOCITY_MPS);
        double availableReactionMassKg = reactionMassOnMount(state.consumables(), mountId);
        double propellantFraction = availableReactionMassKg / (massFlowAtRated * deltaSeconds);
        return Math.max(0d, Math.min(requested, Math.min(capabilityFraction, propellantFraction)));
    }

    private static PowerTotals totals(List<Use> uses) {
        double supply = 0d;
        double demand = 0d;
        double heatRejection = 0d;
        for (Use use : uses) {
            supply += use.supplyW();
            demand += use.demandW();
            heatRejection += use.heatRejectionW();
        }
        return new PowerTotals(supply, demand, heatRejection);
    }

    private double sharedBusEnergyCapacityJ(InstalledFit fit) {
        double result = 0d;
        for (InstalledModuleDefinition assignment : fit.installedModules()) {
            ModuleDefinition module = requireModule(assignment.moduleId());
            if (module.family() == ModuleFamily.ENERGY_STORAGE) {
                result += module.storedEnergyCapacityJ();
            }
        }
        return result;
    }

    private double sharedBusChargePowerW(InstalledFit fit) {
        double result = 0d;
        for (InstalledModuleDefinition assignment : fit.installedModules()) {
            ModuleDefinition module = requireModule(assignment.moduleId());
            if (module.family() == ModuleFamily.ENERGY_STORAGE) {
                result += optionalParameter(module, MAX_CHARGE_POWER_W, 0d);
            }
        }
        return result;
    }

    private double sharedBusDischargePowerW(InstalledFit fit) {
        double result = 0d;
        for (InstalledModuleDefinition assignment : fit.installedModules()) {
            ModuleDefinition module = requireModule(assignment.moduleId());
            if (module.family() == ModuleFamily.ENERGY_STORAGE) {
                result += optionalParameter(module, MAX_DISCHARGE_POWER_W, 0d);
            }
        }
        return result;
    }

    private double shipThermalStoreCapacityJ(InstalledFit fit) {
        double result = 0d;
        for (InstalledModuleDefinition assignment : fit.installedModules()) {
            ModuleDefinition module = requireModule(assignment.moduleId());
            if (module.family() == ModuleFamily.THERMAL_CONTROL) {
                result += optionalParameter(module, SHIP_THERMAL_STORE_CAPACITY_J, 0d);
            }
        }
        return result;
    }

    private FtlCapability ftlCapability(ModuleDefinition module) {
        double mass = requiredPositiveParameter(module, FTL_TRANSLATED_MASS_MAX_KG);
        double energy = requiredPositiveParameter(module, FTL_JUMP_ENERGY_J);
        double power = requiredPositiveParameter(module, FTL_CHARGE_POWER_W);
        double spool = requiredPositiveParameter(module, FTL_SPOOL_TIME_S);
        double transit = requiredPositiveParameter(module, FTL_EDGE_TRANSIT_TIME_S);
        double cooldown = requiredNonNegativeParameter(module, FTL_COOLDOWN_S);
        double heat = requiredNonNegativeParameter(module, FTL_JUMP_HEAT_J);
        if (energy > power * spool + Math.max(1d, energy) * 1e-9) {
            throw new IllegalArgumentException("FTL charge energy exceeds chargePower*spoolTime");
        }
        return new FtlCapability(mass, energy, power, spool, transit, cooldown, heat);
    }

    private static JumpPlan rejected(JumpFailure failure, String mountId, double translatedMassKg) {
        return new JumpPlan(false, failure, mountId, translatedMassKg, 0d, 0d, 0d, 0d, 0d, 0d);
    }

    private static double reactionMassOnMount(ConsumableState state, String mountId) {
        return state.interfaceLoads().stream()
                .filter(load -> load.kind() == InterfaceKind.REACTION_MASS && mountId.equals(load.mountId()))
                .mapToDouble(ConsumableLoad::massKg)
                .sum();
    }

    private static ConsumableState consumeReactionMass(
            ConsumableState state,
            String mountId,
            double consumedKg) {
        double remaining = consumedKg;
        List<ConsumableLoad> loads = new ArrayList<>();
        for (ConsumableLoad load : state.interfaceLoads()) {
            if (remaining > EPSILON
                    && load.kind() == InterfaceKind.REACTION_MASS
                    && mountId.equals(load.mountId())) {
                double take = Math.min(load.massKg(), remaining);
                if (load.amount() + EPSILON < take) {
                    throw new IllegalStateException(
                            "Stage 17.5C reaction-mass interface amount must be physical kilograms");
                }
                loads.add(new ConsumableLoad(
                        load.mountId(),
                        load.interfaceId(),
                        load.kind(),
                        Math.max(0d, load.amount() - take),
                        Math.max(0d, load.massKg() - take),
                        load.itemCount()));
                remaining -= take;
            } else {
                loads.add(load);
            }
        }
        if (remaining > Math.max(EPSILON, consumedKg * 1e-9)) {
            throw new IllegalStateException("reaction mass changed during deterministic propulsion step");
        }
        return new ConsumableState(
                state.cargoMassKg(),
                state.storesMassKg(),
                state.missionPayloadMassKg(),
                state.missionIntegrationVolumeM3(),
                loads);
    }

    private void validateDriveCapability(ModuleDefinition module) {
        double thrust = requiredPositiveParameter(module, THRUST_N);
        double exhaustVelocity = requiredPositiveParameter(module, EXHAUST_VELOCITY_MPS);
        double jetPower = requiredPositiveParameter(module, JET_POWER_W);
        double minimumJetPower = 0.5d * thrust * exhaustVelocity;
        if (jetPower + Math.max(1d, minimumJetPower) * 1e-12 < minimumJetPower) {
            throw new IllegalArgumentException(
                    "drive jet_power_w is below 0.5*thrust_n*exhaust_velocity_mps: " + module.id());
        }
    }

    private ModuleDefinition requireModule(String moduleId) {
        ModuleDefinition module = catalog.findModule(moduleId);
        if (module == null) {
            throw new IllegalArgumentException("unknown module: " + moduleId);
        }
        return module;
    }

    private static boolean isDrive(ModuleDefinition module) {
        return module.family() == ModuleFamily.MAIN_DRIVE
                || module.family() == ModuleFamily.MANEUVER_THRUSTERS;
    }

    private static double requiredPositiveParameter(ModuleDefinition module, String key) {
        Double value = module.capabilityParameters().get(key);
        if (value == null || !Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException("module " + module.id() + " requires positive " + key);
        }
        return value;
    }

    private static double requiredNonNegativeParameter(ModuleDefinition module, String key) {
        Double value = module.capabilityParameters().get(key);
        if (value == null || !Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException("module " + module.id() + " requires non-negative " + key);
        }
        return value;
    }

    private static double optionalParameter(ModuleDefinition module, String key, double fallback) {
        Double value = module.capabilityParameters().get(key);
        if (value == null) {
            return fallback;
        }
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException("module " + module.id() + " has invalid " + key);
        }
        return value;
    }

    private static Map<String, Double> immutableNonNegativeMap(Map<String, Double> source, String field) {
        Objects.requireNonNull(source, field);
        TreeMap<String, Double> result = new TreeMap<>();
        for (Map.Entry<String, Double> entry : source.entrySet()) {
            requireNonBlank(entry.getKey(), field + " key");
            Double value = Objects.requireNonNull(entry.getValue(), field + " value");
            requireNonNegativeFinite(value, field + " value");
            result.put(entry.getKey(), value);
        }
        return Collections.unmodifiableMap(result);
    }

    private static void requireNonNegativeFinite(double value, String field) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(field + " must be finite and non-negative");
        }
    }

    private static void requirePositiveFinite(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be finite and positive");
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
    }

    private final class Use {
        private final String mountId;
        private final ModuleDefinition module;
        private boolean enabled;
        private double fraction;

        private Use(String mountId, ModuleDefinition module, boolean enabled, double fraction) {
            this.mountId = mountId;
            this.module = module;
            this.enabled = enabled;
            this.fraction = fraction;
        }

        private double supplyW() {
            return enabled ? module.continuousPowerSupplyW() * fraction : 0d;
        }

        private double demandW() {
            return enabled ? module.continuousPowerDemandW() * fraction : 0d;
        }

        private double heatW() {
            return enabled ? module.wasteHeatW() * fraction : 0d;
        }

        private double heatRejectionW() {
            return enabled ? module.heatRejectionW() * fraction : 0d;
        }

        private double thrustN() {
            if (!enabled || !isDrive(module)) {
                return 0d;
            }
            double rated = requiredPositiveParameter(module, THRUST_N);
            double limit = Math.min(rated, fraction <= 0d ? 0d : rated);
            return limit * fraction;
        }
    }

    private record PowerTotals(double supplyW, double demandW, double heatRejectionW) { }

    private record FtlCapability(
            double translatedMassMaxKg,
            double jumpEnergyJ,
            double chargePowerW,
            double spoolTimeS,
            double edgeTransitTimeS,
            double cooldownS,
            double jumpHeatJ) { }
}
