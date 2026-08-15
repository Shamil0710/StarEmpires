package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ContentCatalog;
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

        long spendableTreasury = Math.max(
                0L,
                economy.treasuryMilliCredits() - economy.treasuryReserveFloorMilliCredits());
        for (FactionEconomicPressureState pressure : pressures) {
            if (hasActiveProducerProject(world, content, factionId, pressure.systemId(), pressure.itemContentId())) {
                continue;
            }
            Optional<FactionProducerConstructionSelector.Candidate> candidate =
                    FactionProducerConstructionSelector.bestCandidate(
                            content,
                            factionId,
                            pressure.itemContentId(),
                            pressure.lastUnmetDemandUnits());
            if (candidate.isEmpty()) {
                continue;
            }
            FactionProducerConstructionSelector.Candidate selected = candidate.orElseThrow();
            if (selected.fundingMilliCredits() > spendableTreasury
                    || selected.fundingMilliCredits()
                    > economy.maxConstructionInvestmentPerDecisionMilliCredits()) {
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

    static boolean hasActiveProducerProject(
            WorldSimulation world,
            ContentCatalog content,
            String factionId,
            StarSystemId systemId,
            String itemId) {
        for (ConstructionProjectState project : world.getConstructionProjects()) {
            if (!Objects.equals(project.ownerFactionContentId(), factionId)
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

    record InvestmentDecision(
            String factionContentId,
            StarSystemId systemId,
            String itemContentId,
            String stationArchetypeContentId,
            ConstructionProjectId projectId,
            long fundedMilliCredits,
            long expectedUtilityScore) {
    }

    private record Location(float x, float y) {
    }
}
