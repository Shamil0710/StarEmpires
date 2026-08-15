package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.economy.Money;
import com.spacesim.simulation.SimulationSession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class FactionInvestmentPlanner {
    static final int MIN_SUSTAINED_OBSERVATIONS = 3;
    static final long INVESTMENT_COOLDOWN_TICKS = 600L;

    private FactionInvestmentPlanner() {
        throw new AssertionError("Utility class");
    }

    static Optional<InvestmentDecision> evaluateFaction(
            WorldSimulation world,
            ContentCatalog content,
            FactionEconomicPressureTracker pressureTracker,
            String factionContentId) {
        Objects.requireNonNull(world, "WorldSimulation не задан");
        Objects.requireNonNull(content, "ContentCatalog не задан");
        Objects.requireNonNull(pressureTracker, "Pressure tracker не задан");
        String factionId = Objects.requireNonNull(factionContentId, "Faction ID не задан").strip();
        FactionEconomicState economy = world.findFactionEconomicState(factionId).orElse(null);
        FactionStrategicState strategy = world.findFactionStrategicState(factionId).orElse(null);
        if (economy == null || strategy == null) {
            return Optional.empty();
        }

        long tick = world.findSession(world.getActiveSystemId()).orElseThrow().getClock().getTick();
        List<FactionEconomicPressureState> pressures = new ArrayList<>();
        for (FactionEconomicPressureState pressure : pressureTracker.snapshots()) {
            if (pressure.factionContentId().equals(factionId)
                    && pressure.bottleneckType() == EconomicBottleneckType.PRODUCTION_CAPACITY_SHORTAGE
                    && pressure.consecutiveObservations() >= MIN_SUSTAINED_OBSERVATIONS
                    && pressure.lastUnmetDemandUnits() > 0L
                    && tick >= pressure.cooldownUntilTick()
                    && strategy.controlledSystems().contains(pressure.systemId())) {
                pressures.add(pressure);
            }
        }
        pressures.sort(Comparator
                .comparingLong(FactionEconomicPressureState::lastUnmetDemandUnits).reversed()
                .thenComparing(FactionEconomicPressureState::systemId)
                .thenComparing(FactionEconomicPressureState::itemContentId));

        for (FactionEconomicPressureState pressure : pressures) {
            Optional<Candidate> candidate = bestCandidate(world, content, factionId, pressure);
            if (candidate.isEmpty()) {
                continue;
            }
            Candidate selected = candidate.orElseThrow();
            long investmentAuthorization = Math.min(
                    economy.discretionaryTreasuryMilliCredits(),
                    economy.maxConstructionInvestmentPerDecisionMilliCredits());
            if (investmentAuthorization < selected.fundingMilliCredits()) {
                continue;
            }
            Location location = chooseLocation(world, pressure.systemId());
            ConstructionProjectId projectId = world.createConstructionProject(
                    factionId,
                    selected.station().id(),
                    pressure.systemId(),
                    location.x(),
                    location.y());
            long funded = world.fundConstructionProject(projectId, selected.fundingMilliCredits());
            if (funded != selected.fundingMilliCredits()) {
                world.cancelConstructionProject(projectId);
                continue;
            }
            pressureTracker.markInvestment(
                    factionId,
                    pressure.systemId(),
                    pressure.itemContentId(),
                    tick,
                    INVESTMENT_COOLDOWN_TICKS);
            return Optional.of(new InvestmentDecision(
                    factionId,
                    pressure.systemId(),
                    pressure.itemContentId(),
                    selected.station().id(),
                    projectId,
                    funded,
                    selected.expectedUtilityScore()));
        }
        return Optional.empty();
    }

    private static Optional<Candidate> bestCandidate(
            WorldSimulation world,
            ContentCatalog content,
            String factionId,
            FactionEconomicPressureState pressure) {
        if (hasActiveProducerProject(world, content, factionId, pressure.systemId(), pressure.itemContentId())) {
            return Optional.empty();
        }
        List<Candidate> candidates = new ArrayList<>();
        for (ContentCatalog.StationArchetypeDefinition station : content.getStationArchetypes()) {
            if (station.construction() == null || station.recipeId() == null) {
                continue;
            }
            ContentCatalog.RecipeDefinition recipe = content.findRecipe(station.recipeId());
            Integer output = recipe == null ? null : recipe.outputs().get(pressure.itemContentId());
            if (output == null || output <= 0) {
                continue;
            }
            long funding = Money.fromCredits(station.construction().fundingCredits());
            long utility = utilityScore(pressure.lastUnmetDemandUnits(), output, funding);
            candidates.add(new Candidate(station, funding, utility, station.factionId().equals(factionId)));
        }
        Comparator<Candidate> utilityOrder = Comparator.comparingLong(Candidate::expectedUtilityScore).reversed();
        candidates.sort(Comparator
                .comparing(Candidate::nativeFaction).reversed()
                .thenComparing(utilityOrder)
                .thenComparing(candidate -> candidate.station().id()));
        return candidates.stream().findFirst();
    }

    private static boolean hasActiveProducerProject(
            WorldSimulation world,
            ContentCatalog content,
            String factionId,
            StarSystemId systemId,
            String itemId) {
        for (ConstructionProjectState project : world.getConstructionProjects()) {
            if (!project.ownerFactionContentId().equals(factionId)
                    || !project.systemId().equals(systemId)
                    || isTerminal(project.status())) {
                continue;
            }
            ContentCatalog.StationArchetypeDefinition station =
                    content.findStationArchetype(project.stationArchetypeContentId());
            ContentCatalog.RecipeDefinition recipe = station == null || station.recipeId() == null
                    ? null : content.findRecipe(station.recipeId());
            if (recipe != null && recipe.outputs().getOrDefault(itemId, 0) > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTerminal(ConstructionProjectStatus status) {
        return status == ConstructionProjectStatus.COMPLETED
                || status == ConstructionProjectStatus.CANCELLED
                || status == ConstructionProjectStatus.FAILED;
    }

    private static Location chooseLocation(WorldSimulation world, StarSystemId systemId) {
        SimulationSession session = world.findSession(systemId).orElseThrow();
        Entity anchor = null;
        long bestId = Long.MAX_VALUE;
        for (Entity entity : session.getEngine().getEntities()) {
            EntityIdComponent id = entity.getComponent(EntityIdComponent.class);
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            if (id != null && transform != null && entity.getComponent(MarketComponent.class) != null
                    && id.id.value() < bestId) {
                anchor = entity;
                bestId = id.id.value();
            }
        }
        if (anchor == null) {
            return new Location(1000f, 700f);
        }
        TransformComponent transform = anchor.getComponent(TransformComponent.class);
        return new Location(transform.position.x + 60f, transform.position.y + 60f);
    }

    private static long utilityScore(long unmetDemand, int outputPerCycle, long fundingMilliCredits) {
        if (unmetDemand <= 0L || outputPerCycle <= 0 || fundingMilliCredits <= 0L) {
            return 0L;
        }
        long numerator;
        try {
            numerator = Math.multiplyExact(Math.multiplyExact(unmetDemand, outputPerCycle), 1_000_000L);
        } catch (ArithmeticException exception) {
            numerator = Long.MAX_VALUE;
        }
        return numerator / fundingMilliCredits;
    }

    record InvestmentDecision(
            String factionContentId,
            StarSystemId systemId,
            String itemContentId,
            String stationArchetypeContentId,
            ConstructionProjectId projectId,
            long fundedMilliCredits,
            long expectedUtilityScore) {
    }

    private record Candidate(
            ContentCatalog.StationArchetypeDefinition station,
            long fundingMilliCredits,
            long expectedUtilityScore,
            boolean nativeFaction) {
    }

    private record Location(float x, float y) {
    }
}
