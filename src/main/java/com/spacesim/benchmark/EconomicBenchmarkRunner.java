package com.spacesim.benchmark;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.economy.EconomicTransaction;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.trade.MarketDirectory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongFunction;

/**
 * Запускает authoritative {@link SimulationSession} без OpenGL и собирает economic/performance metrics.
 *
 * <p>Runner не содержит альтернативной экономики: каждый tick исполняется тем же production
 * pipeline, что используется headless save/load session. Периодические observations только читают
 * ECS/ledger и не меняют состояние мира. Resource accounting включает inventories, астероиды,
 * explicit source/sink ledger records и точные recipe deltas production transforms.</p>
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
     * @throws IllegalStateException если fixed-step session не исполняет ровно один tick или
     *                               production transform невозможно сопоставить с content catalog
     */
    public BenchmarkReport run(BenchmarkScenario scenario) {
        BenchmarkScenario checked = Objects.requireNonNull(scenario, "BenchmarkScenario не задан");
        SimulationSession session = Objects.requireNonNull(
                sessionFactory.apply(checked.rootSeed()), "Session factory вернула null");
        ContentCatalog catalog = session.getContentCatalog();

        long initialMoney = totalMoney(session);
        long[] initialPhysicalResources = physicalResourceTotals(session);
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
        LedgerSummary ledger = summarizeLedger(session, catalog);
        FinalWorldSummary world = summarizeFinalWorld(session, catalog);
        long[] finalPhysicalResources = physicalResourceTotals(session);

        long expectedFinalMoney = Math.subtractExact(
                Math.addExact(initialMoney, ledger.moneySourceMilliCredits),
                ledger.moneySinkMilliCredits);
        boolean moneyConserved = world.totalMoneyMilliCredits == expectedFinalMoney;
        ResourceAccounting resourceAccounting = reconcileResources(
                initialPhysicalResources,
                finalPhysicalResources,
                ledger,
                catalog);

        double elapsedSeconds = elapsedNanos / 1_000_000_000d;
        double ticksPerSecond = checked.simulationTicks() / elapsedSeconds;
        double simulatedSeconds = checked.simulationTicks()
                * (double) SimulationSession.DEFAULT_FIXED_STEP_SECONDS;
        double simulatedSecondsPerRealSecond = simulatedSeconds / elapsedSeconds;

        BenchmarkObservability observability = new BenchmarkObservability(
                world.minerCount,
                world.traderCount + world.minerCount,
                world.walletCount,
                world.walletMinMilliCredits,
                world.walletMedianMilliCredits,
                world.walletP90MilliCredits,
                world.walletMaxMilliCredits,
                world.walletGini,
                world.totalMinedUnits,
                world.totalDeliveredUnits,
                ledger.productionTransformCycles,
                ledger.productionOutputUnits,
                observations.marketOpportunitySampleSum,
                observations.marketOpportunitySampleMax,
                resourceAccounting.conserved,
                resourceAccounting.activeItemDeltas);

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
                        observations.expectedRouteProfitMilliCreditsSum,
                        observability);

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

    private static LedgerSummary summarizeLedger(
            SimulationSession session,
            ContentCatalog catalog) {
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
                case RESOURCE_SOURCE -> {
                    summary.resourceSourceUnits = Math.addExact(
                            summary.resourceSourceUnits, entry.itemAmount());
                    addItemAmount(summary.resourceSourceByItem, entry.itemId(), entry.itemAmount());
                }
                case RESOURCE_SINK -> {
                    summary.resourceSinkUnits = Math.addExact(
                            summary.resourceSinkUnits, entry.itemAmount());
                    addItemAmount(summary.resourceSinkByItem, entry.itemId(), entry.itemAmount());
                }
                case RESOURCE_TRANSFORM -> {
                    summary.resourceTransforms++;
                    accountProductionTransform(summary, entry, catalog);
                }
            }
        }
        return summary;
    }

    private static void accountProductionTransform(
            LedgerSummary summary,
            EconomicTransaction entry,
            ContentCatalog catalog) {
        String reason = entry.reason();
        int separator = reason == null ? -1 : reason.lastIndexOf(" x");
        if (separator <= 0 || separator + 2 >= reason.length()) {
            throw new IllegalStateException("Неизвестный RESOURCE_TRANSFORM reason: " + reason);
        }
        String recipeName = reason.substring(0, separator);
        long cycles;
        try {
            cycles = Long.parseLong(reason.substring(separator + 2));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Некорректное число production cycles: " + reason, exception);
        }
        if (cycles <= 0L) {
            throw new IllegalStateException("Production transform должен иметь положительные cycles");
        }

        ContentCatalog.RecipeDefinition definition = findRecipeByDisplayName(catalog, recipeName);
        summary.productionTransformCycles = Math.addExact(summary.productionTransformCycles, cycles);
        for (var input : definition.inputs().entrySet()) {
            ContentCatalog.ItemDefinition item = requireItem(catalog, input.getKey());
            long amount = Math.multiplyExact((long) input.getValue(), cycles);
            summary.productionDeltaByItem[item.runtimeId()] = Math.subtractExact(
                    summary.productionDeltaByItem[item.runtimeId()], amount);
        }
        for (var output : definition.outputs().entrySet()) {
            ContentCatalog.ItemDefinition item = requireItem(catalog, output.getKey());
            long amount = Math.multiplyExact((long) output.getValue(), cycles);
            summary.productionDeltaByItem[item.runtimeId()] = Math.addExact(
                    summary.productionDeltaByItem[item.runtimeId()], amount);
            summary.productionOutputUnits = Math.addExact(summary.productionOutputUnits, amount);
        }
    }

    private static ContentCatalog.RecipeDefinition findRecipeByDisplayName(
            ContentCatalog catalog,
            String displayName) {
        ContentCatalog.RecipeDefinition match = null;
        for (ContentCatalog.RecipeDefinition recipe : catalog.getRecipes()) {
            if (!recipe.displayName().equals(displayName)) {
                continue;
            }
            if (match != null) {
                throw new IllegalStateException(
                        "Benchmark resource accounting требует уникальный recipe displayName: " + displayName);
            }
            match = recipe;
        }
        if (match == null) {
            throw new IllegalStateException(
                    "RESOURCE_TRANSFORM не найден в content catalog: " + displayName);
        }
        return match;
    }

    private static ContentCatalog.ItemDefinition requireItem(
            ContentCatalog catalog,
            String contentId) {
        ContentCatalog.ItemDefinition item = catalog.findItem(contentId);
        if (item == null) {
            throw new IllegalStateException("Recipe ссылается на неизвестный benchmark item: " + contentId);
        }
        return item;
    }

    private static void addItemAmount(long[] target, int itemId, long amount) {
        if (itemId < 0 || itemId >= target.length || amount < 0L) {
            throw new IllegalStateException("Resource ledger содержит некорректный item/amount");
        }
        target[itemId] = Math.addExact(target[itemId], amount);
    }

    private static FinalWorldSummary summarizeFinalWorld(
            SimulationSession session,
            ContentCatalog catalog) {
        int entityCount = 0;
        int stationCount = 0;
        int traderCount = 0;
        int minerCount = 0;
        long totalMoney = 0L;
        long totalMinedUnits = 0L;
        long totalDeliveredUnits = 0L;
        boolean nonNegativeInventories = true;
        long[] stockByRuntimeId = new long[Constants.MAX_ITEMS];
        List<Long> wallets = new ArrayList<>();

        for (Entity entity : session.getEngine().getEntities()) {
            entityCount++;
            MarketComponent market = entity.getComponent(MarketComponent.class);
            InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
            WalletComponent wallet = entity.getComponent(WalletComponent.class);
            TradeAIComponent tradeAi = entity.getComponent(TradeAIComponent.class);
            MiningComponent mining = entity.getComponent(MiningComponent.class);
            if (market != null && inventory != null && wallet != null) {
                stationCount++;
            }
            if (tradeAi != null && inventory != null && wallet != null) {
                traderCount++;
            }
            if (mining != null && inventory != null && wallet != null) {
                minerCount++;
                totalMinedUnits = Math.addExact(totalMinedUnits, mining.totalMined);
                totalDeliveredUnits = Math.addExact(totalDeliveredUnits, mining.totalDelivered);
            }
            if (wallet != null) {
                long balance = wallet.getBalanceMilliCredits();
                totalMoney = Math.addExact(totalMoney, balance);
                wallets.add(balance);
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
        WealthSummary wealth = summarizeWealth(wallets);
        return new FinalWorldSummary(
                entityCount,
                stationCount,
                traderCount,
                minerCount,
                totalMoney,
                nonNegativeInventories,
                List.copyOf(activeStock),
                wealth.count,
                wealth.minimum,
                wealth.median,
                wealth.p90,
                wealth.maximum,
                wealth.gini,
                totalMinedUnits,
                totalDeliveredUnits);
    }

    private static WealthSummary summarizeWealth(List<Long> balances) {
        if (balances.isEmpty()) {
            return new WealthSummary(0, 0L, 0L, 0L, 0L, 0d);
        }
        balances.sort(Long::compare);
        int count = balances.size();
        long minimum = balances.get(0);
        long median = balances.get((count - 1) / 2);
        int p90Index = Math.max(0, Math.min(count - 1, (int) Math.ceil(count * 0.90d) - 1));
        long p90 = balances.get(p90Index);
        long maximum = balances.get(count - 1);
        double total = 0d;
        double weighted = 0d;
        for (int index = 0; index < count; index++) {
            double value = balances.get(index);
            total += value;
            weighted += (2d * (index + 1) - count - 1d) * value;
        }
        double gini = total == 0d ? 0d : weighted / (count * total);
        if (gini < 0d && gini > -1e-12d) {
            gini = 0d;
        }
        return new WealthSummary(count, minimum, median, p90, maximum, gini);
    }

    private static long[] physicalResourceTotals(SimulationSession session) {
        long[] totals = new long[Constants.MAX_ITEMS];
        for (Entity entity : session.getEngine().getEntities()) {
            InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
            if (inventory != null) {
                for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
                    totals[itemId] = Math.addExact(totals[itemId], inventory.stock[itemId]);
                }
            }
            AsteroidComponent asteroid = entity.getComponent(AsteroidComponent.class);
            if (asteroid != null) {
                addItemAmount(totals, asteroid.resourceItem, asteroid.remainingResource);
            }
        }
        return totals;
    }

    private static ResourceAccounting reconcileResources(
            long[] initialPhysical,
            long[] finalPhysical,
            LedgerSummary ledger,
            ContentCatalog catalog) {
        boolean conserved = true;
        List<Long> activeDeltas = new ArrayList<>(catalog.getItems().size());
        for (ContentCatalog.ItemDefinition item : catalog.getItems()) {
            int itemId = item.runtimeId();
            long expected = initialPhysical[itemId];
            expected = Math.addExact(expected, ledger.resourceSourceByItem[itemId]);
            expected = Math.subtractExact(expected, ledger.resourceSinkByItem[itemId]);
            expected = Math.addExact(expected, ledger.productionDeltaByItem[itemId]);
            long delta = Math.subtractExact(finalPhysical[itemId], expected);
            activeDeltas.add(delta);
            if (delta != 0L) {
                conserved = false;
            }
        }
        return new ResourceAccounting(conserved, List.copyOf(activeDeltas));
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
        private long marketOpportunitySampleSum;
        private long marketOpportunitySampleMax;

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
            MarketDirectory directory = new MarketDirectory(catalog);
            directory.rebuild(session.getEngine().getEntities());
            long opportunities = 0L;
            for (ContentCatalog.ItemDefinition item : catalog.getItems()) {
                opportunities = Math.addExact(
                        opportunities, directory.opportunities(item.runtimeId()).size());
            }
            marketOpportunitySampleSum = Math.addExact(marketOpportunitySampleSum, opportunities);
            marketOpportunitySampleMax = Math.max(marketOpportunitySampleMax, opportunities);
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
        private long productionTransformCycles;
        private long productionOutputUnits;
        private final long[] resourceSourceByItem = new long[Constants.MAX_ITEMS];
        private final long[] resourceSinkByItem = new long[Constants.MAX_ITEMS];
        private final long[] productionDeltaByItem = new long[Constants.MAX_ITEMS];
    }

    private record FinalWorldSummary(
            int entityCount,
            int stationCount,
            int traderCount,
            int minerCount,
            long totalMoneyMilliCredits,
            boolean nonNegativeInventories,
            List<Long> stockByItem,
            int walletCount,
            long walletMinMilliCredits,
            long walletMedianMilliCredits,
            long walletP90MilliCredits,
            long walletMaxMilliCredits,
            double walletGini,
            long totalMinedUnits,
            long totalDeliveredUnits) {
    }

    private record WealthSummary(
            int count,
            long minimum,
            long median,
            long p90,
            long maximum,
            double gini) {
    }

    private record ResourceAccounting(boolean conserved, List<Long> activeItemDeltas) {
    }
}
