package com.spacesim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ProcurementPolicyComponent;
import com.spacesim.components.ReputationComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.controllers.TradeController;
import com.spacesim.economy.EconomicLedger;
import com.spacesim.economy.Money;
import com.spacesim.flight.InertialNavigation;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityRegistry;
import com.spacesim.trade.FleetTradeProfile;
import com.spacesim.trade.MarketDirectory;
import com.spacesim.trade.TradeRoute;
import com.spacesim.trade.TradeRoutePlanner;
import com.spacesim.trade.TradeSaleRoute;
import com.spacesim.util.SpatialHashGrid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Исполняет автономные торговые FSM поверх маршрутов, рассчитанных pure planner-слоем.
 *
 * <p>Все route decisions — как поиск нового груза, так и реализация уже имеющегося cargo —
 * делегированы {@link TradeRoutePlanner}, который работает только с immutable
 * {@link MarketDirectory} и {@link FleetTradeProfile}. Этот system отвечает за cooldown,
 * повторную валидацию сделки и навигационное намерение, но не интегрирует Transform.</p>
 *
 * <p>Обычное движение generic NPC не имеет отдельной кинематической реализации: этот system
 * пишет только transient flight intent через {@link InertialNavigation}; фактические ускорение,
 * торможение и перемещение выполняет {@link AutonomousFlightSystem} через общий
 * {@link com.spacesim.flight.FlightDynamics}. Поэтому масса реального груза влияет на generic
 * trader тем же способом, что на корабль игрока и delegated fleet.</p>
 *
 * <p>Persistent-план в {@link TradeAIComponent} содержит только устойчивые {@link EntityId}.
 * Перед движением и сделкой ID разрешается через {@link EntityRegistry}; stale route безопасно
 * отбрасывается и будет перепланирован после короткого cooldown.</p>
 *
 * <p>Authoritative деньги хранятся в {@link WalletComponent}, сделки выполняет
 * {@link TradeController} и записывает в общий {@link EconomicLedger}. По умолчанию новые грузы
 * сравниваются по gross profit/second, а уже купленный cargo — по revenue/second.</p>
 *
 * <p>Отрицательный результат pure planner кэшируется только для точной пары
 * {@code MarketDirectory.revision + FleetTradeProfile}. Cache является transient runtime detail и
 * не входит в persistent state: при изменении любого market/fleet input planner выполняется снова.</p>
 */
public class TradeAISystem extends IteratingSystem {
    private static final float ARRIVAL_DISTANCE = 10f;
    private static final float ROUTE_SEARCH_RETRY_SECONDS = 1f;

    private final TradeController tradeController;
    private final EntityRegistry registry;
    private final ContentCatalog contentCatalog;
    private final MarketDirectory marketDirectory;
    private final TradeRoutePlanner routePlanner;
    private final Map<EntityId, FailedRouteSearch> failedRouteSearches = new HashMap<>();
    private ImmutableArray<Entity> marketStations;

    private final ComponentMapper<EntityIdComponent> idm = ComponentMapper.getFor(EntityIdComponent.class);
    private final ComponentMapper<TradeAIComponent> am = ComponentMapper.getFor(TradeAIComponent.class);
    private final ComponentMapper<TransformComponent> tm = ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<MarketComponent> mm = ComponentMapper.getFor(MarketComponent.class);
    private final ComponentMapper<InventoryComponent> im = ComponentMapper.getFor(InventoryComponent.class);
    private final ComponentMapper<FactionComponent> fm = ComponentMapper.getFor(FactionComponent.class);
    private final ComponentMapper<ProcurementPolicyComponent> ppm =
            ComponentMapper.getFor(ProcurementPolicyComponent.class);
    private final ComponentMapper<ReputationComponent> rm = ComponentMapper.getFor(ReputationComponent.class);
    private final ComponentMapper<ShipComponent> sm = ComponentMapper.getFor(ShipComponent.class);
    private final ComponentMapper<WalletComponent> wm = ComponentMapper.getFor(WalletComponent.class);

    /**
     * Создаёт торговую AI-систему с собственными ledger/registry и встроенным catalog.
     *
     * @param grid compatibility spatial index; route planning больше его не использует
     * @throws NullPointerException если индекс не задан
     */
    public TradeAISystem(SpatialHashGrid grid) {
        this(grid, new EconomicLedger(), new EntityRegistry(), ContentCatalogLoader.loadDefault());
    }

    /**
     * Создаёт торговую AI-систему с общим ledger и встроенным catalog.
     *
     * @param grid compatibility spatial index; route planning больше его не использует
     * @param ledger общий экономический журнал
     * @throws NullPointerException если зависимость не задана
     */
    public TradeAISystem(SpatialHashGrid grid, EconomicLedger ledger) {
        this(grid, ledger, new EntityRegistry(), ContentCatalogLoader.loadDefault());
    }

