package com.spacesim.benchmark;

/**
 * Воспроизводимое описание одного headless benchmark-прогона.
 *
 * <p>Продолжительность задаётся числом authoritative fixed ticks, а не wall-clock временем. Это
 * делает экономический результат независимым от скорости машины, JIT и CI runner.</p>
 *
 * @param name стабильное непустое имя сценария
 * @param version положительная версия сценария
 * @param rootSeed deterministic root seed simulation session
 * @param simulationTicks строго положительное число fixed ticks
 * @param sampleEveryTicks период экономических observations в ticks
 */
public record BenchmarkScenario(
        String name,
        int version,
        long rootSeed,
        long simulationTicks,
        long sampleEveryTicks) {

    /** Fixed ticks в 100 игровых часах при production step {@code 0.1s}. */
    public static final long ONE_HUNDRED_HOURS_TICKS = 3_600_000L;

    /**
     * Проверяет benchmark contract.
     *
     * @param name стабильное непустое имя сценария
     * @param version положительная версия сценария
     * @param rootSeed deterministic root seed simulation session
     * @param simulationTicks строго положительное число fixed ticks
     * @param sampleEveryTicks период экономических observations в ticks
     */
    public BenchmarkScenario {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Benchmark scenario должен иметь имя");
        }
        if (version <= 0 || simulationTicks <= 0L || sampleEveryTicks <= 0L) {
            throw new IllegalArgumentException("Version/ticks benchmark scenario должны быть положительными");
        }
        name = name.strip();
    }

    /**
     * Возвращает малый deterministic scenario для обязательного CI smoke-test demo world.
     *
     * @return demo-world benchmark на 600 fixed ticks с sampling каждые 60 ticks
     */
    public static BenchmarkScenario smoke() {
        return new BenchmarkScenario(
                "economic-smoke",
                1,
                0xB3E6_2026L,
                600L,
                60L);
    }

    /**
     * Возвращает короткий CI scenario масштабного мира 100/500.
     *
     * <p>Он проверяет bootstrap и несколько полных simulation ticks на реальном масштабе, но не
     * заменяет тяжёлый 100-hour benchmark и не задаёт performance thresholds.</p>
     *
     * @return 20-tick scenario с двумя observations
     */
    public static BenchmarkScenario scaleCiSmoke() {
        return new BenchmarkScenario(
                "economic-scale-100x500-ci",
                1,
                0xB3E6_500L,
                20L,
                10L);
    }

    /**
     * Возвращает полный milestone scenario 100 stations / 500 agents / 100 simulated hours.
     *
     * <p>Sampling каждые 6000 ticks соответствует 10 игровым минутам и даёт 600 observations за
     * 100 часов. Этот profile предназначен для отдельного reproducible benchmark run, а не для
     * обязательного выполнения на каждом pull request.</p>
     *
     * @return масштабный benchmark на 3 600 000 fixed ticks
     */
    public static BenchmarkScenario scale100Hours() {
        return new BenchmarkScenario(
                "economic-scale-100x500-100h",
                1,
                0xB3E6_500L,
                ONE_HUNDRED_HOURS_TICKS,
                6_000L);
    }
}
