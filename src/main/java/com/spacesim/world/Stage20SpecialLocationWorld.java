package com.spacesim.world;

import com.spacesim.economy.Stage18ExtractionRuntime.PhysicalSourceState;
import com.spacesim.economy.Stage18SalvageRuntime.SalvageStream;
import com.spacesim.ship.SignatureState;
import com.spacesim.world.Stage20LocalInfrastructureLayout.PlacementKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable Stage-20H special-location layer in ordinary local-system SI coordinates.
 *
 * <p>Locations never create a disconnected pocket map. Derelict value is represented by finite
 * Stage-18 salvage streams, and anomalous resource value references an existing finite Stage-20E
 * occurrence rather than creating hidden reserve. Detection signature and survey requirements are
 * world truth; they are not automatically copied into any actor's Stage-20G knowledge.</p>
 *
 * @param version stable special-location world version
 * @param rootSeed exact generated-world root seed
 * @param resolvedProbeVersion exact accepted generated-world authority version
 * @param generationProfileVersion special-location generation profile version
 * @param shipyardFingerprint exact Stage-18 shipyard BOM fingerprint
 * @param locations deterministic special locations
 */
public record Stage20SpecialLocationWorld(
        String version,
        long rootSeed,
        String resolvedProbeVersion,
        String generationProfileVersion,
        String shipyardFingerprint,
        List<SpecialLocation> locations) {
    /** Current immutable Stage-20H world version. */
    public static final String CURRENT_VERSION = "stage20h.special-locations.v1";

    /** Special-location physical/content role. */
    public enum LocationKind {
        /** Energetic or otherwise non-resource physical phenomenon. */ ANOMALY,
        /** Abandoned constructed hull with finite recyclable remains. */ DERELICT,
        /** Special phenomenon bound to an existing finite natural occurrence. */ RESOURCE_PHENOMENON
    }

    /** Authored occurrence-frequency class; it is not a discovery probability. */
    public enum Rarity {
        /** Sparse but expected in a representative generated galaxy. */ UNCOMMON,
        /** Lower-frequency optional content. */ RARE,
        /** Lowest-frequency ordinary generator content. */ VERY_RARE
    }

    /** Minimum physical observation method required to identify a location. */
    public enum ScanRequirement {
        /** Passive evidence is sufficient to classify the phenomenon. */ PASSIVE_CLASSIFICATION,
        /** An active scan is required for classification. */ ACTIVE_CLASSIFICATION,
        /** Only a physical visit/survey establishes the final classification. */ PHYSICAL_SURVEY
    }

    /** Coarse authored local-hazard band with explicit physical tags. */
    public enum HazardBand {
        /** No material local hazard is authored. */ NONE,
        /** Limited hazard that still requires operational awareness. */ LOW,
        /** Material hazard that affects approach planning. */ MODERATE,
        /** Severe local hazard requiring deliberate preparation. */ HIGH
    }

    /** Security knowledge at generation time; no hidden security score is invented. */
    public enum SecurityAssessment {
        /** Security conditions have not been physically observed. */ UNASSESSED
    }

    /** Coordinate domain for every Stage-20H location. */
    public enum CoordinateDomain {
        /** Ordinary unbounded local-system SI coordinates. */ LOCAL_SYSTEM_SI
    }

    /**
     * One physical special location and its finite-value/proximity evidence.
     *
     * @param locationId stable generated location identity
     * @param systemId owning ordinary star system
     * @param coordinateDomain fixed ordinary local-system coordinate domain
     * @param position authoritative hierarchical SI position
     * @param archetypeId stable authored archetype identity
     * @param kind physical/content role
     * @param rarity authored generation frequency class
     * @param signature channelized Stage-17.5 physical detection signature
     * @param scanRequirement minimum method required for classification
     * @param hazardBand local hazard severity
     * @param hazardTags explicit deterministic physical hazard tags
     * @param nearestTrafficAnchorId nearest generated station/jump traffic anchor
     * @param nearestTrafficAnchorKind kind of that anchor
     * @param nearestTrafficDistanceM physical SI separation from the anchor
     * @param miningShipApproachTimeS physically derived routine mining-ship travel time
     * @param securityAssessment explicit generation-time security knowledge
     * @param linkedResourceSourceId existing Stage-20E occurrence for resource phenomena
     * @param salvageStreams finite Stage-18 salvage streams for derelicts
     * @param finiteRecoverableValueKg finite physical recoverable mass; zero for valueless anomalies
     * @param signatureProvenanceId accepted sensor/signature authority provenance
     */
    public record SpecialLocation(
            String locationId,
            StarSystemId systemId,
            CoordinateDomain coordinateDomain,
            LocalPhysicalPosition position,
            String archetypeId,
            LocationKind kind,
            Rarity rarity,
            SignatureState signature,
            ScanRequirement scanRequirement,
            HazardBand hazardBand,
            List<String> hazardTags,
            String nearestTrafficAnchorId,
            PlacementKind nearestTrafficAnchorKind,
            double nearestTrafficDistanceM,
            double miningShipApproachTimeS,
            SecurityAssessment securityAssessment,
            Optional<String> linkedResourceSourceId,
            List<SalvageStream> salvageStreams,
            double finiteRecoverableValueKg,
            String signatureProvenanceId) implements Comparable<SpecialLocation> {
        /**
         * Validates and freezes one special location without creating discovery knowledge.
         *
         * @param locationId stable generated location identity
         * @param systemId owning ordinary star system
         * @param coordinateDomain ordinary local-system coordinate domain
         * @param position authoritative hierarchical SI position
         * @param archetypeId stable authored archetype identity
         * @param kind physical/content role
         * @param rarity authored generation frequency class
         * @param signature channelized physical signature
         * @param scanRequirement minimum classification method
         * @param hazardBand local hazard severity
         * @param hazardTags explicit physical hazard tags
         * @param nearestTrafficAnchorId nearest station/jump traffic anchor
         * @param nearestTrafficAnchorKind nearest anchor kind
         * @param nearestTrafficDistanceM physical anchor separation
         * @param miningShipApproachTimeS derived routine approach time
         * @param securityAssessment explicit generation-time security knowledge
         * @param linkedResourceSourceId existing natural occurrence when applicable
         * @param salvageStreams finite derelict salvage streams
         * @param finiteRecoverableValueKg finite recoverable physical mass
         * @param signatureProvenanceId accepted signature authority provenance
         */
        public SpecialLocation {
            locationId = requireText(locationId, "locationId");
            Objects.requireNonNull(systemId, "systemId");
            if (coordinateDomain != CoordinateDomain.LOCAL_SYSTEM_SI) {
                throw new IllegalArgumentException("special locations must use local-system SI coordinates");
            }
            Objects.requireNonNull(position, "position");
            archetypeId = requireText(archetypeId, "archetypeId");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(rarity, "rarity");
            Objects.requireNonNull(signature, "signature");
            if (!hasPhysicalSignature(signature)) {
                throw new IllegalArgumentException("special location requires a non-zero physical signature");
            }
            Objects.requireNonNull(scanRequirement, "scanRequirement");
            Objects.requireNonNull(hazardBand, "hazardBand");
            hazardTags = canonicalTextList(hazardTags, "hazardTags");
            if ((hazardBand == HazardBand.NONE) != hazardTags.isEmpty()) {
                throw new IllegalArgumentException("hazard tags must exist exactly for a non-NONE hazard");
            }
            nearestTrafficAnchorId = requireText(nearestTrafficAnchorId, "nearestTrafficAnchorId");
            Objects.requireNonNull(nearestTrafficAnchorKind, "nearestTrafficAnchorKind");
            if (nearestTrafficAnchorKind == PlacementKind.RESOURCE_FIELD_ANCHOR) {
                throw new IllegalArgumentException("resource anchors are not traffic/security anchors");
            }
            requirePositiveFinite(nearestTrafficDistanceM, "nearestTrafficDistanceM");
            requirePositiveFinite(miningShipApproachTimeS, "miningShipApproachTimeS");
            Objects.requireNonNull(securityAssessment, "securityAssessment");
            Objects.requireNonNull(linkedResourceSourceId, "linkedResourceSourceId");
            linkedResourceSourceId = linkedResourceSourceId.map(value -> requireText(value, "linkedResourceSourceId"));
            ArrayList<SalvageStream> streams = new ArrayList<>(
                    Objects.requireNonNull(salvageStreams, "salvageStreams"));
            if (streams.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("salvageStreams cannot contain null");
            }
            streams.sort(Comparator.comparing(SalvageStream::streamId));
            Set<String> streamIds = new HashSet<>();
            double accessibleSalvage = 0d;
            for (SalvageStream stream : streams) {
                if (!streamIds.add(stream.streamId())) {
                    throw new IllegalArgumentException("duplicate special-location salvage stream");
                }
                accessibleSalvage += stream.accessibleMassKg();
            }
            salvageStreams = List.copyOf(streams);
            requireNonNegativeFinite(finiteRecoverableValueKg, "finiteRecoverableValueKg");
            signatureProvenanceId = requireText(signatureProvenanceId, "signatureProvenanceId");

            if (kind == LocationKind.DERELICT) {
                if (linkedResourceSourceId.isPresent() || salvageStreams.isEmpty()) {
                    throw new IllegalArgumentException("derelict requires salvage and cannot link a natural occurrence");
                }
                if (Math.abs(accessibleSalvage - finiteRecoverableValueKg) > 1e-6d) {
                    throw new IllegalArgumentException("derelict recoverable value must equal accessible salvage mass");
                }
            } else if (kind == LocationKind.RESOURCE_PHENOMENON) {
                if (linkedResourceSourceId.isEmpty() || !salvageStreams.isEmpty()
                        || finiteRecoverableValueKg <= 0d) {
                    throw new IllegalArgumentException(
                            "resource phenomenon requires one existing finite occurrence and no salvage");
                }
            } else if (linkedResourceSourceId.isPresent()
                    || !salvageStreams.isEmpty()
                    || finiteRecoverableValueKg != 0d) {
                throw new IllegalArgumentException("ordinary anomaly cannot invent resource or salvage value");
            }
        }

        /**
         * Converts every accessible derelict stream into ordinary Stage-18 salvage sources.
         *
         * @return deterministic finite physical sources; empty for non-derelicts
         */
        public List<PhysicalSourceState> salvageSources() {
            return salvageStreams.stream()
                    .map(SalvageStream::toPhysicalSource)
                    .filter(Objects::nonNull)
                    .toList();
        }

        @Override
        public int compareTo(SpecialLocation other) {
            SpecialLocation checked = Objects.requireNonNull(other, "other");
            int system = systemId.compareTo(checked.systemId);
            return system != 0 ? system : locationId.compareTo(checked.locationId);
        }
    }

    /**
     * Validates and deterministically freezes one generated special-location world.
     *
     * @param version stable special-location world version
     * @param rootSeed exact generated-world root seed
     * @param resolvedProbeVersion exact accepted generated-world authority version
     * @param generationProfileVersion special-location profile version
     * @param shipyardFingerprint exact Stage-18 shipyard fingerprint
     * @param locations deterministic special locations
     */
    public Stage20SpecialLocationWorld {
        version = requireText(version, "version");
        resolvedProbeVersion = requireText(resolvedProbeVersion, "resolvedProbeVersion");
        generationProfileVersion = requireText(generationProfileVersion, "generationProfileVersion");
        shipyardFingerprint = requireText(shipyardFingerprint, "shipyardFingerprint");
        ArrayList<SpecialLocation> copy = new ArrayList<>(Objects.requireNonNull(locations, "locations"));
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("locations cannot contain null");
        }
        copy.sort(null);
        Set<String> ids = new HashSet<>();
        for (SpecialLocation location : copy) {
            if (!ids.add(location.locationId())) {
                throw new IllegalArgumentException("duplicate special location ID: " + location.locationId());
            }
        }
        locations = List.copyOf(copy);
    }

    /**
     * Finds one stable special location.
     *
     * @param locationId stable location identity
     * @return matching location when generated
     */
    public Optional<SpecialLocation> location(String locationId) {
        String checked = requireText(locationId, "locationId");
        return locations.stream().filter(value -> value.locationId().equals(checked)).findFirst();
    }

    private static List<String> canonicalTextList(List<String> values, String field) {
        ArrayList<String> copy = new ArrayList<>(Objects.requireNonNull(values, field));
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " cannot contain null");
        }
        copy.replaceAll(value -> requireText(value, field + " entry"));
        copy.sort(String::compareTo);
        if (new HashSet<>(copy).size() != copy.size()) {
            throw new IllegalArgumentException(field + " cannot contain duplicates");
        }
        return List.copyOf(copy);
    }

    private static boolean hasPhysicalSignature(SignatureState value) {
        return value.thermalRadiantPowerW() > 0d
                || value.enginePlumeRadiantPowerW() > 0d
                || value.radarCrossSectionM2() > 0d
                || value.reflectedOpticalPowerW() > 0d
                || value.activeRadioEmissionPowerW() > 0d
                || value.jammerEmissionPowerW() > 0d;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
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
}
