package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipInstanceRuntimeState;

import java.util.Objects;

/**
 * Authoritative fitted engineering state of one physical ship entity.
 *
 * <p>The component is the single ECS owner of a ship's fitted production identity and persistent
 * physical runtime state. Stage 17.5H extends the earlier Stage-17.5C boundary with local damage,
 * shields, maintenance and weapon continuity through {@link ShipInstanceRuntimeState}. Derived mass,
 * acceleration, delta-v, power margins, sensor/weapon/shield capability and UI projections are
 * recomputed through common services and are never persisted as a second source of truth.</p>
 */
public final class EngineeringComponent implements Component {
    /** Stable installed production fit; replacing it is a refit operation rather than a stat edit. */
    public InstalledFit fit;
    /** Mutable-by-replacement propulsion/power/thermal/FTL operating state. */
    public RuntimeState runtimeState;
    /** Mutable-by-replacement damage/shield/maintenance/weapon continuity state. */
    public ShipInstanceRuntimeState instanceState;

    /** Creates an unconfigured component for mapper/framework construction only. */
    public EngineeringComponent() {
    }

    /**
     * Compatibility constructor for pre-17.5H callers.
     *
     * <p>The auxiliary instance state is deliberately neutral and grants no shield energy,
     * ammunition identity or repairs. New production/materialization code should use the full
     * constructor.</p>
     *
     * @param fit installed production fit
     * @param runtimeState current physical operating state
     */
    public EngineeringComponent(InstalledFit fit, RuntimeState runtimeState) {
        this(fit, runtimeState, ShipInstanceRuntimeState.legacyNeutral());
    }

    /**
     * Creates a fully configured authoritative engineering component.
     *
     * @param fit installed production fit
     * @param runtimeState current propulsion/power/thermal/FTL state
     * @param instanceState current damage/shield/maintenance/weapon continuity state
     */
    public EngineeringComponent(
            InstalledFit fit,
            RuntimeState runtimeState,
            ShipInstanceRuntimeState instanceState) {
        this.fit = Objects.requireNonNull(fit, "fit");
        this.runtimeState = Objects.requireNonNull(runtimeState, "runtimeState");
        this.instanceState = Objects.requireNonNull(instanceState, "instanceState");
    }

    /**
     * Replaces only the propulsion/power/thermal/FTL state while preserving fit and local continuity.
     *
     * @param nextState next authoritative operating state
     */
    public void setRuntimeState(RuntimeState nextState) {
        runtimeState = Objects.requireNonNull(nextState, "nextState");
    }

    /**
     * Replaces damage/shield/maintenance/weapon state without changing the fitted hull/modules.
     *
     * @param nextState next authoritative instance state
     */
    public void setInstanceState(ShipInstanceRuntimeState nextState) {
        instanceState = Objects.requireNonNull(nextState, "nextState");
    }
}
