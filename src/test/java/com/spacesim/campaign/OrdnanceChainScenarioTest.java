package com.spacesim.campaign;

import com.spacesim.content.Stage22CivilianMiningProductionPath;
import com.spacesim.content.Stage23AmmunitionProduction;
import com.spacesim.content.Stage23StationStorageCatalog;
import com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader;
import com.spacesim.ship.ShipAmmunitionSupplyService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ordinary ordnance chain after the first industrial chain: mine the six ores (strategic metal from
 * a mapped system several jumps away), buy volatile feedstock at the hub, refine the eight materials
 * on the starter facility, manufacture heavy, electrical and precision components, build and
 * commission the ordnance plant, manufacture Union darts, buy the armed escort and load its
 * kinetic feed at the own station. Only pilot inputs; nothing is granted.
 */
@Tag("scenario")
@Tag("soak")
class OrdnanceChainScenarioTest {
    static final String ORDNANCE = "facility.fabrication.ordnance";
    static final String DART = Stage23AmmunitionProduction.UNION;
    static final String VOLATILES = "commodity.feedstock.volatile_feedstock";
    static final int ROUNDS = 4;
    static final long TRIP_RESERVE_MILLI_CREDITS = 250_000_000L;

    @Test
    @org.junit.jupiter.api.Disabled("blocked by balance findings 11 and 13: NPC freight drains the player's station water, hubs run dry")
    @Timeout(value = 75, unit = TimeUnit.MINUTES)
    void ordinaryPilotBuildsAnOrdnanceLineAndArmsABoughtEscort() throws IOException {
        var industry = IndustrialPilot.start(IndustrialChainScenarioTest.SEED, IndustrialChainScenarioTest.STATION, 14_000_000L);
        IndustrialChainScenarioTest.run(industry);
        industry.save(IndustrialChainScenarioTest.COMPLETE_CHECKPOINT);
        run(industry);
    }

    static void run(IndustrialPilot industry) throws IOException {
        long t0 = System.nanoTime();
        industry.keepFuelReserve();
        var bill = Stage23StationStorageCatalog.construction().requiredMassByCommodityKg(ORDNANCE);
        var components = new TreeMap<String, Double>();
        bill.forEach((commodity, kg) -> { if (commodity.startsWith("commodity.component.")) components.put(commodity, kg); });
        var materials = materials(bill, components);
        var missing = new TreeMap<String, Double>();
        materials.forEach((material, kg) -> missing.put(material, Math.max(0, kg - industry.storedKg(material))));
        var feedstock = new TreeMap<String, Double>();
        IndustrialPilot.refiningInputs(missing).forEach((ore, kg) -> {
            double needed = Math.ceil(kg - industry.storedKg(ore));
            if (needed > 0) feedstock.put(ore, needed);
        });
        System.out.println("SCENARIO ordnance bill " + bill + "\nSCENARIO materials " + materials + "\nSCENARIO feedstock " + feedstock);

        // 1. Working money for long trips and the volatile purchase, then the feedstock.
        long t = System.nanoTime();
        long volatilesCost = Math.round(feedstock.getOrDefault(VOLATILES, 0d))
                * industry.pilot.campaign().pilotCommodityPrice(industry.hub, VOLATILES, true);
        // The volatiles, the escort and a full tank of water for the trip to the strategic ore.
        industry.earn(volatilesCost + Stage228CampaignAuthority.PILOT_SHIP_PRICE_MILLI_CREDITS + TRIP_RESERVE_MILLI_CREDITS);
        industry.phase("earned trip budget", t);
        Double volatiles = feedstock.remove(VOLATILES);
        if (volatiles != null) industry.deliverPurchase(industry.hub, VOLATILES, volatiles);
        feedstock.forEach(industry::deliverOre);
        industry.pilot.saveAndReload();
        industry.save("ordnance-chain-feedstock.s25");

        // 2. Materials on the starter facility and the bulk refinery, then components on the assembly line.
        industry.dock(industry.station);
        var refining = new ArrayList<IndustrialPilot.Job>();
        materials.forEach((material, kg) -> refining.add(IndustrialPilot.Job.refining(material, kg)));
        industry.runJobs(refining);
        var manufacturing = new ArrayList<IndustrialPilot.Job>();
        components.forEach((component, kg) -> manufacturing.add(IndustrialPilot.Job.component(component, kg)));
        industry.runJobs(manufacturing);
        industry.pilot.saveAndReload();
        industry.save("ordnance-chain-materials.s25");

        // 3. The ordnance plant, then the rounds.
        industry.buildAndCommission(ORDNANCE);
        industry.runJobs(java.util.List.of(IndustrialPilot.Job.product(DART, ROUNDS)));
        industry.pilot.saveAndReload();
        industry.save("ordnance-chain-rounds.s25");

        // 4. Buy the armed escort at the hub, take command and load its kinetic feed at the own station.
        armEscort(industry);
        industry.phase("ordnance chain total", t0);
    }

