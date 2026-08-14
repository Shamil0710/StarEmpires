package com.spacesim.ui;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.IdentityComponent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayableMapEntityFilterTest {
    @Test
    void distantZoomKeepsNavigationObjectsAndSelectedShip() {
        Entity station = new Entity().add(new IdentityComponent("Station", IdentityComponent.Kind.STATION));
        Entity fleet = new Entity().add(new IdentityComponent("Fleet", IdentityComponent.Kind.FLEET));
        Entity asteroid = new Entity().add(new IdentityComponent("Asteroid", IdentityComponent.Kind.ASTEROID));
        Entity selected = new Entity().add(new IdentityComponent("Selected asteroid", IdentityComponent.Kind.ASTEROID));

        List<Entity> filtered = PlayableMapEntityFilter.filter(
                List.of(asteroid, station, selected, fleet), selected, 1.2f);

        assertEquals(3, filtered.size());
        assertTrue(filtered.contains(station));
        assertTrue(filtered.contains(fleet));
        assertTrue(filtered.contains(selected));
    }

    @Test
    void closeZoomRestoresFullLocalDetail() {
        Entity asteroid = new Entity().add(new IdentityComponent("Asteroid", IdentityComponent.Kind.ASTEROID));
        Entity salvage = new Entity().add(new IdentityComponent("Salvage", IdentityComponent.Kind.SALVAGE));

        assertEquals(2, PlayableMapEntityFilter.filter(
                List.of(asteroid, salvage), null, PlayableMapEntityFilter.FULL_DETAIL_ZOOM).size());
    }
}
