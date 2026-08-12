package com.spacesim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.VisUI;
import com.spacesim.components.*;
import com.spacesim.constants.Constants;
import com.spacesim.economy.EconomicLedger;
import com.spacesim.events.GlobalEventManager;
import com.spacesim.events.NewsArticle;
import com.spacesim.model.AsteroidSpawnConfig;
import com.spacesim.persistence.EntityIdAllocator;
import com.spacesim.persistence.EntityRegistry;
import com.spacesim.simulation.SimulationClock;
import com.spacesim.simulation.SimulationLoop;
import com.spacesim.simulation.SimulationRandom;
import com.spacesim.systems.*;
import com.spacesim.ui.EconomyStatusUI;
import com.spacesim.ui.EntityDetailsUI;
import com.spacesim.ui.EntityPicker;
import com.spacesim.ui.NewsUI;
import com.spacesim.ui.PriceGraphRenderer;
import com.spacesim.ui.WorldMapLayout;
import com.spacesim.ui.WorldMapRenderer;
import com.spacesim.util.SpatialHashGrid;

/**
 * Главный объект приложения Star Empires и точка сборки демонстрационной сцены.
 *
 * <p>Модель получает только fixed simulation ticks через {@link SimulationLoop}. Все экономические
 * системы одной игровой сессии используют единый {@link EconomicLedger}: обычная торговля
 * фиксируется как transfer, а создание/уничтожение/преобразование ресурсов имеет явный тип записи.
 * Все runtime-сущности получают устойчивый {@link EntityIdComponent}; общий
 * {@link EntityRegistry} разрешает эти ID в текущие Ashley-объекты и автоматически отслеживает их
 * жизненный цикл. UI и Scene2D продолжают использовать render delta и не влияют на authoritative
 * состояние.</p>
 */
public class SpaceSimGame extends ApplicationAdapter {
    private static final float SIMULATION_FIXED_STEP_SECONDS = 0.1f;
    private static final long SIMULATION_ROOT_SEED = 0x5EED_2026L;
    private static final float ECONOMY_UI_UPDATE_INTERVAL_SECONDS = 0.25f;
    private static final float MAP_PADDING = 18f;
    private static final float MAP_PICK_RADIUS = 24f;
    private static final float DETAILS_AREA_GAP = 20f;

    private Engine engine;
    private GlobalEventManager eventManager;
    private EconomicLedger economicLedger;
    private EntityIdAllocator entityIdAllocator;
    private EntityRegistry entityRegistry;
    private SpatialHashGrid grid;
    private SimulationClock simulationClock;
    private SimulationLoop simulationLoop;
    private SimulationRandom simulationRandom;

    private Stage stage;
    private NewsUI newsUI;
    private EconomyStatusUI economyStatusUI;
    private EntityDetailsUI entityDetailsUI;
    private PriceGraphRenderer graphRenderer;
    private WorldMapRenderer worldMapRenderer;
    private Actor mapInteractionLayer;
    private WorldMapLayout mapLayout;
    private Entity selectedEntity;
    private float economyUiUpdateAccumulator;

    /** Создаёт неинициализированный объект приложения; графические ресурсы создаются в create(). */
    public SpaceSimGame() {
    }

