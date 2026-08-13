package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.economy.Money;
import com.spacesim.simulation.SimulationSession;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Pure-decision Stage-11A analyzer that ranks spatial expansion opportunities from live world data.
 *
 * <p>The analyzer does not create projects, move fleets or change territory. It only measures the
 * current world and returns explainable candidates for the future persistent Stage-11B plan.</p>
 */
public final class FactionExpansionOpportunityAnalyzer {
    private static final int NORMALIZED_SCALE = 10_000;

    private FactionExpansionOpportunityAnalyzer() {
        throw new AssertionError("Utility class");
    }

    /**
     * Evaluates bounded reachable systems for one faction.
     *
     * @param world authoritative multi-system world
     * @param content canonical data-driven content catalog
     * @param factionContentId faction evaluating expansion
     * @param policy explicit bounded scoring policy
     * @return immutable deterministic opportunities sorted best-first
     */
    public static List<ExpansionOpportunity> analyze(
            WorldSimulation world,
            ContentCatalog content,
            String factionContentId,
            ExpansionOpportunityPolicy policy) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "WorldSimulation not set");
        ContentCatalog checkedContent = Objects.requireNonNull(content, "ContentCatalog not set");
        ExpansionOpportunityPolicy checkedPolicy = Objects.requireNonNull(policy, "Expansion policy not set");
        String factionId = Objects.requireNonNull(factionContentId, "Faction ID not set").strip();
        FactionStrategicState strategy = checkedWorld.findFactionStrategicState(factionId).orElse(null);
        FactionEconomicState economy = checkedWorld.findFactionEconomicState(factionId).orElse(null);
        if (strategy == null || economy == null || strategy.controlledSystems().isEmpty()) {
            return List.of();
        }

        Anchor anchor = selectAnchor(checkedContent, factionId);
        if (anchor == null || economy.treasuryMilliCredits() < anchor.fundingMilliCredits()) {
            return List.of();
        }

        GalacticPathPlanner pathPlanner = checkedWorld.createGalacticPathPlanner();
        List<RawCandidate> raw = new ArrayList<>();
        for (StarSystemNode target : checkedWorld.getTopology().systems()) {
            if (strategy.controls(target.id())) {
                continue;
            }
            SourcePath sourcePath = nearestControlledPath(
                    strategy.controlledSystems(), target.id(), pathPlanner, checkedPolicy.maxJumpHops());
            if (sourcePath == null) {
                continue;
            }
            SystemMetrics metrics = measureSystem(checkedWorld.findSession(target.id()).orElseThrow());
            String controller = checkedWorld.controllingFaction(target.id()).orElse("");
            int pressure = hostilePressure(checkedWorld, strategy, target.id(), controller);
            raw.add(new RawCandidate(
                    sourcePath.sourceSystemId(),
                    target.id(),
                    controller,
                    sourcePath.path(),
                    anchor,
                    metrics,
                    pressure));
        }
        if (raw.isEmpty()) {
            return List.of();
        }

        MetricsMaxima maxima = maxima(raw);
        List<ExpansionOpportunity> ranked = new ArrayList<>(raw.size());
        for (RawCandidate candidate : raw) {
            long score = score(candidate, maxima, checkedPolicy);
            ranked.add(new ExpansionOpportunity(
                    factionId,
                    candidate.sourceSystemId(),
                    candidate.targetSystemId(),
                    candidate.controllerFactionContentId(),
                    candidate.path(),
                    candidate.anchor().archetypeContentId(),
                    candidate.anchor().fundingMilliCredits(),
                    candidate.metrics().remainingMineableUnits(),
                    candidate.metrics().unmetDemandUnits(),
                    candidate.metrics().marketCount(),
                    candidate.hostileNeighborPressure(),
                    score));
        }
        ranked.sort(Comparator
                .comparingLong(ExpansionOpportunity::utilityScore).reversed()
                .thenComparingLong(value -> value.path().totalJumpTicks())
                .thenComparing(ExpansionOpportunity::targetSystemId));
        int size = Math.min(checkedPolicy.maxCandidates(), ranked.size());
        return List.copyOf(ranked.subList(0, size));
    }

    /**
     * Evaluates with the default Stage-11A policy.
     *
     * @param world authoritative multi-system world
     * @param content canonical content catalog
     * @param factionContentId evaluating faction
     * @return immutable deterministic opportunities sorted best-first
     */
    public static List<ExpansionOpportunity> analyze(
            WorldSimulation world,
            ContentCatalog content,
            String factionContentId) {
        return analyze(world, content, factionContentId, ExpansionOpportunityPolicy.DEFAULT);
    }

    private static Anchor selectAnchor(ContentCatalog content, String factionId) {
        List<Anchor> candidates = new ArrayList<>();
        for (ContentCatalog.StationArchetypeDefinition station : content.getStationArchetypes()) {
            if (station.construction() == null || station.construction().fundingCredits() <= 0d) {
                continue;
            }
            long funding = Money.fromCredits(station.construction().fundingCredits());
            candidates.add(new Anchor(station.id(), funding, station.factionId().equals(factionId)));
        }
        candidates.sort(Comparator
                .comparing(Anchor::nativeFaction).reversed()
                .thenComparingLong(Anchor::fundingMilliCredits)
                .thenComparing(Anchor::archetypeContentId));
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private static SourcePath nearestControlledPath(
            List<StarSystemId> controlledSystems,
            StarSystemId target,
            GalacticPathPlanner pathPlanner,
            int maxJumpHops) {
        SourcePath best = null;
        for (StarSystemId source : controlledSystems) {
            GalacticPath path = pathPlanner.findPath(source, target).orElse(null);
            if (path == null || path.jumpCount() <= 0 || path.jumpCount() > maxJumpHops) {
                continue;
            }
            SourcePath candidate = new SourcePath(source, path);
            if (best == null || SOURCE_ORDER.compare(candidate, best) < 0) {
                best = candidate;
            }
        }
        return best;
    }

    private static SystemMetrics measureSystem(SimulationSession session) {
        long resources = 0L;
        long unmetDemand = 0L;
        int markets = 0;
        for (Entity entity : session.getEngine().getEntities()) {
            AsteroidComponent asteroid = entity.getComponent(AsteroidComponent.class);
            if (asteroid != null && asteroid.remainingResource > 0L) {
                resources = saturatedAdd(resources, asteroid.remainingResource);
            }
            MarketComponent market = entity.getComponent(MarketComponent.class);
            InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
            if (market == null || inventory == null) {
                continue;
            }
            markets++;
            int itemCount = Math.min(market.targetStock.length, inventory.stock.length);
            for (int itemId = 0; itemId < itemCount; itemId++) {
                if (!market.isTradable(itemId)) {
                    continue;
                }
                int missing = market.targetStock[itemId] - inventory.stock[itemId];
                if (missing > 0) {
                    unmetDemand = saturatedAdd(unmetDemand, missing);
                }
            }
        }
        return new SystemMetrics(resources, unmetDemand, markets);
    }

    private static int hostilePressure(
            WorldSimulation world,
            FactionStrategicState strategy,
            StarSystemId target,
            String targetController) {
        int pressure = relationPressure(strategy, targetController);
        for (StarSystemId neighbor : world.getTopology().neighbors(target)) {
            String controller = world.controllingFaction(neighbor).orElse("");
            if (controller.equals(strategy.factionContentId())) {
                continue;
            }
            pressure = Math.min(10_000, pressure + relationPressure(strategy, controller));
        }
        return pressure;
    }

    private static int relationPressure(FactionStrategicState strategy, String otherFaction) {
        if (otherFaction == null || otherFaction.isBlank() || otherFaction.equals(strategy.factionContentId())) {
            return 0;
        }
        return Math.max(0, -strategy.relationTo(otherFaction));
    }

    private static MetricsMaxima maxima(List<RawCandidate> candidates) {
        long maxResources = 0L;
        long maxDemand = 0L;
        int maxMarkets = 0;
        long minTicks = Long.MAX_VALUE;
        long minFunding = Long.MAX_VALUE;
        int maxThreat = 0;
        for (RawCandidate candidate : candidates) {
            maxResources = Math.max(maxResources, candidate.metrics().remainingMineableUnits());
            maxDemand = Math.max(maxDemand, candidate.metrics().unmetDemandUnits());
            maxMarkets = Math.max(maxMarkets, candidate.metrics().marketCount());
            minTicks = Math.min(minTicks, candidate.path().totalJumpTicks());
            minFunding = Math.min(minFunding, candidate.anchor().fundingMilliCredits());
            maxThreat = Math.max(maxThreat, candidate.hostileNeighborPressure());
        }
        return new MetricsMaxima(maxResources, maxDemand, maxMarkets, minTicks, minFunding, maxThreat);
    }

    private static long score(
            RawCandidate candidate,
            MetricsMaxima maxima,
            ExpansionOpportunityPolicy policy) {
        int resource = normalized(candidate.metrics().remainingMineableUnits(), maxima.maxResources());
        int demand = normalized(candidate.metrics().unmetDemandUnits(), maxima.maxDemand());
        int markets = normalized(candidate.metrics().marketCount(), maxima.maxMarkets());
        int proximity = inverseNormalized(maxima.minJumpTicks(), candidate.path().totalJumpTicks());
        int cost = inverseNormalized(maxima.minFundingMilliCredits(), candidate.anchor().fundingMilliCredits());
        int threat = normalized(candidate.hostileNeighborPressure(), maxima.maxThreat());

        long weighted = (long) resource * policy.resourceWeight()
                + (long) demand * policy.demandWeight()
                + (long) markets * policy.marketNetworkWeight()
                + (long) proximity * policy.proximityWeight()
                + (long) cost * policy.constructionCostWeight();
        long score = weighted / policy.benefitWeightSum();
        score -= (long) threat * policy.threatPenaltyWeight() / policy.benefitWeightSum();
        score = Math.max(0L, score);
        if (!candidate.controllerFactionContentId().isEmpty()) {
            score = score * (10_000L - policy.foreignControlPenaltyBasisPoints()) / 10_000L;
        }
        return score;
    }

    private static int normalized(long value, long maximum) {
        if (value <= 0L || maximum <= 0L) {
            return 0;
        }
        BigInteger numerator = BigInteger.valueOf(value).multiply(BigInteger.valueOf(NORMALIZED_SCALE));
        return numerator.divide(BigInteger.valueOf(maximum)).min(BigInteger.valueOf(NORMALIZED_SCALE)).intValue();
    }

    private static int inverseNormalized(long minimum, long value) {
        if (minimum <= 0L || value <= 0L) {
            return 0;
        }
        BigInteger numerator = BigInteger.valueOf(minimum).multiply(BigInteger.valueOf(NORMALIZED_SCALE));
        return numerator.divide(BigInteger.valueOf(value)).min(BigInteger.valueOf(NORMALIZED_SCALE)).intValue();
    }

    private static long saturatedAdd(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static final Comparator<SourcePath> SOURCE_ORDER = Comparator
            .comparingLong((SourcePath value) -> value.path().totalJumpTicks())
            .thenComparingInt(value -> value.path().jumpCount())
            .thenComparing(SourcePath::sourceSystemId);

    private record Anchor(String archetypeContentId, long fundingMilliCredits, boolean nativeFaction) {
    }

    private record SourcePath(StarSystemId sourceSystemId, GalacticPath path) {
    }

    private record SystemMetrics(long remainingMineableUnits, long unmetDemandUnits, int marketCount) {
    }

    private record RawCandidate(
            StarSystemId sourceSystemId,
            StarSystemId targetSystemId,
            String controllerFactionContentId,
            GalacticPath path,
            Anchor anchor,
            SystemMetrics metrics,
            int hostileNeighborPressure) {
    }

    private record MetricsMaxima(
            long maxResources,
            long maxDemand,
            int maxMarkets,
            long minJumpTicks,
            long minFundingMilliCredits,
            int maxThreat) {
    }
}
