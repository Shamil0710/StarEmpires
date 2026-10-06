package com.spacesim.economy;

import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.economy.Stage18LogisticsRuntime.HandlingCapability;
import com.spacesim.economy.Stage18LogisticsRuntime.TransferBudget;
import com.spacesim.world.FleetId;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/** Tick-owned finite handling of reserved fresh products, distinct from individual used equipment. */
public final class FinishedProductTransferWorkQueue {
    private final Stage18ManufacturingProductRegistry products;
    private final TreeMap<String, Order> orders = new TreeMap<>();
    private final Set<String> boundSources = new TreeSet<>();
    private long lastTick;

    /**
     * Restores exact pending work without moving stock or awarding handling time.
     * @param products authoritative finished-product vocabulary
     * @param saved bounded pending handling state
     */
    public FinishedProductTransferWorkQueue(Stage18ManufacturingProductRegistry products, State saved) {
        this.products = Objects.requireNonNull(products); Objects.requireNonNull(saved);
        lastTick = saved.lastProcessedTick();
        for (var order : saved.orders()) {
            if (order.completedHandlingKg() >= mass(order)) throw new IllegalArgumentException("Product handling is already complete");
            orders.put(order.orderId(), order);
        }
    }

    /** @return exact unfinished handling and source reservations */
    public State capture() { return new State(lastTick, List.copyOf(orders.values())); }

    /**
     * Rebinds queue-owned counts to canonical live source storage; stock is not duplicated.
     * @param stores canonical physical source resolver
     */
    public void bindReservations(Function<String, Stage18StationStorage> stores) {
        var reservations = reservations(null);
        var allSources = new TreeSet<>(boundSources); allSources.addAll(reservations.keySet());
        // Validate all physical owners before modifying any reservation layer.
        for (String id : allSources) {
            var store = Objects.requireNonNull(stores.apply(id), "Missing product handling source");
            if (!id.equals(store.stationId())) throw new IllegalArgumentException("Wrong product handling source");
            for (var entry : reservations.getOrDefault(id, Map.of()).entrySet())
                if (store.snapshotProductCountById().getOrDefault(entry.getKey(), 0) < entry.getValue())
                    throw new IllegalArgumentException("Product handling source has lost reserved physical units");
        }
        for (String id : allSources) stores.apply(id).bindProductHandlingReservations(reservations.getOrDefault(id, Map.of()));
        boundSources.clear(); boundSources.addAll(reservations.keySet());
    }

    /**
     * Reserves actual source counts at the current completed tick; no handling occurs yet.
     * @param order exact zero-progress job
     * @param context current authorized physical contact
     * @param stores canonical resolver for all existing source owners
     * @param tick authoritative current tick
     */
    public void start(Order order, Context context, Function<String, Stage18StationStorage> stores, long tick) {
        Objects.requireNonNull(order); Objects.requireNonNull(context);
        if (orders.size() >= State.CAPACITY || orders.containsKey(order.orderId()) || order.completedHandlingKg() != 0d
                || order.startedAtTick() != tick || tick < lastTick || !orders.isEmpty() && tick != lastTick
                || orders.values().stream().anyMatch(o -> o.fleetId().equals(order.fleetId())))
            throw new IllegalArgumentException("Product handling identity or tick conflict");
        if (!ready(order, context, false)) throw new IllegalArgumentException("Product handling has no feasible physical contact");
        if (stores.apply(order.sourceStorageId()) != context.source()) throw new IllegalArgumentException("Product source is not canonical");
        orders.put(order.orderId(), order);
        try { bindReservations(stores); }
        catch (RuntimeException failure) { orders.remove(order.orderId()); throw failure; }
        lastTick = tick;
    }

    /**
     * Cancels pending handling, leaving products at the source and discarding spent handling time.
     * @param id exact pending job
     * @param stores canonical live sources
     */
    public void cancel(String id, Function<String, Stage18StationStorage> stores) {
        var order = orders.remove(id);
        if (order == null) throw new IllegalArgumentException("Unknown product handling job");
        try { bindReservations(stores); }
        catch (RuntimeException failure) { orders.put(id, order); throw failure; }
    }

