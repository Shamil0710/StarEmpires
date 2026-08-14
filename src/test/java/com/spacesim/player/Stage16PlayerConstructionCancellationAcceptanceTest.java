package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.economy.EconomicTransaction;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.ConstructionMaterialState;
import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.ConstructionProjectState;
import com.spacesim.world.ConstructionProjectStatus;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetPlacementState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage16PlayerConstructionCancellationAcceptanceTest {
    @Test
    void fundedEmptyProjectRefundsAllMoneyAndUsesOrdinaryWorldCancellation() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(16_801L);
        PlayerRuntime runtime = scenario.runtime();
        PlayerState initial = runtime.player();
        runtime.replacePlayerState(PlayerRuntime.copyWithOwnershipAndWallet(
                initial,
                100_000_000L,
                initial.ownedFleetIds(),
                initial.activeFleetId()));
        PlayerConstructionService construction = new PlayerConstructionService(runtime);
        PlayerConstructionCancellationService cancellation = new PlayerConstructionCancellationService(runtime);
        FleetPlacementState placement = runtime.world().findFleet(runtime.player().activeFleetId()).orElseThrow();
        Entity ship = runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().find(placement.localEntityId());
        TransformComponent transform = ship.getComponent(TransformComponent.class);
        ConstructionProjectId projectId = construction.createProject(
                "station.mining_base", transform.position.x, transform.position.y);
        ConstructionProjectState project = runtime.world().findConstructionProject(projectId).orElseThrow();
        SimulationSession session = runtime.world().findSession(project.systemId()).orElseThrow();
        Entity site = session.getEntityRegistry().find(project.constructionSiteEntityId());
        WalletComponent siteWallet = site.getComponent(WalletComponent.class);
        long funding = project.minimumFundingMilliCredits();
        long playerBeforeFunding = runtime.player().walletMilliCredits();
        assertEquals(funding, construction.fundProject(projectId, funding));
        int ledgerBeforeCancel = session.getLedger().size();

        PlayerConstructionCancellationView preview = cancellation.preview(projectId);
        assertTrue(preview.allowed());
        assertEquals(PlayerConstructionCancellationRejection.NONE, preview.rejection());
        assertEquals(funding, preview.refundableMilliCredits());
        assertTrue(cancellation.cancel(projectId));

        assertEquals(playerBeforeFunding, runtime.player().walletMilliCredits());
        assertEquals(0L, siteWallet.getBalanceMilliCredits());
        assertNull(session.getEntityRegistry().find(project.constructionSiteEntityId()));
        assertEquals(ConstructionProjectStatus.CANCELLED,
                runtime.world().findConstructionProject(projectId).orElseThrow().status());
        assertFalse(runtime.player().ownedConstructionProjectIds().contains(projectId));
        assertEquals(ledgerBeforeCancel + 1, session.getLedger().size());
        EconomicTransaction refund = session.getLedger().getEntries().get(session.getLedger().size() - 1);
        assertEquals(EconomicTransaction.Type.MONEY_TRANSFER, refund.type());
        assertEquals("construction:" + projectId.value() + ":site", refund.source());
        assertEquals("PLAYER", refund.destination());
        assertEquals(funding, refund.moneyMilliCredits());
        assertEquals("player-construction-cancel-refund", refund.reason());
    }

    @Test
    void physicalMaterialAtSiteBlocksCancellationWithoutDeletingMoneyOrCargo() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(16_802L);
        PlayerRuntime runtime = scenario.runtime();
        PlayerState initial = runtime.player();
        runtime.replacePlayerState(PlayerRuntime.copyWithOwnershipAndWallet(
                initial,
                100_000_000L,
                initial.ownedFleetIds(),
                initial.activeFleetId()));
        PlayerConstructionService construction = new PlayerConstructionService(runtime);
        PlayerConstructionCancellationService cancellation = new PlayerConstructionCancellationService(runtime);
        FleetId fleetId = runtime.player().activeFleetId();
        FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
        SimulationSession session = runtime.world().findSession(placement.systemId()).orElseThrow();
        Entity ship = session.getEntityRegistry().find(placement.localEntityId());
        TransformComponent transform = ship.getComponent(TransformComponent.class);
        InventoryComponent shipInventory = ship.getComponent(InventoryComponent.class);
        ConstructionProjectId projectId = construction.createProject(
                "station.mining_base", transform.position.x, transform.position.y);
        ConstructionProjectState project = runtime.world().findConstructionProject(projectId).orElseThrow();
        Entity site = session.getEntityRegistry().find(project.constructionSiteEntityId());
        WalletComponent siteWallet = site.getComponent(WalletComponent.class);
        InventoryComponent siteInventory = site.getComponent(InventoryComponent.class);
        ConstructionMaterialState requirement = project.materials().get(0);
        ContentCatalog.ItemDefinition item = runtime.content().findItem(requirement.itemContentId());
        long funding = project.minimumFundingMilliCredits();
        assertEquals(funding, construction.fundProject(projectId, funding));
        shipInventory.stock[item.runtimeId()] += 1;
        transform.velocity.setZero();
        assertEquals(1, construction.deliverMaterial(projectId, fleetId, item.id(), 1));
        long playerBefore = runtime.player().walletMilliCredits();
        long siteMoneyBefore = siteWallet.getBalanceMilliCredits();
        int siteCargoBefore = siteInventory.stock[item.runtimeId()];
        int ledgerBefore = session.getLedger().size();

        PlayerConstructionCancellationView preview = cancellation.preview(projectId);
        assertFalse(preview.allowed());
        assertEquals(PlayerConstructionCancellationRejection.MATERIALS_DELIVERED, preview.rejection());
        assertFalse(cancellation.cancel(projectId));

        assertEquals(playerBefore, runtime.player().walletMilliCredits());
        assertEquals(siteMoneyBefore, siteWallet.getBalanceMilliCredits());
        assertEquals(siteCargoBefore, siteInventory.stock[item.runtimeId()]);
        assertEquals(ledgerBefore, session.getLedger().size());
        assertTrue(runtime.player().ownedConstructionProjectIds().contains(projectId));
        assertTrue(runtime.world().findConstructionProject(projectId).orElseThrow().status()
                != ConstructionProjectStatus.CANCELLED);
        assertTrue(session.getEntityRegistry().find(project.constructionSiteEntityId()) != null);
    }
}
