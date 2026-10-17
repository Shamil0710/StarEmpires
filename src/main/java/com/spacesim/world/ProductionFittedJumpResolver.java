package com.spacesim.world;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.ship.ProductionEngineeringRuntimeResolver;
import com.spacesim.ship.ShipEngineeringRuntime.JumpPlan;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;

import java.util.List;
import java.util.Objects;

/**
 * Narrow fitted-jump adapter over the common production engineering resolver.
 *
 * <p>Catalog selection and engineering authority are shared with ordinary fitted propulsion. This
 * class retains only the historical {@link FleetJumpService} dependency shape and does not own a
 * second propulsion/power/thermal implementation.</p>
 */
final class ProductionFittedJumpResolver implements FleetJumpService.FittedJumpResolver {
    private final ProductionEngineeringRuntimeResolver engineering;
    private final java.util.WeakHashMap<EngineeringComponent, CachedPlan> plans = new java.util.WeakHashMap<>();

    private record CachedPlan(com.spacesim.ship.ShipEngineeringState.InstalledFit fit,
            RuntimeState state, com.spacesim.ship.ShipEngineeringState.DamageState damage, JumpPlan plan) { }

    ProductionFittedJumpResolver() {
        this(new ProductionEngineeringRuntimeResolver());
    }

    ProductionFittedJumpResolver(List<ShipEngineeringCatalog> catalogs) {
        this(new ProductionEngineeringRuntimeResolver(catalogs));
    }

    ProductionFittedJumpResolver(ProductionEngineeringRuntimeResolver engineering) {
        this.engineering = Objects.requireNonNull(engineering, "engineering");
    }

    @Override
    public JumpPlan plan(EngineeringComponent component) {
        var checked = Objects.requireNonNull(component, "component");
        var cached = plans.get(checked);
        var damage = checked.instanceState == null || checked.instanceState.damage() == null ? null
                : checked.instanceState.damage().moduleDamage();
        // These are exactly the immutable inputs passed to ShipEngineeringRuntime.planJump.
        // An equal replacement snapshot (for example idle maintenance/weapon bookkeeping) is safe.
        if (cached != null && cached.fit().equals(checked.fit) && cached.state().equals(checked.runtimeState)
                && cached.damage().equals(damage)) return cached.plan();
        var plan = engineering.planJump(checked);
        plans.put(checked, new CachedPlan(checked.fit, checked.runtimeState, damage, plan));
        return plan;
    }

    @Override
    public RuntimeState commit(EngineeringComponent component, JumpPlan plan) {
        return engineering.commitJump(component, plan);
    }

    @Override
    public RuntimeState advanceIdle(EngineeringComponent component, double deltaSeconds) {
        return engineering.advanceIdle(component, deltaSeconds);
    }
}
