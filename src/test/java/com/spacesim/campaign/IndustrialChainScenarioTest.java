package com.spacesim.campaign;

import com.spacesim.content.Stage18RefiningCatalogLoader;
import com.spacesim.content.Stage23BootstrapMaterialsCatalog;
import com.spacesim.content.Stage23StationStorageCatalog;
import com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader;
import com.spacesim.economy.Stage18FacilityConstructionWorkQueue;
import com.spacesim.economy.Stage18FacilityConstructionRuntime.OrderStatus;
import com.spacesim.world.Stage21HPlayerMissionAuthority;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ordinary first industrial chain: earn money by mining, buy the local industrial station, mine the
 * three ores (one of them in a neighbouring system), unload them, refine the three basic alloys and
 * build and commission the starter batch-processing facility. Only pilot inputs; nothing is granted.
 */
@Tag("scenario")
@Tag("slow")
class IndustrialChainScenarioTest {
    private static final long SEED = 2L;
    private static final double CRUISE_MPS = 1000d;
    private static final String STATION = "industry.1";
    private static final Map<String, String> ORE_BY_ALLOY = Map.of(
            "commodity.material.structural_alloy", "commodity.feedstock.metallic_ore",
            "commodity.material.light_alloy", "commodity.feedstock.light_metal_minerals",
            "commodity.material.conductor_metal", "commodity.feedstock.conductor_ore");
    private static final Map<String, String> RECIPE_BY_ALLOY = Map.of(
            "commodity.material.structural_alloy", "refining.structural_alloy",
            "commodity.material.light_alloy", "refining.light_alloy",
            "commodity.material.conductor_metal", "refining.conductor_metal");

