package com.spacesim.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionFiscalReviewProfileSelectorTest {
    private static final long RESERVE_TARGET = 1_000_000L;

    @Test
    void neutralDoctrineProducesStableExplicitBaseline() {
        FactionFiscalReviewProfile first = FactionFiscalReviewProfileSelector.select(
                FactionDoctrineState.neutral(), RESERVE_TARGET);
        FactionFiscalReviewProfile repeat = FactionFiscalReviewProfileSelector.select(
                FactionDoctrineState.neutral(), RESERVE_TARGET);

        assertEquals(first, repeat);
        assertEquals(3_500, first.liquidityStressEnterBasisPoints());
        assertEquals(1_166, first.liquidityStressExitBasisPoints());
        assertEquals(1_000, first.normalStationTaxTargetBasisPoints());
        assertEquals(600, first.stressStationTaxTargetBasisPoints());
        assertEquals(200, first.maxStationTaxStepBasisPoints());
        assertEquals(150_000L, first.normalLiquiditySupportCapMilliCredits());
        assertEquals(600_000L, first.stressLiquiditySupportCapMilliCredits());
        assertEquals(75_000L, first.maxLiquiditySupportCapStepMilliCredits());
    }

    @Test
    void tradeOpennessAndExpansionMoveNormalTaxMonotonicallyWithoutFactionIdentity() {
        FactionDoctrineState closed = doctrine(0, 50, 50, 50, 50, 50, 50);
        FactionDoctrineState open = doctrine(100, 50, 50, 50, 50, 50, 50);
        FactionDoctrineState lowExpansion = doctrine(50, 50, 0, 50, 50, 50, 50);
        FactionDoctrineState highExpansion = doctrine(50, 50, 100, 50, 50, 50, 50);

        FactionFiscalReviewProfile closedProfile = FactionFiscalReviewProfileSelector.select(closed, RESERVE_TARGET);
        FactionFiscalReviewProfile openProfile = FactionFiscalReviewProfileSelector.select(open, RESERVE_TARGET);
        FactionFiscalReviewProfile lowExpansionProfile = FactionFiscalReviewProfileSelector.select(
                lowExpansion, RESERVE_TARGET);
        FactionFiscalReviewProfile highExpansionProfile = FactionFiscalReviewProfileSelector.select(
                highExpansion, RESERVE_TARGET);

        assertTrue(openProfile.normalStationTaxTargetBasisPoints()
                < closedProfile.normalStationTaxTargetBasisPoints());
        assertTrue(highExpansionProfile.normalStationTaxTargetBasisPoints()
                > lowExpansionProfile.normalStationTaxTargetBasisPoints());
    }

    @Test
    void resilienceReactsEarlierAndAuthorizesMoreLiquiditySupport() {
        FactionFiscalReviewProfile low = FactionFiscalReviewProfileSelector.select(
                doctrine(50, 50, 50, 50, 50, 50, 0), RESERVE_TARGET);
        FactionFiscalReviewProfile high = FactionFiscalReviewProfileSelector.select(
                doctrine(50, 50, 50, 50, 50, 50, 100), RESERVE_TARGET);

        assertTrue(high.liquidityStressEnterBasisPoints() < low.liquidityStressEnterBasisPoints());
        assertTrue(high.liquidityStressExitBasisPoints() < low.liquidityStressExitBasisPoints());
        assertTrue(high.stressStationTaxTargetBasisPoints() < low.stressStationTaxTargetBasisPoints());
        assertTrue(high.normalLiquiditySupportCapMilliCredits() > low.normalLiquiditySupportCapMilliCredits());
        assertTrue(high.stressLiquiditySupportCapMilliCredits() > low.stressLiquiditySupportCapMilliCredits());
    }

    @Test
    void interventionismOnlyMakesBoundedResponseStepsFaster() {
        FactionFiscalReviewProfile low = FactionFiscalReviewProfileSelector.select(
                doctrine(50, 50, 50, 50, 50, 0, 50), RESERVE_TARGET);
        FactionFiscalReviewProfile high = FactionFiscalReviewProfileSelector.select(
                doctrine(50, 50, 50, 50, 50, 100, 50), RESERVE_TARGET);

        assertEquals(low.normalStationTaxTargetBasisPoints(), high.normalStationTaxTargetBasisPoints());
        assertEquals(low.stressStationTaxTargetBasisPoints(), high.stressStationTaxTargetBasisPoints());
        assertEquals(low.liquidityStressEnterBasisPoints(), high.liquidityStressEnterBasisPoints());
        assertEquals(low.normalLiquiditySupportCapMilliCredits(), high.normalLiquiditySupportCapMilliCredits());
        assertTrue(high.maxStationTaxStepBasisPoints() > low.maxStationTaxStepBasisPoints());
        assertTrue(high.maxLiquiditySupportCapStepMilliCredits()
                > low.maxLiquiditySupportCapStepMilliCredits());
    }

    @Test
    void zeroAndMaximumReserveScalesRemainValidWithoutOverflow() {
        FactionDoctrineState maximumResponse = doctrine(0, 100, 100, 100, 100, 100, 100);
        FactionFiscalReviewProfile zero = FactionFiscalReviewProfileSelector.select(maximumResponse, 0L);
        FactionFiscalReviewProfile maximum = FactionFiscalReviewProfileSelector.select(
                maximumResponse, Long.MAX_VALUE);

        assertEquals(0L, zero.normalLiquiditySupportCapMilliCredits());
        assertEquals(0L, zero.stressLiquiditySupportCapMilliCredits());
        assertEquals(1L, zero.maxLiquiditySupportCapStepMilliCredits());
        assertTrue(maximum.normalLiquiditySupportCapMilliCredits() >= 0L);
        assertTrue(maximum.stressLiquiditySupportCapMilliCredits()
                >= maximum.normalLiquiditySupportCapMilliCredits());
        assertTrue(maximum.maxLiquiditySupportCapStepMilliCredits() > 0L);
        assertTrue(maximum.stressLiquiditySupportCapMilliCredits() <= Long.MAX_VALUE);
    }

    private static FactionDoctrineState doctrine(
            int tradeOpenness,
            int securityPosture,
            int expansionPreference,
            int sovereigntySensitivity,
            int treatyLegalism,
            int interventionism,
            int economicResiliencePriority) {
        return new FactionDoctrineState(
                tradeOpenness,
                securityPosture,
                expansionPreference,
                sovereigntySensitivity,
                treatyLegalism,
                interventionism,
                economicResiliencePriority);
    }
}
