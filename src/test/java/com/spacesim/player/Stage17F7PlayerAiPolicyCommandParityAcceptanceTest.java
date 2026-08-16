package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.world.FactionDoctrineState;
import com.spacesim.world.FactionFiscalPolicyState;
import com.spacesim.world.FactionPolicyCommand;
import com.spacesim.world.FactionPolicyCommandExecutor;
import com.spacesim.world.FactionStockPolicyState;
import com.spacesim.world.FactionStockProductionPolicyState;
import com.spacesim.world.StarSystemNode;
import com.spacesim.world.WorldSimulation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Stage17F7PlayerAiPolicyCommandParityAcceptanceTest {
    private static final String PLAYER_FACTION = "faction.player_policy_parity";

    @Test
    void playerAdapterAndCommonExecutorProduceIdenticalAuthoritativePolicyState() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_700_001L);
        PlayableWorldState independent = independentSnapshot(scenario.runtime().snapshot());
        PlayerRuntime independentRuntime = PlayerRuntime.restore(
                independent,
                scenario.content(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);

        assertThrows(
                IllegalStateException.class,
                () -> PlayerFactionPolicyService.submit(
                        independentRuntime,
                        new FactionPolicyCommand.ApplyStrategicPolicy()),
                "Independent player must not receive hidden faction-policy authority");

        PlayableWorldState founded = PlayerFactionFoundationService.foundFaction(
                independent,
                scenario.content(),
                PLAYER_FACTION,
                "Policy Parity Faction");
        PlayerRuntime playerPath = PlayerRuntime.restore(
                founded,
                scenario.content(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        PlayerRuntime commonPath = PlayerRuntime.restore(
                founded,
                scenario.content(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);

        PhysicalTotals before = physicalTotals(playerPath.world(), PLAYER_FACTION);
        long personalWalletBefore = playerPath.player().walletMilliCredits();
        List<FactionPolicyCommand> commands = commands(scenario.content());

        for (FactionPolicyCommand command : commands) {
            FactionPolicyCommandExecutor.ExecutionResult playerResult =
                    PlayerFactionPolicyService.submit(playerPath, command);
            FactionPolicyCommandExecutor.ExecutionResult commonResult =
                    FactionPolicyCommandExecutor.execute(commonPath.world(), PLAYER_FACTION, command);
            assertEquals(commonResult, playerResult,
                    "Player-facing and common executor reports must match for the same command");
            assertEquals(commonPath.world().snapshot(), playerPath.world().snapshot(),
                    "Player-facing command submission must not create a separate authoritative policy model");
        }

        assertEquals(new FactionDoctrineState(72, 61, 48, 57, 66, 35, 79),
                playerPath.world().findFactionStrategicState(PLAYER_FACTION).orElseThrow().doctrine());
        assertEquals(new FactionFiscalPolicyState(
                        750,
                        250,
                        5_000_000L,
                        1_000_000L,
                        500_000L,
                        2_000_000L),
                playerPath.world().findFactionFiscalPolicy(PLAYER_FACTION).orElseThrow());
        assertEquals(commands(scenario.content()).get(2),
                new FactionPolicyCommand.UpdateStockProductionPolicy(
                        playerPath.world().findFactionStockProductionPolicy(PLAYER_FACTION).orElseThrow()));

        assertEquals(before, physicalTotals(playerPath.world(), PLAYER_FACTION),
                "Policy authoring and ordinary strategic configuration apply must not create cargo or money");
        assertEquals(personalWalletBefore, playerPath.player().walletMilliCredits(),
                "Faction policy commands must not silently spend or credit the personal wallet");
    }

    private static List<FactionPolicyCommand> commands(ContentCatalog content) {
        ContentCatalog.ItemDefinition item = content.getItems().get(0);
        return List.of(
                new FactionPolicyCommand.UpdateDoctrine(
                        new FactionDoctrineState(72, 61, 48, 57, 66, 35, 79)),
                new FactionPolicyCommand.UpdateFiscalPolicy(
                        new FactionFiscalPolicyState(
                                750,
                                250,
                                5_000_000L,
                                1_000_000L,
                                500_000L,
                                2_000_000L)),
                new FactionPolicyCommand.UpdateStockProductionPolicy(
                        new FactionStockProductionPolicyState(
                                List.of(new FactionStockPolicyState(item.id(), 9)),
                                List.of())),
                new FactionPolicyCommand.ApplyStrategicPolicy());
    }

    private static PhysicalTotals physicalTotals(WorldSimulation world, String factionContentId) {
        long inventoryUnits = 0L;
        long entityWallets = 0L;
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
                    entityWallets = Math.addExact(entityWallets, wallet.getBalanceMilliCredits());
                }
            }
        }
        long treasury = world.findFactionEconomicState(factionContentId).orElseThrow().treasuryMilliCredits();
        return new PhysicalTotals(inventoryUnits, entityWallets, treasury);
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

    private record PhysicalTotals(long inventoryUnits, long entityWallets, long treasury) {
    }
}
