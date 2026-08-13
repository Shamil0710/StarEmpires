package com.spacesim.trade;

import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.economy.Money;

import java.util.Objects;
import java.util.Optional;

/**
 * Pure planner торговых рейсов поверх immutable {@link MarketDirectory}.
 *
 * <p>Planner не читает Ashley ECS и не меняет состояние мира. Для нового груза он применяет
 * ограничения ликвидности, вместимости, спроса, cargo policy и репутационной цены. Pairing
 * supplier-consumer выполняет общий directory один раз за market snapshot; конкретный fleet
 * оценивает bounded {@link TradeOpportunity} shortlist. Внешние fuel/tariff/risk penalties
 * подключаются через {@link TradeRouteCostModel} и участвуют в net scoring.</p>
 */
public final class TradeRoutePlanner {
    /** Политика сравнения допустимых маршрутов. */
    public enum ScoringMode {
        /** Legacy-форма: максимальная абсолютная net прибыль/выручка. */
        GROSS_PROFIT,
        /** Целевая Stage-5 политика: максимальная net прибыль/выручка на секунду движения. */
        PROFIT_PER_SECOND
    }

    private final ContentCatalog contentCatalog;
    private final ScoringMode scoringMode;
    private final TradeRouteCostModel costModel;

    /**
     * Создаёт planner с нулевыми внешними route costs.
     *
     * @param contentCatalog каталог товаров текущей session
     * @param scoringMode способ сравнения маршрутов
     */
    public TradeRoutePlanner(ContentCatalog contentCatalog, ScoringMode scoringMode) {
        this(contentCatalog, scoringMode, TradeRouteCostModel.none());
    }

    /**
     * Создаёт planner с явно заданной моделью логистических расходов.
     *
     * @param contentCatalog каталог товаров текущей session
     * @param scoringMode способ сравнения маршрутов
     * @param costModel policy дополнительных fuel/tariff/risk costs
     */
    public TradeRoutePlanner(
            ContentCatalog contentCatalog,
            ScoringMode scoringMode,
            TradeRouteCostModel costModel) {
        this.contentCatalog = Objects.requireNonNull(contentCatalog, "ContentCatalog не задан");
        this.scoringMode = Objects.requireNonNull(scoringMode, "ScoringMode не задан");
        this.costModel = Objects.requireNonNull(costModel, "TradeRouteCostModel не задан");
    }

