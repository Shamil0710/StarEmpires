package com.spacesim.world;

import com.spacesim.content.Stage18ShipyardCatalog;
import com.spacesim.content.Stage18ShipyardCatalog.HullPhysicalProfile;
import com.spacesim.content.Stage18ShipyardCatalog.PhysicalInputDefinition;
import com.spacesim.content.Stage18ShipyardCatalogLoader;
import com.spacesim.economy.Stage18SalvageRuntime.SalvageStream;
import com.spacesim.ship.SignatureState;
import com.spacesim.world.Stage20GeneratedWorldSeedAcceptance.Status;
import com.spacesim.world.Stage20LocalInfrastructureLayout.InfrastructurePlacement;
import com.spacesim.world.Stage20LocalInfrastructureLayout.PlacementKind;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.ResourceOccurrence;
import com.spacesim.world.Stage20SpecialLocationWorld.CoordinateDomain;
import com.spacesim.world.Stage20SpecialLocationWorld.HazardBand;
import com.spacesim.world.Stage20SpecialLocationWorld.LocationKind;
import com.spacesim.world.Stage20SpecialLocationWorld.Rarity;
import com.spacesim.world.Stage20SpecialLocationWorld.ScanRequirement;
import com.spacesim.world.Stage20SpecialLocationWorld.SecurityAssessment;
import com.spacesim.world.Stage20SpecialLocationWorld.SpecialLocation;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalog.BandDefinition;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalog.BandId;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalogLoader;
import com.spacesim.world.calibration.Stage20RepresentativeEnduranceProfile;
import com.spacesim.world.calibration.Stage20RepresentativeEnduranceProfile.EnduranceSample;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;
import com.spacesim.world.calibration.Stage20RouteCalibrationCalculator;
import com.spacesim.world.calibration.Stage20ScaleCalibrationCalculator;
import com.spacesim.world.calibration.Stage20ScaleCalibrationProfile;
import com.spacesim.world.calibration.Stage20ScaleCalibrationProfile.RepresentativeShipPropulsionEnvelope;
import com.spacesim.world.calibration.Stage20SensorTargetClassCoverageProfile;
import com.spacesim.world.calibration.Stage20SensorTargetClassCoverageProfile.TargetClass;
import com.spacesim.world.calibration.Stage20SensorTargetClassCoverageProfile.TargetSignatureReference;
import com.spacesim.world.generation.Stage20ResolvedGeneratedWorldProductionProbe.ResolvedProbeResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Deterministic Stage-20H generator for anomalies, derelicts and resource-bound special sites.
 *
 * <p>The generator consumes one exact accepted Stage-20E resolved world. It reuses its ordinary
 * system IDs, local layouts, finite occurrences, Stage-18 hull BOM and accepted sensor signatures.
 * A bounded deterministic coverage repair guarantees at least one location of each current kind in
 * a representative accepted galaxy; it never adds an unbound resource or free faction asset.</p>
 */
public final class Stage20SpecialLocationGenerator {
    /** Current explicit special-location authoring profile. */
    public static final String CURRENT_PROFILE_VERSION = "stage20h.special-location-profile.v1";
    private static final String MINING_REPRESENTATIVE_ID = "MINING_SHIP";
    private static final String DERELICT_HULL_ID = "hull.escort_destroyer_v1";
    private static final double DERELICT_ACCESSIBLE_FRACTION = 0.4d;

    private Stage20SpecialLocationGenerator() {
        throw new AssertionError("No instances");
    }

