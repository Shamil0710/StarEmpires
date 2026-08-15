package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.economy.EconomicTransaction;
import com.spacesim.persistence.EntityId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17DControlConsequencesAcceptanceTest {
    private static final String PLAYER_FACTION = "faction.player.control_consequence";
    private static final int PLAYER_RUNTIME_ID = Constants.LEGACY_FACTION_COUNT;
    private static final String FOREIGN_FACTION = "faction.trade_league";
    private static final long FOREIGN_WALLET = 100_000L;

    @Test
    void acquiringControlCreatesNoMoneyButEnablesExistingTerritorialTariffPath() {
        WorldSimulation world = restoreWithDynamicFaction(17_430L);
        EntityId anchorId = world.createEntity(
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                new Entity()
                        .add(new IdentityComponent("Player Control Anchor", IdentityComponent.Kind.STATION))
                        .add(new MarketComponent())
                        .add(new FactionComponent(PLAYER_RUNTIME_ID)));
        assertNotNull(world.findSession(DemoGalaxyFactory.FRONTIER_SYSTEM_ID)
                .orElseThrow().getEntityRegistry().find(anchorId));

        int foreignRuntimeId = world.findFactionRuntimeId(FOREIGN_FACTION).orElseThrow();
        EntityId foreignStationId = world.createEntity(
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                new Entity()
                        .add(new IdentityComponent("Foreign Frontier Market", IdentityComponent.Kind.STATION))
                        .add(new MarketComponent())
                        .add(new WalletComponent())
                        .add(new FactionComponent(foreignRuntimeId)));
        Entity foreignStation = world.findSession(DemoGalaxyFactory.FRONTIER_SYSTEM_ID)
                .orElseThrow().getEntityRegistry().find(foreignStationId);
        WalletComponent foreignWallet = foreignStation.getComponent(WalletComponent.class);
        assertTrue(foreignWallet.creditFromSource(FOREIGN_WALLET));

        long treasuryBefore = world.findFactionEconomicState(PLAYER_FACTION)
                .orElseThrow().treasuryMilliCredits();

        world.declareTerritorialClaim(PLAYER_FACTION, DemoGalaxyFactory.FRONTIER_SYSTEM_ID);
        long claimTick = world.getAuthoritativeWorldTick();
        advanceToAtLeast(world, claimTick + TerritorialControlRuntime.REQUIRED_STABILIZATION_TICKS + 50L);

        assertEquals(PLAYER_FACTION,
                world.controllingFaction(DemoGalaxyFactory.FRONTIER_SYSTEM_ID).orElseThrow());
        assertEquals(treasuryBefore,
                world.findFactionEconomicState(PLAYER_FACTION).orElseThrow().treasuryMilliCredits(),
                "Acquiring control must not capitalize the faction treasury");
        assertEquals(FOREIGN_WALLET, foreignWallet.getBalanceMilliCredits(),
                "Acquiring control must not seize a foreign station wallet");

        int ledgerBeforeFiscal = world.findSession(DemoGalaxyFactory.FRONTIER_SYSTEM_ID)
                .orElseThrow().getLedger().getEntries().size();
        WorldSimulation.FiscalPolicyReport report = world.applyFiscalPolicy(PLAYER_FACTION);
        assertEquals(0L, report.taxCollectedMilliCredits());
        assertTrue(report.tariffCollectedMilliCredits() > 0L);
        assertTrue(report.tariffedStations() >= 1);
        assertEquals(treasuryBefore + report.tariffCollectedMilliCredits(),
                world.findFactionEconomicState(PLAYER_FACTION).orElseThrow().treasuryMilliCredits());
        assertEquals(90_000L, foreignWallet.getBalanceMilliCredits());

        List<EconomicTransaction> entries = world.findSession(DemoGalaxyFactory.FRONTIER_SYSTEM_ID)
                .orElseThrow().getLedger().getEntries();
        assertEquals(ledgerBeforeFiscal + report.tariffedStations(), entries.size());
        for (EconomicTransaction transfer : entries.subList(ledgerBeforeFiscal, entries.size())) {
            assertEquals(EconomicTransaction.Type.MONEY_TRANSFER, transfer.type());
            assertEquals("faction-territory-tariff", transfer.reason());
        }

        assertTrue(world.relinquishTerritorialControl(
                PLAYER_FACTION, DemoGalaxyFactory.FRONTIER_SYSTEM_ID));
        assertTrue(world.controllingFaction(DemoGalaxyFactory.FRONTIER_SYSTEM_ID).isEmpty());
        long treasuryAfterRelinquish = world.findFactionEconomicState(PLAYER_FACTION)
                .orElseThrow().treasuryMilliCredits();
        WorldSimulation.FiscalPolicyReport afterLoss = world.applyFiscalPolicy(PLAYER_FACTION);
        assertEquals(0L, afterLoss.tariffCollectedMilliCredits());
        assertEquals(treasuryAfterRelinquish,
                world.findFactionEconomicState(PLAYER_FACTION).orElseThrow().treasuryMilliCredits());
        assertFalse(world.findFactionStrategicState(PLAYER_FACTION)
                .orElseThrow().controls(DemoGalaxyFactory.FRONTIER_SYSTEM_ID));
    }

    private static void advanceToAtLeast(WorldSimulation world, long targetTick) {
        int guard = 0;
        while (world.getAuthoritativeWorldTick() < targetTick) {
            world.advanceFrame(1.0f);
            if (++guard > 20_000) {
                throw new AssertionError("World did not reach target authoritative tick");
            }
        }
    }

    private static WorldSimulation restoreWithDynamicFaction(long seed) {
        WorldState base = DemoGalaxyFactory.create(seed).snapshot();
        List<FactionStrategicState> strategies = new ArrayList<>();
        for (FactionStrategicState strategy : base.factionStrategies()) {
            List<StarSystemId> controlled = strategy.controlledSystems().stream()
                    .filter(systemId -> !systemId.equals(DemoGalaxyFactory.FRONTIER_SYSTEM_ID))
                    .toList();
            strategies.add(new FactionStrategicState(
                    strategy.factionContentId(),
                    strategy.minimumMarketAccessRelation(),
                    strategy.relations(),
                    controlled,
                    strategy.stationTaxBasisPoints(),
                    strategy.foreignTerritoryTariffBasisPoints(),
                    strategy.stockPolicies(),
                    strategy.productionPolicies(),
                    strategy.strategicGoals()));
        }
        strategies.add(new FactionStrategicState(
                PLAYER_FACTION,
                0,
                List.of(),
                List.of(),
                0,
                1_000,
                List.of(),
                List.of(),
                List.of()));

        List<FactionEconomicState> factions = new ArrayList<>(base.factions());
        factions.add(new FactionEconomicState(PLAYER_FACTION, 0L, 0L, 0L));

        List<WorldFactionIdentityState> identities = new ArrayList<>(base.factionIdentities());
        identities.add(new WorldFactionIdentityState(
                PLAYER_FACTION,
                PLAYER_RUNTIME_ID,
                "Control Consequence Test Faction",
                WorldFactionIdentityState.Origin.PLAYER_CREATED));

        return WorldSimulation.restore(
                new WorldState(
                        WorldState.CURRENT_VERSION,
                        base.topology(),
                        base.systems(),
                        factions,
                        strategies,
                        base.nextConstructionProjectIdValue(),
                        base.constructionProjects(),
                        base.factionEconomicPressures(),
                        base.nextFleetIdValue(),
                        base.fleets(),
                        base.fleetJumps(),
                        identities),
                ContentCatalogLoader.loadDefault(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
    }
}
