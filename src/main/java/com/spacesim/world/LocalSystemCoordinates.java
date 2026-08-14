package com.spacesim.world;

import com.spacesim.constants.Constants;

/** Shared local-system coordinate conventions used by travel and strategic commands. */
public final class LocalSystemCoordinates {
    /** Canonical safe materialization X inside the current bounded local-system viewport. */
    public static final float ARRIVAL_X = Constants.WORLD_WIDTH / 2f;
    /** Canonical safe materialization Y inside the current bounded local-system viewport. */
    public static final float ARRIVAL_Y = Constants.WORLD_HEIGHT / 2f;

    private LocalSystemCoordinates() {
        throw new AssertionError("LocalSystemCoordinates does not create instances");
    }

    /**
     * Resolves the Stage-10 legacy {@code (0,0)} placeholder to the canonical interior arrival X.
     * Explicit non-zero arrival coordinates remain untouched.
     *
     * @param requestedX requested local X
     * @param requestedY requested local Y
     * @return canonicalized arrival X
     */
    public static float resolveArrivalX(float requestedX, float requestedY) {
        return isLegacyDefault(requestedX, requestedY) ? ARRIVAL_X : requestedX;
    }

    /**
     * Resolves the Stage-10 legacy {@code (0,0)} placeholder to the canonical interior arrival Y.
     * Explicit non-zero arrival coordinates remain untouched.
     *
     * @param requestedX requested local X
     * @param requestedY requested local Y
     * @return canonicalized arrival Y
     */
    public static float resolveArrivalY(float requestedX, float requestedY) {
        return isLegacyDefault(requestedX, requestedY) ? ARRIVAL_Y : requestedY;
    }

    private static boolean isLegacyDefault(float x, float y) {
        return Float.floatToIntBits(x) == Float.floatToIntBits(0f)
                && Float.floatToIntBits(y) == Float.floatToIntBits(0f);
    }
}
