package com.spacesim.world;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Pure deterministic mapping from persistent faction doctrine to bounded fiscal-review targets.
 *
 * <p>The selector intentionally does not inspect current station-wallet stress. Doctrine defines the
 * faction's institutional response shape, while {@link FactionFiscalPolicyReviewer} continues to use
 * live {@link FactionFiscalPositionDiagnostics} as the economic signal. No faction ID/name exceptions,
 * random state or hidden prosperity score are used.</p>
 */
public final class FactionFiscalReviewProfileSelector {
    private static final int BASIS_POINTS = 10_000;
    private static final int MIN_NORMAL_STATION_TAX_BPS = 400;
    private static final int MAX_NORMAL_STATION_TAX_BPS = 1_600;

    private FactionFiscalReviewProfileSelector() {
        throw new AssertionError("Utility class");
    }

    /**
     * Derives one explicit review profile from doctrine and the real aggregate station-liquidity target.
     *
     * <p>Trade openness reduces the normal own-station tax target, expansion preference moderately
     * increases it, resilience lowers the stress-entry threshold and increases the stress support
     * envelope, while interventionism only increases the maximum adjustment step per review.</p>
     *
     * @param doctrine persistent institutional doctrine
     * @param aggregateLiquidityReserveTargetMilliCredits non-negative aggregate owned-market reserve target
     * @return deterministic bounded fiscal review profile
     */
    public static FactionFiscalReviewProfile select(
            FactionDoctrineState doctrine,
            long aggregateLiquidityReserveTargetMilliCredits) {
        FactionDoctrineState checked = Objects.requireNonNull(doctrine, "Faction doctrine not set");
        if (aggregateLiquidityReserveTargetMilliCredits < 0L) {
            throw new IllegalArgumentException("Aggregate liquidity reserve target cannot be negative");
        }

        int normalTax = clamp(
                1_000
                        - (checked.tradeOpenness() - 50) * 8
                        + (checked.expansionPreference() - 50) * 4,
                MIN_NORMAL_STATION_TAX_BPS,
                MAX_NORMAL_STATION_TAX_BPS);
        int stressTaxRelief = 200 + checked.economicResiliencePriority() * 4;
        int stressTax = Math.max(0, normalTax - stressTaxRelief);

        int stressEnter = 5_000 - checked.economicResiliencePriority() * 30;
        int stressExit = Math.max(500, stressEnter / 3);
        int maxTaxStep = 100 + checked.interventionism() * 2;

        int normalSupportShare = 1_000 + checked.economicResiliencePriority() * 10;
        int stressSupportShare = 4_000 + checked.economicResiliencePriority() * 40;
        int supportStepShare = 500 + checked.interventionism() * 5;
        long normalSupport = scaleBasisPoints(
                aggregateLiquidityReserveTargetMilliCredits, normalSupportShare);
        long stressSupport = scaleBasisPoints(
                aggregateLiquidityReserveTargetMilliCredits, stressSupportShare);
        long supportStep = Math.max(
                1L,
                scaleBasisPoints(aggregateLiquidityReserveTargetMilliCredits, supportStepShare));

        return new FactionFiscalReviewProfile(
                stressEnter,
                stressExit,
                normalTax,
                stressTax,
                maxTaxStep,
                normalSupport,
                stressSupport,
                supportStep);
    }

    private static long scaleBasisPoints(long value, int basisPoints) {
        if (value == 0L || basisPoints == 0) {
            return 0L;
        }
        BigInteger scaled = BigInteger.valueOf(value)
                .multiply(BigInteger.valueOf(basisPoints))
                .divide(BigInteger.valueOf(BASIS_POINTS));
        return scaled.longValueExact();
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
