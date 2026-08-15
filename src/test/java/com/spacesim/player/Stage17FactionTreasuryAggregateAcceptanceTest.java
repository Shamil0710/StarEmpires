package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.economy.EconomicLedger;
import com.spacesim.economy.EconomicTransaction;
import com.spacesim.economy.Money;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.PlayableWorldStateCodec;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FactionStrategicState;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldSimulation;
import com.spacesim.world.WorldState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17FactionTreasuryAggregateAcceptanceTest {
    private static final String PLAYER_FACTION_ID = "faction.stage17c_aggregate_union";
    private static final int STATION_TAX_BASIS_POINTS = 1_000;

    @Test
    void personalTreasuryAndStationAccountsRemainConservedSeparateAndPersistent() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_699L);
        PlayerRuntime original = scenario.runtime();
        StationRef station = findFundedMarketStation(original);

        PlayableWorldState source = original.snapshot();
        PlayerState player = source.playerState();
        PlayerState independentOwner = new PlayerState(
                player.walletMilliCredits(),
                null,
                player.reputations(),
                player.ownedFleetIds(),
                player.activeFleetId(),
                player.discoveredSystemIds(),
                player.discoveredObjects(),
                player.homeSystemId(),
                player.dockedAt(),
                player.fleetOrders(),
                player.threatIntel(),
                player.ownedConstructionProjectIds(),
                List.of(new OwnedStationRef(station.systemId(), station.entityId())));
        PlayableWorldState founded = PlayerFactionFoundationService.foundFaction(
                new PlayableWorldState(
                        PlayableWorldState.CURRENT_VERSION,
                        source.worldState(),
                        independentOwner),
                scenario.content(),
                PLAYER_FACTION_ID,
                "Stage 17C Aggregate Union");
        PlayerRuntime runtime = PlayerRuntime.restore(
                founded,
                scenario.content(),
                station.systemId());

        PlayerFactionAssetAffiliationService.StationAffiliationReport affiliation =
                new PlayerFactionAssetAffiliationService(runtime).affiliateOwnedStations();
        assertEquals(1, affiliation.newlyAffiliatedStations());
        int runtimeFactionId = runtime.world().findFactionRuntimeId(PLAYER_FACTION_ID).orElseThrow();
        assertEquals(runtimeFactionId, stationEntity(runtime, station).getComponent(FactionComponent.class).factionId);

        runtime = restoreWithStationTax(runtime, scenario.content(), STATION_TAX_BASIS_POINTS);
        PlayerFactionTreasuryRuntimeService treasuryService = new PlayerFactionTreasuryRuntimeService(runtime);
        PlayerFactionTreasuryView initial = treasuryService.view().orElseThrow();
        long stationBeforeTransfers = stationWallet(runtime, station).getBalanceMilliCredits();
        long privatePublicTotal = Math.addExact(
                initial.personalWalletMilliCredits(), initial.factionTreasuryMilliCredits());
        EconomicLedger ledger = activeLedger(runtime);
        int ledgerBeforeTransfers = ledger.size();
        var systemsBeforeTransfers = runtime.world().snapshot().systems();
        var fleetsBeforeTransfers = runtime.world().snapshot().fleets();
        var jumpsBeforeTransfers = runtime.world().snapshot().fleetJumps();
        var constructionBeforeTransfers = runtime.world().snapshot().constructionProjects();
        var strategiesBeforeTransfers = runtime.world().snapshot().factionStrategies();
        var ownershipBeforeTransfers = runtime.player().ownedStations();

        long capitalization = Money.fromCredits(5_000d);
        long withdrawal = Money.fromCredits(2_000d);
        assertTrue(treasuryService.capitalize(capitalization));
        assertTrue(treasuryService.transferToPersonal(withdrawal));

        PlayerFactionTreasuryView afterExplicitTransfers = treasuryService.view().orElseThrow();
        assertEquals(
                initial.personalWalletMilliCredits() - capitalization + withdrawal,
                afterExplicitTransfers.personalWalletMilliCredits());
        assertEquals(
                initial.factionTreasuryMilliCredits() + capitalization - withdrawal,
                afterExplicitTransfers.factionTreasuryMilliCredits());
        assertEquals(privatePublicTotal, Math.addExact(
                afterExplicitTransfers.personalWalletMilliCredits(),
                afterExplicitTransfers.factionTreasuryMilliCredits()));
        assertEquals(stationBeforeTransfers, stationWallet(runtime, station).getBalanceMilliCredits());
        assertPhysicalSystemsEqualIgnoringLedger(systemsBeforeTransfers, runtime.world().snapshot().systems());
        assertEquals(fleetsBeforeTransfers, runtime.world().snapshot().fleets());
        assertEquals(jumpsBeforeTransfers, runtime.world().snapshot().fleetJumps());
        assertEquals(constructionBeforeTransfers, runtime.world().snapshot().constructionProjects());
        assertEquals(strategiesBeforeTransfers, runtime.world().snapshot().factionStrategies());
        assertEquals(ownershipBeforeTransfers, runtime.player().ownedStations());

        assertEquals(ledgerBeforeTransfers + 2, ledger.size());
        EconomicTransaction capitalizationEntry = ledger.getEntries().get(ledgerBeforeTransfers);
        assertEquals(EconomicTransaction.Type.MONEY_TRANSFER, capitalizationEntry.type());
        assertEquals("PLAYER", capitalizationEntry.source());
        assertEquals("faction:" + PLAYER_FACTION_ID + ":treasury", capitalizationEntry.destination());
        assertEquals(capitalization, capitalizationEntry.moneyMilliCredits());
        assertEquals("player-faction-capitalization", capitalizationEntry.reason());

        EconomicTransaction withdrawalEntry = ledger.getEntries().get(ledgerBeforeTransfers + 1);
        assertEquals(EconomicTransaction.Type.MONEY_TRANSFER, withdrawalEntry.type());
        assertEquals("faction:" + PLAYER_FACTION_ID + ":treasury", withdrawalEntry.source());
        assertEquals("PLAYER", withdrawalEntry.destination());
        assertEquals(withdrawal, withdrawalEntry.moneyMilliCredits());
        assertEquals("player-faction-treasury-to-personal", withdrawalEntry.reason());

        long personalBeforeFiscalIncome = afterExplicitTransfers.personalWalletMilliCredits();
        long treasuryBeforeFiscalIncome = afterExplicitTransfers.factionTreasuryMilliCredits();
        long stationBeforeFiscalIncome = stationWallet(runtime, station).getBalanceMilliCredits();
        long threeAccountTotal = Math.addExact(
                Math.addExact(personalBeforeFiscalIncome, treasuryBeforeFiscalIncome),
                stationBeforeFiscalIncome);

        WorldSimulation.FiscalPolicyReport fiscal = runtime.world().applyFiscalPolicy(PLAYER_FACTION_ID);

        assertTrue(fiscal.taxCollectedMilliCredits() > 0L);
        assertEquals(0L, fiscal.tariffCollectedMilliCredits());
        assertEquals(1, fiscal.taxedStations());
        assertEquals(0, fiscal.tariffedStations());
        PlayerFactionTreasuryView afterFiscalIncome = treasuryService.view().orElseThrow();
        assertEquals(personalBeforeFiscalIncome, afterFiscalIncome.personalWalletMilliCredits());
        assertEquals(
                treasuryBeforeFiscalIncome + fiscal.taxCollectedMilliCredits(),
                afterFiscalIncome.factionTreasuryMilliCredits());
        assertEquals(
                stationBeforeFiscalIncome - fiscal.taxCollectedMilliCredits(),
                stationWallet(runtime, station).getBalanceMilliCredits());
        assertEquals(threeAccountTotal, Math.addExact(
                Math.addExact(
                        afterFiscalIncome.personalWalletMilliCredits(),
                        afterFiscalIncome.factionTreasuryMilliCredits()),
                stationWallet(runtime, station).getBalanceMilliCredits()));

        EconomicTransaction fiscalEntry = ledger.getEntries().get(ledger.size() - 1);
        assertEquals(EconomicTransaction.Type.MONEY_TRANSFER, fiscalEntry.type());
        assertEquals("faction:" + PLAYER_FACTION_ID + ":treasury", fiscalEntry.destination());
        assertEquals(fiscal.taxCollectedMilliCredits(), fiscalEntry.moneyMilliCredits());
        assertEquals("faction-station-tax", fiscalEntry.reason());

        long stationBeforeSave = stationWallet(runtime, station).getBalanceMilliCredits();
        List<EconomicTransaction> ledgerBeforeSave = List.copyOf(ledger.getEntries());
        PlayableWorldState snapshot = runtime.snapshot();
        PlayableWorldState decoded = PlayableWorldStateCodec.decode(PlayableWorldStateCodec.encode(snapshot));
        assertEquals(snapshot, decoded);
        PlayerRuntime restored = PlayerRuntime.restore(decoded, scenario.content(), station.systemId());
        PlayerFactionTreasuryRuntimeService restoredTreasury = new PlayerFactionTreasuryRuntimeService(restored);

        assertEquals(afterFiscalIncome, restoredTreasury.view().orElseThrow());
        assertEquals(stationBeforeSave, stationWallet(restored, station).getBalanceMilliCredits());
        assertEquals(ledgerBeforeSave, activeLedger(restored).getEntries());
        assertEquals(runtimeFactionId,
                stationEntity(restored, station).getComponent(FactionComponent.class).factionId);
        assertEquals(ownershipBeforeTransfers, restored.player().ownedStations());
    }

    private static PlayerRuntime restoreWithStationTax(
            PlayerRuntime runtime,
            ContentCatalog content,
            int stationTaxBasisPoints) {
        PlayableWorldState snapshot = runtime.snapshot();
        WorldState world = snapshot.worldState();
        List<FactionStrategicState> strategies = new ArrayList<>(world.factionStrategies().size());
        for (FactionStrategicState state : world.factionStrategies()) {
            if (state.factionContentId().equals(PLAYER_FACTION_ID)) {
                strategies.add(new FactionStrategicState(
                        state.factionContentId(),
                        state.minimumMarketAccessRelation(),
                        state.relations(),
                        state.controlledSystems(),
                        stationTaxBasisPoints,
                        state.foreignTerritoryTariffBasisPoints(),
                        state.stockPolicies(),
                        state.productionPolicies(),
                        state.strategicGoals()));
            } else {
                strategies.add(state);
            }
        }
        WorldState updatedWorld = new WorldState(
                WorldState.CURRENT_VERSION,
                world.topology(),
                world.systems(),
                world.factions(),
                strategies,
                world.nextConstructionProjectIdValue(),
                world.constructionProjects(),
                world.factionEconomicPressures(),
                world.nextFleetIdValue(),
                world.fleets(),
                world.fleetJumps(),
                world.factionIdentities());
        return PlayerRuntime.restore(
                new PlayableWorldState(
                        PlayableWorldState.CURRENT_VERSION,
                        updatedWorld,
                        snapshot.playerState()),
                content,
                runtime.world().getActiveSystemId());
    }

    private static StationRef findFundedMarketStation(PlayerRuntime runtime) {
        StarSystemId systemId = runtime.world().getActiveSystemId();
        SimulationSession session = runtime.world().findSession(systemId).orElseThrow();
        for (Entity entity : session.getEngine().getEntities()) {
            IdentityComponent identity = entity.getComponent(IdentityComponent.class);
            EntityIdComponent id = entity.getComponent(EntityIdComponent.class);
            WalletComponent wallet = entity.getComponent(WalletComponent.class);
            if (identity != null
                    && identity.kind == IdentityComponent.Kind.STATION
                    && id != null
                    && wallet != null
                    && wallet.getBalanceMilliCredits() > 0L
                    && entity.getComponent(MarketComponent.class) != null) {
                return new StationRef(systemId, id.id);
            }
        }
        throw new AssertionError("Playable test world has no funded local market station");
    }

    private static Entity stationEntity(PlayerRuntime runtime, StationRef station) {
        Entity entity = runtime.world().findSession(station.systemId()).orElseThrow()
                .getEntityRegistry().find(station.entityId());
        assertNotNull(entity);
        return entity;
    }

    private static WalletComponent stationWallet(PlayerRuntime runtime, StationRef station) {
        WalletComponent wallet = stationEntity(runtime, station).getComponent(WalletComponent.class);
        assertNotNull(wallet);
        return wallet;
    }

    private static EconomicLedger activeLedger(PlayerRuntime runtime) {
        return runtime.world().findSession(runtime.world().getActiveSystemId()).orElseThrow().getLedger();
    }

    private static void assertPhysicalSystemsEqualIgnoringLedger(
            List<com.spacesim.world.StarSystemSimulationState> before,
            List<com.spacesim.world.StarSystemSimulationState> after) {
        assertEquals(before.size(), after.size());
        for (int i = 0; i < before.size(); i++) {
            assertEquals(before.get(i).systemId(), after.get(i).systemId());
            assertEquals(before.get(i).simulationState().entities(), after.get(i).simulationState().entities());
            assertEquals(before.get(i).simulationState().clock(), after.get(i).simulationState().clock());
        }
    }

    private record StationRef(StarSystemId systemId, EntityId entityId) {
    }
}
