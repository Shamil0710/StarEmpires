package com.spacesim.desktop;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.spacesim.SpaceSimGame;
import com.spacesim.presentation.validation.GraphicsValidationApp;
import com.spacesim.presentation.validation.GraphicsValidationProfile;
import com.spacesim.presentation.validation.HeavyCorvetteAssetValidationApp;

/**
 * Точка входа desktop-версии Star Empires на базе LWJGL3.
 *
 * <p>По умолчанию запускается обычная игра. Аргумент {@code --graphics-spike} выбирает
 * representative Stage-8.5 rendering scene, а {@code --asset-pack-validation} запускает отдельный
 * инспектор production-like heavy-corvette asset pack. Оба validation-режима не создают
 * authoritative simulation state.</p>
 */
public final class DesktopLauncher {
    private static final String GRAPHICS_SPIKE_ARGUMENT = "--graphics-spike";
    private static final String ASSET_PACK_VALIDATION_ARGUMENT = "--asset-pack-validation";

    /** Запрещает создание служебного класса. */
    private DesktopLauncher() {
    }

    /**
     * Создаёт конфигурацию окна и запускает выбранный libGDX application listener.
     *
     * @param args аргументы командной строки; validation flags выбирают Stage-8.5 review tools
     */
    public static void main(String[] args) {
        boolean assetPackValidation = containsArgument(args, ASSET_PACK_VALIDATION_ARGUMENT);
        boolean graphicsSpike = containsArgument(args, GRAPHICS_SPIKE_ARGUMENT);
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
        } else {
            configuration.setTitle("Star Empires");
            configuration.setWindowedMode(1280, 720);
            configuration.setWindowSizeLimits(800, 600, -1, -1);
            configuration.setResizable(true);
            configuration.useVsync(true);
            configuration.setForegroundFPS(60);
            listener = new SpaceSimGame();
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
