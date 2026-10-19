package com.spacesim.campaign;

import com.spacesim.persistence.Stage228GeneratedCampaignPersistentState;
import com.spacesim.player.OwnedStationRef;
import com.spacesim.player.PlayerState;

import java.util.List;

/** Explicit fixture: the same campaign state with the pilot owning one existing station; nothing else changes. */
public final class GeneratedCampaignOwnedStationFixture {
    private GeneratedCampaignOwnedStationFixture() { }

    /**
     * @param campaign started campaign
     * @param stationId existing station with a market
     * @return checkpoint in which the pilot owns that station
     */
    public static Stage228GeneratedCampaignPersistentState owning(Stage228CampaignAuthority campaign, String stationId) {
        var p = campaign.playerState().orElseThrow();
        var ref = campaign.pilotMarketReference(stationId).orElseThrow();
        var owner = new PlayerState(p.walletMilliCredits(), p.factionContentId(), p.reputations(), p.ownedFleetIds(), p.activeFleetId(),
                p.discoveredSystemIds(), p.discoveredObjects(), p.homeSystemId(), p.dockedAt(), p.fleetOrders(), p.threatIntel(),
                p.ownedConstructionProjectIds(), List.of(new OwnedStationRef(ref.systemId(), ref.entityId())));
        return GeneratedCampaignPlayerWorldTransition.composeRuntime(campaign.captureState(), campaign.coordinator().runtime().captureState(), owner);
    }
}
