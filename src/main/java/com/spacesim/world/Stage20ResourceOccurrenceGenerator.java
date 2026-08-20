package com.spacesim.world;

import com.spacesim.content.Stage18ExtractionCatalog;
import com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment;
import com.spacesim.content.Stage18ExtractionCatalog.ExtractionMethodDefinition;
import com.spacesim.content.Stage18ExtractionCatalog.SourceKind;
import com.spacesim.content.Stage18FacilityCatalog;
import com.spacesim.content.Stage18FacilityCatalog.FacilityDefinition;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyCatalog.ResourceOccurrenceTypeDefinition;
import com.spacesim.simulation.SimulationRandom;
import com.spacesim.simulation.StatefulRandom;
import com.spacesim.world.Stage20LocalInfrastructureLayout.InfrastructurePlacement;
import com.spacesim.world.Stage20LocalInfrastructureLayout.PlacementKind;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.InitialExtractionSite;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.ResourceOccurrence;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.SystemResourceConditions;
import com.spacesim.world.calibration.Stage20ResourceGenerationProfile;
import com.spacesim.world.calibration.Stage20ResourceGenerationProfile.OccurrenceBand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Deterministic Stage-20E generator for correlated Stage-18 natural occurrences and initial sites.
 *
 * <p>Generation is intentionally split into latent graph-correlated physical conditions and
 * host-local realization. It never adds a new resource family, never infers a host class from an
 * anchor name and never inserts a fallback deposit to rescue a later economic failure.</p>
 */
public final class Stage20ResourceOccurrenceGenerator {
    private static final String RNG_PREFIX = "stage20e.resource-geography.v1";

    private Stage20ResourceOccurrenceGenerator() {
        throw new AssertionError("No instances");
    }

    /**
     * Explicit physical semantics for one Stage-20C resource-field anchor.
     *
     * <p>{@code hostClassId} and the affinity map are generation provenance, not a second resource
     * taxonomy. Affinity keys must be authoritative Stage-18 occurrence type IDs. A zero/missing
     * affinity means this physical host cannot realize that occurrence through this profile.</p>
     *
     * @param systemId owning system
     * @param anchorId Stage-20C resource-field anchor ID
     * @param hostClassId stable generation-only physical host classifier
     * @param environment authoritative Stage-18 extraction environment
     * @param locationTag physical Stage-18 facility installation location tag
     * @param occurrenceAffinityByTypeId non-negative host affinity by Stage-18 occurrence type ID
     * @param sourceRequiredCapabilityTags source-specific extraction capabilities beyond method baseline
     */
    public record ResourceHostProfile(
            StarSystemId systemId,
            String anchorId,
            String hostClassId,
            ExtractionEnvironment environment,
            String locationTag,
            Map<String, Double> occurrenceAffinityByTypeId,
            Set<String> sourceRequiredCapabilityTags) {
        /** Validates and deterministically freezes one host profile. */
        public ResourceHostProfile {
            Objects.requireNonNull(systemId, "systemId");
            anchorId = requireText(anchorId, "anchorId");
            hostClassId = requireText(hostClassId, "hostClassId");
            Objects.requireNonNull(environment, "environment");
            locationTag = requireText(locationTag, "locationTag");
            Objects.requireNonNull(occurrenceAffinityByTypeId, "occurrenceAffinityByTypeId");
            TreeMap<String, Double> affinities = new TreeMap<>();
            for (Map.Entry<String, Double> entry : occurrenceAffinityByTypeId.entrySet()) {
                String typeId = requireText(entry.getKey(), "occurrence affinity type");
                double value = Objects.requireNonNull(entry.getValue(), "occurrence affinity");
                if (!Double.isFinite(value) || value < 0d || value > 2d) {
                    throw new IllegalArgumentException("host occurrence affinity must be in [0,2]");
                }
                affinities.put(typeId, value);
            }
            if (affinities.isEmpty()) {
                throw new IllegalArgumentException("host must expose at least one Stage-18 occurrence affinity");
            }
            occurrenceAffinityByTypeId = Collections.unmodifiableMap(affinities);
            sourceRequiredCapabilityTags = immutableTags(sourceRequiredCapabilityTags);
        }
    }

