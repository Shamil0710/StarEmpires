package com.spacesim.economy;

import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.economy.ShipyardModuleCustodyState.StoredModule;
import java.util.*;
import java.util.function.Function;

/** Finite handling of exact equipment. The caller owns actual docking, travel and joint persistence. */
public final class ShipyardModuleTransferWorkQueue {
    private final Stage18ManufacturingProductRegistry products;
    private final TreeMap<String, Order> orders = new TreeMap<>();
    private long lastTick;

    /**
     * Restores pending handling without moving equipment or crediting time.
     * @param products actual physical module definitions
     * @param saved bounded incomplete work
     */
    public ShipyardModuleTransferWorkQueue(Stage18ManufacturingProductRegistry products, State saved) {
        this.products = Objects.requireNonNull(products); Objects.requireNonNull(saved);
        lastTick = saved.lastProcessedTick();
        for (var order : saved.orders()) {
            if (order.completedHandlingKg() >= mass(order)) throw new IllegalArgumentException("Transfer is already complete");
            orders.put(order.orderId(), order);
        }
    }

    /** @return exact incomplete work and source evidence, without granting inventory */
    public State capture() { return new State(lastTick, List.copyOf(orders.values())); }

    /**
     * Checks that pending work still refers to the exact source equipment.
     * @param custody authoritative current equipment inventory
     */
    public void validateCustody(ShipyardModuleCustodyState custody) {
        var actual = new HashMap<String, StoredModule>();
        custody.modules().forEach(row -> actual.put(row.custodyId(), row));
        orders.values().forEach(order -> {
            if (!order.source().equals(actual.get(order.source().custodyId())))
                throw new IllegalArgumentException("Pending handling requires its exact original equipment");
        });
    }

    /**
     * Reserves an exact identity. Equipment stays with its source until all handling is paid.
     * @param id unique job identity
     * @param custody actual inventory
     * @param custodyId exact equipment identity
     * @param context caller-authorized actual source, destination and handling
     * @param otherReservedIds identities already reserved by other job owners
     * @param tick actual completed tick
     */
    public void start(String id, ShipyardModuleCustodyState custody, String custodyId, Context context,
            Set<String> otherReservedIds, long tick) {
        validateCustody(custody); Objects.requireNonNull(otherReservedIds);
        if (orders.size() >= State.CAPACITY || orders.containsKey(id) || tick < lastTick
                || !orders.isEmpty() && tick != lastTick || otherReservedIds.contains(custodyId)
                || orders.values().stream().anyMatch(o -> o.source().custodyId().equals(custodyId)))
            throw new IllegalArgumentException("Equipment handling start conflict");
        var move = ShipyardModuleCustodyStorage.prepareMove(custody, custodyId, products,
                context.source(), context.destination(), context.handling());
        var source = custody.modules().stream().filter(row -> row.custodyId().equals(custodyId)).findFirst().orElseThrow();
        var order = new Order(id, source, context.destination().stationId(), tick, 0);
        if (move.massKg() != mass(order)) throw new IllegalArgumentException("Physical equipment mass differs");
        orders.put(id, order); lastTick = tick;
    }

    /**
     * Releases an identity without moving it or refunding already performed handling.
     * @param id exact pending job
     */
    public void cancel(String id) {
        if (orders.remove(id) == null) throw new IllegalArgumentException("Unknown equipment handling job");
    }

    /**
     * Advances one actual completed interval against shared endpoint budgets.
     * Missing contact, incompatible handling or unavailable destination capacity pauses work.
     * @param tick actual completed tick
     * @param custody exact source inventory
     * @param contexts caller-authorized physical contacts, or null to pause
     * @param budgets actual finite shared budgets, or null to pause
     * @param otherReservedIds current reservations of other physical job owners
     * @return equipment custody and jobs completed on this interval
     */
    public Result advance(long tick, ShipyardModuleCustodyState custody, Function<Order, Context> contexts,
            Function<Order, Stage18LogisticsRuntime.TransferBudget> budgets, Set<String> otherReservedIds) {
        validateCustody(custody); Objects.requireNonNull(otherReservedIds);
        if (tick < 0) throw new IllegalArgumentException("Negative handling tick");
        if (tick <= lastTick || orders.isEmpty()) return new Result(custody, List.of());
        if (tick != lastTick + 1) throw new IllegalArgumentException("Handling cannot credit missed intervals");
        var completed = new ArrayList<Order>();
        for (var order : List.copyOf(orders.values())) {
            if (otherReservedIds.contains(order.source().custodyId())) continue;
            var context = contexts.apply(order);
            if (context == null || !context.source().stationId().equals(order.source().stationId())
                    || !context.destination().stationId().equals(order.destinationStorageId())) continue;
            ShipyardModuleCustodyStorage.Move move;
            try { move = ShipyardModuleCustodyStorage.prepareMove(custody, order.source().custodyId(), products,
                    context.source(), context.destination(), context.handling()); }
            catch (IllegalArgumentException unavailablePhysicalContact) { continue; }
            var budget = budgets.apply(order); if (budget == null) continue;
            if (budget.allocatedMassKg() > context.handling().massRateKgPerSecond() * budget.durationSeconds() + 1e-9)
                throw new IllegalArgumentException("Handling budget exceeds actual endpoint capability");
            // Each actual endpoint rate limits the supplied interval even if the caller offers a larger budget.
            double remaining = move.massKg() - order.completedHandlingKg();
            double offered = Math.min(remaining, Math.min(budget.remainingMassKg(),
                    context.handling().massRateKgPerSecond() * budget.durationSeconds()));
            if (offered <= 0) continue;
            if (offered < remaining && order.completedHandlingKg() + offered < move.massKg()) {
                budget.consume(offered);
                orders.put(order.orderId(), new Order(order.orderId(), order.source(), order.destinationStorageId(),
                        order.startedAtTick(), order.completedHandlingKg() + offered));
            } else {
                move.publish(); budget.consume(offered); custody = move.next(); orders.remove(order.orderId()); completed.add(order);
            }
        }
        lastTick = tick; return new Result(custody, List.copyOf(completed));
    }

