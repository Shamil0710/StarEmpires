package com.spacesim.economy;

import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage23YardConstructionCatalog;
import com.spacesim.economy.Stage18FacilityConstructionRuntime.WorkBudget;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;

/** Physical yard structures use real station stock and the existing shared construction-work budget. */
public final class Stage23YardConstructionWorkQueue {
    private static final double MATERIAL_EPSILON_KG = 1e-9d;
    private final Stage23YardConstructionCatalog catalog;
    private final Stage18ResourceOntologyCatalog ontology;
    private final TreeMap<String, Order> orders = new TreeMap<>();
    private long lastProcessedTick;

    /**
     * Restores exact unfinished or completed structures without installing or powering a yard.
     * @param catalog physical construction specifications
     * @param ontology actual material and storage classes
     * @param state retained work and construction evidence
     */
    public Stage23YardConstructionWorkQueue(Stage23YardConstructionCatalog catalog, Stage18ResourceOntologyCatalog ontology, State state) {
        this.catalog = Objects.requireNonNull(catalog); this.ontology = Objects.requireNonNull(ontology);
        Objects.requireNonNull(state);
        if (!state.orders().isEmpty() && !catalog.fingerprint().equals(state.specificationFingerprint()))
            throw new IllegalArgumentException("Yard construction specification differs from retained contract");
        lastProcessedTick = state.lastProcessedTick();
        for (var order : state.orders()) { validate(order); orders.put(order.orderId(), order); }
    }

    /** @return immutable retained structures; snapshots do not duplicate available station stock */
    public State capture() { return new State(catalog.fingerprint(), lastProcessedTick, List.copyOf(orders.values())); }

    /**
     * Binds custody to its actual source storage capacity after composed restore.
     * @param stores canonical station storage resolver
     */
    public void bindReservations(Function<String, Stage18StationStorage> stores) {
        for (String station : orders.values().stream().map(Order::stationId).distinct().toList()) {
            var store = Objects.requireNonNull(stores.apply(station)); requireStore(station, store);
            store.replaceContentsWithYardConstructionReservation(store.snapshotCommodityMassByIdKg(),
                    store.snapshotProductCountById(), reservations(station, null, null));
        }
    }

    /**
     * Atomically reserves a complete physical bill; no completed yard or resources are created.
     * @param orderId unique retained construction order
     * @param yardInstanceId future installed yard identity
     * @param yardDefinitionId authored construction target
     * @param locationTag actual compatible installation location
     * @param storage executing station
     * @param tick authoritative current tick
     * @return ready order with real reserved material
     */
    public Order start(String orderId, String yardInstanceId, String yardDefinitionId, String locationTag,
            Stage18StationStorage storage, long tick) {
        if (orders.size() >= State.CAPACITY || orders.containsKey(orderId)
                || orders.values().stream().anyMatch(o -> o.yardInstanceId().equals(yardInstanceId)))
            throw new IllegalArgumentException("Duplicate or excessive yard construction");
        if (tick < lastProcessedTick || active() && tick != lastProcessedTick)
            throw new IllegalStateException("Yard construction requires the current processed tick");
        var order = new Order(orderId, yardInstanceId, yardDefinitionId, storage.stationId(), locationTag, tick, 0);
        validate(order); var spec = catalog.find(yardDefinitionId);
        var raw = new TreeMap<>(storage.snapshotCommodityMassByIdKg());
        for (var input : spec.requiredMassByCommodityKg().entrySet()) {
            double available = raw.getOrDefault(input.getKey(), 0d);
            if (available < input.getValue()) throw new IllegalStateException("Insufficient physical yard construction material");
            double remaining = available - input.getValue();
            if (remaining == 0) raw.remove(input.getKey()); else raw.put(input.getKey(), remaining);
        }
        storage.replaceContentsWithYardConstructionReservation(raw, storage.snapshotProductCountById(), reservations(storage.stationId(), null, order));
        orders.put(orderId, order); lastProcessedTick = tick; return order;
    }

