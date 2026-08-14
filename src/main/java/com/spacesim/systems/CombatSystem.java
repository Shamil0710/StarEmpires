package com.spacesim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.spacesim.combat.CombatController;
import com.spacesim.components.CombatCommandComponent;
import com.spacesim.components.CombatComponent;
import com.spacesim.components.CombatRuntimeComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fixed-tick executor for shared player/AI combat commands.
 *
 * <p>The system advances weapon cooldown, resolves persistent target identity and delegates every
 * accepted shot to {@link CombatController}. It never removes entities during Ashley iteration.
 * Lethal shots are emitted as destruction requests for a world-layer resolver, preserving the
 * Stage-9 destruction/economic accounting boundary.</p>
 */
public final class CombatSystem extends IteratingSystem {
    private static final Family COMBATANTS = Family.all(
            CombatComponent.class,
            TransformComponent.class,
            EntityIdComponent.class).get();

    private final ComponentMapper<CombatRuntimeComponent> runtimeMapper =
            ComponentMapper.getFor(CombatRuntimeComponent.class);
    private final ComponentMapper<CombatCommandComponent> commandMapper =
            ComponentMapper.getFor(CombatCommandComponent.class);
    private final ComponentMapper<EntityIdComponent> idMapper =
            ComponentMapper.getFor(EntityIdComponent.class);
    private final ContentCatalog contentCatalog;
    private final EntityRegistry registry;
    private final List<DestructionRequest> destructionRequests = new ArrayList<>();

    /**
     * Creates the authoritative fixed-tick combat executor.
     *
     * @param contentCatalog data-driven weapon catalog
     * @param registry persistent local entity registry
     */
    public CombatSystem(ContentCatalog contentCatalog, EntityRegistry registry) {
        super(COMBATANTS);
        this.contentCatalog = Objects.requireNonNull(contentCatalog, "ContentCatalog not set");
        this.registry = Objects.requireNonNull(registry, "EntityRegistry not set");
    }

    /** Advances cooldown and attempts the current shared combat command. */
    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        if (!Float.isFinite(deltaTime) || deltaTime < 0f) {
            throw new IllegalArgumentException("Combat delta must be finite and non-negative");
        }
        CombatRuntimeComponent runtime = runtimeMapper.get(entity);
        if (runtime == null) {
            runtime = CombatController.ensureRuntime(entity, contentCatalog);
        }
        runtime.advanceCooldown(deltaTime);

        CombatCommandComponent command = commandMapper.get(entity);
        if (command == null || !command.fireRequested || command.targetId == null) {
            return;
        }
        Entity target = registry.find(command.targetId);
        CombatController.FireResult result = CombatController.tryFire(entity, target, contentCatalog);
        if (!result.targetDestroyed()) {
            return;
        }
        EntityIdComponent attackerId = idMapper.get(entity);
        EntityIdComponent targetId = target == null ? null : idMapper.get(target);
        if (attackerId != null && targetId != null) {
            destructionRequests.add(new DestructionRequest(targetId.id, attackerId.id));
        }
    }

    /**
     * Returns lethal-shot requests accumulated since the previous drain and clears the queue.
     *
     * @return immutable requests in deterministic execution order
     */
    public List<DestructionRequest> drainDestructionRequests() {
        if (destructionRequests.isEmpty()) {
            return List.of();
        }
        List<DestructionRequest> result = List.copyOf(destructionRequests);
        destructionRequests.clear();
        return result;
    }

    /**
     * Lethal combat event awaiting ordinary world destruction processing.
     *
     * @param victimId persistent entity killed by the shot
     * @param attackerId persistent entity that fired the lethal shot
     */
    public record DestructionRequest(EntityId victimId, EntityId attackerId) {
        /** Validates stable identities carried across the system/world boundary. */
        public DestructionRequest {
            Objects.requireNonNull(victimId, "Combat victimId not set");
            Objects.requireNonNull(attackerId, "Combat attackerId not set");
        }
    }
}
