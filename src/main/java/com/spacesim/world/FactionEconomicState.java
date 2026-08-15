package com.spacesim.world;

import java.util.Objects;

/**
 * Persistent faction treasury and bounded fiscal spending authorizations.
 *
 * <p>All amounts are stored in integer milli-credits. The reserve floor and construction budget are
 * policy authorizations over the same treasury wallet; neither creates a second account or money
 * source. A reserve floor may exceed the current treasury balance, in which case discretionary
 * spending is zero until the treasury recovers.</p>
 *
 * @param factionContentId stable faction content ID
 * @param treasuryMilliCredits current real treasury balance
 * @param stationLiquidityReserveMilliCredits desired minimum operating liquidity per owned station
 * @param maxLiquiditySupportPerDecisionMilliCredits maximum subsidy transfer per policy decision
 * @param treasuryReserveFloorMilliCredits protected treasury floor for discretionary spending
 * @param maxConstructionInvestmentPerDecisionMilliCredits maximum treasury construction funding per decision
 */
public record FactionEconomicState(
        String factionContentId,
        long treasuryMilliCredits,
        long stationLiquidityReserveMilliCredits,
        long maxLiquiditySupportPerDecisionMilliCredits,
        long treasuryReserveFloorMilliCredits,
        long maxConstructionInvestmentPerDecisionMilliCredits)
        implements Comparable<FactionEconomicState> {

    /** Legacy behavior before Stage 17F.2: no reserve floor and no construction budget ceiling. */
    public static final long LEGACY_UNBOUNDED_CONSTRUCTION_INVESTMENT = Long.MAX_VALUE;

    /**
     * Source-compatible Stage-8 constructor preserving historical spending behavior.
     *
     * @param factionContentId stable faction content ID
     * @param treasuryMilliCredits current real treasury balance
     * @param stationLiquidityReserveMilliCredits desired station liquidity floor
     * @param maxLiquiditySupportPerDecisionMilliCredits maximum subsidy transfer per decision
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
                LEGACY_UNBOUNDED_CONSTRUCTION_INVESTMENT);
    }

    /** Validates stable identity and non-negative balances/authorizations. */
    public FactionEconomicState {
        factionContentId = Objects.requireNonNull(factionContentId, "Faction content ID не задан").strip();
        if (factionContentId.isEmpty()) {
            throw new IllegalArgumentException("Faction content ID не должен быть пустым");
        }
        if (treasuryMilliCredits < 0L
                || stationLiquidityReserveMilliCredits < 0L
                || maxLiquiditySupportPerDecisionMilliCredits < 0L
                || treasuryReserveFloorMilliCredits < 0L
                || maxConstructionInvestmentPerDecisionMilliCredits < 0L) {
            throw new IllegalArgumentException("Faction economic balances/policy cannot be negative");
        }
    }

    /**
     * Computes real treasury currently available above the protected reserve floor.
     *
     * @return non-negative discretionary balance
     */
    public long discretionaryTreasuryMilliCredits() {
        return treasuryMilliCredits <= treasuryReserveFloorMilliCredits
                ? 0L
                : treasuryMilliCredits - treasuryReserveFloorMilliCredits;
    }

    @Override
    public int compareTo(FactionEconomicState other) {
        return factionContentId.compareTo(
                Objects.requireNonNull(other, "FactionEconomicState не задан").factionContentId);
    }
}
