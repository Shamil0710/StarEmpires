package com.spacesim.world;

import com.spacesim.world.Stage20FactionStartDependencyDiagnostics.CommodityDiagnostic;
import com.spacesim.world.Stage20FactionStartDependencyDiagnostics.Report;
import com.spacesim.world.calibration.Stage20FactionStartAcceptanceProfile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Pure Stage-20E acceptance/ranking layer over authoritative faction-start dependency diagnostics.
 *
 * <p>The evaluator never modifies generated resources, topology, facilities, ownership, prices or
 * stock. A rejected candidate remains rejected; there is no emergency deposit, hidden supply or
 * synthetic route fallback. The finite {@code selectionPenalty} is only a deterministic placement
 * ordering signal among already acceptable candidates and has no runtime economic effect.</p>
 */
public final class Stage20FactionStartCandidateEvaluator {
    /** Current immutable candidate-evaluation result version. */
    public static final String CURRENT_VERSION = "stage20e.faction-start-candidate-evaluation.v1";
    private static final double EPSILON = 1.0e-9d;

    private Stage20FactionStartCandidateEvaluator() {
        throw new AssertionError("No instances");
    }

    /** Candidate acceptance state. */
    public enum Status {
        /** Candidate satisfies every currently authoritative hard generation gate. */
        ACCEPTED,
        /** Candidate fails one or more physical/economic generation gates. */
        REJECTED,
        /** Required acceptance authority is explicitly unavailable rather than guessed. */
        UNRESOLVED_AUTHORITY
    }

    /** Stable machine-readable hard-gate failure classes. */
    public enum ViolationType {
        /** Diagnostics were produced by an incompatible dependency-report version. */
        STALE_DIAGNOSTICS_VERSION,
        /** Reachable physical supply cannot sustain the essential requirement. */
        ESSENTIAL_THROUGHPUT_DEFICIT,
        /** Import-dependent essential has too few external suppliers. */
        INSUFFICIENT_EXTERNAL_SUPPLIERS,
        /** Import-dominant essential is excessively concentrated by supplier capacity. */
        EXCESS_SUPPLIER_CONCENTRATION,
        /** Import-dominant essential is excessively concentrated through final gateways. */
        EXCESS_ROUTE_CONCENTRATION,
        /** Import-dominant essential depends too strongly on one final gateway. */
        EXCESS_GATEWAY_DEPENDENCY,
        /** Import-dominant essential lacks the required edge-disjoint route floor. */
        INSUFFICIENT_ROUTE_REDUNDANCY,
        /** Import-dominant essential finite recoverable reserves are too concentrated. */
        EXCESS_RESERVE_CONCENTRATION,
        /** Current profile requires delivered-cost authority that diagnostics cannot resolve. */
        DELIVERED_COST_AUTHORITY_UNRESOLVED,
        /** Current profile requires physical inventory-buffer authority that diagnostics cannot resolve. */
        BUFFER_AUTHORITY_UNRESOLVED,
        /** Current profile requires reserve-ownership authority that diagnostics cannot resolve. */
        OWNERSHIP_AUTHORITY_UNRESOLVED
    }

    /**
     * One normalized candidate failure.
     *
     * @param type stable violation type
     * @param subject stable commodity or report subject
     * @param observed observed diagnostic value or count
     * @param limit acceptance bound that was not satisfied
     */
    public record Violation(ViolationType type, String subject, double observed, double limit) {
        /**
         * Validates one immutable violation row.
         *
         * @param type stable violation type
         * @param subject stable commodity or report subject
         * @param observed observed diagnostic value or count
         * @param limit acceptance bound
         */
        public Violation {
            Objects.requireNonNull(type, "type");
            if (subject == null || subject.isBlank()) {
                throw new IllegalArgumentException("subject must not be blank");
            }
            if (!Double.isFinite(observed) || !Double.isFinite(limit)) {
                throw new IllegalArgumentException("violation observed/limit must be finite");
            }
        }

        /**
         * Returns whether this violation represents missing upstream authority rather than a bad seed.
         *
         * @return true for explicit unresolved-authority failures
         */
        public boolean unresolvedAuthority() {
            return type == ViolationType.DELIVERED_COST_AUTHORITY_UNRESOLVED
                    || type == ViolationType.BUFFER_AUTHORITY_UNRESOLVED
                    || type == ViolationType.OWNERSHIP_AUTHORITY_UNRESOLVED;
        }
    }

