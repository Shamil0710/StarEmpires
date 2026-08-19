package com.spacesim.world.generation;

import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.generation.SystemSpec.CelestialAnchor;
import com.spacesim.world.generation.SystemSpec.CelestialKind;
import com.spacesim.world.generation.SystemSpec.ContentExtents;
import com.spacesim.world.generation.SystemSpec.LocalSite;
import com.spacesim.world.generation.SystemSpec.OperationalRegion;
import com.spacesim.world.generation.SystemSpec.RegionKind;
import com.spacesim.world.generation.SystemSpec.SiteKind;
import com.spacesim.world.generation.SystemSpec.ValidationParameters;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Deterministic Stage-20B generator for SI star-system physical geometry.
 *
 * <p>The numeric bands here establish a coherent first geometry contract only. Stage 20C owns
 * logistics-cadence calibration of station/resource/jump spacing against representative ships. This
 * generator never treats a generated extent as a physical wall.</p>
 */
public final class SystemSpecGenerator {
    private static final double TAU = Math.PI * 2d;
    private static final ValidationParameters V1_VALIDATION = new ValidationParameters(
            5_000_000d,
            2_000_000_000d,
            10_000_000d);

    private final GeneratorVersion version;

    /** Creates a generator for an explicit stable content version. */
    public SystemSpecGenerator(GeneratorVersion version) {
        this.version = Objects.requireNonNull(version, "version");
    }

    /** Creates the current Stage-20B v1 generator. */
    public static SystemSpecGenerator stage20BV1() {
        return new SystemSpecGenerator(GeneratorVersion.STAGE_20_B_V1);
    }

    /**
     * Generates one deterministic physical system from a caller-owned seed.
     *
     * @param seed stable system-generation seed
     * @return immutable physical system specification
     */
    public SystemSpec generate(long seed) {
        StableRandom random = new StableRandom(version.versionedSeed(seed));
        String systemId = String.format(Locale.ROOT, "SYS-%016X", version.versionedSeed(seed));
        LocalPhysicalPosition origin = LocalPhysicalPosition.origin();

        SystemSpec.CentralBody star = new SystemSpec.CentralBody(
                systemId + ":STAR",
                origin,
                between(random, 450_000_000d, 900_000_000d),
                between(random, 1.4e30, 2.6e30));

        List<CelestialAnchor> celestials = generateCelestials(systemId, star, random);
        List<LocalSite> sites = generateSites(systemId, origin, celestials, random);
        ContentExtents extents = calculateExtents(origin, star, celestials, sites);
        List<OperationalRegion> regions = generateRegions(systemId, origin, sites, extents);

        SystemSpec spec = new SystemSpec(
                version,
                seed,
                systemId,
                star,
                celestials,
                regions,
                sites,
                extents,
                V1_VALIDATION);
        SystemSpecValidator.requireValid(spec);
        return spec;
    }

    private static List<CelestialAnchor> generateCelestials(
            String systemId,
            SystemSpec.CentralBody star,
            StableRandom random) {
        List<CelestialAnchor> result = new ArrayList<>();
        int planetCount = 3 + random.nextInt(4);
        double orbit = between(random, 18_000_000_000d, 32_000_000_000d);

        for (int planetIndex = 0; planetIndex < planetCount; planetIndex++) {
            if (planetIndex > 0) {
                orbit *= between(random, 1.45d, 1.85d);
            }
            String planetId = systemId + ":P" + planetIndex;
            double angle = random.nextDouble() * TAU;
            double planetRadius = between(random, 2_000_000d, 9_000_000d);
            LocalPhysicalPosition planetPosition = star.position().translated(
                    Math.cos(angle) * orbit,
                    Math.sin(angle) * orbit);
            result.add(new CelestialAnchor(
                    planetId,
                    CelestialKind.PLANET,
                    star.id(),
                    planetPosition,
                    planetRadius,
                    orbit));

            int moonCount = random.nextInt(3);
            for (int moonIndex = 0; moonIndex < moonCount; moonIndex++) {
                double moonOrbit = 90_000_000d + moonIndex * 120_000_000d
                        + between(random, 0d, 40_000_000d);
                double moonAngle = random.nextDouble() * TAU;
                LocalPhysicalPosition moonPosition = planetPosition.translated(
                        Math.cos(moonAngle) * moonOrbit,
                        Math.sin(moonAngle) * moonOrbit);
                result.add(new CelestialAnchor(
                        planetId + ":M" + moonIndex,
                        CelestialKind.MOON,
                        planetId,
                        moonPosition,
                        between(random, 300_000d, 1_800_000d),
                        moonOrbit));
            }
        }
        return List.copyOf(result);
    }

