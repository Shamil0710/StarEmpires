package com.spacesim.campaign;

import com.spacesim.components.WalletComponent;
import com.spacesim.content.Stage22ShipConsumableCatalogLoader;
import com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader;
import com.spacesim.ui.GeneratedWorldUiModel;
import com.spacesim.ui.MiningBeam;
import com.spacesim.ui.ProductionUiProjector;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ordinary miner loop on a generated campaign: dock at the seller's hub, buy the existing mining ship
 * and the local dispatcher's resource maps, fly to the nearest mapped free-body source, mine with a
 * save/load in the middle, return, sell the ore and pay for reaction mass. Everything goes through
 * {@link ScenarioPilot}; no state is edited and no coordinate comes from outside the pilot's own knowledge.
 */
@Tag("scenario")
class MinerLoopScenarioTest {
    private static final long SEED = 2L;
    private static final long TICK_BUDGET = 1_500_000L;
    private static final double CRUISE_MPS = 3000d;
    private static final int MINING_KG_PER_TICK = 100;
    private static final int MINING_TICKS = 100;

    @Test
    void ordinaryMinerLoopConservesMoneyAndMassAcrossSaveLoad() {
        long t0 = System.nanoTime();
        var campaign = Stage228CampaignAuthority.create(SEED);
        campaign.submitIndependentPilotStart(campaign.previewIndependentPilotStart());
        var pilot = new ScenarioPilot(campaign, TICK_BUDGET);
        phase("create", t0, pilot);

        // 1. The pilot starts beside the existing mining ship; fly to its hub and dock.
        long t = System.nanoTime();
        var miner = freight(pilot).capture().freighters().stream()
                .filter(f -> f.fitId().equals(Stage22FreightStrategicEngineeringCatalogLoader.UNION_MINING_FREIGHT_STRATEGIC_FIT))
                .findFirst().orElseThrow();
        assertEquals(miner.currentSystemId(), pilot.fleet().systemId(), "pilot starts in the miner seller's system");
        String hub = "hub." + miner.currentSystemId().value();
        pilot.fly(endpoint(pilot, hub).position(), 500, CRUISE_MPS);
        pilot.action("DOCK", hub, "", 0);
        phase("approach hub", t, pilot);

        // 2. Buy the existing mining ship; money moves from the wallet to the seller's treasury.
        t = System.nanoTime();
        long walletBefore = pilot.player().walletMilliCredits();
        long treasuryBefore = treasury(pilot, miner.stableFactionId());
        int hullsBefore = pilot.world().getFleetPlacements().size();
        long paid = pilot.action("PURCHASE", Long.toString(miner.fleetId().value()), "", 0);
        assertTrue(paid < 0, "purchase costs money");
        assertEquals(walletBefore + paid, pilot.player().walletMilliCredits());
        assertEquals(treasuryBefore - paid, treasury(pilot, miner.stableFactionId()));
        assertEquals(hullsBefore, pilot.world().getFleetPlacements().size(), "purchase creates no hull");

        // Buy every resource map the local dispatcher offers; 100 credits each go to its operator.
        var offers = com.spacesim.ui.GeneratedCampaignResourceMapUi.rows(pilot.campaign());
        assertTrue(!offers.isEmpty(), "the local dispatcher sells maps of its system's resource sites");
        for (var offer : offers) {
            var id = offer.selection().stableId().split("\\|");
            long operatorBefore = treasury(pilot, miner.stableFactionId());
            long cost = pilot.action("BUY_RESOURCE_MAP", id[1], id[2], 0);
            assertEquals(-Stage228CampaignAuthority.RESOURCE_MAP_PRICE_MILLI_CREDITS, cost);
            assertEquals(operatorBefore - cost, treasury(pilot, miner.stableFactionId()));
        }
        pilot.action("UNDOCK", "", "", 0);
        pilot.action("SWITCH", Long.toString(miner.fleetId().value()), "", 0);
        assertEquals(miner.fleetId(), pilot.fleetId());
        phase("purchase miner and " + offers.size() + " maps", t, pilot);

        // 3. Fly to the nearest personally mapped source and mine, saving and reloading mid-order.
        t = System.nanoTime();
        var mapped = pilot.campaign().coordinator().runtime().discoveryState()
                .knowledgeFor(com.spacesim.world.Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID).entries().stream()
                .filter(k -> k.object().resourceObject() && k.knownLocation().isPresent()
                        && k.object().systemId().equals(pilot.fleet().systemId()))
                .min(Comparator.comparingDouble(k -> k.knownLocation().orElseThrow().distanceTo(pilot.physical().position())))
                .orElseThrow();
        long outboundTicks = pilot.fly(mapped.knownLocation().orElseThrow(), 500, CRUISE_MPS);
        phase("outbound flight " + outboundTicks + " ticks", t, pilot);

        t = System.nanoTime();
        String sourceId = mapped.object().objectId();
        double sourceBefore = remainingSourceKg(pilot, sourceId);
        double cargoBefore = oreKg(pilot);
        assertTrue(ProductionUiProjector.miningBeams(pilot.campaign()).isEmpty(), "no beam before mining");
        pilot.action("START_MINING", sourceId, "", MINING_KG_PER_TICK);
        // The system map draws a beam between the two objects it shows, from the order itself.
        var beam = new MiningBeam("fleet:" + pilot.fleetId().value(), "resource:" + sourceId,
                pilot.world().getAuthoritativeWorldTick());
        assertEquals(List.of(beam), ProductionUiProjector.miningBeams(pilot.campaign()), "the assigned order shows a beam");
        var shown = mapObjectIds(pilot);
        assertTrue(shown.contains(beam.shipStableId()) && shown.contains(beam.sourceStableId()),
                "the beam's ship and source are both on the system map");
        pilot.ticks(MINING_TICKS / 2);
        assertEquals(pilot.world().getAuthoritativeWorldTick(),
                ProductionUiProjector.miningBeams(pilot.campaign()).get(0).lastProcessedTick(),
                "the watermark advances with every extraction, which is what fires the sparks");
        double beforeReload = oreKg(pilot) - cargoBefore;
        var beamsBeforeReload = ProductionUiProjector.miningBeams(pilot.campaign());
        pilot.saveAndReload();
        assertEquals(beamsBeforeReload, ProductionUiProjector.miningBeams(pilot.campaign()), "same beam after save/load");
        pilot.ticks(MINING_TICKS / 2);
        pilot.action("STOP_MINING", "", "", 0);
        assertTrue(ProductionUiProjector.miningBeams(pilot.campaign()).isEmpty(), "the beam ends with the order");
        double mined = oreKg(pilot) - cargoBefore;
        System.out.printf("SCENARIO mining before reload=%.3f kg, after reload=%.3f kg%n", beforeReload, mined - beforeReload);
        assertTrue(beforeReload > 0 && mined > beforeReload, "mining progresses on both sides of save/load");
        double extracted = sourceBefore - remainingSourceKg(pilot, sourceId);
        assertTrue(mined > 0, "mining loads ore");
        assertTrue(extracted + 1e-6 >= mined, "ore cannot exceed mass removed from the source: mined=" + mined + " extracted=" + extracted);
        phase("mining " + Math.round(mined) + " kg", t, pilot);

        // 4. Return to the hub and sell the ore in per-tick handling batches.
        t = System.nanoTime();
        long returnTicks = pilot.fly(endpoint(pilot, hub).position(), 500, CRUISE_MPS);
        pilot.action("DOCK", hub, "", 0);
        pilot.tick();
        String ore = oreCommodity(pilot);
        double cargoAtHub = cargoKg(pilot, ore);
        double stockBefore = endpoint(pilot, hub).storage().commodityMassKg(ore);
        long moneyBefore = pilot.player().walletMilliCredits();
        long income = 0;
        int sold = 0;
        for (int left = (int) Math.floor(cargoAtHub); left > 0; ) {
            var station = endpoint(pilot, hub);
            long quote = pilot.campaign().pilotCommodityPrice(hub, ore, false);
            long liquid = stationWallet(pilot, hub);
            int handling = (int) Math.floor(station.handlingCapability().massRateKgPerSecond()
                    * pilot.campaign().coordinator().session().fixedStepSeconds());
            int batch = (int) Math.min(left, Math.min(handling, liquid / Math.max(1, quote)));
            assertTrue(batch > 0, "market can buy: handling=" + handling + " liquid=" + liquid + " quote=" + quote);
            long received = pilot.action("SELL", hub, ore, batch);
            assertEquals(liquid - received, stationWallet(pilot, hub), "the market pays exactly what the pilot receives");
            income += received;
            sold += batch;
            left -= batch;
            pilot.tick();
        }
        assertTrue(income > 0, "ore sells for money");
        assertEquals(moneyBefore + income, pilot.player().walletMilliCredits());
        assertEquals(cargoAtHub - sold, cargoKg(pilot, ore), 1e-6);
        assertEquals(stockBefore + sold, endpoint(pilot, hub).storage().commodityMassKg(ore), 1e-6);
        phase("return " + returnTicks + " ticks and sale of " + sold + " kg", t, pilot);

        // 5. Pay for reaction mass at the same berth.
        t = System.nanoTime();
        var fitted = pilot.engineering();
        var binding = Stage22ShipConsumableCatalogLoader.loadDefault().getBindings().stream()
                .filter(b -> fitted.fit.installedModules().stream().anyMatch(m -> m.moduleId().equals(b.moduleId())))
                .findFirst().orElseThrow();
        String mount = fitted.fit.installedModules().stream().filter(m -> m.moduleId().equals(binding.moduleId()))
                .findFirst().orElseThrow().mountId();
        double tankBefore = pilot.reactionMassKg();
        double waterBefore = endpoint(pilot, hub).storage().commodityMassKg(binding.commodityId());
        long walletBeforeFuel = pilot.player().walletMilliCredits();
        long stationBeforeFuel = stationWallet(pilot, hub);
        long fuelCost = pilot.action("BUY_CONSUMABLE", binding.id(), mount, 1);
        assertTrue(fuelCost < 0, "reaction mass is paid");
        assertEquals(walletBeforeFuel + fuelCost, pilot.player().walletMilliCredits());
        assertEquals(stationBeforeFuel - fuelCost, stationWallet(pilot, hub), "the station receives the fuel payment");
        assertEquals(tankBefore + 1, pilot.reactionMassKg(), 1e-6);
        assertEquals(waterBefore - 1, endpoint(pilot, hub).storage().commodityMassKg(binding.commodityId()), 1e-6);
        phase("refuel", t, pilot);

        pilot.saveAndReload();
        phase("total", t0, pilot);
    }

