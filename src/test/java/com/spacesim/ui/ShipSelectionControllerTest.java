package com.spacesim.ui;

import com.badlogic.gdx.math.Vector2;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ShipGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.TacticalSide;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipSelectionControllerTest {
    private static final WorldMapLayout LAYOUT = new WorldMapLayout(
            0f,
            0f,
            1000f,
            700f,
            0f,
            WorldMapLayout.WORLD_WIDTH * 0.5f,
            WorldMapLayout.WORLD_HEIGHT * 0.5f,
            1f);

    @Test
    void hitTestSelectsTheNearestOverlappingVisibleMarker() {
        ShipGlyph first = ship(101L, WorldMapLayout.WORLD_WIDTH * 0.5, WorldMapLayout.WORLD_HEIGHT * 0.5);
        ShipGlyph second = ship(102L, first.xM() + 8d, first.yM());
        TacticalPrototypeVisualSnapshot snapshot = snapshot(first, second);
        Vector2 click = project(second);

        var hit = new ShipHitTestService().hitTest(click.x, click.y, LAYOUT, snapshot);

        assertTrue(hit.isPresent());
        assertEquals(102L, hit.getAsLong());
    }

    @Test
    void hitTestRejectsEmptyTacticalSpace() {
        ShipGlyph ship = ship(201L, WorldMapLayout.WORLD_WIDTH * 0.5, WorldMapLayout.WORLD_HEIGHT * 0.5);

        var hit = new ShipHitTestService().hitTest(20f, 20f, LAYOUT, snapshot(ship));

        assertFalse(hit.isPresent());
    }

    @Test
    void selectionPersistsAcrossTicksWhileEntityRemainsPresent() {
        ShipGlyph initial = ship(301L, WorldMapLayout.WORLD_WIDTH * 0.5, WorldMapLayout.WORLD_HEIGHT * 0.5);
        ShipSelectionController controller = new ShipSelectionController();
        Vector2 click = project(initial);
        controller.selectAt(click.x, click.y, LAYOUT, snapshot(initial));

        ShipGlyph moved = ship(301L, initial.xM() + 120d, initial.yM() + 40d);
        controller.reconcile(snapshot(moved));

        assertTrue(controller.selectedEntityId().isPresent());
        assertEquals(301L, controller.selectedEntityId().getAsLong());
        assertEquals(moved, controller.selectedShip(snapshot(moved)).orElseThrow());
    }

    @Test
    void emptyClickAndMissingEntityClearSelection() {
        ShipGlyph ship = ship(401L, WorldMapLayout.WORLD_WIDTH * 0.5, WorldMapLayout.WORLD_HEIGHT * 0.5);
        ShipSelectionController controller = new ShipSelectionController();
        Vector2 click = project(ship);
        controller.selectAt(click.x, click.y, LAYOUT, snapshot(ship));
        assertTrue(controller.selectedEntityId().isPresent());

        controller.selectAt(20f, 20f, LAYOUT, snapshot(ship));
        assertFalse(controller.selectedEntityId().isPresent());

        controller.selectAt(click.x, click.y, LAYOUT, snapshot(ship));
        controller.reconcile(TacticalPrototypeVisualSnapshot.empty());
        assertFalse(controller.selectedEntityId().isPresent());
    }

    private static ShipGlyph ship(long entityId, double xM, double yM) {
        return new ShipGlyph(
                entityId,
                TacticalSide.ALPHA,
                ShipVisualRole.MISSILE,
                xM,
                yM,
                0d,
                150d,
                48d,
                0d,
                1d,
                false);
    }

    private static TacticalPrototypeVisualSnapshot snapshot(ShipGlyph... ships) {
        return new TacticalPrototypeVisualSnapshot(
                List.of(ships),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private static Vector2 project(ShipGlyph ship) {
        Vector2 result = new Vector2();
        assertTrue(LAYOUT.worldToScreen((float) ship.xM(), (float) ship.yM(), result));
        return result;
    }
}
