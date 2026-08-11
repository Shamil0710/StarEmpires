package com.spacesim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.kotcrab.vis.ui.VisUI;
import com.spacesim.components.*;
import com.spacesim.events.GlobalEventManager;
import com.spacesim.systems.*;
import com.spacesim.ui.NewsUI;
import com.spacesim.ui.PriceGraphRenderer;
import com.spacesim.util.SpatialHashGrid;

public class SpaceSimGame extends ApplicationAdapter {
    private Engine engine;
    private GlobalEventManager eventManager;
    private SpatialHashGrid grid;

    // UI
    private Stage stage;
    private NewsUI newsUI;
    private PriceGraphRenderer graphRenderer;

    @Override
    public void create() {
        VisUI.load();
        engine = new Engine();
        eventManager = new GlobalEventManager();
        grid = new SpatialHashGrid(200);

        // UI Init
        stage = new Stage();
        Skin skin = VisUI.getSkin();
        newsUI = new NewsUI(skin);
        stage.addActor(newsUI);
        graphRenderer = new PriceGraphRenderer();

        // Systems Init
        engine.addSystem(new MarketSystem(eventManager));
        engine.addSystem(new ConsumptionSystem());
        engine.addSystem(new ProductionSystem());
        engine.addSystem(new TradeAISystem(grid));
        engine.addSystem(new PriceRecorderSystem());

        // Test Station
        createStation(100, 100);
        // Test Fleet
        createFleet(300, 300);
    }

    private void createStation(float x, float y) {
        Entity e = new Entity();
        e.add(new TransformComponent());
        e.getComponent(TransformComponent.class).position.set(x, y);

        InventoryComponent inv = new InventoryComponent();
        inv.stock[2] = 500; // Food
        e.add(inv);

        MarketComponent m = new MarketComponent();
        m.targetStock[2] = 1000;
        m.baseConsumption[2] = 5.0f; // Eaters
        e.add(m);

        e.add(new PriceHistoryComponent());
        engine.addEntity(e);
    }

    private void createFleet(float x, float y) {
        Entity e = new Entity();
        e.add(new TransformComponent());
        e.getComponent(TransformComponent.class).position.set(x, y);
        e.add(new TradeAIComponent());
        e.add(new InventoryComponent());
        engine.addEntity(e);
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();

        // Update Logic
        eventManager.update(delta);
        engine.update(delta);
        stage.act(delta);

        // Draw
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.draw();

        // Рисуем график для первой попавшейся станции (для теста)
        if(engine.getEntities().size() > 0) {
            Entity s = engine.getEntities().first();
            if (s.getComponent(PriceHistoryComponent.class) != null) {
                // Рисуем график товара ID 2 (Food)
                graphRenderer.render(s.getComponent(PriceHistoryComponent.class).history[2],
                        50, 50, 200, 100);
            }
        }
    }

    @Override
    public void dispose() {
        VisUI.dispose();
        stage.dispose();
        graphRenderer.dispose();
    }
}