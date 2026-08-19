package com.spacesim.world.calibration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Machine-readable Stage-20A closure/readiness gate assembled from accepted A.1-A.9 calibration outputs.
 *
 * <p>The gate does not turn every unresolved calibration note into a blocker. It separates physical
 * information required before Stage 20B can author local system geometry from content promotion work
 * explicitly permitted to remain provisional until Stage 22 and world data intentionally owned by
 * later Stage-20 slices. The requirement set also audits the complete accepted Stage-20A DoD rather
 * than treating implementation of A.1-A.9 seams as sufficient by itself.</p>
 *
 * @param version stable readiness-profile version
 * @param overallStatus aggregate Stage-20B entry status
 * @param representativeCoverage required Stage-20 representative-role coverage
 * @param requirements deterministic requirement results
 */
public record Stage20ACalibrationReadinessProfile(
        String version,
        GateStatus overallStatus,
        List<RepresentativeRoleCoverage> representativeCoverage,
        List<RequirementResult> requirements) {
    /** Current Stage-20A closure/readiness profile version. */
    public static final String CURRENT_VERSION = "stage20a.closure-readiness.v1";

    /** Aggregate Stage-20A gate result. */
    public enum GateStatus {
        /** One or more physical/calibration requirements block Stage-20B entry. */
        BLOCKED_FOR_STAGE20B,
        /** All Stage-20B entry requirements are physically/calibrationally closed. */
        READY_FOR_STAGE20B
    }

    /** Classification of one readiness requirement. */
    public enum RequirementStatus {
        /** Current accepted calibration is sufficient for Stage-20B entry. */
        SATISFIED,
        /** Missing physical/calibration authority prevents Stage-20B entry. */
        BLOCKING_STAGE20B_ENTRY,
        /** Provisional calibration is sufficient for Stage 20; production content promotion belongs to Stage 22. */
        DEFERRED_STAGE22_CONTENT,
        /** The value is intentionally authored by a later Stage-20 world-generation slice, not Stage 20A. */
        OWNED_BY_LATER_STAGE20
    }

    /** Stable Stage-20A readiness requirements. */
    public enum RequirementId {
        /** Required civilian/military representative propulsion roles are covered. */
        REPRESENTATIVE_PROPULSION_COVERAGE,
        /** Representative stores/endurance and sustained-vs-maximum-thrust consequences are calibrated. */
        REPRESENTATIVE_ENDURANCE_THRUST_COVERAGE,
        /** At least one accepted civilian logistics representative can execute ordinary neighbor-edge FTL. */
        CIVILIAN_ORDINARY_FTL_COVERAGE,
        /** Ordinary jump topology semantics are explicit and neighbor-only. */
        FTL_TOPOLOGY_SEMANTICS,
        /** System-neighbor, regional 3-5-hop and fleet-reinforcement cadence bands are calibrated. */
        INTERSYSTEM_CADENCE_CALIBRATION_BANDS,
        /** Production FTL content replaces/absorbs the accepted reference drive. */
        PRODUCTION_FTL_MODULE_PROMOTION,
        /** Generated one-edge transit distributions are authored by Stage 20D. */
        FTL_EDGE_TRANSIT_DISTRIBUTION,
        /** Numeric FTL heat law is promoted with production content. */
        FTL_HEAT_COEFFICIENT,
        /** Representative sensor observer/target class coverage is sufficient for world scale. */
        SENSOR_TARGET_CLASS_COVERAGE,
        /** Fused TRACKED/FIRE_CONTROL policy is closed against accepted weapon geometry. */
        FUSED_TRACK_FIRE_CONTROL_POLICY_CLOSURE,
        /** Weapon/PD runtime geometry produces deterministic spatial evidence. */
        WEAPON_PD_SPATIAL_EVIDENCE,
        /** Weapon effectiveness/time-of-flight is covered against representative target classes. */
        WEAPON_REPRESENTATIVE_TARGET_COVERAGE,
        /** PD safe-intercept geometry is physically derived rather than a scheduler probe input. */
        PD_SAFE_INTERCEPT_GEOMETRY,
        /** Formation runtime produces deterministic frontage/recovery spatial evidence. */
        FORMATION_SPATIAL_EVIDENCE,
        /** Formation-spacing bands are accepted beyond provisional Stage-19 fixture geometry. */
        FORMATION_SPACING_BAND_CLOSURE,
        /** All Stage-18 station archetypes have explicit footprint/docking/traffic physical closure. */
        STATION_PHYSICAL_GEOMETRY,
        /** Station defensive/sensor capability has explicit spatial geometry for placement calibration. */
        STATION_DEFENSIVE_SENSOR_GEOMETRY,
        /** Station-specific jump-arrival stand-off can be derived without fallback radius. */
        STATION_JUMP_ARRIVAL_STANDOFF,
        /** Named local route classes required by DoD 20A have calibrated physical bands. */
        LOCAL_ROUTE_SEMANTIC_BANDS,
        /** Stage-20A topology-quality acceptance bands are machine-readable before topology generation. */
        TOPOLOGY_QUALITY_CALIBRATION_BANDS,
        /** Major-infrastructure extent bands are physically closed for system placement calibration. */
        MAJOR_INFRASTRUCTURE_EXTENT_BANDS,
        /** Far local coordinates satisfy the accepted precision strategy. */
        FAR_COORDINATE_PRECISION,
        /** Materialization lifecycle and numeric activation bands are physically/wake-latency closed. */
        MATERIALIZATION_LOD_CLOSURE
    }

    /** Functional representative roles required by the accepted Stage-20 plan. */
    public enum RequiredRepresentativeRole {
        /** Early civilian trade/freight ship. */ EARLY_CIVILIAN_FREIGHTER,
        /** Loaded bulk cargo transport. */ LOADED_BULK_FREIGHTER,
        /** Resource extraction ship. */ MINING_SHIP,
        /** Fast patrol/corvette combatant. */ PATROL_CORVETTE,
        /** Escort destroyer. */ ESCORT_DESTROYER,
        /** Cruiser combatant. */ CRUISER,
        /** Capital combatant. */ CAPITAL_COMBATANT,
        /** Fleet tanker/logistics support ship. */ FLEET_TANKER,
        /** Carrier/aviation group where relevant to accepted doctrine. */ CARRIER_AVIATION
    }

    /**
     * Creates one immutable readiness profile and verifies aggregate status consistency.
     *
     * @param version stable readiness-profile version
     * @param overallStatus aggregate Stage-20B entry result
     * @param representativeCoverage required representative-role coverage
     * @param requirements requirement results
     */
    public Stage20ACalibrationReadinessProfile {
        requireText(version, "version");
        Objects.requireNonNull(overallStatus, "overallStatus");
        representativeCoverage = sortedCopy(
                representativeCoverage,
                Comparator.comparing(RepresentativeRoleCoverage::role),
                "representativeCoverage");
        requirements = sortedCopy(
                requirements,
                Comparator.comparing(RequirementResult::id),
                "requirements");
        long blockers = requirements.stream()
                .filter(value -> value.status() == RequirementStatus.BLOCKING_STAGE20B_ENTRY)
                .count();
        if (overallStatus == GateStatus.READY_FOR_STAGE20B && blockers != 0L) {
            throw new IllegalArgumentException("READY_FOR_STAGE20B cannot contain blocking requirements");
        }
        if (overallStatus == GateStatus.BLOCKED_FOR_STAGE20B && blockers == 0L) {
            throw new IllegalArgumentException("BLOCKED_FOR_STAGE20B requires at least one blocker");
        }
    }

    /**
     * Coverage of one required representative role by the current Stage-20 scale profile.
     *
     * @param role required functional role
     * @param expectedRepresentativeId stable representative ID expected to close the role
     * @param present whether the current scale profile contains the representative
     * @param authority current production/provisional authority when present
     * @param provenance current capability provenance when present
     */
    public record RepresentativeRoleCoverage(
            RequiredRepresentativeRole role,
            String expectedRepresentativeId,
            boolean present,
            Optional<String> authority,
            Optional<String> provenance) {
        /**
         * Validates one representative-role coverage result.
         *
         * @param role required role
         * @param expectedRepresentativeId expected Stage-20 representative ID
         * @param present whether that representative is present
         * @param authority capability authority when present
         * @param provenance capability provenance when present
         */
        public RepresentativeRoleCoverage {
            Objects.requireNonNull(role, "role");
            requireText(expectedRepresentativeId, "expectedRepresentativeId");
            Objects.requireNonNull(authority, "authority");
            Objects.requireNonNull(provenance, "provenance");
            if (present && (authority.isEmpty() || provenance.isEmpty())) {
                throw new IllegalArgumentException("Present representative coverage requires authority and provenance");
            }
            if (!present && (authority.isPresent() || provenance.isPresent())) {
                throw new IllegalArgumentException("Missing representative coverage cannot claim authority/provenance");
            }
        }
    }

    /**
     * One deterministic readiness requirement result.
     *
     * @param id stable requirement identity
     * @param status closure classification
     * @param evidence concise machine-readable evidence/reason
     */
    public record RequirementResult(RequirementId id, RequirementStatus status, String evidence) {
        /**
         * Validates one readiness result.
         *
         * @param id requirement identity
         * @param status closure classification
         * @param evidence concise evidence/reason
         */
        public RequirementResult {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(status, "status");
            requireText(evidence, "evidence");
        }
    }

    /**
     * Returns only current Stage-20B entry blockers in deterministic requirement order.
     *
     * @return immutable blocking requirement list
     */
    public List<RequirementResult> blockingRequirements() {
        return requirements.stream()
                .filter(value -> value.status() == RequirementStatus.BLOCKING_STAGE20B_ENTRY)
                .toList();
    }

    /**
     * Returns representative roles not yet covered by current calibration content.
     *
     * @return immutable missing-role list
     */
    public List<RepresentativeRoleCoverage> missingRepresentativeRoles() {
        return representativeCoverage.stream()
                .filter(value -> !value.present())
                .toList();
    }

    private static <T> List<T> sortedCopy(List<T> values, Comparator<? super T> comparator, String field) {
        Objects.requireNonNull(values, field);
        ArrayList<T> copy = new ArrayList<>(values);
        if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " must be non-empty and contain no null");
        }
        copy.sort(comparator);
        return List.copyOf(copy);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