    private static List<LocalSite> generateSites(
            String systemId,
            LocalPhysicalPosition origin,
            List<CelestialAnchor> celestials,
            StableRandom random) {
        List<CelestialAnchor> planets = celestials.stream()
                .filter(value -> value.kind() == CelestialKind.PLANET)
                .toList();
        List<LocalSite> result = new ArrayList<>();

        CelestialAnchor primary = planets.get(0);
        CelestialAnchor secondary = planets.get(Math.min(1, planets.size() - 1));
        result.add(siteNear(systemId + ":STATION:0", SiteKind.STATION, primary.position(),
                between(random, 220_000_000d, 360_000_000d), random.nextDouble() * TAU, 1_500_000d));
        result.add(siteNear(systemId + ":STATION:1", SiteKind.STATION, secondary.position(),
                between(random, 260_000_000d, 440_000_000d), random.nextDouble() * TAU, 1_200_000d));

        for (int i = 0; i < Math.min(2, planets.size() - 1); i++) {
            double radius = (planets.get(i).orbitalRadiusM() + planets.get(i + 1).orbitalRadiusM()) * 0.5d;
            double angle = random.nextDouble() * TAU;
            result.add(new LocalSite(
                    systemId + ":RESOURCE:" + i,
                    SiteKind.RESOURCE_FIELD,
                    origin.translated(Math.cos(angle) * radius, Math.sin(angle) * radius),
                    between(random, 35_000_000d, 75_000_000d)));
        }

        double outerPlanetRadius = planets.get(planets.size() - 1).orbitalRadiusM();
        double jumpRadius = outerPlanetRadius * between(random, 1.20d, 1.42d);
        double jumpAngle = random.nextDouble() * TAU;
        result.add(new LocalSite(
                systemId + ":JUMP:0",
                SiteKind.JUMP_ZONE,
                origin.translated(Math.cos(jumpAngle) * jumpRadius, Math.sin(jumpAngle) * jumpRadius),
                20_000_000d));
        result.add(new LocalSite(
                systemId + ":JUMP:1",
                SiteKind.JUMP_ZONE,
                origin.translated(
                        Math.cos(jumpAngle + Math.PI) * jumpRadius * 1.08d,
                        Math.sin(jumpAngle + Math.PI) * jumpRadius * 1.08d),
                20_000_000d));

        double derelictRadius = outerPlanetRadius * between(random, 0.55d, 0.78d);
        double derelictAngle = random.nextDouble() * TAU;
        result.add(new LocalSite(
                systemId + ":DERELICT:0",
                SiteKind.DERELICT,
                origin.translated(Math.cos(derelictAngle) * derelictRadius,
                        Math.sin(derelictAngle) * derelictRadius),
                250_000d));

        if (random.nextDouble() >= 0.35d) {
            double anomalyRadius = outerPlanetRadius * between(random, 0.72d, 1.05d);
            double anomalyAngle = random.nextDouble() * TAU;
            result.add(new LocalSite(
                    systemId + ":ANOMALY:0",
                    SiteKind.ANOMALY,
                    origin.translated(Math.cos(anomalyAngle) * anomalyRadius,
                            Math.sin(anomalyAngle) * anomalyRadius),
                    5_000_000d));
        }
        return List.copyOf(result);
    }

    private static LocalSite siteNear(
            String id,
            SiteKind kind,
            LocalPhysicalPosition parentPosition,
            double standOffM,
            double angle,
            double footprintRadiusM) {
        return new LocalSite(
                id,
                kind,
                parentPosition.translated(Math.cos(angle) * standOffM, Math.sin(angle) * standOffM),
                footprintRadiusM);
    }