    @Test
    @org.junit.jupiter.api.Timeout(value = 30, unit = java.util.concurrent.TimeUnit.MINUTES)
    void ordinaryPilotBuildsAndCommissionsTheStarterProcessingFacility() {
        long t0 = System.nanoTime();
        var start = Stage228CampaignAuthority.create(SEED);
        start.submitIndependentPilotStart(start.previewIndependentPilotStart());
        var pilot = new ScenarioPilot(start, 6_000_000L);
        var home = pilot.fleet().systemId();
        String hub = "hub." + home.value();

        // 1. Miner and local resource maps.
        var miner = pilot.campaign().coordinator().runtime().freight().capture().freighters().stream()
                .filter(f -> f.fitId().equals(Stage22FreightStrategicEngineeringCatalogLoader.UNION_MINING_FREIGHT_STRATEGIC_FIT))
                .findFirst().orElseThrow();
        pilot.fly(endpoint(pilot, hub).position(), 500, CRUISE_MPS);
        pilot.action("DOCK", hub, "", 0);
        pilot.action("PURCHASE", Long.toString(miner.fleetId().value()), "", 0);
        buyResourceMaps(pilot);
        pilot.action("UNDOCK", "", "", 0);
        pilot.action("SWITCH", Long.toString(miner.fleetId().value()), "", 0);
        phase("miner and maps", t0, pilot);

        // 2. Earn the station price by mining and selling metallic ore at the hub.
        long t = System.nanoTime();
        long price = pilot.campaign().stationSalePriceMilliCredits(STATION);
        while (pilot.player().walletMilliCredits() < price) {
            long quote = pilot.campaign().pilotCommodityPrice(hub, "commodity.feedstock.metallic_ore", false);
            double shortfallKg = Math.ceil((price - pilot.player().walletMilliCredits()) / (double) quote * 1.1) + 100;
            mine(pilot, "commodity.feedstock.metallic_ore", shortfallKg);
            pilot.fly(endpoint(pilot, hub).position(), 500, CRUISE_MPS);
            pilot.action("DOCK", hub, "", 0);
            pilot.tick();
            sellAll(pilot, hub, "commodity.feedstock.metallic_ore");
            refuel(pilot, hub);
            pilot.action("UNDOCK", "", "", 0);
            System.out.printf("SCENARIO wallet %.0f / %.0f credits%n", pilot.player().walletMilliCredits() / 1000d, price / 1000d);
        }
        phase("earned station price", t, pilot);

        // 3. Buy the station.
        t = System.nanoTime();
        pilot.fly(endpoint(pilot, STATION).position(), 500, CRUISE_MPS);
        pilot.action("DOCK", STATION, "", 0);
        pilot.action("PURCHASE_STATION", STATION, "", 0);
        assertTrue(pilot.campaign().ownsProductionStation(STATION));
        pilot.action("UNDOCK", "", "", 0);
        phase("bought " + STATION, t, pilot);

        // 4. Mine and unload the ore for each alloy of the starter facility's bill.
        var bill = Stage23StationStorageCatalog.construction().requiredMassByCommodityKg(Stage23BootstrapMaterialsCatalog.FACILITY_ID);
        var recipes = Stage18RefiningCatalogLoader.loadDefault();
        var oreNeeded = new TreeMap<String, Double>();
        bill.forEach((alloy, kg) -> oreNeeded.put(ORE_BY_ALLOY.get(alloy),
                Math.ceil(kg / recipes.findRecipe(RECIPE_BY_ALLOY.get(alloy)).outputMassFraction()) + 1));
        System.out.println("SCENARIO bill " + bill + " ore " + oreNeeded);
        for (var ore : oreNeeded.entrySet()) {
            t = System.nanoTime();
            if (!hasMappedSite(pilot, ore.getKey())) travelForOre(pilot, ore.getKey());
            mine(pilot, ore.getKey(), ore.getValue());
            if (!pilot.fleet().systemId().equals(home)) pilot.travel(home);
            pilot.fly(endpoint(pilot, STATION).position(), 500, CRUISE_MPS);
            pilot.action("DOCK", STATION, "", 0);
            pilot.tick();
            unloadAll(pilot, STATION, ore.getKey());
            refuel(pilot, STATION);
            pilot.action("UNDOCK", "", "", 0);
            phase("delivered " + Math.round(ore.getValue()) + " kg " + ore.getKey(), t, pilot);
        }
        pilot.saveAndReload();

        // 5. Refine the three alloys at the owned bulk refinery.
        t = System.nanoTime();
        pilot.fly(endpoint(pilot, STATION).position(), 500, CRUISE_MPS);
        pilot.action("DOCK", STATION, "", 0);
        for (var alloy : bill.entrySet()) {
            String ore = ORE_BY_ALLOY.get(alloy.getKey());
            int kg = (int) Math.floor(storedKg(pilot, ore));
            pilot.action("START_REFINING", STATION, RECIPE_BY_ALLOY.get(alloy.getKey()), kg);
            for (double before = -1; storedKg(pilot, alloy.getKey()) + 1e-6 < alloy.getValue(); pilot.ticks(1000)) {
                if (storedKg(pilot, alloy.getKey()) <= before) throw new AssertionError("refining of " + alloy.getKey()
                        + " stopped at " + storedKg(pilot, alloy.getKey()) + " kg of " + alloy.getValue());
                before = storedKg(pilot, alloy.getKey());
            }
        }
        phase("refined " + bill.keySet(), t, pilot);

        // 6. Build and commission the starter facility.
        t = System.nanoTime();
        long ordered = pilot.world().getAuthoritativeWorldTick();
        pilot.action("START_FACILITY_CONSTRUCTION", STATION, Stage23BootstrapMaterialsCatalog.FACILITY_ID, 0);
        String orderId = Stage18FacilityConstructionWorkQueue.PREFIX + STATION + ':' + Stage23BootstrapMaterialsCatalog.FACILITY_ID + ':' + ordered;
        for (int waited = 0; constructionStatus(pilot, orderId) != OrderStatus.COMPLETE; waited++) {
            if (waited > 2_000) throw new AssertionError("construction did not complete in 2,000,000 ticks: " + constructionStatus(pilot, orderId));
            pilot.ticks(1000);
        }
        pilot.saveAndReload();
        pilot.action("ALLOCATE_FACILITY_RESOURCES_HALF", STATION, orderId + ":installed", 0);
        var installed = pilot.campaign().coordinator().runtime().captureState().campaign().industrialState().facilities().stream()
                .filter(f -> f.state().facilityInstanceId().equals(orderId + ":installed")).findFirst().orElseThrow();
        assertEquals(Stage23BootstrapMaterialsCatalog.FACILITY_ID, installed.state().definitionId());
        assertTrue(installed.state().allocatedProcessPowerW() > 0, "the commissioned facility receives operating power");
        phase("built and commissioned " + Stage23BootstrapMaterialsCatalog.FACILITY_ID, t, pilot);
        pilot.saveAndReload();
        phase("total", t0, pilot);
    }