    /**
     * Создаёт торговую AI-систему с общими ledger/registry и встроенным catalog.
     *
     * @param grid compatibility spatial index; route planning больше его не использует
     * @param ledger общий экономический журнал
     * @param registry runtime-индекс устойчивых EntityId
     * @throws NullPointerException если зависимость не задана
     */
    public TradeAISystem(SpatialHashGrid grid, EconomicLedger ledger, EntityRegistry registry) {
        this(grid, ledger, registry, ContentCatalogLoader.loadDefault());
    }

    /**
     * Создаёт торговую AI-систему с явно заданным catalog и Stage-5 profit/time scoring.
     *
     * @param grid compatibility spatial index; route planning больше его не использует
     * @param ledger общий экономический журнал
     * @param registry runtime-индекс устойчивых EntityId
     * @param contentCatalog каталог товаров текущей simulation session
     * @throws NullPointerException если зависимость не задана
     */
    public TradeAISystem(
            SpatialHashGrid grid,
            EconomicLedger ledger,
            EntityRegistry registry,
            ContentCatalog contentCatalog) {
        this(grid, ledger, registry, contentCatalog, TradeRoutePlanner.ScoringMode.PROFIT_PER_SECOND);
    }

    /**
     * Создаёт торговую AI-систему с явно заданной политикой route scoring.
     *
     * @param grid compatibility spatial index; route planning больше его не использует
     * @param ledger общий экономический журнал
     * @param registry runtime-индекс устойчивых EntityId
     * @param contentCatalog каталог товаров текущей simulation session
     * @param scoringMode политика сравнения торговых маршрутов
     * @throws NullPointerException если зависимость не задана
     */
    public TradeAISystem(
            SpatialHashGrid grid,
            EconomicLedger ledger,
            EntityRegistry registry,
            ContentCatalog contentCatalog,
            TradeRoutePlanner.ScoringMode scoringMode) {
        super(Family.all(
                EntityIdComponent.class,
                TradeAIComponent.class,
                TransformComponent.class,
                InventoryComponent.class,
                WalletComponent.class).get());
        Objects.requireNonNull(grid, "SpatialHashGrid не задан");
        this.tradeController = new TradeController(
                Objects.requireNonNull(ledger, "EconomicLedger не задан"));
        this.registry = Objects.requireNonNull(registry, "EntityRegistry не задан");
        this.contentCatalog = Objects.requireNonNull(contentCatalog, "ContentCatalog не задан");
        this.marketDirectory = new MarketDirectory(this.contentCatalog);
        this.routePlanner = new TradeRoutePlanner(
                this.contentCatalog,
                Objects.requireNonNull(scoringMode, "ScoringMode не задан"));
    }

    /** @return ledger, в который система записывает успешные сделки */
    public EconomicLedger getLedger() {
        return tradeController.getLedger();
    }

    /**
     * Invalidates transient planner state after authoritative entity removal.
     *
     * <p>The removed entity ID is dropped from per-fleet negative-search cache. When the removed
     * entity was a market, the shared immutable market snapshot is discarded immediately so no
     * route planner invocation can observe it as a candidate.</p>
     *
     * @param removedId persistent ID that left the local simulation
     * @param marketRemoved whether the removed entity participated as a market station
     */
    public void invalidateAfterEntityRemoval(EntityId removedId, boolean marketRemoved) {
        if (removedId != null) {
            failedRouteSearches.remove(removedId);
        }
        if (marketRemoved) {
            marketDirectory.invalidate();
        }
    }

