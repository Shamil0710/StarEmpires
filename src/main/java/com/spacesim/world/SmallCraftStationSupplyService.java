package com.spacesim.world;

import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ManufacturingProductRegistry.ProductKind;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog;
import com.spacesim.content.weapon.WeaponLauncherCatalog;
import com.spacesim.economy.Stage18ShipConsumableService;
import com.spacesim.economy.Stage18StationIndustrialNode;
import com.spacesim.economy.Stage19WarfareSupplyService;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipInstanceRuntimeState;
import com.spacesim.ship.WeaponDefinition.Family;
import com.spacesim.ship.WeaponDefinition.Launcher;
import com.spacesim.ship.WeaponLoadoutState;
import com.spacesim.ship.WeaponLoadoutState.FeedBinding;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.HostKind;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import com.spacesim.world.SmallCraftHangarCapacity.Usage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * M22.8G station-local physical refuel and rearm authority for persistent small craft.
 *
 * <p>Canonical Stage-18 station stock is the only source of loaded reaction mass and finished
 * ammunition. The service requires the craft to be physically present in the exact station bay,
 * preflights hull/bay mass before stock mutation, delegates actual stock removal to the accepted
 * Stage-18/19 supply services, then commits the resulting consumables back to the same
 * {@link SmallCraftId}. No supply currency, virtual magazine or docking refill is introduced.</p>
 */
public final class SmallCraftStationSupplyService {
    private static final double EPSILON = 1e-9d;

    private final SmallCraftRegistry craftRegistry;
    private final SmallCraftHangarRegistry hangars;
    private final ShipEngineeringCatalog engineering;
    private final Stage18ShipConsumableService consumableService;
    private final Stage19WarfareSupplyService ammunitionService;
    private final Stage18ManufacturingProductRegistry products;
    private final WeaponLauncherCatalog launchers;
    private final WeaponAmmunitionCatalog ammunition;

    /**
     * Creates the station-local M22.8G physical supply boundary.
     *
     * @param craftRegistry authoritative persistent individual-craft registry
     * @param hangars authoritative physical bay occupancy registry
     * @param engineering accepted Stage-17.5 engineering catalog
     * @param consumableService accepted Stage-18 commodity-to-interface loader
     * @param products accepted Stage-18 manufactured-product registry
     * @param launchers accepted Stage-19 launcher catalog
     * @param ammunition accepted Stage-19 physical ammunition catalog
     */
    public SmallCraftStationSupplyService(
            SmallCraftRegistry craftRegistry,
            SmallCraftHangarRegistry hangars,
            ShipEngineeringCatalog engineering,
            Stage18ShipConsumableService consumableService,
            Stage18ManufacturingProductRegistry products,
            WeaponLauncherCatalog launchers,
            WeaponAmmunitionCatalog ammunition) {
        this.craftRegistry = Objects.requireNonNull(craftRegistry, "craftRegistry");
        this.hangars = Objects.requireNonNull(hangars, "hangars");
        this.engineering = Objects.requireNonNull(engineering, "engineering");
        this.consumableService = Objects.requireNonNull(consumableService, "consumableService");
        this.products = Objects.requireNonNull(products, "products");
        this.ammunitionService = new Stage19WarfareSupplyService(products);
        this.launchers = Objects.requireNonNull(launchers, "launchers");
        this.ammunition = Objects.requireNonNull(ammunition, "ammunition");
        if (!craftRegistry.engineeringCatalogFingerprint().equals(engineering.getFingerprint())) {
            throw new IllegalArgumentException(
                    "small-craft registry and station supply engineering catalog must match");
        }
    }

