package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.FactionMarketAccessComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.systems.FactionMarketAccessSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Materializes persistent diplomacy into transient local-ECS market access rules. */
final class FactionPolicyRuntime {
    private FactionPolicyRuntime() {
        throw new AssertionError("FactionPolicyRuntime does not create instances");
    }

    /**
     * Legacy/source-compatible install path for authored-only worlds.
     *
     * @param session local simulation session
     * @param contentCatalog semantic faction catalog
     * @param strategies persistent strategic policies
     */
    static void install(
            SimulationSession session,
            ContentCatalog contentCatalog,
            List<FactionStrategicState> strategies) {
        install(
                session,
                FactionIdentityResolver.createDefault(
                        Objects.requireNonNull(contentCatalog, "ContentCatalog not set"),
                        List.of()),
                strategies,
                neutralDiplomacy(strategies),
                0L);
    }

    /**
     * Source-compatible unified-identity install path with neutral explicit diplomacy.
     *
     * @param session local simulation session
     * @param resolver unified authored + world-defined faction identity resolver
     * @param strategies persistent strategic policies
     */
    static void install(
            SimulationSession session,
            FactionIdentityResolver resolver,
            List<FactionStrategicState> strategies) {
        install(session, resolver, strategies, neutralDiplomacy(strategies), 0L);
    }

    /**
     * Installs or refreshes station access components using Stage-17E precedence.
     *
     * <p>Every call first removes old transient access components and rematerializes them from
     * persistent strategy + diplomacy. Effective precedence is hard embargo, explicit treaty right,
     * then the legacy relation threshold. The post-planner safety system is installed at most once
     * per {@link SimulationSession}.</p>
     *
     * @param session local simulation session
     * @param resolver unified authored + world-defined faction identity resolver
     * @param strategies persistent strategic policies
     * @param diplomacyStates persistent explicit diplomacy aggregates
     * @param worldTick authoritative world tick used for treaty/embargo expiry
     */
    static void install(
            SimulationSession session,
            FactionIdentityResolver resolver,
            List<FactionStrategicState> strategies,
            List<FactionDiplomacyState> diplomacyStates,
            long worldTick) {
        SimulationSession checkedSession = Objects.requireNonNull(session, "SimulationSession not set");
        FactionIdentityResolver identities = Objects.requireNonNull(resolver, "FactionIdentityResolver not set");
        Objects.requireNonNull(strategies, "Faction strategic states not set");
        Objects.requireNonNull(diplomacyStates, "Faction diplomacy states not set");
        if (worldTick < 0L) {
            throw new IllegalArgumentException("Authoritative world tick cannot be negative");
        }

        for (Entity entity : checkedSession.getEngine().getEntities()) {
            if (entity.getComponent(MarketComponent.class) != null) {
                entity.remove(FactionMarketAccessComponent.class);
            }
        }
        if (strategies.isEmpty()) {
            return;
        }

        Map<Integer, FactionMarketAccessComponent> ruleByOwnerRuntimeId = new HashMap<>();
        for (FactionStrategicState strategy : strategies) {
            FactionStrategicState value = Objects.requireNonNull(strategy, "FactionStrategicState not set");
            int ownerRuntimeId = identities.runtimeId(value.factionContentId()).orElseThrow(
                    () -> new IllegalArgumentException(
                            "Strategic policy contains unknown faction: " + value.factionContentId()));
            FactionMarketAccessComponent rule = new FactionMarketAccessComponent()
                    .allowUnfactioned(DiplomaticMarketAccessResolver.evaluate(
                            strategies,
                            diplomacyStates,
                            value.factionContentId(),
                            null,
                            worldTick).allowed());
            for (int participantRuntimeId = 0;
                    participantRuntimeId < identities.runtimeSlotCapacity();
                    participantRuntimeId++) {
                String participantStableId = identities.stableId(participantRuntimeId).orElse(null);
                if (participantStableId == null) {
                    continue;
                }
                boolean allowed = DiplomaticMarketAccessResolver.evaluate(
                        strategies,
                        diplomacyStates,
                        value.factionContentId(),
                        participantStableId,
                        worldTick).allowed();
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
        if (checkedSession.getEngine().getSystem(FactionMarketAccessSystem.class) == null) {
            checkedSession.getEngine().addSystem(
                    new FactionMarketAccessSystem(checkedSession.getEntityRegistry()));
        }
    }

    private static List<FactionDiplomacyState> neutralDiplomacy(List<FactionStrategicState> strategies) {
        Objects.requireNonNull(strategies, "Faction strategic states not set");
        List<FactionDiplomacyState> result = new ArrayList<>(strategies.size());
        for (FactionStrategicState strategy : strategies) {
            result.add(FactionDiplomacyState.neutral(
                    Objects.requireNonNull(strategy, "FactionStrategicState not set").factionContentId()));
        }
        result.sort(null);
        return List.copyOf(result);
    }
}
