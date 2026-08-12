package com.spacesim.trade;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.persistence.EntityId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Общий read-only снимок доступных рынков для поиска торговых маршрутов.
 *
 * <p>Directory перестраивается один раз из ECS-состояния, после чего ship planners работают только
 * с immutable {@link StationMarket}. По каждому active item поддерживаются списки поставщиков,
 * покупателей и bounded shortlist {@link TradeOpportunity}. Квадратичная supplier-consumer работа
 * выполняется один раз на общий market snapshot, а не повторяется каждым торговым кораблём.</p>
 */
public final class MarketDirectory {
    /** Максимальное число consumer-кандидатов одного supplier по одному товару. */
    public static final int MAX_CONSUMERS_PER_SUPPLIER = 8;

    private static final int PRICE_CANDIDATE_SLOTS = MAX_CONSUMERS_PER_SUPPLIER / 2;

    private final ContentCatalog contentCatalog;
    private final ComponentMapper<EntityIdComponent> idm = ComponentMapper.getFor(EntityIdComponent.class);
    private final ComponentMapper<TransformComponent> tm = ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<MarketComponent> mm = ComponentMapper.getFor(MarketComponent.class);
    private final ComponentMapper<InventoryComponent> im = ComponentMapper.getFor(InventoryComponent.class);
    private final ComponentMapper<WalletComponent> wm = ComponentMapper.getFor(WalletComponent.class);
    private final ComponentMapper<FactionComponent> fm = ComponentMapper.getFor(FactionComponent.class);
    private final Set<EntityId> seenLiveIds = new HashSet<>();

    private List<StationMarket> stations = List.of();
    private Map<EntityId, StationMarket> byId = Map.of();
    private List<List<StationMarket>> suppliersByItem = emptyIndex();
    private List<List<StationMarket>> consumersByItem = emptyIndex();
    private List<List<TradeOpportunity>> opportunitiesByItem = emptyOpportunityIndex();
    private long revision;

    /**
     * Создаёт пустой directory для указанного каталога.
     *
     * @param contentCatalog authoritative content catalog текущей session
     */
    public MarketDirectory(ContentCatalog contentCatalog) {
        this.contentCatalog = Objects.requireNonNull(contentCatalog, "ContentCatalog не задан");
    }

