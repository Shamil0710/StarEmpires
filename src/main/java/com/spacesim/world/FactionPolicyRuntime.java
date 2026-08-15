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
     * Legacy/source-compatible install path для authored-only мира.
     *
     * @param session локальная simulation session
     * @param contentCatalog semantic faction catalog
     * @param strategies persistent strategic policies мира
     */
    static void install(
            SimulationSession session,
            ContentCatalog contentCatalog,
            List<FactionStrategicState> strategies) {
        install(
                session,
                FactionIdentityResolver.createDefault(
                        Objects.requireNonNull(contentCatalog, "ContentCatalog не задан"),
                        List.of()),
                strategies);
    }

    /**
     * Устанавливает station access components и post-planner safety system через unified faction
     * identity directory.
     *
     * <p>Пустой strategic layer сохраняет Stage-7 runtime буквально: никакие компоненты и системы
     * не добавляются. Отсутствующая strategy у владельца конкретного рынка означает unrestricted
     * market. Explicit strategy materializes полный allowed-set для всех authored и world-defined
     * factions, имеющих dense runtime slot; missing relation считается нейтральным значением 0.</p>
     *
     * @param session локальная simulation session
     * @param resolver unified authored + world-defined faction identity resolver
     * @param strategies persistent strategic policies мира
     */
    static void install(
            SimulationSession session,
            FactionIdentityResolver resolver,
            List<FactionStrategicState> strategies) {
        SimulationSession checkedSession = Objects.requireNonNull(session, "SimulationSession не задана");
        FactionIdentityResolver identities = Objects.requireNonNull(resolver, "FactionIdentityResolver не задан");
        Objects.requireNonNull(strategies, "Faction strategic states не заданы");
        if (strategies.isEmpty()) {
            return;
        }

        Map<Integer, FactionMarketAccessComponent> ruleByOwnerRuntimeId = new HashMap<>();
        for (FactionStrategicState strategy : strategies) {
            FactionStrategicState value = Objects.requireNonNull(strategy, "FactionStrategicState не задан");
            int ownerRuntimeId = identities.runtimeId(value.factionContentId()).orElseThrow(
                    () -> new IllegalArgumentException(
                            "Strategic policy содержит неизвестную faction: " + value.factionContentId()));
            FactionMarketAccessComponent rule = new FactionMarketAccessComponent()
                    .allowUnfactioned(value.relationTo("") >= value.minimumMarketAccessRelation());
            for (int participantRuntimeId = 0;
                    participantRuntimeId < identities.runtimeSlotCapacity();
                    participantRuntimeId++) {
                String participantStableId = identities.stableId(participantRuntimeId).orElse(null);
                if (participantStableId == null) {
                    continue;
                }
                boolean allowed = participantRuntimeId == ownerRuntimeId
                        || value.relationTo(participantStableId) >= value.minimumMarketAccessRelation();
                rule.setFactionAllowed(participantRuntimeId, allowed);
            }
            ruleByOwnerRuntimeId.put(ownerRuntimeId, rule);
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