    /**
     * Подключает registry к Engine и получает живое представление идентифицированных рынков.
     *
     * @param engine Ashley-движок
     */
    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        registry.track(engine);
        marketStations = engine.getEntitiesFor(Family.all(
                EntityIdComponent.class,
                TransformComponent.class,
                MarketComponent.class,
                InventoryComponent.class,
                WalletComponent.class).get());
    }

    /**
     * Перестраивает общий market snapshot и исполняет торговые автоматы.
     *
     * @param deltaTime прошедшее игровое время в секундах
     */
    @Override
    public void update(float deltaTime) {
        if (!Float.isFinite(deltaTime) || deltaTime < 0f) {
            return;
        }
        marketDirectory.rebuild(marketStations);
        super.update(deltaTime);
    }

    /** Исполняет текущее состояние одного торгового флота. */
    @Override
    protected void processEntity(Entity fleet, float deltaTime) {
        TradeAIComponent ai = am.get(fleet);
        TransformComponent transform = tm.get(fleet);
        if (ai.state == null) {
            InertialNavigation.stop(fleet, ai.movementSpeed);
            abandonRoute(ai);
            return;
        }

        switch (ai.state) {
            case IDLE -> {
                InertialNavigation.stop(fleet, ai.movementSpeed);
                processIdle(fleet, ai, transform, deltaTime);
            }
            case TRAVEL_TO_BUY -> navigate(
                    fleet, ai.buyStationId, ai, TradeAIComponent.State.BUYING);
            case BUYING -> {
                InertialNavigation.stop(fleet, ai.movementSpeed);
                buyCargo(fleet, ai);
            }
            case TRAVEL_TO_SELL -> navigate(
                    fleet, ai.sellStationId, ai, TradeAIComponent.State.SELLING);
            case SELLING -> {
                InertialNavigation.stop(fleet, ai.movementSpeed);
                sellCargo(fleet, ai);
            }
        }
    }

    private void processIdle(Entity fleet, TradeAIComponent ai, TransformComponent position, float deltaTime) {
        if (ai.routeSearchCooldown > 0f) {
            ai.routeSearchCooldown = Math.max(0f, ai.routeSearchCooldown - Math.max(0f, deltaTime));
            if (ai.routeSearchCooldown > 0f) {
                return;
            }
        }

        FleetTradeProfile profile = createFleetTradeProfile(fleet, ai, position);
        EntityId fleetId = idm.get(fleet).id;
        long marketRevision = marketDirectory.revision();
        FailedRouteSearch previousFailure = fleetId == null
                ? null
                : failedRouteSearches.get(fleetId);

        boolean routeFound;
        if (previousFailure != null
                && previousFailure.marketRevision == marketRevision
                && previousFailure.profile.samePlanningState(profile)) {
            ai.resetRoute();
            routeFound = false;
        } else {
            routeFound = im.get(fleet).getTotalStock() > 0
                    ? findFactionProcurementSale(fleet, ai, profile) || findSellRoute(ai, profile)
                    : findTradeRoute(ai, profile);
            if (fleetId != null) {
                if (routeFound) {
                    failedRouteSearches.remove(fleetId);
                } else {
                    failedRouteSearches.put(
                            fleetId,
                            new FailedRouteSearch(marketRevision, profile));
                }
            }
        }

        if (!routeFound) {
            ai.routeSearchCooldown = ROUTE_SEARCH_RETRY_SECONDS;
        }
    }

    private boolean findTradeRoute(TradeAIComponent ai, FleetTradeProfile profile) {
        ai.resetRoute();
        TradeRoute route = routePlanner.findBestNewCargoRoute(profile, marketDirectory).orElse(null);
        if (route == null) {
            return false;
        }

        ai.buyStationId = route.buyStationId();
        ai.sellStationId = route.sellStationId();
        ai.targetStationId = route.buyStationId();
        ai.targetItem = route.itemId();
        ai.targetAmount = route.amount();
        ai.expectedProfitMilliCredits = route.grossProfitMilliCredits();
        ai.routeSearchCooldown = 0f;
        ai.state = TradeAIComponent.State.TRAVEL_TO_BUY;
        return true;
    }

    private boolean findFactionProcurementSale(
            Entity fleet, TradeAIComponent ai, FleetTradeProfile profile) {
        FactionComponent fleetFaction = fm.get(fleet);
        if (fleetFaction == null || marketStations == null) {
            return false;
        }
        List<Entity> procurementMarkets = new ArrayList<>();
        for (Entity station : marketStations) {
            FactionComponent stationFaction = fm.get(station);
            if (ppm.has(station)
                    && stationFaction != null
                    && stationFaction.factionId == fleetFaction.factionId) {
                procurementMarkets.add(station);
            }
        }
        if (procurementMarkets.isEmpty()) {
            return false;
        }
        MarketDirectory procurementDirectory = new MarketDirectory(contentCatalog);
        procurementDirectory.rebuild(procurementMarkets);
        ai.resetRoute();
        return applySaleRoute(
                ai, routePlanner.findBestExistingCargoSale(profile, procurementDirectory).orElse(null));
    }

    private boolean findSellRoute(TradeAIComponent ai, FleetTradeProfile profile) {
        ai.resetRoute();
        return applySaleRoute(
                ai, routePlanner.findBestExistingCargoSale(profile, marketDirectory).orElse(null));
    }

    private boolean applySaleRoute(TradeAIComponent ai, TradeSaleRoute route) {
        if (route == null) {
            return false;
        }

        ai.buyStationId = null;
        ai.sellStationId = route.sellStationId();
        ai.targetStationId = route.sellStationId();
        ai.targetItem = route.itemId();
        ai.targetAmount = route.amount();
        ai.expectedProfitMilliCredits = route.saleRevenueMilliCredits();
        ai.routeSearchCooldown = 0f;
        ai.state = TradeAIComponent.State.TRAVEL_TO_SELL;
        return true;
    }

    private FleetTradeProfile createFleetTradeProfile(
            Entity fleet,
            TradeAIComponent ai,
            TransformComponent position) {
        InventoryComponent inventory = im.get(fleet);
        WalletComponent wallet = wm.get(fleet);
        ReputationComponent reputationComponent = rm.get(fleet);
        float[] reputation = new float[Constants.MAX_FACTIONS];
        if (reputationComponent != null) {
            for (int factionId = 0; factionId < Constants.MAX_FACTIONS; factionId++) {
                reputation[factionId] = reputationComponent.getReputation(factionId);
            }
        }
        ShipComponent ship = sm.get(fleet);
        FactionComponent faction = fm.get(fleet);
        return new FleetTradeProfile(
                position.position.x,
                position.position.y,
                ai.movementSpeed,
                wallet.getBalanceMilliCredits(),
                inventory.capacity,
                inventory.getTotalStock(),
                ai.cargoSpace,
                ai.specializedItem,
                ship != null,
                ship == null ? null : ship.type,
                faction == null ? -1 : faction.factionId,
                inventory.stock,
                reputation);
    }

    private int calculateTradeAmount(
            Entity fleet,
            TradeAIComponent ai,
            Entity buyStation,
            Entity sellStation,
            int itemId,
            float purchasePrice,
            float salePrice) {
        InventoryComponent fleetInventory = im.get(fleet);
        InventoryComponent buyInventory = im.get(buyStation);
        InventoryComponent sellInventory = im.get(sellStation);
        MarketComponent sellMarket = mm.get(sellStation);
        WalletComponent fleetWallet = wm.get(fleet);
        WalletComponent buyWallet = wm.get(buyStation);
        WalletComponent sellWallet = wm.get(sellStation);

        int fleetFree = Math.max(0,
                Math.min(ai.cargoSpace - fleetInventory.getTotalStock(), fleetInventory.getFreeCapacity()));
        int transferable = Math.min(fleetFree, sellInventory.getFreeCapacity());
        transferable = Math.min(transferable, buyInventory.stock[itemId]);
        int demand = Math.max(0, sellMarket.targetStock[itemId] - sellInventory.stock[itemId]);
        if (demand > 0) {
            transferable = Math.min(transferable, demand);
        }
        if (transferable <= 0) {
            return 0;
        }

        transferable = Math.min(transferable,
                safeMaximumAffordable(
                        fleetWallet.getBalanceMilliCredits(), purchasePrice, transferable));
        transferable = Math.min(transferable,
                safeMaximumAffordable(
                        Long.MAX_VALUE - buyWallet.getBalanceMilliCredits(),
                        purchasePrice,
                        transferable));
        transferable = Math.min(transferable,
                safeMaximumAffordable(
                        sellWallet.getBalanceMilliCredits(), salePrice, transferable));
        transferable = Math.min(transferable,
                safeMaximumAffordable(
                        Long.MAX_VALUE - fleetWallet.getBalanceMilliCredits(), salePrice, transferable));
        return Math.max(0, transferable);
    }

    private void navigate(
            Entity fleet,
            EntityId targetId,
            TradeAIComponent ai,
            TradeAIComponent.State arrivalState) {
        Entity target = resolveActiveMarketStation(targetId);
        if (target == null) {
            InertialNavigation.stop(fleet, ai.movementSpeed);
            abandonRoute(ai);
            return;
        }
        if (!Float.isFinite(ai.movementSpeed) || ai.movementSpeed <= 0f) {
            InertialNavigation.clear(fleet);
            return;
        }

        InertialNavigation.Status status = InertialNavigation.approach(
                fleet, tm.get(target), ai.movementSpeed, ARRIVAL_DISTANCE);
        if (status == InertialNavigation.Status.ARRIVED) {
            ai.targetStationId = targetId;
            ai.state = arrivalState;
        }
    }

    private void buyCargo(Entity fleet, TradeAIComponent ai) {
        Entity buyStation = resolveActiveMarketStation(ai.buyStationId);
        Entity sellStation = resolveActiveMarketStation(ai.sellStationId);
        if (!isValidItem(ai.targetItem)
                || !canShipPurchaseItem(fleet, contentCatalog.findItem(ai.targetItem))
                || buyStation == null
                || sellStation == null
                || buyStation == sellStation
                || !mm.get(buyStation).isTradable(ai.targetItem)
                || !mm.get(sellStation).isTradable(ai.targetItem)) {
            abandonRoute(ai);
            return;
        }

        ReputationComponent reputation = rm.get(fleet);
        float purchasePrice = tradeController.getEffectiveSellPrice(
                buyStation, ai.targetItem, reputation);
        float salePrice = tradeController.getEffectiveBuyPrice(
                sellStation, ai.targetItem, reputation);
        if (!isPositiveFinitePrice(purchasePrice)
                || !isPositiveFinitePrice(salePrice)
                || salePrice <= purchasePrice) {
            abandonRoute(ai);
            return;
        }

        int amount = Math.min(ai.targetAmount, calculateTradeAmount(
                fleet,
                ai,
                buyStation,
                sellStation,
                ai.targetItem,
                purchasePrice,
                salePrice));
        if (amount <= 0 || !tradeController.buyFromStation(
                buyStation, fleet, ai.targetItem, amount, reputation)) {
            abandonRoute(ai);
            return;
        }

        ai.targetAmount = amount;
        ai.targetStationId = ai.sellStationId;
        ai.state = TradeAIComponent.State.TRAVEL_TO_SELL;
    }

    private void sellCargo(Entity fleet, TradeAIComponent ai) {
        Entity sellStation = resolveActiveMarketStation(ai.sellStationId);
        if (!isValidItem(ai.targetItem)
                || sellStation == null
                || !mm.get(sellStation).isTradable(ai.targetItem)) {
            abandonRoute(ai);
            return;
        }

        InventoryComponent fleetInventory = im.get(fleet);
        InventoryComponent stationInventory = im.get(sellStation);
        WalletComponent stationWallet = wm.get(sellStation);
        WalletComponent fleetWallet = wm.get(fleet);
        ReputationComponent reputation = rm.get(fleet);
        float salePrice = tradeController.getEffectiveBuyPrice(
                sellStation, ai.targetItem, reputation);
        if (!isPositiveFinitePrice(salePrice)) {
            abandonRoute(ai);
            return;
        }

        int amount = Math.min(ai.targetAmount,
                Math.min(fleetInventory.stock[ai.targetItem], stationInventory.getFreeCapacity()));
        amount = Math.min(amount,
                safeMaximumAffordable(stationWallet.getBalanceMilliCredits(), salePrice, amount));
        amount = Math.min(amount,
                safeMaximumAffordable(
                        Long.MAX_VALUE - fleetWallet.getBalanceMilliCredits(), salePrice, amount));
        if (amount <= 0 || !tradeController.sellToStation(
                sellStation, fleet, ai.targetItem, amount, reputation)) {
            abandonRoute(ai);
            return;
        }
        finishRoute(ai);
    }

    private Entity resolveActiveMarketStation(EntityId id) {
        Entity entity = registry.find(id);
        return isActiveMarketStation(entity) ? entity : null;
    }

    private boolean isActiveMarketStation(Entity entity) {
        if (entity == null || marketStations == null) {
            return false;
        }
        for (Entity station : marketStations) {
            if (station == entity) {
                return true;
            }
        }
        return false;
    }

    private boolean canShipPurchaseItem(Entity fleet, ContentCatalog.ItemDefinition item) {
        if (item == null) {
            return false;
        }
        ShipComponent ship = sm.get(fleet);
        return ship == null
                || (ship.type != null && ship.type.canPurchase(item.category(), item.mineable()));
    }

    private boolean isValidItem(int itemId) {
        return contentCatalog.findItem(itemId) != null;
    }

    private boolean isPositiveFinitePrice(float price) {
        return Float.isFinite(price) && price > 0f;
    }

    private int safeMaximumAffordable(long balance, float price, int maxAmount) {
        if (balance < 0L || maxAmount <= 0 || !isPositiveFinitePrice(price)) {
            return 0;
        }
        try {
            return Money.maximumAffordable(balance, price, maxAmount);
        } catch (IllegalArgumentException exception) {
            return 0;
        }
    }

    private void abandonRoute(TradeAIComponent ai) {
        ai.resetRoute();
        ai.state = TradeAIComponent.State.IDLE;
        ai.routeSearchCooldown = ROUTE_SEARCH_RETRY_SECONDS;
    }

    private void finishRoute(TradeAIComponent ai) {
        ai.resetRoute();
        ai.state = TradeAIComponent.State.IDLE;
        ai.routeSearchCooldown = 0f;
    }

    private record FailedRouteSearch(long marketRevision, FleetTradeProfile profile) {
    }
}