    /**
     * Перестраивает immutable snapshot из текущих ECS entities.
     *
     * <p>Сущности без полного market-набора компонентов игнорируются. Дублирующий persistent ID
     * считается повреждением runtime state и отклоняется. Для каждого supplier сохраняется максимум
     * {@link #MAX_CONSUMERS_PER_SUPPLIER} consumers: половина выбирается по высокой raw buy price,
     * оставшаяся часть — по optimistic margin/distance. Это сохраняет ценовые и логистические
     * альтернативы, но ограничивает число кандидатов, просматриваемых каждым fleet planner.</p>
     *
     * <p>Перед созданием новых immutable snapshots live ECS-компоненты точно сравниваются с последним
     * snapshot. Если ни одна station не изменилась, массивы не копируются, индексы переиспользуются,
     * а {@link #revision()} не меняется. При любом отличии выполняется обычная полная перестройка.</p>
     *
     * @param entities кандидаты рынков
     * @throws NullPointerException если iterable не задан
     * @throws IllegalStateException если обнаружены два рынка с одинаковым EntityId
     */
    public void rebuild(Iterable<Entity> entities) {
        Objects.requireNonNull(entities, "Market entities не заданы");
        if (matchesPreviousLiveState(entities)) {
            return;
        }

        List<StationMarket> stationBuilder = new ArrayList<>();
        Map<EntityId, StationMarket> idBuilder = new LinkedHashMap<>();
        List<List<StationMarket>> supplierBuilder = mutableIndex();
        List<List<StationMarket>> consumerBuilder = mutableIndex();

        for (Entity entity : entities) {
            if (!isMarketEntity(entity)) {
                continue;
            }
            EntityId id = idm.get(entity).id;
            if (id == null) {
                throw new IllegalStateException("Market entity не имеет persistent EntityId");
            }
            StationMarket snapshot = snapshot(entity);
            if (idBuilder.putIfAbsent(id, snapshot) != null) {
                throw new IllegalStateException("Дублирующий market EntityId: " + id);
            }
            stationBuilder.add(snapshot);

            for (ContentCatalog.ItemDefinition item : contentCatalog.getItems()) {
                int itemId = item.runtimeId();
                if (!snapshot.isTradable(itemId)) {
                    continue;
                }
                if (snapshot.stock(itemId) > 0 && isPositiveFinite(snapshot.sellPrice(itemId))) {
                    supplierBuilder.get(itemId).add(snapshot);
                }
                if (snapshot.freeCapacity() > 0
                        && snapshot.walletBalanceMilliCredits() > 0L
                        && isPositiveFinite(snapshot.buyPrice(itemId))) {
                    consumerBuilder.get(itemId).add(snapshot);
                }
            }
        }

        stationBuilder.sort(Comparator.comparing(StationMarket::id));
        DistanceTable distanceTable = new DistanceTable(stationBuilder);
        List<List<TradeOpportunity>> opportunityBuilder = mutableOpportunityIndex();
        for (ContentCatalog.ItemDefinition item : contentCatalog.getItems()) {
            int itemId = item.runtimeId();
            List<StationMarket> suppliers = supplierBuilder.get(itemId);
            List<StationMarket> consumers = consumerBuilder.get(itemId);
            suppliers.sort(
                    Comparator.comparingDouble((StationMarket station) -> station.sellPrice(itemId))
                            .thenComparing(StationMarket::id));
            consumers.sort(
                    Comparator.comparingDouble((StationMarket station) -> station.buyPrice(itemId))
                            .reversed()
                            .thenComparing(StationMarket::id));

            List<TradeOpportunity> opportunities = opportunityBuilder.get(itemId);
            for (StationMarket supplier : suppliers) {
                for (StationMarket consumer : selectConsumers(
                        supplier, consumers, itemId, distanceTable)) {
                    opportunities.add(new TradeOpportunity(
                            supplier.id(),
                            consumer.id(),
                            itemId,
                            supplier.sellPrice(itemId),
                            consumer.buyPrice(itemId),
                            distanceTable.distance(supplier, consumer)));
                }
            }
            opportunities.sort(
                    Comparator.comparing(TradeOpportunity::buyStationId)
                            .thenComparing(TradeOpportunity::sellStationId));
        }

        stations = List.copyOf(stationBuilder);
        byId = Collections.unmodifiableMap(new LinkedHashMap<>(idBuilder));
        suppliersByItem = immutableIndex(supplierBuilder);
        consumersByItem = immutableIndex(consumerBuilder);
        opportunitiesByItem = immutableOpportunityIndex(opportunityBuilder);
        revision++;
    }

    /** @return все market snapshots в deterministic EntityId-порядке */
    public List<StationMarket> stations() {
        return stations;
    }

    /**
     * Возвращает revision market snapshot; значение меняется только при точном изменении station state.
     *
     * @return monotonic revision текущего directory snapshot
     */
    public long revision() {
        return revision;
    }

    /**
     * Ищет market snapshot по persistent ID.
     *
     * @param id station ID
     * @return snapshot либо {@code null}
     */
    public StationMarket find(EntityId id) {
        return id == null ? null : byId.get(id);
    }

    /**
     * Возвращает станции, способные продать товар, в порядке raw sell price ASC.
     *
     * @param itemId runtime item ID
     * @return immutable список или пустой список
     */
    public List<StationMarket> suppliers(int itemId) {
        return validItemId(itemId) ? suppliersByItem.get(itemId) : List.of();
    }

    /**
     * Возвращает станции, способные купить товар, в порядке raw buy price DESC.
     *
     * @param itemId runtime item ID
     * @return immutable список или пустой список
     */
    public List<StationMarket> consumers(int itemId) {
        return validItemId(itemId) ? consumersByItem.get(itemId) : List.of();
    }

    /**
     * Возвращает bounded supplier-consumer shortlist товара.
     *
     * @param itemId runtime item ID
     * @return immutable opportunities или пустой список
     */
    public List<TradeOpportunity> opportunities(int itemId) {
        return validItemId(itemId) ? opportunitiesByItem.get(itemId) : List.of();
    }