    /**
     * Plans a bounded construction site containing no granted material or working structure.
     * Deliveries on subsequent real intervals move only the authored bill out of station stock.
     * @param orderId retained project identity
     * @param yardInstanceId future structure identity
     * @param yardDefinitionId authored physical design
     * @param locationTag actual compatible installation location
     * @param storage actual source station
     * @param tick current completed world tick
     * @return empty material site, unable to perform construction work
     */
    public Order plan(String orderId, String yardInstanceId, String yardDefinitionId, String locationTag,
            Stage18StationStorage storage, long tick) {
        if (orders.size() >= State.CAPACITY || orders.containsKey(orderId)
                || orders.values().stream().anyMatch(o -> o.yardInstanceId().equals(yardInstanceId)))
            throw new IllegalArgumentException("Duplicate or excessive yard construction");
        if (tick < lastProcessedTick || active() && tick != lastProcessedTick)
            throw new IllegalStateException("Yard construction requires the current processed tick");
        var order = new Order(orderId, yardInstanceId, yardDefinitionId, storage.stationId(), locationTag, tick, 0, true, Map.of());
        validate(order); orders.put(orderId, order); lastProcessedTick = tick; return order;
    }

    /**
     * Actual handling capability and the caller's shared finite station interval.
     * @param capability physical source endpoint capability
     * @param budget shared remaining handling budget
     */
    public record MaterialDeliveryContext(Stage18LogisticsRuntime.HandlingCapability capability,
            Stage18LogisticsRuntime.TransferBudget budget) {
        /**
         * Requires real capability and budget owners.
         * @param capability actual endpoint
         * @param budget finite shared interval
         */
        public MaterialDeliveryContext { Objects.requireNonNull(capability); Objects.requireNonNull(budget); }
    }

    /**
     * Uses the remaining caller-owned site work, sharing the same finite budget with facility construction.
     * @param tick completed consecutive world tick
     * @param stores canonical source storages
     * @param budgets actual shared fabrication interval; null pauses work
     * @return newly completed structures, without installing or supplying power to them
     */
    public List<Order> advance(long tick, Function<String, Stage18StationStorage> stores, Function<Order, WorkBudget> budgets) {
        return advance(tick, stores, budgets, order -> null);
    }