    /**
     * Loads physical reaction mass or another authored Stage-18 bound commodity.
     *
     * @param craftId persistent craft receiving the commodity
     * @param station canonical Stage-18 station whose storage supplies the commodity
     * @param stationBay exact station bay currently occupied by the craft
     * @param bindingId authored Stage-18 commodity/interface binding
     * @param mountId installed module mount receiving the commodity
     * @param requestedMassKg positive physical mass requested
     * @return immutable load result; rejected results leave craft and station stock unchanged
     */
    public CommodityLoadResult loadCommodityAtStation(
            SmallCraftId craftId,
            Stage18StationIndustrialNode station,
            BayDefinition stationBay,
            String bindingId,
            String mountId,
            double requestedMassKg) {
        SmallCraftState current = requireStationServiceState(craftId, station, stationBay);
        if (!Double.isFinite(requestedMassKg) || requestedMassKg <= 0d) {
            return CommodityLoadResult.preflightRejected(SupplyStatus.INVALID_REQUEST, current.id());
        }
        SupplyStatus massStatus = preflightAddedMass(current, stationBay, requestedMassKg);
        if (massStatus != SupplyStatus.LOADED) {
            return CommodityLoadResult.preflightRejected(massStatus, current.id());
        }

        Stage18ShipConsumableService.LoadResult physical = consumableService.load(
                bindingId,
                mountId,
                requestedMassKg,
                current.fit(),
                current.runtimeState().consumables(),
                station.storage());
        if (!physical.committed()) {
            return new CommodityLoadResult(
                    SupplyStatus.PHYSICAL_STOCK_OR_INTERFACE_REJECTED,
                    current.id(),
                    physical,
                    0d);
        }

        SmallCraftState candidate = withConsumables(
                current,
                physical.consumables(),
                current.instanceState());
        // The underlying loader already validated interface capacity; this central fitting authority
        // therefore only has total-mass/ordinary fit checks left and must succeed after preflight.
        craftRegistry.candidatePhysicalFootprint(candidate);
        craftRegistry.replacePhysicalState(candidate);
        return new CommodityLoadResult(
                SupplyStatus.LOADED,
                current.id(),
                physical,
                physical.loadedMassKg());
    }

