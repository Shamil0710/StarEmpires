package com.spacesim.events;
import com.badlogic.gdx.graphics.Color;

public class NewsArticle {
    public String headline;
    public String content;
    public Color color;
    public long timestamp;

    public NewsArticle(String h, String c, Color col) {
        this.headline = h; this.content = c; this.color = col;
        this.timestamp = System.currentTimeMillis();
    }
}
