package com.spacesim.world;

import java.util.Objects;

/**
 * Immutable persistent economic/governance state одной strategic faction.
 *
 * <p>Faction адресуется stable content ID, а не dense runtime ID. Treasury является реальным
 * денежным пулом world-layer: обычная policy-операция может только переводить эти деньги другим
 * economic actors либо получать переводы обратно; создание/уничтожение денег остаётся отдельным
 * source/sink контрактом. Stage 17F.6 дополнительно хранит только общий anti-oscillation review
 * watermark; сами policy values остаются в своих authoritative state objects.</p>
 *
 * @param factionContentId stable faction content ID
 * @param treasuryMilliCredits неотрицательный authoritative treasury balance
 * @param stationLiquidityReserveMilliCredits целевой минимальный wallet принадлежащей станции
 * @param maxLiquiditySupportPerDecisionMilliCredits максимальный общий расход liquidity-support decision
 * @param treasuryReserveFloorMilliCredits protected treasury balance for ordinary policy spending
 * @param maxConstructionInvestmentPerDecisionMilliCredits construction authorization cap per decision
 * @param policyReviewState persistent common policy-review watermark
 */
public record FactionEconomicState(
        String factionContentId,
        long treasuryMilliCredits,
        long stationLiquidityReserveMilliCredits,
        long maxLiquiditySupportPerDecisionMilliCredits,
        long treasuryReserveFloorMilliCredits,
        long maxConstructionInvestmentPerDecisionMilliCredits,
        FactionPolicyReviewState policyReviewState)
        implements Comparable<FactionEconomicState> {

    /**
     * Source-compatible pre-17F.2 constructor.
     *
     * <p>Legacy worlds had no protected treasury floor, construction authorization cap or policy-review
     * watermark, so they migrate to zero protected reserve, effectively unlimited per-decision
     * construction funding and a never-reviewed governance state.</p>
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
                Long.MAX_VALUE,
                FactionPolicyReviewState.INITIAL);
    }

    /**
     * Source-compatible pre-17F.6 fiscal constructor.
     *
     * @param factionContentId stable faction content ID
     * @param treasuryMilliCredits authoritative treasury balance
     * @param stationLiquidityReserveMilliCredits station liquidity target
     * @param maxLiquiditySupportPerDecisionMilliCredits liquidity-support cap
     * @param treasuryReserveFloorMilliCredits protected treasury floor
     * @param maxConstructionInvestmentPerDecisionMilliCredits construction authorization cap
     */
    public FactionEconomicState(
            String factionContentId,
            long treasuryMilliCredits,
            long stationLiquidityReserveMilliCredits,
            long maxLiquiditySupportPerDecisionMilliCredits,
            long treasuryReserveFloorMilliCredits,
            long maxConstructionInvestmentPerDecisionMilliCredits) {
        this(
                factionContentId,
                treasuryMilliCredits,
                stationLiquidityReserveMilliCredits,
                maxLiquiditySupportPerDecisionMilliCredits,
                treasuryReserveFloorMilliCredits,
                maxConstructionInvestmentPerDecisionMilliCredits,
                FactionPolicyReviewState.INITIAL);
    }

    /**
     * Validates persistent faction economy, fiscal spending limits and governance watermark.
     *
     * @param factionContentId stable faction content ID
     * @param treasuryMilliCredits non-negative authoritative treasury balance
     * @param stationLiquidityReserveMilliCredits non-negative station liquidity target
     * @param maxLiquiditySupportPerDecisionMilliCredits non-negative liquidity-support cap
     * @param treasuryReserveFloorMilliCredits non-negative protected treasury balance
     * @param maxConstructionInvestmentPerDecisionMilliCredits non-negative construction funding cap
     * @param policyReviewState persistent common policy-review watermark
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
        policyReviewState = Objects.requireNonNull(policyReviewState, "Faction policy review state not set");
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
