package com.spacesim.world.calibration;

import com.spacesim.content.Stage18StationInfrastructureCatalog.StationArchetypeDefinition;
import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.world.calibration.Stage20IntersystemCadenceCalibrationProfile.HopCadenceSample;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalog.BandId;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticCalibrationProfile.RepresentativeGroup;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticCalibrationProfile.SemanticRouteSample;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticCalibrationProfile.ThrustPolicy;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.ReferenceDefinition;

import java.util.List;
import java.util.Objects;

/**
 * Stage-20E ordinary-start supplier-service time authority derived from accepted Stage-20A physical
 * cadence rather than inventory depletion time.
 *
 * <p>The Stage-20 world-generation contract treats haul time, ship throughput and inventory buffer
 * as separate causal quantities. A station's storage divided by sustained demand is therefore a
 * buffer-coverage diagnostic, not by itself the maximum physically acceptable supplier trip. This
 * profile derives the v2 supplier service envelope from the already accepted regional 5-hop cadence,
 * the same local semantic route envelopes consumed by Stage-20C, the representative early civilian
 * freighter payload and the representative major-hub handling rate.</p>
 *
 * <p>The budget matches the current production-probe endpoint abstraction deliberately: one source
 * non-jump local leg, one jump-access leg at each endpoint, a five-edge regional FTL path and one
 * load/unload operation at each end. It is a provisional Stage-20/22 generation-quality envelope,
 * not a hidden speed multiplier, a world radius or an inventory grant.</p>
 *
 * @param version stable service-cadence authority version
 * @param freightReferenceClass representative physical freight role
 * @param hubStationArchetypeId representative transfer hub used by the production probe
 * @param regionalHopCount accepted ordinary regional supplier hop envelope
 * @param maximumSourceLocalAccessSeconds worst accepted civilian routine source-to-hub local leg
 * @param maximumJumpAccessSeconds worst accepted civilian routine jump-arrival-to-hub leg per endpoint
 * @param regionalFtlArrivalSeconds representative freight arrival time across the regional hop envelope
 * @param payloadMassKg representative delivered payload per freighter
 * @param hubTransferMassRateKgPerSecond representative physical hub handling rate
 * @param oneEndpointHandlingSeconds physical load or unload duration for one representative payload
 * @param maximumSupplierDeliveryTimeSeconds complete one-way service-time acceptance budget
 * @param localRouteCalibrationVersion exact Stage-20A local-route authority version
 * @param intersystemCadenceVersion exact Stage-20A inter-system cadence authority version
 * @param propulsionReferenceVersion exact representative propulsion catalog version
 * @param stationInfrastructureFingerprint exact Stage-18 station-infrastructure fingerprint
 * @param stage22ReviewRequired whether this provisional ordinary-start envelope requires Stage-22 review
 */
