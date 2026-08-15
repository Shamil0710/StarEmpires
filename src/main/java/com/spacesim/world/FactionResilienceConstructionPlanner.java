package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.simulation.SimulationSession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Maps Stage-17F.5 domestic-production capacity gaps onto real controlled-system construction demand.
 *
 * <p>A capacity-gap flag is not sufficient by itself. The planner requires a positive physical stock
 * deficit at the faction's own markets in a currently controlled system and a real constructible
 * producer from the shared Stage-9 candidate selector. It remains read-only and does not create a
 * project, transfer treasury funds or fabricate materials.</p>
 */
public final class FactionResilienceConstructionPlanner {
    private FactionResilienceConstructionPlanner() {
        throw new AssertionError("Utility class");
    }

    /**
     * Finds the highest-priority currently actionable resilience construction recommendation.
     *
     * @param world authoritative world runtime
     * @param localProductionPlan current local-production plan containing explicit capacity gaps
     * @return next real construction recommendation, or empty when no gap is physically actionable
     */
    public static Optional<FactionResilienceConstructionRecommendation> recommendNext(
            WorldSimulation world,
            FactionLocalProductionPlan localProductionPlan) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "WorldSimulation not set");
        FactionLocalProductionPlan checkedPlan = Objects.requireNonNull(
                localProductionPlan, "Local production plan not set");
        long tick = checkedWorld.getAuthoritativeWorldTick();
        if (checkedPlan.observationTick() != tick) {
            throw new IllegalArgumentException("Local production plan observation tick is stale");
        }
        String factionId = checkedPlan.factionContentId();
        FactionStrategicState strategy = checkedWorld.findFactionStrategicState(factionId).orElseThrow(
                () -> new IllegalArgumentException("Faction has no strategic state: " + factionId));
        int factionRuntimeId = checkedWorld.findFactionRuntimeId(factionId).orElseThrow(
                () -> new IllegalArgumentException("Unknown faction: " + factionId));
        ContentCatalog content = checkedWorld.findSession(checkedWorld.getActiveSystemId())
                .orElseThrow(() -> new IllegalStateException("Active world session not found"))
                .getContentCatalog();

        List<StarSystemId> controlledSystems = new ArrayList<>(strategy.controlledSystems());
        controlledSystems.sort(null);
        List<FactionResilienceConstructionRecommendation> candidates = new ArrayList<>();
        for (String itemContentId : checkedPlan.capacityGapItemContentIds()) {
            ContentCatalog.ItemDefinition item = content.findItem(itemContentId);
            if (item == null) {
                throw new IllegalStateException("Capacity gap references unknown item: " + itemContentId);
            }
            for (StarSystemId systemId : controlledSystems) {
                long deficit = ownedMarketDeficit(
                        checkedWorld,
                        systemId,
                        factionRuntimeId,
                        item.runtimeId());
                if (deficit <= 0L
                        || FactionInvestmentPlanner.hasActiveProducerProject(
                                checkedWorld,
                                content,
                                factionId,
                                systemId,
                                itemContentId)) {
                    continue;
                }
                FactionProducerConstructionSelector.Candidate producer =
                        FactionProducerConstructionSelector.bestCandidate(
                                        content,
                                        factionId,
                                        itemContentId,
                                        deficit)
                                .orElse(null);
                if (producer == null) {
                    continue;
                }
                ContentCatalog.ConstructionDefinition construction = producer.station().construction();
                candidates.add(new FactionResilienceConstructionRecommendation(
                        factionId,
                        tick,
                        systemId,
                        itemContentId,
                        deficit,
                        producer.station().id(),
                        producer.outputUnitsPerCycle(),
                        producer.fundingMilliCredits(),
                        construction.buildSeconds(),
                        construction.materials(),
                        producer.expectedUtilityScore()));
            }
        }
        candidates.sort(Comparator
                .comparingLong(FactionResilienceConstructionRecommendation::ownedMarketDeficitUnits).reversed()
                .thenComparing(
                        Comparator.comparingLong(
                                FactionResilienceConstructionRecommendation::expectedUtilityScore).reversed())
                .thenComparing(FactionResilienceConstructionRecommendation::itemContentId)
                .thenComparing(FactionResilienceConstructionRecommendation::systemId)
                .thenComparing(FactionResilienceConstructionRecommendation::stationArchetypeContentId));
        return candidates.stream().findFirst();
    }

    private static long ownedMarketDeficit(
            WorldSimulation world,
            StarSystemId systemId,
            int factionRuntimeId,
            int itemRuntimeId) {
        SimulationSession session = world.findSession(systemId).orElseThrow();
        long deficit = 0L;
        for (Entity entity : session.getEngine().getEntities()) {
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            MarketComponent market = entity.getComponent(MarketComponent.class);
            InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
            if (faction == null
                    || faction.factionId != factionRuntimeId
                    || market == null
                    || inventory == null
                    || !market.isTradable(itemRuntimeId)) {
                continue;
            }
            deficit += Math.max(0L, (long) market.targetStock[itemRuntimeId] - inventory.stock[itemRuntimeId]);
        }
        return deficit;
    }
}