    /**
     * Создаёт экономическую модель, persistent-ID инфраструктуру, общий ledger, pipeline и UI.
     *
     * <p>Порядок Ashley-систем значим: рынок → потребление → производство → появление астероидов →
     * добыча → торговый ИИ → запись истории. Глобальные события обновляются перед Ashley-движком
     * внутри {@link SimulationLoop}. Registry listener подключается до добавления любых сущностей,
     * поэтому одинаково отслеживает bootstrap-мир и динамические астероиды.</p>
     */
    @Override
    public void create() {
        VisUI.load();
        engine = new Engine();
        simulationRandom = new SimulationRandom(SIMULATION_ROOT_SEED);
        eventManager = new GlobalEventManager(simulationRandom.createStream("economy-events"));
        economicLedger = new EconomicLedger();
        entityIdAllocator = new EntityIdAllocator();
        entityRegistry = new EntityRegistry();
        engine.addEntityListener(Family.all(EntityIdComponent.class).get(), entityRegistry);
        grid = new SpatialHashGrid(200);
        simulationClock = new SimulationClock(SIMULATION_FIXED_STEP_SECONDS);

        stage = new Stage(new ScreenViewport());
        Skin skin = VisUI.getSkin();
        graphRenderer = new PriceGraphRenderer();
        worldMapRenderer = new WorldMapRenderer(skin.get(Label.LabelStyle.class).font);

        mapInteractionLayer = createMapInteractionLayer();
        stage.addActor(mapInteractionLayer);
        newsUI = new NewsUI(skin);
        stage.addActor(newsUI);
        economyStatusUI = new EconomyStatusUI(skin);
        stage.addActor(economyStatusUI);
        entityDetailsUI = new EntityDetailsUI(skin);
        stage.addActor(entityDetailsUI);
        updateMapLayout();
        Gdx.input.setInputProcessor(stage);

        engine.addSystem(new MarketSystem(eventManager));
        engine.addSystem(new ConsumptionSystem(eventManager, economicLedger));
        engine.addSystem(new ProductionSystem(economicLedger));
        engine.addSystem(new AsteroidSpawnSystem(
                AsteroidSpawnConfig.demoWorld(),
                simulationRandom.createStream("asteroid-spawn"),
                economicLedger,
                entityIdAllocator));
        engine.addSystem(new MiningSystem(economicLedger));
        engine.addSystem(new TradeAISystem(grid, economicLedger));
        engine.addSystem(new PriceRecorderSystem());

        for (Entity entity : DemoWorldFactory.createEntities(entityIdAllocator)) {
            engine.addEntity(entity);
        }
        simulationLoop = new SimulationLoop(simulationClock, eventManager, engine);

        economyStatusUI.update(engine.getEntities());
        entityDetailsUI.refresh();
        Gdx.gl.glClearColor(0.018f, 0.025f, 0.045f, 1f);
    }

    /**
     * @return общий экономический журнал текущей игровой сессии
     * @throws IllegalStateException если приложение ещё не инициализировано
     */
    public EconomicLedger getEconomicLedger() {
        if (economicLedger == null) {
            throw new IllegalStateException("Экономическая симуляция ещё не инициализирована");
        }
        return economicLedger;
    }

    /**
     * @return runtime registry устойчивых ID текущей игровой сессии
     * @throws IllegalStateException если приложение ещё не инициализировано
     */
    public EntityRegistry getEntityRegistry() {
        if (entityRegistry == null) {
            throw new IllegalStateException("Registry сущностей ещё не инициализирован");
        }
        return entityRegistry;
    }

    /**
     * @return значение persistent ID, которое будет выдано следующей созданной сущности
     * @throws IllegalStateException если приложение ещё не инициализировано
     */
    public long getNextEntityIdValue() {
        if (entityIdAllocator == null) {
            throw new IllegalStateException("Аллокатор EntityId ещё не инициализирован");
        }
        return entityIdAllocator.getNextValue();
    }

    /**
     * Включает или снимает паузу игрового времени.
     *
     * @param paused новое состояние паузы
     */
    public void setSimulationPaused(boolean paused) {
        requireSimulationClock().setPaused(paused);
    }

    /** @return текущее состояние паузы игрового времени */
    public boolean isSimulationPaused() {
        return requireSimulationClock().isPaused();
    }

    /**
     * Задаёт скорость игрового времени независимо от скорости UI/рендера.
     *
     * @param timeScale конечный неотрицательный множитель
     */
    public void setSimulationTimeScale(double timeScale) {
        requireSimulationClock().setTimeScale(timeScale);
    }

    /** @return текущий множитель игрового времени */
    public double getSimulationTimeScale() {
        return requireSimulationClock().getTimeScale();
    }

    /** @return игровое время полностью исполненных fixed ticks в секундах */
    public double getSimulationTimeSeconds() {
        return requireSimulationClock().getSimulationTimeSeconds();
    }

    private SimulationClock requireSimulationClock() {
        if (simulationClock == null) {
            throw new IllegalStateException("Игровая симуляция ещё не инициализирована");
        }
        return simulationClock;
    }