    /**
     * Generates correlated resource conditions, concrete finite sources and explicit initial sites.
     *
     * @param rootSeed deterministic world-generation seed
     * @param topology authoritative explicit neighbor topology
     * @param localLayouts Stage-20C local infrastructure layouts containing requested resource anchors
     * @param hosts explicit physical semantics for resource-field anchors
     * @param ontology authoritative Stage-18 resource ontology
     * @param extractionCatalog authoritative Stage-18 extraction methods
     * @param facilityCatalog authoritative Stage-18 facility definitions
     * @return immutable deterministic Stage-20E result
     */
    public static Stage20ResourceOccurrenceWorld generate(
            long rootSeed,
            GalaxyTopology topology,
            List<Stage20LocalInfrastructureLayout> localLayouts,
            List<ResourceHostProfile> hosts,
            Stage18ResourceOntologyCatalog ontology,
            Stage18ExtractionCatalog extractionCatalog,
            Stage18FacilityCatalog facilityCatalog) {
        GalaxyTopology checkedTopology = Objects.requireNonNull(topology, "topology");
        Stage18ResourceOntologyCatalog checkedOntology = Objects.requireNonNull(ontology, "ontology");
        Stage18ExtractionCatalog checkedExtraction = Objects.requireNonNull(extractionCatalog, "extractionCatalog");
        Stage18FacilityCatalog checkedFacilities = Objects.requireNonNull(facilityCatalog, "facilityCatalog");
        Objects.requireNonNull(localLayouts, "localLayouts");
        Objects.requireNonNull(hosts, "hosts");

        validateProfileCoverage(checkedOntology);
        Map<StarSystemId, Stage20LocalInfrastructureLayout> layoutsBySystem = canonicalLayouts(
                rootSeed, checkedTopology, localLayouts);
        List<ResourceHostProfile> orderedHosts = canonicalHosts(
                checkedTopology, layoutsBySystem, hosts, checkedOntology);

        Map<StarSystemId, Map<String, Double>> correlatedConditions = generateCorrelatedConditions(
                rootSeed, checkedTopology, checkedOntology);
        List<SystemResourceConditions> conditionRows = correlatedConditions.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new SystemResourceConditions(entry.getKey(), entry.getValue()))
                .toList();