    /**
     * Loads manufactured ammunition and binds the physical feed to that exact ammunition content ID.
     *
     * @param craftId persistent craft receiving ammunition
     * @param station canonical Stage-18 station whose storage supplies the rounds
     * @param stationBay exact station bay currently occupied by the craft
     * @param productId manufactured ammunition content/product identity
     * @param mountId installed weapon mount receiving the ammunition
     * @param requestedRounds positive physical round count requested
     * @return immutable load result; rejected results leave craft and station stock unchanged
     */
    public AmmunitionLoadResult loadAmmunitionAtStation(
            SmallCraftId craftId,
            Stage18StationIndustrialNode station,
            BayDefinition stationBay,
            String productId,
            String mountId,
            int requestedRounds) {
        SmallCraftState current = requireStationServiceState(craftId, station, stationBay);
        if (productId == null || productId.isBlank()
                || mountId == null || mountId.isBlank()
                || requestedRounds <= 0) {
            return AmmunitionLoadResult.preflightRejected(
                    SupplyStatus.INVALID_REQUEST, current.id(), productId);
        }

        var installed = current.fit().installedModules().stream()
                .filter(value -> value.mountId().equals(mountId))
                .findFirst()
                .orElse(null);
        if (installed == null) {
            return AmmunitionLoadResult.preflightRejected(
                    SupplyStatus.AMMUNITION_INCOMPATIBLE, current.id(), productId);
        }
        ModuleDefinition module = engineering.findModule(installed.moduleId());
        var profile = launchers.findByModuleId(installed.moduleId());
        if (module == null || profile == null || profile.family() == Family.BEAM) {
            return AmmunitionLoadResult.preflightRejected(
                    SupplyStatus.AMMUNITION_INCOMPATIBLE, current.id(), productId);
        }
        InterfaceDefinition feed = module.interfaces().stream()
                .filter(value -> value.kind() == InterfaceKind.AMMUNITION)
                .filter(value -> value.id().equals(profile.ammunitionInterfaceId()))
                .findFirst()
                .orElse(null);
        if (feed == null) {
            return AmmunitionLoadResult.preflightRejected(
                    SupplyStatus.AMMUNITION_INCOMPATIBLE, current.id(), productId);
        }

        var product = products.findProduct(productId);
        if (product == null || product.kind() != ProductKind.AMMUNITION) {
            return AmmunitionLoadResult.preflightRejected(
                    SupplyStatus.AMMUNITION_INCOMPATIBLE, current.id(), productId);
        }
        if (!compatibleAmmunition(profile, module, productId, product.unitMassKg())) {
            return AmmunitionLoadResult.preflightRejected(
                    SupplyStatus.AMMUNITION_INCOMPATIBLE, current.id(), productId);
        }

        long existingRounds = current.runtimeState().consumables().interfaceLoads().stream()
                .filter(load -> load.kind() == InterfaceKind.AMMUNITION)
                .filter(load -> load.mountId().equals(mountId))
                .filter(load -> load.interfaceId().equals(feed.id()))
                .mapToLong(ConsumableLoad::itemCount)
                .sum();
        Optional<String> existingIdentity = current.instanceState().weaponLoadout()
                .ammunitionContentId(mountId, feed.id());
        if (existingRounds > 0L
                && (existingIdentity.isEmpty() || !existingIdentity.orElseThrow().equals(productId))) {
            return AmmunitionLoadResult.preflightRejected(
                    SupplyStatus.AMMUNITION_IDENTITY_CONFLICT, current.id(), productId);
        }

        double addedMassKg = product.unitMassKg() * requestedRounds;
        SupplyStatus massStatus = preflightAddedMass(current, stationBay, addedMassKg);
        if (massStatus != SupplyStatus.LOADED) {
            return AmmunitionLoadResult.preflightRejected(
                    massStatus, current.id(), productId);
        }

        Launcher launcher = new Launcher(
                "launcher." + installed.moduleId(),
                profile.ammunitionInterfaceId(),
                profile.ammunitionAmountPerShot(),
                profile.cycleTimeSeconds(),
                profile.supportChannelCount());
        Stage19WarfareSupplyService.AmmunitionLoadResult physical =
                ammunitionService.loadAmmunition(
                        productId,
                        mountId,
                        requestedRounds,
                        launcher,
                        feed,
                        current.runtimeState().consumables(),
                        station.storage());
        if (!physical.committed()) {
            return new AmmunitionLoadResult(
                    SupplyStatus.PHYSICAL_STOCK_OR_INTERFACE_REJECTED,
                    current.id(),
                    productId,
                    physical,
                    0,
                    0d);
        }
        if (Math.abs(physical.loadedMassKg() - addedMassKg)
                > Math.max(1d, addedMassKg) * 1e-9d) {
            throw new IllegalStateException(
                    "Stage-18 ammunition product mass disagrees with tactical ammunition content");
        }

        WeaponLoadoutState nextLoadout = bindFeed(
                current.instanceState().weaponLoadout(),
                mountId,
                feed.id(),
                productId);
        ShipInstanceRuntimeState nextInstance = new ShipInstanceRuntimeState(
                current.instanceState().damage(),
                current.instanceState().shieldStatesByMount(),
                current.instanceState().maintenance(),
                nextLoadout,
                current.instanceState().weaponMountRuntime());
        SmallCraftState candidate = withConsumables(
                current,
                physical.consumables(),
                nextInstance);
        craftRegistry.candidatePhysicalFootprint(candidate);
        craftRegistry.replacePhysicalState(candidate);
        return new AmmunitionLoadResult(
                SupplyStatus.LOADED,
                current.id(),
                productId,
                physical,
                physical.loadedRoundCount(),
                physical.loadedMassKg());
    }

    private SmallCraftState requireStationServiceState(
            SmallCraftId craftId,
            Stage18StationIndustrialNode station,
            BayDefinition bay) {
        SmallCraftState current = craftRegistry.find(Objects.requireNonNull(craftId, "craftId"))
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown small craft: " + craftId));
        Stage18StationIndustrialNode node = Objects.requireNonNull(station, "station");
        BayDefinition checkedBay = Objects.requireNonNull(bay, "bay");
        if (checkedBay.hostKind() != HostKind.STATION
                || !checkedBay.id().hostStableId().equals(node.stationId())) {
            throw new IllegalArgumentException(
                    "station servicing bay must be physically co-located with Stage-18 storage");
        }
        var assignment = hangars.find(craftId).orElseThrow(
                () -> new IllegalStateException(
                        "small craft must be physically embarked for station servicing"));
        if (!assignment.bayId().equals(checkedBay.id())
                || assignment.hostKind() != HostKind.STATION) {
            throw new IllegalStateException(
                    "small craft is not physically assigned to the supplied station bay");
        }
        if (assignment.state() != OccupancyState.PARKED
                && assignment.state() != OccupancyState.SERVICING) {
            throw new IllegalStateException(
                    "station supply requires PARKED or SERVICING occupancy");
        }
        return current;
    }

