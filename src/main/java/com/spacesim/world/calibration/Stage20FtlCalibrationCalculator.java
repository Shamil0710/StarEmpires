package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20FtlCalibrationProfile.JumpEdgeCalibrationSample;
import com.spacesim.world.calibration.Stage20FtlCalibrationProfile.ReferenceDriveCompatibility;
import com.spacesim.world.calibration.Stage20ScaleCalibrationProfile.RepresentativeShipPropulsionEnvelope;

import java.util.Objects;
import java.util.OptionalDouble;

/** Derives one-edge FTL mass/energy/spool/cooldown calibration from accepted reference capability. */
public final class Stage20FtlCalibrationCalculator {
    private Stage20FtlCalibrationCalculator() {
        throw new AssertionError("utility class");
    }

    /**
     * Tests one representative departure mass against the accepted reference jump drive.
     *
     * <p>No parallel-drive count, mass bypass or direct multi-hop capability is inferred. If the
     * representative exceeds the reference translated-mass envelope, energy/spool/cadence outputs
     * remain absent rather than extrapolating the reference drive outside its accepted domain.</p>
     *
     * @param reference accepted FTL calibration reference
     * @param representative representative propulsion/mass envelope
     * @return deterministic one-edge compatibility/cadence sample
     */
    public static JumpEdgeCalibrationSample derive(
            Stage20FtlCalibrationReference reference,
            RepresentativeShipPropulsionEnvelope representative) {
        Stage20FtlCalibrationReference checkedReference = Objects.requireNonNull(reference, "reference");
        RepresentativeShipPropulsionEnvelope checkedRepresentative =
                Objects.requireNonNull(representative, "representative");
        double translatedMassKg = checkedRepresentative.wetMassKg();
        Stage20FtlCalibrationReference.ReferenceDrive drive = checkedReference.referenceDrive();
        double massToLimitRatio = translatedMassKg / drive.maxTranslatedMassKg();
        double edgeTransitTimeS = checkedReference.referenceClosure().exampleEdgeTransitTimeS();

        if (translatedMassKg > drive.maxTranslatedMassKg()) {
            return new JumpEdgeCalibrationSample(
                    checkedRepresentative.representativeId(),
                    checkedRepresentative.authority(),
                    checkedRepresentative.provenanceId(),
                    checkedReference.status(),
                    checkedReference.sourceBaselineId() + ":" + drive.id(),
                    translatedMassKg,
                    drive.maxTranslatedMassKg(),
                    massToLimitRatio,
                    ReferenceDriveCompatibility.EXCEEDS_TRANSLATED_MASS_LIMIT,
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    edgeTransitTimeS,
                    drive.cooldownS(),
                    OptionalDouble.empty());
        }

        double requiredEnergyJ = translatedMassKg * drive.translationEnergyPerKgJ();
        double usefulChargePowerW = drive.chargePowerW() * drive.chargeEfficiency();
        double spoolTimeS = requiredEnergyJ / usefulChargePowerW;
        double readyAgainCadenceS = spoolTimeS + edgeTransitTimeS + drive.cooldownS();
        return new JumpEdgeCalibrationSample(
                checkedRepresentative.representativeId(),
                checkedRepresentative.authority(),
                checkedRepresentative.provenanceId(),
                checkedReference.status(),
                checkedReference.sourceBaselineId() + ":" + drive.id(),
                translatedMassKg,
                drive.maxTranslatedMassKg(),
                massToLimitRatio,
                ReferenceDriveCompatibility.COMPATIBLE,
                OptionalDouble.of(requiredEnergyJ),
                OptionalDouble.of(spoolTimeS),
                edgeTransitTimeS,
                drive.cooldownS(),
                OptionalDouble.of(readyAgainCadenceS));
    }
}
