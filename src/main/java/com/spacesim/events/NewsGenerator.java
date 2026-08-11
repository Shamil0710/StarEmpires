package com.spacesim.events;

import com.badlogic.gdx.graphics.Color;

/**
 * Формирует новостные сообщения о событиях экономики.
 */
public final class NewsGenerator {
    private NewsGenerator() {
    }

    /**
     * Создаёт статью, описывающую начало события.
     *
     * @param event событие, о котором требуется сообщить
     * @return новая новостная статья
     */
    public static NewsArticle generate(EconomyEvent event) {
        return new NewsArticle(
                "Event: " + event.getName(),
                "Impact on item " + event.getTargetItemId(),
                Color.RED);
    }
}