    /** Tops up reaction mass at the docked station in purchasable batches, as an ordinary pilot would. */
    private static void refuel(ScenarioPilot pilot, String station) {
        var fitted = pilot.engineering();
        var binding = com.spacesim.content.Stage22ShipConsumableCatalogLoader.loadDefault().getBindings().stream()
                .filter(b -> fitted.fit.installedModules().stream().anyMatch(m -> m.moduleId().equals(b.moduleId())))
                .findFirst().orElseThrow();
        String mount = fitted.fit.installedModules().stream().filter(m -> m.moduleId().equals(binding.moduleId()))
                .findFirst().orElseThrow().mountId();
        double before = pilot.reactionMassKg();
        int chunk = (int) Math.min(2_000_000, handlingKg(pilot, station));
        for (int guard = 0; chunk >= 1_000 && guard < 1_000; guard++) {
            if (pilot.allowed("BUY_CONSUMABLE", binding.id(), mount, chunk)) {
                pilot.action("BUY_CONSUMABLE", binding.id(), mount, chunk);
                pilot.tick();
            } else chunk /= 2;
        }
        System.out.printf("SCENARIO refuel at %s: +%.0f kg, tank %.0f kg%n", station, pilot.reactionMassKg() - before, pilot.reactionMassKg());
    }

    private static void buyResourceMaps(ScenarioPilot pilot) {
        for (var offer : com.spacesim.ui.GeneratedCampaignResourceMapUi.rows(pilot.campaign())) {
            var id = offer.selection().stableId().split("\\|");
            pilot.action("BUY_RESOURCE_MAP", id[1], id[2], 0);
        }
    }

    private static boolean hasMappedSite(ScenarioPilot pilot, String commodity) {
        return mappedSites(pilot, commodity).findAny().isPresent();
    }