    private Actor createMapInteractionLayer() {
        Actor interactionLayer = new Actor();
        interactionLayer.addListener(new InputListener() {
            private int dragPointer = -1;
            private float previousX;
            private float previousY;

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                if (pointer == -1 && event.getStage() != null) {
                    event.getStage().setScrollFocus(event.getListenerActor());
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                if (pointer == -1
                        && event.getStage() != null
                        && event.getStage().getScrollFocus() == event.getListenerActor()) {
                    event.getStage().setScrollFocus(null);
                }
            }

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (mapLayout == null || !mapLayout.containsMapPoint(x, y)) {
                    return false;
                }

                if (button == Input.Buttons.LEFT) {
                    selectedEntity = EntityPicker.pick(
                            engine.getEntities(), mapLayout, x, y, MAP_PICK_RADIUS);
                    entityDetailsUI.select(selectedEntity);
                    if (selectedEntity == null) {
                        beginDrag(pointer, x, y);
                    }
                    return true;
                }
                if (button == Input.Buttons.MIDDLE || button == Input.Buttons.RIGHT) {
                    beginDrag(pointer, x, y);
                    return true;
                }
                return false;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (pointer != dragPointer || mapLayout == null) {
                    return;
                }
                mapLayout = mapLayout.panByScreen(x - previousX, y - previousY);
                previousX = x;
                previousY = y;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                if (pointer == dragPointer) {
                    dragPointer = -1;
                }
            }

            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                if (mapLayout == null || !mapLayout.containsMapPoint(x, y)) {
                    return false;
                }
                mapLayout = mapLayout.zoomByScroll(x, y, amountY);
                return true;
            }

            private void beginDrag(int pointer, float x, float y) {
                dragPointer = pointer;
                previousX = x;
                previousY = y;
            }
        });
        return interactionLayer;
    }

    private void updateMapLayout() {
        float stageWidth = Math.max(64f, stage.getWidth());
        float stageHeight = Math.max(64f, stage.getHeight());
        float availableWidth = Math.max(
                64f,
                stageWidth - EntityDetailsUI.RECOMMENDED_WIDTH - DETAILS_AREA_GAP);
        float padding = Math.min(MAP_PADDING, Math.min(availableWidth, stageHeight) * 0.2f);

        if (mapLayout == null) {
            mapLayout = new WorldMapLayout(0f, 0f, availableWidth, stageHeight, padding);
        } else {
            mapLayout = mapLayout.resize(0f, 0f, availableWidth, stageHeight, padding);
        }
        mapInteractionLayer.setBounds(0f, 0f, availableWidth, stageHeight);
    }

    private void renderSelectedPriceGraph() {
        if (selectedEntity == null || mapLayout == null || mapLayout.getMapWidth() < 600f) {
            return;
        }

        PriceHistoryComponent priceHistory = selectedEntity.getComponent(PriceHistoryComponent.class);
        MarketComponent market = selectedEntity.getComponent(MarketComponent.class);
        if (priceHistory == null || market == null) {
            return;
        }

        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            if (!market.isTradable(itemId) || priceHistory.history[itemId].size < 2) {
                continue;
            }

            float graphWidth = Math.min(220f, mapLayout.getMapWidth() * 0.38f);
            float graphHeight = Math.min(90f, mapLayout.getMapHeight() * 0.22f);
            graphRenderer.render(
                    stage.getCamera().combined,
                    priceHistory.history[itemId],
                    mapLayout.getMapX() + mapLayout.getMapWidth() - graphWidth - 16f,
                    mapLayout.getMapY() + 16f,
                    graphWidth,
                    graphHeight);
            return;
        }
    }

    private void clearInactiveSelection() {
        if (selectedEntity == null || engine == null) {
            return;
        }
        for (Entity entity : engine.getEntities()) {
            if (entity == selectedEntity) {
                return;
            }
        }
        selectedEntity = null;
        entityDetailsUI.select(null);
    }

    /** Выполняет один render frame и доступные fixed simulation ticks. */
    @Override
    public void render() {
        float renderDelta = Gdx.graphics.getDeltaTime();

        simulationLoop.advanceFrame(renderDelta);
        for (NewsArticle article : eventManager.consumePendingNews()) {
            newsUI.addNews(article);
        }
        clearInactiveSelection();
        economyUiUpdateAccumulator += renderDelta;
        if (economyUiUpdateAccumulator >= ECONOMY_UI_UPDATE_INTERVAL_SECONDS) {
            economyUiUpdateAccumulator %= ECONOMY_UI_UPDATE_INTERVAL_SECONDS;
            economyStatusUI.update(engine.getEntities());
            entityDetailsUI.refresh();
        }
        stage.act(renderDelta);

        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        worldMapRenderer.render(
                stage.getCamera().combined,
                engine.getEntities(),
                mapLayout,
                selectedEntity);
        renderSelectedPriceGraph();
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0 || stage == null) {
            return;
        }
        stage.getViewport().update(width, height, true);
        updateMapLayout();
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
        if (worldMapRenderer != null) {
            worldMapRenderer.dispose();
            worldMapRenderer = null;
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
