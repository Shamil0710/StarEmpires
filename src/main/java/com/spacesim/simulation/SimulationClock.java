package com.spacesim.simulation;

/**
 * Накопительный fixed-step таймер игровой симуляции.
 *
 * <p>Часы отделяют реальное время кадра от игрового времени. Реальный delta умножается на
 * {@link #getTimeScale() time scale} и накапливается; вызывающий код затем последовательно
 * извлекает шаги неизменной длины через {@link #consumeStep()}. Пауза не накапливает пропущенное
 * реальное время. Благодаря этому одинаковая последовательность simulation ticks не зависит от
 * частоты рендера.</p>
 */
public final class SimulationClock {
    private static final double MIN_EPSILON_SECONDS = 1e-12d;

    private final float fixedStepSeconds;
    private final double stepEpsilonSeconds;
    private double accumulatorSeconds;
    private double timeScale = 1d;
    private boolean paused;
    private long tick;

    /**
     * Создаёт часы с заданной длительностью одного simulation tick.
     *
     * @param fixedStepSeconds конечная строго положительная длительность шага в секундах
     * @throws IllegalArgumentException если длительность некорректна
     */
    public SimulationClock(float fixedStepSeconds) {
        if (!Float.isFinite(fixedStepSeconds) || fixedStepSeconds <= 0f) {
            throw new IllegalArgumentException("Fixed step должен быть конечным и положительным");
        }
        this.fixedStepSeconds = fixedStepSeconds;
        this.stepEpsilonSeconds = Math.max(MIN_EPSILON_SECONDS, fixedStepSeconds * 1e-9d);
    }

    /**
     * Добавляет прошедшее реальное время кадра с учётом паузы и масштаба времени.
     *
     * @param realDeltaSeconds конечное неотрицательное реальное время кадра
     * @throws IllegalArgumentException если delta некорректен либо масштабирование переполняет double
     */
    public void addFrameTime(float realDeltaSeconds) {
        if (!Float.isFinite(realDeltaSeconds) || realDeltaSeconds < 0f) {
            throw new IllegalArgumentException("Frame delta должен быть конечным и неотрицательным");
        }
        if (paused || timeScale == 0d || realDeltaSeconds == 0f) {
            return;
        }

        double scaledDelta = realDeltaSeconds * timeScale;
        if (!Double.isFinite(scaledDelta)) {
            throw new IllegalArgumentException("Масштабированный frame delta вышел за допустимый диапазон");
        }
        accumulatorSeconds += scaledDelta;
        if (!Double.isFinite(accumulatorSeconds)) {
            throw new IllegalStateException("Накопленное игровое время вышло за допустимый диапазон");
        }
    }

    /** @return {@code true}, если накоплено достаточно времени для ещё одного полного tick */
    public boolean hasPendingStep() {
        return accumulatorSeconds + stepEpsilonSeconds >= fixedStepSeconds;
    }

    /**
     * Извлекает один полный fixed-step и продвигает номер игрового tick.
     *
     * @return неизменная длительность simulation tick в секундах
     * @throws IllegalStateException если полный шаг ещё не накоплен или исчерпан диапазон счётчика
     */
    public float consumeStep() {
        if (!hasPendingStep()) {
            throw new IllegalStateException("Полный simulation tick ещё не накоплен");
        }
        if (tick == Long.MAX_VALUE) {
            throw new IllegalStateException("Счётчик simulation ticks исчерпан");
        }

        accumulatorSeconds -= fixedStepSeconds;
        if (accumulatorSeconds < 0d && accumulatorSeconds > -stepEpsilonSeconds) {
            accumulatorSeconds = 0d;
        }
        tick++;
        return fixedStepSeconds;
    }

    /** @return длительность одного simulation tick в секундах */
    public float getFixedStepSeconds() {
        return fixedStepSeconds;
    }

    /** @return число полностью исполненных simulation ticks */
    public long getTick() {
        return tick;
    }

    /** @return игровое время полностью исполненных ticks в секундах */
    public double getSimulationTimeSeconds() {
        return tick * (double) fixedStepSeconds;
    }

    /**
     * Возвращает долю следующего tick, уже накопленную реальным временем.
     * Значение удобно для будущей интерполяции визуального положения объектов.
     *
     * @return значение в диапазоне {@code [0, 1)} при штатном состоянии
     */
    public double getInterpolationAlpha() {
        if (accumulatorSeconds <= 0d) {
            return 0d;
        }
        return Math.min(0.999999999d, accumulatorSeconds / fixedStepSeconds);
    }

    /** @return текущий множитель скорости игрового времени */
    public double getTimeScale() {
        return timeScale;
    }

    /**
     * Задаёт множитель игрового времени. Ноль останавливает накопление так же, как пауза.
     *
     * @param timeScale конечный неотрицательный множитель
     * @throws IllegalArgumentException если значение отрицательно, бесконечно или NaN
     */
    public void setTimeScale(double timeScale) {
        if (!Double.isFinite(timeScale) || timeScale < 0d) {
            throw new IllegalArgumentException("Time scale должен быть конечным и неотрицательным");
        }
        this.timeScale = timeScale;
    }

    /** @return {@code true}, если игровое время временно остановлено */
    public boolean isPaused() {
        return paused;
    }

    /**
     * Включает или снимает паузу. Время, прошедшее во время паузы, не добавляется в accumulator.
     *
     * @param paused новое состояние паузы
     */
    public void setPaused(boolean paused) {
        this.paused = paused;
    }
}
