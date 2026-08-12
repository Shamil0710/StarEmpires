package com.spacesim.presentation.validation;

/**
 * Immutable workload contract for a Stage-8.5 graphics validation scene.
 */
public final class GraphicsValidationProfile {
    /** Target width of the representative validation viewport. */
    public static final int REPRESENTATIVE_WIDTH = 1920;
    /** Target height of the representative validation viewport. */
    public static final int REPRESENTATIVE_HEIGHT = 1080;
    /** Minimum representative ship count. */
    public static final int REPRESENTATIVE_SHIPS = 50;
    /** Minimum representative asteroid/background-object count. */
    public static final int REPRESENTATIVE_ASTEROIDS = 500;
    /** Minimum representative active particle count. */
    public static final int REPRESENTATIVE_PARTICLES = 2000;

    private final int width;
    private final int height;
    private final int shipCount;
    private final int asteroidCount;
    private final int particleCount;

    /**
     * Creates a validation profile.
     *
     * @param width positive viewport width in pixels
     * @param height positive viewport height in pixels
     * @param shipCount non-negative ship count
     * @param asteroidCount non-negative asteroid/background-object count
     * @param particleCount non-negative active particle count
     */
    public GraphicsValidationProfile(
            int width,
            int height,
            int shipCount,
            int asteroidCount,
            int particleCount) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Validation viewport dimensions must be positive");
        }
        if (shipCount < 0 || asteroidCount < 0 || particleCount < 0) {
            throw new IllegalArgumentException("Validation object counts must be non-negative");
        }
        this.width = width;
        this.height = height;
        this.shipCount = shipCount;
        this.asteroidCount = asteroidCount;
        this.particleCount = particleCount;
    }

    /** @return the roadmap representative Stage-8.5 workload */
    public static GraphicsValidationProfile representative() {
        return new GraphicsValidationProfile(
                REPRESENTATIVE_WIDTH,
                REPRESENTATIVE_HEIGHT,
                REPRESENTATIVE_SHIPS,
                REPRESENTATIVE_ASTEROIDS,
                REPRESENTATIVE_PARTICLES);
    }

    /** @return target viewport width in pixels */
    public int width() {
        return width;
    }

    /** @return target viewport height in pixels */
    public int height() {
        return height;
    }

    /** @return number of simultaneously rendered ships */
    public int shipCount() {
        return shipCount;
    }

    /** @return number of asteroid/background objects */
    public int asteroidCount() {
        return asteroidCount;
    }

    /** @return number of active additive particles */
    public int particleCount() {
        return particleCount;
    }

    /** @return total number of world/effect objects represented by the profile */
    public int totalObjectCount() {
        return shipCount + asteroidCount + particleCount;
    }
}
