package com.spacesim.world.generation;

import com.spacesim.world.LocalPhysicalPosition;

import java.util.List;
import java.util.Objects;

/**
 * Immutable Stage-20B physical geometry for one generated star system.
 *
 * <p>All authoritative positions use {@link LocalPhysicalPosition} and every linear value is SI
 * meters. {@link ContentExtents} are descriptive statistics for content distribution and LOD; they
 * are intentionally not a movement, deletion, teleport or transition boundary.</p>
 */
public record SystemSpec(
        GeneratorVersion generatorVersion,
        long seed,
        String systemId,
        CentralBody centralBody,
        List<CelestialAnchor> celestialAnchors,
        List<OperationalRegion> operationalRegions,
        List<LocalSite> sites,
        ContentExtents contentExtents,
        ValidationParameters validationParameters) {

    /** Validates and defensively snapshots all generated collections. */
    public SystemSpec {
        Objects.requireNonNull(generatorVersion, "generatorVersion");
        systemId = requireText(systemId, "systemId");
        Objects.requireNonNull(centralBody, "centralBody");
        celestialAnchors = List.copyOf(Objects.requireNonNull(celestialAnchors, "celestialAnchors"));
        operationalRegions = List.copyOf(Objects.requireNonNull(operationalRegions, "operationalRegions"));
        sites = List.copyOf(Objects.requireNonNull(sites, "sites"));
        Objects.requireNonNull(contentExtents, "contentExtents");
        Objects.requireNonNull(validationParameters, "validationParameters");
    }

    /** Returns generated sites of the requested physical/content kind in deterministic source order. */
    public List<LocalSite> sitesOfKind(SiteKind kind) {
        Objects.requireNonNull(kind, "kind");
        return sites.stream().filter(site -> site.kind() == kind).toList();
    }

    /** Central stellar/body reference used as local-system origin anchor. */
    public record CentralBody(String id, LocalPhysicalPosition position, double physicalRadiusM, double massKg) {
        /** Validates physical central-body data. */
        public CentralBody {
            id = requireText(id, "centralBody.id");
            Objects.requireNonNull(position, "centralBody.position");
            requirePositiveFinite(physicalRadiusM, "centralBody.physicalRadiusM");
            requirePositiveFinite(massKg, "centralBody.massKg");
        }
    }

    /** Planet or moon with explicit parent and physical orbital radius. */
    public record CelestialAnchor(
            String id,
            CelestialKind kind,
            String parentId,
            LocalPhysicalPosition position,
            double physicalRadiusM,
            double orbitalRadiusM) {
        /** Validates one celestial anchor. */
        public CelestialAnchor {
            id = requireText(id, "celestial.id");
            Objects.requireNonNull(kind, "celestial.kind");
            parentId = requireText(parentId, "celestial.parentId");
            Objects.requireNonNull(position, "celestial.position");
            requirePositiveFinite(physicalRadiusM, "celestial.physicalRadiusM");
            requirePositiveFinite(orbitalRadiusM, "celestial.orbitalRadiusM");
        }
    }

    /** Descriptive local activity/traffic region; never a world boundary. */
    public record OperationalRegion(
            String id,
            RegionKind kind,
            LocalPhysicalPosition center,
            double innerRadiusM,
            double outerRadiusM,
            double activityWeight) {
        /** Validates one generated operational region. */
        public OperationalRegion {
            id = requireText(id, "region.id");
            Objects.requireNonNull(kind, "region.kind");
            Objects.requireNonNull(center, "region.center");
            requireNonNegativeFinite(innerRadiusM, "region.innerRadiusM");
            requirePositiveFinite(outerRadiusM, "region.outerRadiusM");
            if (outerRadiusM <= innerRadiusM) {
                throw new IllegalArgumentException("region.outerRadiusM must exceed innerRadiusM");
            }
            if (!Double.isFinite(activityWeight) || activityWeight < 0d || activityWeight > 1d) {
                throw new IllegalArgumentException("region.activityWeight must be finite in [0, 1]");
            }
        }
    }

    /** Physical location of generated infrastructure, resource, transition or special content. */
    public record LocalSite(String id, SiteKind kind, LocalPhysicalPosition position, double footprintRadiusM) {
        /** Validates one local site. */
        public LocalSite {
            id = requireText(id, "site.id");
            Objects.requireNonNull(kind, "site.kind");
            Objects.requireNonNull(position, "site.position");
            requireNonNegativeFinite(footprintRadiusM, "site.footprintRadiusM");
        }
    }

    /** Descriptive extent metrics used by maps, LOD and later Stage-20 calibration. */
    public record ContentExtents(
            double coreActivityRadiusPercentileM,
            double majorInfrastructureExtentM,
            double resourceFieldExtentM,
            double jumpArrivalExtentM,
            double surveyedContentExtentM,
            double expectedTrafficExtentM) {
        /** Validates descriptive extent metrics. */
        public ContentExtents {
            requireNonNegativeFinite(coreActivityRadiusPercentileM, "coreActivityRadiusPercentileM");
            requireNonNegativeFinite(majorInfrastructureExtentM, "majorInfrastructureExtentM");
            requireNonNegativeFinite(resourceFieldExtentM, "resourceFieldExtentM");
            requireNonNegativeFinite(jumpArrivalExtentM, "jumpArrivalExtentM");
            requireNonNegativeFinite(surveyedContentExtentM, "surveyedContentExtentM");
            requireNonNegativeFinite(expectedTrafficExtentM, "expectedTrafficExtentM");
        }
    }

    /** Versioned physical placement constraints consumed by validation and later calibration. */
    public record ValidationParameters(
            double minimumSiteClearanceM,
            double jumpArrivalStationStandOffM,
            double celestialClearanceM) {
        /** Validates physical generation constraints. */
        public ValidationParameters {
            requireNonNegativeFinite(minimumSiteClearanceM, "minimumSiteClearanceM");
            requireNonNegativeFinite(jumpArrivalStationStandOffM, "jumpArrivalStationStandOffM");
            requireNonNegativeFinite(celestialClearanceM, "celestialClearanceM");
        }
    }

    /** Supported Stage-20B celestial anchors. */
    public enum CelestialKind { PLANET, MOON }

    /** Generated operational-region meaning. */
    public enum RegionKind { CORE_ACTIVITY, RESOURCE_BELT, PATROL_SECURITY, EMPTY_TRANSIT }

    /** Generated local physical/content site meaning. */
    public enum SiteKind { RESOURCE_FIELD, STATION, JUMP_ZONE, DERELICT, ANOMALY }

    private static String requireText(String value, String field) {
        String checked = Objects.requireNonNull(value, field).trim();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return checked;
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
