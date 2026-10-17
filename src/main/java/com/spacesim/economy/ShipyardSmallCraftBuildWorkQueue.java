package com.spacesim.economy;

import com.spacesim.content.*;
import com.spacesim.economy.ShipyardSmallCraftBuildQueueState.Order;
import com.spacesim.economy.Stage18ShipyardRuntime.*;
import com.spacesim.ship.ShipyardEngineeringService.*;
import java.util.*;
import java.util.function.Function;

/** Full physical escrow and finite multi-interval work for authored production small craft. */
public final class ShipyardSmallCraftBuildWorkQueue {
    private final TreeMap<String, Order> orders = new TreeMap<>();
    private final Map<String, Stage18StationStorage> boundStores = new HashMap<>();
    private final Map<String, CachedPlan> currentPlans = new HashMap<>();
    private record CachedPlan(YardCapabilitySnapshot yard, WorkPlan plan) { }
    private long lastTick;
    private static final class Catalogs { private static final Stage18ResourceOntologyCatalog ONTOLOGY = Stage18ResourceOntologyLoader.loadDefault(); }
    /**
     * Recomputes each saved bill/work without creating hardware or spending work.
     * @param state retained custody and timing
     */
    public ShipyardSmallCraftBuildWorkQueue(ShipyardSmallCraftBuildQueueState state) {
        lastTick = state.lastProcessedTick();
        for (var o : state.orders()) { validate(o); orders.put(o.orderId(), o); }
    }
    /** @return immutable custody owner checkpoint */
    public ShipyardSmallCraftBuildQueueState capture() { return new ShipyardSmallCraftBuildQueueState(lastTick, List.copyOf(orders.values())); }
    /**
     * Plans only an authored production fit using actual installed capability.
     * @param design authored fit ID
     * @param yard actual installed yard
     * @return ordinary physical build plan
     */
    public static WorkPlan plan(String design, YardCapabilitySnapshot yard) {
        if (yard == null || !yard.active()) throw new IllegalArgumentException("Active build yard required");
        return Stage23SmallCraftProductionCatalogs.planner(design).planBuild(
                Stage23SmallCraftProductionCatalogs.fit(design), yard.plannerCapability());
    }
    private static YardCapabilitySnapshot designYard(String instance, String definition) {
        var d = Objects.requireNonNull(Stage23SmallCraftProductionCatalogs.shipyards().findYard(definition), "Unknown build yard");
        return new YardCapabilitySnapshot(instance, definition, YardStatus.ACTIVE,
                new ShipyardCapability(instance, d.berthDimensionsM(), d.maxServiceMassKg(), d.stage175FabricationCapabilities(),
                        d.stage175HandledRequirementIds(), d.toolingTags(), d.precisionCapability(), d.ratedEngineeringWorkRate(),
                        d.laborCapacity(), d.automationCapacity(), d.ratedIntegrationPowerW()), d.handledStorageClassIds(), d.maxHandledUnitMassKg());
    }
    private static Map<String, Double> materials(String design) {
        var result = new TreeMap<String, Double>();
        Stage23SmallCraftProductionCatalogs.shipyards().findHullProfile(Stage23SmallCraftProductionCatalogs.fit(design).hullId())
                .buildInputsKg().forEach(i -> result.merge(i.commodityId(), i.massKg(), Double::sum));
        return result;
    }
    private static Map<String, Integer> equipment(String design) {
        var result = new TreeMap<String, Integer>();
        Stage23SmallCraftProductionCatalogs.fit(design).installedModules().forEach(m -> result.merge(m.moduleId(), 1, Math::addExact));
        return result;
    }
    private static boolean handles(Order o, YardCapabilitySnapshot yard) {
        var ontology = Catalogs.ONTOLOGY; var products = Stage23SmallCraftProductionCatalogs.products();
        return o.reservedCommodityMassKg().keySet().stream().allMatch(id -> yard.handledStorageClassIds().contains(ontology.findCommodity(id).storageClassId()))
                && o.reservedProductCounts().keySet().stream().allMatch(id -> yard.handledStorageClassIds().contains(products.findProduct(id).storageClassId())
                        && products.findProduct(id).unitMassKg() <= yard.maxHandledUnitMassKg());
    }
    private static void validate(Order o) {
        var yard = designYard(o.yardInstanceId(), o.yardDefinitionId()); var p = plan(o.designId(), yard);
        if (!p.feasibility().feasible() || p.requirements().totalWorkSeconds() != o.requiredWorkSeconds()
                || !materials(o.designId()).equals(o.reservedCommodityMassKg()) || !equipment(o.designId()).equals(o.reservedProductCounts())
                || !handles(o, yard)) throw new IllegalArgumentException("Build custody/work differs from authored plan");
    }
    private Map<String, Double> reserved(String station, String excluded, Order added) {
        var mass = new TreeMap<String, Double>();
        var all = new ArrayList<>(orders.values()); if (added != null) all.add(added);
        all.sort(Comparator.comparing(Order::orderId));
        var ontology = Catalogs.ONTOLOGY; var products = Stage23SmallCraftProductionCatalogs.products();
        for (var o : all) if (o.stationId().equals(station) && !o.orderId().equals(excluded)) {
            o.reservedCommodityMassKg().forEach((id, kg) -> mass.merge(ontology.findCommodity(id).storageClassId(), kg, Double::sum));
            o.reservedProductCounts().forEach((id, n) -> mass.merge(products.findProduct(id).storageClassId(), products.findProduct(id).unitMassKg() * n, Double::sum));
        }
        return mass;
    }
    /**
     * Rebinds occupied capacity on the same canonical stores without removing stock again.
     * @param stores canonical custody resolver
     */
    public void restoreReservations(Function<String, Stage18StationStorage> stores) {
        for (var id : orders.values().stream().map(Order::stationId).distinct().toList()) {
            var s = Objects.requireNonNull(stores.apply(id)); requireStation(id, s);
            if (boundStores.containsKey(id) && boundStores.get(id) != s) throw new IllegalArgumentException("Build custody cannot change canonical store");
            s.replaceContentsWithCraftBuildReservation(s.snapshotCommodityMassByIdKg(), s.snapshotProductCountById(), reserved(id, "", null));
            boundStores.put(id, s);
        }
    }
    /**
     * Reserves a complete bare-hull/module bill, without allocating a craft identity.
     * @param id unique order identity
     * @param actor actual property owner
     * @param faction actual legal operator
     * @param design authored production fit
     * @param store canonical material owner
     * @param yard actual compatible installed yard
     * @param tick actual current tick
     */
    public void start(String id, String actor, String faction, String design, Stage18StationStorage store, YardCapabilitySnapshot yard, long tick) {
        if (boundStores.containsKey(store.stationId()) && boundStores.get(store.stationId()) != store)
            throw new IllegalArgumentException("Build custody requires the same canonical store");
        if (tick < lastTick || orders.size() >= ShipyardSmallCraftBuildQueueState.CAPACITY || orders.containsKey(id)
                || orders.values().stream().anyMatch(o -> o.stationId().equals(store.stationId()) && o.yardInstanceId().equals(yard.yardInstanceId())))
            throw new IllegalArgumentException("Build slot unavailable or stale tick");
        var p = plan(design, yard);
        if (!p.feasibility().feasible()) throw new IllegalArgumentException("Incompatible build yard");
        var o = new Order(id, actor, faction, store.stationId(), yard.yardInstanceId(), yard.yardDefinitionId(), design, tick,
                p.requirements().totalWorkSeconds(), 0, materials(design), equipment(design)); validate(o);
        if (!handles(o, yard)) throw new IllegalArgumentException("Yard cannot handle build equipment");
        var raw = new TreeMap<>(store.snapshotCommodityMassByIdKg()); var stock = new TreeMap<>(store.snapshotProductCountById());
        o.reservedCommodityMassKg().forEach((m, kg) -> { double left = raw.getOrDefault(m, 0d) - kg;
            if (left < 0) throw new IllegalArgumentException("Missing build material"); if (left == 0) raw.remove(m); else raw.put(m, left); });
        o.reservedProductCounts().forEach((m, n) -> { int left = stock.getOrDefault(m, 0) - n;
            if (left < 0) throw new IllegalArgumentException("Missing build equipment"); if (left == 0) stock.remove(m); else stock.put(m, left); });
        store.replaceContentsWithCraftBuildReservation(raw, stock, reserved(store.stationId(), "", o));
        orders.put(id, o); currentPlans.remove(id); boundStores.put(store.stationId(), store); lastTick = tick;
    }
    /**
     * Refunds untouched custody exactly once; partially worked hulls need a salvage policy.
     * @param id retained order
     * @param store canonical original store
     */
    public void cancel(String id, Stage18StationStorage store) {
        var o = Objects.requireNonNull(orders.get(id), "Unknown build order"); requireStation(o.stationId(), store);
        requireBound(o, store);
        if (o.completedWorkSeconds() != 0) throw new IllegalStateException("Build work already started");
        var raw = new TreeMap<>(store.snapshotCommodityMassByIdKg()); var stock = new TreeMap<>(store.snapshotProductCountById());
        o.reservedCommodityMassKg().forEach((m, kg) -> raw.merge(m, kg, Double::sum));
        o.reservedProductCounts().forEach((m, n) -> stock.merge(m, n, Math::addExact));
        store.replaceContentsWithCraftBuildReservation(raw, stock, reserved(o.stationId(), id, null)); orders.remove(id); currentPlans.remove(id);
    }
    /**
     * Pays at most one actual interval from shared repair/refit/build budgets.
     * @param tick actual completed world interval
     * @param contexts current authorized yards, null to suspend
     * @param budgets remaining common installed-yard budgets
     */
    public void advance(long tick, Function<Order, YardCapabilitySnapshot> contexts, Function<Order, YardWorkBudget> budgets) {
        if (tick < 0) throw new IllegalArgumentException("Negative build tick");
        if (tick <= lastTick || orders.isEmpty()) return;
        if (!orders.isEmpty() && tick != lastTick + 1) throw new IllegalArgumentException("Build cannot credit missed intervals");
        for (var o : List.copyOf(orders.values())) {
            if (o.ready()) continue;
            var yard = contexts.apply(o);
            if (yard == null || !yard.active() || !yard.yardInstanceId().equals(o.yardInstanceId())
                    || !yard.yardDefinitionId().equals(o.yardDefinitionId()) || !handles(o, yard)) continue;
            var cached = currentPlans.get(o.orderId());
            if (cached == null || !cached.yard().equals(yard)) {
                cached = new CachedPlan(yard, plan(o.designId(), yard)); currentPlans.put(o.orderId(), cached);
            }
            var p = cached.plan();
            if (!p.feasibility().feasible() || p.requirements().totalWorkSeconds() != o.requiredWorkSeconds()) continue;
            var budget = budgets.apply(o); if (budget == null) continue;
            double remaining = o.requiredWorkSeconds() - o.completedWorkSeconds();
            double paid = budget.allocate(Math.min(remaining, budget.remainingWorkSeconds()));
            if (paid > 0) orders.put(o.orderId(), o.withWork(paid == remaining ? o.requiredWorkSeconds() : o.completedWorkSeconds() + paid));
        }
        lastTick = tick;
    }
    private static void requireStation(String expected, Stage18StationStorage store) {
        if (!store.stationId().equals(expected)) throw new IllegalArgumentException("Wrong build custody owner");
    }
    private void requireBound(Order order, Stage18StationStorage store) {
        if (boundStores.get(order.stationId()) != store || !store.craftBuildReservation().equals(reserved(order.stationId(), "", null)))
            throw new IllegalArgumentException("Build proof lacks its exact canonical physical escrow");
    }
    /**
     * Produces a single-use, queue-bound completion proof only after actual full work.
     * @param id completed retained order
     * @param store actual original custody owner
     * @return completion proof, still retaining escrow until consumed
     */
    public CompletionPermit completionPermit(String id, Stage18StationStorage store) {
        var o = Objects.requireNonNull(orders.get(id), "Unknown build order"); requireStation(o.stationId(), store);
        requireBound(o, store);
        if (!o.ready()) throw new IllegalStateException("Build work incomplete");
        return new CompletionPermit(o, store);
    }
    /** Unforgeable outside its queue; stale, duplicate and cross-store consumption fails closed. */
    public final class CompletionPermit {
        private final Order order; private final Stage18StationStorage store; private boolean consumed;
        private CompletionPermit(Order order, Stage18StationStorage store) { this.order = order; this.store = store; }
        /** @return immutable paid build evidence */
        public Order order() { return order; }
        /** @return recomputed authored plan; operating resources are not granted */
        public WorkPlan workPlan() { return plan(order.designId(), designYard(order.yardInstanceId(), order.yardDefinitionId())); }
        /** Releases paid physical custody exactly once, after the publisher's pure preflight. */
        public void consume() {
            if (consumed || orders.get(order.orderId()) != order) throw new IllegalStateException("Stale build completion proof");
            requireBound(order, store);
            store.replaceContentsWithCraftBuildReservation(store.snapshotCommodityMassByIdKg(), store.snapshotProductCountById(),
                    reserved(order.stationId(), order.orderId(), null));
            orders.remove(order.orderId()); consumed = true;
            currentPlans.remove(order.orderId());
        }
    }
}