    /**
     * Deterministic candidate-evaluation result.
     *
     * @param version stable result version
     * @param profileVersion exact acceptance profile version consumed
     * @param diagnosticsVersion exact diagnostics version consumed
     * @param candidateSystemId evaluated system
     * @param status acceptance outcome
     * @param selectionPenalty non-negative ordering signal among accepted candidates; lower is more resilient
     * @param violations deterministic hard-gate failures
     */
    public record Evaluation(
            String version,
            String profileVersion,
            String diagnosticsVersion,
            StarSystemId candidateSystemId,
            Status status,
            double selectionPenalty,
            List<Violation> violations) {
        /**
         * Validates and freezes one candidate evaluation.
         *
         * @param version stable result version
         * @param profileVersion exact profile version consumed
         * @param diagnosticsVersion exact diagnostics version consumed
         * @param candidateSystemId evaluated system
         * @param status acceptance outcome
         * @param selectionPenalty deterministic non-negative selection penalty
         * @param violations deterministic hard-gate failures
         */
        public Evaluation {
            version = requireText(version, "version");
            profileVersion = requireText(profileVersion, "profileVersion");
            diagnosticsVersion = requireText(diagnosticsVersion, "diagnosticsVersion");
            Objects.requireNonNull(candidateSystemId, "candidateSystemId");
            Objects.requireNonNull(status, "status");
            if (!Double.isFinite(selectionPenalty) || selectionPenalty < 0d) {
                throw new IllegalArgumentException("selectionPenalty must be non-negative and finite");
            }
            Objects.requireNonNull(violations, "violations");
            ArrayList<Violation> copy = new ArrayList<>(violations);
            if (copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("violations cannot contain null");
            }
            copy.sort(Comparator.comparing((Violation value) -> value.type().name())
                    .thenComparing(Violation::subject));
            violations = List.copyOf(copy);
            if (status == Status.ACCEPTED && !violations.isEmpty()) {
                throw new IllegalArgumentException("accepted candidate cannot contain hard violations");
            }
            if (status == Status.UNRESOLVED_AUTHORITY
                    && violations.stream().noneMatch(Violation::unresolvedAuthority)) {
                throw new IllegalArgumentException("unresolved status requires an authority violation");
            }
        }
    }

