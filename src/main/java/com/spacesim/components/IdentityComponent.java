package com.spacesim.components;

import com.badlogic.ashley.core.Component;

/**
 * Неизменяемая пользовательская идентичность отображаемого космического объекта.
 *
 * <p>Класс отделяет человекочитаемое имя и presentation-kind от технического состава ECS
 * компонентов. Kind не определяет authoritative gameplay rules: системы по-прежнему работают по
 * компонентам, а визуальный/UI слой получает устойчивую категорию объекта.</p>
 */
public final class IdentityComponent implements Component {
    /** Категория космического объекта, используемая presentation/UI. */
    public enum Kind {
        /** Неподвижная экономическая станция или другой стационарный объект. */
        STATION,
        /** Подвижный флот либо отдельный корабль. */
        FLEET,
        /** Ограниченный природный источник ресурса. */
        ASTEROID,
        /** Persistent физический контейнер salvage после уничтожения объекта. */
        SALVAGE
    }

    /** Отображаемое имя объекта без пробельных символов по краям. */
    public final String name;
    /** Категория объекта. */
    public final Kind kind;

    /**
     * Создаёт идентичность космического объекта.
     *
     * @param name непустое отображаемое имя
     * @param kind обязательная категория
     */
    public IdentityComponent(String name, Kind kind) {
        if (name == null || name.strip().isEmpty()) {
            throw new IllegalArgumentException("Имя космического объекта не должно быть пустым");
        }
        if (kind == null) {
            throw new NullPointerException("Тип космического объекта не должен быть null");
        }
        this.name = name.strip();
        this.kind = kind;
    }
}
