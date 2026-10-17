package com.spacesim.economy;

import com.spacesim.content.Stage18ManufacturingCatalog;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.persistence.Stage18IndustrialState.ProcessKind;
import com.spacesim.persistence.Stage18IndustrialState.ProcessOrderSnapshot;
import java.util.*;
import java.util.function.Function;

/** Physical reserved-material assembly queue using the native Stage18 process checkpoint. */
public final class Stage18ManufacturingWorkQueue {
    // Same kilogram settlement precision as finite station storage and construction delivery.
    private static final double MASS_EPSILON_KG = 1e-9;
    /** Explicit identity of executable personal manufacturing orders; older process rows are untouched. */
    public static final String PREFIX = "player-manufacturing:v1:";
    /** Executable refining orders; historical unrelated process rows remain untouched. */
    public static final String REFINING_PREFIX = "player-refining:v1:";
    /** Executable component batches; requestedAmount is finished component mass. */
    public static final String COMPONENT_PREFIX = "player-components:v1:";
    private final com.spacesim.content.Stage18RefiningCatalog refining = com.spacesim.content.Stage18RefiningCatalogLoader.loadDefault();
    private final Stage18ManufacturingCatalog catalog;
    private final Stage18ResourceOntologyCatalog ontology;
    private final Stage18ManufacturingProductRegistry products;
    private final Stage18ManufacturingRuntime manufacturing;
    private final TreeMap<String, ProcessOrderSnapshot> orders = new TreeMap<>();
    private long lastProcessedTick;

    /**
     * Restores exact orders without spending materials or completing work.
     * @param ontology physical material vocabulary
     * @param catalog authored manufacturing profiles
     * @param products physical finished-product vocabulary
     * @param saved native process rows, including unrelated historical rows
     * @param tick persisted completed-tick watermark
     */
    public Stage18ManufacturingWorkQueue(Stage18ResourceOntologyCatalog ontology, Stage18ManufacturingCatalog catalog,
            Stage18ManufacturingProductRegistry products, List<ProcessOrderSnapshot> saved, long tick) {
        this.catalog = Objects.requireNonNull(catalog);
        this.ontology = Objects.requireNonNull(ontology);
        this.products = Objects.requireNonNull(products);
        manufacturing = new Stage18ManufacturingRuntime(ontology, catalog, products);
        if (tick < 0) throw new IllegalArgumentException("Negative manufacturing watermark");
        lastProcessedTick = tick;
        for (var order : saved) {
            if (orders.putIfAbsent(order.orderId(), order) != null) throw new IllegalArgumentException("Duplicate order");
            if (managed(order)) validate(order);
        }
    }

    /** @return immutable native process rows */
    public List<ProcessOrderSnapshot> capture() { return List.copyOf(orders.values()); }
    /** @return last actual completed tick */
    public long lastProcessedTick() { return lastProcessedTick; }

    /**
     * Rebinds persisted custody to canonical capacity without duplicating material.
     * @param stores actual canonical station resolver
     */
    public void restoreReservations(Function<String, Stage18StationStorage> stores) {
        orders.values().stream().filter(Stage18ManufacturingWorkQueue::managed).map(ProcessOrderSnapshot::stationId)
                .distinct().forEach(id -> {
                    var storage = Objects.requireNonNull(stores.apply(id), "Missing reservation station");
                    storage.replaceContentsWithProcessReservation(storage.snapshotCommodityMassByIdKg(),
                            storage.snapshotProductCountById(), reservationMass(id, null, Map.of()));
                });
    }
    /**
     * Identifies this executable queue's native rows.
     * @param order native row
     * @return whether this queue executes it
     */
    public static boolean managed(ProcessOrderSnapshot order) {
        return order.orderId().startsWith(PREFIX) || order.orderId().startsWith(REFINING_PREFIX)
                || order.orderId().startsWith(COMPONENT_PREFIX);
    }

