package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader;
import com.spacesim.ship.ShipEngineeringRuntime.JumpPlan;
import com.spacesim.ship.ShipEngineeringRuntime.OperatingCommand;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringRuntime.TickResult;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Common production resolver for fitted ship engineering.
 *
 * <p>This is the single catalog-routing seam used by ordinary flight and the existing fitted jump
 * adapter. It never creates fuel, energy or thrust. Every propulsion step delegates to
 * {@link ShipEngineeringRuntime}, so reaction mass, power, thermal limits and damage are identical
 * to the Stage-19 tactical path.</p>
 */
public final class ProductionEngineeringRuntimeResolver {
    private static final double EPSILON = 1.0e-9d;
    private static final double MANEUVER_STEP_SECONDS = 1.0d;
    private static final int MAX_MANEUVER_STEPS = 100_000;

    private final List<CatalogRuntime> catalogs;

    /**
     * Creates the production resolver over every currently admitted fitted-ship catalog.
     */
    public ProductionEngineeringRuntimeResolver() {
        this(List.of(
                ShipEngineeringCatalogLoader.loadDefault(),
                Stage175ICombatTestContentPack.loadStage21StrategicDoctrines(),
                Stage22FreightStrategicEngineeringCatalogLoader.loadDefault()));
    }

    /**
     * Creates an explicit resolver, primarily for deterministic acceptance fixtures.
     *
     * @param catalogs non-empty engineering catalogs admitted by this resolver
     */
    public ProductionEngineeringRuntimeResolver(List<ShipEngineeringCatalog> catalogs) {
        Objects.requireNonNull(catalogs, "catalogs");
        if (catalogs.isEmpty()) {
            throw new IllegalArgumentException("at least one engineering catalog is required");
        }
        ArrayList<CatalogRuntime> resolved = new ArrayList<>(catalogs.size());
        for (ShipEngineeringCatalog catalog : catalogs) {
            ShipEngineeringCatalog checked = Objects.requireNonNull(catalog, "catalog");
            resolved.add(new CatalogRuntime(
                    checked,
                    new ShipEngineeringRuntime(checked),
                    new DerivedShipCalculator(checked)));
        }
        this.catalogs = List.copyOf(resolved);
    }

    /**
     * Resolves the minimum commanded drive throttle needed to cover a requested velocity change
     * within one fixed simulation interval.
     *
     * <p>The estimate uses the same current damage-aware fitted mass/thrust authority as the
     * engineering runtime. Power or thermal limits may still reduce actually produced thrust, in
     * which case the controller simply continues accelerating on a later tick. The method therefore
     * avoids over-burning reaction mass when only a fraction of a full-thrust tick is needed without
     * ever manufacturing more acceleration than the live engineering state can produce.</p>
     *
     * @param component authoritative fitted engineering component
     * @param requiredDeltaVMps non-negative desired velocity change for this tick
     * @param deltaSeconds positive fixed simulation interval
     * @return commanded common drive throttle in [0,1]
     */
    public double throttleForDeltaV(
            EngineeringComponent component,
            double requiredDeltaVMps,
            double deltaSeconds) {
        EngineeringComponent checked = requireComplete(component);
        if (!Double.isFinite(requiredDeltaVMps) || requiredDeltaVMps < 0d) {
            throw new IllegalArgumentException("requiredDeltaVMps must be finite and non-negative");
        }
        if (!Double.isFinite(deltaSeconds) || deltaSeconds <= 0d) {
            throw new IllegalArgumentException("deltaSeconds must be positive and finite");
        }
        if (requiredDeltaVMps <= EPSILON) {
            return 0d;
        }
        DerivedShipState state = derive(checked);
        if (state.availableThrustN() <= EPSILON || state.totalMassKg() <= EPSILON) {
            return 0d;
        }
        double maximumDeltaV = state.availableThrustN() / state.totalMassKg() * deltaSeconds;
        if (!Double.isFinite(maximumDeltaV) || maximumDeltaV <= EPSILON) {
            return 0d;
        }
        return Math.min(1d, requiredDeltaVMps / maximumDeltaV);
    }

    /**
     * Advances one ordinary fitted propulsion tick and replaces the component runtime state.
     *
     * @param component authoritative fitted component
     * @param throttle requested common drive throttle in [0,1]
     * @param deltaSeconds positive authoritative simulation interval
     * @return physical engineering result used by FlightDynamics
     */
    public TickResult advancePropulsion(
            EngineeringComponent component,
            double throttle,
            double deltaSeconds) {
        EngineeringComponent checked = requireComplete(component);
        if (!Double.isFinite(throttle) || throttle < 0d || throttle > 1d) {
            throw new IllegalArgumentException("throttle must be in [0,1]");
        }
        if (!Double.isFinite(deltaSeconds) || deltaSeconds <= 0d) {
            throw new IllegalArgumentException("deltaSeconds must be positive and finite");
        }
        CatalogRuntime selected = resolve(checked.fit);
        TickResult result = selected.runtime().advance(
                checked.fit,
                checked.runtimeState,
                checked.instanceState.damage().moduleDamage(),
                command(selected.catalog(), checked.fit, throttle),
                deltaSeconds);
        checked.setRuntimeState(result.state());
        return result;
    }

