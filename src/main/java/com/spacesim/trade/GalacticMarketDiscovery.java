package com.spacesim.trade;

import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.persistence.EntityId;
import com.spacesim.world.GalacticPath;
import com.spacesim.world.GalacticPathPlanner;
import com.spacesim.world.SectorId;
import com.spacesim.world.StarSystemId;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic bounded discovery of cross-system trade candidates.
 *
 * <p>The discovery layer never scans arbitrary galaxy-wide market pairs. It first traverses the
 * immutable jump topology from the fleet's current system under explicit hop/system budgets, then
 * combines at most eight cheapest local suppliers per item with a bounded number of remote
 * consumers per visited system. The resulting shortlist is ranked deterministically and truncated
 * before it reaches {@link TradeRoutePlanner#findBestGalacticRoute(FleetTradeProfile, List)}.</p>
 *
 * <p>Stage 10B has no physical jump-gate coordinates yet. Accordingly local travel estimates cover
 * only fleet-to-supplier movement. No fictional gate or destination-local distance is invented;
 * those presentation/navigation details can be added later without changing this discovery API.</p>
 */
public final class GalacticMarketDiscovery {
    private static final int MAX_LOCAL_SUPPLIERS_PER_ITEM = 8;

    private final GalacticPathPlanner pathPlanner;
    private final GalacticMarketDiscoveryPolicy policy;

    /**
     * Creates a discovery service with explicit authoritative path timing and complexity policy.
     *
     * @param pathPlanner path planner configured from the current world
     * @param policy bounded search policy
     */
    public GalacticMarketDiscovery(
            GalacticPathPlanner pathPlanner,
            GalacticMarketDiscoveryPolicy policy) {
        this.pathPlanner = Objects.requireNonNull(pathPlanner, "GalacticPathPlanner не задан");
        this.policy = Objects.requireNonNull(policy, "GalacticMarketDiscoveryPolicy не задан");
    }

    /**
     * Discovers a deterministic bounded set of inter-system opportunities for one fleet.
     *
     * @param fleet immutable fleet planning state
     * @param originSystemId system currently containing the fleet and all eligible suppliers
     * @param index world market index
     * @return immutable result tagged with the aggregate market revision used for discovery
     */
    public Result discover(
            FleetTradeProfile fleet,
            StarSystemId originSystemId,
            GalacticMarketIndex index) {
        FleetTradeProfile checkedFleet = Objects.requireNonNull(fleet, "FleetTradeProfile не задан");
        StarSystemId origin = Objects.requireNonNull(originSystemId, "Origin StarSystemId не задан");
        GalacticMarketIndex checkedIndex = Objects.requireNonNull(index, "GalacticMarketIndex не задан");
        if (checkedIndex.topology().findSystem(origin).isEmpty()) {
            throw new IllegalArgumentException("Origin StarSystem отсутствует в topology: " + origin);
        }
        checkedIndex.rebuild();
        long revision = checkedIndex.revision();
        if (checkedFleet.routeCargoCapacity() <= 0) {
            return new Result(revision, List.of());
        }

        List<ReachableSystem> reachable = reachableSystems(origin, checkedIndex);
        MarketDirectory originDirectory = checkedIndex.directory(origin);
        ContentCatalog content = checkedIndex.world().findSession(origin).orElseThrow().getContentCatalog();
        List<RankedOpportunity> ranked = new ArrayList<>();

        for (ContentCatalog.ItemDefinition item : content.getItems()) {
            if (!checkedFleet.canPurchase(item)) {
                continue;
            }
            int itemId = item.runtimeId();
            List<MarketDirectory.StationMarket> suppliers = originDirectory.suppliers(itemId);
            int supplierLimit = Math.min(MAX_LOCAL_SUPPLIERS_PER_ITEM, suppliers.size());
            for (int supplierIndex = 0; supplierIndex < supplierLimit; supplierIndex++) {
                MarketDirectory.StationMarket supplier = suppliers.get(supplierIndex);
                if (!supplier.canTrade(checkedFleet.factionId())) {
                    continue;
                }
                LocalTravel localTravel = localTravel(checkedFleet, supplier);
                if (localTravel == null) {
                    continue;
                }
                for (ReachableSystem remote : reachable) {
                    GalacticPath path = remote.path();
                    if (path.jumpCount() <= 0 || path.jumpCount() > policy.maxJumpHops()) {
                        continue;
                    }
                    List<MarketDirectory.StationMarket> consumers =
                            checkedIndex.directory(remote.systemId()).consumers(itemId);
                    int consumerLimit = Math.min(policy.maxConsumersPerSystemPerItem(), consumers.size());
                    for (int consumerIndex = 0; consumerIndex < consumerLimit; consumerIndex++) {
                        MarketDirectory.StationMarket consumer = consumers.get(consumerIndex);
                        if (!consumer.canTrade(checkedFleet.factionId())
                                || !optimisticallyProfitable(supplier, consumer, itemId)) {
                            continue;
                        }
                        int risk = riskBasisPoints(path.jumpCount());
                        GalacticTradeOpportunity opportunity = new GalacticTradeOpportunity(
                                new SystemMarketRef(origin, supplier),
                                new SystemMarketRef(remote.systemId(), consumer),
                                itemId,
                                path,
                                localTravel.distance(),
                                localTravel.seconds(),
                                risk);
                        ranked.add(new RankedOpportunity(
                                opportunity,
                                optimisticScore(supplier, consumer, itemId, opportunity.totalExpectedSeconds())));
                    }
                }
            }
        }

        ranked.sort(RANKING);
        int resultSize = Math.min(policy.maxOpportunities(), ranked.size());
        List<GalacticTradeOpportunity> opportunities = new ArrayList<>(resultSize);
        for (int indexPosition = 0; indexPosition < resultSize; indexPosition++) {
            opportunities.add(ranked.get(indexPosition).opportunity());
        }
        return new Result(revision, List.copyOf(opportunities));
    }

    private List<ReachableSystem> reachableSystems(
            StarSystemId origin,
            GalacticMarketIndex index) {
        ArrayDeque<SystemDepth> queue = new ArrayDeque<>();
        Map<StarSystemId, Integer> depthBySystem = new HashMap<>();
        queue.addLast(new SystemDepth(origin, 0));
        depthBySystem.put(origin, 0);
        List<SystemDepth> discovered = new ArrayList<>();

        while (!queue.isEmpty()) {
            SystemDepth current = queue.removeFirst();
            if (current.depth() >= policy.maxJumpHops()) {
                continue;
            }
            for (StarSystemId neighbor : index.topology().neighbors(current.systemId())) {
                if (depthBySystem.containsKey(neighbor)) {
                    continue;
                }
                int depth = current.depth() + 1;
                depthBySystem.put(neighbor, depth);
                queue.addLast(new SystemDepth(neighbor, depth));
                discovered.add(new SystemDepth(neighbor, depth));
            }
        }

        SectorId originSector = index.topology().sectorOf(origin).orElseThrow().id();
        discovered.sort(
                Comparator.comparingInt(SystemDepth::depth)
                        .thenComparing((SystemDepth value) -> !sameSector(index, originSector, value.systemId()))
                        .thenComparing(SystemDepth::systemId));
        int systemLimit = Math.min(policy.maxSystems(), discovered.size());
        List<ReachableSystem> result = new ArrayList<>(systemLimit);
        for (int position = 0; position < systemLimit; position++) {
            SystemDepth candidate = discovered.get(position);
            GalacticPath path = pathPlanner.findPath(origin, candidate.systemId()).orElse(null);
            if (path != null && path.jumpCount() <= policy.maxJumpHops()) {
                result.add(new ReachableSystem(candidate.systemId(), path));
            }
        }
        return List.copyOf(result);
    }

    private static boolean sameSector(
            GalacticMarketIndex index,
            SectorId originSector,
            StarSystemId systemId) {
        return index.topology().sectorOf(systemId)
                .map(sector -> sector.id().equals(originSector))
                .orElse(false);
    }

    private static LocalTravel localTravel(
            FleetTradeProfile fleet,
            MarketDirectory.StationMarket supplier) {
        double distanceValue = Math.hypot(supplier.x() - fleet.x(), supplier.y() - fleet.y());
        if (!Double.isFinite(distanceValue) || distanceValue > Float.MAX_VALUE) {
            return null;
        }
        float distance = (float) distanceValue;
        if (distance == 0f) {
            return new LocalTravel(0f, 0d);
        }
        if (!(fleet.movementSpeed() > 0f)) {
            return null;
        }
        double seconds = distance / (double) fleet.movementSpeed();
        return Double.isFinite(seconds) ? new LocalTravel(distance, seconds) : null;
    }

    private static boolean optimisticallyProfitable(
            MarketDirectory.StationMarket supplier,
            MarketDirectory.StationMarket consumer,
            int itemId) {
        double minimumPurchase = supplier.sellPrice(itemId)
                * (1d - Constants.MAX_REPUTATION_PRICE_BONUS);
        double maximumSale = consumer.buyPrice(itemId)
                * (1d + Constants.MAX_REPUTATION_PRICE_BONUS);
        return Double.isFinite(minimumPurchase)
                && Double.isFinite(maximumSale)
                && maximumSale > minimumPurchase;
    }

    private static double optimisticScore(
            MarketDirectory.StationMarket supplier,
            MarketDirectory.StationMarket consumer,
            int itemId,
            double seconds) {
        double minimumPurchase = supplier.sellPrice(itemId)
                * (1d - Constants.MAX_REPUTATION_PRICE_BONUS);
        double maximumSale = consumer.buyPrice(itemId)
                * (1d + Constants.MAX_REPUTATION_PRICE_BONUS);
        double margin = Math.max(0d, maximumSale - minimumPurchase);
        return margin / Math.max(0.001d, seconds);
    }

    private int riskBasisPoints(int jumpCount) {
        long risk = (long) policy.riskPerJumpBasisPoints() * jumpCount;
        return (int) Math.min(10_000L, risk);
    }

    private static final Comparator<RankedOpportunity> RANKING =
            Comparator.comparingDouble(RankedOpportunity::score).reversed()
                    .thenComparingLong(value -> value.opportunity().jumpPath().totalJumpTicks())
                    .thenComparingInt(value -> value.opportunity().itemId())
                    .thenComparing(value -> value.opportunity().supplier().entityId())
                    .thenComparing(value -> value.opportunity().consumer().systemId())
                    .thenComparing(value -> value.opportunity().consumer().entityId());

    /**
     * Immutable discovery result carrying the exact aggregate market revision used to build it.
     *
     * @param marketRevision world market index revision at discovery time
     * @param opportunities bounded deterministic candidates
     */
    public record Result(long marketRevision, List<GalacticTradeOpportunity> opportunities) {
        /** Defensive immutable-copy constructor. */
        public Result {
            if (marketRevision < 0L) {
                throw new IllegalArgumentException("Market revision не может быть отрицательным");
            }
            opportunities = List.copyOf(Objects.requireNonNull(opportunities, "Opportunities не заданы"));
        }

        /**
         * Checks whether no market/access snapshot changed since this result was produced.
         *
         * @param index index to refresh and compare
         * @return {@code true} only when the aggregate revision remains identical
         */
        public boolean isCurrent(GalacticMarketIndex index) {
            GalacticMarketIndex checked = Objects.requireNonNull(index, "GalacticMarketIndex не задан");
            checked.rebuild();
            return marketRevision == checked.revision();
        }
    }

    private record SystemDepth(StarSystemId systemId, int depth) {
    }

    private record ReachableSystem(StarSystemId systemId, GalacticPath path) {
    }

    private record LocalTravel(float distance, double seconds) {
    }

    private record RankedOpportunity(GalacticTradeOpportunity opportunity, double score) {
    }
}
