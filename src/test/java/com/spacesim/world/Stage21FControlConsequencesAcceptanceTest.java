package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityState;
import com.spacesim.trade.FleetTradeProfile;
import com.spacesim.trade.TradeRouteCostModel;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Stage21FControlConsequencesAcceptanceTest {
    private static final String PLAYER_FACTION = "faction.player.stage21f.consequences";
    private static final int PLAYER_RUNTIME_ID = Constants.LEGACY_FACTION_COUNT;
    private static final String FOREIGN_FACTION = "faction.trade_league";
    private static final StarSystemId TARGET = DemoGalaxyFactory.FRONTIER_SYSTEM_ID;
    private static final FleetId INVADER = new FleetId(21_630L);

    @Test
    void occupationControlChangesRouteTariffAndConstructionLawWithoutSeizingMarketAllegiance() {
        Fixture fixture = fixture(21_630L);
        WorldSimulation world = fixture.world();
        int foreignRuntimeId = world.findFactionRuntimeId(FOREIGN_FACTION).orElseThrow();

        world.createEntity(TARGET, new Entity()
                .add(new IdentityComponent("Occupation Control Anchor", IdentityComponent.Kind.STATION))
                .add(new MarketComponent())
                .add(new FactionComponent(PLAYER_RUNTIME_ID)));
        EntityId foreignStationId = world.createEntity(TARGET, new Entity()
                .add(new IdentityComponent("Existing Foreign Market", IdentityComponent.Kind.STATION))
                .add(new MarketComponent())
                .add(new WalletComponent())
                .add(new FactionComponent(foreignRuntimeId)));
        Entity foreignStation = world.findSession(TARGET).orElseThrow().getEntityRegistry().find(foreignStationId);
        WalletComponent foreignWallet = foreignStation.getComponent(WalletComponent.class);
        assertTrue(foreignWallet.creditFromSource(100_000L));

        WorldTradeRouteCostModel routeCosts = new WorldTradeRouteCostModel(
                fixture.identities(),
                () -> world.snapshot().factionStrategies(),
                () -> world.snapshot().factionDiplomacyStates(),
                world::getAuthoritativeWorldTick);
        FleetTradeProfile trader = new FleetTradeProfile(
                0f, 0f, 1f, 100_000L, 100, 0, 100, -1, false, null,
                PLAYER_RUNTIME_ID,
                new int[Constants.MAX_ITEMS],
                new float[Constants.FACTION_RUNTIME_CAPACITY]);
        TradeRouteCostModel.Context route = new TradeRouteCostModel.Context(
                foreignStationId,
                new EntityId(99_999L),
                foreignRuntimeId,
                PLAYER_RUNTIME_ID,
                0,
                1,
                10_000L,
                15_000L,
                0f,
                0d,
                TARGET,
                TARGET,
                new GalacticPath(List.of(TARGET), 0L, 0d, 0d),
                0);
        long routeCostBefore = routeCosts.estimateCostMilliCredits(trader, route);
        DiplomaticMarketAccessResolver.Decision accessBefore =
                world.evaluateFactionMarketAccess(FOREIGN_FACTION, PLAYER_FACTION);
        assertTrue(TerritorialConstructionAuthorization.evaluate(world, FOREIGN_FACTION, TARGET).allowed());

        TerritorialTransitionService service = new TerritorialTransitionService();
        StrategicOperationState operations = new StrategicOperationState(2L, List.of(activeInvasion()));
        FleetForceRegistry forces = new FleetForceRegistry(List.of(invaderForce()));
        long start = world.getAuthoritativeWorldTick();
        TerritorialTransitionService.AdvanceResult initial = service.advance(
                TerritorialTransitionState.empty(), world, operations, forces, fixture.identities(), 1L, start);
        advanceToAtLeast(world, start + TerritorialTransitionService.REQUIRED_OCCUPATION_TICKS);
        TerritorialTransitionService.AdvanceResult occupied = service.advance(
                initial.transitions(), world, initial.operations(), forces, fixture.identities(),
                1L, world.getAuthoritativeWorldTick());
        assertTrue(occupied.claimCreated());
        assertTrue(world.controllingFaction(TARGET).isEmpty());

        long claimTick = world.getAuthoritativeWorldTick();
        advanceToAtLeast(world, claimTick + TerritorialControlRuntime.REQUIRED_STABILIZATION_TICKS + 50L);
        assertEquals(PLAYER_FACTION, world.controllingFaction(TARGET).orElseThrow());
        TerritorialTransitionService.AdvanceResult established = service.advance(
                occupied.transitions(), world, occupied.operations(), forces, fixture.identities(),
                1L, world.getAuthoritativeWorldTick());
        assertTrue(established.occupation().controlEverEstablished());

        long routeCostAfter = routeCosts.estimateCostMilliCredits(trader, route);
        assertEquals(routeCostBefore + 1_000L, routeCostAfter,
                "existing route-cost authority must observe the new 10% territorial tariff");
        assertEquals(accessBefore, world.evaluateFactionMarketAccess(FOREIGN_FACTION, PLAYER_FACTION),
                "territorial control must not silently rewrite the foreign station owner's diplomacy policy");
        assertTrue(TerritorialConstructionAuthorization.evaluate(world, PLAYER_FACTION, TARGET).allowed());
        assertFalse(TerritorialConstructionAuthorization.evaluate(world, FOREIGN_FACTION, TARGET).allowed());

        FactionComponent foreignFaction = foreignStation.getComponent(FactionComponent.class);
        assertEquals(foreignRuntimeId, foreignFaction.factionId);
        assertEquals(100_000L, foreignWallet.getBalanceMilliCredits(),
                "control transfer must not seize an existing foreign station wallet");

        long treasuryBeforeFiscal = world.findFactionEconomicState(PLAYER_FACTION)
                .orElseThrow().treasuryMilliCredits();
        WorldSimulation.FiscalPolicyReport fiscal = world.applyFiscalPolicy(PLAYER_FACTION);
        assertTrue(fiscal.tariffCollectedMilliCredits() > 0L);
        assertEquals(treasuryBeforeFiscal + fiscal.tariffCollectedMilliCredits(),
                world.findFactionEconomicState(PLAYER_FACTION).orElseThrow().treasuryMilliCredits());
        assertEquals(90_000L, foreignWallet.getBalanceMilliCredits());
    }

    private static Fixture fixture(long seed) {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState base = DemoGalaxyFactory.create(seed).snapshot();
        List<FactionStrategicState> strategies = new ArrayList<>();
        for (FactionStrategicState strategy : base.factionStrategies()) {
            List<StarSystemId> controlled = strategy.controlledSystems().stream()
                    .filter(systemId -> !systemId.equals(TARGET))
                    .toList();
            strategies.add(new FactionStrategicState(
                    strategy.factionContentId(), strategy.minimumMarketAccessRelation(), strategy.relations(), controlled,
                    strategy.stationTaxBasisPoints(), strategy.foreignTerritoryTariffBasisPoints(),
                    strategy.stockPolicies(), strategy.productionPolicies(), strategy.strategicGoals()));
        }
        strategies.add(new FactionStrategicState(
                PLAYER_FACTION, 0, List.of(), List.of(), 0, 1_000, List.of(), List.of(), List.of()));
        List<FactionEconomicState> factions = new ArrayList<>(base.factions());
        factions.add(new FactionEconomicState(PLAYER_FACTION, 0L, 0L, 0L));
        List<WorldFactionIdentityState> identities = new ArrayList<>(base.factionIdentities());
        identities.add(new WorldFactionIdentityState(
                PLAYER_FACTION, PLAYER_RUNTIME_ID, "Stage 21F Consequence Faction",
                WorldFactionIdentityState.Origin.PLAYER_CREATED));
        WorldState state = new WorldState(
                WorldState.CURRENT_VERSION, base.topology(), base.systems(), factions, strategies,
                base.nextConstructionProjectIdValue(), base.constructionProjects(), base.factionEconomicPressures(),
                base.nextFleetIdValue(), base.fleets(), base.fleetJumps(), identities);
        WorldSimulation world = WorldSimulation.restore(
                state, content, DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        return new Fixture(world, FactionIdentityResolver.createDefault(content, identities));
    }

    private static OperationState activeInvasion() {
        return new OperationState(
                1L, OperationType.INVASION, 1L, 1L, PLAYER_RUNTIME_ID, List.of(INVADER),
                TARGET, TARGET, "system:" + TARGET.value(), RulesOfEngagement.DECLARED_HOSTILES,
                new SupplyPolicy(2_000, 2_000, 300L),
                new WithdrawalPolicy(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, 1_500, true, true),
                OperationStatus.ACTIVE, 0L, 0L, -1L, null, null);
    }

    private static FleetForceRegistry.Entry invaderForce() {
        EntityState entity = new EntityState(
                new EntityId(51_630L),
                null, null, null, null, null, null, null,
                new EntityState.FactionState(PLAYER_RUNTIME_ID),
                null, null, null, null, null, null, null, null, null);
        FleetReadinessState ready = new FleetReadinessState(10_000, 10_000, 10_000, 10_000, 10_000, 10_000, 10_000);
        return new FleetForceRegistry.Entry(
                INVADER, PLAYER_RUNTIME_ID, FleetLocationKind.IN_SYSTEM, TARGET, null, null, entity, ready);
    }

    private static void advanceToAtLeast(WorldSimulation world, long targetTick) {
        int guard = 0;
        while (world.getAuthoritativeWorldTick() < targetTick) {
            world.advanceFrame(1.0f);
            if (++guard > 20_000) throw new AssertionError("world did not reach target authoritative tick");
        }
    }

    private record Fixture(WorldSimulation world, FactionIdentityResolver identities) {}
}
