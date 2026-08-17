package com.spacesim.desktop;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.PlayableTestGame;
import com.spacesim.SpaceSimGame;
import com.spacesim.presentation.validation.GraphicsValidationApp;
import com.spacesim.presentation.validation.GraphicsValidationProfile;
import com.spacesim.presentation.validation.HeavyCorvetteAssetValidationApp;
import com.spacesim.presentation.validation.Stage175ITacticalAcceptanceApp;

/**
 * Desktop entry point for Star Empires on LWJGL3.
 *
 * <p>The normal playable build and economy spectator explicitly enable the 100-system large demo.
 * Automated tests and graphics-validation modes do not set the large-demo property, so compact
 * deterministic fixtures remain the default outside manual desktop play.</p>
 */
public final class DesktopLauncher {
    private static final String GRAPHICS_SPIKE_ARGUMENT = "--graphics-spike";
    private static final String ASSET_PACK_VALIDATION_ARGUMENT = "--asset-pack-validation";
    private static final String TACTICAL_ACCEPTANCE_ARGUMENT = "--tactical-acceptance";
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
        boolean tacticalAcceptance = containsArgument(args, TACTICAL_ACCEPTANCE_ARGUMENT);
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
        } else if (tacticalAcceptance) {
            configuration.setTitle("Star Empires — Stage 17.5I Tactical Acceptance");
            configuration.setWindowedMode(1440, 900);
            configuration.setWindowSizeLimits(1000, 650, -1, -1);
            configuration.setResizable(true);
            configuration.useVsync(true);
            configuration.setForegroundFPS(60);
            listener = new Stage175ITacticalAcceptanceApp();
        } else if (spectator) {
            enableLargeDemo();
            configuration.setTitle("Star Empires — 100 System Economy Spectator");
            configuration.setWindowedMode(1280, 720);
            configuration.setWindowSizeLimits(800, 600, -1, -1);
            configuration.setResizable(true);
            configuration.useVsync(true);
            configuration.setForegroundFPS(60);
            listener = new SpaceSimGame();
        } else {
            enableLargeDemo();
            configuration.setTitle("Star Empires — 100 System Live Demo");
            configuration.setWindowedMode(1440, 900);
            configuration.setWindowSizeLimits(1000, 650, -1, -1);
            configuration.setResizable(true);
            configuration.useVsync(true);
            configuration.setForegroundFPS(60);
            listener = new PlayableTestGame();
        }

        new Lwjgl3Application(listener, configuration);
    }

    private static void enableLargeDemo() {
        System.setProperty(DemoGalaxyFactory.LARGE_DEMO_PROPERTY, Boolean.TRUE.toString());
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
