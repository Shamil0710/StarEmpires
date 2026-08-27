package com.spacesim.ui;

import com.spacesim.content.ship.Stage21GeneratedMilitaryEngineeringCatalog;
import com.spacesim.persistence.Stage21HGeneratedWorldRuntimePersistentState;
import com.spacesim.world.FleetForceRegistry;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetOperationalAvailability;
import com.spacesim.world.FleetReadinessEvaluator;
import com.spacesim.world.FleetReadinessState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Final Stage-21I command/UI projector.
 *
 * <p>The existing {@link Stage21ILivingWorldUiProjector} remains responsible for actor-bounded
 * diplomacy, goals, overlays, timeline and NPC/mission presentation. This final coordinator enriches
 * only viewer-owned military rows through the accepted Stage-21D {@link FleetForceRegistry} and
 * {@link FleetReadinessEvaluator}. It owns no fleet, supply, damage or logistics state.</p>
 */
public final class Stage21IFinalLivingWorldUiProjector {
    private final Stage21ILivingWorldUiProjector baseProjector;
    private final FleetReadinessEvaluator readinessEvaluator;

    /**
     * Creates the final generated-world projector with the same provisional engineering catalog
     * authority used to materialize Stage-21 generated military fleets.
     */
    public Stage21IFinalLivingWorldUiProjector() {
        this(new Stage21ILivingWorldUiProjector(),
                new FleetReadinessEvaluator(Stage21GeneratedMilitaryEngineeringCatalog.load()));
    }

    Stage21IFinalLivingWorldUiProjector(
            Stage21ILivingWorldUiProjector baseProjector,
            FleetReadinessEvaluator readinessEvaluator) {
        this.baseProjector = Objects.requireNonNull(baseProjector, "baseProjector");
        this.readinessEvaluator = Objects.requireNonNull(readinessEvaluator, "readinessEvaluator");
    }

    /**
     * Projects a checkpoint with fail-closed crew/service observations.
     *
     * <p>Persisted damage, ammunition, propellant, sensors and maintenance are still derived from the
     * authoritative fleet payload. Crew and supply access become zero until an owning runtime provides
     * an explicit bounded observation.</p>
     *
     * @param checkpoint accepted Stage-21H simulation checkpoint
     * @param viewerFactionId actor whose bounded UI is requested
     * @return deterministic read-only final UI snapshot
     */
    public Stage21ILivingWorldUiSnapshot project(
            Stage21HGeneratedWorldRuntimePersistentState checkpoint,
            String viewerFactionId) {
        return project(checkpoint, viewerFactionId, Map.of());
    }

    /**
     * Projects a checkpoint with explicit Stage-21D operational availability observations.
     *
     * @param checkpoint accepted Stage-21H simulation checkpoint
     * @param viewerFactionId actor whose bounded UI is requested
     * @param availabilityByFleet actor-authorized crew/service observations keyed by stable FleetId
     * @return deterministic read-only final UI snapshot
     */
    public Stage21ILivingWorldUiSnapshot project(
            Stage21HGeneratedWorldRuntimePersistentState checkpoint,
            String viewerFactionId,
            Map<FleetId, FleetOperationalAvailability> availabilityByFleet) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        Map<FleetId, FleetOperationalAvailability> availability = availabilityByFleet == null
                ? Map.of()
                : Map.copyOf(availabilityByFleet);

        Stage21ILivingWorldUiSnapshot base = baseProjector.project(checkpoint, viewerFactionId);
        var stage21D = checkpoint.stage21GRuntime()
                .stage21FRuntime()
                .stage21ERuntime()
                .stage21DRuntime();
        var world = stage21D.stage21CRuntime()
                .stage21BRuntime()
                .stage21ARuntime()
                .stage20Runtime()
                .worldState();
        FleetForceRegistry registry = FleetForceRegistry.reconstruct(world, readinessEvaluator, availability);

        List<Stage21ILivingWorldUiSnapshot.MilitaryRow> military = new ArrayList<>();
        for (Stage21ILivingWorldUiSnapshot.MilitaryRow row : base.military()) {
            var group = stage21D.fleetCommandState().groups().stream()
                    .filter(candidate -> candidate.id() == row.commandGroupId())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "projected command group missing from Stage-21D authority: " + row.commandGroupId()));
            List<FleetReadinessState> readiness = group.memberFleetIds().stream()
                    .map(fleetId -> registry.find(fleetId)
                            .orElseThrow(() -> new IllegalStateException(
                                    "command group FleetId missing from Stage-21D force registry: " + fleetId))
                            .readiness())
                    .toList();
            FleetReadinessState aggregate = aggregate(readiness);
            military.add(new Stage21ILivingWorldUiSnapshot.MilitaryRow(
                    row.commandGroupId(),
                    row.commandGroupName(),
                    row.fleetIds(),
                    row.order(),
                    readinessSummary(aggregate),
                    row.route(),
                    "accessBps=" + aggregate.supplyAccessBps(),
                    row.operation(),
                    row.destination(),
                    row.authorityRef()
                            + "+stage21d.force-registry+stage21d.readiness+explicit-operational-availability"));
        }
        military.sort(Comparator.comparingLong(Stage21ILivingWorldUiSnapshot.MilitaryRow::commandGroupId));

        return new Stage21ILivingWorldUiSnapshot(
                base.viewerFactionId(),
                base.simulationTick(),
                base.factions(),
                List.copyOf(military),
                base.overlays(),
                base.timeline(),
                base.npcMissions());
    }

    private static FleetReadinessState aggregate(List<FleetReadinessState> readiness) {
        if (readiness.isEmpty()) {
            return FleetReadinessState.unavailable();
        }
        int structural = FleetReadinessState.FULL;
        int ammunition = FleetReadinessState.FULL;
        int propellant = FleetReadinessState.FULL;
        int crew = FleetReadinessState.FULL;
        int sensors = FleetReadinessState.FULL;
        int maintenance = FleetReadinessState.FULL;
        int supply = FleetReadinessState.FULL;
        for (FleetReadinessState state : readiness) {
            structural = Math.min(structural, state.structuralBps());
            ammunition = Math.min(ammunition, state.ammunitionBps());
            propellant = Math.min(propellant, state.propellantBps());
            crew = Math.min(crew, state.crewBps());
            sensors = Math.min(sensors, state.sensorsBps());
            maintenance = Math.min(maintenance, state.maintenanceBps());
            supply = Math.min(supply, state.supplyAccessBps());
        }
        return new FleetReadinessState(
                structural, ammunition, propellant, crew, sensors, maintenance, supply);
    }

    private static String readinessSummary(FleetReadinessState state) {
        return "overallBps=" + state.overallBps()
                + ";structuralBps=" + state.structuralBps()
                + ";ammunitionBps=" + state.ammunitionBps()
                + ";propellantBps=" + state.propellantBps()
                + ";crewBps=" + state.crewBps()
                + ";sensorsBps=" + state.sensorsBps()
                + ";maintenanceBps=" + state.maintenanceBps();
    }
}
