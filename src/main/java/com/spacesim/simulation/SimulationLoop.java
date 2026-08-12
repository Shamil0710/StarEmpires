package com.spacesim.simulation;

import com.badlogic.ashley.core.Engine;
import com.spacesim.events.GlobalEventManager;

import java.util.Objects;

/**
 * Исполняет фиксированные simulation ticks поверх Ashley-движка.
 *
 * <p>Pipeline одного tick намеренно явный и стабилен: сначала продвигаются глобальные события,
 * затем все Ashley-системы в зарегистрированном порядке. Render/UI в этот класс не входят.</p>
 */
public final class SimulationLoop {
    /** Защитная граница работы одного render frame; непройденные ticks остаются в accumulator. */
    public static final int DEFAULT_MAX_STEPS_PER_FRAME = 2_048;

    private final SimulationClock clock;
    private final GlobalEventManager eventManager;
    private final Engine engine;
    private final int maxStepsPerFrame;

    /**
     * Создаёт loop со стандартной защитной границей числа шагов за кадр.
     *
     * @param clock игровые часы
     * @param eventManager менеджер глобальных событий
     * @param engine Ashley-движок экономической симуляции
     */
    public SimulationLoop(SimulationClock clock, GlobalEventManager eventManager, Engine engine) {
        this(clock, eventManager, engine, DEFAULT_MAX_STEPS_PER_FRAME);
    }

    /**
     * Создаёт loop с явной границей работы одного render frame.
     *
     * @param clock игровые часы
     * @param eventManager менеджер глобальных событий
     * @param engine Ashley-движок экономической симуляции
     * @param maxStepsPerFrame строго положительное максимальное число ticks за один вызов
     * @throws NullPointerException если обязательная зависимость не задана
     * @throws IllegalArgumentException если граница неположительна
     */
    public SimulationLoop(
            SimulationClock clock,
            GlobalEventManager eventManager,
            Engine engine,
            int maxStepsPerFrame) {
        this.clock = Objects.requireNonNull(clock, "SimulationClock не задан");
        this.eventManager = Objects.requireNonNull(eventManager, "GlobalEventManager не задан");
        this.engine = Objects.requireNonNull(engine, "Ashley Engine не задан");
        if (maxStepsPerFrame <= 0) {
            throw new IllegalArgumentException("maxStepsPerFrame должен быть положительным");
        }
        this.maxStepsPerFrame = maxStepsPerFrame;
    }

    /**
     * Добавляет время render frame и исполняет доступные fixed ticks.
     *
     * <p>Если накоплено больше шагов, чем разрешено защитной границей, остаток не теряется и будет
     * исполнен последующими вызовами. Это предотвращает пропуск игрового времени.</p>
     *
     * @param realDeltaSeconds прошедшее реальное время кадра
     * @return число simulation ticks, реально исполненных в этом вызове
     */
    public int advanceFrame(float realDeltaSeconds) {
        clock.addFrameTime(realDeltaSeconds);
        int executedSteps = 0;
        while (executedSteps < maxStepsPerFrame && clock.hasPendingStep()) {
            float fixedStep = clock.consumeStep();
            eventManager.update(fixedStep);
            engine.update(fixedStep);
            executedSteps++;
        }
        return executedSteps;
    }

    /** @return игровые часы, используемые loop */
    public SimulationClock getClock() {
        return clock;
    }

    /** @return максимальное число simulation ticks за один render frame */
    public int getMaxStepsPerFrame() {
        return maxStepsPerFrame;
    }
}