    /**
     * Delivers finite material on the same tick authority before advancing paid construction work.
     * Site custody is limited to the exact bill and cannot supply general storage or another project.
     * @param tick completed consecutive world tick
     * @param stores actual station stock owners
     * @param budgets shared remaining engineering work
     * @param deliveries actual shared endpoint handling; null pauses incomplete deliveries
     * @return newly completed structures with their exact retained material evidence
     */
    public List<Order> advance(long tick, Function<String, Stage18StationStorage> stores, Function<Order, WorkBudget> budgets,
            Function<Order, MaterialDeliveryContext> deliveries) {
        if (tick < 0) throw new IllegalArgumentException("Negative yard construction tick");
        if (tick <= lastProcessedTick || !active()) return List.of();
        if (tick != lastProcessedTick + 1) throw new IllegalArgumentException("Yard construction cannot invent missed work");
        var result = new ArrayList<Order>();
        for (var order : List.copyOf(orders.values())) {
            var spec = catalog.find(order.yardDefinitionId());
            if (order.completedWorkSeconds() == spec.requiredWorkSeconds()) continue;
            var storage = stores.apply(order.stationId()); if (storage == null) continue; requireStore(order.stationId(), storage);
            if (order.stagedAtSite()) {
                var context = deliveries.apply(order);
                if (context != null) {
                    var capacities = new TreeMap<String, Double>();
                    spec.requiredMassByCommodityKg().forEach((id, kg) -> capacities.merge(ontology.findCommodity(id).storageClassId(), kg, Double::sum));
                    var site = new Stage18StationStorage(ontology, com.spacesim.content.Stage22CivilianMiningProductionPath.loadProducts(),
                            "yard-construction-site:" + order.orderId(), capacities, order.deliveredMassByCommodityKg(), Map.of());
                    var logistics = new Stage18LogisticsRuntime(ontology, com.spacesim.content.Stage22CivilianMiningProductionPath.loadProducts());
                    for (var input : spec.requiredMassByCommodityKg().entrySet()) {
                        double amount = Math.min(input.getValue() - site.commodityMassKg(input.getKey()),
                                Math.min(storage.commodityMassKg(input.getKey()), context.budget().remainingMassKg()));
                        if (amount > 0) logistics.transferCommodity(storage, site, input.getKey(), amount, context.capability(), context.budget());
                    }
                    order = new Order(order.orderId(), order.yardInstanceId(), order.yardDefinitionId(), order.stationId(),
                            order.locationTag(), order.startedAtTick(), order.completedWorkSeconds(), true, site.snapshotCommodityMassByIdKg());
                    orders.put(order.orderId(), order);
                }
                var delivered = order.deliveredMassByCommodityKg();
                if (spec.requiredMassByCommodityKg().entrySet().stream()
                        .anyMatch(e -> delivered.getOrDefault(e.getKey(), 0d) + MATERIAL_EPSILON_KG < e.getValue())) continue;
                // The shared storage settlement discards sub-epsilon residuals. Canonicalize the
                // fully settled bill at the same precision before recording construction work.
                if (!delivered.equals(spec.requiredMassByCommodityKg())) {
                    order = new Order(order.orderId(), order.yardInstanceId(), order.yardDefinitionId(), order.stationId(),
                            order.locationTag(), order.startedAtTick(), order.completedWorkSeconds(), true, spec.requiredMassByCommodityKg());
                    orders.put(order.orderId(), order);
                }
            }
            var budget = budgets.apply(order);
            if (budget == null || !budget.capabilityTags().containsAll(spec.requiredCapabilityTags())) continue;
            double work = Math.min(spec.requiredWorkSeconds() - order.completedWorkSeconds(), budget.remainingWorkSeconds());
            if (work == 0) continue;
            var updated = new Order(order.orderId(), order.yardInstanceId(), order.yardDefinitionId(), order.stationId(),
                    order.locationTag(), order.startedAtTick(), Math.min(spec.requiredWorkSeconds(), order.completedWorkSeconds() + work),
                    order.stagedAtSite(), order.deliveredMassByCommodityKg());
            if (updated.completedWorkSeconds() == spec.requiredWorkSeconds()) {
                storage.replaceContentsWithYardConstructionReservation(storage.snapshotCommodityMassByIdKg(),
                        storage.snapshotProductCountById(), reservations(order.stationId(), order.orderId(), null));
                result.add(updated);
            }
            budget.consume(work); orders.put(order.orderId(), updated);
        }
        lastProcessedTick = tick; return List.copyOf(result);
    }

    /**
     * Cancels an unworked structure, returning its actual bill to the same physical station.
     * @param orderId retained unworked order
     * @param storage actual source receiving its own material
     */
    public void cancel(String orderId, Stage18StationStorage storage) {
        var order = orders.get(orderId);
        if (order == null || order.completedWorkSeconds() != 0) throw new IllegalStateException("Worked construction requires an explicit salvage policy");
        requireStore(order.stationId(), storage);
        var raw = new TreeMap<>(storage.snapshotCommodityMassByIdKg());
        var returned = order.stagedAtSite() ? order.deliveredMassByCommodityKg() : catalog.find(order.yardDefinitionId()).requiredMassByCommodityKg();
        returned.forEach((id, kg) -> raw.merge(id, kg, Double::sum));
        storage.replaceContentsWithYardConstructionReservation(raw, storage.snapshotProductCountById(), reservations(order.stationId(), orderId, null));
        orders.remove(orderId);
    }

