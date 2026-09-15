package com.spacesim;

import com.spacesim.campaign.GeneratedCampaignCoordinator;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.SelectionKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;
import com.spacesim.ui.GeneratedWorldUiModel;
import com.spacesim.ui.MapCameraState;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedWorldProductionClientSmokeTest {
    @TempDir
    Path tempDir;

    @Test
    void ordinaryClientProjectionSupportsSelectionCameraPanelsAndSaveReload() throws IOException {
        GeneratedCampaignCoordinator campaign = GeneratedCampaignCoordinator.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        GeneratedWorldUiModel model = new GeneratedWorldUiModel(
                campaign.rootSeed(), campaign.runtime(), campaign.content());
        var initial = model.capture();

        assertFalse(initial.galaxy().systems().isEmpty(), "generated world must boot into the client model");
        assertFalse(initial.localObjects().isEmpty(), "system map must expose selectable physical objects");
        assertFalse(initial.galaxy().factions().isEmpty(), "factions panel must expose autonomous factions");
        assertFalse(initial.freight().isEmpty(), "logistics panel must expose physical freight fleets");

        var objectSelection = new UiSelection(
                SelectionKind.LOCAL_OBJECT, initial.localObjects().get(0).stableId());
        var factionSelection = new UiSelection(
                SelectionKind.FACTION, initial.galaxy().factions().get(0).factionId());
        var freightSelection = new UiSelection(
                SelectionKind.FREIGHT, Long.toString(initial.freight().get(0).fleetId()));
        assertEquals(initial.localObjects().get(0).stableId(), objectSelection.stableId());
        assertEquals(initial.galaxy().factions().get(0).factionId(), factionSelection.stableId());
        assertEquals(Long.toString(initial.freight().get(0).fleetId()), freightSelection.stableId());

        MapCameraState camera = new MapCameraState();
        float overviewZoom = camera.zoom();
        camera.zoomAt(-2f, 700f, 420f, 640f, 360f);
        assertTrue(camera.zoom() > overviewZoom, "mouse-wheel semantics must support inspection zoom");
        float panXBefore = camera.panX();
        float panYBefore = camera.panY();
        camera.pan(48f, -27f);
        assertNotEquals(panXBefore, camera.panX(), "middle-drag semantics must pan horizontally");
        assertNotEquals(panYBefore, camera.panY(), "middle-drag semantics must pan vertically");

        campaign.setTimeScale(8d);
        campaign.advanceFrame(0.1f);
        var beforeSave = campaign.captureState();
        var beforeSaveProjection = model.capture();
        Path save = tempDir.resolve("generated-world-runtime.s25");
        Stage21IGeneratedWorldRuntimePersistenceCodec.write(save, beforeSave);

        var restoredCheckpoint = Stage21IGeneratedWorldRuntimePersistenceCodec.readOrMigrate(save);
        GeneratedCampaignCoordinator restored = GeneratedCampaignCoordinator.restore(restoredCheckpoint);
        GeneratedWorldUiModel restoredModel = new GeneratedWorldUiModel(
                restored.rootSeed(), restored.runtime(), restored.content());
        var afterLoadProjection = restoredModel.capture();

        assertEquals(beforeSave, restored.captureState(),
                "production save/reload must restore the exact accepted campaign authority chain");
        assertEquals(beforeSaveProjection.worldTick(), afterLoadProjection.worldTick());
        assertEquals(beforeSaveProjection.activeSystemId(), afterLoadProjection.activeSystemId());
        assertEquals(
                beforeSaveProjection.galaxy().factions().stream().map(view -> view.factionId()).toList(),
                afterLoadProjection.galaxy().factions().stream().map(view -> view.factionId()).toList());
        assertEquals(
                beforeSaveProjection.freight().stream().map(view -> view.fleetId()).toList(),
                afterLoadProjection.freight().stream().map(view -> view.fleetId()).toList());
    }
}
