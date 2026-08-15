package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.model.Recipe;
import com.spacesim.simulation.SimulationSession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Применяет persistent faction production/stock goals к существующему authoritative economic core.
 *
 * <p>Engine не создаёт виртуальный спрос и не производит ресурсы напрямую. Он только пересчитывает
 * обычный {@link MarketComponent#targetStock} из persistent configured baseline и текущих strategic
 * demand contributions, а также меняет data-driven recipe существующего
 * {@link ProductionComponent}. После этого цены, логистика и физическое производство продолжают
 * работать штатными Market/TradeAI/Production systems.</p>
 */
public final class FactionStrategicPolicyEngine {
    private FactionStrategicPolicyEngine() {
        throw new AssertionError("FactionStrategicPolicyEngine не создаёт экземпляров");
    }

    /**
     * Validates semantic references in one common stock/production authoring value without mutation.
     *
     * @param contentCatalog authoritative semantic catalog
     * @param policy common player/AI stock-production policy
     * @throws NullPointerException when a required value is absent
     * @throws IllegalArgumentException when an item, station archetype or recipe reference is unknown
     */
    public static void validatePolicy(
            ContentCatalog contentCatalog,
            FactionStockProductionPolicyState policy) {
        ContentCatalog content = Objects.requireNonNull(contentCatalog, "ContentCatalog не задан");
        FactionStockProductionPolicyState checked = Objects.requireNonNull(
                policy, "Faction stock/production policy not set");
        for (FactionStockPolicyState stock : checked.stockPolicies()) {
            requireItem(content, stock);
        }
        validateProduction(checked.productionPolicies(), content);
    }

    /**
     * Применяет одно deterministic strategic policy decision.
     *
     * <p>Все semantic content references валидируются до первой mutation. StarSystems обходятся по
     * stable ID, entities — по persistent EntityId. Для каждого уже торгуемого item effective target
     * пересчитывается как максимум configured station baseline и текущего aggregate strategic demand.
     * Поэтому добавление policy может повысить target, а удаление policy безопасно возвращает его к
     * baseline либо к оставшемуся более высокому demand. Production progress сбрасывается только при
     * фактической смене recipe.</p>
     *
     * @param world runtime multi-system world
     * @param contentCatalog authoritative semantic catalog мира
     * @param factionContentId faction, чьи policies применяются
     * @return отчёт о фактически изменённых ECS-конфигурациях
     * @throws NullPointerException если обязательная зависимость не задана
     * @throws IllegalArgumentException если faction/policy content reference неизвестна
     */
    public static ApplicationReport apply(
            WorldSimulation world,
            ContentCatalog contentCatalog,
            String factionContentId) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "WorldSimulation не задан");
        ContentCatalog content = Objects.requireNonNull(contentCatalog, "ContentCatalog не задан");
        String factionId = Objects.requireNonNull(factionContentId, "Faction content ID не задан").strip();
        if (factionId.isEmpty()) {
            throw new IllegalArgumentException("Faction content ID не может быть пустым");
        }
        int factionRuntimeId = checkedWorld.findFactionRuntimeId(factionId).orElseThrow(
                () -> new IllegalArgumentException("Неизвестная faction: " + factionId));
        FactionStrategicState strategy = checkedWorld.findFactionStrategicState(factionId)
                .orElseThrow(() -> new IllegalArgumentException("Faction не имеет strategic state: " + factionId));

        Map<Integer, Integer> demandFloorByRuntimeItem = validateAndBuildDemand(strategy, content);
        Map<String, String> productionByArchetype = validateProduction(strategy.productionPolicies(), content);

        int marketsAdjusted = 0;
        int productionRetooled = 0;
        List<StarSystemNode> systems = new ArrayList<>(checkedWorld.getTopology().systems());
        systems.sort(Comparator.comparing(StarSystemNode::id));
        for (StarSystemNode system : systems) {
            SimulationSession session = checkedWorld.findSession(system.id()).orElseThrow();
            List<Entity> owned = ownedEntities(session, factionRuntimeId);
            for (Entity entity : owned) {
                MarketComponent market = entity.getComponent(MarketComponent.class);
                InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
                if (market != null && inventory != null) {
                    boolean changed = false;
                    for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
                        if (!market.tradableItems[itemId]) {
                            continue;
                        }
                        int configuredBaseline = Math.max(0, market.configuredTargetStock[itemId]);
                        int requestedFloor = demandFloorByRuntimeItem.getOrDefault(itemId, 0);
                        int feasiblePolicyFloor = Math.min(requestedFloor, Math.max(0, inventory.capacity));
                        int desiredTarget = Math.max(configuredBaseline, feasiblePolicyFloor);
                        if (market.targetStock[itemId] != desiredTarget) {
                            market.targetStock[itemId] = desiredTarget;
                            changed = true;
                        }
                    }
                    if (changed) {
                        market.isDirty = true;
                        marketsAdjusted++;
                    }
                }

                ArchetypeComponent archetype = entity.getComponent(ArchetypeComponent.class);
                ProductionComponent production = entity.getComponent(ProductionComponent.class);
                if (archetype == null || production == null) {
                    continue;
                }
                String desiredRecipeId = productionByArchetype.get(archetype.contentId);
                if (desiredRecipeId == null) {
                    continue;
                }
                Recipe desired = content.createRuntimeRecipe(desiredRecipeId);
                if (!sameProductionConfiguration(production, desired)) {
                    production.recipes.clear();
                    production.recipes.add(desired);
                    production.activeRecipeIndex = 0;
                    production.progressSeconds = 0f;
                    productionRetooled++;
                }
            }
        }
        return new ApplicationReport(
                marketsAdjusted,
                productionRetooled,
                strategy.strategicGoals().size());
    }

    private static Map<Integer, Integer> validateAndBuildDemand(
            FactionStrategicState strategy,
            ContentCatalog content) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (FactionStockPolicyState policy : strategy.stockPolicies()) {
            addDemand(result, content, policy);
        }
        for (FactionStrategicGoalState goal : strategy.strategicGoals()) {
            for (FactionStockPolicyState demand : goal.demandFloors()) {
                addDemand(result, content, demand);
            }
        }
        return Map.copyOf(result);
    }

    private static void addDemand(
            Map<Integer, Integer> result,
            ContentCatalog content,
            FactionStockPolicyState demand) {
        ContentCatalog.ItemDefinition item = requireItem(content, demand);
        result.merge(item.runtimeId(), demand.targetStockFloor(), Math::max);
    }

    private static ContentCatalog.ItemDefinition requireItem(
            ContentCatalog content,
            FactionStockPolicyState demand) {
        ContentCatalog.ItemDefinition item = content.findItem(demand.itemContentId());
        if (item == null) {
            throw new IllegalArgumentException("Strategic demand ссылается на неизвестный item: "
                    + demand.itemContentId());
        }
        return item;
    }

    private static Map<String, String> validateProduction(
            List<FactionProductionPolicyState> policies,
            ContentCatalog content) {
        Map<String, String> result = new LinkedHashMap<>();
        for (FactionProductionPolicyState policy : policies) {
            if (content.findStationArchetype(policy.stationArchetypeContentId()) == null) {
                throw new IllegalArgumentException("Production policy ссылается на неизвестный station archetype: "
                        + policy.stationArchetypeContentId());
            }
            if (content.findRecipe(policy.recipeContentId()) == null) {
                throw new IllegalArgumentException("Production policy ссылается на неизвестный recipe: "
                        + policy.recipeContentId());
            }
            result.put(policy.stationArchetypeContentId(), policy.recipeContentId());
        }
        return Map.copyOf(result);
    }

    private static List<Entity> ownedEntities(SimulationSession session, int factionRuntimeId) {
        List<Entity> result = new ArrayList<>();
        for (Entity entity : session.getEngine().getEntities()) {
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            EntityIdComponent id = entity.getComponent(EntityIdComponent.class);
            if (faction != null && faction.factionId == factionRuntimeId && id != null) {
                result.add(entity);
            }
        }
        result.sort(Comparator.comparingLong(entity ->
                entity.getComponent(EntityIdComponent.class).id.value()));
        return result;
    }

    private static boolean sameProductionConfiguration(ProductionComponent production, Recipe desired) {
        if (production.activeRecipeIndex != 0 || production.recipes.size() != 1) {
            return false;
        }
        Recipe current = production.recipes.get(0);
        if (current == null
                || !current.name.equals(desired.name)
                || Float.floatToIntBits(current.durationSeconds) != Float.floatToIntBits(desired.durationSeconds)) {
            return false;
        }
        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            if (current.getInputAmount(itemId) != desired.getInputAmount(itemId)
                    || current.getOutputAmount(itemId) != desired.getOutputAmount(itemId)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param marketsAdjusted число market entities, где изменён хотя бы один effective targetStock
     * @param productionStationsRetooled число production entities с фактически заменённым recipe
     * @param activeStrategicGoals число active strategic goals в policy
     */
    public record ApplicationReport(
            int marketsAdjusted,
            int productionStationsRetooled,
            int activeStrategicGoals) {
        /**
         * @param marketsAdjusted неотрицательное число изменённых рынков
         * @param productionStationsRetooled неотрицательное число retooled production stations
         * @param activeStrategicGoals неотрицательное число goals
         */
        public ApplicationReport {
            if (marketsAdjusted < 0 || productionStationsRetooled < 0 || activeStrategicGoals < 0) {
                throw new IllegalArgumentException("Strategic policy report counters не могут быть отрицательными");
            }
        }
    }
}
