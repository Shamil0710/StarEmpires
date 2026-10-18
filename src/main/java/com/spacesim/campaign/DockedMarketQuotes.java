package com.spacesim.campaign;

import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.persistence.Stage20FreightPersistentState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;

import java.util.List;
import java.util.Optional;

/**
 * Which commodities the docked pilot can see quotes for, shared by the inspector table and the
 * recorded observations so both use one rule: the ship's hold, the station's handling and its
 * storage must all take the commodity. The ontology is loaded once.
 */
public final class DockedMarketQuotes {
    /** Shared resource ontology, loaded once. */
    public static final Stage18ResourceOntologyCatalog ONTOLOGY = Stage18ResourceOntologyLoader.loadDefault();

    private DockedMarketQuotes() {
    }

    /**
     * @param hold the pilot's active ship hold
     * @param endpoint the station the ship is docked at
     * @return commodities whose quotes are disclosed, in ontology order
     */
    public static List<Stage18ResourceOntologyCatalog.CommodityDefinition> eligible(
            Stage20FreightPersistentState.FreighterState hold, Stage20GeneratedWorldRuntimeBridge.RuntimeEndpoint endpoint) {
        return ONTOLOGY.getCommodities().stream()
                .filter(definition -> hold.cargoStorage().capacityByStorageClassKg().containsKey(definition.storageClassId())
                        && endpoint.handlingCapability().supportedStorageClassIds().contains(definition.storageClassId())
                        && endpoint.storage().snapshot().capacityByStorageClassKg().containsKey(definition.storageClassId()))
                .toList();
    }

    /**
     * @param campaign current campaign
     * @return the local market the active ship is docked at, or empty when not docked at one
     */
    public static Optional<Stage20GeneratedWorldRuntimeBridge.RuntimeEndpoint> dockedEndpoint(Stage228CampaignAuthority campaign) {
        var player = campaign.playerState().orElse(null);
        if (player == null || player.activeFleetId() == null || !player.docked()) return Optional.empty();
        var runtime = campaign.coordinator().runtime();
        var fleet = runtime.world().findFleet(player.activeFleetId()).orElse(null);
        if (fleet == null || fleet.locationKind() != com.spacesim.world.FleetLocationKind.IN_SYSTEM) return Optional.empty();
        for (var endpoint : runtime.infrastructure().endpoints()) {
            if (!endpoint.systemId().equals(fleet.systemId())) continue;
            var ref = campaign.pilotMarketReference(endpoint.stationId()).orElse(null);
            if (ref != null && ref.equals(player.dockedAt())) return Optional.of(endpoint);
        }
        return Optional.empty();
    }
}
