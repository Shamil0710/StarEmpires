package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.CombatCommandComponent;
import com.spacesim.components.CombatComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MiningCommandComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Read-only Stage-14D telemetry accumulator for the playable economic loop.
 *
 * <p>The tracker samples authoritative state after ordinary simulation ticks. It never awards money,
 * moves ships, changes cargo, fires weapons or performs mining. Wallet contribution methods only
 * classify deltas that have already been produced by normal gameplay services.</p>
 */
public final class Stage14TelemetryTracker {
    private static final float MOVING_SPEED_EPSILON_SQUARED = 0.0001f;

    private final PlayerRuntime runtime;
    private final long initialWallet;
    private final int initialOwnedFleetCount;
    private final Set<FleetId> everOwned = new HashSet<>();
    private final Set<FleetId> lostOwned = new HashSet<>();

    private double elapsedSeconds;
    private double travelSeconds;
    private double miningSeconds;
    private double combatSeconds;
    private double idleSeconds;
    private double cargoUtilizationTimeIntegral;
    private double peakCargoUtilization;
    private double previousOwnedDurability;
    private boolean hasDurabilitySample;
    private double damageTaken;
    private double secondsToFirstProgression;
    private boolean firstProgressionObserved;
    private long tradeProfit;
    private long miningProfit;
    private long shipPurchaseCost;

