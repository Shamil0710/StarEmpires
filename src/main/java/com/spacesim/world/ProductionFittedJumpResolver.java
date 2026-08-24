package com.spacesim.world;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.ship.ShipEngineeringRuntime;
import com.spacesim.ship.ShipEngineeringRuntime.JumpPlan;
import com.spacesim.ship.ShipEngineeringRuntime.OperatingCommand;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic production resolver that composes existing fitted-engineering catalogs for the
 * ordinary {@link FleetJumpService} FSM.
 *
 * <p>The resolver owns no movement or jump state. It selects exactly one existing engineering
 * catalog that can interpret the fitted hull/modules, delegates FTL planning, physical cooldown
 * progression and commit to that catalog's {@link ShipEngineeringRuntime}, and always plans against
 * the current physical module damage. Unknown or ambiguous fitted content fails closed. Stage-10
 * legacy fleets without an {@link EngineeringComponent} never enter this resolver.</p>
 */
final class ProductionFittedJumpResolver implements FleetJumpService.FittedJumpResolver {
    private final List<CatalogRuntime> catalogs;

    ProductionFittedJumpResolver() {
        this(List.of(
                ShipEngineeringCatalogLoader.loadDefault(),
                Stage175ICombatTestContentPack.loadStage21StrategicDoctrines()));
    }

    ProductionFittedJumpResolver(List<ShipEngineeringCatalog> catalogs) {
        Objects.requireNonNull(catalogs, "catalogs");
        if (catalogs.isEmpty()) {
            throw new IllegalArgumentException("at least one engineering catalog is required");
        }
        ArrayList<CatalogRuntime> resolved = new ArrayList<>(catalogs.size());
        for (ShipEngineeringCatalog catalog : catalogs) {
            ShipEngineeringCatalog checked = Objects.requireNonNull(catalog, "catalog");
            resolved.add(new CatalogRuntime(checked, new ShipEngineeringRuntime(checked)));
        }
        this.catalogs = List.copyOf(resolved);
    }

    @Override
    public JumpPlan plan(EngineeringComponent component) {
        EngineeringComponent checked = requireComplete(component);
        CatalogRuntime selected = resolve(checked.fit);
        return selected.runtime().planJump(
                checked.fit,
                checked.runtimeState,
                checked.instanceState.damage().moduleDamage());
    }

    @Override
    public RuntimeState commit(EngineeringComponent component, JumpPlan plan) {
        EngineeringComponent checked = requireComplete(component);
        CatalogRuntime selected = resolve(checked.fit);
        return selected.runtime().commitJump(
                checked.runtimeState,
                Objects.requireNonNull(plan, "plan"));
    }

    @Override
    public RuntimeState advanceIdle(EngineeringComponent component, double deltaSeconds) {
        EngineeringComponent checked = requireComplete(component);
        CatalogRuntime selected = resolve(checked.fit);
        return selected.runtime().advance(
                checked.fit,
                checked.runtimeState,
                checked.instanceState.damage(),
                OperatingCommand.idle(),
                deltaSeconds).state();
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
        if (checked.fit == null || checked.runtimeState == null || checked.instanceState == null) {
            throw new IllegalStateException("fitted EngineeringComponent is incomplete");
        }
        Objects.requireNonNull(checked.instanceState.damage(), "component.instanceState.damage");
        return checked;
    }

    private record CatalogRuntime(ShipEngineeringCatalog catalog, ShipEngineeringRuntime runtime) {
        private CatalogRuntime {
            Objects.requireNonNull(catalog, "catalog");
            Objects.requireNonNull(runtime, "runtime");
        }
    }
}
