package com.spacesim.events;

import com.badlogic.gdx.graphics.Color;

/**
 * Изменяемый объект данных для новости об экономическом событии.
 *
 * <p>Класс не обращается к системным часам компьютера: {@link #timestamp} выражает игровое время
 * от начала симуляции. Заголовок, содержимое и цвет остаются изменяемыми данными представления.</p>
 */
public class NewsArticle {
    /** Краткий заголовок новости; может быть равен {@code null}. */
    public String headline;

    /** Развёрнутый текст новости; может быть равен {@code null}. */
    public String content;

    /** Предпочтительный цвет отображения или {@code null} для цвета по умолчанию. */
    public Color color;

    /**
     * Игровое время создания статьи в миллисекундах от начала симуляции.
     * Значение не связано с Unix epoch или wall-clock временем компьютера.
     */
    public long timestamp;

    /**
     * Создаёт статью с нулевым игровым timestamp для совместимости с простыми UI-тестами.
     *
     * @param h заголовок; допускается {@code null}
     * @param c содержимое; допускается {@code null}
     * @param col цвет отображения; допускается {@code null}
     */
    public NewsArticle(String h, String c, Color col) {
        this(h, c, col, 0L);
    }

    /**
     * Создаёт статью с явно заданным игровым timestamp.
     *
     * @param h заголовок; допускается {@code null}
     * @param c содержимое; допускается {@code null}
     * @param col цвет отображения; допускается {@code null}
     * @param gameTimestampMillis неотрицательное игровое время в миллисекундах
     * @throws IllegalArgumentException если timestamp отрицателен
     */
    public NewsArticle(String h, String c, Color col, long gameTimestampMillis) {
        if (gameTimestampMillis < 0L) {
            throw new IllegalArgumentException("Игровой timestamp новости не может быть отрицательным");
        }
        this.headline = h;
        this.content = c;
        this.color = col;
        this.timestamp = gameTimestampMillis;
    }
}
