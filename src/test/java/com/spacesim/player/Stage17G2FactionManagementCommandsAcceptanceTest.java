package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.world.DiplomaticEmbargoCommand;
import com.spacesim.world.DiplomaticTreatyClauseState;
import com.spacesim.world.DiplomaticTreatyCommand;
import com.spacesim.world.DiplomaticTreatyCommandResult;
import com.spacesim.world.FactionDoctrineState;
import com.spacesim.world.FactionEconomicState;
import com.spacesim.world.FactionFiscalPolicyState;
import com.spacesim.world.FactionPolicyCommand;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemNode;
import com.spacesim.world.TerritorialClaimState;
import com.spacesim.world.WorldSimulation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17G2FactionManagementCommandsAcceptanceTest {
    private static final String PLAYER_FACTION = "faction.player_management_commands";
    private static final long CAPITALIZATION = 5_000_000L;

    @Test
    void facadeDelegatesToAuthoritativeBoundariesPreservesResourcesAndRoundTrips() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_720_001L);
        PlayableWorldState independent = independentSnapshot(scenario.runtime().snapshot());
        PlayerRuntime independentRuntime = PlayerRuntime.restore(
                independent,
                scenario.content(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        PlayerFactionManagementService independentManagement =
                new PlayerFactionManagementService(independentRuntime);

        assertThrows(IllegalStateException.class,
                () -> independentManagement.capitalizeTreasury(1L));
        assertThrows(IllegalStateException.class,
                () -> independentManagement.declareClaim(DemoGalaxyFactory.ACTIVE_SYSTEM_ID));
        assertThrows(IllegalStateException.class,
                () -> independentManagement.submitPolicy(new FactionPolicyCommand.ApplyStrategicPolicy()));

        PlayableWorldState founded = PlayerFactionFoundationService.foundFaction(
                independentRuntime.snapshot(),
                scenario.content(),
                PLAYER_FACTION,
                "Management Commands Faction");
        PlayerRuntime runtime = PlayerRuntime.restore(
                founded,
                scenario.content(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        PlayerFactionManagementService management = new PlayerFactionManagementService(runtime);
        FactionManagementSnapshot before = management.snapshot();
        String targetFaction = before.counterparties().get(0).factionContentId();

        List<FleetPlacementState> placementsBefore = List.copyOf(runtime.world().getFleetPlacements());
        PhysicalTotals physicalBefore = physicalTotals(runtime);
        long personalBefore = runtime.player().walletMilliCredits();
        long treasuryBefore = runtime.world().findFactionEconomicState(PLAYER_FACTION)
                .orElseThrow().treasuryMilliCredits();

        PlayerFactionManagementService.AssetAffiliationResult affiliation = management.affiliateOwnedAssets();
        assertEquals(placementsBefore, runtime.world().getFleetPlacements(),
                "Faction affiliation must not replace/move persistent FleetIds or placements");
        int ownedFleetCount = runtime.player().ownedFleetIds().size();
        assertEquals(ownedFleetCount, affiliation.localFleets().inspectedOwnedFleets(),
                "Local affiliation must inspect the same persistent owned FleetIds");
        assertEquals(ownedFleetCount, affiliation.transitFleets().inspectedOwnedFleets(),
                "Transit affiliation must inspect the same persistent owned FleetIds");
        assertEquals(ownedFleetCount,
                affiliation.localFleets().newlyAffiliatedLocalFleets()
                        + affiliation.localFleets().alreadyAffiliatedLocalFleets()
                        + affiliation.localFleets().deferredTransitFleets(),
                "Local affiliation report must account for every owned FleetId exactly once");
        assertEquals(ownedFleetCount,
                affiliation.transitFleets().newlyAffiliatedTransitFleets()
                        + affiliation.transitFleets().alreadyAffiliatedTransitFleets()
                        + affiliation.transitFleets().deferredLocalFleets(),
                "Transit affiliation report must account for every owned FleetId exactly once");

        assertTrue(management.capitalizeTreasury(CAPITALIZATION));
        long personalAfterCapitalization = runtime.player().walletMilliCredits();
        long treasuryAfterCapitalization = runtime.world().findFactionEconomicState(PLAYER_FACTION)
                .orElseThrow().treasuryMilliCredits();
        assertEquals(personalBefore - CAPITALIZATION, personalAfterCapitalization);
        assertEquals(treasuryBefore + CAPITALIZATION, treasuryAfterCapitalization);
        assertEquals(personalBefore + treasuryBefore,
                personalAfterCapitalization + treasuryAfterCapitalization,
                "Capitalization must conserve personal plus public money exactly");

        FactionDoctrineState doctrine = new FactionDoctrineState(68, 57, 46, 63, 71, 38, 75);
        management.submitPolicy(new FactionPolicyCommand.UpdateDoctrine(doctrine));
        assertEquals(doctrine,
                runtime.world().findFactionStrategicState(PLAYER_FACTION).orElseThrow().doctrine());

        FactionFiscalPolicyState fiscalPolicy = new FactionFiscalPolicyState(
                750,
                250,
                5_000_000L,
                1_000_000L,
                500_000L,
                2_000_000L);
        management.submitPolicy(new FactionPolicyCommand.UpdateFiscalPolicy(fiscalPolicy));
        assertEquals(fiscalPolicy, runtime.world().findFactionFiscalPolicy(PLAYER_FACTION).orElseThrow(),
                "Player tax/foreign-territory tariff authoring must use the shared faction policy command");

        assertThrows(IllegalArgumentException.class,
                () -> management.submitTreaty(new DiplomaticTreatyCommand.Offer(
                        targetFaction,
                        PLAYER_FACTION,
                        List.of(new DiplomaticTreatyClauseState(
                                DiplomaticTreatyClauseState.Kind.MARKET_ACCESS,
                                DiplomaticTreatyClauseState.Direction.MUTUAL,
                                null)),
                        -1L)),
                "Player-facing treaty facade must not allow another faction to be impersonated");

        DiplomaticTreatyCommandResult treatyOffer = management.submitTreaty(new DiplomaticTreatyCommand.Offer(
                PLAYER_FACTION,
                targetFaction,
                List.of(new DiplomaticTreatyClauseState(
                        DiplomaticTreatyClauseState.Kind.MARKET_ACCESS,
                        DiplomaticTreatyClauseState.Direction.MUTUAL,
                        null)),
                -1L));
        assertEquals(DiplomaticTreatyCommandResult.Operation.OFFERED, treatyOffer.operation());
        assertEquals(PLAYER_FACTION, treatyOffer.treatyOwnerFactionContentId());
        assertEquals(treatyOffer.treaty(), runtime.world().findDiplomaticTreaty(treatyOffer.treaty().treatyId())
                .orElseThrow(),
                "Treaty facade must write the same authoritative diplomacy directory as AI/world commands");

        assertThrows(IllegalArgumentException.class,
                () -> management.submitEmbargo(new DiplomaticEmbargoCommand.Impose(
                        targetFaction,
                        PLAYER_FACTION,
                        -1L,
                        "impersonation-attempt")),
                "Player-facing facade must not allow another faction to be impersonated");

        management.submitEmbargo(new DiplomaticEmbargoCommand.Impose(
                PLAYER_FACTION,
                targetFaction,
                -1L,
                "stage17g-management-acceptance"));
        assertTrue(runtime.world().findFactionDiplomacyState(PLAYER_FACTION).orElseThrow()
                .hasActiveMarketEmbargoAgainst(targetFaction, runtime.world().getAuthoritativeWorldTick()));
        assertFalse(runtime.world().evaluateFactionMarketAccess(targetFaction, PLAYER_FACTION).allowed(),
                "Ordinary embargo law must affect effective market access through the shared resolver");

        TerritorialClaimState claim = management.declareClaim(DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        assertEquals(0L, claim.stabilizationTicks(),
                "A new political claim cannot start with fabricated stabilization time");
        assertFalse(runtime.world().findFactionStrategicState(PLAYER_FACTION).orElseThrow()
                        .controls(DemoGalaxyFactory.ACTIVE_SYSTEM_ID),
                "Management claim command must not grant immediate sovereignty");

        PhysicalTotals afterCommands = physicalTotals(runtime);
        assertEquals(physicalBefore.inventoryUnits(), afterCommands.inventoryUnits(),
                "Management commands must not create/delete physical cargo");
        assertEquals(physicalBefore.totalMoneyMilliCredits(), afterCommands.totalMoneyMilliCredits(),
                "Affiliation, capitalization, policy, diplomacy and claims must conserve total money");

        FactionManagementSnapshot persistentBefore = management.snapshot();
        PlayableWorldState saved = runtime.snapshot();
        PlayerRuntime restored = PlayerRuntime.restore(
                saved,
                scenario.content(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        FactionManagementSnapshot persistentAfter = FactionManagementModel.capture(restored);

        assertEquals(persistentBefore.factionContentId(), persistentAfter.factionContentId());
        assertEquals(persistentBefore.economy(), persistentAfter.economy());
        assertEquals(persistentBefore.doctrine(), persistentAfter.doctrine());
        assertEquals(persistentBefore.fiscalPolicy(), persistentAfter.fiscalPolicy());
        assertEquals(persistentBefore.stockProductionPolicy(), persistentAfter.stockProductionPolicy());
        assertEquals(persistentBefore.diplomacy(), persistentAfter.diplomacy());
        assertEquals(persistentBefore.territories(), persistentAfter.territories());
        assertEquals(persistentBefore.ownedFleets(), persistentAfter.ownedFleets());
        assertEquals(physicalTotals(runtime), physicalTotals(restored),
                "Save/load must preserve Stage-17G money and cargo totals exactly");
    }

    private static PhysicalTotals physicalTotals(PlayerRuntime runtime) {
        WorldSimulation world = runtime.world();
        long inventoryUnits = 0L;
        long money = runtime.player().walletMilliCredits();
        for (FactionEconomicState economy : world.snapshot().factions()) {
            money = Math.addExact(money, economy.treasuryMilliCredits());
        }
        for (StarSystemNode system : world.getTopology().systems()) {
            for (Entity entity : world.findSession(system.id()).orElseThrow().getEngine().getEntities()) {
                InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
                if (inventory != null) {
                    for (int units : inventory.stock) {
                        inventoryUnits = Math.addExact(inventoryUnits, units);
                    }
                }
                WalletComponent wallet = entity.getComponent(WalletComponent.class);
                if (wallet != null) {
                    money = Math.addExact(money, wallet.getBalanceMilliCredits());
                }
            }
        }
        return new PhysicalTotals(inventoryUnits, money);
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

    private record PhysicalTotals(long inventoryUnits, long totalMoneyMilliCredits) {
    }
}