    /**
     * Generates the current special-location layer over one exact accepted physical world.
     *
     * @param accepted exact accepted resolved production probe
     * @return deterministic special-location world
     */
    public static Stage20SpecialLocationWorld generateCurrent(ResolvedProbeResult accepted) {
        ResolvedProbeResult world = requireAccepted(accepted);
        List<Stage20LocalInfrastructureLayout> layouts = world.generation().localLayouts().orElseThrow();
        Stage20ResourceOccurrenceWorld resources = world.generation().resourceWorld().orElseThrow();
        Stage18ShipyardCatalog shipyards = Stage18ShipyardCatalogLoader.loadDefault();
        HullPhysicalProfile derelictHull = Objects.requireNonNull(
                shipyards.findHullProfile(DERELICT_HULL_ID), "production derelict hull BOM");

        Map<StarSystemId, List<ResourceOccurrence>> occurrencesBySystem = occurrencesBySystem(resources);
        Map<BandId, BandDefinition> bands = routeBands();
        Map<TargetClass, TargetSignatureReference> signatures = targetSignatures();
        RepresentativeShipPropulsionEnvelope miningRoute = miningRouteEnvelope();

        List<Blueprint> blueprints = blueprints();
        ArrayList<SpecialLocation> locations = new ArrayList<>();
        EnumSet<LocationKind> generatedKinds = EnumSet.noneOf(LocationKind.class);
        for (Stage20LocalInfrastructureLayout layout : layouts.stream()
                .sorted(Comparator.comparing(Stage20LocalInfrastructureLayout::systemId)).toList()) {
            for (Blueprint blueprint : blueprints) {
                if (!eligible(layout, blueprint, occurrencesBySystem)) {
                    continue;
                }
                long selection = mix64(world.rootSeed()
                        ^ Long.rotateLeft(layout.systemId().value(), 19)
                        ^ blueprint.salt());
                if (Long.remainderUnsigned(selection, blueprint.frequencyDenominator()) == 0L) {
                    locations.add(generateLocation(
                            world.rootSeed(), layout, blueprint, occurrencesBySystem, bands,
                            signatures, miningRoute, derelictHull));
                    generatedKinds.add(blueprint.kind());
                }
            }
        }

        for (Blueprint blueprint : blueprints) {
            if (generatedKinds.contains(blueprint.kind())) {
                continue;
            }
            Stage20LocalInfrastructureLayout fallback = layouts.stream()
                    .filter(layout -> eligible(layout, blueprint, occurrencesBySystem))
                    .min((left, right) -> compareUnsignedThenSystem(
                            fallbackRank(world.rootSeed(), left.systemId(), blueprint), left.systemId(),
                            fallbackRank(world.rootSeed(), right.systemId(), blueprint), right.systemId()))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "accepted generated world has no eligible system for " + blueprint.kind()));
            locations.add(generateLocation(
                    world.rootSeed(), fallback, blueprint, occurrencesBySystem, bands,
                    signatures, miningRoute, derelictHull));
            generatedKinds.add(blueprint.kind());
        }

        return new Stage20SpecialLocationWorld(
                Stage20SpecialLocationWorld.CURRENT_VERSION,
                world.rootSeed(),
                world.version(),
                CURRENT_PROFILE_VERSION,
                shipyards.getFingerprint(),
                locations);
    }

    private static SpecialLocation generateLocation(
            long rootSeed,
            Stage20LocalInfrastructureLayout layout,
            Blueprint blueprint,
            Map<StarSystemId, List<ResourceOccurrence>> occurrencesBySystem,
            Map<BandId, BandDefinition> bands,
            Map<TargetClass, TargetSignatureReference> signatures,
            RepresentativeShipPropulsionEnvelope miningRoute,
            HullPhysicalProfile derelictHull) {
        String locationId = "special." + layout.systemId().value() + "." + blueprint.archetypeId();
        Optional<ResourceOccurrence> linkedOccurrence = selectOccurrence(
                rootSeed, layout.systemId(), blueprint, occurrencesBySystem);
        LocalPhysicalPosition position = linkedOccurrence.map(ResourceOccurrence::position)
                .orElseGet(() -> offsetPosition(rootSeed, layout, blueprint, bands));
        TrafficAnchor traffic = nearestTrafficAnchor(layout, position);
        double approachTime = Stage20RouteCalibrationCalculator.derive(
                MINING_REPRESENTATIVE_ID, miningRoute, traffic.distanceM()).totalTravelTimeS();

        TargetSignatureReference signatureReference = Objects.requireNonNull(
                signatures.get(blueprint.signatureReference()), "special-location signature reference");
        SignatureState signature = blueprint.radarOnly()
                ? radarOnly(signatureReference.signature())
                : signatureReference.signature();

        List<SalvageStream> salvage = blueprint.kind() == LocationKind.DERELICT
                ? derelictSalvage(locationId, derelictHull)
                : List.of();
        double value = switch (blueprint.kind()) {
            case ANOMALY -> 0d;
            case DERELICT -> salvage.stream().mapToDouble(SalvageStream::accessibleMassKg).sum();
            case RESOURCE_PHENOMENON -> {
                ResourceOccurrence occurrence = linkedOccurrence.orElseThrow();
                yield occurrence.initialAccessibleMassKg()
                        * occurrence.gradeFraction()
                        * occurrence.sourceRecoveryFraction();
            }
        };

        return new SpecialLocation(
                locationId,
                layout.systemId(),
                CoordinateDomain.LOCAL_SYSTEM_SI,
                position,
                blueprint.archetypeId(),
                blueprint.kind(),
                blueprint.rarity(),
                signature,
                blueprint.scanRequirement(),
                blueprint.hazardBand(),
                blueprint.hazardTags(),
                traffic.placement().id(),
                traffic.placement().kind(),
                traffic.distanceM(),
                approachTime,
                SecurityAssessment.UNASSESSED,
                linkedOccurrence.map(ResourceOccurrence::sourceId),
                salvage,
                value,
                signatureReference.provenanceId()
                        + (blueprint.radarOnly() ? ":radar-cross-section-only" : ":full-signature"));
    }

    private static List<Blueprint> blueprints() {
        return List.of(
                new Blueprint(
                        "anomaly.energetic-field.v1",
                        LocationKind.ANOMALY,
                        Rarity.UNCOMMON,
                        ScanRequirement.PASSIVE_CLASSIFICATION,
                        HazardBand.HIGH,
                        List.of("hazard.radiation.variable", "hazard.sensor.interference"),
                        TargetClass.RECON_EW_FRIGATE,
                        false,
                        BandId.INNER_TO_OUTER_SYSTEM,
                        PlacementKind.RESOURCE_FIELD_ANCHOR,
                        false,
                        4,
                        0x2a6f_9d35_1b74_c083L),
                new Blueprint(
                        "derelict.escort-hull.v1",
                        LocationKind.DERELICT,
                        Rarity.VERY_RARE,
                        ScanRequirement.ACTIVE_CLASSIFICATION,
                        HazardBand.MODERATE,
                        List.of("hazard.debris.uncontrolled", "hazard.structure.unstable"),
                        TargetClass.BATTLESHIP,
                        true,
                        BandId.STATION_TO_STATION,
                        PlacementKind.JUMP_ARRIVAL_ANCHOR,
                        false,
                        6,
                        0x71b4_c6e2_053d_98afL),
                new Blueprint(
                        "resource.resonant-deposit.v1",
                        LocationKind.RESOURCE_PHENOMENON,
                        Rarity.RARE,
                        ScanRequirement.PHYSICAL_SURVEY,
                        HazardBand.LOW,
                        List.of("hazard.particulate.field"),
                        TargetClass.TORPEDO_CORVETTE,
                        true,
                        BandId.STATION_TO_RESOURCE_FIELD,
                        PlacementKind.RESOURCE_FIELD_ANCHOR,
                        true,
                        5,
                        0x4ce9_02d7_a861_3f5bL));
    }

    private static LocalPhysicalPosition offsetPosition(
            long rootSeed,
            Stage20LocalInfrastructureLayout layout,
            Blueprint blueprint,
            Map<BandId, BandDefinition> bands) {
        List<InfrastructurePlacement> anchors = layout.placements().stream()
                .filter(value -> value.kind() == blueprint.anchorKind())
                .sorted(Comparator.comparing(InfrastructurePlacement::id))
                .toList();
        if (anchors.isEmpty()) {
            throw new IllegalArgumentException(
                    "generated layout lacks special-location anchor kind " + blueprint.anchorKind());
        }
        long anchorHash = mix64(rootSeed ^ blueprint.salt() ^ Long.rotateLeft(layout.systemId().value(), 7));
        InfrastructurePlacement anchor = anchors.get((int) Long.remainderUnsigned(anchorHash, anchors.size()));
        BandDefinition band = Objects.requireNonNull(bands.get(blueprint.bandId()), "special-location route band");
        double distanceFraction = unitFraction(mix64(anchorHash ^ 0x6a09_e667_f3bc_c909L));
        double distance = Math.fma(distanceFraction, band.maxDistanceM() - band.minDistanceM(), band.minDistanceM());
        double angle = unitFraction(mix64(anchorHash ^ 0xbb67_ae85_84ca_a73bL)) * (2d * StrictMath.PI);
        return anchor.position().translated(distance * StrictMath.cos(angle), distance * StrictMath.sin(angle));
    }

    private static TrafficAnchor nearestTrafficAnchor(
            Stage20LocalInfrastructureLayout layout,
            LocalPhysicalPosition position) {
        return layout.placements().stream()
                .filter(value -> value.kind() != PlacementKind.RESOURCE_FIELD_ANCHOR)
                .map(value -> new TrafficAnchor(value, value.position().distanceTo(position)))
                .filter(value -> value.distanceM() > 0d)
                .min(Comparator.comparingDouble(TrafficAnchor::distanceM)
                        .thenComparing(value -> value.placement().id()))
                .orElseThrow(() -> new IllegalArgumentException("generated layout lacks a distinct traffic anchor"));
    }

    private static List<SalvageStream> derelictSalvage(String locationId, HullPhysicalProfile hull) {
        ArrayList<SalvageStream> result = new ArrayList<>();
        for (PhysicalInputDefinition input : hull.buildInputsKg()) {
            double accessible = input.massKg() * DERELICT_ACCESSIBLE_FRACTION;
            result.add(new SalvageStream(
                    locationId + ".stream." + sanitize(input.commodityId()),
                    input.commodityId(),
                    input.massKg(),
                    accessible,
                    input.massKg() - accessible));
        }
        return List.copyOf(result);
    }

    private static Optional<ResourceOccurrence> selectOccurrence(
            long rootSeed,
            StarSystemId systemId,
            Blueprint blueprint,
            Map<StarSystemId, List<ResourceOccurrence>> occurrencesBySystem) {
        if (!blueprint.linkedResourceRequired()) {
            return Optional.empty();
        }
        List<ResourceOccurrence> occurrences = occurrencesBySystem.getOrDefault(systemId, List.of());
        if (occurrences.isEmpty()) {
            return Optional.empty();
        }
        long hash = mix64(rootSeed ^ blueprint.salt() ^ Long.rotateLeft(systemId.value(), 31));
        return Optional.of(occurrences.get((int) Long.remainderUnsigned(hash, occurrences.size())));
    }

    private static boolean eligible(
            Stage20LocalInfrastructureLayout layout,
            Blueprint blueprint,
            Map<StarSystemId, List<ResourceOccurrence>> occurrencesBySystem) {
        if (blueprint.linkedResourceRequired()
                && occurrencesBySystem.getOrDefault(layout.systemId(), List.of()).isEmpty()) {
            return false;
        }
        return layout.placements().stream().anyMatch(value -> value.kind() == blueprint.anchorKind());
    }

    private static Map<StarSystemId, List<ResourceOccurrence>> occurrencesBySystem(
            Stage20ResourceOccurrenceWorld resources) {
        TreeMap<StarSystemId, List<ResourceOccurrence>> result = new TreeMap<>();
        for (ResourceOccurrence occurrence : resources.occurrences()) {
            result.computeIfAbsent(occurrence.systemId(), ignored -> new ArrayList<>()).add(occurrence);
        }
        result.replaceAll((ignored, values) -> values.stream()
                .sorted(Comparator.comparing(ResourceOccurrence::sourceId)).toList());
        return Map.copyOf(result);
    }

    private static Map<BandId, BandDefinition> routeBands() {
        TreeMap<BandId, BandDefinition> result = new TreeMap<>();
        for (BandDefinition band : Stage20LocalRouteSemanticBandCatalogLoader.loadDefault().bands()) {
            result.put(band.id(), band);
        }
        if (!result.keySet().containsAll(Set.of(
                BandId.STATION_TO_STATION,
                BandId.STATION_TO_RESOURCE_FIELD,
                BandId.INNER_TO_OUTER_SYSTEM))) {
            throw new IllegalStateException("special-location generation requires accepted local-route bands");
        }
        return Map.copyOf(result);
    }

    private static Map<TargetClass, TargetSignatureReference> targetSignatures() {
        HashMap<TargetClass, TargetSignatureReference> result = new HashMap<>();
        for (TargetSignatureReference reference
                : Stage20SensorTargetClassCoverageProfile.deriveCurrent().targets()) {
            result.put(reference.targetClass(), reference);
        }
        return Map.copyOf(result);
    }

    private static RepresentativeShipPropulsionEnvelope miningRouteEnvelope() {
        Stage20ScaleCalibrationProfile scale = Stage20ScaleCalibrationProfile.deriveCurrent();
        RepresentativeShipPropulsionEnvelope baseline = scale.representativeShips().stream()
                .filter(value -> value.representativeId().equals(MINING_REPRESENTATIVE_ID))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("missing mining-ship propulsion authority"));
        EnduranceSample endurance = Stage20RepresentativeEnduranceProfile.deriveCurrent().samples().stream()
                .filter(value -> value.representativeId().equals(MINING_REPRESENTATIVE_ID))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("missing mining-ship endurance authority"));
        return Stage20ScaleCalibrationCalculator.deriveAtThrust(
                baseline,
                CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE,
                endurance.sustainedThrustSourceEvidenceId(),
                CURRENT_PROFILE_VERSION + ":mining-ship-routine-approach",
                endurance.sustainedThrustN());
    }

    private static ResolvedProbeResult requireAccepted(ResolvedProbeResult value) {
        ResolvedProbeResult accepted = Objects.requireNonNull(value, "accepted");
        if (accepted.seedAcceptance().status() != Status.ACCEPTED
                || accepted.generation().localLayouts().isEmpty()
                || accepted.generation().resourceWorld().isEmpty()) {
            throw new IllegalArgumentException("Stage-20H requires one accepted complete generated world");
        }
        return accepted;
    }

    private static SignatureState radarOnly(SignatureState source) {
        if (source.radarCrossSectionM2() <= 0d) {
            throw new IllegalArgumentException("radar-only special location requires accepted RCS evidence");
        }
        return new SignatureState(0d, 0d, source.radarCrossSectionM2(), 0d, 0d, 0d);
    }

    private static int compareUnsignedThenSystem(
            long leftRank,
            StarSystemId leftSystem,
            long rightRank,
            StarSystemId rightSystem) {
        int rank = Long.compareUnsigned(leftRank, rightRank);
        return rank != 0 ? rank : leftSystem.compareTo(rightSystem);
    }

    private static long fallbackRank(long rootSeed, StarSystemId systemId, Blueprint blueprint) {
        return mix64(rootSeed ^ blueprint.salt() ^ Long.rotateLeft(systemId.value(), 43));
    }

    private static double unitFraction(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }

    private static String sanitize(String value) {
        return value.replace('.', '_').replace('-', '_');
    }

    private static long mix64(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58_476d_1ce4_e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d0_49bb_1331_11ebL;
        return mixed ^ (mixed >>> 31);
    }

    private record Blueprint(
            String archetypeId,
            LocationKind kind,
            Rarity rarity,
            ScanRequirement scanRequirement,
            HazardBand hazardBand,
            List<String> hazardTags,
            TargetClass signatureReference,
            boolean radarOnly,
            BandId bandId,
            PlacementKind anchorKind,
            boolean linkedResourceRequired,
            int frequencyDenominator,
            long salt) {
        private Blueprint {
            if (frequencyDenominator <= 1) {
                throw new IllegalArgumentException("special-location frequency denominator must exceed one");
            }
        }
    }

    private record TrafficAnchor(InfrastructurePlacement placement, double distanceM) {
    }
}
