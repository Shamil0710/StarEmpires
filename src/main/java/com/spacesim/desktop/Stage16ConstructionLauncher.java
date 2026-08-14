package com.spacesim.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.spacesim.Stage16ConstructionGame;

/** Desktop entry point for the functional Stage-16 local construction management harness. */
public final class Stage16ConstructionLauncher {
    private Stage16ConstructionLauncher() {
        throw new AssertionError("Stage16ConstructionLauncher does not create instances");
    }

    /**
     * Launches the Stage-16 construction harness.
     *
     * @param args ignored desktop arguments
     */
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Star Empires — Stage 16 Construction");
        configuration.setWindowedMode(1440, 900);
        configuration.useVsync(true);
        new Lwjgl3Application(new Stage16ConstructionGame(), configuration);
    }
}