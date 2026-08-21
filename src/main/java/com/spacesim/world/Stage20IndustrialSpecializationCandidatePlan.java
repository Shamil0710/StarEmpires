package com.spacesim.world;

import com.spacesim.content.Stage18ExtractionCatalogLoader;
import com.spacesim.content.Stage18FacilityCatalog.FacilityDefinition;
import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18ManufacturingCatalogLoader;
import com.spacesim.content.Stage18RefiningCatalogLoader;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage18StationInfrastructureCatalog.StationArchetypeDefinition;
import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.world.Stage20BootstrapProductionCapacityCalculator.ExtractionCapacity;
import com.spacesim.world.Stage20BootstrapProductionCapacityCalculator.StationProcessCapacity;
import com.spacesim.world.Stage20GeneratedWorldSeedAcceptance.Status;
import com.spacesim.world.Stage20LocalInfrastructureLayout.InfrastructurePlacement;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.ProcessThroughputEvidence;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyThroughputReport;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe;
import com.spacesim.world.generation.Stage20ResolvedGeneratedWorldProductionProbe;
import com.spacesim.world.generation.Stage20ResolvedGeneratedWorldProductionProbe.ResolvedProbeResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reconstructs Stage-20F industrial-specialization candidates from one accepted generated world.
 *
 * <p>This plan preserves only evidence already authored by the Stage-18/20 physical chain: exact
 * station placements and archetypes, concrete facility-definition slots, storage/handling limits,
 * finite extraction sites and the existing process/input-limited throughput upper bounds. A station
 * name never grants output and this class adds no production multiplier.</p>
 *
 * <p>The result is deliberately a candidate plan rather than operational industrial state. The
 * current production probe does not retain installed-facility condition/power/heat/labor authority,
 * initial inventories, reserved industrial inputs, owned input freight or installed shipyards. Those
 * seams remain machine-readable and block promotion of a candidate into an operational specialization.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20IndustrialSpecializationCandidatePlan {
    /** Stable Stage-20F candidate-plan version. */
    public static final String CURRENT_VERSION = "stage20f.industrial-specialization-candidate-plan.v1";
    private static final Set<MissingAuthority> CURRENT_MISSING_AUTHORITIES = Collections.unmodifiableSet(
            EnumSet.allOf(MissingAuthority.class));

    private Stage20IndustrialSpecializationCandidatePlan() {
        throw new AssertionError("No instances");
    }

    /** Whether a real configured process has a positive unreserved physical input upper bound. */
    public enum CandidateStatus {
        /** Physical input closure is positive, but no input/freight capacity has been reserved. */
        REACHABLE_UNRESERVED_UPPER_BOUND,
        /** The existing Stage-20E physical input closure produces no positive output. */
        INPUT_BLOCKED
    }

    /** Authorities intentionally absent from the current generated-world evidence. */
    public enum MissingAuthority {
        /** Installed condition, allocated power, heat rejection, labor and maintenance work. */
        INSTALLED_FACILITY_OPERATING_STATE,
        /** Initial Stage-18 commodity/product inventory at generated stations. */
        INITIAL_STATION_INVENTORY,
        /** Shared upstream supply capacity reserved for the selected industrial processes. */
        RESERVED_INDUSTRIAL_INPUTS,
        /** Concrete owned freight assets/routes assigned to industrial input delivery. */
        OWNED_INDUSTRIAL_INPUT_FREIGHT,
        /** Explicit Stage-18G yard instances bound to generated physical station placements. */
        INSTALLED_SHIPYARDS
    }

    /** One deterministic non-runtime facility slot inherited from a Stage-18 station archetype. */
    public record FacilitySlot(int facilityOrdinal, FacilityDefinition definition) {
        public FacilitySlot {
            if (facilityOrdinal < 0) {
                throw new IllegalArgumentException("facilityOrdinal must be non-negative");
            }
            Objects.requireNonNull(definition, "definition");
        }
    }

    /** One exact configured process joined to its input-limited Stage-20E evidence. */
    public record ProcessCandidate(
            StationProcessCapacity capacity,
            ProcessThroughputEvidence throughput,
            CandidateStatus status) {
        public ProcessCandidate {
            Objects.requireNonNull(capacity, "capacity");
            Objects.requireNonNull(throughput, "throughput");
            Objects.requireNonNull(status, "status");
            if (!capacity.systemId().equals(throughput.systemId())
                    || !capacity.stationPlacementId().equals(throughput.stationPlacementId())
                    || !capacity.facilityDefinitionId().equals(throughput.facilityDefinitionId())
                    || !capacity.processId().equals(throughput.processId())
                    || !capacity.outputCommodityId().equals(throughput.outputCommodityId())
                    || Double.compare(
                    capacity.theoreticalExportableOutputKgPerSecond(),
                    throughput.processAndStationCeilingKgPerSecond()) != 0) {
                throw new IllegalArgumentException(
                        "process capacity and throughput evidence must identify the same physical row");
            }
            CandidateStatus expected = throughput.inputLimitedOutputKgPerSecond() > 0d
                    ? CandidateStatus.REACHABLE_UNRESERVED_UPPER_BOUND
                    : CandidateStatus.INPUT_BLOCKED;
            if (status != expected) {
                throw new IllegalArgumentException("candidate status differs from physical throughput evidence");
            }
        }

        static ProcessCandidate from(
                StationProcessCapacity capacity,
                ProcessThroughputEvidence throughput) {
            CandidateStatus status = throughput.inputLimitedOutputKgPerSecond() > 0d
                    ? CandidateStatus.REACHABLE_UNRESERVED_UPPER_BOUND
                    : CandidateStatus.INPUT_BLOCKED;
            return new ProcessCandidate(capacity, throughput, status);
        }
    }

    /** Physical generated station and its exact Stage-18 industrial candidate evidence. */
    public record StationCandidate(
            StarSystemId systemId,
            InfrastructurePlacement placement,
            StationArchetypeDefinition archetype,
            List<FacilitySlot> facilitySlots,
            List<ProcessCandidate> processes) {
        public StationCandidate {
            Objects.requireNonNull(systemId, "systemId");
            Objects.requireNonNull(placement, "placement");
            Objects.requireNonNull(archetype, "archetype");
            if (!placement.isStation()
                    || !placement.stationArchetypeId().orElseThrow().equals(archetype.id())) {
                throw new IllegalArgumentException("station placement and Stage-18 archetype differ");
            }

            ArrayList<FacilitySlot> slots = new ArrayList<>(Objects.requireNonNull(
                    facilitySlots, "facilitySlots"));
            if (slots.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("facilitySlots cannot contain nulls");
            }
            slots.sort(Comparator.comparingInt(FacilitySlot::facilityOrdinal));
            List<String> expectedFacilityIds = archetype.installedFacilityDefinitionIds();
            if (slots.size() != expectedFacilityIds.size()) {
                throw new IllegalArgumentException("facility slots must exactly cover the station archetype");
            }
            Set<String> facilityIds = new HashSet<>();
            for (int ordinal = 0; ordinal < slots.size(); ordinal++) {
                FacilitySlot slot = slots.get(ordinal);
                if (slot.facilityOrdinal() != ordinal
                        || !slot.definition().id().equals(expectedFacilityIds.get(ordinal))
                        || !facilityIds.add(slot.definition().id())) {
                    throw new IllegalArgumentException(
                            "facility slots must retain canonical unique station-archetype order");
                }
            }
            facilitySlots = List.copyOf(slots);

            ArrayList<ProcessCandidate> processCopy = new ArrayList<>(Objects.requireNonNull(
                    processes, "processes"));
            if (processCopy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("processes cannot contain nulls");
            }
            processCopy.sort(PROCESS_ORDER);
            Set<ProcessKey> processKeys = new HashSet<>();
            for (ProcessCandidate process : processCopy) {
                if (!process.capacity().systemId().equals(systemId)
                        || !process.capacity().stationPlacementId().equals(placement.id())
                        || !facilityIds.contains(process.capacity().facilityDefinitionId())
                        || !processKeys.add(ProcessKey.from(process.capacity()))) {
                    throw new IllegalArgumentException(
                            "station processes must be unique and backed by an installed facility slot");
                }
            }
            processes = List.copyOf(processCopy);
        }
    }

    /** All extraction and station candidates in one generated physical system. */
    public record SystemCandidate(
            StarSystemId systemId,
            List<ExtractionCapacity> extractionSites,
            List<StationCandidate> stations) {
        public SystemCandidate {
            Objects.requireNonNull(systemId, "systemId");
            ArrayList<ExtractionCapacity> extractionCopy = new ArrayList<>(Objects.requireNonNull(
                    extractionSites, "extractionSites"));
            if (extractionCopy.stream().anyMatch(Objects::isNull)
                    || extractionCopy.stream().anyMatch(value -> !value.systemId().equals(systemId))) {
                throw new IllegalArgumentException("extraction sites must belong to their candidate system");
            }
            extractionCopy.sort(Comparator.comparing(ExtractionCapacity::siteId));
            if (extractionCopy.stream().map(ExtractionCapacity::siteId).distinct().count()
                    != extractionCopy.size()) {
                throw new IllegalArgumentException("extraction site IDs must be unique inside a system");
            }
            extractionSites = List.copyOf(extractionCopy);

            ArrayList<StationCandidate> stationCopy = new ArrayList<>(Objects.requireNonNull(
                    stations, "stations"));
            if (stationCopy.isEmpty()
                    || stationCopy.stream().anyMatch(Objects::isNull)
                    || stationCopy.stream().anyMatch(value -> !value.systemId().equals(systemId))) {
                throw new IllegalArgumentException("candidate system requires its physical stations");
            }
            stationCopy.sort(Comparator.comparing(value -> value.placement().id()));
            if (stationCopy.stream().map(value -> value.placement().id()).distinct().count()
                    != stationCopy.size()) {
                throw new IllegalArgumentException("station placement IDs must be unique inside a system");
            }
            stations = List.copyOf(stationCopy);
        }
    }

    /** Complete fail-closed candidate plan for one exact accepted generated root seed. */
    public record CandidateReport(
            String version,
            long rootSeed,
            String resolvedProbeVersion,
            String generationProbeVersion,
            String supplyProfileVersion,
            List<SystemCandidate> systems,
            Set<MissingAuthority> missingAuthorities) {
        public CandidateReport {
            version = requireText(version, "version");
            resolvedProbeVersion = requireText(resolvedProbeVersion, "resolvedProbeVersion");
            generationProbeVersion = requireText(generationProbeVersion, "generationProbeVersion");
            supplyProfileVersion = requireText(supplyProfileVersion, "supplyProfileVersion");
            ArrayList<SystemCandidate> systemCopy = new ArrayList<>(Objects.requireNonNull(
                    systems, "systems"));
            if (systemCopy.isEmpty() || systemCopy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("candidate report requires generated systems");
            }
            systemCopy.sort(Comparator.comparing(SystemCandidate::systemId));
            if (systemCopy.stream().map(SystemCandidate::systemId).distinct().count()
                    != systemCopy.size()) {
                throw new IllegalArgumentException("candidate report systems must be unique");
            }
            systems = List.copyOf(systemCopy);

            Objects.requireNonNull(missingAuthorities, "missingAuthorities");
            EnumSet<MissingAuthority> missing = missingAuthorities.isEmpty()
                    ? EnumSet.noneOf(MissingAuthority.class)
                    : EnumSet.copyOf(missingAuthorities);
            if (!missing.equals(CURRENT_MISSING_AUTHORITIES)) {
                throw new IllegalArgumentException(
                        "v1 candidate evidence must retain every unresolved operational authority");
            }
            missingAuthorities = Collections.unmodifiableSet(missing);
        }

        /** @return number of generated physical station placements represented by the report */
        public int stationCount() {
            return systems.stream().mapToInt(value -> value.stations().size()).sum();
        }

        /** @return number of exact Stage-18 facility-definition slots represented by the report */
        public int facilitySlotCount() {
            return systems.stream().flatMap(value -> value.stations().stream())
                    .mapToInt(value -> value.facilitySlots().size()).sum();
        }

        /** @return whether this candidate report can already authorize operational specialization */
        public boolean operationallyAuthoritative() {
            return missingAuthorities.isEmpty();
        }
    }

    /**
     * Reconstructs current Stage-20F candidate evidence from one accepted resolved production result.
     *
     * @param resolved exact accepted Stage-20E resolved production authority
     * @return deterministic candidate plan; no runtime industrial state or bonus is created
     */
    public static CandidateReport reconstruct(ResolvedProbeResult resolved) {
        ResolvedProbeResult accepted = Objects.requireNonNull(resolved, "resolved");
        if (accepted.seedAcceptance().status() != Status.ACCEPTED) {
            throw new IllegalArgumentException(
                    "industrial specialization candidates require an accepted resolved seed");
        }
        if (!Stage20ResolvedGeneratedWorldProductionProbe.CURRENT_VERSION.equals(accepted.version())
                || !Stage20GeneratedWorldProductionProbe.CURRENT_VERSION.equals(
                accepted.generation().version())) {
            throw new IllegalArgumentException("v1 candidate plan requires current production-probe evidence");
        }

        var generation = accepted.generation();
        List<Stage20LocalInfrastructureLayout> layouts = generation.localLayouts().orElseThrow(
                () -> new IllegalArgumentException("accepted seed lost local infrastructure layouts"));
        Stage20ResourceOccurrenceWorld resourceWorld = generation.resourceWorld().orElseThrow(
                () -> new IllegalArgumentException("accepted seed lost finite resource occurrences"));
        var logistics = generation.logisticsReport().orElseThrow(
                () -> new IllegalArgumentException("accepted seed lost extraction logistics"));
        SupplyThroughputReport supply = generation.supplyThroughput().orElseThrow(
                () -> new IllegalArgumentException("accepted seed lost physical supply evidence"));

        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var extraction = Stage18ExtractionCatalogLoader.loadDefault();
        var facilities = Stage18FacilityCatalogLoader.loadDefault();
        var refining = Stage18RefiningCatalogLoader.loadDefault();
        var manufacturing = Stage18ManufacturingCatalogLoader.loadDefault();
        var stations = Stage18StationInfrastructureCatalogLoader.loadDefault();
        if (!resourceWorld.ontologyFingerprint().equals(ontology.getFingerprint())
                || !resourceWorld.extractionFingerprint().equals(extraction.getFingerprint())
                || !resourceWorld.facilityFingerprint().equals(facilities.getFingerprint())) {
            throw new IllegalArgumentException(
                    "generated resource evidence differs from current Stage-18 content authority");
        }

        List<ExtractionCapacity> extractionCapacities =
                Stage20BootstrapProductionCapacityCalculator.extractionCapacities(
                        resourceWorld,
                        extraction,
                        facilities,
                        logistics.asExportHandlingProvider());
        List<StationProcessCapacity> processCapacities =
                Stage20BootstrapProductionCapacityCalculator.stationProcessCapacities(
                        layouts,
                        stations,
                        facilities,
                        ontology,
                        refining,
                        manufacturing);

        TreeMap<ProcessKey, ProcessThroughputEvidence> throughputByProcess = new TreeMap<>();
        for (ProcessThroughputEvidence evidence : supply.processEvidence()) {
            ProcessKey key = ProcessKey.from(evidence);
            if (throughputByProcess.putIfAbsent(key, evidence) != null) {
                throw new IllegalArgumentException("physical supply evidence contains duplicate process rows");
            }
        }
        TreeMap<ProcessKey, StationProcessCapacity> capacityByProcess = new TreeMap<>();
        for (StationProcessCapacity capacity : processCapacities) {
            ProcessKey key = ProcessKey.from(capacity);
            if (capacityByProcess.putIfAbsent(key, capacity) != null) {
                throw new IllegalArgumentException("station capacity contains duplicate process rows");
            }
        }
        if (!throughputByProcess.keySet().equals(capacityByProcess.keySet())) {
            throw new IllegalArgumentException(
                    "process throughput must exactly cover reconstructed station facility capacity");
        }

        TreeMap<StarSystemId, List<ExtractionCapacity>> extractionBySystem = new TreeMap<>();
        for (ExtractionCapacity capacity : extractionCapacities) {
            extractionBySystem.computeIfAbsent(capacity.systemId(), ignored -> new ArrayList<>()).add(capacity);
        }
        TreeMap<StarSystemId, Stage20LocalInfrastructureLayout> layoutBySystem = new TreeMap<>();
        for (Stage20LocalInfrastructureLayout layout : layouts) {
            if (layout.rootSeed() != accepted.rootSeed()
                    || layoutBySystem.putIfAbsent(layout.systemId(), layout) != null) {
                throw new IllegalArgumentException("local layouts must uniquely retain the accepted root seed");
            }
        }
        Set<StarSystemId> topologySystems = new HashSet<>();
        generation.topology().requireAcceptedTopology().systems()
                .forEach(system -> topologySystems.add(system.id()));
        if (!layoutBySystem.keySet().equals(topologySystems)) {
            throw new IllegalArgumentException("candidate layouts must exactly cover the accepted topology");
        }
        if (!topologySystems.containsAll(extractionBySystem.keySet())) {
            throw new IllegalArgumentException("candidate extraction site lies outside the accepted topology");
        }

        ArrayList<SystemCandidate> systemCandidates = new ArrayList<>();
        for (Stage20LocalInfrastructureLayout layout : layoutBySystem.values()) {
            ArrayList<StationCandidate> stationCandidates = new ArrayList<>();
            for (InfrastructurePlacement placement : layout.placements()) {
                if (!placement.isStation()) {
                    continue;
                }
                StationArchetypeDefinition archetype = Objects.requireNonNull(
                        stations.findArchetype(placement.stationArchetypeId().orElseThrow()),
                        "generated station references unknown Stage-18 archetype");
                ArrayList<FacilitySlot> slots = new ArrayList<>();
                for (int ordinal = 0; ordinal < archetype.installedFacilityDefinitionIds().size(); ordinal++) {
                    String facilityId = archetype.installedFacilityDefinitionIds().get(ordinal);
                    FacilityDefinition definition = Objects.requireNonNull(
                            facilities.findFacility(facilityId),
                            "station archetype references unknown Stage-18 facility");
                    slots.add(new FacilitySlot(ordinal, definition));
                }

                ArrayList<ProcessCandidate> processCandidates = new ArrayList<>();
                for (var entry : capacityByProcess.entrySet()) {
                    ProcessKey key = entry.getKey();
                    if (key.systemId().equals(layout.systemId())
                            && key.stationPlacementId().equals(placement.id())) {
                        processCandidates.add(ProcessCandidate.from(
                                entry.getValue(), throughputByProcess.get(key)));
                    }
                }
                stationCandidates.add(new StationCandidate(
                        layout.systemId(), placement, archetype, slots, processCandidates));
            }
            systemCandidates.add(new SystemCandidate(
                    layout.systemId(),
                    extractionBySystem.getOrDefault(layout.systemId(), List.of()),
                    stationCandidates));
        }

        return new CandidateReport(
                CURRENT_VERSION,
                accepted.rootSeed(),
                accepted.version(),
                generation.version(),
                supply.profileVersion(),
                systemCandidates,
                CURRENT_MISSING_AUTHORITIES);
    }

    private record ProcessKey(
            StarSystemId systemId,
            String stationPlacementId,
            String facilityDefinitionId,
            String processId,
            String outputCommodityId) implements Comparable<ProcessKey> {
        static ProcessKey from(StationProcessCapacity value) {
            return new ProcessKey(
                    value.systemId(),
                    value.stationPlacementId(),
                    value.facilityDefinitionId(),
                    value.processId(),
                    value.outputCommodityId());
        }

        static ProcessKey from(ProcessThroughputEvidence value) {
            return new ProcessKey(
                    value.systemId(),
                    value.stationPlacementId(),
                    value.facilityDefinitionId(),
                    value.processId(),
                    value.outputCommodityId());
        }

        @Override
        public int compareTo(ProcessKey other) {
            int comparison = systemId.compareTo(other.systemId);
            if (comparison != 0) return comparison;
            comparison = stationPlacementId.compareTo(other.stationPlacementId);
            if (comparison != 0) return comparison;
            comparison = facilityDefinitionId.compareTo(other.facilityDefinitionId);
            if (comparison != 0) return comparison;
            comparison = processId.compareTo(other.processId);
            return comparison != 0 ? comparison : outputCommodityId.compareTo(other.outputCommodityId);
        }
    }

    private static final Comparator<ProcessCandidate> PROCESS_ORDER = Comparator
            .comparing((ProcessCandidate value) -> value.capacity().facilityDefinitionId())
            .thenComparing(value -> value.capacity().processId())
            .thenComparing(value -> value.capacity().outputCommodityId());

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.strip();
    }
}
