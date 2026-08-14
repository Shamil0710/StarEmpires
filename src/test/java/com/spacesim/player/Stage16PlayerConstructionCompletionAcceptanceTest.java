package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.economy.EconomicTransaction;
import com.spacesim.persistence.PlayableWorldStateCodec;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.ConstructionMaterialState;
import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.ConstructionProjectState;
import com.spacesim.world.ConstructionProjectStatus;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetPlacementState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage16PlayerConstructionCompletionAcceptanceTest {
    @Test
    void externalProjectCompletesIntoOrdinaryOwnedStationAndPersists() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(16_601L);
        PlayerRuntime runtime = scenario.runtime();
        PlayerState initial = runtime.player();
        runtime.replacePlayerState(PlayerRuntime.copyWithOwnershipAndWallet(
                initial,
                100_000_000L,
                initial.ownedFleetIds(),
                initial.activeFleetId()));

        FleetId fleetId = runtime.player().activeFleetId();
        FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
        SimulationSession session = runtime.world().findSession(placement.systemId()).orElseThrow();
        Entity ship = session.getEntityRegistry().find(placement.localEntityId());
        TransformComponent shipTransform = ship.getComponent(TransformComponent.class);
        InventoryComponent shipInventory = ship.getComponent(InventoryComponent.class);
        assertNotNull(shipTransform);
        assertNotNull(shipInventory);
        shipTransform.velocity.setZero();

        PlayerConstructionService construction = new PlayerConstructionService(runtime);
        ConstructionProjectId projectId = construction.createProject(
                "station.mining_base", shipTransform.position.x, shipTransform.position.y);
        ConstructionProjectState project = runtime.world().findConstructionProject(projectId).orElseThrow();
        long funding = project.minimumFundingMilliCredits();
        assertEquals(funding, construction.fundProject(projectId, funding));

        Entity site = session.getEntityRegistry().find(project.constructionSiteEntityId());
        WalletComponent siteWallet = site.getComponent(WalletComponent.class);
        assertNotNull(siteWallet);
        Arrays.fill(shipInventory.stock, 0);
        int requiredTotal = project.materials().stream()
                .mapToInt(ConstructionMaterialState::requiredAmount)
                .sum();
        shipInventory.capacity = Math.max(shipInventory.capacity, requiredTotal);
        for (ConstructionMaterialState material : project.materials()) {
            int itemId = runtime.content().findItem(material.itemContentId()).runtimeId();
            shipInventory.stock[itemId] = material.requiredAmount();
        }
        for (ConstructionMaterialState material : project.materials()) {
            assertEquals(
                    material.requiredAmount(),
                    construction.deliverMaterial(
                            projectId, fleetId, material.itemContentId(), material.requiredAmount()));
        }

        ConstructionProjectState fulfilled = runtime.world().findConstructionProject(projectId).orElseThrow();
        assertTrue(fulfilled.materialsFulfilled());
        long operatingCapital = siteWallet.getBalanceMilliCredits();
        long playerWalletAfterFunding = runtime.player().walletMilliCredits();
        int ledgerBeforeCompletion = session.getLedger().size();

        long safetyTicks = fulfilled.buildDurationTicks() + 20L;
        for (long tick = 0L; tick < safetyTicks; tick++) {
            runtime.advanceFrame(0.1f);
            if (runtime.world().findConstructionProject(projectId).orElseThrow().status()
                    == ConstructionProjectStatus.COMPLETED) {
                break;
            }
        }

        ConstructionProjectState completed = runtime.world().findConstructionProject(projectId).orElseThrow();
        assertEquals(ConstructionProjectStatus.COMPLETED, completed.status());
        assertNotNull(completed.completedStationEntityId());
        assertNull(completed.constructionSiteEntityId());
        assertNull(session.getEntityRegistry().find(project.constructionSiteEntityId()));

        Entity station = session.getEntityRegistry().find(completed.completedStationEntityId());
        assertNotNull(station);
        IdentityComponent identity = station.getComponent(IdentityComponent.class);
        assertNotNull(identity);
        assertEquals(IdentityComponent.Kind.STATION, identity.kind);
        assertNotNull(station.getComponent(InventoryComponent.class));
        assertNotNull(station.getComponent(MarketComponent.class));
        WalletComponent stationWallet = station.getComponent(WalletComponent.class);
        assertNotNull(stationWallet);
        assertEquals(operatingCapital, stationWallet.getBalanceMilliCredits());
        assertNull(station.getComponent(FactionComponent.class));
        assertEquals(playerWalletAfterFunding, runtime.player().walletMilliCredits(),
                "Completion must not create passive income in the personal wallet");

        OwnedStationRef stationRef = new OwnedStationRef(project.systemId(), completed.completedStationEntityId());
        assertFalse(runtime.player().ownedConstructionProjectIds().contains(projectId));
        assertTrue(runtime.player().ownedStations().contains(stationRef));

        boolean operatingCapitalTransfer = session.getLedger().getEntries().stream()
                .skip(ledgerBeforeCompletion)
                .anyMatch(entry -> entry.type() == EconomicTransaction.Type.MONEY_TRANSFER
                        && entry.moneyMilliCredits() == operatingCapital
                        && "construction-project-operating-capital".equals(entry.reason()));
        assertTrue(operatingCapitalTransfer);

        PlayableWorldState encodedState = runtime.snapshot();
        PlayableWorldState decoded = PlayableWorldStateCodec.decode(PlayableWorldStateCodec.encode(encodedState));
        assertTrue(decoded.playerState().ownedConstructionProjectIds().isEmpty());
        assertTrue(decoded.playerState().ownedStations().contains(stationRef));

        PlayerRuntime restored = PlayerRuntime.restore(decoded, scenario.content(), project.systemId());
        assertTrue(restored.player().ownedStations().contains(stationRef));
        Entity restoredStation = restored.world().findSession(project.systemId()).orElseThrow()
                .getEntityRegistry().find(stationRef.stationEntityId());
        assertNotNull(restoredStation);
        assertEquals(operatingCapital,
                restoredStation.getComponent(WalletComponent.class).getBalanceMilliCredits());
    }
}
