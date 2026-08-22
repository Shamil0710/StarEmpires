package com.spacesim.persistence;

import com.spacesim.content.Stage18ExtractionCatalog;
import com.spacesim.content.Stage18ExtractionCatalogLoader;
import com.spacesim.content.Stage18FacilityCatalog;
import com.spacesim.content.Stage18FacilityCatalog.FacilityDefinition;
import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18ManufacturingCatalogLoader;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18RefiningCatalogLoader;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyCatalog.CommodityDefinition;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage18StationInfrastructureCatalog;
import com.spacesim.content.Stage18StationInfrastructureCatalog.StationArchetypeDefinition;
import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.economy.Stage18ExtractionRuntime;
import com.spacesim.economy.Stage18ExtractionRuntime.ExtractionResult;
import com.spacesim.economy.Stage18FacilityRuntime;
import com.spacesim.economy.Stage18FacilityRuntime.FacilityCapabilitySnapshot;
import com.spacesim.economy.Stage18FacilityRuntime.InstalledFacilityState;
import com.spacesim.economy.Stage18ManufacturingRuntime;
import com.spacesim.economy.Stage18RefiningRuntime;
import com.spacesim.economy.Stage18StationIndustrialNode;
import com.spacesim.economy.Stage18StationIndustrialNode.InstalledFacilityReference;
import com.spacesim.economy.Stage18StationProductionBridge;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.economy.Stage18StationStorage.StationStorageSnapshot;
import com.spacesim.persistence.Stage18IndustrialState.FacilityInstallationSnapshot;
import com.spacesim.persistence.Stage18IndustrialState.PhysicalSourceSnapshot;
import com.spacesim.persistence.Stage20SourceSupplyMaterializer.InitialExtractionSite;
import com.spacesim.persistence.Stage20SourceSupplyMaterializer.MaterializedSource;
import com.spacesim.persistence.Stage20SourceSupplyMaterializer.MaterializedSourceRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Stage-20.5A bridge from saved generated extraction sites to ordinary Stage-18 source-side
 * industrial nodes.
 *
 * <p>The bridge never generates a source, station role, cargo quantity or production result.
 * Canonical Stage-20K source/site rows provide identity and location authority, current Stage-18
 * content provides the unique physically compatible outpost composition, and the ordinary
 * {@link Stage18StationProductionBridge} is the only path that may turn finite source reserve into
 * station inventory.</p>
 *
 * <p>Stage 20 never persisted dynamic source-outpost staffing/power allocation. V1 therefore has one
 * explicit bootstrap policy: a newly commissioned outpost starts pristine and fully allocated to its
 * already-selected extraction facility. This policy creates capability only, never commodity mass.
 * After the first capture, the exact saved {@link InstalledFacilityState} becomes authoritative and
 * is restored without reapplying the bootstrap policy.</p>
 */
public final class Stage20SourceOutpostMaterializer {
    /** Stable Stage-20.5A source-outpost materialization contract version. */
    public static final String CURRENT_VERSION = "stage20_5.source-outpost-materialization.v1";
    /**
     * Versioned compatibility authority for generated surface/deep sites whose exact extraction
     * facility is not present in a production Stage-18 station archetype.
     */
    public static final String COMPATIBILITY_AUTHORITY_VERSION =
            "stage20_5.source-outpost-chassis-compatibility.v1";
    /** Existing physical chassis whose storage/handling envelope is retained by compatibility variants. */
    public static final String COMPATIBILITY_CHASSIS_ID =
            "station.infrastructure.mining_outpost";
    /** Explicit later-content review retained for compatibility-derived source outpost variants. */
    public static final String STAGE22_REVIEW_MARKER =
            "stage22.review.source-outpost-surface-deep-archetypes";
    /** Stable suffix separating generated site identity from its runtime outpost station identity. */
    public static final String OUTPOST_SUFFIX = ".runtime-outpost";

    private Stage20SourceOutpostMaterializer() {
        throw new AssertionError("No instances");
    }

