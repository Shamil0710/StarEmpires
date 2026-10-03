package com.spacesim.campaign;

import com.spacesim.persistence.*;
import com.spacesim.ui.*;
import com.spacesim.world.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("slow")
class GeneratedCampaignStockProductionCommandsTest {
    private static final String OWN="faction.player", STOCK="player-government|stock|item.energy", RECIPE="player-government|production|station.arsenal";

    @Test void visibleStockAndRecipeDraftsPersistOnlyOwnIntentUntilExplicitApplication() {
        var c=founded();var before=c.captureState();var foreign=c.coordinator().runtime().world().findFactionStrategicState("faction.alpha");
        var snapshot=new GeneratedWorldUiModel(1L,c.coordinator().runtime(),c.coordinator().content()).capture();
        var rows=GeneratedCampaignFactionUi.rows(c,snapshot);
        assertEquals(before,c.captureState());
        assertTrue(rows.stream().anyMatch(r->r.selection().stableId().equals(STOCK)&&r.name().contains(c.coordinator().content().findItem("item.energy").displayName())));
        var preview=GeneratedCampaignFactionUi.preview(c,STOCK,"more");assertTrue(preview.allowed());assertEquals(before,c.captureState());
        var adopted=c.submitPlayerFactionCommand(preview);assertEquals(before,c.captureState());
        assertEquals(100,policy(adopted).stockPolicies().get(0).targetStockFloor());
        assertEquals(before.playerState(),adopted.captureState().playerState());
        assertEquals(c.coordinator().runtime().captureState().campaign().industrialState(),adopted.coordinator().runtime().captureState().campaign().industrialState());
        assertEquals(foreign,adopted.coordinator().runtime().world().findFactionStrategicState("faction.alpha"));
        var draft=GeneratedCampaignFactionUi.preview(adopted,RECIPE,"more");assertTrue(draft.allowed());
        adopted=adopted.submitPlayerFactionCommand(draft);String first=policy(adopted).productionPolicies().get(0).recipeContentId();
        adopted=roundtrip(adopted);adopted=adopted.submitPlayerFactionCommand(GeneratedCampaignFactionUi.preview(adopted,RECIPE,"more"));
        assertNotEquals(first,policy(adopted).productionPolicies().get(0).recipeContentId());
        adopted=adopted.submitPlayerFactionCommand(GeneratedCampaignFactionUi.preview(adopted,RECIPE,"less"));
        assertEquals(first,policy(adopted).productionPolicies().get(0).recipeContentId());
        adopted=adopted.submitPlayerFactionCommand(GeneratedCampaignFactionUi.preview(adopted,STOCK,"less"));
        assertTrue(policy(adopted).stockPolicies().isEmpty());
        adopted=adopted.submitPlayerFactionCommand(GeneratedCampaignFactionUi.preview(adopted,RECIPE,"reset"));
        assertTrue(policy(adopted).productionPolicies().isEmpty());roundtrip(adopted);
    }

    @Test void noEligibleOwnCommodityConsumerIsReportedAndApplyCannotRewritePhysicalIndustry() {
        var c=founded();c=c.submitPlayerFactionCommand(GeneratedCampaignFactionUi.preview(c,STOCK,"more"));
        c=c.submitPlayerFactionCommand(GeneratedCampaignFactionUi.preview(c,RECIPE,"more"));
        var before=c.captureState();String id="player-government|apply-production";
        assertTrue(GeneratedCampaignFactionUi.explanation(c,id,"apply").contains("рынков 0, производств 0"));
        var preview=GeneratedCampaignFactionUi.preview(c,id,"apply");assertTrue(preview.allowed());assertEquals(before,c.captureState());
        var next=c.submitPlayerFactionCommand(preview);assertEquals(before,next.captureState());roundtrip(next);
    }

    @Test void unknownReferencesUnaffiliatedStaleForeignAndReusedConfirmationsCannotAuthorPolicy() {
        var independent=Stage228CampaignAuthority.create(1);independent.submitIndependentPilotStart(independent.previewIndependentPilotStart());
        assertFalse(independent.previewPlayerFactionPolicy(new FactionPolicyCommand.ApplyStrategicPolicy()).allowed());
        var c=founded();var before=c.captureState();
        assertFalse(c.previewPlayerFactionPolicy(new FactionPolicyCommand.UpdateStockProductionPolicy(new FactionStockProductionPolicyState(List.of(new FactionStockPolicyState("item.unknown",1)),List.of()))).allowed());
        assertFalse(c.previewPlayerFactionPolicy(new FactionPolicyCommand.UpdateStockProductionPolicy(new FactionStockProductionPolicyState(List.of(),List.of(new FactionProductionPolicyState("station.arsenal","recipe.unknown"))))).allowed());
        assertThrows(IllegalArgumentException.class,()->GeneratedCampaignFactionUi.preview(c,STOCK,"less"));
        var preview=GeneratedCampaignFactionUi.preview(c,STOCK,"more");var other=Stage228CampaignAuthority.restore(before);
        assertThrows(IllegalStateException.class,()->other.submitPlayerFactionCommand(preview));
        c.submitPlayerFactionCommand(preview);assertThrows(IllegalStateException.class,()->c.submitPlayerFactionCommand(preview));
        var stale=GeneratedCampaignFactionUi.preview(c,STOCK,"more");c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        assertThrows(IllegalStateException.class,()->c.submitPlayerFactionCommand(stale));
    }

    private static FactionStockProductionPolicyState policy(Stage228CampaignAuthority c){return c.coordinator().runtime().world().findFactionStockProductionPolicy(OWN).orElseThrow();}
    private static Stage228CampaignAuthority founded(){var c=Stage228CampaignAuthority.create(1);c.submitIndependentPilotStart(c.previewIndependentPilotStart());return c.submitPlayerFactionFoundation(c.previewPlayerFactionFoundation(OWN,"Содружество"));}
    private static Stage228CampaignAuthority roundtrip(Stage228CampaignAuthority c){var state=c.captureState();var next=Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(state)));assertEquals(state,next.captureState());return next;}
}
