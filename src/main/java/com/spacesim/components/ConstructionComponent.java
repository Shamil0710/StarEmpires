package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.spacesim.constants.Constants;

import java.util.Arrays;

/**
 * Persistent-ready runtime state одного station construction site.
 *
 * <p>Компонент хранит stable target archetype/name и точный bill of materials по runtime item ID.
 * Физически доставленные материалы находятся только в обычном {@link InventoryComponent}; этот
 * компонент не содержит виртуального прогресса или скрытого ресурса.</p>
 */
public final class ConstructionComponent implements Component {
    /** Stable station archetype content ID, который будет создан после fulfillment. */
    public final String targetStationArchetypeContentId;
    /** Отображаемое имя будущей станции. */
    public final String targetStationName;
    private final int[] requiredMaterials;
    private final int totalRequiredMaterials;

    /**
     * Создаёт construction state.
     *
     * @param targetStationArchetypeContentId непустой stable station archetype ID
     * @param targetStationName непустое имя будущей станции
     * @param requiredMaterials точные неотрицательные требования длиной {@link Constants#MAX_ITEMS}
     * @throws IllegalArgumentException если строки/requirements некорректны или bill of materials пуст
     */
    public ConstructionComponent(
            String targetStationArchetypeContentId,
            String targetStationName,
            int[] requiredMaterials) {
        if (targetStationArchetypeContentId == null || targetStationArchetypeContentId.strip().isEmpty()) {
            throw new IllegalArgumentException("Target station archetype ID не может быть пустым");
        }
        if (targetStationName == null || targetStationName.strip().isEmpty()) {
            throw new IllegalArgumentException("Target station name не может быть пустым");
        }
        if (requiredMaterials == null || requiredMaterials.length != Constants.MAX_ITEMS) {
            throw new IllegalArgumentException("Construction requirements должны иметь длину Constants.MAX_ITEMS");
        }
        long total = 0L;
        for (int amount : requiredMaterials) {
            if (amount < 0) {
                throw new IllegalArgumentException("Construction requirement не может быть отрицательным");
            }
            total += amount;
            if (total > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Construction bill of materials превышает int capacity");
            }
        }
        if (total == 0L) {
            throw new IllegalArgumentException("Construction bill of materials не может быть пустым");
        }
        this.targetStationArchetypeContentId = targetStationArchetypeContentId.strip();
        this.targetStationName = targetStationName.strip();
        this.requiredMaterials = Arrays.copyOf(requiredMaterials, requiredMaterials.length);
        this.totalRequiredMaterials = (int) total;
    }

    /**
     * Возвращает required amount по runtime item ID.
     *
     * @param itemId runtime item ID
     * @return requirement или 0 для некорректного ID
     */
    public int getRequiredAmount(int itemId) {
        return itemId >= 0 && itemId < requiredMaterials.length ? requiredMaterials[itemId] : 0;
    }

    /** @return defensive copy полного bill of materials */
    public int[] copyRequiredMaterials() {
        return Arrays.copyOf(requiredMaterials, requiredMaterials.length);
    }

    /** @return суммарное число физических единиц материалов */
    public int getTotalRequiredMaterials() {
        return totalRequiredMaterials;
    }

    /**
     * Проверяет физическое fulfillment обычного inventory.
     *
     * @param inventory склад construction site
     * @return true, если каждого required item достаточно
     */
    public boolean isFulfilled(InventoryComponent inventory) {
        if (inventory == null || inventory.stock == null || inventory.stock.length < requiredMaterials.length) {
            return false;
        }
        for (int itemId = 0; itemId < requiredMaterials.length; itemId++) {
            if (inventory.stock[itemId] < requiredMaterials[itemId]) {
                return false;
            }
        }
        return true;
    }
}