    /**
     * Plans a fitted FTL jump through the same resolved engineering runtime.
     *
     * @param component authoritative fitted engineering component
     * @return current physical FTL plan
     */
    public JumpPlan planJump(EngineeringComponent component) {
        EngineeringComponent checked = requireComplete(component);
        CatalogRuntime selected = resolve(checked.fit);
        return selected.runtime().planJump(
                checked.fit,
                checked.runtimeState,
                checked.instanceState.damage().moduleDamage());
    }

    /**
     * Commits an already planned fitted FTL jump.
     *
     * @param component authoritative fitted engineering component
     * @param plan previously validated FTL plan
     * @return committed next engineering runtime state
     */
    public RuntimeState commitJump(EngineeringComponent component, JumpPlan plan) {
        EngineeringComponent checked = requireComplete(component);
        CatalogRuntime selected = resolve(checked.fit);
        return selected.runtime().commitJump(
                checked.runtimeState,
                Objects.requireNonNull(plan, "plan"));
    }

    /**
     * Advances fitted engineering with propulsion idle.
     *
     * @param component authoritative fitted engineering component
     * @param deltaSeconds positive authoritative simulation interval
     * @return next idle engineering runtime state
     */
    public RuntimeState advanceIdle(EngineeringComponent component, double deltaSeconds) {
        EngineeringComponent checked = requireComplete(component);
        CatalogRuntime selected = resolve(checked.fit);
        return selected.runtime().advance(
                checked.fit,
                checked.runtimeState,
                checked.instanceState.damage().moduleDamage(),
                OperatingCommand.idle(),
                deltaSeconds).state();
    }

    /**
     * Returns the current damage-aware derived ship state without mutation.
     *
     * @param component authoritative fitted engineering component
     * @return current derived physical ship state
     */
    public DerivedShipState derive(EngineeringComponent component) {
        EngineeringComponent checked = requireComplete(component);
        CatalogRuntime selected = resolve(checked.fit);
        return selected.calculator().derive(
                selected.catalog().findHull(checked.fit.hullId()),
                checked.fit,
                checked.runtimeState.consumables(),
                checked.instanceState.damage().moduleDamage());
    }

    /**
     * Previews a finite delta-v maneuver on immutable runtime state.
     *
     * <p>The preview repeatedly executes the real engineering runtime on local immutable state. It
     * therefore cannot promise a maneuver that current reaction mass, power, thermal condition or
     * drive damage cannot actually produce. The component itself is never mutated.</p>
     */
    public ManeuverPlan planDeltaV(EngineeringComponent component, double requiredDeltaVMps) {
        EngineeringComponent checked = requireComplete(component);
        if (!Double.isFinite(requiredDeltaVMps) || requiredDeltaVMps < 0d) {
            throw new IllegalArgumentException("requiredDeltaVMps must be finite and non-negative");
        }
        CatalogRuntime selected = resolve(checked.fit);
        RuntimeState state = checked.runtimeState;
        if (requiredDeltaVMps <= EPSILON) {
            return new ManeuverPlan(true, requiredDeltaVMps, 0d, 0d, state);
        }

        double delivered = 0d;
        double elapsed = 0d;
        Map<String, Double> throttles = driveThrottle(selected.catalog(), checked.fit, 1d);
        if (throttles.isEmpty()) {
            return new ManeuverPlan(false, requiredDeltaVMps, 0d, 0d, state);
        }
        OperatingCommand command = new OperatingCommand(throttles, Map.of(), java.util.Set.of());

        for (int step = 0; step < MAX_MANEUVER_STEPS && delivered + EPSILON < requiredDeltaVMps; step++) {
            RuntimeState before = state;
            double remaining = requiredDeltaVMps - delivered;
            TickResult full = selected.runtime().advance(
                    checked.fit,
                    before,
                    checked.instanceState.damage().moduleDamage(),
                    command,
                    MANEUVER_STEP_SECONDS);
            if (full.actualThrustN() <= EPSILON) {
                return new ManeuverPlan(false, requiredDeltaVMps, delivered, elapsed, state);
            }
            double acceleration = full.actualThrustN() / full.derivedState().totalMassKg();
            if (!Double.isFinite(acceleration) || acceleration <= EPSILON) {
                return new ManeuverPlan(false, requiredDeltaVMps, delivered, elapsed, state);
            }
            double stepSeconds = Math.min(MANEUVER_STEP_SECONDS, remaining / acceleration);
            TickResult accepted = stepSeconds + EPSILON < MANEUVER_STEP_SECONDS
                    ? selected.runtime().advance(
                            checked.fit,
                            before,
                            checked.instanceState.damage().moduleDamage(),
                            command,
                            stepSeconds)
                    : full;
            if (accepted.actualThrustN() <= EPSILON) {
                return new ManeuverPlan(false, requiredDeltaVMps, delivered, elapsed, state);
            }
            double stepAcceleration = accepted.actualThrustN() / accepted.derivedState().totalMassKg();
            double stepDeltaV = stepAcceleration * stepSeconds;
            if (!(stepDeltaV > 0d) || !Double.isFinite(stepDeltaV)) {
                return new ManeuverPlan(false, requiredDeltaVMps, delivered, elapsed, state);
            }
            state = accepted.state();
            delivered += stepDeltaV;
            elapsed += stepSeconds;
        }
        boolean feasible = delivered + 1.0e-6d >= requiredDeltaVMps;
        return new ManeuverPlan(feasible, requiredDeltaVMps, delivered, elapsed, state);
    }

