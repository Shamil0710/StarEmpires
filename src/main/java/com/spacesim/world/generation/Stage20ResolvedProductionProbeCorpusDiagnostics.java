package com.spacesim.world.generation;

import com.spacesim.world.Stage20CommodityFreightFrontierCombiner;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import com.spacesim.world.Stage20GeneratedWorldSeedAcceptance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Read-only fixed-corpus evidence for the resolved coordinated-freight production probe. */
public final class Stage20ResolvedProductionProbeCorpusDiagnostics {
    /** Stable diagnostic version. */
    public static final String CURRENT_VERSION = "stage20e.resolved-production-probe-corpus-diagnostics.v1";
    private static final long FIRST_FIXED_SEED = 1L;
    private static final long LAST_FIXED_SEED = 16L;

    private Stage20ResolvedProductionProbeCorpusDiagnostics() {
        throw new AssertionError("No instances");
    }

    /** One final resolved whole-seed status. */
    public enum SeedStatus { ACCEPTED, REJECTED, UNRESOLVED }

    /**
     * Evidence for one fixed representative seed.
     *
     * @param rootSeed exact root seed
     * @param status final resolved whole-seed status
     * @param placementStatus placement status when topology reached downstream generation
     * @param freightStatus coordinated freight status when placement was accepted
     * @param failureReasons normalized whole-seed failure/blocker reasons
     * @param freightSearchNodesVisited bounded coordinated freight search work
     */
    public record SeedEvidence(
            long rootSeed,
            SeedStatus status,
            Optional<PlacementStatus> placementStatus,
            Optional<Stage20CommodityFreightFrontierCombiner.Status> freightStatus,
            List<Stage20GeneratedWorldSeedAcceptance.FailureReason> failureReasons,
            int freightSearchNodesVisited) {
        /** Validates one immutable seed row. */
        public SeedEvidence {
            if (rootSeed <= 0L || freightSearchNodesVisited < 0) {
                throw new IllegalArgumentException("seed/search values must be valid");
            }
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(placementStatus, "placementStatus");
            Objects.requireNonNull(freightStatus, "freightStatus");
            failureReasons = List.copyOf(Objects.requireNonNull(failureReasons, "failureReasons"));
            if (failureReasons.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("failureReasons cannot contain null");
            }
            if (freightStatus.isEmpty() && freightSearchNodesVisited != 0) {
                throw new IllegalArgumentException("search work requires coordinated freight evidence");
            }
            if (status == SeedStatus.ACCEPTED && !failureReasons.isEmpty()) {
                throw new IllegalArgumentException("accepted seed cannot contain failures");
            }
        }
    }

    /** Aggregate fixed-corpus evidence without a pass-rate target. */
    public record Report(
            String version,
            String resolvedProbeVersion,
            String representativeProfileVersion,
            String wholeSeedAcceptanceVersion,
            String coordinatedFreightProfileVersion,
            int fixedSeedCount,
            int acceptedSeedCount,
            int rejectedSeedCount,
            int unresolvedSeedCount,
            int totalFreightSearchNodesVisited,
            List<SeedEvidence> seeds) {
        /** Validates aggregate count partitioning. */
        public Report {
            version = text(version, "version");
            resolvedProbeVersion = text(resolvedProbeVersion, "resolvedProbeVersion");
            representativeProfileVersion = text(representativeProfileVersion, "representativeProfileVersion");
            wholeSeedAcceptanceVersion = text(wholeSeedAcceptanceVersion, "wholeSeedAcceptanceVersion");
            coordinatedFreightProfileVersion = text(
                    coordinatedFreightProfileVersion, "coordinatedFreightProfileVersion");
            if (fixedSeedCount <= 0 || acceptedSeedCount < 0 || rejectedSeedCount < 0
                    || unresolvedSeedCount < 0 || totalFreightSearchNodesVisited < 0
                    || acceptedSeedCount + rejectedSeedCount + unresolvedSeedCount != fixedSeedCount) {
                throw new IllegalArgumentException("resolved corpus counts must partition the fixed set");
            }
            seeds = List.copyOf(Objects.requireNonNull(seeds, "seeds"));
            if (seeds.size() != fixedSeedCount || seeds.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("resolved corpus requires exactly one row per fixed seed");
            }
        }
    }

