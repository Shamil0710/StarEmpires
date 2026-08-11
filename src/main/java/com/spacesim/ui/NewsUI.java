package com.spacesim.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Value;
import com.spacesim.events.NewsArticle;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

public class NewsUI extends Table {
    private static final int MAX_ARTICLES = 50;
    private static final float MIN_PANEL_WIDTH = 280f;
    private static final float MAX_PANEL_WIDTH = 520f;
    private static final float HORIZONTAL_GAP = 20f;

    private final Skin skin;
    private final Table articlesTable;
    private final ScrollPane scrollPane;
    private final ArticleBuffer articleBuffer = new ArticleBuffer(MAX_ARTICLES);

    public NewsUI(Skin skin) {
        this.skin = Objects.requireNonNull(skin, "Skin must not be null");
        articlesTable = new Table();
        articlesTable.top().left();
        scrollPane = new ScrollPane(articlesTable, this.skin);
        scrollPane.setScrollingDisabled(true, false);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setOverscroll(false, false);

        setFillParent(true);
        setTouchable(Touchable.childrenOnly);
        top().left();
        pad(10f);
        add(scrollPane).top().left().width(new Value() {
            @Override
            public float get(Actor context) {
                float availableHalf = NewsUI.this.getWidth() * 0.5f - HORIZONTAL_GAP;
                return Math.min(MAX_PANEL_WIDTH, Math.max(MIN_PANEL_WIDTH, availableHalf));
            }
        }).height(240f);
    }

    public void addNews(NewsArticle article) {
        NewsArticle checkedArticle = Objects.requireNonNull(article, "Article must not be null");
        articleBuffer.add(checkedArticle);

        articlesTable.clearChildren();
        for (NewsArticle displayedArticle : articleBuffer.snapshot()) {
            Label label = new Label(Objects.requireNonNullElse(displayedArticle.headline, ""), skin);
            if (displayedArticle.color != null) {
                label.setColor(displayedArticle.color);
            }
            label.setWrap(true);
            articlesTable.add(label).left().growX().row();
        }
        scrollPane.setScrollY(0f);
    }

    public int getArticleCount() {
        return articleBuffer.size();
    }

    static final class ArticleBuffer {
        private final int capacity;
        private final Deque<NewsArticle> articles;

        ArticleBuffer(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("Capacity must be positive");
            }
            this.capacity = capacity;
            articles = new ArrayDeque<>(capacity);
        }

        void add(NewsArticle article) {
            articles.addFirst(Objects.requireNonNull(article, "Article must not be null"));
            if (articles.size() > capacity) {
                articles.removeLast();
            }
        }

        int size() {
            return articles.size();
        }

        List<NewsArticle> snapshot() {
            return List.copyOf(articles);
        }
    }
}
