package com.spacesim.campaign;

import com.spacesim.content.Stage18RefiningCatalogLoader;
import com.spacesim.content.Stage22CivilianMiningProductionPath;
import com.spacesim.content.Stage23BootstrapMaterialsCatalog;
import com.spacesim.content.Stage23StationStorageCatalog;
import com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader;
import com.spacesim.economy.Stage18FacilityConstructionRuntime.OrderStatus;
import com.spacesim.economy.Stage18FacilityConstructionWorkQueue;
import com.spacesim.economy.Stage18ManufacturingWorkQueue;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.RuntimeEndpoint;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticKnowledge;
import com.spacesim.world.Stage21HPlayerMissionAuthority;
import com.spacesim.world.StarSystemId;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ordinary industrial steps for long scenarios, built on {@link ScenarioPilot}: every step is a pilot
 * input followed by world ticks, and every wait fails fast with its reason when progress stops.
 */
final class IndustrialPilot {
    static final double CRUISE_MPS = 1000d;
    static final Map<String, String> RECIPE_BY_MATERIAL = Map.of(
            "commodity.material.structural_alloy", "refining.structural_alloy",
            "commodity.material.light_alloy", "refining.light_alloy",
            "commodity.material.conductor_metal", "refining.conductor_metal",
            "commodity.material.refractory_alloy", "refining.refractory_alloy",
            "commodity.material.ceramic_glass", "refining.ceramic_glass",
            "commodity.material.carbon_material", "refining.carbon_material",
            "commodity.material.industrial_chemicals", "refining.industrial_chemicals",
            "commodity.material.electronic_grade_material", "refining.electronic_grade_material");

    final ScenarioPilot pilot;
    final String station;
    final String hub;
    private final Set<String> exhaustedSites = new HashSet<>();

    IndustrialPilot(ScenarioPilot pilot, String station) {
        this.pilot = pilot;
        this.station = station;
        this.hub = "hub." + pilot.campaign().coordinator().runtime().infrastructure().endpoint(station).systemId().value();
    }

    /** Builds a new seed campaign, starts the pilot and wraps it. */
    static IndustrialPilot start(long seed, String station, long tickBudget) {
        var campaign = Stage228CampaignAuthority.create(seed);
        campaign.submitIndependentPilotStart(campaign.previewIndependentPilotStart());
        return new IndustrialPilot(new ScenarioPilot(campaign, tickBudget), station);
    }