    private boolean matchesPreviousLiveState(Iterable<Entity> entities) {
        if (stations.isEmpty()) {
            return false;
        }
        seenLiveIds.clear();
        int liveMarketCount = 0;
        boolean matches = true;
        for (Entity entity : entities) {
            if (!isMarketEntity(entity)) {
                continue;
            }
            EntityId id = idm.get(entity).id;
            if (id == null) {
                throw new IllegalStateException("Market entity не имеет persistent EntityId");
            }
            if (!seenLiveIds.add(id)) {
                throw new IllegalStateException("Дублирующий market EntityId: " + id);
            }
            liveMarketCount++;
            StationMarket previous = byId.get(id);
            if (previous == null || !matchesLiveState(previous, entity)) {
                matches = false;
            }
        }
        return matches && liveMarketCount == stations.size();
    }

    private boolean matchesLiveState(StationMarket previous, Entity entity) {
        TransformComponent transform = tm.get(entity);
        InventoryComponent inventory = im.get(entity);
        MarketComponent market = mm.get(entity);
        WalletComponent wallet = wm.get(entity);
        int factionId = fm.has(entity) ? fm.get(entity).factionId : -1;
        return previous.matchesLiveState(transform, factionId, wallet, inventory, market);
    }

    private boolean isMarketEntity(Entity entity) {
        return entity != null
                && idm.has(entity)
                && tm.has(entity)
                && mm.has(entity)
                && im.has(entity)
                && wm.has(entity);
    }

    private List<StationMarket> selectConsumers(
            StationMarket supplier,
            List<StationMarket> consumers,
            int itemId,
            DistanceTable distanceTable) {
        LinkedHashMap<EntityId, StationMarket> selected = new LinkedHashMap<>();
        StationMarket[] efficiencyCandidates = new StationMarket[MAX_CONSUMERS_PER_SUPPLIER];
        double[] efficiencyScores = new double[MAX_CONSUMERS_PER_SUPPLIER];
        int efficiencyCount = 0;

        for (StationMarket consumer : consumers) {
            if (!isPotentiallyProfitable(supplier, consumer, itemId)) {
                continue;
            }
            if (selected.size() < PRICE_CANDIDATE_SLOTS) {
                selected.put(consumer.id(), consumer);
            }

            double score = optimisticEfficiency(
                    supplier,
                    consumer,
                    itemId,
                    distanceTable.distance(supplier, consumer));
            int insertionIndex = efficiencyCount;
            for (int index = 0; index < efficiencyCount; index++) {
                int scoreCompare = Double.compare(score, efficiencyScores[index]);
                if (scoreCompare > 0
                        || (scoreCompare == 0
                        && consumer.id().compareTo(efficiencyCandidates[index].id()) < 0)) {
                    insertionIndex = index;
                    break;
                }
            }
            if (insertionIndex >= MAX_CONSUMERS_PER_SUPPLIER) {
                continue;
            }

            int last = Math.min(efficiencyCount, MAX_CONSUMERS_PER_SUPPLIER - 1);
            for (int index = last; index > insertionIndex; index--) {
                efficiencyCandidates[index] = efficiencyCandidates[index - 1];
                efficiencyScores[index] = efficiencyScores[index - 1];
            }
            efficiencyCandidates[insertionIndex] = consumer;
            efficiencyScores[insertionIndex] = score;
            if (efficiencyCount < MAX_CONSUMERS_PER_SUPPLIER) {
                efficiencyCount++;
            }
        }

        for (int index = 0;
                index < efficiencyCount && selected.size() < MAX_CONSUMERS_PER_SUPPLIER;
                index++) {
            StationMarket consumer = efficiencyCandidates[index];
            selected.putIfAbsent(consumer.id(), consumer);
        }
        return List.copyOf(selected.values());
    }

    private static boolean isPotentiallyProfitable(
            StationMarket supplier,
            StationMarket consumer,
            int itemId) {
        if (supplier.id().equals(consumer.id())) {
            return false;
        }
        double minimumPurchase = supplier.sellPrice(itemId)
                * (1d - Constants.MAX_REPUTATION_PRICE_BONUS);
        double maximumSale = consumer.buyPrice(itemId)
                * (1d + Constants.MAX_REPUTATION_PRICE_BONUS);
        return maximumSale > minimumPurchase;
    }

    private static double optimisticEfficiency(
            StationMarket supplier,
            StationMarket consumer,
            int itemId,
            float stationDistance) {
        double minimumPurchase = supplier.sellPrice(itemId)
                * (1d - Constants.MAX_REPUTATION_PRICE_BONUS);
        double maximumSale = consumer.buyPrice(itemId)
                * (1d + Constants.MAX_REPUTATION_PRICE_BONUS);
        double margin = Math.max(0d, maximumSale - minimumPurchase);
        return margin / Math.max(1d, stationDistance);
    }

