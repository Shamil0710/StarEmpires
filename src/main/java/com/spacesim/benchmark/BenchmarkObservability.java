package com.spacesim.benchmark;

import java.util.List;
import java.util.Objects;

/**
 * Дополнительные deterministic observability metrics benchmark run.
 *
 * <p>Эти значения зависят только от simulation state/scenario и потому могут сравниваться между
 * машинами в отличие от wall-clock/heap performance metrics.</p>
 *
 * @param minerCount число mining-agent entities
 * @param economicAgentCount traders + miners
 * @param walletCount число экономических кошельков
 * @param walletMinMilliCredits минимальный финальный баланс
 * @param walletMedianMilliCredits медианный финальный баланс
 * @param walletP90MilliCredits 90-й процентиль финального баланса
 * @param walletMaxMilliCredits максимальный финальный баланс
 * @param walletGini коэффициент Джини финальных балансов
 * @param totalMinedUnits cumulative {@code MiningComponent.totalMined}
 * @param totalDeliveredUnits cumulative {@code MiningComponent.totalDelivered}
 * @param productionTransformCycles суммарное число production cycles из ledger transform records
 * @param productionOutputUnits суммарный gross output всех production cycles
 * @param marketOpportunitySampleSum сумма размеров shared TradeOpportunity index по observations
 * @param marketOpportunitySampleMax максимальный размер shared TradeOpportunity index
 * @param resourceAccountingConserved совпали ли expected и physical item totals
 * @param resourceAccountingDeltaByItem {@code finalPhysical - expectedPhysical} по active items
 */
public record BenchmarkObservability(
        int minerCount,
        int economicAgentCount,
        int walletCount,
        long walletMinMilliCredits,
        long walletMedianMilliCredits,
        long walletP90MilliCredits,
        long walletMaxMilliCredits,
        double walletGini,
        long totalMinedUnits,
        long totalDeliveredUnits,
        long productionTransformCycles,
        long productionOutputUnits,
        long marketOpportunitySampleSum,
        long marketOpportunitySampleMax,
        boolean resourceAccountingConserved,
        List<Long> resourceAccountingDeltaByItem) {

    /**
     * Проверяет deterministic diagnostic ranges и копирует item deltas.
     *
     * @param minerCount число mining-agent entities
     * @param economicAgentCount traders + miners
     * @param walletCount число экономических кошельков
     * @param walletMinMilliCredits минимальный финальный баланс
     * @param walletMedianMilliCredits медианный финальный баланс
     * @param walletP90MilliCredits 90-й процентиль финального баланса
     * @param walletMaxMilliCredits максимальный финальный баланс
     * @param walletGini коэффициент Джини финальных балансов
     * @param totalMinedUnits cumulative mined units
     * @param totalDeliveredUnits cumulative delivered units
     * @param productionTransformCycles суммарное число production cycles
     * @param productionOutputUnits суммарный gross production output
     * @param marketOpportunitySampleSum сумма opportunity counts по observations
     * @param marketOpportunitySampleMax максимальный opportunity count
     * @param resourceAccountingConserved результат resource accounting
     * @param resourceAccountingDeltaByItem physical minus expected по active items
     */
    public BenchmarkObservability {
        resourceAccountingDeltaByItem = List.copyOf(Objects.requireNonNull(
                resourceAccountingDeltaByItem, "resourceAccountingDeltaByItem не задан"));
        if (minerCount < 0 || economicAgentCount < 0 || walletCount < 0
                || walletMinMilliCredits < 0L || walletMedianMilliCredits < 0L
                || walletP90MilliCredits < 0L || walletMaxMilliCredits < 0L
                || totalMinedUnits < 0L || totalDeliveredUnits < 0L
                || productionTransformCycles < 0L || productionOutputUnits < 0L
                || marketOpportunitySampleSum < 0L || marketOpportunitySampleMax < 0L) {
            throw new IllegalArgumentException("Benchmark observability metric не может быть отрицательной");
        }
        if (walletCount > 0
                && (walletMinMilliCredits > walletMedianMilliCredits
                || walletMedianMilliCredits > walletP90MilliCredits
                || walletP90MilliCredits > walletMaxMilliCredits)) {
            throw new IllegalArgumentException("Wealth percentiles benchmark несогласованы");
        }
        if (!Double.isFinite(walletGini) || walletGini < 0d || walletGini > 1d) {
            throw new IllegalArgumentException("Wallet Gini должен находиться в диапазоне [0, 1]");
        }
    }

    /**
     * Возвращает среднее число shared trade opportunities на observation.
     *
     * @param sampleCount число benchmark observations
     * @return среднее либо 0, если observations отсутствуют
     */
    public double meanMarketOpportunities(long sampleCount) {
        return sampleCount <= 0L ? 0d : marketOpportunitySampleSum / (double) sampleCount;
    }
}
