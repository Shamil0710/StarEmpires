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
     * Возвращает малый deterministic scenario для обязательного CI smoke-test.
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
}