    static void armEscort(IndustrialPilot industry) {
        long t = System.nanoTime();
        var pilot = industry.pilot;
        var escort = pilot.campaign().coordinator().runtime().freight().capture().freighters().stream()
                .filter(f -> f.fitId().equals(Stage22FreightStrategicEngineeringCatalogLoader.UNION_ARMED_FREIGHT_STRATEGIC_FIT))
                .findFirst().orElseThrow();
        industry.dock(industry.hub);
        pilot.action("PURCHASE", Long.toString(escort.fleetId().value()), "", 0);
        pilot.action("UNDOCK", "", "", 0);
        pilot.action("SWITCH", Long.toString(escort.fleetId().value()), "", 0);
        industry.dock(industry.hub);
        industry.refuel(industry.hub);
        industry.dock(industry.station);
        pilot.tick();
        var feed = new ShipAmmunitionSupplyService().feeds(pilot.engineering()).stream()
                .filter(f -> f.mountId().equals(Stage22FreightStrategicEngineeringCatalogLoader.FREIGHT_WEAPON_HARDPOINT))
                .findFirst().orElseThrow();
        assertEquals(DART, feed.productId());
        pilot.action("LOAD_AMMUNITION", industry.station, feed.mountId(), ROUNDS);
        assertEquals(ROUNDS, pilot.engineering().runtimeState.consumables().ammunitionCount());
        pilot.saveAndReload();
        assertEquals(ROUNDS, pilot.engineering().runtimeState.consumables().ammunitionCount(), "loaded rounds survive save/load");
        industry.phase("bought the armed escort and loaded " + ROUNDS + " rounds", t);
    }

    /**
     * @return refined materials for the plant bill, its components (each ordered one kilogram over,
     *         as {@link IndustrialPilot#runJobs} does) and the rounds, each with a kilogram of margin
     */
    static Map<String, Double> materials(Map<String, Double> bill, Map<String, Double> components) {
        var manufacturing = Stage22CivilianMiningProductionPath.loadManufacturing();
        var result = new TreeMap<String, Double>();
        bill.forEach((commodity, kg) -> { if (!components.containsKey(commodity)) result.merge(commodity, kg, Double::sum); });
        components.forEach((component, kg) -> manufacturing.getComponentRecipes().stream()
                .filter(r -> r.outputCommodityId().equals(component)).findFirst().orElseThrow()
                .inputs().forEach(i -> result.merge(i.commodityId(), (Math.ceil(kg) + 1) * i.fractionOfOutputMass(), Double::sum)));
        double roundsKg = Stage22CivilianMiningProductionPath.loadProducts().findProduct(DART).unitMassKg() * ROUNDS;
        manufacturing.findProductProfile(manufacturing.findProductBinding(DART).profileId()).inputs()
                .forEach(i -> result.merge(i.commodityId(), roundsKg * i.fractionOfOutputMass(), Double::sum));
        result.replaceAll((material, kg) -> Math.ceil(kg) + 1);
        return result;
    }
}
