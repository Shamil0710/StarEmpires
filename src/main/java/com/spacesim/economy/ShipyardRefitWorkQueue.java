package com.spacesim.economy;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.*;
import com.spacesim.economy.ShipyardRefitQueueState.Order;
import com.spacesim.economy.Stage18ShipyardRuntime.*;
import com.spacesim.persistence.EntityId;
import com.spacesim.ship.*;
import com.spacesim.ship.ShipEngineeringState.*;
import com.spacesim.ship.ShipyardEngineeringService.*;
import com.spacesim.ship.ShipyardRefitContinuity.Completion;
import java.util.*;
import java.util.function.*;

/** Finite physical refit queue. Caller owns authorization, cargo preflight and composed persistence. */
public final class ShipyardRefitWorkQueue {
    private final Stage18ManufacturingProductRegistry products = Stage22CivilianMiningProductionPath.loadProducts();
    private final TreeMap<String, Order> orders = new TreeMap<>();
    private long lastTick;
    /**
     * Restores validated authored work and escrow without applying a fitting.
     * @param saved exact pending checkpoint
     */
    public ShipyardRefitWorkQueue(ShipyardRefitQueueState saved) {
        lastTick = saved.lastProcessedTick();
        for (var o : saved.orders()) {
            validate(o); orders.put(o.orderId(), o);
        }
    }
    /** @return exact pending work and equipment custody */
    public ShipyardRefitQueueState capture() { return new ShipyardRefitQueueState(lastTick, List.copyOf(orders.values())); }
    /**
     * Plans using the actual fitting, carried stores and installed capability.
     * @param asset existing ship ID
     * @param ship actual engineering component
     * @param target same-hull proposal
     * @param yard actual installed yard
     * @return common physical work plan
     */
    public static WorkPlan plan(EntityId asset, EngineeringComponent ship, InstalledFit target, YardCapabilitySnapshot yard) {
        return ShipyardRepairWorkQueue.planner(ship.fit).planRefit(asset, ship.fit, target,
                ship.runtimeState.consumables(), ship.instanceState.damage(), yard.plannerCapability());
    }
    private WorkPlan designPlan(Order o) {
        var d = Objects.requireNonNull(Stage22CivilianMiningProductionPath.loadRuntimeShipyards().findYard(o.yardDefinitionId()), "Unknown refit yard");
        var capability = new ShipyardCapability(o.yardInstanceId(), d.berthDimensionsM(), d.maxServiceMassKg(),
                d.stage175FabricationCapabilities(), d.stage175HandledRequirementIds(), d.toolingTags(), d.precisionCapability(),
                d.ratedEngineeringWorkRate(), d.laborCapacity(), d.automationCapacity(), d.ratedIntegrationPowerW());
        return ShipyardRepairWorkQueue.planner(o.sourceFit()).planRefit(new EntityId(o.assetId()), o.sourceFit(), o.targetFit(),
                ConsumableState.empty(), o.sourceDamage(), capability);
    }
    /**
     * Quotes the exact actual plan without reserving cash or hardware.
     * @param asset actual ship
     * @param ship current fitting
     * @param target target fitting
     * @param yard actual installed yard
     * @param used exact existing inputs supplied by the caller
     * @return full service reservation
     */
    public static long serviceQuote(EntityId asset, EngineeringComponent ship, InstalledFit target,
            YardCapabilitySnapshot yard, Map<String, ShipyardModuleCustodyState.StoredModule> used) {
        var planned = plan(asset, ship, target, yard);
        if (!planned.feasibility().feasible()) throw new IllegalArgumentException("No compatible refit quote");
        var fresh = new TreeMap<>(changed(ship.fit, target));
        for (var input : used.values()) {
            String id = input.condition().assignment().moduleId(); int count = fresh.getOrDefault(id, 0);
            if (count <= 0) throw new IllegalArgumentException("Used input is not required by refit");
            if (count == 1) fresh.remove(id); else fresh.put(id, count - 1);
        }
        return ShipyardRefitServicePayment.quote(planned.requirements().totalWorkSeconds(), fresh);
    }
    private void validate(Order o) {
        var plan = designPlan(o);
        if (!plan.feasibility().feasible() || Double.compare(plan.requirements().totalWorkSeconds(), o.requiredWorkSeconds()) != 0
                || !changed(o.sourceFit(), o.targetFit()).equals(incomingCounts(o))) throw new IllegalArgumentException("Refit escrow/work differs from authored plan");
        reservation(o);
    }
    /**
     * Binds escrow and reserved removal room to actual canonical stores on composed restore.
     * @param stores canonical station resolver
     */
    public void restoreReservations(Function<String, Stage18StationStorage> stores) {
        for (String station : orders.values().stream().map(Order::stationId).distinct().toList()) {
            var store = Objects.requireNonNull(stores.apply(station)); requireStation(station, store);
            store.replaceContentsWithRefitReservation(store.snapshotCommodityMassByIdKg(), store.snapshotProductCountById(), reserved(station, "", null), store.moduleCustodyReservation());
        }
    }
    /**
     * Reserves incoming pristine modules and sufficient room for condition-preserving removals.
     * No work or fitting change is applied immediately.
     * @param id unique job identity
     * @param fleet stable fleet
     * @param asset persistent physical ship
     * @param ship actual source component
     * @param target same-hull proposal
     * @param store canonical station
     * @param yard actually installed compatible yard
     * @param tick current completed tick
     */
    public void start(String id, long fleet, EntityId asset, EngineeringComponent ship, InstalledFit target,
            Stage18StationStorage store, YardCapabilitySnapshot yard, long tick) {
        start(id, fleet, asset, ship, target, store, yard, tick, Map.of(), ShipyardModuleCustodyState.empty());
    }
    /**
     * Reserves exact used inputs while retaining them in physical custody until completion.
     * @param id unique job identity
     * @param fleet existing fleet
     * @param asset existing physical ship
     * @param ship actual source component
     * @param target same-hull target
     * @param store canonical receiving store
     * @param yard actual installed yard
     * @param tick completed tick
     * @param used exact custody rows by changed target mount
     * @param custody current physical custody
     */
    public void start(String id, long fleet, EntityId asset, EngineeringComponent ship, InstalledFit target,
            Stage18StationStorage store, YardCapabilitySnapshot yard, long tick,
            Map<String, ShipyardModuleCustodyState.StoredModule> used, ShipyardModuleCustodyState custody) {
        start(id, fleet, asset, ship, target, store, yard, tick, used, custody, null);
    }

