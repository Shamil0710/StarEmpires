package com.spacesim.trade;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ReputationComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.controllers.TradeController;
import com.spacesim.economy.Money;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldSimulation;

import java.util.List;
import java.util.Objects;

/**
 * Runtime executor for one already-scored inter-system cargo route.
 *
 * <p>The job deliberately reuses authoritative boundaries instead of introducing a second trade or
 * transit implementation: purchase and sale go through {@link TradeController}, while every hop is
 * requested through {@link WorldSimulation#requestFleetJump(FleetId, StarSystemId, float, float)}.
 * Consequently goods, wallets, ledgers, world FleetId handoff and Stage-10B jump timing all remain
 * physical and authoritative.</p>
 *
 * <p>A route is only a planning snapshot. Destination stock, price, free capacity and liquidity may
 * legally change while the fleet is in transit. On arrival the executor therefore revalidates the
 * live consumer and sells the largest still-valid portion of the purchased cargo. Any unsold
 * remainder remains physically in the fleet inventory for later replanning; access restrictions are
 * never bypassed.</p>
 *
 * <p>Local jump-gate coordinates are not yet part of world topology. Intermediate hops therefore
 * materialize at the neutral local origin coordinate {@code (0,0)}. The final hop materializes at
 * the consumer market coordinate so the Stage-10E acceptance loop does not invent a separate
 * destination object. This seam is intentionally replaceable when gate anchors are introduced.</p>
 */
public final class InterSystemTradeJob {
    /** Execution phase of a physical inter-system trade job. */
    public enum State {
        /** Cargo has not yet been purchased. */
        BUYING,
        /** Fleet is progressing through the route's jump path. */
        JUMPING,
        /** Fleet reached the consumer system and must sell the cargo. */
        SELLING,
        /** A positive live-valid portion of the cargo was physically delivered and sold. */
        COMPLETED,
        /** Live world state invalidated the planned job before any destination sale. */
        FAILED
    }

    private final FleetId fleetId;
    private final GalacticTradeRoute route;
    private State state = State.BUYING;
    private int currentPathIndex;
    private int soldAmount;
    private String failureReason;

    /**
     * Creates an executable job from a pure galactic trade route.
     *
     * @param fleetId stable world-level fleet identity
     * @param route already-scored cross-system route
     */
    public InterSystemTradeJob(FleetId fleetId, GalacticTradeRoute route) {
        this.fleetId = Objects.requireNonNull(fleetId, "FleetId trade job не задан");
        this.route = Objects.requireNonNull(route, "GalacticTradeRoute не задан");
        if (route.jumpPath().jumpCount() <= 0) {
            throw new IllegalArgumentException("Inter-system trade job требует хотя бы один jump");
        }
    }

