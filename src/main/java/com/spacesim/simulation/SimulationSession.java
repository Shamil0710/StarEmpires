package com.spacesim.simulation;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoWorldFactory;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.constants.Constants;
import com.spacesim.economy.EconomicLedger;
import com.spacesim.events.GlobalEventManager;
import com.spacesim.model.AsteroidSpawnConfig;
import com.spacesim.persistence.EntityIdAllocator;
import com.spacesim.persistence.EntityRegistry;
import com.spacesim.persistence.EntityState;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.persistence.GameState;
import com.spacesim.systems.AsteroidSpawnSystem;
import com.spacesim.systems.ConsumptionSystem;
import com.spacesim.systems.MarketSystem;
import com.spacesim.systems.MiningSystem;
import com.spacesim.systems.PriceRecorderSystem;
import com.spacesim.systems.ProductionSystem;
import com.spacesim.systems.TradeAISystem;
import com.spacesim.util.SpatialHashGrid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Headless authoritative игровая сессия, владеющая всеми stateful-узлами симуляции.
 *
 * <p>Класс не создаёт OpenGL/Scene2D-ресурсов и потому подходит для save/load, continuation-тестов и
 * будущего benchmark runner. {@link #snapshot()} собирает полный {@link GameState};
 * {@link #restore(GameState)} создаёт новый Ashley Engine и новые runtime-сущности, сохраняя
 * persistent ID, RNG, системные таймеры и fixed-step accumulator.</p>
 */
public final class SimulationSession {
    /** Fixed tick демонстрационной экономики. */
    public static final float DEFAULT_FIXED_STEP_SECONDS = 0.1f;

    private final long rootSeed;
    private final Engine engine;
    private final SimulationRandom simulationRandom;
    private final StatefulRandom eventRandom;
    private final StatefulRandom asteroidRandom;
    private final GlobalEventManager eventManager;
    private final EconomicLedger ledger;
    private final EntityIdAllocator entityIdAllocator;
    private final EntityRegistry entityRegistry;
    private final AsteroidSpawnSystem asteroidSpawnSystem;
    private final PriceRecorderSystem priceRecorderSystem;
    private final SimulationClock clock;
    private final SimulationLoop loop;

    private SimulationSession(
            long rootSeed,
            Engine engine,
            SimulationRandom simulationRandom,
            StatefulRandom eventRandom,
            StatefulRandom asteroidRandom,
            GlobalEventManager eventManager,
            EconomicLedger ledger,
            EntityIdAllocator entityIdAllocator,
            EntityRegistry entityRegistry,
            AsteroidSpawnSystem asteroidSpawnSystem,
            PriceRecorderSystem priceRecorderSystem,
            SimulationClock clock) {
        this.rootSeed = rootSeed;
        this.engine = engine;
        this.simulationRandom = simulationRandom;
        this.eventRandom = eventRandom;
        this.asteroidRandom = asteroidRandom;
        this.eventManager = eventManager;
        this.ledger = ledger;
        this.entityIdAllocator = entityIdAllocator;
        this.entityRegistry = entityRegistry;
        this.asteroidSpawnSystem = asteroidSpawnSystem;
        this.priceRecorderSystem = priceRecorderSystem;
        this.clock = clock;
        this.loop = new SimulationLoop(clock, eventManager, engine);
    }

    /**
     * Создаёт новую демонстрационную headless-сессию от заданного root seed.
     *
     * @param rootSeed корневой seed deterministic simulation streams
     * @return полностью собранная новая сессия
     */
    public static SimulationSession createDemo(long rootSeed) {
        SimulationRandom random = new SimulationRandom(rootSeed);
        StatefulRandom eventRandom = random.createStream("economy-events");
        StatefulRandom asteroidRandom = random.createStream("asteroid-spawn");
        GlobalEventManager events = new GlobalEventManager(eventRandom);
        EconomicLedger ledger = new EconomicLedger();
        EntityIdAllocator ids = new EntityIdAllocator();
        EntityRegistry registry = new EntityRegistry();
        Engine engine = new Engine();
        registry.track(engine);
        AsteroidSpawnSystem spawner = new AsteroidSpawnSystem(
                AsteroidSpawnConfig.demoWorld(), asteroidRandom, ledger, ids);
        PriceRecorderSystem recorder = new PriceRecorderSystem();
        addSystems(engine, events, ledger, registry, spawner, recorder);
        for (Entity entity : DemoWorldFactory.createEntities(ids)) {
            engine.addEntity(entity);
        }
        return new SimulationSession(
                rootSeed,
                engine,
                random,
                eventRandom,
                asteroidRandom,
                events,
                ledger,
                ids,
                registry,
                spawner,
                recorder,
                new SimulationClock(DEFAULT_FIXED_STEP_SECONDS));
    }

    /**
     * Восстанавливает новую runtime-сессию из value-based snapshot.
     *
     * @param state сохранённое состояние текущей поддерживаемой версии
     * @return новая независимая сессия
     * @throws NullPointerException если state не задан
     * @throws IllegalArgumentException если версия или обязательное состояние некорректны
     */
    public static SimulationSession restore(GameState state) {
        GameState checked = Objects.requireNonNull(state, "GameState не задан");
        if (checked.schemaVersion() != GameState.CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Неподдерживаемая версия GameState: " + checked.schemaVersion());
        }
        if (checked.nextEntityIdValue() <= 0L) {
            throw new IllegalArgumentException("Следующий EntityId сохранения должен быть положительным");
        }

        SimulationRandom random = new SimulationRandom(checked.rootSeed());
        StatefulRandom eventRandom = random.restoreStream(checked.eventRandomState());
        StatefulRandom asteroidRandom = random.restoreStream(checked.asteroidRandomState());
        GlobalEventManager events = new GlobalEventManager(eventRandom,
                Objects.requireNonNull(checked.events(), "Состояние событий не задано"));
        EconomicLedger ledger = new EconomicLedger(
                Objects.requireNonNull(checked.ledger(), "Состояние ledger не задано"));
        EntityIdAllocator ids = new EntityIdAllocator(checked.nextEntityIdValue());
        EntityRegistry registry = new EntityRegistry();
        Engine engine = new Engine();
        registry.track(engine);
        AsteroidSpawnSystem spawner = new AsteroidSpawnSystem(
                AsteroidSpawnConfig.demoWorld(),
                asteroidRandom,
                ledger,
                ids,
                Objects.requireNonNull(checked.asteroidSpawner(), "Состояние spawner не задано"));
        PriceRecorderSystem recorder = new PriceRecorderSystem(
                Objects.requireNonNull(checked.priceRecorder(), "Состояние recorder не задано"));
        addSystems(engine, events, ledger, registry, spawner, recorder);

        List<EntityState> entityStates = Objects.requireNonNull(
                checked.entities(), "Список сущностей GameState не задан");
        for (EntityState entityState : entityStates) {
            engine.addEntity(EntityStateMapper.restore(entityState));
        }

        return new SimulationSession(
                checked.rootSeed(),
                engine,
                random,
                eventRandom,
                asteroidRandom,
                events,
                ledger,
                ids,
                registry,
                spawner,
                recorder,
                new SimulationClock(Objects.requireNonNull(checked.clock(), "Состояние clock не задано")));
    }

    private static void addSystems(
            Engine engine,
            GlobalEventManager events,
            EconomicLedger ledger,
            EntityRegistry registry,
            AsteroidSpawnSystem spawner,
            PriceRecorderSystem recorder) {
        engine.addSystem(new MarketSystem(events));
        engine.addSystem(new ConsumptionSystem(events, ledger));
        engine.addSystem(new ProductionSystem(ledger));
        engine.addSystem(spawner);
        engine.addSystem(new MiningSystem(ledger, registry));
        engine.addSystem(new TradeAISystem(
                new SpatialHashGrid(Constants.CELL_SIZE), ledger, registry));
        engine.addSystem(recorder);
    }

    /**
     * Продвигает сессию на один render-frame, выполняя доступные fixed ticks.
     *
     * @param realDeltaSeconds конечный неотрицательный render delta
     * @return число выполненных simulation ticks
     */
    public int advanceFrame(float realDeltaSeconds) {
        return loop.advanceFrame(realDeltaSeconds);
    }

    /**
     * Собирает полный value-based snapshot текущей сессии.
     *
     * @return immutable состояние текущей persistent schema
     */
    public GameState snapshot() {
        List<Entity> entities = new ArrayList<>();
        for (Entity entity : engine.getEntities()) {
            EntityIdComponent id = entity.getComponent(EntityIdComponent.class);
            if (id == null) {
                throw new IllegalStateException("Runtime Entity без EntityId нельзя сохранить");
            }
            entities.add(entity);
        }
        entities.sort(Comparator.comparingLong(entity ->
                entity.getComponent(EntityIdComponent.class).id.value()));

        List<EntityState> states = new ArrayList<>(entities.size());
        for (Entity entity : entities) {
            states.add(EntityStateMapper.capture(entity));
        }
        return new GameState(
                GameState.CURRENT_VERSION,
                rootSeed,
                clock.snapshotState(),
                entityIdAllocator.getNextValue(),
                eventRandom.getState(),
                asteroidRandom.getState(),
                eventManager.snapshotState(),
                asteroidSpawnSystem.snapshotState(),
                priceRecorderSystem.snapshotState(),
                ledger.snapshotState(),
                List.copyOf(states));
    }

    /** @return Ashley Engine текущей headless-сессии для read-only тестовой диагностики */
    public Engine getEngine() {
        return engine;
    }

    /** @return fixed-step часы текущей сессии */
    public SimulationClock getClock() {
        return clock;
    }

    /** @return общий ledger текущей сессии */
    public EconomicLedger getLedger() {
        return ledger;
    }

    /** @return общий registry текущей сессии */
    public EntityRegistry getEntityRegistry() {
        return entityRegistry;
    }

    /** @return deterministic RNG service текущей сессии */
    public SimulationRandom getSimulationRandom() {
        return simulationRandom;
    }
}
