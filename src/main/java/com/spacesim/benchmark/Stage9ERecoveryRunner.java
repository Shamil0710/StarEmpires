package com.spacesim.benchmark;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.EntityId;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.ConstructionProjectState;
import com.spacesim.world.ConstructionProjectStatus;
import com.spacesim.world.FactionEconomicPressureState;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldSimulation;

import java.util.Map;

final class Stage9ERecoveryRunner {
    static final int WARMUP_TICKS = 1_000;
    static final int DECISION_INTERVAL_TICKS = 100;
    static final int MAX_POST_SHOCK_TICKS = 6_000;
    private static final float DELTA_SECONDS = 0.1f;
    private static final String MINERS = "faction.miners";
    private static final String STEEL = "item.steel";
    private static final String ENERGY = "item.energy";
    private static final String WEAPONS = "item.weapons";

    private Stage9ERecoveryRunner() {
        throw new AssertionError("Utility class");
    }

    static Stage9ERecoveryReport run(long rootSeed) {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(rootSeed);
        for (int tick = 0; tick < WARMUP_TICKS; tick++) {
            world.advanceFrame(DELTA_SECONDS);
        }

        SimulationSession inner = world.findSession(DemoGalaxyFactory.INNER_SYSTEM_ID).orElseThrow();
        Entity foundry = Stage9EMetrics.requireFoundry(inner);
        EntityId reserveId = Stage9ESetup.createReserve(world, content, foundry);
        int steelId = content.findItem(STEEL).runtimeId();
        int weaponsId = content.findItem(WEAPONS).runtimeId();
        long baselineSteel = Stage9EMetrics.unmetDemand(inner, steelId);
        long[] initialResources = Stage9EAccounting.physicalResourceTotals(world);
        long initialMoney = Stage9EAccounting.totalMoney(world, content);
        Map<StarSystemId, Integer> ledgerStarts = Stage9EAccounting.ledgerStarts(world);
        int foundriesBefore = Stage9EMetrics.countFoundries(inner);
        long shockTick = activeTick(world);

        Stage9ESetup.applyShock(world, foundry, reserveId);
        int foundriesAfterShock = Stage9EMetrics.countFoundries(inner);

        Progress progress = new Progress(
                Stage9EMetrics.unmetDemand(inner, steelId),
                Stage9EMetrics.unmetDemand(inner, weaponsId),
                Stage9EMetrics.structuralPressureBasisPoints(inner, steelId));

        for (int elapsed = 1; elapsed <= MAX_POST_SHOCK_TICKS; elapsed++) {
            world.advanceFrame(DELTA_SECONDS);
            long tick = activeTick(world);
            if (elapsed % DECISION_INTERVAL_TICKS == 0) {
                if (progress.projectId < 0L) {
                    world.applyEconomicInvestmentDecision();
                }
                world.applyLiquiditySupport(MINERS);
            }
            samplePressure(world, inner, steelId, weaponsId, progress);
            sampleProject(world, inner, tick, progress);

            long steelUnmet = Stage9EMetrics.unmetDemand(inner, steelId);
            if (progress.replacementProduced
                    && steelUnmet <= baselineSteel) {
                progress.recoveryTick = tick;
                break;
            }
        }

        Stage9EAccounting.LedgerDelta ledger = Stage9EAccounting.summarize(world, content, ledgerStarts);
        long finalMoney = Stage9EAccounting.totalMoney(world, content);
        long expectedMoney = Math.addExact(
                Math.addExact(initialMoney, ledger.moneySourceMilliCredits),
                -ledger.moneySinkMilliCredits);
        boolean resourcesConserved = Stage9EAccounting.resourcesConserved(
                initialResources,
                Stage9EAccounting.physicalResourceTotals(world),
                ledger,
                content);

        return new Stage9ERecoveryReport(
                rootSeed,
                WARMUP_TICKS,
                shockTick,
                progress.detectionTick,
                progress.decisionTick,
                progress.firstDeliveryTick,
                progress.fulfilledTick,
                progress.buildTick,
                progress.completedTick,
                progress.recoveryTick,
                baselineSteel,
                progress.peakSteel,
                progress.peakWeapons,
                progress.peakPressure,
                progress.funding,
                progress.deliveredSteel,
                progress.deliveredEnergy,
                initialMoney,
                finalMoney,
                expectedMoney,
                finalMoney == expectedMoney,
                resourcesConserved,
                foundriesBefore,
                foundriesAfterShock,
                Stage9EMetrics.countFoundries(inner),
                progress.projectId);
    }

