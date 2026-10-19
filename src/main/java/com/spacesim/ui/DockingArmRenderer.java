package com.spacesim.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * GPU owner/drawer of the boarding arm: the authored tube segment repeated along the arm and the
 * docking collar at the ship end ({@link DockingArmGeometry.Layout}).
 */
final class DockingArmRenderer {
    /** Authored repeating tube segment, 256 by 64 texels, tiling along X. */
    static final String SEGMENT_PATH = "assets/stage20_5/stations/docking_arm_segment_v1.png";
    /** Authored docking collar, 64 by 64 texels, tube ring entering from the left, clamp mouth facing right. */
    static final String COLLAR_PATH = "assets/stage20_5/stations/docking_arm_collar_v1.png";

    private final Texture segment = load(SEGMENT_PATH, true);
    private final Texture collar = load(COLLAR_PATH, false);
    private boolean disposed;

    /**
     * Draws one arm inside an active batch; the batch's tint and blend factors are restored after.
     *
     * @param batch active sprite batch
     * @param arm arm layout on screen
     */
    void draw(SpriteBatch batch, DockingArmGeometry.Layout arm) {
        if (disposed) throw new IllegalStateException("docking arm renderer is disposed");
        BatchState entry = BatchState.capture(batch);
        try {
            batch.setColor(Color.WHITE);
            float quad = arm.quadHeight();
            float tubeLength = (float) Math.hypot(arm.tubeEndX() - arm.portX(), arm.tubeEndY() - arm.portY());
            if (tubeLength > 0.5f && arm.tileRepeats() > 0f) {
                int sourceWidth = Math.max(1, Math.round(arm.tileRepeats() * segment.getWidth()));
                batch.draw(segment, arm.portX(), arm.portY() - quad * 0.5f, 0f, quad * 0.5f, tubeLength, quad, 1f, 1f,
                        arm.rotationDegrees(), 0, 0, sourceWidth, segment.getHeight(), false, false);
            }
            // A short arm squeezes the collar along its length, so it stays between the port and the hull.
            float collarLength = arm.collarLength();
            batch.draw(collar, arm.collarX() - collarLength * 0.5f, arm.collarY() - quad * 0.5f, collarLength * 0.5f, quad * 0.5f,
                    collarLength, quad, 1f, 1f, arm.rotationDegrees(), 0, 0, collar.getWidth(), collar.getHeight(), false, false);
        } finally {
            entry.restore(batch);
        }
    }

    /** Releases the arm textures exactly once. */
    void dispose() {
        if (disposed) return;
        disposed = true;
        segment.dispose();
        collar.dispose();
    }

    private static Texture load(String path, boolean repeatAlong) {
        Texture texture = new Texture(Gdx.files.internal(path));
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        if (repeatAlong) texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.ClampToEdge);
        return texture;
    }
}
