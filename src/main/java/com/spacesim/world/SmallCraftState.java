package com.spacesim.world;

import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipInstanceRuntimeState;

import java.util.Objects;

/**
 * Immutable authoritative identity and physical engineering state of one M22.8 small craft.
 *
 * <p>There is deliberately no wing hit-point pool or abstract ammunition count here. The exact
 * Stage-17.5 state carries fitted hull/modules, physical ammunition and reaction mass, power/thermal
 * state, damage, shields, maintenance age and weapon continuity for this individual craft.</p>
 *
 * @param id stable campaign identity
 * @param stableFactionId stable owning faction identity
 * @param designId stable authored design/fit identity
 * @param fit exact installed hull/module fit
 * @param runtimeState propulsion/power/thermal and physical consumable state
 * @param instanceState damage/shield/maintenance/weapon continuity state
 */
public record SmallCraftState(
        SmallCraftId id,
        String stableFactionId,
        String designId,
        InstalledFit fit,
        RuntimeState runtimeState,
        ShipInstanceRuntimeState instanceState) implements Comparable<SmallCraftState> {

    /** Validates one complete individual physical-craft state. */
    public SmallCraftState {
        Objects.requireNonNull(id, "id");
        stableFactionId = requireText(stableFactionId, "stableFactionId");
        designId = requireText(designId, "designId");
        Objects.requireNonNull(fit, "fit");
        Objects.requireNonNull(runtimeState, "runtimeState");
        Objects.requireNonNull(instanceState, "instanceState");
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(SmallCraftState other) {
        return id.compareTo(other.id);
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