    private StationMarket snapshot(Entity entity) {
        EntityId id = idm.get(entity).id;
        TransformComponent transform = tm.get(entity);
        MarketComponent market = mm.get(entity);
        InventoryComponent inventory = im.get(entity);
        WalletComponent wallet = wm.get(entity);
        int factionId = fm.has(entity) ? fm.get(entity).factionId : -1;
        return new StationMarket(
                id,
                transform.position.x,
                transform.position.y,
                factionId,
                wallet.getBalanceMilliCredits(),
                inventory.capacity,
                inventory.getTotalStock(),
                inventory.stock,
                market.targetStock,
                market.sellPrices,
                market.buyPrices,
                market.tradableItems);
    }

    private static float distance(StationMarket first, StationMarket second) {
        double value = Math.hypot(second.x() - first.x(), second.y() - first.y());
        if (!Double.isFinite(value) || value > Float.MAX_VALUE) {
            return Float.MAX_VALUE;
        }
        return (float) value;
    }

    private static boolean isPositiveFinite(float value) {
        return Float.isFinite(value) && value > 0f;
    }

    private static boolean validItemId(int itemId) {
        return itemId >= 0 && itemId < Constants.MAX_ITEMS;
    }

    private static List<List<StationMarket>> mutableIndex() {
        List<List<StationMarket>> index = new ArrayList<>(Constants.MAX_ITEMS);
        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            index.add(new ArrayList<>());
        }
        return index;
    }

    private static List<List<StationMarket>> emptyIndex() {
        List<List<StationMarket>> index = new ArrayList<>(Constants.MAX_ITEMS);
        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            index.add(List.of());
        }
        return List.copyOf(index);
    }

    private static List<List<StationMarket>> immutableIndex(List<List<StationMarket>> source) {
        List<List<StationMarket>> result = new ArrayList<>(source.size());
        for (List<StationMarket> values : source) {
            result.add(List.copyOf(values));
        }
        return List.copyOf(result);
    }

    private static List<List<TradeOpportunity>> mutableOpportunityIndex() {
        List<List<TradeOpportunity>> index = new ArrayList<>(Constants.MAX_ITEMS);
        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            index.add(new ArrayList<>());
        }
        return index;
    }

    private static List<List<TradeOpportunity>> emptyOpportunityIndex() {
        List<List<TradeOpportunity>> index = new ArrayList<>(Constants.MAX_ITEMS);
        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            index.add(List.of());
        }
        return List.copyOf(index);
    }

    private static List<List<TradeOpportunity>> immutableOpportunityIndex(
            List<List<TradeOpportunity>> source) {
        List<List<TradeOpportunity>> result = new ArrayList<>(source.size());
        for (List<TradeOpportunity> values : source) {
            result.add(List.copyOf(values));
        }
        return List.copyOf(result);
    }

    private static final class DistanceTable {
        private final Map<EntityId, Integer> indexById;
        private final float[][] values;

        private DistanceTable(List<StationMarket> stationMarkets) {
            indexById = new LinkedHashMap<>(stationMarkets.size());
            values = new float[stationMarkets.size()][stationMarkets.size()];
            for (int index = 0; index < stationMarkets.size(); index++) {
                indexById.put(stationMarkets.get(index).id(), index);
            }
            for (int firstIndex = 0; firstIndex < stationMarkets.size(); firstIndex++) {
                StationMarket first = stationMarkets.get(firstIndex);
                for (int secondIndex = firstIndex + 1;
                        secondIndex < stationMarkets.size();
                        secondIndex++) {
                    float value = MarketDirectory.distance(first, stationMarkets.get(secondIndex));
                    values[firstIndex][secondIndex] = value;
                    values[secondIndex][firstIndex] = value;
                }
            }
        }

        private float distance(StationMarket first, StationMarket second) {
            Integer firstIndex = indexById.get(first.id());
            Integer secondIndex = indexById.get(second.id());
            if (firstIndex == null || secondIndex == null) {
                return MarketDirectory.distance(first, second);
            }
            return values[firstIndex][secondIndex];
        }
    }

    /** Immutable снимок одной торговой станции. */
    public static final class StationMarket {
        private final EntityId id;
        private final float x;
        private final float y;
        private final int factionId;
        private final long walletBalanceMilliCredits;
        private final int inventoryCapacity;
        private final int totalStock;
        private final int[] stock;
        private final int[] targetStock;
        private final float[] sellPrices;
        private final float[] buyPrices;
        private final boolean[] tradable;

        private StationMarket(
                EntityId id,
                float x,
                float y,
                int factionId,
                long walletBalanceMilliCredits,
                int inventoryCapacity,
                int totalStock,
                int[] stock,
                int[] targetStock,
                float[] sellPrices,
                float[] buyPrices,
                boolean[] tradable) {
            this.id = Objects.requireNonNull(id, "Station EntityId не задан");
            this.x = x;
            this.y = y;
            this.factionId = factionId;
            this.walletBalanceMilliCredits = walletBalanceMilliCredits;
            this.inventoryCapacity = inventoryCapacity;
            this.totalStock = totalStock;
            this.stock = Arrays.copyOf(stock, stock.length);
            this.targetStock = Arrays.copyOf(targetStock, targetStock.length);
            this.sellPrices = Arrays.copyOf(sellPrices, sellPrices.length);
            this.buyPrices = Arrays.copyOf(buyPrices, buyPrices.length);
            this.tradable = Arrays.copyOf(tradable, tradable.length);
        }

        /** @return persistent station ID */
        public EntityId id() {
            return id;
        }

        /** @return X-coordinate */
        public float x() {
            return x;
        }

        /** @return Y-coordinate */
        public float y() {
            return y;
        }

        /** @return runtime faction ID или {@code -1} */
        public int factionId() {
            return factionId;
        }

        /** @return station wallet balance */
        public long walletBalanceMilliCredits() {
            return walletBalanceMilliCredits;
        }

        /** @return свободная вместимость station inventory */
        public int freeCapacity() {
            return Math.max(0, inventoryCapacity - totalStock);
        }

        /**
         * Возвращает текущий запас товара станции.
         *
         * @param itemId runtime ID товара
         * @return количество товара либо {@code 0} для некорректного ID
         */
        public int stock(int itemId) {
            return validItemId(itemId) ? stock[itemId] : 0;
        }

        /**
         * Возвращает целевой запас товара станции.
         *
         * @param itemId runtime ID товара
         * @return целевой запас либо {@code 0} для некорректного ID
         */
        public int targetStock(int itemId) {
            return validItemId(itemId) ? targetStock[itemId] : 0;
        }

        /**
         * Возвращает raw цену продажи станции.
         *
         * @param itemId runtime ID товара
         * @return цена продажи либо {@code 0} для некорректного ID
         */
        public float sellPrice(int itemId) {
            return validItemId(itemId) ? sellPrices[itemId] : 0f;
        }

        /**
         * Возвращает raw закупочную цену станции.
         *
         * @param itemId runtime ID товара
         * @return закупочная цена либо {@code 0} для некорректного ID
         */
        public float buyPrice(int itemId) {
            return validItemId(itemId) ? buyPrices[itemId] : 0f;
        }

        /**
         * Проверяет доступность торговли товаром.
         *
         * @param itemId runtime ID товара
         * @return {@code true}, если товар включён и имеет положительный target stock
         */
        public boolean isTradable(int itemId) {
            return validItemId(itemId)
                    && tradable[itemId]
                    && targetStock[itemId] > 0;
        }

        private boolean matchesLiveState(
                TransformComponent transform,
                int liveFactionId,
                WalletComponent wallet,
                InventoryComponent inventory,
                MarketComponent market) {
            return transform != null
                    && wallet != null
                    && inventory != null
                    && market != null
                    && Float.floatToIntBits(x) == Float.floatToIntBits(transform.position.x)
                    && Float.floatToIntBits(y) == Float.floatToIntBits(transform.position.y)
                    && factionId == liveFactionId
                    && walletBalanceMilliCredits == wallet.getBalanceMilliCredits()
                    && inventoryCapacity == inventory.capacity
                    && Arrays.equals(stock, inventory.stock)
                    && Arrays.equals(targetStock, market.targetStock)
                    && Arrays.equals(sellPrices, market.sellPrices)
                    && Arrays.equals(buyPrices, market.buyPrices)
                    && Arrays.equals(tradable, market.tradableItems);
        }
    }
}
