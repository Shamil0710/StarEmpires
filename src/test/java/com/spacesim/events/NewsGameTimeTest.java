package com.spacesim.events;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.spacesim.constants.Constants;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NewsGameTimeTest {
    @Test
    void managerПроставляетНовостиПоИгровомуВремениАНеWallClock() {
        GlobalEventManager manager = new GlobalEventManager(new Random(123L), 0d);
        manager.update(2.5f);
        manager.activateEvent(event());

        NewsArticle article = manager.consumePendingNews().get(0);
        assertEquals(2.5d, manager.getSimulationTimeSeconds(), 1e-12);
        assertEquals(2_500L, article.timestamp);
    }

    @Test
    void прямойКонструкторНовостиНеОбращаетсяКСистемнымЧасам() {
        NewsArticle article = new NewsArticle("H", "C", Color.WHITE);
        assertEquals(0L, article.timestamp);

        NewsArticle timed = new NewsArticle("H", "C", Color.WHITE, 42L);
        assertEquals(42L, timed.timestamp);
        assertThrows(IllegalArgumentException.class,
                () -> new NewsArticle("H", "C", Color.WHITE, -1L));
    }

    @Test
    void generatorПроверяетИгровоеВремя() {
        NewsArticle article = NewsGenerator.generate(event(), 1.234d);
        assertEquals(1_234L, article.timestamp);
        assertThrows(IllegalArgumentException.class, () -> NewsGenerator.generate(event(), -1d));
        assertThrows(IllegalArgumentException.class, () -> NewsGenerator.generate(event(), Double.NaN));
        assertThrows(NullPointerException.class, () -> NewsGenerator.generate(null, 0d));
    }

    private EconomyEvent event() {
        return new EconomyEvent(
                "TEST",
                Constants.ITEM_FOOD,
                2f,
                1.5f,
                10f,
                new Vector2(0f, 0f),
                100f);
    }
}
