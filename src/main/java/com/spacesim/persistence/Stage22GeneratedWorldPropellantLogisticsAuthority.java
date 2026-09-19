package com.spacesim.persistence;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.content.Stage18ShipConsumableCatalog;
import com.spacesim.content.Stage18ShipConsumableCatalog.ShipConsumableBinding;
import com.spacesim.content.Stage22ShipConsumableCatalogLoader;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.Stage21GeneratedMilitaryEngineeringCatalog;
import com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader;
import com.spacesim.economy.Stage18ShipConsumableService;
import com.spacesim.persistence.Stage20GeneratedIndustrialRuntimeBridge.MaterializedGeneratedIndustrialRuntime;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.InfrastructureRegistry;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.RuntimeEndpoint;
import com.spacesim.ship.ProductionEngineeringRuntimeResolver;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.FleetPropellantLogisticsAuthority;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldSimulation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Generated-world Stage-22 finite propellant planner and local servicing authority.
 *
 * <p>Planning is pure. It projects real station stock, authored commodity/interface bindings and the
 * existing geometric route delta-v authority without reserving or creating fuel. Execution is
 * receding-horizon: only the current-system refuel actions are committed, then the same remaining
 * route is replanned from authoritative state. Future stock can therefore disappear before arrival,
 * but a fleet is never allowed to start a hop that its current physical tank cannot finish safely.</p>
 */
