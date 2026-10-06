package com.spacesim.economy;

import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.economy.Stage18FacilityConstructionRuntime.ConstructionOrderSnapshot;
import com.spacesim.economy.Stage18FacilityConstructionRuntime.WorkBudget;
import com.spacesim.economy.Stage18FacilityConstructionRuntime.OrderStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;

/** Tick-owned construction queue using ordinary Stage-18H bills, work and installation rules. */
public final class Stage18FacilityConstructionWorkQueue {
    /** Identities owned by this queue; unrelated historical orders remain untouched. */
    public static final String PREFIX = "player-facility-construction:";
    private final Stage18FacilityConstructionRuntime construction;
    private final Stage18ResourceOntologyCatalog ontology;
    private final TreeMap<String, ConstructionOrderSnapshot> orders = new TreeMap<>();
    private long lastProcessedTick;

    /**
     * Restores queue custody from the existing industrial construction orders and clock.
     * @param construction shared physical construction authority
     * @param ontology authoritative commodity definitions
     * @param saved persisted construction orders, including unrelated orders
     * @param tick persisted industrial watermark
     */
    public Stage18FacilityConstructionWorkQueue(Stage18FacilityConstructionRuntime construction,
            Stage18ResourceOntologyCatalog ontology, List<ConstructionOrderSnapshot> saved, long tick) {
        this.construction = Objects.requireNonNull(construction);
        this.ontology = Objects.requireNonNull(ontology);
        if (tick < 0) throw new IllegalArgumentException("Negative construction watermark");
        lastProcessedTick = tick;
        var facilities = new java.util.HashSet<String>();
        for (var order : List.copyOf(saved)) {
            if (orders.putIfAbsent(order.orderId(), order) != null)
                throw new IllegalArgumentException("Duplicate construction order");
            if (!facilities.add(order.facilityInstanceId()))
                throw new IllegalArgumentException("Duplicate future facility identity");
            if (managed(order)) validate(order);
        }
    }

    /** @return deterministic immutable snapshots, retaining completed installation evidence */
    public List<ConstructionOrderSnapshot> capture() { return List.copyOf(orders.values()); }

    /** @return last completed or initially reserved world tick, for the shared industrial checkpoint */
    public long lastProcessedTick() { return lastProcessedTick; }

    /**
     * Rebinds delivered material to storage capacity without turning it into available stock.
     * @param stores canonical storage resolver
     */
    public void bindReservations(Function<String, Stage18StationStorage> stores) {
        for (String station : orders.values().stream().filter(Stage18FacilityConstructionWorkQueue::managed)
                .map(ConstructionOrderSnapshot::stationId).distinct().toList()) {
            var storage = Objects.requireNonNull(stores.apply(station), "Missing construction storage");
            if (!station.equals(storage.stationId())) throw new IllegalArgumentException("Wrong construction storage");
            storage.replaceContentsWithConstructionReservation(storage.snapshotCommodityMassByIdKg(),
                    storage.snapshotProductCountById(), reservation(station, null, Map.of()));
        }
    }

    /**
     * Reserves the exact physical bill in one atomic station transaction; installs nothing.
     * @param id unique queue order identity
     * @param facilityId future installed facility identity
     * @param definition authored facility definition
     * @param location actual allowed installation location
     * @param storage canonical executing station
     * @param tick authoritative current tick
     * @return fully supplied order awaiting physical work
     */
    public ConstructionOrderSnapshot start(String id, String facilityId, String definition, String location,
            Stage18StationStorage storage, long tick) {
        Objects.requireNonNull(id); Objects.requireNonNull(storage);
        if (!id.startsWith(PREFIX) || orders.containsKey(id)
                || orders.values().stream().anyMatch(o -> o.facilityInstanceId().equals(facilityId)))
            throw new IllegalArgumentException("Duplicate or foreign construction identity");
        if (tick < lastProcessedTick || (activeOrders() && tick != lastProcessedTick))
            throw new IllegalStateException("Process the current construction tick before starting another order");
        var initial = construction.createOrder(id, facilityId, definition, storage.stationId(), location);
        // Scratch custody executes the shared delivery validator without touching the live station.
        var scratch = new Stage18StationStorage(ontology,
                com.spacesim.content.Stage18ManufacturingProductRegistry.loadDefault(), storage.stationId(),
                storage.snapshotCapacityByStorageClassKg(), storage.snapshotCommodityMassByIdKg(), Map.of());
        var supplied = initial;
        for (var input : initial.requiredMassByCommodityKg().entrySet()) {
            var delivered = construction.deliver(supplied, scratch, input.getKey(), input.getValue());
            if (delivered.status() != Stage18FacilityConstructionRuntime.DeliveryStatus.DELIVERED)
                throw new IllegalStateException("Insufficient physical construction materials");
            supplied = delivered.order();
        }
        storage.replaceContentsWithConstructionReservation(scratch.snapshotCommodityMassByIdKg(),
                storage.snapshotProductCountById(), reservation(storage.stationId(), null, supplied.deliveredMassByCommodityKg()));
        orders.put(id, supplied);
        lastProcessedTick = tick;
        return supplied;
    }

