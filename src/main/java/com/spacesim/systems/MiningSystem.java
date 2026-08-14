package com.spacesim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.MiningCommandComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.PlayerControlledComponent;
import com.spacesim.components.ReputationComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.controllers.TradeController;
import com.spacesim.economy.EconomicLedger;
import com.spacesim.economy.Money;
import com.spacesim.model.ItemType;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityRegistry;

import java.util.Objects;

/**
 * Управляет автономной и командной добычей конечных астероидных ресурсов.
 *
 * <p>Persistent-состояние {@link MiningComponent} хранит цели только как устойчивые
 * {@link EntityId}. Перед движением, добычей и разгрузкой система разрешает их через
 * {@link EntityRegistry}. Исчезнувший астероид или рынок поэтому безопасно инвалидирует ссылку без
 * сохранения runtime-объекта Ashley.</p>
 *
 * <p>Legacy autonomous mode сохраняет исторический цикл: поиск, полёт, физическая добыча,
 * возвращение и продажа через {@link TradeController}. Command mode активируется наличием
 * {@link MiningCommandComponent}: Stage 14A использует его на directly controlled ship, а Stage 15
 * — на delegated owned fleet. В command mode система никогда не двигает корабль и не продаёт груз
 * автоматически; она только валидирует цель и применяет тот же physical extraction boundary.</p>
 *
 * <p>Добыча сама не является resource source: сумма {@code asteroid.remainingResource + cargo}
 * сохраняется. Деньги возникают у добытчика только после обычной рыночной продажи.</p>
 */
public final class MiningSystem extends IteratingSystem {
    private static final double DISTANCE_EPSILON = 0.0001d;

    private final TradeController tradeController;
    private final EntityRegistry registry;
    private ImmutableArray<Entity> asteroids;
    private ImmutableArray<Entity> marketBases;

    private final ComponentMapper<ShipComponent> shipMapper = ComponentMapper.getFor(ShipComponent.class);
    private final ComponentMapper<MiningComponent> miningMapper = ComponentMapper.getFor(MiningComponent.class);
    private final ComponentMapper<MiningCommandComponent> miningCommandMapper =
            ComponentMapper.getFor(MiningCommandComponent.class);
    private final ComponentMapper<PlayerControlledComponent> playerControlledMapper =
            ComponentMapper.getFor(PlayerControlledComponent.class);
    private final ComponentMapper<InventoryComponent> inventoryMapper = ComponentMapper.getFor(InventoryComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper = ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<AsteroidComponent> asteroidMapper = ComponentMapper.getFor(AsteroidComponent.class);
    private final ComponentMapper<MarketComponent> marketMapper = ComponentMapper.getFor(MarketComponent.class);
    private final ComponentMapper<ReputationComponent> reputationMapper = ComponentMapper.getFor(ReputationComponent.class);
    private final ComponentMapper<TradeAIComponent> tradeAIMapper = ComponentMapper.getFor(TradeAIComponent.class);
    private final ComponentMapper<WalletComponent> walletMapper = ComponentMapper.getFor(WalletComponent.class);
    private final ComponentMapper<EntityIdComponent> idMapper = ComponentMapper.getFor(EntityIdComponent.class);

    /** Создаёт систему с собственными экономическим журналом и registry. */
    public MiningSystem() {
        this(new EconomicLedger(), new EntityRegistry());
    }

    /**
     * Создаёт систему с общим ledger и собственным registry.
     *
     * @param ledger общий экономический журнал
     * @throws NullPointerException если журнал не задан
     */
    public MiningSystem(EconomicLedger ledger) {
        this(ledger, new EntityRegistry());
    }

    /**
     * Создаёт систему с общими ledger и persistent-ID registry игровой сессии.
     *
     * @param ledger общий экономический журнал
     * @param registry runtime-индекс устойчивых EntityId
     * @throws NullPointerException если зависимость не задана
     */
    public MiningSystem(EconomicLedger ledger, EntityRegistry registry) {
        super(Family.all(
                EntityIdComponent.class,
                ShipComponent.class,
                MiningComponent.class,
                InventoryComponent.class,
                TransformComponent.class).get());
        tradeController = new TradeController(
                Objects.requireNonNull(ledger, "EconomicLedger не задан"));
        this.registry = Objects.requireNonNull(registry, "EntityRegistry не задан");
    }

    /** @return ledger, в который система записывает успешные автономные продажи */
    public EconomicLedger getLedger() {
        return tradeController.getLedger();
    }

    /**
     * Подключает registry и живые представления идентифицированных источников и рынков.
     *
     * @param engine движок Ashley, к которому добавлена система
     */
    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        registry.track(engine);
        asteroids = engine.getEntitiesFor(Family.all(
                EntityIdComponent.class,
                AsteroidComponent.class,
                TransformComponent.class).get());
        marketBases = engine.getEntitiesFor(Family.all(
                EntityIdComponent.class,
                MarketComponent.class,
                InventoryComponent.class,
                TransformComponent.class,
                WalletComponent.class).get());
    }