    private static void samplePressure(
            WorldSimulation world,
            SimulationSession inner,
            int steelId,
            int weaponsId,
            Progress progress) {
        FactionEconomicPressureState pressure = Stage9EMetrics.findSteelPressure(world);
        if (pressure != null && pressure.consecutiveObservations() > 0 && progress.detectionTick < 0L) {
            progress.detectionTick = pressure.firstObservedTick();
        }
        progress.peakSteel = Math.max(progress.peakSteel, Stage9EMetrics.unmetDemand(inner, steelId));
        progress.peakWeapons = Math.max(progress.peakWeapons, Stage9EMetrics.unmetDemand(inner, weaponsId));
        progress.peakPressure = Math.max(
                progress.peakPressure,
                Stage9EMetrics.structuralPressureBasisPoints(inner, steelId));
    }

    private static void sampleProject(
            WorldSimulation world,
            SimulationSession inner,
            long tick,
            Progress progress) {
        ConstructionProjectState project = Stage9EMetrics.findReplacementProject(world);
        if (project == null) {
            return;
        }
        if (progress.projectId < 0L) {
            progress.projectId = project.id().value();
            progress.decisionTick = tick;
            progress.funding = project.minimumFundingMilliCredits();
        }
        progress.deliveredSteel = Stage9EMetrics.delivered(project, STEEL);
        progress.deliveredEnergy = Stage9EMetrics.delivered(project, ENERGY);
        if (progress.firstDeliveryTick < 0L && progress.deliveredSteel + progress.deliveredEnergy > 0) {
            progress.firstDeliveryTick = tick;
        }
        if (progress.fulfilledTick < 0L && project.materialsFulfilled()) {
            progress.fulfilledTick = tick;
        }
        if (progress.buildTick < 0L
                && (project.status() == ConstructionProjectStatus.BUILDING
                || project.status() == ConstructionProjectStatus.COMPLETED)) {
            progress.buildTick = tick;
        }
        if (progress.completedTick < 0L && project.status() == ConstructionProjectStatus.COMPLETED) {
            progress.completedTick = tick;
            progress.replacementName = Stage9EMetrics.completedStationName(inner, project);
            world.applyLiquiditySupport(MINERS);
        }
        if (!progress.replacementProduced && progress.completedTick >= 0L) {
            progress.replacementProduced = Stage9EMetrics.hasResourceTransformFrom(
                    inner,
                    0,
                    progress.replacementName);
        }
    }

    private static long activeTick(WorldSimulation world) {
        return world.findSession(world.getActiveSystemId()).orElseThrow().getClock().getTick();
    }

    private static final class Progress {
        private long detectionTick = -1L;
        private long decisionTick = -1L;
        private long firstDeliveryTick = -1L;
        private long fulfilledTick = -1L;
        private long buildTick = -1L;
        private long completedTick = -1L;
        private long recoveryTick = -1L;
        private long peakSteel;
        private long peakWeapons;
        private int peakPressure;
        private long funding;
        private int deliveredSteel;
        private int deliveredEnergy;
        private long projectId = -1L;
        private String replacementName;
        private boolean replacementProduced;

        private Progress(long peakSteel, long peakWeapons, int peakPressure) {
            this.peakSteel = peakSteel;
            this.peakWeapons = peakWeapons;
            this.peakPressure = peakPressure;
        }
    }
}
