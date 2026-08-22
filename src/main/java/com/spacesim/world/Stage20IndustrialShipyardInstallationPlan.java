package com.spacesim.world;

import com.spacesim.content.Stage18FacilityCatalog;
import com.spacesim.content.Stage18FacilityCatalog.FacilityDefinition;
import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage18ShipyardCatalog;
import com.spacesim.content.Stage18ShipyardCatalog.YardDefinition;
import com.spacesim.content.Stage18ShipyardCatalogLoader;
import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.economy.Stage18FacilityRuntime;
import com.spacesim.economy.Stage18FacilityRuntime.FacilityCapabilitySnapshot;
import com.spacesim.economy.Stage18FacilityRuntime.InstalledFacilityState;
import com.spacesim.economy.Stage18ShipyardRuntime;
import com.spacesim.economy.Stage18ShipyardRuntime.InstalledYardState;
import com.spacesim.economy.Stage18ShipyardRuntime.YardCapabilitySnapshot;
import com.spacesim.economy.Stage18StationIndustrialNode;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.FacilityOperatingEvidence;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.FacilitySlotKey;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.FacilityStateAssignment;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.StationKey;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.StationOperatingEvidence;
import com.spacesim.world.Stage20IndustrialInitialInventoryPlan.InventoryReport;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.CandidateReport;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.MissingAuthority;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.StationCandidate;
import com.spacesim.world.generation.Stage20ResolvedGeneratedWorldProductionProbe.ResolvedProbeResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Binds explicit Stage-18G yard instances to generated stations without reusing facility resources.
 *
 * <p>Every selected operating station receives one explicit yard-authority row, including an empty
 * row when no yard is installed. Active yards are projected by {@link Stage18ShipyardRuntime} from
 * canonical generated station identity and active required support facilities. Supplemental support
 * states are allowed only for exact generated facility slots required by an authored yard.</p>
 *
 * <p>Selected facilities, supplemental support facilities and installed yards share the preceding
 * station service pool. Yard engineering work additionally consumes only support-facility work left
 * after selected process demand. This closes both the hidden-yard and double-use seams.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20IndustrialShipyardInstallationPlan {
    /** Stable Stage-20F installed-shipyard plan version. */
    public static final String CURRENT_VERSION =
            "stage20f.industrial-shipyard-installation-plan.v1";
    private static final double EPSILON = 1.0e-9d;

    private Stage20IndustrialShipyardInstallationPlan() {
        throw new AssertionError("No instances");
    }

    /** Final installed-yard authority state. */
    public enum Status {
        /** Explicit yard presence or absence fits every physical prerequisite and shared pool. */
        ACCEPTED,
        /** At least one installed yard, support facility or shared resource is insufficient. */
        INSUFFICIENT_INSTALLED_YARD_CAPABILITY
    }

    /** Machine-readable rejection reason. */
    public enum FailureReason {
        /** Authored yard state cannot coexist with support and station resource commitments. */
        YARD_SUPPORT_OR_RESOURCE_CONFLICT
    }

    /**
     * Exact generated installed-yard slot.
     *
     * @param station generated station
     * @param yardOrdinal deterministic station-local yard ordinal
     */
    public record YardSlotKey(StationKey station, int yardOrdinal)
            implements Comparable<YardSlotKey> {
        /**
         * Validates one yard slot.
         *
         * @param station generated station
         * @param yardOrdinal station-local yard ordinal
         */
        public YardSlotKey {
            Objects.requireNonNull(station, "station");
            if (yardOrdinal < 0) {
                throw new IllegalArgumentException("yardOrdinal must be non-negative");
            }
        }

        /** Orders exact yard slots deterministically. */
        @Override
        public int compareTo(YardSlotKey other) {
            int comparison = station.compareTo(other.station);
            return comparison != 0
                    ? comparison
                    : Integer.compare(yardOrdinal, other.yardOrdinal);
        }
    }

    /**
     * Explicit owner and Stage-18G state for one installed yard.
     *
     * @param slot exact generated station yard slot
     * @param stableFactionId explicit owning faction
     * @param state installed Stage-18G yard state
     */
    public record InstalledYardAssignment(
            YardSlotKey slot,
            String stableFactionId,
            InstalledYardState state) {
        /**
         * Validates one explicit installed-yard assignment.
         *
         * @param slot exact yard slot
         * @param stableFactionId explicit owner
         * @param state exact installed state
         */
        public InstalledYardAssignment {
            Objects.requireNonNull(slot, "slot");
            stableFactionId = WorldFactionIdentityState.normalizeStableId(stableFactionId);
            Objects.requireNonNull(state, "state");
        }
    }

    /**
     * Explicit yard/support authority for one selected station.
     *
     * @param station exact generated station
     * @param supplementalSupportFacilities exact required states not selected for production
     * @param yards exact installed yards, or an explicit empty list
     */
    public record StationYardAuthority(
            StationKey station,
            List<FacilityStateAssignment> supplementalSupportFacilities,
            List<InstalledYardAssignment> yards) {
        /**
         * Canonicalizes one station yard row.
         *
         * @param station exact generated station
         * @param supplementalSupportFacilities exact supplemental support states
         * @param yards exact installed yard states
         */
        public StationYardAuthority {
            Objects.requireNonNull(station, "station");
            ArrayList<FacilityStateAssignment> supports = new ArrayList<>(Objects.requireNonNull(
                    supplementalSupportFacilities, "supplementalSupportFacilities"));
            ArrayList<InstalledYardAssignment> installed = new ArrayList<>(Objects.requireNonNull(
                    yards, "yards"));
            supports.sort(Comparator.comparing(FacilityStateAssignment::slot));
            installed.sort(Comparator.comparing(InstalledYardAssignment::slot));
            if (supports.stream().anyMatch(Objects::isNull)
                    || installed.stream().anyMatch(Objects::isNull)
                    || supports.stream().anyMatch(value -> !value.slot().station().equals(station))
                    || installed.stream().anyMatch(value -> !value.slot().station().equals(station))
                    || supports.stream().map(FacilityStateAssignment::slot).distinct().count()
                    != supports.size()
                    || installed.stream().map(InstalledYardAssignment::slot).distinct().count()
                    != installed.size()) {
                throw new IllegalArgumentException(
                        "station yard authority identities must be unique and station-local");
            }
            supplementalSupportFacilities = List.copyOf(supports);
            yards = List.copyOf(installed);
        }
    }

    /**
     * Versioned explicit installed-yard authority.
     *
     * @param version caller authority version
     * @param rootSeed exact accepted generated root seed
     * @param stations exact selected-station yard rows
     */
    public record ShipyardInstallationAuthority(
            String version,
            long rootSeed,
            List<StationYardAuthority> stations) {
        /**
         * Canonicalizes one complete yard authority.
         *
         * @param version caller authority version
         * @param rootSeed exact root seed
         * @param stations exact station rows
         */
        public ShipyardInstallationAuthority {
            version = requireText(version, "version");
            ArrayList<StationYardAuthority> copy = new ArrayList<>(Objects.requireNonNull(
                    stations, "stations"));
            copy.sort(Comparator.comparing(StationYardAuthority::station));
            if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)
                    || copy.stream().map(StationYardAuthority::station).distinct().count()
                    != copy.size()) {
                throw new IllegalArgumentException(
                        "shipyard authority stations must be non-empty and unique");
            }
            stations = List.copyOf(copy);
        }
    }

    /**
     * Active-state and residual-work evidence for one required support facility.
     *
     * @param assignment exact installed support state
     * @param snapshot exact Stage-18 effective projection
     * @param supplemental whether this state is additional to selected process facilities
     * @param committedProcessWorkRate selected process work already consuming this snapshot
     * @param residualEngineeringWorkRate work left for installed yards
     * @param status whether the support facility is physically active
     */
    public record SupportFacilityEvidence(
            FacilityStateAssignment assignment,
            FacilityCapabilitySnapshot snapshot,
            boolean supplemental,
            double committedProcessWorkRate,
            double residualEngineeringWorkRate,
            Status status) {
        /**
         * Validates one support-facility row.
         *
         * @param assignment exact support assignment
         * @param snapshot exact Stage-18 projection
         * @param supplemental whether the state is supplemental
         * @param committedProcessWorkRate already committed work rate
         * @param residualEngineeringWorkRate residual yard-support work rate
         * @param status physical support status
         */
        public SupportFacilityEvidence {
            Objects.requireNonNull(assignment, "assignment");
            Objects.requireNonNull(snapshot, "snapshot");
            requireNonNegativeFinite(committedProcessWorkRate, "committedProcessWorkRate");
            requireNonNegativeFinite(
                    residualEngineeringWorkRate, "residualEngineeringWorkRate");
            Objects.requireNonNull(status, "status");
            if (!snapshot.facilityInstanceId().equals(
                    assignment.state().facilityInstanceId())
                    || !snapshot.definitionId().equals(assignment.state().definitionId())) {
                throw new IllegalArgumentException(
                        "support projection differs from installed assignment identity");
            }
            close(residualEngineeringWorkRate,
                    Math.max(0d, snapshot.effectiveEngineeringWorkRate()
                            - committedProcessWorkRate),
                    "residualEngineeringWorkRate");
            boolean active = snapshot.status() == Stage18FacilityRuntime.Status.ACTIVE;
            if ((status == Status.ACCEPTED) != active) {
                throw new IllegalArgumentException(
                        "support status differs from Stage-18 facility projection");
            }
        }
    }

    /**
     * Physical projection and non-reused support-work evidence for one yard.
     *
     * @param assignment exact installed-yard assignment
     * @param snapshot exact Stage-18G projection
     * @param availableResidualSupportWorkRate work available from this yard's required supports
     * @param effectiveYardWorkRate projected yard work demand
     * @param status whether the yard is active and fits residual support work
     */
    public record InstalledYardEvidence(
            InstalledYardAssignment assignment,
            YardCapabilitySnapshot snapshot,
            double availableResidualSupportWorkRate,
            double effectiveYardWorkRate,
            Status status) {
        /**
         * Validates one installed-yard row.
         *
         * @param assignment exact installed assignment
         * @param snapshot exact runtime projection
         * @param availableResidualSupportWorkRate available residual support work
         * @param effectiveYardWorkRate projected yard work
         * @param status yard acceptance status
         */
        public InstalledYardEvidence {
            Objects.requireNonNull(assignment, "assignment");
            Objects.requireNonNull(snapshot, "snapshot");
            requireNonNegativeFinite(
                    availableResidualSupportWorkRate,
                    "availableResidualSupportWorkRate");
            requireNonNegativeFinite(effectiveYardWorkRate, "effectiveYardWorkRate");
            Objects.requireNonNull(status, "status");
            if (!snapshot.yardInstanceId().equals(assignment.state().yardInstanceId())
                    || !snapshot.yardDefinitionId().equals(
                    assignment.state().yardDefinitionId())) {
                throw new IllegalArgumentException(
                        "yard projection differs from installed assignment identity");
            }
            double expectedWork = snapshot.active()
                    ? snapshot.plannerCapability().workRate()
                    : 0d;
            close(effectiveYardWorkRate, expectedWork, "effectiveYardWorkRate");
            boolean accepted = snapshot.active()
                    && effectiveYardWorkRate
                    <= availableResidualSupportWorkRate + EPSILON;
            if ((status == Status.ACCEPTED) != accepted) {
                throw new IllegalArgumentException(
                        "yard status differs from projection and residual support work");
            }
        }
    }

    /**
     * Complete shared yard/resource evidence for one selected station.
     *
     * @param station exact generated station
     * @param stationArchetypeId exact physical station archetype
     * @param operatingState preceding station operating evidence
     * @param authority exact caller-authored yard row
     * @param supports all exact required support facilities
     * @param yards all exact installed-yard projections
     * @param totalAllocatedPowerW selected facilities, supplemental supports and yards
     * @param totalAllocatedHeatRejectionW selected and supplemental facility heat rejection
     * @param totalAllocatedLaborUnits selected, supplemental and yard labor
     * @param totalAllocatedMaintenanceWorkRate selected and supplemental facility maintenance
     * @param residualSupportEngineeringWorkRate total non-reused support work
     * @param requiredYardEngineeringWorkRate total projected yard work
     * @param status whether explicit yard state fits every shared resource
     */
    public record StationYardEvidence(
            StationKey station,
            String stationArchetypeId,
            StationOperatingEvidence operatingState,
            StationYardAuthority authority,
            List<SupportFacilityEvidence> supports,
            List<InstalledYardEvidence> yards,
            double totalAllocatedPowerW,
            double totalAllocatedHeatRejectionW,
            double totalAllocatedLaborUnits,
            double totalAllocatedMaintenanceWorkRate,
            double residualSupportEngineeringWorkRate,
            double requiredYardEngineeringWorkRate,
            Status status) {
        /**
         * Canonicalizes and validates one station yard result.
         *
         * @param station exact generated station
         * @param stationArchetypeId exact station archetype
         * @param operatingState preceding operating evidence
         * @param authority exact station yard authority
         * @param supports exact required support evidence
         * @param yards exact installed-yard evidence
         * @param totalAllocatedPowerW combined allocated power
         * @param totalAllocatedHeatRejectionW combined heat rejection
         * @param totalAllocatedLaborUnits combined labor allocation
         * @param totalAllocatedMaintenanceWorkRate combined maintenance work
         * @param residualSupportEngineeringWorkRate residual support engineering work
         * @param requiredYardEngineeringWorkRate projected yard engineering work
         * @param status station yard acceptance status
         */
        public StationYardEvidence {
            Objects.requireNonNull(station, "station");
            stationArchetypeId = requireText(stationArchetypeId, "stationArchetypeId");
            Objects.requireNonNull(operatingState, "operatingState");
            Objects.requireNonNull(authority, "authority");
            if (!operatingState.station().equals(station)
                    || !authority.station().equals(station)
                    || !operatingState.stationArchetypeId().equals(stationArchetypeId)) {
                throw new IllegalArgumentException(
                        "station yard evidence targets inconsistent physical station identity");
            }
            ArrayList<SupportFacilityEvidence> supportCopy = new ArrayList<>(Objects.requireNonNull(
                    supports, "supports"));
            ArrayList<InstalledYardEvidence> yardCopy = new ArrayList<>(Objects.requireNonNull(
                    yards, "yards"));
            supportCopy.sort(Comparator.comparing(value -> value.assignment().slot()));
            yardCopy.sort(Comparator.comparing(value -> value.assignment().slot()));
            if (supportCopy.stream().anyMatch(Objects::isNull)
                    || yardCopy.stream().anyMatch(Objects::isNull)
                    || supportCopy.stream().map(value -> value.assignment().slot()).distinct().count()
                    != supportCopy.size()
                    || yardCopy.stream().map(value -> value.assignment().slot()).distinct().count()
                    != yardCopy.size()
                    || supportCopy.stream().anyMatch(value ->
                    !value.assignment().slot().station().equals(station))
                    || yardCopy.stream().anyMatch(value ->
                    !value.assignment().slot().station().equals(station))) {
                throw new IllegalArgumentException(
                        "station support/yard evidence must be unique and station-local");
            }
            supports = List.copyOf(supportCopy);
            yards = List.copyOf(yardCopy);
            requireNonNegativeFinite(totalAllocatedPowerW, "totalAllocatedPowerW");
            requireNonNegativeFinite(
                    totalAllocatedHeatRejectionW, "totalAllocatedHeatRejectionW");
            requireNonNegativeFinite(totalAllocatedLaborUnits, "totalAllocatedLaborUnits");
            requireNonNegativeFinite(
                    totalAllocatedMaintenanceWorkRate,
                    "totalAllocatedMaintenanceWorkRate");
            requireNonNegativeFinite(
                    residualSupportEngineeringWorkRate,
                    "residualSupportEngineeringWorkRate");
            requireNonNegativeFinite(
                    requiredYardEngineeringWorkRate,
                    "requiredYardEngineeringWorkRate");
            Objects.requireNonNull(status, "status");

            close(residualSupportEngineeringWorkRate,
                    sumSupports(supports, SupportFacilityEvidence::residualEngineeringWorkRate),
                    "residualSupportEngineeringWorkRate");
            close(requiredYardEngineeringWorkRate,
                    sumYards(yards, InstalledYardEvidence::effectiveYardWorkRate),
                    "requiredYardEngineeringWorkRate");
            boolean accepted = supports.stream().allMatch(value -> value.status() == Status.ACCEPTED)
                    && yards.stream().allMatch(value -> value.status() == Status.ACCEPTED)
                    && totalAllocatedPowerW
                    <= operatingState.services().availableProcessPowerW() + EPSILON
                    && totalAllocatedHeatRejectionW
                    <= operatingState.services().availableHeatRejectionW() + EPSILON
                    && totalAllocatedLaborUnits
                    <= operatingState.services().availableLaborUnits() + EPSILON
                    && totalAllocatedMaintenanceWorkRate
                    <= operatingState.services().availableMaintenanceWorkRate() + EPSILON
                    && requiredYardEngineeringWorkRate
                    <= residualSupportEngineeringWorkRate + EPSILON;
            if ((status == Status.ACCEPTED) != accepted) {
                throw new IllegalArgumentException(
                        "station yard status differs from shared physical resource evidence");
            }
        }
    }

    /**
     * Complete all-or-nothing installed-yard authority result.
     *
     * @param version plan contract version
     * @param rootSeed exact root seed
     * @param resolvedProbeVersion exact generated evidence version
     * @param candidatePlanVersion exact candidate version
     * @param facilityCatalogFingerprint exact Stage-18E content
     * @param shipyardCatalogFingerprint exact Stage-18G content
     * @param inventory accepted preceding initial inventory
     * @param authority exact caller-authored installed-yard authority
     * @param status final installed-yard status
     * @param failureReason absent only when accepted
     * @param stations exact selected-station yard evidence
     * @param missingAuthorities remaining specialization authorities
     */
    public record YardReport(
            String version,
            long rootSeed,
            String resolvedProbeVersion,
            String candidatePlanVersion,
            String facilityCatalogFingerprint,
            String shipyardCatalogFingerprint,
            InventoryReport inventory,
            ShipyardInstallationAuthority authority,
            Status status,
            Optional<FailureReason> failureReason,
            List<StationYardEvidence> stations,
            Set<MissingAuthority> missingAuthorities) {
        /**
         * Canonicalizes and validates one installed-yard report.
         *
         * @param version plan contract version
         * @param rootSeed exact root seed
         * @param resolvedProbeVersion generated evidence version
         * @param candidatePlanVersion candidate plan version
         * @param facilityCatalogFingerprint Stage-18E catalog fingerprint
         * @param shipyardCatalogFingerprint Stage-18G catalog fingerprint
         * @param inventory accepted preceding inventory
         * @param authority explicit installed-yard authority
         * @param status final yard status
         * @param failureReason optional rejection reason
         * @param stations exact station yard evidence
         * @param missingAuthorities remaining authorities
         */
        public YardReport {
            version = requireText(version, "version");
            resolvedProbeVersion = requireText(resolvedProbeVersion, "resolvedProbeVersion");
            candidatePlanVersion = requireText(candidatePlanVersion, "candidatePlanVersion");
            facilityCatalogFingerprint = requireText(
                    facilityCatalogFingerprint, "facilityCatalogFingerprint");
            shipyardCatalogFingerprint = requireText(
                    shipyardCatalogFingerprint, "shipyardCatalogFingerprint");
            Objects.requireNonNull(inventory, "inventory");
            Objects.requireNonNull(authority, "authority");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(failureReason, "failureReason");
            if (rootSeed != inventory.rootSeed() || rootSeed != authority.rootSeed()
                    || !inventory.initialInventoryAuthoritative()) {
                throw new IllegalArgumentException(
                        "installed-yard report requires matching accepted inventory authority");
            }
            if (!CURRENT_VERSION.equals(version)
                    || !Stage18FacilityCatalogLoader.loadDefault().getFingerprint()
                    .equals(facilityCatalogFingerprint)
                    || !Stage18ShipyardCatalogLoader.loadDefault().getFingerprint()
                    .equals(shipyardCatalogFingerprint)) {
                throw new IllegalArgumentException(
                        "yard report version/catalog authority differs from current contract");
            }
            if ((status == Status.ACCEPTED) != failureReason.isEmpty()) {
                throw new IllegalArgumentException(
                        "yard failure reason must be absent exactly when accepted");
            }
            ArrayList<StationYardEvidence> copy = new ArrayList<>(Objects.requireNonNull(
                    stations, "stations"));
            copy.sort(Comparator.comparing(StationYardEvidence::station));
            if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)
                    || copy.stream().map(StationYardEvidence::station).distinct().count()
                    != copy.size()) {
                throw new IllegalArgumentException(
                        "yard report stations must be non-empty and unique");
            }
            stations = List.copyOf(copy);
            validateReportCoverage(inventory, authority, stations);
            validateCatalogEvidence(inventory, stations);
            boolean allAccepted = stations.stream().allMatch(
                    value -> value.status() == Status.ACCEPTED);
            if ((status == Status.ACCEPTED) != allAccepted) {
                throw new IllegalArgumentException(
                        "yard report status differs from station evidence");
            }
            Objects.requireNonNull(missingAuthorities, "missingAuthorities");
            EnumSet<MissingAuthority> expected = copyAuthorities(
                    inventory.missingAuthorities());
            if (status == Status.ACCEPTED) {
                expected.remove(MissingAuthority.INSTALLED_SHIPYARDS);
            }
            EnumSet<MissingAuthority> actual = copyAuthorities(missingAuthorities);
            if (!actual.equals(expected)) {
                throw new IllegalArgumentException(
                        "yard report cannot silently change another authority");
            }
            missingAuthorities = immutableAuthorities(actual);
        }

        /** @return whether explicit installed-yard presence or absence is authoritative */
        public boolean installedYardsAuthoritative() {
            return status == Status.ACCEPTED
                    && !missingAuthorities.contains(MissingAuthority.INSTALLED_SHIPYARDS);
        }

        /** @return number of physically active installed yards */
        public int activeYardCount() {
            return stations.stream().flatMap(value -> value.yards().stream())
                    .mapToInt(value -> value.snapshot().active() ? 1 : 0)
                    .sum();
        }

        /** @return whether every Stage-20F operational authority is closed */
        public boolean operationallyAuthoritative() {
            return installedYardsAuthoritative() && missingAuthorities.isEmpty();
        }
    }

    /**
     * Validates explicit installed-yard state after accepted initial inventory.
     *
     * @param resolved exact accepted generated-world authority
     * @param inventory accepted initial station inventory
     * @param authority explicit yard presence/support state
     * @return deterministic accepted or fail-closed yard report
     */
    public static YardReport plan(
            ResolvedProbeResult resolved,
            InventoryReport inventory,
            ShipyardInstallationAuthority authority) {
        ResolvedProbeResult accepted = Objects.requireNonNull(resolved, "resolved");
        InventoryReport stock = Objects.requireNonNull(inventory, "inventory");
        ShipyardInstallationAuthority installed = Objects.requireNonNull(authority, "authority");
        if (accepted.rootSeed() != stock.rootSeed()
                || !stock.resolvedProbeVersion().equals(accepted.version())
                || !stock.initialInventoryAuthoritative()) {
            throw new IllegalArgumentException(
                    "yard installation requires matching accepted generated inventory evidence");
        }
        return planEvidence(
                stock,
                Stage20IndustrialSpecializationCandidatePlan.reconstruct(accepted),
                Stage18FacilityCatalogLoader.loadDefault(),
                Stage18ShipyardCatalogLoader.loadDefault(),
                Stage18ResourceOntologyLoader.loadDefault(),
                Stage18ManufacturingProductRegistry.loadDefault(),
                installed);
    }

    static YardReport planEvidence(
            InventoryReport inventory,
            CandidateReport candidates,
            Stage18FacilityCatalog facilities,
            Stage18ShipyardCatalog shipyards,
            Stage18ResourceOntologyCatalog ontology,
            Stage18ManufacturingProductRegistry products,
            ShipyardInstallationAuthority authority) {
        InventoryReport stock = Objects.requireNonNull(inventory, "inventory");
        CandidateReport candidateReport = Objects.requireNonNull(candidates, "candidates");
        Stage18FacilityCatalog facilityCatalog = Objects.requireNonNull(facilities, "facilities");
        Stage18ShipyardCatalog shipyardCatalog = Objects.requireNonNull(shipyards, "shipyards");
        Objects.requireNonNull(ontology, "ontology");
        Objects.requireNonNull(products, "products");
        ShipyardInstallationAuthority installed = Objects.requireNonNull(authority, "authority");
        if (!stock.initialInventoryAuthoritative()
                || stock.rootSeed() != candidateReport.rootSeed()
                || stock.rootSeed() != installed.rootSeed()) {
            throw new IllegalArgumentException("yard evidence targets different authorities");
        }

        TreeMap<StationKey, StationCandidate> candidateByStation = candidateStations(candidateReport);
        TreeMap<StationKey, StationYardAuthority> authorityByStation = new TreeMap<>();
        installed.stations().forEach(value -> authorityByStation.put(value.station(), value));
        TreeMap<StationKey, StationOperatingEvidence> operatingByStation = new TreeMap<>();
        stock.operatingState().stations().forEach(value ->
                operatingByStation.put(value.station(), value));
        if (!authorityByStation.keySet().equals(operatingByStation.keySet())) {
            throw new IllegalArgumentException(
                    "yard authority must exactly cover selected operating stations");
        }

        TreeMap<FacilitySlotKey, FacilityOperatingEvidence> selectedFacilityBySlot = new TreeMap<>();
        stock.operatingState().facilities().forEach(value ->
                selectedFacilityBySlot.put(value.assignment().slot(), value));
        Stage18FacilityRuntime facilityRuntime = new Stage18FacilityRuntime(facilityCatalog);
        Stage18ShipyardRuntime yardRuntime = new Stage18ShipyardRuntime(
                shipyardCatalog, ontology, products);
        ArrayList<StationYardEvidence> stationEvidence = new ArrayList<>();

        for (Map.Entry<StationKey, StationYardAuthority> entry : authorityByStation.entrySet()) {
            StationKey station = entry.getKey();
            StationYardAuthority stationAuthority = entry.getValue();
            StationCandidate physical = candidateByStation.get(station);
            StationOperatingEvidence operating = operatingByStation.get(station);
            if (physical == null || operating == null) {
                throw new IllegalArgumentException(
                        "yard authority station is absent from generated/operating evidence");
            }
            TreeMap<String, PhysicalFacility> physicalFacilities = physicalFacilities(
                    station, physical);
            validateYardOrdinals(stationAuthority);

            TreeMap<String, YardDefinition> definitionByInstance = new TreeMap<>();
            TreeSet<String> requiredDefinitions = new TreeSet<>();
            for (InstalledYardAssignment yard : stationAuthority.yards()) {
                String expectedId = canonicalYardInstanceId(
                        station.stationPlacementId(), yard.slot().yardOrdinal());
                if (!yard.state().yardInstanceId().equals(expectedId)) {
                    throw new IllegalArgumentException(
                            "installed yard must use canonical generated station identity");
                }
                YardDefinition definition = shipyardCatalog.findYard(
                        yard.state().yardDefinitionId());
                if (definition == null) {
                    throw new IllegalArgumentException(
                            "installed yard references unknown Stage-18G definition");
                }
                definitionByInstance.put(yard.state().yardInstanceId(), definition);
                requiredDefinitions.addAll(
                        definition.requiredSupportFacilityDefinitionIds());
            }

            TreeMap<String, FacilityOperatingEvidence> selectedByDefinition = new TreeMap<>();
            for (FacilityOperatingEvidence selected : selectedFacilityBySlot.values()) {
                if (selected.assignment().slot().station().equals(station)) {
                    selectedByDefinition.put(
                            selected.assignment().slot().facilityDefinitionId(), selected);
                }
            }
            TreeMap<String, FacilityStateAssignment> supplementalByDefinition = new TreeMap<>();
            for (FacilityStateAssignment supplemental
                    : stationAuthority.supplementalSupportFacilities()) {
                String definitionId = supplemental.slot().facilityDefinitionId();
                PhysicalFacility expected = physicalFacilities.get(definitionId);
                if (expected == null
                        || selectedByDefinition.containsKey(definitionId)
                        || supplementalByDefinition.putIfAbsent(definitionId, supplemental) != null
                        || !supplemental.state().facilityInstanceId().equals(
                        Stage20IndustrialFacilityOperatingPlan.canonicalFacilityInstanceId(
                                station.stationPlacementId(), expected.ordinal()))
                        || !supplemental.state().locationTag().equals(
                        Stage20IndustrialFacilityOperatingPlan.GENERATED_STATION_LOCATION_TAG)) {
                    throw new IllegalArgumentException(
                            "supplemental support must be one canonical unselected generated slot");
                }
            }
            TreeSet<String> expectedSupplemental = new TreeSet<>(requiredDefinitions);
            expectedSupplemental.removeAll(selectedByDefinition.keySet());
            if (!expectedSupplemental.equals(supplementalByDefinition.keySet())) {
                throw new IllegalArgumentException(
                        "supplemental support must exactly cover yard-required unselected facilities");
            }

            ArrayList<SupportFacilityEvidence> supports = new ArrayList<>();
            TreeMap<String, SupportFacilityEvidence> supportByDefinition = new TreeMap<>();
            for (String definitionId : requiredDefinitions) {
                FacilityOperatingEvidence selected = selectedByDefinition.get(definitionId);
                FacilityStateAssignment assignment;
                FacilityCapabilitySnapshot snapshot;
                double committedWork;
                boolean supplemental;
                if (selected != null) {
                    assignment = selected.assignment();
                    snapshot = selected.snapshot();
                    committedWork = selected.requiredEngineeringWorkRate();
                    supplemental = false;
                } else {
                    assignment = supplementalByDefinition.get(definitionId);
                    snapshot = facilityRuntime.project(assignment.state());
                    committedWork = 0d;
                    supplemental = true;
                }
                double residual = Math.max(
                        0d, snapshot.effectiveEngineeringWorkRate() - committedWork);
                SupportFacilityEvidence evidence = new SupportFacilityEvidence(
                        assignment,
                        snapshot,
                        supplemental,
                        committedWork,
                        residual,
                        snapshot.status() == Stage18FacilityRuntime.Status.ACTIVE
                                ? Status.ACCEPTED
                                : Status.INSUFFICIENT_INSTALLED_YARD_CAPABILITY);
                supports.add(evidence);
                supportByDefinition.put(definitionId, evidence);
            }

            Stage18StationIndustrialNode stationNode = Stage18StationIndustrialNode.instantiate(
                    station.stationPlacementId(),
                    Stage20IndustrialFacilityOperatingPlan.GENERATED_STATION_LOCATION_TAG,
                    physical.archetype(),
                    ontology,
                    products);
            ArrayList<InstalledYardEvidence> yards = new ArrayList<>();
            for (InstalledYardAssignment yard : stationAuthority.yards()) {
                YardDefinition definition = definitionByInstance.get(
                        yard.state().yardInstanceId());
                ArrayList<FacilityCapabilitySnapshot> snapshots = new ArrayList<>();
                double availableResidualWork = 0d;
                for (String requiredDefinition
                        : definition.requiredSupportFacilityDefinitionIds()) {
                    SupportFacilityEvidence support = supportByDefinition.get(requiredDefinition);
                    if (support == null
                            || !support.assignment().stableFactionId().equals(
                            yard.stableFactionId())) {
                        throw new IllegalArgumentException(
                                "yard and every required support facility must share explicit owner");
                    }
                    snapshots.add(support.snapshot());
                    availableResidualWork = finiteAdd(
                            availableResidualWork,
                            support.residualEngineeringWorkRate());
                }
                YardCapabilitySnapshot projection = yardRuntime.projectYard(
                        yard.state(), stationNode, snapshots);
                double effectiveWork = projection.active()
                        ? projection.plannerCapability().workRate()
                        : 0d;
                yards.add(new InstalledYardEvidence(
                        yard,
                        projection,
                        availableResidualWork,
                        effectiveWork,
                        projection.active() && effectiveWork <= availableResidualWork + EPSILON
                                ? Status.ACCEPTED
                                : Status.INSUFFICIENT_INSTALLED_YARD_CAPABILITY));
            }

            double totalPower = operating.allocatedProcessPowerW();
            double totalHeat = operating.allocatedHeatRejectionW();
            double totalLabor = operating.allocatedLaborUnits();
            double totalMaintenance = operating.allocatedMaintenanceWorkRate();
            for (FacilityStateAssignment supplemental
                    : stationAuthority.supplementalSupportFacilities()) {
                InstalledFacilityState state = supplemental.state();
                totalPower = finiteAdd(totalPower, state.allocatedProcessPowerW());
                totalHeat = finiteAdd(totalHeat, state.availableHeatRejectionW());
                totalLabor = finiteAdd(totalLabor, state.availableLaborUnits());
                totalMaintenance = finiteAdd(
                        totalMaintenance, state.availableMaintenanceWorkRate());
            }
            for (InstalledYardAssignment yard : stationAuthority.yards()) {
                totalPower = finiteAdd(
                        totalPower, yard.state().allocatedIntegrationPowerW());
                totalLabor = finiteAdd(
                        totalLabor, yard.state().availableLaborCapacity());
            }
            double residualSupportWork = sumSupports(
                    supports, SupportFacilityEvidence::residualEngineeringWorkRate);
            double requiredYardWork = sumYards(
                    yards, InstalledYardEvidence::effectiveYardWorkRate);
            boolean accepted = supports.stream().allMatch(value -> value.status() == Status.ACCEPTED)
                    && yards.stream().allMatch(value -> value.status() == Status.ACCEPTED)
                    && totalPower <= operating.services().availableProcessPowerW() + EPSILON
                    && totalHeat <= operating.services().availableHeatRejectionW() + EPSILON
                    && totalLabor <= operating.services().availableLaborUnits() + EPSILON
                    && totalMaintenance
                    <= operating.services().availableMaintenanceWorkRate() + EPSILON
                    && requiredYardWork <= residualSupportWork + EPSILON;
            stationEvidence.add(new StationYardEvidence(
                    station,
                    physical.archetype().id(),
                    operating,
                    stationAuthority,
                    supports,
                    yards,
                    totalPower,
                    totalHeat,
                    totalLabor,
                    totalMaintenance,
                    residualSupportWork,
                    requiredYardWork,
                    accepted
                            ? Status.ACCEPTED
                            : Status.INSUFFICIENT_INSTALLED_YARD_CAPABILITY));
        }

        boolean accepted = stationEvidence.stream().allMatch(
                value -> value.status() == Status.ACCEPTED);
        EnumSet<MissingAuthority> missing = copyAuthorities(stock.missingAuthorities());
        if (accepted) {
            missing.remove(MissingAuthority.INSTALLED_SHIPYARDS);
        }
        return new YardReport(
                CURRENT_VERSION,
                stock.rootSeed(),
                stock.resolvedProbeVersion(),
                candidateReport.version(),
                facilityCatalog.getFingerprint(),
                shipyardCatalog.getFingerprint(),
                stock,
                installed,
                accepted ? Status.ACCEPTED : Status.INSUFFICIENT_INSTALLED_YARD_CAPABILITY,
                accepted
                        ? Optional.empty()
                        : Optional.of(FailureReason.YARD_SUPPORT_OR_RESOURCE_CONFLICT),
                stationEvidence,
                missing);
    }

    /**
     * Returns the canonical Stage-20F pre-runtime yard instance identity.
     *
     * @param stationPlacementId generated station ID
     * @param yardOrdinal station-local yard ordinal
     * @return deterministic installed-yard identity
     */
    public static String canonicalYardInstanceId(
            String stationPlacementId,
            int yardOrdinal) {
        String station = requireText(stationPlacementId, "stationPlacementId");
        if (yardOrdinal < 0) {
            throw new IllegalArgumentException("yardOrdinal must be non-negative");
        }
        return station + ".yard." + yardOrdinal;
    }

    private static void validateYardOrdinals(StationYardAuthority authority) {
        for (int index = 0; index < authority.yards().size(); index++) {
            if (authority.yards().get(index).slot().yardOrdinal() != index) {
                throw new IllegalArgumentException(
                        "installed yard ordinals must be contiguous from zero");
            }
        }
    }

    private static TreeMap<StationKey, StationCandidate> candidateStations(
            CandidateReport candidates) {
        TreeMap<StationKey, StationCandidate> result = new TreeMap<>();
        for (var system : candidates.systems()) {
            for (StationCandidate station : system.stations()) {
                StationKey key = new StationKey(system.systemId(), station.placement().id());
                if (result.putIfAbsent(key, station) != null) {
                    throw new IllegalArgumentException(
                            "candidate station identities must be unique");
                }
            }
        }
        return result;
    }

    private static TreeMap<String, PhysicalFacility> physicalFacilities(
            StationKey station,
            StationCandidate physical) {
        TreeMap<String, PhysicalFacility> result = new TreeMap<>();
        for (var slot : physical.facilitySlots()) {
            if (result.putIfAbsent(
                    slot.definition().id(),
                    new PhysicalFacility(slot.facilityOrdinal(), slot.definition())) != null) {
                throw new IllegalArgumentException(
                        "generated station facility definitions must be unique: " + station);
            }
        }
        return result;
    }

    private static void validateReportCoverage(
            InventoryReport inventory,
            ShipyardInstallationAuthority authority,
            List<StationYardEvidence> stations) {
        TreeMap<StationKey, StationYardAuthority> authorityByStation = new TreeMap<>();
        authority.stations().forEach(value -> authorityByStation.put(value.station(), value));
        TreeMap<StationKey, StationOperatingEvidence> operatingByStation = new TreeMap<>();
        inventory.operatingState().stations().forEach(value ->
                operatingByStation.put(value.station(), value));
        Set<StationKey> actual = stations.stream().map(StationYardEvidence::station)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!authorityByStation.keySet().equals(operatingByStation.keySet())
                || !authorityByStation.keySet().equals(actual)) {
            throw new IllegalArgumentException(
                    "yard report must exactly cover selected station identities");
        }
        for (StationYardEvidence station : stations) {
            if (!station.authority().equals(authorityByStation.get(station.station()))
                    || !station.operatingState().equals(
                    operatingByStation.get(station.station()))) {
                throw new IllegalArgumentException(
                        "yard report must retain exact authority and operating evidence");
            }
            List<FacilityStateAssignment> supplemental = station.supports().stream()
                    .filter(SupportFacilityEvidence::supplemental)
                    .map(SupportFacilityEvidence::assignment)
                    .sorted(Comparator.comparing(FacilityStateAssignment::slot))
                    .toList();
            List<InstalledYardAssignment> yards = station.yards().stream()
                    .map(InstalledYardEvidence::assignment)
                    .sorted(Comparator.comparing(InstalledYardAssignment::slot))
                    .toList();
            if (!supplemental.equals(station.authority().supplementalSupportFacilities())
                    || !yards.equals(station.authority().yards())) {
                throw new IllegalArgumentException(
                        "yard evidence must retain exact supplemental and yard assignments");
            }
            close(station.totalAllocatedPowerW(), derivedPower(station),
                    "totalAllocatedPowerW");
            close(station.totalAllocatedHeatRejectionW(), derivedHeat(station),
                    "totalAllocatedHeatRejectionW");
            close(station.totalAllocatedLaborUnits(), derivedLabor(station),
                    "totalAllocatedLaborUnits");
            close(station.totalAllocatedMaintenanceWorkRate(), derivedMaintenance(station),
                    "totalAllocatedMaintenanceWorkRate");
        }
    }

    private static void validateCatalogEvidence(
            InventoryReport inventory,
            List<StationYardEvidence> stations) {
        Stage18FacilityCatalog facilities = Stage18FacilityCatalogLoader.loadDefault();
        Stage18ShipyardCatalog shipyards = Stage18ShipyardCatalogLoader.loadDefault();
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        Stage18ManufacturingProductRegistry products =
                Stage18ManufacturingProductRegistry.loadDefault();
        var stationCatalog = Stage18StationInfrastructureCatalogLoader.loadDefault();
        Stage18FacilityRuntime facilityRuntime = new Stage18FacilityRuntime(facilities);
        Stage18ShipyardRuntime yardRuntime = new Stage18ShipyardRuntime(
                shipyards, ontology, products);
        TreeMap<FacilitySlotKey, FacilityOperatingEvidence> selectedBySlot = new TreeMap<>();
        inventory.operatingState().facilities().forEach(value ->
                selectedBySlot.put(value.assignment().slot(), value));

        for (StationYardEvidence station : stations) {
            var archetype = stationCatalog.findArchetype(station.stationArchetypeId());
            if (archetype == null) {
                throw new IllegalArgumentException(
                        "yard report references unknown station archetype");
            }
            Stage18StationIndustrialNode node = Stage18StationIndustrialNode.instantiate(
                    station.station().stationPlacementId(),
                    Stage20IndustrialFacilityOperatingPlan.GENERATED_STATION_LOCATION_TAG,
                    archetype,
                    ontology,
                    products);
            TreeMap<String, Integer> ordinalByDefinition = new TreeMap<>();
            for (int ordinal = 0;
                    ordinal < archetype.installedFacilityDefinitionIds().size();
                    ordinal++) {
                ordinalByDefinition.put(
                        archetype.installedFacilityDefinitionIds().get(ordinal), ordinal);
            }
            TreeMap<String, SupportFacilityEvidence> supportByDefinition = new TreeMap<>();
            for (SupportFacilityEvidence support : station.supports()) {
                String definitionId = support.assignment().slot().facilityDefinitionId();
                Integer ordinal = ordinalByDefinition.get(definitionId);
                if (ordinal == null
                        || supportByDefinition.putIfAbsent(definitionId, support) != null
                        || !support.assignment().state().facilityInstanceId().equals(
                        Stage20IndustrialFacilityOperatingPlan.canonicalFacilityInstanceId(
                                station.station().stationPlacementId(), ordinal))
                        || !support.assignment().state().locationTag().equals(
                        Stage20IndustrialFacilityOperatingPlan.GENERATED_STATION_LOCATION_TAG)
                        || !facilityRuntime.project(support.assignment().state())
                        .equals(support.snapshot())) {
                    throw new IllegalArgumentException(
                            "yard support evidence differs from canonical Stage-18 projection");
                }
                FacilityOperatingEvidence selected = selectedBySlot.get(
                        support.assignment().slot());
                boolean expectedSupplemental = selected == null;
                double expectedCommitted = expectedSupplemental
                        ? 0d
                        : selected.requiredEngineeringWorkRate();
                if (support.supplemental() != expectedSupplemental
                        || (!expectedSupplemental
                        && (!support.assignment().equals(selected.assignment())
                                || !support.snapshot().equals(selected.snapshot())))) {
                    throw new IllegalArgumentException(
                            "yard support supplemental classification differs from operation");
                }
                close(support.committedProcessWorkRate(), expectedCommitted,
                        "committedProcessWorkRate");
            }

            TreeSet<String> expectedSupportDefinitions = new TreeSet<>();
            for (InstalledYardEvidence yard : station.yards()) {
                YardDefinition definition = shipyards.findYard(
                        yard.assignment().state().yardDefinitionId());
                if (definition == null
                        || !yard.assignment().state().yardInstanceId().equals(
                        canonicalYardInstanceId(
                                station.station().stationPlacementId(),
                                yard.assignment().slot().yardOrdinal()))) {
                    throw new IllegalArgumentException(
                            "yard evidence differs from canonical Stage-18G identity");
                }
                expectedSupportDefinitions.addAll(
                        definition.requiredSupportFacilityDefinitionIds());
                ArrayList<FacilityCapabilitySnapshot> snapshots = new ArrayList<>();
                double residual = 0d;
                for (String required : definition.requiredSupportFacilityDefinitionIds()) {
                    SupportFacilityEvidence support = supportByDefinition.get(required);
                    if (support == null
                            || !support.assignment().stableFactionId().equals(
                            yard.assignment().stableFactionId())) {
                        throw new IllegalArgumentException(
                                "yard evidence lost exact required support ownership");
                    }
                    snapshots.add(support.snapshot());
                    residual = finiteAdd(
                            residual, support.residualEngineeringWorkRate());
                }
                YardCapabilitySnapshot expected = yardRuntime.projectYard(
                        yard.assignment().state(), node, snapshots);
                if (!expected.equals(yard.snapshot())) {
                    throw new IllegalArgumentException(
                            "yard evidence differs from canonical Stage-18G projection");
                }
                close(yard.availableResidualSupportWorkRate(), residual,
                        "availableResidualSupportWorkRate");
            }
            if (!expectedSupportDefinitions.equals(supportByDefinition.keySet())) {
                throw new IllegalArgumentException(
                        "yard report must exactly cover authored support definitions");
            }
        }
    }

    private static double derivedPower(StationYardEvidence station) {
        double result = station.operatingState().allocatedProcessPowerW();
        for (FacilityStateAssignment support
                : station.authority().supplementalSupportFacilities()) {
            result = finiteAdd(result, support.state().allocatedProcessPowerW());
        }
        for (InstalledYardAssignment yard : station.authority().yards()) {
            result = finiteAdd(result, yard.state().allocatedIntegrationPowerW());
        }
        return result;
    }

    private static double derivedHeat(StationYardEvidence station) {
        double result = station.operatingState().allocatedHeatRejectionW();
        for (FacilityStateAssignment support
                : station.authority().supplementalSupportFacilities()) {
            result = finiteAdd(result, support.state().availableHeatRejectionW());
        }
        return result;
    }

    private static double derivedLabor(StationYardEvidence station) {
        double result = station.operatingState().allocatedLaborUnits();
        for (FacilityStateAssignment support
                : station.authority().supplementalSupportFacilities()) {
            result = finiteAdd(result, support.state().availableLaborUnits());
        }
        for (InstalledYardAssignment yard : station.authority().yards()) {
            result = finiteAdd(result, yard.state().availableLaborCapacity());
        }
        return result;
    }

    private static double derivedMaintenance(StationYardEvidence station) {
        double result = station.operatingState().allocatedMaintenanceWorkRate();
        for (FacilityStateAssignment support
                : station.authority().supplementalSupportFacilities()) {
            result = finiteAdd(result, support.state().availableMaintenanceWorkRate());
        }
        return result;
    }

    private static double sumSupports(
            List<SupportFacilityEvidence> values,
            java.util.function.ToDoubleFunction<SupportFacilityEvidence> field) {
        double result = 0d;
        for (SupportFacilityEvidence value : values) {
            result = finiteAdd(result, field.applyAsDouble(value));
        }
        return result;
    }

    private static double sumYards(
            List<InstalledYardEvidence> values,
            java.util.function.ToDoubleFunction<InstalledYardEvidence> field) {
        double result = 0d;
        for (InstalledYardEvidence value : values) {
            result = finiteAdd(result, field.applyAsDouble(value));
        }
        return result;
    }

    private static EnumSet<MissingAuthority> copyAuthorities(
            Set<MissingAuthority> authorities) {
        Objects.requireNonNull(authorities, "authorities");
        return authorities.isEmpty()
                ? EnumSet.noneOf(MissingAuthority.class)
                : EnumSet.copyOf(authorities);
    }

    private static Set<MissingAuthority> immutableAuthorities(
            EnumSet<MissingAuthority> authorities) {
        return Collections.unmodifiableSet(authorities.isEmpty()
                ? EnumSet.noneOf(MissingAuthority.class)
                : EnumSet.copyOf(authorities));
    }

    private static double finiteAdd(double left, double right) {
        double result = left + right;
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("yard resource sum overflowed finite range");
        }
        return result;
    }

    private static void close(double actual, double expected, String field) {
        double scale = Math.max(1d, Math.max(Math.abs(actual), Math.abs(expected)));
        if (Math.abs(actual - expected) > EPSILON * scale) {
            throw new IllegalArgumentException(field + " differs from derived yard evidence");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.strip();
    }

    private static void requireNonNegativeFinite(double value, String field) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(field + " must be non-negative and finite");
        }
    }

    private record PhysicalFacility(int ordinal, FacilityDefinition definition) {}
}
