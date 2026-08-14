package com.spacesim.ui;

import com.spacesim.persistence.EntityId;

import java.util.List;
import java.util.Objects;

/**
 * Read-only local-system minimap data captured from authoritative ECS state.
 *
 * <p>The snapshot contains presentation markers only. It cannot issue movement, combat, trade or
 * travel mutations and therefore remains safe to render at any UI scale.</p>
 *
 * @param markers deterministically ordered visible semantic markers
 */
public record LocalMinimapSnapshot(List<Marker> markers) {
    /** Semantic marker category used by the compact renderer. */
    public enum Kind {
        /** Currently controlled physical player ship. */
        PLAYER,
        /** Economic/industrial station. */
        STATION,
        /** Fleet belonging to the same legal faction as the player ship. */
        FRIENDLY_FLEET,
        /** Combat-capable fleet of a different faction under the current Stage-13 hostility rule. */
        HOSTILE_FLEET,
        /** Other fleet whose hostility is not established by the current simple combat model. */
        OTHER_FLEET,
        /** Finite asteroid/resource object. */
        ASTEROID,
        /** Persistent physical salvage container. */
        SALVAGE
    }

    /**
     * One persistent local object marker.
     *
     * @param entityId stable local persistent ID
     * @param kind presentation classification
     * @param name human-readable object name
     * @param worldX authoritative world X position
     * @param worldY authoritative world Y position
     */
    public record Marker(EntityId entityId, Kind kind, String name, float worldX, float worldY) {
        /**
         * Validates immutable marker values.
         *
         * @param entityId stable local persistent ID
         * @param kind presentation classification
         * @param name human-readable object name
         * @param worldX authoritative world X position
         * @param worldY authoritative world Y position
         */
        public Marker {
            Objects.requireNonNull(entityId, "Minimap EntityId not set");
            Objects.requireNonNull(kind, "Minimap kind not set");
            name = Objects.requireNonNull(name, "Minimap marker name not set").strip();
            if (name.isEmpty() || !Float.isFinite(worldX) || !Float.isFinite(worldY)) {
                throw new IllegalArgumentException("Invalid minimap marker");
            }
        }
    }

    /**
     * Validates and defensively copies the marker list.
     *
     * @param markers deterministically ordered visible semantic markers
     */
    public LocalMinimapSnapshot {
        markers = List.copyOf(Objects.requireNonNull(markers, "Minimap markers not set"));
    }
}