    /**
     * Reserves exact hardware with a caller-funded service obligation.
     * @param id exact job
     * @param fleet original fleet
     * @param asset actual ship
     * @param ship actual engineering
     * @param target target fitting
     * @param store operator warehouse
     * @param yard installed working yard
     * @param tick actual start tick
     * @param used exact used inputs
     * @param custody current physical inventory
     * @param payment caller-funded money, or null for owner work
     */
    public void start(String id, long fleet, EntityId asset, EngineeringComponent ship, InstalledFit target,
            Stage18StationStorage store, YardCapabilitySnapshot yard, long tick,
            Map<String, ShipyardModuleCustodyState.StoredModule> used, ShipyardModuleCustodyState custody,
            ShipyardRefitServicePayment payment) {
        if (orders.size() >= ShipyardRefitQueueState.CAPACITY || orders.containsKey(id) || orders.values().stream().anyMatch(o -> o.fleetId() == fleet)
                || tick < lastTick || !orders.isEmpty() && tick != lastTick || !yard.active()) throw new IllegalArgumentException("Refit start conflict");
        var plan = plan(asset, ship, target, yard);
        if (!plan.feasibility().feasible()) throw new IllegalArgumentException("Refit plan infeasible");
        var pristine = new TreeMap<>(changed(ship.fit, target));
        used.values().forEach(row -> {
            String module = row.condition().assignment().moduleId(); int count = pristine.getOrDefault(module, 0);
            if (count <= 0) throw new IllegalArgumentException("Used input is not required by target");
            if (count == 1) pristine.remove(module); else pristine.put(module, count - 1);
        });
        var order = new Order(id, fleet, asset.value(), store.stationId(), yard.yardInstanceId(), yard.yardDefinitionId(), tick,
                ship.fit, target, ship.instanceState.damage(), plan.requirements().totalWorkSeconds(), 0, pristine, used, payment);
        validate(order); handling(order, yard);
        if (!used.isEmpty()) {
            validateUsedReservations(custody);
            var reservedIds = new HashSet<String>();
            orders.values().forEach(o -> o.reservedUsedModulesByTargetMount().values().forEach(row -> reservedIds.add(row.custodyId())));
            used.values().forEach(row -> {
                if (!custody.modules().contains(row) || !reservedIds.add(row.custodyId()))
                    throw new IllegalArgumentException("Used input missing or already reserved");
            });
            if (!ShipyardModuleCustodyStorage.massByStation(custody, products).getOrDefault(store.stationId(), Map.of())
                    .equals(store.moduleCustodyReservation())) throw new IllegalArgumentException("Used input custody is not bound to store");
        }
        var stock = new TreeMap<>(store.snapshotProductCountById());
        order.reservedProductCounts().forEach((module, count) -> {
            int remaining = stock.getOrDefault(module, 0) - count;
            if (remaining < 0) throw new IllegalArgumentException("Missing physical refit module");
            if (remaining == 0) stock.remove(module); else stock.put(module, remaining);
        });
        store.replaceContentsWithRefitReservation(store.snapshotCommodityMassByIdKg(), stock, reserved(store.stationId(), "", order), store.moduleCustodyReservation());
        orders.put(id, order); lastTick = tick;
    }
    /**
     * Returns all incoming modules; spent engineering work is not refunded.
     * @param id exact pending job
     * @param store original canonical custody owner
     */
    public void cancel(String id, Stage18StationStorage store) {
        var o = orders.get(id); if (o == null) throw new IllegalArgumentException("Unknown refit job"); requireStation(o.stationId(), store);
        if (o.servicePayment() != null && o.completedWorkSeconds() > 0)
            throw new IllegalStateException("Paid refit cannot refund after work starts");
        var stock = new TreeMap<>(store.snapshotProductCountById()); o.reservedProductCounts().forEach((m, count) -> stock.merge(m, count, Math::addExact));
        store.replaceContentsWithRefitReservation(store.snapshotCommodityMassByIdKg(), stock, reserved(o.stationId(), id, null), store.moduleCustodyReservation());
        orders.remove(id);
    }
    /**
     * Advances exactly one completed interval. The capacity preflight must be pure and use actual cargo.
     * Completion stages ship and removed equipment before consuming its final work.
     * @param tick actual completed interval
     * @param stores canonical station resolver
     * @param contexts authorized docked contexts, or null to pause
     * @param budgets shared finite budget per installed yard
     * @param custody current exact removed-equipment owner
     * @return custody and jobs completed physically on this interval
     */
    public AdvanceResult advance(long tick, Function<String, Stage18StationStorage> stores,
            Function<Order, Context> contexts, Function<Order, YardWorkBudget> budgets, ShipyardModuleCustodyState custody) {
        Objects.requireNonNull(custody);
        validateUsedReservations(custody);
        if (tick < 0) throw new IllegalArgumentException("Negative refit tick");
        if (tick <= lastTick || orders.isEmpty()) return new AdvanceResult(custody, List.of());
        if (tick != lastTick + 1) throw new IllegalArgumentException("Refit cannot credit missed intervals");
        var completed = new ArrayList<Order>();
        for (var o : List.copyOf(orders.values())) {
            var c = contexts.apply(o);
            if (c != null && o.servicePayment() != null && (c.ownerActorId() == null || !c.paymentPreflight().test(o))) continue;
            if (c != null && o.reservedUsedModulesByTargetMount().values().stream().anyMatch(row ->
                    row.ownerActorId() != null && !row.ownerActorId().equals(c.ownerActorId()))) continue;
            if (c == null || !c.ship().fit.equals(o.sourceFit()) || !c.ship().instanceState.damage().equals(o.sourceDamage())
                    || !c.yard().active() || !c.yard().yardInstanceId().equals(o.yardInstanceId()) || !c.yard().yardDefinitionId().equals(o.yardDefinitionId())) continue;
            var p = plan(new EntityId(o.assetId()), c.ship(), o.targetFit(), c.yard());
            if (!p.feasibility().feasible() || Double.compare(p.requirements().totalWorkSeconds(), o.requiredWorkSeconds()) != 0) continue;
            if (!handles(o, c.yard())) continue;
            var planner = ShipyardRepairWorkQueue.planner(o.sourceFit()); var delivered = new TreeMap<String, Double>();
            p.requirements().inputs().forEach(i -> delivered.merge(i.contentId(), i.amount(), Double::sum));
            // Pure staged continuity. Published only after reserved hardware and real work are consumed below.
            var incoming = new TreeMap<String, ShipyardRefitContinuity.RemovedModuleState>();
            o.reservedUsedModulesByTargetMount().forEach((mount, row) -> incoming.put(mount, row.condition()));
            var completion = ShipyardRefitContinuity.withIncomingConditions(ShipyardRefitContinuity.complete(planner, p,
                    new WorkSettlement(delivered, o.requiredWorkSeconds()), c.ship().instanceState.damage(),
                    c.ship().instanceState.maintenance()), o.sourceFit(), incoming);
            if (!c.capacityPreflight().test(completion)) continue;
            var budget = budgets.apply(o); var store = stores.apply(o.stationId()); if (budget == null || store == null) continue;
            requireStation(o.stationId(), store);
            var oldMass = ShipyardModuleCustodyStorage.massByStation(custody, products).getOrDefault(o.stationId(), Map.of());
            if (!oldMass.equals(store.moduleCustodyReservation())) throw new IllegalArgumentException("Refit custody differs from canonical store");
            double remaining = o.requiredWorkSeconds() - o.completedWorkSeconds();
            double offered = Math.min(remaining, budget.remainingWorkSeconds());
            if (offered <= 0) continue;
            if (offered < remaining && o.completedWorkSeconds() + offered < o.requiredWorkSeconds()) {
                budget.allocate(offered); orders.put(o.orderId(), o.withWork(o.completedWorkSeconds() + offered)); continue;
            }
            ShipyardModuleCustodyState nextCustody;
            try { nextCustody = custody.withdraw(o.reservedUsedModulesByTargetMount().values().stream()
                    .map(ShipyardModuleCustodyState.StoredModule::custodyId).collect(java.util.stream.Collectors.toSet()))
                    .deposit(o.orderId(), o.stationId(), completion, tick, c.ownerActorId()); }
            catch (IllegalArgumentException unavailableCustodySlot) { continue; }
            var staged = new EngineeringComponent(c.ship().fit, c.ship().runtimeState, c.ship().instanceState);
            new ShipRefitApplicationService(ShipyardRepairWorkQueue.engineering(o.sourceFit())).apply(new EntityId(o.assetId()), staged, completion, incoming);
            var nextMass = ShipyardModuleCustodyStorage.massByStation(nextCustody, products).getOrDefault(o.stationId(), Map.of());
            var nextReserved = reserved(o.stationId(), o.orderId(), null);
            store.preflightRefitReservation(store.snapshotCommodityMassByIdKg(), store.snapshotProductCountById(), nextReserved, nextMass);
            if (o.servicePayment() != null && !c.settlePayment().test(o)) continue;
            store.replaceContentsWithRefitReservation(store.snapshotCommodityMassByIdKg(), store.snapshotProductCountById(), nextReserved, nextMass);
            budget.allocate(offered); c.ship().fit = staged.fit; c.ship().setRuntimeState(staged.runtimeState); c.ship().setInstanceState(staged.instanceState);
            orders.remove(o.orderId()); custody = nextCustody; completed.add(o);
        }
        lastTick = tick; return new AdvanceResult(custody, List.copyOf(completed));
    }
    private static Map<String, Integer> changed(InstalledFit before, InstalledFit after) {
        var old = new HashMap<String, String>(); before.installedModules().forEach(m -> old.put(m.mountId(), m.moduleId()));
        var result = new TreeMap<String, Integer>(); after.installedModules().stream().filter(m -> !m.moduleId().equals(old.get(m.mountId())))
                .forEach(m -> result.merge(m.moduleId(), 1, Math::addExact)); return result;
    }
    private Map<String, Double> mass(Map<String, Integer> counts) {
        var result = new TreeMap<String, Double>(); counts.forEach((id, count) -> {
            var product = products.findProduct(id);
            if (product == null || product.kind() != Stage18ManufacturingProductRegistry.ProductKind.MODULE
                    || Stage22CivilianMiningProductionPath.loadRuntimeShipyards().findModuleProfile(id) == null) throw new IllegalArgumentException("Unknown physical refit module");
            result.merge(product.storageClassId(), product.unitMassKg() * count, Double::sum);
        }); return result;
    }
    private Map<String, Double> reservation(Order o) {
        var result = new TreeMap<>(mass(incomingCounts(o)));
        mass(changed(o.targetFit(), o.sourceFit())).forEach((id, kg) -> result.merge(id, kg, Math::max));
        var usedCounts = new TreeMap<String, Integer>();
        o.reservedUsedModulesByTargetMount().values().forEach(row -> usedCounts.merge(row.condition().assignment().moduleId(), 1, Math::addExact));
        mass(usedCounts).forEach((id, kg) -> result.compute(id, (key, total) -> total - kg));
        result.values().removeIf(kg -> kg == 0); return result;
    }
    private static Map<String, Integer> incomingCounts(Order o) {
        var counts = new TreeMap<>(o.reservedProductCounts());
        o.reservedUsedModulesByTargetMount().values().forEach(row -> counts.merge(row.condition().assignment().moduleId(), 1, Math::addExact));
        return counts;
    }
    /**
     * Rejects missing or altered exact used inputs before restoring or advancing live work.
     * @param custody actual physical custody owner
     */
    public void validateUsedReservations(ShipyardModuleCustodyState custody) {
        Objects.requireNonNull(custody); var rows = new HashMap<String, ShipyardModuleCustodyState.StoredModule>();
        custody.modules().forEach(row -> rows.put(row.custodyId(), row));
        orders.values().forEach(o -> o.reservedUsedModulesByTargetMount().values().forEach(row -> {
            if (!row.equals(rows.get(row.custodyId()))) throw new IllegalArgumentException("Reserved used module missing or altered");
        }));
    }
    private Map<String, Double> reserved(String station, String excluded, Order addition) {
        var result = new TreeMap<String, Double>(); orders.values().stream().filter(o -> o.stationId().equals(station) && !o.orderId().equals(excluded))
                .forEach(o -> reservation(o).forEach((id, kg) -> result.merge(id, kg, Double::sum)));
        if (addition != null) reservation(addition).forEach((id, kg) -> result.merge(id, kg, Double::sum)); return result;
    }
    private void handling(Order o, YardCapabilitySnapshot yard) {
        if (!handles(o, yard)) throw new IllegalArgumentException("Refit equipment exceeds actual handling");
    }
    private boolean handles(Order o, YardCapabilitySnapshot yard) {
        var all = new TreeMap<>(incomingCounts(o)); changed(o.targetFit(), o.sourceFit()).forEach((id, n) -> all.merge(id, n, Math::addExact));
        for (String id : all.keySet()) {
            var product = Objects.requireNonNull(products.findProduct(id));
            if (!yard.handledStorageClassIds().contains(product.storageClassId()) || product.unitMassKg() > yard.maxHandledUnitMassKg())
                return false;
        }
        return true;
    }
    private static void requireStation(String id, Stage18StationStorage store) {
        if (!id.equals(store.stationId())) throw new IllegalArgumentException("Wrong refit custody owner");
    }
    /**
     * Actual docked context supplied by the authoritative caller.
     * @param ship actual existing source ship
     * @param yard installed capability
     * @param capacityPreflight pure actual cargo/consumable capacity check for the target
     * @param ownerActorId actual authorized equipment owner, or null for legacy owner work
     * @param paymentPreflight pure actual held-money/operator check
     * @param settlePayment exact final money transfer, returning false without effects when unavailable
     */
    public record Context(EngineeringComponent ship, YardCapabilitySnapshot yard, Predicate<Completion> capacityPreflight,
            String ownerActorId, Predicate<Order> paymentPreflight, Predicate<Order> settlePayment) {
        /**
         * Preserves owner-only callers; a financial obligation cannot complete through this context.
         * @param ship actual ship
         * @param yard installed yard
         * @param capacityPreflight pure target guard
         */
        public Context(EngineeringComponent ship, YardCapabilitySnapshot yard, Predicate<Completion> capacityPreflight) {
            this(ship, yard, capacityPreflight, null, order -> order.servicePayment() == null, order -> false);
        }
        /**
         * Requires all physical context owners and the explicit cargo guard.
         * @param ship existing ship
         * @param yard installed yard
         * @param capacityPreflight actual cargo guard
         * @param ownerActorId actual authorized actor
         * @param paymentPreflight pure funding check
         * @param settlePayment final exact transfer
         */
        public Context {
            Objects.requireNonNull(ship); Objects.requireNonNull(yard); Objects.requireNonNull(capacityPreflight);
            Objects.requireNonNull(paymentPreflight); Objects.requireNonNull(settlePayment);
            if (ownerActorId != null && (ownerActorId.isBlank() || ownerActorId.length() > 512
                    || ownerActorId.contains("\n") || ownerActorId.contains("\r")))
                throw new IllegalArgumentException("Invalid refit equipment owner");
        }
    }
    /**
     * Must be retained with the mutated ships and pending queue in the same composed checkpoint.
     * @param custody exact updated removed equipment
     * @param completed physically completed jobs only
     */
    public record AdvanceResult(ShipyardModuleCustodyState custody, List<Order> completed) {
        /**
         * Freezes completion reporting.
         * @param custody exact physical owner
         * @param completed completed jobs
         */
        public AdvanceResult { Objects.requireNonNull(custody); completed = List.copyOf(completed); }
    }
}
