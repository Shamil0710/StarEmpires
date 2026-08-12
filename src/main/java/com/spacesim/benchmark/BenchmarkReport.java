package com.spacesim.benchmark;

import java.util.List;
import java.util.Objects;

/**
 * Полный результат headless economic benchmark.
 *
 * <p>Детерминированные экономические observations отделены от machine-dependent performance
 * измерений. Поэтому regression tests сравнивают {@link DeterministicMetrics}, а wall-clock/JVM
 * поля используются только как benchmark baseline.</p>
 *
 * @param scenario исходный versioned benchmark scenario
 * @param deterministic воспроизводимые economic metrics
 * @param performance machine-dependent timing/heap metrics
 */
public record BenchmarkReport(
        BenchmarkScenario scenario,
        DeterministicMetrics deterministic,
        PerformanceMetrics performance) {

    /** Версия machine-readable JSON report contract. */
    public static final int REPORT_SCHEMA_VERSION = 1;

    /**
     * Проверяет обязательные части отчёта.
     *
     * @param scenario исходный versioned benchmark scenario
     * @param deterministic воспроизводимые economic metrics
     * @param performance machine-dependent timing/heap metrics
     */
    public BenchmarkReport {
        Objects.requireNonNull(scenario, "BenchmarkScenario не задан");
        Objects.requireNonNull(deterministic, "DeterministicMetrics не заданы");
        Objects.requireNonNull(performance, "PerformanceMetrics не заданы");
    }

    /**
     * Формирует стабильный machine-readable JSON без внешней serialization dependency.
     *
     * @return JSON object с scenario, deterministic и performance секциями
     */
    public String toJson() {
        StringBuilder out = new StringBuilder(4096);
        out.append('{')
                .append("\"reportSchemaVersion\":").append(REPORT_SCHEMA_VERSION).append(',');
        out.append("\"scenario\":{")
                .append("\"name\":\"").append(escapeJson(scenario.name())).append("\",")
                .append("\"version\":").append(scenario.version()).append(',')
                .append("\"rootSeed\":").append(scenario.rootSeed()).append(',')
                .append("\"simulationTicks\":").append(scenario.simulationTicks()).append(',')
                .append("\"sampleEveryTicks\":").append(scenario.sampleEveryTicks())
                .append("},");

        DeterministicMetrics d = deterministic;
        out.append("\"deterministic\":{")
                .append("\"finalTick\":").append(d.finalTick()).append(',')
                .append("\"sampleCount\":").append(d.sampleCount()).append(',')
                .append("\"entityCount\":").append(d.entityCount()).append(',')
                .append("\"stationCount\":").append(d.stationCount()).append(',')
                .append("\"traderCount\":").append(d.traderCount()).append(',')
                .append("\"ledgerEntries\":").append(d.ledgerEntries()).append(',')
                .append("\"tradeTransactions\":").append(d.tradeTransactions()).append(',')
                .append("\"tradedUnits\":").append(d.tradedUnits()).append(',')
                .append("\"tradedMoneyMilliCredits\":").append(d.tradedMoneyMilliCredits()).append(',')
                .append("\"resourceSourceUnits\":").append(d.resourceSourceUnits()).append(',')
                .append("\"resourceSinkUnits\":").append(d.resourceSinkUnits()).append(',')
                .append("\"resourceTransforms\":").append(d.resourceTransforms()).append(',')
                .append("\"moneySourceMilliCredits\":").append(d.moneySourceMilliCredits()).append(',')
                .append("\"moneySinkMilliCredits\":").append(d.moneySinkMilliCredits()).append(',')
                .append("\"initialMoneyMilliCredits\":").append(d.initialMoneyMilliCredits()).append(',')
                .append("\"finalMoneyMilliCredits\":").append(d.finalMoneyMilliCredits()).append(',')
                .append("\"moneyConserved\":").append(d.moneyConserved()).append(',')
                .append("\"nonNegativeInventories\":").append(d.nonNegativeInventories()).append(',')
                .append("\"finalStockByItem\":");
        appendLongArray(out, d.finalStockByItem());
        out.append(',')
                .append("\"stockoutObservations\":").append(d.stockoutObservations()).append(',')
                .append("\"unmetDemandUnitObservations\":").append(d.unmetDemandUnitObservations()).append(',')
                .append("\"sampledSellPriceCount\":").append(d.sampledSellPriceCount()).append(',')
                .append("\"sampledSellPriceMean\":").append(d.sampledSellPriceMean()).append(',')
                .append("\"sampledSellPriceVariance\":").append(d.sampledSellPriceVariance()).append(',')
                .append("\"activeRouteObservations\":").append(d.activeRouteObservations()).append(',')
                .append("\"expectedRouteProfitMilliCreditsSum\":")
                .append(d.expectedRouteProfitMilliCreditsSum()).append(',')
                .append("\"observability\":");
        appendObservability(out, d.observability());
        out.append("},");

        PerformanceMetrics p = performance;
        out.append("\"performance\":{")
                .append("\"wallClockNanos\":").append(p.wallClockNanos()).append(',')
                .append("\"ticksPerSecond\":").append(p.ticksPerSecond()).append(',')
                .append("\"simulatedSecondsPerRealSecond\":")
                .append(p.simulatedSecondsPerRealSecond()).append(',')
                .append("\"heapUsedBeforeBytes\":").append(p.heapUsedBeforeBytes()).append(',')
                .append("\"heapUsedAfterBytes\":").append(p.heapUsedAfterBytes()).append(',')
                .append("\"heapUsedDeltaBytes\":").append(p.heapUsedDeltaBytes())
                .append("}}");
        return out.toString();
    }

    private static void appendObservability(StringBuilder out, BenchmarkObservability o) {
        out.append('{')
                .append("\"minerCount\":").append(o.minerCount()).append(',')
                .append("\"economicAgentCount\":").append(o.economicAgentCount()).append(',')
                .append("\"walletCount\":").append(o.walletCount()).append(',')
                .append("\"walletMinMilliCredits\":").append(o.walletMinMilliCredits()).append(',')
                .append("\"walletMedianMilliCredits\":").append(o.walletMedianMilliCredits()).append(',')
                .append("\"walletP90MilliCredits\":").append(o.walletP90MilliCredits()).append(',')
                .append("\"walletMaxMilliCredits\":").append(o.walletMaxMilliCredits()).append(',')
                .append("\"walletGini\":").append(o.walletGini()).append(',')
                .append("\"totalMinedUnits\":").append(o.totalMinedUnits()).append(',')
                .append("\"totalDeliveredUnits\":").append(o.totalDeliveredUnits()).append(',')
                .append("\"productionTransformCycles\":").append(o.productionTransformCycles()).append(',')
                .append("\"productionOutputUnits\":").append(o.productionOutputUnits()).append(',')
                .append("\"marketOpportunitySampleSum\":").append(o.marketOpportunitySampleSum()).append(',')
                .append("\"marketOpportunitySampleMax\":").append(o.marketOpportunitySampleMax()).append(',')
                .append("\"resourceAccountingConserved\":").append(o.resourceAccountingConserved()).append(',')
                .append("\"resourceAccountingDeltaByItem\":");
        appendLongArray(out, o.resourceAccountingDeltaByItem());
        out.append('}');
    }

    private static void appendLongArray(StringBuilder out, List<Long> values) {
        out.append('[');
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                out.append(',');
            }
            out.append(values.get(index));
        }
        out.append(']');
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            switch (ch) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }
        return escaped.toString();
    }

    /**
     * Reproducible economic result одного benchmark run.
     *
     * @param finalTick итоговый authoritative simulation tick
     * @param sampleCount число периодических observations
     * @param entityCount итоговое число ECS entities
     * @param stationCount число market stations
     * @param traderCount число TradeAI entities
     * @param ledgerEntries число ledger entries
     * @param tradeTransactions число обычных trade transfers
     * @param tradedUnits суммарное число переданных единиц товара
     * @param tradedMoneyMilliCredits суммарный monetary turnover
     * @param resourceSourceUnits суммарные resource-source units
     * @param resourceSinkUnits суммарные resource-sink units
     * @param resourceTransforms число production transform records
     * @param moneySourceMilliCredits явные денежные sources
     * @param moneySinkMilliCredits явные денежные sinks
     * @param initialMoneyMilliCredits деньги всех WalletComponent до run
     * @param finalMoneyMilliCredits деньги всех WalletComponent после run
     * @param moneyConserved выполняется ли ledger-adjusted money conservation
     * @param nonNegativeInventories остались ли все inventory slots неотрицательными
     * @param finalStockByItem итоговые inventory-остатки по active catalog item runtime order
     * @param stockoutObservations число station/item observations с нулевым stock
     * @param unmetDemandUnitObservations сумма observed target-stock deficits
     * @param sampledSellPriceCount число валидных sampled sell prices
     * @param sampledSellPriceMean средняя sampled sell price
     * @param sampledSellPriceVariance population variance sampled sell prices
     * @param activeRouteObservations число observations активных/запланированных TradeAI routes
     * @param expectedRouteProfitMilliCreditsSum сумма expectedProfit по route observations
     * @param observability дополнительные deterministic diagnostics
     */
    public record DeterministicMetrics(
            long finalTick,
            long sampleCount,
            int entityCount,
            int stationCount,
            int traderCount,
            int ledgerEntries,
            long tradeTransactions,
            long tradedUnits,
            long tradedMoneyMilliCredits,
            long resourceSourceUnits,
            long resourceSinkUnits,
            long resourceTransforms,
            long moneySourceMilliCredits,
            long moneySinkMilliCredits,
            long initialMoneyMilliCredits,
            long finalMoneyMilliCredits,
            boolean moneyConserved,
            boolean nonNegativeInventories,
            List<Long> finalStockByItem,
            long stockoutObservations,
            long unmetDemandUnitObservations,
            long sampledSellPriceCount,
            double sampledSellPriceMean,
            double sampledSellPriceVariance,
            long activeRouteObservations,
            long expectedRouteProfitMilliCreditsSum,
            BenchmarkObservability observability) {

        /**
         * Копирует item totals и проверяет базовые диапазоны.
         *
         * @param finalTick итоговый authoritative simulation tick
         * @param sampleCount число периодических observations
         * @param entityCount итоговое число ECS entities
         * @param stationCount число market stations
         * @param traderCount число TradeAI entities
         * @param ledgerEntries число ledger entries
         * @param tradeTransactions число обычных trade transfers
         * @param tradedUnits суммарное число переданных единиц товара
         * @param tradedMoneyMilliCredits суммарный monetary turnover
         * @param resourceSourceUnits суммарные resource-source units
         * @param resourceSinkUnits суммарные resource-sink units
         * @param resourceTransforms число production transform records
         * @param moneySourceMilliCredits явные денежные sources
         * @param moneySinkMilliCredits явные денежные sinks
         * @param initialMoneyMilliCredits деньги всех WalletComponent до run
         * @param finalMoneyMilliCredits деньги всех WalletComponent после run
         * @param moneyConserved выполняется ли ledger-adjusted money conservation
         * @param nonNegativeInventories остались ли все inventory slots неотрицательными
         * @param finalStockByItem итоговые inventory-остатки по active catalog item runtime order
         * @param stockoutObservations число station/item observations с нулевым stock
         * @param unmetDemandUnitObservations сумма observed target-stock deficits
         * @param sampledSellPriceCount число валидных sampled sell prices
         * @param sampledSellPriceMean средняя sampled sell price
         * @param sampledSellPriceVariance population variance sampled sell prices
         * @param activeRouteObservations число observations активных/запланированных TradeAI routes
         * @param expectedRouteProfitMilliCreditsSum сумма expectedProfit по route observations
         * @param observability дополнительные deterministic diagnostics
         */
        public DeterministicMetrics {
            finalStockByItem = List.copyOf(Objects.requireNonNull(
                    finalStockByItem, "finalStockByItem не задан"));
            Objects.requireNonNull(observability, "BenchmarkObservability не задан");
            if (finalTick < 0L || sampleCount < 0L || entityCount < 0 || stationCount < 0
                    || traderCount < 0 || ledgerEntries < 0 || tradeTransactions < 0L
                    || tradedUnits < 0L || tradedMoneyMilliCredits < 0L
                    || resourceSourceUnits < 0L || resourceSinkUnits < 0L
                    || resourceTransforms < 0L || moneySourceMilliCredits < 0L
                    || moneySinkMilliCredits < 0L || stockoutObservations < 0L
                    || unmetDemandUnitObservations < 0L || sampledSellPriceCount < 0L
                    || activeRouteObservations < 0L || expectedRouteProfitMilliCreditsSum < 0L) {
                throw new IllegalArgumentException("Benchmark deterministic metric не может быть отрицательной");
            }
            if (!Double.isFinite(sampledSellPriceMean)
                    || !Double.isFinite(sampledSellPriceVariance)
                    || sampledSellPriceVariance < 0d) {
                throw new IllegalArgumentException("Price statistics benchmark должны быть конечными");
            }
        }
    }

    /**
     * Machine-dependent performance snapshot.
     *
     * @param wallClockNanos elapsed monotonic wall-clock time
     * @param ticksPerSecond authoritative ticks per real second
     * @param simulatedSecondsPerRealSecond simulated seconds per real second
     * @param heapUsedBeforeBytes approximate JVM used heap before run
     * @param heapUsedAfterBytes approximate JVM used heap after run
     * @param heapUsedDeltaBytes signed difference after-before
     */
    public record PerformanceMetrics(
            long wallClockNanos,
            double ticksPerSecond,
            double simulatedSecondsPerRealSecond,
            long heapUsedBeforeBytes,
            long heapUsedAfterBytes,
            long heapUsedDeltaBytes) {

        /**
         * Проверяет timing/heap ranges.
         *
         * @param wallClockNanos elapsed monotonic wall-clock time
         * @param ticksPerSecond authoritative ticks per real second
         * @param simulatedSecondsPerRealSecond simulated seconds per real second
         * @param heapUsedBeforeBytes approximate JVM used heap before run
         * @param heapUsedAfterBytes approximate JVM used heap after run
         * @param heapUsedDeltaBytes signed difference after-before
         */
        public PerformanceMetrics {
            if (wallClockNanos <= 0L
                    || !Double.isFinite(ticksPerSecond) || ticksPerSecond < 0d
                    || !Double.isFinite(simulatedSecondsPerRealSecond)
                    || simulatedSecondsPerRealSecond < 0d
                    || heapUsedBeforeBytes < 0L || heapUsedAfterBytes < 0L) {
                throw new IllegalArgumentException("Benchmark performance metrics повреждены");
            }
            if (heapUsedDeltaBytes != heapUsedAfterBytes - heapUsedBeforeBytes) {
                throw new IllegalArgumentException("Heap delta benchmark несогласован");
            }
        }
    }
}
