package com.spacesim.ui;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.CombatComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.persistence.EntityId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalMinimapModelTest {
    @Test
    void classifiesPlayerStationsFriendlyAndHostileFleetsDeterministically() {
        Entity hostile = entity(4, "Raider", IdentityComponent.Kind.FLEET, 2, 40f, 50f);
        hostile.add(new CombatComponent());
        Entity player = entity(2, "Player", IdentityComponent.Kind.FLEET, 1, 20f, 30f);
        Entity station = entity(1, "Port", IdentityComponent.Kind.STATION, 1, 10f, 15f);
        Entity friendly = entity(3, "Escort", IdentityComponent.Kind.FLEET, 1, 25f, 35f);

        LocalMinimapSnapshot snapshot = LocalMinimapModel.capture(
                List.of(hostile, player, friendly, station), player);

        assertEquals(List.of(
                        LocalMinimapSnapshot.Kind.STATION,
                        LocalMinimapSnapshot.Kind.PLAYER,
                        LocalMinimapSnapshot.Kind.FRIENDLY_FLEET,
                        LocalMinimapSnapshot.Kind.HOSTILE_FLEET),
                snapshot.markers().stream().map(LocalMinimapSnapshot.Marker::kind).toList());
        assertEquals(List.of(1L, 2L, 3L, 4L),
                snapshot.markers().stream().map(marker -> marker.entityId().value()).toList());
    }

    private static Entity entity(
            long id,
            String name,
            IdentityComponent.Kind kind,
            int faction,
            float x,
            float y) {
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        return new Entity()
                .add(new EntityIdComponent(new EntityId(id)))
                .add(new IdentityComponent(name, kind))
                .add(new FactionComponent(faction))
                .add(transform);
    }
}
