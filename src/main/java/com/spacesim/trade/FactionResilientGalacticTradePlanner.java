package com.spacesim.trade;

import com.spacesim.world.GalacticPath;
import com.spacesim.world.GalacticPathPlanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.IntUnaryOperator;

/**
 * Selects resilience-aware cross-system procurement without replacing ordinary economic planning.
 *
 * <p>The wrapped {@link TradeRoutePlanner} remains authoritative for route feasibility, physical
 * suppliers, prices, tariffs, risk, travel time and profitability. Resilience may first choose a less
 * concentrated real supplier and may then choose a real edge-disjoint jump path. Both choices are
 * restricted to inbound procurement into the trading faction's own physical market and must remain
 * within measured expected-profit willingness-to-pay. No supplier or topology link is invented.</p>
 */
public final class FactionResilientGalacticTradePlanner {
    private final TradeRoutePlanner economicPlanner;
    private final TradeRoutePlanner.ScoringMode scoringMode;
    private final SupplierDiversificationPolicy diversificationPolicy;
    private final GalacticPathPlanner redundancyPathPlanner;
    private final int maxRedundantJumpHops;
    private final IntUnaryOperator routeRiskByJumpCount;
    private final RouteRedundancyPolicy routeRedundancyPolicy;

    /**
     * Creates a supplier-diversification selector with route redundancy disabled.
     *
     * @param economicPlanner ordinary economic route planner
     * @param scoringMode same scoring mode used by the wrapped planner
     * @param diversificationPolicy measured strategic supplier policy
     */
    public FactionResilientGalacticTradePlanner(
            TradeRoutePlanner economicPlanner,
            TradeRoutePlanner.ScoringMode scoringMode,
            SupplierDiversificationPolicy diversificationPolicy) {
        this(
                economicPlanner,
                scoringMode,
                diversificationPolicy,
                null,
                Integer.MAX_VALUE,
                ignored -> 0,
                RouteRedundancyPolicy.none());
    }

    /**
     * Creates a full Stage-17F.5 physical procurement selector.
     *
     * @param economicPlanner ordinary economic route planner
     * @param scoringMode same scoring mode used by the wrapped planner
     * @param diversificationPolicy measured strategic supplier policy
     * @param redundancyPathPlanner physical topology planner used to find edge-disjoint alternatives
     * @param maxRedundantJumpHops maximum allowed hops for a redundant path
     * @param routeRiskByJumpCount deterministic route-risk function for an alternative path
     * @param routeRedundancyPolicy measured strategic route policy
     */
    public FactionResilientGalacticTradePlanner(
            TradeRoutePlanner economicPlanner,
            TradeRoutePlanner.ScoringMode scoringMode,
            SupplierDiversificationPolicy diversificationPolicy,
            GalacticPathPlanner redundancyPathPlanner,
            int maxRedundantJumpHops,
            IntUnaryOperator routeRiskByJumpCount,
            RouteRedundancyPolicy routeRedundancyPolicy) {
        this.economicPlanner = Objects.requireNonNull(economicPlanner, "Economic trade planner not set");
        this.scoringMode = Objects.requireNonNull(scoringMode, "Trade scoring mode not set");
        this.diversificationPolicy = Objects.requireNonNull(
                diversificationPolicy, "Supplier diversification policy not set");
        this.redundancyPathPlanner = redundancyPathPlanner;
        if (maxRedundantJumpHops <= 0) {
            throw new IllegalArgumentException("Maximum redundant jump hops must be positive");
        }
        this.maxRedundantJumpHops = maxRedundantJumpHops;
        this.routeRiskByJumpCount = Objects.requireNonNull(
                routeRiskByJumpCount, "Route risk function not set");
        this.routeRedundancyPolicy = Objects.requireNonNull(
                routeRedundancyPolicy, "Route redundancy policy not set");
    }

    /**
     * Returns the selected real route after bounded resilience comparison.
     *
     * @param fleet immutable fleet planning profile
     * @param opportunities bounded real galactic market opportunities
     * @return selected route or empty when the ordinary planner finds no profitable route
     */
    public Optional<GalacticTradeRoute> findBestGalacticRoute(
            FleetTradeProfile fleet,
            List<GalacticTradeOpportunity> opportunities) {
        return selectBestGalacticRoute(fleet, opportunities).map(Selection::selectedRoute);
    }