    /**
     * Reserves the exact authored material bill for a bulk component batch.
     * @param id unique component order
     * @param recipeId existing component recipe
     * @param kilograms requested finished mass
     * @param storage real authorized station store
     * @param capability installed compatible line
     * @param tick current completed world tick
     */
    public void startComponents(String id, String recipeId, double kilograms, Stage18StationStorage storage,
            Stage18ManufacturingRuntime.ManufacturingCapability capability, long tick) {
        var recipe = catalog.findComponentRecipe(recipeId);
        if (recipe == null || id == null || !id.startsWith(COMPONENT_PREFIX)
                || orders.containsKey(id) || tick < lastProcessedTick)
            throw new IllegalArgumentException("Invalid component batch");
        var inputs = componentInputs(recipeId, kilograms);
        if (tick != lastProcessedTick && orders.values().stream().anyMatch(Stage18ManufacturingWorkQueue::managed))
            throw new IllegalStateException("Existing work must process the current tick first");
        if (!capability.capabilityTags().containsAll(recipe.requiredCapabilityTags())
                || capability.availablePowerW() <= 0 || capability.workRate() <= 0
                || recipe.maintenanceWorkSecondsPerOutputKg() > 0 && capability.maintenanceWorkRate() <= 0)
            throw new IllegalStateException("No operational component line");
        var next = new TreeMap<>(storage.snapshotCommodityMassByIdKg());
        inputs.forEach((commodity, mass) -> {
            double available = next.getOrDefault(commodity, 0d);
            if (mass - available > MASS_EPSILON_KG) throw new IllegalStateException("Insufficient physical component material");
            next.put(commodity, Math.max(0d, available - mass));
        });
        var order = new ProcessOrderSnapshot(id, ProcessKind.COMPONENT_MANUFACTURING, recipeId, storage.stationId(),
                "", kilograms, 0, 0, inputs, Map.of());
        if (!commodityOutputFits(order, storage, recipe.outputCommodityId(), kilograms))
            throw new IllegalStateException("No component output storage");
        storage.replaceContentsWithProcessReservation(next, storage.snapshotProductCountById(),
                reservationMass(storage.stationId(), null, inputs));
        orders.put(id, order); lastProcessedTick = tick;
    }

    /**
     * Reserves a gross feedstock batch for an authored refining recipe.
     * @param id unique player refining order
     * @param recipeId existing refining recipe
     * @param kilograms gross input mass
     * @param storage actual owner-authorized store
     * @param capability installed process line
     * @param tick current completed world tick
     */
    public void startRefining(String id, String recipeId, double kilograms, Stage18StationStorage storage,
            Stage18ManufacturingRuntime.ManufacturingCapability capability, long tick) {
        var recipe = refining.findRecipe(recipeId);
        if (recipe == null || !Double.isFinite(kilograms) || kilograms <= 0
                || id == null || !id.startsWith(REFINING_PREFIX) || orders.containsKey(id) || tick < lastProcessedTick)
            throw new IllegalArgumentException("Invalid refining batch");
        if (tick != lastProcessedTick && orders.values().stream().anyMatch(Stage18ManufacturingWorkQueue::managed))
            throw new IllegalStateException("Existing work must process the current tick first");
        if (!capability.capabilityTags().containsAll(recipe.requiredCapabilityTags())
                || capability.availablePowerW() <= 0 || capability.workRate() <= 0
                || recipe.maintenanceWorkSecondsPerInputKg() > 0 && capability.maintenanceWorkRate() <= 0)
            throw new IllegalStateException("No operational refining line");
        var inputs = refiningInputs(recipeId, kilograms);
        var next = new TreeMap<>(storage.snapshotCommodityMassByIdKg());
        inputs.forEach((commodity, mass) -> {
            double available = next.getOrDefault(commodity, 0d);
            if (mass - available > MASS_EPSILON_KG) throw new IllegalStateException("Insufficient physical refining feedstock");
            next.put(commodity, Math.max(0d, available - mass));
        });
        var order = new ProcessOrderSnapshot(id, ProcessKind.REFINING, recipeId, storage.stationId(),
                "", kilograms, 0, 0, inputs, Map.of());
        if (!refiningOutputFits(order, storage)) throw new IllegalStateException("No refining output storage");
        storage.replaceContentsWithProcessReservation(next, storage.snapshotProductCountById(),
                reservationMass(storage.stationId(), null, inputs));
        orders.put(id, order);
        lastProcessedTick = tick;
    }