    private static ContentExtents calculateExtents(
            LocalPhysicalPosition origin,
            SystemSpec.CentralBody star,
            List<CelestialAnchor> celestials,
            List<LocalSite> sites) {
        double infrastructure = maxSiteExtent(origin, sites, SiteKind.STATION);
        double resources = maxSiteExtent(origin, sites, SiteKind.RESOURCE_FIELD);
        double jumps = maxSiteExtent(origin, sites, SiteKind.JUMP_ZONE);
        double expectedTraffic = Math.max(infrastructure, jumps);

        double surveyed = star.physicalRadiusM();
        for (CelestialAnchor celestial : celestials) {
            surveyed = Math.max(surveyed, origin.distanceTo(celestial.position()) + celestial.physicalRadiusM());
        }
        for (LocalSite site : sites) {
            surveyed = Math.max(surveyed, origin.distanceTo(site.position()) + site.footprintRadiusM());
        }

        List<Double> activeDistances = sites.stream()
                .filter(site -> site.kind() == SiteKind.STATION || site.kind() == SiteKind.RESOURCE_FIELD)
                .map(site -> origin.distanceTo(site.position()))
                .sorted(Comparator.naturalOrder())
                .toList();
        int percentileIndex = Math.min(activeDistances.size() - 1,
                (int) Math.ceil(activeDistances.size() * 0.75d) - 1);
        double corePercentile = activeDistances.get(percentileIndex);

        return new ContentExtents(
                corePercentile,
                infrastructure,
                resources,
                jumps,
                surveyed,
                expectedTraffic);
    }

    private static List<OperationalRegion> generateRegions(
            String systemId,
            LocalPhysicalPosition origin,
            List<LocalSite> sites,
            ContentExtents extents) {
        List<OperationalRegion> regions = new ArrayList<>();
        regions.add(new OperationalRegion(
                systemId + ":REGION:CORE",
                RegionKind.CORE_ACTIVITY,
                origin,
                0d,
                Math.max(1d, extents.coreActivityRadiusPercentileM()),
                1d));
        regions.add(new OperationalRegion(
                systemId + ":REGION:RESOURCE",
                RegionKind.RESOURCE_BELT,
                origin,
                Math.max(0d, extents.resourceFieldExtentM() * 0.45d),
                Math.max(1d, extents.resourceFieldExtentM() * 1.08d),
                0.65d));

        LocalSite primaryStation = sites.stream()
                .filter(site -> site.kind() == SiteKind.STATION)
                .findFirst()
                .orElseThrow();
        regions.add(new OperationalRegion(
                systemId + ":REGION:PATROL",
                RegionKind.PATROL_SECURITY,
                primaryStation.position(),
                0d,
                750_000_000d,
                0.85d));

        double emptyInner = extents.coreActivityRadiusPercentileM() * 1.10d;
        double emptyOuter = Math.max(emptyInner + 1d, extents.jumpArrivalExtentM() * 0.92d);
        regions.add(new OperationalRegion(
                systemId + ":REGION:EMPTY_TRANSIT",
                RegionKind.EMPTY_TRANSIT,
                origin,
                emptyInner,
                emptyOuter,
                0.05d));
        return List.copyOf(regions);
    }

    private static double maxSiteExtent(LocalPhysicalPosition origin, List<LocalSite> sites, SiteKind kind) {
        return sites.stream()
                .filter(site -> site.kind() == kind)
                .mapToDouble(site -> origin.distanceTo(site.position()) + site.footprintRadiusM())
                .max()
                .orElse(0d);
    }

    private static double between(StableRandom random, double minInclusive, double maxExclusive) {
        return minInclusive + (maxExclusive - minInclusive) * random.nextDouble();
    }

    /** Fixed SplitMix64 stream whose sequence is owned by GeneratorVersion rather than JDK Random APIs. */
    private static final class StableRandom {
        private long state;

        private StableRandom(long seed) {
            state = seed;
        }

        private long nextLong() {
            long z = (state += 0x9e3779b97f4a7c15L);
            z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
            z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
            return z ^ (z >>> 31);
        }

        private double nextDouble() {
            return (nextLong() >>> 11) * 0x1.0p-53;
        }

        private int nextInt(int bound) {
            if (bound <= 0) {
                throw new IllegalArgumentException("bound must be positive");
            }
            return (int) Long.remainderUnsigned(nextLong(), bound);
        }
    }
}
