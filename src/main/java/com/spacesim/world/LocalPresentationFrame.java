package com.spacesim.world;

import java.util.Objects;

/**
 * Camera/materialization-relative projection over immutable authoritative physical coordinates.
 *
 * <p>libGDX render paths may continue to consume floats after the nearby physical displacement has
 * been calculated in the hierarchical double-precision coordinate domain. Replacing this frame's
 * origin is presentation rebasing only: it never mutates an entity's {@link LocalPhysicalPosition}.</p>
 *
 * @param origin physical coordinate used as the temporary presentation origin
 */
public record LocalPresentationFrame(LocalPhysicalPosition origin) {
    /**
     * Creates a presentation frame around an immutable physical origin.
     *
     * @param origin physical coordinate used as the presentation origin
     */
    public LocalPresentationFrame {
        Objects.requireNonNull(origin, "origin");
    }

    /**
     * Projects a nearby physical position into camera-relative float meters.
     *
     * <p>The float cast happens only after subtracting the hierarchical physical origin. A caller
     * that tries to project an object outside finite float range receives an explicit failure rather
     * than an authoritative coordinate mutation or clamp.</p>
     *
     * @param position authoritative physical position to project
     * @return camera-relative presentation point in meters
     */
    public PresentationPoint project(LocalPhysicalPosition position) {
        LocalPhysicalPosition.Displacement displacement = origin.displacementTo(
                Objects.requireNonNull(position, "position"));
        float x = (float) displacement.deltaXM();
        float y = (float) displacement.deltaYM();
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new ArithmeticException("Position is outside finite camera-relative float range");
        }
        return new PresentationPoint(x, y);
    }

    /**
     * Creates another presentation frame without changing any authoritative physical coordinate.
     *
     * @param newOrigin new camera/materialization origin
     * @return independently rebased presentation frame
     */
    public LocalPresentationFrame rebased(LocalPhysicalPosition newOrigin) {
        return new LocalPresentationFrame(Objects.requireNonNull(newOrigin, "newOrigin"));
    }

    /**
     * Float-valued nearby presentation coordinate. This type is deliberately not physical authority.
     *
     * @param xM camera-relative X in meters
     * @param yM camera-relative Y in meters
     */
    public record PresentationPoint(float xM, float yM) {
    }
}