    /**
     * Reserves actual station materials, without advancing time or granting finished products.
     * @param id unique personal order identity
     * @param productId authored finished product
     * @param units whole count
     * @param storage canonical owner-authorized station store
     * @param capability actual installed production line
     * @param tick current world tick
     */
    public void start(String id, String productId, int units, Stage18StationStorage storage,
            Stage18ManufacturingRuntime.ManufacturingCapability capability, long tick) {
        if (id == null || !id.startsWith(PREFIX) || orders.containsKey(id) || tick < lastProcessedTick)
            throw new IllegalArgumentException("Invalid or reused manufacturing order");
        if (tick != lastProcessedTick && orders.values().stream().anyMatch(Stage18ManufacturingWorkQueue::managed))
            throw new IllegalStateException("Existing work must process the current tick before adding another order");
        var inputs = inputs(productId, units);
        var profile = catalog.findProductProfile(catalog.findProductBinding(productId).profileId());
        if (!capability.capabilityTags().containsAll(profile.requiredCapabilityTags())
                || capability.availablePowerW() <= 0 || capability.workRate() <= 0)
            throw new IllegalStateException("No operational compatible assembly line");
        var next = new TreeMap<>(storage.snapshotCommodityMassByIdKg());
        for (var input : inputs.entrySet()) {
            double available = next.getOrDefault(input.getKey(), 0d);
            if (input.getValue() - available > MASS_EPSILON_KG) throw new IllegalStateException("Insufficient physical manufacturing material");
            next.put(input.getKey(), Math.max(0d, available - input.getValue()));
        }
        if (!storage.canAddProduct(productId, units)) throw new IllegalStateException("No finished-product storage");
        var order = new ProcessOrderSnapshot(id, ProcessKind.PRODUCT_MANUFACTURING, productId, storage.stationId(),
                "", 0, units, 0, inputs, Map.of());
        storage.replaceContentsWithProcessReservation(next, storage.snapshotProductCountById(),
                reservationMass(storage.stationId(), null, inputs));
        orders.put(id, order);
        lastProcessedTick = tick;
    }

    /**
     * Advances once per consecutive completed world tick. Caller allocates each actual line's
     * interval once; competing jobs must receive the same mutable budget object.
     * @param tick completed authoritative tick
     * @param stores current canonical station resolver
     * @param budgets caller-allocated shared line budget resolver; null pauses an unavailable line
     */
    public void advance(long tick, Function<String, Stage18StationStorage> stores,
            Function<ProcessOrderSnapshot, Stage18ManufacturingRuntime.IntervalBudget> budgets) {
        if (tick < 0) throw new IllegalArgumentException("Negative manufacturing tick");
        if (tick <= lastProcessedTick || orders.values().stream().noneMatch(Stage18ManufacturingWorkQueue::managed)) return;
        if (tick != lastProcessedTick + 1) throw new IllegalArgumentException("Manufacturing cannot invent missed intervals");
        lastProcessedTick = tick;
        for (var order : List.copyOf(orders.values())) {
            if (!managed(order)) continue;
            var store = stores.apply(order.stationId());
            if (store != null) store.replaceContentsWithProcessReservation(store.snapshotCommodityMassByIdKg(),
                    store.snapshotProductCountById(), reservationMass(order.stationId(), null, Map.of()));
            var budget = budgets.apply(order);
            if (order.kind() == ProcessKind.COMPONENT_MANUFACTURING) {
                var recipe = catalog.findComponentRecipe(order.operationId());
                double mass = order.requestedAmount();
                if (store == null || budget == null || !commodityOutputFits(order, store, recipe.outputCommodityId(), mass)) continue;
                double fraction = order.completedFraction() + budget.allocateProcessWork(recipe.requiredCapabilityTags(),
                        mass * recipe.energyJPerOutputKg(), mass * recipe.workSecondsPerOutputKg(),
                        mass * recipe.maintenanceWorkSecondsPerOutputKg(), 1 - order.completedFraction());
                if (fraction >= 1) {
                    var next = new TreeMap<>(store.snapshotCommodityMassByIdKg());
                    next.merge(recipe.outputCommodityId(), mass, Double::sum);
                    store.replaceContentsWithProcessReservation(next, store.snapshotProductCountById(),
                            reservationMass(order.stationId(), order.orderId(), Map.of()));
                    orders.remove(order.orderId());
                } else orders.put(order.orderId(), new ProcessOrderSnapshot(order.orderId(), order.kind(),
                        order.operationId(), order.stationId(), "", mass, 0, fraction,
                        order.reservedCommodityMassByIdKg(), Map.of()));
                continue;
            }
            if (order.kind() == ProcessKind.REFINING) {
                if (store == null || budget == null || !refiningOutputFits(order, store)) continue;
                var recipe = refining.findRecipe(order.operationId());
                double mass = order.requestedAmount();
                double fraction = order.completedFraction() + budget.allocateProcessWork(recipe.requiredCapabilityTags(),
                        mass * recipe.energyJPerInputKg(), mass * recipe.workSecondsPerInputKg(),
                        mass * recipe.maintenanceWorkSecondsPerInputKg(), 1 - order.completedFraction());
                if (fraction >= 1) {
                    var next = new TreeMap<>(store.snapshotCommodityMassByIdKg());
                    next.merge(recipe.outputCommodityId(), mass * recipe.outputMassFraction(), Double::sum);
                    store.replaceContentsWithProcessReservation(next, store.snapshotProductCountById(),
                            reservationMass(order.stationId(), order.orderId(), Map.of()));
                    orders.remove(order.orderId());
                } else orders.put(order.orderId(), new ProcessOrderSnapshot(order.orderId(), order.kind(),
                        order.operationId(), order.stationId(), "", mass, 0, fraction,
                        order.reservedCommodityMassByIdKg(), Map.of()));
                continue;
            }
            if (store == null || budget == null || (long) store.productCount(order.operationId()) + order.requestedUnits() > Integer.MAX_VALUE
                    || !store.canAddProduct(order.operationId(), order.requestedUnits())) continue;
            double fraction = order.completedFraction() + manufacturing.allocateProductWork(order.operationId(),
                    order.requestedUnits(), 1 - order.completedFraction(), budget);
            if (fraction >= 1) {
                var finished = new TreeMap<>(store.snapshotProductCountById());
                finished.merge(order.operationId(), order.requestedUnits(), Math::addExact);
                store.replaceContentsWithProcessReservation(store.snapshotCommodityMassByIdKg(), finished,
                        reservationMass(order.stationId(), order.orderId(), Map.of()));
                orders.remove(order.orderId());
            } else {
                orders.put(order.orderId(), new ProcessOrderSnapshot(order.orderId(), order.kind(), order.operationId(),
                        order.stationId(), "", 0, order.requestedUnits(), fraction, order.reservedCommodityMassByIdKg(), Map.of()));
            }
        }
    }