    /**
     * Ищет лучший новый маршрут для пустого/частично пустого коммерческого трюма.
     *
     * @param fleet immutable профиль конкретного флота
     * @param directory общий snapshot рынков текущего tick
     * @return лучший допустимый маршрут либо empty
     */
    public Optional<TradeRoute> findBestNewCargoRoute(
            FleetTradeProfile fleet,
            MarketDirectory directory) {
        Objects.requireNonNull(fleet, "FleetTradeProfile не задан");
        Objects.requireNonNull(directory, "MarketDirectory не задан");
        if (fleet.routeCargoCapacity() <= 0) {
            return Optional.empty();
        }

        TradeRoute best = null;
        for (ContentCatalog.ItemDefinition item : contentCatalog.getItems()) {
            if (!fleet.canPurchase(item)) {
                continue;
            }
            int itemId = item.runtimeId();
            for (TradeOpportunity opportunity : directory.opportunities(itemId)) {
                MarketDirectory.StationMarket supplier = directory.find(opportunity.buyStationId());
                MarketDirectory.StationMarket consumer = directory.find(opportunity.sellStationId());
                if (supplier == null || consumer == null
                        || !supplier.canTrade(fleet.factionId())
                        || !consumer.canTrade(fleet.factionId())) {
                    continue;
                }

                float purchasePrice = effectiveSellPrice(supplier, itemId, fleet);
                float salePrice = effectiveBuyPrice(consumer, itemId, fleet);
                if (!isPositiveFinite(purchasePrice)
                        || !isPositiveFinite(salePrice)
                        || salePrice <= purchasePrice) {
                    continue;
                }
                int amount = calculateAmount(
                        fleet, supplier, consumer, itemId, purchasePrice, salePrice);
                if (amount <= 0) {
                    continue;
                }
                long purchaseCost = safeTradeValue(purchasePrice, amount);
                long saleRevenue = safeTradeValue(salePrice, amount);
                if (purchaseCost <= 0L || saleRevenue <= purchaseCost) {
                    continue;
                }
                long grossProfit = saleRevenue - purchaseCost;
                float distance = routeDistance(fleet, supplier, opportunity.stationDistance());
                double seconds = travelSeconds(distance, fleet.movementSpeed());
                long routeCost = estimateRouteCost(
                        fleet,
                        new TradeRouteCostModel.Context(
                                supplier.id(),
                                consumer.id(),
                                supplier.factionId(),
                                consumer.factionId(),
                                itemId,
                                amount,
                                purchaseCost,
                                saleRevenue,
                                distance,
                                seconds));
                if (routeCost >= grossProfit) {
                    continue;
                }
                TradeRoute candidate = new TradeRoute(
                        supplier.id(),
                        consumer.id(),
                        itemId,
                        amount,
                        purchaseCost,
                        saleRevenue,
                        grossProfit,
                        routeCost,
                        grossProfit - routeCost,
                        distance,
                        seconds);
                if (isBetter(candidate, best)) {
                    best = candidate;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Ищет лучшую точку реализации уже находящегося на борту груза.
     *
     * <p>Закупочная цена является sunk cost. Route costs всё равно вычитаются из ожидаемой выручки,
     * поэтому дальняя дорогая продажа может уступить ближней после fuel/risk/tariff policy.</p>
     *
     * @param fleet immutable профиль флота с текущим cargo snapshot
     * @param directory общий snapshot рынков текущего tick
     * @return лучший sale route либо empty
     */
    public Optional<TradeSaleRoute> findBestExistingCargoSale(
            FleetTradeProfile fleet,
            MarketDirectory directory) {
        Objects.requireNonNull(fleet, "FleetTradeProfile не задан");
        Objects.requireNonNull(directory, "MarketDirectory не задан");

        TradeSaleRoute best = null;
        for (ContentCatalog.ItemDefinition item : contentCatalog.getItems()) {
            int itemId = item.runtimeId();
            int cargo = fleet.stock(itemId);
            if (cargo <= 0) {
                continue;
            }
            for (MarketDirectory.StationMarket consumer : directory.consumers(itemId)) {
                if (!consumer.canTrade(fleet.factionId())) {
                    continue;
                }
                float salePrice = effectiveBuyPrice(consumer, itemId, fleet);
                if (!isPositiveFinite(salePrice)) {
                    continue;
                }
                int amount = Math.min(cargo, consumer.freeCapacity());
                int demand = Math.max(0, consumer.targetStock(itemId) - consumer.stock(itemId));
                if (demand > 0) {
                    amount = Math.min(amount, demand);
                }
                long minimumRevenue = safeTradeValue(salePrice, 1);
                if (amount <= 0
                        || minimumRevenue <= 0L
                        || consumer.walletBalanceMilliCredits() < minimumRevenue
                        || Long.MAX_VALUE - fleet.walletBalanceMilliCredits() < minimumRevenue) {
                    continue;
                }
                amount = Math.min(amount,
                        safeMaximumAffordable(
                                consumer.walletBalanceMilliCredits(), salePrice, amount));
                amount = Math.min(amount,
                        safeMaximumAffordable(
                                Long.MAX_VALUE - fleet.walletBalanceMilliCredits(), salePrice, amount));
                if (amount <= 0) {
                    continue;
                }
                long revenue = safeTradeValue(salePrice, amount);
                if (revenue <= 0L) {
                    continue;
                }
                float distance = directDistance(fleet, consumer);
                double seconds = travelSeconds(distance, fleet.movementSpeed());
                long routeCost = estimateRouteCost(
                        fleet,
                        new TradeRouteCostModel.Context(
                                null,
                                consumer.id(),
                                -1,
                                consumer.factionId(),
                                itemId,
                                amount,
                                0L,
                                revenue,
                                distance,
                                seconds));
                if (routeCost >= revenue) {
                    continue;
                }
                TradeSaleRoute candidate = new TradeSaleRoute(
                        consumer.id(),
                        itemId,
                        amount,
                        revenue,
                        routeCost,
                        revenue - routeCost,
                        distance,
                        seconds);
                if (isBetter(candidate, best)) {
                    best = candidate;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private int calculateAmount(
            FleetTradeProfile fleet,
            MarketDirectory.StationMarket supplier,
            MarketDirectory.StationMarket consumer,
            int itemId,
            float purchasePrice,
            float salePrice) {
        int transferable = Math.min(fleet.routeCargoCapacity(), consumer.freeCapacity());
        transferable = Math.min(transferable, supplier.stock(itemId));
        int demand = Math.max(0, consumer.targetStock(itemId) - consumer.stock(itemId));
        if (demand > 0) {
            transferable = Math.min(transferable, demand);
        }
        if (transferable <= 0) {
            return 0;
        }

        long minimumPurchaseCost = safeTradeValue(purchasePrice, 1);
        long minimumSaleRevenue = safeTradeValue(salePrice, 1);
        if (minimumPurchaseCost <= 0L
                || minimumSaleRevenue <= 0L
                || consumer.walletBalanceMilliCredits() < minimumSaleRevenue
                || fleet.walletBalanceMilliCredits() < minimumPurchaseCost
                || Long.MAX_VALUE - supplier.walletBalanceMilliCredits() < minimumPurchaseCost
                || Long.MAX_VALUE - fleet.walletBalanceMilliCredits() < minimumSaleRevenue) {
            return 0;
        }

        transferable = Math.min(transferable,
                safeMaximumAffordable(
                        consumer.walletBalanceMilliCredits(), salePrice, transferable));
        transferable = Math.min(transferable,
                safeMaximumAffordable(
                        fleet.walletBalanceMilliCredits(), purchasePrice, transferable));
        transferable = Math.min(transferable,
                safeMaximumAffordable(
                        Long.MAX_VALUE - supplier.walletBalanceMilliCredits(),
                        purchasePrice,
                        transferable));
        transferable = Math.min(transferable,
                safeMaximumAffordable(
                        Long.MAX_VALUE - fleet.walletBalanceMilliCredits(), salePrice, transferable));
        return Math.max(0, transferable);
    }

    private boolean isBetter(TradeRoute candidate, TradeRoute current) {
        if (current == null) {
            return true;
        }
        int primary = scoringMode == ScoringMode.GROSS_PROFIT
                ? Long.compare(candidate.netProfitMilliCredits(), current.netProfitMilliCredits())
                : Double.compare(candidate.netProfitPerSecond(), current.netProfitPerSecond());
        if (primary != 0) {
            return primary > 0;
        }
        int netTie = Long.compare(candidate.netProfitMilliCredits(), current.netProfitMilliCredits());
        if (netTie != 0) {
            return netTie > 0;
        }
        int grossTie = Long.compare(candidate.grossProfitMilliCredits(), current.grossProfitMilliCredits());
        if (grossTie != 0) {
            return grossTie > 0;
        }
        int timeTie = Double.compare(candidate.travelSeconds(), current.travelSeconds());
        if (timeTie != 0) {
            return timeTie < 0;
        }
        int itemTie = Integer.compare(candidate.itemId(), current.itemId());
        if (itemTie != 0) {
            return itemTie < 0;
        }
        int supplierTie = candidate.buyStationId().compareTo(current.buyStationId());
        if (supplierTie != 0) {
            return supplierTie < 0;
        }
        return candidate.sellStationId().compareTo(current.sellStationId()) < 0;
    }

    private boolean isBetter(TradeSaleRoute candidate, TradeSaleRoute current) {
        if (current == null) {
            return true;
        }
        int primary = scoringMode == ScoringMode.GROSS_PROFIT
                ? Long.compare(candidate.netRevenueMilliCredits(), current.netRevenueMilliCredits())
                : Double.compare(candidate.netRevenuePerSecond(), current.netRevenuePerSecond());
        if (primary != 0) {
            return primary > 0;
        }
        int netTie = Long.compare(candidate.netRevenueMilliCredits(), current.netRevenueMilliCredits());
        if (netTie != 0) {
            return netTie > 0;
        }
        int grossTie = Long.compare(
                candidate.saleRevenueMilliCredits(), current.saleRevenueMilliCredits());
        if (grossTie != 0) {
            return grossTie > 0;
        }
        int timeTie = Double.compare(candidate.travelSeconds(), current.travelSeconds());
        if (timeTie != 0) {
            return timeTie < 0;
        }
        int itemTie = Integer.compare(candidate.itemId(), current.itemId());
        if (itemTie != 0) {
            return itemTie < 0;
        }
        return candidate.sellStationId().compareTo(current.sellStationId()) < 0;
    }

    private long estimateRouteCost(FleetTradeProfile fleet, TradeRouteCostModel.Context context) {
        long cost = costModel.estimateCostMilliCredits(fleet, context);
        if (cost < 0L) {
            throw new IllegalArgumentException("TradeRouteCostModel вернул отрицательную стоимость");
        }
        return cost;
    }

    private float effectiveSellPrice(
            MarketDirectory.StationMarket station,
            int itemId,
            FleetTradeProfile fleet) {
        return station.sellPrice(itemId) * (1f - reputationBonus(station.factionId(), fleet));
    }

    private float effectiveBuyPrice(
            MarketDirectory.StationMarket station,
            int itemId,
            FleetTradeProfile fleet) {
        return station.buyPrice(itemId) * (1f + reputationBonus(station.factionId(), fleet));
    }

    private float reputationBonus(int factionId, FleetTradeProfile fleet) {
        if (factionId < 0 || factionId >= Constants.MAX_FACTIONS) {
            return 0f;
        }
        float value = fleet.reputation(factionId);
        if (!Float.isFinite(value)) {
            return 0f;
        }
        float normalized = Math.min(1f, Math.max(0f, value) / Constants.MAX_REPUTATION);
        return normalized * Constants.MAX_REPUTATION_PRICE_BONUS;
    }

    private static float routeDistance(
            FleetTradeProfile fleet,
            MarketDirectory.StationMarket supplier,
            float stationDistance) {
        double toSupplier = Math.hypot(supplier.x() - fleet.x(), supplier.y() - fleet.y());
        double total = toSupplier + stationDistance;
        if (!Double.isFinite(total) || total > Float.MAX_VALUE) {
            return Float.MAX_VALUE;
        }
        return (float) total;
    }

    private static float directDistance(
            FleetTradeProfile fleet,
            MarketDirectory.StationMarket consumer) {
        double distance = Math.hypot(consumer.x() - fleet.x(), consumer.y() - fleet.y());
        if (!Double.isFinite(distance) || distance > Float.MAX_VALUE) {
            return Float.MAX_VALUE;
        }
        return (float) distance;
    }

    private static double travelSeconds(float distance, float movementSpeed) {
        if (distance == 0f) {
            return 0d;
        }
        if (!Float.isFinite(movementSpeed) || movementSpeed <= 0f) {
            return Double.MAX_VALUE;
        }
        double seconds = distance / (double) movementSpeed;
        return Double.isFinite(seconds) ? seconds : Double.MAX_VALUE;
    }

    private static boolean isPositiveFinite(float value) {
        return Float.isFinite(value) && value > 0f;
    }

    private static long safeTradeValue(float price, int amount) {
        try {
            return Money.tradeValue(price, amount);
        } catch (IllegalArgumentException exception) {
            return -1L;
        }
    }

    private static int safeMaximumAffordable(long balance, float price, int maxAmount) {
        if (balance < 0L || maxAmount <= 0 || !isPositiveFinite(price)) {
            return 0;
        }
        try {
            return Money.maximumAffordable(balance, price, maxAmount);
        } catch (IllegalArgumentException exception) {
            return 0;
        }
    }
}
