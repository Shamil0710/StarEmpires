package com.spacesim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
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
import com.spacesim.events.GlobalEventManager;
import com.spacesim.events.NewsArticle;
import com.spacesim.model.Recipe;
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
 * <p>Класс управляет жизненным циклом libGDX: создаёт Ashley-движок, регистрирует
 * экономические системы в порядке их выполнения, наполняет мир начальными
 * станциями и флотом, а затем на каждом кадре обновляет симуляцию и интерфейс.
 * Экономическая панель намеренно обновляется не чаще четырёх раз в секунду,
 * тогда как модель продолжает получать фактическое время кадра.</p>
 *
 * <p>Экземпляр создаётся desktop-запускателем. Все графические ресурсы,
 * созданные в {@link #create()}, освобождаются в {@link #dispose()}.</p>
 */
public class SpaceSimGame extends ApplicationAdapter {
    private static final float ECONOMY_UI_UPDATE_INTERVAL_SECONDS = 0.25f;
    private static final float MAP_PADDING = 18f;
    private static final float MAP_PICK_RADIUS = 24f;
    private static final float DETAILS_AREA_GAP = 20f;

    private Engine engine;
    private GlobalEventManager eventManager;
    private SpatialHashGrid grid;

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

    /**
     * Создаёт неинициализированный объект приложения.
     *
     * <p>Ресурсы libGDX здесь не создаются: графический контекст становится
     * доступен только при последующем вызове {@link #create()}.</p>
     */
    public SpaceSimGame() {
    }

    /**
     * Создаёт экономическую модель, пользовательский интерфейс и начальный мир.
     *
     * <p>Метод вызывается libGDX один раз после инициализации графического
     * контекста. Порядок регистрации систем значим: сначала формируются цены и
     * потребление, затем выполняются производство, торговый ИИ и запись истории.</p>
     */
    @Override
    public void create() {
        VisUI.load();
        engine = new Engine();
        eventManager = new GlobalEventManager();
        grid = new SpatialHashGrid(200);

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
        engine.addSystem(new ConsumptionSystem(eventManager));
        engine.addSystem(new ProductionSystem());
        engine.addSystem(new TradeAISystem(grid));
        engine.addSystem(new PriceRecorderSystem());

        // Три станции образуют на карте заметный экономический треугольник.
        createProductionStation("Кузница Гелиос", 350, 390, Constants.FACTION_MINERS);
        createStation("Биржа Аврора", 120, 135, 1200, 500, 0.0f,
                Constants.FACTION_TRADE_LEAGUE);
        createStation("Фронтир-Хаб", 580, 135, 100, 1000, 5.0f,
                Constants.FACTION_NEUTRAL);

        // Несколько флотов с разной скоростью делают торговые маршруты наглядными.
        createFleet("Купец-01", 300, 280, 58f, 1200f);
        createFleet("Караван Вега", 350, 330, 72f, 1600f);
        createFleet("Стриж", 430, 270, 88f, 900f);

        economyStatusUI.update(engine.getEntities());
        entityDetailsUI.refresh();
        Gdx.gl.glClearColor(0.018f, 0.025f, 0.045f, 1f);
    }

    /**
     * Добавляет обычную торговую станцию с рынком продовольствия.
     *
     * <p>Если начальный запас превышает стандартную вместимость склада, она
     * увеличивается до величины запаса, чтобы сохранить инвариант инвентаря.</p>
     *
     * @param name отображаемое имя станции
     * @param x координата станции по горизонтали
     * @param y координата станции по вертикали
     * @param foodStock начальный запас продовольствия
     * @param targetFoodStock целевой запас, относительно которого рынок формирует цену
     * @param foodConsumption расход продовольствия в единицах товара за секунду
     * @param factionId идентификатор фракции-владельца
     */
    private void createStation(String name, float x, float y, int foodStock, int targetFoodStock,
                               float foodConsumption, int factionId) {
        Entity e = new Entity();
        e.add(new IdentityComponent(name, IdentityComponent.Kind.STATION));
        e.add(new TransformComponent());
        e.getComponent(TransformComponent.class).position.set(x, y);

        InventoryComponent inv = new InventoryComponent();
        inv.stock[Constants.ITEM_FOOD] = foodStock;
        inv.capacity = Math.max(inv.capacity, foodStock);
        e.add(inv);

        MarketComponent m = new MarketComponent();
        m.configureTradableItem(Constants.ITEM_FOOD, targetFoodStock, foodConsumption);
        e.add(m);
        e.add(new FactionComponent(factionId));

        e.add(new PriceHistoryComponent());
        engine.addEntity(e);
    }

    /**
     * Добавляет станцию, которая выплавляет сталь из руды и энергии.
     *
     * @param name отображаемое имя станции
     * @param x координата станции по горизонтали
     * @param y координата станции по вертикали
     * @param factionId идентификатор фракции-владельца
     */
    private void createProductionStation(String name, float x, float y, int factionId) {
        Entity e = new Entity();
        e.add(new IdentityComponent(name, IdentityComponent.Kind.STATION));
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

    /**
     * Добавляет автономный торговый флот с пустым трюмом и стартовой репутацией.
     *
     * @param name отображаемое имя корабля
     * @param x начальная координата флота по горизонтали
     * @param y начальная координата флота по вертикали
     * @param movementSpeed скорость движения в мировых единицах в секунду
     * @param credits стартовый денежный баланс в кредитах
     */
    private void createFleet(String name, float x, float y, float movementSpeed, float credits) {
        Entity e = new Entity();
        e.add(new IdentityComponent(name, IdentityComponent.Kind.FLEET));
        e.add(new TransformComponent());
        e.getComponent(TransformComponent.class).position.set(x, y);
        TradeAIComponent tradeAI = new TradeAIComponent();
        tradeAI.movementSpeed = movementSpeed;
        tradeAI.credits = credits;
        e.add(tradeAI);
        ReputationComponent reputation = new ReputationComponent();
        reputation.addReputation(Constants.FACTION_TRADE_LEAGUE, 25f);
        reputation.addReputation(Constants.FACTION_MINERS, 10f);
        e.add(reputation);
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = tradeAI.cargoSpace;
        e.add(inventory);
        engine.addEntity(e);
    }

    /**
     * Создаёт прозрачный нижний слой сцены, преобразующий щелчок по карте в выбор сущности.
     *
     * <p>Панели добавляются на сцену после этого слоя и поэтому получают ввод первыми. Щелчок
     * по свободному месту карты снимает текущий выбор; остальные кнопки мыши игнорируются.</p>
     *
     * @return настроенный Scene2D-актор без собственного визуального представления
     */
    private Actor createMapInteractionLayer() {
        Actor interactionLayer = new Actor();
        interactionLayer.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (button != Input.Buttons.LEFT || mapLayout == null) {
                    return false;
                }

                selectedEntity = EntityPicker.pick(
                        engine.getEntities(), mapLayout, x, y, MAP_PICK_RADIUS);
                entityDetailsUI.select(selectedEntity);
                return true;
            }
        });
        return interactionLayer;
    }

    /**
     * Пересчитывает вписанную карту и область обработки ввода по текущему размеру сцены.
     *
     * <p>Справа всегда резервируется место под карточку объекта. Для необычно маленького
     * viewport сохраняется минимальная корректная геометрия, чтобы сворачивание и восстановление
     * окна не передавали нулевые размеры в преобразование координат.</p>
     */
    private void updateMapLayout() {
        float stageWidth = Math.max(64f, stage.getWidth());
        float stageHeight = Math.max(64f, stage.getHeight());
        float availableWidth = Math.max(
                64f,
                stageWidth - EntityDetailsUI.RECOMMENDED_WIDTH - DETAILS_AREA_GAP);
        float padding = Math.min(MAP_PADDING, Math.min(availableWidth, stageHeight) * 0.2f);

        mapLayout = new WorldMapLayout(0f, 0f, availableWidth, stageHeight, padding);
        mapInteractionLayer.setBounds(0f, 0f, availableWidth, stageHeight);
    }

    /** Рисует график первого торгуемого товара выбранной станции в нижней части карты. */
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

    /**
     * Выполняет один кадр симуляции и отрисовки.
     *
     * <p>Сначала обновляются глобальные события и Ashley-системы, затем с ограниченной частотой
     * актуализируются текстовые панели. Карта и выбранный ценовой график рисуются под Scene2D,
     * поэтому интерактивные панели и карточка объекта всегда остаются читаемыми.</p>
     */
    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();

        eventManager.update(delta);
        for (NewsArticle article : eventManager.consumePendingNews()) {
            newsUI.addNews(article);
        }
        engine.update(delta);
        economyUiUpdateAccumulator += delta;
        if (economyUiUpdateAccumulator >= ECONOMY_UI_UPDATE_INTERVAL_SECONDS) {
            economyUiUpdateAccumulator %= ECONOMY_UI_UPDATE_INTERVAL_SECONDS;
            economyStatusUI.update(engine.getEntities());
            entityDetailsUI.refresh();
        }
        stage.act(delta);

        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        worldMapRenderer.render(
                stage.getCamera().combined,
                engine.getEntities(),
                mapLayout,
                selectedEntity);
        renderSelectedPriceGraph();
        stage.draw();
    }

    /**
     * Подгоняет виртуальную сцену под новый размер окна.
     *
     * <p>Нулевые размеры, которые возможны при сворачивании окна, игнорируются,
     * чтобы не передавать некорректную геометрию во viewport.</p>
     *
     * @param width новая ширина окна в пикселях
     * @param height новая высота окна в пикселях
     */
    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0 || stage == null) {
            return;
        }
        stage.getViewport().update(width, height, true);
        updateMapLayout();
    }

    /**
     * Освобождает Scene2D, отрисовщик графика и глобальный скин VisUI.
     *
     * <p>Перед освобождением сцена снимается с обработчика ввода, если она всё
     * ещё установлена. Повторный вызов безопасен для локальных ресурсов.</p>
     */
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
