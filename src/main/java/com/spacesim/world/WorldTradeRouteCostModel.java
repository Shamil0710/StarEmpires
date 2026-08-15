package com.spacesim.world;

import com.spacesim.content.ContentCatalog;
import com.spacesim.trade.FleetTradeProfile;
import com.spacesim.trade.TradeRouteCostModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * World-policy adapter for the common trade-route cost seam.
 *
 * <p>Stage-10 route risk and the legacy Stage-8 territorial station levy remain separate from the
 * Stage-17E.4 transaction/customs tariff. The planner sees the same effective customs rate used by
 * live settlement, including domestic trade and active treaty exemptions, before choosing a route.
 * This model only estimates cost; authoritative money moves exclusively at transaction time.</p>
 */
final class WorldTradeRouteCostModel implements TradeRouteCostModel {
    private static final long BASIS_POINTS_DENOMINATOR = 10_000L;

    private final FactionIdentityResolver identities;
    private final Supplier<List<FactionStrategicState>> strategies;
    private final Supplier<List<FactionDiplomacyState>> diplomacyStates;
    private final LongSupplier worldTick;

    /** Source-compatible authored-only constructor with neutral customs policy. */
    WorldTradeRouteCostModel(ContentCatalog contentCatalog, List<FactionStrategicState> strategies) {
        this(
                FactionIdentityResolver.createDefault(
                        Objects.requireNonNull(contentCatalog, "ContentCatalog not set"),
                        List.of()),
                () -> List.copyOf(Objects.requireNonNull(strategies, "Faction strategies not set")),
                () -> neutralDiplomacy(strategies),
                () -> 0L);
    }

    /** Unified identity constructor with neutral customs policy retained for compatibility. */
    WorldTradeRouteCostModel(FactionIdentityResolver identities, List<FactionStrategicState> strategies) {
        this(
                identities,
                () -> List.copyOf(Objects.requireNonNull(strategies, "Faction strategies not set")),
                () -> neutralDiplomacy(strategies),
                () -> 0L);
    }

    /**
     * Creates the live world cost model.
     *
     * @param identities authored + world-defined faction identity resolver
     * @param strategies live strategic-state supplier
     * @param diplomacyStates live diplomacy-state supplier
     * @param worldTick authoritative world-tick supplier
     */
    WorldTradeRouteCostModel(
            FactionIdentityResolver identities,
            Supplier<List<FactionStrategicState>> strategies,
            Supplier<List<FactionDiplomacyState>> diplomacyStates,
            LongSupplier worldTick) {
        this.identities = Objects.requireNonNull(identities, "FactionIdentityResolver not set");
        this.strategies = Objects.requireNonNull(strategies, "Faction strategy supplier not set");
        this.diplomacyStates = Objects.requireNonNull(diplomacyStates, "Faction diplomacy supplier not set");
        this.worldTick = Objects.requireNonNull(worldTick, "World tick supplier not set");
    }

    @Override
    public long estimateCostMilliCredits(FleetTradeProfile fleet, Context context) {
        FleetTradeProfile checkedFleet = Objects.requireNonNull(fleet, "FleetTradeProfile not set");
        Context route = Objects.requireNonNull(context, "TradeRouteCostModel.Context not set");
        long result = basisPointCeil(route.purchaseCostMilliCredits(), route.routeRiskBasisPoints());
        String traderFactionId = stableFactionOrNull(checkedFleet.factionId(), "trader");
        List<FactionDiplomacyState> diplomacy = diplomacyStates.get();
        long tick = worldTick.getAsLong();

        if (route.buyFactionId() >= 0 && route.purchaseCostMilliCredits() > 0L) {
            String marketOwner = stableFactionOrNull(route.buyFactionId(), "supplier");
            CustomsTariffResolver.Decision customs = CustomsTariffResolver.evaluate(
                    diplomacy, marketOwner, traderFactionId, tick);
            result = safeAdd(result, basisPointCeil(
                    route.purchaseCostMilliCredits(), customs.basisPoints()));
        }
        if (route.sellFactionId() >= 0 && route.saleRevenueMilliCredits() > 0L) {
            String marketOwner = stableFactionOrNull(route.sellFactionId(), "consumer");
            CustomsTariffResolver.Decision customs = CustomsTariffResolver.evaluate(
                    diplomacy, marketOwner, traderFactionId, tick);
            result = safeAdd(result, basisPointCeil(
                    route.saleRevenueMilliCredits(), customs.basisPoints()));
        }

        if (!route.isGalactic() || route.buyFactionId() < 0) {
            return result;
        }
        Map<StarSystemId, FactionStrategicState> controllerBySystem = indexControllers(strategies.get());
        FactionStrategicState controller = controllerBySystem.get(route.buySystemId());
        if (controller == null || controller.foreignTerritoryTariffBasisPoints() <= 0) {
            return result;
        }
        String marketFactionId = stableFactionOrNull(route.buyFactionId(), "supplier");
        if (marketFactionId.equals(controller.factionContentId())) {
            return result;
        }
        return safeAdd(result, basisPointCeil(
                route.purchaseCostMilliCredits(), controller.foreignTerritoryTariffBasisPoints()));
    }

    private String stableFactionOrNull(int runtimeFactionId, String label) {
        if (runtimeFactionId < 0) {
            return null;
        }
        return identities.stableId(runtimeFactionId).orElseThrow(
                () -> new IllegalArgumentException("Unknown " + label + " runtime faction: " + runtimeFactionId));
    }

    private static Map<StarSystemId, FactionStrategicState> indexControllers(
            List<FactionStrategicState> strategies) {
        Objects.requireNonNull(strategies, "Faction strategies not set");
        Map<StarSystemId, FactionStrategicState> controllers = new HashMap<>();
        for (FactionStrategicState strategy : strategies) {
            FactionStrategicState value = Objects.requireNonNull(strategy, "FactionStrategicState not set");
            for (StarSystemId systemId : value.controlledSystems()) {
                if (controllers.putIfAbsent(systemId, value) != null) {
                    throw new IllegalArgumentException("Multiple factions control StarSystem: " + systemId);
                }
            }
        }
        return Map.copyOf(controllers);
    }

    private static List<FactionDiplomacyState> neutralDiplomacy(List<FactionStrategicState> strategies) {
        return Objects.requireNonNull(strategies, "Faction strategies not set").stream()
                .map(strategy -> FactionDiplomacyState.neutral(strategy.factionContentId()))
                .sorted()
                .toList();
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
