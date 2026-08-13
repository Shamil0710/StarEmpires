package com.spacesim.ui;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.TransformComponent;

/**
 * Выполняет hit-test отображаемых ECS-сущностей на интерактивной карте.
 *
 * <p>Расстояние вычисляется после проекции позиции объекта в экранные координаты, поэтому
 * радиус выбора остаётся одинаковым при любом размере и увеличении карты. Сущности без
 * идентичности, пространственного компонента, конечной позиции, находящиеся за пределами мира
 * или текущего обзора не участвуют в выборе. При равном расстоянии приоритет типов равен
 * {@code флот > астероид/salvage > станция}: так небольшой подвижный корабль, источник ресурса
 * или salvage проще выбрать поверх крупного стационарного объекта.</p>
 */
public final class EntityPicker {
    private static final ComponentMapper<IdentityComponent> IDENTITIES =
            ComponentMapper.getFor(IdentityComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORMS =
            ComponentMapper.getFor(TransformComponent.class);

    private EntityPicker() {
        throw new AssertionError("Служебный класс не предназначен для создания экземпляров");
    }

    /**
     * Находит ближайшую к указателю кликабельную сущность.
     *
     * @param entities сущности мира; {@code null} и отдельные {@code null}-элементы пропускаются
     * @param layout преобразование координат карты
     * @param screenX координата указателя по горизонтали в системе координат карты/Scene2D
     * @param screenY координата указателя по вертикали в системе координат карты/Scene2D
     * @param hitRadius положительный максимальный радиус выбора в экранных единицах
     * @return ближайшая сущность в пределах радиуса либо {@code null}, если выбрать нечего или
     *         входные параметры некорректны
     */
    public static Entity pick(
            Iterable<Entity> entities,
            WorldMapLayout layout,
            float screenX,
            float screenY,
            float hitRadius) {
        if (entities == null
                || layout == null
                || !layout.containsMapPoint(screenX, screenY)
                || !Float.isFinite(hitRadius)
                || hitRadius <= 0f) {
            return null;
        }

        double maximumDistanceSquared = (double) hitRadius * hitRadius;
        double bestDistanceSquared = maximumDistanceSquared;
        Entity best = null;
        IdentityComponent.Kind bestKind = null;
        Vector2 projected = new Vector2();

        for (Entity entity : entities) {
            if (entity == null) {
                continue;
            }
            IdentityComponent identity = IDENTITIES.get(entity);
            TransformComponent transform = TRANSFORMS.get(entity);
            if (!isClickable(identity, transform, layout)
                    || !layout.worldToScreen(transform.position.x, transform.position.y, projected)) {
                continue;
            }

            double offsetX = (double) projected.x - screenX;
            double offsetY = (double) projected.y - screenY;
            double distanceSquared = offsetX * offsetX + offsetY * offsetY;
            if (!Double.isFinite(distanceSquared) || distanceSquared > maximumDistanceSquared) {
                continue;
            }

            boolean closer = best == null || distanceSquared < bestDistanceSquared;
            boolean higherKindWinsTie = best != null
                    && Double.compare(distanceSquared, bestDistanceSquared) == 0
                    && kindPriority(identity.kind) > kindPriority(bestKind);
            if (closer || higherKindWinsTie) {
                best = entity;
                bestKind = identity.kind;
                bestDistanceSquared = distanceSquared;
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
                && layout.containsVisibleWorldPoint(
                        transform.position.x,
                        transform.position.y);
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
