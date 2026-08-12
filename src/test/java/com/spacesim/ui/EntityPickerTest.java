package com.spacesim.ui;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.TransformComponent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class EntityPickerTest {
    private final WorldMapLayout layout = new WorldMapLayout(0f, 0f, 2000f, 1400f, 0f);

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
    void приРавномРасстоянииИспользуетПриоритетФлотАстероидСтанция() {
        Entity station = entity("Станция", IdentityComponent.Kind.STATION, 100f, 100f);
        Entity asteroid = entity("Астероид", IdentityComponent.Kind.ASTEROID, 100f, 100f);
        Entity fleet = entity("Флот", IdentityComponent.Kind.FLEET, 100f, 100f);

        assertSame(
                asteroid,
                EntityPicker.pick(List.of(station, asteroid), layout, 100f, 100f, 20f));
        assertSame(
                asteroid,
                EntityPicker.pick(List.of(asteroid, station), layout, 100f, 100f, 20f));
        assertSame(
                fleet,
                EntityPicker.pick(List.of(station, fleet, asteroid), layout, 100f, 100f, 20f));
        assertSame(
                fleet,
                EntityPicker.pick(List.of(fleet, asteroid, station), layout, 100f, 100f, 20f));
    }

    @Test
    void игнорируетСущностиБезОбязательныхКомпонентовИНекорректныеПозиции() {
        Entity withoutIdentity = new Entity().add(transform(100f, 100f));
        Entity withoutTransform = new Entity().add(
                new IdentityComponent("Без координат", IdentityComponent.Kind.STATION));
        Entity nanPosition = entity("NaN", IdentityComponent.Kind.FLEET, Float.NaN, 100f);
        Entity outsideWorld = entity("За границей", IdentityComponent.Kind.STATION, 2001f, 100f);
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
        Entity station = entity("Станция", IdentityComponent.Kind.STATION, 0f, 700f);
        float leftEdge = letterboxed.getMapX();

        assertNull(EntityPicker.pick(List.of(station), letterboxed, leftEdge - 1f, 300f, 20f));
        assertNull(EntityPicker.pick(List.of(station), letterboxed, leftEdge, 300f, 0f));
        assertNull(EntityPicker.pick(List.of(station), letterboxed, leftEdge, 300f, Float.NaN));
        assertNull(EntityPicker.pick(null, letterboxed, leftEdge, 300f, 20f));
        assertNull(EntityPicker.pick(List.of(station), null, leftEdge, 300f, 20f));
    }

    @Test
    void включаетГраницуРадиусаВыбора() {
        Entity station = entity("На границе", IdentityComponent.Kind.STATION, 120f, 100f);

        assertSame(station, EntityPicker.pick(List.of(station), layout, 100f, 100f, 20f));
    }

    @Test
    void послеУвеличенияВыбираетПоНовомуПреобразованиюИИгнорируетОбъектЗаОбзором() {
        WorldMapLayout zoomed = new WorldMapLayout(
                0f,
                0f,
                1000f,
                700f,
                0f,
                1000f,
                700f,
                2f);
        Entity visible = entity("В обзоре", IdentityComponent.Kind.ASTEROID, 1250f, 700f);
        Entity justOutside = entity("За обзором", IdentityComponent.Kind.STATION, 1500.01f, 700f);

        assertSame(
                visible,
                EntityPicker.pick(List.of(visible), zoomed, 750f, 350f, 24f));
        assertNull(EntityPicker.pick(List.of(justOutside), zoomed, 1000f, 350f, 24f));
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
