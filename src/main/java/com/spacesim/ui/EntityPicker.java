package com.spacesim.ui;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.TransformComponent;

import java.util.Objects;

/** Pure helper for deterministic screen-space entity picking. */
public final class EntityPicker {
    private EntityPicker() {
        throw new AssertionError("EntityPicker не создаёт экземпляров");
    }

    /**
     * Выбирает ближайший отображаемый entity в заданном screen-space радиусе.
     *
     * <p>При одинаковом расстоянии порядок стабилен: station, asteroid/salvage, fleet. Это делает
     * выбор воспроизводимым независимо от iteration order Ashley.</p>
     *
     * @param entities кандидаты
     * @param layout текущая world-map projection
     * @param screenX screen X курсора
     * @param screenY screen Y курсора
     * @param radiusPixels положительный радиус выбора
     * @return ближайший entity либо {@code null}
     */
    public static Entity pick(
            Iterable<Entity> entities,
            WorldMapLayout layout,
            float screenX,
            float screenY,
            float radiusPixels) {
        Objects.requireNonNull(entities, "Entities для picking не заданы");
        WorldMapLayout checkedLayout = Objects.requireNonNull(layout, "WorldMapLayout для picking не задан");
        if (!Float.isFinite(screenX) || !Float.isFinite(screenY)
                || !Float.isFinite(radiusPixels) || radiusPixels <= 0f) {
            throw new IllegalArgumentException("Параметры picking должны быть конечными, radius > 0");
        }

        float radiusSquared = radiusPixels * radiusPixels;
        Entity best = null;
        float bestDistanceSquared = Float.POSITIVE_INFINITY;
        int bestPriority = Integer.MAX_VALUE;
        Vector2 screen = new Vector2();
        for (Entity entity : entities) {
            if (entity == null) {
                continue;
            }
            IdentityComponent identity = entity.getComponent(IdentityComponent.class);
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            if (!isClickable(identity, transform, checkedLayout)) {
                continue;
            }
            checkedLayout.worldToScreen(transform.position.x, transform.position.y, screen);
            float dx = screen.x - screenX;
            float dy = screen.y - screenY;
            float distanceSquared = dx * dx + dy * dy;
            if (distanceSquared > radiusSquared) {
                continue;
            }
            int priority = kindPriority(identity.kind);
            if (distanceSquared < bestDistanceSquared
                    || (Float.compare(distanceSquared, bestDistanceSquared) == 0 && priority < bestPriority)) {
                best = entity;
                bestDistanceSquared = distanceSquared;
                bestPriority = priority;
            }
        }
        return best;
    }

    /** Проверяет обязательные компоненты, тип и диапазон позиции кандидата. */
    private static boolean isClickable(
            IdentityComponent identity,
            TransformComponent transform,
            WorldMapLayout layout) {
        return identity != null
                && identity.kind != null
                && transform != null
                && transform.position != null
                && layout.containsVisibleWorldPoint(transform.position.x, transform.position.y);
    }

    /** Возвращает приоритет объекта при точном равенстве экранного расстояния. */
    private static int kindPriority(IdentityComponent.Kind kind) {
        if (kind == null) {
            return -1;
        }
        return switch (kind) {
            case STATION -> 0;
            case ASTEROID, SALVAGE -> 1;
            case FLEET -> 2;
        };
    }
}
