package com.spacesim.model;

/**
 * Разрешённая координата появления астероида в ресурсном поясе.
 *
 * @param id устойчивый непустой идентификатор точки
 * @param x мировая координата X
 * @param y мировая координата Y
 */
public record AsteroidSpawnPoint(String id, float x, float y) {
    public AsteroidSpawnPoint {
        if (id == null || id.strip().isEmpty()) {
            throw new IllegalArgumentException("Идентификатор точки спавна не должен быть пустым");
        }
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException("Координаты точки спавна должны быть конечными");
        }
        id = id.strip();
    }
}
