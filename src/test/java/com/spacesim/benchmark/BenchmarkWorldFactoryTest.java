package com.spacesim.benchmark;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkWorldFactoryTest {
    @Test
    void scaleWorldСодержитРовно100StationsИ500EconomicAgents() {
        SimulationSession session = BenchmarkWorldFactory.createScale100x500(0xB3E6_500L);
        int stations = 0;
        int traders = 0;
        int miners = 0;
        int wallets = 0;

        for (Entity entity : session.getEngine().getEntities()) {
            if (entity.getComponent(MarketComponent.class) != null) {
                stations++;
            }
            if (entity.getComponent(TradeAIComponent.class) != null) {
                traders++;
            }
            if (entity.getComponent(MiningComponent.class) != null) {
                miners++;
            }
            if (entity.getComponent(WalletComponent.class) != null) {
                wallets++;
            }
        }

        assertEquals(BenchmarkWorldFactory.SCALE_STATION_COUNT, stations);
        assertEquals(BenchmarkWorldFactory.SCALE_TRADER_COUNT, traders);
        assertEquals(BenchmarkWorldFactory.SCALE_MINER_COUNT, miners);
        assertEquals(
                BenchmarkWorldFactory.SCALE_ECONOMIC_AGENT_COUNT,
                traders + miners);
        assertEquals(
                BenchmarkWorldFactory.SCALE_STATION_COUNT
                        + BenchmarkWorldFactory.SCALE_ECONOMIC_AGENT_COUNT,
                wallets);
    }

    @Test
    void scaleCiSmokeИсполняетAuthoritativePipelineИСохраняетMoneyConservation() {
        BenchmarkScenario scenario = BenchmarkScenario.scaleCiSmoke();
        BenchmarkReport report = new EconomicBenchmarkRunner(BenchmarkWorldFactory::createScale100x500)
                .run(scenario);

        assertEquals(scenario.simulationTicks(), report.deterministic().finalTick());
        assertEquals(2L, report.deterministic().sampleCount());
        assertEquals(BenchmarkWorldFactory.SCALE_STATION_COUNT, report.deterministic().stationCount());
        assertEquals(BenchmarkWorldFactory.SCALE_TRADER_COUNT, report.deterministic().traderCount());
        assertTrue(report.deterministic().entityCount()
                >= BenchmarkWorldFactory.SCALE_STATION_COUNT
                + BenchmarkWorldFactory.SCALE_ECONOMIC_AGENT_COUNT);
        assertTrue(report.deterministic().moneyConserved());
        assertTrue(report.deterministic().nonNegativeInventories());
        assertTrue(report.performance().ticksPerSecond() > 0d);
    }

    @Test
    void hundredHourScenarioСодержитРовно100ИгровыхЧасов() {
        BenchmarkScenario scenario = BenchmarkScenario.scale100Hours();

        assertEquals(BenchmarkScenario.ONE_HUNDRED_HOURS_TICKS, scenario.simulationTicks());
        assertEquals(3_600_000L, scenario.simulationTicks());
        assertEquals(600L, scenario.simulationTicks() / scenario.sampleEveryTicks());
    }
}
