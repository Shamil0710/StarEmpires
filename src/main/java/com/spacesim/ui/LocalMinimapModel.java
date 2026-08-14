package com.spacesim.ui;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.CombatComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.TransformComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure read model that classifies local-system entities for the compact Stage-14C minimap.
 *
 * <p>Classification intentionally mirrors the simple Stage-13 combat seam: a combat-capable fleet
 * of a different faction is treated as hostile for presentation. This is not a permanent diplomacy
 * model; Stage 18 will replace the temporary rule with authoritative relations/ROE.</p>
 */
public final class LocalMinimapModel {
    private LocalMinimapModel() {
        throw new AssertionError("LocalMinimapModel does not create instances");
    }

    /**
     * Captures a deterministic read-only marker list from current local ECS state.
     *
     * @param entities current local-system entities
     * @param playerEntity currently controlled entity, or {@code null}
     * @return deterministic minimap snapshot
     */
    public static LocalMinimapSnapshot capture(Iterable<Entity> entities, Entity playerEntity) {
        if (entities == null) {
            return new LocalMinimapSnapshot(List.of());
        }
        FactionComponent playerFaction = playerEntity == null
                ? null : playerEntity.getComponent(FactionComponent.class);
        List<LocalMinimapSnapshot.Marker> markers = new ArrayList<>();
        for (Entity entity : entities) {
            if (entity == null) {
                continue;
            }
            EntityIdComponent id = entity.getComponent(EntityIdComponent.class);
            IdentityComponent identity = entity.getComponent(IdentityComponent.class);
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            if (id == null || identity == null || transform == null) {
                continue;
            }
            LocalMinimapSnapshot.Kind kind = classify(entity, playerEntity, playerFaction, identity);
            markers.add(new LocalMinimapSnapshot.Marker(
                    id.id,
                    kind,
                    identity.name,
                    transform.position.x,
                    transform.position.y));
        }
        markers.sort(Comparator.comparing(LocalMinimapSnapshot.Marker::entityId));
        return new LocalMinimapSnapshot(markers);
    }

    private static LocalMinimapSnapshot.Kind classify(
            Entity entity,
            Entity playerEntity,
            FactionComponent playerFaction,
            IdentityComponent identity) {
        if (entity == playerEntity) {
            return LocalMinimapSnapshot.Kind.PLAYER;
        }
        return switch (identity.kind) {
            case STATION -> LocalMinimapSnapshot.Kind.STATION;
            case ASTEROID -> LocalMinimapSnapshot.Kind.ASTEROID;
            case SALVAGE -> LocalMinimapSnapshot.Kind.SALVAGE;
            case FLEET -> classifyFleet(entity, playerFaction);
        };
    }

    private static LocalMinimapSnapshot.Kind classifyFleet(Entity entity, FactionComponent playerFaction) {
        FactionComponent faction = entity.getComponent(FactionComponent.class);
        if (playerFaction != null && faction != null && faction.factionId == playerFaction.factionId) {
            return LocalMinimapSnapshot.Kind.FRIENDLY_FLEET;
        }
        if (playerFaction != null
                && faction != null
                && faction.factionId != playerFaction.factionId
                && entity.getComponent(CombatComponent.class) != null) {
            return LocalMinimapSnapshot.Kind.HOSTILE_FLEET;
        }
        return LocalMinimapSnapshot.Kind.OTHER_FLEET;
    }
}
