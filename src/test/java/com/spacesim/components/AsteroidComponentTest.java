package com.spacesim.components;

import com.spacesim.constants.Constants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsteroidComponentTest {
    @Test
    void сохраняетНачальныйЗапасИБезопасноСчитаетОстаток() {
        AsteroidComponent asteroid = new AsteroidComponent(" NW-1 ", Constants.ITEM_ORE, 80L);

        assertEquals("NW-1", asteroid.spawnPointId);
        assertEquals(Constants.ITEM_ORE, asteroid.resourceItem);
        assertEquals(80L, asteroid.initialResource);
        assertEquals(80L, asteroid.remainingResource);
        assertEquals(1f, asteroid.getRemainingRatio(), 0f);
        assertFalse(asteroid.isDepleted());

        asteroid.remainingResource = 20L;
        assertEquals(0.25f, asteroid.getRemainingRatio(), 0.000001f);
        asteroid.remainingResource = 100L;
        assertEquals(1f, asteroid.getRemainingRatio(), 0f);
        asteroid.remainingResource = 0L;
        assertEquals(0f, asteroid.getRemainingRatio(), 0f);
        assertTrue(asteroid.isDepleted());
        asteroid.remainingResource = -1L;
        assertEquals(0f, asteroid.getRemainingRatio(), 0f);
        assertTrue(asteroid.isDepleted());
    }

    @Test
    void отклоняетПустуюТочкуНедобываемыйТоварИНеположительныйЗапас() {
        assertThrows(IllegalArgumentException.class,
                () -> new AsteroidComponent(" ", Constants.ITEM_ORE, 10L));
        assertThrows(IllegalArgumentException.class,
                () -> new AsteroidComponent("A", Constants.ITEM_FOOD, 10L));
        assertThrows(IllegalArgumentException.class,
                () -> new AsteroidComponent("A", -1, 10L));
        assertThrows(IllegalArgumentException.class,
                () -> new AsteroidComponent("A", Constants.ITEM_ORE, 0L));
    }
}
