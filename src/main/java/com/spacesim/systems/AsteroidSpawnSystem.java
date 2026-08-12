package com.spacesim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.economy.EconomicLedger;
import com.spacesim.model.AsteroidSpawnConfig;
import com.spacesim.model.AsteroidSpawnPoint;
import com.spacesim.persistence.EntityIdAllocator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Поддерживает ограниченный набор конечных астероидных источников в разрешённых точках пояса.
 *
 * <p>Каждый созданный астероид объявляется в {@link EconomicLedger} как явный
 * {@code RESOURCE_SOURCE}. Внутренний refill timer и sequence являются частью сохраняемого
 * {@link State}; состояние RNG-потока и {@link EntityIdAllocator} сохраняются владельцем игровой
 * сессии отдельно.</p>
 */
public final class AsteroidSpawnSystem extends EntitySystem {
    private static final double REFILL_TIME_EPSILON_SECONDS = 1e-6d;

    /**
     * Полный mutable state spawner, не включая внешние RNG и ID allocator.
     *
     * @param initialized выполнено ли первоначальное заполнение
     * @param secondsSinceRefill накопленное время после последнего refill boundary
     * @param spawnSequence номер последнего созданного астероида
     * @param spawnedAsteroidCount общее число когда-либо созданных астероидов
     */
    public record State(
            boolean initialized,
            double secondsSinceRefill,
            long spawnSequence,
            long spawnedAsteroidCount) {
        /**
         * Проверяет численные инварианты сохраняемого состояния.
         *
         * @param initialized выполнено ли первоначальное заполнение
         * @param secondsSinceRefill накопленное время refill
         * @param spawnSequence номер последнего астероида
         * @param spawnedAsteroidCount общее число созданных астероидов
         */
        public State {
            if (!Double.isFinite(secondsSinceRefill) || secondsSinceRefill < 0d) {
                throw new IllegalArgumentException("Refill timer состояния должен быть неотрицательным");
            }
            if (spawnSequence < 0L || spawnedAsteroidCount < 0L) {
                throw new IllegalArgumentException("Счётчики asteroid spawner не могут быть отрицательными");
            }
            if (spawnedAsteroidCount < spawnSequence) {
                throw new IllegalArgumentException("Общий счётчик не может быть меньше spawn sequence");
            }
        }
    }

    private final AsteroidSpawnConfig config;
    private final RandomGenerator random;
    private final EconomicLedger ledger;
    private final EntityIdAllocator idAllocator;
    private final ComponentMapper<AsteroidComponent> asteroidMapper =
            ComponentMapper.getFor(AsteroidComponent.class);

    private Engine engine;
    private ImmutableArray<Entity> asteroids;
    private boolean initialized;
    private double secondsSinceRefill;
    private long spawnSequence;
    private long spawnedAsteroidCount;

    /**
     * Создаёт систему с RNG по seed конфигурации, собственным ledger и собственной ID-последовательностью.
     *
     * @param config валидная конфигурация астероидного пояса
     */
    public AsteroidSpawnSystem(AsteroidSpawnConfig config) {
        this(config, new Random(requireConfig(config).getSeed()), new EconomicLedger(),
                new EntityIdAllocator());
    }

    /**
     * Создаёт систему с внешним RNG, собственным ledger и собственной ID-последовательностью.
     *
     * @param config валидная конфигурация астероидного пояса
     * @param random источник случайности подсистемы
     */
    public AsteroidSpawnSystem(AsteroidSpawnConfig config, RandomGenerator random) {
        this(config, random, new EconomicLedger(), new EntityIdAllocator());
    }

    /**
     * Создаёт систему с внешним RNG, общим экономическим журналом и собственной ID-последовательностью.
     *
     * @param config валидная конфигурация астероидного пояса
     * @param random источник случайности подсистемы
     * @param ledger общий экономический журнал
     */
    public AsteroidSpawnSystem(
            AsteroidSpawnConfig config,
            RandomGenerator random,
            EconomicLedger ledger) {
        this(config, random, ledger, new EntityIdAllocator());
    }

    /**
     * Создаёт систему с внешним RNG, общим ledger и общей persistent ID-последовательностью сессии.
     *
     * @param config валидная конфигурация астероидного пояса
     * @param random источник случайности подсистемы
     * @param ledger общий экономический журнал
     * @param idAllocator общий детерминированный аллокатор ID
     */
    public AsteroidSpawnSystem(
            AsteroidSpawnConfig config,
            RandomGenerator random,
            EconomicLedger ledger,
            EntityIdAllocator idAllocator) {
        this(config, random, ledger, idAllocator, new State(false, 0d, 0L, 0L));
    }

    /**
     * Восстанавливает spawner без дополнительного RNG-вызова или создания сущностей в конструкторе.
     *
     * @param config конфигурация астероидного пояса
     * @param random RNG-поток в сохранённом состоянии
     * @param ledger общий экономический журнал
     * @param idAllocator восстановленный общий ID allocator
     * @param state сохранённый mutable state системы
     */
    public AsteroidSpawnSystem(
            AsteroidSpawnConfig config,
            RandomGenerator random,
            EconomicLedger ledger,
            EntityIdAllocator idAllocator,
            State state) {
        this.config = requireConfig(config);
        this.random = Objects.requireNonNull(random, "Источник случайности не должен быть null");
        this.ledger = Objects.requireNonNull(ledger, "EconomicLedger не задан");
        this.idAllocator = Objects.requireNonNull(idAllocator, "EntityIdAllocator не задан");
        State checked = Objects.requireNonNull(state, "Состояние asteroid spawner не задано");
        if (checked.secondsSinceRefill() >= config.getRefillIntervalSeconds()
                + REFILL_TIME_EPSILON_SECONDS) {
            throw new IllegalArgumentException("Сохранённый refill timer превышает interval");
        }
        initialized = checked.initialized();
        secondsSinceRefill = checked.secondsSinceRefill();
        spawnSequence = checked.spawnSequence();
        spawnedAsteroidCount = checked.spawnedAsteroidCount();
    }

