package com.spacesim.economy;

import com.spacesim.content.Stage18ManufacturingProductRegistry;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;

/** Binds individual equipment custody to actual station capacity alongside manufacturing reserves. */
public final class ShipyardModuleCustodyStorage {
    private ShipyardModuleCustodyStorage() { }

    /**
     * Binds one actual owner, also clearing its occupancy after the final module leaves.
     * @param state current exact inventory
     * @param products actual module definitions
     * @param storage actual station or ship storage owner
     */
    public static void bindOwner(ShipyardModuleCustodyState state, Stage18ManufacturingProductRegistry products, Stage18StationStorage storage) {
        storage.replaceModuleCustodyReservation(massByStation(state, products).getOrDefault(storage.stationId(), Map.of()));
    }

    /**
     * Transfers one exact module between caller-authorized physical storage nodes.
     * This boundary supplies no travel, docking permission or elapsed time.
     * Both owners and their occupied capacity are checked before spending finite handling.
     * @param state actual inventory
     * @param custodyId individual module identity
     * @param products physical product definitions
     * @param source actual original storage
     * @param destination actual receiving storage
     * @param handling actual common endpoint handling
     * @param budget already allocated finite handling interval
     * @param reservedIds identities reserved by other physical jobs
     * @return transferred inventory; caller retains it with both stores atomically
     */
    public static ShipyardModuleCustodyState transfer(ShipyardModuleCustodyState state, String custodyId,
            Stage18ManufacturingProductRegistry products, Stage18StationStorage source, Stage18StationStorage destination,
            Stage18LogisticsRuntime.HandlingCapability handling, Stage18LogisticsRuntime.TransferBudget budget,
            java.util.Set<String> reservedIds) {
        Objects.requireNonNull(budget); Objects.requireNonNull(reservedIds);
        if (reservedIds.contains(custodyId)) throw new IllegalArgumentException("Individual module is reserved");
        var move = prepareMove(state, custodyId, products, source, destination, handling);
        if (budget.allocatedMassKg() > handling.massRateKgPerSecond() * budget.durationSeconds() + 1e-9)
            throw new IllegalArgumentException("Handling budget exceeds actual endpoint capability");
        if (budget.remainingMassKg() + 1e-9 < move.massKg()) throw new IllegalArgumentException("Insufficient finite handling");
        move.publish(); budget.consume(move.massKg()); return move.next();
    }

    static Move prepareMove(ShipyardModuleCustodyState state, String custodyId, Stage18ManufacturingProductRegistry products,
            Stage18StationStorage source, Stage18StationStorage destination, Stage18LogisticsRuntime.HandlingCapability handling) {
        Objects.requireNonNull(source); Objects.requireNonNull(destination); Objects.requireNonNull(handling);
        if (source == destination) throw new IllegalArgumentException("Equipment transfer requires two owners");
        var actual = state.modules().stream().filter(row -> row.custodyId().equals(custodyId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown individual equipment"));
        var product = products.findProduct(actual.condition().assignment().moduleId());
        if (product == null) throw new IllegalArgumentException("Unknown physical equipment");
        if (product.kind() != Stage18ManufacturingProductRegistry.ProductKind.MODULE
                || !handling.supportedStorageClassIds().contains(product.storageClassId())
                || product.unitMassKg() > handling.maxUnitMassKg() + 1e-9)
            throw new IllegalArgumentException("Actual handling cannot move this equipment");
        var before = massByStation(state, products);
        if (!before.getOrDefault(source.stationId(), Map.of()).equals(source.moduleCustodyReservation())
                || !before.getOrDefault(destination.stationId(), Map.of()).equals(destination.moduleCustodyReservation()))
            throw new IllegalArgumentException("Equipment custody differs from physical storage");
        var next = state.relocate(custodyId, source.stationId(), destination.stationId());
        var after = massByStation(next, products);
        var sourceMass = after.getOrDefault(source.stationId(), Map.of());
        var destinationMass = after.getOrDefault(destination.stationId(), Map.of());
        source.validateModuleCustodyReservation(sourceMass); destination.validateModuleCustodyReservation(destinationMass);
        return new Move(next, product.unitMassKg(), source, destination, sourceMass, destinationMass);
    }

    record Move(ShipyardModuleCustodyState next, double massKg, Stage18StationStorage source, Stage18StationStorage destination,
            Map<String, Double> sourceMass, Map<String, Double> destinationMass) {
        void publish() { source.replaceModuleCustodyReservation(sourceMass); destination.replaceModuleCustodyReservation(destinationMass); }
    }

    /**
     * Rebinds custody without adding pristine products or consuming physical work.
     * All affected station capacities are checked before any reservation changes.
     * @param state authoritative individual module inventory
     * @param products actual finished-module definitions
     * @param stores canonical station resolver
     */
    public static void bind(ShipyardModuleCustodyState state, Stage18ManufacturingProductRegistry products,
            Function<String, Stage18StationStorage> stores) {
        var masses = massByStation(state, products);
        var storage = new TreeMap<String, Stage18StationStorage>();
        masses.forEach((id, mass) -> {
            var actual = stores.apply(id);
            if (actual == null) throw new IllegalArgumentException("Missing module custody station");
            if (!actual.stationId().equals(id)) throw new IllegalArgumentException("Wrong module custody station");
            actual.validateModuleCustodyReservation(mass);
            storage.put(id, actual);
        });
        masses.forEach((id, mass) -> storage.get(id).replaceModuleCustodyReservation(mass));
    }

    /**
     * Derives occupied mass from real module definitions, never from damage or declared mass.
     * @param state authoritative physical inventory
     * @param products actual product definitions
     * @return occupied kilograms per station and compatible storage class
     */
    public static Map<String, Map<String, Double>> massByStation(ShipyardModuleCustodyState state,
            Stage18ManufacturingProductRegistry products) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(products, "products");
        var result = new TreeMap<String, Map<String, Double>>();
        for (var module : state.modules()) {
            var product = products.findProduct(module.condition().assignment().moduleId());
            if (product == null || product.kind() != Stage18ManufacturingProductRegistry.ProductKind.MODULE)
                throw new IllegalArgumentException("Custody requires an authored physical module");
            var mass = result.computeIfAbsent(module.stationId(), id -> new TreeMap<>());
            double total = mass.getOrDefault(product.storageClassId(), 0d) + product.unitMassKg();
            if (!Double.isFinite(total)) throw new IllegalArgumentException("Module custody mass overflow");
            mass.put(product.storageClassId(), total);
        }
        var frozen = new TreeMap<String, Map<String, Double>>();
        result.forEach((id, mass) -> frozen.put(id, Map.copyOf(mass)));
        return Map.copyOf(frozen);
    }
}
