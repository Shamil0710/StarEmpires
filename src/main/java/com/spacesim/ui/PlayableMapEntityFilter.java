package com.spacesim.ui;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.IdentityComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Presentation-only zoom declutter policy for the Stage-14C playable world view.
 *
 * <p>The policy never changes simulation/discovery state. It merely omits low-priority markers from
 * the large world render at distant zooms; the compact minimap continues to consume the complete
 * authoritative marker snapshot.</p>
 */
public final class PlayableMapEntityFilter {
    /** Below this zoom only navigation-critical stations/fleets are retained. */
    public static final float DETAIL_ZOOM = 1.8f;
    /** At and above this zoom all supported local objects are retained. */
    public static final float FULL_DETAIL_ZOOM = 3.0f;

    private PlayableMapEntityFilter() {
        throw new AssertionError("PlayableMapEntityFilter does not create instances");
    }

    /**
     * Builds a stable-order render list appropriate for the current world zoom.
     *
     * @param entities current authoritative local entities
     * @param selected current active player entity, always retained when non-null
     * @param zoom current bounded gameplay zoom
     * @return presentation-only filtered list
     */
    public static List<Entity> filter(Iterable<Entity> entities, Entity selected, float zoom) {
        List<Entity> result = new ArrayList<>();
        if (entities == null) {
            return result;
        }
        float safeZoom = Float.isFinite(zoom) ? zoom : WorldMapLayout.MIN_ZOOM;
        for (Entity entity : entities) {
            if (entity == null) {
                continue;
            }
            if (entity == selected) {
                result.add(entity);
                continue;
            }
            IdentityComponent identity = entity.getComponent(IdentityComponent.class);
            if (identity == null || include(identity.kind, safeZoom)) {
                result.add(entity);
            }
        }
        return result;
    }

    private static boolean include(IdentityComponent.Kind kind, float zoom) {
        if (zoom >= FULL_DETAIL_ZOOM) {
            return true;
        }
        if (kind == IdentityComponent.Kind.STATION || kind == IdentityComponent.Kind.FLEET) {
            return true;
        }
        return zoom >= DETAIL_ZOOM && kind == IdentityComponent.Kind.SALVAGE;
    }
}
