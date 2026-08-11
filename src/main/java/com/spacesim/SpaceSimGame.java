package com.spacesim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.VisUI;
import com.spacesim.components.*;
import com.spacesim.constants.Constants;
import com.spacesim.events.GlobalEventManager;
import com.spacesim.events.NewsArticle;
import com.spacesim.model.Recipe;
import com.spacesim.systems.*;
import com.spacesim.ui.EconomyStatusUI;
import com.spacesim.ui.NewsUI;
import com.spacesim.ui.PriceGraphRenderer;
import com.spacesim.util.SpatialHashGrid;

public class SpaceSimGame extends ApplicationAdapter {
    private static final float ECONOMY_UI_UPDATE_INTERVAL_SECONDS = 0.25f;

    private Engine engine;
    private GlobalEventManager eventManager;
    private SpatialHashGrid grid;

    // UI
    private Stage stage;
    private NewsUI newsUI;
    private EconomyStatusUI economyStatusUI;
    private PriceGraphRenderer graphRenderer;
    private float economyUiUpdateAccumulator;

    @Override
    public void create() {
        VisUI.load();
        engine = new Engine();
        eventManager = new GlobalEventManager();
        grid = new SpatialHashGrid(200);

        // UI Init
        stage = new Stage(new ScreenViewport());
        Skin skin = VisUI.getSkin();
        newsUI = new NewsUI(skin);
        stage.addActor(newsUI);
        economyStatusUI = new EconomyStatusUI(skin);
        stage.addActor(economyStatusUI);
        graphRenderer = new PriceGraphRenderer();
        Gdx.input.setInputProcessor(stage);

        // Systems Init
        engine.addSystem(new MarketSystem(eventManager));
        engine.addSystem(new ConsumptionSystem(eventManager));
        engine.addSystem(new ProductionSystem());
        engine.addSystem(new TradeAISystem(grid));
        engine.addSystem(new PriceRecorderSystem());

        // Тестовая производственная станция с рудой и энергией
        createProductionStation(100, 400, Constants.FACTION_MINERS);
        // Тестовая станция-продавец с избытком еды
        createStation(100, 100, 1200, 500, 0.0f, Constants.FACTION_TRADE_LEAGUE);
        // Тестовая станция-покупатель с дефицитом еды
        createStation(500, 100, 100, 1000, 5.0f, Constants.FACTION_NEUTRAL);
        // Тестовый торговый флот
        createFleet(300, 300);

        economyStatusUI.update(engine.getEntities());
    }

    private void createStation(float x, float y, int foodStock, int targetFoodStock, float foodConsumption, int factionId) {
        Entity e = new Entity();
        e.add(new TransformComponent());
        e.getComponent(TransformComponent.class).position.set(x, y);

        InventoryComponent inv = new InventoryComponent();
        inv.stock[Constants.ITEM_FOOD] = foodStock;
        e.add(inv);

        MarketComponent m = new MarketComponent();
        m.configureTradableItem(Constants.ITEM_FOOD, targetFoodStock, foodConsumption);
        e.add(m);
        e.add(new FactionComponent(factionId));

        e.add(new PriceHistoryComponent());
        engine.addEntity(e);
    }

    private void createProductionStation(float x, float y, int factionId) {
        Entity e = new Entity();
        e.add(new TransformComponent());
        e.getComponent(TransformComponent.class).position.set(x, y);

        InventoryComponent inv = new InventoryComponent();
        inv.stock[Constants.ITEM_ORE] = 500;
        inv.stock[Constants.ITEM_ENERGY] = 250;
        e.add(inv);

        MarketComponent m = new MarketComponent();
        m.configureTradableItem(Constants.ITEM_STEEL, 300, 0f);
        e.add(m);
        e.add(new FactionComponent(factionId));

        ProductionComponent production = new ProductionComponent();
        production.recipes.add(new Recipe("Выплавка стали", 2.0f)
                .input(Constants.ITEM_ORE, 2)
                .input(Constants.ITEM_ENERGY, 1)
                .output(Constants.ITEM_STEEL, 1));
        e.add(production);

        e.add(new PriceHistoryComponent());
        engine.addEntity(e);
    }

    private void createFleet(float x, float y) {
        Entity e = new Entity();
        e.add(new TransformComponent());
        e.getComponent(TransformComponent.class).position.set(x, y);
        e.add(new TradeAIComponent());
        ReputationComponent reputation = new ReputationComponent();
        reputation.addReputation(Constants.FACTION_TRADE_LEAGUE, 25f);
        reputation.addReputation(Constants.FACTION_MINERS, 10f);
        e.add(reputation);
        e.add(new InventoryComponent());
        engine.addEntity(e);
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();

        // Update Logic
        eventManager.update(delta);
        for (NewsArticle article : eventManager.consumePendingNews()) {
            newsUI.addNews(article);
        }
        engine.update(delta);
        economyUiUpdateAccumulator += delta;
        if (economyUiUpdateAccumulator >= ECONOMY_UI_UPDATE_INTERVAL_SECONDS) {
            economyUiUpdateAccumulator %= ECONOMY_UI_UPDATE_INTERVAL_SECONDS;
            economyStatusUI.update(engine.getEntities());
        }
        stage.act(delta);

        // Draw
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.draw();

        // Рисуем график для первой попавшейся станции (для теста)
        if (engine.getEntities().size() > 0) {
            Entity s = engine.getEntities().first();
            if (s.getComponent(PriceHistoryComponent.class) != null) {
                graphRenderer.render(
                        stage.getCamera().combined,
                        s.getComponent(PriceHistoryComponent.class).history[Constants.ITEM_FOOD],
                        50f,
                        50f,
                        200f,
                        100f);
            }
        }
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0 || stage == null) {
            return;
        }
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        if (Gdx.input != null && Gdx.input.getInputProcessor() == stage) {
            Gdx.input.setInputProcessor(null);
        }
        if (stage != null) {
            stage.dispose();
            stage = null;
        }
        if (graphRenderer != null) {
            graphRenderer.dispose();
            graphRenderer = null;
        }
        if (VisUI.isLoaded()) {
            VisUI.dispose();
        }
    }
}
