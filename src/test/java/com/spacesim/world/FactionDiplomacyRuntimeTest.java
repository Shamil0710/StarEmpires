package com.spacesim.world;

import com.spacesim.content.ContentCatalogLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionDiplomacyRuntimeTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";
    private static final String MINERS = "faction.miners";

    @Test
    void futureTreatyAndEmbargoBoundariesInvalidateMarketAccessProjectionDeterministically() {
        FactionIdentityResolver identities = FactionIdentityResolver.createDefault(
                ContentCatalogLoader.loadDefault(), List.of());
        FactionDiplomacyState diplomacy = new FactionDiplomacyState(
                TRADE_LEAGUE,
                List.of(),
                List.of(),
                List.of(new DiplomaticTreatyState(
                        "treaty.scheduled-access",
                        MINERS,
                        DiplomaticTreatyState.Status.ACTIVE,
                        0L,
                        20L,
                        50L,
                        List.of(new DiplomaticTreatyClauseState(
                                DiplomaticTreatyClauseState.Kind.MARKET_ACCESS,
                                DiplomaticTreatyClauseState.Direction.OWNER_TO_COUNTERPARTY,
                                null)))),
                List.of(new DiplomaticEmbargoState(
                        MINERS,
                        DiplomaticEmbargoState.Scope.MARKET_ACCESS,
                        30L,
                        40L,
                        "scheduled-embargo")));
        FactionDiplomacyRuntime runtime = new FactionDiplomacyRuntime(
                identities, List.of(diplomacy));

        runtime.noteMarketAccessPolicyRefreshed(0L);
        assertFalse(runtime.marketAccessExpiryCrossed(19L));
        assertTrue(runtime.marketAccessExpiryCrossed(20L),
                "Future treaty activation must invalidate the cached access projection");

        runtime.noteMarketAccessPolicyRefreshed(20L);
        assertFalse(runtime.marketAccessExpiryCrossed(29L));
        assertTrue(runtime.marketAccessExpiryCrossed(30L),
                "Future embargo imposition must invalidate the cached access projection");

        runtime.noteMarketAccessPolicyRefreshed(30L);
        assertFalse(runtime.marketAccessExpiryCrossed(39L));
        assertTrue(runtime.marketAccessExpiryCrossed(40L),
                "Embargo expiry must invalidate the cached access projection");

        runtime.noteMarketAccessPolicyRefreshed(40L);
        assertFalse(runtime.marketAccessExpiryCrossed(49L));
        assertTrue(runtime.marketAccessExpiryCrossed(50L),
                "Treaty expiry must invalidate the cached access projection");

        runtime.noteMarketAccessPolicyRefreshed(50L);
        assertFalse(runtime.marketAccessExpiryCrossed(Long.MAX_VALUE));
    }
}
