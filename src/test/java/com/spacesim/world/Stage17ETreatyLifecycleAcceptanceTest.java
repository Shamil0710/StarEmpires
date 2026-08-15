package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.MarketComponent;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17ETreatyLifecycleAcceptanceTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";
    private static final String MINERS = "faction.miners";
    private static final String NEUTRAL = "faction.neutral";

    @Test
    void offerSurvivesSaveLoadAcceptActivatesAccessAndNoticeExpiryRemovesIt() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = restore(withTradeLeagueThreshold(
                DemoGalaxyFactory.createState(17_520L, content), 50), content);
        int tradeLeagueRuntimeId = world.findFactionRuntimeId(TRADE_LEAGUE).orElseThrow();
        int minersRuntimeId = world.findFactionRuntimeId(MINERS).orElseThrow();
        EntityId marketId = createMarket(world, tradeLeagueRuntimeId, "Lifecycle Market");
        FactionPolicyRefreshService.refresh(world, content);
        assertFalse(canTrade(world, marketId, minersRuntimeId));

        DiplomaticTreatyCommandResult offered = world.applyDiplomaticTreatyCommand(
                new DiplomaticTreatyCommand.Offer(
                        TRADE_LEAGUE,
                        MINERS,
                        List.of(marketAccess(DiplomaticTreatyClauseState.Direction.OWNER_TO_COUNTERPARTY)),
                        -1L));
        assertEquals(DiplomaticTreatyCommandResult.Operation.OFFERED, offered.operation());
        assertEquals(DiplomaticTreatyState.Status.PROPOSED, offered.treaty().status());
        assertFalse(canTrade(world, marketId, minersRuntimeId),
                "Proposal must not grant rights before acceptance");

        world = restore(WorldStateCodec.decode(WorldStateCodec.encode(world.snapshot())), content);
        assertEquals(
                DiplomaticTreatyState.Status.PROPOSED,
                world.findDiplomaticTreaty(offered.treaty().treatyId()).orElseThrow().status());

        DiplomaticTreatyCommandResult accepted = world.applyDiplomaticTreatyCommand(
                new DiplomaticTreatyCommand.Accept(MINERS, offered.treaty().treatyId()));
        assertEquals(DiplomaticTreatyCommandResult.Operation.ACCEPTED, accepted.operation());
        assertEquals(DiplomaticTreatyState.Status.ACTIVE, accepted.treaty().status());
        assertTrue(canTrade(world, marketId, minersRuntimeId),
                "Accepted explicit market right must reach ordinary TradeController immediately");

        long noticeStart = world.getAuthoritativeWorldTick();
        DiplomaticTreatyCommandResult terminating = world.applyDiplomaticTreatyCommand(
                new DiplomaticTreatyCommand.TerminateWithNotice(
                        TRADE_LEAGUE,
                        accepted.treaty().treatyId(),
                        30L));
        assertEquals(DiplomaticTreatyState.Status.TERMINATING, terminating.treaty().status());
        assertEquals(noticeStart + 30L, terminating.treaty().expiresTick());
        assertTrue(canTrade(world, marketId, minersRuntimeId),
                "Treaty obligations remain in force during notice");

        advanceToAtLeast(world, noticeStart + 31L);
        assertEquals(
                DiplomaticTreatyState.Status.EXPIRED,
                world.findDiplomaticTreaty(accepted.treaty().treatyId()).orElseThrow().status());
        assertFalse(canTrade(world, marketId, minersRuntimeId),
                "Expired treaty must fall back to the denying directed-relation threshold");

        WorldSimulation restored = restore(
                WorldStateCodec.decode(WorldStateCodec.encode(world.snapshot())), content);
        assertEquals(
                DiplomaticTreatyState.Status.EXPIRED,
                restored.findDiplomaticTreaty(accepted.treaty().treatyId()).orElseThrow().status());
        assertFalse(canTrade(restored, marketId, minersRuntimeId));
    }

    @Test
    void breachIsImmediateCreatesDirectedGrievanceAndDamagesTrustAndCredibility() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = restore(withTradeLeagueThreshold(
                DemoGalaxyFactory.createState(17_521L, content), 50), content);
        int tradeLeagueRuntimeId = world.findFactionRuntimeId(TRADE_LEAGUE).orElseThrow();
        int minersRuntimeId = world.findFactionRuntimeId(MINERS).orElseThrow();
        EntityId marketId = createMarket(world, tradeLeagueRuntimeId, "Breach Market");
        FactionPolicyRefreshService.refresh(world, content);

        DiplomaticTreatyState active = activateMarketTreaty(world, -1L);
        assertTrue(canTrade(world, marketId, minersRuntimeId));
        WorldState beforeUnauthorized = world.snapshot();
        assertThrows(
                IllegalArgumentException.class,
                () -> world.applyDiplomaticTreatyCommand(
                        new DiplomaticTreatyCommand.Breach(
                                NEUTRAL, active.treatyId(), "not-a-party")));
        assertEquals(beforeUnauthorized, world.snapshot(),
                "Unrelated faction must not mutate treaty or economic world state");

        DiplomaticTreatyCommandResult breached = world.applyDiplomaticTreatyCommand(
                new DiplomaticTreatyCommand.Breach(
                        MINERS,
                        active.treatyId(),
                        "market-access-refusal"));
        assertEquals(DiplomaticTreatyCommandResult.Operation.BREACHED, breached.operation());
        assertEquals(DiplomaticTreatyState.Status.BREACHED, breached.treaty().status());
        assertEquals(TRADE_LEAGUE, breached.offendedFactionContentId());
        assertFalse(canTrade(world, marketId, minersRuntimeId),
                "Breach must stop ordinary treaty rights without creating an abstract economic debuff");

        FactionDiplomacyState offended = world.findFactionDiplomacyState(TRADE_LEAGUE).orElseThrow();
        DiplomaticGrievanceState grievance = offended.grievances().stream()
                .filter(value -> value.targetFactionContentId().equals(MINERS)
                        && value.kind() == DiplomaticGrievanceState.Kind.TREATY_BREACH)
                .findFirst()
                .orElseThrow();
        assertEquals(60, grievance.severity());
        assertTrue(grievance.subjectKey().contains(active.treatyId()));
        assertEquals(-20, offended.trustTo(MINERS));
        assertEquals(25, offended.credibilityOf(MINERS));

        WorldSimulation restored = restore(
                WorldStateCodec.decode(WorldStateCodec.encode(world.snapshot())), content);
        assertEquals(DiplomaticTreatyState.Status.BREACHED,
                restored.findDiplomaticTreaty(active.treatyId()).orElseThrow().status());
        assertEquals(-20, restored.findFactionDiplomacyState(TRADE_LEAGUE).orElseThrow().trustTo(MINERS));
        assertEquals(25, restored.findFactionDiplomacyState(TRADE_LEAGUE).orElseThrow().credibilityOf(MINERS));
    }

    @Test
    void counterofferRejectRenewAndAutomaticExpiryUseOnePersistentLifecycle() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = restore(withTradeLeagueThreshold(
                DemoGalaxyFactory.createState(17_522L, content), 50), content);
        int tradeLeagueRuntimeId = world.findFactionRuntimeId(TRADE_LEAGUE).orElseThrow();
        int minersRuntimeId = world.findFactionRuntimeId(MINERS).orElseThrow();
        EntityId marketId = createMarket(world, tradeLeagueRuntimeId, "Renewal Market");
        FactionPolicyRefreshService.refresh(world, content);

        DiplomaticTreatyCommandResult original = world.applyDiplomaticTreatyCommand(
                new DiplomaticTreatyCommand.Offer(
                        TRADE_LEAGUE,
                        MINERS,
                        List.of(new DiplomaticTreatyClauseState(
                                DiplomaticTreatyClauseState.Kind.TRANSIT_RIGHT,
                                DiplomaticTreatyClauseState.Direction.MUTUAL,
                                null)),
                        50L));
        DiplomaticTreatyCommandResult counter = world.applyDiplomaticTreatyCommand(
                new DiplomaticTreatyCommand.CounterOffer(
                        MINERS,
                        original.treaty().treatyId(),
                        List.of(marketAccess(DiplomaticTreatyClauseState.Direction.COUNTERPARTY_TO_OWNER)),
                        50L));
        assertEquals(DiplomaticTreatyCommandResult.Operation.COUNTEROFFERED, counter.operation());
        assertEquals(original.treaty().treatyId(), counter.relatedTreatyId());
        assertEquals(DiplomaticTreatyState.Status.REJECTED,
                world.findDiplomaticTreaty(original.treaty().treatyId()).orElseThrow().status());
        assertEquals(DiplomaticTreatyState.Status.PROPOSED, counter.treaty().status());

        DiplomaticTreatyCommandResult accepted = world.applyDiplomaticTreatyCommand(
                new DiplomaticTreatyCommand.Accept(TRADE_LEAGUE, counter.treaty().treatyId()));
        assertTrue(canTrade(world, marketId, minersRuntimeId));

        DiplomaticTreatyCommandResult renewal = world.applyDiplomaticTreatyCommand(
                new DiplomaticTreatyCommand.Renew(
                        MINERS,
                        accepted.treaty().treatyId(),
                        120L));
        assertEquals(DiplomaticTreatyCommandResult.Operation.RENEWAL_OFFERED, renewal.operation());
        assertEquals(accepted.treaty().treatyId(), renewal.relatedTreatyId());
        assertEquals(accepted.treaty().clauses(), renewal.treaty().clauses());
        world.applyDiplomaticTreatyCommand(
                new DiplomaticTreatyCommand.Accept(TRADE_LEAGUE, renewal.treaty().treatyId()));

        DiplomaticTreatyCommandResult disposable = world.applyDiplomaticTreatyCommand(
                new DiplomaticTreatyCommand.Offer(
                        TRADE_LEAGUE,
                        NEUTRAL,
                        List.of(new DiplomaticTreatyClauseState(
                                DiplomaticTreatyClauseState.Kind.TRANSIT_RIGHT,
                                DiplomaticTreatyClauseState.Direction.MUTUAL,
                                null)),
                        -1L));
        DiplomaticTreatyCommandResult rejected = world.applyDiplomaticTreatyCommand(
                new DiplomaticTreatyCommand.Reject(NEUTRAL, disposable.treaty().treatyId()));
        assertEquals(DiplomaticTreatyState.Status.REJECTED, rejected.treaty().status());

        advanceToAtLeast(world, 60L);
        assertEquals(DiplomaticTreatyState.Status.EXPIRED,
                world.findDiplomaticTreaty(accepted.treaty().treatyId()).orElseThrow().status());
        assertEquals(DiplomaticTreatyState.Status.ACTIVE,
                world.findDiplomaticTreaty(renewal.treaty().treatyId()).orElseThrow().status());
        assertTrue(canTrade(world, marketId, minersRuntimeId),
                "Accepted renewal must preserve access after the old treaty expires");

        advanceToAtLeast(world, 121L);
        assertEquals(DiplomaticTreatyState.Status.EXPIRED,
                world.findDiplomaticTreaty(renewal.treaty().treatyId()).orElseThrow().status());
        assertFalse(canTrade(world, marketId, minersRuntimeId));
    }

    private static DiplomaticTreatyState activateMarketTreaty(WorldSimulation world, long expiresTick) {
        DiplomaticTreatyCommandResult offer = world.applyDiplomaticTreatyCommand(
                new DiplomaticTreatyCommand.Offer(
                        TRADE_LEAGUE,
                        MINERS,
                        List.of(marketAccess(DiplomaticTreatyClauseState.Direction.OWNER_TO_COUNTERPARTY)),
                        expiresTick));
        return world.applyDiplomaticTreatyCommand(
                new DiplomaticTreatyCommand.Accept(MINERS, offer.treaty().treatyId())).treaty();
    }

    private static DiplomaticTreatyClauseState marketAccess(
            DiplomaticTreatyClauseState.Direction direction) {
        return new DiplomaticTreatyClauseState(
                DiplomaticTreatyClauseState.Kind.MARKET_ACCESS,
                direction,
                null);
    }

    private static EntityId createMarket(WorldSimulation world, int ownerRuntimeId, String name) {
        return world.createEntity(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                new Entity()
                        .add(new IdentityComponent(name, IdentityComponent.Kind.STATION))
                        .add(new MarketComponent())
                        .add(new FactionComponent(ownerRuntimeId)));
    }

    private static boolean canTrade(WorldSimulation world, EntityId marketId, int participantRuntimeId) {
        SimulationSession session = world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow();
        Entity market = session.getEntityRegistry().find(marketId);
        Entity participant = new Entity().add(new FactionComponent(participantRuntimeId));
        return new TradeController(session.getLedger()).canTradeWithStation(participant, market);
    }

    private static void advanceToAtLeast(WorldSimulation world, long targetTick) {
        int guard = 0;
        while (world.getAuthoritativeWorldTick() < targetTick) {
            world.advanceFrame(1f);
            if (++guard > 10_000) {
                throw new AssertionError("World did not reach diplomatic target tick");
            }
        }
    }

    private static WorldState withTradeLeagueThreshold(WorldState base, int threshold) {
        List<FactionStrategicState> strategies = new ArrayList<>();
        for (FactionStrategicState source : base.factionStrategies()) {
            strategies.add(source.factionContentId().equals(TRADE_LEAGUE)
                    ? new FactionStrategicState(
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
                            source.constructionRightsGranted())
                    : source);
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
                base.factionDiplomacyStates());
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
