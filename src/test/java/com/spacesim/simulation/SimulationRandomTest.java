package com.spacesim.simulation;

import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimulationRandomTest {
    @Test
    void одинаковыйSeedИИмяДаютОдинаковуюПоследовательность() {
        SimulationRandom first = new SimulationRandom(12345L);
        SimulationRandom second = new SimulationRandom(12345L);
        RandomGenerator a = first.createStream("events");
        RandomGenerator b = second.createStream("events");

        for (int index = 0; index < 16; index++) {
            assertEquals(a.nextLong(), b.nextLong());
        }
        assertEquals(12345L, first.getRootSeed());
    }

    @Test
    void разныеИменаНеДелятОднуПоследовательность() {
        SimulationRandom random = new SimulationRandom(12345L);
        RandomGenerator events = random.createStream("events");
        RandomGenerator asteroids = random.createStream("asteroids");

        assertNotEquals(events.nextLong(), asteroids.nextLong());
        assertNotEquals(random.deriveSeed("events"), random.deriveSeed("asteroids"));
    }

    @Test
    void statefulПотокПродолжаетсяСExactСледующегоЗначенияПослеRestore() {
        SimulationRandom random = new SimulationRandom(987654321L);
        StatefulRandom original = random.createStream("events");
        for (int index = 0; index < 25; index++) {
            original.nextLong();
        }

        long savedState = original.getState();
        StatefulRandom restored = random.restoreStream(savedState);

        assertEquals(savedState, restored.getState());
        for (int index = 0; index < 32; index++) {
            assertEquals(original.nextLong(), restored.nextLong());
        }
    }

    @Test
    void statefulRandomПринимаетЛюбые64БитаСостояния() {
        StatefulRandom zero = new StatefulRandom(0L);
        StatefulRandom min = new StatefulRandom(Long.MIN_VALUE);
        StatefulRandom max = new StatefulRandom(Long.MAX_VALUE);

        assertNotEquals(zero.nextLong(), min.nextLong());
        assertNotEquals(min.nextLong(), max.nextLong());
    }

    @Test
    void пустоеИмяПотокаОтклоняется() {
        SimulationRandom random = new SimulationRandom(1L);
        assertThrows(IllegalArgumentException.class, () -> random.createStream(null));
        assertThrows(IllegalArgumentException.class, () -> random.createStream(" "));
        assertThrows(IllegalArgumentException.class, () -> random.deriveSeed(""));
    }
}