    /**
     * Evaluates one dependency report against one versioned ordinary-generation profile.
     *
     * @param diagnostics authoritative-derived Stage-20E dependency report
     * @param profile versioned faction-start acceptance policy
     * @return immutable deterministic candidate evaluation
     */
    public static Evaluation evaluate(Report diagnostics, Stage20FactionStartAcceptanceProfile profile) {
        Report checked = Objects.requireNonNull(diagnostics, "diagnostics");
        Stage20FactionStartAcceptanceProfile policy = Objects.requireNonNull(profile, "profile");
        ArrayList<Violation> violations = new ArrayList<>();

        if (!Stage20FactionStartDependencyDiagnostics.CURRENT_VERSION.equals(checked.version())) {
            violations.add(new Violation(
                    ViolationType.STALE_DIAGNOSTICS_VERSION,
                    checked.candidateSystemId().toString(),
                    1d,
                    0d));
        }

        double penalty = 0d;
        for (CommodityDiagnostic commodity : checked.commodities()) {
            double required = commodity.requiredKgPerSecond();
            if (commodity.totalReachableSupplyKgPerSecond() + EPSILON < required
                    || commodity.throughputHeadroomKgPerSecond() < -EPSILON) {
                violations.add(new Violation(
                        ViolationType.ESSENTIAL_THROUGHPUT_DEFICIT,
                        commodity.commodityId(),
                        commodity.totalReachableSupplyKgPerSecond(),
                        required));
            }

            double importShare = commodity.importDependencyFraction();
            penalty += importShare;
            penalty += importShare * commodity.supplierConcentrationHhi();
            penalty += importShare * commodity.routeConcentrationHhi();
            penalty += importShare * commodity.criticalGatewayDependencyFraction();
            penalty += importShare * commodity.accessibleReserveConcentrationHhi();

            if (importShare > EPSILON
                    && commodity.externalSupplierCount() < policy.minimumExternalSuppliersForAnyImport()) {
                violations.add(new Violation(
                        ViolationType.INSUFFICIENT_EXTERNAL_SUPPLIERS,
                        commodity.commodityId(),
                        commodity.externalSupplierCount(),
                        policy.minimumExternalSuppliersForAnyImport()));
            }

            if (importShare + EPSILON >= policy.dominantImportDependencyFraction()) {
                if (commodity.externalSupplierCount() < policy.minimumExternalSuppliersForDominantImport()) {
                    violations.add(new Violation(
                            ViolationType.INSUFFICIENT_EXTERNAL_SUPPLIERS,
                            commodity.commodityId(),
                            commodity.externalSupplierCount(),
                            policy.minimumExternalSuppliersForDominantImport()));
                }
                if (commodity.supplierConcentrationHhi() > policy.maximumSupplierConcentrationHhi() + EPSILON) {
                    violations.add(new Violation(
                            ViolationType.EXCESS_SUPPLIER_CONCENTRATION,
                            commodity.commodityId(),
                            commodity.supplierConcentrationHhi(),
                            policy.maximumSupplierConcentrationHhi()));
                }
                if (commodity.routeConcentrationHhi() > policy.maximumRouteConcentrationHhi() + EPSILON) {
                    violations.add(new Violation(
                            ViolationType.EXCESS_ROUTE_CONCENTRATION,
                            commodity.commodityId(),
                            commodity.routeConcentrationHhi(),
                            policy.maximumRouteConcentrationHhi()));
                }
                if (commodity.criticalGatewayDependencyFraction()
                        > policy.maximumCriticalGatewayDependencyFraction() + EPSILON) {
                    violations.add(new Violation(
                            ViolationType.EXCESS_GATEWAY_DEPENDENCY,
                            commodity.commodityId(),
                            commodity.criticalGatewayDependencyFraction(),
                            policy.maximumCriticalGatewayDependencyFraction()));
                }
                if (commodity.alternativePathCountFloor() < policy.minimumAlternativePathsForDominantImport()) {
                    violations.add(new Violation(
                            ViolationType.INSUFFICIENT_ROUTE_REDUNDANCY,
                            commodity.commodityId(),
                            commodity.alternativePathCountFloor(),
                            policy.minimumAlternativePathsForDominantImport()));
                }
                if (commodity.accessibleReserveConcentrationHhi()
                        > policy.maximumAccessibleReserveConcentrationHhi() + EPSILON) {
                    violations.add(new Violation(
                            ViolationType.EXCESS_RESERVE_CONCENTRATION,
                            commodity.commodityId(),
                            commodity.accessibleReserveConcentrationHhi(),
                            policy.maximumAccessibleReserveConcentrationHhi()));
                }
            }
        }

        if (policy.requireDeliveredCostAuthority() && checked.unresolvedDeliveredCostCommodityCount() > 0) {
            violations.add(new Violation(
                    ViolationType.DELIVERED_COST_AUTHORITY_UNRESOLVED,
                    "delivered-cost-authority",
                    checked.unresolvedDeliveredCostCommodityCount(),
                    0d));
        }
        if (policy.requireBufferAuthority() && checked.unresolvedBufferCommodityCount() > 0) {
            violations.add(new Violation(
                    ViolationType.BUFFER_AUTHORITY_UNRESOLVED,
                    "buffer-authority",
                    checked.unresolvedBufferCommodityCount(),
                    0d));
        }
        if (policy.requireOwnershipAuthority() && checked.unresolvedOwnershipCommodityCount() > 0) {
            violations.add(new Violation(
                    ViolationType.OWNERSHIP_AUTHORITY_UNRESOLVED,
                    "ownership-authority",
                    checked.unresolvedOwnershipCommodityCount(),
                    0d));
        }

        boolean hasSeedFailure = violations.stream().anyMatch(value -> !value.unresolvedAuthority());
        boolean hasAuthorityFailure = violations.stream().anyMatch(Violation::unresolvedAuthority);
        Status status = hasSeedFailure
                ? Status.REJECTED
                : hasAuthorityFailure ? Status.UNRESOLVED_AUTHORITY : Status.ACCEPTED;
        return new Evaluation(
                CURRENT_VERSION,
                policy.version(),
                checked.version(),
                checked.candidateSystemId(),
                status,
                penalty,
                violations);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