    /**
     * Returns reserved material atomically; spent work/energy is not refunded.
     * A full store leaves the order intact instead of discarding custody.
     * @param id existing queue identity
     * @param storage canonical executing station
     */
    public void cancel(String id, Stage18StationStorage storage) {
        var order = orders.get(id);
        if (order == null) throw new IllegalStateException("Unknown manufacturing order");
        if (!managed(order) || !order.stationId().equals(storage.stationId())) throw new IllegalArgumentException("Wrong order owner");
        var next = new TreeMap<>(storage.snapshotCommodityMassByIdKg());
        order.reservedCommodityMassByIdKg().forEach((commodity, mass) -> next.merge(commodity, mass, Double::sum));
        storage.replaceContentsWithProcessReservation(next, storage.snapshotProductCountById(),
                reservationMass(order.stationId(), id, Map.of()));
        orders.remove(id);
    }

    private Map<String, Double> inputs(String productId, int units) {
        var product = products.findProduct(productId);
        var binding = catalog.findProductBinding(productId);
        if (product == null || binding == null || units <= 0) throw new IllegalArgumentException("Unknown product batch");
        double mass = product.unitMassKg() * units;
        if (!Double.isFinite(mass)) throw new IllegalArgumentException("Batch mass overflow");
        var result = new TreeMap<String, Double>();
        for (var input : catalog.findProductProfile(binding.profileId()).inputs())
            result.merge(input.commodityId(), mass * input.fractionOfOutputMass(), Double::sum);
        return Collections.unmodifiableMap(result);
    }

    private Map<String, Double> reservationMass(String station, String excluded, Map<String, Double> additional) {
        var result = new TreeMap<String, Double>();
        for (var order : orders.values()) if (managed(order) && order.stationId().equals(station) && !order.orderId().equals(excluded))
            order.reservedCommodityMassByIdKg().forEach((commodity, mass) ->
                    result.merge(ontology.findCommodity(commodity).storageClassId(), mass, Double::sum));
        additional.forEach((commodity, mass) -> result.merge(ontology.findCommodity(commodity).storageClassId(), mass, Double::sum));
        return result;
    }

