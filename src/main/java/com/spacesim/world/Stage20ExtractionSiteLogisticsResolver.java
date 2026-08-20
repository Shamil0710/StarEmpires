package com.spacesim.world;

import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyCatalog.CommodityDefinition;
import com.spacesim.content.Stage18StationInfrastructureCatalog;
import com.spacesim.content.Stage18StationInfrastructureCatalog.StationArchetypeDefinition;
import com.spacesim.world.Stage20BootstrapProductionCapacityCalculator.SourceExportHandlingProvider;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.InitialExtractionSite;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.ResourceOccurrence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeSet;

/**
 * Stage-20E resolver for explicit extraction-site logistics bindings using only existing Stage-18
 * station/outpost archetypes.
 *
 * <p>A site is resolved only when exactly one Stage-18 archetype simultaneously:</p>
 * <ul>
 *   <li>installs the generated site's extraction/processing facility;</li>
 *   <li>is allowed on the same physical location tag;</li>
 *   <li>has positive storage for the source commodity's storage class;</li>
 *   <li>can transfer that storage class through its physical cargo interface.</li>
 * </ul>
 *
 * <p>Zero candidates remain unresolved. Multiple candidates are also unresolved because choosing
 * between materially different infrastructure archetypes is a world-generation policy decision,
 * not a lexical or hidden optimization shortcut.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20ExtractionSiteLogisticsResolver {
    private Stage20ExtractionSiteLogisticsResolver() {
        throw new AssertionError("No instances");
    }

    /** Stable resolution status for one generated extraction site. */
    public enum ResolutionStatus {
        /** Exactly one existing physical Stage-18 logistics archetype fits the generated site. */ RESOLVED,
        /** No existing Stage-18 station/outpost archetype can provide the required physical handling. */ NO_COMPATIBLE_ARCHETYPE,
        /** More than one archetype fits and an explicit generation policy must choose between them. */ AMBIGUOUS_COMPATIBLE_ARCHETYPES
    }

    /**
     * One deterministic site-logistics binding diagnostic.
     *
     * @param siteId generated extraction site identity
     * @param sourceId generated finite source identity
     * @param systemId owning star system
     * @param storageClassId Stage-18 storage class that must be exported
     * @param status resolution status
     * @param compatibleArchetypeIds all physically compatible existing archetype IDs
     * @param resolvedArchetypeId unique selected identity only when resolved
     * @param resolvedTransferKgPerSecond unique physical transfer rate only when resolved
     */
    public record SiteLogisticsBinding(
            String siteId,
            String sourceId,
            StarSystemId systemId,
            String storageClassId,
            ResolutionStatus status,
            Set<String> compatibleArchetypeIds,
            Optional<String> resolvedArchetypeId,
            OptionalDouble resolvedTransferKgPerSecond) {
        /** Validates and freezes one deterministic logistics binding. */
        public SiteLogisticsBinding {
            siteId = requireText(siteId, "siteId");
            sourceId = requireText(sourceId, "sourceId");
            Objects.requireNonNull(systemId, "systemId");
            storageClassId = requireText(storageClassId, "storageClassId");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(compatibleArchetypeIds, "compatibleArchetypeIds");
            TreeSet<String> candidates = new TreeSet<>();
            for (String candidate : compatibleArchetypeIds) {
                candidates.add(requireText(candidate, "compatible archetype ID"));
            }
            compatibleArchetypeIds = Collections.unmodifiableSet(candidates);
            Objects.requireNonNull(resolvedArchetypeId, "resolvedArchetypeId");
            Objects.requireNonNull(resolvedTransferKgPerSecond, "resolvedTransferKgPerSecond");
            if (status == ResolutionStatus.RESOLVED) {
                if (compatibleArchetypeIds.size() != 1
                        || resolvedArchetypeId.isEmpty()
                        || resolvedTransferKgPerSecond.isEmpty()
                        || !compatibleArchetypeIds.contains(resolvedArchetypeId.orElseThrow())) {
                    throw new IllegalArgumentException("resolved logistics binding requires exactly one matching archetype/rate");
                }
                requirePositiveFinite(resolvedTransferKgPerSecond.getAsDouble(), "resolvedTransferKgPerSecond");
            } else if (resolvedArchetypeId.isPresent() || resolvedTransferKgPerSecond.isPresent()) {
                throw new IllegalArgumentException("unresolved logistics binding cannot expose selected archetype/rate");
            }
            if (status == ResolutionStatus.NO_COMPATIBLE_ARCHETYPE && !compatibleArchetypeIds.isEmpty()) {
                throw new IllegalArgumentException("NO_COMPATIBLE_ARCHETYPE requires an empty candidate set");
            }
            if (status == ResolutionStatus.AMBIGUOUS_COMPATIBLE_ARCHETYPES
                    && compatibleArchetypeIds.size() < 2) {
                throw new IllegalArgumentException("ambiguous binding requires at least two candidates");
            }
        }
    }

    /**
     * Complete deterministic logistics-resolution result.
     *
     * @param bindings one row for every generated initial extraction site
     */
    public record ResolutionReport(List<SiteLogisticsBinding> bindings) {
        /** Validates and freezes one report. */
        public ResolutionReport {
            Objects.requireNonNull(bindings, "bindings");
            ArrayList<SiteLogisticsBinding> copy = new ArrayList<>(bindings);
            if (copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("bindings cannot contain nulls");
            }
            copy.sort(Comparator.comparing(SiteLogisticsBinding::systemId)
                    .thenComparing(SiteLogisticsBinding::siteId));
            TreeSet<String> ids = new TreeSet<>();
            for (SiteLogisticsBinding binding : copy) {
                if (!ids.add(binding.siteId())) {
                    throw new IllegalArgumentException("duplicate site logistics binding: " + binding.siteId());
                }
            }
            bindings = List.copyOf(copy);
        }

        /**
         * Returns one generated site's logistics row.
         *
         * @param siteId generated site identity
         * @return binding row
         */
        public SiteLogisticsBinding binding(String siteId) {
            String checked = requireText(siteId, "siteId");
            return bindings.stream()
                    .filter(value -> value.siteId().equals(checked))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("unknown site logistics binding: " + checked));
        }

        /**
         * Adapts resolved bindings to the Stage-20E source export-handling seam.
         *
         * @return provider that remains empty for unresolved/ambiguous sites
         */
        public SourceExportHandlingProvider asExportHandlingProvider() {
            return (site, source) -> {
                Objects.requireNonNull(site, "site");
                Objects.requireNonNull(source, "source");
                SiteLogisticsBinding binding = binding(site.siteId());
                if (!binding.sourceId().equals(source.sourceId())
                        || !binding.systemId().equals(source.systemId())) {
                    throw new IllegalArgumentException("site/source mismatch for logistics binding: " + site.siteId());
                }
                return binding.resolvedTransferKgPerSecond();
            };
        }
    }

    /**
     * Resolves all generated initial extraction sites against existing Stage-18 infrastructure.
     *
     * @param world generated Stage-20E resource world
     * @param ontology authoritative Stage-18 resource ontology
     * @param stationInfrastructure authoritative Stage-18 station/outpost archetypes
     * @return one explicit deterministic resolution row per initial extraction site
     */
    public static ResolutionReport resolve(
            Stage20ResourceOccurrenceWorld world,
            Stage18ResourceOntologyCatalog ontology,
            Stage18StationInfrastructureCatalog stationInfrastructure) {
        Stage20ResourceOccurrenceWorld checkedWorld = Objects.requireNonNull(world, "world");
        Stage18ResourceOntologyCatalog checkedOntology = Objects.requireNonNull(ontology, "ontology");
        Stage18StationInfrastructureCatalog checkedStations = Objects.requireNonNull(
                stationInfrastructure, "stationInfrastructure");
        ArrayList<SiteLogisticsBinding> rows = new ArrayList<>();
        for (InitialExtractionSite site : checkedWorld.initialExtractionSites()) {
            ResourceOccurrence source = checkedWorld.occurrence(site.sourceId());
            CommodityDefinition commodity = checkedOntology.findCommodity(source.outputCommodityId());
            if (commodity == null) {
                throw new IllegalArgumentException("generated source references unknown commodity: " + source.outputCommodityId());
            }
            String storageClassId = commodity.storageClassId();
            ArrayList<StationArchetypeDefinition> candidates = new ArrayList<>();
            for (StationArchetypeDefinition archetype : checkedStations.getArchetypes()) {
                if (!archetype.installedFacilityDefinitionIds().contains(site.facilityDefinitionId())) {
                    continue;
                }
                if (!archetype.allowedLocationTags().contains(site.locationTag())) {
                    continue;
                }
                if (!archetype.transferStorageClassIds().contains(storageClassId)) {
                    continue;
                }
                if (archetype.storageCapacityByClassKg().getOrDefault(storageClassId, 0d) <= 0d) {
                    continue;
                }
                candidates.add(archetype);
            }
            candidates.sort(Comparator.comparing(StationArchetypeDefinition::id));
            TreeSet<String> candidateIds = new TreeSet<>();
            for (StationArchetypeDefinition candidate : candidates) {
                candidateIds.add(candidate.id());
            }
            if (candidates.size() == 1) {
                StationArchetypeDefinition resolved = candidates.get(0);
                rows.add(new SiteLogisticsBinding(
                        site.siteId(),
                        site.sourceId(),
                        site.systemId(),
                        storageClassId,
                        ResolutionStatus.RESOLVED,
                        candidateIds,
                        Optional.of(resolved.id()),
                        OptionalDouble.of(resolved.transferMassRateKgPerSecond())));
            } else {
                rows.add(new SiteLogisticsBinding(
                        site.siteId(),
                        site.sourceId(),
                        site.systemId(),
                        storageClassId,
                        candidates.isEmpty()
                                ? ResolutionStatus.NO_COMPATIBLE_ARCHETYPE
                                : ResolutionStatus.AMBIGUOUS_COMPATIBLE_ARCHETYPES,
                        candidateIds,
                        Optional.empty(),
                        OptionalDouble.empty()));
            }
        }
        return new ResolutionReport(rows);
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
}
