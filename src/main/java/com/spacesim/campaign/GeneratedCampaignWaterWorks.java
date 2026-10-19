package com.spacesim.campaign;

import com.spacesim.components.FactionComponent;
import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.economy.WaterWorks;

import java.util.Comparator;

/**
 * Once per game hour, every operated NPC depot (a station whose design includes volatile processing)
 * with ice deposits in its own system extracts ice from them, moves it into its store and purifies it
 * into water ({@link WaterWorks}), up to {@link WaterWorks#WATER_PER_HOUR_KG} and while its water is
 * below half of its tank capacity. The ice leaves finite deposits; no money moves. A pure function of
 * the tick and the saved state, so it needs no state of its own.
 */
final class GeneratedCampaignWaterWorks {
    private static final String LIQUID = "storage.liquid_tank";
    private static final double HOUR_SECONDS = 3600d;

    private GeneratedCampaignWaterWorks() { }

    static void review(Stage228CampaignAuthority campaign, long tick) {
        long interval = Math.round(HOUR_SECONDS / campaign.coordinator().session().fixedStepSeconds());
        if (tick <= 0 || tick % interval != 0) return;
        var runtime = campaign.coordinator().runtime();
        var archetypes = Stage18StationInfrastructureCatalogLoader.loadDefault();
        for (var depot : runtime.infrastructure().endpoints()) {
            var design = archetypes.findArchetype(depot.stationArchetypeId());
            if (design == null || !design.installedFacilityDefinitionIds().contains(WaterWorks.FACILITY)
                    || campaign.ownsProductionStation(depot.stationId()) || !operated(campaign, depot.stationId())) continue;
            Double tanks = depot.storage().snapshot().capacityByStorageClassKg().get(LIQUID);
            if (tanks == null || tanks <= 0) continue;
            double wanted = Math.min(WaterWorks.WATER_PER_HOUR_KG, Math.min(tanks / 2 - depot.storage().commodityMassKg(WaterWorks.WATER),
                    depot.storage().remainingCapacityKg(LIQUID)));
            if (wanted < 1) continue;
            double ice = 0;
            double iceWanted = wanted / WaterWorks.waterPerIceKg();
            var sites = runtime.industry().sourceOutposts().outposts().stream()
                    .filter(o -> o.site().systemId().equals(depot.systemId())
                            && o.source().sourceState().outputCommodityId().equals(WaterWorks.ICE))
                    .sorted(Comparator.comparing(o -> o.site().siteId())).toList();
            for (var site : sites) {
                if (iceWanted - ice < 1) break;
                double gross = com.spacesim.simulation.OutpostExtractionBudget.feasibleSourceMassKg(site, depot, iceWanted - ice, HOUR_SECONDS);
                if (gross < 1) continue;
                var extracted = runtime.extract(site.site().siteId(), gross, HOUR_SECONDS);
                if (!extracted.committed() || extracted.outputMassStoredKg() <= 0) continue;
                var moved = runtime.transferOutpostToStation(site.site().siteId(), depot.stationId(),
                        extracted.outputMassStoredKg(), HOUR_SECONDS);
                if (moved.transferred()) ice += moved.transferredMassKg();
            }
            if (ice > 0) WaterWorks.purify(depot.storage(), ice);
        }
    }

    /** @return whether a faction with a treasury operates the station's market */
    private static boolean operated(Stage228CampaignAuthority campaign, String stationId) {
        var ref = campaign.pilotMarketReference(stationId).orElse(null);
        if (ref == null) return false;
        var world = campaign.coordinator().runtime().world();
        var legal = world.findSession(ref.systemId()).orElseThrow().getEntityRegistry().require(ref.entityId())
                .getComponent(FactionComponent.class);
        return legal != null && world.findFactionStableId(legal.factionId).flatMap(world::findFactionEconomicState).isPresent();
    }
}
