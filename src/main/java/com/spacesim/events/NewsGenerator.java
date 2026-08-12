package com.spacesim.events;

import com.badlogic.gdx.graphics.Color;

import java.util.Objects;

/**
 * Формирует новостные сообщения о событиях экономики.
 */
public final class NewsGenerator {
    private NewsGenerator() {
    }

    /**
     * Создаёт статью с нулевым игровым timestamp.
     *
     * @param event событие, о котором требуется сообщить
     * @return новая новостная статья
     * @throws NullPointerException если событие не задано
     */
    public static NewsArticle generate(EconomyEvent event) {
        return generate(event, 0d);
    }

    /**
     * Создаёт статью, описывающую начало события в заданный момент игрового времени.
     *
     * @param event событие, о котором требуется сообщить
     * @param simulationTimeSeconds неотрицательное конечное игровое время в секундах
     * @return новая новостная статья с game timestamp в миллисекундах
     * @throws NullPointerException если событие не задано
     * @throws IllegalArgumentException если игровое время отрицательно или неконечно
     */
    public static NewsArticle generate(EconomyEvent event, double simulationTimeSeconds) {
        EconomyEvent checkedEvent = Objects.requireNonNull(event, "Событие не задано");
        if (!Double.isFinite(simulationTimeSeconds) || simulationTimeSeconds < 0d) {
            throw new IllegalArgumentException("Игровое время новости должно быть конечным и неотрицательным");
        }

        return new NewsArticle(
                "Event: " + checkedEvent.getName(),
                "Impact on item " + checkedEvent.getTargetItemId(),
                Color.RED,
                toGameTimeMillis(simulationTimeSeconds));
    }

    private static long toGameTimeMillis(double simulationTimeSeconds) {
        double milliseconds = simulationTimeSeconds * 1_000d;
        if (milliseconds >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.round(milliseconds);
    }
}
