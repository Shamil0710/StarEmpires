package com.spacesim.ship;

import com.spacesim.ship.ShieldFieldRuntime.State;
import com.spacesim.ship.ShipDamageRuntime.Snapshot;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipyardEngineeringService.MaintenanceState;
import com.spacesim.ship.WeaponMountRuntime.RuntimeState;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Authoritative Stage-17.5H mutable-by-replacement state that completes one fitted ship instance.
 *
 * <p>The Stage-17.5C {@link ShipEngineeringRuntime.RuntimeState} remains the propulsion/power/thermal
 * operating state. This record owns the other persistent physical state that must not be recreated
 * from a fit during materialization: local compartment/module damage, shield field reserves and
 * collapse timers, scheduled-service age, ammunition feed identity, and launcher cycle state.</p>
 *
 * <p>Derived capabilities are deliberately absent. They are always recomputed from the fitted hull,
 * consumables, damage and current operating state. Rendering and UI must project this state rather
 * than mutate it.</p>
 *
 * @param damage local structural/subsystem damage snapshot
 * @param shieldStatesByMount persistent shield field state keyed by physical emitter mount
 * @param maintenance scheduled-service age by installed mount
 * @param weaponLoadout ammunition content identity bound to physical feeds; quantities remain in consumables
 * @param weaponMountRuntime physical launcher-cycle cooldowns
 */
public record ShipInstanceRuntimeState(
        Snapshot damage,
        Map<String, State> shieldStatesByMount,
        MaintenanceState maintenance,
        WeaponLoadoutState weaponLoadout,
        RuntimeState weaponMountRuntime) {

    /**
     * Validates, sorts and freezes one complete physical ship-instance state.
     *
     * @param damage local structural/subsystem damage snapshot
     * @param shieldStatesByMount persistent shield states by physical emitter mount
     * @param maintenance scheduled-service age by installed mount
     * @param weaponLoadout ammunition feed identity bindings
     * @param weaponMountRuntime launcher-cycle cooldown state
     */
    public ShipInstanceRuntimeState {
        Objects.requireNonNull(damage, "damage");
        Objects.requireNonNull(shieldStatesByMount, "shieldStatesByMount");
        TreeMap<String, State> shields = new TreeMap<>();
        for (Map.Entry<String, State> entry : shieldStatesByMount.entrySet()) {
            requireNonBlank(entry.getKey(), "shield mountId");
            shields.put(entry.getKey(), Objects.requireNonNull(entry.getValue(), "shield state"));
        }
        shieldStatesByMount = Collections.unmodifiableMap(shields);
        Objects.requireNonNull(maintenance, "maintenance");
        Objects.requireNonNull(weaponLoadout, "weaponLoadout");
        Objects.requireNonNull(weaponMountRuntime, "weaponMountRuntime");
    }

    /**
     * Neutral compatibility state for pre-17.5H saves and legacy constructors.
     *
     * <p>It intentionally creates no damage, shield energy, ammunition identity or launcher delay.
     * A migrated legacy ship can only gain shield reserve later through ordinary recharge or an
     * explicit new-asset initialization path.</p>
     *
     * @return empty non-granting physical state
     */
    public static ShipInstanceRuntimeState legacyNeutral() {
        return new ShipInstanceRuntimeState(
                new Snapshot(Map.of(), DamageState.pristine()),
                Map.of(),
                new MaintenanceState(Map.of()),
                WeaponLoadoutState.empty(),
                RuntimeState.empty());
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
    }
}
