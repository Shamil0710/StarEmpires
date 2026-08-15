package com.spacesim.world;

import com.spacesim.content.ContentCatalog;
import com.spacesim.trade.FleetTradeProfile;
import com.spacesim.trade.TradeRouteCostModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stage-10C world-policy adapter for the existing trade-route cost seam.
 *
 * <p>Route risk is modeled as expected loss against purchased cargo value. Tariff exposure still
 * follows the Stage-8 territorial fiscal levy semantics: it estimates future station-wallet levy
 * exposure and does not invent a transaction tax or transfer money. Stage 17E resolves faction
 * ownership through the unified authored + world-defined identity directory.</p>
 */
final class WorldTradeRouteCostModel implements TradeRouteCostModel {
    private static final long BASIS_POINTS_DENOMINATOR = 10_000L;

    private final FactionIdentityResolver identities;
    private final Map<StarSystemId, FactionStrategicState> controllerBySystem;

    /** Source-compatible authored-only constructor. */
    WorldTradeRouteCostModel(ContentCatalog contentCatalog, List<FactionStrategicState> strategies) {
        this(
                FactionIdentityResolver.createDefault(
                        Objects.requireNonNull(contentCatalog, "ContentCatalog not set"),
                        List.of()),
                strategies);
    }

    /** Unified identity constructor used by the world runtime. */
    WorldTradeRouteCostModel(FactionIdentityResolver identities, List<FactionStrategicState> strategies) {
        this.identities = Objects.requireNonNull(identities, "FactionIdentityResolver not set");
        Objects.requireNonNull(strategies, "Faction strategies not set");
        Map<StarSystemId, FactionStrategicState> controllers = new HashMap<>();
        for (FactionStrategicState strategy : strategies) {
            FactionStrategicState value = Objects.requireNonNull(strategy, "FactionStrategicState not set");
            if (identities.runtimeId(value.factionContentId()).isEmpty()) {
                throw new IllegalArgumentException("Unknown strategic faction: " + value.factionContentId());
            }
            for (StarSystemId systemId : value.controlledSystems()) {
                if (controllers.putIfAbsent(systemId, value) != null) {
                    throw new IllegalArgumentException("Multiple factions control StarSystem: " + systemId);
                }
            }
        }
        this.controllerBySystem = Map.copyOf(controllers);
    }

    @Override
    public long estimateCostMilliCredits(FleetTradeProfile fleet, Context context) {
        Objects.requireNonNull(fleet, "FleetTradeProfile not set");
        Context route = Objects.requireNonNull(context, "TradeRouteCostModel.Context not set");
        long riskExposure = basisPointCeil(
                route.purchaseCostMilliCredits(), route.routeRiskBasisPoints());
        if (!route.isGalactic() || route.buyFactionId() < 0) {
            return riskExposure;
        }

        FactionStrategicState controller = controllerBySystem.get(route.buySystemId());
        if (controller == null || controller.foreignTerritoryTariffBasisPoints() <= 0) {
            return riskExposure;
        }
        String marketFactionId = identities.stableId(route.buyFactionId()).orElseThrow(
                () -> new IllegalArgumentException("Unknown supplier runtime faction: " + route.buyFactionId()));
        if (marketFactionId.equals(controller.factionContentId())) {
            return riskExposure;
        }
        long tariffExposure = basisPointCeil(
                route.purchaseCostMilliCredits(), controller.foreignTerritoryTariffBasisPoints());
        return safeAdd(riskExposure, tariffExposure);
    }

    private static long basisPointCeil(long value, int basisPoints) {
        if (value <= 0L || basisPoints <= 0) {
            return 0L;
        }
        long whole = Math.multiplyExact(value / BASIS_POINTS_DENOMINATOR, basisPoints);
        long remainderProduct = (value % BASIS_POINTS_DENOMINATOR) * basisPoints;
        long remainder = remainderProduct / BASIS_POINTS_DENOMINATOR;
        if (remainderProduct % BASIS_POINTS_DENOMINATOR != 0L) {
            remainder = Math.addExact(remainder, 1L);
        }
        return safeAdd(whole, remainder);
    }

    private static long safeAdd(long first, long second) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("World trade route cost overflow", exception);
        }
    }
}
