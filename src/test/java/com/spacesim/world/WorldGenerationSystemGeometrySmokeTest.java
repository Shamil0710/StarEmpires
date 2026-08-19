package com.spacesim.world;

import com.spacesim.world.WorldGenerationPlacementNormalizer.FailureReason;
import com.spacesim.world.WorldGenerationPlacementNormalizer.NormalizationResult;
import com.spacesim.world.WorldGenerationPlacementNormalizer.Status;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalog.BandId;
import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldGenerationSystemGeometrySmokeTest {
    @Test
    void generatedGeometryIsDeterministicSiAndConsumesAcceptedStage20AScale() {
        StarSystemId systemId = new StarSystemId(42L);

        Stage20SystemGeometry first = Stage20SystemGeometryGenerator.generate(0x5EED20BL, systemId);
        Stage20SystemGeometry second = Stage20SystemGeometryGenerator.generate(0x5EED20BL, systemId);

        assertEquals(first, second);
        assertEquals(Stage20SystemGeometry.CURRENT_VERSION, first.version());
        assertEquals(systemId, first.systemId());
        assertEquals(LocalPhysicalPosition.origin(), first.centralReference());
        assertEquals(BandId.INNER_TO_OUTER_SYSTEM, first.sourceRouteBand());
        assertEquals(1_000_000_000d, first.majorInfrastructureExtentM(), 0d);
        assertTrue(first.operationalEnvelope().radiusM() >= 1_000_000_000d);
        assertTrue(first.operationalEnvelope().radiusM() <= 10_000_000_000d);
        assertFalse(first.operationalEnvelope().hardBoundary());
        assertFalse(first.operationalEnvelope().clampAllowed());
        assertTrue(first.provenance().contains("stage20a.local-route-semantic-bands.v1"));
        assertTrue(first.provenance().contains("stage20a.major-infrastructure-extents.v1"));
    }

    @Test
    void operationalEnvelopeDoesNotInvalidateOrClampPhysicalSpaceBeyondIt() {
        Stage20SystemGeometry geometry = Stage20SystemGeometryGenerator.generate(77L, new StarSystemId(7L));
        double radiusM = geometry.operationalEnvelope().radiusM();
        LocalPhysicalPosition outside = geometry.centralReference().translated(radiusM * 2d, radiusM * 0.25d);

        assertTrue(geometry.centralReference().distanceTo(outside) > radiusM);

        NormalizationResult result = WorldGenerationPlacementNormalizer.normalizeBoundaryPlacement(
                geometry.centralReference(),
                outside,
                geometry.majorInfrastructureExtentM(),
                OptionalDouble.empty());

        assertEquals(Status.UNCHANGED, result.status());
        assertEquals(outside, result.resolvedPosition());
        assertEquals(0d, result.displacementM(), 0d);
        assertTrue(result.failureReason().isEmpty());
    }

    @Test
    void normalizeBoundaryPlacementUsesMinimumRadialDisplacement() {
        LocalPhysicalPosition center = LocalPhysicalPosition.origin().translated(2_000_000_000d, -3_000_000_000d);
        LocalPhysicalPosition candidate = center.translated(3_000_000d, 4_000_000d);

        NormalizationResult result = WorldGenerationPlacementNormalizer.normalizeBoundaryPlacement(
                center,
                candidate,
                10_000_000d,
                OptionalDouble.empty());

        assertEquals(Status.NORMALIZED, result.status());
        assertEquals(5_000_000d, result.displacementM(), 1e-6d);
        assertEquals(10_000_000d, center.distanceTo(result.resolvedPosition()), 1e-6d);
        LocalPhysicalPosition.Displacement radial = center.displacementTo(result.resolvedPosition());
        assertEquals(6_000_000d, radial.deltaXM(), 1e-6d);
        assertEquals(8_000_000d, radial.deltaYM(), 1e-6d);
        assertTrue(result.failureReason().isEmpty());
    }

    @Test
    void exactCenterFailsInsteadOfInventingAnArbitraryDirection() {
        LocalPhysicalPosition center = LocalPhysicalPosition.origin();

        NormalizationResult result = WorldGenerationPlacementNormalizer.normalizeBoundaryPlacement(
                center,
                center,
                100_000d,
                OptionalDouble.empty());

        assertEquals(Status.FAILED, result.status());
        assertEquals(center, result.resolvedPosition());
        assertEquals(FailureReason.UNDEFINED_RADIAL_DIRECTION, result.failureReason().orElseThrow());
    }

    @Test
    void genuineHardConstraintConflictFailsInsteadOfClamping() {
        LocalPhysicalPosition candidate = LocalPhysicalPosition.origin().translated(1_000d, 0d);

        NormalizationResult conflict = WorldGenerationPlacementNormalizer.normalizeBoundaryPlacement(
                LocalPhysicalPosition.origin(),
                candidate,
                10_000d,
                OptionalDouble.of(9_000d));
        NormalizationResult outside = WorldGenerationPlacementNormalizer.normalizeBoundaryPlacement(
                LocalPhysicalPosition.origin(),
                LocalPhysicalPosition.origin().translated(20_000d, 0d),
                5_000d,
                OptionalDouble.of(10_000d));

        assertEquals(Status.FAILED, conflict.status());
        assertEquals(FailureReason.HARD_CONSTRAINT_CONFLICT, conflict.failureReason().orElseThrow());
        assertEquals(candidate, conflict.resolvedPosition());
        assertEquals(Status.FAILED, outside.status());
        assertEquals(FailureReason.OUTSIDE_HARD_CONSTRAINT, outside.failureReason().orElseThrow());
    }

    @Test
    void alreadyValidPlacementRemainsBitStable() {
        LocalPhysicalPosition candidate = LocalPhysicalPosition.origin().translated(-8_000d, 6_000d);

        NormalizationResult result = WorldGenerationPlacementNormalizer.normalizeBoundaryPlacement(
                LocalPhysicalPosition.origin(),
                candidate,
                10_000d,
                OptionalDouble.of(20_000d));

        assertEquals(Status.UNCHANGED, result.status());
        assertEquals(candidate, result.originalPosition());
        assertEquals(candidate, result.resolvedPosition());
        assertEquals(0d, result.displacementM(), 0d);
    }

    @Test
    void nonBoundarySemanticsAreConstructorEnforced() {
        assertThrows(IllegalArgumentException.class,
                () -> new Stage20SystemGeometry.OperationalEnvelope(1_000_000_000d, true, false));
        assertThrows(IllegalArgumentException.class,
                () -> new Stage20SystemGeometry.OperationalEnvelope(1_000_000_000d, false, true));
        assertThrows(IllegalArgumentException.class,
                () -> new Stage20SystemGeometry.OperationalEnvelope(0d, false, false));

        assertThrows(IllegalArgumentException.class, () -> new Stage20SystemGeometry(
                Stage20SystemGeometry.CURRENT_VERSION,
                new StarSystemId(1L),
                1L,
                LocalPhysicalPosition.origin(),
                new Stage20SystemGeometry.OperationalEnvelope(1_000d, false, false),
                2_000d,
                BandId.INNER_TO_OUTER_SYSTEM,
                "test-provenance"));
    }

    @Test
    void invalidNormalizationInputsFailBeforeChangingPhysicalState() {
        LocalPhysicalPosition origin = LocalPhysicalPosition.origin();

        assertThrows(IllegalArgumentException.class, () -> WorldGenerationPlacementNormalizer.normalizeBoundaryPlacement(
                origin, origin, -1d, OptionalDouble.empty()));
        assertThrows(IllegalArgumentException.class, () -> WorldGenerationPlacementNormalizer.normalizeBoundaryPlacement(
                origin, origin, 1d, OptionalDouble.of(Double.NaN)));
        assertThrows(NullPointerException.class, () -> Stage20SystemGeometryGenerator.generate(1L, null));
    }
}
