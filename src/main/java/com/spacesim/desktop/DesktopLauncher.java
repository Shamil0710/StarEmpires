package com.spacesim.desktop;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.spacesim.PlayableTestGame;
import com.spacesim.SpaceSimGame;
import com.spacesim.presentation.validation.GraphicsValidationApp;
import com.spacesim.presentation.validation.GraphicsValidationProfile;
import com.spacesim.presentation.validation.HeavyCorvetteAssetValidationApp;

/**
 * Desktop entry point for Star Empires on LWJGL3.
 *
 * <p>The default executable now opens the curated playable Stage-12 test world. The legacy
 * spectator/economy view remains available through {@code --spectator}; Stage-8.5 graphics and
 * asset validation modes remain unchanged.</p>
 */
public final class DesktopLauncher {
    private static final String GRAPHICS_SPIKE_ARGUMENT = "--graphics-spike";
    private static final String ASSET_PACK_VALIDATION_ARGUMENT = "--asset-pack-validation";
    private static final String SPECTATOR_ARGUMENT = "--spectator";

    /** Prevents construction of the utility entry-point class. */
    private DesktopLauncher() {
    }

    /**
     * Creates the window configuration and launches the selected libGDX application listener.
     *
     * @param args command-line arguments selecting optional validation/spectator modes
     */
    public static void main(String[] args) {
        boolean assetPackValidation = containsArgument(args, ASSET_PACK_VALIDATION_ARGUMENT);
        boolean graphicsSpike = containsArgument(args, GRAPHICS_SPIKE_ARGUMENT);
        boolean spectator = containsArgument(args, SPECTATOR_ARGUMENT);
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();

        ApplicationListener listener;
        if (assetPackValidation) {
            configuration.setTitle("Star Empires — Heavy Corvette Asset Validation");
            configuration.setWindowedMode(1600, 900);
            configuration.setWindowSizeLimits(1000, 650, -1, -1);
            configuration.setResizable(true);
            configuration.useVsync(true);
            configuration.setForegroundFPS(60);
            listener = new HeavyCorvetteAssetValidationApp();
        } else if (graphicsSpike) {
            GraphicsValidationProfile profile = GraphicsValidationProfile.representative();
            configuration.setTitle("Star Empires — Graphics Validation");
            configuration.setWindowedMode(profile.width(), profile.height());
            configuration.setWindowSizeLimits(800, 600, -1, -1);
            configuration.setResizable(true);
            configuration.useVsync(false);
            configuration.setForegroundFPS(0);
            listener = new GraphicsValidationApp();
        } else if (spectator) {
            configuration.setTitle("Star Empires — Economy Spectator");
            configuration.setWindowedMode(1280, 720);
            configuration.setWindowSizeLimits(800, 600, -1, -1);
            configuration.setResizable(true);
            configuration.useVsync(true);
            configuration.setForegroundFPS(60);
            listener = new SpaceSimGame();
        } else {
            configuration.setTitle("Star Empires — Playable Test World");
            configuration.setWindowedMode(1440, 900);
            configuration.setWindowSizeLimits(1000, 650, -1, -1);
            configuration.setResizable(true);
            configuration.useVsync(true);
            configuration.setForegroundFPS(60);
            listener = new PlayableTestGame();
        }

        new Lwjgl3Application(listener, configuration);
    }

    private static boolean containsArgument(String[] args, String expected) {
        if (args == null) {
            return false;
        }
        for (String argument : args) {
            if (expected.equals(argument)) {
                return true;
            }
        }
        return false;
    }
}
