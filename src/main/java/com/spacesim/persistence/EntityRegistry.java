package com.spacesim.persistence;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.Family;
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
 * <p>{@link #track(Engine)} привязывает registry ровно к одному Ashley Engine, индексирует уже
 * существующие сущности с {@link EntityIdComponent} и подписывается на их дальнейшее добавление и
 * удаление. Повторный вызов для того же Engine идемпотентен. Один ID не может принадлежать двум
 * разным runtime-сущностям.</p>
 */
public final class EntityRegistry implements EntityListener {
    private static final Family IDENTIFIED_ENTITIES = Family.all(EntityIdComponent.class).get();

    private final Map<EntityId, Entity> entitiesById = new HashMap<>();
    private Engine trackedEngine;

    /**
     * Привязывает registry к Ashley Engine и индексирует уже существующие идентифицированные сущности.
     *
     * @param engine отслеживаемый runtime Engine
     * @throws NullPointerException если Engine не задан
     * @throws IllegalStateException если registry уже привязан к другому Engine
     */
    public void track(Engine engine) {
        Objects.requireNonNull(engine, "Ashley Engine не задан");
        if (trackedEngine == engine) {
            return;
        }
        if (trackedEngine != null) {
            throw new IllegalStateException("EntityRegistry уже отслеживает другой Engine");
        }

        for (Entity entity : engine.getEntitiesFor(IDENTIFIED_ENTITIES)) {
            register(entity);
        }
        engine.addEntityListener(IDENTIFIED_ENTITIES, this);
        trackedEngine = engine;
    }

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
     * Автоматически регистрирует сущность, добавленную в отслеживаемую Ashley family.
     *
     * @param entity добавленная сущность с ID-компонентом
     */
    @Override
    public void entityAdded(Entity entity) {
        register(entity);
    }

    /**
     * Автоматически удаляет сущность, покинувшую отслеживаемую Ashley family или Engine.
     *
     * @param entity удалённая сущность
     */
    @Override
    public void entityRemoved(Entity entity) {
        unregister(entity);
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

    /** Удаляет все runtime-сопоставления, не меняя привязку lifecycle listener к Engine. */
    public void clear() {
        entitiesById.clear();
    }
}
