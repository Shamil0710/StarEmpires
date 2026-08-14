package com.spacesim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.spacesim.combat.CombatController;
import com.spacesim.components.CombatCommandComponent;
import com.spacesim.components.CombatComponent;
import com.spacesim.components.CombatRuntimeComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.PlayerControlledComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ContentCatalog;

import java.util.Objects;

/**
 * Minimal deterministic Stage-13 combat AI.
 *
 * <p>AI does not mutate hull/shields and has no private damage formula. It only chooses the nearest
 * different-faction combatant already inside weapon range and writes the same
 * {@link CombatCommandComponent} used by player input. Tactical pursuit, diplomacy-aware rules of
 * engagement and fleet doctrine remain later-stage responsibilities.</p>
 */
public final class CombatAISystem extends EntitySystem {
    private static final Family COMBATANTS = Family.all(
            CombatComponent.class,
            TransformComponent.class,
            EntityIdComponent.class,
            FactionComponent.class).get();

    private final ComponentMapper<CombatComponent> combatMapper = ComponentMapper.getFor(CombatComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper = ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<EntityIdComponent> idMapper = ComponentMapper.getFor(EntityIdComponent.class);
    private final ComponentMapper<FactionComponent> factionMapper = ComponentMapper.getFor(FactionComponent.class);
    private final ComponentMapper<CombatCommandComponent> commandMapper =
            ComponentMapper.getFor(CombatCommandComponent.class);
    private final ContentCatalog contentCatalog;

    /**
     * Creates basic command-producing combat AI.
     *
     * @param contentCatalog authoritative data-driven content
     */
    public CombatAISystem(ContentCatalog contentCatalog) {
        this.contentCatalog = Objects.requireNonNull(contentCatalog, "ContentCatalog not set");
    }

    /** Selects deterministic in-range targets and writes shared fire intent. */
    @Override
    public void update(float deltaTime) {
        ImmutableArray<Entity> combatants = getEngine().getEntitiesFor(COMBATANTS);
        for (Entity attacker : combatants) {
            CombatComponent attackerCombat = combatMapper.get(attacker);
            if (attackerCombat == null || attackerCombat.hull <= 0f
                    || attacker.getComponent(PlayerControlledComponent.class) != null) {
                continue;
            }
            CombatCommandComponent command = commandMapper.get(attacker);
            if (command == null) {
                command = new CombatCommandComponent();
                attacker.add(command);
            }
            Entity target = selectTarget(attacker, combatants);
            if (target == null) {
                command.clear();
            } else {
                command.targetId = idMapper.get(target).id;
                command.fireRequested = true;
            }
        }
    }

    private Entity selectTarget(Entity attacker, ImmutableArray<Entity> combatants) {
        CombatRuntimeComponent runtime;
        try {
            runtime = CombatController.ensureRuntime(attacker, contentCatalog);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return null;
        }
        ContentCatalog.WeaponDefinition weapon = contentCatalog.findWeapon(runtime.weaponId);
        if (weapon == null) {
            return null;
        }
        TransformComponent attackerTransform = transformMapper.get(attacker);
        FactionComponent attackerFaction = factionMapper.get(attacker);
        float rangeSquared = weapon.range() * weapon.range();
        Entity best = null;
        float bestDistanceSquared = Float.POSITIVE_INFINITY;
        long bestId = Long.MAX_VALUE;

        for (Entity candidate : combatants) {
            if (candidate == attacker) {
                continue;
            }
            CombatComponent candidateCombat = combatMapper.get(candidate);
            FactionComponent candidateFaction = factionMapper.get(candidate);
            TransformComponent candidateTransform = transformMapper.get(candidate);
            EntityIdComponent candidateId = idMapper.get(candidate);
            if (candidateCombat == null || candidateCombat.hull <= 0f
                    || candidateFaction == null || candidateFaction.factionId == attackerFaction.factionId
                    || candidateTransform == null || candidateId == null) {
                continue;
            }
            float distanceSquared = attackerTransform.position.dst2(candidateTransform.position);
            if (!Float.isFinite(distanceSquared) || distanceSquared > rangeSquared) {
                continue;
            }
            long idValue = candidateId.id.value();
            int distanceComparison = Float.compare(distanceSquared, bestDistanceSquared);
            if (distanceComparison < 0 || (distanceComparison == 0 && idValue < bestId)) {
                best = candidate;
                bestDistanceSquared = distanceSquared;
                bestId = idValue;
            }
        }
        return best;
    }
}