    /**
     * Returns the selected route together with explainable resilience diagnostics.
     *
     * @param fleet immutable fleet planning profile
     * @param opportunities bounded real galactic market opportunities
     * @return selection diagnostics or empty when no profitable route exists
     */
    public Optional<Selection> selectBestGalacticRoute(
            FleetTradeProfile fleet,
            List<GalacticTradeOpportunity> opportunities) {
        FleetTradeProfile checkedFleet = Objects.requireNonNull(fleet, "Fleet trade profile not set");
        List<GalacticTradeOpportunity> checkedOpportunities = List.copyOf(
                Objects.requireNonNull(opportunities, "Galactic opportunities not set"));
        GalacticTradeRoute economicBaseline = economicPlanner
                .findBestGalacticRoute(checkedFleet, checkedOpportunities)
                .orElse(null);
        if (economicBaseline == null) {
            return Optional.empty();
        }

        int baselineConsumerFaction = consumerFactionId(economicBaseline, checkedOpportunities);
        if (checkedFleet.factionId() < 0 || baselineConsumerFaction != checkedFleet.factionId()) {
            return Optional.of(Selection.economicOnly(economicBaseline));
        }

        int baselineSupplierFaction = supplierFactionId(economicBaseline, checkedOpportunities);
        SupplierDiversificationPolicy.Assessment baselineAssessment = supplierAssessment(
                checkedFleet, baselineSupplierFaction, economicBaseline.itemId());
        int baselineShare = baselineAssessment.active()
                ? baselineAssessment.supplierShareBasisPoints()
                : 10_000;

        Map<Integer, List<GalacticTradeOpportunity>> opportunitiesBySupplier = new TreeMap<>();
        for (GalacticTradeOpportunity opportunity : checkedOpportunities) {
            GalacticTradeOpportunity value = Objects.requireNonNull(
                    opportunity, "Galactic opportunity not set");
            if (value.itemId() != economicBaseline.itemId()
                    || !sameConsumer(value, economicBaseline)) {
                continue;
            }
            int supplierFactionId = value.supplier().market().factionId();
            opportunitiesBySupplier.computeIfAbsent(supplierFactionId, ignored -> new ArrayList<>())
                    .add(value);
        }

        GalacticTradeRoute selected = economicBaseline;
        int selectedShare = baselineShare;
        long selectedSacrifice = 0L;
        long selectedBudget = baselineAssessment.active()
                ? baselineAssessment.acceptableProfitSacrificeMilliCredits()
                : 0L;
        boolean diversified = false;

        for (Map.Entry<Integer, List<GalacticTradeOpportunity>> entry : opportunitiesBySupplier.entrySet()) {
            SupplierDiversificationPolicy.Assessment candidateAssessment = supplierAssessment(
                    checkedFleet, entry.getKey(), economicBaseline.itemId());
            if (!candidateAssessment.active()
                    || candidateAssessment.supplierShareBasisPoints() >= baselineShare) {
                continue;
            }
            GalacticTradeRoute candidate = economicPlanner
                    .findBestGalacticRoute(checkedFleet, entry.getValue())
                    .orElse(null);
            if (candidate == null) {
                continue;
            }
            long sacrifice = profitSacrificeMilliCredits(economicBaseline, candidate);
            if (sacrifice > candidateAssessment.acceptableProfitSacrificeMilliCredits()) {
                continue;
            }
            if (!diversified
                    || candidateAssessment.supplierShareBasisPoints() < selectedShare
                    || (candidateAssessment.supplierShareBasisPoints() == selectedShare
                    && economicallyBetter(candidate, selected))) {
                selected = candidate;
                selectedShare = candidateAssessment.supplierShareBasisPoints();
                selectedSacrifice = sacrifice;
                selectedBudget = candidateAssessment.acceptableProfitSacrificeMilliCredits();
                diversified = !sameMarketPair(candidate, economicBaseline);
            }
        }

        RouteRedundancyPolicy.Assessment routeAssessment = RouteRedundancyPolicy.Assessment.require(
                routeRedundancyPolicy.assess(checkedFleet, economicBaseline.itemId()));
        boolean redundantRoute = false;
        if (routeAssessment.active() && redundancyPathPlanner != null) {
            GalacticTradeOpportunity selectedOpportunity = findOpportunity(selected, checkedOpportunities);
            GalacticPath alternative = redundancyPathPlanner
                    .findEdgeDisjointAlternative(selected.jumpPath())
                    .orElse(null);
            if (selectedOpportunity != null
                    && alternative != null
                    && alternative.jumpCount() <= maxRedundantJumpHops) {
                int alternativeRisk = routeRiskByJumpCount.applyAsInt(alternative.jumpCount());
                if (alternativeRisk < 0 || alternativeRisk > 10_000) {
                    throw new IllegalStateException("Route risk function returned value outside 0..10000 bps");
                }
                GalacticTradeOpportunity alternativeOpportunity = new GalacticTradeOpportunity(
                        selectedOpportunity.supplier(),
                        selectedOpportunity.consumer(),
                        selectedOpportunity.itemId(),
                        alternative,
                        selectedOpportunity.localTravelDistance(),
                        selectedOpportunity.localTravelSeconds(),
                        alternativeRisk);
                GalacticTradeRoute candidate = economicPlanner
                        .findBestGalacticRoute(checkedFleet, List.of(alternativeOpportunity))
                        .orElse(null);
                if (candidate != null) {
                    long routeBudget = routeAssessment.acceptableProfitSacrificeMilliCredits();
                    long effectiveBudget = diversified
                            ? Math.min(selectedBudget, routeBudget)
                            : routeBudget;
                    long totalSacrifice = profitSacrificeMilliCredits(economicBaseline, candidate);
                    if (totalSacrifice <= effectiveBudget) {
                        selected = candidate;
                        selectedSacrifice = totalSacrifice;
                        selectedBudget = effectiveBudget;
                        redundantRoute = true;
                    }
                }
            }
        }

        return Optional.of(new Selection(
                selected,
                economicBaseline,
                baselineShare,
                selectedShare,
                selectedBudget,
                selectedSacrifice,
                diversified,
                redundantRoute));
    }