        SimulationRandom rootRandom = new SimulationRandom(rootSeed);
        ArrayList<ResourceOccurrence> occurrences = new ArrayList<>();
        ArrayList<InitialExtractionSite> sites = new ArrayList<>();
        for (ResourceHostProfile host : orderedHosts) {
            InfrastructurePlacement placement = layoutsBySystem.get(host.systemId()).placement(host.anchorId());
            Map<String, Double> systemPotentials = correlatedConditions.get(host.systemId());
            for (ResourceOccurrenceTypeDefinition occurrenceType : checkedOntology.getOccurrenceTypes()) {
                double affinity = host.occurrenceAffinityByTypeId().getOrDefault(occurrenceType.id(), 0d);
                if (affinity <= 0d) {
                    continue;
                }
                List<ExtractionMethodDefinition> methods = compatibleMethods(
                        checkedExtraction, occurrenceType.id(), host.environment());
                if (methods.isEmpty()) {
                    continue;
                }
                OccurrenceBand band = Stage20ResourceGenerationProfile.requireBand(occurrenceType.id());
                StatefulRandom presenceRandom = rootRandom.createStream(streamName(
                        host, occurrenceType.id(), "presence"));
                double localVariance = interpolate(
                        Stage20ResourceGenerationProfile.LOCAL_VARIANCE_MIN,
                        Stage20ResourceGenerationProfile.LOCAL_VARIANCE_MAX,
                        presenceRandom.nextDouble());
                double generationScore = clamp01(systemPotentials.get(occurrenceType.id()) * affinity * localVariance);
                if (generationScore < band.presenceThreshold()) {
                    continue;
                }
                double richness = normalizedRichness(generationScore, band.presenceThreshold());
                for (String feedstockId : occurrenceType.feedstockCommodityIds().stream().sorted().toList()) {
                    String sourceId = sourceId(host, occurrenceType.id(), feedstockId);
                    StatefulRandom propertyRandom = rootRandom.createStream(streamName(
                            host, occurrenceType.id() + ".feedstock." + feedstockId, "properties"));
                    double gradeControl = clamp01(0.55d * propertyRandom.nextDouble() + 0.45d * richness);
                    double reserveControl = clamp01(0.50d * propertyRandom.nextDouble() + 0.50d * richness);
                    double recoveryControl = clamp01(0.65d * propertyRandom.nextDouble() + 0.35d * richness);
                    double grade = interpolate(band.minGradeFraction(), band.maxGradeFraction(), gradeControl);
                    double reserveKg = logInterpolate(
                            band.minAccessibleMassKg(), band.maxAccessibleMassKg(), reserveControl);
                    double recovery = interpolate(
                            band.minSourceRecoveryFraction(), band.maxSourceRecoveryFraction(), recoveryControl);
                    ResourceOccurrence occurrence = new ResourceOccurrence(
                            sourceId,
                            host.systemId(),
                            host.anchorId(),
                            host.hostClassId(),
                            placement.position(),
                            occurrenceType.id(),
                            host.environment(),
                            feedstockId,
                            generationScore,
                            reserveKg,
                            grade,
                            recovery,
                            host.sourceRequiredCapabilityTags());
                    occurrences.add(occurrence);

                    if (generationScore >= Math.min(
                            1d, band.presenceThreshold() + Stage20ResourceGenerationProfile.INITIAL_SITE_SCORE_MARGIN)) {
                        SiteChoice siteChoice = chooseInitialSite(
                                host, methods, checkedFacilities,
                                checkedOntology.findCommodity(feedstockId).storageClassId());
                        if (siteChoice != null) {
                            sites.add(new InitialExtractionSite(
                                    "site." + sourceId,
                                    sourceId,
                                    host.systemId(),
                                    host.anchorId(),
                                    host.locationTag(),
                                    siteChoice.facility().id(),
                                    siteChoice.method().id()));
                        }
                    }
                }
            }
        }

