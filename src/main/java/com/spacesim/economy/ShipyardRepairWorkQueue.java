package com.spacesim.economy;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.*;
import com.spacesim.content.ship.*;
import com.spacesim.economy.ShipyardRepairQueueState.RepairOrder;
import com.spacesim.economy.Stage18ShipyardRuntime.YardCapabilitySnapshot;
import com.spacesim.economy.Stage18ShipyardRuntime.YardWorkBudget;
import com.spacesim.persistence.EntityId;
import com.spacesim.ship.*;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipyardEngineeringService.*;
import java.util.*;
import java.util.function.Function;

/** Finite reserved-material repairs executed once per actual completed interval. */
public final class ShipyardRepairWorkQueue {
    private final Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
    private final Stage18ShipyardRuntime physical = new Stage18ShipyardRuntime(
            Stage22CivilianMiningProductionPath.loadRuntimeShipyards(), ontology, Stage22CivilianMiningProductionPath.loadProducts());
    private final TreeMap<String, RepairOrder> orders = new TreeMap<>();
    private long lastTick;
    /**
     * Restores exact custody/progress; no stock, repair or work is granted.
     * @param saved pending job checkpoint
     */
    public ShipyardRepairWorkQueue(ShipyardRepairQueueState saved) {
        lastTick = saved.lastProcessedTick();
        for (var order : saved.orders()) { validateRecipe(order); orders.put(order.orderId(), order); }
    }
    /** @return exact pending jobs and completed-tick watermark */
    public ShipyardRepairQueueState capture() { return new ShipyardRepairQueueState(lastTick, List.copyOf(orders.values())); }
    /**
     * Plans with actual current installed capability and carried loads.
     * @param asset persistent ship ID
     * @param ship actual fitted engineering component
     * @param yard actual installed yard
     * @return common repair feasibility and work requirements
     */
    public static WorkPlan plan(EntityId asset, EngineeringComponent ship, YardCapabilitySnapshot yard) {
        return planner(ship.fit).planRepair(asset, ship.fit, ship.runtimeState.consumables(), ship.instanceState.damage(), yard.plannerCapability());
    }
    /**
     * Quotes foreign service from the same actual repair plan and physical input bill.
     * @param asset exact ship identity
     * @param ship actual fitted and damaged ship
     * @param yard actual compatible installed yard
     * @return disclosed full reservation in milli-credits
     */
    public long serviceQuote(EntityId asset, EngineeringComponent ship, YardCapabilitySnapshot yard) {
        var planned = plan(asset, ship, yard);
        if (!planned.feasibility().feasible()) throw new IllegalStateException("No compatible repair service");
        return ShipyardRepairServicePayment.quote(planned.requirements().totalWorkSeconds(),
                physical.repairMaterialRequirements(planned, ship.instanceState.damage()));
    }
    static ShipyardEngineeringService planner(ShipEngineeringState.InstalledFit fit) {
        return Catalogs.UNION.findHullProfile(fit.hullId()) != null ? Catalogs.UNION_PLANNER
                : Catalogs.EMPIRE.findHullProfile(fit.hullId()) != null ? Catalogs.EMPIRE_PLANNER : Catalogs.COMMON_PLANNER;
    }
    static ShipEngineeringCatalog engineering(ShipEngineeringState.InstalledFit fit) {
        return Catalogs.ENGINEERING.findHull(fit.hullId()) != null ? Catalogs.ENGINEERING : Catalogs.COMMON_ENGINEERING;
    }
    private static final class Catalogs {
        private static final ShipEngineeringCatalog ENGINEERING = Stage22FreightStrategicEngineeringCatalogLoader.loadDefault();
        private static final ShipyardIndustrialCatalog UNION = Stage22CivilianMiningIndustrialCatalogLoader.loadDefault();
        private static final ShipyardIndustrialCatalog EMPIRE = Stage22FreightStrategicProductionCatalogs.industrial(
                Stage22CorePairShipyardIndustrialCatalogLoader.loadEmpireDefault());
        private static final ShipyardEngineeringService UNION_PLANNER = new ShipyardEngineeringService(ENGINEERING, UNION);
        private static final ShipyardEngineeringService EMPIRE_PLANNER = new ShipyardEngineeringService(ENGINEERING, EMPIRE);
        private static final ShipEngineeringCatalog COMMON_ENGINEERING = ShipEngineeringCatalogLoader.loadDefault();
        private static final ShipyardEngineeringService COMMON_PLANNER = new ShipyardEngineeringService(COMMON_ENGINEERING, ShipyardIndustrialCatalogLoader.loadDefault(COMMON_ENGINEERING));
    }
    private void validateRecipe(RepairOrder order) {
        var definition = Stage22CivilianMiningProductionPath.loadRuntimeShipyards().findYard(order.yardDefinitionId());
        if (definition == null) throw new IllegalArgumentException("Unknown repair yard definition");
        // Authored design envelope is used only to validate the recipe, never as an operational grant.
        var design = new ShipyardCapability(order.yardInstanceId(), definition.berthDimensionsM(), definition.maxServiceMassKg(),
                definition.stage175FabricationCapabilities(), definition.stage175HandledRequirementIds(), definition.toolingTags(),
                definition.precisionCapability(), definition.ratedEngineeringWorkRate(), definition.laborCapacity(),
                definition.automationCapacity(), definition.ratedIntegrationPowerW());
        var plan = planner(order.sourceFit()).planRepair(new EntityId(order.assetId()), order.sourceFit(),
                ConsumableState.empty(), order.sourceDamage(), design);
        if (!plan.feasibility().feasible() || Double.compare(plan.requirements().totalWorkSeconds(), order.requiredWorkSeconds()) != 0
                || !physical.repairMaterialRequirements(plan, order.sourceDamage()).equals(order.reservedCommodityMassByIdKg()))
            throw new IllegalArgumentException("Repair custody/work does not match the authored physical recipe");
    }
    /**
     * Rebinds escrow to the same storage as manufacturing and individual equipment.
     * @param stores canonical station resolver
     */
    public void restoreReservations(Function<String, Stage18StationStorage> stores) {
        orders.values().stream().map(RepairOrder::stationId).distinct().forEach(id -> {
            var store = stores.apply(id);
            if (store == null || !store.stationId().equals(id)) throw new IllegalArgumentException("Missing repair station");
            store.replaceContentsWithRepairReservation(store.snapshotCommodityMassByIdKg(), store.snapshotProductCountById(), reservedMass(id, null, Map.of()));
        });
    }
    /**
     * Reserves an exact finite recipe, retaining occupancy and leaving damage unchanged.
     * @param id unique job identity
     * @param fleet original personally owned fleet
     * @param asset same physical ship ID
     * @param ship actual engineering component
     * @param storage actual owner-authorized station stock
     * @param yard installed compatible operational yard
     * @param tick actual reservation tick
     */
    public void start(String id, long fleet, EntityId asset, EngineeringComponent ship, Stage18StationStorage storage,
            YardCapabilitySnapshot yard, long tick) {
        start(id, fleet, asset, ship, storage, yard, tick, null);
    }
    /**
     * Reserves physical work with money already held by the campaign for a foreign operator.
     * @param id job identity
     * @param fleet actual fleet
     * @param asset actual ship
     * @param ship current engineering
     * @param storage operator material custody
     * @param yard installed working yard
     * @param tick current completed tick
     * @param payment actual held money, or null for owner work
     */
    public void start(String id, long fleet, EntityId asset, EngineeringComponent ship, Stage18StationStorage storage,
            YardCapabilitySnapshot yard, long tick, ShipyardRepairServicePayment payment) {
        if (orders.size() >= ShipyardRepairQueueState.CAPACITY || orders.containsKey(id)
                || orders.values().stream().anyMatch(o -> o.fleetId() == fleet) || tick < lastTick
                || !orders.isEmpty() && tick != lastTick) throw new IllegalStateException("Repair queue unavailable or stale");
        if (!yard.active()) throw new IllegalStateException("Yard is not operational");
        var plan = plan(asset, ship, yard);
        if (!plan.feasibility().feasible() || plan.requirements().totalWorkSeconds() <= 0)
            throw new IllegalStateException("No feasible physical repair work");
        var inputs = physical.repairMaterialRequirements(plan, ship.instanceState.damage());
        var raw = new TreeMap<>(storage.snapshotCommodityMassByIdKg());
        for (var input : inputs.entrySet()) {
            if (!yard.handledStorageClassIds().contains(ontology.findCommodity(input.getKey()).storageClassId()))
                throw new IllegalStateException("Yard cannot handle a required material");
            double available = raw.getOrDefault(input.getKey(), 0d);
            if (available < input.getValue()) throw new IllegalStateException("Insufficient physical repair material");
            raw.put(input.getKey(), available - input.getValue());
        }
        var order = new RepairOrder(id, fleet, asset.value(), storage.stationId(), yard.yardInstanceId(), yard.yardDefinitionId(),
                tick, ship.fit, ship.instanceState.damage(), plan.requirements().totalWorkSeconds(), 0, inputs, payment);
        validateRecipe(order);
        storage.replaceContentsWithRepairReservation(raw, storage.snapshotProductCountById(), reservedMass(storage.stationId(), null, inputs));
        orders.put(id, order); lastTick = tick;
    }
    /**
     * Returns exact unconsumed escrow; actual spent work is not refunded and no damage is repaired.
     * @param id pending job
     * @param store exact owner station
     */
    public void cancel(String id, Stage18StationStorage store) {
        var order = orders.get(id);
        if (order == null || !order.stationId().equals(store.stationId())) throw new IllegalArgumentException("Wrong repair custody owner");
        if (order.servicePayment() != null && order.completedWorkSeconds() > 0)
            throw new IllegalStateException("Paid repair can only be cancelled before actual work starts");
        var raw = new TreeMap<>(store.snapshotCommodityMassByIdKg());
        order.reservedCommodityMassByIdKg().forEach((item, mass) -> raw.merge(item, mass, Double::sum));
        store.replaceContentsWithRepairReservation(raw, store.snapshotProductCountById(), reservedMass(order.stationId(), id, Map.of()));
        orders.remove(id);
    }
    /**
     * Advances jobs with actual shared work budgets. Missing access, contact or changed damage pauses.
     * @param tick actual completed campaign tick
     * @param stores canonical station resolver
     * @param contexts authorized docked physical ship and installed yard, or null to pause
     * @param budgets one shared finite budget per actual yard for this interval
     * @return jobs physically completed on this tick
     */
    public List<RepairOrder> advance(long tick, Function<String, Stage18StationStorage> stores,
            Function<RepairOrder, RepairContext> contexts, Function<RepairOrder, YardWorkBudget> budgets) {
        if (tick < 0) throw new IllegalArgumentException("Negative repair tick");
        if (tick <= lastTick || orders.isEmpty()) return List.of();
        if (tick != lastTick + 1) throw new IllegalArgumentException("Repair cannot credit missed intervals");
        var completed = new ArrayList<RepairOrder>();
        for (var order : List.copyOf(orders.values())) {
            var context = contexts.apply(order);
            if (context == null || !context.ship().fit.equals(order.sourceFit())
                    || !context.ship().instanceState.damage().equals(order.sourceDamage()) || !context.yard().active()
                    || !context.yard().yardInstanceId().equals(order.yardInstanceId())
                    || !context.yard().yardDefinitionId().equals(order.yardDefinitionId())) continue;
            var plan = plan(new EntityId(order.assetId()), context.ship(), context.yard());
            if (!plan.feasibility().feasible() || Double.compare(plan.requirements().totalWorkSeconds(), order.requiredWorkSeconds()) != 0) continue;
            var budget = budgets.apply(order); var store = stores.apply(order.stationId());
            if (budget == null || store == null) continue;
            double remaining = order.requiredWorkSeconds() - order.completedWorkSeconds();
            double allocated = budget.allocate(remaining);
            if (allocated >= remaining || order.completedWorkSeconds() + allocated >= order.requiredWorkSeconds()) {
                var nextDamage = plan.completionDamage();
                var instance = context.ship().instanceState;
                var target = new ShipInstanceRuntimeState(nextDamage, repairedShields(context.ship(), nextDamage.moduleDamage()),
                        instance.maintenance(), instance.weaponLoadout(), instance.weaponMountRuntime());
                store.replaceContentsWithRepairReservation(store.snapshotCommodityMassByIdKg(), store.snapshotProductCountById(), reservedMass(order.stationId(), order.orderId(), Map.of()));
                context.ship().setInstanceState(target); orders.remove(order.orderId()); completed.add(order);
            } else if (allocated > 0) orders.put(order.orderId(), order.withWork(order.completedWorkSeconds() + allocated));
        }
        lastTick = tick;
        return List.copyOf(completed);
    }
    private Map<String, ShieldFieldRuntime.State> repairedShields(EngineeringComponent ship, ShipEngineeringState.DamageState damage) {
        var catalog = Catalogs.ENGINEERING.findHull(ship.fit.hullId()) != null ? Catalogs.ENGINEERING : Catalogs.COMMON_ENGINEERING;
        var derived = new ShipEngineeringRuntime(catalog).derive(ship.fit, ship.runtimeState, damage);
        var result = new TreeMap<String, ShieldFieldRuntime.State>();
        var runtime = new ShieldFieldRuntime();
        for (var fitted : new ShipShieldEngineeringAdapter().derive(derived)) {
            var before = ship.instanceState.shieldStatesByMount().getOrDefault(fitted.mountId(),
                    new ShieldFieldRuntime.State(0, 0, true, 0, fitted.emitterIntegrity()));
            result.put(fitted.mountId(), runtime.withEmitterIntegrity(fitted.definition(), before, fitted.emitterIntegrity()));
        }
        return result;
    }
    private Map<String, Double> reservedMass(String station, String excluded, Map<String, Double> extra) {
        var mass = new TreeMap<String, Double>();
        orders.values().stream().filter(o -> o.stationId().equals(station) && !o.orderId().equals(excluded))
                .forEach(o -> addMass(mass, o.reservedCommodityMassByIdKg()));
        addMass(mass, extra); return mass;
    }
    private void addMass(Map<String, Double> mass, Map<String, Double> inputs) {
        inputs.forEach((id, kg) -> {
            var commodity = ontology.findCommodity(id);
            if (commodity == null || commodity.quantityUnit() != Stage18ResourceOntologyCatalog.QuantityUnit.KILOGRAM)
                throw new IllegalArgumentException("Unknown physical repair commodity");
            mass.merge(commodity.storageClassId(), kg, Double::sum);
        });
    }
    /**
     * Actual authorization/context supplied by the campaign on the same tick.
     * @param ship existing docked physical engineering component
     * @param yard actual installed yard capability
     */
    public record RepairContext(EngineeringComponent ship, YardCapabilitySnapshot yard) { }
}
