package com.spacesim.trade;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.ReputationComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.GalacticPathPlanner;
import com.spacesim.world.WorldRouteRedundancyPolicy;
import com.spacesim.world.WorldSimulation;
import com.spacesim.world.WorldSupplierDiversificationPolicy;

import java.util.Objects;
import java.util.Optional;

/**
 * Canonical Stage-10D/E facade from a world fleet to an executable inter-system trade job.
 *
 * <p>The service owns one shared {@link GalacticMarketIndex}; repeated planning therefore reuses
 * per-system immutable market snapshots and their revisions. It combines bounded discovery with the
 * world's canonical {@link TradeRoutePlanner}, then applies Stage-17F.5 supplier diversification and
 * physical edge-disjoint route redundancy before returning an {@link InterSystemTradeJob} that
 * performs the same ordinary transactions and jump handoffs.</p>
 */
public final class InterSystemTradeService {
    private final WorldSimulation world;
    private final GalacticMarketIndex marketIndex;
    private final GalacticMarketDiscovery discovery;
    private final FactionResilientGalacticTradePlanner routePlanner;

    /**
     * Creates a service using explicit discovery and scoring policies.
     *
     * @param world authoritative multi-system runtime
     * @param discoveryPolicy bounded candidate-search policy
     * @param scoringMode economic route ranking mode
     */
    public InterSystemTradeService(
            WorldSimulation world,
            GalacticMarketDiscoveryPolicy discoveryPolicy,
            TradeRoutePlanner.ScoringMode scoringMode) {
        this.world = Objects.requireNonNull(world, "WorldSimulation не задан");
        GalacticMarketDiscoveryPolicy checkedDiscoveryPolicy = Objects.requireNonNull(
                discoveryPolicy, "Discovery policy не задан");
        GalacticPathPlanner pathPlanner = world.createGalacticPathPlanner();
        this.marketIndex = new GalacticMarketIndex(world);
        this.discovery = new GalacticMarketDiscovery(pathPlanner, checkedDiscoveryPolicy);
        TradeRoutePlanner.ScoringMode checkedScoringMode = Objects.requireNonNull(
                scoringMode, "ScoringMode не задан");
        TradeRoutePlanner economicPlanner = world.createGalacticTradeRoutePlanner(checkedScoringMode);
        this.routePlanner = new FactionResilientGalacticTradePlanner(
                economicPlanner,
                checkedScoringMode,
                new WorldSupplierDiversificationPolicy(world),
                pathPlanner,
                checkedDiscoveryPolicy.maxJumpHops(),
                jumpCount -> routeRiskBasisPoints(checkedDiscoveryPolicy, jumpCount),
                new WorldRouteRedundancyPolicy(world));
    }

    /**
     * Creates a service with the default bounded horizon and profit-per-second scoring.
     *
     * @param world authoritative multi-system runtime
     */
    public InterSystemTradeService(WorldSimulation world) {
        this(world, GalacticMarketDiscoveryPolicy.DEFAULT, TradeRoutePlanner.ScoringMode.PROFIT_PER_SECOND);
    }

    /**
     * Plans the best currently valid inter-system cargo job for a local world fleet.
     *
     * <p>Ordinary economics remains the baseline. A faction may choose a less concentrated physical
     * supplier and/or an edge-disjoint physical jump path only when Stage-17F.5 diagnostics recommend
     * the resilience action and the final real expected-profit sacrifice remains within its measured
     * budget. Execution itself is unchanged.</p>
     *
     * @param fleetId stable fleet identity
     * @return executable job or empty when no bounded profitable candidate exists
     */
    public Optional<InterSystemTradeJob> plan(FleetId fleetId) {
        FleetId checkedFleetId = Objects.requireNonNull(fleetId, "FleetId не задан");
        FleetPlacementState placement = world.findFleet(checkedFleetId).orElse(null);
        if (placement == null || placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
            return Optional.empty();
        }
        SimulationSession session = world.findSession(placement.systemId()).orElse(null);
        if (session == null) {
            return Optional.empty();
        }
        Entity fleet = session.getEntityRegistry().find(placement.localEntityId());
        FleetTradeProfile profile = captureProfile(fleet);
        if (profile == null || profile.routeCargoCapacity() <= 0) {
            return Optional.empty();
        }
        GalacticMarketDiscovery.Result result = discovery.discover(
                profile, placement.systemId(), marketIndex);
        return routePlanner.findBestGalacticRoute(profile, result.opportunities())
                .map(route -> new InterSystemTradeJob(checkedFleetId, route));
    }

    /** @return shared market index and its current aggregate revision */
    public GalacticMarketIndex marketIndex() {
        return marketIndex;
    }

    private static int routeRiskBasisPoints(
            GalacticMarketDiscoveryPolicy policy,
            int jumpCount) {
        if (jumpCount < 0) {
            throw new IllegalArgumentException("Jump count cannot be negative");
        }
        long risk = (long) policy.riskPerJumpBasisPoints() * jumpCount;
        return (int) Math.min(10_000L, risk);
    }

    private static FleetTradeProfile captureProfile(Entity fleet) {
        if (fleet == null) {
            return null;
        }
        TransformComponent transform = fleet.getComponent(TransformComponent.class);
        InventoryComponent inventory = fleet.getComponent(InventoryComponent.class);
        WalletComponent wallet = fleet.getComponent(WalletComponent.class);
        TradeAIComponent ai = fleet.getComponent(TradeAIComponent.class);
        if (transform == null || inventory == null || wallet == null || ai == null) {
            return null;
        }

        float[] reputation = new float[Constants.MAX_FACTIONS];
        ReputationComponent reputationComponent = fleet.getComponent(ReputationComponent.class);
        if (reputationComponent != null) {
            for (int factionId = 0; factionId < Constants.MAX_FACTIONS; factionId++) {
                reputation[factionId] = reputationComponent.getReputation(factionId);
            }
        }
        ShipComponent ship = fleet.getComponent(ShipComponent.class);
        FactionComponent faction = fleet.getComponent(FactionComponent.class);
        return new FleetTradeProfile(
                transform.position.x,
                transform.position.y,
                ai.movementSpeed,
                wallet.getBalanceMilliCredits(),
                inventory.capacity,
                inventory.getTotalStock(),
                ai.cargoSpace,
                ai.specializedItem,
                ship != null,
                ship == null ? null : ship.type,
                faction == null ? -1 : faction.factionId,
                inventory.stock,
                reputation);
    }
}
