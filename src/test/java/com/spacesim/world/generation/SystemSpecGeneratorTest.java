package com.spacesim.world.generation;

import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.generation.SystemSpec.LocalSite;
import com.spacesim.world.generation.SystemSpec.RegionKind;
import com.spacesim.world.generation.SystemSpec.SiteKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemSpecGeneratorTest {
    private static final SystemSpecGenerator GENERATOR = SystemSpecGenerator.stage20BV1();

    @Test
    void sameSeedAndVersionProduceExactlyTheSamePhysicalSystem() {
        SystemSpec first = GENERATOR.generate(0x5EED_20B1L);
        SystemSpec second = GENERATOR.generate(0x5EED_20B1L);

        assertEquals(first, second);
        assertEquals(GeneratorVersion.STAGE_20_B_V1, first.generatorVersion());
        assertEquals("20B-v1", first.generatorVersion().stableId());
    }

    @Test
    void differentSeedsProduceDifferentButValidSystems() {
        SystemSpec first = GENERATOR.generate(41L);
        SystemSpec second = GENERATOR.generate(42L);

        assertNotEquals(first.systemId(), second.systemId());
        assertNotEquals(first, second);
        assertTrue(SystemSpecValidator.validate(first).isValid());
        assertTrue(SystemSpecValidator.validate(second).isValid());
    }

    @Test
    void deterministicSeedSweepSatisfiesStage20BPhysicalPlacementInvariants() {
        for (long seed = -64L; seed <= 64L; seed++) {
            SystemSpec spec = GENERATOR.generate(seed);
            SystemSpecValidator.ValidationReport report = SystemSpecValidator.validate(spec);

            assertTrue(report.isValid(), () -> "seed=" + spec.seed() + " violations=" + report.violations());
            assertTrue(spec.celestialAnchors().stream().anyMatch(value -> value.kind() == SystemSpec.CelestialKind.PLANET));
            assertEquals(2, spec.sitesOfKind(SiteKind.STATION).size());
            assertEquals(2, spec.sitesOfKind(SiteKind.JUMP_ZONE).size());
            assertFalse(spec.sitesOfKind(SiteKind.RESOURCE_FIELD).isEmpty());
            assertEquals(
                    EnumSet.allOf(RegionKind.class),
                    spec.operationalRegions().stream()
                            .map(SystemSpec.OperationalRegion::kind)
                            .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(RegionKind.class))));
        }
    }

    @Test
    void generatedContentExtentIsDescriptiveAndDoesNotBoundAuthoritativeCoordinates() {
        SystemSpec spec = GENERATOR.generate(20260819L);
        double surveyedExtent = spec.contentExtents().surveyedContentExtentM();
        LocalPhysicalPosition farOutsideGeneratedContent = spec.centralBody().position().translated(
                surveyedExtent * 4d + LocalPhysicalPosition.CELL_SIZE_M,
                -surveyedExtent * 3d);

        assertTrue(spec.centralBody().position().distanceTo(farOutsideGeneratedContent) > surveyedExtent);
        assertTrue(Double.isFinite(farOutsideGeneratedContent.offsetXM()));
        assertTrue(Double.isFinite(farOutsideGeneratedContent.offsetYM()));
        assertTrue(SystemSpecValidator.validate(spec).isValid());
    }

    @Test
    void generatedJumpZonesRespectPhysicalStationStandOff() {
        SystemSpec spec = GENERATOR.generate(77L);
        double requiredStandOff = spec.validationParameters().jumpArrivalStationStandOffM();

        for (LocalSite jump : spec.sitesOfKind(SiteKind.JUMP_ZONE)) {
            for (LocalSite station : spec.sitesOfKind(SiteKind.STATION)) {
                double minimum = jump.footprintRadiusM() + station.footprintRadiusM() + requiredStandOff;
                assertTrue(jump.position().distanceTo(station.position()) >= minimum);
            }
        }
    }

    @Test
    void validatorRejectsOverlappingGeneratedSites() {
        SystemSpec valid = GENERATOR.generate(123L);
        LocalSite station = valid.sitesOfKind(SiteKind.STATION).get(0);
        List<LocalSite> invalidSites = new ArrayList<>(valid.sites());
        invalidSites.add(new LocalSite(
                valid.systemId() + ":TEST_OVERLAP",
                SiteKind.DERELICT,
                station.position(),
                1d));
        SystemSpec invalid = new SystemSpec(
                valid.generatorVersion(),
                valid.seed(),
                valid.systemId(),
                valid.centralBody(),
                valid.celestialAnchors(),
                valid.operationalRegions(),
                invalidSites,
                valid.contentExtents(),
                valid.validationParameters());

        SystemSpecValidator.ValidationReport report = SystemSpecValidator.validate(invalid);
        assertFalse(report.isValid());
        assertTrue(report.violations().stream().anyMatch(value -> value.contains("site overlap/clearance violation")));
    }
}
