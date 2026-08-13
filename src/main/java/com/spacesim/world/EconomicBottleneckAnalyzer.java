package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.model.Recipe;
import com.spacesim.simulation.SimulationSession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

final class EconomicBottleneckAnalyzer {
    private static final int BASE_PRESSURE_BPS = 10_000;

    private EconomicBottleneckAnalyzer() {
        throw new AssertionError("Utility class");
    }

    static EconomicBottleneckReport analyze(WorldSimulation world, ContentCatalog content) {
        Objects.requireNonNull(world, "WorldSimulation не задан");
        Objects.requireNonNull(content, "ContentCatalog не задан");
        List<EconomicBottleneck> result = new ArrayList<>();
        List<StarSystemNode> systems = new ArrayList<>(world.getTopology().systems());
        systems.sort(Comparator.comparing(StarSystemNode::id));
        for (StarSystemNode system : systems) {
            SimulationSession session = world.findSession(system.id()).orElseThrow();
            for (ContentCatalog.ItemDefinition item : content.getItems()) {
                EconomicBottleneck value = analyzeItem(system.id(), session, item);
                if (value != null) {
                    result.add(value);
                }
            }
        }
        result.sort(Comparator.comparingLong(EconomicBottleneck::severityScore).reversed()
                .thenComparing(EconomicBottleneck::systemId)
                .thenComparing(EconomicBottleneck::itemContentId));
        return new EconomicBottleneckReport(result);
    }

    private static EconomicBottleneck analyzeItem(
            StarSystemId systemId, SimulationSession session, ContentCatalog.ItemDefinition item) {
        long deficit = 0L;
        long surplus = 0L;
        int stockouts = 0;
        int pressureBps = BASE_PRESSURE_BPS;
        int itemId = item.runtimeId();
        for (Entity entity : session.getEngine().getEntities()) {
            MarketComponent market = entity.getComponent(MarketComponent.class);
            InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
            if (market == null || inventory == null || !market.isTradable(itemId)) {
                continue;
            }
            int stock = inventory.stock[itemId];
            int target = Math.max(0, market.targetStock[itemId]);
            if (stock < target) {
                deficit += (long) target - stock;
                stockouts += stock == 0 && target > 0 ? 1 : 0;
                pressureBps = Math.max(pressureBps, pressureBps(target, stock));
            } else {
                surplus += (long) stock - target;
            }
        }
        long unmet = Math.max(0L, deficit - surplus);
        if (unmet == 0L) {
            return null;
        }

        int producers = 0;
        int ready = 0;
        int inputBlocked = 0;
        int storageBlocked = 0;
        for (Entity entity : session.getEngine().getEntities()) {
            ProductionComponent production = entity.getComponent(ProductionComponent.class);
            InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
            Recipe recipe = production == null ? null : production.getActiveRecipe();
            if (inventory == null || recipe == null || recipe.getOutputAmount(itemId) <= 0) {
                continue;
            }
            producers++;
            if (missingInput(inventory, recipe)) {
                inputBlocked++;
            } else if (!fitsOneCycle(inventory, recipe)) {
                storageBlocked++;
            } else {
                ready++;
            }
        }

        EconomicBottleneckType type = producers == 0 || ready > 0
                ? EconomicBottleneckType.PRODUCTION_CAPACITY_SHORTAGE
                : inputBlocked >= storageBlocked
                ? EconomicBottleneckType.LOGISTICS_SHORTAGE
                : EconomicBottleneckType.STORAGE_CONGESTION;
        long severity = severityScore(unmet, stockouts, pressureBps);
        return new EconomicBottleneck(systemId, item.id(), type, deficit, surplus, unmet, stockouts,
                producers, ready, inputBlocked, storageBlocked, pressureBps, severity);
    }

    private static boolean missingInput(InventoryComponent inventory, Recipe recipe) {
        for (int itemId = 0; itemId < inventory.stock.length; itemId++) {
            if (inventory.stock[itemId] < recipe.getInputAmount(itemId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean fitsOneCycle(InventoryComponent inventory, Recipe recipe) {
        long total = 0L;
        for (int itemId = 0; itemId < inventory.stock.length; itemId++) {
            long value = (long) inventory.stock[itemId] - recipe.getInputAmount(itemId) + recipe.getOutputAmount(itemId);
            if (value < 0L || value > Integer.MAX_VALUE) {
                return false;
            }
            total += value;
        }
        return total <= inventory.capacity;
    }

    private static int pressureBps(int target, int stock) {
        long value = (long) target * BASE_PRESSURE_BPS / Math.max(1, stock);
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(BASE_PRESSURE_BPS, value);
    }

    private static long severityScore(long unmet, int stockouts, int pressureBps) {
        try {
            long unmetScore = Math.multiplyExact(unmet, 100_000L);
            long stockoutScore = Math.multiplyExact((long) stockouts, 10_000L);
            long pressureScore = Math.max(0L, (long) pressureBps - BASE_PRESSURE_BPS);
            return Math.addExact(Math.addExact(unmetScore, stockoutScore), pressureScore);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }
}
