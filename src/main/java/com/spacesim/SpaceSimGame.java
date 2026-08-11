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

    private Engine engine;
    private GlobalEventManager eventManager;
    private SpatialHashGrid grid;

    private Stage stage;
    private NewsUI newsUI;
    private EconomyStatusUI economyStatusUI;
    private PriceGraphRenderer graphRenderer;
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
        newsUI = new NewsUI(skin);
        stage.addActor(newsUI);
        economyStatusUI = new EconomyStatusUI(skin);
        stage.addActor(economyStatusUI);
        graphRenderer = new PriceGraphRenderer();
        Gdx.input.setInputProcessor(stage);

        engine.addSystem(new MarketSystem(eventManager));
        engine.addSystem(new ConsumptionSystem(eventManager));
        engine.addSystem(new ProductionSystem());
        engine.addSystem(new TradeAISystem(grid));
        engine.addSystem(new PriceRecorderSystem());

        // Демонстрационная производственная станция с рудой и энергией.
        createProductionStation(100, 400, Constants.FACTION_MINERS);
        // Демонстрационная станция-продавец с избытком еды.
        createStation(100, 100, 1200, 500, 0.0f, Constants.FACTION_TRADE_LEAGUE);
        // Демонстрационная станция-покупатель с дефицитом еды.
        createStation(500, 100, 100, 1000, 5.0f, Constants.FACTION_NEUTRAL);
        // Демонстрационный торговый флот.
        createFleet(300, 300);

        economyStatusUI.update(engine.getEntities());
    }

    /**
     * Добавляет обычную торговую станцию с рынком продовольствия.
     *
     * <p>Если начальный запас превышает стандартную вместимость склада, она
     * увеличивается до величины запаса, чтобы сохранить инвариант инвентаря.</p>
     *
     * @param x координата станции по горизонтали
     * @param y координата станции по вертикали
     * @param foodStock начальный запас продовольствия
     * @param targetFoodStock целевой запас, относительно которого рынок формирует цену
     * @param foodConsumption расход продовольствия в единицах товара за секунду
     * @param factionId идентификатор фракции-владельца
     */
    private void createStation(float x, float y, int foodStock, int targetFoodStock, float foodConsumption, int factionId) {
        Entity e = new Entity();
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
     * @param x координата станции по горизонтали
     * @param y координата станции по вертикали
     * @param factionId идентификатор фракции-владельца
     */
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

    /**
     * Добавляет автономный торговый флот с пустым трюмом и стартовой репутацией.
     *
     * @param x начальная координата флота по горизонтали
     * @param y начальная координата флота по вертикали
     */
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

    /**
     * Выполняет один кадр симуляции и отрисовки.
     *
     * <p>Сначала обновляются глобальные события и Ashley-системы, затем Scene2D.
     * После отрисовки интерфейса поверх него строится демонстрационный график,
     * если первая сущность движка содержит историю цен.</p>
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
        }
        stage.act(delta);

        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.draw();

        // Демонстрационный график для первой сущности с историей цен.
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
        if (graphRenderer != null) {
            graphRenderer.dispose();
            graphRenderer = null;
        }
        if (VisUI.isLoaded()) {
            VisUI.dispose();
        }
    }
}