public record Stage20BootstrapServiceCadenceCalibrationProfile(
        String version,
        String freightReferenceClass,
        String hubStationArchetypeId,
        int regionalHopCount,
        double maximumSourceLocalAccessSeconds,
        double maximumJumpAccessSeconds,
        double regionalFtlArrivalSeconds,
        double payloadMassKg,
        double hubTransferMassRateKgPerSecond,
        double oneEndpointHandlingSeconds,
        double maximumSupplierDeliveryTimeSeconds,
        String localRouteCalibrationVersion,
        String intersystemCadenceVersion,
        String propulsionReferenceVersion,
        String stationInfrastructureFingerprint,
        boolean stage22ReviewRequired) {

    /** Current corrected Stage-20E bootstrap supplier-service cadence authority. */
    public static final String CURRENT_VERSION = "stage20e.bootstrap-service-cadence.v1";
    /** Same representative freight role already used by the representative production probe. */
    public static final String FREIGHT_REFERENCE_CLASS = "EARLY_CIVILIAN_FREIGHTER";
    /** Same representative major-hub archetype already used by the representative production probe. */
    public static final String HUB_STATION_ARCHETYPE_ID = "station.infrastructure.trade_logistics_hub";
    /** Stage-20A defines the ordinary regional calibration envelope through five explicit hops. */
    public static final int REGIONAL_HOP_COUNT = 5;

    /**
     * Validates one immutable service-cadence authority.
     *
     * @param version stable service-cadence version
     * @param freightReferenceClass representative freight role
     * @param hubStationArchetypeId representative hub archetype
     * @param regionalHopCount accepted regional hop count
     * @param maximumSourceLocalAccessSeconds source local access budget
     * @param maximumJumpAccessSeconds jump-access budget per endpoint
     * @param regionalFtlArrivalSeconds regional FTL arrival budget
     * @param payloadMassKg representative payload
     * @param hubTransferMassRateKgPerSecond hub handling rate
     * @param oneEndpointHandlingSeconds one load/unload handling duration
     * @param maximumSupplierDeliveryTimeSeconds total one-way supplier service budget
     * @param localRouteCalibrationVersion local-route authority version
     * @param intersystemCadenceVersion inter-system cadence authority version
     * @param propulsionReferenceVersion propulsion reference version
     * @param stationInfrastructureFingerprint Stage-18 station-infrastructure fingerprint
     * @param stage22ReviewRequired Stage-22 review boundary
     */
    public Stage20BootstrapServiceCadenceCalibrationProfile {
        version = requireText(version, "version");
        freightReferenceClass = requireText(freightReferenceClass, "freightReferenceClass");
        hubStationArchetypeId = requireText(hubStationArchetypeId, "hubStationArchetypeId");
        if (regionalHopCount <= 0) {
            throw new IllegalArgumentException("regionalHopCount must be positive");
        }
        requirePositive(maximumSourceLocalAccessSeconds, "maximumSourceLocalAccessSeconds");
        requirePositive(maximumJumpAccessSeconds, "maximumJumpAccessSeconds");
        requirePositive(regionalFtlArrivalSeconds, "regionalFtlArrivalSeconds");
        requirePositive(payloadMassKg, "payloadMassKg");
        requirePositive(hubTransferMassRateKgPerSecond, "hubTransferMassRateKgPerSecond");
        requirePositive(oneEndpointHandlingSeconds, "oneEndpointHandlingSeconds");
        requirePositive(maximumSupplierDeliveryTimeSeconds, "maximumSupplierDeliveryTimeSeconds");
        localRouteCalibrationVersion = requireText(localRouteCalibrationVersion, "localRouteCalibrationVersion");
        intersystemCadenceVersion = requireText(intersystemCadenceVersion, "intersystemCadenceVersion");
        propulsionReferenceVersion = requireText(propulsionReferenceVersion, "propulsionReferenceVersion");
        stationInfrastructureFingerprint = requireText(
                stationInfrastructureFingerprint, "stationInfrastructureFingerprint");
        double composed = 2d * oneEndpointHandlingSeconds
                + maximumSourceLocalAccessSeconds
                + 2d * maximumJumpAccessSeconds
                + regionalFtlArrivalSeconds;
        if (Math.abs(composed - maximumSupplierDeliveryTimeSeconds)
                > 1.0e-9d * Math.max(1d, maximumSupplierDeliveryTimeSeconds)) {
            throw new IllegalArgumentException("supplier delivery budget must equal its physical cadence components");
        }
    }

    /**
     * Derives the current ordinary-start supplier service-time envelope from accepted authorities.
     *
     * @return deterministic current service-cadence profile
     */
    public static Stage20BootstrapServiceCadenceCalibrationProfile deriveCurrent() {
        Stage20LocalRouteSemanticCalibrationProfile local =
                Stage20LocalRouteSemanticCalibrationProfile.deriveCurrent();
        Stage20IntersystemCadenceCalibrationProfile intersystem =
                Stage20IntersystemCadenceCalibrationProfile.deriveCurrent();
        Stage20RepresentativePropulsionCatalog propulsion =
                Stage20RepresentativePropulsionCatalogLoader.loadDefault();
        var stations = Stage18StationInfrastructureCatalogLoader.loadDefault();

        double sourceLocal = maximumCivilianRoutineSeconds(
                local,
                List.of(BandId.STATION_TO_STATION, BandId.STATION_TO_RESOURCE_FIELD));
        double jumpAccess = maximumCivilianRoutineSeconds(
                local,
                List.of(BandId.JUMP_ARRIVAL_TO_MAJOR_HUB));
        HopCadenceSample regionalFreight = intersystem.samples().stream()
                .filter(value -> value.representativeId().equals(FREIGHT_REFERENCE_CLASS))
                .filter(value -> value.hopCount() == REGIONAL_HOP_COUNT)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "missing accepted five-hop cadence for " + FREIGHT_REFERENCE_CLASS));
        ReferenceDefinition freight = propulsion.findByRepresentativeClass(FREIGHT_REFERENCE_CLASS);
        if (freight == null) {
            throw new IllegalStateException("missing representative freight definition: " + FREIGHT_REFERENCE_CLASS);
        }
        StationArchetypeDefinition hub = stations.findArchetype(HUB_STATION_ARCHETYPE_ID);
        if (hub == null) {
            throw new IllegalStateException("missing representative logistics hub: " + HUB_STATION_ARCHETYPE_ID);
        }
        double handling = freight.missionCargoStoresMassKg() / hub.transferMassRateKgPerSecond();
        requirePositive(handling, "oneEndpointHandlingSeconds");
        double maximumDelivery = 2d * handling
                + sourceLocal
                + 2d * jumpAccess
                + regionalFreight.arrivalTimeS();

        return new Stage20BootstrapServiceCadenceCalibrationProfile(
                CURRENT_VERSION,
                FREIGHT_REFERENCE_CLASS,
                HUB_STATION_ARCHETYPE_ID,
                REGIONAL_HOP_COUNT,
                sourceLocal,
                jumpAccess,
                regionalFreight.arrivalTimeS(),
                freight.missionCargoStoresMassKg(),
                hub.transferMassRateKgPerSecond(),
                handling,
                maximumDelivery,
                local.version(),
                intersystem.version(),
                propulsion.version(),
                stations.getFingerprint(),
                true);
    }

    private static double maximumCivilianRoutineSeconds(
            Stage20LocalRouteSemanticCalibrationProfile local,
            List<BandId> allowedBands) {
        Objects.requireNonNull(local, "local");
        Objects.requireNonNull(allowedBands, "allowedBands");
        return local.samples().stream()
                .filter(value -> allowedBands.contains(value.bandId()))
                .filter(value -> value.representativeGroup() == RepresentativeGroup.CIVILIAN_LOGISTICS)
                .filter(value -> value.thrustPolicy() == ThrustPolicy.ROUTINE_SUSTAINED)
                .mapToDouble(SemanticRouteSample::totalTravelTimeS)
                .max()
                .orElseThrow(() -> new IllegalStateException("missing civilian routine local-route calibration"));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }

    private static void requirePositive(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }
}
