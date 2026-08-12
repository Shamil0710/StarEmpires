package com.spacesim.simulation;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.EntitySystem;
import com.spacesim.events.GlobalEventManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationLoopTest {
    @Test
    void pipelineИсполняетСистемыТолькоПолнымиFixedTicks() {
        Engine engine = new Engine();
        CountingSystem counter = new CountingSystem();
        engine.addSystem(counter);
        SimulationClock clock = new SimulationClock(0.1f);
        GlobalEventManager events = new GlobalEventManager(0d);
        SimulationLoop loop = new SimulationLoop(clock, events, engine);

        assertEquals(0, loop.advanceFrame(0.04f));
        assertEquals(0, counter.updates);
        assertEquals(1, loop.advanceFrame(0.06f));
        assertEquals(1, counter.updates);
        assertEquals(0.1f, counter.totalDelta, 0.000001f);
        assertEquals(1L, loop.getClock().getTick());
    }

    @Test
    void разныеRenderPatternsДаютОдинаковоеЧислоSimulationTicks() {
        LoopFixture coarse = fixture();
        LoopFixture fine = fixture();

        for (int frame = 0; frame < 10; frame++) {
            coarse.loop.advanceFrame(1f);
        }
        for (int frame = 0; frame < 100; frame++) {
            fine.loop.advanceFrame(0.1f);
        }

        assertEquals(coarse.clock.getTick(), fine.clock.getTick());
        assertEquals(coarse.counter.updates, fine.counter.updates);
        assertEquals(coarse.counter.totalDelta, fine.counter.totalDelta, 0f);
        assertEquals(100L, coarse.clock.getTick());
    }

    @Test
    void защитнаяГраницаНеТеряетНакопленныеTicks() {
        Engine engine = new Engine();
        CountingSystem counter = new CountingSystem();
        engine.addSystem(counter);
        SimulationClock clock = new SimulationClock(0.1f);
        SimulationLoop loop = new SimulationLoop(clock, new GlobalEventManager(0d), engine, 3);

        assertEquals(3, loop.advanceFrame(1f));
        assertTrue(clock.hasPendingStep());
        assertEquals(3, loop.advanceFrame(0f));
        assertEquals(3, loop.advanceFrame(0f));
        assertEquals(1, loop.advanceFrame(0f));
        assertEquals(10, counter.updates);
        assertEquals(3, loop.getMaxStepsPerFrame());
    }

    @Test
    void конструкторПроверяетЗависимостиИГраницу() {
        Engine engine = new Engine();
        SimulationClock clock = new SimulationClock(0.1f);
        GlobalEventManager events = new GlobalEventManager(0d);

        assertThrows(NullPointerException.class, () -> new SimulationLoop(null, events, engine));
        assertThrows(NullPointerException.class, () -> new SimulationLoop(clock, null, engine));
        assertThrows(NullPointerException.class, () -> new SimulationLoop(clock, events, null));
        assertThrows(IllegalArgumentException.class, () -> new SimulationLoop(clock, events, engine, 0));
    }

    private LoopFixture fixture() {
        Engine engine = new Engine();
        CountingSystem counter = new CountingSystem();
        engine.addSystem(counter);
        SimulationClock clock = new SimulationClock(0.1f);
        SimulationLoop loop = new SimulationLoop(clock, new GlobalEventManager(0d), engine);
        return new LoopFixture(clock, loop, counter);
    }

    private static final class CountingSystem extends EntitySystem {
        private int updates;
        private float totalDelta;

        @Override
        public void update(float deltaTime) {
            updates++;
            totalDelta += deltaTime;
        }
    }

    private record LoopFixture(SimulationClock clock, SimulationLoop loop, CountingSystem counter) {
    }
}