    /** Runs the unchanged fixed root seeds through the resolved v3 production path. */
    public static Report runFixed() {
        var profile = Stage20RepresentativeGeneratedWorldProbeProfileV3.deriveCurrent();
        ArrayList<SeedEvidence> rows = new ArrayList<>();
        int accepted = 0;
        int rejected = 0;
        int unresolved = 0;
        int totalSearchNodes = 0;
        for (long seed = FIRST_FIXED_SEED; seed <= LAST_FIXED_SEED; seed++) {
            var result = Stage20ResolvedGeneratedWorldProductionProbe.run(seed, profile);
            Stage20GeneratedWorldSeedAcceptance.SeedResult finalResult = result.seedAcceptance();
            SeedStatus status = switch (finalResult.status()) {
                case ACCEPTED -> SeedStatus.ACCEPTED;
                case REJECTED_SEED -> SeedStatus.REJECTED;
                case UNRESOLVED_AUTHORITY -> SeedStatus.UNRESOLVED;
            };
            switch (status) {
                case ACCEPTED -> accepted++;
                case REJECTED -> rejected++;
                case UNRESOLVED -> unresolved++;
            }
            int searchNodes = result.coordinatedFreightAcceptance()
                    .map(value -> value.totalSearchNodesVisited())
                    .orElse(0);
            totalSearchNodes = Math.addExact(totalSearchNodes, searchNodes);
            rows.add(new SeedEvidence(
                    seed,
                    status,
                    result.generation().placement().map(value -> value.status()),
                    result.coordinatedFreightAcceptance().map(value -> value.combination().status()),
                    finalResult.failures().stream().map(Stage20GeneratedWorldSeedAcceptance.Failure::reason).toList(),
                    searchNodes));
        }
        return new Report(
                CURRENT_VERSION,
                Stage20ResolvedGeneratedWorldProductionProbe.CURRENT_VERSION,
                profile.version(),
                Stage20GeneratedWorldSeedAcceptance.RESOLVED_FREIGHT_VERSION,
                profile.coordinatedFreightAcceptance().version(),
                rows.size(),
                accepted,
                rejected,
                unresolved,
                totalSearchNodes,
                rows);
    }

    /** Serializes deterministic compact CI evidence. */
    public static String toText(Report report) {
        Report value = Objects.requireNonNull(report, "report");
        StringBuilder text = new StringBuilder(4_096);
        text.append("version=").append(value.version()).append('\n');
        text.append("resolvedProbeVersion=").append(value.resolvedProbeVersion()).append('\n');
        text.append("representativeProfileVersion=").append(value.representativeProfileVersion()).append('\n');
        text.append("wholeSeedAcceptanceVersion=").append(value.wholeSeedAcceptanceVersion()).append('\n');
        text.append("coordinatedFreightProfileVersion=").append(value.coordinatedFreightProfileVersion()).append('\n');
        text.append("fixedSeedCount=").append(value.fixedSeedCount()).append('\n');
        text.append("acceptedSeedCount=").append(value.acceptedSeedCount()).append('\n');
        text.append("rejectedSeedCount=").append(value.rejectedSeedCount()).append('\n');
        text.append("unresolvedSeedCount=").append(value.unresolvedSeedCount()).append('\n');
        text.append("totalFreightSearchNodesVisited=").append(value.totalFreightSearchNodesVisited()).append('\n');
        for (SeedEvidence seed : value.seeds()) {
            text.append("seed=").append(seed.rootSeed())
                    .append(" status=").append(seed.status())
                    .append(" placement=").append(seed.placementStatus().map(Enum::name).orElse("NONE"))
                    .append(" freight=").append(seed.freightStatus().map(Enum::name).orElse("NONE"))
                    .append(" nodes=").append(seed.freightSearchNodesVisited())
                    .append(" failures=").append(seed.failureReasons())
                    .append('\n');
        }
        return text.toString();
    }

    private static String text(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.strip();
    }
}
