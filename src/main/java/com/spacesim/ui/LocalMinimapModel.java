package com.spacesim.ui;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.CombatComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.persistence.EntityId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure read model that classifies local-system entities for the compact Stage-14C minimap.
 *
 * <p>Player ownership takes precedence over the temporary Stage-13 faction-hostility rule because
 * Stage 12 deliberately keeps ownership independent from legal/faction identity. For unowned
 * fleets, a combat-capable fleet of a different faction is treated as hostile for presentation.
 * Stage 18 will replace that temporary fallback with authoritative diplomacy/ROE.</p>
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
     * @param ownedLocalEntityIds persistent local IDs of all player-owned fleets materialized here
     * @return deterministic minimap snapshot
     */
    public static LocalMinimapSnapshot capture(
            Iterable<Entity> entities,
            Entity playerEntity,
            Set<EntityId> ownedLocalEntityIds) {
        Set<EntityId> owned = Set.copyOf(Objects.requireNonNull(
                ownedLocalEntityIds, "Owned local EntityIds not set"));
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
            LocalMinimapSnapshot.Kind kind = classify(
                    entity, playerEntity, playerFaction, identity, id.id, owned);
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
            IdentityComponent identity,
            EntityId entityId,
            Set<EntityId> owned) {
        if (entity == playerEntity) {
            return LocalMinimapSnapshot.Kind.PLAYER;
        }
        return switch (identity.kind) {
            case STATION -> LocalMinimapSnapshot.Kind.STATION;
            case ASTEROID -> LocalMinimapSnapshot.Kind.ASTEROID;
            case SALVAGE -> LocalMinimapSnapshot.Kind.SALVAGE;
            case FLEET -> classifyFleet(entity, playerFaction, entityId, owned);
        };
    }

    private static LocalMinimapSnapshot.Kind classifyFleet(
            Entity entity,
            FactionComponent playerFaction,
            EntityId entityId,
            Set<EntityId> owned) {
        if (owned.contains(entityId)) {
            return LocalMinimapSnapshot.Kind.FRIENDLY_FLEET;
        }
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
