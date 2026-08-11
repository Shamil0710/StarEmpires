package com.spacesim.ui;

import com.badlogic.gdx.graphics.Color;
import com.spacesim.events.NewsArticle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NewsUITest {
    @Test
    void хранитПятьдесятПоследнихНовостейВОбратнойХронологии() {
        NewsUI.ArticleBuffer buffer = new NewsUI.ArticleBuffer(50);

        for (int index = 0; index < 75; index++) {
            buffer.add(new NewsArticle("Новость " + index, "", Color.WHITE));
        }

        List<NewsArticle> snapshot = buffer.snapshot();
        assertEquals(50, buffer.size());
        assertEquals("Новость 74", snapshot.get(0).headline);
        assertEquals("Новость 25", snapshot.get(snapshot.size() - 1).headline);
        assertThrows(NullPointerException.class, () -> buffer.add(null));
    }

    @Test
    void отклоняетНеположительныйЛимит() {
        assertThrows(IllegalArgumentException.class, () -> new NewsUI.ArticleBuffer(0));
        assertThrows(IllegalArgumentException.class, () -> new NewsUI.ArticleBuffer(-1));
    }
}
