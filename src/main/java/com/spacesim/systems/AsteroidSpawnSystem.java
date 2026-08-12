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
import com.spacesim.model.AsteroidSpawnConfig;
import com.spacesim.model.AsteroidSpawnPoint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Поддерживает ограниченный набор конечных астероидных источников в разрешённых точках пояса.
 *
 * <p>При первом обновлении система создаёт {@link AsteroidSpawnConfig#getInitialCount()} источников.
 * Затем через каждый интервал пополнения восстанавливает число активных источников до
 * {@link AsteroidSpawnConfig#getTargetCount()}, не создавая два астероида в одной точке.
 * Выбор свободной точки и величины запаса воспроизводимы для одинаковой конфигурации и seed.</p>
 */
public final class AsteroidSpawnSystem extends EntitySystem {
    private static final double REFILL_TIME_EPSILON_SECONDS = 1e-6d;

    private final AsteroidSpawnConfig config;
    private final RandomGenerator random;
    private final ComponentMapper<AsteroidComponent> asteroidMapper =
            ComponentMapper.getFor(AsteroidComponent.class);

    private Engine engine;
    private ImmutableArray<Entity> asteroids;
    private boolean initialized;
    private double secondsSinceRefill;
    private long spawnSequence;
    private long spawnedAsteroidCount;

    /** Создаёт систему с генератором, инициализированным seed из конфигурации. */
    public AsteroidSpawnSystem(AsteroidSpawnConfig config) {
        this(config, new Random(requireConfig(config).getSeed()));
    }

    /** Конструктор с внедряемым генератором для детерминированных тестов. */
    AsteroidSpawnSystem(AsteroidSpawnConfig config, RandomGenerator random) {
        this.config = requireConfig(config);
        if (random == null) {
            throw new NullPointerException("Источник случайности не должен быть null");
        }
        this.random = random;
    }

    private static AsteroidSpawnConfig requireConfig(AsteroidSpawnConfig config) {
        if (config == null) {
            throw new NullPointerException("Конфигурация астероидного пояса не должна быть null");
        }
        return config;
    }

    @Override
    public void addedToEngine(Engine engine) {
        if (engine == null) {
            throw new NullPointerException("Ashley Engine не должен быть null");
        }
        this.engine = engine;
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
     * Неконечное или отрицательное время игнорируется; нулевой шаг всё равно выполняет первичное
     * заселение, что позволяет детерминированно построить стартовый мир до первого реального кадра.
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

    /** Общее число когда-либо созданных этой системой астероидов, включая уже истощённые. */
    public long getSpawnedAsteroidCount() {
        return spawnedAsteroidCount;
    }

    /** Создаёт новые источники до заданного общего количества либо пока не закончатся свободные точки. */
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

    /** Считает только непустые источники текущей ресурсной конфигурации. */
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

        return new Entity()
                .add(new IdentityComponent(
                        "Астероид " + point.id() + "-" + spawnSequence,
                        IdentityComponent.Kind.ASTEROID))
                .add(transform)
                .add(new AsteroidComponent(point.id(), config.getResourceItem(), resource));
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
