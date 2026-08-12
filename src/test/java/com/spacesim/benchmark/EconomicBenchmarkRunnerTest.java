package com.spacesim.benchmark;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomicBenchmarkRunnerTest {
    @Test
    void одинаковыйScenarioДаётОдинаковыеDeterministicMetrics() {
        BenchmarkScenario scenario = new BenchmarkScenario(
                "ci-smoke",
                1,
                0x51A6E6L,
                120L,
                30L);
        EconomicBenchmarkRunner runner = new EconomicBenchmarkRunner();

        BenchmarkReport first = runner.run(scenario);
        BenchmarkReport second = runner.run(scenario);

        assertEquals(first.deterministic(), second.deterministic());
        assertEquals(120L, first.deterministic().finalTick());
        assertEquals(4L, first.deterministic().sampleCount());
        assertTrue(first.deterministic().entityCount() > 0);
        assertTrue(first.deterministic().stationCount() > 0);
        assertTrue(first.deterministic().traderCount() > 0);
        assertTrue(first.deterministic().moneyConserved());
        assertTrue(first.deterministic().nonNegativeInventories());
        assertFalse(first.deterministic().finalStockByItem().isEmpty());
        assertTrue(first.performance().wallClockNanos() > 0L);
        assertTrue(first.performance().ticksPerSecond() > 0d);
        assertTrue(first.performance().simulatedSecondsPerRealSecond() > 0d);
    }

    @Test
    void reportJsonСодержитОтдельныеDeterministicИPerformanceСекции() {
        BenchmarkScenario scenario = new BenchmarkScenario(
                "json-smoke",
                1,
                7L,
                20L,
                10L);

        String json = new EconomicBenchmarkRunner().run(scenario).toJson();

        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));
        assertTrue(json.contains("\"scenario\":{"));
        assertTrue(json.contains("\"deterministic\":{"));
        assertTrue(json.contains("\"performance\":{"));
        assertTrue(json.contains("\"name\":\"json-smoke\""));
        assertTrue(json.contains("\"moneyConserved\":true"));
        assertTrue(json.contains("\"simulationTicks\":20"));
    }

    @Test
    void scenarioОтклоняетНекорректнуюДлительностьИSampling() {
        assertThrows(IllegalArgumentException.class,
                () -> new BenchmarkScenario("bad", 1, 1L, 0L, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new BenchmarkScenario("bad", 1, 1L, 1L, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new BenchmarkScenario(" ", 1, 1L, 1L, 1L));
    }
}
