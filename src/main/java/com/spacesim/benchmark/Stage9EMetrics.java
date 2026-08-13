package com.spacesim.benchmark;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.economy.EconomicTransaction;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.ConstructionMaterialState;
import com.spacesim.world.ConstructionProjectState;
import com.spacesim.world.FactionEconomicPressureState;
import com.spacesim.world.WorldSimulation;

import java.util.List;

final class Stage9EMetrics {
    private static final String MINERS = "faction.miners";
    private static final String FOUNDRY = "station.foundry";
    private static final String STEEL = "item.steel";

    private Stage9EMetrics() {
        throw new AssertionError("Utility class");
    }

    static long unmetDemand(SimulationSession session, int itemId) {
        long deficit = 0L;
        long surplus = 0L;
        for (Entity entity : session.getEngine().getEntities()) {
            MarketComponent market = entity.getComponent(MarketComponent.class);
            InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
            if (market == null || inventory == null || !market.isTradable(itemId)) {
                continue;
            }
            long delta = (long) market.targetStock[itemId] - inventory.stock[itemId];
            if (delta > 0L) {
                deficit = Math.addExact(deficit, delta);
            } else {
                surplus = Math.addExact(surplus, -delta);
            }
        }
        return Math.max(0L, deficit - surplus);
    }

    static int structuralPressureBasisPoints(SimulationSession session, int itemId) {
        int pressure = 10_000;
        for (Entity entity : session.getEngine().getEntities()) {
            MarketComponent market = entity.getComponent(MarketComponent.class);
            InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
            if (market == null || inventory == null || !market.isTradable(itemId)) {
                continue;
            }
            int target = Math.max(0, market.targetStock[itemId]);
            int stock = inventory.stock[itemId];
            if (stock >= target || target <= 0) {
                continue;
            }
            long value = (long) target * 10_000L / Math.max(1, stock);
            pressure = Math.max(pressure, value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value);
        }
        return pressure;
    }

    static int countFoundries(SimulationSession session) {
        int count = 0;
        for (Entity entity : session.getEngine().getEntities()) {
            ArchetypeComponent archetype = entity.getComponent(ArchetypeComponent.class);
            if (archetype != null && FOUNDRY.equals(archetype.contentId)) {
                count++;
            }
        }
        return count;
    }

    static Entity requireFoundry(SimulationSession session) {
        for (Entity entity : session.getEngine().getEntities()) {
            ArchetypeComponent archetype = entity.getComponent(ArchetypeComponent.class);
            if (archetype != null && FOUNDRY.equals(archetype.contentId)) {
                return entity;
            }
        }
        throw new IllegalStateException("Stage 9E requires initial Corona foundry");
    }

    static FactionEconomicPressureState findSteelPressure(WorldSimulation world) {
        for (FactionEconomicPressureState pressure : world.getFactionEconomicPressureStates()) {
            if (MINERS.equals(pressure.factionContentId())
                    && DemoGalaxyFactory.INNER_SYSTEM_ID.equals(pressure.systemId())
                    && STEEL.equals(pressure.itemContentId())) {
                return pressure;
            }
        }
        return null;
    }

    static ConstructionProjectState findReplacementProject(WorldSimulation world) {
        for (ConstructionProjectState project : world.getConstructionProjects()) {
            if (MINERS.equals(project.ownerFactionContentId())
                    && DemoGalaxyFactory.INNER_SYSTEM_ID.equals(project.systemId())
                    && FOUNDRY.equals(project.stationArchetypeContentId())) {
                return project;
            }
        }
        return null;
    }

    static int delivered(ConstructionProjectState project, String itemContentId) {
        for (ConstructionMaterialState material : project.materials()) {
            if (itemContentId.equals(material.itemContentId())) {
                return material.deliveredAmount();
            }
        }
        return 0;
    }

    static String completedStationName(SimulationSession session, ConstructionProjectState project) {
        if (project.completedStationEntityId() == null) {
            return null;
        }
        Entity station = session.getEntityRegistry().find(project.completedStationEntityId());
        IdentityComponent identity = station == null ? null : station.getComponent(IdentityComponent.class);
        return identity == null ? null : identity.name;
    }

    static boolean hasResourceTransformFrom(SimulationSession session, int startIndex, String source) {
        if (source == null || source.isBlank()) {
            return false;
        }
        List<EconomicTransaction> entries = session.getLedger().getEntries();
        for (int index = Math.max(0, startIndex); index < entries.size(); index++) {
            EconomicTransaction entry = entries.get(index);
            if (entry.type() == EconomicTransaction.Type.RESOURCE_TRANSFORM
                    && source.equals(entry.source())) {
                return true;
            }
        }
        return false;
    }
}
