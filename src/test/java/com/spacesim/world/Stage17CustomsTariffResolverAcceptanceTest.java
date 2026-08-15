package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.model.ShipType;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.WorldStateCodec;
import com.spacesim.trade.FleetTradeProfile;
import com.spacesim.trade.TradeRouteCostModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Stage17CustomsTariffResolverAcceptanceTest {
    private static final String LEAGUE = "faction.trade_league";
    private static final String MINERS = "faction.miners";

    @Test
    void routePlannerCostUsesSameForeignCustomsRateAndActiveTreatyExemption() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        FactionIdentityResolver identities = FactionIdentityResolver.createDefault(content, List.of());
        int leagueRuntime = identities.runtimeId(LEAGUE).orElseThrow();
        int minersRuntime = identities.runtimeId(MINERS).orElseThrow();
        FleetTradeProfile fleet = new FleetTradeProfile(
                0f,
                0f,
                1f,
                1_000_000L,
                100,
                0,
                100,
                -1,
                false,
                (ShipType) null,
                minersRuntime,
                new int[Constants.MAX_ITEMS],
                new float[Constants.FACTION_RUNTIME_CAPACITY]);
        TradeRouteCostModel.Context context = new TradeRouteCostModel.Context(
                new EntityId(1L),
                new EntityId(2L),
                leagueRuntime,
                minersRuntime,
                0,
                1,
                100_000L,
                200_000L,
                10f,
                10d);

        List<FactionDiplomacyState> taxed = List.of(
                new FactionDiplomacyState(LEAGUE, List.of(), List.of(), List.of(), List.of(), 1_000),
                FactionDiplomacyState.neutral(MINERS));
        WorldTradeRouteCostModel taxedModel = new WorldTradeRouteCostModel(
                identities,
                List::of,
                () -> taxed,
                () -> 10L);
        assertEquals(10_000L, taxedModel.estimateCostMilliCredits(fleet, context));
        assertEquals(
                1_000,
                CustomsTariffResolver.evaluate(taxed, LEAGUE, MINERS, 10L).basisPoints());

        DiplomaticTreatyState exemption = new DiplomaticTreatyState(
                "treaty.customs-exemption",
                MINERS,
                DiplomaticTreatyState.Status.ACTIVE,
                0L,
                0L,
                -1L,
                List.of(new DiplomaticTreatyClauseState(
                        DiplomaticTreatyClauseState.Kind.CUSTOMS_TARIFF_EXEMPTION,
                        DiplomaticTreatyClauseState.Direction.OWNER_TO_COUNTERPARTY,
                        null)));
        List<FactionDiplomacyState> exempt = List.of(
                new FactionDiplomacyState(
                        LEAGUE,
                        List.of(),
                        List.of(),
                        List.of(exemption),
                        List.of(),
                        1_000),
                FactionDiplomacyState.neutral(MINERS));
        WorldTradeRouteCostModel exemptModel = new WorldTradeRouteCostModel(
                identities,
                List::of,
                () -> exempt,
                () -> 10L);
        CustomsTariffResolver.Decision decision = CustomsTariffResolver.evaluate(
                exempt, LEAGUE, MINERS, 10L);
        assertEquals(0, decision.basisPoints());
        assertEquals(CustomsTariffResolver.Reason.TREATY_EXEMPTION, decision.reason());
        assertEquals("treaty.customs-exemption", decision.instrumentId());
        assertEquals(0L, exemptModel.estimateCostMilliCredits(fleet, context));
    }

    @Test
    void customsRateSurvivesBinarySaveLoadWithoutChangingPhysicalWorld() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState base = DemoGalaxyFactory.createState(17_404L, content);
        List<FactionDiplomacyState> diplomacy = new ArrayList<>();
        for (FactionDiplomacyState state : base.factionDiplomacyStates()) {
            diplomacy.add(state.factionContentId().equals(LEAGUE)
                    ? new FactionDiplomacyState(
                            state.factionContentId(),
                            state.standings(),
                            state.grievances(),
                            state.treaties(),
                            state.embargoes(),
                            1_250)
                    : state);
        }
        WorldState source = new WorldState(
                WorldState.CURRENT_VERSION,
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

        WorldState decoded = WorldStateCodec.decode(WorldStateCodec.encode(source));
        assertEquals(source, decoded);
        assertEquals(base.systems(), decoded.systems());
        assertEquals(base.fleets(), decoded.fleets());
        assertEquals(base.fleetJumps(), decoded.fleetJumps());
        FactionDiplomacyState restoredLeague = decoded.factionDiplomacyStates().stream()
                .filter(state -> state.factionContentId().equals(LEAGUE))
                .findFirst()
                .orElseThrow();
        assertEquals(1_250, restoredLeague.customsTariffBasisPoints());
    }
}
