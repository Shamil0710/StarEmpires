package com.spacesim.world;

import com.spacesim.content.Stage18FacilityCatalog;
import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18ManufacturingCatalog;
import com.spacesim.content.Stage18ManufacturingCatalog.ComponentRecipeDefinition;
import com.spacesim.content.Stage18ManufacturingCatalogLoader;
import com.spacesim.content.Stage18RefiningCatalog;
import com.spacesim.content.Stage18RefiningCatalog.RefiningRecipeDefinition;
import com.spacesim.content.Stage18RefiningCatalogLoader;
import com.spacesim.economy.Stage18FacilityRuntime;
import com.spacesim.economy.Stage18FacilityRuntime.FacilityCapabilitySnapshot;
import com.spacesim.economy.Stage18FacilityRuntime.InstalledFacilityState;
import com.spacesim.world.Stage20BootstrapProductionCapacityCalculator.ProcessKind;
import com.spacesim.world.Stage20IndustrialInputFreightOwnershipPlan.IndustrialFreightReport;
import com.spacesim.world.Stage20IndustrialInputFreightOwnershipPlan.ProcessOwnerAssignment;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.ProcessOutputRequest;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.ProcessSelectionKey;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.CandidateReport;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.FacilitySlot;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.MissingAuthority;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.ProcessCandidate;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.StationCandidate;
import com.spacesim.world.generation.Stage20ResolvedGeneratedWorldProductionProbe.ResolvedProbeResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Projects explicit Stage-18 facility state and reserves its shared station operating resources.
 *
 * <p>Every selected process is joined back to its exact generated facility slot. The caller must
 * provide the canonical installed-facility instance, its faction owner and a finite station service
 * pool. Facility power, heat rejection, labor and maintenance allocations are shared inside that
 * pool, while selected recipes share the facility snapshot's effective power/work/maintenance.
 * Station cargo transfer is also shared across all selected input and output rates.</p>
 *
 * <p>The plan creates no runtime entity and no resource supply. It validates caller-authored
 * bootstrap state against the existing Stage-18 runtime projection and retains deterministic IDs
 * that {@code Stage18StationIndustrialNode.instantiate(...)} will use later.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20IndustrialFacilityOperatingPlan {
    /** Stable Stage-20F installed-facility operating-state plan version. */
    public static final String CURRENT_VERSION =
            "stage20f.industrial-facility-operating-plan.v1";
    /** Physical location authored by the current generated orbital-station layout. */
    public static final String GENERATED_STATION_LOCATION_TAG = "location.orbital_station";
    private static final double EPSILON = 1.0e-9d;

    private Stage20IndustrialFacilityOperatingPlan() {
        throw new AssertionError("No instances");
    }

    /** Final shared operating-state result. */
    public enum Status {
        /** Every selected process fits active facility and shared station resource limits. */
        ACCEPTED,
        /** At least one facility or station shared resource is insufficient. */
        INSUFFICIENT_OPERATING_CAPABILITY
    }

    /** Machine-readable failure for a non-authoritative operating result. */
    public enum FailureReason {
        /** Explicit facility state or shared station services cannot sustain the selection. */
        FACILITY_OR_STATION_RESOURCE_CONFLICT
    }

    /**
     * Exact generated station identity.
     *
     * @param systemId physical system
     * @param stationPlacementId exact generated station placement
     */
    public record StationKey(
            StarSystemId systemId,
            String stationPlacementId) implements Comparable<StationKey> {
        /**
         * Validates one generated station identity.
         *
         * @param systemId physical system
         * @param stationPlacementId exact generated station placement
         */
        public StationKey {
            Objects.requireNonNull(systemId, "systemId");
            stationPlacementId = requireText(stationPlacementId, "stationPlacementId");
        }

        /** Orders physical stations deterministically. */
        @Override
        public int compareTo(StationKey other) {
            int comparison = systemId.compareTo(other.systemId);
            return comparison != 0
                    ? comparison
                    : stationPlacementId.compareTo(other.stationPlacementId);
        }
    }

    /**
     * Exact generated installed-facility slot identity.
     *
     * @param station owning generated station
     * @param facilityDefinitionId exact Stage-18 facility definition
     */
    public record FacilitySlotKey(
            StationKey station,
            String facilityDefinitionId) implements Comparable<FacilitySlotKey> {
        /**
         * Validates one generated facility slot identity.
         *
         * @param station owning generated station
         * @param facilityDefinitionId exact Stage-18 facility definition
         */
        public FacilitySlotKey {
            Objects.requireNonNull(station, "station");
            facilityDefinitionId = requireText(facilityDefinitionId, "facilityDefinitionId");
        }

        /**
         * Creates the slot identity behind one selected process.
         *
         * @param process exact selected process
         * @return generated facility slot identity
         */
        public static FacilitySlotKey from(ProcessSelectionKey process) {
            ProcessSelectionKey value = Objects.requireNonNull(process, "process");
            return new FacilitySlotKey(
                    new StationKey(value.systemId(), value.stationPlacementId()),
                    value.facilityDefinitionId());
        }

        /** Orders physical facility slots deterministically. */
        @Override
        public int compareTo(FacilitySlotKey other) {
            int comparison = station.compareTo(other.station);
            return comparison != 0
                    ? comparison
                    : facilityDefinitionId.compareTo(other.facilityDefinitionId);
        }
    }

    /**
     * Finite station-level service pool shared by selected facility allocations.
     *
     * @param station exact generated station
     * @param availableProcessPowerW total allocatable process power
     * @param availableHeatRejectionW total allocatable heat rejection
     * @param availableLaborUnits total allocatable staffed labor-equivalent units
     * @param availableMaintenanceWorkRate total allocatable maintenance work per second
     */
    public record StationServiceAllocation(
            StationKey station,
            double availableProcessPowerW,
            double availableHeatRejectionW,
            double availableLaborUnits,
            double availableMaintenanceWorkRate) {
        /**
         * Validates one finite station service pool.
         *
         * @param station exact generated station
         * @param availableProcessPowerW total allocatable process power
         * @param availableHeatRejectionW total allocatable heat rejection
         * @param availableLaborUnits total allocatable staffed labor-equivalent units
         * @param availableMaintenanceWorkRate total allocatable maintenance work per second
         */
        public StationServiceAllocation {
            Objects.requireNonNull(station, "station");
            requireNonNegativeFinite(availableProcessPowerW, "availableProcessPowerW");
            requireNonNegativeFinite(availableHeatRejectionW, "availableHeatRejectionW");
            requireNonNegativeFinite(availableLaborUnits, "availableLaborUnits");
            requireNonNegativeFinite(
                    availableMaintenanceWorkRate, "availableMaintenanceWorkRate");
        }
    }

    /**
     * Explicit world-authored state for one exact generated facility slot.
     *
     * @param slot exact generated facility slot
     * @param stableFactionId explicit owning faction
     * @param state exact Stage-18 installed-facility state
     */
    public record FacilityStateAssignment(
            FacilitySlotKey slot,
            String stableFactionId,
            InstalledFacilityState state) {
        /**
         * Validates one explicit facility-state assignment.
         *
         * @param slot exact generated facility slot
         * @param stableFactionId explicit owning faction
         * @param state exact Stage-18 installed-facility state
         */
        public FacilityStateAssignment {
            Objects.requireNonNull(slot, "slot");
            stableFactionId = WorldFactionIdentityState.normalizeStableId(stableFactionId);
            Objects.requireNonNull(state, "state");
            if (!slot.facilityDefinitionId().equals(state.definitionId())) {
                throw new IllegalArgumentException("facility state definition differs from its slot");
            }
        }
    }

    /**
     * Versioned explicit operating-state authority for one selected industrial plan.
     *
     * @param version caller-defined authority version
     * @param rootSeed exact accepted generated root seed
     * @param stationServices exact selected-station service pools
     * @param facilities exact selected-facility installed states
     */
    public record OperatingStateAuthority(
            String version,
            long rootSeed,
            List<StationServiceAllocation> stationServices,
            List<FacilityStateAssignment> facilities) {
        /**
         * Canonicalizes one explicit operating-state authority.
         *
         * @param version caller-defined authority version
         * @param rootSeed exact accepted generated root seed
         * @param stationServices exact selected-station service pools
         * @param facilities exact selected-facility installed states
         */
        public OperatingStateAuthority {
            version = requireText(version, "version");
            ArrayList<StationServiceAllocation> services = new ArrayList<>(Objects.requireNonNull(
                    stationServices, "stationServices"));
            ArrayList<FacilityStateAssignment> states = new ArrayList<>(Objects.requireNonNull(
                    facilities, "facilities"));
            if (services.isEmpty() || states.isEmpty()
                    || services.stream().anyMatch(Objects::isNull)
                    || states.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException(
                        "operating authority requires non-empty station and facility state");
            }
            services.sort(Comparator.comparing(StationServiceAllocation::station));
            states.sort(Comparator.comparing(FacilityStateAssignment::slot));
            if (services.stream().map(StationServiceAllocation::station).distinct().count()
                    != services.size()
                    || states.stream().map(FacilityStateAssignment::slot).distinct().count()
                    != states.size()) {
                throw new IllegalArgumentException(
                        "operating authority station/facility identities must be unique");
            }
            stationServices = List.copyOf(services);
            facilities = List.copyOf(states);
        }
    }

    /**
     * Exact continuous resource demand of one selected Stage-18 recipe.
     *
     * @param process exact selected process
     * @param facility exact consuming facility slot
     * @param stableFactionId explicit owner
     * @param requestedOutputKgPerSecond selected output rate
     * @param requiredProcessPowerW required continuous process power
     * @param requiredEngineeringWorkRate required engineering work per second
     * @param requiredMaintenanceWorkRate required maintenance work per second
     * @param requiredCapabilityTags exact Stage-18 recipe capabilities
     */
    public record ProcessOperatingDemand(
            ProcessSelectionKey process,
            FacilitySlotKey facility,
            String stableFactionId,
            double requestedOutputKgPerSecond,
            double requiredProcessPowerW,
            double requiredEngineeringWorkRate,
            double requiredMaintenanceWorkRate,
            Set<String> requiredCapabilityTags) {
        /**
         * Validates one exact positive process resource demand.
         *
         * @param process exact selected process
         * @param facility exact consuming facility slot
         * @param stableFactionId explicit owner
         * @param requestedOutputKgPerSecond selected output rate
         * @param requiredProcessPowerW required continuous process power
         * @param requiredEngineeringWorkRate required engineering work per second
         * @param requiredMaintenanceWorkRate required maintenance work per second
         * @param requiredCapabilityTags exact Stage-18 recipe capabilities
         */
        public ProcessOperatingDemand {
            Objects.requireNonNull(process, "process");
            Objects.requireNonNull(facility, "facility");
            if (!facility.equals(FacilitySlotKey.from(process))) {
                throw new IllegalArgumentException("process demand facility differs from process identity");
            }
            stableFactionId = WorldFactionIdentityState.normalizeStableId(stableFactionId);
            requirePositiveFinite(requestedOutputKgPerSecond, "requestedOutputKgPerSecond");
            requirePositiveFinite(requiredProcessPowerW, "requiredProcessPowerW");
            requirePositiveFinite(requiredEngineeringWorkRate, "requiredEngineeringWorkRate");
            requirePositiveFinite(requiredMaintenanceWorkRate, "requiredMaintenanceWorkRate");
            requiredCapabilityTags = immutableTextSet(
                    requiredCapabilityTags, "requiredCapabilityTags");
            if (requiredCapabilityTags.isEmpty()) {
                throw new IllegalArgumentException("process demand requires capability tags");
            }
        }
    }

    /**
     * Effective state and shared selected-process demand for one installed facility.
     *
     * @param assignment exact caller-authored installed state
     * @param snapshot exact Stage-18 effective projection
     * @param processDemands selected processes sharing this facility
     * @param requiredProcessPowerW summed selected process power
     * @param requiredEngineeringWorkRate summed selected engineering work
     * @param requiredMaintenanceWorkRate summed selected maintenance work
     * @param status whether this facility can sustain every selected rate together
     */
    public record FacilityOperatingEvidence(
            FacilityStateAssignment assignment,
            FacilityCapabilitySnapshot snapshot,
            List<ProcessOperatingDemand> processDemands,
            double requiredProcessPowerW,
            double requiredEngineeringWorkRate,
            double requiredMaintenanceWorkRate,
            Status status) {
        /**
         * Validates one immutable facility operating row.
         *
         * @param assignment exact caller-authored installed state
         * @param snapshot exact Stage-18 effective projection
         * @param processDemands selected processes sharing this facility
         * @param requiredProcessPowerW summed selected process power
         * @param requiredEngineeringWorkRate summed selected engineering work
         * @param requiredMaintenanceWorkRate summed selected maintenance work
         * @param status whether this facility can sustain every selected rate together
         */
        public FacilityOperatingEvidence {
            Objects.requireNonNull(assignment, "assignment");
            Objects.requireNonNull(snapshot, "snapshot");
            ArrayList<ProcessOperatingDemand> demands = new ArrayList<>(Objects.requireNonNull(
                    processDemands, "processDemands"));
            if (demands.isEmpty() || demands.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("facility evidence requires selected process demands");
            }
            demands.sort(Comparator.comparing(ProcessOperatingDemand::process));
            if (demands.stream().map(ProcessOperatingDemand::process).distinct().count()
                    != demands.size()) {
                throw new IllegalArgumentException("facility process demands must be unique");
            }
            processDemands = List.copyOf(demands);
            requirePositiveFinite(requiredProcessPowerW, "requiredProcessPowerW");
            requirePositiveFinite(requiredEngineeringWorkRate, "requiredEngineeringWorkRate");
            requirePositiveFinite(requiredMaintenanceWorkRate, "requiredMaintenanceWorkRate");
            Objects.requireNonNull(status, "status");
            if (!snapshot.facilityInstanceId().equals(assignment.state().facilityInstanceId())
                    || !snapshot.definitionId().equals(assignment.state().definitionId())
                    || demands.stream().anyMatch(value ->
                    !value.facility().equals(assignment.slot())
                            || !value.stableFactionId().equals(assignment.stableFactionId()))) {
                throw new IllegalArgumentException(
                        "facility evidence must retain its exact state, owner and process demands");
            }
            close(requiredProcessPowerW,
                    sum(demands, ProcessOperatingDemand::requiredProcessPowerW),
                    "requiredProcessPowerW");
            close(requiredEngineeringWorkRate,
                    sum(demands, ProcessOperatingDemand::requiredEngineeringWorkRate),
                    "requiredEngineeringWorkRate");
            close(requiredMaintenanceWorkRate,
                    sum(demands, ProcessOperatingDemand::requiredMaintenanceWorkRate),
                    "requiredMaintenanceWorkRate");
            boolean capable = snapshot.status() == Stage18FacilityRuntime.Status.ACTIVE
                    && demands.stream().allMatch(value ->
                    snapshot.capabilityTags().containsAll(value.requiredCapabilityTags()))
                    && requiredProcessPowerW <= snapshot.effectiveProcessPowerW() + EPSILON
                    && requiredEngineeringWorkRate
                    <= snapshot.effectiveEngineeringWorkRate() + EPSILON
                    && requiredMaintenanceWorkRate
                    <= snapshot.effectiveMaintenanceWorkRate() + EPSILON;
            if ((status == Status.ACCEPTED) != capable) {
                throw new IllegalArgumentException(
                        "facility status differs from projected shared process capability");
            }
        }
    }

    /**
     * Shared resource and cargo-transfer accounting for one selected generated station.
     *
     * @param station exact generated station
     * @param stationArchetypeId exact Stage-18 station archetype
     * @param services caller-authored finite station service pool
     * @param facilitySlots selected facility slots at this station
     * @param allocatedProcessPowerW summed facility allocation
     * @param allocatedHeatRejectionW summed facility allocation
     * @param allocatedLaborUnits summed facility allocation
     * @param allocatedMaintenanceWorkRate summed facility allocation
     * @param requiredCargoTransferKgPerSecond selected input plus output transfer rate
     * @param availableCargoTransferKgPerSecond physical archetype transfer ceiling
     * @param status whether shared station resources sustain the selected plan
     */
    public record StationOperatingEvidence(
            StationKey station,
            String stationArchetypeId,
            StationServiceAllocation services,
            List<FacilitySlotKey> facilitySlots,
            double allocatedProcessPowerW,
            double allocatedHeatRejectionW,
            double allocatedLaborUnits,
            double allocatedMaintenanceWorkRate,
            double requiredCargoTransferKgPerSecond,
            double availableCargoTransferKgPerSecond,
            Status status) {
        /**
         * Validates one immutable station operating row.
         *
         * @param station exact generated station
         * @param stationArchetypeId exact Stage-18 station archetype
         * @param services caller-authored finite station service pool
         * @param facilitySlots selected facility slots at this station
         * @param allocatedProcessPowerW summed facility allocation
         * @param allocatedHeatRejectionW summed facility allocation
         * @param allocatedLaborUnits summed facility allocation
         * @param allocatedMaintenanceWorkRate summed facility allocation
         * @param requiredCargoTransferKgPerSecond selected input plus output transfer rate
         * @param availableCargoTransferKgPerSecond physical archetype transfer ceiling
         * @param status whether shared station resources sustain the selected plan
         */
        public StationOperatingEvidence {
            Objects.requireNonNull(station, "station");
            stationArchetypeId = requireText(stationArchetypeId, "stationArchetypeId");
            Objects.requireNonNull(services, "services");
            if (!services.station().equals(station)) {
                throw new IllegalArgumentException("station services target a different station");
            }
            ArrayList<FacilitySlotKey> slots = new ArrayList<>(Objects.requireNonNull(
                    facilitySlots, "facilitySlots"));
            slots.sort(FacilitySlotKey::compareTo);
            if (slots.isEmpty() || slots.stream().anyMatch(Objects::isNull)
                    || slots.stream().anyMatch(value -> !value.station().equals(station))
                    || slots.stream().distinct().count() != slots.size()) {
                throw new IllegalArgumentException("station facility slots must be non-empty and unique");
            }
            facilitySlots = List.copyOf(slots);
            requireNonNegativeFinite(allocatedProcessPowerW, "allocatedProcessPowerW");
            requireNonNegativeFinite(allocatedHeatRejectionW, "allocatedHeatRejectionW");
            requireNonNegativeFinite(allocatedLaborUnits, "allocatedLaborUnits");
            requireNonNegativeFinite(allocatedMaintenanceWorkRate, "allocatedMaintenanceWorkRate");
            requirePositiveFinite(requiredCargoTransferKgPerSecond, "requiredCargoTransferKgPerSecond");
            requirePositiveFinite(availableCargoTransferKgPerSecond, "availableCargoTransferKgPerSecond");
            Objects.requireNonNull(status, "status");
            boolean capable = allocatedProcessPowerW <= services.availableProcessPowerW() + EPSILON
                    && allocatedHeatRejectionW <= services.availableHeatRejectionW() + EPSILON
                    && allocatedLaborUnits <= services.availableLaborUnits() + EPSILON
                    && allocatedMaintenanceWorkRate
                    <= services.availableMaintenanceWorkRate() + EPSILON
                    && requiredCargoTransferKgPerSecond
                    <= availableCargoTransferKgPerSecond + EPSILON;
            if ((status == Status.ACCEPTED) != capable) {
                throw new IllegalArgumentException(
                        "station status differs from shared services and cargo transfer");
            }
        }
    }

    /**
     * Complete fail-closed installed-facility operating-state result.
     *
     * @param version plan contract version
     * @param rootSeed exact accepted generated root seed
     * @param resolvedProbeVersion exact resolved production evidence version
     * @param candidatePlanVersion exact candidate reconstruction version
     * @param facilityCatalogFingerprint exact Stage-18 facility content authority
     * @param freightOwnership accepted preceding industrial freight authority
     * @param authority exact caller-authored operating state
     * @param status final shared operating status
     * @param failureReason absent only when accepted
     * @param processes exact selected recipe resource demands
     * @param facilities exact projected facility evidence
     * @param stations exact shared station evidence
     * @param missingAuthorities authorities still blocking operational specialization
     */
    public record OperatingReport(
            String version,
            long rootSeed,
            String resolvedProbeVersion,
            String candidatePlanVersion,
            String facilityCatalogFingerprint,
            IndustrialFreightReport freightOwnership,
            OperatingStateAuthority authority,
            Status status,
            Optional<FailureReason> failureReason,
            List<ProcessOperatingDemand> processes,
            List<FacilityOperatingEvidence> facilities,
            List<StationOperatingEvidence> stations,
            Set<MissingAuthority> missingAuthorities) {
        /**
         * Canonicalizes and validates one immutable operating-state result.
         *
         * @param version plan contract version
         * @param rootSeed exact accepted generated root seed
         * @param resolvedProbeVersion exact resolved production evidence version
         * @param candidatePlanVersion exact candidate reconstruction version
         * @param facilityCatalogFingerprint exact Stage-18 facility content authority
         * @param freightOwnership accepted preceding industrial freight authority
         * @param authority exact caller-authored operating state
         * @param status final shared operating status
         * @param failureReason absent only when accepted
         * @param processes exact selected recipe resource demands
         * @param facilities exact projected facility evidence
         * @param stations exact shared station evidence
         * @param missingAuthorities authorities still blocking operational specialization
         */
        public OperatingReport {
            version = requireText(version, "version");
            resolvedProbeVersion = requireText(resolvedProbeVersion, "resolvedProbeVersion");
            candidatePlanVersion = requireText(candidatePlanVersion, "candidatePlanVersion");
            facilityCatalogFingerprint = requireText(
                    facilityCatalogFingerprint, "facilityCatalogFingerprint");
            Objects.requireNonNull(freightOwnership, "freightOwnership");
            Objects.requireNonNull(authority, "authority");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(failureReason, "failureReason");
            if (rootSeed != freightOwnership.rootSeed() || rootSeed != authority.rootSeed()) {
                throw new IllegalArgumentException("operating-state authorities target different root seeds");
            }
            if (!freightOwnership.freightOwnershipAuthoritative()) {
                throw new IllegalArgumentException("facility operation requires accepted freight ownership");
            }
            if (!CURRENT_VERSION.equals(version)
                    || !Stage18FacilityCatalogLoader.loadDefault().getFingerprint()
                    .equals(facilityCatalogFingerprint)) {
                throw new IllegalArgumentException(
                        "operating report version/catalog authority differs from current contract");
            }
            if ((status == Status.ACCEPTED) != failureReason.isEmpty()) {
                throw new IllegalArgumentException("failure reason must be absent exactly when accepted");
            }

            ArrayList<ProcessOperatingDemand> processCopy = new ArrayList<>(Objects.requireNonNull(
                    processes, "processes"));
            ArrayList<FacilityOperatingEvidence> facilityCopy = new ArrayList<>(Objects.requireNonNull(
                    facilities, "facilities"));
            ArrayList<StationOperatingEvidence> stationCopy = new ArrayList<>(Objects.requireNonNull(
                    stations, "stations"));
            processCopy.sort(Comparator.comparing(ProcessOperatingDemand::process));
            facilityCopy.sort(Comparator.comparing(value -> value.assignment().slot()));
            stationCopy.sort(Comparator.comparing(StationOperatingEvidence::station));
            if (processCopy.isEmpty() || facilityCopy.isEmpty() || stationCopy.isEmpty()
                    || processCopy.stream().anyMatch(Objects::isNull)
                    || facilityCopy.stream().anyMatch(Objects::isNull)
                    || stationCopy.stream().anyMatch(Objects::isNull)
                    || processCopy.stream().map(ProcessOperatingDemand::process).distinct().count()
                    != processCopy.size()
                    || facilityCopy.stream().map(value -> value.assignment().slot()).distinct().count()
                    != facilityCopy.size()
                    || stationCopy.stream().map(StationOperatingEvidence::station).distinct().count()
                    != stationCopy.size()) {
                throw new IllegalArgumentException("operating report evidence must be non-empty and unique");
            }
            processes = List.copyOf(processCopy);
            facilities = List.copyOf(facilityCopy);
            stations = List.copyOf(stationCopy);
            validateReportCoverage(freightOwnership, authority, processes, facilities, stations);
            validateDerivedProcessDemands(freightOwnership, processes);

            boolean allAccepted = facilities.stream().allMatch(value -> value.status() == Status.ACCEPTED)
                    && stations.stream().allMatch(value -> value.status() == Status.ACCEPTED);
            if ((status == Status.ACCEPTED) != allAccepted) {
                throw new IllegalArgumentException("operating report status differs from facility/station evidence");
            }
            Objects.requireNonNull(missingAuthorities, "missingAuthorities");
            EnumSet<MissingAuthority> expected = EnumSet.copyOf(
                    freightOwnership.missingAuthorities());
            if (status == Status.ACCEPTED) {
                expected.remove(MissingAuthority.INSTALLED_FACILITY_OPERATING_STATE);
            }
            EnumSet<MissingAuthority> actual = missingAuthorities.isEmpty()
                    ? EnumSet.noneOf(MissingAuthority.class)
                    : EnumSet.copyOf(missingAuthorities);
            if (!actual.equals(expected)) {
                throw new IllegalArgumentException(
                        "operating report cannot silently change another authority");
            }
            missingAuthorities = immutableAuthorities(actual);
        }

        /** @return whether selected facilities have authoritative shared operating state */
        public boolean facilityOperatingStateAuthoritative() {
            return status == Status.ACCEPTED
                    && !missingAuthorities.contains(
                    MissingAuthority.INSTALLED_FACILITY_OPERATING_STATE);
        }

        /** @return whether every operational specialization authority is present */
        public boolean operationallyAuthoritative() {
            return missingAuthorities.isEmpty();
        }
    }

    /**
     * Validates explicit operating state for an accepted industrial freight plan.
     *
     * @param resolved exact accepted generated-world authority
     * @param freightOwnership exact accepted industrial freight ownership
     * @param authority explicit facility and station operating allocations
     * @return deterministic accepted or fail-closed operating-state report
     */
    public static OperatingReport plan(
            ResolvedProbeResult resolved,
            IndustrialFreightReport freightOwnership,
            OperatingStateAuthority authority) {
        ResolvedProbeResult accepted = Objects.requireNonNull(resolved, "resolved");
        IndustrialFreightReport freight = Objects.requireNonNull(
                freightOwnership, "freightOwnership");
        OperatingStateAuthority state = Objects.requireNonNull(authority, "authority");
        if (accepted.rootSeed() != freight.rootSeed()
                || !freight.freightOwnershipAuthoritative()
                || !freight.reservation().resolvedProbeVersion().equals(accepted.version())) {
            throw new IllegalArgumentException(
                    "facility operation requires matching accepted generated freight evidence");
        }

        CandidateReport candidates = Stage20IndustrialSpecializationCandidatePlan.reconstruct(accepted);
        Stage18FacilityCatalog facilityCatalog = Stage18FacilityCatalogLoader.loadDefault();
        Stage18RefiningCatalog refining = Stage18RefiningCatalogLoader.loadDefault();
        Stage18ManufacturingCatalog manufacturing = Stage18ManufacturingCatalogLoader.loadDefault();
        return planEvidence(
                freight,
                candidates,
                facilityCatalog,
                refining,
                manufacturing,
                state);
    }

    static OperatingReport planEvidence(
            IndustrialFreightReport freightOwnership,
            CandidateReport candidates,
            Stage18FacilityCatalog facilityCatalog,
            Stage18RefiningCatalog refining,
            Stage18ManufacturingCatalog manufacturing,
            OperatingStateAuthority authority) {
        IndustrialFreightReport freight = Objects.requireNonNull(
                freightOwnership, "freightOwnership");
        CandidateReport candidateReport = Objects.requireNonNull(candidates, "candidates");
        Stage18FacilityCatalog catalog = Objects.requireNonNull(facilityCatalog, "facilityCatalog");
        Stage18RefiningCatalog refiningCatalog = Objects.requireNonNull(refining, "refining");
        Stage18ManufacturingCatalog manufacturingCatalog = Objects.requireNonNull(
                manufacturing, "manufacturing");
        OperatingStateAuthority state = Objects.requireNonNull(authority, "authority");
        if (!freight.freightOwnershipAuthoritative()
                || freight.rootSeed() != candidateReport.rootSeed()
                || state.rootSeed() != freight.rootSeed()) {
            throw new IllegalArgumentException("operating-state evidence targets different authorities");
        }

        EvidenceIndex index = EvidenceIndex.build(candidateReport);
        TreeMap<ProcessSelectionKey, String> ownerByProcess = new TreeMap<>();
        for (ProcessOwnerAssignment owner : freight.processOwnership().assignments()) {
            ownerByProcess.put(owner.process(), owner.stableFactionId());
        }
        TreeMap<ProcessSelectionKey, ProcessOutputRequest> requestByProcess = new TreeMap<>();
        for (ProcessOutputRequest request : freight.reservation().selection().requests()) {
            requestByProcess.put(request.process(), request);
        }
        if (!requestByProcess.keySet().equals(ownerByProcess.keySet())) {
            throw new IllegalArgumentException("selected process owner coverage changed before operation");
        }

        TreeMap<FacilitySlotKey, FacilityStateAssignment> assignmentBySlot = new TreeMap<>();
        state.facilities().forEach(value -> assignmentBySlot.put(value.slot(), value));
        TreeMap<StationKey, StationServiceAllocation> serviceByStation = new TreeMap<>();
        state.stationServices().forEach(value -> serviceByStation.put(value.station(), value));
        Set<FacilitySlotKey> selectedSlots = requestByProcess.keySet().stream()
                .map(FacilitySlotKey::from)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<StationKey> selectedStations = selectedSlots.stream()
                .map(FacilitySlotKey::station)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!assignmentBySlot.keySet().equals(selectedSlots)
                || !serviceByStation.keySet().equals(selectedStations)) {
            throw new IllegalArgumentException(
                    "operating authority must exactly cover selected facilities and stations");
        }

        ArrayList<ProcessOperatingDemand> processDemands = new ArrayList<>();
        for (ProcessOutputRequest request : requestByProcess.values()) {
            ProcessCandidate candidate = index.processes().get(request.process());
            if (candidate == null) {
                throw new IllegalArgumentException("selected process is absent from candidate evidence");
            }
            processDemands.add(processDemand(
                    candidate,
                    request,
                    ownerByProcess.get(request.process()),
                    refiningCatalog,
                    manufacturingCatalog));
        }
        processDemands.sort(Comparator.comparing(ProcessOperatingDemand::process));

        Stage18FacilityRuntime runtime = new Stage18FacilityRuntime(catalog);
        ArrayList<FacilityOperatingEvidence> facilityEvidence = new ArrayList<>();
        for (FacilitySlotKey slot : selectedSlots.stream().sorted().toList()) {
            FacilityStateAssignment assignment = assignmentBySlot.get(slot);
            SlotEvidence physical = index.slots().get(slot);
            if (physical == null) {
                throw new IllegalArgumentException("selected facility slot is absent from candidate evidence");
            }
            String canonicalId = canonicalFacilityInstanceId(
                    slot.station().stationPlacementId(), physical.facilityOrdinal());
            if (!assignment.state().facilityInstanceId().equals(canonicalId)
                    || !assignment.state().locationTag().equals(GENERATED_STATION_LOCATION_TAG)) {
                throw new IllegalArgumentException(
                        "installed facility state must use canonical generated station identity/location");
            }
            List<ProcessOperatingDemand> demands = processDemands.stream()
                    .filter(value -> value.facility().equals(slot))
                    .toList();
            if (demands.stream().anyMatch(value ->
                    !value.stableFactionId().equals(assignment.stableFactionId()))) {
                throw new IllegalArgumentException(
                        "process and installed facility owners must match exactly");
            }
            FacilityCapabilitySnapshot snapshot = runtime.project(assignment.state());
            double power = sum(demands, ProcessOperatingDemand::requiredProcessPowerW);
            double work = sum(demands, ProcessOperatingDemand::requiredEngineeringWorkRate);
            double maintenance = sum(demands, ProcessOperatingDemand::requiredMaintenanceWorkRate);
            boolean capable = snapshot.status() == Stage18FacilityRuntime.Status.ACTIVE
                    && demands.stream().allMatch(value ->
                    snapshot.capabilityTags().containsAll(value.requiredCapabilityTags()))
                    && power <= snapshot.effectiveProcessPowerW() + EPSILON
                    && work <= snapshot.effectiveEngineeringWorkRate() + EPSILON
                    && maintenance <= snapshot.effectiveMaintenanceWorkRate() + EPSILON;
            facilityEvidence.add(new FacilityOperatingEvidence(
                    assignment,
                    snapshot,
                    demands,
                    power,
                    work,
                    maintenance,
                    capable ? Status.ACCEPTED : Status.INSUFFICIENT_OPERATING_CAPABILITY));
        }

        ArrayList<StationOperatingEvidence> stationEvidence = new ArrayList<>();
        for (StationKey station : selectedStations.stream().sorted().toList()) {
            StationCandidate physical = index.stations().get(station);
            StationServiceAllocation services = serviceByStation.get(station);
            List<FacilityStateAssignment> stationAssignments = state.facilities().stream()
                    .filter(value -> value.slot().station().equals(station))
                    .toList();
            double allocatedPower = sumStates(
                    stationAssignments, value -> value.state().allocatedProcessPowerW());
            double allocatedHeat = sumStates(
                    stationAssignments, value -> value.state().availableHeatRejectionW());
            double allocatedLabor = sumStates(
                    stationAssignments, value -> value.state().availableLaborUnits());
            double allocatedMaintenance = sumStates(
                    stationAssignments, value -> value.state().availableMaintenanceWorkRate());
            double outputTransfer = requestByProcess.values().stream()
                    .filter(value -> FacilitySlotKey.from(value.process()).station().equals(station))
                    .mapToDouble(ProcessOutputRequest::requestedOutputKgPerSecond)
                    .sum();
            double inputTransfer = freight.reservation().reservations().stream()
                    .filter(value -> new StationKey(
                            value.process().systemId(), value.process().stationPlacementId()).equals(station))
                    .mapToDouble(value -> value.reservedInputKgPerSecond())
                    .sum();
            double requiredTransfer = finiteAdd(outputTransfer, inputTransfer);
            boolean capable = allocatedPower <= services.availableProcessPowerW() + EPSILON
                    && allocatedHeat <= services.availableHeatRejectionW() + EPSILON
                    && allocatedLabor <= services.availableLaborUnits() + EPSILON
                    && allocatedMaintenance <= services.availableMaintenanceWorkRate() + EPSILON
                    && requiredTransfer
                    <= physical.archetype().transferMassRateKgPerSecond() + EPSILON;
            stationEvidence.add(new StationOperatingEvidence(
                    station,
                    physical.archetype().id(),
                    services,
                    stationAssignments.stream().map(FacilityStateAssignment::slot).toList(),
                    allocatedPower,
                    allocatedHeat,
                    allocatedLabor,
                    allocatedMaintenance,
                    requiredTransfer,
                    physical.archetype().transferMassRateKgPerSecond(),
                    capable ? Status.ACCEPTED : Status.INSUFFICIENT_OPERATING_CAPABILITY));
        }

        boolean accepted = facilityEvidence.stream().allMatch(value -> value.status() == Status.ACCEPTED)
                && stationEvidence.stream().allMatch(value -> value.status() == Status.ACCEPTED);
        EnumSet<MissingAuthority> missing = EnumSet.copyOf(freight.missingAuthorities());
        if (accepted) {
            missing.remove(MissingAuthority.INSTALLED_FACILITY_OPERATING_STATE);
        }
        return new OperatingReport(
                CURRENT_VERSION,
                freight.rootSeed(),
                freight.reservation().resolvedProbeVersion(),
                candidateReport.version(),
                catalog.getFingerprint(),
                freight,
                state,
                accepted ? Status.ACCEPTED : Status.INSUFFICIENT_OPERATING_CAPABILITY,
                accepted
                        ? Optional.empty()
                        : Optional.of(FailureReason.FACILITY_OR_STATION_RESOURCE_CONFLICT),
                processDemands,
                facilityEvidence,
                stationEvidence,
                missing);
    }

    /**
     * Returns the canonical Stage-18F facility identity for a generated station slot.
     *
     * @param stationPlacementId generated station identity
     * @param facilityOrdinal canonical archetype facility ordinal
     * @return deterministic runtime-compatible facility instance ID
     */
    public static String canonicalFacilityInstanceId(
            String stationPlacementId,
            int facilityOrdinal) {
        String station = requireText(stationPlacementId, "stationPlacementId");
        if (facilityOrdinal < 0) {
            throw new IllegalArgumentException("facilityOrdinal must be non-negative");
        }
        return station + ".facility." + facilityOrdinal;
    }

    private static ProcessOperatingDemand processDemand(
            ProcessCandidate candidate,
            ProcessOutputRequest request,
            String owner,
            Stage18RefiningCatalog refining,
            Stage18ManufacturingCatalog manufacturing) {
        var capacity = candidate.capacity();
        double output = request.requestedOutputKgPerSecond();
        if (capacity.processKind() == ProcessKind.REFINING) {
            RefiningRecipeDefinition recipe = refining.findRecipe(capacity.processId());
            if (recipe == null || !recipe.outputCommodityId().equals(capacity.outputCommodityId())) {
                throw new IllegalArgumentException("selected refining process differs from Stage-18 recipe");
            }
            double grossInput = finiteDivide(output, recipe.outputMassFraction());
            return new ProcessOperatingDemand(
                    request.process(),
                    FacilitySlotKey.from(request.process()),
                    owner,
                    output,
                    finiteMultiply(grossInput, recipe.energyJPerInputKg()),
                    finiteMultiply(grossInput, recipe.workSecondsPerInputKg()),
                    finiteMultiply(grossInput, recipe.maintenanceWorkSecondsPerInputKg()),
                    recipe.requiredCapabilityTags());
        }
        if (capacity.processKind() == ProcessKind.COMPONENT_MANUFACTURING) {
            ComponentRecipeDefinition recipe = manufacturing.findComponentRecipe(capacity.processId());
            if (recipe == null || !recipe.outputCommodityId().equals(capacity.outputCommodityId())) {
                throw new IllegalArgumentException(
                        "selected manufacturing process differs from Stage-18 recipe");
            }
            return new ProcessOperatingDemand(
                    request.process(),
                    FacilitySlotKey.from(request.process()),
                    owner,
                    output,
                    finiteMultiply(output, recipe.energyJPerOutputKg()),
                    finiteMultiply(output, recipe.workSecondsPerOutputKg()),
                    finiteMultiply(output, recipe.maintenanceWorkSecondsPerOutputKg()),
                    recipe.requiredCapabilityTags());
        }
        throw new IllegalArgumentException("unsupported selected process kind");
    }

    private static void validateDerivedProcessDemands(
            IndustrialFreightReport freight,
            List<ProcessOperatingDemand> actual) {
        TreeMap<ProcessSelectionKey, String> owners = new TreeMap<>();
        freight.processOwnership().assignments().forEach(value ->
                owners.put(value.process(), value.stableFactionId()));
        Stage18RefiningCatalog refining = Stage18RefiningCatalogLoader.loadDefault();
        Stage18ManufacturingCatalog manufacturing = Stage18ManufacturingCatalogLoader.loadDefault();
        ArrayList<ProcessOperatingDemand> expected = new ArrayList<>();
        for (ProcessOutputRequest request : freight.reservation().selection().requests()) {
            String owner = owners.get(request.process());
            if (owner == null) {
                throw new IllegalArgumentException(
                        "operating report lost explicit selected-process ownership");
            }
            RefiningRecipeDefinition refiningRecipe = refining.findRecipe(
                    request.process().processId());
            ComponentRecipeDefinition manufacturingRecipe = manufacturing.findComponentRecipe(
                    request.process().processId());
            boolean isRefining = refiningRecipe != null
                    && refiningRecipe.outputCommodityId().equals(
                    request.process().outputCommodityId());
            boolean isManufacturing = manufacturingRecipe != null
                    && manufacturingRecipe.outputCommodityId().equals(
                    request.process().outputCommodityId());
            if (isRefining == isManufacturing) {
                throw new IllegalArgumentException(
                        "selected process does not resolve to one exact Stage-18 recipe");
            }
            double output = request.requestedOutputKgPerSecond();
            if (isRefining) {
                double grossInput = finiteDivide(output, refiningRecipe.outputMassFraction());
                expected.add(new ProcessOperatingDemand(
                        request.process(),
                        FacilitySlotKey.from(request.process()),
                        owner,
                        output,
                        finiteMultiply(grossInput, refiningRecipe.energyJPerInputKg()),
                        finiteMultiply(grossInput, refiningRecipe.workSecondsPerInputKg()),
                        finiteMultiply(
                                grossInput,
                                refiningRecipe.maintenanceWorkSecondsPerInputKg()),
                        refiningRecipe.requiredCapabilityTags()));
            } else {
                expected.add(new ProcessOperatingDemand(
                        request.process(),
                        FacilitySlotKey.from(request.process()),
                        owner,
                        output,
                        finiteMultiply(output, manufacturingRecipe.energyJPerOutputKg()),
                        finiteMultiply(output, manufacturingRecipe.workSecondsPerOutputKg()),
                        finiteMultiply(
                                output,
                                manufacturingRecipe.maintenanceWorkSecondsPerOutputKg()),
                        manufacturingRecipe.requiredCapabilityTags()));
            }
        }
        expected.sort(Comparator.comparing(ProcessOperatingDemand::process));
        if (!List.copyOf(expected).equals(actual)) {
            throw new IllegalArgumentException(
                    "operating report process demands differ from exact Stage-18 recipes");
        }
    }

    private static void validateReportCoverage(
            IndustrialFreightReport freight,
            OperatingStateAuthority authority,
            List<ProcessOperatingDemand> processes,
            List<FacilityOperatingEvidence> facilities,
            List<StationOperatingEvidence> stations) {
        Set<ProcessSelectionKey> selected = freight.reservation().selection().requests().stream()
                .map(ProcessOutputRequest::process)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<ProcessSelectionKey> actual = processes.stream()
                .map(ProcessOperatingDemand::process)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<FacilitySlotKey> selectedFacilities = selected.stream()
                .map(FacilitySlotKey::from)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<FacilitySlotKey> authorityFacilities = authority.facilities().stream()
                .map(FacilityStateAssignment::slot)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<FacilitySlotKey> actualFacilities = facilities.stream()
                .map(value -> value.assignment().slot())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<StationKey> selectedStations = selectedFacilities.stream()
                .map(FacilitySlotKey::station)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<StationKey> authorityStations = authority.stationServices().stream()
                .map(StationServiceAllocation::station)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<StationKey> actualStations = stations.stream()
                .map(StationOperatingEvidence::station)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!selected.equals(actual)
                || !selectedFacilities.equals(authorityFacilities)
                || !selectedFacilities.equals(actualFacilities)
                || !selectedStations.equals(authorityStations)
                || !selectedStations.equals(actualStations)) {
            throw new IllegalArgumentException(
                    "operating report must exactly cover selected process/facility/station identities");
        }

        TreeMap<FacilitySlotKey, List<ProcessOperatingDemand>> demandsByFacility = new TreeMap<>();
        for (ProcessOperatingDemand demand : processes) {
            demandsByFacility.computeIfAbsent(demand.facility(), ignored -> new ArrayList<>()).add(demand);
        }
        TreeMap<FacilitySlotKey, FacilityStateAssignment> authorityByFacility = new TreeMap<>();
        authority.facilities().forEach(value -> authorityByFacility.put(value.slot(), value));
        for (FacilityOperatingEvidence evidence : facilities) {
            List<ProcessOperatingDemand> expected = demandsByFacility.get(evidence.assignment().slot());
            if (expected == null || !List.copyOf(expected).equals(evidence.processDemands())
                    || !evidence.assignment().equals(
                    authorityByFacility.get(evidence.assignment().slot()))) {
                throw new IllegalArgumentException(
                        "facility evidence must retain exact assignment, projection and process demand");
            }
        }

        TreeMap<StationKey, StationServiceAllocation> authorityByStation = new TreeMap<>();
        authority.stationServices().forEach(value ->
                authorityByStation.put(value.station(), value));
        for (StationOperatingEvidence station : stations) {
            List<FacilityStateAssignment> stationAssignments = authority.facilities().stream()
                    .filter(value -> value.slot().station().equals(station.station()))
                    .toList();
            List<FacilitySlotKey> expectedSlots = stationAssignments.stream()
                    .map(FacilityStateAssignment::slot)
                    .sorted()
                    .toList();
            if (!station.services().equals(authorityByStation.get(station.station()))
                    || !station.facilitySlots().equals(expectedSlots)) {
                throw new IllegalArgumentException(
                        "station evidence must retain exact services and facility assignments");
            }
            close(station.allocatedProcessPowerW(), sumStates(
                    stationAssignments, value -> value.state().allocatedProcessPowerW()),
                    "allocatedProcessPowerW");
            close(station.allocatedHeatRejectionW(), sumStates(
                    stationAssignments, value -> value.state().availableHeatRejectionW()),
                    "allocatedHeatRejectionW");
            close(station.allocatedLaborUnits(), sumStates(
                    stationAssignments, value -> value.state().availableLaborUnits()),
                    "allocatedLaborUnits");
            close(station.allocatedMaintenanceWorkRate(), sumStates(
                    stationAssignments, value ->
                            value.state().availableMaintenanceWorkRate()),
                    "allocatedMaintenanceWorkRate");
            double outputTransfer = freight.reservation().selection().requests().stream()
                    .filter(value -> FacilitySlotKey.from(value.process()).station()
                            .equals(station.station()))
                    .mapToDouble(ProcessOutputRequest::requestedOutputKgPerSecond)
                    .sum();
            double inputTransfer = freight.reservation().reservations().stream()
                    .filter(value -> new StationKey(
                            value.process().systemId(),
                            value.process().stationPlacementId()).equals(station.station()))
                    .mapToDouble(value -> value.reservedInputKgPerSecond())
                    .sum();
            close(station.requiredCargoTransferKgPerSecond(),
                    finiteAdd(outputTransfer, inputTransfer),
                    "requiredCargoTransferKgPerSecond");
        }
    }

    private static Set<String> immutableTextSet(Set<String> source, String field) {
        Objects.requireNonNull(source, field);
        java.util.TreeSet<String> copy = new java.util.TreeSet<>();
        for (String value : source) {
            copy.add(requireText(value, field + " entry"));
        }
        return Collections.unmodifiableSet(copy);
    }

    private static Set<MissingAuthority> immutableAuthorities(EnumSet<MissingAuthority> authorities) {
        return Collections.unmodifiableSet(authorities.isEmpty()
                ? EnumSet.noneOf(MissingAuthority.class)
                : EnumSet.copyOf(authorities));
    }

    private static double sum(
            List<ProcessOperatingDemand> values,
            java.util.function.ToDoubleFunction<ProcessOperatingDemand> field) {
        double result = 0d;
        for (ProcessOperatingDemand value : values) {
            result = finiteAdd(result, field.applyAsDouble(value));
        }
        return result;
    }

    private static double sumStates(
            List<FacilityStateAssignment> values,
            java.util.function.ToDoubleFunction<FacilityStateAssignment> field) {
        double result = 0d;
        for (FacilityStateAssignment value : values) {
            result = finiteAdd(result, field.applyAsDouble(value));
        }
        return result;
    }

    private static double finiteAdd(double left, double right) {
        double result = left + right;
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("operating resource sum overflowed finite range");
        }
        return result;
    }

    private static double finiteMultiply(double left, double right) {
        double result = left * right;
        if (!Double.isFinite(result) || result <= 0d) {
            throw new IllegalArgumentException("operating resource product must be positive and finite");
        }
        return result;
    }

    private static double finiteDivide(double numerator, double denominator) {
        double result = numerator / denominator;
        if (!Double.isFinite(result) || result <= 0d) {
            throw new IllegalArgumentException("operating resource quotient must be positive and finite");
        }
        return result;
    }

    private static void close(double actual, double expected, String field) {
        double scale = Math.max(1d, Math.max(Math.abs(actual), Math.abs(expected)));
        if (Math.abs(actual - expected) > EPSILON * scale) {
            throw new IllegalArgumentException(field + " differs from derived operating evidence");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.strip();
    }

    private static void requirePositiveFinite(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }

    private static void requireNonNegativeFinite(double value, String field) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(field + " must be non-negative and finite");
        }
    }

    private record SlotEvidence(int facilityOrdinal) {}

    private record EvidenceIndex(
            Map<ProcessSelectionKey, ProcessCandidate> processes,
            Map<FacilitySlotKey, SlotEvidence> slots,
            Map<StationKey, StationCandidate> stations) {
        private static EvidenceIndex build(CandidateReport candidates) {
            TreeMap<ProcessSelectionKey, ProcessCandidate> processIndex = new TreeMap<>();
            TreeMap<FacilitySlotKey, SlotEvidence> slotIndex = new TreeMap<>();
            TreeMap<StationKey, StationCandidate> stationIndex = new TreeMap<>();
            for (var system : candidates.systems()) {
                for (StationCandidate station : system.stations()) {
                    StationKey stationKey = new StationKey(
                            system.systemId(), station.placement().id());
                    if (stationIndex.putIfAbsent(stationKey, station) != null) {
                        throw new IllegalArgumentException("candidate stations must be unique");
                    }
                    for (FacilitySlot slot : station.facilitySlots()) {
                        FacilitySlotKey key = new FacilitySlotKey(
                                stationKey, slot.definition().id());
                        if (slotIndex.putIfAbsent(
                                key, new SlotEvidence(slot.facilityOrdinal())) != null) {
                            throw new IllegalArgumentException("candidate facility slots must be unique");
                        }
                    }
                    for (ProcessCandidate process : station.processes()) {
                        if (processIndex.putIfAbsent(
                                ProcessSelectionKey.from(process), process) != null) {
                            throw new IllegalArgumentException("candidate processes must be unique");
                        }
                    }
                }
            }
            return new EvidenceIndex(
                    Collections.unmodifiableMap(processIndex),
                    Collections.unmodifiableMap(slotIndex),
                    Collections.unmodifiableMap(stationIndex));
        }
    }
}
