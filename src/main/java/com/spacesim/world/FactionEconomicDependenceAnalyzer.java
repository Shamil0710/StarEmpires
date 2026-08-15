package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.FactionMarketAccessComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.economy.Money;
import com.spacesim.model.Recipe;
import com.spacesim.simulation.SimulationSession;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

/**
 * Stage-17E.7 read-only analyzer for current structural faction economic dependence.
 *
 * <p>The analyzer never invents historical trade flows. It observes current physical inventories,
 * market targets/quotes, active production inputs, legal market access and jump topology. This is
 * authoritative world-truth diagnostics with confidence {@code 10000}; Stage 19 can later publish a
 * delayed/noisy observation using the same output contract.</p>
 */
final class FactionEconomicDependenceAnalyzer {
    private static final int FULL_CONFIDENCE_BPS = 10_000;

    private FactionEconomicDependenceAnalyzer() {
        throw new AssertionError("Utility class");
    }

    static FactionEconomicDependenceDiagnostics analyze(
            WorldSimulation world,
            ContentCatalog content,
            String sourceFactionContentId,
            String partnerFactionContentId) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "WorldSimulation not set");
        ContentCatalog checkedContent = Objects.requireNonNull(content, "ContentCatalog not set");
        String sourceId = requireFaction(checkedWorld, sourceFactionContentId, "Source faction ID");
        String partnerId = requireFaction(checkedWorld, partnerFactionContentId, "Partner faction ID");
        if (sourceId.equals(partnerId)) {
            throw new IllegalArgumentException("Economic dependence requires two different factions");
        }
        int sourceRuntimeId = checkedWorld.findFactionRuntimeId(sourceId).orElseThrow();
        FactionStrategicState sourceStrategy = checkedWorld.findFactionStrategicState(sourceId).orElse(null);
        List<ObservedMarket> markets = observeMarkets(checkedWorld);

        List<FactionItemDependenceDiagnostic> rows = new ArrayList<>();
        long aggregateRequirement = 0L;
        long aggregatePartnerCoverage = 0L;
        long aggregateExportOpportunity = 0L;
        long aggregatePartnerExportOpportunity = 0L;
        long aggregatePremium = 0L;
        long aggregateUncovered = 0L;
        int uniqueCorridorCriticalItems = 0;

        for (ContentCatalog.ItemDefinition item : checkedContent.getItems()) {
            int itemId = item.runtimeId();
            long sourceTarget = 0L;
            long sourceOnHand = 0L;
            long sourceExportable = 0L;
            long partnerPhysicalSurplus = 0L;
            long partnerAccessibleSurplus = 0L;
            long alternativeAccessibleSurplus = 0L;
            long partnerAccessibleDemand = 0L;
            long otherAccessibleForeignDemand = 0L;
            long partnerBestPrice = -1L;
            long alternativeBestPrice = -1L;
            Set<StarSystemId> partnerSupplierSystems = new HashSet<>();
            Set<StarSystemId> alternativeSupplierSystems = new HashSet<>();
            Set<StarSystemId> sourceDemandSystems = new HashSet<>();
            Set<StarSystemId> sourceMarketSystems = new HashSet<>();

            for (ObservedMarket observed : markets) {
                int stock = observed.inventory().stock[itemId];
                int target = Math.max(0, observed.market().targetStock[itemId]);
                long surplus = Math.max(0L, (long) stock - target);
                long deficit = Math.max(0L, (long) target - stock);
                boolean sourceOwned = sourceId.equals(observed.ownerFactionContentId());
                boolean partnerOwned = partnerId.equals(observed.ownerFactionContentId());
                boolean accessible = sourceOwned || observed.canTrade(sourceRuntimeId);

                if (sourceOwned) {
                    sourceTarget = safeAdd(sourceTarget, target);
                    sourceOnHand = safeAdd(sourceOnHand, stock);
                    sourceExportable = safeAdd(sourceExportable, surplus);
                    sourceMarketSystems.add(observed.systemId());
                    if (deficit > 0L) {
                        sourceDemandSystems.add(observed.systemId());
                    }
                    continue;
                }
                if (partnerOwned) {
                    partnerPhysicalSurplus = safeAdd(partnerPhysicalSurplus, surplus);
                    if (accessible) {
                        partnerAccessibleSurplus = safeAdd(partnerAccessibleSurplus, surplus);
                        partnerAccessibleDemand = safeAdd(partnerAccessibleDemand, deficit);
                        if (surplus > 0L) {
                            partnerSupplierSystems.add(observed.systemId());
                            partnerBestPrice = minimumQuote(
                                    partnerBestPrice, unitSellPrice(observed.market(), itemId));
                        }
                    }
                    continue;
                }
                if (accessible) {
                    alternativeAccessibleSurplus = safeAdd(alternativeAccessibleSurplus, surplus);
                    otherAccessibleForeignDemand = safeAdd(otherAccessibleForeignDemand, deficit);
                    if (surplus > 0L) {
                        alternativeSupplierSystems.add(observed.systemId());
                        alternativeBestPrice = minimumQuote(
                                alternativeBestPrice, unitSellPrice(observed.market(), itemId));
                    }
                }
            }

            long strategicFloor = sourceStrategy == null
                    ? 0L
                    : sourceStrategy.effectiveTargetStockFloor(item.id());
            long requiredStock = Math.max(sourceTarget, strategicFloor);
            long externalRequirement = Math.max(0L, requiredStock - sourceOnHand);
            if (externalRequirement > 0L && sourceDemandSystems.isEmpty()) {
                if (sourceStrategy != null && !sourceStrategy.controlledSystems().isEmpty()) {
                    sourceDemandSystems.addAll(sourceStrategy.controlledSystems());
                } else {
                    sourceDemandSystems.addAll(sourceMarketSystems);
                }
            }
            long productionInputPerCycle = observeSourceProductionInputPerCycle(
                    checkedWorld, sourceRuntimeId, itemId);
            long bufferEndurance = productionInputPerCycle == 0L
                    ? -1L
                    : sourceOnHand / productionInputPerCycle;

            int partnerSupplyShare = shareBasisPoints(
                    partnerAccessibleSurplus,
                    safeAdd(partnerAccessibleSurplus, alternativeAccessibleSurplus));
            long coveredByPartner = Math.min(externalRequirement, partnerAccessibleSurplus);
            int partnerCoverage = shareBasisPoints(coveredByPartner, externalRequirement);
            long uncoveredAfterLoss = Math.max(0L, externalRequirement - alternativeAccessibleSurplus);
            long replacementPremium = replacementPremium(
                    coveredByPartner,
                    alternativeAccessibleSurplus,
                    partnerBestPrice,
                    alternativeBestPrice);
            int partnerDemandShare = shareBasisPoints(
                    partnerAccessibleDemand,
                    safeAdd(partnerAccessibleDemand, otherAccessibleForeignDemand));

            RouteMetric partnerRoute = routeMetric(
                    checkedWorld.getTopology(), sourceDemandSystems, partnerSupplierSystems);
            RouteMetric alternativeRoute = routeMetric(
                    checkedWorld.getTopology(), sourceDemandSystems, alternativeSupplierSystems);
            if (externalRequirement > 0L
                    && partnerAccessibleSurplus > 0L
                    && partnerRoute.uniqueShortestRoute()
                    && partnerRoute.intermediateSystems() > 0) {
                uniqueCorridorCriticalItems++;
            }

            rows.add(new FactionItemDependenceDiagnostic(
                    item.id(),
                    requiredStock,
                    sourceOnHand,
                    productionInputPerCycle,
                    bufferEndurance,
                    externalRequirement,
                    partnerPhysicalSurplus,
                    partnerAccessibleSurplus,
                    alternativeAccessibleSurplus,
                    partnerSupplyShare,
                    partnerCoverage,
                    partnerBestPrice,
                    alternativeBestPrice,
                    replacementPremium,
                    uncoveredAfterLoss,
                    sourceExportable,
                    partnerAccessibleDemand,
                    otherAccessibleForeignDemand,
                    partnerDemandShare,
                    partnerRoute.hops(),
                    alternativeRoute.hops(),
                    partnerRoute.uniqueShortestRoute(),
                    partnerRoute.intermediateSystems()));

            aggregateRequirement = safeAdd(aggregateRequirement, externalRequirement);
            aggregatePartnerCoverage = safeAdd(aggregatePartnerCoverage, coveredByPartner);
            long accessibleDemand = safeAdd(partnerAccessibleDemand, otherAccessibleForeignDemand);
            long exportOpportunity = Math.min(sourceExportable, accessibleDemand);
            aggregateExportOpportunity = safeAdd(aggregateExportOpportunity, exportOpportunity);
            aggregatePartnerExportOpportunity = safeAdd(
                    aggregatePartnerExportOpportunity,
                    Math.min(exportOpportunity, partnerAccessibleDemand));
            aggregatePremium = safeAdd(aggregatePremium, replacementPremium);
            aggregateUncovered = safeAdd(aggregateUncovered, uncoveredAfterLoss);
        }

        return new FactionEconomicDependenceDiagnostics(
                sourceId,
                partnerId,
                checkedWorld.getAuthoritativeWorldTick(),
                FULL_CONFIDENCE_BPS,
                shareBasisPoints(aggregatePartnerCoverage, aggregateRequirement),
                shareBasisPoints(aggregatePartnerExportOpportunity, aggregateExportOpportunity),
                aggregatePremium,
                aggregateUncovered,
                uniqueCorridorCriticalItems,
                rows);
    }

    private static List<ObservedMarket> observeMarkets(WorldSimulation world) {
        List<ObservedMarket> result = new ArrayList<>();
        for (StarSystemNode system : world.getTopology().systems()) {
            SimulationSession session = world.findSession(system.id()).orElseThrow();
            for (Entity entity : session.getEngine().getEntities()) {
                MarketComponent market = entity.getComponent(MarketComponent.class);
                InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
                FactionComponent faction = entity.getComponent(FactionComponent.class);
                if (market == null || inventory == null || faction == null) {
                    continue;
                }
                String owner = world.findFactionStableId(faction.factionId).orElse(null);
                if (owner == null) {
                    continue;
                }
                result.add(new ObservedMarket(
                        system.id(),
                        owner,
                        market,
                        inventory,
                        entity.getComponent(FactionMarketAccessComponent.class)));
            }
        }
        return List.copyOf(result);
    }

    private static long observeSourceProductionInputPerCycle(
            WorldSimulation world,
            int sourceRuntimeId,
            int itemId) {
        long result = 0L;
        for (StarSystemNode system : world.getTopology().systems()) {
            SimulationSession session = world.findSession(system.id()).orElseThrow();
            for (Entity entity : session.getEngine().getEntities()) {
                FactionComponent faction = entity.getComponent(FactionComponent.class);
                ProductionComponent production = entity.getComponent(ProductionComponent.class);
                Recipe recipe = production == null ? null : production.getActiveRecipe();
                if (faction == null || faction.factionId != sourceRuntimeId || recipe == null) {
                    continue;
                }
                result = safeAdd(result, recipe.getInputAmount(itemId));
            }
        }
        return result;
    }

    private static RouteMetric routeMetric(
            GalaxyTopology topology,
            Set<StarSystemId> sources,
            Set<StarSystemId> targets) {
        if (sources.isEmpty() || targets.isEmpty()) {
            return RouteMetric.unavailable();
        }
        Map<StarSystemId, Integer> distance = new HashMap<>();
        Map<StarSystemId, Integer> pathCount = new HashMap<>();
        Queue<StarSystemId> queue = new ArrayDeque<>();
        List<StarSystemId> sortedSources = new ArrayList<>(sources);
        sortedSources.sort(null);
        for (StarSystemId source : sortedSources) {
            distance.put(source, 0);
            pathCount.put(source, Math.min(2, pathCount.getOrDefault(source, 0) + 1));
            queue.add(source);
        }
        while (!queue.isEmpty()) {
            StarSystemId current = queue.remove();
            int nextDistance = distance.get(current) + 1;
            for (StarSystemId neighbor : topology.neighbors(current)) {
                Integer known = distance.get(neighbor);
                if (known == null) {
                    distance.put(neighbor, nextDistance);
                    pathCount.put(neighbor, pathCount.get(current));
                    queue.add(neighbor);
                } else if (known == nextDistance) {
                    pathCount.put(neighbor, Math.min(
                            2,
                            pathCount.get(neighbor) + pathCount.get(current)));
                }
            }
        }

        int best = Integer.MAX_VALUE;
        int shortestPaths = 0;
        for (StarSystemId target : targets) {
            Integer hops = distance.get(target);
            if (hops == null) {
                continue;
            }
            if (hops < best) {
                best = hops;
                shortestPaths = pathCount.getOrDefault(target, 1);
            } else if (hops == best) {
                shortestPaths = Math.min(2, shortestPaths + pathCount.getOrDefault(target, 1));
            }
        }
        if (best == Integer.MAX_VALUE) {
            return RouteMetric.unavailable();
        }
        boolean unique = shortestPaths == 1;
        return new RouteMetric(best, unique, unique ? Math.max(0, best - 1) : 0);
    }

    private static long replacementPremium(
            long partnerCoveredUnits,
            long alternativeSupplyUnits,
            long partnerPrice,
            long alternativePrice) {
        if (partnerCoveredUnits <= 0L
                || alternativeSupplyUnits <= 0L
                || partnerPrice < 0L
                || alternativePrice < 0L
                || alternativePrice <= partnerPrice) {
            return 0L;
        }
        long replaceableUnits = Math.min(partnerCoveredUnits, alternativeSupplyUnits);
        try {
            return Math.multiplyExact(replaceableUnits, alternativePrice - partnerPrice);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static long unitSellPrice(MarketComponent market, int itemId) {
        if (!market.isTradable(itemId)) {
            return -1L;
        }
        float price = market.sellPrices[itemId];
        if (!Float.isFinite(price) || price <= 0f) {
            return -1L;
        }
        try {
            return Money.tradeValue(price, 1);
        } catch (IllegalArgumentException exception) {
            return -1L;
        }
    }

    private static long minimumQuote(long current, long candidate) {
        if (candidate < 0L) {
            return current;
        }
        return current < 0L ? candidate : Math.min(current, candidate);
    }

    private static int shareBasisPoints(long numerator, long denominator) {
        if (numerator <= 0L || denominator <= 0L) {
            return 0;
        }
        long bounded = Math.min(numerator, denominator);
        long whole = (bounded / denominator) * 10_000L;
        long remainder = bounded % denominator;
        long fractional;
        try {
            fractional = Math.multiplyExact(remainder, 10_000L) / denominator;
        } catch (ArithmeticException exception) {
            fractional = (long) (((double) remainder / (double) denominator) * 10_000d);
        }
        return (int) Math.min(10_000L, whole + fractional);
    }

    private static long safeAdd(long first, long second) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static String requireFaction(WorldSimulation world, String value, String label) {
        String normalized = Objects.requireNonNull(value, label + " not set").strip();
        if (normalized.isEmpty() || world.findFactionRuntimeId(normalized).isEmpty()) {
            throw new IllegalArgumentException(label + " is unknown: " + normalized);
        }
        return normalized;
    }

    private record ObservedMarket(
            StarSystemId systemId,
            String ownerFactionContentId,
            MarketComponent market,
            InventoryComponent inventory,
            FactionMarketAccessComponent access) {
        private boolean canTrade(int runtimeFactionId) {
            return access == null || access.canTrade(runtimeFactionId);
        }
    }

    private record RouteMetric(int hops, boolean uniqueShortestRoute, int intermediateSystems) {
        private static RouteMetric unavailable() {
            return new RouteMetric(-1, false, 0);
        }
    }
}
