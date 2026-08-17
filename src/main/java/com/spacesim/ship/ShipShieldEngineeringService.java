package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.ship.ShieldFieldRuntime.State;
import com.spacesim.ship.ShipShieldEngineeringAdapter.FittedShield;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Stage-17.5H shield operation facade that binds Stage-17.5F field state to common engineering power.
 *
 * <p>Missing persisted field state starts empty/collapsed. Recharge power is admitted and committed
 * through {@link ShipEngineeringGrantService}; therefore a fitted emitter cannot recharge from an
 * implicit battery or bypass local damage. Updated reserve/collapse state is written back to the
 * authoritative {@link ShipInstanceRuntimeState}.</p>
 */
public final class ShipShieldEngineeringService {
    private final ShipCapabilityService capabilities;
    private final ShipEngineeringGrantService grants;
    private final ShieldFieldRuntime runtime = new ShieldFieldRuntime();

    /**
     * Creates a common shield-operation facade.
     *
     * @param catalog production engineering catalog
     */
    public ShipShieldEngineeringService(ShipEngineeringCatalog catalog) {
        ShipEngineeringCatalog checked = Objects.requireNonNull(catalog, "catalog");
        this.capabilities = new ShipCapabilityService(checked);
        this.grants = new ShipEngineeringGrantService(checked);
    }

    /**
     * Advances recharge/restart for one currently fitted emitter and persists the resulting state.
     *
     * @param engineering authoritative physical ship component
     * @param mountId fitted shield emitter mount
     * @param deltaSeconds positive simulation interval
     * @return persisted next shield state
     */
    public State stepRecharge(
            EngineeringComponent engineering,
            String mountId,
            double deltaSeconds) {
        EngineeringComponent component = Objects.requireNonNull(engineering, "engineering");
        if (mountId == null || mountId.isBlank()) {
            throw new IllegalArgumentException("mountId must be non-blank");
        }
        if (!Double.isFinite(deltaSeconds) || deltaSeconds <= 0d) {
            throw new IllegalArgumentException("deltaSeconds must be finite and positive");
        }
        FittedShield fitted = fittedShield(component, mountId);
        State current = component.instanceState.shieldStatesByMount().get(mountId);
        if (current == null) {
            current = new State(0d, 0d, true, 0d, fitted.emitterIntegrity());
        }
        current = runtime.withEmitterIntegrity(fitted.definition(), current, fitted.emitterIntegrity());

        double requestedPowerW = current.restartRemainingSeconds() > 0d
                ? 0d
                : fitted.definition().rechargePowerW() * fitted.emitterIntegrity();
        double grantedPowerW = 0d;
        if (requestedPowerW > 0d) {
            ShipEngineeringGrantService.GrantResult grant = grants.grantAndCommit(
                    component, mountId, requestedPowerW, 0d, deltaSeconds);
            if (grant.committed()) {
                grantedPowerW = grant.grant().grantedPowerW();
            }
        }
        State next = runtime.step(fitted.definition(), current, deltaSeconds, grantedPowerW);
        TreeMap<String, State> shieldStates = new TreeMap<>(component.instanceState.shieldStatesByMount());
        shieldStates.put(mountId, next);
        replaceInstance(component, shieldStates);
        return next;
    }

    private FittedShield fittedShield(EngineeringComponent component, String mountId) {
        return new ShipShieldEngineeringAdapter().derive(capabilities.snapshot(component).derived()).stream()
                .filter(value -> value.mountId().equals(mountId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "mount is not an operational fitted shield emitter: " + mountId));
    }

    private static void replaceInstance(EngineeringComponent component, Map<String, State> shields) {
        ShipInstanceRuntimeState current = component.instanceState;
        component.setInstanceState(new ShipInstanceRuntimeState(
                current.damage(),
                shields,
                current.maintenance(),
                current.weaponLoadout(),
                current.weaponMountRuntime()));
    }
}