    private double mass(Order order) {
        var product = products.findProduct(order.source().condition().assignment().moduleId());
        if (product == null || product.kind() != Stage18ManufacturingProductRegistry.ProductKind.MODULE)
            throw new IllegalArgumentException("Unknown physical handling module");
        return product.unitMassKg();
    }

    private static void text(String value) {
        if (value == null || value.isBlank() || value.length() > 512 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Invalid equipment handling identity");
    }

    /**
     * Exact incomplete handling evidence, without a second inventory owner.
     * @param orderId unique job identity
     * @param source original exact module row
     * @param destinationStorageId proposed receiving storage
     * @param startedAtTick original completed tick
     * @param completedHandlingKg actual accumulated handling mass
     */
    public record Order(String orderId, StoredModule source, String destinationStorageId, long startedAtTick, double completedHandlingKg) {
        /**
         * Validates identity, original time and finite incomplete evidence.
         * @param orderId unique job identity
         * @param source exact source equipment
         * @param destinationStorageId receiving physical owner
         * @param startedAtTick original completed tick
         * @param completedHandlingKg paid handling mass
         */
        public Order {
            text(orderId); Objects.requireNonNull(source); text(destinationStorageId);
            if (destinationStorageId.equals(source.stationId()) || startedAtTick < source.removedAtTick()
                    || !Double.isFinite(completedHandlingKg) || completedHandlingKg < 0)
                throw new IllegalArgumentException("Invalid equipment handling evidence");
        }
    }

    /**
     * Bounded pending handling checkpoint.
     * @param lastProcessedTick actual interval watermark
     * @param orders exact incomplete jobs
     */
    public record State(long lastProcessedTick, List<Order> orders) {
        /** Maximum simultaneous handling jobs. */
        public static final int CAPACITY = 128;
        /**
         * Validates unique jobs and reservations; sorts deterministic capture.
         * @param lastProcessedTick actual completed interval watermark
         * @param orders bounded incomplete work
         */
        public State {
            if (lastProcessedTick < 0 || orders == null || orders.size() > CAPACITY)
                throw new IllegalArgumentException("Invalid equipment handling bounds");
            var sorted = new TreeMap<String, Order>(); var identities = new HashSet<String>();
            for (var order : orders) if (order == null || order.startedAtTick() > lastProcessedTick
                    || sorted.putIfAbsent(order.orderId(), order) != null || !identities.add(order.source().custodyId()))
                throw new IllegalArgumentException("Duplicate handling reservation or invalid time");
            orders = List.copyOf(sorted.values());
        }
        /** @return no module or handling grants */
        public static State empty() { return new State(0, List.of()); }
    }

    /**
     * Actual authorized physical contact supplied by the caller.
     * @param source actual original owner
     * @param destination actual receiver
     * @param handling actual common endpoint capability
     */
    public record Context(Stage18StationStorage source, Stage18StationStorage destination, Stage18LogisticsRuntime.HandlingCapability handling) {
        /**
         * Requires all physical context owners.
         * @param source original physical storage
         * @param destination receiving physical storage
         * @param handling actual common handling capability
         */
        public Context { Objects.requireNonNull(source); Objects.requireNonNull(destination); Objects.requireNonNull(handling); }
    }

    /**
     * Must be persisted alongside storage and pending work by the composed owner.
     * @param custody updated exact inventory
     * @param completed jobs completed physically on this interval
     */
    public record Result(ShipyardModuleCustodyState custody, List<Order> completed) {
        /**
         * Freezes exact completion reporting.
         * @param custody resulting physical inventory
         * @param completed physically completed jobs
         */
        public Result { Objects.requireNonNull(custody); completed = List.copyOf(completed); }
    }
}
