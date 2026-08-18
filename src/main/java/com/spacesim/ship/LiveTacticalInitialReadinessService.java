package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.CombatantRuntime;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;

import java.util.ArrayList;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Validated Stage-19I seam for authored damaged/depleted tactical initial conditions.
 *
 * <p>The service does not derive or cache combat performance. It changes only authoritative physical
 * inputs that already belong to the ship instance: local module integrity, interface-bound consumables,
 * shared stored electrical energy and local stored heat. Every capability, delta-v, acceleration,
 * sensor/datalink output and later AI readiness decision is still re-derived by production owners.</p>
 *
 * <p>This is intentionally narrower than a combat-damage API. Live impacts must continue to use
 * {@link ShipDamageRuntime} and the accepted protection pipeline. This seam exists for deterministic
 * acceptance/scenario starts where a ship enters the battle already damaged or resource-constrained.</p>
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
        requireInstalledMount(checked, mountId);

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
        engineering.setRuntimeState(withConsumables(state, new ConsumableState(
                consumables.cargoMassKg(),
                consumables.storesMassKg(),
                consumables.missionPayloadMassKg(),
                consumables.missionIntegrationVolumeM3(),
                loads)));
    }

    /**
     * Retains an exact number of physical ammunition items on one concrete installed feed.
     *
     * <p>The method may only remove items from the current load. Interface-native amount and physical
     * mass are scaled by the same retained-item ratio, preserving the current per-item amount/mass and
     * the central ship-mass authority. This is initial-state authoring, not a magazine counter and not
     * a combat-effectiveness multiplier.</p>
     *
     * @param combatant materialized physical combatant
     * @param mountId installed weapon/launcher mount owning the feed
     * @param interfaceId concrete ammunition interface on that mount
     * @param retainedItemCount exact physical item count to retain
     */
    public void retainAmmunitionItems(
            CombatantRuntime combatant,
            String mountId,
            String interfaceId,
            long retainedItemCount) {
        CombatantRuntime checked = Objects.requireNonNull(combatant, "combatant");
        requireNonBlank(mountId, "mountId");
        requireNonBlank(interfaceId, "interfaceId");
        if (retainedItemCount < 0L) {
            throw new IllegalArgumentException("retainedItemCount must be non-negative");
        }

        EngineeringComponent engineering = checked.engineering();
        RuntimeState state = engineering.runtimeState;
        ConsumableState consumables = state.consumables();
        ArrayList<ConsumableLoad> loads = new ArrayList<>(consumables.interfaceLoads().size());
        boolean matched = false;
        for (ConsumableLoad load : consumables.interfaceLoads()) {
            if (load.kind() == InterfaceKind.AMMUNITION
                    && load.mountId().equals(mountId)
                    && load.interfaceId().equals(interfaceId)) {
                if (matched) {
                    throw new IllegalStateException("Duplicate physical ammunition feed: " + mountId + "/" + interfaceId);
                }
                matched = true;
                if (retainedItemCount > load.itemCount()) {
                    throw new IllegalArgumentException("retainedItemCount cannot exceed current physical item count");
                }
                if (load.itemCount() == 0L) {
                    loads.add(load);
                } else {
                    double retainedRatio = retainedItemCount / (double) load.itemCount();
                    loads.add(new ConsumableLoad(
                            load.mountId(),
                            load.interfaceId(),
                            load.kind(),
                            canonicalZero(load.amount() * retainedRatio),
                            canonicalZero(load.massKg() * retainedRatio),
                            retainedItemCount));
                }
            } else {
                loads.add(load);
            }
        }
        if (!matched) {
            throw new IllegalArgumentException("Unknown physical ammunition feed: " + mountId + "/" + interfaceId);
        }
        engineering.setRuntimeState(withConsumables(state, new ConsumableState(
                consumables.cargoMassKg(),
                consumables.storesMassKg(),
                consumables.missionPayloadMassKg(),
                consumables.missionIntegrationVolumeM3(),
                loads)));
    }

    /**
     * Removes every physically loaded ammunition item while preserving all non-ammunition stores.
     *
     * @param combatant materialized physical combatant
     */
    public void clearAmmunition(CombatantRuntime combatant) {
        CombatantRuntime checked = Objects.requireNonNull(combatant, "combatant");
        EngineeringComponent engineering = checked.engineering();
        RuntimeState state = engineering.runtimeState;
        ConsumableState consumables = state.consumables();
        ArrayList<ConsumableLoad> loads = new ArrayList<>(consumables.interfaceLoads().size());
        for (ConsumableLoad load : consumables.interfaceLoads()) {
            if (load.kind() == InterfaceKind.AMMUNITION) {
                loads.add(new ConsumableLoad(
                        load.mountId(),
                        load.interfaceId(),
                        load.kind(),
                        0d,
                        0d,
                        0L));
            } else {
                loads.add(load);
            }
        }
        engineering.setRuntimeState(withConsumables(state, new ConsumableState(
                consumables.cargoMassKg(),
                consumables.storesMassKg(),
                consumables.missionPayloadMassKg(),
                consumables.missionIntegrationVolumeM3(),
                loads)));
    }

    /**
     * Authors current shared stored electrical energy for a constrained battle start.
     *
     * @param combatant materialized physical combatant
     * @param sharedBusEnergyJ current non-negative shared bus energy in joules
     */
    public void setSharedBusEnergyJ(CombatantRuntime combatant, double sharedBusEnergyJ) {
        CombatantRuntime checked = Objects.requireNonNull(combatant, "combatant");
        requireNonNegativeFinite(sharedBusEnergyJ, "sharedBusEnergyJ");
        EngineeringComponent engineering = checked.engineering();
        RuntimeState state = engineering.runtimeState;
        engineering.setRuntimeState(new RuntimeState(
                state.consumables(),
                sharedBusEnergyJ,
                state.shipHeatStoredJ(),
                state.localHeatJByMount(),
                state.thrustLimitNByMount(),
                state.coolantBusCapacityW(),
                state.ftlCooldownSecondsByMount()));
    }

    /**
     * Authors current local stored heat on one actually installed module mount.
     *
     * @param combatant materialized physical combatant
     * @param mountId installed physical mount
     * @param localHeatJ current non-negative local stored heat in joules
     */
    public void setLocalHeatJ(CombatantRuntime combatant, String mountId, double localHeatJ) {
        CombatantRuntime checked = Objects.requireNonNull(combatant, "combatant");
        requireNonNegativeFinite(localHeatJ, "localHeatJ");
        requireInstalledMount(checked, mountId);
        EngineeringComponent engineering = checked.engineering();
        RuntimeState state = engineering.runtimeState;
        TreeMap<String, Double> localHeat = new TreeMap<>(state.localHeatJByMount());
        localHeat.put(mountId, localHeatJ);
        engineering.setRuntimeState(new RuntimeState(
                state.consumables(),
                state.sharedBusEnergyJ(),
                state.shipHeatStoredJ(),
                localHeat,
                state.thrustLimitNByMount(),
                state.coolantBusCapacityW(),
                state.ftlCooldownSecondsByMount()));
    }

    private static RuntimeState withConsumables(RuntimeState state, ConsumableState consumables) {
        return new RuntimeState(
                consumables,
                state.sharedBusEnergyJ(),
                state.shipHeatStoredJ(),
                state.localHeatJByMount(),
                state.thrustLimitNByMount(),
                state.coolantBusCapacityW(),
                state.ftlCooldownSecondsByMount());
    }

    private static void requireInstalledMount(CombatantRuntime combatant, String mountId) {
        requireNonBlank(mountId, "mountId");
        boolean installed = combatant.engineering().fit.installedModules().stream()
                .anyMatch(value -> value.mountId().equals(mountId));
        if (!installed) {
            throw new IllegalArgumentException("Unknown installed module mount: " + mountId);
        }
    }

    private static double canonicalZero(double value) {
        return value == 0d ? 0d : value;
    }

    private static void requireFraction(double value, String label) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(label + " must be finite in [0,1]");
        }
    }

    private static void requireNonNegativeFinite(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
    }
}
