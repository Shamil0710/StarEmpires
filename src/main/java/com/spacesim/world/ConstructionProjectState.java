package com.spacesim.world;

import com.spacesim.persistence.EntityId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable persistent snapshot of one world-level construction project.
 *
 * @param id stable world-level project ID
 * @param ownerFactionContentId owner faction content ID
 * @param stationArchetypeContentId target station archetype content ID
 * @param systemId target StarSystem
 * @param x target world X coordinate
 * @param y target world Y coordinate
 * @param constructionSiteEntityId local site ID while non-terminal
 * @param materials canonical material requirements/history
 * @param minimumFundingMilliCredits minimum project liquidity
 * @param projectWalletMilliCredits current project/site wallet balance
 * @param buildDurationTicks required target-system build ticks
 * @param status persistent state-machine state
 * @param createdTick project creation tick
 * @param stateChangedTick latest transition tick
 * @param buildStartedTick BUILDING start tick or -1
 * @param completedTick terminal transition tick or -1
 * @param completedStationEntityId historical created station ID only for COMPLETED
 */
public record ConstructionProjectState(
        ConstructionProjectId id,
        String ownerFactionContentId,
        String stationArchetypeContentId,
        StarSystemId systemId,
        float x,
        float y,
        EntityId constructionSiteEntityId,
        List<ConstructionMaterialState> materials,
        long minimumFundingMilliCredits,
        long projectWalletMilliCredits,
        long buildDurationTicks,
        ConstructionProjectStatus status,
        long createdTick,
        long stateChangedTick,
        long buildStartedTick,
        long completedTick,
        EntityId completedStationEntityId) implements Comparable<ConstructionProjectState> {

    /**
     * Validates project invariants and canonical material ordering.
     *
     * @param id stable world-level project ID
     * @param ownerFactionContentId owner faction content ID
     * @param stationArchetypeContentId target station archetype content ID
     * @param systemId target StarSystem
     * @param x target world X coordinate
     * @param y target world Y coordinate
     * @param constructionSiteEntityId local site ID while non-terminal
     * @param materials material requirements/history
     * @param minimumFundingMilliCredits minimum project liquidity
     * @param projectWalletMilliCredits current project wallet balance
     * @param buildDurationTicks required build duration
     * @param status persistent status
     * @param createdTick creation tick
     * @param stateChangedTick latest transition tick
     * @param buildStartedTick BUILDING start or -1
     * @param completedTick terminal tick or -1
     * @param completedStationEntityId historical station ID for COMPLETED
     */
    public ConstructionProjectState {
        Objects.requireNonNull(id, "Construction project ID не задан");
        ownerFactionContentId = requireId(ownerFactionContentId, "Owner faction");
        stationArchetypeContentId = requireId(stationArchetypeContentId, "Station archetype");
        Objects.requireNonNull(systemId, "Construction target system не задан");
        Objects.requireNonNull(status, "Construction status не задан");
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException("Construction coordinates должны быть конечными");
        }
        if (minimumFundingMilliCredits <= 0L || projectWalletMilliCredits < 0L) {
            throw new IllegalArgumentException("Construction funding values некорректны");
        }
        if (buildDurationTicks <= 0L) {
            throw new IllegalArgumentException("Construction buildDurationTicks должен быть положительным");
        }
        if (createdTick < 0L || stateChangedTick < createdTick) {
            throw new IllegalArgumentException("Construction timestamps некорректны");
        }
        if (buildStartedTick < -1L || completedTick < -1L) {
            throw new IllegalArgumentException("Construction optional ticks некорректны");
        }

        List<ConstructionMaterialState> sorted = new ArrayList<>(
                Objects.requireNonNull(materials, "Construction materials не заданы"));
        if (sorted.isEmpty()) {
            throw new IllegalArgumentException("Construction project требует хотя бы один material");
        }
        Set<String> ids = new HashSet<>();
        for (ConstructionMaterialState material : sorted) {
            ConstructionMaterialState checked = Objects.requireNonNull(material, "Construction material не задан");
            if (!ids.add(checked.itemContentId())) {
                throw new IllegalArgumentException("Duplicate construction material: " + checked.itemContentId());
            }
        }
        sorted.sort(ConstructionMaterialState::compareTo);
        materials = List.copyOf(sorted);

        boolean terminal = status == ConstructionProjectStatus.COMPLETED
                || status == ConstructionProjectStatus.CANCELLED
                || status == ConstructionProjectStatus.FAILED;
        if (terminal) {
            if (constructionSiteEntityId != null) {
                throw new IllegalArgumentException("Terminal construction project не должен сохранять site entity");
            }
            if (completedTick < stateChangedTick) {
                throw new IllegalArgumentException("Terminal construction project требует completedTick");
            }
        } else if (constructionSiteEntityId == null) {
            throw new IllegalArgumentException("Non-terminal construction project требует site entity");
        }

        if (status == ConstructionProjectStatus.COMPLETED) {
            if (completedStationEntityId == null) {
                throw new IllegalArgumentException("Completed project требует historical station entity ID");
            }
            if (buildStartedTick < createdTick) {
                throw new IllegalArgumentException("Completed project требует buildStartedTick");
            }
            for (ConstructionMaterialState material : materials) {
                if (!material.fulfilled()) {
                    throw new IllegalArgumentException("Completed project должен иметь fulfilled material history");
                }
            }
        } else if (completedStationEntityId != null) {
            throw new IllegalArgumentException("Only COMPLETED project может ссылаться на station ID");
        }

        if (status == ConstructionProjectStatus.BUILDING && buildStartedTick < createdTick) {
            throw new IllegalArgumentException("BUILDING project требует buildStartedTick");
        }
        if (status == ConstructionProjectStatus.FAILED) {
            if (projectWalletMilliCredits != 0L) {
                throw new IllegalArgumentException("FAILED project не должен сохранять wallet value");
            }
            if (buildStartedTick != -1L && buildStartedTick < createdTick) {
                throw new IllegalArgumentException("FAILED buildStartedTick некорректен");
            }
        } else if (status != ConstructionProjectStatus.BUILDING
                && status != ConstructionProjectStatus.COMPLETED
                && buildStartedTick != -1L) {
            throw new IllegalArgumentException("buildStartedTick допустим только для BUILDING/COMPLETED/FAILED");
        }
        if (!terminal && completedTick != -1L) {
            throw new IllegalArgumentException("Non-terminal project не должен иметь completedTick");
        }
    }

    /** @return true when every required material is fully delivered */
    public boolean materialsFulfilled() {
        for (ConstructionMaterialState material : materials) {
            if (!material.fulfilled()) {
                return false;
            }
        }
        return true;
    }

    /** @return total delivered units over all material types */
    public long totalDeliveredUnits() {
        long total = 0L;
        for (ConstructionMaterialState material : materials) {
            total += material.deliveredAmount();
        }
        return total;
    }

    @Override
    public int compareTo(ConstructionProjectState other) {
        return id.compareTo(other.id);
    }

    private static String requireId(String value, String label) {
        String normalized = Objects.requireNonNull(value, label + " не задан").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " не должен быть пустым");
        }
        return normalized;
    }
}
