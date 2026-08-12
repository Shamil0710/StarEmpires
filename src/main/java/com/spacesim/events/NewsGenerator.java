package com.spacesim.events;

import com.badlogic.gdx.graphics.Color;

/**
 * Формирует новостные сообщения о событиях экономики.
 *
 * <p>Генератор не хранит состояния и пригоден для параллельного вызова при условии, что переданные
 * события не изменяются конкурентно. Возвращаемые {@link NewsArticle} являются изменяемыми и должны
 * синхронизироваться отдельно при передаче между потоками.</p>
 */
public final class NewsGenerator {
    private NewsGenerator() {
    }

    /**
     * Создаёт статью, описывающую начало события.
     *
     * @param event событие, о котором требуется сообщить
     * @return новая изменяемая новостная статья с заголовком, идентификатором затронутого товара и
     *         красным цветом
     * @throws NullPointerException если событие не задано
     */
    public static NewsArticle generate(EconomyEvent event) {
        return new NewsArticle(
                "Event: " + event.getName(),
                "Impact on item " + event.getTargetItemId(),
                Color.RED);
    }
}
