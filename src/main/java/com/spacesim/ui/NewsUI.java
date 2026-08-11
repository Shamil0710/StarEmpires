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

/**
 * Прокручиваемая лента экономических новостей в левом верхнем углу сцены.
 *
 * <p>Новые публикации располагаются сверху. Лента хранит не более пятидесяти
 * последних статей, чтобы длительная сессия не приводила к неограниченному росту
 * Scene2D-акторов и памяти. Горизонтальная прокрутка отключена, а подписи
 * переносятся в пределах адаптивной ширины панели.</p>
 */
public class NewsUI extends Table {
    private static final int MAX_ARTICLES = 50;
    private static final float MIN_PANEL_WIDTH = 280f;
    private static final float MAX_PANEL_WIDTH = 520f;
    private static final float HORIZONTAL_GAP = 20f;

    private final Skin skin;
    private final Table articlesTable;
    private final ScrollPane scrollPane;
    private final ArticleBuffer articleBuffer = new ArticleBuffer(MAX_ARTICLES);

    /**
     * Создаёт пустую ленту новостей.
     *
     * @param skin скин для полосы прокрутки и подписей; не {@code null}
     * @throws NullPointerException если {@code skin == null}
     */
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

    /**
     * Помещает публикацию в начало ленты и обновляет отображаемые подписи.
     *
     * <p>Если лимит достигнут, самая старая публикация удаляется. Цвет заголовка
     * берётся из статьи, если он задан.</p>
     *
     * @param article добавляемая публикация; не {@code null}
     * @throws NullPointerException если статья равна {@code null}
     */
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

    /**
     * Возвращает фактическое число публикаций в ограниченном буфере.
     *
     * @return значение от нуля до пятидесяти включительно
     */
    public int getArticleCount() {
        return articleBuffer.size();
    }

    /** Ограниченный буфер, сохраняющий публикации от новых к старым. */
    static final class ArticleBuffer {
        private final int capacity;
        private final Deque<NewsArticle> articles;

        /**
         * Создаёт буфер указанной ёмкости.
         *
         * @param capacity максимальное количество публикаций
         * @throws IllegalArgumentException если ёмкость неположительна
         */
        ArticleBuffer(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("Capacity must be positive");
            }
            this.capacity = capacity;
            articles = new ArrayDeque<>(capacity);
        }

        /** Добавляет публикацию в начало и при необходимости удаляет хвост. */
        void add(NewsArticle article) {
            articles.addFirst(Objects.requireNonNull(article, "Article must not be null"));
            if (articles.size() > capacity) {
                articles.removeLast();
            }
        }

        /** Возвращает текущее количество публикаций. */
        int size() {
            return articles.size();
        }

        /** Возвращает неизменяемый снимок публикаций от новых к старым. */
        List<NewsArticle> snapshot() {
            return List.copyOf(articles);
        }
    }
}
