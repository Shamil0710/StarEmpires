package com.spacesim.ui;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.TransformComponent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class EntityPickerTest {
    private final WorldMapLayout layout = new WorldMapLayout(0f, 0f, 700f, 550f, 0f);

    @Test
    void выбираетБлижайшуюСущностьВЭкранномРадиусе() {
        Entity farStation = entity("Дальняя", IdentityComponent.Kind.STATION, 140f, 100f);
        Entity nearestStation = entity("Ближняя", IdentityComponent.Kind.STATION, 106f, 103f);

        Entity selected = EntityPicker.pick(
                List.of(farStation, nearestStation), layout, 100f, 100f, 50f);

        assertSame(nearestStation, selected);
        assertNull(EntityPicker.pick(List.of(farStation), layout, 100f, 100f, 20f));
    }

    @Test
    void приРавномРасстоянииПредпочитаетФлотНезависимоОтПорядка() {
        Entity station = entity("Станция", IdentityComponent.Kind.STATION, 90f, 100f);
        Entity fleet = entity("Флот", IdentityComponent.Kind.FLEET, 110f, 100f);

        assertSame(fleet, EntityPicker.pick(List.of(station, fleet), layout, 100f, 100f, 20f));
        assertSame(fleet, EntityPicker.pick(List.of(fleet, station), layout, 100f, 100f, 20f));
    }

    @Test
    void игнорируетСущностиБезОбязательныхКомпонентовИНекорректныеПозиции() {
        Entity withoutIdentity = new Entity().add(transform(100f, 100f));
        Entity withoutTransform = new Entity().add(
                new IdentityComponent("Без координат", IdentityComponent.Kind.STATION));
        Entity nanPosition = entity("NaN", IdentityComponent.Kind.FLEET, Float.NaN, 100f);
        Entity outsideWorld = entity("За границей", IdentityComponent.Kind.STATION, 701f, 100f);
        Entity valid = entity("Корректная", IdentityComponent.Kind.STATION, 102f, 100f);

        Entity selected = EntityPicker.pick(
                List.of(withoutIdentity, withoutTransform, nanPosition, outsideWorld, valid),
                layout,
                100f,
                100f,
                20f);

        assertSame(valid, selected);
    }

    @Test
    void отклоняетКликВПолеКартыИНекорректныйРадиус() {
        WorldMapLayout letterboxed = new WorldMapLayout(0f, 0f, 1000f, 600f, 25f);
        Entity station = entity("Станция", IdentityComponent.Kind.STATION, 0f, 275f);

        assertNull(EntityPicker.pick(List.of(station), letterboxed, 140f, 300f, 20f));
        assertNull(EntityPicker.pick(List.of(station), letterboxed, 150f, 300f, 0f));
        assertNull(EntityPicker.pick(List.of(station), letterboxed, 150f, 300f, Float.NaN));
        assertNull(EntityPicker.pick(null, letterboxed, 150f, 300f, 20f));
        assertNull(EntityPicker.pick(List.of(station), null, 150f, 300f, 20f));
    }

    @Test
    void включаетГраницуРадиусаВыбора() {
        Entity station = entity("На границе", IdentityComponent.Kind.STATION, 120f, 100f);

        assertSame(station, EntityPicker.pick(List.of(station), layout, 100f, 100f, 20f));
    }

    private static Entity entity(
            String name,
            IdentityComponent.Kind kind,
            float x,
            float y) {
        return new Entity()
                .add(new IdentityComponent(name, kind))
                .add(transform(x, y));
    }

    private static TransformComponent transform(float x, float y) {
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        return transform;
    }
}
