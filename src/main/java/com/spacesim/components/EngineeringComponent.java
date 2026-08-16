package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;

import java.util.Objects;

/**
 * Authoritative fitted engineering state of one physical ship entity.
 *
 * <p>The component stores only the installed fit and mutable physical operating state. Derived
 * mass, acceleration, delta-v, power margins and other capabilities are recomputed through the
 * central Stage-17.5 calculator/runtime and are never persisted as a second source of truth.</p>
 */
public final class EngineeringComponent implements Component {
    /** Stable installed production fit; replacing it is a refit operation rather than a stat edit. */
    public InstalledFit fit;
    /** Mutable-by-replacement physical operating state for propulsion, power, thermal and FTL. */
    public RuntimeState runtimeState;

    /** Creates an unconfigured component for mapper/framework construction only. */
    public EngineeringComponent() {
    }

    /**
     * Creates a fully configured authoritative engineering component.
     *
     * @param fit installed production fit
     * @param runtimeState current physical operating state
     */
    public EngineeringComponent(InstalledFit fit, RuntimeState runtimeState) {
        this.fit = Objects.requireNonNull(fit, "fit");
        this.runtimeState = Objects.requireNonNull(runtimeState, "runtimeState");
    }

    /**
     * Replaces only the current physical operating state while preserving the installed fit.
     *
     * @param nextState next authoritative runtime state
     */
    public void setRuntimeState(RuntimeState nextState) {
        runtimeState = Objects.requireNonNull(nextState, "nextState");
    }
}
