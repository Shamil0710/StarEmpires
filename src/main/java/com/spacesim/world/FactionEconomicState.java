package com.spacesim.world;

import java.util.Objects;

/**
 * Immutable persistent economic state одной strategic faction.
 *
 * <p>Faction адресуется stable content ID, а не dense runtime ID. Treasury является реальным
 * денежным пулом world-layer: обычная policy-операция может только переводить эти деньги другим
 * economic actors либо получать переводы обратно; создание/уничтожение денег остаётся отдельным
 * source/sink контрактом.</p>
 *
 * @param factionContentId stable faction content ID
 * @param treasuryMilliCredits неотрицательный authoritative treasury balance
 * @param stationLiquidityReserveMilliCredits целевой минимальный wallet принадлежащей станции
 * @param maxLiquiditySupportPerDecisionMilliCredits максимальный общий расход liquidity-support decision
 * @param treasuryReserveFloorMilliCredits protected treasury balance for ordinary policy spending
 * @param maxConstructionInvestmentPerDecisionMilliCredits construction authorization cap per decision
 */
public record FactionEconomicState(
        String factionContentId,
        long treasuryMilliCredits,
        long stationLiquidityReserveMilliCredits,
        long maxLiquiditySupportPerDecisionMilliCredits,
        long treasuryReserveFloorMilliCredits,
        long maxConstructionInvestmentPerDecisionMilliCredits)
        implements Comparable<FactionEconomicState> {

    /**
     * Source-compatible pre-17F.2 constructor.
     *
     * <p>Legacy worlds had no protected treasury floor or construction authorization cap, so they
     * migrate to zero protected reserve and effectively unlimited per-decision construction funding.
     * Existing liquidity policy is preserved exactly.</p>
     *
     * @param factionContentId stable faction content ID
     * @param treasuryMilliCredits authoritative treasury balance
     * @param stationLiquidityReserveMilliCredits legacy station liquidity target
     * @param maxLiquiditySupportPerDecisionMilliCredits legacy liquidity-support cap
     */
    public FactionEconomicState(
            String factionContentId,
            long treasuryMilliCredits,
            long stationLiquidityReserveMilliCredits,
            long maxLiquiditySupportPerDecisionMilliCredits) {
        this(
                factionContentId,
                treasuryMilliCredits,
                stationLiquidityReserveMilliCredits,
                maxLiquiditySupportPerDecisionMilliCredits,
                0L,
                Long.MAX_VALUE);
    }

    /**
     * Validates persistent faction economy and fiscal spending limits.
     *
     * @param factionContentId stable faction content ID
     * @param treasuryMilliCredits non-negative authoritative treasury balance
     * @param stationLiquidityReserveMilliCredits non-negative station liquidity target
     * @param maxLiquiditySupportPerDecisionMilliCredits non-negative liquidity-support cap
     * @param treasuryReserveFloorMilliCredits non-negative protected treasury balance
     * @param maxConstructionInvestmentPerDecisionMilliCredits non-negative construction funding cap
     */
    public FactionEconomicState {
        factionContentId = normalizedContentId(factionContentId);
        if (treasuryMilliCredits < 0L
                || stationLiquidityReserveMilliCredits < 0L
                || maxLiquiditySupportPerDecisionMilliCredits < 0L
                || treasuryReserveFloorMilliCredits < 0L
                || maxConstructionInvestmentPerDecisionMilliCredits < 0L) {
            throw new IllegalArgumentException("Faction treasury/policy amounts не могут быть отрицательными");
        }
    }

    @Override
    public int compareTo(FactionEconomicState other) {
        return factionContentId.compareTo(Objects.requireNonNull(other, "Faction state не задан")
                .factionContentId);
    }

    private static String normalizedContentId(String value) {
        String result = Objects.requireNonNull(value, "Faction content ID не задан").strip();
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Faction content ID не может быть пустым");
        }
        return result;
    }
}
