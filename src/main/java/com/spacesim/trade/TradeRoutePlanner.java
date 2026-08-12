package com.spacesim.trade;

import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.economy.Money;

import java.util.Objects;
import java.util.Optional;

/**
 * Pure planner новых торговых рейсов поверх immutable {@link MarketDirectory}.
 *
 * <p>Planner не читает Ashley ECS и не меняет состояние мира. Он применяет те же ограничения
 * ликвидности, вместимости, спроса, cargo policy и репутационной цены, которые используются при
 * фактической сделке. Scoring policy вынесена отдельно: Stage 5 сначала может доказать legacy
 * gross-profit equivalence, а затем намеренно перейти на profit/time.</p>
 */
public final class TradeRoutePlanner {
    /** Политика сравнения допустимых маршрутов. */
    public enum ScoringMode {
        /** Legacy-поведение: максимальная абсолютная валовая прибыль. */
        GROSS_PROFIT,
        /** Целевая Stage-5 политика: максимальная валовая прибыль на секунду движения. */
        PROFIT_PER_SECOND
    }

    private final ContentCatalog contentCatalog;
    private final ScoringMode scoringMode;

    /**
     * Создаёт planner с явной политикой scoring.
     *
     * @param contentCatalog каталог товаров текущей session
     * @param scoringMode способ сравнения маршрутов
     */
    public TradeRoutePlanner(ContentCatalog contentCatalog, ScoringMode scoringMode) {
        this.contentCatalog = Objects.requireNonNull(contentCatalog, "ContentCatalog не задан");
        this.scoringMode = Objects.requireNonNull(scoringMode, "ScoringMode не задан");
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
            for (MarketDirectory.StationMarket supplier : directory.suppliers(itemId)) {
                float purchasePrice = effectiveSellPrice(supplier, itemId, fleet);
                if (!isPositiveFinite(purchasePrice)) {
                    continue;
                }
                for (MarketDirectory.StationMarket consumer : directory.consumers(itemId)) {
                    if (consumer.id().equals(supplier.id())) {
                        continue;
                    }
                    float salePrice = effectiveBuyPrice(consumer, itemId, fleet);
                    if (!isPositiveFinite(salePrice) || salePrice <= purchasePrice) {
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
                    float distance = routeDistance(fleet, supplier, consumer);
                    double travelSeconds = travelSeconds(distance, fleet.movementSpeed());
                    TradeRoute candidate = new TradeRoute(
                            supplier.id(),
                            consumer.id(),
                            itemId,
                            amount,
                            purchaseCost,
                            saleRevenue,
                            saleRevenue - purchaseCost,
                            distance,
                            travelSeconds);
                    if (isBetter(candidate, best)) {
                        best = candidate;
                    }
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
                        consumer.walletBalanceMilliCredits(), salePrice, transferable));
        transferable = Math.min(transferable,
                safeMaximumAffordable(
                        Long.MAX_VALUE - fleet.walletBalanceMilliCredits(),
                        salePrice,
                        transferable));
        return Math.max(0, transferable);
    }

    private boolean isBetter(TradeRoute candidate, TradeRoute current) {
        if (current == null) {
            return true;
        }
        int primary = scoringMode == ScoringMode.GROSS_PROFIT
                ? Long.compare(candidate.grossProfitMilliCredits(), current.grossProfitMilliCredits())
                : Double.compare(candidate.grossProfitPerSecond(), current.grossProfitPerSecond());
        if (primary != 0) {
            return primary > 0;
        }

        int profitTie = Long.compare(candidate.grossProfitMilliCredits(), current.grossProfitMilliCredits());
        if (profitTie != 0) {
            return profitTie > 0;
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
            MarketDirectory.StationMarket consumer) {
        double toSupplier = Math.hypot(supplier.x() - fleet.x(), supplier.y() - fleet.y());
        double toConsumer = Math.hypot(consumer.x() - supplier.x(), consumer.y() - supplier.y());
        double total = toSupplier + toConsumer;
        if (!Double.isFinite(total) || total > Float.MAX_VALUE) {
            return Float.MAX_VALUE;
        }
        return (float) total;
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
