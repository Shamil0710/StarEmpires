package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.spacesim.persistence.EntityId;

import java.util.Objects;

/**
 * Привязывает Ashley-сущность к её устойчивому persistent-идентификатору.
 *
 * <p>Компонент неизменяем: ID выдаётся при создании объекта и не должен меняться в течение его
 * жизни. Пользовательское имя и тип объекта по-прежнему принадлежат {@link IdentityComponent} и не
 * используются как технический ключ.</p>
 */
public final class EntityIdComponent implements Component {
    /** Устойчивый идентификатор сущности. */
    public final EntityId id;

    /**
     * Создаёт компонент с обязательным ID.
     *
     * @param id устойчивый идентификатор
     * @throws NullPointerException если ID не задан
     */
    public EntityIdComponent(EntityId id) {
        this.id = Objects.requireNonNull(id, "EntityId не задан");
    }
}
