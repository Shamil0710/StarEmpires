package com.spacesim.campaign;

import com.spacesim.components.*;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedCampaignMarketWorkingCapitalTest {
    @Test void actualDockedMarketUsesExistingOperatorMoneyOnlyOnCompletedTicks() {
        var c = Stage228CampaignAuthority.create(1); c.submitIndependentPilotStart(c.previewIndependentPilotStart());
        var runtime = c.coordinator().runtime(); var world = runtime.world();
        var fleet = world.findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        String station = "hub." + fleet.systemId().value();
        c.submitPilotAction(c.previewPilotAction("DOCK", station, "", 0));
        var ref = c.pilotMarketReference(station).orElseThrow();
        var entity = world.findSession(ref.systemId()).orElseThrow().getEntityRegistry().require(ref.entityId());
        var wallet = entity.getComponent(WalletComponent.class);
        // A legacy registered market uses the same real owner and funds, without changing its quote policy.
        entity.add(new IdentityComponent(Stage228CampaignAuthority.PILOT_MARKET_IDENTITY_PREFIX + station, IdentityComponent.Kind.STATION));
        String seller = world.findFactionStableId(entity.getComponent(FactionComponent.class).factionId).orElseThrow();
        long amount = 4_000_000L;
        // Explicit conserved operator remittance creates a working-capital deficit; no new money or geometry.
        assertTrue(world.transferToFactionTreasury(seller, wallet, entity.getComponent(IdentityComponent.class).name, amount, "fixture.market-remittance"));
        long treasury = world.findFactionEconomicState(seller).orElseThrow().treasuryMilliCredits();
        long personal = c.playerState().orElseThrow().walletMilliCredits();
        var pending = c.captureState();
        var loaded = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(pending)));
        assertEquals(pending, loaded.captureState(), "Restore alone must not replenish a market");
        c.coordinator().setPaused(true); c.advanceFrame(1);
        assertEquals(Stage228CampaignAuthority.PILOT_MARKET_INITIAL_LIQUIDITY - amount, wallet.getBalanceMilliCredits());
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        assertEquals(Stage228CampaignAuthority.PILOT_MARKET_INITIAL_LIQUIDITY, wallet.getBalanceMilliCredits());
        assertEquals(treasury - amount, world.findFactionEconomicState(seller).orElseThrow().treasuryMilliCredits());
        assertEquals(personal, c.playerState().orElseThrow().walletMilliCredits());
        c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        assertEquals(treasury - amount, world.findFactionEconomicState(seller).orElseThrow().treasuryMilliCredits(), "No repeated funding at the target balance");
        var completed = c.captureState();
        assertEquals(completed, Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(completed))).captureState());
    }

    @Test void reserveFloorAndPersonalOwnershipPreventOperatorFunding() {
        var c = Stage228CampaignAuthority.create(1); c.submitIndependentPilotStart(c.previewIndependentPilotStart());
        var runtime = c.coordinator().runtime(); var world = runtime.world();
        String station = "hub." + world.findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow().systemId().value();
        c.submitPilotAction(c.previewPilotAction("DOCK", station, "", 0));
        var ref = c.pilotMarketReference(station).orElseThrow();
        var entity = world.findSession(ref.systemId()).orElseThrow().getEntityRegistry().require(ref.entityId());
        var wallet = entity.getComponent(WalletComponent.class);
        String seller = world.findFactionStableId(entity.getComponent(FactionComponent.class).factionId).orElseThrow();
        assertTrue(world.transferToFactionTreasury(seller, wallet, entity.getComponent(IdentityComponent.class).name, 4_000_000L, "fixture.market-remittance"));
        var economy = world.findFactionEconomicState(seller).orElseThrow();
        // Any other market of the same operator can hold the drained treasury; the operator need not own industry.
        var otherEntity = runtime.infrastructure().endpoints().stream().filter(e -> !e.stationId().equals(station))
                .flatMap(e -> c.pilotMarketReference(e.stationId()).stream())
                .map(r -> world.findSession(r.systemId()).orElseThrow().getEntityRegistry().require(r.entityId()))
                .filter(e -> e.getComponent(FactionComponent.class) != null
                        && world.findFactionStableId(e.getComponent(FactionComponent.class).factionId).orElseThrow().equals(seller))
                .findFirst().orElseThrow();
        assertTrue(world.transferFromFactionTreasury(seller, otherEntity.getComponent(WalletComponent.class),
                otherEntity.getComponent(IdentityComponent.class).name, economy.treasuryMilliCredits() - economy.treasuryReserveFloorMilliCredits(), "fixture.operator-reserve"));
        var before = c.captureState(); GeneratedCampaignMarketWorkingCapital.review(c);
        assertEquals(before, c.captureState(), "The operator's reserve floor cannot be spent");

        var owned = GeneratedCampaignPersonalRepairTest.fixture("utility_sensor", .99, FoundedCampaignFixture.restore());
        String personalStation = owned.dockedModuleStationId().orElseThrow();
        var personalRef = owned.pilotMarketReference(personalStation).orElseThrow();
        var personalMarket = owned.coordinator().runtime().world().findSession(personalRef.systemId()).orElseThrow().getEntityRegistry().require(personalRef.entityId());
        var operator = owned.coordinator().runtime().world().findFactionStableId(personalMarket.getComponent(FactionComponent.class).factionId).orElseThrow();
        assertTrue(owned.coordinator().runtime().world().transferToFactionTreasury(operator, personalMarket.getComponent(WalletComponent.class),
                personalMarket.getComponent(IdentityComponent.class).name, 1_000_000L, "fixture.personal-market-remittance"));
        var personalBefore = owned.captureState(); GeneratedCampaignMarketWorkingCapital.review(owned);
        assertEquals(personalBefore, owned.captureState(), "A personally acquired market receives no foreign treasury grant");
    }
}