    /**
     * Claims only the next completed tick and shares the caller's actual endpoint handling budgets.
     * Products remain reserved at the source until all handling has been paid and publication succeeds.
     * @param tick authoritative completed tick
     * @param stores canonical physical source resolver
     * @param contexts current authorized contact, or null to pause
     * @param budgets shared finite interval budgets, or null to pause
     * @param publisher atomic owning freight publication using the one-use completion permit
     * @return jobs whose physical transfer committed on this interval
     */
    public List<Order> advance(long tick, Function<String, Stage18StationStorage> stores, Function<Order, Context> contexts,
            Function<Order, TransferBudget> budgets, Predicate<CompletionPermit> publisher) {
        if (tick < 0) throw new IllegalArgumentException("Negative product handling tick");
        if (tick <= lastTick || orders.isEmpty()) return List.of();
        if (tick != lastTick + 1) throw new IllegalArgumentException("Product handling cannot invent missed intervals");
        bindReservations(stores);
        var completed = new ArrayList<Order>();
        for (var order : List.copyOf(orders.values())) {
            var context = contexts.apply(order);
            if (context == null || !ready(order, context, true)) continue;
            var budget = budgets.apply(order); if (budget == null) continue;
            if (budget.allocatedMassKg() > context.handling().massRateKgPerSecond() * budget.durationSeconds() + 1e-9)
                throw new IllegalArgumentException("Product handling budget exceeds its actual endpoint");
            double remaining = mass(order) - order.completedHandlingKg();
            double offered = Math.min(remaining, budget.remainingMassKg());
            if (offered <= 0d) continue;
            if (offered < remaining && order.completedHandlingKg() + offered < mass(order)) {
                budget.consume(offered);
                orders.put(order.orderId(), new Order(order.orderId(), order.fleetId(), order.sourceStorageId(), order.destinationStorageId(),
                        order.productId(), order.count(), order.startedAtTick(), order.completedHandlingKg() + offered));
                continue;
            }
            // Release only this fully handled reservation for atomic publication; others remain unavailable.
            context.source().bindProductHandlingReservations(reservations(order.orderId()).getOrDefault(order.sourceStorageId(), Map.of()));
            var permit = new CompletionPermit(order, context, mass(order));
            boolean published;
            try { published = publisher.test(permit); }
            catch (RuntimeException failure) {
                permit.active = false;
                if (!permit.published()) bindReservations(stores);
                throw failure;
            }
            permit.active = false;
            if (published != permit.published()) throw new IllegalStateException("Product publisher result differs from physical commit");
            if (!published) { bindReservations(stores); continue; }
            budget.consume(offered); orders.remove(order.orderId()); completed.add(order); bindReservations(stores);
        }
        lastTick = tick;
        return List.copyOf(completed);
    }

    private boolean ready(Order order, Context context, boolean reserved) {
        var product = products.findProduct(order.productId());
        return product != null && order.sourceStorageId().equals(context.source().stationId())
                && order.destinationStorageId().equals(context.destination().stationId())
                && context.source() != context.destination()
                && context.handling().supportedStorageClassIds().contains(product.storageClassId())
                && context.handling().maxUnitMassKg() + 1e-9 >= product.unitMassKg()
                && (reserved ? context.source().snapshotProductCountById().getOrDefault(order.productId(), 0)
                        : context.source().productCount(order.productId())) >= order.count()
                && context.destination().canAddProduct(order.productId(), order.count())
                && context.destinationRemainingGrossMassKg() + 1e-9 >= mass(order);
    }

    private double mass(Order order) {
        var product = products.findProduct(order.productId());
        if (product == null) throw new IllegalArgumentException("Unknown handling product");
        double mass = product.unitMassKg() * order.count();
        if (!Double.isFinite(mass)) throw new IllegalArgumentException("Product handling mass overflow");
        return mass;
    }

    private Map<String, Map<String, Integer>> reservations(String excluded) {
        var bySource = new TreeMap<String, Map<String, Integer>>();
        for (var order : orders.values()) if (!order.orderId().equals(excluded))
            bySource.computeIfAbsent(order.sourceStorageId(), ignored -> new TreeMap<>()).merge(order.productId(), order.count(), Math::addExact);
        return bySource;
    }

