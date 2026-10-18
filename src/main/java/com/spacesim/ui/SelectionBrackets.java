package com.spacesim.ui;

import java.util.List;

/**
 * Corner-bracket selection frame around a rotated hull sprite on the system map.
 *
 * <p>A selected object whose sprite is at least marker-sized gets four corner brackets around
 * the screen bounds of its rotated physical hull; smaller objects keep the circular marker.
 * Pure presentation geometry: it never reads or changes simulation state.</p>
 */
final class SelectionBrackets {
    /** Fraction of the shorter frame side used by one bracket arm. */
    static final float ARM_FRACTION = 0.22f;

    /**
     * One straight bracket stroke.
     *
     * @param x1 start X
     * @param y1 start Y
     * @param x2 end X
     * @param y2 end Y
     */
    record Segment(float x1, float y1, float x2, float y2) { }

    /**
     * Axis-aligned bracket frame.
     *
     * @param left left edge
     * @param bottom bottom edge
     * @param right right edge
     * @param top top edge
     * @param arm length of each bracket arm
     */
    record Frame(float left, float bottom, float right, float top, float arm) {
        /** @return eight strokes, two per corner, each running inward from the corner */
        List<Segment> segments() {
            return List.of(
                    new Segment(left, bottom, left + arm, bottom),
                    new Segment(left, bottom, left, bottom + arm),
                    new Segment(right, bottom, right - arm, bottom),
                    new Segment(right, bottom, right, bottom + arm),
                    new Segment(left, top, left + arm, top),
                    new Segment(left, top, left, top - arm),
                    new Segment(right, top, right - arm, top),
                    new Segment(right, top, right, top - arm));
        }
    }

    private SelectionBrackets() {
    }

    /**
     * @param hasSprite whether the object has hull art
     * @param lengthPixels on-screen physical hull length
     * @param markerPixels size of the object's circular marker
     * @return true when the selection is shown by brackets instead of the marker circle
     */
    static boolean useBrackets(boolean hasSprite, float lengthPixels, float markerPixels) {
        return hasSprite && lengthPixels >= markerPixels;
    }

    /**
     * Frames a hull rectangle rotated about its centre.
     *
     * @param centerX hull centre X on screen
     * @param centerY hull centre Y on screen
     * @param lengthPixels hull length on screen, along the heading
     * @param widthPixels hull width on screen, across the heading
     * @param headingDegrees counter-clockwise sprite rotation
     * @param padding gap between hull bounds and brackets
     * @param minArm shortest bracket arm
     * @return bracket frame; arms never exceed half a side, so opposite brackets never touch
     */
    static Frame around(float centerX, float centerY, float lengthPixels, float widthPixels,
            float headingDegrees, float padding, float minArm) {
        if (!Float.isFinite(centerX) || !Float.isFinite(centerY) || !Float.isFinite(headingDegrees)
                || !Float.isFinite(lengthPixels) || !Float.isFinite(widthPixels)
                || !Float.isFinite(padding) || !Float.isFinite(minArm)
                || !(lengthPixels > 0f) || !(widthPixels > 0f) || padding < 0f || minArm < 0f) {
            throw new IllegalArgumentException("bracket geometry must be finite and positive");
        }
        double radians = Math.toRadians(headingDegrees);
        double cos = Math.abs(Math.cos(radians));
        double sin = Math.abs(Math.sin(radians));
        float halfX = (float) (cos * lengthPixels / 2d + sin * widthPixels / 2d) + padding;
        float halfY = (float) (sin * lengthPixels / 2d + cos * widthPixels / 2d) + padding;
        float shorter = 2f * Math.min(halfX, halfY);
        float arm = Math.min(Math.max(minArm, shorter * ARM_FRACTION), shorter / 2f);
        return new Frame(centerX - halfX, centerY - halfY, centerX + halfX, centerY + halfY, arm);
    }
}