    /** Обновляет добывающие корабли только для конечного неотрицательного игрового времени. */
    @Override
    public void update(float deltaTime) {
        if (!Float.isFinite(deltaTime) || deltaTime < 0f) {
            return;
        }
        super.update(deltaTime);
    }

    /** Выполняет текущий командный либо legacy autonomous этап одного добывающего корабля. */
    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        ShipComponent ship = shipMapper.get(entity);
        MiningComponent mining = miningMapper.get(entity);
        InventoryComponent inventory = inventoryMapper.get(entity);
        TransformComponent transform = transformMapper.get(entity);
        PlayerControlledComponent playerControl = playerControlledMapper.get(entity);
        MiningCommandComponent command = miningCommandMapper.get(entity);

        if (playerControl != null || command != null) {
            processCommandMining(
                    entity,
                    ship,
                    mining,
                    inventory,
                    transform,
                    playerControl != null && playerControl.docked,
                    deltaTime);
            return;
        }

        WalletComponent wallet = walletMapper.get(entity);
        if (!isValidAutonomousConfiguration(ship, mining, inventory, transform, wallet)) {
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
            mining.targetAsteroidId = null;
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
            case RETURNING_TO_BASE -> returnToBase(entity, mining, inventory, transform, deltaTime);
            case UNLOADING -> unloadAtBase(entity, mining, inventory, transform);
            case PAUSED -> stop(transform);
        }
    }

    private void processCommandMining(
            Entity miner,
            ShipComponent ship,
            MiningComponent mining,
            InventoryComponent inventory,
            TransformComponent transform,
            boolean docked,
            float deltaTime) {
        MiningCommandComponent command = miningCommandMapper.get(miner);
        if (command == null) {
            return;
        }
        command.extractedLastTick = 0;
        mining.active = false;
        mining.state = MiningComponent.State.PAUSED;

        if (docked) {
            command.status = MiningCommandComponent.Status.DOCKED;
            return;
        }
        if (!isMiningRoleCompatible(ship, mining)) {
            command.status = MiningCommandComponent.Status.INCOMPATIBLE_SHIP;
            return;
        }
        if (!isValidMiningConfiguration(ship, mining, inventory, transform)) {
            command.status = MiningCommandComponent.Status.INVALID_CONFIGURATION;
            return;
        }
        if (command.targetAsteroidId == null) {
            command.status = command.miningRequested
                    ? MiningCommandComponent.Status.NO_TARGET
                    : MiningCommandComponent.Status.IDLE;
            return;
        }

        Entity target = registry.find(command.targetAsteroidId);
        if (!Objects.equals(mining.targetAsteroidId, command.targetAsteroidId)) {
            mining.targetAsteroidId = command.targetAsteroidId;
            mining.extractionRemainder = 0d;
        }

        if (!command.miningRequested) {
            if (command.status == MiningCommandComponent.Status.DEPLETED && target == null) {
                return;
            }
            command.status = toManualStatus(
                    validateExtractionTarget(mining, inventory, transform, target), false);
            return;
        }

        ExtractionResult result = extractResource(
                miner, mining, inventory, transform, target, deltaTime);
        command.extractedLastTick = result.extracted();
        command.status = toManualStatus(result.status(), true);

        if (result.depletedAfterExtraction() || result.status() == ExtractionStatus.DEPLETED) {
            command.status = MiningCommandComponent.Status.DEPLETED;
            command.miningRequested = false;
            mining.extractionRemainder = 0d;
            if (target != null && asteroidMapper.has(target)
                    && asteroidMapper.get(target).isDepleted()) {
                removeDepletedTarget(mining, target);
            }
        }
    }

    private MiningCommandComponent.Status toManualStatus(
            ExtractionStatus status,
            boolean requested) {
        return switch (status) {
            case VALID, WAITING, EXTRACTED -> requested
                    ? MiningCommandComponent.Status.MINING
                    : MiningCommandComponent.Status.READY;
            case INVALID_TARGET -> MiningCommandComponent.Status.INVALID_TARGET;
            case INVALID_RESOURCE -> MiningCommandComponent.Status.INVALID_RESOURCE;
            case OUT_OF_RANGE -> MiningCommandComponent.Status.OUT_OF_RANGE;
            case CARGO_FULL -> MiningCommandComponent.Status.CARGO_FULL;
            case DEPLETED -> MiningCommandComponent.Status.DEPLETED;
            case INVALID_CONFIGURATION -> MiningCommandComponent.Status.INVALID_CONFIGURATION;
        };
    }

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
        mining.targetAsteroidId = idOf(target);
        mining.extractionRemainder = 0d;
        mining.state = MiningComponent.State.TRAVEL_TO_ASTEROID;
    }

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
        Entity target = registry.find(mining.targetAsteroidId);
        if (!isUsableAsteroid(target, mining.resourceItem)) {
            loseTarget(mining, inventory);
            stop(transform);
            return;
        }

        TransformComponent targetTransform = transformMapper.get(target);
        if (moveWithinRange(
                transform,
                targetTransform,
                mining.movementSpeed,
                mining.extractionRange,
                deltaTime)) {
            mining.state = MiningComponent.State.MINING;
        }
    }

    /** Переносит целые единицы из конечного запаса астероида в трюм. */
    private void mineAsteroid(
            Entity miner,
            MiningComponent mining,
            InventoryComponent inventory,
            TransformComponent transform,
            float deltaTime) {
        Entity target = registry.find(mining.targetAsteroidId);
        ExtractionResult result = extractResource(
                miner, mining, inventory, transform, target, deltaTime);

        switch (result.status()) {
            case INVALID_TARGET, INVALID_RESOURCE -> {
                loseTarget(mining, inventory);
                stop(transform);
                return;
            }
            case DEPLETED -> {
                if (target != null && asteroidMapper.has(target)) {
                    removeDepletedTarget(mining, target);
                }
                mining.extractionRemainder = 0d;
                if (inventory.stock[mining.resourceItem] > 0) {
                    beginReturn(mining);
                } else {
                    mining.state = MiningComponent.State.SEARCHING;
                }
                stop(transform);
                return;
            }
            case OUT_OF_RANGE -> {
                mining.state = MiningComponent.State.TRAVEL_TO_ASTEROID;
                return;
            }
            case CARGO_FULL -> {
                mining.extractionRemainder = 0d;
                beginReturn(mining);
                stop(transform);
                return;
            }
            case INVALID_CONFIGURATION -> {
                stop(transform);
                return;
            }
            case VALID, WAITING, EXTRACTED -> stop(transform);
        }

        if (result.depletedAfterExtraction()) {
            removeDepletedTarget(mining, target);
            beginReturn(mining);
        } else if (inventory.getFreeCapacity() <= 0) {
            mining.extractionRemainder = 0d;
            beginReturn(mining);
        }
    }

    private ExtractionResult extractResource(
            Entity miner,
            MiningComponent mining,
            InventoryComponent inventory,
            TransformComponent transform,
            Entity target,
            float deltaTime) {
        ExtractionStatus validation = validateExtractionTarget(
                mining, inventory, transform, target);
        if (validation != ExtractionStatus.VALID) {
            return new ExtractionResult(validation, 0, false);
        }
        if (!Float.isFinite(deltaTime) || deltaTime < 0f) {
            return new ExtractionResult(ExtractionStatus.INVALID_CONFIGURATION, 0, false);
        }

        int currentStock = inventory.stock[mining.resourceItem];
        int freeCapacity = inventory.getFreeCapacity();
        int itemCapacity = Integer.MAX_VALUE - currentStock;
        AsteroidComponent asteroid = asteroidMapper.get(target);
        long maximumExtraction = Math.min(
                Math.min((long) freeCapacity, itemCapacity),
                asteroid.remainingResource);
        if (maximumExtraction <= 0L) {
            return new ExtractionResult(
                    asteroid.isDepleted() ? ExtractionStatus.DEPLETED : ExtractionStatus.CARGO_FULL,
                    0,
                    false);
        }

        double remainder = normalizedRemainder(mining.extractionRemainder);
        double produced = remainder + (double) mining.extractionPerSecond * deltaTime;
        if (!Double.isFinite(produced) || produced < 0d) {
            mining.extractionRemainder = 0d;
            return new ExtractionResult(ExtractionStatus.INVALID_CONFIGURATION, 0, false);
        }

        double completedUnits = Math.floor(produced);
        if (completedUnits < 1d) {
            mining.extractionRemainder = produced;
            return new ExtractionResult(ExtractionStatus.WAITING, 0, false);
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
        return new ExtractionResult(ExtractionStatus.EXTRACTED, extracted, asteroid.isDepleted());
    }

    private ExtractionStatus validateExtractionTarget(
            MiningComponent mining,
            InventoryComponent inventory,
            TransformComponent transform,
            Entity target) {
        if (target == null
                || asteroids == null
                || !asteroids.contains(target, true)
                || !idMapper.has(target)
                || !asteroidMapper.has(target)
                || !transformMapper.has(target)) {
            return ExtractionStatus.INVALID_TARGET;
        }
        AsteroidComponent asteroid = asteroidMapper.get(target);
        if (asteroid.resourceItem != mining.resourceItem) {
            return ExtractionStatus.INVALID_RESOURCE;
        }
        if (asteroid.isDepleted()) {
            return ExtractionStatus.DEPLETED;
        }
        if (asteroid.initialResource <= 0L || asteroid.remainingResource > asteroid.initialResource
                || !isValidPosition(transformMapper.get(target))) {
            return ExtractionStatus.INVALID_TARGET;
        }
        if (!isWithinRange(transform, transformMapper.get(target), mining.extractionRange)) {
            return ExtractionStatus.OUT_OF_RANGE;
        }
        if (inventory.getFreeCapacity() <= 0
                || inventory.stock[mining.resourceItem] == Integer.MAX_VALUE) {
            return ExtractionStatus.CARGO_FULL;
        }
        return ExtractionStatus.VALID;
    }

    private void returnToBase(
            Entity miner,
            MiningComponent mining,
            InventoryComponent inventory,
            TransformComponent transform,
            float deltaTime) {
        stop(transform);
        if (inventory.stock[mining.resourceItem] <= 0) {
            finishDeliveryCycle(mining);
            return;
        }

        Entity base = resolveBase(miner, mining, transform);
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

    /** Продаёт максимально оплачиваемую и помещающуюся часть груза через общий trade controller. */
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

        ReputationComponent reputation = reputationMapper.get(miner);
        Entity base = registry.find(mining.homeBaseId);
        if (!isUsableBase(base, miner, mining.resourceItem, reputation)
                || !isWithinRange(
                        transform,
                        transformMapper.get(base),
                        mining.dockingRange)) {
            mining.homeBaseId = null;
            mining.state = MiningComponent.State.RETURNING_TO_BASE;
            return;
        }

        InventoryComponent baseInventory = inventoryMapper.get(base);
        float salePrice = tradeController.getEffectiveBuyPrice(
                base,
                mining.resourceItem,
                reputation);
        int amount = Math.min(cargo, baseInventory.getFreeCapacity());
        amount = Math.min(amount, maximumBasePurchaseAmount(
                base,
                miner,
                salePrice,
                amount));
        if (amount <= 0 || !tradeController.sellToStation(
                base,
                miner,
                mining.resourceItem,
                amount,
                reputation)) {
            mining.homeBaseId = null;
            mining.state = MiningComponent.State.RETURNING_TO_BASE;
            return;
        }

        mining.totalDelivered = saturatedAdd(normalizedCounter(mining.totalDelivered), amount);
        if (inventory.stock[mining.resourceItem] <= 0) {
            finishDeliveryCycle(mining);
        } else {
            mining.homeBaseId = null;
            mining.state = MiningComponent.State.RETURNING_TO_BASE;
        }
    }

    private Entity resolveBase(Entity miner, MiningComponent mining, TransformComponent transform) {
        ReputationComponent reputation = reputationMapper.get(miner);
        Entity savedBase = registry.find(mining.homeBaseId);
        if (isUsableBase(savedBase, miner, mining.resourceItem, reputation)) {
            return savedBase;
        }
        mining.homeBaseId = null;

        Entity closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        if (marketBases == null) {
            return null;
        }
        for (int index = 0; index < marketBases.size(); index++) {
            Entity candidate = marketBases.get(index);
            if (!isUsableBaseCandidate(candidate, miner, mining.resourceItem, reputation)) {
                continue;
            }
            double distance = distanceSquared(transform, transformMapper.get(candidate));
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = candidate;
            }
        }
        mining.homeBaseId = closest == null ? null : idOf(closest);
        return closest;
    }

    private Entity findClosestAsteroid(TransformComponent transform, int resourceItem) {
        Entity closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        if (asteroids == null) {
            return null;
        }
        for (int index = 0; index < asteroids.size(); index++) {
            Entity candidate = asteroids.get(index);
            if (!isUsableAsteroidCandidate(candidate, resourceItem)) {
                continue;
            }
            double distance = distanceSquared(transform, transformMapper.get(candidate));
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = candidate;
            }
        }
        return closest;
    }

    private EntityId idOf(Entity entity) {
        EntityIdComponent component = idMapper.get(entity);
        if (component == null) {
            throw new IllegalStateException("Экономическая Entity не имеет EntityIdComponent");
        }
        return component.id;
    }

    private boolean isValidAutonomousConfiguration(
            ShipComponent ship,
            MiningComponent mining,
            InventoryComponent inventory,
            TransformComponent transform,
            WalletComponent wallet) {
        return isValidMiningConfiguration(ship, mining, inventory, transform)
                && wallet != null
                && wallet.getBalanceMilliCredits() >= 0L;
    }

    private boolean isValidMiningConfiguration(
            ShipComponent ship,
            MiningComponent mining,
            InventoryComponent inventory,
            TransformComponent transform) {
        ItemType resource = ItemType.fromId(mining.resourceItem);
        return isMiningRoleCompatible(ship, mining)
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
                && isValidInventory(inventory)
                && isValidPosition(transform);
    }

    private boolean isMiningRoleCompatible(ShipComponent ship, MiningComponent mining) {
        return ship != null
                && ship.type != null
                && ship.type.isMining()
                && mining != null;
    }

    private boolean isUsableAsteroid(Entity entity, int resourceItem) {
        return entity != null
                && asteroids != null
                && asteroids.contains(entity, true)
                && isUsableAsteroidCandidate(entity, resourceItem);
    }

    private boolean isUsableAsteroidCandidate(Entity entity, int resourceItem) {
        if (entity == null
                || !idMapper.has(entity)
                || !asteroidMapper.has(entity)
                || !transformMapper.has(entity)) {
            return false;
        }
        AsteroidComponent asteroid = asteroidMapper.get(entity);
        return asteroid.resourceItem == resourceItem
                && !asteroid.isDepleted()
                && asteroid.initialResource > 0L
                && asteroid.remainingResource <= asteroid.initialResource
                && isValidPosition(transformMapper.get(entity));
    }

    private boolean isUsableBase(
            Entity entity,
            Entity miner,
            int resourceItem,
            ReputationComponent reputation) {
        return entity != null
                && marketBases != null
                && marketBases.contains(entity, true)
                && isUsableBaseCandidate(entity, miner, resourceItem, reputation);
    }

    private boolean isUsableBaseCandidate(
            Entity entity,
            Entity miner,
            int resourceItem,
            ReputationComponent reputation) {
        if (entity == null
                || entity == miner
                || !idMapper.has(entity)
                || !marketMapper.has(entity)
                || !inventoryMapper.has(entity)
                || !walletMapper.has(entity)
                || !transformMapper.has(entity)
                || !walletMapper.has(miner)) {
            return false;
        }
        MarketComponent market = marketMapper.get(entity);
        InventoryComponent inventory = inventoryMapper.get(entity);
        if (!isValidMarket(market)
                || resourceItem < 0
                || resourceItem >= Constants.MAX_ITEMS
                || !market.isTradable(resourceItem)
                || !hasValidFreeCapacity(inventory)
                || !isValidPosition(transformMapper.get(entity))) {
            return false;
        }
        float price = tradeController.getEffectiveBuyPrice(entity, resourceItem, reputation);
        return maximumBasePurchaseAmount(entity, miner, price, 1) >= 1;
    }

    private int maximumBasePurchaseAmount(Entity base, Entity miner, float price, int maximumAmount) {
        if (maximumAmount <= 0 || !Float.isFinite(price) || price <= 0f) {
            return 0;
        }
        WalletComponent baseWallet = walletMapper.get(base);
        WalletComponent minerWallet = walletMapper.get(miner);
        if (baseWallet == null || minerWallet == null) {
            return 0;
        }
        try {
            int payable = Money.maximumAffordable(
                    baseWallet.getBalanceMilliCredits(),
                    price,
                    maximumAmount);
            int receivable = Money.maximumAffordable(
                    Long.MAX_VALUE - minerWallet.getBalanceMilliCredits(),
                    price,
                    maximumAmount);
            return Math.min(payable, receivable);
        } catch (IllegalArgumentException exception) {
            return 0;
        }
    }

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

    private boolean isValidInventory(InventoryComponent inventory) {
        if (inventory == null || inventory.capacity < 0) {
            return false;
        }
        long total = 0L;
        boolean saturated = false;
        for (int stock : inventory.stock) {
            if (stock < 0) {
                return false;
            }
            if (!saturated) {
                total += stock;
                if (total >= Integer.MAX_VALUE) {
                    total = Integer.MAX_VALUE;
                    saturated = true;
                }
            }
        }
        return total <= inventory.capacity;
    }

    private boolean hasValidFreeCapacity(InventoryComponent inventory) {
        if (inventory == null || inventory.capacity <= 0) {
            return false;
        }
        long total = 0L;
        for (int stock : inventory.stock) {
            if (stock < 0) {
                return false;
            }
            total += stock;
            if (total >= inventory.capacity) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidPosition(TransformComponent transform) {
        return transform != null
                && transform.position != null
                && transform.velocity != null
                && Float.isFinite(transform.position.x)
                && Float.isFinite(transform.position.y)
                && Float.isFinite(transform.velocity.x)
                && Float.isFinite(transform.velocity.y);
    }

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

    private boolean isWithinRange(
            TransformComponent first,
            TransformComponent second,
            float range) {
        double distanceSquared = distanceSquared(first, second);
        double allowed = (double) range + DISTANCE_EPSILON;
        return distanceSquared <= allowed * allowed;
    }

    private double distanceSquared(TransformComponent first, TransformComponent second) {
        double dx = (double) second.position.x - first.position.x;
        double dy = (double) second.position.y - first.position.y;
        double result = dx * dx + dy * dy;
        return Double.isFinite(result) ? result : Double.POSITIVE_INFINITY;
    }

    private void beginReturn(MiningComponent mining) {
        mining.targetAsteroidId = null;
        mining.extractionRemainder = 0d;
        mining.state = MiningComponent.State.RETURNING_TO_BASE;
    }

    private void loseTarget(MiningComponent mining, InventoryComponent inventory) {
        mining.targetAsteroidId = null;
        mining.extractionRemainder = 0d;
        mining.state = inventory.stock[mining.resourceItem] > 0
                ? MiningComponent.State.RETURNING_TO_BASE
                : MiningComponent.State.SEARCHING;
    }

    private void removeDepletedTarget(MiningComponent mining, Entity target) {
        mining.targetAsteroidId = null;
        if (getEngine() != null && target != null) {
            getEngine().removeEntity(target);
        }
    }

    private void finishDeliveryCycle(MiningComponent mining) {
        mining.targetAsteroidId = null;
        mining.extractionRemainder = 0d;
        mining.state = MiningComponent.State.SEARCHING;
    }

    private double normalizedRemainder(double remainder) {
        if (!Double.isFinite(remainder) || remainder < 0d || remainder >= 1d) {
            return 0d;
        }
        return remainder;
    }

    private long normalizedCounter(long counter) {
        return Math.max(0L, counter);
    }

    private long saturatedAdd(long counter, int amount) {
        if (Long.MAX_VALUE - counter < amount) {
            return Long.MAX_VALUE;
        }
        return counter + amount;
    }

    private void stop(TransformComponent transform) {
        if (transform != null && transform.velocity != null) {
            transform.velocity.setZero();
        }
    }

    private boolean isFloatRepresentable(double value) {
        return Double.isFinite(value) && value >= -Float.MAX_VALUE && value <= Float.MAX_VALUE;
    }

    private enum ExtractionStatus {
        VALID,
        WAITING,
        EXTRACTED,
        INVALID_TARGET,
        INVALID_RESOURCE,
        OUT_OF_RANGE,
        CARGO_FULL,
        DEPLETED,
        INVALID_CONFIGURATION
    }

    private record ExtractionResult(
            ExtractionStatus status,
            int extracted,
            boolean depletedAfterExtraction) {
    }
}
