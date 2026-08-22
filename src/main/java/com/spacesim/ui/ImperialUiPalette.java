package com.spacesim.ui;

import com.badlogic.gdx.graphics.Color;

/** Restrained command-interface palette derived from the Imperial visual code v0.1. */
public final class ImperialUiPalette {
    /** Main near-black background. */
    public static final Color GRAPHITE = Color.valueOf("171A1D");
    /** Primary dark panel color. */
    public static final Color MIDNIGHT = Color.valueOf("1F2930");
    /** Elevated panel and structural divider color. */
    public static final Color GUNMETAL = Color.valueOf("41494E");
    /** Primary warm readable text. */
    public static final Color IVORY = Color.valueOf("D1CCC0");
    /** Faction/navigation selection accent. */
    public static final Color BURGUNDY = Color.valueOf("6D2933");
    /** Command and hierarchy accent. */
    public static final Color BRASS = Color.valueOf("9B793E");
    /** Warning accent. */
    public static final Color AMBER = Color.valueOf("C4872D");
    /** Normal electronics/selection accent. */
    public static final Color CYAN = Color.valueOf("4D95A5");
    /** Critical failure accent. */
    public static final Color RED = Color.valueOf("A23C35");
    /** Secondary text derived from warm ivory. */
    public static final Color MUTED_TEXT = new Color(0.63f, 0.65f, 0.65f, 1f);
    /** Translucent map surface. */
    public static final Color MAP_SURFACE = new Color(0.055f, 0.075f, 0.085f, 0.98f);
    /** Elevated inspector/list surface. */
    public static final Color PANEL_SURFACE = new Color(0.085f, 0.105f, 0.115f, 0.985f);
    /** Low-contrast chart grid. */
    public static final Color GRID = new Color(0.27f, 0.31f, 0.32f, 0.32f);

    private ImperialUiPalette() {
        throw new AssertionError("No instances");
    }
}
