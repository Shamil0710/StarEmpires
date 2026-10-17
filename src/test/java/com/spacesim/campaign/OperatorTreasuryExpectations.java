package com.spacesim.campaign;

import com.spacesim.components.FactionComponent;

/** Expected new-game operator treasury: a multiple of the opening working capital of the operator's markets. */
final class OperatorTreasuryExpectations {
    private OperatorTreasuryExpectations() { }

    /** @return opening treasury granted to {@code faction} by the pilot start of {@code campaign} */
    static long openingTreasury(Stage228CampaignAuthority campaign, String faction) {
        var world = campaign.coordinator().runtime().world();
        long markets = campaign.coordinator().runtime().infrastructure().endpoints().stream()
                .flatMap(e -> campaign.pilotMarketReference(e.stationId()).stream())
                .map(ref -> world.findSession(ref.systemId()).orElseThrow().getEntityRegistry().require(ref.entityId())
                        .getComponent(FactionComponent.class))
                .filter(legal -> legal != null && world.findFactionStableId(legal.factionId).orElseThrow().equals(faction))
                .count();
        return markets * Stage228CampaignAuthority.PILOT_MARKET_INITIAL_LIQUIDITY
                * Stage228CampaignAuthority.OPERATOR_TREASURY_MARKET_MULTIPLE;
    }
}
