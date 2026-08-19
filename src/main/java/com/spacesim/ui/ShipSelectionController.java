package com.spacesim.ui;

import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ShipGlyph;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Presentation-only selected-ship state for the tactical validation viewer.
 *
 * <p>Selection contains only a stable entity id and never changes authoritative combat state.</p>
 */
public final class ShipSelectionController {
    private final ShipHitTestService hitTestService;
    private long selectedEntityId = -1L;

    /** Creates a controller with the standard tactical hit-test service. */
    public ShipSelectionController() {
        this(new ShipHitTestService());
    }

    /**
     * Creates a controller with an explicit hit-test dependency.
     *
     * @param hitTestService read-only ship hit-test service
     */
    public ShipSelectionController(ShipHitTestService hitTestService) {
        this.hitTestService = Objects.requireNonNull(hitTestService, "hitTestService");
    }

    /**
     * Selects the ship under the cursor, or clears selection when the click hits empty tactical space.
     *
     * @param screenX screen-space x coordinate
     * @param screenY screen-space y coordinate
     * @param layout current world-to-screen layout
     * @param snapshot current immutable tactical snapshot
     */
    public void selectAt(
            float screenX,
            float screenY,
            WorldMapLayout layout,
            TacticalPrototypeVisualSnapshot snapshot) {
        OptionalLong hit = hitTestService.hitTest(screenX, screenY, layout, snapshot);
        selectedEntityId = hit.orElse(-1L);
    }

    /** Clears presentation selection without changing simulation state. */
    public void clear() {
        selectedEntityId = -1L;
    }

    /**
     * Clears a stale selection only when the selected entity is no longer present in the visual snapshot.
     *
     * @param snapshot current immutable tactical snapshot
     */
    public void reconcile(TacticalPrototypeVisualSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (selectedEntityId <= 0L) {
            return;
        }
        boolean stillPresent = snapshot.ships().stream()
                .anyMatch(ship -> ship.entityId() == selectedEntityId);
        if (!stillPresent) {
            clear();
        }
    }

    /**
     * Returns the currently selected stable entity id.
     *
     * @return selected entity id, or empty when nothing is selected
     */
    public OptionalLong selectedEntityId() {
        return selectedEntityId > 0L ? OptionalLong.of(selectedEntityId) : OptionalLong.empty();
    }

    /**
     * Resolves the selected visual ship from the supplied current snapshot.
     *
     * @param snapshot current immutable tactical snapshot
     * @return selected ship glyph, or empty when selection is absent/stale
     */
    public Optional<ShipGlyph> selectedShip(TacticalPrototypeVisualSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (selectedEntityId <= 0L) {
            return Optional.empty();
        }
        return snapshot.ships().stream()
                .filter(ship -> ship.entityId() == selectedEntityId)
                .findFirst();
    }
}