    private void validate(ProcessOrderSnapshot order) {
        if (order.orderId().startsWith(COMPONENT_PREFIX)) {
            if (order.kind() != ProcessKind.COMPONENT_MANUFACTURING || !order.sourceId().isEmpty()
                    || order.requestedUnits() != 0 || order.completedFraction() >= 1
                    || !order.reservedProductCountById().isEmpty()
                    || !componentInputs(order.operationId(), order.requestedAmount()).equals(order.reservedCommodityMassByIdKg()))
                throw new IllegalArgumentException("Invalid reserved component custody");
            return;
        }
        if (order.orderId().startsWith(REFINING_PREFIX)) {
            if (order.kind() != ProcessKind.REFINING || !order.sourceId().isEmpty()
                    || order.requestedUnits() != 0 || order.completedFraction() >= 1
                    || !order.reservedProductCountById().isEmpty()
                    || !refiningInputs(order.operationId(), order.requestedAmount()).equals(order.reservedCommodityMassByIdKg()))
                throw new IllegalArgumentException("Invalid reserved refining custody");
            return;
        }
        if (order.kind() != ProcessKind.PRODUCT_MANUFACTURING || !order.sourceId().isEmpty()
                || order.requestedAmount() != 0 || order.completedFraction() >= 1
                || !order.reservedProductCountById().isEmpty()
                || !inputs(order.operationId(), order.requestedUnits()).equals(order.reservedCommodityMassByIdKg()))
            throw new IllegalArgumentException("Invalid reserved manufacturing custody");
    }

    private Map<String, Double> refiningInputs(String recipeId, double kilograms) {
        var recipe = refining.findRecipe(recipeId);
        if (recipe == null || !Double.isFinite(kilograms) || kilograms <= 0)
            throw new IllegalArgumentException("Invalid refining recipe or amount");
        if (!Double.isFinite(kilograms * recipe.energyJPerInputKg())
                || !Double.isFinite(kilograms * recipe.workSecondsPerInputKg())
                || !Double.isFinite(kilograms * recipe.maintenanceWorkSecondsPerInputKg()))
            throw new IllegalArgumentException("Refining batch demand overflow");
        var inputs = new TreeMap<String, Double>();
        for (var input : recipe.inputs()) inputs.put(input.commodityId(), kilograms * input.fractionOfInputMass());
        return Collections.unmodifiableMap(inputs);
    }

    private boolean refiningOutputFits(ProcessOrderSnapshot order, Stage18StationStorage storage) {
        var recipe = refining.findRecipe(order.operationId());
        return commodityOutputFits(order, storage, recipe.outputCommodityId(), order.requestedAmount() * recipe.outputMassFraction());
    }

    private boolean commodityOutputFits(ProcessOrderSnapshot order, Stage18StationStorage storage, String output, double mass) {
        String storageClass = ontology.findCommodity(output).storageClassId();
        double released = order.reservedCommodityMassByIdKg().entrySet().stream()
                .filter(e -> ontology.findCommodity(e.getKey()).storageClassId().equals(storageClass))
                .mapToDouble(Map.Entry::getValue).sum();
        // Before reservation the input already occupies the same physical capacity.
        return mass <= storage.remainingCapacityKg(storageClass) + released + MASS_EPSILON_KG;
    }

    private Map<String, Double> componentInputs(String recipeId, double kilograms) {
        var recipe = catalog.findComponentRecipe(recipeId);
        if (recipe == null || !Double.isFinite(kilograms) || kilograms <= 0
                || !Double.isFinite(kilograms * recipe.energyJPerOutputKg())
                || !Double.isFinite(kilograms * recipe.workSecondsPerOutputKg())
                || !Double.isFinite(kilograms * recipe.maintenanceWorkSecondsPerOutputKg()))
            throw new IllegalArgumentException("Invalid component recipe or batch demand");
        var inputs = new TreeMap<String, Double>();
        for (var input : recipe.inputs()) inputs.put(input.commodityId(), kilograms * input.fractionOfOutputMass());
        return Collections.unmodifiableMap(inputs);
    }
}
