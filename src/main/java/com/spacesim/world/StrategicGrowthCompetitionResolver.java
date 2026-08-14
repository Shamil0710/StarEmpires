package com.spacesim.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure Stage-11D pre-combat competition rule for simultaneous growth plans.
 *
 * <p>Only physically completed anchor projects are eligible. The earliest authoritative
 * construction completion wins an unclaimed system; equal completion ticks are resolved by stable
 * {@link StrategicGrowthState.PlanId}. Existing foreign territory is never conquered by this
 * resolver.</p>
 */
public final class StrategicGrowthCompetitionResolver {
    private StrategicGrowthCompetitionResolver() {
        throw new AssertionError("Utility class");
    }

    /**
     * Selects the deterministic claim winner for one target system.
     *
     * @param target target StarSystem
     * @param currentControllerContentId current controller, blank when unclaimed
     * @param plans persistent growth plans from all factions
     * @param projects current physical construction-project snapshots
     * @return winning PlanId, or empty when no completed eligible anchor may claim
     */
    public static Optional<StrategicGrowthState.PlanId> chooseWinner(
            StarSystemId target,
            String currentControllerContentId,
            List<StrategicGrowthState.Plan> plans,
            List<ConstructionProjectState> projects) {
        StarSystemId targetSystem = Objects.requireNonNull(target, "Target system not set");
        String controller = currentControllerContentId == null
                ? "" : currentControllerContentId.strip();
        Objects.requireNonNull(plans, "Growth plans not set");
        Objects.requireNonNull(projects, "Construction projects not set");

        Map<ConstructionProjectId, ConstructionProjectState> projectsById = new HashMap<>();
        for (ConstructionProjectState project : projects) {
            ConstructionProjectState value = Objects.requireNonNull(project, "Construction project not set");
            if (projectsById.putIfAbsent(value.id(), value) != null) {
                throw new IllegalArgumentException("Duplicate ConstructionProjectId in competition input: " + value.id());
            }
        }

        List<Candidate> candidates = new ArrayList<>();
        for (StrategicGrowthState.Plan plan : plans) {
            StrategicGrowthState.Plan value = Objects.requireNonNull(plan, "Growth plan not set");
            if (!targetSystem.equals(value.targetSystemId())
                    || value.anchorProjectId() == null
                    || (value.status() != StrategicGrowthState.Status.EXECUTING
                    && value.status() != StrategicGrowthState.Status.ESTABLISHED)) {
                continue;
            }
            ConstructionProjectState project = projectsById.get(value.anchorProjectId());
            if (project == null || project.status() != ConstructionProjectStatus.COMPLETED) {
                continue;
            }
            if (!controller.isEmpty() && !controller.equals(value.id().ownerContentId())) {
                continue;
            }
            candidates.add(new Candidate(value.id(), project.completedTick()));
        }
        candidates.sort(Comparator
                .comparingLong(Candidate::completedTick)
                .thenComparing(Candidate::planId));
        return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(0).planId());
    }

    private record Candidate(StrategicGrowthState.PlanId planId, long completedTick) {
        private Candidate {
            Objects.requireNonNull(planId, "PlanId not set");
            if (completedTick < 0L) {
                throw new IllegalArgumentException("Completed tick cannot be negative");
            }
        }
    }
}
