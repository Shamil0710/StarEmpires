package com.spacesim.world;

import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.EntityId;
import com.spacesim.trade.FleetTradeProfile;
import com.spacesim.trade.TradeRouteCostModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldTradeRouteCostModelTest {
    private static final StarSystemId A = new StarSystemId(1L);
    private static final StarSystemId B = new StarSystemId(2L);
    private final ContentCatalog catalog = ContentCatalogLoader.loadDefault();

    @Test
    void combinesCargoRiskWithForeignSupplierFiscalExposure() {
        FactionStrategicState miners = strategy("faction.miners", A, 1_000);
        WorldTradeRouteCostModel model = new WorldTradeRouteCostModel(catalog, List.of(miners));
        TradeRouteCostModel.Context context = galacticContext(1, 500);

        long cost = model.estimateCostMilliCredits(fleet(), context);

        assertEquals(15_000L, cost);
    }

    @Test
    void ownSupplierMarketAvoidsForeignTariffExposure() {
        FactionStrategicState miners = strategy("faction.miners", A, 1_000);
        WorldTradeRouteCostModel model = new WorldTradeRouteCostModel(catalog, List.of(miners));

        assertEquals(5_000L, model.estimateCostMilliCredits(fleet(), galacticContext(2, 500)));
    }

    @Test
    void localContextHasNoWorldPolicyCost() {
        WorldTradeRouteCostModel model = new WorldTradeRouteCostModel(catalog, List.of());
        TradeRouteCostModel.Context local = new TradeRouteCostModel.Context(
                new EntityId(10L), new EntityId(11L), 1, 2,
                Constants.ITEM_FOOD, 10, 100_000L, 200_000L, 50f, 3d);

        assertEquals(0L, model.estimateCostMilliCredits(fleet(), local));
    }

    private TradeRouteCostModel.Context galacticContext(int supplierFactionId, int riskBasisPoints) {
        GalacticPath path = new GalacticPath(List.of(A, B), 20L, 2d, 100d);
        return new TradeRouteCostModel.Context(
                new EntityId(10L),
                new EntityId(11L),
                supplierFactionId,
                2,
                Constants.ITEM_FOOD,
                10,
                100_000L,
                200_000L,
                0f,
                2d,
                A,
                B,
                path,
                riskBasisPoints);
    }

    private static FactionStrategicState strategy(String factionId, StarSystemId systemId, int tariffBasisPoints) {
        return new FactionStrategicState(
                factionId,
                -100,
                List.of(),
                List.of(systemId),
                0,
                tariffBasisPoints,
                List.of(),
                List.of(),
                List.of());
    }

    private static FleetTradeProfile fleet() {
        return new FleetTradeProfile(
                0f, 0f, 20f, 1_000_000L, 100, 0, 100,
                -1, false, null, 2,
                new int[Constants.MAX_ITEMS], new float[Constants.MAX_FACTIONS]);
    }
}
