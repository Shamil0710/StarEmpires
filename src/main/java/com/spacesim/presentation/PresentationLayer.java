package com.spacesim.presentation;

/**
 * Stable high-level order of presentation layers.
 *
 * <p>Layers are intentionally coarse. Concrete renderers may register multiple passes inside one
 * layer; registration order is preserved inside that layer.</p>
 */
public enum PresentationLayer {
    /** Far background, star fields and other scene-clearing/background work. */
    BACKGROUND,
    /** Primary world geometry and sprites. */
    WORLD,
    /** Lighting, particles, emissive work and other world-space effects. */
    EFFECTS,
    /** Tactical overlays, graphs, selection and diagnostics above the world. */
    OVERLAY,
    /** Scene2D/HUD and other screen-space interface rendering. */
    UI
}
