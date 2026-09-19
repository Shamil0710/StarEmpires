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
        return engineering.planJump(component);
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