    private static AsteroidSpawnConfig requireConfig(AsteroidSpawnConfig config) {
        return Objects.requireNonNull(config, "Конфигурация астероидного пояса не должна быть null");
    }

    /** @return ledger, в который записываются resource-source операции появления астероидов */
    public EconomicLedger getLedger() {
        return ledger;
    }

    /** @return immutable снимок внутренних таймеров и счётчиков */
    public State snapshotState() {
        return new State(initialized, secondsSinceRefill, spawnSequence, spawnedAsteroidCount);
    }

    @Override
    public void addedToEngine(Engine engine) {
        this.engine = Objects.requireNonNull(engine, "Ashley Engine не должен быть null");
        asteroids = engine.getEntitiesFor(Family.all(
                AsteroidComponent.class,
                TransformComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        this.engine = null;
        asteroids = null;
    }

    /**
     * Продвигает расписание появления источников.
     *
     * @param deltaTime прошедшее игровое время в секундах
     */
    @Override
    public void update(float deltaTime) {
        if (engine == null || !Float.isFinite(deltaTime) || deltaTime < 0f) {
            return;
        }

        if (!initialized) {
            spawnUntil(config.getInitialCount());
            initialized = true;
        }
        if (deltaTime <= 0f) {
            return;
        }

        secondsSinceRefill += deltaTime;
        double interval = config.getRefillIntervalSeconds();
        double completedIntervals = Math.floor(
                (secondsSinceRefill + REFILL_TIME_EPSILON_SECONDS) / interval);
        if (completedIntervals < 1d) {
            return;
        }

        secondsSinceRefill -= completedIntervals * interval;
        if (secondsSinceRefill < 0d
                && secondsSinceRefill > -REFILL_TIME_EPSILON_SECONDS) {
            secondsSinceRefill = 0d;
        }
        spawnUntil(config.getTargetCount());
    }

    /** @return общее число когда-либо созданных этой системой астероидов */
    public long getSpawnedAsteroidCount() {
        return spawnedAsteroidCount;
    }

    private void spawnUntil(int desiredCount) {
        int activeCount = countUsableAsteroids();
        if (activeCount >= desiredCount) {
            return;
        }

        Set<String> occupiedPointIds = new HashSet<>();
        if (asteroids != null) {
            for (Entity entity : asteroids) {
                AsteroidComponent asteroid = asteroidMapper.get(entity);
                if (asteroid != null && !asteroid.isDepleted()) {
                    occupiedPointIds.add(asteroid.spawnPointId);
                }
            }
        }

        List<AsteroidSpawnPoint> freePoints = new ArrayList<>();
        for (AsteroidSpawnPoint point : config.getSpawnPoints()) {
            if (!occupiedPointIds.contains(point.id())) {
                freePoints.add(point);
            }
        }

        int toSpawn = Math.min(desiredCount - activeCount, freePoints.size());
        for (int index = 0; index < toSpawn; index++) {
            int pointIndex = random.nextInt(freePoints.size());
            AsteroidSpawnPoint point = freePoints.remove(pointIndex);
            engine.addEntity(createAsteroid(point));
        }
    }

    private int countUsableAsteroids() {
        if (asteroids == null) {
            return 0;
        }
        int count = 0;
        for (Entity entity : asteroids) {
            AsteroidComponent asteroid = asteroidMapper.get(entity);
            if (asteroid != null
                    && asteroid.resourceItem == config.getResourceItem()
                    && !asteroid.isDepleted()) {
                count++;
            }
        }
        return count;
    }

    private Entity createAsteroid(AsteroidSpawnPoint point) {
        if (spawnSequence == Long.MAX_VALUE) {
            throw new IllegalStateException("Диапазон sequence asteroid spawner исчерпан");
        }
        long resource = randomResourceAmount();
        TransformComponent transform = new TransformComponent();
        transform.position.set(point.x(), point.y());
        spawnSequence++;
        if (spawnedAsteroidCount < Long.MAX_VALUE) {
            spawnedAsteroidCount++;
        }

        String asteroidName = "Астероид " + point.id() + "-" + spawnSequence;
        Entity entity = new Entity()
                .add(new EntityIdComponent(idAllocator.allocate()))
                .add(new IdentityComponent(asteroidName, IdentityComponent.Kind.ASTEROID))
                .add(transform)
                .add(new AsteroidComponent(point.id(), config.getResourceItem(), resource));
        ledger.recordResourceSource(
                asteroidName,
                config.getResourceItem(),
                resource,
                "asteroid-spawn");
        return entity;
    }

    private long randomResourceAmount() {
        long min = config.getMinResource();
        long max = config.getMaxResource();
        if (min == max) {
            return min;
        }
        return random.nextLong(min, max + 1L);
    }
}
