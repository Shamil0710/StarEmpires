package com.spacesim.presentation.asset;

/**
 * Horizontal direction the authored source texture considers the ship's nose/forward direction.
 *
 * <p>The Stage-8.5 runtime presentation convention is {@link #RIGHT}. Assets authored facing
 * left are mirrored by the presentation transform together with their visual hardpoints.</p>
 */
public enum SourceFacing {
    /** Authored texture already points toward positive local X. */
    RIGHT(1f),
    /** Authored texture points toward negative local X and requires horizontal normalization. */
    LEFT(-1f);

    private final float horizontalScale;

    SourceFacing(float horizontalScale) {
        this.horizontalScale = horizontalScale;
    }

    /** @return +1 for right-authored assets, -1 for assets that must be mirrored */
    public float horizontalScale() {
        return horizontalScale;
    }
}