        return new Stage20ResourceOccurrenceWorld(
                Stage20ResourceOccurrenceWorld.CURRENT_VERSION,
                rootSeed,
                conditionRows,
                occurrences,
                sites,
                checkedOntology.getFingerprint(),
                checkedExtraction.getFingerprint(),
                checkedFacilities.getFingerprint(),
                Stage20ResourceGenerationProfile.CURRENT_VERSION);
    }

    private static Map<StarSystemId, Stage20LocalInfrastructureLayout> canonicalLayouts(
            long rootSeed,
            GalaxyTopology topology,
            List<Stage20LocalInfrastructureLayout> localLayouts) {
        TreeMap<StarSystemId, Stage20LocalInfrastructureLayout> result = new TreeMap<>();
        for (Stage20LocalInfrastructureLayout layout : localLayouts) {
            Stage20LocalInfrastructureLayout checked = Objects.requireNonNull(layout, "local layout");
            if (checked.rootSeed() != rootSeed) {
                throw new IllegalArgumentException("Stage-20C layout root seed differs from Stage-20E root seed");
            }
            if (topology.findSystem(checked.systemId()).isEmpty()) {
                throw new IllegalArgumentException("Stage-20C layout references system outside topology: " + checked.systemId());
            }
            if (result.putIfAbsent(checked.systemId(), checked) != null) {
                throw new IllegalArgumentException("duplicate Stage-20C layout for system " + checked.systemId());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static List<ResourceHostProfile> canonicalHosts(
            GalaxyTopology topology,
            Map<StarSystemId, Stage20LocalInfrastructureLayout> layoutsBySystem,
            List<ResourceHostProfile> hosts,
            Stage18ResourceOntologyCatalog ontology) {
        ArrayList<ResourceHostProfile> ordered = new ArrayList<>();
        HashSet<String> keys = new HashSet<>();
        for (ResourceHostProfile host : hosts) {
            ResourceHostProfile checked = Objects.requireNonNull(host, "resource host");
            if (topology.findSystem(checked.systemId()).isEmpty()) {
                throw new IllegalArgumentException("resource host references system outside topology: " + checked.systemId());
            }
            Stage20LocalInfrastructureLayout layout = layoutsBySystem.get(checked.systemId());
            if (layout == null) {
                throw new IllegalArgumentException("resource host has no Stage-20C layout: " + checked.systemId());
            }
            InfrastructurePlacement placement = layout.placement(checked.anchorId());
            if (placement.kind() != PlacementKind.RESOURCE_FIELD_ANCHOR) {
                throw new IllegalArgumentException("resource host must reference RESOURCE_FIELD_ANCHOR: " + checked.anchorId());
            }
            for (String occurrenceTypeId : checked.occurrenceAffinityByTypeId().keySet()) {
                if (ontology.findOccurrenceType(occurrenceTypeId) == null) {
                    throw new IllegalArgumentException("host affinity references unknown Stage-18 occurrence: " + occurrenceTypeId);
                }
            }
            for (String capability : checked.sourceRequiredCapabilityTags()) {
                if (ontology.findCapabilityTag(capability) == null) {
                    throw new IllegalArgumentException("resource host requires unknown Stage-18 capability: " + capability);
                }
            }
            String key = checked.systemId().value() + "\u0000" + checked.anchorId();
            if (!keys.add(key)) {
                throw new IllegalArgumentException("duplicate resource host: " + key);
            }
            ordered.add(checked);
        }
        ordered.sort(Comparator.comparing(ResourceHostProfile::systemId)
                .thenComparing(ResourceHostProfile::anchorId));
        return List.copyOf(ordered);
    }

    private static Map<StarSystemId, Map<String, Double>> generateCorrelatedConditions(
            long rootSeed,
            GalaxyTopology topology,
            Stage18ResourceOntologyCatalog ontology) {
        SimulationRandom random = new SimulationRandom(rootSeed);
        TreeMap<StarSystemId, Map<String, Double>> current = new TreeMap<>();
        List<ResourceOccurrenceTypeDefinition> occurrenceTypes = ontology.getOccurrenceTypes().stream()
                .sorted(Comparator.comparing(ResourceOccurrenceTypeDefinition::id))
                .toList();
        for (StarSystemNode system : topology.systems()) {
            TreeMap<String, Double> potentials = new TreeMap<>();
            for (ResourceOccurrenceTypeDefinition type : occurrenceTypes) {
                StatefulRandom stream = random.createStream(
                        RNG_PREFIX + ".latent.system." + system.id().value() + "." + type.id());
                potentials.put(type.id(), stream.nextDouble());
            }
            current.put(system.id(), Collections.unmodifiableMap(potentials));
        }

        for (int pass = 0; pass < Stage20ResourceGenerationProfile.CORRELATION_PASSES; pass++) {
            TreeMap<StarSystemId, Map<String, Double>> next = new TreeMap<>();
            for (StarSystemNode system : topology.systems()) {
                List<StarSystemId> neighbors = topology.neighbors(system.id());
                TreeMap<String, Double> smoothed = new TreeMap<>();
                for (ResourceOccurrenceTypeDefinition type : occurrenceTypes) {
                    double own = current.get(system.id()).get(type.id());
                    if (neighbors.isEmpty()) {
                        smoothed.put(type.id(), own);
                        continue;
                    }
                    double neighborMean = 0d;
                    for (StarSystemId neighbor : neighbors) {
                        neighborMean += current.get(neighbor).get(type.id());
                    }
                    neighborMean /= neighbors.size();
                    double value = Stage20ResourceGenerationProfile.LOCAL_CONDITION_WEIGHT * own
                            + (1d - Stage20ResourceGenerationProfile.LOCAL_CONDITION_WEIGHT) * neighborMean;
                    // Increase comparative contrast without changing ordering or creating runtime bonuses.
                    value = clamp01(0.5d + (value - 0.5d) * 1.18d);
                    smoothed.put(type.id(), value);
                }
                next.put(system.id(), Collections.unmodifiableMap(smoothed));
            }
            current = next;
        }
        return Collections.unmodifiableMap(current);
    }

    private static List<ExtractionMethodDefinition> compatibleMethods(
            Stage18ExtractionCatalog catalog,
            String occurrenceTypeId,
            ExtractionEnvironment environment) {
        return catalog.getMethods().stream()
                .filter(method -> method.sourceKind() == SourceKind.NATURAL_OCCURRENCE)
                .filter(method -> method.environment() == environment)
                .filter(method -> method.compatibleOccurrenceTypeIds().contains(occurrenceTypeId))
                .sorted(Comparator.comparing(ExtractionMethodDefinition::id))
                .toList();
    }

    private static SiteChoice chooseInitialSite(
            ResourceHostProfile host,
            List<ExtractionMethodDefinition> methods,
            Stage18FacilityCatalog facilities,
            String outputStorageClassId) {
        ArrayList<SiteChoice> choices = new ArrayList<>();
        for (ExtractionMethodDefinition method : methods) {
            for (FacilityDefinition facility : facilities.getFacilities()) {
                if (!facility.allowedLocationTags().contains(host.locationTag())) {
                    continue;
                }
                if (!facility.capabilityTags().containsAll(method.requiredCapabilityTags())) {
                    continue;
                }
                if (!facility.storageClassInterfaces().contains(outputStorageClassId)) {
                    continue;
                }
                if (!facility.capabilityTags().containsAll(host.sourceRequiredCapabilityTags())) {
                    continue;
                }
                choices.add(new SiteChoice(method, facility));
            }
        }
        choices.sort(Comparator.comparing((SiteChoice choice) -> choice.method().id())
                .thenComparing(choice -> choice.facility().id()));
        return choices.isEmpty() ? null : choices.get(0);
    }

    private static void validateProfileCoverage(Stage18ResourceOntologyCatalog ontology) {
        Set<String> profileIds = Stage20ResourceGenerationProfile.occurrenceBands().keySet();
        TreeSet<String> ontologyIds = new TreeSet<>();
        for (ResourceOccurrenceTypeDefinition type : ontology.getOccurrenceTypes()) {
            ontologyIds.add(type.id());
        }
        if (!profileIds.equals(ontologyIds)) {
            TreeSet<String> missing = new TreeSet<>(ontologyIds);
            missing.removeAll(profileIds);
            TreeSet<String> extra = new TreeSet<>(profileIds);
            extra.removeAll(ontologyIds);
            throw new IllegalStateException(
                    "Stage-20E calibration must exactly cover Stage-18 occurrences; missing=" + missing + ", extra=" + extra);
        }
    }

    private static String sourceId(
            ResourceHostProfile host,
            String occurrenceTypeId,
            String feedstockId) {
        return "source.stage20e.s" + host.systemId().value()
                + "." + stableToken(host.anchorId())
                + "." + stableToken(occurrenceTypeId)
                + "." + stableToken(feedstockId);
    }

    private static String streamName(ResourceHostProfile host, String suffix, String purpose) {
        return RNG_PREFIX
                + ".system." + host.systemId().value()
                + ".host." + host.anchorId()
                + "." + suffix
                + "." + purpose;
    }

    private static String stableToken(String value) {
        return requireText(value, "stable token source")
                .replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static double normalizedRichness(double score, double threshold) {
        if (threshold >= 1d) {
            return score >= 1d ? 1d : 0d;
        }
        return clamp01((score - threshold) / (1d - threshold));
    }

    private static double logInterpolate(double min, double max, double t) {
        if (min == max) {
            return min;
        }
        return Math.exp(Math.log(min) + (Math.log(max) - Math.log(min)) * clamp01(t));
    }

    private static double interpolate(double min, double max, double t) {
        return Math.fma(max - min, clamp01(t), min);
    }

    private static double clamp01(double value) {
        return Math.max(0d, Math.min(1d, value));
    }

    private static Set<String> immutableTags(Set<String> source) {
        Objects.requireNonNull(source, "sourceRequiredCapabilityTags");
        TreeSet<String> copy = new TreeSet<>();
        for (String tag : source) {
            copy.add(requireText(tag, "source capability tag"));
        }
        return Collections.unmodifiableSet(copy);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }

    private record SiteChoice(ExtractionMethodDefinition method, FacilityDefinition facility) {
    }
}
