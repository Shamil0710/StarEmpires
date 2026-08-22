package com.spacesim.world;

import com.spacesim.content.Stage18ManufacturingCatalogLoader;
import com.spacesim.content.Stage18RefiningCatalogLoader;
import com.spacesim.world.Stage20BootstrapProductionCapacityCalculator.ProcessKind;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.ProcessOperatingDemand;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.StationKey;
import com.spacesim.world.Stage20IndustrialShipyardInstallationPlan.InstalledYardEvidence;
import com.spacesim.world.Stage20IndustrialShipyardInstallationPlan.Status;
import com.spacesim.world.Stage20IndustrialShipyardInstallationPlan.YardReport;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.MissingAuthority;
import com.spacesim.world.generation.Stage20ResolvedGeneratedWorldProductionProbe.ResolvedProbeResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Derives final Stage-20F operational specialization only from closed physical authorities.
 *
 * <p>Roles are descriptive indexes over selected active recipes and active installed yards. They
 * grant no production multiplier, facility, inventory, route or ship. Refining and manufacturing
 * come from exact Stage-18 recipe kinds; shipbuilding exists only where an explicit Stage-18G yard
 * projection is active.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20OperationalIndustrialSpecializationPlan {
    /** Stable final Stage-20F operational-specialization version. */
    public static final String CURRENT_VERSION =
            "stage20f.operational-industrial-specialization-plan.v1";
    private static final double EPSILON = 1.0e-9d;
    private static final Set<RuntimeBridgeRequirement> CURRENT_RUNTIME_BRIDGE_REQUIREMENTS =
            Collections.unmodifiableSet(EnumSet.allOf(RuntimeBridgeRequirement.class));

    private Stage20OperationalIndustrialSpecializationPlan() {
        throw new AssertionError("No instances");
    }

    /** Physical capability-derived specialization roles. */
    public enum IndustrialRole {
        /** At least one selected active Stage-18 refining recipe. */
        REFINING,
        /** At least one selected active Stage-18 component-manufacturing recipe. */
        COMPONENT_MANUFACTURING,
        /** At least one explicit active installed Stage-18G yard. */
        SHIPBUILDING
    }

    /** Final derived acceptance state. */
    public enum AcceptanceStatus {
        /** Every Stage-20F authority is closed and exact operational roles are indexed. */
        OPERATIONAL
    }

    /** Explicit materialization work that Stage 20F does not pretend is already runtime state. */
    public enum RuntimeBridgeRequirement {
        /** Bind every reserved SupplyKey rate to live source producer operation or physical stock. */
        SOURCE_SUPPLY_MATERIALIZATION,
        /** Materialize retained ownership ordinals as persistent FleetIds without replacing assets. */
        FREIGHT_FLEET_MATERIALIZATION,
        /** Create ordinary source cargo lots, transport orders and route/cadence deadlines. */
        CARGO_ORDER_AND_LOT_MATERIALIZATION,
        /** Instantiate exact station, facility, storage and yard runtime entities and states. */
        INDUSTRIAL_ENTITY_MATERIALIZATION
    }

    /**
     * Exact owner/station specialization identity.
     *
     * @param station exact generated station
     * @param stableFactionId explicit owning faction
     */
    public record SpecializationKey(
            StationKey station,
            String stableFactionId) implements Comparable<SpecializationKey> {
        /**
         * Validates one owner/station identity.
         *
         * @param station exact generated station
         * @param stableFactionId explicit owner
         */
        public SpecializationKey {
            Objects.requireNonNull(station, "station");
            stableFactionId = WorldFactionIdentityState.normalizeStableId(stableFactionId);
        }

        /** Orders owner/station specialization identities deterministically. */
        @Override
        public int compareTo(SpecializationKey other) {
            int comparison = station.compareTo(other.station);
            return comparison != 0
                    ? comparison
                    : stableFactionId.compareTo(other.stableFactionId);
        }
    }

    /**
     * Exact active selected process and its canonical Stage-18 kind.
     *
     * @param demand accepted operating process demand
     * @param processKind exact Stage-18 process kind
     */
    public record OperationalProcessEvidence(
            ProcessOperatingDemand demand,
            ProcessKind processKind) {
        /**
         * Validates one physical process-role row.
         *
         * @param demand accepted operating demand
         * @param processKind exact process kind
         */
        public OperationalProcessEvidence {
            Objects.requireNonNull(demand, "demand");
            Objects.requireNonNull(processKind, "processKind");
        }

        /** @return role derived from the exact Stage-18 process kind */
        public IndustrialRole role() {
            return switch (processKind) {
                case REFINING -> IndustrialRole.REFINING;
                case COMPONENT_MANUFACTURING -> IndustrialRole.COMPONENT_MANUFACTURING;
            };
        }
    }

    /**
     * Final physical specialization for one owner at one station.
     *
     * @param key exact owner/station identity
     * @param processes accepted selected processes owned here
     * @param activeYards accepted active installed yards owned here
     * @param roles exact capability-derived roles
     */
    public record FactionStationSpecialization(
            SpecializationKey key,
            List<OperationalProcessEvidence> processes,
            List<InstalledYardEvidence> activeYards,
            Set<IndustrialRole> roles) {
        /**
         * Canonicalizes and validates one final owner/station specialization.
         *
         * @param key exact owner/station identity
         * @param processes exact selected processes
         * @param activeYards exact active installed yards
         * @param roles exact derived roles
         */
        public FactionStationSpecialization {
            Objects.requireNonNull(key, "key");
            ArrayList<OperationalProcessEvidence> processCopy = new ArrayList<>(Objects.requireNonNull(
                    processes, "processes"));
            ArrayList<InstalledYardEvidence> yardCopy = new ArrayList<>(Objects.requireNonNull(
                    activeYards, "activeYards"));
            processCopy.sort(Comparator.comparing(value -> value.demand().process()));
            yardCopy.sort(Comparator.comparing(value -> value.assignment().slot()));
            if (processCopy.isEmpty() && yardCopy.isEmpty()) {
                throw new IllegalArgumentException(
                        "operational specialization requires process or active yard evidence");
            }
            if (processCopy.stream().anyMatch(Objects::isNull)
                    || yardCopy.stream().anyMatch(Objects::isNull)
                    || processCopy.stream().map(value -> value.demand().process()).distinct().count()
                    != processCopy.size()
                    || yardCopy.stream().map(value -> value.assignment().slot()).distinct().count()
                    != yardCopy.size()
                    || processCopy.stream().anyMatch(value ->
                    !value.demand().facility().station().equals(key.station())
                            || !value.demand().stableFactionId().equals(key.stableFactionId()))
                    || yardCopy.stream().anyMatch(value ->
                    !value.assignment().slot().station().equals(key.station())
                            || !value.assignment().stableFactionId().equals(key.stableFactionId())
                            || value.status() != Status.ACCEPTED
                            || !value.snapshot().active())) {
                throw new IllegalArgumentException(
                        "specialization evidence must be unique, active and match its owner/station");
            }
            processes = List.copyOf(processCopy);
            activeYards = List.copyOf(yardCopy);
            Objects.requireNonNull(roles, "roles");
            EnumSet<IndustrialRole> expected = EnumSet.noneOf(IndustrialRole.class);
            processes.forEach(value -> expected.add(value.role()));
            if (!activeYards.isEmpty()) {
                expected.add(IndustrialRole.SHIPBUILDING);
            }
            EnumSet<IndustrialRole> actual = roles.isEmpty()
                    ? EnumSet.noneOf(IndustrialRole.class)
                    : EnumSet.copyOf(roles);
            if (!actual.equals(expected)) {
                throw new IllegalArgumentException(
                        "specialization roles differ from exact process/yard capability");
            }
            roles = Collections.unmodifiableSet(EnumSet.copyOf(actual));
        }

        /** @return total selected output mass rate owned at this station */
        public double selectedOutputKgPerSecond() {
            double result = 0d;
            for (OperationalProcessEvidence process : processes) {
                result = finiteAdd(result, process.demand().requestedOutputKgPerSecond());
            }
            return result;
        }
    }

    /**
     * Final Stage-20F operational industrial specialization acceptance.
     *
     * @param version plan contract version
     * @param rootSeed exact accepted root seed
     * @param resolvedProbeVersion exact generated-world evidence version
     * @param yardInstallation accepted installed-yard authority
     * @param status final operational state
     * @param specializations exact owner/station specializations
     * @param missingAuthorities exact empty closed-authority set
     * @param runtimeBridgeRequirements explicit remaining runtime materialization work
     */
    public record OperationalSpecializationReport(
            String version,
            long rootSeed,
            String resolvedProbeVersion,
            YardReport yardInstallation,
            AcceptanceStatus status,
            List<FactionStationSpecialization> specializations,
            Set<MissingAuthority> missingAuthorities,
            Set<RuntimeBridgeRequirement> runtimeBridgeRequirements) {
        /**
         * Canonicalizes and validates final Stage-20F acceptance.
         *
         * @param version plan contract version
         * @param rootSeed exact root seed
         * @param resolvedProbeVersion generated evidence version
         * @param yardInstallation accepted yard authority
         * @param status final operational status
         * @param specializations exact owner/station specializations
         * @param missingAuthorities exact empty authority set
         * @param runtimeBridgeRequirements explicit runtime materialization work
         */
        public OperationalSpecializationReport {
            version = requireText(version, "version");
            resolvedProbeVersion = requireText(resolvedProbeVersion, "resolvedProbeVersion");
            Objects.requireNonNull(yardInstallation, "yardInstallation");
            Objects.requireNonNull(status, "status");
            if (!CURRENT_VERSION.equals(version)
                    || rootSeed != yardInstallation.rootSeed()
                    || !resolvedProbeVersion.equals(yardInstallation.resolvedProbeVersion())
                    || !yardInstallation.operationallyAuthoritative()
                    || status != AcceptanceStatus.OPERATIONAL) {
                throw new IllegalArgumentException(
                        "final specialization requires matching fully closed Stage-20F authority");
            }
            ArrayList<FactionStationSpecialization> copy = new ArrayList<>(Objects.requireNonNull(
                    specializations, "specializations"));
            copy.sort(Comparator.comparing(FactionStationSpecialization::key));
            if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)
                    || copy.stream().map(FactionStationSpecialization::key).distinct().count()
                    != copy.size()) {
                throw new IllegalArgumentException(
                        "final specializations must be non-empty and owner/station-unique");
            }
            specializations = List.copyOf(copy);
            validateExactCoverage(yardInstallation, specializations);
            Objects.requireNonNull(missingAuthorities, "missingAuthorities");
            if (!missingAuthorities.isEmpty()
                    || !yardInstallation.missingAuthorities().isEmpty()) {
                throw new IllegalArgumentException(
                        "final Stage-20F acceptance cannot retain missing authorities");
            }
            missingAuthorities = Set.of();
            Objects.requireNonNull(runtimeBridgeRequirements, "runtimeBridgeRequirements");
            EnumSet<RuntimeBridgeRequirement> bridge = runtimeBridgeRequirements.isEmpty()
                    ? EnumSet.noneOf(RuntimeBridgeRequirement.class)
                    : EnumSet.copyOf(runtimeBridgeRequirements);
            if (!bridge.equals(CURRENT_RUNTIME_BRIDGE_REQUIREMENTS)) {
                throw new IllegalArgumentException(
                        "final Stage-20F report cannot hide or invent runtime bridge work");
            }
            runtimeBridgeRequirements = Collections.unmodifiableSet(bridge);
        }

        /** @return total selected physical output rate across final specializations */
        public double totalSelectedOutputKgPerSecond() {
            double result = 0d;
            for (FactionStationSpecialization specialization : specializations) {
                result = finiteAdd(result, specialization.selectedOutputKgPerSecond());
            }
            return result;
        }

        /** @return number of exact active installed yards in the final acceptance */
        public int activeYardCount() {
            return specializations.stream().mapToInt(value -> value.activeYards().size()).sum();
        }

        /** @return whether Stage 20F is operationally authoritative */
        public boolean operationallyAuthoritative() {
            return status == AcceptanceStatus.OPERATIONAL
                    && missingAuthorities.isEmpty()
                    && yardInstallation.operationallyAuthoritative();
        }

        /** @return whether the closed Stage-20F plan exposes the exact runtime handoff seam */
        public boolean readyForRuntimeBridge() {
            return operationallyAuthoritative()
                    && runtimeBridgeRequirements.equals(CURRENT_RUNTIME_BRIDGE_REQUIREMENTS);
        }
    }

    /**
     * Derives final operational specialization from a fully accepted installed-yard report.
     *
     * @param resolved exact accepted generated-world authority
     * @param yardInstallation exact fully accepted installed-yard authority
     * @return deterministic final Stage-20F specialization acceptance
     */
    public static OperationalSpecializationReport derive(
            ResolvedProbeResult resolved,
            YardReport yardInstallation) {
        ResolvedProbeResult accepted = Objects.requireNonNull(resolved, "resolved");
        YardReport yards = Objects.requireNonNull(yardInstallation, "yardInstallation");
        if (accepted.rootSeed() != yards.rootSeed()
                || !accepted.version().equals(yards.resolvedProbeVersion())
                || !yards.operationallyAuthoritative()) {
            throw new IllegalArgumentException(
                    "operational specialization requires matching closed yard authority");
        }

        TreeMap<SpecializationKey, MutableSpecialization> grouped = new TreeMap<>();
        for (ProcessOperatingDemand process
                : yards.inventory().operatingState().processes()) {
            ProcessKind kind = resolveProcessKind(process);
            SpecializationKey key = new SpecializationKey(
                    process.facility().station(), process.stableFactionId());
            grouped.computeIfAbsent(key, ignored -> new MutableSpecialization())
                    .processes.add(new OperationalProcessEvidence(process, kind));
        }
        for (var station : yards.stations()) {
            for (InstalledYardEvidence yard : station.yards()) {
                if (yard.status() != Status.ACCEPTED || !yard.snapshot().active()) {
                    throw new IllegalArgumentException(
                            "closed yard authority contains inactive installed yard");
                }
                SpecializationKey key = new SpecializationKey(
                        station.station(), yard.assignment().stableFactionId());
                grouped.computeIfAbsent(key, ignored -> new MutableSpecialization())
                        .yards.add(yard);
            }
        }

        ArrayList<FactionStationSpecialization> result = new ArrayList<>();
        for (Map.Entry<SpecializationKey, MutableSpecialization> entry : grouped.entrySet()) {
            EnumSet<IndustrialRole> roles = EnumSet.noneOf(IndustrialRole.class);
            entry.getValue().processes.forEach(value -> roles.add(value.role()));
            if (!entry.getValue().yards.isEmpty()) {
                roles.add(IndustrialRole.SHIPBUILDING);
            }
            result.add(new FactionStationSpecialization(
                    entry.getKey(),
                    entry.getValue().processes,
                    entry.getValue().yards,
                    roles));
        }
        return new OperationalSpecializationReport(
                CURRENT_VERSION,
                yards.rootSeed(),
                yards.resolvedProbeVersion(),
                yards,
                AcceptanceStatus.OPERATIONAL,
                result,
                Set.of(),
                CURRENT_RUNTIME_BRIDGE_REQUIREMENTS);
    }

    private static ProcessKind resolveProcessKind(ProcessOperatingDemand process) {
        var refining = Stage18RefiningCatalogLoader.loadDefault().findRecipe(
                process.process().processId());
        var manufacturing = Stage18ManufacturingCatalogLoader.loadDefault().findComponentRecipe(
                process.process().processId());
        boolean isRefining = refining != null
                && refining.outputCommodityId().equals(process.process().outputCommodityId());
        boolean isManufacturing = manufacturing != null
                && manufacturing.outputCommodityId().equals(process.process().outputCommodityId());
        if (isRefining == isManufacturing) {
            throw new IllegalArgumentException(
                    "operational process does not resolve to one exact Stage-18 recipe");
        }
        return isRefining ? ProcessKind.REFINING : ProcessKind.COMPONENT_MANUFACTURING;
    }

    private static void validateExactCoverage(
            YardReport yards,
            List<FactionStationSpecialization> specializations) {
        TreeMap<com.spacesim.world.Stage20IndustrialInputReservationPlan.ProcessSelectionKey,
                OperationalProcessEvidence> processes = new TreeMap<>();
        TreeMap<Stage20IndustrialShipyardInstallationPlan.YardSlotKey,
                InstalledYardEvidence> activeYards = new TreeMap<>();
        for (FactionStationSpecialization specialization : specializations) {
            for (OperationalProcessEvidence process : specialization.processes()) {
                if (processes.putIfAbsent(process.demand().process(), process) != null
                        || process.processKind() != resolveProcessKind(process.demand())) {
                    throw new IllegalArgumentException(
                            "final process specialization coverage is duplicate or misclassified");
                }
            }
            for (InstalledYardEvidence yard : specialization.activeYards()) {
                if (activeYards.putIfAbsent(yard.assignment().slot(), yard) != null) {
                    throw new IllegalArgumentException(
                            "final active yard specialization coverage is duplicate");
                }
            }
        }
        Set<com.spacesim.world.Stage20IndustrialInputReservationPlan.ProcessSelectionKey>
                expectedProcesses = yards.inventory().operatingState().processes().stream()
                .map(ProcessOperatingDemand::process)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        TreeMap<Stage20IndustrialShipyardInstallationPlan.YardSlotKey,
                InstalledYardEvidence> expectedYards = new TreeMap<>();
        yards.stations().forEach(station -> station.yards().forEach(value ->
                expectedYards.put(value.assignment().slot(), value)));
        if (!processes.keySet().equals(expectedProcesses)
                || !activeYards.equals(expectedYards)) {
            throw new IllegalArgumentException(
                    "final specialization must exactly cover selected processes and active yards");
        }
    }

    private static double finiteAdd(double left, double right) {
        double result = left + right;
        if (!Double.isFinite(result) || result < -EPSILON) {
            throw new IllegalArgumentException(
                    "specialization output sum must remain finite and non-negative");
        }
        return Math.max(0d, result);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.strip();
    }

    private static final class MutableSpecialization {
        private final List<OperationalProcessEvidence> processes = new ArrayList<>();
        private final List<InstalledYardEvidence> yards = new ArrayList<>();
    }
}
