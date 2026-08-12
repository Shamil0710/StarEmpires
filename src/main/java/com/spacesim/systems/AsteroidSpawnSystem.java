package com.spacesim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.economy.EconomicLedger;
import com.spacesim.model.AsteroidSpawnConfig;
import com.spacesim.model.AsteroidSpawnPoint;

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
 * {@code RESOURCE_SOURCE}: именно появление конечного природного резервуара вводит новый товар в
 * физический ресурсный пул. Последующая добыча лишь переносит этот ресурс из астероида в трюм и
 * отдельным source не считается.</p>
 */
public final class AsteroidSpawnSystem extends EntitySystem {
    private static final double REFILL_TIME_EPSILON_SECONDS = 1e-6d;

    private final AsteroidSpawnConfig config;
    private final RandomGenerator random;
    private final EconomicLedger ledger;
    private final ComponentMapper<AsteroidComponent> asteroidMapper =
            ComponentMapper.getFor(AsteroidComponent.class);

    private Engine engine;
    private ImmutableArray<Entity> asteroids;
    private boolean initialized;
    private double secondsSinceRefill;
    private long spawnSequence;
    private long spawnedAsteroidCount;

    /**
     * Создаёт систему с RNG по seed конфигурации и собственным диагностическим ledger.
     *
     * @param config валидная конфигурация астероидного пояса
     */
    public AsteroidSpawnSystem(AsteroidSpawnConfig config) {
        this(config, new Random(requireConfig(config).getSeed()), new EconomicLedger());
    }

    /**
     * Создаёт систему с внешним RNG и собственным диагностическим ledger.
     *
     * @param config валидная конфигурация астероидного пояса
     * @param random источник случайности подсистемы
     */
    public AsteroidSpawnSystem(AsteroidSpawnConfig config, RandomGenerator random) {
        this(config, random, new EconomicLedger());
    }

    /**
     * Создаёт систему с внешним RNG и общим экономическим журналом игровой сессии.
     *
     * @param config валидная конфигурация астероидного пояса
     * @param random источник случайности подсистемы
     * @param ledger общий экономический журнал
     * @throws NullPointerException если обязательная зависимость не задана
     */
    public AsteroidSpawnSystem(
            AsteroidSpawnConfig config,
            RandomGenerator random,
            EconomicLedger ledger) {
        this.config = requireConfig(config);
        this.random = Objects.requireNonNull(random, "Источник случайности не должен быть null");
        this.ledger = Objects.requireNonNull(ledger, "EconomicLedger не задан");
    }

    private static AsteroidSpawnConfig requireConfig(AsteroidSpawnConfig config) {
        return Objects.requireNonNull(config, "Конфигурация астероидного пояса не должна быть null");
    }

    /** @return ledger, в который записываются resource-source операции появления астероидов */
    public EconomicLedger getLedger() {
        return ledger;
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
        long resource = randomResourceAmount();
        TransformComponent transform = new TransformComponent();
        transform.position.set(point.x(), point.y());
        spawnSequence++;
        if (spawnedAsteroidCount < Long.MAX_VALUE) {
            spawnedAsteroidCount++;
        }

        String asteroidName = "Астероид " + point.id() + "-" + spawnSequence;
        Entity entity = new Entity()
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