    private SupplyStatus preflightAddedMass(
            SmallCraftState current,
            BayDefinition bay,
            double addedMassKg) {
        if (!Double.isFinite(addedMassKg) || addedMassKg <= 0d) {
            return SupplyStatus.INVALID_REQUEST;
        }
        var hull = engineering.findHull(current.fit().hullId());
        if (hull == null) {
            throw new IllegalStateException("registered craft hull disappeared from engineering catalog");
        }
        var footprint = craftRegistry.physicalFootprint(current.id());
        double nextMassKg = footprint.currentMassKg() + addedMassKg;
        if (nextMassKg > hull.maxOperationalMassKg() + EPSILON) {
            return SupplyStatus.HULL_MASS_LIMIT;
        }

        Usage usage = hangars.usage(bay.id());
        Usage withoutCurrent = new Usage(
                Math.max(0, usage.craftCount() - 1),
                Math.max(0d, usage.occupiedMassKg() - footprint.currentMassKg()),
                Math.max(0d, usage.occupiedEnvelopeVolumeM3() - footprint.envelopeVolumeM3()));
        var loaded = new SmallCraftHangarCapacity.CraftFootprint(
                current.id(),
                footprint.envelopeM(),
                nextMassKg);
        return SmallCraftHangarCapacity.canAccept(bay, withoutCurrent, loaded)
                ? SupplyStatus.LOADED
                : SupplyStatus.BAY_MASS_LIMIT;
    }

    private boolean compatibleAmmunition(
            WeaponLauncherCatalog.LauncherProfile profile,
            ModuleDefinition module,
            String productId,
            double manufacturedUnitMassKg) {
        double bodyMassKg;
        double lengthM;
        double diameterM;
        if (profile.family() == Family.KINETIC) {
            var body = ammunition.findKinetic(productId);
            if (body == null) {
                return false;
            }
            bodyMassKg = body.massKg();
            lengthM = body.lengthM();
            diameterM = body.diameterM();
            Double authoredMass = module.capabilityParameters().get("projectile_mass_kg");
            if (authoredMass != null && !nearlyEqual(authoredMass, bodyMassKg)) {
                return false;
            }
        } else if (profile.family() == Family.GUIDED) {
            var body = ammunition.findGuided(productId);
            if (body == null) {
                return false;
            }
            bodyMassKg = body.wetMassKg();
            lengthM = body.lengthM();
            diameterM = body.diameterM();
        } else {
            return false;
        }
        return nearlyEqual(bodyMassKg, manufacturedUnitMassKg)
                && bodyMassKg <= profile.maxProjectileMassKg() + EPSILON
                && lengthM <= profile.maxProjectileLengthM() + EPSILON
                && diameterM <= profile.maxProjectileDiameterM() + EPSILON;
    }

    private static SmallCraftState withConsumables(
            SmallCraftState current,
            ConsumableState consumables,
            ShipInstanceRuntimeState instance) {
        RuntimeState state = current.runtimeState();
        RuntimeState updated = new RuntimeState(
                consumables,
                state.sharedBusEnergyJ(),
                state.shipHeatStoredJ(),
                state.localHeatJByMount(),
                state.thrustLimitNByMount(),
                state.coolantBusCapacityW(),
                state.ftlCooldownSecondsByMount());
        return new SmallCraftState(
                current.id(),
                current.stableFactionId(),
                current.designId(),
                current.fit(),
                updated,
                instance);
    }

    private static WeaponLoadoutState bindFeed(
            WeaponLoadoutState current,
            String mountId,
            String interfaceId,
            String productId) {
        List<FeedBinding> feeds = new ArrayList<>();
        for (FeedBinding feed : current.feeds()) {
            if (!feed.mountId().equals(mountId) || !feed.interfaceId().equals(interfaceId)) {
                feeds.add(feed);
            }
        }
        feeds.add(new FeedBinding(mountId, interfaceId, productId));
        return new WeaponLoadoutState(feeds);
    }