    /**
     * Materializes every accepted initial extraction site into a live ordinary Stage-18 outpost.
     *
     * @param saved exact Stage-20K saved campaign
     * @return deterministic live source/outpost registry
     */
    public static MaterializedSourceOutpostRegistry materialize(
            Stage20GeneratedCampaignPersistentState saved) {
        Stage20GeneratedCampaignPersistentState state = Objects.requireNonNull(saved, "saved");
        MaterializedSourceRegistry sources = Stage20SourceSupplyMaterializer.materialize(state);

        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        Stage18ExtractionCatalog extraction = Stage18ExtractionCatalogLoader.loadDefault();
        Stage18FacilityCatalog facilities = Stage18FacilityCatalogLoader.loadDefault();
        Stage18StationInfrastructureCatalog infrastructure = Stage18StationInfrastructureCatalogLoader.loadDefault();
        Stage18ManufacturingProductRegistry products = Stage18ManufacturingProductRegistry.loadDefault();
        Stage18FacilityRuntime facilityRuntime = new Stage18FacilityRuntime(facilities);
        Stage18StationProductionBridge production = new Stage18StationProductionBridge(
                ontology,
                products,
                facilityRuntime,
                new Stage18ExtractionRuntime(ontology, extraction),
                new Stage18RefiningRuntime(ontology, Stage18RefiningCatalogLoader.loadDefault()),
                new Stage18ManufacturingRuntime(
                        ontology, Stage18ManufacturingCatalogLoader.loadDefault(), products));

        Map<String, StationStorageSnapshot> persistedStorage = new TreeMap<>();
        for (StationStorageSnapshot snapshot : state.industrialState().stationStorages()) {
            persistedStorage.put(snapshot.stationId(), snapshot);
        }
        Map<String, List<FacilityInstallationSnapshot>> persistedFacilities = new TreeMap<>();
        for (FacilityInstallationSnapshot snapshot : state.industrialState().facilities()) {
            persistedFacilities.computeIfAbsent(snapshot.stationId(), ignored -> new ArrayList<>()).add(snapshot);
        }

        ArrayList<MaterializedExtractionOutpost> outposts = new ArrayList<>();
        for (InitialExtractionSite site : sources.initialExtractionSites()) {
            MaterializedSource source = sources.source(site.sourceId());
            CommodityDefinition commodity = Objects.requireNonNull(
                    ontology.findCommodity(source.sourceState().outputCommodityId()),
                    "generated source commodity is absent from installed ontology");
            StationArchetypeDefinition archetype = resolveUniqueArchetype(
                    site, commodity.storageClassId(), infrastructure, facilities);
            String stationId = outpostStationId(site.siteId());
            Stage18StationIndustrialNode node = Stage18StationIndustrialNode.instantiate(
                    stationId, site.locationTag(), archetype, ontology, products);
            InstalledFacilityReference reference = requireExtractionFacilityReference(
                    node, site.facilityDefinitionId());
            FacilityDefinition definition = Objects.requireNonNull(
                    facilities.findFacility(site.facilityDefinitionId()),
                    "generated extraction site references unknown facility");
            if (extraction.findMethod(site.extractionMethodId()) == null) {
                throw new IllegalArgumentException(
                        "generated extraction site references unknown extraction method: " + site.extractionMethodId());
            }

            StationStorageSnapshot savedStorage = persistedStorage.get(stationId);
            List<FacilityInstallationSnapshot> savedFacilities = persistedFacilities.getOrDefault(
                    stationId, List.of());
            if ((savedStorage == null) != savedFacilities.isEmpty()) {
                throw new IllegalArgumentException(
                        "source outpost persistence is partial for site: " + site.siteId());
            }

            Stage18StationStorage storage;
            InstalledFacilityState facilityState;
            if (savedStorage == null) {
                storage = node.storage();
                if (!storage.snapshotCommodityMassByIdKg().isEmpty()
                        || !storage.snapshotProductCountById().isEmpty()) {
                    throw new IllegalStateException("fresh source outpost must start with empty physical storage");
                }
                facilityState = commissionedPristine(reference, definition, site.locationTag());
            } else {
                if (!savedStorage.capacityByStorageClassKg().equals(archetype.storageCapacityByClassKg())) {
                    throw new IllegalArgumentException(
                            "saved source-outpost storage differs from resolved archetype: " + stationId);
                }
                if (savedFacilities.size() != 1) {
                    throw new IllegalArgumentException(
                            "source outpost requires exactly one persisted extraction facility: " + stationId);
                }
                FacilityInstallationSnapshot installation = savedFacilities.get(0);
                facilityState = installation.state();
                if (!facilityState.facilityInstanceId().equals(reference.facilityInstanceId())
                        || !facilityState.definitionId().equals(site.facilityDefinitionId())
                        || !facilityState.locationTag().equals(site.locationTag())) {
                    throw new IllegalArgumentException(
                            "saved source-outpost facility differs from canonical site/archetype: " + stationId);
                }
                storage = Stage18StationStorage.restore(ontology, products, savedStorage);
            }

            FacilityCapabilitySnapshot capability = facilityRuntime.project(facilityState);
            outposts.add(new MaterializedExtractionOutpost(
                    site,
                    source,
                    stationId,
                    archetype.id(),
                    node,
                    storage,
                    facilityState,
                    capability));
        }
        outposts.sort(Comparator.comparing(value -> value.site().siteId()));
        return new MaterializedSourceOutpostRegistry(
                sources, List.copyOf(outposts), production);
    }