public final class Stage22GeneratedWorldPropellantLogisticsAuthority
        implements FleetPropellantLogisticsAuthority {
    private static final double EPSILON = 1.0e-6d;
    private static final String LIQUID_STORAGE_CLASS_ID = "storage.liquid_tank";
    private static final double RESERVE_FRACTION = 0.10d;
    private static final int TOP_UP_SEARCH_STEPS = 64;

    private final WorldSimulation world;
    private final InfrastructureRegistry infrastructure;
    private final MaterializedGeneratedIndustrialRuntime industry;
    private final Stage18ShipConsumableCatalog bindings;
    private final List<ShipEngineeringCatalog> engineeringCatalogs;
    private final ProductionEngineeringRuntimeResolver engineering =
            new ProductionEngineeringRuntimeResolver();
    private final Map<String, String> generatedIndustrialOwnerByStation;

    /**
     * Binds generated infrastructure and physical ship engineering to one refueling authority.
     *
     * @param world ordinary multi-system world authority
     * @param infrastructure canonical generated station endpoints
     * @param industry generated industrial ownership/runtime registry
     */
    public Stage22GeneratedWorldPropellantLogisticsAuthority(
            WorldSimulation world,
            InfrastructureRegistry infrastructure,
            MaterializedGeneratedIndustrialRuntime industry) {
        this.world = Objects.requireNonNull(world, "world");
        this.infrastructure = Objects.requireNonNull(infrastructure, "infrastructure");
        this.industry = Objects.requireNonNull(industry, "industry");
        this.bindings = Stage22ShipConsumableCatalogLoader.loadDefault();
        this.engineeringCatalogs = List.of(
                Stage21GeneratedMilitaryEngineeringCatalog.load(),
                Stage22FreightStrategicEngineeringCatalogLoader.loadDefault());
        TreeMap<String, String> owners = new TreeMap<>();
        industry.industrial().stations().forEach(station ->
                owners.put(station.stationId(), station.stableFactionId()));
        this.generatedIndustrialOwnerByStation = Map.copyOf(owners);
    }

    @Override
    public JourneyPlan planJourney(FleetId fleetId, List<StarSystemId> orderedSystems) {
        FleetId id = Objects.requireNonNull(fleetId, "fleetId");
        List<StarSystemId> route = List.copyOf(Objects.requireNonNull(orderedSystems, "orderedSystems"));
        FleetPlacementState placement = world.findFleet(id).orElseThrow(
                () -> new IllegalArgumentException("unknown FleetId: " + id));
        if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
            throw new IllegalStateException("propellant journey planning requires local fleet placement");
        }
        if (route.isEmpty() || !route.get(0).equals(placement.systemId())) {
            throw new IllegalArgumentException("propellant journey must begin at the fleet's current system");
        }
        if (route.size() == 1) {
            EngineeringComponent fitted = localEngineering(placement);
            double remaining = fitted == null ? 0d : engineering.derive(fitted).reactionMassKg();
            return new JourneyPlan(true, true, route, List.of(), 0d, remaining, "");
        }

        EngineeringComponent fitted = localEngineering(placement);
        if (fitted == null) {
            var direct = world.planFleetRouteFuel(id, route);
            return JourneyPlan.compatibility(
                    route,
                    direct.feasible(),
                    direct.requiredDeltaVMps(),
                    direct.remainingReactionMassKg(),
                    direct.reason());
        }

        FuelPort port = fuelPort(fitted);
        var derived = engineering.derive(fitted);
        if (port == null) {
            var direct = world.planFleetRouteFuel(id, route);
            return new JourneyPlan(
                    true,
                    direct.feasible(),
                    route,
                    List.of(),
                    direct.requiredDeltaVMps(),
                    direct.remainingReactionMassKg(),
                    direct.feasible() ? "" : "fitted propulsion has no authored station-servicing binding");
        }
        double exhaustVelocityMps = derived.effectiveExhaustVelocityMps();
        if (!(exhaustVelocityMps > 0d) || !Double.isFinite(exhaustVelocityMps)
                || !(derived.availableThrustN() > 0d)) {
            return new JourneyPlan(
                    true, false, route, List.of(), 0d, derived.reactionMassKg(),
                    "fitted propulsion has no operational thrust/exhaust authority");
        }

        double currentFuelKg = derived.reactionMassKg();
        double nonReactionMassKg = Math.max(EPSILON, derived.totalMassKg() - currentFuelKg);
        double capacityKg = port.capacityKg();
        if (currentFuelKg > capacityKg + EPSILON) {
            throw new IllegalStateException("reaction mass exceeds authored serviceable tank capacity");
        }
        if (route.stream().distinct().count() != route.size()) {
            return new JourneyPlan(
                    true, false, route, List.of(), 0d, currentFuelKg,
                    "finite station projection requires a simple route without repeated systems");
        }

        String participantFaction = fleetFaction(placement);
        int segmentCount = route.size() - 1;
        double[] segmentDeltaVMps = new double[segmentCount];
        double previousCumulativeDeltaV = 0d;
        double totalDeltaV = 0d;
        for (int index = 1; index < route.size(); index++) {
            List<StarSystemId> prefix = route.subList(0, index + 1);
            var geometry = world.planFleetRouteFuel(id, prefix);
            if (!geometry.supported()) {
                return JourneyPlan.compatibility(
                        route,
                        geometry.feasible(),
                        geometry.requiredDeltaVMps(),
                        geometry.remainingReactionMassKg(),
                        geometry.reason());
            }
            double cumulativeDeltaV = geometry.requiredDeltaVMps();
            double segmentDeltaV = cumulativeDeltaV - previousCumulativeDeltaV;
            if (segmentDeltaV < -EPSILON) {
                throw new IllegalStateException("route delta-v prefix regressed");
            }
            segmentDeltaVMps[index - 1] = Math.max(0d, segmentDeltaV);
            previousCumulativeDeltaV = cumulativeDeltaV;
            totalDeltaV = cumulativeDeltaV;
        }

        double[] availableStationStockKg = new double[route.size()];
        for (int index = 0; index < route.size(); index++) {
            availableStationStockKg[index] = projectedAvailableStockKg(
                    route.get(index), participantFaction, port);
        }

        // Solve backwards for the minimum departure fuel at every route node. This is what makes
        // refueling anticipatory rather than merely reactive: if a later node is dry or has only a
        // small stock, an earlier station can load the extra mass that must be carried through it.
        //
        // The protected reserve belongs to the whole no-service stretch, not independently to every
        // hop. Otherwise two dry hops could each retain 10% of that hop's departure fuel while the
        // complete remaining route still ends below the direct-route 10% reserve authority. Reset
        // the reserve horizon only at a node that has physically accessible finite propellant stock.
        double[] requiredDepartureFuelKg = new double[route.size()];
        double reserveHorizonDeltaVMps = 0d;
        for (int index = segmentCount - 1; index >= 0; index--) {
            if (availableStationStockKg[index + 1] > EPSILON) {
                reserveHorizonDeltaVMps = segmentDeltaVMps[index];
            } else {
                reserveHorizonDeltaVMps += segmentDeltaVMps[index];
            }
            double requiredAtNextDeparture = requiredDepartureFuelKg[index + 1];
            double requiredArrivalFuel = Math.max(
                    0d, requiredAtNextDeparture - availableStationStockKg[index + 1]);
            double requiredDepartureFuel = minimumDepartureFuelKg(
                    nonReactionMassKg,
                    exhaustVelocityMps,
                    segmentDeltaVMps[index],
                    reserveHorizonDeltaVMps,
                    requiredArrivalFuel);
            if (!Double.isFinite(requiredDepartureFuel)
                    || requiredDepartureFuel > capacityKg + EPSILON) {
                return new JourneyPlan(
                        true,
                        false,
                        route,
                        List.of(),
                        totalDeltaV,
                        currentFuelKg,
                        "tank capacity and accessible future station stock cannot make route fuel-safe");
            }
            requiredDepartureFuelKg[index] = Math.max(0d, requiredDepartureFuel);
        }

        TreeMap<String, Double> projectedStationUseKg = new TreeMap<>();
        ArrayList<RefuelStop> stops = new ArrayList<>();
        for (int index = 0; index < segmentCount; index++) {
            double requiredDepartureFuel = requiredDepartureFuelKg[index];
            if (currentFuelKg + EPSILON < requiredDepartureFuel) {
                double requestedTopUpKg = requiredDepartureFuel - currentFuelKg;
                double maximumTopUpKg = maximumProjectedTopUpKg(
                        route.get(index),
                        participantFaction,
                        port,
                        currentFuelKg,
                        capacityKg,
                        projectedStationUseKg);
                if (requestedTopUpKg > maximumTopUpKg + EPSILON) {
                    return new JourneyPlan(
                            true,
                            false,
                            route,
                            List.copyOf(stops),
                            totalDeltaV,
                            currentFuelKg,
                            "current/future accessible station stock cannot satisfy proactive route fuel");
                }
                List<RefuelStop> additions = allocateProjectedTopUp(
                        route.get(index),
                        participantFaction,
                        port,
                        requestedTopUpKg,
                        currentFuelKg,
                        capacityKg,
                        projectedStationUseKg);
                double loaded = additions.stream().mapToDouble(RefuelStop::massKg).sum();
                currentFuelKg += loaded;
                stops.addAll(additions);
            }

            double departureFuelKg = currentFuelKg;
            double consumed = requiredPropellantKg(
                    nonReactionMassKg + departureFuelKg,
                    exhaustVelocityMps,
                    segmentDeltaVMps[index]);
            if (consumed > departureFuelKg + EPSILON) {
                return new JourneyPlan(
                        true, false, route, List.copyOf(stops), totalDeltaV, currentFuelKg,
                        "route segment exceeds physical reaction-mass supply");
            }
            currentFuelKg = Math.max(0d, departureFuelKg - consumed);
            if (currentFuelKg + EPSILON < departureFuelKg * RESERVE_FRACTION) {
                return new JourneyPlan(
                        true, false, route, List.copyOf(stops), totalDeltaV, currentFuelKg,
                        "route segment would violate protected reaction-mass reserve");
            }
        }

        return new JourneyPlan(
                true,
                true,
                route,
                List.copyOf(stops),
                totalDeltaV,
                currentFuelKg,
                "");
    }

    @Override
    public DeparturePreparation prepareDeparture(FleetId fleetId, List<StarSystemId> orderedSystems) {
        FleetId id = Objects.requireNonNull(fleetId, "fleetId");
        List<StarSystemId> route = List.copyOf(Objects.requireNonNull(orderedSystems, "orderedSystems"));
        JourneyPlan planned = planJourney(id, route);
        if (!planned.supported()) {
            return new DeparturePreparation(
                    false, planned.feasible(), 0d, List.of(), planned, planned.reason());
        }
        if (!planned.feasible()) {
            return new DeparturePreparation(
                    true, false, 0d, List.of(), planned, planned.reason());
        }

        FleetPlacementState placement = world.findFleet(id).orElseThrow();
        EngineeringComponent fitted = localEngineering(placement);
        if (fitted == null) {
            return new DeparturePreparation(
                    true, planned.feasible(), 0d, List.of(), planned, planned.reason());
        }

        double loadedMassKg = 0d;
        LinkedHashSet<String> stations = new LinkedHashSet<>();
        for (RefuelStop stop : planned.refuelStops()) {
            if (!stop.systemId().equals(placement.systemId())) {
                continue;
            }
            RuntimeEndpoint endpoint = infrastructure.endpoint(stop.stationId());
            ShipConsumableBinding binding = bindings.findBinding(stop.bindingId());
            ShipEngineeringCatalog catalog = catalogForModule(binding.moduleId());
            Stage18ShipConsumableService service =
                    new Stage18ShipConsumableService(bindings, catalog);
            var loaded = service.load(
                    stop.bindingId(),
                    stop.mountId(),
                    stop.massKg(),
                    fitted.fit,
                    fitted.runtimeState.consumables(),
                    endpoint.storage());
            if (!loaded.committed()) {
                throw new IllegalStateException(
                        "planned local propellant service failed: " + loaded.status()
                                + " station=" + stop.stationId()
                                + " binding=" + stop.bindingId());
            }
            RuntimeState state = fitted.runtimeState;
            fitted.setRuntimeState(new RuntimeState(
                    loaded.consumables(),
                    state.sharedBusEnergyJ(),
                    state.shipHeatStoredJ(),
                    state.localHeatJByMount(),
                    state.thrustLimitNByMount(),
                    state.coolantBusCapacityW(),
                    state.ftlCooldownSecondsByMount()));
            loadedMassKg += loaded.loadedMassKg();
            stations.add(stop.stationId());
        }

        JourneyPlan revalidated = planJourney(id, route);
        return new DeparturePreparation(
                true,
                revalidated.feasible(),
                loadedMassKg,
                List.copyOf(stations),
                revalidated,
                revalidated.feasible() ? "" : revalidated.reason());
    }

    private FuelPort fuelPort(EngineeringComponent fitted) {
        ArrayList<FuelPort> ports = new ArrayList<>();
        for (InstalledModuleDefinition installed : fitted.fit.installedModules()) {
            for (ShipConsumableBinding binding : bindings.getBindings()) {
                if (!binding.moduleId().equals(installed.moduleId())
                        || binding.interfaceKind() != InterfaceKind.REACTION_MASS) {
                    continue;
                }
                ShipEngineeringCatalog catalog = catalogForModule(installed.moduleId());
                var module = catalog.findModule(installed.moduleId());
                InterfaceDefinition physical = module.interfaces().stream()
                        .filter(value -> value.id().equals(binding.interfaceId()))
                        .filter(value -> value.kind() == InterfaceKind.REACTION_MASS)
                        .findFirst().orElse(null);
                if (physical == null) {
                    continue;
                }
                double currentMassKg = fitted.runtimeState.consumables().interfaceLoads().stream()
                        .filter(load -> load.mountId().equals(installed.mountId()))
                        .filter(load -> load.interfaceId().equals(binding.interfaceId()))
                        .filter(load -> load.kind() == InterfaceKind.REACTION_MASS)
                        .mapToDouble(ConsumableLoad::massKg)
                        .sum();
                ports.add(new FuelPort(
                        installed.mountId(),
                        binding,
                        physical.capacity() / binding.amountPerKg(),
                        currentMassKg));
            }
        }
        ports.sort(Comparator
                .comparing(FuelPort::mountId)
                .thenComparing(value -> value.binding().id()));
        if (ports.isEmpty()) {
            return null;
        }
        if (ports.size() != 1) {
            throw new IllegalStateException(
                    "generated refuel planner currently requires exactly one authored reaction-mass feed");
        }
        return ports.get(0);
    }

    private double maximumProjectedTopUpKg(
            StarSystemId systemId,
            String participantFaction,
            FuelPort port,
            double currentFuelKg,
            double capacityKg,
            Map<String, Double> projectedStationUseKg) {
        double remainingCapacity = Math.max(0d, capacityKg - currentFuelKg);
        if (remainingCapacity <= EPSILON) {
            return 0d;
        }
        double available = 0d;
        for (RuntimeEndpoint endpoint : accessibleEndpoints(systemId, participantFaction)) {
            double alreadyUsed = projectedStationUseKg.getOrDefault(stockKey(
                    endpoint.stationId(), port.binding().commodityId()), 0d);
            available += Math.max(
                    0d,
                    endpoint.storage().commodityMassKg(port.binding().commodityId()) - alreadyUsed);
        }
        return Math.min(remainingCapacity, available);
    }

    private List<RefuelStop> allocateProjectedTopUp(
            StarSystemId systemId,
            String participantFaction,
            FuelPort port,
            double requestedKg,
            double currentFuelKg,
            double capacityKg,
            Map<String, Double> projectedStationUseKg) {
        double remaining = Math.min(requestedKg, Math.max(0d, capacityKg - currentFuelKg));
        ArrayList<RefuelStop> result = new ArrayList<>();
        for (RuntimeEndpoint endpoint : accessibleEndpoints(systemId, participantFaction)) {
            if (remaining <= EPSILON) {
                break;
            }
            String key = stockKey(endpoint.stationId(), port.binding().commodityId());
            double alreadyUsed = projectedStationUseKg.getOrDefault(key, 0d);
            double available = Math.max(
                    0d,
                    endpoint.storage().commodityMassKg(port.binding().commodityId()) - alreadyUsed);
            double load = Math.min(remaining, available);
            if (load <= EPSILON) {
                continue;
            }
            result.add(new RefuelStop(
                    systemId,
                    endpoint.stationId(),
                    port.binding().id(),
                    port.mountId(),
                    port.binding().commodityId(),
                    load));
            projectedStationUseKg.put(key, alreadyUsed + load);
            remaining -= load;
        }
        if (remaining > EPSILON) {
            throw new IllegalStateException("projected station allocation could not satisfy computed top-up");
        }
        return List.copyOf(result);
    }

    private List<RuntimeEndpoint> accessibleEndpoints(
            StarSystemId systemId,
            String participantFaction) {
        return infrastructure.endpoints().stream()
                .filter(endpoint -> endpoint.systemId().equals(systemId))
                .filter(endpoint -> endpoint.handlingCapability().supportedStorageClassIds()
                        .contains(LIQUID_STORAGE_CLASS_ID))
                .filter(endpoint -> marketAccess(endpoint, participantFaction))
                .sorted(Comparator.comparing(RuntimeEndpoint::stationId))
                .toList();
    }

    private boolean marketAccess(RuntimeEndpoint endpoint, String participantFaction) {
        String owner = endpoint.generatedIndustrial()
                ? generatedIndustrialOwnerByStation.get(endpoint.stationId())
                : world.controllingFaction(endpoint.systemId()).orElse(null);
        if (owner == null) {
            return true;
        }
        return world.evaluateFactionMarketAccess(owner, participantFaction).allowed();
    }

    private String fleetFaction(FleetPlacementState placement) {
        Entity entity = localEntity(placement);
        FactionComponent faction = entity.getComponent(FactionComponent.class);
        if (faction == null) {
            return null;
        }
        return world.findFactionStableId(faction.factionId).orElse(null);
    }

    private EngineeringComponent localEngineering(FleetPlacementState placement) {
        return localEntity(placement).getComponent(EngineeringComponent.class);
    }

    private Entity localEntity(FleetPlacementState placement) {
        return world.findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().require(placement.localEntityId());
    }

    private ShipEngineeringCatalog catalogForModule(String moduleId) {
        ShipEngineeringCatalog result = null;
        for (ShipEngineeringCatalog catalog : engineeringCatalogs) {
            if (catalog.findModule(moduleId) == null) {
                continue;
            }
            if (result != null) {
                throw new IllegalStateException("ambiguous servicing engineering module: " + moduleId);
            }
            result = catalog;
        }
        if (result == null) {
            throw new IllegalArgumentException("unknown servicing engineering module: " + moduleId);
        }
        return result;
    }

    private double projectedAvailableStockKg(
            StarSystemId systemId,
            String participantFaction,
            FuelPort port) {
        return accessibleEndpoints(systemId, participantFaction).stream()
                .mapToDouble(endpoint -> endpoint.storage().commodityMassKg(
                        port.binding().commodityId()))
                .sum();
    }

    private static double minimumDepartureFuelKg(
            double nonReactionMassKg,
            double exhaustVelocityMps,
            double segmentDeltaVMps,
            double reserveHorizonDeltaVMps,
            double requiredArrivalFuelKg) {
        if (segmentDeltaVMps <= EPSILON && reserveHorizonDeltaVMps <= EPSILON) {
            return Math.max(0d, requiredArrivalFuelKg);
        }
        double retainedFraction = StrictMath.exp(-segmentDeltaVMps / exhaustVelocityMps);
        double burnedFraction = 1d - retainedFraction;
        if (!(retainedFraction > 0d)) {
            return Double.POSITIVE_INFINITY;
        }
        double futureRequirement = (Math.max(0d, requiredArrivalFuelKg)
                + nonReactionMassKg * burnedFraction) / retainedFraction;

        double reserveRetainedFraction =
                StrictMath.exp(-reserveHorizonDeltaVMps / exhaustVelocityMps);
        double reserveBurnedFraction = 1d - reserveRetainedFraction;
        double reserveDenominator = reserveRetainedFraction - RESERVE_FRACTION;
        if (!(reserveDenominator > EPSILON)) {
            return Double.POSITIVE_INFINITY;
        }
        double reserveRequirement =
                nonReactionMassKg * reserveBurnedFraction / reserveDenominator;
        return Math.max(futureRequirement, reserveRequirement);
    }

    private static double minimumTopUpKg(
            double currentFuelKg,
            double maximumTopUpKg,
            double nonReactionMassKg,
            double exhaustVelocityMps,
            double segmentDeltaVMps) {
        double low = 0d;
        double high = maximumTopUpKg;
        for (int attempt = 0; attempt < TOP_UP_SEARCH_STEPS; attempt++) {
            double candidate = (low + high) * 0.5d;
            if (canCompleteSegment(
                    currentFuelKg + candidate,
                    nonReactionMassKg,
                    exhaustVelocityMps,
                    segmentDeltaVMps)) {
                high = candidate;
            } else {
                low = candidate;
            }
        }
        return high;
    }

    private static boolean canCompleteSegment(
            double fuelKg,
            double nonReactionMassKg,
            double exhaustVelocityMps,
            double segmentDeltaVMps) {
        if (segmentDeltaVMps <= EPSILON) {
            return true;
        }
        if (fuelKg <= EPSILON) {
            return false;
        }
        double consumed = requiredPropellantKg(
                nonReactionMassKg + fuelKg,
                exhaustVelocityMps,
                segmentDeltaVMps);
        double reserveKg = fuelKg * RESERVE_FRACTION;
        return consumed <= fuelKg + EPSILON
                && fuelKg - consumed + EPSILON >= reserveKg;
    }

    private static double requiredPropellantKg(
            double initialMassKg,
            double exhaustVelocityMps,
            double deltaVMps) {
        if (deltaVMps <= EPSILON) {
            return 0d;
        }
        return initialMassKg * (1d - StrictMath.exp(-deltaVMps / exhaustVelocityMps));
    }

    private static String stockKey(String stationId, String commodityId) {
        return stationId + '\u0000' + commodityId;
    }

    private record FuelPort(
            String mountId,
            ShipConsumableBinding binding,
            double capacityKg,
            double currentMassKg) {
        private FuelPort {
            Objects.requireNonNull(mountId, "mountId");
            Objects.requireNonNull(binding, "binding");
            if (!Double.isFinite(capacityKg) || capacityKg <= 0d
                    || !Double.isFinite(currentMassKg) || currentMassKg < 0d) {
                throw new IllegalArgumentException("fuel port masses must be finite and physically valid");
            }
        }
    }
}