    private static void phase(String name, long startNanos, ScenarioPilot pilot) {
        System.out.printf("SCENARIO %-60s %6.1fs  tick=%d%n", name, (System.nanoTime() - startNanos) / 1e9,
                pilot.world().getAuthoritativeWorldTick());
    }

    /** Ids of the objects the pilot's system map shows, from the same model the game draws. */
    private static java.util.Set<String> mapObjectIds(ScenarioPilot pilot) {
        var campaign = pilot.campaign();
        var coordinator = campaign.coordinator();
        var model = new GeneratedWorldUiModel(coordinator.rootSeed(), coordinator.runtime(), coordinator.content(),
                com.spacesim.ui.GeneratedCampaignCarrierUi.source(campaign), () -> campaign.playerState().orElse(null));
        return model.capture().localObjects().stream().map(com.spacesim.ui.GeneratedWorldUiSnapshot.LocalObjectView::stableId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static com.spacesim.persistence.Stage20FreightRuntime freight(ScenarioPilot pilot) {
        return pilot.campaign().coordinator().runtime().freight();
    }

    private static com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.RuntimeEndpoint endpoint(ScenarioPilot pilot, String stationId) {
        return pilot.campaign().coordinator().runtime().infrastructure().endpoint(stationId);
    }

    private static long treasury(ScenarioPilot pilot, String factionId) {
        return pilot.world().findFactionEconomicState(factionId).orElseThrow().treasuryMilliCredits();
    }

    private static long stationWallet(ScenarioPilot pilot, String stationId) {
        var ref = pilot.campaign().pilotMarketReference(stationId).orElseThrow();
        return pilot.world().findSession(ref.systemId()).orElseThrow().getEntityRegistry().require(ref.entityId())
                .getComponent(WalletComponent.class).getBalanceMilliCredits();
    }

    private static double remainingSourceKg(ScenarioPilot pilot, String sourceId) {
        return pilot.campaign().coordinator().runtime().industry().sourceOutposts().sources().sources().stream()
                .filter(s -> s.sourceId().equals(sourceId)).findFirst().orElseThrow()
                .sourceState().remainingAccessibleMassKg();
    }

    private static double cargoKg(ScenarioPilot pilot, String commodity) {
        return freight(pilot).findFreighter(pilot.fleetId()).orElseThrow().cargoStorage()
                .commodityMassByIdKg().getOrDefault(commodity, 0d);
    }

    private static double oreKg(ScenarioPilot pilot) {
        return freight(pilot).findFreighter(pilot.fleetId()).orElseThrow().cargoStorage().commodityMassByIdKg().entrySet().stream()
                .filter(e -> e.getKey().startsWith("commodity.feedstock.")).mapToDouble(e -> e.getValue()).sum();
    }

    private static String oreCommodity(ScenarioPilot pilot) {
        return freight(pilot).findFreighter(pilot.fleetId()).orElseThrow().cargoStorage().commodityMassByIdKg().entrySet().stream()
                .filter(e -> e.getKey().startsWith("commodity.feedstock.") && e.getValue() >= 1)
                .max(java.util.Map.Entry.comparingByValue()).orElseThrow().getKey();
    }
}