    /**
     * Returns the stable runtime station ID owned by one canonical generated extraction site.
     *
     * @param siteId canonical Stage-20 extraction-site ID
     * @return stable ordinary Stage-18 station identity
     */
    public static String outpostStationId(String siteId) {
        return requireText(siteId, "siteId") + OUTPOST_SUFFIX;
    }

    static Set<String> expectedOutpostStationIds(Stage20GeneratedCampaignPersistentState saved) {
        TreeSet<String> ids = new TreeSet<>();
        for (Stage20GeneratedCampaignPersistentState.CanonicalRow row
                : Objects.requireNonNull(saved, "saved").materializedWorld().worldRows()) {
            if (row.domain().equals("INITIAL_EXTRACTION_SITE")) {
                ids.add(outpostStationId(row.stableId()));
            }
        }
        return Set.copyOf(ids);
    }

    private static StationArchetypeDefinition resolveUniqueArchetype(
            InitialExtractionSite site,
            String storageClassId,
            Stage18StationInfrastructureCatalog infrastructure,
            Stage18FacilityCatalog facilities) {
        ArrayList<StationArchetypeDefinition> candidates = new ArrayList<>();
        for (StationArchetypeDefinition archetype : infrastructure.getArchetypes()) {
            if (archetype.installedFacilityDefinitionIds().contains(site.facilityDefinitionId())
                    && archetype.allowedLocationTags().contains(site.locationTag())
                    && archetype.transferStorageClassIds().contains(storageClassId)
                    && archetype.storageCapacityByClassKg().getOrDefault(storageClassId, 0d) > 0d) {
                candidates.add(archetype);
            }
        }
        candidates.sort(Comparator.comparing(StationArchetypeDefinition::id));
        if (candidates.isEmpty()) {
            StationArchetypeDefinition compatible = compatibilityVariant(
                    site, storageClassId, infrastructure, facilities);
            if (compatible != null) {
                return compatible;
            }
        }
        if (candidates.size() != 1) {
            throw new IllegalArgumentException(
                    "source outpost requires exactly one compatible Stage-18 archetype for "
                            + site.siteId()
                            + " [facility=" + site.facilityDefinitionId()
                            + ", location=" + site.locationTag()
                            + ", storageClass=" + storageClassId + "]"
                            + ", candidates="
                            + candidates.stream().map(StationArchetypeDefinition::id).toList());
        }
        return candidates.get(0);
    }

    private static StationArchetypeDefinition compatibilityVariant(
            InitialExtractionSite site,
            String storageClassId,
            Stage18StationInfrastructureCatalog infrastructure,
            Stage18FacilityCatalog facilities) {
        String requiredFacility = switch (site.locationTag()) {
            case "location.surface" -> "facility.extraction.surface";
            case "location.deep_subsurface" -> "facility.extraction.deep";
            default -> "";
        };
        if (!requiredFacility.equals(site.facilityDefinitionId())) {
            return null;
        }
        FacilityDefinition facility = facilities.findFacility(requiredFacility);
        StationArchetypeDefinition chassis = infrastructure.findArchetype(COMPATIBILITY_CHASSIS_ID);
        if (facility == null
                || chassis == null
                || !facility.allowedLocationTags().contains(site.locationTag())
                || !facility.storageClassInterfaces().contains(storageClassId)
                || !chassis.transferStorageClassIds().contains(storageClassId)
                || chassis.storageCapacityByClassKg().getOrDefault(storageClassId, 0d) <= 0d) {
            return null;
        }
        return new StationArchetypeDefinition(
                COMPATIBILITY_AUTHORITY_VERSION + "." + requiredFacility,
                "Generated extraction outpost compatibility variant",
                List.of(requiredFacility),
                chassis.storageCapacityByClassKg(),
                chassis.transferStorageClassIds(),
                chassis.transferMassRateKgPerSecond(),
                chassis.maxTransferUnitMassKg(),
                Set.of(site.locationTag()));
    }

