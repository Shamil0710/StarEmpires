package com.spacesim.economy;

import com.spacesim.content.Stage18ShipyardCatalog;
import com.spacesim.economy.Stage18ShipyardRuntime.InstalledYardState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Transfers existing yard allocations within one station without creating staff or operating capacity. */
public final class Stage18YardResourceAllocator {
    private final Stage18ShipyardCatalog catalog;

    /** @param catalog authoritative yard ratings */
    public Stage18YardResourceAllocator(Stage18ShipyardCatalog catalog) { this.catalog = Objects.requireNonNull(catalog); }

    /**
     * Transfers rated power, work, staff and automation from other installed yards of the same station.
     * Integer staffing remains integer staffing; facility resources are not reinterpreted as yard resources.
     * @param installed actual states from one station
     * @param targetId installed target identity
     * @return immutable states conserving all four totals
     */
    public List<InstalledYardState> allocateRated(List<InstalledYardState> installed, String targetId) {
        var ordered = new ArrayList<>(List.copyOf(installed));
        ordered.sort(Comparator.comparing(InstalledYardState::yardInstanceId));
        if (ordered.stream().map(InstalledYardState::yardInstanceId).distinct().count() != ordered.size())
            throw new IllegalArgumentException("Duplicate yard allocation owner");
        var target = ordered.stream().filter(y -> y.yardInstanceId().equals(targetId)).findFirst().orElseThrow(
                () -> new IllegalArgumentException("Yard resource target is not installed"));
        var definition = catalog.findYard(target.yardDefinitionId());
        if (definition == null || target.conditionFraction() <= 0)
            throw new IllegalArgumentException("Yard resource target has no usable physical design");
        double[] current = {target.allocatedIntegrationPowerW(), target.availableIntegrationWorkRate()};
        double[] deficit = {Math.max(0, definition.ratedIntegrationPowerW() * target.conditionFraction() - current[0]),
                Math.max(0, definition.ratedEngineeringWorkRate() * target.conditionFraction() - current[1])};
        int labor = target.availableLaborCapacity(), automation = target.availableAutomationCapacity();
        int needLabor = Math.max(0, definition.laborCapacity() - labor), needAutomation = Math.max(0, definition.automationCapacity() - automation);
        double power = 0, work = 0; long availableLabor = 0, availableAutomation = 0;
        for (var yard : ordered) if (!yard.yardInstanceId().equals(targetId)) {
            power += yard.allocatedIntegrationPowerW(); work += yard.availableIntegrationWorkRate();
            availableLabor += yard.availableLaborCapacity(); availableAutomation += yard.availableAutomationCapacity();
        }
        if (!Double.isFinite(power) || !Double.isFinite(work) || power < deficit[0] || work < deficit[1]
                || availableLabor < needLabor || availableAutomation < needAutomation)
            throw new IllegalStateException("Insufficient existing yard resources at this station");
        var result = new ArrayList<InstalledYardState>();
        for (var yard : ordered) if (!yard.yardInstanceId().equals(targetId)) {
            double takenPower = Math.min(yard.allocatedIntegrationPowerW(), deficit[0]);
            double takenWork = Math.min(yard.availableIntegrationWorkRate(), deficit[1]);
            int takenLabor = Math.min(yard.availableLaborCapacity(), needLabor);
            int takenAutomation = Math.min(yard.availableAutomationCapacity(), needAutomation);
            deficit[0] -= takenPower; deficit[1] -= takenWork; current[0] += takenPower; current[1] += takenWork;
            needLabor -= takenLabor; needAutomation -= takenAutomation; labor += takenLabor; automation += takenAutomation;
            result.add(new InstalledYardState(yard.yardInstanceId(), yard.yardDefinitionId(), yard.conditionFraction(),
                    yard.allocatedIntegrationPowerW() - takenPower, yard.availableIntegrationWorkRate() - takenWork,
                    yard.availableLaborCapacity() - takenLabor, yard.availableAutomationCapacity() - takenAutomation, yard.enabled()));
        }
        result.add(new InstalledYardState(target.yardInstanceId(), target.yardDefinitionId(), target.conditionFraction(),
                current[0], current[1], labor, automation, true));
        result.sort(Comparator.comparing(InstalledYardState::yardInstanceId)); return List.copyOf(result);
    }
}
