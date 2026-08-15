package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.FactionMarketAccessComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.controllers.TradeController;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.WorldStateCodec;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17EMarketAccessPrecedenceAcceptanceTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";
    private static final String MINERS = "faction.miners";
    private static final String DYNAMIC = "faction.player.diplomacy_access";

    @Test
    void embargoOverridesTreatyThenExpiryRestoresTreatyAccessInLiveEcsAndSaveLoad() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState base = DemoGalaxyFactory.createState(17_510L, content);
        WorldState diplomatic = withTradeLeagueThresholdAndDiplomacy(
                base,
                MINERS,
                new DiplomaticEmbargoState(
                        TRADE_LEAGUE,
                        DiplomaticEmbargoState.Scope.MARKET_ACCESS,
                        0L,
                        30L,
                        "acceptance-embargo"));
        WorldSimulation world = restore(diplomatic, content);
        int tradeLeagueRuntimeId = world.findFactionRuntimeId(TRADE_LEAGUE).orElseThrow();
        int minersRuntimeId = world.findFactionRuntimeId(MINERS).orElseThrow();
        EntityId stationId = createMarket(world, tradeLeagueRuntimeId, "Treaty Test Market");
        FactionPolicyRefreshService.refresh(world, content);

        DiplomaticMarketAccessResolver.Decision blocked =
                world.evaluateFactionMarketAccess(TRADE_LEAGUE, MINERS);
        assertFalse(blocked.allowed());
        assertEquals(DiplomaticMarketAccessResolver.Reason.EMBARGO, blocked.reason());
        assertFalse(canTrade(world, stationId, minersRuntimeId));

        while (world.getAuthoritativeWorldTick() < 35L) {
            world.advanceFrame(1f);
        }

        DiplomaticMarketAccessResolver.Decision treaty =
                world.evaluateFactionMarketAccess(TRADE_LEAGUE, MINERS);
        assertTrue(treaty.allowed());
        assertEquals(DiplomaticMarketAccessResolver.Reason.EXPLICIT_TREATY_RIGHT, treaty.reason());
        assertEquals("treaty.trade_league.access", treaty.instrumentId());
        assertTrue(canTrade(world, stationId, minersRuntimeId),
                "Expiry refresh must rematerialize the station access component without a manual UI action");

        WorldSimulation restored = restore(
                WorldStateCodec.decode(WorldStateCodec.encode(world.snapshot())),
                content);
        assertEquals(
                DiplomaticMarketAccessResolver.Reason.EXPLICIT_TREATY_RIGHT,
                restored.evaluateFactionMarketAccess(TRADE_LEAGUE, MINERS).reason());
        assertTrue(canTrade(restored, stationId, minersRuntimeId));
    }

    @Test
    void explicitTreatyAccessSupportsWorldDefinedFactionRuntimeSlots() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState base = DemoGalaxyFactory.createState(17_511L, content);
        int dynamicRuntimeId = Constants.LEGACY_FACTION_COUNT;

        List<FactionEconomicState> economy = new ArrayList<>(base.factions());
        economy.add(new FactionEconomicState(DYNAMIC, 0L, 0L, 0L));

        List<FactionStrategicState> strategies = new ArrayList<>();
        for (FactionStrategicState strategy : base.factionStrategies()) {
            strategies.add(strategy.factionContentId().equals(TRADE_LEAGUE)
                    ? copyWithMarketThreshold(strategy, 50)
                    : strategy);
        }
        strategies.add(new FactionStrategicState(DYNAMIC, 0, List.of(), List.of()));

        List<WorldFactionIdentityState> identities = new ArrayList<>(base.factionIdentities());
        identities.add(new WorldFactionIdentityState(
                DYNAMIC,
                dynamicRuntimeId,
                "Diplomacy Access Test Faction",
                WorldFactionIdentityState.Origin.PLAYER_CREATED));

        List<FactionDiplomacyState> diplomacy = new ArrayList<>();
        for (FactionDiplomacyState state : base.factionDiplomacyStates()) {
            if (state.factionContentId().equals(TRADE_LEAGUE)) {
                diplomacy.add(treatyDirectory(TRADE_LEAGUE, DYNAMIC, List.of()));
            } else {
                diplomacy.add(state);
            }
        }
        diplomacy.add(FactionDiplomacyState.neutral(DYNAMIC));

        WorldState state = new WorldState(
                base.schemaVersion(),
                base.topology(),
                base.systems(),
                economy,
                strategies,
                base.nextConstructionProjectIdValue(),
                base.constructionProjects(),
                base.factionEconomicPressures(),
                base.nextFleetIdValue(),
                base.fleets(),
                base.fleetJumps(),
                identities,
                diplomacy);
        WorldSimulation world = restore(state, content);
        assertEquals(dynamicRuntimeId, world.findFactionRuntimeId(DYNAMIC).orElseThrow());

        int tradeLeagueRuntimeId = world.findFactionRuntimeId(TRADE_LEAGUE).orElseThrow();
        EntityId stationId = createMarket(world, tradeLeagueRuntimeId, "Dynamic Treaty Market");
        FactionPolicyRefreshService.refresh(world, content);

        DiplomaticMarketAccessResolver.Decision decision =
                world.evaluateFactionMarketAccess(TRADE_LEAGUE, DYNAMIC);
        assertTrue(decision.allowed());
        assertEquals(DiplomaticMarketAccessResolver.Reason.EXPLICIT_TREATY_RIGHT, decision.reason());
        assertTrue(canTrade(world, stationId, dynamicRuntimeId));
    }

    private static WorldState withTradeLeagueThresholdAndDiplomacy(
            WorldState base,
            String treatyCounterparty,
            DiplomaticEmbargoState counterpartyEmbargo) {
        List<FactionStrategicState> strategies = new ArrayList<>();
        for (FactionStrategicState strategy : base.factionStrategies()) {
            strategies.add(strategy.factionContentId().equals(TRADE_LEAGUE)
                    ? copyWithMarketThreshold(strategy, 50)
                    : strategy);
        }

        List<FactionDiplomacyState> diplomacy = new ArrayList<>();
        for (FactionDiplomacyState state : base.factionDiplomacyStates()) {
            if (state.factionContentId().equals(TRADE_LEAGUE)) {
                diplomacy.add(treatyDirectory(TRADE_LEAGUE, treatyCounterparty, List.of()));
            } else if (state.factionContentId().equals(treatyCounterparty)) {
                diplomacy.add(new FactionDiplomacyState(
                        treatyCounterparty,
                        state.standings(),
                        state.grievances(),
                        state.treaties(),
                        List.of(counterpartyEmbargo)));
            } else {
                diplomacy.add(state);
            }
        }

        return new WorldState(
                base.schemaVersion(),
                base.topology(),
                base.systems(),
                base.factions(),
                strategies,
                base.nextConstructionProjectIdValue(),
                base.constructionProjects(),
                base.factionEconomicPressures(),
                base.nextFleetIdValue(),
                base.fleets(),
                base.fleetJumps(),
                base.factionIdentities(),
                diplomacy);
    }

    private static FactionDiplomacyState treatyDirectory(
            String owner,
            String counterparty,
            List<DiplomaticEmbargoState> embargoes) {
        return new FactionDiplomacyState(
                owner,
                List.of(),
                List.of(),
                List.of(new DiplomaticTreatyState(
                        "treaty.trade_league.access",
                        counterparty,
                        DiplomaticTreatyState.Status.ACTIVE,
                        0L,
                        0L,
                        -1L,
                        List.of(new DiplomaticTreatyClauseState(
                                DiplomaticTreatyClauseState.Kind.MARKET_ACCESS,
                                DiplomaticTreatyClauseState.Direction.OWNER_TO_COUNTERPARTY,
                                null)))),
                embargoes);
    }

    private static FactionStrategicState copyWithMarketThreshold(
            FactionStrategicState source,
            int threshold) {
        return new FactionStrategicState(
                source.factionContentId(),
                threshold,
                source.relations(),
                source.controlledSystems(),
                source.stationTaxBasisPoints(),
                source.foreignTerritoryTariffBasisPoints(),
                source.stockPolicies(),
                source.productionPolicies(),
                source.strategicGoals(),
                source.territorialClaims(),
                source.territorialControlStates(),
                source.territorialRecognitions(),
                source.constructionRightsGranted());
    }

    private static EntityId createMarket(
            WorldSimulation world,
            int ownerRuntimeId,
            String name) {
        return world.createEntity(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                new Entity()
                        .add(new IdentityComponent(name, IdentityComponent.Kind.STATION))
                        .add(new MarketComponent())
                        .add(new FactionComponent(ownerRuntimeId)));
    }

    private static boolean canTrade(
            WorldSimulation world,
            EntityId stationId,
            int participantRuntimeId) {
        SimulationSession session = world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow();
        Entity station = session.getEntityRegistry().find(stationId);
        Entity participant = new Entity().add(new FactionComponent(participantRuntimeId));
        FactionMarketAccessComponent access = station.getComponent(FactionMarketAccessComponent.class);
        assertEquals(
                world.evaluateFactionMarketAccess(TRADE_LEAGUE,
                        world.findFactionStableId(participantRuntimeId).orElseThrow()).allowed(),
                access.canTrade(participantRuntimeId));
        return new TradeController(session.getLedger()).canTradeWithStation(participant, station);
    }

    private static WorldSimulation restore(WorldState state, ContentCatalog content) {
        return WorldSimulation.restore(
                state,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
    }
}
