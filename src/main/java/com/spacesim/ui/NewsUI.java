package com.spacesim.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.spacesim.events.NewsArticle;

public class NewsUI extends Table {
    private Skin skin;

    public NewsUI(Skin skin) {
        this.skin = skin;
        this.setFillParent(true);
        this.top().left();
    }

    public void addNews(NewsArticle article) {
        Label l = new Label(article.headline, skin);
        l.setColor(article.color);
        this.add(l).left().row();
    }
}