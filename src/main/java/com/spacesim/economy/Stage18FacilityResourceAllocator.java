package com.spacesim.economy;

import com.spacesim.content.Stage18FacilityCatalog;
import com.spacesim.economy.Stage18FacilityRuntime.InstalledFacilityState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Redistributes already allocated station resources; never produces power, staff or maintenance. */
public final class Stage18FacilityResourceAllocator {
    private final Stage18FacilityCatalog catalog;

    /** @param catalog authoritative facility ratings */
    public Stage18FacilityResourceAllocator(Stage18FacilityCatalog catalog) { this.catalog = Objects.requireNonNull(catalog); }

    /**
     * Gives one installed facility its rated allocation by withdrawing from the same station's installations.
     * Insufficient existing resources reject the whole immutable transaction. Donors keep their conditions,
     * identities and enabled states; subsequent capability projection reflects their reduced allocation.
     * @param installed exact states from one physical station
     * @param targetId installed target identity
     * @return deterministically ordered states with conserved allocation totals
     */
    public List<InstalledFacilityState> allocateRated(List<InstalledFacilityState> installed, String targetId) {
        var ordered = new ArrayList<>(List.copyOf(installed));
        ordered.sort(Comparator.comparing(InstalledFacilityState::facilityInstanceId));
        if (ordered.stream().map(InstalledFacilityState::facilityInstanceId).distinct().count() != ordered.size())
            throw new IllegalArgumentException("Duplicate installed resource owner");
        var target = ordered.stream().filter(f -> f.facilityInstanceId().equals(targetId)).findFirst().orElseThrow(
                () -> new IllegalArgumentException("Resource target is not installed"));
        var definition = catalog.findFacility(target.definitionId());
        if (definition == null || !definition.allowedLocationTags().contains(target.locationTag()) || target.conditionFraction() <= 0d)
            throw new IllegalArgumentException("Resource target has no compatible working physical installation");
        double[] requested = {definition.ratedProcessPowerW() * target.conditionFraction(),
                definition.ratedProcessPowerW() * target.conditionFraction() * definition.heatRejectionWPerProcessW(),
                definition.requiredLaborUnitsAtFullRate(), definition.maintenanceWorkRate() * target.conditionFraction()};
        double[] current = values(target);
        double[] deficit = new double[4];
        for (int r = 0; r < 4; r++) {
            deficit[r] = Math.max(0d, requested[r] - current[r]);
            double available = 0d;
            for (var donor : ordered) if (!donor.facilityInstanceId().equals(targetId)) available += values(donor)[r];
            if (!Double.isFinite(available) || deficit[r] > available)
                throw new IllegalStateException("Insufficient existing station resources for rated allocation");
        }
        var result = new ArrayList<InstalledFacilityState>();
        for (var state : ordered) {
            if (state.facilityInstanceId().equals(targetId)) continue;
            double[] remaining = values(state);
            for (int r = 0; r < 4; r++) {
                double take = Math.min(remaining[r], deficit[r]);
                remaining[r] -= take; deficit[r] -= take; current[r] += take;
            }
            result.add(withValues(state, remaining, state.enabled()));
        }
        result.add(withValues(target, current, true));
        result.sort(Comparator.comparing(InstalledFacilityState::facilityInstanceId));
        return List.copyOf(result);
    }

    private static double[] values(InstalledFacilityState state) {
        return new double[]{state.allocatedProcessPowerW(), state.availableHeatRejectionW(),
                state.availableLaborUnits(), state.availableMaintenanceWorkRate()};
    }

    private static InstalledFacilityState withValues(InstalledFacilityState state, double[] values, boolean enabled) {
        return new InstalledFacilityState(state.facilityInstanceId(), state.definitionId(), state.conditionFraction(),
                values[0], values[1], values[2], values[3], state.locationTag(), enabled);
    }
}