    /**
     * Advances only job decisions; world time itself remains owned by {@link WorldSimulation}.
     *
     * <p>Callers normally invoke this once after planning and then after each world advance. The
     * method is deterministic and idempotent while an authoritative jump is still active.</p>
     *
     * @param world authoritative runtime containing the fleet and both markets
     * @return state after this decision step
     */
    public State advance(WorldSimulation world) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "WorldSimulation не задан");
        if (isTerminal()) {
            return state;
        }
        try {
            switch (state) {
                case BUYING -> buyAndStart(checkedWorld);
                case JUMPING -> continueJumpPath(checkedWorld);
                case SELLING -> sell(checkedWorld);
                case COMPLETED, FAILED -> {
                    // terminal states are handled above
                }
            }
        } catch (RuntimeException exception) {
            fail(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
        }
        return state;
    }

    /** @return stable fleet executing this job */
    public FleetId fleetId() {
        return fleetId;
    }

    /** @return immutable route selected for this job */
    public GalacticTradeRoute route() {
        return route;
    }

    /** @return current execution phase */
    public State state() {
        return state;
    }

    /** @return amount physically sold at the destination, zero before a successful sale */
    public int soldAmount() {
        return soldAmount;
    }

    /** @return planned purchased cargo left in the fleet after a partial destination sale */
    public int remainingCargoAmount() {
        return Math.max(0, route.amount() - soldAmount);
    }

    /** @return {@code true} after either successful completion or a permanent execution failure */
    public boolean isTerminal() {
        return state == State.COMPLETED || state == State.FAILED;
    }

    /** @return diagnostic failure reason, or {@code null} before/after successful execution */
    public String failureReason() {
        return failureReason;
    }

    private void buyAndStart(WorldSimulation world) {
        FleetPlacementState placement = requireLocalPlacement(world, route.buySystemId());
        SimulationSession session = requireSession(world, route.buySystemId());
        Entity fleet = requireEntity(session, placement.localEntityId(), "fleet");
        Entity supplier = requireEntity(session, route.buyStationId(), "supplier");
        TradeController controller = new TradeController(session.getLedger());
        if (!controller.buyFromStation(
                supplier,
                fleet,
                route.itemId(),
                route.amount(),
                fleet.getComponent(ReputationComponent.class))) {
            fail("Supplier transaction became invalid before purchase");
            return;
        }
        state = State.JUMPING;
        requestNextHop(world);
    }

    private void continueJumpPath(WorldSimulation world) {
        if (world.findFleetJump(fleetId).isPresent()) {
            return;
        }
        List<StarSystemId> systems = route.jumpPath().systems();
        int arrivedIndex = currentPathIndex + 1;
        if (arrivedIndex >= systems.size()) {
            fail("Jump path index exceeded destination");
            return;
        }
        FleetPlacementState placement = requireLocalPlacement(world, systems.get(arrivedIndex));
        currentPathIndex = arrivedIndex;
        if (currentPathIndex == systems.size() - 1) {
            state = State.SELLING;
            sell(world);
            return;
        }
        if (!placement.systemId().equals(systems.get(currentPathIndex))) {
            fail("Fleet arrived in an unexpected StarSystem");
            return;
        }
        requestNextHop(world);
    }

    private void requestNextHop(WorldSimulation world) {
        List<StarSystemId> systems = route.jumpPath().systems();
        int destinationIndex = currentPathIndex + 1;
        if (destinationIndex >= systems.size()) {
            state = State.SELLING;
            return;
        }
        StarSystemId destination = systems.get(destinationIndex);
        float arrivalX = 0f;
        float arrivalY = 0f;
        if (destinationIndex == systems.size() - 1) {
            MarketDirectory.StationMarket consumer = destinationMarketSnapshot(world);
            arrivalX = consumer.x();
            arrivalY = consumer.y();
        }
        world.requestFleetJump(fleetId, destination, arrivalX, arrivalY);
    }

    private void sell(WorldSimulation world) {
        FleetPlacementState placement = requireLocalPlacement(world, route.sellSystemId());
        SimulationSession session = requireSession(world, route.sellSystemId());
        Entity fleet = requireEntity(session, placement.localEntityId(), "fleet");
        Entity consumer = requireEntity(session, route.sellStationId(), "consumer");
        TradeController controller = new TradeController(session.getLedger());
        ReputationComponent reputation = fleet.getComponent(ReputationComponent.class);

        int liveAmount = maximumLiveSellAmount(controller, consumer, fleet, reputation);
        if (liveAmount <= 0
                || !controller.sellToStation(
                consumer,
                fleet,
                route.itemId(),
                liveAmount,
                reputation)) {
            fail("Consumer transaction became invalid before sale");
            return;
        }
        soldAmount = liveAmount;
        state = State.COMPLETED;
    }

    private int maximumLiveSellAmount(
            TradeController controller,
            Entity consumer,
            Entity fleet,
            ReputationComponent reputation) {
        if (!controller.canTradeWithStation(fleet, consumer)) {
            return 0;
        }
        InventoryComponent consumerInventory = consumer.getComponent(InventoryComponent.class);
        InventoryComponent fleetInventory = fleet.getComponent(InventoryComponent.class);
        MarketComponent market = consumer.getComponent(MarketComponent.class);
        WalletComponent consumerWallet = consumer.getComponent(WalletComponent.class);
        WalletComponent fleetWallet = fleet.getComponent(WalletComponent.class);
        if (consumerInventory == null
                || fleetInventory == null
                || market == null
                || consumerWallet == null
                || fleetWallet == null
                || !market.isTradable(route.itemId())) {
            return 0;
        }

        int demand = Math.max(0, market.targetStock[route.itemId()] - consumerInventory.stock[route.itemId()]);
        int high = Math.min(
                route.amount(),
                Math.min(
                        fleetInventory.stock[route.itemId()],
                        Math.min(controller.getFreeCapacity(consumerInventory), demand)));
        if (high <= 0) {
            return 0;
        }

        float unitPrice = controller.getEffectiveBuyPrice(consumer, route.itemId(), reputation);
        if (!Float.isFinite(unitPrice) || unitPrice <= 0f) {
            return 0;
        }

        int low = 0;
        while (low < high) {
            int candidate = low + (high - low + 1) / 2;
            long value;
            try {
                value = Money.tradeValue(unitPrice, candidate);
            } catch (IllegalArgumentException exception) {
                high = candidate - 1;
                continue;
            }
            if (value > 0L
                    && consumerWallet.canDebit(value)
                    && fleetWallet.canCredit(value)) {
                low = candidate;
            } else {
                high = candidate - 1;
            }
        }
        return low;
    }

    private MarketDirectory.StationMarket destinationMarketSnapshot(WorldSimulation world) {
        SimulationSession session = requireSession(world, route.sellSystemId());
        MarketDirectory directory = new MarketDirectory(session.getContentCatalog());
        directory.rebuild(session.getEngine().getEntities());
        MarketDirectory.StationMarket market = directory.find(route.sellStationId());
        if (market == null) {
            throw new IllegalStateException("Destination consumer market no longer exists");
        }
        return market;
    }

    private FleetPlacementState requireLocalPlacement(WorldSimulation world, StarSystemId expectedSystem) {
        FleetPlacementState placement = world.findFleet(fleetId).orElseThrow(
                () -> new IllegalStateException("Trade fleet no longer exists: " + fleetId));
        if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                || !expectedSystem.equals(placement.systemId())) {
            throw new IllegalStateException(
                    "Trade fleet is not in expected system " + expectedSystem + ": " + placement);
        }
        return placement;
    }

    private static SimulationSession requireSession(WorldSimulation world, StarSystemId systemId) {
        return world.findSession(systemId).orElseThrow(
                () -> new IllegalStateException("Missing SimulationSession: " + systemId));
    }

    private static Entity requireEntity(
            SimulationSession session,
            com.spacesim.persistence.EntityId entityId,
            String role) {
        Entity entity = session.getEntityRegistry().find(entityId);
        if (entity == null) {
            throw new IllegalStateException("Missing " + role + " entity: " + entityId);
        }
        return entity;
    }

    private void fail(String reason) {
        state = State.FAILED;
        failureReason = reason == null || reason.isBlank() ? "Unknown inter-system trade failure" : reason;
    }
}
