package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.simulation.SimulationSession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Maps Stage-17F.5 local-production resilience intent onto already owned physical production capacity.
 *
 * <p>The planner is read-only. A station archetype is considered capable only when the faction owns a
 * live persistent entity with both {@link ArchetypeComponent} and {@link ProductionComponent}, and the
 * archetype's canonical catalog recipe actually outputs the critical item. Arbitrary cross-archetype
 * retool capability is deliberately not inferred before a future facility-capability model exists.</p>
 */
public final class FactionLocalProductionPlanner {
    private FactionLocalProductionPlanner() {
        throw new AssertionError("Utility class");
    }

    /**
     * Derives current resilience intent and maps it to currently owned canonical production capacity.
     *
     * @param world authoritative world runtime
     * @param factionContentId stable authored or world-defined faction ID
     * @return immutable local-production plan
     */
    public static FactionLocalProductionPlan analyze(
            WorldSimulation world,
            String factionContentId) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "WorldSimulation not set");
        return analyze(checkedWorld, FactionResiliencePlanner.analyze(checkedWorld, factionContentId));
    }

    /**
     * Maps an already measured resilience snapshot to live physical production capacity.
     *
     * <p>This overload is useful when one strategic planning pass already owns a resilience snapshot;
     * it rejects stale or cross-faction input instead of silently mixing observations.</p>
     *
     * @param world authoritative world runtime
     * @param resiliencePlan current resilience snapshot for the same world/faction
     * @return immutable local-production plan
     */
    public static FactionLocalProductionPlan analyze(
            WorldSimulation world,
            FactionResiliencePlan resiliencePlan) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "WorldSimulation not set");
        FactionResiliencePlan checkedPlan = Objects.requireNonNull(resiliencePlan, "Resilience plan not set");
        if (checkedPlan.observationTick() != checkedWorld.getAuthoritativeWorldTick()) {
            throw new IllegalArgumentException("Resilience plan observation tick is stale");
        }
        String factionId = checkedPlan.factionContentId();
        int factionRuntimeId = checkedWorld.findFactionRuntimeId(factionId).orElseThrow(
                () -> new IllegalArgumentException("Unknown faction: " + factionId));
        checkedWorld.findFactionStrategicState(factionId).orElseThrow(
                () -> new IllegalArgumentException("Faction has no strategic state: " + factionId));

        ContentCatalog content = checkedWorld.findSession(checkedWorld.getActiveSystemId())
                .orElseThrow(() -> new IllegalStateException("Active world session not found"))
                .getContentCatalog();
        Set<String> ownedProductionArchetypes = ownedProductionArchetypes(checkedWorld, factionRuntimeId);

        List<FactionLocalProductionRecommendation> recommendations = new ArrayList<>();
        List<String> capacityGaps = new ArrayList<>();
        for (FactionResilienceItemDecision item : checkedPlan.items()) {
            if (!item.localProductionRecommended()) {
                continue;
            }
            Candidate best = null;
            for (String archetypeId : ownedProductionArchetypes) {
                ContentCatalog.StationArchetypeDefinition archetype = content.findStationArchetype(archetypeId);
                if (archetype == null || archetype.recipeId() == null) {
                    continue;
                }
                ContentCatalog.RecipeDefinition recipe = content.findRecipe(archetype.recipeId());
                if (recipe == null) {
                    throw new IllegalStateException(
                            "Owned production archetype references unknown recipe: " + archetype.recipeId());
                }
                int output = recipe.outputs().getOrDefault(item.itemContentId(), 0);
                if (output <= 0) {
                    continue;
                }
                Candidate candidate = new Candidate(
                        item.itemContentId(),
                        archetype.id(),
                        recipe.id(),
                        output,
                        recipe.durationSeconds());
                if (best == null || candidate.compareTo(best) < 0) {
                    best = candidate;
                }
            }
            if (best == null) {
                capacityGaps.add(item.itemContentId());
            } else {
                recommendations.add(best.toRecommendation());
            }
        }
        return new FactionLocalProductionPlan(
                factionId,
                checkedPlan.observationTick(),
                recommendations,
                capacityGaps);
    }

    private static Set<String> ownedProductionArchetypes(
            WorldSimulation world,
            int factionRuntimeId) {
        TreeSet<String> result = new TreeSet<>();
        List<StarSystemNode> systems = new ArrayList<>(world.getTopology().systems());
        systems.sort(Comparator.comparing(StarSystemNode::id));
        for (StarSystemNode system : systems) {
            SimulationSession session = world.findSession(system.id()).orElseThrow();
            for (Entity entity : session.getEngine().getEntities()) {
                FactionComponent faction = entity.getComponent(FactionComponent.class);
                ArchetypeComponent archetype = entity.getComponent(ArchetypeComponent.class);
                ProductionComponent production = entity.getComponent(ProductionComponent.class);
                EntityIdComponent identity = entity.getComponent(EntityIdComponent.class);
                if (faction != null
                        && faction.factionId == factionRuntimeId
                        && archetype != null
                        && production != null
                        && identity != null) {
                    result.add(archetype.contentId);
                }
            }
        }
        return result;
    }

    private record Candidate(
            String itemContentId,
            String stationArchetypeContentId,
            String recipeContentId,
            int outputUnitsPerCycle,
            float durationSeconds)
            implements Comparable<Candidate> {
        @Override
        public int compareTo(Candidate other) {
            double rate = outputUnitsPerCycle / (double) durationSeconds;
            double otherRate = other.outputUnitsPerCycle / (double) other.durationSeconds;
            int rateOrder = Double.compare(otherRate, rate);
            if (rateOrder != 0) {
                return rateOrder;
            }
            int archetype = stationArchetypeContentId.compareTo(other.stationArchetypeContentId);
            return archetype != 0 ? archetype : recipeContentId.compareTo(other.recipeContentId);
        }

        private FactionLocalProductionRecommendation toRecommendation() {
            return new FactionLocalProductionRecommendation(
                    itemContentId,
                    stationArchetypeContentId,
                    recipeContentId,
                    outputUnitsPerCycle,
                    durationSeconds);
        }
    }
}
