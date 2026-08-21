package com.spacesim.world.generation;

import com.spacesim.world.Stage20ExtractionSiteLogisticsResolver.ResolutionStatus;
import com.spacesim.world.Stage20FactionStartDependencyDiagnostics.Requirement;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.InitialExtractionSite;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.ResourceOccurrence;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyKey;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyThroughputReport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Read-only Stage-20E diagnostics that locate where essential physical supply is lost in the fixed
 * representative generated-world corpus.
 *
 * <p>The report deliberately separates four already-authoritative layers: generated finite
 * occurrences, generated initial extraction sites, extraction-site logistics resolution and the
 * final non-reserved physical supply-throughput closure. It does not add resources, choose an
 * ambiguous logistics archetype, allocate extra freighters, relax route horizons or modify any
 * acceptance threshold.</p>
 */
public final class Stage20RepresentativeSupplyBottleneckDiagnostics {
    /** Current deterministic diagnostics schema/version. */
    public static final String CURRENT_VERSION = "stage20e.representative-supply-bottleneck-diagnostics.v1";

    private Stage20RepresentativeSupplyBottleneckDiagnostics() {
        throw new AssertionError("No instances");
    }

    /**
     * Essential-commodity evidence for one generated seed.
     *
     * @param commodityId authoritative Stage-18 commodity ID
     * @param requiredKgPerSecond current provenance-backed bootstrap service requirement
     * @param occurrenceCount generated finite source occurrences for the commodity
     * @param initialSiteCount generated initial extraction sites for those occurrences
     * @param resolvedSiteCount sites with exactly one physically compatible logistics archetype
     * @param noCompatibleArchetypeSiteCount sites with no compatible logistics archetype
     * @param ambiguousArchetypeSiteCount sites with multiple compatible logistics archetypes
     * @param producerSystemCount systems contributing resolved supply to the throughput closure
     * @param totalResolvedSupplyKgPerSecond global resolved supply before candidate-specific routing
     * @param maximumProducerSystemSupplyKgPerSecond largest resolved producer-system supply
     */
    public record CommodityEvidence(
            String commodityId,
            double requiredKgPerSecond,
            int occurrenceCount,
            int initialSiteCount,
            int resolvedSiteCount,
            int noCompatibleArchetypeSiteCount,
            int ambiguousArchetypeSiteCount,
            int producerSystemCount,
            double totalResolvedSupplyKgPerSecond,
            double maximumProducerSystemSupplyKgPerSecond) {
        /**
         * Validates one immutable commodity evidence row.
         *
         * @param commodityId authoritative commodity ID
         * @param requiredKgPerSecond required service throughput
         * @param occurrenceCount generated occurrences
         * @param initialSiteCount generated initial extraction sites
         * @param resolvedSiteCount resolved site bindings
         * @param noCompatibleArchetypeSiteCount sites without a compatible logistics archetype
         * @param ambiguousArchetypeSiteCount sites with ambiguous compatible archetypes
         * @param producerSystemCount resolved producer systems
         * @param totalResolvedSupplyKgPerSecond global resolved supply
         * @param maximumProducerSystemSupplyKgPerSecond maximum producer-system contribution
         */
        public CommodityEvidence {
            commodityId = requireText(commodityId, "commodityId");
            requirePositiveFinite(requiredKgPerSecond, "requiredKgPerSecond");
            requireNonNegative(occurrenceCount, "occurrenceCount");
            requireNonNegative(initialSiteCount, "initialSiteCount");
            requireNonNegative(resolvedSiteCount, "resolvedSiteCount");
            requireNonNegative(noCompatibleArchetypeSiteCount, "noCompatibleArchetypeSiteCount");
            requireNonNegative(ambiguousArchetypeSiteCount, "ambiguousArchetypeSiteCount");
            requireNonNegative(producerSystemCount, "producerSystemCount");
            requireNonNegativeFinite(totalResolvedSupplyKgPerSecond, "totalResolvedSupplyKgPerSecond");
            requireNonNegativeFinite(maximumProducerSystemSupplyKgPerSecond,
                    "maximumProducerSystemSupplyKgPerSecond");
            if (resolvedSiteCount + noCompatibleArchetypeSiteCount + ambiguousArchetypeSiteCount
                    != initialSiteCount) {
                throw new IllegalArgumentException("commodity logistics counts must equal initialSiteCount");
            }
            if (maximumProducerSystemSupplyKgPerSecond > totalResolvedSupplyKgPerSecond + 1.0e-9d) {
                throw new IllegalArgumentException("maximum producer supply cannot exceed global resolved supply");
            }
            if ((producerSystemCount == 0) != (totalResolvedSupplyKgPerSecond == 0d)) {
                throw new IllegalArgumentException("producer count and resolved supply presence are inconsistent");
            }
        }

        /** @return global resolved-supply headroom before route/freight accessibility. */
        public double globalHeadroomKgPerSecond() {
            return totalResolvedSupplyKgPerSecond - requiredKgPerSecond;
        }
    }

