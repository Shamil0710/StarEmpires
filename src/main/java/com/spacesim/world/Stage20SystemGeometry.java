package com.spacesim.world;

import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalog.BandId;

import java.util.Objects;

/**
 * Versioned Stage-20B descriptive physical geometry for one local star system.
 *
 * <p>The operational envelope describes where generated content is normally concentrated. It is
 * explicitly not a world edge, movement wall, render boundary or validity limit for
 * {@link LocalPhysicalPosition}. Authoritative local physical space remains unbounded by this model.</p>
 *
 * @param version stable Stage-20B geometry version
 * @param systemId stable star-system identity
 * @param rootSeed root world-generation seed used for deterministic derivation
 * @param centralReference authoritative local physical reference for the central stellar body
 * @param operationalEnvelope descriptive default content-distribution envelope
 * @param majorInfrastructureExtentM largest accepted Stage-20A major-infrastructure extent
 * @param sourceRouteBand accepted route band used to derive the envelope scale
 * @param provenance exact versioned calibration provenance
 */
public record Stage20SystemGeometry(
        String version,
        StarSystemId systemId,
        long rootSeed,
        LocalPhysicalPosition centralReference,
        OperationalEnvelope operationalEnvelope,
        double majorInfrastructureExtentM,
        BandId sourceRouteBand,
        String provenance) {

    /** Current Stage-20B system-geometry model version. */
    public static final String CURRENT_VERSION = "stage20b.system-geometry.v1";

    /**
     * Validates one immutable generated geometry snapshot.
     *
     * @param version stable Stage-20B geometry version
     * @param systemId stable star-system identity
     * @param rootSeed root world-generation seed used for deterministic derivation
     * @param centralReference authoritative local physical reference for the central stellar body
     * @param operationalEnvelope descriptive default content-distribution envelope
     * @param majorInfrastructureExtentM largest accepted Stage-20A major-infrastructure extent
     * @param sourceRouteBand accepted route band used to derive the envelope scale
     * @param provenance exact versioned calibration provenance
     */
    public Stage20SystemGeometry {
        requireText(version, "version");
        Objects.requireNonNull(systemId, "systemId");
        Objects.requireNonNull(centralReference, "centralReference");
        Objects.requireNonNull(operationalEnvelope, "operationalEnvelope");
        requirePositiveFinite(majorInfrastructureExtentM, "majorInfrastructureExtentM");
        Objects.requireNonNull(sourceRouteBand, "sourceRouteBand");
        requireText(provenance, "provenance");
        if (operationalEnvelope.radiusM() < majorInfrastructureExtentM) {
            throw new IllegalArgumentException(
                    "operational envelope cannot be smaller than accepted major infrastructure extent");
        }
    }

    /**
     * Descriptive generated-content envelope around the central physical reference.
     *
     * <p>Both boundary flags are intentionally represented and validated so callers cannot silently
     * reinterpret a Stage-20B content-distribution radius as a world boundary or clamp.</p>
     *
     * @param radiusM default generated-content extent in SI meters
     * @param hardBoundary whether this radius is a physical movement/world boundary; must be false
     * @param clampAllowed whether actors may be silently clamped to this radius; must be false
     */
    public record OperationalEnvelope(double radiusM, boolean hardBoundary, boolean clampAllowed) {
        /**
         * Validates non-boundary envelope semantics.
         *
         * @param radiusM default generated-content extent in SI meters
         * @param hardBoundary must remain false
         * @param clampAllowed must remain false
         */
        public OperationalEnvelope {
            requirePositiveFinite(radiusM, "radiusM");
            if (hardBoundary || clampAllowed) {
                throw new IllegalArgumentException(
                        "operational envelope is descriptive and cannot be a hard boundary or clamp");
            }
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void requirePositiveFinite(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }
}
