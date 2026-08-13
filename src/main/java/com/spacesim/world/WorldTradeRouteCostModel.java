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
 * <p>Route risk is modeled as expected loss against purchased cargo value. Tariff exposure follows
 * the existing Stage-8 fiscal semantics instead of inventing a per-transaction tax: purchasing from
 * a foreign-owned market inside another faction's controlled territory increases that supplier's
 * wallet and therefore its future foreign-territory levy base. The adapter estimates that marginal
 * exposure as a route cost; it does not transfer money or change authoritative fiscal accounting.</p>
 */
final class WorldTradeRouteCostModel implements TradeRouteCostModel {
    private static final long BASIS_POINTS_DENOMINATOR = 10_000L;

    private final ContentCatalog contentCatalog;
    private final Map<StarSystemId, FactionStrategicState> controllerBySystem;

    WorldTradeRouteCostModel(ContentCatalog contentCatalog, List<FactionStrategicState> strategies) {
        this.contentCatalog = Objects.requireNonNull(contentCatalog, "ContentCatalog не задан");
        Objects.requireNonNull(strategies, "Faction strategies не заданы");
        Map<StarSystemId, FactionStrategicState> controllers = new HashMap();
        for (FactionStrategicState strategy : strategies) {
            FactionStrategicState value = Objects.requireNonNull(strategy, "FactionStrategicState не задан");
            if (contentCatalog.findFaction(value.factionContentId()) == null) {
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
        Objects.requireNonNull(fleet, "FleetTradeProfile не задан");
        Context route = Objects.requireNonNull(context, "TradeRouteCostModel.Context не задан");
        long riskExposure = basisPointCeil(
                route.purchaseCostMilliCredits(), route.routeRiskBasisPoints());
        if (!route.isGalactic() || route.buyFactionId() < 0) {
            return riskExposure;
        }

        FactionStrategicState controller = controllerBySystem.get(route.buySystemId());
        if (controller == null || controller.foreignTerritoryTariffBasisPoints() <= 0) {
            return riskExposure;
        }
        ContentCatalog.FactionDefinition marketFaction = contentCatalog.findFaction(route.buyFactionId());
        if (marketFaction == null) {
            throw new IllegalArgumentException("Unknown supplier runtime faction: " + route.buyFactionId());
        }
        if (marketFaction.id().equals(controller.factionContentId())) {
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
