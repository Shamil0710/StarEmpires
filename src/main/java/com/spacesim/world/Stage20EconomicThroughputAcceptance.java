package com.spacesim.world;

import com.spacesim.world.Stage20EconomicBootstrapValidator.BootstrapRequirementProfile;
import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteAssessment;
import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteEvaluator;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyKey;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyThroughputReport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Quantitative Stage-20E essential-supply acceptance over physical throughput closure.
 *
 * <p>This gate strengthens the structural bootstrap validator by requiring each essential commodity
 * to have a producer whose non-reserved theoretical supply capacity and physical route throughput
 * both satisfy the injected demand threshold. It still does not model simultaneous multi-commodity
 * fleet allocation; that limitation remains explicit in the source throughput report.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20EconomicThroughputAcceptance {
    private Stage20EconomicThroughputAcceptance() {
        throw new AssertionError("No instances");
    }

    /** Stable rejection cause for one required delivered commodity. */
    public enum FailureReason {
        /** Throughput closure contains no resolved producer for this commodity. */ NO_RESOLVED_PRODUCER,
        /** Producer capacity exists, but every physical route is absent or exceeds the time boundary. */ NO_FEASIBLE_ROUTE,
        /** Physical routes exist, but producer/route throughput is below the required rate. */ INSUFFICIENT_THROUGHPUT
    }

    /**
     * Successful quantitative requirement evidence.
     *
     * @param startSystemId evaluated start/consumer system
     * @param commodityId required commodity
     * @param producerSystemId selected supplier system
     * @param producerCapacityKgPerSecond non-reserved producer upper bound
     * @param routeCapacityKgPerSecond physical freight-route throughput
     * @param deliveredCapacityKgPerSecond minimum of producer and route capacity
     * @param requiredKgPerSecond injected requirement
     * @param headroomKgPerSecond delivered minus required
     * @param route physical selected route assessment
     */
    public record RequirementEvidence(
            StarSystemId startSystemId,
            String commodityId,
            StarSystemId producerSystemId,
            double producerCapacityKgPerSecond,
            double routeCapacityKgPerSecond,
            double deliveredCapacityKgPerSecond,
            double requiredKgPerSecond,
            double headroomKgPerSecond,
            RouteAssessment route) {
        /** Validates one immutable evidence row. */
        public RequirementEvidence {
            Objects.requireNonNull(startSystemId, "startSystemId");
            commodityId = requireText(commodityId, "commodityId");
            Objects.requireNonNull(producerSystemId, "producerSystemId");
            requirePositiveFinite(producerCapacityKgPerSecond, "producerCapacityKgPerSecond");
            requirePositiveFinite(routeCapacityKgPerSecond, "routeCapacityKgPerSecond");
            requirePositiveFinite(deliveredCapacityKgPerSecond, "deliveredCapacityKgPerSecond");
            requirePositiveFinite(requiredKgPerSecond, "requiredKgPerSecond");
            if (!Double.isFinite(headroomKgPerSecond) || headroomKgPerSecond < -1e-9d) {
                throw new IllegalArgumentException("accepted headroomKgPerSecond must be non-negative and finite");
            }
            Objects.requireNonNull(route, "route");
        }
    }

    /**
     * One deterministic quantitative rejection row.
     *
     * @param startSystemId evaluated start/consumer system
     * @param commodityId required commodity
     * @param reason stable rejection reason
     * @param detail deterministic diagnostic detail
     */
    public record RequirementFailure(
            StarSystemId startSystemId,
            String commodityId,
            FailureReason reason,
            String detail) {
        /** Validates one immutable failure row. */
        public RequirementFailure {
            Objects.requireNonNull(startSystemId, "startSystemId");
            commodityId = requireText(commodityId, "commodityId");
            Objects.requireNonNull(reason, "reason");
            detail = requireText(detail, "detail");
        }
    }

    /**
     * Machine-readable throughput acceptance report.
     *
     * @param accepted true only when no requirement failed
     * @param requirementProfileVersion exact requirement profile version
     * @param supplyProfileVersion exact theoretical supply profile version
     * @param evidence accepted requirement evidence
     * @param failures rejection diagnostics
     */
    public record AcceptanceReport(
            boolean accepted,
            String requirementProfileVersion,
            String supplyProfileVersion,
            List<RequirementEvidence> evidence,
            List<RequirementFailure> failures) {
        /** Validates and freezes one deterministic acceptance report. */
        public AcceptanceReport {
            requirementProfileVersion = requireText(requirementProfileVersion, "requirementProfileVersion");
            supplyProfileVersion = requireText(supplyProfileVersion, "supplyProfileVersion");
            Objects.requireNonNull(evidence, "evidence");
            Objects.requireNonNull(failures, "failures");
            ArrayList<RequirementEvidence> evidenceCopy = new ArrayList<>(evidence);
            ArrayList<RequirementFailure> failureCopy = new ArrayList<>(failures);
            evidenceCopy.sort(Comparator.comparing(RequirementEvidence::startSystemId)
                    .thenComparing(RequirementEvidence::commodityId));
            failureCopy.sort(Comparator.comparing(RequirementFailure::startSystemId)
                    .thenComparing(RequirementFailure::commodityId)
                    .thenComparing(value -> value.reason().name()));
            evidence = List.copyOf(evidenceCopy);
            failures = List.copyOf(failureCopy);
            if (accepted != failures.isEmpty()) {
                throw new IllegalArgumentException("accepted must equal failures.isEmpty()");
            }
        }
    }

    /**
     * Evaluates essential delivered-throughput requirements for each start system.
     *
     * @param topology authoritative explicit neighbor topology
     * @param supply theoretical non-reserved supply closure
     * @param startSystems start/consumer systems to check
     * @param requirements calibrated essential commodity requirements
     * @param routes physical route evaluator
     * @return quantitative acceptance report
     */
    public static AcceptanceReport validate(
            GalaxyTopology topology,
            SupplyThroughputReport supply,
            Set<StarSystemId> startSystems,
            BootstrapRequirementProfile requirements,
            RouteEvaluator routes) {
        GalaxyTopology checkedTopology = Objects.requireNonNull(topology, "topology");
        SupplyThroughputReport checkedSupply = Objects.requireNonNull(supply, "supply");
        BootstrapRequirementProfile checkedRequirements = Objects.requireNonNull(requirements, "requirements");
        RouteEvaluator checkedRoutes = Objects.requireNonNull(routes, "routes");
        Objects.requireNonNull(startSystems, "startSystems");
        TreeSet<StarSystemId> starts = new TreeSet<>(startSystems);
        if (starts.isEmpty() || starts.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("startSystems must be non-empty and contain no nulls");
        }
        for (StarSystemId start : starts) {
            if (checkedTopology.findSystem(start).isEmpty()) {
                throw new IllegalArgumentException("start system is outside topology: " + start);
            }
        }

        ArrayList<RequirementEvidence> evidence = new ArrayList<>();
        ArrayList<RequirementFailure> failures = new ArrayList<>();
        for (StarSystemId start : starts) {
            for (CommodityRequirement requirement : checkedRequirements.essentialCommodities()) {
                Selection selection = select(
                        checkedTopology, checkedSupply, start, requirement, checkedRoutes);
                if (selection.evidence() != null) {
                    evidence.add(selection.evidence());
                } else {
                    failures.add(new RequirementFailure(
                            start, requirement.commodityId(), selection.failureReason(), selection.detail()));
                }
            }
        }
        return new AcceptanceReport(
                failures.isEmpty(),
                checkedRequirements.version(),
                checkedSupply.profileVersion(),
                evidence,
                failures);
    }

    private static Selection select(
            GalaxyTopology topology,
            SupplyThroughputReport supply,
            StarSystemId start,
            CommodityRequirement requirement,
            RouteEvaluator routes) {
        ArrayList<Map.Entry<SupplyKey, Double>> producers = new ArrayList<>();
        for (Map.Entry<SupplyKey, Double> entry : supply.capacityKgPerSecondBySupply().entrySet()) {
            if (entry.getKey().commodityId().equals(requirement.commodityId())) {
                producers.add(entry);
            }
        }
        producers.sort(Map.Entry.comparingByKey());
        if (producers.isEmpty()) {
            return Selection.failure(
                    FailureReason.NO_RESOLVED_PRODUCER,
                    "No resolved producer/export capacity exists in the theoretical throughput closure");
        }

        RequirementEvidence best = null;
        boolean hadFeasibleTimeRoute = false;
        double bestObservedDelivered = 0d;
        for (Map.Entry<SupplyKey, Double> producer : producers) {
            Optional<RouteAssessment> maybe = routes.assess(producer.getKey().systemId(), start);
            if (maybe.isEmpty()) {
                continue;
            }
            RouteAssessment route = validateRoute(
                    topology, producer.getKey().systemId(), start, maybe.orElseThrow());
            if (route.travelTimeS() > requirement.maxSupplierRouteTimeS()) {
                continue;
            }
            hadFeasibleTimeRoute = true;
            double delivered = Math.min(producer.getValue(), route.sustainableCargoThroughputKgPerSecond());
            bestObservedDelivered = Math.max(bestObservedDelivered, delivered);
            if (delivered + 1e-9d < requirement.minSupplierThroughputKgPerSecond()) {
                continue;
            }
            RequirementEvidence candidate = new RequirementEvidence(
                    start,
                    requirement.commodityId(),
                    producer.getKey().systemId(),
                    producer.getValue(),
                    route.sustainableCargoThroughputKgPerSecond(),
                    delivered,
                    requirement.minSupplierThroughputKgPerSecond(),
                    delivered - requirement.minSupplierThroughputKgPerSecond(),
                    route);
            if (best == null
                    || candidate.route().travelTimeS() < best.route().travelTimeS()
                    || (candidate.route().travelTimeS() == best.route().travelTimeS()
                    && candidate.deliveredCapacityKgPerSecond() > best.deliveredCapacityKgPerSecond())
                    || (candidate.route().travelTimeS() == best.route().travelTimeS()
                    && candidate.deliveredCapacityKgPerSecond() == best.deliveredCapacityKgPerSecond()
                    && candidate.producerSystemId().compareTo(best.producerSystemId()) < 0)) {
                best = candidate;
            }
        }
        if (best != null) {
            return Selection.success(best);
        }
        if (hadFeasibleTimeRoute) {
            return Selection.failure(
                    FailureReason.INSUFFICIENT_THROUGHPUT,
                    "Best physically deliverable throughput=" + bestObservedDelivered
                            + " kg/s, required=" + requirement.minSupplierThroughputKgPerSecond() + " kg/s");
        }
        return Selection.failure(
                FailureReason.NO_FEASIBLE_ROUTE,
                "Resolved producers exist but no explicit neighbor path satisfies the route-time boundary");
    }

    private static RouteAssessment validateRoute(
            GalaxyTopology topology,
            StarSystemId origin,
            StarSystemId destination,
            RouteAssessment route) {
        List<StarSystemId> path = route.orderedSystems();
        if (!path.get(0).equals(origin) || !path.get(path.size() - 1).equals(destination)) {
            throw new IllegalArgumentException("route assessment endpoints do not match acceptance request");
        }
        if (origin.equals(destination)) {
            if (path.size() != 1) {
                throw new IllegalArgumentException("same-system acceptance route must contain one system");
            }
            return route;
        }
        for (int index = 0; index < path.size() - 1; index++) {
            if (!topology.neighbors(path.get(index)).contains(path.get(index + 1))) {
                throw new IllegalArgumentException("acceptance route contains a non-neighbor shortcut");
            }
        }
        return route;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }

    private static void requirePositiveFinite(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }

    private record Selection(
            RequirementEvidence evidence,
            FailureReason failureReason,
            String detail) {
        private static Selection success(RequirementEvidence evidence) {
            return new Selection(Objects.requireNonNull(evidence, "evidence"), null, null);
        }

        private static Selection failure(FailureReason reason, String detail) {
            return new Selection(null, Objects.requireNonNull(reason, "reason"), requireText(detail, "detail"));
        }
    }
}
