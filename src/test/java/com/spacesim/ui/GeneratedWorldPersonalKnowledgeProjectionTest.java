package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.world.Stage21HPlayerMissionAuthority;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedWorldPersonalKnowledgeProjectionTest {
    @Test void personalMapsAndPanelsCannotBorrowAnUnrelatedFactionArchiveAndRemainExactAfterReload() {
        var campaign = Stage228CampaignAuthority.create(1);
        var runtime = campaign.coordinator().runtime();
        var model = new GeneratedWorldUiModel(1, runtime, campaign.coordinator().content(),
                GeneratedWorldUiModel.CarrierUiSource.none(), () -> campaign.playerState().orElse(null));
        var empty = model.capture();
        assertTrue(empty.galaxy().systems().isEmpty());
        assertTrue(empty.localObjects().isEmpty());
        assertTrue(empty.freight().isEmpty());
        assertTrue(empty.military().isEmpty());
        var diagnosticBeforeStart = new GeneratedWorldUiModel(1, runtime, campaign.coordinator().content()).capture();
        var invitation = ProductionUiProjector.personalClient().capture(campaign, diagnosticBeforeStart, true);
        assertTrue(invitation.rows(GeneratedWorldCommandUiRenderer.Tab.SETTINGS).stream()
                .anyMatch(r -> r.selection().stableId().equals("pilot-start")));
        for (var tab : java.util.List.of(GeneratedWorldCommandUiRenderer.Tab.FACTIONS,
                GeneratedWorldCommandUiRenderer.Tab.MILITARY, GeneratedWorldCommandUiRenderer.Tab.CONTACTS,
                GeneratedWorldCommandUiRenderer.Tab.HISTORY, GeneratedWorldCommandUiRenderer.Tab.INTELLIGENCE))
            assertTrue(invitation.rows(tab).isEmpty(), "Character creation has no privileged faction observer: " + tab);
        campaign.submitIndependentPilotStart(campaign.previewIndependentPilotStart());
        var player = campaign.playerState().orElseThrow();
        var fleet = runtime.world().findFleet(player.activeFleetId()).orElseThrow();
        runtime.world().activateSystem(fleet.systemId());
        String hub = "hub." + fleet.systemId().value();
        var baseline = campaign.captureState();
        var snapshot = model.capture();
        assertEquals(player.discoveredSystemIds().size(), snapshot.galaxy().systems().size());
        assertTrue(snapshot.galaxy().factions().isEmpty(), "Independent pilots have no private faction balances");
        assertTrue(snapshot.galaxy().edges().stream().allMatch(e -> player.discoveredSystemIds().contains(e.first())
                && player.discoveredSystemIds().contains(e.second())));
        assertTrue(snapshot.galaxy().systems().stream().allMatch(s -> s.controllerFactionId() == null));
        assertEquals(1, snapshot.freight().size());
        assertEquals(player.activeFleetId().value(), snapshot.freight().get(0).fleetId());
        assertTrue(snapshot.military().isEmpty());
        assertTrue(snapshot.localObjects().stream().anyMatch(o -> o.stableId().equals("fleet:" + fleet.id().value())));
        assertTrue(snapshot.localObjects().stream().noneMatch(o -> o.stableId().equals("station:" + hub)),
                "An immediate dock contact is not yet a saved coordinate");
        var archive = runtime.discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID);
        for (var object : snapshot.localObjects()) {
            if (object.kind() == GeneratedWorldUiSnapshot.ObjectKind.FLEET) continue;
            assertTrue(archive.entries().stream().anyMatch(k -> object.stableId().endsWith(":" + k.object().objectId())
                    && k.knownLocation().filter(object.position()::equals).isPresent()));
        }
        var unrelated = new ProductionUiProjector("faction.beta").capture(campaign, snapshot);
        var diagnostic = new GeneratedWorldUiModel(1, runtime, campaign.coordinator().content()).capture();
        assertEquals(unrelated, new ProductionUiProjector("faction.beta").capture(campaign, diagnostic),
                "A personal panel must also restrict a diagnostic input snapshot");
        assertTrue(unrelated.rows(GeneratedWorldCommandUiRenderer.Tab.MILITARY).isEmpty());
        assertTrue(unrelated.rows(GeneratedWorldCommandUiRenderer.Tab.INTELLIGENCE).stream()
                .noneMatch(r -> r.selection().stableId().startsWith("overlay:")));
        assertEquals(baseline, campaign.captureState(), "Neither map nor panel may discover objects");
        campaign.submitPilotAction(campaign.previewPilotAction("DOCK", hub, "", 0));
        var visited = model.capture().localObjects().stream().filter(o -> o.stableId().equals("station:" + hub)).findFirst().orElseThrow();
        assertTrue(visited.sections().stream().flatMap(s -> s.lines().stream()).noneMatch(l ->
                l.label().equals("Товары") || l.label().equals("Модули") || l.label().equals("Верфи") || l.label().equals("Кошелёк")));
        var expected = model.capture();
        var saved = campaign.captureState();
        var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved)));
        var next = new GeneratedWorldUiModel(1, restored.coordinator().runtime(), restored.coordinator().content(),
                GeneratedWorldUiModel.CarrierUiSource.none(), () -> restored.playerState().orElse(null));
        assertEquals(saved, restored.captureState());
        assertEquals(expected, next.capture());
        assertEquals(new ProductionUiProjector("faction.beta").capture(campaign, expected),
                new ProductionUiProjector("faction.beta").capture(restored, next.capture()));
    }
}
