package com.spacesim.campaign;

import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.world.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedCampaignFactionCommandsTest {
    private static final String OWN = "faction.player", FOREIGN = "faction.alpha";

    @Test void policiesUseOwnSharedRulesAndPreservePhysicalOwnersMoneyAndClock() {
        var c = founded(); var before = c.captureState(); var freight = c.coordinator().runtime().captureState().freight();
        var doctrine = new FactionDoctrineState(75,60,45,70,80,25,90);
        var preview = c.previewPlayerFactionPolicy(new FactionPolicyCommand.UpdateDoctrine(doctrine));
        assertTrue(preview.allowed()); assertEquals(before,c.captureState());
        var next = c.submitPlayerFactionCommand(preview);
        assertEquals(doctrine,next.coordinator().runtime().world().findFactionStrategicState(OWN).orElseThrow().doctrine());
        assertEquals(before,c.captureState()); assertEquals(freight,next.coordinator().runtime().captureState().freight());
        assertEquals(c.playerState(),next.playerState()); assertEquals(before.smallCraft(),next.captureState().smallCraft());
        assertThrows(IllegalStateException.class,()->c.submitPlayerFactionCommand(preview));
        var fiscal = new FactionFiscalPolicyState(1200,250,1_000_000,2_000_000,300_000,400_000);
        next = next.submitPlayerFactionCommand(next.previewPlayerFactionPolicy(new FactionPolicyCommand.UpdateFiscalPolicy(fiscal)));
        assertEquals(fiscal,next.coordinator().runtime().world().findFactionFiscalPolicy(OWN).orElseThrow());
        assertEquals(75_000_000L,next.playerState().orElseThrow().walletMilliCredits());
        assertEquals(0L,next.coordinator().runtime().world().findFactionEconomicState(OWN).orElseThrow().treasuryMilliCredits());
        roundtrip(next);
    }

    @Test void independentForeignStaleAndReusedFactionCommandsRejectWithoutChanges() {
        var independent = Stage228CampaignAuthority.create(1);independent.submitIndependentPilotStart(independent.previewIndependentPilotStart());
        var before = independent.captureState();
        assertFalse(independent.previewPlayerFactionPolicy(new FactionPolicyCommand.UpdateDoctrine(FactionDoctrineState.neutral())).allowed());
        assertEquals(before,independent.captureState());
        var c = independent.submitPlayerFactionFoundation(independent.previewPlayerFactionFoundation(OWN,"Содружество"));
        before=c.captureState();
        assertFalse(c.previewPlayerFactionEmbargo(new DiplomaticEmbargoCommand.Impose(FOREIGN,"faction.beta",-1,"test")).allowed());
        assertFalse(c.previewPlayerFactionTreaty(new DiplomaticTreatyCommand.Offer(FOREIGN,OWN,clauses(),-1)).allowed());
        assertEquals(before,c.captureState());
        var preview=c.previewPlayerFactionPolicy(new FactionPolicyCommand.UpdateDoctrine(FactionDoctrineState.neutral()));
        var other=Stage228CampaignAuthority.restore(before);
        assertThrows(IllegalStateException.class,()->other.submitPlayerFactionCommand(preview));
        c.coordinator().setPaused(true);
        assertThrows(IllegalStateException.class,()->c.submitPlayerFactionCommand(preview));
    }

    @Test void embargoImpositionAndRevocationPersistLegalStateWithoutEconomicDamage() {
        var c=founded();var originalPlayer=c.playerState();var originalFreight=c.coordinator().runtime().captureState().freight();
        var preview=c.previewPlayerFactionEmbargo(new DiplomaticEmbargoCommand.Impose(OWN,FOREIGN,-1,"player.market-policy"));
        assertTrue(preview.allowed());c=c.submitPlayerFactionCommand(preview);
        assertEquals(1,c.coordinator().runtime().world().findFactionDiplomacyState(OWN).orElseThrow().embargoes().size());
        c=roundtrip(c);
        c=c.submitPlayerFactionCommand(c.previewPlayerFactionEmbargo(new DiplomaticEmbargoCommand.Revoke(OWN,FOREIGN)));
        assertTrue(c.coordinator().runtime().world().findFactionDiplomacyState(OWN).orElseThrow().embargoes().isEmpty());
        assertEquals(originalPlayer,c.playerState());assertEquals(originalFreight,c.coordinator().runtime().captureState().freight());roundtrip(c);
    }

    @Test void treatyOffersDoNotAutoAcceptAndIncomingAcceptanceUsesSharedLifecycle() {
        var c=founded();var before=c.captureState();var offer=c.previewPlayerFactionTreaty(new DiplomaticTreatyCommand.Offer(OWN,FOREIGN,clauses(),-1));
        assertTrue(offer.allowed());assertEquals(before,c.captureState());c=c.submitPlayerFactionCommand(offer);
        var proposed=c.coordinator().runtime().world().findFactionDiplomacyState(OWN).orElseThrow().treaties().get(0);
        assertEquals(DiplomaticTreatyState.Status.PROPOSED,proposed.status());
        assertFalse(c.previewPlayerFactionTreaty(new DiplomaticTreatyCommand.Accept(OWN,proposed.treatyId())).allowed());
        // Explicit incoming-offer fixture issued by the ordinary foreign world authority, never the player adapter.
        var incoming=c.coordinator().runtime().world().applyDiplomaticTreatyCommand(new DiplomaticTreatyCommand.Offer(FOREIGN,OWN,clauses(),-1)).treaty();
        c=c.submitPlayerFactionCommand(c.previewPlayerFactionTreaty(new DiplomaticTreatyCommand.Accept(OWN,incoming.treatyId())));
        assertEquals(DiplomaticTreatyState.Status.ACTIVE,c.coordinator().runtime().world().findDiplomaticTreaty(incoming.treatyId()).orElseThrow().status());
        c=c.submitPlayerFactionCommand(c.previewPlayerFactionTreaty(new DiplomaticTreatyCommand.TerminateWithNotice(OWN,incoming.treatyId(),20)));
        assertEquals(DiplomaticTreatyState.Status.TERMINATING,c.coordinator().runtime().world().findDiplomaticTreaty(incoming.treatyId()).orElseThrow().status());roundtrip(c);
    }

    @Test void discoveredClaimAndWithdrawalDoNotGrantForeignSovereigntyOrUnknownTargets() {
        var c=founded();var home=c.playerState().orElseThrow().homeSystemId();var world=c.coordinator().runtime().world();
        var originalController=world.controllingFaction(home);var originalFreight=c.coordinator().runtime().captureState().freight();
        var discovered=c.playerState().orElseThrow().discoveredSystemIds();
        var unknown=world.getTopology().systems().stream().map(s->s.id()).filter(id->!discovered.contains(id)).findFirst().orElseThrow();
        assertFalse(c.previewPlayerFactionTerritory("CLAIM",unknown,"",-1).allowed());
        assertFalse(c.previewPlayerFactionTerritory("RELINQUISH",home,"",-1).allowed());
        var before=c.captureState();var claim=c.previewPlayerFactionTerritory("CLAIM",home,"",-1);assertTrue(claim.allowed());assertEquals(before,c.captureState());
        c=c.submitPlayerFactionCommand(claim);assertEquals(originalController,c.coordinator().runtime().world().controllingFaction(home));
        assertEquals(1,c.coordinator().runtime().world().findFactionStrategicState(OWN).orElseThrow().territorialClaims().size());
        c=roundtrip(c);c=c.submitPlayerFactionCommand(c.previewPlayerFactionTerritory("WITHDRAW",home,"",-1));
        assertTrue(c.coordinator().runtime().world().findFactionStrategicState(OWN).orElseThrow().territorialClaims().isEmpty());
        assertEquals(originalFreight,c.coordinator().runtime().captureState().freight());roundtrip(c);
    }
    @Test void personalFactionUiProjectionIsPureIndependentOfViewerAndDraftsTheDisplayedField() {
        var c=founded();var before=c.captureState();var model=new com.spacesim.ui.GeneratedWorldUiModel(1,c.coordinator().runtime(),c.coordinator().content());
        var world=model.capture();var rows=com.spacesim.ui.GeneratedCampaignFactionUi.rows(c,world);
        assertEquals(before,c.captureState());assertTrue(rows.size()>20);
        assertTrue(rows.stream().allMatch(row->row.category().startsWith("Мо")));
        var id="player-government|doctrine|0";
        assertTrue(rows.stream().anyMatch(row->row.selection().stableId().equals(id)));
        assertTrue(com.spacesim.ui.GeneratedCampaignFactionUi.explanation(c,id,"more").contains("50 → 55"));
        var preview=com.spacesim.ui.GeneratedCampaignFactionUi.preview(c,id,"more");assertTrue(preview.allowed());assertEquals(before,c.captureState());
        c=c.submitPlayerFactionCommand(preview);
        assertEquals(55,c.coordinator().runtime().world().findFactionStrategicState(OWN).orElseThrow().doctrine().tradeOpenness());
        var projected=new com.spacesim.ui.ProductionUiProjector(FOREIGN).capture(c,new com.spacesim.ui.GeneratedWorldUiModel(1,c.coordinator().runtime(),c.coordinator().content()).capture());
        assertTrue(projected.rows(com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab.FACTIONS).stream().anyMatch(row->row.selection().stableId().equals(id)));
        roundtrip(c);
    }

    @Test void counterofferAndRenewalPreserveTheActualGrantorWhenDirectoryOwnerChanges() {
        var c=founded();var right=new DiplomaticTreatyClauseState(DiplomaticTreatyClauseState.Kind.MARKET_ACCESS,DiplomaticTreatyClauseState.Direction.OWNER_TO_COUNTERPARTY,null);
        var incoming=c.coordinator().runtime().world().applyDiplomaticTreatyCommand(new DiplomaticTreatyCommand.Offer(FOREIGN,OWN,List.of(right),-1)).treaty();
        var preview=com.spacesim.ui.GeneratedCampaignFactionUi.preview(c,"player-government|treaty-extra|"+incoming.treatyId(),"counter");
        assertTrue(preview.allowed());c=c.submitPlayerFactionCommand(preview);
        var counter=c.coordinator().runtime().world().findFactionDiplomacyState(OWN).orElseThrow().treaties().get(0);
        assertEquals(DiplomaticTreatyClauseState.Direction.COUNTERPARTY_TO_OWNER,counter.clauses().get(0).direction());
        // Ordinary foreign acceptance fixture keeps the same foreign-to-player market grant.
        c.coordinator().runtime().world().applyDiplomaticTreatyCommand(new DiplomaticTreatyCommand.Accept(FOREIGN,counter.treatyId()));
        var shared=c.coordinator().runtime().world().applyDiplomaticTreatyCommand(new DiplomaticTreatyCommand.Renew(FOREIGN,counter.treatyId(),-1)).treaty();
        assertEquals(DiplomaticTreatyClauseState.Direction.OWNER_TO_COUNTERPARTY,shared.clauses().get(0).direction());
        assertEquals(75_000_000L,c.playerState().orElseThrow().walletMilliCredits());roundtrip(c);
    }

    private static List<DiplomaticTreatyClauseState> clauses(){return List.of(new DiplomaticTreatyClauseState(DiplomaticTreatyClauseState.Kind.MARKET_ACCESS,DiplomaticTreatyClauseState.Direction.MUTUAL,null));}
    private static Stage228CampaignAuthority founded(){var c=Stage228CampaignAuthority.create(1);c.submitIndependentPilotStart(c.previewIndependentPilotStart());return c.submitPlayerFactionFoundation(c.previewPlayerFactionFoundation(OWN,"Содружество"));}
    private static Stage228CampaignAuthority roundtrip(Stage228CampaignAuthority c){var state=c.captureState();var r=Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(state)));assertEquals(state,r.captureState());return r;}
}
