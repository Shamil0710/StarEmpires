package com.spacesim.persistence;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime-индекс устойчивых {@link EntityId} в текущие Ashley {@link Entity}.
 *
 * <p>Registry отделяет persistent-ссылку от runtime-объекта ECS. После загрузки сущности могут быть
 * созданы заново как другие экземпляры {@code Entity}; достаточно зарегистрировать их с теми же
 * ID, после чего сохранённые связи разрешаются через этот индекс.</p>
 *
 * <p>Один ID не может принадлежать двум разным runtime-сущностям. Повторная регистрация той же
 * сущности с тем же ID идемпотентна.</p>
 */
public final class EntityRegistry {
    private final Map<EntityId, Entity> entitiesById = new HashMap<>();

    /**
     * Регистрирует сущность по её обязательному {@link EntityIdComponent}.
     *
     * @param entity регистрируемая runtime-сущность
     * @return ID зарегистрированной сущности
     * @throws NullPointerException если сущность не задана
     * @throws IllegalArgumentException если у сущности отсутствует ID-компонент
     * @throws IllegalStateException если ID уже принадлежит другой сущности
     */
    public EntityId register(Entity entity) {
        Objects.requireNonNull(entity, "Entity не задана");
        EntityIdComponent component = entity.getComponent(EntityIdComponent.class);
        if (component == null) {
            throw new IllegalArgumentException("У Entity отсутствует EntityIdComponent");
        }

        Entity existing = entitiesById.putIfAbsent(component.id, entity);
        if (existing != null && existing != entity) {
            throw new IllegalStateException("Дублирующий EntityId: " + component.id);
        }
        return component.id;
    }

    /**
     * Удаляет регистрацию только если ID всё ещё указывает именно на переданную сущность.
     *
     * @param entity удаляемая runtime-сущность; {@code null} безопасно игнорируется
     * @return {@code true}, если запись была удалена
     */
    public boolean unregister(Entity entity) {
        if (entity == null) {
            return false;
        }
        EntityIdComponent component = entity.getComponent(EntityIdComponent.class);
        return component != null && entitiesById.remove(component.id, entity);
    }

    /**
     * Разрешает persistent ID в текущую runtime-сущность.
     *
     * @param id идентификатор либо {@code null}
     * @return зарегистрированная сущность или {@code null}
     */
    public Entity find(EntityId id) {
        return id == null ? null : entitiesById.get(id);
    }

    /**
     * Разрешает обязательную ссылку и явно сообщает о повреждённом состоянии.
     *
     * @param id обязательный идентификатор
     * @return зарегистрированная сущность
     * @throws NullPointerException если ID не задан
     * @throws IllegalStateException если ID не зарегистрирован
     */
    public Entity require(EntityId id) {
        Objects.requireNonNull(id, "EntityId не задан");
        Entity entity = entitiesById.get(id);
        if (entity == null) {
            throw new IllegalStateException("EntityId не зарегистрирован: " + id);
        }
        return entity;
    }

    /**
     * Проверяет наличие идентификатора в registry.
     *
     * @param id идентификатор либо {@code null}
     * @return {@code true}, если ID зарегистрирован
     */
    public boolean contains(EntityId id) {
        return id != null && entitiesById.containsKey(id);
    }

    /**
     * Возвращает число зарегистрированных runtime-сущностей.
     *
     * @return неотрицательный размер registry
     */
    public int size() {
        return entitiesById.size();
    }

    /** Удаляет все runtime-сопоставления, не изменяя сами сущности. */
    public void clear() {
        entitiesById.clear();
    }
}
