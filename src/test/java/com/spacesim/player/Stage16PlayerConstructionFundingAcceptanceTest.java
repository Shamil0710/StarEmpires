package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.WalletComponent;
import com.spacesim.economy.EconomicTransaction;
import com.spacesim.persistence.PlayableWorldStateCodec;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.ConstructionProjectState;
import com.spacesim.world.ConstructionProjectStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage16PlayerConstructionFundingAcceptanceTest {
    @Test
    void playerFundingMovesRealMoneyIntoSiteAndPreservesBuildContract() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(16_301L);
        PlayerRuntime runtime = scenario.runtime();
        PlayerState original = runtime.player();
        runtime.replacePlayerState(PlayerRuntime.copyWithOwnershipAndWallet(
                original,
                100_000_000L,
                original.ownedFleetIds(),
                original.activeFleetId()));

        PlayerConstructionService construction = new PlayerConstructionService(runtime);
        ConstructionProjectId projectId = construction.createProject("station.mining_base", 560f, 520f);
        ConstructionProjectState before = runtime.world().findConstructionProject(projectId).orElseThrow();
        long duration = before.buildDurationTicks();
        long funding = before.minimumFundingMilliCredits();
        SimulationSession session = runtime.world().findSession(before.systemId()).orElseThrow();
        Entity site = session.getEntityRegistry().find(before.constructionSiteEntityId());
        WalletComponent siteWallet = site.getComponent(WalletComponent.class);
        long playerBefore = runtime.player().walletMilliCredits();
        long siteBefore = siteWallet.getBalanceMilliCredits();
        int ledgerBefore = session.getLedger().size();

        assertEquals(funding, construction.fundProject(projectId, funding));

        assertEquals(playerBefore - funding, runtime.player().walletMilliCredits());
        assertEquals(siteBefore + funding, siteWallet.getBalanceMilliCredits());
        assertEquals(playerBefore + siteBefore,
                runtime.player().walletMilliCredits() + siteWallet.getBalanceMilliCredits());
        assertEquals(ledgerBefore + 1, session.getLedger().size());
        EconomicTransaction transfer = session.getLedger().getEntries().get(session.getLedger().size() - 1);
        assertEquals(EconomicTransaction.Type.MONEY_TRANSFER, transfer.type());
        assertEquals("PLAYER", transfer.source());
        assertEquals("construction:" + projectId.value() + ":site", transfer.destination());
        assertEquals(funding, transfer.moneyMilliCredits());
        assertEquals("player-construction-funding", transfer.reason());
        assertEquals(duration, runtime.world().findConstructionProject(projectId).orElseThrow().buildDurationTicks());

        runtime.advanceFrame(0.1f);
        assertEquals(ConstructionProjectStatus.FUNDED,
                runtime.world().findConstructionProject(projectId).orElseThrow().status());

        long extra = 1_000_000L;
        assertEquals(extra, construction.fundProject(projectId, extra));
        assertEquals(duration, runtime.world().findConstructionProject(projectId).orElseThrow().buildDurationTicks());

        PlayableWorldState decoded = PlayableWorldStateCodec.decode(
                PlayableWorldStateCodec.encode(runtime.snapshot()));
        ConstructionProjectState persisted = decoded.worldState().constructionProjects().stream()
                .filter(project -> project.id().equals(projectId))
                .findFirst()
                .orElseThrow();
        assertEquals(siteWallet.getBalanceMilliCredits(), persisted.projectWalletMilliCredits());
        assertEquals(runtime.player().walletMilliCredits(), decoded.playerState().walletMilliCredits());
        assertTrue(decoded.playerState().ownedConstructionProjectIds().contains(projectId));
    }

    @Test
    void insufficientPlayerWalletDoesNotPartiallyFundSite() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(16_302L);
        PlayerRuntime runtime = scenario.runtime();
        PlayerConstructionService construction = new PlayerConstructionService(runtime);
        ConstructionProjectId projectId = construction.createProject("station.mining_base", 600f, 540f);
        ConstructionProjectState project = runtime.world().findConstructionProject(projectId).orElseThrow();
        SimulationSession session = runtime.world().findSession(project.systemId()).orElseThrow();
        Entity site = session.getEntityRegistry().find(project.constructionSiteEntityId());
        WalletComponent siteWallet = site.getComponent(WalletComponent.class);
        long playerBefore = runtime.player().walletMilliCredits();
        int ledgerBefore = session.getLedger().size();

        long impossible = Math.addExact(playerBefore, 1L);
        assertEquals(0L, construction.fundProject(projectId, impossible));

        assertEquals(playerBefore, runtime.player().walletMilliCredits());
        assertEquals(0L, siteWallet.getBalanceMilliCredits());
        assertEquals(ledgerBefore, session.getLedger().size());
        assertEquals(ConstructionProjectStatus.PLANNED,
                runtime.world().findConstructionProject(projectId).orElseThrow().status());
    }
}