    /**
     * Advances each order once using caller-owned shared budgets; no missed intervals are invented.
     * @param tick completed authoritative tick
     * @param stores canonical storage resolver; absent stations pause their work
     * @param budgets shared per-site work budget resolver; null pauses unavailable work
     * @return only installations that completed on this call, for the physical registry to adopt
     */
    public List<ConstructionOrderSnapshot> advance(long tick, Function<String, Stage18StationStorage> stores,
            Function<ConstructionOrderSnapshot, WorkBudget> budgets) {
        if (tick < 0) throw new IllegalArgumentException("Negative construction tick");
        if (tick <= lastProcessedTick || !activeOrders()) return List.of();
        if (tick != lastProcessedTick + 1) throw new IllegalArgumentException("Construction cannot invent missed intervals");
        var currentStores = new TreeMap<String, Stage18StationStorage>();
        var currentBudgets = new TreeMap<String, WorkBudget>();
        for (var order : orders.values()) {
            if (!active(order)) continue;
            var storage = stores.apply(order.stationId());
            if (storage == null) continue;
            if (!order.stationId().equals(storage.stationId())) throw new IllegalArgumentException("Wrong construction storage");
            currentStores.put(order.orderId(), storage);
            currentBudgets.put(order.orderId(), budgets.apply(order));
        }
        var completed = new ArrayList<ConstructionOrderSnapshot>();
        for (var order : List.copyOf(orders.values())) {
            if (!active(order)) continue;
            var storage = currentStores.get(order.orderId());
            if (storage == null) continue;
            var budget = currentBudgets.get(order.orderId());
            if (budget == null) continue;
            var result = construction.advanceWork(order, budget);
            if (result.order().status() == OrderStatus.COMPLETE) {
                storage.replaceContentsWithConstructionReservation(storage.snapshotCommodityMassByIdKg(),
                        storage.snapshotProductCountById(), reservation(order.stationId(), order.orderId(), Map.of()));
                completed.add(result.order());
            }
            orders.put(order.orderId(), result.order());
        }
        lastProcessedTick = tick;
        return List.copyOf(completed);
    }

    /**
     * Returns the exact reserved bill before any work begins; worked structures need a salvage policy.
     * @param id existing active queue order
     * @param storage canonical station receiving its own reserved material
     */
    public void cancel(String id, Stage18StationStorage storage) {
        var order = orders.get(id);
        if (order == null || !active(order) || !order.stationId().equals(storage.stationId()))
            throw new IllegalArgumentException("Unknown or foreign construction order");
        if (order.completedWorkSeconds() != 0d)
            throw new IllegalStateException("Worked construction requires an explicit salvage fate");
        var raw = new TreeMap<>(storage.snapshotCommodityMassByIdKg());
        order.deliveredMassByCommodityKg().forEach((commodity, mass) -> raw.merge(commodity, mass, Double::sum));
        storage.replaceContentsWithConstructionReservation(raw, storage.snapshotProductCountById(),
                reservation(order.stationId(), id, Map.of()));
        orders.remove(id);
    }

    private boolean activeOrders() { return orders.values().stream().anyMatch(Stage18FacilityConstructionWorkQueue::active); }
    /**
     * Identifies this queue's custody without claiming historical construction orders.
     * @param order existing physical construction order
     * @return whether this queue owns its lifecycle
     */
    public static boolean managed(ConstructionOrderSnapshot order) { return order.orderId().startsWith(PREFIX); }
    private static boolean active(ConstructionOrderSnapshot order) {
        return managed(order) && order.status() != OrderStatus.COMPLETE && order.status() != OrderStatus.CANCELLED;
    }

    private void validate(ConstructionOrderSnapshot order) {
        var expected = construction.createOrder(order.orderId(), order.facilityInstanceId(),
                order.facilityDefinitionId(), order.stationId(), order.locationTag());
        if (!expected.requiredMassByCommodityKg().equals(order.requiredMassByCommodityKg())
                || expected.requiredWorkSeconds() != order.requiredWorkSeconds()
                || !order.materialsFulfilled() || order.status() == OrderStatus.CANCELLED
                || order.status() == OrderStatus.AWAITING_MATERIALS)
            throw new IllegalArgumentException("Construction differs from the authoritative supplied bill");
        if (order.status() == OrderStatus.COMPLETE) construction.completedFacility(order);
        else if (order.remainingWorkSeconds() <= 0d)
            throw new IllegalArgumentException("Completed work must retain completion evidence");
        else if ((order.status() == OrderStatus.READY_FOR_WORK && order.completedWorkSeconds() != 0d)
                || (order.status() == OrderStatus.BUILDING && order.completedWorkSeconds() == 0d))
            throw new IllegalArgumentException("Construction status must match performed work");
    }

    private Map<String, Double> reservation(String station, String excluded, Map<String, Double> additional) {
        var result = new TreeMap<String, Double>();
        for (var order : orders.values()) {
            if (active(order) && order.stationId().equals(station) && !order.orderId().equals(excluded))
                addReservation(result, order.deliveredMassByCommodityKg());
        }
        addReservation(result, additional);
        return result;
    }

    private void addReservation(Map<String, Double> result, Map<String, Double> mass) {
        mass.forEach((commodity, kg) -> {
            var definition = ontology.findCommodity(commodity);
            if (definition == null) throw new IllegalArgumentException("Unknown construction commodity");
            result.merge(definition.storageClassId(), kg, Double::sum);
        });
    }
}