    private SupplierDiversificationPolicy.Assessment supplierAssessment(
            FleetTradeProfile fleet,
            int supplierFactionId,
            int itemId) {
        return SupplierDiversificationPolicy.Assessment.require(
                diversificationPolicy.assess(fleet, supplierFactionId, itemId));
    }

    private long profitSacrificeMilliCredits(
            GalacticTradeRoute economicBaseline,
            GalacticTradeRoute candidate) {
        if (scoringMode == TradeRoutePlanner.ScoringMode.GROSS_PROFIT) {
            return Math.max(0L,
                    economicBaseline.netProfitMilliCredits() - candidate.netProfitMilliCredits());
        }
        double baselineRate = economicBaseline.netProfitPerSecond();
        double candidateRate = candidate.netProfitPerSecond();
        if (candidateRate >= baselineRate) {
            return 0L;
        }
        if (Double.isInfinite(baselineRate)) {
            return Double.isInfinite(candidateRate)
                    ? Math.max(0L, economicBaseline.netProfitMilliCredits() - candidate.netProfitMilliCredits())
                    : Long.MAX_VALUE;
        }
        double horizon = Math.max(
                1d,
                Math.max(economicBaseline.expectedDurationSeconds(), candidate.expectedDurationSeconds()));
        double sacrifice = (baselineRate - candidateRate) * horizon;
        if (!Double.isFinite(sacrifice) || sacrifice >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return sacrifice <= 0d ? 0L : (long) Math.ceil(sacrifice);
    }

    private boolean economicallyBetter(GalacticTradeRoute candidate, GalacticTradeRoute current) {
        int primary = scoringMode == TradeRoutePlanner.ScoringMode.GROSS_PROFIT
                ? Long.compare(candidate.netProfitMilliCredits(), current.netProfitMilliCredits())
                : Double.compare(candidate.netProfitPerSecond(), current.netProfitPerSecond());
        if (primary != 0) {
            return primary > 0;
        }
        int netTie = Long.compare(candidate.netProfitMilliCredits(), current.netProfitMilliCredits());
        if (netTie != 0) {
            return netTie > 0;
        }
        int timeTie = Double.compare(candidate.expectedDurationSeconds(), current.expectedDurationSeconds());
        if (timeTie != 0) {
            return timeTie < 0;
        }
        int buySystemTie = candidate.buySystemId().compareTo(current.buySystemId());
        if (buySystemTie != 0) {
            return buySystemTie < 0;
        }
        int buyStationTie = candidate.buyStationId().compareTo(current.buyStationId());
        if (buyStationTie != 0) {
            return buyStationTie < 0;
        }
        int sellSystemTie = candidate.sellSystemId().compareTo(current.sellSystemId());
        if (sellSystemTie != 0) {
            return sellSystemTie < 0;
        }
        int sellStationTie = candidate.sellStationId().compareTo(current.sellStationId());
        if (sellStationTie != 0) {
            return sellStationTie < 0;
        }
        return candidate.amount() > current.amount();
    }

    private static GalacticTradeOpportunity findOpportunity(
            GalacticTradeRoute route,
            List<GalacticTradeOpportunity> opportunities) {
        for (GalacticTradeOpportunity opportunity : opportunities) {
            if (opportunity.itemId() == route.itemId()
                    && opportunity.supplier().systemId().equals(route.buySystemId())
                    && opportunity.supplier().market().id().equals(route.buyStationId())
                    && opportunity.consumer().systemId().equals(route.sellSystemId())
                    && opportunity.consumer().market().id().equals(route.sellStationId())) {
                return opportunity;
            }
        }
        return null;
    }

    private static int supplierFactionId(
            GalacticTradeRoute route,
            List<GalacticTradeOpportunity> opportunities) {
        GalacticTradeOpportunity opportunity = findOpportunity(route, opportunities);
        return opportunity == null ? -1 : opportunity.supplier().market().factionId();
    }

    private static int consumerFactionId(
            GalacticTradeRoute route,
            List<GalacticTradeOpportunity> opportunities) {
        GalacticTradeOpportunity opportunity = findOpportunity(route, opportunities);
        return opportunity == null ? -1 : opportunity.consumer().market().factionId();
    }

    private static boolean sameConsumer(
            GalacticTradeOpportunity opportunity,
            GalacticTradeRoute baseline) {
        return opportunity.consumer().systemId().equals(baseline.sellSystemId())
                && opportunity.consumer().market().id().equals(baseline.sellStationId());
    }

    private static boolean sameMarketPair(GalacticTradeRoute first, GalacticTradeRoute second) {
        return first.itemId() == second.itemId()
                && first.buySystemId().equals(second.buySystemId())
                && first.buyStationId().equals(second.buyStationId())
                && first.sellSystemId().equals(second.sellSystemId())
                && first.sellStationId().equals(second.sellStationId());
    }

    /**
     * Explainable result of one resilience-aware procurement selection.
     *
     * @param selectedRoute route actually selected for execution
     * @param economicBaseline ordinary economic best route before resilience preference
     * @param baselineSupplierShareBasisPoints measured concentration of the economic supplier
     * @param selectedSupplierShareBasisPoints measured concentration of the selected supplier
     * @param acceptableProfitSacrificeMilliCredits effective total resilience willingness-to-pay
     * @param actualProfitSacrificeMilliCredits real final expected-profit sacrifice versus baseline
     * @param diversificationApplied whether a different physical supplier market was selected
     * @param routeRedundancyApplied whether an edge-disjoint physical jump path was selected
     */
    public record Selection(
            GalacticTradeRoute selectedRoute,
            GalacticTradeRoute economicBaseline,
            int baselineSupplierShareBasisPoints,
            int selectedSupplierShareBasisPoints,
            long acceptableProfitSacrificeMilliCredits,
            long actualProfitSacrificeMilliCredits,
            boolean diversificationApplied,
            boolean routeRedundancyApplied) {

        /**
         * Validates one immutable selection diagnostic.
         *
         * @param selectedRoute route actually selected for execution
         * @param economicBaseline ordinary economic best route before resilience preference
         * @param baselineSupplierShareBasisPoints measured concentration of the economic supplier
         * @param selectedSupplierShareBasisPoints measured concentration of the selected supplier
         * @param acceptableProfitSacrificeMilliCredits effective total resilience willingness-to-pay
         * @param actualProfitSacrificeMilliCredits real final expected-profit sacrifice versus baseline
         * @param diversificationApplied whether a different physical supplier market was selected
         * @param routeRedundancyApplied whether an edge-disjoint physical jump path was selected
         */
        public Selection {
            Objects.requireNonNull(selectedRoute, "Selected galactic route not set");
            Objects.requireNonNull(economicBaseline, "Economic baseline route not set");
            requireBasisPoints(baselineSupplierShareBasisPoints, "baselineSupplierShareBasisPoints");
            requireBasisPoints(selectedSupplierShareBasisPoints, "selectedSupplierShareBasisPoints");
            if (acceptableProfitSacrificeMilliCredits < 0L || actualProfitSacrificeMilliCredits < 0L) {
                throw new IllegalArgumentException("Resilience profit values cannot be negative");
            }
            if (actualProfitSacrificeMilliCredits > acceptableProfitSacrificeMilliCredits
                    && (diversificationApplied || routeRedundancyApplied)) {
                throw new IllegalArgumentException("Resilience selection exceeds accepted profit sacrifice");
            }
        }

        private static Selection economicOnly(GalacticTradeRoute economicBaseline) {
            return new Selection(
                    economicBaseline,
                    economicBaseline,
                    10_000,
                    10_000,
                    0L,
                    0L,
                    false,
                    false);
        }

        private static void requireBasisPoints(int value, String label) {
            if (value < 0 || value > 10_000) {
                throw new IllegalArgumentException(label + " must be in range 0..10000");
            }
        }
    }
}