    private static InstalledFacilityReference requireExtractionFacilityReference(
            Stage18StationIndustrialNode node,
            String definitionId) {
        List<InstalledFacilityReference> matches = node.installedFacilities().stream()
                .filter(value -> value.facilityDefinitionId().equals(definitionId))
                .toList();
        if (matches.size() != 1) {
            throw new IllegalArgumentException(
                    "resolved source outpost must install extraction facility exactly once: " + definitionId);
        }
        return matches.get(0);
    }

    private static InstalledFacilityState commissionedPristine(
            InstalledFacilityReference reference,
            FacilityDefinition definition,
            String locationTag) {
        return new InstalledFacilityState(
                reference.facilityInstanceId(),
                definition.id(),
                1d,
                definition.ratedProcessPowerW(),
                definition.ratedProcessPowerW() * definition.heatRejectionWPerProcessW(),
                definition.requiredLaborUnitsAtFullRate(),
                definition.maintenanceWorkRate(),
                locationTag,
                true);
    }

    /**
     * One live generated extraction site bound to ordinary Stage-18 source, storage and facility state.
     *
     * @param site immutable canonical generated site binding
     * @param source live finite generated source
     * @param stationId stable ordinary Stage-18 outpost identity
     * @param stationArchetypeId unique physically compatible Stage-18 infrastructure archetype
     * @param stationNode ordinary Stage-18 station/outpost composition
     * @param storage ordinary mutable Stage-18 physical storage
     * @param facilityState exact mutable-world facility allocation state
     * @param facilityCapability current physical extraction capability projection
     */
    public record MaterializedExtractionOutpost(
            InitialExtractionSite site,
            MaterializedSource source,
            String stationId,
            String stationArchetypeId,
            Stage18StationIndustrialNode stationNode,
            Stage18StationStorage storage,
            InstalledFacilityState facilityState,
            FacilityCapabilitySnapshot facilityCapability) {
        /**
         * Validates one live source-outpost binding.
         *
         * @param site canonical generated site
         * @param source matching generated source
         * @param stationId runtime outpost identity
         * @param stationArchetypeId resolved infrastructure archetype
         * @param stationNode ordinary station node
         * @param storage ordinary station storage
         * @param facilityState installed extraction facility state
         * @param facilityCapability projected extraction capability
         */
        public MaterializedExtractionOutpost {
            Objects.requireNonNull(site, "site");
            Objects.requireNonNull(source, "source");
            stationId = requireText(stationId, "stationId");
            stationArchetypeId = requireText(stationArchetypeId, "stationArchetypeId");
            Objects.requireNonNull(stationNode, "stationNode");
            Objects.requireNonNull(storage, "storage");
            Objects.requireNonNull(facilityState, "facilityState");
            Objects.requireNonNull(facilityCapability, "facilityCapability");
            if (!site.sourceId().equals(source.sourceId())
                    || !stationId.equals(stationNode.stationId())
                    || !stationId.equals(storage.stationId())
                    || !site.facilityDefinitionId().equals(facilityState.definitionId())) {
                throw new IllegalArgumentException("source-outpost runtime identity mismatch");
            }
        }
    }

    /** Live deterministic registry for all generated initial extraction outposts. */
    public static final class MaterializedSourceOutpostRegistry {
        private final MaterializedSourceRegistry sources;
        private final List<MaterializedExtractionOutpost> outposts;
        private final Map<String, MaterializedExtractionOutpost> bySiteId;
        private final Stage18StationProductionBridge production;

        private MaterializedSourceOutpostRegistry(
                MaterializedSourceRegistry sources,
                List<MaterializedExtractionOutpost> outposts,
                Stage18StationProductionBridge production) {
            this.sources = Objects.requireNonNull(sources, "sources");
            this.production = Objects.requireNonNull(production, "production");
            ArrayList<MaterializedExtractionOutpost> copy = new ArrayList<>(
                    Objects.requireNonNull(outposts, "outposts"));
            copy.sort(Comparator.comparing(value -> value.site().siteId()));
            TreeMap<String, MaterializedExtractionOutpost> index = new TreeMap<>();
            for (MaterializedExtractionOutpost outpost : copy) {
                if (index.putIfAbsent(outpost.site().siteId(), outpost) != null) {
                    throw new IllegalArgumentException("duplicate materialized extraction site");
                }
            }
            this.outposts = List.copyOf(copy);
            this.bySiteId = Map.copyOf(index);
        }

