package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.FactionMarketAccessComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.systems.FactionMarketAccessSystem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Материализует persistent diplomacy в transient local-ECS market access rules. */
final class FactionPolicyRuntime {
    private FactionPolicyRuntime() {
        throw new AssertionError("FactionPolicyRuntime не создаёт экземпляров");
    }

    /**
     * Устанавливает station access components и post-planner safety system.
     *
     * <p>Пустой strategic layer сохраняет Stage-7 runtime буквально: никакие компоненты и системы
     * не добавляются. Отсутствующая strategy у владельца конкретного рынка означает unrestricted
     * market. Explicit strategy materializes полный allowed-set для текущего content catalog;
     * missing relation считается нейтральным значением 0.</p>
     *
     * @param session локальная simulation session
     * @param contentCatalog semantic faction catalog
     * @param strategies persistent strategic policies мира
     */
    static void install(
            SimulationSession session,
            ContentCatalog contentCatalog,
            List<FactionStrategicState> strategies) {
        SimulationSession checkedSession = Objects.requireNonNull(session, "SimulationSession не задана");
        ContentCatalog content = Objects.requireNonNull(contentCatalog, "ContentCatalog не задан");
        Objects.requireNonNull(strategies, "Faction strategic states не заданы");
        if (strategies.isEmpty()) {
            return;
        }

        Map<String, ContentCatalog.FactionDefinition> factionsByContentId = new HashMap<>();
        for (ContentCatalog.FactionDefinition faction : content.getFactions()) {
            factionsByContentId.put(faction.id(), faction);
        }
        Map<Integer, FactionMarketAccessComponent> ruleByOwnerRuntimeId = new HashMap<>();
        for (FactionStrategicState strategy : strategies) {
            FactionStrategicState value = Objects.requireNonNull(strategy, "FactionStrategicState не задан");
            ContentCatalog.FactionDefinition owner = factionsByContentId.get(value.factionContentId());
            if (owner == null) {
                throw new IllegalArgumentException(
                        "Strategic policy содержит неизвестную faction: " + value.factionContentId());
            }
            FactionMarketAccessComponent rule = new FactionMarketAccessComponent()
                    .allowUnfactioned(value.relationTo("") >= value.minimumMarketAccessRelation());
            for (ContentCatalog.FactionDefinition participant : content.getFactions()) {
                boolean allowed = participant.runtimeId() == owner.runtimeId()
                        || value.relationTo(participant.id()) >= value.minimumMarketAccessRelation();
                rule.setFactionAllowed(participant.runtimeId(), allowed);
            }
            ruleByOwnerRuntimeId.put(owner.runtimeId(), rule);
        }

        for (Entity entity : checkedSession.getEngine().getEntities()) {
            if (entity.getComponent(MarketComponent.class) == null) {
                continue;
            }
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            if (faction == null) {
                continue;
            }
            FactionMarketAccessComponent template = ruleByOwnerRuntimeId.get(faction.factionId);
            if (template == null) {
                continue;
            }
            FactionMarketAccessComponent access = new FactionMarketAccessComponent()
                    .allowUnfactioned(template.canTrade(-1));
            boolean[] allowed = template.copyAllowedFactionIds();
            for (int factionId = 0; factionId < allowed.length; factionId++) {
                access.setFactionAllowed(factionId, allowed[factionId]);
            }
            entity.add(access);
        }
        checkedSession.getEngine().addSystem(new FactionMarketAccessSystem(checkedSession.getEntityRegistry()));
    }
}
