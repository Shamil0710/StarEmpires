package com.spacesim.campaign;

import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ui.GeneratedCampaignFleetUi;
import com.spacesim.ui.GeneratedWorldUiModel;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedCampaignPlayerCommandGroupTest {
    @Test void retirementKeepsHistoryAllowsRegroupingAndCannotMasqueradeAsNativeV23() {
        var c = FoundedCampaignFixture.restore();
        c = c.submitPlayerFactionCommand(c.previewPlayerAssetAffiliation());
        var p = c.playerState().orElseThrow(); var fleet = p.activeFleetId();
        var home = c.coordinator().runtime().world().findFleet(fleet).orElseThrow().systemId();
        c = c.submitPlayerFactionCommand(c.previewPlayerCommandGroup("Первая группа", List.of(fleet), home));
        long group = c.coordinator().commands().groups().get(0).id();
        var physical = c.coordinator().runtime().captureState(); var before = c.captureState();
        var preview = c.previewPlayerCommandGroupRetirement(group);
        assertTrue(preview.allowed()); assertEquals(before, c.captureState());
        c = c.submitPlayerFactionCommand(preview);
        assertTrue(c.coordinator().commands().requireGroup(group).retired());
        assertEquals(physical, c.coordinator().runtime().captureState());
        assertFalse(c.previewPlayerCommandGroupRetirement(group).allowed());
        assertTrue(c.previewPlayerCommandGroup("Следующая группа", List.of(fleet), home).allowed());
        var saved = c.captureState(); var bytes = Stage228GeneratedCampaignPersistenceCodec.encode(saved);
        assertEquals(saved, Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(bytes)).captureState());
        java.nio.ByteBuffer.wrap(bytes).putInt(4, 23).putInt(8, 23);
        byte[] old = "m22.8.generated-campaign.v23".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.arraycopy(old, 0, bytes, 14, old.length);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistenceCodec.decode(bytes))
                .getMessage().contains("native campaign v24"));
    }

    @Test void physicalLossRetiresMembershipOnItsActualTickWithoutRestoreEventsOrReplacementAssets() {
        var c = FoundedCampaignFixture.restore();
        c = c.submitPlayerFactionCommand(c.previewPlayerAssetAffiliation());
        var p = c.playerState().orElseThrow();
        var w = c.coordinator().runtime().world(); var placement = w.findFleet(p.activeFleetId()).orElseThrow();
        c = c.submitPlayerFactionCommand(c.previewPlayerCommandGroup("Lost group", List.of(placement.id()), placement.systemId()));
        w = c.coordinator().runtime().world();
        c.coordinator().runtime().destroyLocalFreighter(placement.id(), com.spacesim.world.DestructionPolicy.destroyAll());
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds()); c.coordinator().setPaused(true);
        assertTrue(c.coordinator().commands().groups().get(0).retired());
        assertTrue(w.findFleet(placement.id()).isEmpty());
        assertFalse(c.playerState().orElseThrow().ownedFleetIds().contains(placement.id()));
        assertEquals(p.walletMilliCredits(), c.playerState().orElseThrow().walletMilliCredits());
        assertTrue(c.playerJournal().entries().stream().anyMatch(e -> e.action().equals("GROUP_AUTHORITY_LOST")));
        var saved = c.captureState(); var restored = Stage228CampaignAuthority.restore(
                Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(saved)));
        assertEquals(saved, restored.captureState());
        restored.coordinator().setPaused(false); restored.advanceFrame(restored.coordinator().session().fixedStepSeconds());
        assertEquals(1, restored.playerJournal().entries().stream().filter(e -> e.action().equals("GROUP_AUTHORITY_LOST")).count());
    }
    @Test void groupFormationPreservesExistingAssetsAndUsesPureSingleUseUiConfirmation() {
        var c = FoundedCampaignFixture.restore();
        c = c.submitPlayerFactionCommand(c.previewPlayerAssetAffiliation());
        var player = c.playerState().orElseThrow();
        var fleet = player.activeFleetId();
        var before = c.captureState();
        var physical = c.coordinator().runtime().captureState();
        var model = new GeneratedWorldUiModel(1, c.coordinator().runtime(), c.coordinator().content());
        String selected = "player-fleet|group|" + fleet.value();
        assertTrue(GeneratedCampaignFleetUi.rows(c, model.capture()).stream().anyMatch(row -> row.selection().stableId().equals(selected)));
        assertEquals(before, c.captureState());
        var preview = GeneratedCampaignFleetUi.preview(c, selected, "group");
        assertTrue(preview.allowed()); assertEquals(before, c.captureState());
        var adopted = c.submitPlayerFactionCommand(preview);
        assertEquals(physical, adopted.coordinator().runtime().captureState(), "Group metadata cannot change ordinary assets or readiness inputs");
        var group = adopted.coordinator().commands().groups().stream().filter(g -> g.memberFleetIds().equals(List.of(fleet))).findFirst().orElseThrow();
        assertEquals(player.ownedFleetIds(), adopted.playerState().orElseThrow().ownedFleetIds());
        assertEquals(2500, group.maxStrategicRiskBps());
        assertTrue(adopted.playerJournal().entries().stream().anyMatch(e -> e.action().equals("FORM_COMMAND_GROUP")));
        assertThrows(IllegalStateException.class, () -> adopted.submitPlayerFactionCommand(preview));
        assertFalse(adopted.previewPlayerCommandGroup("Duplicate", List.of(fleet), group.homeSystemId()).allowed());
        var saved = adopted.captureState();
        assertEquals(saved, Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved))).captureState());
    }

    @Test void unregisteredAndForeignAssetsCannotBecomePersonalCommandGroups() {
        var c = FoundedCampaignFixture.restore();
        var player = c.playerState().orElseThrow();
        var home = c.coordinator().runtime().world().findFleet(player.activeFleetId()).orElseThrow().systemId();
        var before = c.captureState();
        assertFalse(c.previewPlayerCommandGroup("Unregistered", player.ownedFleetIds(), home).allowed());
        var foreign = c.coordinator().runtime().world().getFleetPlacements().stream().filter(f -> !player.ownedFleetIds().contains(f.id())).findFirst().orElseThrow();
        assertFalse(c.previewPlayerCommandGroup("Foreign", List.of(foreign.id()), home).allowed());
        assertEquals(before, c.captureState());
    }
}
