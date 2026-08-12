package com.spacesim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.ReputationComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.constants.Constants;
import com.spacesim.controllers.TradeController;
import com.spacesim.model.ItemType;

/**
 * Управляет полным автономным циклом добычи конечных астероидных ресурсов.
 *
 * <p>Система обрабатывает только добывающие корабли с позицией и трюмом. Конечный автомат
 * последовательно ищет ближайший совместимый астероид, летит к нему, извлекает целые единицы
 * ресурса, возвращается к подходящему рынку и продаёт груз через {@link TradeController}:</p>
 * <pre>
 * SEARCHING -&gt; TRAVEL_TO_ASTEROID -&gt; MINING
 *     ^                                      |
 *     +----- UNLOADING &lt;- RETURNING_TO_BASE -+
 * </pre>
 * <p>Корабль возвращается после заполнения трюма, исчезновения либо истощения цели. Если база не
 * назначена, выбирается ближайшая активная станция с положительной закупочной ценой и свободным
 * местом. Успешная продажа атомарно переносит товар, начисляет
 * {@link MiningComponent#credits кредиты}, помечает рынок для пересчёта и увеличивает счётчик
 * доставки.</p>
 *
 * <p>Добыча ограничена одновременно остатком астероида, общей вместимостью трюма, диапазоном
 * {@code int} товарного запаса и диапазоном {@code long} статистики. Большой шаг времени
 * обрабатывается одной пакетной операцией. Дробный выпуск сохраняется только у текущего доступного
 * источника и сбрасывается при заполнении трюма либо смене цели, поэтому не создаёт скрытый ресурс.
 * Истощённая сущность удаляется из Ashley-движка сразу после последней фактической единицы.</p>
 */
public final class MiningSystem extends IteratingSystem {
    private static final double DISTANCE_EPSILON = 0.0001d;

    private final TradeController tradeController = new TradeController();
    private ImmutableArray<Entity> asteroids;
    private ImmutableArray<Entity> marketBases;

    private final ComponentMapper<ShipComponent> shipMapper =
            ComponentMapper.getFor(ShipComponent.class);
    private final ComponentMapper<MiningComponent> miningMapper =
            ComponentMapper.getFor(MiningComponent.class);
    private final ComponentMapper<InventoryComponent> inventoryMapper =
            ComponentMapper.getFor(InventoryComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper =
            ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<AsteroidComponent> asteroidMapper =
            ComponentMapper.getFor(AsteroidComponent.class);
    private final ComponentMapper<MarketComponent> marketMapper =
            ComponentMapper.getFor(MarketComponent.class);
    private final ComponentMapper<ReputationComponent> reputationMapper =
            ComponentMapper.getFor(ReputationComponent.class);
    private final ComponentMapper<TradeAIComponent> tradeAIMapper =
            ComponentMapper.getFor(TradeAIComponent.class);

    /**
     * Создаёт систему для кораблей с типом, добывающим оборудованием, позицией и трюмом.
     */
    public MiningSystem() {
        super(Family.all(
                ShipComponent.class,
                MiningComponent.class,
                InventoryComponent.class,
                TransformComponent.class).get());
    }

    /**
     * Подключает живые представления источников и потенциальных рынков разгрузки.
     *
     * @param engine движок Ashley, к которому добавлена система
     */
    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        asteroids = engine.getEntitiesFor(Family.all(
                AsteroidComponent.class,
                TransformComponent.class).get());
        marketBases = engine.getEntitiesFor(Family.all(
                MarketComponent.class,
                InventoryComponent.class,
                TransformComponent.class).get());
    }

    /**
     * Обновляет все добывающие корабли только для конечного неотрицательного времени.
     *
     * <p>Нулевой шаг разрешает дискретные переходы автомата, поиск цели и сделку разгрузки, но не
     * создаёт движение или добычу.</p>
     *
     * @param deltaTime прошедшее игровое время в секундах
     */
    @Override
    public void update(float deltaTime) {
        if (!Float.isFinite(deltaTime) || deltaTime < 0f) {
            return;
        }
        super.update(deltaTime);
    }

    /**
     * Выполняет текущий этап автономного цикла одного корабля.
     *
     * @param entity добывающий корабль с обязательными компонентами системы
     * @param deltaTime проверенное неотрицательное игровое время
     */
    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        ShipComponent ship = shipMapper.get(entity);
        MiningComponent mining = miningMapper.get(entity);
        InventoryComponent inventory = inventoryMapper.get(entity);
        TransformComponent transform = transformMapper.get(entity);