    /**
     * Admits only an exact retained completion as a disabled physical structure.
     * Power, engineering work rate, staff and automation require separate allocation.
     * @param order exact completed order retained by this queue
     * @return intact installed structure with no operating resources
     */
    public Stage18ShipyardRuntime.InstalledYardState completedYard(Order order) {
        Objects.requireNonNull(order, "order");
        if (!order.equals(orders.get(order.orderId()))
                || order.completedWorkSeconds() != catalog.find(order.yardDefinitionId()).requiredWorkSeconds())
            throw new IllegalArgumentException("Yard installation requires exact retained completed construction");
        return new Stage18ShipyardRuntime.InstalledYardState(order.yardInstanceId(), order.yardDefinitionId(),
                1d, 0d, 0d, 0, 0, false);
    }

    private boolean active() { return orders.values().stream().anyMatch(o -> o.completedWorkSeconds() < catalog.find(o.yardDefinitionId()).requiredWorkSeconds()); }
    private void validate(Order order) {
        var spec = catalog.find(order.yardDefinitionId());
        if (spec == null || order.completedWorkSeconds() > spec.requiredWorkSeconds()
                || !com.spacesim.content.Stage22CivilianMiningProductionPath.loadRuntimeShipyards().findYard(order.yardDefinitionId()).allowedLocationTags().contains(order.locationTag()))
            throw new IllegalArgumentException("Invalid physical yard construction target, work or location");
        if (!order.stagedAtSite() && !order.deliveredMassByCommodityKg().isEmpty())
            throw new IllegalArgumentException("Historical warehouse custody cannot claim a second material site");
        for (var entry : order.deliveredMassByCommodityKg().entrySet())
            if (!spec.requiredMassByCommodityKg().containsKey(entry.getKey()) || entry.getValue() > spec.requiredMassByCommodityKg().get(entry.getKey()))
                throw new IllegalArgumentException("Construction site custody exceeds its exact physical bill");
        if (order.stagedAtSite() && order.completedWorkSeconds() > 0 && !order.deliveredMassByCommodityKg().equals(spec.requiredMassByCommodityKg()))
            throw new IllegalArgumentException("Construction work requires the whole physically delivered bill");
    }
    private static void requireStore(String station, Stage18StationStorage storage) {
        if (!station.equals(storage.stationId())) throw new IllegalArgumentException("Wrong yard construction source storage");
    }
    private Map<String, Double> reservations(String station, String excluded, Order additional) {
        var result = new TreeMap<String, Double>();
        for (var order : orders.values()) if (!order.stagedAtSite() && order.stationId().equals(station) && !order.orderId().equals(excluded)
                && order.completedWorkSeconds() < catalog.find(order.yardDefinitionId()).requiredWorkSeconds()) add(result, order);
        if (additional != null) add(result, additional); return result;
    }
    private void add(Map<String, Double> reservation, Order order) {
        catalog.find(order.yardDefinitionId()).requiredMassByCommodityKg().forEach((id, kg) -> {
            var commodity = ontology.findCommodity(id);
            if (commodity == null) throw new IllegalArgumentException("Unknown yard construction material");
            reservation.merge(commodity.storageClassId(), kg, Double::sum);
        });
    }