    /**
     * One seed-level physical supply bottleneck summary.
     *
     * @param rootSeed exact measured seed
     * @param occurrenceCount all generated finite occurrences
     * @param initialExtractionSiteCount all generated initial extraction sites
     * @param resolvedLogisticsSiteCount all uniquely resolved extraction-site logistics bindings
     * @param noCompatibleArchetypeSiteCount all sites with no compatible logistics archetype
     * @param ambiguousArchetypeSiteCount all sites with multiple compatible logistics archetypes
     * @param unresolvedSupplySiteCount sites omitted from the supply closure because export handling is unresolved
     * @param commodities essential-commodity evidence in deterministic ID order
     */
    public record SeedSummary(
            long rootSeed,
            int occurrenceCount,
            int initialExtractionSiteCount,
            int resolvedLogisticsSiteCount,
            int noCompatibleArchetypeSiteCount,
            int ambiguousArchetypeSiteCount,
            int unresolvedSupplySiteCount,
            List<CommodityEvidence> commodities) {
        /**
         * Validates and freezes one seed summary.
         *
         * @param rootSeed exact measured seed
         * @param occurrenceCount all generated finite occurrences
         * @param initialExtractionSiteCount all generated initial extraction sites
         * @param resolvedLogisticsSiteCount uniquely resolved extraction-site logistics bindings
         * @param noCompatibleArchetypeSiteCount sites without a compatible logistics archetype
         * @param ambiguousArchetypeSiteCount sites with ambiguous compatible archetypes
         * @param unresolvedSupplySiteCount sites omitted from resolved supply
         * @param commodities deterministic essential-commodity evidence
         */
        public SeedSummary {
            requireNonNegative(occurrenceCount, "occurrenceCount");
            requireNonNegative(initialExtractionSiteCount, "initialExtractionSiteCount");
            requireNonNegative(resolvedLogisticsSiteCount, "resolvedLogisticsSiteCount");
            requireNonNegative(noCompatibleArchetypeSiteCount, "noCompatibleArchetypeSiteCount");
            requireNonNegative(ambiguousArchetypeSiteCount, "ambiguousArchetypeSiteCount");
            requireNonNegative(unresolvedSupplySiteCount, "unresolvedSupplySiteCount");
            if (resolvedLogisticsSiteCount + noCompatibleArchetypeSiteCount + ambiguousArchetypeSiteCount
                    != initialExtractionSiteCount) {
                throw new IllegalArgumentException("seed logistics counts must equal initial extraction sites");
            }
            Objects.requireNonNull(commodities, "commodities");
            ArrayList<CommodityEvidence> copy = new ArrayList<>(commodities);
            if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("commodities must be non-empty and contain no nulls");
            }
            copy.sort(Comparator.comparing(CommodityEvidence::commodityId));
            if (copy.stream().map(CommodityEvidence::commodityId).distinct().count() != copy.size()) {
                throw new IllegalArgumentException("duplicate essential commodity evidence");
            }
            commodities = List.copyOf(copy);
        }
    }

    /**
     * Aggregate fixed-corpus supply bottleneck evidence.
     *
     * @param version exact diagnostics version
     * @param corpusVersion exact fixed corpus version
     * @param representativeProfileVersion exact production-probe profile version
     * @param bootstrapRequirementVersion exact bootstrap requirement authority version
     * @param seeds deterministic seed summaries
     */
    public record Report(
            String version,
            String corpusVersion,
            String representativeProfileVersion,
            String bootstrapRequirementVersion,
            List<SeedSummary> seeds) {
        /**
         * Validates and freezes one aggregate report.
         *
         * @param version exact diagnostics version
         * @param corpusVersion exact fixed corpus version
         * @param representativeProfileVersion exact production-probe profile version
         * @param bootstrapRequirementVersion exact bootstrap requirement authority version
         * @param seeds deterministic seed summaries
         */
        public Report {
            version = requireText(version, "version");
            corpusVersion = requireText(corpusVersion, "corpusVersion");
            representativeProfileVersion = requireText(representativeProfileVersion,
                    "representativeProfileVersion");
            bootstrapRequirementVersion = requireText(bootstrapRequirementVersion,
                    "bootstrapRequirementVersion");
            Objects.requireNonNull(seeds, "seeds");
            ArrayList<SeedSummary> copy = new ArrayList<>(seeds);
            if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("seeds must be non-empty and contain no nulls");
            }
            copy.sort(Comparator.comparingLong(SeedSummary::rootSeed));
            if (copy.stream().map(SeedSummary::rootSeed).distinct().count() != copy.size()) {
                throw new IllegalArgumentException("duplicate seed summary");
            }
            seeds = List.copyOf(copy);
        }
    }

    /**
     * Replays the fixed representative corpus and measures supply loss across existing physical layers.
     *
     * @return deterministic read-only diagnostics
     */
    public static Report evaluateCurrent() {
        Stage20RepresentativeGeneratedWorldProbeProfile.DerivedProfile profile =
                Stage20RepresentativeGeneratedWorldProbeProfile.deriveCurrent();
        List<Requirement> requirements = profile.inputs().acceptance().dependencyRequirements();
        ArrayList<SeedSummary> seeds = new ArrayList<>();

        for (long rootSeed : Stage20RepresentativeSeedCorpus.seeds()) {
            Stage20GeneratedWorldProductionProbe.ProbeResult probe =
                    Stage20GeneratedWorldProductionProbe.run(rootSeed, profile.inputs());
            var world = probe.resourceWorld().orElseThrow(() -> new IllegalStateException(
                    "fixed representative baseline unexpectedly lacks generated resource world for seed " + rootSeed));
            var logistics = probe.logisticsReport().orElseThrow(() -> new IllegalStateException(
                    "fixed representative baseline unexpectedly lacks logistics report for seed " + rootSeed));
            SupplyThroughputReport supply = probe.supplyThroughput().orElseThrow(() -> new IllegalStateException(
                    "fixed representative baseline unexpectedly lacks supply report for seed " + rootSeed));

            Map<String, ResourceOccurrence> occurrenceBySource = new HashMap<>();
            TreeMap<String, Integer> occurrenceCounts = new TreeMap<>();
            for (ResourceOccurrence occurrence : world.occurrences()) {
                occurrenceBySource.put(occurrence.sourceId(), occurrence);
                occurrenceCounts.merge(occurrence.outputCommodityId(), 1, Math::addExact);
            }

            TreeMap<String, Integer> siteCounts = new TreeMap<>();
            TreeMap<String, Integer> resolvedByCommodity = new TreeMap<>();
            TreeMap<String, Integer> noCompatibleByCommodity = new TreeMap<>();
            TreeMap<String, Integer> ambiguousByCommodity = new TreeMap<>();
            int resolvedSites = 0;
            int noCompatibleSites = 0;
            int ambiguousSites = 0;
            for (InitialExtractionSite site : world.initialExtractionSites()) {
                ResourceOccurrence occurrence = Objects.requireNonNull(
                        occurrenceBySource.get(site.sourceId()), "site source must exist in generated occurrence map");
                String commodityId = occurrence.outputCommodityId();
                siteCounts.merge(commodityId, 1, Math::addExact);
                ResolutionStatus status = logistics.binding(site.siteId()).status();
                switch (status) {
                    case RESOLVED -> {
                        resolvedSites++;
                        resolvedByCommodity.merge(commodityId, 1, Math::addExact);
                    }
                    case NO_COMPATIBLE_ARCHETYPE -> {
                        noCompatibleSites++;
                        noCompatibleByCommodity.merge(commodityId, 1, Math::addExact);
                    }
                    case AMBIGUOUS_COMPATIBLE_ARCHETYPES -> {
                        ambiguousSites++;
                        ambiguousByCommodity.merge(commodityId, 1, Math::addExact);
                    }
                }
            }

            TreeMap<String, Double> totalSupply = new TreeMap<>();
            TreeMap<String, Double> maximumSystemSupply = new TreeMap<>();
            TreeMap<String, Integer> producerSystemCount = new TreeMap<>();
            for (Map.Entry<SupplyKey, Double> entry : supply.capacityKgPerSecondBySupply().entrySet()) {
                String commodityId = entry.getKey().commodityId();
                double capacity = entry.getValue();
                totalSupply.merge(commodityId, capacity, Double::sum);
                maximumSystemSupply.merge(commodityId, capacity, Math::max);
                producerSystemCount.merge(commodityId, 1, Math::addExact);
            }

            ArrayList<CommodityEvidence> commodityEvidence = new ArrayList<>();
            for (Requirement requirement : requirements) {
                String commodityId = requirement.commodityId();
                commodityEvidence.add(new CommodityEvidence(
                        commodityId,
                        requirement.requiredKgPerSecond(),
                        occurrenceCounts.getOrDefault(commodityId, 0),
                        siteCounts.getOrDefault(commodityId, 0),
                        resolvedByCommodity.getOrDefault(commodityId, 0),
                        noCompatibleByCommodity.getOrDefault(commodityId, 0),
                        ambiguousByCommodity.getOrDefault(commodityId, 0),
                        producerSystemCount.getOrDefault(commodityId, 0),
                        totalSupply.getOrDefault(commodityId, 0d),
                        maximumSystemSupply.getOrDefault(commodityId, 0d)));
            }

            seeds.add(new SeedSummary(
                    rootSeed,
                    world.occurrences().size(),
                    world.initialExtractionSites().size(),
                    resolvedSites,
                    noCompatibleSites,
                    ambiguousSites,
                    supply.unresolvedExtractionSiteIds().size(),
                    commodityEvidence));
        }

        return new Report(
                CURRENT_VERSION,
                Stage20RepresentativeSeedCorpus.CURRENT_VERSION,
                profile.version(),
                profile.bootstrapRequirementVersion(),
                seeds);
    }

    /**
     * Serializes compact deterministic evidence for repository CI inspection.
     *
     * @param report measured report
     * @return deterministic text ending with a newline
     */
    public static String toText(Report report) {
        Report value = Objects.requireNonNull(report, "report");
        StringBuilder text = new StringBuilder(16_384);
        text.append("version=").append(value.version()).append('\n');
        text.append("corpusVersion=").append(value.corpusVersion()).append('\n');
        text.append("representativeProfileVersion=").append(value.representativeProfileVersion()).append('\n');
        text.append("bootstrapRequirementVersion=").append(value.bootstrapRequirementVersion()).append('\n');
        for (SeedSummary seed : value.seeds()) {
            text.append("seed=").append(seed.rootSeed())
                    .append(" occurrences=").append(seed.occurrenceCount())
                    .append(" sites=").append(seed.initialExtractionSiteCount())
                    .append(" resolvedSites=").append(seed.resolvedLogisticsSiteCount())
                    .append(" noCompatibleSites=").append(seed.noCompatibleArchetypeSiteCount())
                    .append(" ambiguousSites=").append(seed.ambiguousArchetypeSiteCount())
                    .append(" unresolvedSupplySites=").append(seed.unresolvedSupplySiteCount())
                    .append('\n');
            for (CommodityEvidence commodity : seed.commodities()) {
                text.append("  commodity=").append(commodity.commodityId())
                        .append(" requiredKgS=").append(commodity.requiredKgPerSecond())
                        .append(" occurrences=").append(commodity.occurrenceCount())
                        .append(" sites=").append(commodity.initialSiteCount())
                        .append(" resolvedSites=").append(commodity.resolvedSiteCount())
                        .append(" noCompatibleSites=").append(commodity.noCompatibleArchetypeSiteCount())
                        .append(" ambiguousSites=").append(commodity.ambiguousArchetypeSiteCount())
                        .append(" producerSystems=").append(commodity.producerSystemCount())
                        .append(" totalSupplyKgS=").append(commodity.totalResolvedSupplyKgPerSecond())
                        .append(" maxSystemSupplyKgS=").append(commodity.maximumProducerSystemSupplyKgPerSecond())
                        .append(" globalHeadroomKgS=").append(commodity.globalHeadroomKgPerSecond())
                        .append('\n');
            }
        }
        return text.toString();
    }

    private static void requireNonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
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

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }
}
