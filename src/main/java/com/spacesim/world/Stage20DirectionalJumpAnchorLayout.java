package com.spacesim.world;

import com.spacesim.world.Stage20LocalInfrastructureLayout.CalibratedConnection;
import com.spacesim.world.Stage20LocalInfrastructureLayout.InfrastructurePlacement;
import com.spacesim.world.Stage20LocalInfrastructureLayout.PlacementKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Topology-aware finalizer for generated Stage-20 jump-arrival anchors.
 *
 * <p>The generic local-layout generator intentionally knows only local semantic distance bands. The
 * accepted galaxy topology, however, knows the macro bearing of every neighboring system. This
 * finalizer composes those two existing authorities without inventing another coordinate system: it
 * preserves each generated jump anchor's calibrated hub distance and rotates only its azimuth so the
 * anchor lies on the side of the local system facing the neighbor represented by that ordinary edge.</p>
 *
 * <p>One canonical anchor is required for every incident edge. The stable identity is
 * {@code jump-arrival.&lt;local-system&gt;.&lt;neighbor-system&gt;}. Distinct edges are never merged into one
 * arrival lane. Calibrated connection distances are recomputed from the rotated authoritative SI
 * positions, while all accepted route-band provenance and consequence envelopes remain unchanged.</p>
 */
public final class Stage20DirectionalJumpAnchorLayout {
    private Stage20DirectionalJumpAnchorLayout() {
        throw new AssertionError("No instances");
    }

    /**
     * Returns the canonical generated jump-anchor identity for one directed local endpoint.
     *
     * @param localSystem system containing the physical anchor
     * @param neighborSystem ordinary topology neighbor represented by the anchor
     * @return stable generated anchor ID
     */
    public static String anchorId(StarSystemId localSystem, StarSystemId neighborSystem) {
        StarSystemId local = Objects.requireNonNull(localSystem, "localSystem");
        StarSystemId neighbor = Objects.requireNonNull(neighborSystem, "neighborSystem");
        if (local.equals(neighbor)) {
            throw new IllegalArgumentException("jump anchor must represent a distinct neighboring system");
        }
        return "jump-arrival." + local.value() + '.' + neighbor.value();
    }

    /**
     * Aligns all accepted-system layouts to the macro bearing of their incident topology edges.
     *
     * @param topology accepted ordinary galaxy topology
     * @param layouts generated local layouts containing one canonical anchor per incident edge
     * @return deterministic system-sorted layouts with topology-facing jump anchors
     */
    public static List<Stage20LocalInfrastructureLayout> alignAll(
            GalaxyTopology topology,
            List<Stage20LocalInfrastructureLayout> layouts) {
        GalaxyTopology checkedTopology = Objects.requireNonNull(topology, "topology");
        Objects.requireNonNull(layouts, "layouts");
        TreeMap<StarSystemId, Stage20LocalInfrastructureLayout> bySystem = new TreeMap<>();
        for (Stage20LocalInfrastructureLayout layout : layouts) {
            Stage20LocalInfrastructureLayout checked = Objects.requireNonNull(layout, "layout");
            if (bySystem.putIfAbsent(checked.systemId(), checked) != null) {
                throw new IllegalArgumentException("duplicate local layout for system " + checked.systemId());
            }
        }
        Set<StarSystemId> expected = new HashSet<>();
        for (StarSystemNode system : checkedTopology.systems()) {
            expected.add(system.id());
        }
        if (!bySystem.keySet().equals(expected)) {
            throw new IllegalArgumentException("local layout coverage differs from accepted topology systems");
        }

        ArrayList<Stage20LocalInfrastructureLayout> result = new ArrayList<>(bySystem.size());
        for (StarSystemNode system : checkedTopology.systems().stream()
                .sorted(java.util.Comparator.comparing(StarSystemNode::id))
                .toList()) {
            result.add(align(checkedTopology, bySystem.get(system.id())));
        }
        return List.copyOf(result);
    }

