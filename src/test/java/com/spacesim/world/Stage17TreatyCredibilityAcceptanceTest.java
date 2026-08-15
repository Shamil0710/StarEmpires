package com.spacesim.world;

import com.spacesim.content.ContentCatalogLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17TreatyCredibilityAcceptanceTest {
    private static final String OWNER = "faction.neutral";
    private static final String PARTNER = "faction.trade_league";

    @Test
    void naturallyCompletedObservableTreatyBuildsDirectedTrustAndCredibilityExactlyOnce() {
        FactionDiplomacyRuntime runtime = runtimeWith(
                activeTreaty(
                        "treaty.honored-market-access",
                        DiplomaticTreatyClauseState.Kind.MARKET_ACCESS),
                List.of());

        assertTrue(runtime.advanceTime(10L));

        DiplomaticTreatyState expired = runtime.findTreaty("treaty.honored-market-access");
        assertEquals(DiplomaticTreatyState.Status.EXPIRED, expired.status());
        assertStanding(runtime.find(OWNER).standingToward(PARTNER), 4, 55, 10L);
        assertStanding(runtime.find(PARTNER).standingToward(OWNER), 4, 55, 10L);

        assertFalse(runtime.advanceTime(20L));
        assertStanding(runtime.find(OWNER).standingToward(PARTNER), 4, 55, 10L);
        assertStanding(runtime.find(PARTNER).standingToward(OWNER), 4, 55, 10L);
    }

    @Test
    void terminationWithNoticeExpiresWithoutPositiveComplianceReward() {
        DiplomaticTreatyState terminating = new DiplomaticTreatyState(
                "treaty.terminating",
                PARTNER,
                DiplomaticTreatyState.Status.TERMINATING,
                0L,
                0L,
                10L,
                List.of(new DiplomaticTreatyClauseState(
                        DiplomaticTreatyClauseState.Kind.MARKET_ACCESS,
                        DiplomaticTreatyClauseState.Direction.MUTUAL,
                        null)));
        FactionDiplomacyRuntime runtime = runtimeWith(terminating, List.of());

        assertTrue(runtime.advanceTime(10L));
        assertEquals(DiplomaticTreatyState.Status.EXPIRED, runtime.findTreaty("treaty.terminating").status());
        assertNull(runtime.find(OWNER).standingToward(PARTNER));
        assertNull(runtime.find(PARTNER).standingToward(OWNER));
    }

    @Test
    void unobservableSupplyObligationGetsNoAutomaticCredibilityReward() {
        FactionDiplomacyRuntime runtime = runtimeWith(
                activeTreaty(
                        "treaty.resource-supply",
                        DiplomaticTreatyClauseState.Kind.RESOURCE_SUPPLY),
                List.of());

        assertTrue(runtime.advanceTime(10L));
        assertEquals(DiplomaticTreatyState.Status.EXPIRED, runtime.findTreaty("treaty.resource-supply").status());
        assertNull(runtime.find(OWNER).standingToward(PARTNER));
        assertNull(runtime.find(PARTNER).standingToward(OWNER));
    }

    @Test
    void embargoDuringObservableTreatySuppressesPositiveCompletionReward() {
        DiplomaticGrievanceState embargoHistory = new DiplomaticGrievanceState(
                "grievance.embargo-during-treaty",
                PARTNER,
                DiplomaticGrievanceState.Kind.EMBARGO,
                40,
                5L,
                -1L,
                "market-access");
        FactionDiplomacyRuntime runtime = runtimeWith(
                activeTreaty(
                        "treaty.embargoed-market-access",
                        DiplomaticTreatyClauseState.Kind.MARKET_ACCESS),
                List.of(embargoHistory));

        assertTrue(runtime.advanceTime(10L));
        assertNull(runtime.find(OWNER).standingToward(PARTNER));
        assertNull(runtime.find(PARTNER).standingToward(OWNER));
        assertEquals(1, runtime.find(OWNER).grievances().size());
    }

    private static FactionDiplomacyRuntime runtimeWith(
            DiplomaticTreatyState treaty,
            List<DiplomaticGrievanceState> ownerGrievances) {
        FactionIdentityResolver identities = FactionIdentityResolver.createDefault(
                ContentCatalogLoader.loadDefault(),
                List.of());
        return new FactionDiplomacyRuntime(
                identities,
                List.of(
                        new FactionDiplomacyState(
                                OWNER,
                                List.of(),
                                ownerGrievances,
                                List.of(treaty),
                                List.of()),
                        FactionDiplomacyState.neutral(PARTNER),
                        FactionDiplomacyState.neutral("faction.miners")));
    }

    private static DiplomaticTreatyState activeTreaty(
            String treatyId,
            DiplomaticTreatyClauseState.Kind kind) {
        return new DiplomaticTreatyState(
                treatyId,
                PARTNER,
                DiplomaticTreatyState.Status.ACTIVE,
                0L,
                0L,
                10L,
                List.of(new DiplomaticTreatyClauseState(
                        kind,
                        DiplomaticTreatyClauseState.Direction.MUTUAL,
                        null)));
    }

    private static void assertStanding(
            DiplomaticStandingState standing,
            int trust,
            int credibility,
            long tick) {
        assertEquals(trust, standing.trust());
        assertEquals(credibility, standing.credibility());
        assertEquals(tick, standing.lastUpdatedTick());
    }
}
