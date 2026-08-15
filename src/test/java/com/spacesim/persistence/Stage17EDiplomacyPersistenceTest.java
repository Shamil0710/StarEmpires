package com.spacesim.persistence;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.world.DiplomaticEmbargoState;
import com.spacesim.world.DiplomaticGrievanceState;
import com.spacesim.world.DiplomaticStandingState;
import com.spacesim.world.DiplomaticTreatyClauseState;
import com.spacesim.world.DiplomaticTreatyState;
import com.spacesim.world.FactionDiplomacyState;
import com.spacesim.world.WorldSimulation;
import com.spacesim.world.WorldState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Stage17EDiplomacyPersistenceTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";
    private static final String MINERS = "faction.miners";
    private static final String NEUTRAL = "faction.neutral";

    @Test
    void explicitDiplomacyRoundTripsWithoutChangingPhysicalWorldState() {
        WorldState base = DemoGalaxyFactory.createState(17_501L, ContentCatalogLoader.loadDefault());
        List<FactionDiplomacyState> diplomacy = new ArrayList<>();
        for (FactionDiplomacyState state : base.factionDiplomacyStates()) {
            if (!state.factionContentId().equals(TRADE_LEAGUE)) {
                diplomacy.add(state);
                continue;
            }
            diplomacy.add(new FactionDiplomacyState(
                    TRADE_LEAGUE,
                    List.of(new DiplomaticStandingState(MINERS, 23, 71, 120L)),
                    List.of(new DiplomaticGrievanceState(
                            "grievance.trade_league.miners.1",
                            MINERS,
                            DiplomaticGrievanceState.Kind.ECONOMIC_HARM,
                            35,
                            100L,
                            500L,
                            "route:corona")),
                    List.of(new DiplomaticTreatyState(
                            "treaty.trade_league.miners.market.1",
                            MINERS,
                            DiplomaticTreatyState.Status.ACTIVE,
                            90L,
                            100L,
                            -1L,
                            List.of(new DiplomaticTreatyClauseState(
                                    DiplomaticTreatyClauseState.Kind.MARKET_ACCESS,
                                    DiplomaticTreatyClauseState.Direction.OWNER_TO_COUNTERPARTY,
                                    null)))),
                    List.of(new DiplomaticEmbargoState(
                            NEUTRAL,
                            DiplomaticEmbargoState.Scope.MARKET_ACCESS,
                            110L,
                            600L,
                            "test-embargo"))));
        }

        WorldState explicit = new WorldState(
                base.schemaVersion(),
                base.topology(),
                base.systems(),
                base.factions(),
                base.factionStrategies(),
                base.nextConstructionProjectIdValue(),
                base.constructionProjects(),
                base.factionEconomicPressures(),
                base.nextFleetIdValue(),
                base.fleets(),
                base.fleetJumps(),
                base.factionIdentities(),
                diplomacy);

        byte[] encoded = WorldStateCodec.encode(explicit);
        WorldState decoded = WorldStateCodec.decode(encoded);

        assertEquals(explicit.factionDiplomacyStates(), decoded.factionDiplomacyStates());
        assertEquals(explicit.topology(), decoded.topology());
        assertEquals(explicit.systems(), decoded.systems());
        assertEquals(explicit.factions(), decoded.factions());
        assertEquals(explicit.factionStrategies(), decoded.factionStrategies());
        assertEquals(explicit.constructionProjects(), decoded.constructionProjects());
        assertEquals(explicit.fleets(), decoded.fleets());
        assertEquals(explicit.fleetJumps(), decoded.fleetJumps());

        WorldSimulation restored = WorldSimulation.restore(
                decoded,
                ContentCatalogLoader.loadDefault(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        assertEquals(
                decoded.factionDiplomacyStates(),
                restored.snapshot().factionDiplomacyStates());
        assertEquals(
                diplomacy.stream()
                        .filter(state -> state.factionContentId().equals(TRADE_LEAGUE))
                        .findFirst()
                        .orElseThrow(),
                restored.findFactionDiplomacyState(TRADE_LEAGUE).orElseThrow());
    }
}