    private static java.util.stream.Stream<com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticKnowledge> mappedSites(
            ScenarioPilot pilot, String commodity) {
        return pilot.campaign().coordinator().runtime().discoveryState()
                .knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID).entries().stream()
                .filter(k -> k.object().resourceObject() && k.knownLocation().isPresent()
                        && k.classificationId().filter(commodity::equals).isPresent());
    }

    /** Visits neighbouring dispatcher systems, docking at their stations and buying maps, until a site is mapped. */
    private static void travelForOre(ScenarioPilot pilot, String commodity) {
        for (var neighbour : pilot.world().getTopology().neighbors(pilot.fleet().systemId())) {
            pilot.travel(neighbour);
            for (var station : pilot.campaign().coordinator().runtime().infrastructure().endpoints().stream()
                    .filter(e -> e.systemId().equals(neighbour)).sorted(Comparator.comparing(e -> e.stationId())).toList()) {
                if (personalLocation(pilot, station.stationId()).isEmpty() && pilot.physical().position().distanceTo(station.position()) > 2e7) continue;
                pilot.fly(station.position(), 500, CRUISE_MPS);
                if (!pilot.allowed("DOCK", station.stationId(), "", 0)) continue;
                pilot.action("DOCK", station.stationId(), "", 0);
                buyResourceMaps(pilot);
                pilot.action("UNDOCK", "", "", 0);
                if (mappedSites(pilot, commodity).anyMatch(k -> k.object().systemId().equals(neighbour))) return;
            }
        }
        throw new AssertionError("no neighbouring dispatcher sells a map of " + commodity);
    }

    private static void mine(ScenarioPilot pilot, String commodity, double kg) {
        var site = mappedSites(pilot, commodity).filter(k -> k.object().systemId().equals(pilot.fleet().systemId()))
                .min(Comparator.comparingDouble(k -> k.knownLocation().orElseThrow().distanceTo(pilot.physical().position())))
                .orElseThrow(() -> new AssertionError("no mapped site of " + commodity + " in " + pilot.fleet().systemId()));
        pilot.fly(site.knownLocation().orElseThrow(), 500, CRUISE_MPS);
        double target = cargoKg(pilot, commodity) + kg;
        pilot.action("START_MINING", site.object().objectId(), "", 1000);
        double last = cargoKg(pilot, commodity);
        while (cargoKg(pilot, commodity) < target) {
            pilot.ticks(1000);
            if (cargoKg(pilot, commodity) <= last) throw new AssertionError("mining of " + commodity + " stopped at "
                    + Math.round(last) + " kg of " + Math.round(target) + " kg");
            last = cargoKg(pilot, commodity);
        }
        pilot.action("STOP_MINING", "", "", 0);
    }

    /** Sells in batches the market can pay for now; its operator refills the docked market between ticks. */
    private static void sellAll(ScenarioPilot pilot, String station, String commodity) {
        int idle = 0;
        while (cargoKg(pilot, commodity) >= 1) {
            long quote = pilot.campaign().pilotCommodityPrice(station, commodity, false);
            long affordable = marketWallet(pilot, station) / Math.max(1, quote);
            int kg = (int) Math.min(Math.min(Math.floor(cargoKg(pilot, commodity)), handlingKg(pilot, station)), affordable);
            if (kg >= 1) { pilot.action("SELL", station, commodity, kg); idle = 0; }
            else if (++idle > 600) throw new AssertionError("market " + station + " stopped buying " + commodity
                    + ": wallet=" + marketWallet(pilot, station) + " quote=" + quote);
            pilot.tick();
        }
    }

    private static long marketWallet(ScenarioPilot pilot, String stationId) {
        var ref = pilot.campaign().pilotMarketReference(stationId).orElseThrow();
        return pilot.world().findSession(ref.systemId()).orElseThrow().getEntityRegistry().require(ref.entityId())
                .getComponent(com.spacesim.components.WalletComponent.class).getBalanceMilliCredits();
    }

    private static void unloadAll(ScenarioPilot pilot, String station, String commodity) {
        while (cargoKg(pilot, commodity) >= 1) {
            int kg = (int) Math.min(Math.floor(cargoKg(pilot, commodity)), handlingKg(pilot, station));
            pilot.action("UNLOAD_COMMODITY", station, commodity, kg);
            pilot.tick();
        }
    }

    private static double handlingKg(ScenarioPilot pilot, String station) {
        return Math.floor(endpoint(pilot, station).handlingCapability().massRateKgPerSecond()
                * pilot.campaign().coordinator().session().fixedStepSeconds());
    }

    private static OrderStatus constructionStatus(ScenarioPilot pilot, String orderId) {
        return pilot.campaign().coordinator().runtime().captureState().campaign().industrialState().constructionOrders().stream()
                .filter(o -> o.orderId().equals(orderId)).findFirst().orElseThrow().status();
    }

    private static java.util.Optional<com.spacesim.world.LocalPhysicalPosition> personalLocation(ScenarioPilot pilot, String stationId) {
        return pilot.campaign().coordinator().runtime().discoveryState()
                .knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID).entries().stream()
                .filter(k -> k.object().objectId().equals(stationId) && k.knownLocation().isPresent())
                .map(k -> k.knownLocation().orElseThrow()).findFirst();
    }

    private static double storedKg(ScenarioPilot pilot, String commodity) {
        return endpoint(pilot, STATION).storage().commodityMassKg(commodity);
    }

    private static double cargoKg(ScenarioPilot pilot, String commodity) {
        return pilot.campaign().coordinator().runtime().freight().findFreighter(pilot.fleetId()).orElseThrow()
                .cargoStorage().commodityMassByIdKg().getOrDefault(commodity, 0d);
    }

    private static com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.RuntimeEndpoint endpoint(ScenarioPilot pilot, String id) {
        return pilot.campaign().coordinator().runtime().infrastructure().endpoint(id);
    }

    private static void phase(String name, long startNanos, ScenarioPilot pilot) {
        System.out.printf("SCENARIO %-78s %6.1fs  tick=%d%n", name, (System.nanoTime() - startNanos) / 1e9,
                pilot.world().getAuthoritativeWorldTick());
    }
}