    /**
     * Retained structure evidence and consecutive-work watermark.
     * @param specificationFingerprint exact authored bill/work identity
     * @param lastProcessedTick actual completed or reservation tick
     * @param orders bounded unique structures, retaining completed work
     */
    public record State(String specificationFingerprint, long lastProcessedTick, List<Order> orders) {
        /** Maximum retained construction structures. */
        public static final int CAPACITY = 128;
        /**
         * Validates bounded identities and temporal causality.
         * @param specificationFingerprint exact content identity
         * @param lastProcessedTick completed world tick
         * @param orders actual retained structures
         */
        public State {
            if (specificationFingerprint == null || !specificationFingerprint.matches("[0-9a-f]{64}") || lastProcessedTick < 0)
                throw new IllegalArgumentException("Invalid yard construction contract identity or tick");
            orders = List.copyOf(orders);
            if (orders.size() > CAPACITY || orders.stream().map(Order::orderId).distinct().count() != orders.size()
                    || orders.stream().map(Order::yardInstanceId).distinct().count() != orders.size()
                    || orders.stream().anyMatch(o -> o.startedAtTick() > lastProcessedTick
                            || (o.completedWorkSeconds() > 0 || !o.deliveredMassByCommodityKg().isEmpty()) && o.startedAtTick() == lastProcessedTick))
                throw new IllegalArgumentException("Invalid yard construction identities or future work");
            orders = orders.stream().sorted(java.util.Comparator.comparing(Order::orderId)).toList();
        }
        /** @return no structural, inventory or work grants */
        public static State empty() { return new State(Stage23YardConstructionCatalog.loadDefault().fingerprint(), 0, List.of()); }
    }

    /**
     * Structural work; installation and resource admission are distinct physical transitions.
     * @param orderId retained construction identity
     * @param yardInstanceId future yard identity
     * @param yardDefinitionId existing yard design
     * @param stationId actual station
     * @param locationTag compatible physical location
     * @param startedAtTick actual reservation tick
     * @param completedWorkSeconds finite performed engineering work
     * @param stagedAtSite whether materials are physically delivered into a bounded site rather than reserved in the warehouse
     * @param deliveredMassByCommodityKg exact site custody, incorporated into the structure on completion
     */
    public record Order(String orderId, String yardInstanceId, String yardDefinitionId, String stationId, String locationTag,
            long startedAtTick, double completedWorkSeconds, boolean stagedAtSite, Map<String, Double> deliveredMassByCommodityKg) {
        /**
         * Preserves historical full-bill warehouse custody without granting a second material site.
         * @param orderId retained construction identity
         * @param yardInstanceId installed identity
         * @param yardDefinitionId authored design
         * @param stationId actual source station
         * @param locationTag physical location
         * @param startedAtTick reservation tick
         * @param completedWorkSeconds actually performed work
         */
        public Order(String orderId, String yardInstanceId, String yardDefinitionId, String stationId, String locationTag,
                long startedAtTick, double completedWorkSeconds) {
            this(orderId, yardInstanceId, yardDefinitionId, stationId, locationTag, startedAtTick, completedWorkSeconds, false, Map.of());
        }
        /**
         * Rejects malformed identities and non-physical work.
         * @param orderId construction identity
         * @param yardInstanceId installed identity
         * @param yardDefinitionId authored design
         * @param stationId actual source
         * @param locationTag physical location
         * @param startedAtTick reservation tick
         * @param completedWorkSeconds performed work
         * @param stagedAtSite actual site custody instead of historical warehouse reserve
         * @param deliveredMassByCommodityKg physically delivered material
         */
        public Order {
            for (String id : new String[]{orderId, yardInstanceId, yardDefinitionId, stationId, locationTag})
                if (id == null || id.isBlank() || id.length() > 512 || id.contains("\n") || id.contains("\r"))
                    throw new IllegalArgumentException("Invalid yard construction identity");
            if (startedAtTick < 0 || !Double.isFinite(completedWorkSeconds) || completedWorkSeconds < 0)
                throw new IllegalArgumentException("Invalid yard construction time or work");
            var delivered = new TreeMap<String, Double>();
            Objects.requireNonNull(deliveredMassByCommodityKg).forEach((id, kg) -> {
                if (id == null || id.isBlank() || id.length() > 512 || kg == null || !Double.isFinite(kg) || kg <= 0)
                    throw new IllegalArgumentException("Invalid delivered yard material");
                delivered.put(id, kg);
            });
            if (delivered.size() > 64) throw new IllegalArgumentException("Excessive material site entries");
            deliveredMassByCommodityKg = java.util.Collections.unmodifiableMap(delivered);
        }
    }
}