    private static void text(String value) {
        if (value == null || value.isBlank() || value.length() > 512 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Invalid product handling identity");
    }

    /**
     * Actual unfinished physical job, without a second product inventory.
     * @param orderId unique pending job
     * @param fleetId carrying personal freight identity
     * @param sourceStorageId canonical original owner
     * @param destinationStorageId canonical receiver
     * @param productId authored countable product
     * @param count reserved whole units
     * @param startedAtTick actual original completed tick
     * @param completedHandlingKg actual already spent processing mass
     */
    public record Order(String orderId, FleetId fleetId, String sourceStorageId, String destinationStorageId,
            String productId, int count, long startedAtTick, double completedHandlingKg) {
        /**
         * Validates a bounded immutable handling intent.
         * @param orderId unique pending job
         * @param fleetId carrying personal freight identity
         * @param sourceStorageId canonical original owner
         * @param destinationStorageId canonical receiver
         * @param productId authored countable product
         * @param count reserved whole units
         * @param startedAtTick actual original completed tick
         * @param completedHandlingKg actual already spent processing mass
         */
        public Order {
            text(orderId); Objects.requireNonNull(fleetId); text(sourceStorageId); text(destinationStorageId); text(productId);
            String hold = com.spacesim.persistence.Stage20FreightPersistentState.cargoHoldId(fleetId);
            if (sourceStorageId.equals(destinationStorageId) || sourceStorageId.equals(hold) == destinationStorageId.equals(hold)
                    || count <= 0 || startedAtTick < 0 || !Double.isFinite(completedHandlingKg) || completedHandlingKg < 0d)
                throw new IllegalArgumentException("Invalid physical product handling evidence");
        }
        /** @return whether the station supplies the freight hold */
        public boolean loading() { return destinationStorageId.equals(com.spacesim.persistence.Stage20FreightPersistentState.cargoHoldId(fleetId)); }
        /** @return physical station at this loading or unloading contact */
        public String stationId() { return loading() ? sourceStorageId : destinationStorageId; }
    }

    /**
     * Bounded pending-work checkpoint.
     * @param lastProcessedTick actual interval watermark
     * @param orders unfinished jobs
     */
    public record State(long lastProcessedTick, List<Order> orders) {
        /** Maximum simultaneous product handling jobs. */
        public static final int CAPACITY = 128;
        /**
         * Validates order and carrying-owner uniqueness without granting work.
         * @param lastProcessedTick actual interval watermark
         * @param orders bounded unfinished jobs
         */
        public State {
            if (lastProcessedTick < 0 || orders == null || orders.size() > CAPACITY)
                throw new IllegalArgumentException("Invalid product handling state bounds");
            var sorted = new TreeMap<String, Order>(); var fleets = new HashSet<FleetId>();
            for (var order : orders) if (order == null || order.startedAtTick() > lastProcessedTick
                    || sorted.putIfAbsent(order.orderId(), order) != null || !fleets.add(order.fleetId()))
                throw new IllegalArgumentException("Duplicate product handling owner or invalid time");
            orders = List.copyOf(sorted.values());
        }
        /** @return no pending product or work grants */
        public static State empty() { return new State(0, List.of()); }
    }

    /**
     * Current authorized physical source, receiver and finite endpoint handling.
     * @param source actual original product owner
     * @param destination actual receiver
     * @param handling actual common endpoint capability
     * @param destinationRemainingGrossMassKg current whole-hold capacity, or finite station bound
     */
    public record Context(Stage18StationStorage source, Stage18StationStorage destination, HandlingCapability handling,
            double destinationRemainingGrossMassKg) {
        /**
         * Validates current physical contact resources.
         * @param source actual original product owner
         * @param destination actual receiver
         * @param handling actual common endpoint capability
         * @param destinationRemainingGrossMassKg current remaining whole-owner capacity
         */
        public Context {
            Objects.requireNonNull(source); Objects.requireNonNull(destination); Objects.requireNonNull(handling);
            if (!Double.isFinite(destinationRemainingGrossMassKg) || destinationRemainingGrossMassKg < 0d)
                throw new IllegalArgumentException("Invalid gross product destination capacity");
        }
    }

    /** One-use publication authority issued only after the queue pays all required finite handling. */
    public static final class CompletionPermit {
        private final Order order;
        private final Context context;
        private final double massKg;
        private final Stage18StationStorage.StationStorageSnapshot sourceSnapshot;
        private final Stage18StationStorage.StationStorageSnapshot destinationSnapshot;
        private boolean published;
        private boolean active = true;
        private CompletionPermit(Order order, Context context, double massKg) {
            this.order = order; this.context = context; this.massKg = massKg;
            sourceSnapshot = context.source().snapshot(); destinationSnapshot = context.destination().snapshot();
        }
        /** @return exact completed handling request */
        public Order order() { return order; }
        /** @return exact authorized source reference */
        public Stage18StationStorage source() { return context.source(); }
        /** @return exact authorized destination reference */
        public Stage18StationStorage destination() { return context.destination(); }
        /** @return full physical product mass whose handling has been paid */
        public double massKg() { return massKg; }
        /** @return whether this authority has already published its unique physical move */
        public boolean published() { return published; }
        /**
         * Publishes actual whole products once; it allocates no new handling budget.
         * @return whether the exact unchanged physical transaction committed
         */
        public boolean publish() {
            if (!active || published || !sourceSnapshot.equals(source().snapshot()) || !destinationSnapshot.equals(destination().snapshot())
                    || source().productCount(order.productId()) < order.count() || !destination().canAddProduct(order.productId(), order.count()))
                return false;
            source().removeProduct(order.productId(), order.count()); destination().addProduct(order.productId(), order.count());
            published = true; return true;
        }
    }
}
