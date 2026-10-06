package com.spacesim.persistence;

import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.economy.Stage18LogisticsRuntime;
import com.spacesim.economy.Stage18LogisticsRuntime.HandlingCapability;
import com.spacesim.economy.Stage18LogisticsRuntime.Status;
import com.spacesim.economy.Stage18LogisticsRuntime.TransferBudget;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.economy.Stage18StationStorage.StationStorageSnapshot;
import com.spacesim.persistence.Stage20FreightPersistentState.CargoLotState;
import com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase;
import com.spacesim.persistence.Stage20FreightPersistentState.FreighterState;
import com.spacesim.persistence.Stage20FreightPersistentState.TransportOrderState;
import com.spacesim.world.FleetId;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.Stage20OperationalIndustrialSpecializationPlan.OperationalSpecializationReport;
import com.spacesim.world.StarSystemId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Ordinary mutable Stage-20.5B freight runtime over Stage-18 physical storage transfers.
 *
 * <p>Orders authorize movement but never create cargo. Loading and unloading call the same
 * {@link Stage18LogisticsRuntime} used by station logistics, so source inventory decreases before a
 * provenance lot can exist aboard. Route progress is exact and neighbor-by-neighbor according to
 * the persisted order. Destruction removes the physical hold and its lots without replacement,
 * making loss directly reduce future deliverable supply.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20FreightRuntime {
    private static final double EPSILON = 1.0e-9d;
    private static final class RefitCatalog {
        private static final com.spacesim.content.ship.ShipEngineeringCatalog ENGINEERING =
                com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader.loadDefault();
    }
    private static final class ModuleCargoCatalog {
        private static final Stage18ManufacturingProductRegistry PRODUCTS = com.spacesim.content.Stage22CivilianMiningProductionPath.loadProducts();
    }

    private final Stage18ResourceOntologyCatalog ontology;
    private final Stage18ManufacturingProductRegistry products;
    private final Stage18LogisticsRuntime logistics;
    private final long rootSeed;
    private final String generatorVersion;
    private final String worldFingerprint;
    private final String materializationVersion;
    private final String compatibilityAuthorityVersion;
    private final long nextFleetIdValue;
    private long nextCargoLotOrdinal;
    private final TreeMap<FleetId, FreighterState> freighters = new TreeMap<>();
    private final TreeMap<FleetId, Stage18StationStorage> holds = new TreeMap<>();
    private final TreeMap<String, CargoLotState> lots = new TreeMap<>();
    private final TreeMap<String, Stage20FreightPersistentState.ProductCargoLotState> productLots = new TreeMap<>();
    private final TreeMap<String, TransportOrderState> orders = new TreeMap<>();
    private final TreeMap<FleetId, Stage20FreightPersistentState.PersonalMiningOrder> mining = new TreeMap<>();
    private com.spacesim.economy.ShipyardModuleCustodyState equipmentCustody = com.spacesim.economy.ShipyardModuleCustodyState.empty();
    private com.spacesim.economy.Stage18StationProductionBridge personalExtraction;
    private com.spacesim.content.Stage18ExtractionCatalog personalExtractionCatalog;

    private Stage20FreightRuntime(
            Stage20FreightPersistentState state,
            Stage18ResourceOntologyCatalog ontology,
            Stage18ManufacturingProductRegistry products) {
        Stage20FreightPersistentState saved = Objects.requireNonNull(state, "state");
        this.ontology = Objects.requireNonNull(ontology, "ontology");
        this.products = Objects.requireNonNull(products, "products");
        this.logistics = new Stage18LogisticsRuntime(ontology, products);
        rootSeed = saved.rootSeed();
        generatorVersion = saved.generatorVersion();
        worldFingerprint = saved.worldFingerprint();
        materializationVersion = saved.materializationVersion();
        compatibilityAuthorityVersion = saved.compatibilityAuthorityVersion();
        nextFleetIdValue = saved.nextFleetIdValue();
        nextCargoLotOrdinal = saved.nextCargoLotOrdinal();
        saved.freighters().forEach(value -> {
            freighters.put(value.fleetId(), value);
            holds.put(value.fleetId(), Stage18StationStorage.restore(
                    ontology, products, value.cargoStorage()));
        });
        saved.cargoLots().forEach(value -> lots.put(value.lotId(), value));
        saved.productLots().forEach(value -> productLots.put(value.lotId(), value));
        saved.orders().forEach(value -> orders.put(value.orderId(), value));
        saved.personalMiningOrders().forEach(value -> mining.put(value.fleetId(), value));
    }

    /**
     * Restores an independently validated physical freight sidecar.
     *
     * @param state exact persistent freight sidecar
     * @return mutable ordinary freight runtime
     */
    public static Stage20FreightRuntime restore(Stage20FreightPersistentState state) {
        return new Stage20FreightRuntime(
                state,
                Stage18ResourceOntologyLoader.loadDefault(),
                com.spacesim.content.Stage22CivilianMiningProductionPath.loadProducts());
    }

    /**
     * Validates all generated authority before restoring the mutable runtime.
     *
     * @param campaign exact saved generated campaign
     * @param specialization exact matching closed Stage-20F authority
     * @param state exact persistent freight sidecar
     * @param compatibility explicit hull/fit compatibility authority
     * @param engineering exact named engineering catalog
     * @return mutable ordinary freight runtime
     */
    public static Stage20FreightRuntime restore(
            Stage20GeneratedCampaignPersistentState campaign,
            OperationalSpecializationReport specialization,
            Stage20FreightPersistentState state,
            Stage20FreightRuntimeMaterializer.FreighterCompatibilityAuthority compatibility,
            com.spacesim.content.ship.ShipEngineeringCatalog engineering) {
        Stage20FreightPersistentState checked = Stage20FreightRuntimeMaterializer.validateRestore(
                campaign, specialization, state, compatibility, engineering);
        return restore(checked);
    }

    /**
     * Restores from the canonical saved authority without rerunning the Stage-20F planner.
     *
     * @param campaign exact saved generated campaign
     * @param state exact persistent freight sidecar
     * @param compatibility explicit hull/fit compatibility authority
     * @param engineering exact named engineering catalog
     * @return mutable ordinary freight runtime
     */
    public static Stage20FreightRuntime restore(
            Stage20GeneratedCampaignPersistentState campaign,
            Stage20FreightPersistentState state,
            Stage20FreightRuntimeMaterializer.FreighterCompatibilityAuthority compatibility,
            com.spacesim.content.ship.ShipEngineeringCatalog engineering) {
        Stage20FreightPersistentState checked = Stage20FreightRuntimeMaterializer.validateRestore(
                campaign, state, compatibility, engineering);
        return restore(checked);
    }

    /**
     * Captures exact fleet, hold, lot, route and deadline identity without regeneration.
     *
     * @return complete deterministic Stage-20.5B freight sidecar
     */
    public Stage20FreightPersistentState capture() {
        ArrayList<FreighterState> fleetRows = new ArrayList<>();
        for (FreighterState state : freighters.values()) {
            fleetRows.add(copyFreighter(state, holds.get(state.fleetId()).snapshot()));
        }
        return new Stage20FreightPersistentState(
                Stage20FreightPersistentState.CURRENT_VERSION,
                rootSeed,
                generatorVersion,
                worldFingerprint,
                materializationVersion,
                compatibilityAuthorityVersion,
                nextFleetIdValue,
                nextCargoLotOrdinal,
                fleetRows,
                List.copyOf(lots.values()),
                List.copyOf(orders.values()), List.copyOf(mining.values()), List.copyOf(productLots.values()));
    }

    /** @return deterministic current personal SI mining intents */
    public List<Stage20FreightPersistentState.PersonalMiningOrder> personalMiningOrders() {
        return List.copyOf(mining.values());
    }

    /**
     * Records a validated intent without performing physical work.
     * @param order current-tick personal excavation intent
     */
    public void startPersonalMining(Stage20FreightPersistentState.PersonalMiningOrder order) {
        requirePhase(requireFreighter(order.fleetId()), FreightPhase.IDLE);
        if (mining.containsKey(order.fleetId())) throw new IllegalStateException("Mining already assigned");
        mining.put(order.fleetId(), order);
    }

    /**
     * Cancels future work without altering reserves or cargo.
     * @param fleetId actual personal fleet
     */
    public void stopPersonalMining(FleetId fleetId) { mining.remove(fleetId); }

    /**
     * Marks one completed tick before allocating its interval.
     * @param fleetId actual personal fleet
     * @param tick completed authoritative tick
     * @return whether this tick was newly claimed
     */
    public boolean claimPersonalMiningTick(FleetId fleetId, long tick) {
        var order = mining.get(fleetId);
        if (order == null || tick <= order.lastProcessedTick()) return false;
        mining.put(fleetId, new Stage20FreightPersistentState.PersonalMiningOrder(fleetId, order.sourceId(),
                order.methodId(), order.requestedSourceKgPerTick(), tick));
        return true;
    }

    /**
     * Finds one immutable current fleet state.
     *
     * @param fleetId stable real fleet identity
     * @return current fleet state or empty when unknown
     */
    public Optional<FreighterState> findFreighter(FleetId fleetId) {
        FreighterState state = freighters.get(fleetId);
        return state == null ? Optional.empty() : Optional.of(copyFreighter(
                state, holds.get(state.fleetId()).snapshot()));
    }

    /**
     * Returns one immutable physical cargo-hold snapshot.
     *
     * @param fleetId stable real fleet identity
     * @return exact current Stage-18 hold snapshot
     */
    public StationStorageSnapshot cargoHoldSnapshot(FleetId fleetId) {
        return requireHold(fleetId).snapshot();
    }

    /**
     * Returns the actual physical hold for an authorized composed equipment operation.
     * @param fleetId actual carrying fleet
     * @return canonical existing hold
     */
    public Stage18StationStorage moduleCargoStorage(FleetId fleetId) { return requireHold(fleetId); }
    /**
     * Finds an existing cargo owner without copying fleet, lot or route checkpoints.
     * @param storageId exact physical storage identity
     * @return existing carrying fleet, if present
     */
    public Optional<FleetId> moduleCargoCarrier(String storageId) {
        return freighters.values().stream().filter(f -> f.cargoStorage().stationId().equals(storageId)).map(FreighterState::fleetId).findFirst();
    }

    /**
     * Rebinds exact aboard equipment and its redundant total mass without creating stock.
     * @param custody actual composed equipment inventory
     */
    public void synchronizeModuleCustody(com.spacesim.economy.ShipyardModuleCustodyState custody) {
        var masses = com.spacesim.economy.ShipyardModuleCustodyStorage.massByStation(custody, ModuleCargoCatalog.PRODUCTS);
        var updates = new TreeMap<FleetId, FreighterState>();
        for (var f : freighters.values()) {
            double mass = masses.getOrDefault(Stage20FreightPersistentState.cargoHoldId(f.fleetId()), Map.of())
                    .values().stream().mapToDouble(Double::doubleValue).sum();
            updates.put(f.fleetId(), new FreighterState(f.fleetId(), f.stableFactionId(), f.ownershipOrdinal(), f.hullId(),
                    f.fitId(), f.cargoCapacityKg(), f.currentSystemId(), f.physicalState(), f.phase(), f.activeOrderId(),
                    f.routeIndex(), requireHold(f.fleetId()).snapshot(), f.legalFactionId(), mass));
        }
        // Validate all masses above before replacing any mirrored row.
        for (var f : updates.values()) com.spacesim.economy.ShipyardModuleCustodyStorage.bindOwner(custody, ModuleCargoCatalog.PRODUCTS, requireHold(f.fleetId()));
        freighters.putAll(updates); equipmentCustody = custody;
    }

    /**
     * Stages a supported same-hull fitting and its finite hold without changing cargo or provenance.
     * @param fleetId actual idle personal ship
     * @param targetFitId authored Union strategic cargo or mining fitting
     * @return exact candidate freight row
     */
    public FreighterState previewPersonalRefit(FleetId fleetId, String targetFitId) {
        var original = requireFreighter(fleetId);
        requirePhase(original, FreightPhase.IDLE);
        if (mining.containsKey(fleetId)) throw new IllegalStateException("Stop excavation before refitting");
        var catalog = RefitCatalog.ENGINEERING;
        String legacyFit = "fit.test_bulk_freighter_baseline_v1";
        String sourceId = original.fitId().equals(legacyFit)
                ? com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader.UNION_FREIGHT_STRATEGIC_FIT : original.fitId();
        if (targetFitId.equals(legacyFit)) targetFitId = com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader.UNION_FREIGHT_STRATEGIC_FIT;
        var source = catalog.findDemonstratorFit(sourceId);
        var target = catalog.findDemonstratorFit(targetFitId);
        String cargo = com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader.UNION_FREIGHT_STRATEGIC_FIT;
        String miningFit = com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader.UNION_MINING_FREIGHT_STRATEGIC_FIT;
        if (source == null || target == null || !source.hullId().equals(target.hullId())
                || !original.stableFactionId().equals("faction.beta")
                || !original.hullId().equals("hull.test_bulk_freighter_v1") || sourceId.equals(targetFitId)
                || !java.util.Set.of(cargo, miningFit).contains(sourceId)
                || !java.util.Set.of(cargo, miningFit).contains(targetFitId))
            throw new IllegalArgumentException("Unsupported personal freight refit");
        double capacity = targetFitId.equals(miningFit)
                ? com.spacesim.content.ship.Stage22CivilianMiningEngineeringCatalogLoader.ORE_CAPACITY_KG
                : Stage20FreightRuntimeMaterializer.CURRENT_PAYLOAD_KG;
        var oldHold = requireHold(fleetId).snapshot();
        var capacities = new TreeMap<String, Double>();
        oldHold.capacityByStorageClassKg().keySet().forEach(id -> capacities.put(id, capacity));
        var nextHold = Stage18StationStorage.restore(ontology, products, new StationStorageSnapshot(
                oldHold.stationId(), capacities, oldHold.commodityMassByIdKg(), oldHold.productCountById()));
        com.spacesim.economy.ShipyardModuleCustodyStorage.bindOwner(equipmentCustody, ModuleCargoCatalog.PRODUCTS, nextHold);
        return new FreighterState(original.fleetId(), original.stableFactionId(), original.ownershipOrdinal(),
                original.hullId(), targetFitId.equals(cargo) ? legacyFit : targetFitId, capacity, original.currentSystemId(), original.physicalState(),
                original.phase(), original.activeOrderId(), original.routeIndex(), nextHold.snapshot(), original.legalFactionId(), original.carriedEquipmentMassKg());
    }

    /**
     * Applies an immediately revalidated fitting/hold change, retaining all cargo lots and ownership.
     * @param candidate pure candidate from the current live hold
     */
    public void applyPersonalRefit(FreighterState candidate) {
        var current = previewPersonalRefit(candidate.fleetId(), candidate.fitId());
        if (!current.equals(candidate)) throw new IllegalStateException("Personal refit hold changed after preflight");
        var hold = Stage18StationStorage.restore(ontology, products, candidate.cargoStorage());
        com.spacesim.economy.ShipyardModuleCustodyStorage.bindOwner(equipmentCustody, ModuleCargoCatalog.PRODUCTS, hold);
        freighters.put(candidate.fleetId(), candidate);
        holds.put(candidate.fleetId(), hold);
    }

    /**
     * Settles finite physical extraction into an existing idle ship hold.
     * The command owner must validate ownership, installed equipment and local source geometry,
     * and supply the already allocated simulation interval. This method never opens a new budget.
     * @param fleetId actual carrying fleet
     * @param source actual finite source owned by industrial persistence
     * @param methodId authored extraction method
     * @param requestedSourceMassKg requested gross mass
     * @param capability installed physical equipment capability
     * @param budget shared finite interval budget
     * @param seconds authoritative observation time
     * @return ordinary Stage18 extraction result
     */
    public com.spacesim.economy.Stage18ExtractionRuntime.ExtractionResult extractPersonalCommodity(
            FleetId fleetId, com.spacesim.economy.Stage18ExtractionRuntime.PhysicalSourceState source,
            String methodId, double requestedSourceMassKg,
            com.spacesim.economy.Stage18ExtractionRuntime.ExtractionCapability capability,
            com.spacesim.economy.Stage18ExtractionRuntime.IntervalBudget budget, double seconds) {
        var fleet = requireFreighter(fleetId); requirePhase(fleet, FreightPhase.IDLE);
        Objects.requireNonNull(source, "source");
        requireNonNegativeFinite(seconds, "seconds");
        if (nextCargoLotOrdinal == Long.MAX_VALUE) throw new IllegalStateException("Cargo lot allocator exhausted");
        if (personalExtractionCatalog == null) personalExtractionCatalog = com.spacesim.content.Stage18ExtractionCatalogLoader.loadDefault();
        var extraction = personalExtractionCatalog;
        var method = extraction.findMethod(methodId);
        if (method != null && Double.isFinite(requestedSourceMassKg) && requestedSourceMassKg > 0) {
            double output = Math.min(requestedSourceMassKg, source.remainingAccessibleMassKg())
                    * source.gradeFraction() * source.sourceRecoveryFraction() * method.recoveryFraction();
            if (fleet.cargoMassKg() + output > fleet.cargoCapacityKg() + EPSILON)
                return new com.spacesim.economy.Stage18ExtractionRuntime.ExtractionResult(
                        com.spacesim.economy.Stage18ExtractionRuntime.Status.STORAGE_FULL, 0, 0, 0, 0, 0, 0);
        }
        if (personalExtraction == null) {
            personalExtraction = new com.spacesim.economy.Stage18StationProductionBridge(ontology, products,
                    new com.spacesim.economy.Stage18FacilityRuntime(com.spacesim.content.Stage18FacilityCatalogLoader.loadDefault()),
                    new com.spacesim.economy.Stage18ExtractionRuntime(ontology, extraction),
                    new com.spacesim.economy.Stage18RefiningRuntime(ontology, com.spacesim.content.Stage18RefiningCatalogLoader.loadDefault()),
                    new com.spacesim.economy.Stage18ManufacturingRuntime(ontology,
                            com.spacesim.content.Stage18ManufacturingCatalogLoader.loadDefault(), products));
        }
        var result = personalExtraction.extractToStorage(source, methodId, requestedSourceMassKg,
                requireHold(fleetId), capability, budget);
        if (result.committed()) {
            if (result.outputMassStoredKg() > 0) {
                String id = "freight-lot:" + nextCargoLotOrdinal++;
                lots.put(id, new CargoLotState(id, fleetId, Stage20FreightPersistentState.personalExtractionOrderId(fleetId),
                        source.outputCommodityId(), result.outputMassStoredKg(), source.sourceId(),
                        "player-extraction:" + source.sourceId(), seconds));
            }
            refreshFreighterHold(fleetId);
        }
        return result;
    }

    /**
     * Exchanges manually purchased commodity mass using the same finite logistics/hold authority.
     * The caller validates personal ownership, docking, consideration and the once-per-tick budget.
     * @param fleetId existing unassigned reserve
     * @param endpoint actual station storage
     * @param commodityId admitted physical commodity
     * @param massKg positive requested mass
     * @param buying whether to load from the station
     * @param seconds authoritative transaction time
     * @param handling compatible physical handling
     * @param budget unspent finite interval budget
     * @return whether the physical transfer committed
     */
    public boolean exchangeManualCommodity(FleetId fleetId, Stage18StationStorage endpoint,
            String commodityId, double massKg, boolean buying, double seconds,
            HandlingCapability handling, TransferBudget budget) {
        var fleet = requireFreighter(fleetId);
        requirePhase(fleet, FreightPhase.IDLE);
        requireNonNegativeFinite(seconds, "seconds");
        if (!Double.isFinite(massKg) || massKg <= 0 || buying && fleet.cargoMassKg() + massKg > fleet.cargoCapacityKg()) return false;
        if (buying && nextCargoLotOrdinal == Long.MAX_VALUE) return false;
        String order = Stage20FreightPersistentState.manualCargoOrderId(fleetId);
        if (!buying && lots.values().stream().filter(l -> l.fleetId().equals(fleetId)
                && (l.orderId().equals(order) || l.orderId().equals(Stage20FreightPersistentState.personalExtractionOrderId(fleetId)))
                && l.commodityId().equals(commodityId))
                .mapToDouble(CargoLotState::massKg).sum() + EPSILON < massKg) return false;
        var hold = requireHold(fleetId);
        var result = logistics.transferCommodity(buying ? endpoint : hold, buying ? hold : endpoint,
                commodityId, massKg, handling, budget);
        if (!result.transferred()) return false;
        if (buying) {
            String id = "freight-lot:" + nextCargoLotOrdinal++;
            lots.put(id, new CargoLotState(id, fleetId, order, commodityId, massKg,
                    endpoint.stationId(), "player-market:" + endpoint.stationId(), seconds));
        } else consumePersonalLots(fleetId, commodityId, massKg);
        refreshFreighterHold(fleetId);
        return true;
    }

    /**
     * Transfers physical countable finished goods and records their loading source.
     * The caller owns personal ownership, berth authorization and the finite shared handling interval.
     * @param fleetId existing idle personal freight asset
     * @param endpoint actual station storage
     * @param productId authored product identity
     * @param count positive whole units
     * @param loading whether the station supplies the hold
     * @param seconds authoritative completed simulation time
     * @param handling actual compatible endpoint handling
     * @param budget caller-owned finite shared handling budget
     * @return whether the atomic physical transfer committed
     */
    public boolean exchangePersonalProduct(FleetId fleetId, Stage18StationStorage endpoint, String productId,
            int count, boolean loading, double seconds, HandlingCapability handling, TransferBudget budget) {
        var fleet = requireFreighter(fleetId);
        requirePhase(fleet, FreightPhase.IDLE);
        requireNonNegativeFinite(seconds, "product loading time");
        var product = products.findProduct(productId);
        if (product == null || count <= 0) return false;
        double mass = product.unitMassKg() * count;
        if (!Double.isFinite(mass) || loading && (fleet.cargoMassKg() + mass > fleet.cargoCapacityKg() + EPSILON
                || nextCargoLotOrdinal == Long.MAX_VALUE)) return false;
        if (!loading && productLots.values().stream().filter(l -> l.fleetId().equals(fleetId) && l.productId().equals(productId))
                .mapToLong(Stage20FreightPersistentState.ProductCargoLotState::count).sum() < count) return false;
        var hold = requireHold(fleetId);
        var result = logistics.transferProduct(loading ? endpoint : hold, loading ? hold : endpoint,
                productId, count, handling, budget);
        if (!result.transferred()) return false;
        updateProductProvenance(fleetId, endpoint, productId, count, loading, seconds);
        refreshFreighterHold(fleetId);
        return true;
    }

    /**
     * Physically delivers personal cargo and returns evidence minted only after finite handling commits.
     * The berth, legal access and financial settlement remain the caller's ordinary authorities.
     * @param fleetId actual idle carrying fleet
     * @param endpoint receiving physical storage
     * @param commodityId actual commodity
     * @param massKg positive delivered kilograms
     * @param seconds actual simulation time
     * @param handling installed handling capability
     * @param budget shared finite interval
     * @return one-use physical receipt, or empty when delivery was rejected
     */
    public Optional<PersonalCommodityDeliveryReceipt> deliverPersonalCommodity(FleetId fleetId,
            Stage18StationStorage endpoint, String commodityId, double massKg, double seconds,
            HandlingCapability handling, TransferBudget budget) {
        if (!Double.isFinite(massKg) || massKg <= EPSILON) return Optional.empty();
        var deliveredLots = new ArrayList<CargoLotState>();
        double remaining = massKg;
        for (var lot : matchingCargoLots(fleetId, Stage20FreightPersistentState.manualCargoOrderId(fleetId), commodityId, true)) {
            if (remaining <= EPSILON) break;
            double portion = Math.min(remaining, lot.massKg());
            deliveredLots.add(new CargoLotState(lot.lotId(), lot.fleetId(), lot.orderId(), lot.commodityId(), portion,
                    lot.sourceEndpointId(), lot.sourceProvenanceId(), lot.loadedAtSimulationSeconds()));
            remaining -= portion;
        }
        if (!exchangeManualCommodity(fleetId, endpoint, commodityId, massKg, false, seconds, handling, budget))
            return Optional.empty();
        return Optional.of(new PersonalCommodityDeliveryReceipt(this, fleetId, endpoint.stationId(), commodityId, massKg, seconds, deliveredLots));
    }

    /**
     * Consumes a receipt from this exact runtime once; restored runtimes cannot claim old transient receipts.
     * @param receipt physical delivery evidence
     * @return whether this is its first authorized claim
     */
    public boolean claimPersonalDelivery(PersonalCommodityDeliveryReceipt receipt) {
        Objects.requireNonNull(receipt);
        if (receipt.owner != this || receipt.claimed) return false;
        receipt.claimed = true;
        return true;
    }

    /** Unforgeable transient evidence of one committed physical personal delivery. */
    public static final class PersonalCommodityDeliveryReceipt {
        private final Stage20FreightRuntime owner;
        private final FleetId fleetId;
        private final String stationId;
        private final String commodityId;
        private final double massKg;
        private final double simulationSeconds;
        private final List<CargoLotState> deliveredLots;
        private boolean claimed;
        private PersonalCommodityDeliveryReceipt(Stage20FreightRuntime owner, FleetId fleetId, String stationId,
                String commodityId, double massKg, double simulationSeconds, List<CargoLotState> deliveredLots) {
            this.owner = owner; this.fleetId = fleetId; this.stationId = stationId;
            this.commodityId = commodityId; this.massKg = massKg; this.simulationSeconds = simulationSeconds;
            this.deliveredLots = List.copyOf(deliveredLots);
        }
        /** @return actual delivering fleet */
        public FleetId fleetId() { return fleetId; }
        /** @return actual destination storage identity */
        public String stationId() { return stationId; }
        /** @return actual delivered commodity */
        public String commodityId() { return commodityId; }
        /** @return exact physically transferred kilograms */
        public double massKg() { return massKg; }
        /** @return actual delivery time */
        public double simulationSeconds() { return simulationSeconds; }
        /** @return consumed FIFO portions with original loading provenance */
        public List<CargoLotState> deliveredLots() { return deliveredLots; }
    }

    /**
     * Publishes already paid queue handling and its freight provenance without allocating another interval.
     * @param permit one-use authority from actual completed product handling
     * @param seconds actual completed world simulation time
     * @return whether physical goods and their owning provenance committed together
     */
    public boolean publishHandledPersonalProduct(com.spacesim.economy.FinishedProductTransferWorkQueue.CompletionPermit permit,
            double seconds) {
        Objects.requireNonNull(permit); requireNonNegativeFinite(seconds, "finished product handling time");
        var order = permit.order();
        var fleet = requireFreighter(order.fleetId());
        if (fleet.phase() != FreightPhase.IDLE || products.findProduct(order.productId()) == null) return false;
        var hold = requireHold(order.fleetId());
        if ((order.loading() ? permit.destination() : permit.source()) != hold) return false;
        if (order.loading() && (nextCargoLotOrdinal == Long.MAX_VALUE || fleet.cargoMassKg() + permit.massKg() > fleet.cargoCapacityKg() + EPSILON))
            return false;
        if (!order.loading() && productLots.values().stream().filter(l -> l.fleetId().equals(order.fleetId()) && l.productId().equals(order.productId()))
                .mapToLong(Stage20FreightPersistentState.ProductCargoLotState::count).sum() < order.count()) return false;
        if (!permit.publish()) return false;
        updateProductProvenance(order.fleetId(), order.loading() ? permit.source() : permit.destination(), order.productId(),
                order.count(), order.loading(), seconds);
        refreshFreighterHold(order.fleetId()); return true;
    }

    private void updateProductProvenance(FleetId fleetId, Stage18StationStorage endpoint, String productId,
            int count, boolean loading, double seconds) {
        if (loading) {
            String id = "freight-lot:" + nextCargoLotOrdinal++;
            productLots.put(id, new Stage20FreightPersistentState.ProductCargoLotState(id, fleetId, productId,
                    count, endpoint.stationId(), seconds));
        } else {
            int remaining = count;
            for (var lot : List.copyOf(productLots.values())) {
                if (remaining == 0) break;
                if (!lot.fleetId().equals(fleetId) || !lot.productId().equals(productId)) continue;
                int removed = Math.min(remaining, lot.count()); remaining -= removed;
                if (removed == lot.count()) productLots.remove(lot.lotId());
                else productLots.put(lot.lotId(), new Stage20FreightPersistentState.ProductCargoLotState(lot.lotId(),
                        lot.fleetId(), lot.productId(), lot.count() - removed, lot.sourceEndpointId(), lot.loadedAtSimulationSeconds()));
            }
        }
    }

    /**
     * Loads an installed consumable interface from personally purchased cargo in the same hold.
     * The caller validates ownership, current physical docking and engineering identity.
     * The ordinary consumable service consumes the hold; matching lots retain exact provenance.
     * @param fleetId existing idle fleet
     * @param bindings existing physical consumable catalog
     * @param engineering existing installed engineering catalog
     * @param bindingId authored commodity/interface binding
     * @param mountId installed receiving mount
     * @param massKg requested finite mass
     * @param fit current installed fit
     * @param current current engineering consumables
     * @return physical load result, without any new material source
     */
    public com.spacesim.economy.Stage18ShipConsumableService.LoadResult loadManualConsumable(
            FleetId fleetId, com.spacesim.content.Stage18ShipConsumableCatalog bindings,
            com.spacesim.content.ship.ShipEngineeringCatalog engineering,
            String bindingId, String mountId, double massKg,
            com.spacesim.ship.ShipEngineeringState.InstalledFit fit,
            com.spacesim.ship.ShipEngineeringState.ConsumableState current) {
        requirePhase(requireFreighter(fleetId), FreightPhase.IDLE);
        var binding = Objects.requireNonNull(bindings).findBinding(bindingId);
        if (binding == null) throw new IllegalArgumentException("Unknown consumable binding");
        String commodityId = binding.commodityId();
        String order = Stage20FreightPersistentState.manualCargoOrderId(fleetId);
        if (!Double.isFinite(massKg) || massKg <= 0 || lots.values().stream()
                .filter(l -> l.fleetId().equals(fleetId) && l.orderId().equals(order) && l.commodityId().equals(commodityId))
                .mapToDouble(CargoLotState::massKg).sum() + EPSILON < massKg)
            throw new IllegalStateException("Personal cargo provenance or amount is unavailable");
        var result = new com.spacesim.economy.Stage18ShipConsumableService(bindings, engineering)
                .load(bindingId, mountId, massKg, fit, current, requireHold(fleetId));
        if (result.committed()) {
            consumeLots(fleetId, order, commodityId, result.loadedMassKg());
            refreshFreighterHold(fleetId);
        }
        return result;
    }

    /**
     * Finds one immutable current order state.
     *
     * @param orderId stable transport order identity
     * @return current order or empty when unknown
     */
    public Optional<TransportOrderState> findOrder(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    /** @return exact current lots, deterministically ordered by lot identity */
    public List<CargoLotState> cargoLots() {
        return List.copyOf(lots.values());
    }

    /**
     * Loads physical commodity mass from an ordinary Stage-18 source storage and creates provenance
     * only after the atomic transfer succeeds.
     *
     * @param fleetId stable carrying fleet identity
     * @param source ordinary source storage
     * @param massKg requested positive physical mass
     * @param sourceProvenanceId exact accepted source provenance
     * @param simulationSeconds authoritative loading time
     * @param handling compatible endpoint handling authority
     * @param budget finite current transfer budget
     * @return committed or rejected physical cargo operation
     */
    public CargoOperationResult loadCommodity(
            FleetId fleetId,
            Stage18StationStorage source,
            double massKg,
            String sourceProvenanceId,
            double simulationSeconds,
            HandlingCapability handling,
            TransferBudget budget) {
        FreighterState fleet = requireFreighter(fleetId);
        TransportOrderState order = requireOrder(fleet);
        requirePhase(fleet, FreightPhase.AT_SOURCE);
        Stage18StationStorage sourceStorage = Objects.requireNonNull(source, "source");
        String provenance = requireText(sourceProvenanceId, "sourceProvenanceId");
        requireNonNegativeFinite(simulationSeconds, "simulationSeconds");
        if (!sourceStorage.stationId().equals(order.sourceEndpointId())) {
            throw new IllegalArgumentException("loading storage differs from order source endpoint");
        }
        if (!order.sourceProvenanceId().equals(provenance)) {
            throw new IllegalArgumentException("loading provenance differs from order authority");
        }
        if (!Double.isFinite(massKg) || massKg <= 0d
                || fleet.cargoMassKg() + massKg > fleet.cargoCapacityKg() + EPSILON) {
            return CargoOperationResult.rejected(Status.DESTINATION_FULL);
        }
        Stage18LogisticsRuntime.TransferResult transfer = logistics.transferCommodity(
                sourceStorage,
                requireHold(fleetId),
                order.commodityId(),
                massKg,
                handling,
                budget);
        if (!transfer.transferred()) {
            return CargoOperationResult.rejected(transfer.status());
        }
        String lotId = "freight-lot:" + nextCargoLotOrdinal++;
        CargoLotState lot = new CargoLotState(
                lotId,
                fleetId,
                order.orderId(),
                order.commodityId(),
                massKg,
                order.sourceEndpointId(),
                provenance,
                simulationSeconds);
        lots.put(lotId, lot);
        refreshFreighterHold(fleetId);
        return new CargoOperationResult(Status.TRANSFERRED, lotId, massKg);
    }

    /**
     * Starts the persisted loaded producer-to-consumer route.
     *
     * <p>The order's delivery deadline is an already-authoritative service commitment. Dispatching
     * late must not slide that commitment to {@code dispatchTime + physicalTravelTime}; otherwise
     * source-side delay can never become an observable shortage. A fresh deadline is created only
     * after the empty freighter completes its return to source for the next recurring cycle.</p>
     *
     * @param fleetId stable carrying fleet identity
     * @param simulationSeconds authoritative dispatch time
     */
    public void dispatchOutbound(FleetId fleetId, double simulationSeconds) {
        FreighterState fleet = requireFreighter(fleetId);
        requireOrder(fleet);
        requirePhase(fleet, FreightPhase.AT_SOURCE);
        requireNonNegativeFinite(simulationSeconds, "simulationSeconds");
        if (fleet.cargoMassKg() <= EPSILON) {
            throw new IllegalStateException("outbound freight dispatch requires physical cargo");
        }
        if (fleet.routeIndex() != 0) {
            throw new IllegalStateException("outbound dispatch must begin at route origin");
        }
        freighters.put(fleetId, copyFreighter(
                fleet, fleet.currentSystemId(), fleet.physicalState(), FreightPhase.OUTBOUND,
                fleet.routeIndex(), requireHold(fleetId).snapshot()));
    }

    /**
     * Mirrors a completed ordinary world hop for an unassigned physical freighter.
     * This changes neither cargo, orders nor travel time; the caller supplies committed arrival.
     * @param fleetId existing idle identity
     * @param systemId committed world location
     * @param physical exact arrived kinematics
     * @return the same freight identity and hold at its committed location
     */
    public FreighterState synchronizeIdleArrival(FleetId fleetId, StarSystemId systemId,
            LocalPhysicalKinematics physical) {
        var fleet = requireFreighter(fleetId);
        requirePhase(fleet, FreightPhase.IDLE);
        var updated = copyFreighter(fleet, Objects.requireNonNull(systemId), Objects.requireNonNull(physical),
                FreightPhase.IDLE, fleet.routeIndex(), requireHold(fleetId).snapshot());
        freighters.put(fleetId, updated);
        return updated;
    }

    /**
     * Mirrors an explicit ordinary world affiliation for a personally controlled idle hull.
     * Bootstrap pool origin, allocator slot, physical placement and cargo remain exact.
     * Capture/restore verifies this mirror against the canonical world faction.
     * @param fleetId existing idle hull
     * @param legalFactionId actually committed ordinary world affiliation
     * @return updated redundant freight mirror
     */
    public FreighterState synchronizeLegalAffiliation(FleetId fleetId, String legalFactionId) {
        var f = requireFreighter(fleetId); requirePhase(f, FreightPhase.IDLE);
        var updated = new FreighterState(f.fleetId(), f.stableFactionId(), f.ownershipOrdinal(), f.hullId(),
                f.fitId(), f.cargoCapacityKg(), f.currentSystemId(), f.physicalState(), f.phase(),
                f.activeOrderId(), f.routeIndex(), requireHold(fleetId).snapshot(), legalFactionId, f.carriedEquipmentMassKg());
        freighters.put(fleetId, updated); return updated;
    }

    /**
     * Completes exactly the next persisted outbound neighbor hop using caller-supplied physical
     * arrival kinematics. Stage-20.5D supplies that edge-authoritative state.
     *
     * @param fleetId stable carrying fleet identity
     * @param nextSystemId exact next persisted neighbor
     * @param arrivalState exact destination-local physical state
     * @return updated immutable fleet state
     */
    public FreighterState completeNextOutboundHop(
            FleetId fleetId,
            StarSystemId nextSystemId,
            LocalPhysicalKinematics arrivalState) {
        FreighterState fleet = requireFreighter(fleetId);
        TransportOrderState order = requireOrder(fleet);
        requirePhase(fleet, FreightPhase.OUTBOUND);
        int nextIndex = fleet.routeIndex() + 1;
        if (nextIndex >= order.orderedSystems().size()
                || !order.orderedSystems().get(nextIndex).equals(nextSystemId)) {
            throw new IllegalArgumentException("outbound progress must follow the next exact route hop");
        }
        FreightPhase phase = nextIndex == order.orderedSystems().size() - 1
                ? FreightPhase.AT_DESTINATION : FreightPhase.OUTBOUND;
        FreighterState updated = copyFreighter(
                fleet,
                nextSystemId,
                Objects.requireNonNull(arrivalState, "arrivalState"),
                phase,
                nextIndex,
                requireHold(fleetId).snapshot());
        freighters.put(fleetId, updated);
        return updated;
    }

    /**
     * Unloads physical cargo into the exact ordinary Stage-18 destination storage.
     *
     * @param fleetId stable carrying fleet identity
     * @param destination ordinary destination storage
     * @param massKg requested positive physical mass
     * @param handling compatible endpoint handling authority
     * @param budget finite current transfer budget
     * @return committed or rejected physical cargo operation
     */
    public CargoOperationResult unloadCommodity(
            FleetId fleetId,
            Stage18StationStorage destination,
            double massKg,
            HandlingCapability handling,
            TransferBudget budget) {
        FreighterState fleet = requireFreighter(fleetId);
        TransportOrderState order = requireOrder(fleet);
        requirePhase(fleet, FreightPhase.AT_DESTINATION);
        Stage18StationStorage destinationStorage = Objects.requireNonNull(destination, "destination");
        if (!destinationStorage.stationId().equals(order.destinationEndpointId())) {
            throw new IllegalArgumentException("unloading storage differs from order destination endpoint");
        }
        Stage18LogisticsRuntime.TransferResult transfer = logistics.transferCommodity(
                requireHold(fleetId),
                destinationStorage,
                order.commodityId(),
                massKg,
                handling,
                budget);
        if (!transfer.transferred()) {
            return CargoOperationResult.rejected(transfer.status());
        }
        consumeLots(fleetId, order.orderId(), order.commodityId(), massKg);
        orders.put(order.orderId(), copyOrder(
                order,
                order.deliveryDeadlineSeconds(),
                order.deliveredMassKg() + massKg,
                order.delayedDeliveryCount()));
        refreshFreighterHold(fleetId);
        return new CargoOperationResult(Status.TRANSFERRED, "", massKg);
    }

    /**
     * Starts the empty consumer-to-producer return route.
     *
     * @param fleetId stable returning fleet identity
     */
    public void dispatchReturn(FleetId fleetId) {
        FreighterState fleet = requireFreighter(fleetId);
        TransportOrderState order = requireOrder(fleet);
        requirePhase(fleet, FreightPhase.AT_DESTINATION);
        if (fleet.cargoMassKg() > EPSILON) {
            throw new IllegalStateException("return dispatch requires an empty physical hold");
        }
        freighters.put(fleetId, copyFreighter(
                fleet,
                fleet.currentSystemId(),
                fleet.physicalState(),
                FreightPhase.RETURNING,
                order.orderedSystems().size() - 1,
                requireHold(fleetId).snapshot()));
    }

    /**
     * Completes exactly the next reverse neighbor hop; origin completion reopens loading.
     *
     * @param fleetId stable returning fleet identity
     * @param nextSystemId exact next persisted reverse-route neighbor
     * @param arrivalState exact destination-local physical state
     * @param simulationSeconds authoritative arrival time
     * @return updated immutable fleet state
     */
    public FreighterState completeNextReturnHop(
            FleetId fleetId,
            StarSystemId nextSystemId,
            LocalPhysicalKinematics arrivalState,
            double simulationSeconds) {
        FreighterState fleet = requireFreighter(fleetId);
        TransportOrderState order = requireOrder(fleet);
        requirePhase(fleet, FreightPhase.RETURNING);
        requireNonNegativeFinite(simulationSeconds, "simulationSeconds");
        int nextIndex = fleet.routeIndex() - 1;
        if (nextIndex < 0 || !order.orderedSystems().get(nextIndex).equals(nextSystemId)) {
            throw new IllegalArgumentException("return progress must follow the next exact reverse route hop");
        }
        FreightPhase phase = nextIndex == 0 ? FreightPhase.AT_SOURCE : FreightPhase.RETURNING;
        FreighterState updated = copyFreighter(
                fleet,
                nextSystemId,
                Objects.requireNonNull(arrivalState, "arrivalState"),
                phase,
                nextIndex,
                requireHold(fleetId).snapshot());
        freighters.put(fleetId, updated);
        if (phase == FreightPhase.AT_SOURCE) {
            orders.put(order.orderId(), copyOrder(
                    order,
                    simulationSeconds + order.oneWayDeliverySeconds(),
                    order.deliveredMassKg(),
                    order.delayedDeliveryCount()));
        }
        return updated;
    }

    /**
     * Replaces only the not-yet-traversed part of an active freight route from the fleet's exact
     * current system.
     *
     * <p>The operation preserves source/destination endpoint identity, cargo, provenance, delivered
     * mass and deadlines. OUTBOUND keeps the already traversed producer-side prefix. RETURNING
     * rewrites the producer-side prefix so the supplied current-to-source route becomes its exact
     * reverse while retaining the untouched consumer-side tail. The caller must provide ordinary
     * neighbor-only physical routing; this state layer owns no topology.</p>
     *
     * @param fleetId active physical freight fleet
     * @param routeFromCurrent ordered route beginning at the fleet's current system and ending at
     *                         the active directional endpoint
     * @return replaced persistent transport order
     */
    public TransportOrderState rerouteRemaining(
            FleetId fleetId,
            List<StarSystemId> routeFromCurrent) {
        FreighterState fleet = requireFreighter(fleetId);
        TransportOrderState order = requireOrder(fleet);
        List<StarSystemId> replacement = List.copyOf(Objects.requireNonNull(
                routeFromCurrent, "routeFromCurrent"));
        if (replacement.size() < 2
                || !replacement.get(0).equals(fleet.currentSystemId())) {
            throw new IllegalArgumentException(
                    "replacement freight route must begin at current system and contain a next hop");
        }

        ArrayList<StarSystemId> route = new ArrayList<>();
        int nextRouteIndex;
        if (fleet.phase() == FreightPhase.OUTBOUND) {
            StarSystemId destination = order.orderedSystems().get(order.orderedSystems().size() - 1);
            if (!replacement.get(replacement.size() - 1).equals(destination)) {
                throw new IllegalArgumentException(
                        "outbound replacement route must preserve order destination");
            }
            route.addAll(order.orderedSystems().subList(0, fleet.routeIndex()));
            route.addAll(replacement);
            nextRouteIndex = fleet.routeIndex();
        } else if (fleet.phase() == FreightPhase.RETURNING) {
            StarSystemId source = order.orderedSystems().get(0);
            if (!replacement.get(replacement.size() - 1).equals(source)) {
                throw new IllegalArgumentException(
                        "return replacement route must preserve order source");
            }
            ArrayList<StarSystemId> sourceToCurrent = new ArrayList<>(replacement);
            java.util.Collections.reverse(sourceToCurrent);
            route.addAll(sourceToCurrent);
            route.addAll(order.orderedSystems().subList(
                    fleet.routeIndex() + 1, order.orderedSystems().size()));
            nextRouteIndex = sourceToCurrent.size() - 1;
        } else {
            throw new IllegalStateException(
                    "freight reroute requires OUTBOUND or RETURNING phase");
        }

        TransportOrderState updatedOrder = new TransportOrderState(
                order.orderId(),
                order.fleetId(),
                order.stableFactionId(),
                order.assignmentKind(),
                order.commodityId(),
                order.sourceEndpointId(),
                order.destinationEndpointId(),
                order.sourceProvenanceId(),
                List.copyOf(route),
                order.oneWayDeliverySeconds(),
                order.roundTripCycleSeconds(),
                order.deliveryDeadlineSeconds(),
                order.deliveredMassKg(),
                order.delayedDeliveryCount());
        FreighterState updatedFleet = copyFreighter(
                fleet,
                fleet.currentSystemId(),
                fleet.physicalState(),
                fleet.phase(),
                nextRouteIndex,
                requireHold(fleetId).snapshot());
        orders.put(updatedOrder.orderId(), updatedOrder);
        freighters.put(fleetId, updatedFleet);
        return updatedOrder;
    }

    /**
     * Records every newly crossed physical delivery deadline exactly once.
     *
     * @param fleetId stable active fleet identity
     * @param simulationSeconds authoritative observation time
     * @return cumulative missed-delivery count
     */
    public long observeDeliveryDelay(FleetId fleetId, double simulationSeconds) {
        FreighterState fleet = requireFreighter(fleetId);
        TransportOrderState order = requireOrder(fleet);
        requireNonNegativeFinite(simulationSeconds, "simulationSeconds");
        if (fleet.phase() == FreightPhase.AT_SOURCE
                || fleet.phase() == FreightPhase.IDLE
                || fleet.phase() == FreightPhase.DESTROYED
                || simulationSeconds <= order.deliveryDeadlineSeconds()) {
            return order.delayedDeliveryCount();
        }
        long misses = (long) Math.floor(
                (simulationSeconds - order.deliveryDeadlineSeconds()) / order.roundTripCycleSeconds()) + 1L;
        double deadline = order.deliveryDeadlineSeconds() + misses * order.roundTripCycleSeconds();
        long delayed = Math.addExact(order.delayedDeliveryCount(), misses);
        orders.put(order.orderId(), copyOrder(order, deadline, order.deliveredMassKg(), delayed));
        return delayed;
    }

    /**
     * Permanently destroys one physical freight asset and its aboard mass. No ID, order or reserve
     * slot is created as a replacement.
     *
     * @param fleetId stable physical fleet identity
     * @return exact lost cargo and provenance result
     */
    public DestructionResult destroy(FleetId fleetId) {
        FreighterState fleet = requireFreighter(fleetId);
        if (fleet.phase() == FreightPhase.DESTROYED) {
            return new DestructionResult(fleetId, 0d, List.of(), false);
        }
        mining.remove(fleetId);
        Stage18StationStorage hold = requireHold(fleetId);
        double lostMass = fleet.cargoMassKg();
        List<CargoLotState> lostLots = lots.values().stream()
                .filter(value -> value.fleetId().equals(fleetId))
                .sorted(Comparator.comparing(CargoLotState::lotId))
                .toList();
        lostLots.forEach(value -> lots.remove(value.lotId()));
        productLots.values().removeIf(value -> value.fleetId().equals(fleetId));
        holds.put(fleetId, new Stage18StationStorage(
                ontology,
                products,
                hold.stationId(),
                hold.snapshotCapacityByStorageClassKg(),
                Map.of(),
                Map.of()));
        freighters.put(fleetId, copyFreighter(
                fleet,
                fleet.currentSystemId(),
                fleet.physicalState(),
                FreightPhase.DESTROYED,
                fleet.routeIndex(),
                holds.get(fleetId).snapshot()));
        return new DestructionResult(fleetId, lostMass, lostLots, true);
    }

    /** Result of one physical cargo operation. */
    public record CargoOperationResult(Status status, String lotId, double transferredMassKg) {
        /**
         * Validates one physical cargo-operation result.
         *
         * @param status exact Stage-18 transfer status
         * @param lotId created lot identity or empty when none
         * @param transferredMassKg physical mass committed by the operation
         */
        public CargoOperationResult {
            Objects.requireNonNull(status, "status");
            lotId = lotId == null ? "" : lotId;
            if (!Double.isFinite(transferredMassKg) || transferredMassKg < 0d) {
                throw new IllegalArgumentException("transferredMassKg must be non-negative and finite");
            }
            if ((status == Status.TRANSFERRED) != (transferredMassKg > 0d)) {
                throw new IllegalArgumentException("cargo operation status and mass differ");
            }
        }

        static CargoOperationResult rejected(Status status) {
            return new CargoOperationResult(status, "", 0d);
        }

        /** @return whether physical storage transfer committed */
        public boolean transferred() {
            return status == Status.TRANSFERRED;
        }
    }

    /** Physical destruction result retaining exact lost lot provenance. */
    public record DestructionResult(
            FleetId fleetId,
            double lostCargoMassKg,
            List<CargoLotState> lostLots,
            boolean destroyedNow) {
        /**
         * Validates one physical freight-destruction result.
         *
         * @param fleetId stable destroyed fleet identity
         * @param lostCargoMassKg physical mass removed with the asset
         * @param lostLots exact removed provenance lots
         * @param destroyedNow whether this call performed the destruction
         */
        public DestructionResult {
            Objects.requireNonNull(fleetId, "fleetId");
            if (!Double.isFinite(lostCargoMassKg) || lostCargoMassKg < 0d) {
                throw new IllegalArgumentException("lostCargoMassKg must be non-negative and finite");
            }
            lostLots = List.copyOf(Objects.requireNonNull(lostLots, "lostLots"));
        }
    }

    private void consumeLots(FleetId fleetId, String orderId, String commodityId, double massKg) {
        consumeMatchingLots(fleetId, orderId, commodityId, massKg, false);
    }

    private void consumePersonalLots(FleetId fleetId, String commodityId, double massKg) {
        consumeMatchingLots(fleetId, Stage20FreightPersistentState.manualCargoOrderId(fleetId), commodityId, massKg, true);
    }

    private void consumeMatchingLots(FleetId fleetId, String orderId, String commodityId, double massKg, boolean personal) {
        double remaining = massKg;
        List<CargoLotState> matching = matchingCargoLots(fleetId, orderId, commodityId, personal);
        for (CargoLotState lot : matching) {
            if (remaining <= EPSILON) {
                break;
            }
            double consumed = Math.min(remaining, lot.massKg());
            double retained = lot.massKg() - consumed;
            if (retained <= EPSILON) {
                lots.remove(lot.lotId());
            } else {
                lots.put(lot.lotId(), new CargoLotState(
                        lot.lotId(), lot.fleetId(), lot.orderId(), lot.commodityId(), retained,
                        lot.sourceEndpointId(), lot.sourceProvenanceId(),
                        lot.loadedAtSimulationSeconds()));
            }
            remaining -= consumed;
        }
        if (remaining > EPSILON) {
            throw new IllegalStateException("physical hold mass exceeded cargo-lot provenance");
        }
    }

    private List<CargoLotState> matchingCargoLots(FleetId fleetId, String orderId, String commodityId, boolean personal) {
        return lots.values().stream().filter(value -> value.fleetId().equals(fleetId)
                && (value.orderId().equals(orderId) || personal && value.orderId().equals(Stage20FreightPersistentState.personalExtractionOrderId(fleetId)))
                && value.commodityId().equals(commodityId))
                .sorted(Comparator.comparingDouble(CargoLotState::loadedAtSimulationSeconds).thenComparing(CargoLotState::lotId)).toList();
    }

    private void refreshFreighterHold(FleetId fleetId) {
        FreighterState fleet = requireFreighter(fleetId);
        freighters.put(fleetId, copyFreighter(fleet, requireHold(fleetId).snapshot()));
    }

    private FreighterState requireFreighter(FleetId fleetId) {
        FreighterState result = freighters.get(Objects.requireNonNull(fleetId, "fleetId"));
        if (result == null) {
            throw new IllegalArgumentException("unknown physical freight fleet: " + fleetId);
        }
        return copyFreighter(result, requireHold(result.fleetId()).snapshot());
    }

    private Stage18StationStorage requireHold(FleetId fleetId) {
        Stage18StationStorage result = holds.get(Objects.requireNonNull(fleetId, "fleetId"));
        if (result == null) {
            throw new IllegalArgumentException("unknown freight hold: " + fleetId);
        }
        return result;
    }

    private TransportOrderState requireOrder(FreighterState fleet) {
        if (fleet.activeOrderId().isEmpty()) {
            throw new IllegalStateException("freighter has no active transport order");
        }
        TransportOrderState result = orders.get(fleet.activeOrderId());
        if (result == null || !result.fleetId().equals(fleet.fleetId())) {
            throw new IllegalStateException("freighter order identity is inconsistent");
        }
        return result;
    }

    private static void requirePhase(FreighterState fleet, FreightPhase required) {
        if (fleet.phase() != required) {
            throw new IllegalStateException("freighter phase must be " + required + ", was " + fleet.phase());
        }
    }

    private static FreighterState copyFreighter(
            FreighterState source,
            StationStorageSnapshot storage) {
        return copyFreighter(
                source,
                source.currentSystemId(),
                source.physicalState(),
                source.phase(),
                source.routeIndex(),
                storage);
    }

    private static FreighterState copyFreighter(
            FreighterState source,
            StarSystemId systemId,
            LocalPhysicalKinematics physical,
            FreightPhase phase,
            int routeIndex,
            StationStorageSnapshot storage) {
        return new FreighterState(
                source.fleetId(),
                source.stableFactionId(),
                source.ownershipOrdinal(),
                source.hullId(),
                source.fitId(),
                source.cargoCapacityKg(),
                systemId,
                physical,
                phase,
                source.activeOrderId(),
                routeIndex,
                storage,
                source.legalFactionId(), phase == FreightPhase.DESTROYED ? 0 : source.carriedEquipmentMassKg());
    }

    private static TransportOrderState copyOrder(
            TransportOrderState source,
            double deadline,
            double deliveredMass,
            long delayedCount) {
        return new TransportOrderState(
                source.orderId(),
                source.fleetId(),
                source.stableFactionId(),
                source.assignmentKind(),
                source.commodityId(),
                source.sourceEndpointId(),
                source.destinationEndpointId(),
                source.sourceProvenanceId(),
                source.orderedSystems(),
                source.oneWayDeliverySeconds(),
                source.roundTripCycleSeconds(),
                deadline,
                deliveredMass,
                delayedCount);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.strip();
    }

    private static void requireNonNegativeFinite(double value, String field) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(field + " must be non-negative and finite");
        }
    }
}
