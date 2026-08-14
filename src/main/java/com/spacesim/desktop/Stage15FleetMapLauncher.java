package com.spacesim.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.spacesim.Stage15FleetMapGame;

/** Desktop entry point for the first functional Stage-15 global fleet command map. */
public final class Stage15FleetMapLauncher {
    private Stage15FleetMapLauncher() {
        throw new AssertionError("Stage15FleetMapLauncher does not create instances");
    }

    /**
     * Launches the strategic map harness.
     *
     * @param args ignored desktop arguments
     */
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Star Empires — Stage 15 Fleet Map");
        configuration.setWindowedMode(1440, 900);
        configuration.useVsync(true);
        new Lwjgl3Application(new Stage15FleetMapGame(), configuration);
    }
}
