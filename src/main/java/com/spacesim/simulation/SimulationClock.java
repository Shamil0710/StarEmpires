package com.spacesim.simulation;

/**
 * Накопительный fixed-step таймер игровой симуляции.
 *
 * <p>Часы отделяют реальное время кадра от игрового времени. Входной {@code float} сначала
 * канонизируется через его десятичное представление и переводится в целые наносекунды, после чего
 * применяется {@link #getTimeScale() time scale}. Полные simulation ticks также хранятся в целых
 * наносекундах. Это исключает накопление двоичной ошибки вида {@code 0.1f ~= 0.10000000149} и делает
 * границы шагов стабильными при разном разбиении кадров.</p>
 *
 * <p>Весь mutable state часов сериализуется через {@link State}; восстановленный экземпляр
 * продолжает накопление с той же доли следующего tick, включая pause и time scale.</p>
 */
public final class SimulationClock {
    private static final double NANOS_PER_SECOND = 1_000_000_000d;

    private final float fixedStepSeconds;
    private final long fixedStepNanos;
    private long accumulatorNanos;
    private double fractionalNanos;
    private double timeScale = 1d;
    private boolean paused;
    private long tick;

    /**
     * Полный сериализуемый снимок часов.
     *
     * @param fixedStepSeconds длительность authoritative fixed tick
     * @param accumulatorNanos целые наносекунды, уже накопленные к следующему tick
     * @param fractionalNanos дробная наносекунда после применения time scale
     * @param timeScale текущий множитель игрового времени
     * @param paused состояние паузы
     * @param tick число уже исполненных ticks
     */
    public record State(
            float fixedStepSeconds,
            long accumulatorNanos,
            double fractionalNanos,
            double timeScale,
            boolean paused,
            long tick) {
        /**
         * Проверяет численные инварианты сохраняемого состояния.
         *
         * @param fixedStepSeconds длительность authoritative fixed tick
         * @param accumulatorNanos целые накопленные наносекунды
         * @param fractionalNanos дробная накопленная наносекунда
         * @param timeScale множитель игрового времени
         * @param paused состояние паузы
         * @param tick число уже исполненных ticks
         */
        public State {
            if (!Float.isFinite(fixedStepSeconds) || fixedStepSeconds <= 0f) {
                throw new IllegalArgumentException("Fixed step состояния должен быть положительным");
            }
            long stepNanos = secondsToRoundedNanos(fixedStepSeconds);
            if (stepNanos <= 0L || accumulatorNanos < 0L || accumulatorNanos >= stepNanos) {
                throw new IllegalArgumentException("Accumulator состояния находится вне fixed tick");
            }
            if (!Double.isFinite(fractionalNanos)
                    || fractionalNanos < 0d
                    || fractionalNanos >= 1d) {
                throw new IllegalArgumentException("Fractional nanos должны принадлежать [0, 1)");
            }
            if (!Double.isFinite(timeScale) || timeScale < 0d) {
                throw new IllegalArgumentException("Time scale состояния некорректен");
            }
            if (tick < 0L) {
                throw new IllegalArgumentException("Tick состояния не может быть отрицательным");
            }
        }
    }

    /**
     * Создаёт часы с заданной длительностью одного simulation tick.
     *
     * @param fixedStepSeconds конечная строго положительная длительность шага в секундах
     * @throws IllegalArgumentException если длительность некорректна или слишком мала
     */
    public SimulationClock(float fixedStepSeconds) {
        if (!Float.isFinite(fixedStepSeconds) || fixedStepSeconds <= 0f) {
            throw new IllegalArgumentException("Fixed step должен быть конечным и положительным");
        }
        long nanos = secondsToRoundedNanos(fixedStepSeconds);
        if (nanos <= 0L) {
            throw new IllegalArgumentException("Fixed step должен быть не меньше одной наносекунды");
        }
        this.fixedStepSeconds = fixedStepSeconds;
        this.fixedStepNanos = nanos;
    }

    /**
     * Восстанавливает часы из полного сохранённого состояния.
     *
     * @param state валидный снимок часов
     * @throws NullPointerException если снимок не задан
     */
    public SimulationClock(State state) {
        this(requireState(state).fixedStepSeconds());
        accumulatorNanos = state.accumulatorNanos();
        fractionalNanos = state.fractionalNanos();
        timeScale = state.timeScale();
        paused = state.paused();
        tick = state.tick();
    }

    /**
     * Возвращает полный immutable снимок для save-state.
     *
     * @return состояние, достаточное для точного продолжения clock
     */
    public State snapshotState() {
        return new State(
                fixedStepSeconds,
                accumulatorNanos,
                fractionalNanos,
                timeScale,
                paused,
                tick);
    }