    /** Restores a scenario checkpoint written by {@link #save}, for short follow-up runs. */
    static IndustrialPilot resume(Path checkpoint, String station, long tickBudget) throws IOException {
        var campaign = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(checkpoint));
        return new IndustrialPilot(new ScenarioPilot(campaign, tickBudget), station);
    }

    void save(String name) throws IOException {
        Stage228GeneratedCampaignPersistenceCodec.write(Path.of("target", name), pilot.campaign().captureState());
    }

    // ---- the starter industrial chain ---------------------------------------------------------

    /** Buys the local miner and the local resource maps at the hub, and takes command of the miner. */
    void buyMinerAndMaps() {
        var miner = pilot.campaign().coordinator().runtime().freight().capture().freighters().stream()
                .filter(f -> f.fitId().equals(Stage22FreightStrategicEngineeringCatalogLoader.UNION_MINING_FREIGHT_STRATEGIC_FIT))
                .findFirst().orElseThrow();
        dock(hub);
        pilot.action("PURCHASE", Long.toString(miner.fleetId().value()), "", 0);
        buyResourceMaps();
        pilot.action("UNDOCK", "", "", 0);
        pilot.action("SWITCH", Long.toString(miner.fleetId().value()), "", 0);
    }

    /** Mines and sells metallic ore in working loads, refuelling, until the wallet holds {@code milliCredits}. */
    void earn(long milliCredits) {
        String ore = "commodity.feedstock.metallic_ore";
        long hour = Math.round(3600d / pilot.campaign().coordinator().session().fixedStepSeconds());
        for (int idleHours = 0; pilot.player().walletMilliCredits() < milliCredits; ) {
            long before = pilot.player().walletMilliCredits();
            if (cargoKg(ore) < 1) {
                long quote = pilot.campaign().pilotCommodityPrice(hub, ore, false);
                // A round trip costs a fixed ~63,000 credits of reaction mass, so carry a full working load.
                mine(ore, Math.max(20_000, Math.ceil((milliCredits - before) / (double) quote * 1.1) + 100));
            }
            // Sell at each local market in turn (not the pilot's own) until the load is gone or no market can pay.
            boolean owned = pilot.campaign().ownsProductionStation(station);
            var markets = owned ? List.of(hub) : List.of(hub, station);
            double refuelled = 0;
            for (String market : markets) {
                if (cargoKg(ore) < 1) break;
                dock(market);
                pilot.tick();
                sellWhilePaid(market, ore);
                refuelled += refuel(market);
                if (cargoKg(ore) >= 1 && pilot.player().walletMilliCredits() <= before) {
                    // No local buyer can pay now: wait docked for an hour of consumer demand to refill the operator.
                    if (++idleHours > 48) throw new AssertionError("no local market paid for " + ore + " in 48 game hours");
                    pilot.ticks((int) hour);
                    sellWhilePaid(market, ore);
                } else idleHours = 0;
                pilot.action("UNDOCK", "", "", 0);
            }
            if (owned && refuelled <= 0) {
                // The hub's reaction mass is gone; the own station keeps its stock.
                dock(station);
                pilot.tick();
                refuel(station);
                pilot.action("UNDOCK", "", "", 0);
            }
            System.out.printf("SCENARIO wallet %.0f / %.0f credits%n", pilot.player().walletMilliCredits() / 1000d, milliCredits / 1000d);
        }
    }

    void buyStation() {
        dock(station);
        pilot.action("PURCHASE_STATION", station, "", 0);
        assertTrue(pilot.campaign().ownsProductionStation(station));
        pilot.action("UNDOCK", "", "", 0);
    }

    /** Mines and delivers the three ores, refines the alloys, builds and commissions the starter facility. */
    String buildStarterFacility() throws IOException {
        var bill = Stage23StationStorageCatalog.construction().requiredMassByCommodityKg(Stage23BootstrapMaterialsCatalog.FACILITY_ID);
        var ore = refiningInputs(bill);
        System.out.println("SCENARIO bill " + bill + " ore " + ore);
        ore.forEach(this::deliverOre);
        pilot.saveAndReload();
        save("industrial-chain-before-refining.s25");
        dock(station);
        // The bulk refinery is the only process line before the starter facility, so its batches run in turn.
        for (var material : bill.entrySet()) runJobs(List.of(Job.refining(material.getKey(), material.getValue())));
        return buildAndCommission(Stage23BootstrapMaterialsCatalog.FACILITY_ID);
    }

    // ---- generic steps -----------------------------------------------------------------------

    void dock(String target) {
        if (pilot.player().docked()) pilot.action("UNDOCK", "", "", 0);
        if (!endpoint(target).systemId().equals(pilot.fleet().systemId())) pilot.travel(endpoint(target).systemId());
        pilot.fly(endpoint(target).position(), 500, CRUISE_MPS);
        pilot.action("DOCK", target, "", 0);
    }

    /** Mines {@code kg} of an ore at mapped sites, returns and unloads it at the station. */
    void deliverOre(String ore, double kg) {
        long t = System.nanoTime();
        mine(ore, kg);
        dock(station);
        pilot.tick();
        unloadAll(station, ore);
        refuel(station);
        pilot.action("UNDOCK", "", "", 0);
        phase("delivered " + Math.round(kg) + " kg " + ore, t);
    }

    /** Buys {@code kg} of a commodity at a market and unloads it at the station. */
    void deliverPurchase(String market, String commodity, double kg) {
        long t = System.nanoTime();
        var previous = pilot.fleetId();
        boolean switched = commandShipFor(commodity);
        dock(market);
        pilot.tick();
        double target = cargoKg(commodity) + kg;
        for (int idle = 0; cargoKg(commodity) + 1e-6 < target; pilot.tick()) {
            int batch = (int) Math.min(Math.ceil(target - cargoKg(commodity)), handlingKg(market));
            if (pilot.allowed("BUY", market, commodity, batch)) { pilot.action("BUY", market, commodity, batch); idle = 0; }
            else if (++idle > 600) pilot.action("BUY", market, commodity, batch); // fails with the authority's reason
        }
        refuel(market);
        dock(station);
        pilot.tick();
        unloadAll(station, commodity);
        pilot.action("UNDOCK", "", "", 0);
        if (switched) switchTo(previous);
        phase("bought and delivered " + Math.round(kg) + " kg " + commodity, t);
    }

    /**
     * Takes command of a local owned ship whose hold carries this commodity's storage class, if the
     * active one cannot (the miner has only a dry-bulk hold).
     * @return whether command changed
     */
    boolean commandShipFor(String commodity) {
        String storageClass = com.spacesim.content.Stage18ResourceOntologyLoader.loadDefault().findCommodity(commodity).storageClassId();
        var freight = pilot.campaign().coordinator().runtime().freight();
        if (freight.cargoHoldSnapshot(pilot.fleetId()).capacityByStorageClassKg().containsKey(storageClass)) return false;
        var carrier = pilot.player().ownedFleetIds().stream()
                .filter(id -> freight.cargoHoldSnapshot(id).capacityByStorageClassKg().containsKey(storageClass)
                        && pilot.world().findFleet(id).orElseThrow().systemId().equals(pilot.fleet().systemId()))
                .findFirst().orElseThrow(() -> new AssertionError("no local owned ship carries " + storageClass));
        switchTo(carrier);
        return true;
    }

    void switchTo(com.spacesim.world.FleetId fleet) {
        if (pilot.player().docked()) pilot.action("UNDOCK", "", "", 0);
        pilot.action("SWITCH", Long.toString(fleet.value()), "", 0);
    }

    /** One process order: the pilot command, its order identity and the stored output that proves it. */
    record Job(String action, String orderPrefix, String operationId, String output, double target, double inputPerOutput) {
        static Job refining(String material, double targetKg) {
            String recipe = RECIPE_BY_MATERIAL.get(material);
            return new Job("START_REFINING", Stage18ManufacturingWorkQueue.REFINING_PREFIX, recipe, material, targetKg,
                    1 / Stage18RefiningCatalogLoader.loadDefault().findRecipe(recipe).outputMassFraction());
        }

        static Job component(String component, double targetKg) {
            var recipe = Stage22CivilianMiningProductionPath.loadManufacturing().getComponentRecipes().stream()
                    .filter(r -> r.outputCommodityId().equals(component)).findFirst().orElseThrow();
            return new Job("START_COMPONENT_MANUFACTURING", Stage18ManufacturingWorkQueue.COMPONENT_PREFIX, recipe.id(), component, targetKg, 1);
        }

        static Job product(String productId, int units) {
            return new Job("START_MANUFACTURING", Stage18ManufacturingWorkQueue.PREFIX, productId, productId, units, 1);
        }

        boolean countsUnits() { return action.equals("START_MANUFACTURING"); }
    }

    /**
     * Starts each order for the missing part of its target at the docked station (one per tick, as
     * the queue requires), then advances until every order has left the queue with its output stored.
     */
    void runJobs(List<Job> jobs) {
        long t = System.nanoTime();
        var started = new ArrayList<Job>();
        for (var job : jobs) {
            double missing = job.target() - stored(job);
            if (missing <= 1e-6) continue;
            int amount = (int) Math.ceil(missing * job.inputPerOutput()) + (job.countsUnits() ? 0 : 1);
            pilot.action(job.action(), station, job.operationId(), amount);
            started.add(job);
            pilot.tick();
        }
        for (double before = -1; ; pilot.ticks(5000)) {
            double progress = started.stream().mapToDouble(j -> orderProgress(orderId(j))).sum();
            if (progress >= 2 * started.size()) break;
            if (progress <= before) throw new AssertionError("process orders stalled: "
                    + started.stream().map(j -> j.operationId() + '=' + orderProgress(orderId(j))).toList());
            before = progress;
        }
        for (var job : started) {
            assertTrue(stored(job) + 1e-6 >= job.target(), job.operationId() + " stored " + stored(job) + " of " + job.target());
            phase((job.countsUnits() ? "manufactured " + Math.round(stored(job)) + " x " : "produced " + Math.round(stored(job)) + " kg ")
                    + job.output(), t);
        }
    }

    private String orderId(Job job) { return job.orderPrefix() + station + ':' + job.operationId(); }

    private double stored(Job job) {
        return job.countsUnits() ? endpoint(station).storage().productCount(job.output()) : storedKg(job.output());
    }

    /** Builds a facility from stored materials and commissions it at full rated resources. */
    String buildAndCommission(String facilityId) {
        long t = System.nanoTime();
        long ordered = pilot.world().getAuthoritativeWorldTick();
        pilot.action("START_FACILITY_CONSTRUCTION", station, facilityId, 0);
        String orderId = Stage18FacilityConstructionWorkQueue.PREFIX + station + ':' + facilityId + ':' + ordered;
        for (int waited = 0; constructionStatus(orderId) != OrderStatus.COMPLETE; waited++) {
            if (waited > 4_000) throw new AssertionError("construction of " + facilityId + " did not complete: " + constructionStatus(orderId));
            pilot.ticks(1000);
        }
        pilot.saveAndReload();
        String instance = orderId + ":installed";
        pilot.action("ALLOCATE_FACILITY_RESOURCES_HALF", station, instance, 0);
        var installed = pilot.campaign().coordinator().runtime().captureState().campaign().industrialState().facilities().stream()
                .filter(f -> f.state().facilityInstanceId().equals(instance)).findFirst().orElseThrow();
        assertEquals(facilityId, installed.state().definitionId());
        assertTrue(installed.state().allocatedProcessPowerW() > 0, "the commissioned facility receives operating power");
        phase("built and commissioned " + facilityId, t);
        return instance;
    }

    void buyResourceMaps() {
        for (var offer : com.spacesim.ui.GeneratedCampaignResourceMapUi.rows(pilot.campaign())) {
            var id = offer.selection().stableId().split("\\|");
            pilot.action("BUY_RESOURCE_MAP", id[1], id[2], 0);
        }
    }

    Stream<StaticKnowledge> mappedSites(String commodity) {
        return pilot.campaign().coordinator().runtime().discoveryState()
                .knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID).entries().stream()
                .filter(k -> k.object().resourceObject() && k.knownLocation().isPresent()
                        && k.classificationId().filter(commodity::equals).isPresent()
                        && !exhaustedSites.contains(k.object().objectId()));
    }

    /**
     * Mines {@code kg} more of an ore: at the nearest mapped site (the current system first, then
     * by jumps), moving on to the next site when one stops yielding.
     */
    void mine(String commodity, double kg) {
        double target = cargoKg(commodity) + kg;
        while (cargoKg(commodity) < target) {
            if (mappedSites(commodity).findAny().isEmpty()) travelForOre(commodity);
            var here = pilot.fleet().systemId();
            var jumps = jumpsFrom(here);
            var site = mappedSites(commodity)
                    .min(Comparator.comparingInt((StaticKnowledge k) -> jumps.getOrDefault(k.object().systemId(), Integer.MAX_VALUE))
                            .thenComparingDouble(k -> k.object().systemId().equals(here)
                                    ? k.knownLocation().orElseThrow().distanceTo(pilot.physical().position()) : 0d))
                    .orElseThrow();
            if (pilot.player().docked()) pilot.action("UNDOCK", "", "", 0);
            if (!site.object().systemId().equals(here)) pilot.travel(site.object().systemId());
            pilot.fly(site.knownLocation().orElseThrow(), 500, CRUISE_MPS);
            if (!pilot.allowed("START_MINING", site.object().objectId(), "", 1000)) {
                System.out.printf("SCENARIO site %s of %s refuses mining%n", site.object().objectId(), commodity);
                exhaustedSites.add(site.object().objectId());
                continue;
            }
            pilot.action("START_MINING", site.object().objectId(), "", 1000);
            for (double last = cargoKg(commodity); cargoKg(commodity) < target; last = cargoKg(commodity)) {
                pilot.ticks(1000);
                if (cargoKg(commodity) <= last) {
                    System.out.printf("SCENARIO site %s of %s stopped yielding at %.0f kg aboard%n",
                            site.object().objectId(), commodity, cargoKg(commodity));
                    exhaustedSites.add(site.object().objectId());
                    break;
                }
            }
            if (pilot.allowed("STOP_MINING", "", "", 0)) pilot.action("STOP_MINING", "", "", 0);
        }
    }

    /** Visits neighbouring dispatcher systems, docking at their stations and buying maps, until a site is mapped. */
    private void travelForOre(String commodity) {
        for (var neighbour : pilot.world().getTopology().neighbors(pilot.fleet().systemId())) {
            pilot.travel(neighbour);
            for (var other : pilot.campaign().coordinator().runtime().infrastructure().endpoints().stream()
                    .filter(e -> e.systemId().equals(neighbour)).sorted(Comparator.comparing(e -> e.stationId())).toList()) {
                if (personalLocation(other.stationId()).isEmpty() && pilot.physical().position().distanceTo(other.position()) > 2e7) continue;
                pilot.fly(other.position(), 500, CRUISE_MPS);
                if (!pilot.allowed("DOCK", other.stationId(), "", 0)) continue;
                pilot.action("DOCK", other.stationId(), "", 0);
                buyResourceMaps();
                pilot.action("UNDOCK", "", "", 0);
                if (mappedSites(commodity).anyMatch(k -> k.object().systemId().equals(neighbour))) return;
            }
        }
        throw new AssertionError("no neighbouring dispatcher sells a map of " + commodity);
    }

    private java.util.Optional<com.spacesim.world.LocalPhysicalPosition> personalLocation(String stationId) {
        return pilot.campaign().coordinator().runtime().discoveryState()
                .knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID).entries().stream()
                .filter(k -> k.object().objectId().equals(stationId) && k.knownLocation().isPresent())
                .map(k -> k.knownLocation().orElseThrow()).findFirst();
    }

    /** @return jump count to every system reachable from {@code origin} */
    Map<StarSystemId, Integer> jumpsFrom(StarSystemId origin) {
        var result = new HashMap<StarSystemId, Integer>();
        var queue = new ArrayDeque<StarSystemId>(List.of(origin));
        result.put(origin, 0);
        while (!queue.isEmpty()) {
            var system = queue.poll();
            for (var next : pilot.world().getTopology().neighbors(system))
                if (result.putIfAbsent(next, result.get(system) + 1) == null) queue.add(next);
        }
        return result;
    }

    /** Sells while this market (refilled by its operator between ticks) can still pay; leaves the rest. */
    void sellWhilePaid(String market, String commodity) {
        for (int idle = 0; cargoKg(commodity) >= 1 && idle <= 60; pilot.tick()) {
            long quote = pilot.campaign().pilotCommodityPrice(market, commodity, false);
            int kg = (int) Math.min(Math.min(Math.floor(cargoKg(commodity)), handlingKg(market)),
                    marketWallet(market) / Math.max(1, quote));
            if (kg >= 1) { pilot.action("SELL", market, commodity, kg); idle = 0; } else idle++;
        }
        System.out.printf("SCENARIO sold at %s; %.0f kg left, market wallet %.0f credits%n", market,
                cargoKg(commodity), marketWallet(market) / 1000d);
    }

    /** Tops up reaction mass at the docked station in purchasable batches, as an ordinary pilot would. */
    double refuel(String at) {
        var fitted = pilot.engineering();
        var binding = com.spacesim.content.Stage22ShipConsumableCatalogLoader.loadDefault().getBindings().stream()
                .filter(b -> fitted.fit.installedModules().stream().anyMatch(m -> m.moduleId().equals(b.moduleId())))
                .findFirst().orElseThrow();
        String mount = fitted.fit.installedModules().stream().filter(m -> m.moduleId().equals(binding.moduleId()))
                .findFirst().orElseThrow().mountId();
        double before = pilot.reactionMassKg();
        int chunk = (int) Math.min(2_000_000, handlingKg(at));
        for (int guard = 0; chunk >= 1_000 && guard < 1_000; guard++) {
            if (pilot.allowed("BUY_CONSUMABLE", binding.id(), mount, chunk)) {
                pilot.action("BUY_CONSUMABLE", binding.id(), mount, chunk);
                pilot.tick();
            } else chunk /= 2;
        }
        System.out.printf("SCENARIO refuel at %s: +%.0f kg, tank %.0f kg, stock %.0f kg; next 1000 kg: %s%n", at,
                pilot.reactionMassKg() - before, pilot.reactionMassKg(), endpoint(at).storage().commodityMassKg(binding.commodityId()),
                pilot.allowed("BUY_CONSUMABLE", binding.id(), mount, 1000) ? "allowed" : pilot.rejectionReason("BUY_CONSUMABLE", binding.id(), mount, 1000));
        return pilot.reactionMassKg() - before;
    }

    /** Unloads in per-tick batches; a tick whose handling budget other traffic already used is retried. */
    void unloadAll(String at, String commodity) {
        for (int idle = 0; cargoKg(commodity) >= 1; pilot.tick()) {
            int kg = (int) Math.min(Math.floor(cargoKg(commodity)), handlingKg(at));
            if (pilot.allowed("UNLOAD_COMMODITY", at, commodity, kg)) { pilot.action("UNLOAD_COMMODITY", at, commodity, kg); idle = 0; }
            else if (++idle > 600) pilot.action("UNLOAD_COMMODITY", at, commodity, kg); // fails with the authority's reason
        }
    }

    long marketWallet(String stationId) {
        var ref = pilot.campaign().pilotMarketReference(stationId).orElseThrow();
        return pilot.world().findSession(ref.systemId()).orElseThrow().getEntityRegistry().require(ref.entityId())
                .getComponent(com.spacesim.components.WalletComponent.class).getBalanceMilliCredits();
    }

    double handlingKg(String at) {
        return Math.floor(endpoint(at).handlingCapability().massRateKgPerSecond()
                * pilot.campaign().coordinator().session().fixedStepSeconds());
    }

    /** @return completed fraction of a process order, or 2 once it has completed and left the queue */
    double orderProgress(String orderId) {
        return pilot.campaign().coordinator().runtime().captureState().campaign().industrialState().processOrders().stream()
                .filter(o -> o.orderId().equals(orderId)).mapToDouble(o -> o.completedFraction()).findFirst().orElse(2d);
    }

    OrderStatus constructionStatus(String orderId) {
        return pilot.campaign().coordinator().runtime().captureState().campaign().industrialState().constructionOrders().stream()
                .filter(o -> o.orderId().equals(orderId)).findFirst().orElseThrow().status();
    }

    double storedKg(String commodity) { return endpoint(station).storage().commodityMassKg(commodity); }

    double cargoKg(String commodity) {
        return pilot.campaign().coordinator().runtime().freight().findFreighter(pilot.fleetId()).orElseThrow()
                .cargoStorage().commodityMassByIdKg().getOrDefault(commodity, 0d);
    }

    RuntimeEndpoint endpoint(String id) {
        return pilot.campaign().coordinator().runtime().infrastructure().endpoint(id);
    }

    /** @return kg of each feedstock (gross rounded up per recipe, as {@link #runJobs} orders it) to refine the given masses */
    static Map<String, Double> refiningInputs(Map<String, Double> materialKg) {
        var recipes = Stage18RefiningCatalogLoader.loadDefault();
        var inputs = new TreeMap<String, Double>();
        materialKg.forEach((material, kg) -> {
            if (kg <= 1e-6) return;
            var recipe = recipes.findRecipe(RECIPE_BY_MATERIAL.get(material));
            double gross = Math.ceil(kg / recipe.outputMassFraction()) + 1;
            for (var input : recipe.inputs()) inputs.merge(input.commodityId(), gross * input.fractionOfInputMass(), Double::sum);
        });
        inputs.replaceAll((commodity, kg) -> Math.ceil(kg) + 1);
        return inputs;
    }

    void phase(String name, long startNanos) {
        System.out.printf("SCENARIO %-78s %6.1fs  tick=%d%n", name, (System.nanoTime() - startNanos) / 1e9,
                pilot.world().getAuthoritativeWorldTick());
    }
}