    private static boolean nearlyEqual(double first, double second) {
        double scale = Math.max(1d, Math.max(Math.abs(first), Math.abs(second)));
        return Math.abs(first - second) <= scale * 1e-9d;
    }

    /** Stable outcome of one station-local physical supply attempt. */
    public enum SupplyStatus {
        /** Physical station stock was consumed and committed to the same craft identity. */
        LOADED,
        /** Request arguments were invalid. */
        INVALID_REQUEST,
        /** Requested load would exceed the authored hull operational-mass limit. */
        HULL_MASS_LIMIT,
        /** Requested load would exceed the current physical bay mass capacity. */
        BAY_MASS_LIMIT,
        /** Requested ammunition does not match the installed launcher/feed envelope. */
        AMMUNITION_INCOMPATIBLE,
        /** Existing rounds in the feed carry a different ammunition content identity. */
        AMMUNITION_IDENTITY_CONFLICT,
        /** Canonical stock, binding or physical interface rejected the otherwise valid request. */
        PHYSICAL_STOCK_OR_INTERFACE_REJECTED
    }

    /**
     * Immutable result of one physical commodity load attempt.
     *
     * @param status stable supply outcome
     * @param craftId persistent craft identity
     * @param physicalResult underlying Stage-18 result when physical loading was attempted
     * @param loadedMassKg physical mass committed to the craft
     */
    public record CommodityLoadResult(
            SupplyStatus status,
            SmallCraftId craftId,
            Stage18ShipConsumableService.LoadResult physicalResult,
            double loadedMassKg) {
        /**
         * Validates one immutable commodity load result.
         *
         * @param status stable supply outcome
         * @param craftId persistent craft identity
         * @param physicalResult underlying Stage-18 load result when attempted
         * @param loadedMassKg physical mass committed to the craft
         */
        public CommodityLoadResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(craftId, "craftId");
            if (!Double.isFinite(loadedMassKg) || loadedMassKg < 0d) {
                throw new IllegalArgumentException("loadedMassKg must be finite and non-negative");
            }
            if ((status == SupplyStatus.LOADED) != (physicalResult != null && physicalResult.committed())) {
                if (status == SupplyStatus.LOADED || physicalResult != null && physicalResult.committed()) {
                    throw new IllegalArgumentException("commodity load status/result mismatch");
                }
            }
        }

        static CommodityLoadResult preflightRejected(
                SupplyStatus status,
                SmallCraftId craftId) {
            return new CommodityLoadResult(status, craftId, null, 0d);
        }
    }

    /**
     * Immutable result of one manufactured-ammunition load attempt.
     *
     * @param status stable supply outcome
     * @param craftId persistent craft identity
     * @param productId attempted physical ammunition product/content identity
     * @param physicalResult underlying Stage-19 warfare-supply result when attempted
     * @param loadedRounds physical round count committed to the craft
     * @param loadedMassKg physical ammunition mass committed to the craft
     */
    public record AmmunitionLoadResult(
            SupplyStatus status,
            SmallCraftId craftId,
            String productId,
            Stage19WarfareSupplyService.AmmunitionLoadResult physicalResult,
            int loadedRounds,
            double loadedMassKg) {
        /**
         * Validates one immutable ammunition load result.
         *
         * @param status stable supply outcome
         * @param craftId persistent craft identity
         * @param productId attempted ammunition content/product identity
         * @param physicalResult underlying Stage-19 warfare-supply result when attempted
         * @param loadedRounds physical round count committed to the craft
         * @param loadedMassKg physical ammunition mass committed to the craft
         */
        public AmmunitionLoadResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(craftId, "craftId");
            productId = productId == null ? "" : productId;
            if (loadedRounds < 0 || !Double.isFinite(loadedMassKg) || loadedMassKg < 0d) {
                throw new IllegalArgumentException("invalid ammunition load quantities");
            }
            if (status == SupplyStatus.LOADED
                    && (physicalResult == null || !physicalResult.committed())) {
                throw new IllegalArgumentException("loaded ammunition requires committed physical result");
            }
        }

        static AmmunitionLoadResult preflightRejected(
                SupplyStatus status,
                SmallCraftId craftId,
                String productId) {
            return new AmmunitionLoadResult(
                    status, craftId, productId, null, 0, 0d);
        }
    }
}
