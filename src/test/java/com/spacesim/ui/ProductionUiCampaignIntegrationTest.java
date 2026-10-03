package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("slow")
final class ProductionUiCampaignIntegrationTest {
    @TempDir Path directory;

    @Test
    void productionProjectionIsPureActorBoundedAndIdenticalAfterComposedSaveLoad() throws Exception {
        var campaign = Stage228CampaignAuthority.create(1L);
        var coordinator = campaign.coordinator();
        var model = new GeneratedWorldUiModel(1L, coordinator.runtime(), coordinator.content());
        var observer = coordinator.actors().capture().get(0).factionContentId();
        var projector = new ProductionUiProjector(observer);
        var before = campaign.captureState();
        var world = model.capture();
        var projected = projector.capture(campaign, world);
        assertEquals(before, campaign.captureState(), "presentation cannot write authority");
        for (var fleet : world.military()) {
            if (fleet.inSystem()) assertTrue(fleet.status().startsWith("В системе"),
                    "physical placement is not evidence of a patrol order");
        }
        assertFalse(projected.rows(Tab.FACTIONS).isEmpty());
        assertFalse(projected.rows(Tab.LOGISTICS).isEmpty());
        assertFalse(projected.rows(Tab.SHIPS).isEmpty());
        assertFalse(projected.rows(Tab.INDUSTRY).isEmpty());
        assertFalse(projected.rows(Tab.INTELLIGENCE).isEmpty());
        assertTrue(projected.rows(Tab.CONTACTS).isEmpty(), "no synthetic NPCs/contracts in migrated fresh state");
        assertTrue(projected.rows(Tab.HISTORY).isEmpty(), "no fake events");
        for (var faction : projected.rows(Tab.FACTIONS)) {
            boolean economyShown = faction.sections().stream().anyMatch(section -> section.title().equals("Экономика и территория"));
            assertEquals(observer.equals(faction.selection().stableId()), economyShown, "foreign private balance must not leak");
        }
        for (var row : projected.rows(Tab.LOGISTICS)) {
            var original = world.freight().stream().filter(freight -> Long.toString(freight.fleetId())
                    .equals(row.selection().stableId())).findFirst().orElseThrow();
            assertEquals(observer, original.factionId());
            assertFalse(row.provenance().isBlank());
            assertTrue(row.explainedSections().size() > row.sections().size());
        }
        var path = directory.resolve("campaign.s25");
        Stage228GeneratedCampaignPersistenceCodec.write(path, before);
        var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(path));
        var next = restored.coordinator();
        var restoredWorld = new GeneratedWorldUiModel(1L, next.runtime(), next.content()).capture();
        assertEquals(projected, projector.capture(restored, restoredWorld));
        assertThrows(IllegalArgumentException.class, () -> new ProductionUiProjector("unknown-observer")
                .capture(campaign, world));
        assertEquals(before, campaign.captureState());
    }
}
