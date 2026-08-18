package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.CombatantRuntime;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Validated Stage-19I seam for authored damaged/depleted tactical initial conditions.
 *
 * <p>The service does not derive or cache combat performance. It changes only authoritative physical
 * inputs that already belong to the ship instance: local module integrity and interface-bound reaction
 * mass. Every capability, delta-v, acceleration, sensor/datalink output and later AI readiness decision
 * is still re-derived by the normal production owners.</p>
 *
 * <p>This is intentionally narrower than a combat-damage API. Live impacts must continue to use
 * {@link ShipDamageRuntime} and the accepted protection pipeline. This seam exists for deterministic
 * acceptance/scenario starts where a ship enters the battle already damaged or depleted.</p>
 */
public final class LiveTacticalInitialReadinessService {
    /**
     * Authors initial integrity for one actually installed module mount.
     *
     * @param combatant materialized physical combatant
     * @param mountId installed physical mount to degrade
     * @param integrity desired initial local integrity in {@code [0,1]}
     */
    public void setModuleIntegrity(CombatantRuntime combatant, String mountId, double integrity) {
        CombatantRuntime checked = Objects.requireNonNull(combatant, "combatant");
        requireFraction(integrity, "integrity");
        if (mountId == null || mountId.isBlank()) {
            throw new IllegalArgumentException("mountId must be non-blank");
        }
        boolean installed = checked.engineering().fit.installedModules().stream()
                .anyMatch(value -> value.mountId().equals(mountId));
        if (!installed) {
            throw new IllegalArgumentException("Unknown installed module mount: " + mountId);
        }

        EngineeringComponent engineering = checked.engineering();
        ShipInstanceRuntimeState instance = engineering.instanceState;
        TreeMap<String, Double> moduleIntegrity = new TreeMap<>(
                instance.damage().moduleDamage().moduleIntegrityByMount());
        moduleIntegrity.put(mountId, integrity);
        ShipDamageRuntime.Snapshot damage = new ShipDamageRuntime.Snapshot(
                instance.damage().compartmentIntegrityById(),
                new DamageState(moduleIntegrity));
        engineering.setInstanceState(new ShipInstanceRuntimeState(
                damage,
                instance.shieldStatesByMount(),
                instance.maintenance(),
                instance.weaponLoadout(),
                instance.weaponMountRuntime()));
    }

    /**
     * Retains a fraction of every currently loaded reaction-mass interface on one combatant.
     *
     * <p>Both authored interface amount and explicit SI mass scale together. Other stores, ammunition,
     * cargo and runtime power/thermal state are preserved. The operation is intended for initial-state
     * authoring and therefore scales the current load exactly once per call.</p>
     *
     * @param combatant materialized physical combatant
     * @param retainedFraction fraction of current reaction mass to retain in {@code [0,1]}
     */
    public void retainReactionMassFraction(CombatantRuntime combatant, double retainedFraction) {
        CombatantRuntime checked = Objects.requireNonNull(combatant, "combatant");
        requireFraction(retainedFraction, "retainedFraction");
        EngineeringComponent engineering = checked.engineering();
        RuntimeState state = engineering.runtimeState;
        ConsumableState consumables = state.consumables();
        ArrayList<ConsumableLoad> loads = new ArrayList<>(consumables.interfaceLoads().size());
        for (ConsumableLoad load : consumables.interfaceLoads()) {
            if (load.kind() == InterfaceKind.REACTION_MASS) {
                loads.add(new ConsumableLoad(
                        load.mountId(),
                        load.interfaceId(),
                        load.kind(),
                        canonicalZero(load.amount() * retainedFraction),
                        canonicalZero(load.massKg() * retainedFraction),
                        load.itemCount()));
            } else {
                loads.add(load);
            }
        }
        ConsumableState nextConsumables = new ConsumableState(
                consumables.cargoMassKg(),
                consumables.storesMassKg(),
                consumables.missionPayloadMassKg(),
                consumables.missionIntegrationVolumeM3(),
                loads);
        engineering.setRuntimeState(new RuntimeState(
                nextConsumables,
                state.sharedBusEnergyJ(),
                state.shipHeatStoredJ(),
                state.localHeatJByMount(),
                state.thrustLimitNByMount(),
                state.coolantBusCapacityW(),
                state.ftlCooldownSecondsByMount()));
    }

    private static double canonicalZero(double value) {
        return value == 0d ? 0d : value;
    }

    private static void requireFraction(double value, String label) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(label + " must be finite in [0,1]");
        }
    }
}