    /**
     * Starts observation from the current player/world state.
     *
     * @param runtime authoritative playable runtime
     */
    public Stage14TelemetryTracker(PlayerRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "PlayerRuntime not set");
        PlayerState player = runtime.player();
        initialWallet = player.walletMilliCredits();
        initialOwnedFleetCount = player.ownedFleetIds().size();
        everOwned.addAll(player.ownedFleetIds());
        previousOwnedDurability = currentOwnedDurability();
        hasDurabilitySample = true;
    }

    /**
     * Samples one elapsed simulation interval and classifies the player's dominant current activity.
     *
     * <p>Priority is combat, manual mining, physical travel/jump, then idle. This prevents one
     * interval from being counted twice while still making the resulting first-hour breakdown easy
     * to interpret.</p>
     *
     * @param deltaSeconds finite non-negative simulation seconds represented by this sample
     */
    public void sample(double deltaSeconds) {
        if (!Double.isFinite(deltaSeconds) || deltaSeconds < 0d) {
            throw new IllegalArgumentException("Telemetry delta must be finite and non-negative");
        }
        if (deltaSeconds == 0d) {
            observeStructuralChanges();
            return;
        }

        PlayerState player = runtime.player();
        everOwned.addAll(player.ownedFleetIds());
        observeStructuralChanges();

        Entity active = activeShip();
        double utilization = cargoUtilization(active);
        cargoUtilizationTimeIntegral += utilization * deltaSeconds;
        peakCargoUtilization = Math.max(peakCargoUtilization, utilization);

        if (isCombatActive(active)) {
            combatSeconds += deltaSeconds;
        } else if (isMiningActive(active)) {
            miningSeconds += deltaSeconds;
        } else if (isTravelActive(active, player.activeFleetId())) {
            travelSeconds += deltaSeconds;
        } else {
            idleSeconds += deltaSeconds;
        }
        elapsedSeconds += deltaSeconds;

        if (!firstProgressionObserved && player.ownedFleetIds().size() > initialOwnedFleetCount) {
            firstProgressionObserved = true;
            secondsToFirstProgression = elapsedSeconds;
        }
    }

    /**
     * Attributes an already-completed ordinary trade wallet change to trade contribution.
     *
     * @param walletBefore player wallet before TradeController-backed command
     * @param walletAfter player wallet after command
     */
    public void recordTradeWalletChange(long walletBefore, long walletAfter) {
        tradeProfit = Math.addExact(tradeProfit, Math.subtractExact(walletAfter, walletBefore));
    }

    /**
     * Attributes an already-completed sale of physically mined cargo to mining contribution.
     *
     * @param walletBefore wallet immediately before mined-cargo sale
     * @param walletAfter wallet immediately after sale
     */
    public void recordMiningSaleWalletChange(long walletBefore, long walletAfter) {
        long delta = Math.subtractExact(walletAfter, walletBefore);
        if (delta < 0L) {
            throw new IllegalArgumentException("Mined-cargo sale cannot be recorded as a wallet loss");
        }
        miningProfit = Math.addExact(miningProfit, delta);
    }

    /**
     * Attributes an already-completed real ship purchase to progression spending.
     *
     * @param walletBefore wallet before ownership transfer
     * @param walletAfter wallet after ownership transfer
     */
    public void recordShipPurchase(long walletBefore, long walletAfter) {
        long cost = Math.subtractExact(walletBefore, walletAfter);
        if (cost < 0L) {
            throw new IllegalArgumentException("Ship purchase cannot increase player wallet");
        }
        shipPurchaseCost = Math.addExact(shipPurchaseCost, cost);
        everOwned.addAll(runtime.player().ownedFleetIds());
        observeStructuralChanges();
        if (!firstProgressionObserved
                && runtime.player().ownedFleetIds().size() > initialOwnedFleetCount) {
            firstProgressionObserved = true;
            secondsToFirstProgression = elapsedSeconds;
        }
    }

    /**
     * Builds a stable report from accumulated samples and current wallet state.
     *
     * @return immutable telemetry report
     */
    public Stage14TelemetryReport report() {
        observeStructuralChanges();
        long finalWallet = runtime.player().walletMilliCredits();
        long net = Math.subtractExact(finalWallet, initialWallet);
        double creditsPerHour = elapsedSeconds <= 0d
                ? 0d : (net / 1000d) * (3600d / elapsedSeconds);
        double averageCargo = elapsedSeconds <= 0d
                ? 0d : cargoUtilizationTimeIntegral / elapsedSeconds;
        return new Stage14TelemetryReport(
                elapsedSeconds,
                initialWallet,
                finalWallet,
                net,
                creditsPerHour,
                tradeProfit,
                miningProfit,
                shipPurchaseCost,
                travelSeconds,
                miningSeconds,
                combatSeconds,
                idleSeconds,
                averageCargo,
                peakCargoUtilization,
                lostOwned.size(),
                damageTaken,
                firstProgressionObserved ? secondsToFirstProgression : 0d,
                firstProgressionObserved);
    }

    private void observeStructuralChanges() {
        PlayerState player = runtime.player();
        everOwned.addAll(player.ownedFleetIds());
        for (FleetId owned : everOwned) {
            if (!player.ownedFleetIds().contains(owned) && runtime.world().findFleet(owned).isEmpty()) {
                lostOwned.add(owned);
            }
        }
        double durability = currentOwnedDurability();
        if (hasDurabilitySample && durability < previousOwnedDurability) {
            damageTaken += previousOwnedDurability - durability;
        }
        previousOwnedDurability = durability;
        hasDurabilitySample = true;
    }

    private double currentOwnedDurability() {
        double total = 0d;
        for (FleetId fleetId : runtime.player().ownedFleetIds()) {
            FleetPlacementState placement = runtime.world().findFleet(fleetId).orElse(null);
            if (placement == null || placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
                continue;
            }
            SimulationSession session = runtime.world().findSession(placement.systemId()).orElse(null);
            Entity entity = session == null ? null : session.getEntityRegistry().find(placement.localEntityId());
            CombatComponent combat = entity == null ? null : entity.getComponent(CombatComponent.class);
            if (combat != null) {
                total += Math.max(0f, combat.hull) + Math.max(0f, combat.shields);
            }
        }
        return total;
    }

    private Entity activeShip() {
        PlayerShipView view = runtime.activeShipView().orElse(null);
        SimulationSession session = view == null ? null : runtime.world().findSession(view.systemId()).orElse(null);
        return session == null ? null : session.getEntityRegistry().find(view.localEntityId());
    }

    private boolean isCombatActive(Entity active) {
        CombatCommandComponent command = active == null
                ? null : active.getComponent(CombatCommandComponent.class);
        return command != null && command.fireRequested && command.targetId != null;
    }

    private boolean isMiningActive(Entity active) {
        MiningCommandComponent command = active == null
                ? null : active.getComponent(MiningCommandComponent.class);
        return command != null && command.miningRequested;
    }

    private boolean isTravelActive(Entity active, FleetId activeFleetId) {
        if (activeFleetId != null && runtime.world().findFleetJump(activeFleetId).isPresent()) {
            return true;
        }
        TransformComponent transform = active == null ? null : active.getComponent(TransformComponent.class);
        return transform != null && transform.velocity.len2() > MOVING_SPEED_EPSILON_SQUARED;
    }

    private static double cargoUtilization(Entity active) {
        InventoryComponent inventory = active == null ? null : active.getComponent(InventoryComponent.class);
        if (inventory == null || inventory.capacity <= 0) {
            return 0d;
        }
        int stock = Math.max(0, inventory.getTotalStock());
        return Math.max(0d, Math.min(1d, (double) stock / inventory.capacity));
    }
}