        /** @return Stage-20.5A source-outpost materialization version */
        public String version() {
            return CURRENT_VERSION;
        }

        /** @return underlying live generated source registry */
        public MaterializedSourceRegistry sources() {
            return sources;
        }

        /** @return stable site-ID ordered live extraction outposts */
        public List<MaterializedExtractionOutpost> outposts() {
            return outposts;
        }

        /**
         * Finds one live extraction outpost by canonical generated site ID.
         *
         * @param siteId canonical generated extraction-site ID
         * @return matching live outpost
         */
        public MaterializedExtractionOutpost outpost(String siteId) {
            MaterializedExtractionOutpost result = bySiteId.get(requireText(siteId, "siteId"));
            if (result == null) {
                throw new IllegalArgumentException("unknown materialized extraction site: " + siteId);
            }
            return result;
        }

        /**
         * Executes real finite extraction into the outpost's ordinary Stage-18 storage.
         *
         * @param siteId canonical generated site identity
         * @param requestedSourceMassKg gross finite source mass requested for removal
         * @param durationSeconds finite processing interval
         * @return ordinary Stage-18 extraction settlement result
         */
        public ExtractionResult extract(
                String siteId,
                double requestedSourceMassKg,
                double durationSeconds) {
            MaterializedExtractionOutpost outpost = outpost(siteId);
            return production.extractToStation(
                    outpost.source().sourceState(),
                    outpost.site().extractionMethodId(),
                    requestedSourceMassKg,
                    outpost.storage(),
                    outpost.facilityCapability(),
                    durationSeconds);
        }

        /**
         * Captures live natural reserves and source-outpost state while preserving other Stage-18 state.
         *
         * @param base current aggregate industrial state containing unrelated stations/salvage/orders
         * @return complete deterministic Stage-18 industrial state
         */
        public Stage18IndustrialState captureIndustrialState(Stage18IndustrialState base) {
            Stage18IndustrialState previous = Objects.requireNonNull(base, "base");
            TreeMap<String, PhysicalSourceSnapshot> sourceById = new TreeMap<>();
            for (PhysicalSourceSnapshot snapshot : previous.sources()) {
                if (snapshot.sourceKind() != Stage18ExtractionCatalog.SourceKind.NATURAL_OCCURRENCE) {
                    sourceById.put(snapshot.sourceId(), snapshot);
                }
            }
            for (PhysicalSourceSnapshot snapshot : sources.captureSourceSnapshots()) {
                if (sourceById.putIfAbsent(snapshot.sourceId(), snapshot) != null) {
                    throw new IllegalArgumentException("source identity collision while capturing source outposts");
                }
            }

            Set<String> ownedStationIds = new HashSet<>();
            outposts.forEach(value -> ownedStationIds.add(value.stationId()));
            ArrayList<StationStorageSnapshot> storage = new ArrayList<>();
            for (StationStorageSnapshot snapshot : previous.stationStorages()) {
                if (!ownedStationIds.contains(snapshot.stationId())) {
                    storage.add(snapshot);
                }
            }
            ArrayList<FacilityInstallationSnapshot> facilities = new ArrayList<>();
            for (FacilityInstallationSnapshot snapshot : previous.facilities()) {
                if (!ownedStationIds.contains(snapshot.stationId())) {
                    facilities.add(snapshot);
                }
            }
            for (MaterializedExtractionOutpost outpost : outposts) {
                storage.add(outpost.storage().snapshot());
                facilities.add(new FacilityInstallationSnapshot(
                        outpost.stationId(), outpost.facilityState()));
            }
            return new Stage18IndustrialState(
                    Stage18IndustrialState.CURRENT_VERSION,
                    previous.contentFingerprint(),
                    previous.simulationTick(),
                    List.copyOf(sourceById.values()),
                    storage,
                    facilities,
                    previous.yards(),
                    previous.constructionOrders(),
                    previous.processOrders());
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }
}
