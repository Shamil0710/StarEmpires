package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.simulation.SimulationSession;

import java.util.Objects;
import java.util.Optional;

/**
 * Explicit Stage-17F.5 command that turns one actionable resilience capacity gap into ordinary construction.
 *
 * <p>The service has no alternate project or funding model. It delegates legal authorization to
 * {@link WorldSimulation#createConstructionProject(String, String, StarSystemId, float, float)} and
 * treasury movement to {@link WorldSimulation#fundConstructionProject(ConstructionProjectId, long)}.
 * Construction materials and build time therefore remain the same physical Stage-9/16 requirements.</p>
 */
public final class FactionResilienceConstructionService {
    private FactionResilienceConstructionService() {
        throw new AssertionError("Utility class");
    }

    /**
     * Starts the highest-priority currently actionable resilience project if fiscal policy permits it.
     *
     * <p>No project is created when the measured gap vanished, the system is no longer controlled,
     * another producer project already covers the gap, treasury reserve protects the required funds,
     * or the per-decision construction authorization is too small.</p>
     *
     * @param world authoritative world runtime
     * @param localProductionPlan current same-tick local-production plan
     * @return started physical project and its recommendation, or empty when no funded action is allowed
     */
    public static Optional<Result> startNext(
            WorldSimulation world,
            FactionLocalProductionPlan localProductionPlan) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "WorldSimulation not set");
        FactionResilienceConstructionRecommendation recommendation =
                FactionResilienceConstructionPlanner.recommendNext(checkedWorld, localProductionPlan)
                        .orElse(null);
        if (recommendation == null) {
            return Optional.empty();
        }

        FactionEconomicState economy = checkedWorld
                .findFactionEconomicState(recommendation.factionContentId())
                .orElseThrow(() -> new IllegalStateException(
                        "Faction has no economic state: " + recommendation.factionContentId()));
        long spendableTreasury = Math.max(
                0L,
                economy.treasuryMilliCredits() - economy.treasuryReserveFloorMilliCredits());
        if (recommendation.fundingMilliCredits() > spendableTreasury
                || recommendation.fundingMilliCredits()
                > economy.maxConstructionInvestmentPerDecisionMilliCredits()) {
            return Optional.empty();
        }

        int factionRuntimeId = checkedWorld.findFactionRuntimeId(recommendation.factionContentId())
                .orElseThrow(() -> new IllegalStateException(
                        "Unknown faction runtime identity: " + recommendation.factionContentId()));
        Location location = chooseLocation(
                checkedWorld,
                recommendation.systemId(),
                factionRuntimeId);
        ConstructionProjectId projectId = checkedWorld.createConstructionProject(
                recommendation.factionContentId(),
                recommendation.stationArchetypeContentId(),
                recommendation.systemId(),
                location.x(),
                location.y());
        long funded = checkedWorld.fundConstructionProject(
                projectId,
                recommendation.fundingMilliCredits());
        if (funded != recommendation.fundingMilliCredits()) {
            checkedWorld.cancelConstructionProject(projectId);
            return Optional.empty();
        }
        ConstructionProjectState project = checkedWorld.findConstructionProject(projectId).orElseThrow(
                () -> new IllegalStateException("Started resilience construction project disappeared: " + projectId));
        return Optional.of(new Result(recommendation, project));
    }

    private static Location chooseLocation(
            WorldSimulation world,
            StarSystemId systemId,
            int factionRuntimeId) {
        SimulationSession session = world.findSession(systemId).orElseThrow();
        Entity anchor = null;
        long bestId = Long.MAX_VALUE;
        for (Entity entity : session.getEngine().getEntities()) {
            EntityIdComponent id = entity.getComponent(EntityIdComponent.class);
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            if (id != null
                    && transform != null
                    && faction != null
                    && faction.factionId == factionRuntimeId
                    && entity.getComponent(MarketComponent.class) != null
                    && id.id.value() < bestId) {
                anchor = entity;
                bestId = id.id.value();
            }
        }
        if (anchor == null) {
            throw new IllegalStateException(
                    "Resilience construction deficit has no owned market anchor in " + systemId);
        }
        TransformComponent transform = anchor.getComponent(TransformComponent.class);
        return new Location(transform.position.x + 60f, transform.position.y + 60f);
    }

    /**
     * Result of one ordinary funded resilience construction command.
     *
     * @param recommendation measured recommendation that triggered the command
     * @param project synchronized physical project snapshot after treasury funding
     */
    public record Result(
            FactionResilienceConstructionRecommendation recommendation,
            ConstructionProjectState project) {

        /**
         * Validates one command result.
         *
         * @param recommendation triggering recommendation
         * @param project funded construction project
         */
        public Result {
            Objects.requireNonNull(recommendation, "Construction recommendation not set");
            Objects.requireNonNull(project, "Construction project not set");
            if (!project.ownerFactionContentId().equals(recommendation.factionContentId())
                    || !project.systemId().equals(recommendation.systemId())
                    || !project.stationArchetypeContentId().equals(
                            recommendation.stationArchetypeContentId())) {
                throw new IllegalArgumentException("Construction result does not match recommendation");
            }
        }
    }

    private record Location(float x, float y) {
    }
}
