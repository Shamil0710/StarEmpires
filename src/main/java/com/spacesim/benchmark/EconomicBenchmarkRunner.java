package com.spacesim.benchmark;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.economy.EconomicTransaction;
import com.spacesim.simulation.SimulationSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongFunction;

/**
 * Запускает authoritative {@link SimulationSession} без OpenGL и собирает economic/performance metrics.
 *
 * <p>Runner не содержит альтернативной экономики: каждый tick исполняется тем же production
 * pipeline, что используется headless save/load session. Периодические observations только читают
 * ECS/ledger и не меняют состояние мира.</p>
 */
public final class EconomicBenchmarkRunner {
    private final LongFunction<SimulationSession> sessionFactory;

    /** Создаёт runner, использующий production demo session factory. */
    public EconomicBenchmarkRunner() {
        this(SimulationSession::createDemo);
    }

    /**
     * Создаёт runner с явно заданной session factory.
     *
     * <p>Extension seam нужен Stage 6 для large benchmark world, не дублируя сам runner/metrics.
     * Factory обязана использовать переданный root seed.</p>
     *
     * @param sessionFactory фабрика authoritative session по root seed
     */
    public EconomicBenchmarkRunner(LongFunction<SimulationSession> sessionFactory) {
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "Session factory не задана");
    }

    /**
     * Выполняет benchmark scenario синхронно до заданного authoritative tick count.
     *
     * @param scenario versioned deterministic benchmark scenario
     * @return полный machine-readable report
     * @throws NullPointerException если scenario/factory result не заданы
     * @throws IllegalStateException если fixed-step session не исполняет ровно один tick на benchmark step
     */
    public BenchmarkReport run(BenchmarkScenario scenario) {
        BenchmarkScenario checked = Objects.requireNonNull(scenario, "BenchmarkScenario не задан");
        SimulationSession session = Objects.requireNonNull(
                sessionFactory.apply(checked.rootSeed()), "Session factory вернула null");
        ContentCatalog catalog = session.getContentCatalog();

        long initialMoney = totalMoney(session);
        long heapBefore = usedHeapBytes();
        long startNanos = System.nanoTime();
        ObservationAccumulator observations = new ObservationAccumulator();

        long startTick = session.getClock().getTick();
        long targetTick = Math.addExact(startTick, checked.simulationTicks());
        long nextSampleTick = Math.addExact(startTick, checked.sampleEveryTicks());

        while (session.getClock().getTick() < targetTick) {
            int executed = session.advanceFrame(SimulationSession.DEFAULT_FIXED_STEP_SECONDS);
            if (executed != 1) {
                throw new IllegalStateException(
                        "Benchmark fixed step должен выполнять ровно один tick, получено: " + executed);
            }
            long currentTick = session.getClock().getTick();
            if (currentTick >= nextSampleTick || currentTick == targetTick) {
                observations.sample(session, catalog);
                while (nextSampleTick <= currentTick) {
                    nextSampleTick = Math.addExact(nextSampleTick, checked.sampleEveryTicks());
                }
            }
        }

        long elapsedNanos = Math.max(1L, System.nanoTime() - startNanos);
        long heapAfter = usedHeapBytes();
        LedgerSummary ledger = summarizeLedger(session);
        FinalWorldSummary world = summarizeFinalWorld(session, catalog);

        long expectedFinalMoney = Math.subtractExact(
                Math.addExact(initialMoney, ledger.moneySourceMilliCredits),
                ledger.moneySinkMilliCredits);
        boolean moneyConserved = world.totalMoneyMilliCredits == expectedFinalMoney;

        double elapsedSeconds = elapsedNanos / 1_000_000_000d;
        double ticksPerSecond = checked.simulationTicks() / elapsedSeconds;
        double simulatedSeconds = checked.simulationTicks()
                * (double) SimulationSession.DEFAULT_FIXED_STEP_SECONDS;
        double simulatedSecondsPerRealSecond = simulatedSeconds / elapsedSeconds;

        BenchmarkReport.DeterministicMetrics deterministic =
                new BenchmarkReport.DeterministicMetrics(
                        session.getClock().getTick(),
                        observations.sampleCount,
                        world.entityCount,
                        world.stationCount,
                        world.traderCount,
                        session.getLedger().size(),
                        ledger.tradeTransactions,
                        ledger.tradedUnits,
                        ledger.tradedMoneyMilliCredits,
                        ledger.resourceSourceUnits,
                        ledger.resourceSinkUnits,
                        ledger.resourceTransforms,
                        ledger.moneySourceMilliCredits,
                        ledger.moneySinkMilliCredits,
                        initialMoney,
                        world.totalMoneyMilliCredits,
                        moneyConserved,
                        world.nonNegativeInventories,
                        world.stockByItem,
                        observations.stockoutObservations,
                        observations.unmetDemandUnitObservations,
                        observations.priceCount,
                        observations.priceMean,
                        observations.pricePopulationVariance(),
                        observations.activeRouteObservations,
                        observations.expectedRouteProfitMilliCreditsSum);

        BenchmarkReport.PerformanceMetrics performance =
                new BenchmarkReport.PerformanceMetrics(
                        elapsedNanos,
                        ticksPerSecond,
                        simulatedSecondsPerRealSecond,
                        heapBefore,
                        heapAfter,
                        heapAfter - heapBefore);
        return new BenchmarkReport(checked, deterministic, performance);
    }

    private static long totalMoney(SimulationSession session) {
        long total = 0L;
        for (Entity entity : session.getEngine().getEntities()) {
            WalletComponent wallet = entity.getComponent(WalletComponent.class);
            if (wallet != null) {
                total = Math.addExact(total, wallet.getBalanceMilliCredits());
            }
        }
        return total;
    }

    private static LedgerSummary summarizeLedger(SimulationSession session) {
        LedgerSummary summary = new LedgerSummary();
        for (EconomicTransaction entry : session.getLedger().getEntries()) {
            switch (entry.type()) {
                case TRADE -> {
                    summary.tradeTransactions++;
                    summary.tradedUnits = Math.addExact(summary.tradedUnits, entry.itemAmount());
                    summary.tradedMoneyMilliCredits = Math.addExact(
                            summary.tradedMoneyMilliCredits, entry.moneyMilliCredits());
                }
                case MONEY_SOURCE -> summary.moneySourceMilliCredits = Math.addExact(
                        summary.moneySourceMilliCredits, entry.moneyMilliCredits());
                case MONEY_SINK -> summary.moneySinkMilliCredits = Math.addExact(
                        summary.moneySinkMilliCredits, entry.moneyMilliCredits());
                case RESOURCE_SOURCE -> summary.resourceSourceUnits = Math.addExact(
                        summary.resourceSourceUnits, entry.itemAmount());
                case RESOURCE_SINK -> summary.resourceSinkUnits = Math.addExact(
                        summary.resourceSinkUnits, entry.itemAmount());
                case RESOURCE_TRANSFORM -> summary.resourceTransforms++;
            }
        }
        return summary;
    }

    private static FinalWorldSummary summarizeFinalWorld(
            SimulationSession session,
            ContentCatalog catalog) {
        int entityCount = 0;
        int stationCount = 0;
        int traderCount = 0;
        long totalMoney = 0L;
        boolean nonNegativeInventories = true;
        long[] stockByRuntimeId = new long[Constants.MAX_ITEMS];

        for (Entity entity : session.getEngine().getEntities()) {
            entityCount++;
            MarketComponent market = entity.getComponent(MarketComponent.class);
            InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
            WalletComponent wallet = entity.getComponent(WalletComponent.class);
            TradeAIComponent tradeAi = entity.getComponent(TradeAIComponent.class);
            if (market != null && inventory != null && wallet != null) {
                stationCount++;
            }
            if (tradeAi != null && inventory != null && wallet != null) {
                traderCount++;
            }
            if (wallet != null) {
                totalMoney = Math.addExact(totalMoney, wallet.getBalanceMilliCredits());
            }
            if (inventory != null) {
                for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
                    int amount = inventory.stock[itemId];
                    if (amount < 0) {
                        nonNegativeInventories = false;
                    }
                    stockByRuntimeId[itemId] = Math.addExact(stockByRuntimeId[itemId], amount);
                }
            }
        }

        List<Long> activeStock = new ArrayList<>(catalog.getItems().size());
        for (ContentCatalog.ItemDefinition item : catalog.getItems()) {
            activeStock.add(stockByRuntimeId[item.runtimeId()]);
        }
        return new FinalWorldSummary(
                entityCount,
                stationCount,
                traderCount,
                totalMoney,
                nonNegativeInventories,
                List.copyOf(activeStock));
    }

    private static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
    }

    private static final class ObservationAccumulator {
        private long sampleCount;
        private long stockoutObservations;
        private long unmetDemandUnitObservations;
        private long priceCount;
        private double priceMean;
        private double priceM2;
        private long activeRouteObservations;
        private long expectedRouteProfitMilliCreditsSum;

        private void sample(SimulationSession session, ContentCatalog catalog) {
            sampleCount++;
            for (Entity entity : session.getEngine().getEntities()) {
                MarketComponent market = entity.getComponent(MarketComponent.class);
                InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
                if (market != null && inventory != null) {
                    sampleMarket(market, inventory, catalog);
                }
                TradeAIComponent tradeAi = entity.getComponent(TradeAIComponent.class);
                if (tradeAi != null
                        && tradeAi.targetItem >= 0
                        && tradeAi.expectedProfitMilliCredits > 0L) {
                    activeRouteObservations++;
                    expectedRouteProfitMilliCreditsSum = Math.addExact(
                            expectedRouteProfitMilliCreditsSum,
                            tradeAi.expectedProfitMilliCredits);
                }
            }
        }

        private void sampleMarket(
                MarketComponent market,
                InventoryComponent inventory,
                ContentCatalog catalog) {
            for (ContentCatalog.ItemDefinition item : catalog.getItems()) {
                int itemId = item.runtimeId();
                if (!market.isTradable(itemId)) {
                    continue;
                }
                int stock = inventory.stock[itemId];
                if (stock == 0) {
                    stockoutObservations++;
                }
                int deficit = Math.max(0, market.targetStock[itemId] - stock);
                unmetDemandUnitObservations = Math.addExact(unmetDemandUnitObservations, deficit);
                float sellPrice = market.sellPrices[itemId];
                if (Float.isFinite(sellPrice) && sellPrice > 0f) {
                    addPrice(sellPrice);
                }
            }
        }

        private void addPrice(double value) {
            priceCount++;
            double delta = value - priceMean;
            priceMean += delta / priceCount;
            priceM2 += delta * (value - priceMean);
        }

        private double pricePopulationVariance() {
            return priceCount == 0L ? 0d : Math.max(0d, priceM2 / priceCount);
        }
    }

    private static final class LedgerSummary {
        private long tradeTransactions;
        private long tradedUnits;
        private long tradedMoneyMilliCredits;
        private long resourceSourceUnits;
        private long resourceSinkUnits;
        private long resourceTransforms;
        private long moneySourceMilliCredits;
        private long moneySinkMilliCredits;
    }

    private record FinalWorldSummary(
            int entityCount,
            int stationCount,
            int traderCount,
            long totalMoneyMilliCredits,
            boolean nonNegativeInventories,
            List<Long> stockByItem) {
    }
}
