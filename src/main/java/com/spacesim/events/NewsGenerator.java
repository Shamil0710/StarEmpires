package com.spacesim.events;
import com.badlogic.gdx.graphics.Color;

public class NewsGenerator {
    public static NewsArticle generate(EconomyEvent e) {
        return new NewsArticle("Event: " + e.name, "Impact on item " + e.targetItemId, Color.RED);
    }
}