    /**
     * Добавляет прошедшее реальное время кадра с учётом паузы и масштаба времени.
     *
     * @param realDeltaSeconds конечное неотрицательное реальное время кадра
     * @throws IllegalArgumentException если delta некорректен или масштабированное значение нельзя
     *                                  представить внутренним диапазоном времени
     * @throws IllegalStateException если накопитель игрового времени переполняется
     */
    public void addFrameTime(float realDeltaSeconds) {
        if (!Float.isFinite(realDeltaSeconds) || realDeltaSeconds < 0f) {
            throw new IllegalArgumentException("Frame delta должен быть конечным и неотрицательным");
        }
        if (paused || timeScale == 0d || realDeltaSeconds == 0f) {
            return;
        }

        long realDeltaNanos = secondsToRoundedNanos(realDeltaSeconds);
        double scaledNanos = realDeltaNanos * timeScale + fractionalNanos;
        if (!Double.isFinite(scaledNanos) || scaledNanos < 0d || scaledNanos > Long.MAX_VALUE) {
            throw new IllegalArgumentException("Масштабированный frame delta вышел за допустимый диапазон");
        }

        long wholeNanos = (long) Math.floor(scaledNanos);
        fractionalNanos = scaledNanos - wholeNanos;
        if (Long.MAX_VALUE - accumulatorNanos < wholeNanos) {
            throw new IllegalStateException("Накопленное игровое время вышло за допустимый диапазон");
        }
        accumulatorNanos += wholeNanos;
    }

    /** @return {@code true}, если накоплено достаточно времени для ещё одного полного tick */
    public boolean hasPendingStep() {
        return accumulatorNanos >= fixedStepNanos;
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

        accumulatorNanos -= fixedStepNanos;
        tick++;
        return fixedStepSeconds;
    }

    /**
     * Продвигает authoritative game time на несколько эквивалентных fixed ticks без создания
     * промежуточных callback boundaries.
     *
     * <p>Метод предназначен только для Stage-7 strategic reduced-rate simulation. Он не изменяет
     * accumulator/fractional frame time: local render cadence остаётся полностью сохранённой, если
     * система позднее снова будет исполняться на полном fixed rate.</p>
     *
     * @param stepCount строго положительное число эквивалентных fixed ticks
     * @return суммарное игровое время этих ticks в секундах
     * @throws IllegalArgumentException если число шагов неположительно
     * @throws IllegalStateException если диапазон tick counter будет исчерпан
     */
    public float advanceStrategicSteps(int stepCount) {
        if (stepCount <= 0) {
            throw new IllegalArgumentException("Strategic step count должен быть положительным");
        }
        if (tick > Long.MAX_VALUE - stepCount) {
            throw new IllegalStateException("Счётчик simulation ticks исчерпан");
        }
        tick += stepCount;
        double seconds = stepCount * (double) fixedStepNanos / NANOS_PER_SECOND;
        if (!Double.isFinite(seconds) || seconds > Float.MAX_VALUE) {
            throw new IllegalStateException("Strategic time span нельзя представить как float delta");
        }
        return (float) seconds;
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
        return tick * (double) fixedStepNanos / NANOS_PER_SECOND;
    }

    /**
     * Возвращает долю следующего tick, уже накопленную реальным временем.
     *
     * @return значение в диапазоне {@code [0, 1)} при штатном состоянии
     */
    public double getInterpolationAlpha() {
        double accumulated = accumulatorNanos + fractionalNanos;
        if (accumulated <= 0d) {
            return 0d;
        }
        return Math.min(0.999999999999d, accumulated / fixedStepNanos);
    }

    /** @return текущий множитель скорости игрового времени */
    public double getTimeScale() {
        return timeScale;
    }

    /**
     * Задаёт множитель игрового времени. Ноль останавливает накопление так же, как пауза.
     *
     * @param timeScale конечный неотрицательный множитель
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
     * Включает или снимает паузу.
     *
     * @param paused новое состояние паузы
     */
    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    private static State requireState(State state) {
        if (state == null) {
            throw new NullPointerException("Состояние SimulationClock не задано");
        }
        return state;
    }

    private static long secondsToRoundedNanos(float seconds) {
        double canonicalSeconds = Double.parseDouble(Float.toString(seconds));
        double nanos = canonicalSeconds * NANOS_PER_SECOND;
        if (!Double.isFinite(nanos) || nanos > Long.MAX_VALUE) {
            throw new IllegalArgumentException("Время в секундах нельзя представить в наносекундах");
        }
        return Math.round(nanos);
    }
}