    /**
     * Aligns one system's canonical jump anchors while preserving every generated radial distance.
     *
     * @param topology accepted ordinary galaxy topology
     * @param layout generated local layout for one topology system
     * @return immutable directionally aligned layout
     */
    public static Stage20LocalInfrastructureLayout align(
            GalaxyTopology topology,
            Stage20LocalInfrastructureLayout layout) {
        GalaxyTopology checkedTopology = Objects.requireNonNull(topology, "topology");
        Stage20LocalInfrastructureLayout source = Objects.requireNonNull(layout, "layout");
        StarSystemNode local = checkedTopology.findSystem(source.systemId()).orElseThrow(
                () -> new IllegalArgumentException("layout system is absent from topology: " + source.systemId()));
        InfrastructurePlacement hub = source.placements().stream()
                .filter(value -> value.id().equals(source.majorHubId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("local layout lacks its major hub placement"));

        List<StarSystemId> neighbors = checkedTopology.neighbors(local.id()).stream().sorted().toList();
        Map<String, StarSystemId> neighborByAnchor = new HashMap<>();
        for (StarSystemId neighbor : neighbors) {
            neighborByAnchor.put(anchorId(local.id(), neighbor), neighbor);
        }
        List<InfrastructurePlacement> jumpAnchors = source.placements().stream()
                .filter(value -> value.kind() == PlacementKind.JUMP_ARRIVAL_ANCHOR)
                .toList();
        Set<String> actualAnchorIds = new HashSet<>();
        for (InfrastructurePlacement anchor : jumpAnchors) {
            actualAnchorIds.add(anchor.id());
        }
        if (!actualAnchorIds.equals(neighborByAnchor.keySet())) {
            throw new IllegalArgumentException(
                    "generated jump-anchor coverage must match incident topology edges in system " + local.id());
        }

        Map<String, InfrastructurePlacement> replacements = new HashMap<>();
        Set<LocalPhysicalPosition> uniquePositions = new HashSet<>();
        for (InfrastructurePlacement anchor : jumpAnchors) {
            StarSystemId neighborId = neighborByAnchor.get(anchor.id());
            StarSystemNode neighbor = checkedTopology.findSystem(neighborId).orElseThrow();
            double macroX = neighbor.x() - local.x();
            double macroY = neighbor.y() - local.y();
            double macroLength = StrictMath.hypot(macroX, macroY);
            if (!Double.isFinite(macroLength) || macroLength <= 0d) {
                throw new IllegalArgumentException(
                        "connected systems must have distinct finite macro coordinates: "
                                + local.id() + " -> " + neighborId);
            }
            double radiusM = hub.position().distanceTo(anchor.position());
            LocalPhysicalPosition alignedPosition = hub.position().translated(
                    macroX / macroLength * radiusM,
                    macroY / macroLength * radiusM);
            if (!uniquePositions.add(alignedPosition)) {
                throw new IllegalStateException(
                        "distinct incident edges collapsed onto one local jump position in system " + local.id());
            }
            replacements.put(anchor.id(), new InfrastructurePlacement(
                    anchor.id(),
                    anchor.kind(),
                    anchor.stationArchetypeId(),
                    alignedPosition,
                    anchor.operationalRadiusM(),
                    anchor.defensiveExclusionReferenceM()));
        }

        ArrayList<InfrastructurePlacement> placements = new ArrayList<>(source.placements().size());
        Map<String, InfrastructurePlacement> byId = new HashMap<>();
        for (InfrastructurePlacement placement : source.placements()) {
            InfrastructurePlacement resolved = replacements.getOrDefault(placement.id(), placement);
            placements.add(resolved);
            byId.put(resolved.id(), resolved);
        }
        ArrayList<CalibratedConnection> connections = new ArrayList<>(source.connections().size());
        for (CalibratedConnection connection : source.connections()) {
            InfrastructurePlacement from = byId.get(connection.fromId());
            InfrastructurePlacement to = byId.get(connection.toId());
            if (from == null || to == null) {
                throw new IllegalArgumentException("local connection references a missing placement");
            }
            connections.add(new CalibratedConnection(
                    connection.fromId(),
                    connection.toId(),
                    connection.bandId(),
                    from.position().distanceTo(to.position()),
                    connection.minDistanceM(),
                    connection.maxDistanceM(),
                    connection.sourceEvidenceId(),
                    connection.logisticsConsequences()));
        }

        return new Stage20LocalInfrastructureLayout(
                source.version(),
                source.systemId(),
                source.rootSeed(),
                source.majorHubId(),
                placements,
                connections,
                source.systemGeometryVersion(),
                source.routeCalibrationVersion(),
                source.stationGeometryVersion(),
                source.stationDefenseVersion());
    }
}