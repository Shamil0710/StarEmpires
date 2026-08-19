package com.spacesim.world;

import com.spacesim.simulation.SimulationRandom;
import com.spacesim.simulation.StatefulRandom;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalog.BandDefinition;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalog.BandId;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticCalibrationProfile;
import com.spacesim.world.calibration.Stage20MajorInfrastructureExtentCalibrationProfile;

/**
 * Deterministic Stage-20B generator for descriptive local-system SI geometry.
 *
 * <p>The generator consumes accepted Stage-20A spatial calibration instead of authoring a parallel
 * scale. The operational envelope is sampled only inside the accepted inner-to-outer-system route
 * band and can never become a hard world radius. Physical positions outside the generated envelope
 * remain valid {@link LocalPhysicalPosition} values.</p>
 */
public final class Stage20SystemGeometryGenerator {
    private static final String RNG_STREAM_PREFIX = "stage20b.system-geometry.v1.system.";

    private Stage20SystemGeometryGenerator() {
        throw new AssertionError("No instances");
    }

    /**
     * Generates one reproducible physical system-geometry snapshot.
     *
     * @param rootSeed root world-generation seed
     * @param systemId stable star-system identity
     * @return deterministic Stage-20B descriptive SI geometry
     */
    public static Stage20SystemGeometry generate(long rootSeed, StarSystemId systemId) {
        if (systemId == null) {
            throw new NullPointerException("systemId");
        }

        Stage20LocalRouteSemanticCalibrationProfile routes =
                Stage20LocalRouteSemanticCalibrationProfile.deriveCurrent();
        Stage20MajorInfrastructureExtentCalibrationProfile infrastructure =
                Stage20MajorInfrastructureExtentCalibrationProfile.deriveCurrent();
        if (!routes.closesStage20BEntryCoverage() || !infrastructure.closesStage20BEntryCoverage()) {
            throw new IllegalStateException("Stage-20B system geometry requires closed Stage-20A spatial calibration");
        }

        BandDefinition innerToOuter = routes.bands().stream()
                .filter(value -> value.id() == BandId.INNER_TO_OUTER_SYSTEM)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing INNER_TO_OUTER_SYSTEM calibration band"));
        double majorInfrastructureExtentM = infrastructure.maximumMajorInfrastructureExtentM();
        double minimumEnvelopeM = Math.max(innerToOuter.minDistanceM(), majorInfrastructureExtentM);
        double maximumEnvelopeM = innerToOuter.maxDistanceM();
        if (minimumEnvelopeM > maximumEnvelopeM) {
            throw new IllegalStateException("Accepted Stage-20A geometry cannot contain major infrastructure");
        }

        StatefulRandom random = new SimulationRandom(rootSeed)
                .createStream(RNG_STREAM_PREFIX + systemId.value());
        double unitSample = unitInterval(random.nextLong());
        double envelopeRadiusM = Math.fma(maximumEnvelopeM - minimumEnvelopeM, unitSample, minimumEnvelopeM);

        return new Stage20SystemGeometry(
                Stage20SystemGeometry.CURRENT_VERSION,
                systemId,
                rootSeed,
                LocalPhysicalPosition.origin(),
                new Stage20SystemGeometry.OperationalEnvelope(envelopeRadiusM, false, false),
                majorInfrastructureExtentM,
                BandId.INNER_TO_OUTER_SYSTEM,
                routes.version() + "|" + infrastructure.version());
    }

    private static double unitInterval(long bits) {
        return (bits >>> 11) * 0x1.0p-53;
    }
}
