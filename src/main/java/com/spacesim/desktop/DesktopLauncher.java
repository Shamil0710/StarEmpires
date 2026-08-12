package com.spacesim.desktop;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.spacesim.SpaceSimGame;
import com.spacesim.presentation.validation.GraphicsValidationApp;
import com.spacesim.presentation.validation.GraphicsValidationProfile;

/**
 * Точка входа desktop-версии Star Empires на базе LWJGL3.
 *
 * <p>По умолчанию запускается обычная игра. Аргумент {@code --graphics-spike} выбирает отдельную
 * Stage-8.5 validation-сцену, которая не создаёт authoritative simulation state и может свободно
 * измерять presentation stack.</p>
 */
public final class DesktopLauncher {
    private static final String GRAPHICS_SPIKE_ARGUMENT = "--graphics-spike";

    /** Запрещает создание служебного класса. */
    private DesktopLauncher() {
    }

    /**
     * Создаёт конфигурацию окна и запускает выбранный libGDX application listener.
     *
     * @param args аргументы командной строки; {@code --graphics-spike} включает Stage-8.5 scene
     */
    public static void main(String[] args) {
        boolean graphicsSpike = containsArgument(args, GRAPHICS_SPIKE_ARGUMENT);
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle(graphicsSpike ? "Star Empires — Graphics Validation" : "Star Empires");

        ApplicationListener listener;
        if (graphicsSpike) {
            GraphicsValidationProfile profile = GraphicsValidationProfile.representative();
            configuration.setWindowedMode(profile.width(), profile.height());
            configuration.setWindowSizeLimits(800, 600, -1, -1);
            configuration.setResizable(true);
            configuration.useVsync(false);
            configuration.setForegroundFPS(0);
            listener = new GraphicsValidationApp();
        } else {
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
