package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.PlayableWorldStateCodec;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.ConstructionMaterialState;
import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.ConstructionProjectState;
import com.spacesim.world.ConstructionProjectStatus;
import com.spacesim.world.DiplomaticEmbargoCommand;
import com.spacesim.world.DiplomaticTreatyClauseState;
import com.spacesim.world.DiplomaticTreatyCommand;
import com.spacesim.world.DiplomaticTreatyCommandResult;
import com.spacesim.world.FactionEconomicState;
import com.spacesim.world.FactionFiscalPolicyState;
import com.spacesim.world.FactionPolicyCommand;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemNode;
import com.spacesim.world.TerritorialClaimState;
import com.spacesim.world.WorldSimulation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Final Stage-17 transition gate from an independent Stage-16 owner to a persistent faction actor. */
class Stage17HEndToEndTransitionAcceptanceTest {
    private static final String PLAYER_FACTION = "faction.stage17h_transition";
    private static final String PROJECT_ARCHETYPE = "station.mining_base";
    private static final long FIXTURE_PERSONAL_WALLET = 150_000_000L;
    private static final long STATION_WORKING_CAPITAL = 5_000_000L;
    private static final long FACTION_CAPITALIZATION = 7_000_000L;

    @Test
    void independentStage16OwnerTransitionsThroughOrdinaryFactionRulesAndBinaryRoundTrip() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_800_001L);
        PlayerRuntime stage16Runtime = prepareCompletedStage16Station(scenario);
        PlayableWorldState independent = independentSnapshot(stage16Runtime.snapshot());
        PlayerRuntime independentRuntime = PlayerRuntime.restore(
                independent,
                scenario.content(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);

        assertFalse(independentRuntime.player().affiliated());
        assertFalse(independentRuntime.player().ownedStations().isEmpty(),
                "Stage 17H must start with a real completed Stage-16 station asset");

        List<FleetId> ownedFleetIdsBefore = independentRuntime.player().ownedFleetIds();
        List<OwnedStationRef> ownedStationsBefore = independentRuntime.player().ownedStations();
        List<FleetPlacementState> placementsBefore = independentRuntime.world().getFleetPlacements();
        PhysicalTotals totalsBeforeTransition = physicalTotals(independentRuntime);
        long nextFleetIdBefore = independentRuntime.world().snapshot().nextFleetIdValue();
        long nextProjectIdBefore = independentRuntime.world().snapshot().nextConstructionProjectIdValue();

        PlayableWorldState founded = PlayerFactionFoundationService.foundFaction(
                independentRuntime.snapshot(),
                scenario.content(),
                PLAYER_FACTION,
                "Stage 17H Transition Faction");
        PlayerRuntime runtime = PlayerRuntime.restore(
                founded,
                scenario.content(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        PlayerFactionManagementService management = new PlayerFactionManagementService(runtime);

        assertEquals(0L, runtime.world().findFactionEconomicState(PLAYER_FACTION).orElseThrow()
                .treasuryMilliCredits(), "Founding must not invent faction capital");
        assertFalse(runtime.world().findFactionStrategicState(PLAYER_FACTION).orElseThrow()
                .controls(DemoGalaxyFactory.ACTIVE_SYSTEM_ID), "Founding must not invent territory");

        PlayerFactionManagementService.AssetAffiliationResult affiliation = management.affiliateOwnedAssets();
        assertEquals(ownedFleetIdsBefore.size(), affiliation.localFleets().inspectedOwnedFleets());
        assertEquals(ownedStationsBefore.size(), affiliation.stations().inspectedOwnedStations());
        assertEquals(placementsBefore, runtime.world().getFleetPlacements(),
                "Affiliation must preserve every persistent FleetId and placement");
        assertEquals(ownedStationsBefore, runtime.player().ownedStations(),
                "Affiliation must preserve every Stage-16 OwnedStationRef");
        assertEquals(nextFleetIdBefore, runtime.world().snapshot().nextFleetIdValue(),
                "Affiliation must not consume a FleetId");
        assertEquals(nextProjectIdBefore, runtime.world().snapshot().nextConstructionProjectIdValue(),
                "Affiliation must not consume a ConstructionProjectId");

        long personalBeforeCapitalization = runtime.player().walletMilliCredits();
        long treasuryBeforeCapitalization = runtime.world().findFactionEconomicState(PLAYER_FACTION)
                .orElseThrow().treasuryMilliCredits();
        assertTrue(management.capitalizeTreasury(FACTION_CAPITALIZATION));
        assertEquals(personalBeforeCapitalization - FACTION_CAPITALIZATION,
                runtime.player().walletMilliCredits());
        assertEquals(treasuryBeforeCapitalization + FACTION_CAPITALIZATION,
                runtime.world().findFactionEconomicState(PLAYER_FACTION).orElseThrow().treasuryMilliCredits());

        FactionFiscalPolicyState fiscalPolicy = new FactionFiscalPolicyState(
                1_000,
                0,
                0L,
                0L,
                0L,
                0L);
        management.submitPolicy(new FactionPolicyCommand.UpdateFiscalPolicy(fiscalPolicy));
        management.submitPolicy(new FactionPolicyCommand.ApplyStrategicPolicy());
        assertEquals(fiscalPolicy, runtime.world().findFactionFiscalPolicy(PLAYER_FACTION).orElseThrow());

        OwnedStationRef taxedStation = ownedStationsBefore.get(0);
        WalletComponent stationWallet = station(runtime, taxedStation).getComponent(WalletComponent.class);
        assertNotNull(stationWallet);
        long stationMoneyBeforeTax = stationWallet.getBalanceMilliCredits();
        long treasuryBeforeTax = runtime.world().findFactionEconomicState(PLAYER_FACTION)
                .orElseThrow().treasuryMilliCredits();
        runtime.world().applyFiscalPolicy(PLAYER_FACTION);
        long stationMoneyAfterTax = stationWallet.getBalanceMilliCredits();
        long treasuryAfterTax = runtime.world().findFactionEconomicState(PLAYER_FACTION)
                .orElseThrow().treasuryMilliCredits();
        assertTrue(stationMoneyAfterTax < stationMoneyBeforeTax,
                "Ordinary fiscal runtime must react to authored policy using the affiliated real station wallet");
        assertEquals(stationMoneyBeforeTax - stationMoneyAfterTax,
                treasuryAfterTax - treasuryBeforeTax,
                "Station tax must be an exactly conserved station-to-treasury transfer");

        FactionManagementSnapshot managementBeforeDiplomacy = management.snapshot();
        String targetFaction = managementBeforeDiplomacy.counterparties().get(0).factionContentId();
        DiplomaticTreatyCommandResult treaty = management.submitTreaty(new DiplomaticTreatyCommand.Offer(
                PLAYER_FACTION,
                targetFaction,
                List.of(new DiplomaticTreatyClauseState(
                        DiplomaticTreatyClauseState.Kind.MARKET_ACCESS,
                        DiplomaticTreatyClauseState.Direction.MUTUAL,
                        null)),
                -1L));
        management.submitEmbargo(new DiplomaticEmbargoCommand.Impose(
                PLAYER_FACTION,
                targetFaction,
                -1L,
                "stage17h-transition-gate"));
        assertFalse(runtime.world().evaluateFactionMarketAccess(targetFaction, PLAYER_FACTION).allowed(),
                "Effective access must come from ordinary diplomacy law, not player ownership");

        TerritorialClaimState claim = management.declareClaim(DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        assertEquals(0L, claim.stabilizationTicks());
        assertFalse(runtime.world().findFactionStrategicState(PLAYER_FACTION).orElseThrow()
                        .controls(DemoGalaxyFactory.ACTIVE_SYSTEM_ID),
                "A declaration must not become instant sovereignty");

        assertEquals(totalsBeforeTransition, physicalTotals(runtime),
                "Founding, affiliation, capital transfer, policy, diplomacy and claim must conserve physical totals");
        assertDistinctOwnedIdentity(runtime.player());

        FactionManagementSnapshot persistentBefore = management.snapshot();
        byte[] encoded = PlayableWorldStateCodec.encode(runtime.snapshot());
        PlayableWorldState decoded = PlayableWorldStateCodec.decode(encoded);
        assertArrayEquals(encoded, PlayableWorldStateCodec.encode(decoded),
                "Decode/re-encode must be a canonical deterministic Stage-17 save");

        PlayerRuntime restored = PlayerRuntime.restore(
                decoded,
                scenario.content(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        FactionManagementSnapshot persistentAfter = FactionManagementModel.capture(restored);

        assertEquals(PLAYER_FACTION, restored.player().factionContentId());
        assertEquals(ownedFleetIdsBefore, restored.player().ownedFleetIds());
        assertEquals(ownedStationsBefore, restored.player().ownedStations());
        assertEquals(placementsBefore, restored.world().getFleetPlacements());
        assertEquals(nextFleetIdBefore, restored.world().snapshot().nextFleetIdValue());
        assertEquals(nextProjectIdBefore, restored.world().snapshot().nextConstructionProjectIdValue());
        assertEquals(persistentBefore.economy(), persistentAfter.economy());
        assertEquals(persistentBefore.doctrine(), persistentAfter.doctrine());
        assertEquals(persistentBefore.fiscalPolicy(), persistentAfter.fiscalPolicy());
        assertEquals(persistentBefore.stockProductionPolicy(), persistentAfter.stockProductionPolicy());
        assertEquals(persistentBefore.diplomacy(), persistentAfter.diplomacy());
        assertEquals(persistentBefore.territories(), persistentAfter.territories());
        assertEquals(persistentBefore.ownedFleets(), persistentAfter.ownedFleets());
        assertEquals(treaty.treaty(), restored.world().findDiplomaticTreaty(treaty.treaty().treatyId()).orElseThrow());
        assertFalse(restored.world().evaluateFactionMarketAccess(targetFaction, PLAYER_FACTION).allowed());
        assertFalse(restored.world().findFactionStrategicState(PLAYER_FACTION).orElseThrow()
                .controls(DemoGalaxyFactory.ACTIVE_SYSTEM_ID));
        assertEquals(physicalTotals(runtime), physicalTotals(restored),
                "Binary save/load must not duplicate, reset or grant money/cargo/assets");
        assertDistinctOwnedIdentity(restored.player());
    }

    private static PlayerRuntime prepareCompletedStage16Station(PlayableTestWorldFactory.Scenario scenario) {
        PlayerRuntime runtime = scenario.runtime();
        PlayerState initial = runtime.player();
        runtime.replacePlayerState(PlayerRuntime.copyWithOwnershipAndWallet(
                initial,
                FIXTURE_PERSONAL_WALLET,
                initial.ownedFleetIds(),
                initial.activeFleetId()));

        PlayerConstructionService construction = new PlayerConstructionService(runtime);
        PlayerConstructionPlacementView location = findValidPlacement(construction);
        ConstructionProjectId projectId = construction.createProject(PROJECT_ARCHETYPE, location.x(), location.y());
        ConstructionProjectState project = runtime.world().findConstructionProject(projectId).orElseThrow();
        assertEquals(project.minimumFundingMilliCredits(),
                construction.fundProject(projectId, project.minimumFundingMilliCredits()));
        runtime.advanceFrame(0.2f);

        FleetPlacementState activeFleet = runtime.world().findFleet(runtime.player().activeFleetId()).orElseThrow();
        Entity ship = fleetEntity(runtime, activeFleet);
        InventoryComponent cargo = ship.getComponent(InventoryComponent.class);
        assertNotNull(cargo);
        cargo.capacity = Math.max(cargo.capacity, 100_000);

        ConstructionProjectState funded = runtime.world().findConstructionProject(projectId).orElseThrow();
        Entity site = runtime.world().findSession(funded.systemId()).orElseThrow()
                .getEntityRegistry().find(funded.constructionSiteEntityId());
        assertNotNull(site);
        TransformComponent shipTransform = ship.getComponent(TransformComponent.class);
        TransformComponent siteTransform = site.getComponent(TransformComponent.class);
        shipTransform.position.set(siteTransform.position);
        shipTransform.velocity.setZero();

        for (ConstructionMaterialState material : funded.materials()) {
            int remaining = material.remainingAmount();
            if (remaining <= 0) {
                continue;
            }
            ContentCatalog.ItemDefinition item = scenario.content().findItem(material.itemContentId());
            assertNotNull(item);
            cargo.stock[item.runtimeId()] = Math.addExact(cargo.stock[item.runtimeId()], remaining);
            assertEquals(remaining,
                    construction.deliverMaterial(projectId, activeFleet.id(), material.itemContentId(), remaining));
        }

        advanceUntilStatus(runtime, projectId, ConstructionProjectStatus.COMPLETED, 6_000);
        ConstructionProjectState completed = runtime.world().findConstructionProject(projectId).orElseThrow();
        EntityId stationId = completed.completedStationEntityId();
        assertNotNull(stationId);
        OwnedStationRef ownedStation = new OwnedStationRef(completed.systemId(), stationId);
        assertTrue(runtime.player().ownedStations().contains(ownedStation));

        Entity station = station(runtime, ownedStation);
        TransformComponent stationTransform = station.getComponent(TransformComponent.class);
        shipTransform.position.set(stationTransform.position);
        shipTransform.velocity.setZero();
        assertTrue(runtime.dockAt(stationId));
        PlayerStationFinanceService finance = new PlayerStationFinanceService(runtime);
        assertTrue(finance.deposit(STATION_WORKING_CAPITAL));
        assertTrue(runtime.undock());
        return runtime;
    }

    private static PlayerConstructionPlacementView findValidPlacement(PlayerConstructionService construction) {
        for (float y = 100f; y <= Constants.WORLD_HEIGHT - 100f; y += 100f) {
            for (float x = 100f; x <= Constants.WORLD_WIDTH - 100f; x += 100f) {
                PlayerConstructionPlacementView view = construction.previewPlacement(x, y);
                if (view.allowed()) {
                    return view;
                }
            }
        }
        throw new AssertionError("Playable test world has no valid Stage-16 construction placement");
    }

    private static void advanceUntilStatus(
            PlayerRuntime runtime,
            ConstructionProjectId projectId,
            ConstructionProjectStatus status,
            int maximumFrames) {
        for (int frame = 0; frame < maximumFrames; frame++) {
            if (runtime.world().findConstructionProject(projectId).orElseThrow().status() == status) {
                return;
            }
            runtime.advanceFrame(0.1f);
        }
        throw new AssertionError("Construction project did not reach " + status + " within frame budget");
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

    private static Entity fleetEntity(PlayerRuntime runtime, FleetPlacementState placement) {
        return runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().find(placement.localEntityId());
    }

    private static Entity station(PlayerRuntime runtime, OwnedStationRef reference) {
        return runtime.world().findSession(reference.systemId()).orElseThrow()
                .getEntityRegistry().find(reference.stationEntityId());
    }

    private static PhysicalTotals physicalTotals(PlayerRuntime runtime) {
        WorldSimulation world = runtime.world();
        long inventoryUnits = 0L;
        long money = runtime.player().walletMilliCredits();
        for (FactionEconomicState economy : world.snapshot().factions()) {
            money = Math.addExact(money, economy.treasuryMilliCredits());
        }
        for (StarSystemNode system : world.getTopology().systems()) {
            SimulationSession session = world.findSession(system.id()).orElseThrow();
            for (Entity entity : session.getEngine().getEntities()) {
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

    private static void assertDistinctOwnedIdentity(PlayerState player) {
        assertEquals(player.ownedFleetIds().size(), player.ownedFleetIds().stream().distinct().count());
        assertEquals(player.ownedStations().size(), player.ownedStations().stream().distinct().count());
    }

    private record PhysicalTotals(long inventoryUnits, long totalMoneyMilliCredits) {
    }
}