        if (!isValidConfiguration(ship, mining, inventory, transform)) {
            stop(transform);
            return;
        }
        if (!mining.active) {
            mining.state = MiningComponent.State.PAUSED;
            stop(transform);
            return;
        }
        if (mining.state == null || mining.state == MiningComponent.State.PAUSED) {
            mining.state = MiningComponent.State.SEARCHING;
            mining.targetAsteroid = null;
            mining.extractionRemainder = 0d;
        }

        TradeAIComponent tradeAI = tradeAIMapper.get(entity);
        if (tradeAI != null && tradeAI.state != TradeAIComponent.State.IDLE) {
            stop(transform);
            return;
        }

        switch (mining.state) {
            case SEARCHING -> searchForAsteroid(mining, inventory, transform);
            case TRAVEL_TO_ASTEROID -> travelToAsteroid(mining, inventory, transform, deltaTime);
            case MINING -> mineAsteroid(entity, mining, inventory, transform, deltaTime);
            case RETURNING_TO_BASE -> returnToBase(mining, inventory, transform, deltaTime);
            case UNLOADING -> unloadAtBase(entity, mining, inventory, transform);
            case PAUSED -> stop(transform);
        }
    }

    /** Выбирает ближайший источник либо начинает возврат с уже имеющимся грузом. */
    private void searchForAsteroid(
            MiningComponent mining,
            InventoryComponent inventory,
            TransformComponent transform) {
        stop(transform);
        int cargo = inventory.stock[mining.resourceItem];
        if (inventory.getFreeCapacity() <= 0) {
            if (cargo > 0) {
                beginReturn(mining);
            }
            return;
        }

        Entity target = findClosestAsteroid(transform, mining.resourceItem);
        if (target == null) {
            if (cargo > 0) {
                beginReturn(mining);
            }
            return;
        }
        mining.targetAsteroid = target;
        mining.extractionRemainder = 0d;
        mining.state = MiningComponent.State.TRAVEL_TO_ASTEROID;
    }

    /** Продвигает полёт к источнику и восстанавливается после исчезновения цели. */
    private void travelToAsteroid(
            MiningComponent mining,
            InventoryComponent inventory,
            TransformComponent transform,
            float deltaTime) {
        if (inventory.getFreeCapacity() <= 0) {
            beginReturn(mining);
            stop(transform);
            return;
        }
        if (!isUsableAsteroid(mining.targetAsteroid, mining.resourceItem)) {
            loseTarget(mining, inventory);
            stop(transform);
            return;
        }

        TransformComponent targetTransform = transformMapper.get(mining.targetAsteroid);
        if (moveWithinRange(
                transform,
                targetTransform,
                mining.movementSpeed,
                mining.extractionRange,
                deltaTime)) {
            mining.state = MiningComponent.State.MINING;
        }
    }

    /** Извлекает целые единицы из конечного запаса текущего астероида. */
    private void mineAsteroid(
            Entity miner,
            MiningComponent mining,
            InventoryComponent inventory,
            TransformComponent transform,
            float deltaTime) {
        Entity target = mining.targetAsteroid;
        if (!isUsableAsteroid(target, mining.resourceItem)) {
            loseTarget(mining, inventory);
            stop(transform);
            return;
        }

        TransformComponent targetTransform = transformMapper.get(target);
        if (!isWithinRange(transform, targetTransform, mining.extractionRange)) {
            mining.state = MiningComponent.State.TRAVEL_TO_ASTEROID;
            return;
        }
        stop(transform);

        int currentStock = inventory.stock[mining.resourceItem];
        int freeCapacity = inventory.getFreeCapacity();
        int itemCapacity = Integer.MAX_VALUE - currentStock;
        AsteroidComponent asteroid = asteroidMapper.get(target);
        long maximumExtraction = Math.min(
                Math.min((long) freeCapacity, itemCapacity),
                asteroid.remainingResource);
        if (maximumExtraction <= 0L) {
            mining.extractionRemainder = 0d;
            if (asteroid.isDepleted()) {
                removeDepletedTarget(mining, target);
            }
            if (currentStock > 0) {
                beginReturn(mining);
            } else {
                mining.state = MiningComponent.State.SEARCHING;
            }
            return;
        }

        double remainder = normalizedRemainder(mining.extractionRemainder);
        double produced = remainder + (double) mining.extractionPerSecond * deltaTime;
        if (!Double.isFinite(produced) || produced <= 0d) {
            mining.extractionRemainder = 0d;
            return;
        }

        double completedUnits = Math.floor(produced);
        if (completedUnits < 1d) {
            mining.extractionRemainder = produced;
            return;
        }

        int extracted = completedUnits >= maximumExtraction
                ? (int) maximumExtraction
                : (int) completedUnits;
        inventory.stock[mining.resourceItem] = currentStock + extracted;
        asteroid.remainingResource -= extracted;
        mining.totalMined = saturatedAdd(normalizedCounter(mining.totalMined), extracted);
        mining.extractionRemainder = completedUnits >= maximumExtraction
                ? 0d
                : produced - extracted;

        MarketComponent minerMarket = marketMapper.get(miner);
        if (minerMarket != null) {
            minerMarket.isDirty = true;
        }

        if (asteroid.isDepleted()) {
            removeDepletedTarget(mining, target);
            beginReturn(mining);
        } else if (inventory.getFreeCapacity() <= 0) {
            mining.extractionRemainder = 0d;
            beginReturn(mining);
        }
    }

    /** Выбирает пригодную базу и перемещает к ней корабль с грузом. */
    private void returnToBase(
            MiningComponent mining,
            InventoryComponent inventory,
            TransformComponent transform,
            float deltaTime) {
        stop(transform);
        if (inventory.stock[mining.resourceItem] <= 0) {
            finishDeliveryCycle(mining);
            return;
        }

        Entity base = resolveBase(mining, transform);
        if (base == null) {
            return;
        }
        if (moveWithinRange(
                transform,
                transformMapper.get(base),
                mining.movementSpeed,
                mining.dockingRange,
                deltaTime)) {
            mining.state = MiningComponent.State.UNLOADING;
        }
    }

    /** Продаёт максимально помещающуюся часть груза рынку и начинает следующий рейс. */
    private void unloadAtBase(
            Entity miner,
            MiningComponent mining,
            InventoryComponent inventory,
            TransformComponent transform) {
        stop(transform);
        int cargo = inventory.stock[mining.resourceItem];
        if (cargo <= 0) {
            finishDeliveryCycle(mining);
            return;
        }
        if (!isUsableBase(mining.homeBase, mining.resourceItem)
                || !isWithinRange(
                        transform,
                        transformMapper.get(mining.homeBase),
                        mining.dockingRange)) {
            mining.homeBase = null;
            mining.state = MiningComponent.State.RETURNING_TO_BASE;
            return;
        }

        InventoryComponent baseInventory = inventoryMapper.get(mining.homeBase);
        int amount = Math.min(cargo, baseInventory.getFreeCapacity());
        if (amount <= 0) {
            mining.homeBase = null;
            mining.state = MiningComponent.State.RETURNING_TO_BASE;
            return;
        }

        TradeController.CreditAccount account;
        try {
            account = new TradeController.CreditAccount(mining.credits);
        } catch (IllegalArgumentException exception) {
            return;
        }
        boolean sold = tradeController.sellToStation(
                mining.homeBase,
                inventory,
                mining.resourceItem,
                amount,
                account,
                reputationMapper.get(miner));
        if (!sold) {
            mining.homeBase = null;
            mining.state = MiningComponent.State.RETURNING_TO_BASE;
            return;
        }

        mining.credits = account.credits;
        mining.totalDelivered = saturatedAdd(
                normalizedCounter(mining.totalDelivered),
                amount);
        if (inventory.stock[mining.resourceItem] <= 0) {
            finishDeliveryCycle(mining);
        } else {
            mining.homeBase = null;
            mining.state = MiningComponent.State.RETURNING_TO_BASE;
        }
    }

    /** Возвращает подходящую сохранённую базу либо выбирает ближайший рынок. */
    private Entity resolveBase(MiningComponent mining, TransformComponent transform) {
        if (isUsableBase(mining.homeBase, mining.resourceItem)) {
            return mining.homeBase;
        }
        mining.homeBase = null;

        Entity closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (Entity candidate : marketBases) {
            if (!isUsableBase(candidate, mining.resourceItem)) {
                continue;
            }
            double distance = distanceSquared(
                    transform,
                    transformMapper.get(candidate));
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = candidate;
            }
        }
        mining.homeBase = closest;
        return closest;
    }

    /** Находит ближайший активный астероид с нужным товаром. */
    private Entity findClosestAsteroid(TransformComponent transform, int resourceItem) {
        Entity closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (Entity candidate : asteroids) {
            if (!isUsableAsteroid(candidate, resourceItem)) {
                continue;
            }
            double distance = distanceSquared(
                    transform,
                    transformMapper.get(candidate));
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = candidate;
            }
        }
        return closest;
    }

    /** Проверяет неизменяемую роль, численные настройки, склад и положение корабля. */
    private boolean isValidConfiguration(
            ShipComponent ship,
            MiningComponent mining,
            InventoryComponent inventory,
            TransformComponent transform) {
        ItemType resource = ItemType.fromId(mining.resourceItem);
        return ship.type != null
                && ship.type.isMining()
                && resource != null
                && resource.isMineable()
                && ship.type.canCarry(resource)
                && Float.isFinite(mining.extractionPerSecond)
                && mining.extractionPerSecond > 0f
                && Float.isFinite(mining.movementSpeed)
                && mining.movementSpeed >= 0f
                && Float.isFinite(mining.extractionRange)
                && mining.extractionRange >= 0f
                && Float.isFinite(mining.dockingRange)
                && mining.dockingRange >= 0f
                && Float.isFinite(mining.credits)
                && mining.credits >= 0f
                && isValidInventory(inventory)
                && isValidPosition(transform);
    }

    /** Проверяет источник, его присутствие в движке, товар, запас и положение. */
    private boolean isUsableAsteroid(Entity entity, int resourceItem) {
        if (entity == null
                || asteroids == null
                || !asteroids.contains(entity, true)
                || !asteroidMapper.has(entity)
                || !transformMapper.has(entity)) {
            return false;
        }
        AsteroidComponent asteroid = asteroidMapper.get(entity);
        return asteroid.resourceItem == resourceItem
                && !asteroid.isDepleted()
                && asteroid.remainingResource <= asteroid.initialResource
                && isValidPosition(transformMapper.get(entity));
    }

    /** Проверяет рынок разгрузки, цену, склад, положение и свободную вместимость. */
    private boolean isUsableBase(Entity entity, int resourceItem) {
        if (entity == null
                || marketBases == null
                || !marketBases.contains(entity, true)
                || !marketMapper.has(entity)
                || !inventoryMapper.has(entity)
                || !transformMapper.has(entity)) {
            return false;
        }
        MarketComponent market = marketMapper.get(entity);
        InventoryComponent inventory = inventoryMapper.get(entity);
        return isValidMarket(market)
                && resourceItem >= 0
                && resourceItem < Constants.MAX_ITEMS
                && market.isTradable(resourceItem)
                && Float.isFinite(market.buyPrices[resourceItem])
                && market.buyPrices[resourceItem] > 0f
                && inventory.getFreeCapacity() > 0
                && isValidInventory(inventory)
                && isValidPosition(transformMapper.get(entity));
    }

    /** Проверяет массивы рынка, которые читают выбор базы и торговый контроллер. */
    private boolean isValidMarket(MarketComponent market) {
        return market != null
                && market.buyPrices != null
                && market.buyPrices.length >= Constants.MAX_ITEMS
                && market.sellPrices != null
                && market.sellPrices.length >= Constants.MAX_ITEMS
                && market.targetStock != null
                && market.targetStock.length >= Constants.MAX_ITEMS
                && market.tradableItems != null
                && market.tradableItems.length >= Constants.MAX_ITEMS;
    }

    /** Проверяет полный инвариант открытого товарного массива. */
    private boolean isValidInventory(InventoryComponent inventory) {
        if (inventory == null || inventory.capacity < 0) {
            return false;
        }
        for (int stock : inventory.stock) {
            if (stock < 0) {
                return false;
            }
        }
        return inventory.getTotalStock() <= inventory.capacity;
    }

    /** Проверяет существование и конечность обоих векторов пространственного компонента. */
    private boolean isValidPosition(TransformComponent transform) {
        return transform != null
                && transform.position != null
                && transform.velocity != null
                && Float.isFinite(transform.position.x)
                && Float.isFinite(transform.position.y)
                && Float.isFinite(transform.velocity.x)
                && Float.isFinite(transform.velocity.y);
    }

    /** Перемещает объект к границе заданного радиуса и сообщает о прибытии. */
    private boolean moveWithinRange(
            TransformComponent moving,
            TransformComponent target,
            float speed,
            float range,
            float deltaTime) {
        double dx = (double) target.position.x - moving.position.x;
        double dy = (double) target.position.y - moving.position.y;
        double distance = Math.hypot(dx, dy);
        if (!Double.isFinite(distance)) {
            stop(moving);
            return false;
        }
        if (distance <= (double) range + DISTANCE_EPSILON) {
            stop(moving);
            return true;
        }

        double step = (double) speed * deltaTime;
        if (step <= 0d) {
            stop(moving);
            return false;
        }
        double distanceToBoundary = distance - range;
        double actualStep = Math.min(step, distanceToBoundary);
        double ratio = actualStep / distance;
        double newX = moving.position.x + dx * ratio;
        double newY = moving.position.y + dy * ratio;
        if (!isFloatRepresentable(newX) || !isFloatRepresentable(newY)) {
            stop(moving);
            return false;
        }
        moving.position.set((float) newX, (float) newY);

        double velocityX = dx / distance * speed;
        double velocityY = dy / distance * speed;
        if (isFloatRepresentable(velocityX) && isFloatRepresentable(velocityY)) {
            moving.velocity.set((float) velocityX, (float) velocityY);
        } else {
            stop(moving);
        }

        if (actualStep >= distanceToBoundary) {
            stop(moving);
            return true;
        }
        return false;
    }

    /** Проверяет, находится ли корабль в конечном радиусе цели. */
    private boolean isWithinRange(
            TransformComponent first,
            TransformComponent second,
            float range) {
        double distanceSquared = distanceSquared(first, second);
        double allowed = (double) range + DISTANCE_EPSILON;
        return distanceSquared <= allowed * allowed;
    }

    /** Возвращает квадрат евклидова расстояния либо бесконечность при переполнении. */
    private double distanceSquared(TransformComponent first, TransformComponent second) {
        double dx = (double) second.position.x - first.position.x;
        double dy = (double) second.position.y - first.position.y;
        double result = dx * dx + dy * dy;
        return Double.isFinite(result) ? result : Double.POSITIVE_INFINITY;
    }

    /** Начинает возврат и сбрасывает состояние, относящееся к прежнему источнику. */
    private void beginReturn(MiningComponent mining) {
        mining.targetAsteroid = null;
        mining.extractionRemainder = 0d;
        mining.state = MiningComponent.State.RETURNING_TO_BASE;
    }

    /** Восстанавливает автомат после исчезновения цели. */
    private void loseTarget(MiningComponent mining, InventoryComponent inventory) {
        mining.targetAsteroid = null;
        mining.extractionRemainder = 0d;
        mining.state = inventory.stock[mining.resourceItem] > 0
                ? MiningComponent.State.RETURNING_TO_BASE
                : MiningComponent.State.SEARCHING;
    }

    /** Удаляет истощённую сущность и очищает ссылку корабля. */
    private void removeDepletedTarget(MiningComponent mining, Entity target) {
        mining.targetAsteroid = null;
        if (getEngine() != null && target != null) {
            getEngine().removeEntity(target);
        }
    }

    /** Завершает доставку, сохраняя пригодную базу для следующего рейса. */
    private void finishDeliveryCycle(MiningComponent mining) {
        mining.targetAsteroid = null;
        mining.extractionRemainder = 0d;
        mining.state = MiningComponent.State.SEARCHING;
    }

    /** Нормализует дробный остаток добычи перед новым вычислением. */
    private double normalizedRemainder(double remainder) {
        if (!Double.isFinite(remainder) || remainder < 0d || remainder >= 1d) {
            return 0d;
        }
        return remainder;
    }

    /** Нормализует повреждённый отрицательный статистический счётчик. */
    private long normalizedCounter(long counter) {
        return Math.max(0L, counter);
    }

    /** Складывает статистику с насыщением на верхней границе {@code long}. */
    private long saturatedAdd(long counter, int amount) {
        if (Long.MAX_VALUE - counter < amount) {
            return Long.MAX_VALUE;
        }
        return counter + amount;
    }

    /** Останавливает отображаемую скорость, не меняя текущую позицию. */
    private void stop(TransformComponent transform) {
        if (transform != null && transform.velocity != null) {
            transform.velocity.setZero();
        }
    }

    /** Проверяет возможность точного сохранения вычисленного числа в {@code float}. */
    private boolean isFloatRepresentable(double value) {
        return Double.isFinite(value) && value >= -Float.MAX_VALUE && value <= Float.MAX_VALUE;
    }
}
