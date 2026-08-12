package com.spacesim.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.spacesim.SpaceSimGame;

/**
 * Точка входа desktop-версии Star Empires на базе LWJGL3.
 *
 * <p>Запускатель задаёт начальный и минимальный размеры окна, вертикальную
 * синхронизацию и ограничение частоты активного приложения, после чего передаёт
 * управление экземпляру {@link SpaceSimGame}.</p>
 */
public final class DesktopLauncher {
    /** Запрещает создание служебного класса. */
    private DesktopLauncher() {
    }

    /**
     * Создаёт конфигурацию окна и запускает цикл libGDX.
     *
     * @param args аргументы командной строки; в текущей версии не используются
     */
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Star Empires");
        configuration.setWindowedMode(1280, 720);
        configuration.setWindowSizeLimits(800, 600, -1, -1);
        configuration.setResizable(true);
        configuration.useVsync(true);
        configuration.setForegroundFPS(60);

        new Lwjgl3Application(new SpaceSimGame(), configuration);
    }
}
