package com.spacesim.world;

/**
 * Устойчивый world-level идентификатор флота или отдельного корабля.
 *
 * <p>System-local EntityId принадлежит конкретной SimulationSession и может измениться при
 * переходе между StarSystems. FleetId принадлежит world layer и сохраняется на протяжении всей
 * жизни fleet, включая состояние transit.</p>
 *
 * @param value положительное числовое значение world-level идентификатора
 */
public record FleetId(long value) implements Comparable<FleetId> {
    /** Проверяет положительность world-level fleet ID. */
    public FleetId {
        if (value <= 0L) {
            throw new IllegalArgumentException("FleetId должен быть положительным");
        }
    }

    @Override
    public int compareTo(FleetId other) {
        return Long.compare(value, other.value);
    }

    @Override
    public String toString() {
        return "fleet:" + value;
    }
}