    /**
     * Applies a previously previewed maneuver result exactly once.
     *
     * @param component authoritative fitted engineering component
     * @param plan feasible immutable maneuver preview
     */
    public void commitManeuver(EngineeringComponent component, ManeuverPlan plan) {
        EngineeringComponent checked = requireComplete(component);
        ManeuverPlan accepted = Objects.requireNonNull(plan, "plan");
        if (!accepted.feasible()) {
            throw new IllegalStateException("cannot commit an infeasible maneuver");
        }
        checked.setRuntimeState(accepted.resultingState());
    }

    private CatalogRuntime resolve(InstalledFit fit) {
        InstalledFit checked = Objects.requireNonNull(fit, "fit");
        CatalogRuntime selected = null;
        for (CatalogRuntime candidate : catalogs) {
            if (!supports(candidate.catalog(), checked)) {
                continue;
            }
            if (selected != null) {
                throw new IllegalArgumentException(
                        "ambiguous engineering catalogs for fitted hull: " + checked.hullId());
            }
            selected = candidate;
        }
        if (selected == null) {
            throw new IllegalArgumentException(
                    "no engineering catalog supports fitted hull/modules: " + checked.hullId());
        }
        return selected;
    }

    private static OperatingCommand command(
            ShipEngineeringCatalog catalog,
            InstalledFit fit,
            double throttle) {
        return new OperatingCommand(driveThrottle(catalog, fit, throttle), Map.of(), java.util.Set.of());
    }

    private static Map<String, Double> driveThrottle(
            ShipEngineeringCatalog catalog,
            InstalledFit fit,
            double throttle) {
        if (throttle <= 0d) {
            return Map.of();
        }
        TreeMap<String, Double> result = new TreeMap<>();
        for (InstalledModuleDefinition installed : fit.installedModules()) {
            var module = catalog.findModule(installed.moduleId());
            if (module != null && (module.family() == ModuleFamily.MAIN_DRIVE
                    || module.family() == ModuleFamily.MANEUVER_THRUSTERS)) {
                result.put(installed.mountId(), throttle);
            }
        }
        return Map.copyOf(result);
    }

    private static boolean supports(ShipEngineeringCatalog catalog, InstalledFit fit) {
        if (catalog.findHull(fit.hullId()) == null) {
            return false;
        }
        for (InstalledModuleDefinition assignment : fit.installedModules()) {
            if (catalog.findModule(assignment.moduleId()) == null) {
                return false;
            }
        }
        return true;
    }

    private static EngineeringComponent requireComplete(EngineeringComponent component) {
        EngineeringComponent checked = Objects.requireNonNull(component, "component");
        if (checked.fit == null || checked.runtimeState == null || checked.instanceState == null
                || checked.instanceState.damage() == null) {
            throw new IllegalStateException("fitted EngineeringComponent is incomplete");
        }
        return checked;
    }

    /**
     * Immutable preview of a propulsion maneuver.
     *
     * @param feasible whether current engineering state can deliver the requested delta-v
     * @param requiredDeltaVMps requested delta-v
     * @param deliveredDeltaVMps physically deliverable delta-v reached by the preview
     * @param burnSeconds authoritative burn duration represented by the preview
     * @param resultingState immutable engineering state after the previewed burn
     */
    public record ManeuverPlan(
            boolean feasible,
            double requiredDeltaVMps,
            double deliveredDeltaVMps,
            double burnSeconds,
            RuntimeState resultingState) {
        /**
         * Validates one immutable maneuver preview.
         *
         * @param feasible whether the requested maneuver is feasible
         * @param requiredDeltaVMps requested delta-v
         * @param deliveredDeltaVMps delivered preview delta-v
         * @param burnSeconds preview burn duration
         * @param resultingState immutable resulting engineering state
         */
        public ManeuverPlan {
            if (!Double.isFinite(requiredDeltaVMps) || requiredDeltaVMps < 0d
                    || !Double.isFinite(deliveredDeltaVMps) || deliveredDeltaVMps < 0d
                    || !Double.isFinite(burnSeconds) || burnSeconds < 0d) {
                throw new IllegalArgumentException("maneuver scalars must be finite and non-negative");
            }
            Objects.requireNonNull(resultingState, "resultingState");
        }
    }

    private record CatalogRuntime(
            ShipEngineeringCatalog catalog,
            ShipEngineeringRuntime runtime,
            DerivedShipCalculator calculator) { }
}
