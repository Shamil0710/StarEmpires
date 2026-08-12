package com.spacesim.components;

import com.badlogic.ashley.core.Component;

import java.util.Objects;

/**
 * Persistent ссылка ECS-сущности на data-driven archetype, из которого она создана.
 *
 * <p>В отличие от runtime {@link com.spacesim.persistence.EntityId}, который идентифицирует один
 * конкретный объект мира, {@link #contentId} идентифицирует его тип в {@code ContentCatalog}.
 * Значение сохраняется в GameState и потому должно быть стабильным между версиями контента.</p>
 */
public final class ArchetypeComponent implements Component {
    /** Стабильный persistent content ID ship/station archetype. */
    public final String contentId;

    /**
     * Создаёт archetype reference.
     *
     * @param contentId непустой stable content ID
     * @throws NullPointerException если ID не задан
     * @throws IllegalArgumentException если ID пустой
     */
    public ArchetypeComponent(String contentId) {
        this.contentId = Objects.requireNonNull(contentId, "Archetype content ID не задан");
        if (contentId.isBlank()) {
            throw new IllegalArgumentException("Archetype content ID не должен быть пустым");
        }
    }
}
