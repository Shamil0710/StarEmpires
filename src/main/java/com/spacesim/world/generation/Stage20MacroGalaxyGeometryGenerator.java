package com.spacesim.world.generation;

import com.spacesim.simulation.SimulationRandom;
import com.spacesim.simulation.StatefulRandom;
import com.spacesim.world.SectorId;
import com.spacesim.world.SectorNode;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic Stage-20B macro-region and star-system placement generator.
 *
 * <p>The generated {@link StarSystemNode#x()} / {@link StarSystemNode#y()} values are macro map
 * coordinates used only as a spatial prior for region identity, topology candidate ranking and map
 * presentation. They are <strong>not</strong> SI distance, FTL distance, jump time or a substitute
 * for explicit ordinary neighbor edges. Inter-system movement remains authoritative only through
 * the Stage-20D jump graph and fitted jump physics.</p>
 *
 * <p>Sector membership is generated from spatial clusters rather than by partitioning an already
 * sorted system list. Each sector receives its own seeded centroid, aspect/orientation and internal
 * core/outer/frontier mixture. The resulting topology still has to pass the independent Stage-20D
 * quality gate; this class never adds connectivity or repairs a later rejected graph.</p>
 */
public final class Stage20MacroGalaxyGeometryGenerator {
    /** Current immutable macro-geometry result/profile version. */
    public static final String CURRENT_VERSION = "stage20b.macro-region-system-placement.v1";
    /** Explicit semantic marker carried by every generated macro result. */
    public static final String COORDINATE_SEMANTICS = "TOPOLOGY_SPATIAL_PRIOR_NOT_TRAVEL_DISTANCE";

    private static final String RNG_PREFIX = "stage20b.macro-region-system-placement.v1";
    private static final double GOLDEN_ANGLE_RAD = 2.39996322972865332d;
    private static final double MACRO_LAYOUT_RADIUS = 1_000d;
    private static final double SECTOR_CENTER_RADIAL_JITTER = 0.10d;
    private static final double SECTOR_CENTER_ANGLE_JITTER_RAD = 0.18d;
    private static final double MIN_SECTOR_ASPECT = 0.72d;
    private static final double MAX_SECTOR_ASPECT = 1.28d;
    private static final double FRONTIER_FRACTION = 0.10d;
    private static final double OUTER_FRACTION = 0.25d;
    private static final int MAX_LOCAL_PLACEMENT_ATTEMPTS = 64;

    private Stage20MacroGalaxyGeometryGenerator() {
        throw new AssertionError("No instances");
    }

    /** Broad spatial role of one system inside its generated region. */
    public enum PlacementClass {
        /** Dense interior system inside the primary regional cluster. */
        CORE,
        /** Less-central ordinary system around the regional core. */
        OUTER,
        /** Bounded sparse system that can support frontier branches after topology shaping. */
        FRONTIER
    }

    /**
     * Explicit world-size request consumed by macro placement.
     *
     * <p>Counts are scenario/world-size inputs, not economic or movement bonuses. A request may still
     * produce a topology-rejected seed later; this generator does not silently increase system count
     * or weaken topology quality requirements to force acceptance.</p>
     *
     * @param sectorCount number of spatial macro regions to generate
     * @param minSystemsPerSector minimum systems sampled for each region
     * @param maxSystemsPerSector maximum systems sampled for each region
     */
    public record GenerationRequest(
            int sectorCount,
            int minSystemsPerSector,
            int maxSystemsPerSector) {
        /**
         * Validates one explicit world-size request.
         *
         * @param sectorCount number of macro regions
         * @param minSystemsPerSector minimum systems per region
         * @param maxSystemsPerSector maximum systems per region
         */
        public GenerationRequest {
            if (sectorCount <= 0) {
                throw new IllegalArgumentException("sectorCount must be positive");
            }
            if (minSystemsPerSector <= 0) {
                throw new IllegalArgumentException("minSystemsPerSector must be positive");
            }
            if (maxSystemsPerSector < minSystemsPerSector) {
                throw new IllegalArgumentException("maxSystemsPerSector cannot be below minimum");
            }
            if (sectorCount > 256 || maxSystemsPerSector > 512) {
                throw new IllegalArgumentException("macro generation request exceeds bounded v1 size limits");
            }
        }

        /**
         * Representative ordinary-region request used by Stage-20 integration tests and evidence.
         *
         * @return four spatial regions with eight to ten systems each
         */
        public static GenerationRequest representative() {
            return new GenerationRequest(4, 8, 10);
        }
    }

    /**
     * Machine-readable generated geometry evidence for one region.
     *
     * @param sectorId stable generated sector ID
     * @param centerX macro-layout centroid X coordinate
     * @param centerY macro-layout centroid Y coordinate
     * @param clusterRadius macro-layout characteristic cluster radius
     * @param aspectRatio generated major/minor axis ratio proxy
     * @param orientationRad generated regional cluster orientation in radians
     * @param systemCount generated systems in the region
     */
    public record SectorGeometryEvidence(
            SectorId sectorId,
            double centerX,
            double centerY,
            double clusterRadius,
            double aspectRatio,
            double orientationRad,
            int systemCount) {
        /**
         * Validates one immutable regional geometry row.
         *
         * @param sectorId stable sector ID
         * @param centerX macro-layout center X
         * @param centerY macro-layout center Y
         * @param clusterRadius positive characteristic radius
         * @param aspectRatio positive aspect ratio
         * @param orientationRad finite cluster orientation
         * @param systemCount positive system count
         */
        public SectorGeometryEvidence {
            Objects.requireNonNull(sectorId, "sectorId");
            requireFinite(centerX, "centerX");
            requireFinite(centerY, "centerY");
            requirePositiveFinite(clusterRadius, "clusterRadius");
            requirePositiveFinite(aspectRatio, "aspectRatio");
            requireFinite(orientationRad, "orientationRad");
            if (systemCount <= 0) {
                throw new IllegalArgumentException("systemCount must be positive");
            }
        }
    }

    /**
     * Machine-readable generated geometry evidence for one star system.
     *
     * @param systemId stable generated system ID
     * @param sectorId owning spatial region
     * @param placementClass core/outer/frontier spatial role used only during generation
     * @param normalizedClusterRadius radial placement divided by the region characteristic radius
     */
    public record SystemGeometryEvidence(
            StarSystemId systemId,
            SectorId sectorId,
            PlacementClass placementClass,
            double normalizedClusterRadius) {
        /**
         * Validates one immutable system geometry evidence row.
         *
         * @param systemId stable system ID
         * @param sectorId owning sector ID
         * @param placementClass generated spatial role
         * @param normalizedClusterRadius non-negative normalized radial placement
         */
        public SystemGeometryEvidence {
            Objects.requireNonNull(systemId, "systemId");
            Objects.requireNonNull(sectorId, "sectorId");
            Objects.requireNonNull(placementClass, "placementClass");
            if (!Double.isFinite(normalizedClusterRadius) || normalizedClusterRadius < 0d) {
                throw new IllegalArgumentException("normalizedClusterRadius must be non-negative and finite");
            }
        }
    }

    /**
     * Immutable macro placement result before jump-topology construction.
     *
     * @param version stable generator/result version
     * @param rootSeed authoritative world-generation seed
     * @param request exact world-size request consumed
     * @param coordinateSemantics explicit non-travel coordinate meaning
     * @param sectors generated spatial regions and systems
     * @param sectorEvidence deterministic region geometry evidence
     * @param systemEvidence deterministic per-system placement evidence
     */
    public record MacroGeometryResult(
            String version,
            long rootSeed,
            GenerationRequest request,
            String coordinateSemantics,
            List<SectorNode> sectors,
            List<SectorGeometryEvidence> sectorEvidence,
            List<SystemGeometryEvidence> systemEvidence) {
        /**
         * Validates and freezes one macro-geometry result.
         *
         * @param version stable result version
         * @param rootSeed authoritative seed
         * @param request exact generation request
         * @param coordinateSemantics explicit coordinate meaning
         * @param sectors generated regions
         * @param sectorEvidence region evidence
         * @param systemEvidence system evidence
         */
        public MacroGeometryResult {
            version = requireText(version, "version");
            Objects.requireNonNull(request, "request");
            coordinateSemantics = requireText(coordinateSemantics, "coordinateSemantics");
            if (!COORDINATE_SEMANTICS.equals(coordinateSemantics)) {
                throw new IllegalArgumentException("macro coordinates cannot claim physical travel-distance authority");
            }
            Objects.requireNonNull(sectors, "sectors");
            Objects.requireNonNull(sectorEvidence, "sectorEvidence");
            Objects.requireNonNull(systemEvidence, "systemEvidence");
            ArrayList<SectorNode> sectorCopy = new ArrayList<>(sectors);
            ArrayList<SectorGeometryEvidence> regionCopy = new ArrayList<>(sectorEvidence);
            ArrayList<SystemGeometryEvidence> systemCopy = new ArrayList<>(systemEvidence);
            if (sectorCopy.stream().anyMatch(Objects::isNull)
                    || regionCopy.stream().anyMatch(Objects::isNull)
                    || systemCopy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("macro result collections cannot contain null");
            }
            sectorCopy.sort(Comparator.comparing(SectorNode::id));
            regionCopy.sort(Comparator.comparing(SectorGeometryEvidence::sectorId));
            systemCopy.sort(Comparator.comparing(SystemGeometryEvidence::systemId));
            sectors = List.copyOf(sectorCopy);
            sectorEvidence = List.copyOf(regionCopy);
            systemEvidence = List.copyOf(systemCopy);
            if (sectors.size() != request.sectorCount() || sectorEvidence.size() != sectors.size()) {
                throw new IllegalArgumentException("macro result must contain evidence for every requested sector");
            }
            int totalSystems = sectors.stream().mapToInt(value -> value.systems().size()).sum();
            if (systemEvidence.size() != totalSystems) {
                throw new IllegalArgumentException("macro result must contain evidence for every generated system");
            }
            HashSet<StarSystemId> systemIds = new HashSet<>();
            sectors.forEach(sector -> sector.systems().forEach(system -> {
                if (!systemIds.add(system.id())) {
                    throw new IllegalArgumentException("duplicate generated system ID: " + system.id());
                }
            }));
        }
    }

    /**
     * Generates deterministic spatial regions and system map coordinates for one root seed.
     *
     * <p>Generated nodes deliberately contain no planets/asteroid fields yet. Those local physical
     * host/content concepts remain a subsequent Stage-20B slice and must not be fabricated here merely
     * to satisfy Stage-20E resource generation.</p>
     *
     * @param rootSeed authoritative world-generation seed
     * @param request explicit world-size request
     * @return deterministic macro geometry before jump-topology generation
     */
    public static MacroGeometryResult generate(long rootSeed, GenerationRequest request) {
        GenerationRequest checked = Objects.requireNonNull(request, "request");
        SimulationRandom random = new SimulationRandom(rootSeed);
        StatefulRandom global = random.createStream(RNG_PREFIX + ".global");
        double globalPhase = unit(global.nextLong()) * StrictMath.PI * 2d;
        double globalScaleX = interpolate(0.88d, 1.12d, unit(global.nextLong()));
        double globalScaleY = interpolate(0.88d, 1.12d, unit(global.nextLong()));

        ArrayList<SectorNode> sectors = new ArrayList<>();
        ArrayList<SectorGeometryEvidence> sectorEvidence = new ArrayList<>();
        ArrayList<SystemGeometryEvidence> systemEvidence = new ArrayList<>();
        long nextSystemId = 1L;
        double nominalClusterRadius = 0.34d * MACRO_LAYOUT_RADIUS / StrictMath.sqrt(checked.sectorCount());

        for (int sectorIndex = 0; sectorIndex < checked.sectorCount(); sectorIndex++) {
            SectorId sectorId = new SectorId(sectorIndex + 1L);
            StatefulRandom sectorRandom = random.createStream(RNG_PREFIX + ".sector." + sectorId.value());
            double baseRadiusFraction = StrictMath.sqrt((sectorIndex + 0.5d) / checked.sectorCount());
            double radialJitter = interpolate(
                    -SECTOR_CENTER_RADIAL_JITTER,
                    SECTOR_CENTER_RADIAL_JITTER,
                    unit(sectorRandom.nextLong()));
            double radius = MACRO_LAYOUT_RADIUS * clamp(baseRadiusFraction + radialJitter, 0.12d, 1.05d);
            double angle = globalPhase
                    + sectorIndex * GOLDEN_ANGLE_RAD
                    + interpolate(
                            -SECTOR_CENTER_ANGLE_JITTER_RAD,
                            SECTOR_CENTER_ANGLE_JITTER_RAD,
                            unit(sectorRandom.nextLong()));
            double centerX = StrictMath.cos(angle) * radius * globalScaleX;
            double centerY = StrictMath.sin(angle) * radius * globalScaleY;
            double clusterRadius = nominalClusterRadius * interpolate(0.78d, 1.22d, unit(sectorRandom.nextLong()));
            double aspect = interpolate(MIN_SECTOR_ASPECT, MAX_SECTOR_ASPECT, unit(sectorRandom.nextLong()));
            double orientation = unit(sectorRandom.nextLong()) * StrictMath.PI * 2d;
            int systemCount = sampleCount(
                    sectorRandom.nextLong(), checked.minSystemsPerSector(), checked.maxSystemsPerSector());

            ArrayList<StarSystemNode> systems = new ArrayList<>();
            ArrayList<Point> localPoints = new ArrayList<>();
            double minimumSeparation = clusterRadius * 0.055d;
            for (int localIndex = 0; localIndex < systemCount; localIndex++) {
                StarSystemId systemId = new StarSystemId(nextSystemId++);
                StatefulRandom systemRandom = random.createStream(
                        RNG_PREFIX + ".sector." + sectorId.value() + ".system." + localIndex);
                PlacementClass placementClass = placementClass(unit(systemRandom.nextLong()));
                Point local = placeLocal(
                        localIndex,
                        placementClass,
                        clusterRadius,
                        aspect,
                        orientation,
                        minimumSeparation,
                        localPoints,
                        systemRandom);
                localPoints.add(local);
                systems.add(new StarSystemNode(
                        systemId,
                        "System " + systemId.value(),
                        centerX + local.x(),
                        centerY + local.y()));
                systemEvidence.add(new SystemGeometryEvidence(
                        systemId,
                        sectorId,
                        placementClass,
                        StrictMath.hypot(local.x(), local.y()) / clusterRadius));
            }

            sectors.add(new SectorNode(sectorId, "Sector " + sectorId.value(), systems));
            sectorEvidence.add(new SectorGeometryEvidence(
                    sectorId,
                    centerX,
                    centerY,
                    clusterRadius,
                    aspect,
                    orientation,
                    systemCount));
        }

        return new MacroGeometryResult(
                CURRENT_VERSION,
                rootSeed,
                checked,
                COORDINATE_SEMANTICS,
                sectors,
                sectorEvidence,
                systemEvidence);
    }

    private static Point placeLocal(
            int localIndex,
            PlacementClass placementClass,
            double clusterRadius,
            double aspect,
            double orientation,
            double minimumSeparation,
            List<Point> existing,
            StatefulRandom random) {
        for (int attempt = 0; attempt < MAX_LOCAL_PLACEMENT_ATTEMPTS; attempt++) {
            double angle = localIndex * GOLDEN_ANGLE_RAD
                    + interpolate(-0.24d, 0.24d, unit(random.nextLong()));
            double radialUnit = StrictMath.sqrt(unit(random.nextLong()));
            double normalizedRadius = switch (placementClass) {
                case CORE -> interpolate(0.08d, 0.55d, radialUnit);
                case OUTER -> interpolate(0.55d, 0.92d, radialUnit);
                case FRONTIER -> interpolate(0.92d, 1.30d, radialUnit);
            };
            double rawX = StrictMath.cos(angle) * clusterRadius * normalizedRadius * aspect;
            double rawY = StrictMath.sin(angle) * clusterRadius * normalizedRadius / aspect;
            double cos = StrictMath.cos(orientation);
            double sin = StrictMath.sin(orientation);
            Point candidate = new Point(
                    rawX * cos - rawY * sin,
                    rawX * sin + rawY * cos);
            boolean clear = existing.stream().allMatch(value -> value.distanceTo(candidate) >= minimumSeparation);
            if (clear) {
                return candidate;
            }
        }
        throw new IllegalStateException("bounded macro system placement could not satisfy local separation");
    }

    private static PlacementClass placementClass(double value) {
        if (value < FRONTIER_FRACTION) {
            return PlacementClass.FRONTIER;
        }
        if (value < FRONTIER_FRACTION + OUTER_FRACTION) {
            return PlacementClass.OUTER;
        }
        return PlacementClass.CORE;
    }

    private static int sampleCount(long bits, int minimum, int maximum) {
        if (minimum == maximum) {
            return minimum;
        }
        int width = maximum - minimum + 1;
        return minimum + (int) Long.remainderUnsigned(mix(bits), width);
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53l;
        value ^= value >>> 33;
        return value;
    }

    private static double unit(long bits) {
        return (bits >>> 11) * 0x1.0p-53;
    }

    private static double interpolate(double minimum, double maximum, double t) {
        return Math.fma(maximum - minimum, clamp(t, 0d, 1d), minimum);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }

    private static void requirePositiveFinite(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }

    private record Point(double x, double y) {
        private double distanceTo(Point other) {
            return StrictMath.hypot(x - other.x, y - other.y);
        }
    }
}
