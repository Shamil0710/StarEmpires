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
    }

    @Test
    void пустоеИмяПотокаОтклоняется() {
        SimulationRandom random = new SimulationRandom(1L);
        assertThrows(IllegalArgumentException.class, () -> random.createStream(null));
        assertThrows(IllegalArgumentException.class, () -> random.createStream(" "));
    }
}
