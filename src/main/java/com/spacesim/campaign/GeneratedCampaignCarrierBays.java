package com.spacesim.campaign;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage21GeneratedMilitaryEngineeringCatalog;
import com.spacesim.content.ship.Stage23RuntimeEngineeringCatalogLoader;
import com.spacesim.world.CarrierWingStrategicReadinessService.CarrierWingAssignment;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.SmallCraftBayResolver;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Current installed carrier bays shared by world progression and personal presentation. */
public final class GeneratedCampaignCarrierBays {
    private GeneratedCampaignCarrierBays() { }

    /**
     * Resolves physical bays without creating equipment, profiles or occupancy.
     * @param campaign live owning authority
     * @param wing existing carrier association
     * @return installed local bays; empty for unavailable or mismatched hosts
     */
    public static List<BayDefinition> resolve(Stage228CampaignAuthority campaign, CarrierWingAssignment wing) {
        var world = campaign.coordinator().runtime().world();
        var fleet = world.findFleet(wing.carrierFleetId()).orElse(null);
        if (fleet == null || fleet.locationKind() != FleetLocationKind.IN_SYSTEM
                || world.findFleetJump(fleet.id()).isPresent()) return List.of();
        var entity = world.findSession(fleet.systemId()).orElseThrow().getEntityRegistry().require(fleet.localEntityId());
        var fitted = entity.getComponent(EngineeringComponent.class);
        var faction = entity.getComponent(FactionComponent.class);
        if (fitted == null || faction == null || world.findFactionStableId(faction.factionId)
                .filter(wing.stableFactionId()::equals).isEmpty()) return List.of();
        for (var catalog : Catalogs.ALL) {
            if (catalog.findHull(fitted.fit.hullId()) != null
                    && fitted.fit.installedModules().stream().allMatch(m -> catalog.findModule(m.moduleId()) != null))
                return new SmallCraftBayResolver(catalog).resolveShipBays(wing.hostStableId(), fitted.fit, fitted.instanceState);
        }
        return List.of();
    }

    /**
     * Projects all associated hosts independently of personal visibility or ownership.
     * @param campaign live owning authority
     * @return deterministic current bay definitions
     */
    public static Map<BayId, BayDefinition> resolveAll(Stage228CampaignAuthority campaign) {
        var result = new TreeMap<BayId, BayDefinition>();
        for (var wing : campaign.carrierWings()) {
            for (var bay : resolve(campaign, wing)) {
                if (result.putIfAbsent(bay.id(), bay) != null)
                    throw new IllegalStateException("Duplicate physical carrier bay: " + bay.id());
            }
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private static final class Catalogs {
        private static final List<ShipEngineeringCatalog> ALL = List.of(
                Stage21GeneratedMilitaryEngineeringCatalog.load(), Stage23RuntimeEngineeringCatalogLoader.loadDefault());
    }
}
