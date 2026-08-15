package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17F6PolicyReviewCoordinatorAcceptanceTest {
    private static final String MINERS = "faction.miners";
    private static final String TRADE_LEAGUE = "faction.trade_league";
    private static final String UNSELECTED = "faction.neutral";

    @Test
    void explicitAutonomousSetUsesOneSharedClaimPerFactionInStableOrder() {
        WorldSimulation world = world(0x17F60031L);
        long firstDueTick = Math.max(
                FactionPolicyReviewCadence.defaultForFaction(MINERS).firstReviewOffsetTicks(),
                FactionPolicyReviewCadence.defaultForFaction(TRADE_LEAGUE).firstReviewOffsetTicks());
        advanceToTick(world, firstDueTick);
        long observationTick = world.getAuthoritativeWorldTick();
        long minersTreasuryBefore = treasury(world, MINERS);
        long leagueTreasuryBefore = treasury(world, TRADE_LEAGUE);
        long neutralTreasuryBefore = treasury(world, UNSELECTED);

        FactionPolicyReviewCoordinator.Report first = FactionPolicyReviewCoordinator.reviewFiscalPolicies(
                world,
                List.of(TRADE_LEAGUE, MINERS, TRADE_LEAGUE));

        assertEquals(observationTick, first.observationTick());
        assertEquals(2, first.factionReviews().size());
        assertEquals(List.of(MINERS, TRADE_LEAGUE),
                first.factionReviews().stream()
                        .map(FactionPolicyReviewCoordinator.FactionReview::factionContentId)
                        .toList());
        assertEquals(2L, first.claimedReviewCount());
        assertTrue(first.factionReviews().stream()
                .allMatch(FactionPolicyReviewCoordinator.FactionReview::reviewClaimed));
        assertEquals(observationTick,
                world.findFactionPolicyReviewState(MINERS).orElseThrow().lastPolicyReviewTick());
        assertEquals(observationTick,
                world.findFactionPolicyReviewState(TRADE_LEAGUE).orElseThrow().lastPolicyReviewTick());
        assertFalse(world.findFactionPolicyReviewState(UNSELECTED).orElseThrow().reviewed());
        assertEquals(minersTreasuryBefore, treasury(world, MINERS));
        assertEquals(leagueTreasuryBefore, treasury(world, TRADE_LEAGUE));
        assertEquals(neutralTreasuryBefore, treasury(world, UNSELECTED));

        FactionFiscalPolicyState minersPolicyAfterFirst = world.findFactionFiscalPolicy(MINERS).orElseThrow();
        FactionFiscalPolicyState leaguePolicyAfterFirst = world.findFactionFiscalPolicy(TRADE_LEAGUE).orElseThrow();
        FactionPolicyReviewCoordinator.Report repeated = FactionPolicyReviewCoordinator.reviewFiscalPolicies(
                world,
                List.of(MINERS, TRADE_LEAGUE));

        assertEquals(observationTick, repeated.observationTick());
        assertEquals(0L, repeated.claimedReviewCount());
        assertEquals(0L, repeated.changedFiscalPolicyCount());
        assertTrue(repeated.factionReviews().stream()
                .noneMatch(FactionPolicyReviewCoordinator.FactionReview::reviewClaimed));
        assertEquals(minersPolicyAfterFirst, world.findFactionFiscalPolicy(MINERS).orElseThrow());
        assertEquals(leaguePolicyAfterFirst, world.findFactionFiscalPolicy(TRADE_LEAGUE).orElseThrow());
        assertEquals(observationTick,
                world.findFactionPolicyReviewState(MINERS).orElseThrow().lastPolicyReviewTick());
        assertEquals(observationTick,
                world.findFactionPolicyReviewState(TRADE_LEAGUE).orElseThrow().lastPolicyReviewTick());
        assertFalse(world.findFactionPolicyReviewState(UNSELECTED).orElseThrow().reviewed());
        assertEquals(minersTreasuryBefore, treasury(world, MINERS));
        assertEquals(leagueTreasuryBefore, treasury(world, TRADE_LEAGUE));
        assertEquals(neutralTreasuryBefore, treasury(world, UNSELECTED));
    }

    @Test
    void emptyAutonomousSetDoesNotClaimAnyFactionReviewWindow() {
        WorldSimulation world = world(0x17F60032L);

        FactionPolicyReviewCoordinator.Report report = FactionPolicyReviewCoordinator.reviewFiscalPolicies(
                world, List.of());

        assertTrue(report.factionReviews().isEmpty());
        assertEquals(0L, report.claimedReviewCount());
        assertFalse(world.findFactionPolicyReviewState(MINERS).orElseThrow().reviewed());
        assertFalse(world.findFactionPolicyReviewState(TRADE_LEAGUE).orElseThrow().reviewed());
        assertFalse(world.findFactionPolicyReviewState(UNSELECTED).orElseThrow().reviewed());
    }

    private static WorldSimulation world(long seed) {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        return WorldSimulation.restore(
                DemoGalaxyFactory.createState(seed, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
    }

    private static long treasury(WorldSimulation world, String factionContentId) {
        return world.findFactionEconomicState(factionContentId).orElseThrow().treasuryMilliCredits();
    }

    private static void advanceToTick(WorldSimulation world, long targetTick) {
        float fixedStep = world.findSession(world.getActiveSystemId()).orElseThrow()
                .getClock().getFixedStepSeconds();
        int guard = 0;
        while (world.getAuthoritativeWorldTick() < targetTick && guard++ < 20_000) {
            world.advanceFrame(fixedStep);
        }
        assertTrue(world.getAuthoritativeWorldTick() >= targetTick, "World did not reach requested review tick");
    }
}
