package com.spacesim.player;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.world.FactionTerritoryService;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StrategicGrowthPlanService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17G1FactionManagementReadModelAcceptanceTest {
    private static final String PLAYER_FACTION = "faction.player_management_read_model";

    @Test
    void managementProjectionIsDeterministicReadOnlyAndDoesNotGrantIndependentAuthority() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_710_001L);
        PlayableWorldState independent = independentSnapshot(scenario.runtime().snapshot());
        PlayerRuntime independentRuntime = PlayerRuntime.restore(
                independent,
                scenario.content(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);

        independentRuntime.player();
        PlayableWorldState independentBefore = independentRuntime.snapshot();
        FactionManagementSnapshot firstIndependent = FactionManagementModel.capture(independentRuntime);
        FactionManagementSnapshot secondIndependent = FactionManagementModel.capture(independentRuntime);

        assertEquals(firstIndependent, secondIndependent,
                "Repeated management projection must be deterministic");
        assertEquals(independentBefore, independentRuntime.snapshot(),
                "Read model must not mutate authoritative playable state");
        assertFalse(firstIndependent.affiliated());
        assertNull(firstIndependent.factionContentId());
        assertNull(firstIndependent.economy());
        assertNull(firstIndependent.doctrine());
        assertNull(firstIndependent.fiscalPolicy());
        assertNull(firstIndependent.stockProductionPolicy());
        assertNull(firstIndependent.diplomacy());
        assertTrue(firstIndependent.territories().isEmpty(),
                "Independent player must not receive hidden faction-territory authority");
        assertTrue(firstIndependent.counterparties().isEmpty(),
                "Independent player must not receive faction diplomacy authority");
        assertEquals(independentRuntime.player().ownedFleetIds().size(), firstIndependent.ownedFleets().size(),
                "Independent Stage-16 physical assets remain visible to their owner");

        PlayableWorldState founded = PlayerFactionFoundationService.foundFaction(
                independentBefore,
                scenario.content(),
                PLAYER_FACTION,
                "Management Read Model Faction");
        PlayerRuntime factionRuntime = PlayerRuntime.restore(
                founded,
                scenario.content(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);

        factionRuntime.player();
        PlayableWorldState factionBefore = factionRuntime.snapshot();
        FactionManagementSnapshot first = FactionManagementModel.capture(factionRuntime);
        FactionManagementSnapshot second = FactionManagementModel.capture(factionRuntime);
        FactionGlobalMapSnapshot composed = FactionGlobalMapModel.capture(factionRuntime);

        assertEquals(first, second,
                "Faction management projection must be stable for unchanged authoritative state");
        assertEquals(first, composed.management(),
                "Strategic global-map composition must reuse the same management projection");
        assertEquals(factionBefore, factionRuntime.snapshot(),
                "Management/global-map projections must not mutate world or player state");

        assertTrue(first.affiliated());
        assertEquals(PLAYER_FACTION, first.factionContentId());
        assertEquals("Management Read Model Faction", first.factionDisplayName());
        assertEquals(
                factionRuntime.world().findFactionEconomicState(PLAYER_FACTION).orElseThrow(),
                first.economy());
        assertEquals(
                factionRuntime.world().findFactionStrategicState(PLAYER_FACTION).orElseThrow().doctrine(),
                first.doctrine());
        assertEquals(
                factionRuntime.world().findFactionFiscalPolicy(PLAYER_FACTION).orElseThrow(),
                first.fiscalPolicy());
        assertEquals(
                factionRuntime.world().findFactionStockProductionPolicy(PLAYER_FACTION).orElseThrow(),
                first.stockProductionPolicy());
        assertEquals(
                factionRuntime.world().findFactionResilienceDemandFloors(PLAYER_FACTION),
                first.resilienceDemandFloors());
        assertEquals(
                factionRuntime.world().findFactionDiplomacyState(PLAYER_FACTION).orElseThrow(),
                first.diplomacy());
        assertEquals(
                StrategicGrowthPlanService.plans(
                        factionRuntime.world().findFactionStrategicState(PLAYER_FACTION).orElseThrow()),
                first.expansionPlans());

        List<StarSystemId> knownSystems = new ArrayList<>(factionRuntime.player().discoveredSystemIds());
        knownSystems.sort(StarSystemId::compareTo);
        assertEquals(knownSystems.size(), first.territories().size());
        for (int index = 0; index < knownSystems.size(); index++) {
            assertEquals(
                    FactionTerritoryService.assess(
                            factionRuntime.world(), knownSystems.get(index), PLAYER_FACTION),
                    first.territories().get(index),
                    "Territory rows must be legal assessments of player-known systems only");
        }

        List<String> counterpartyIds = first.counterparties().stream()
                .map(FactionManagementSnapshot.CounterpartyView::factionContentId)
                .toList();
        List<String> sortedCounterpartyIds = new ArrayList<>(counterpartyIds);
        sortedCounterpartyIds.sort(String::compareTo);
        assertEquals(sortedCounterpartyIds, counterpartyIds,
                "Counterparty rows must have deterministic stable-ID ordering");
        assertFalse(counterpartyIds.contains(PLAYER_FACTION));
        for (FactionManagementSnapshot.CounterpartyView counterparty : first.counterparties()) {
            assertEquals(
                    factionRuntime.world().evaluateFactionMarketAccess(
                            counterparty.factionContentId(), PLAYER_FACTION),
                    counterparty.accessToCounterpartyMarkets());
            assertEquals(
                    factionRuntime.world().evaluateFactionMarketAccess(
                            PLAYER_FACTION, counterparty.factionContentId()),
                    counterparty.counterpartyAccessToOurMarkets());
        }
    }

    private static PlayableWorldState independentSnapshot(PlayableWorldState source) {
        PlayerState player = source.playerState();
        PlayerState independentPlayer = new PlayerState(
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
                player.ownedStations());
        return new PlayableWorldState(
                PlayableWorldState.CURRENT_VERSION,
                source.worldState(),
                independentPlayer);
    }
}
