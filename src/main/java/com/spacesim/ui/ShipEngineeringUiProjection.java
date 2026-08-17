package com.spacesim.ui;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.ship.ShipCapabilityService;
import com.spacesim.ship.ShipCapabilityService.Snapshot;

import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Read-only Stage-17.5H text projection of authoritative fitted ship capabilities for UI surfaces.
 *
 * <p>The projection consumes {@link ShipCapabilityService}; it never reads implementation arrays to
 * invent alternate stats and never mutates ECS. Existing UI panels may embed the returned section or
 * render the same snapshot structurally. Values intentionally expose physical SI budgets and local
 * state rather than class-name bonuses.</p>
 */
public final class ShipEngineeringUiProjection {
    private ShipEngineeringUiProjection() {
        throw new AssertionError("Utility class");
    }

    /**
     * Builds a compact Russian-language engineering section for one fitted entity.
     *
     * @param entity selected ECS entity
     * @param capabilities shared read-only ship capability service
     * @return empty string when the entity has no fitted engineering component; otherwise UI text
     */
    public static String describe(Entity entity, ShipCapabilityService capabilities) {
        Entity checkedEntity = Objects.requireNonNull(entity, "entity");
        ShipCapabilityService service = Objects.requireNonNull(capabilities, "capabilities");
        EngineeringComponent engineering = checkedEntity.getComponent(EngineeringComponent.class);
        if (engineering == null) {
            return "";
        }
        Snapshot state = service.snapshot(engineering);
        StringBuilder text = new StringBuilder("Инженерное состояние\n");
        text.append("  Масса: ").append(si(state.derived().totalMassKg())).append(" кг\n")
                .append("  Макс. тяга: ").append(si(state.acceleration().maxThrustN())).append(" Н\n")
                .append("  Макс. ускорение: ")
                .append(si(state.acceleration().maxAccelerationMps2())).append(" м/с²\n")
                .append("  Остаток Δv: ").append(si(state.remainingDeltaVMps())).append(" м/с\n")
                .append("  Запас мощности: ").append(si(state.derived().continuousPowerMarginW())).append(" Вт\n")
                .append("  Тепловой запас: ").append(si(state.thermal().continuousHeatMarginW())).append(" Вт\n")
                .append("  Локальная теплоёмкость свободна: ")
                .append(si(state.thermal().remainingLocalCapacityJ())).append(" Дж\n")
                .append("  Боеприпасы: ").append(si(state.ammunition().ammunitionMassKg()))
                .append(" кг / ").append(state.ammunition().ammunitionCount()).append(" ед.\n")
                .append("  Ремонт требуется: ")
                .append(state.damage().moduleIntegrityByMount().values().stream().anyMatch(value -> value < 1d)
                        || state.damage().compartmentIntegrityById().values().stream().anyMatch(value -> value < 1d)
                        ? "да" : "нет")
                .append('\n');
        if (!state.shields().isEmpty()) {
            double reserve = state.shields().stream().mapToDouble(value -> value.reserveJ()).sum();
            double capacity = state.shields().stream().mapToDouble(value -> value.reserveCapacityJ()).sum();
            text.append("  Щиты: ").append(si(reserve)).append(" / ").append(si(capacity)).append(" Дж\n");
        }
        if (!state.maintenance().overdueMounts().isEmpty()) {
            StringJoiner mounts = new StringJoiner(", ");
            state.maintenance().overdueMounts().forEach(mounts::add);
            text.append("  Просрочено обслуживание: ").append(mounts).append('\n');
        }
        return text.toString().stripTrailing();
    }

    private static String si(double value) {
        if (!Double.isFinite(value)) {
            return "—";
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
