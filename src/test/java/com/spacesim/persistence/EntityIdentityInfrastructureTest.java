package com.spacesim.persistence;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityIdentityInfrastructureTest {
    @Test
    void entityIdТребуетПоложительноеЗначениеИДаётСтабильноеПредставление() {
        EntityId id = new EntityId(42L);

        assertEquals(42L, id.value());
        assertEquals("entity:42", id.toString());
        assertTrue(id.compareTo(new EntityId(43L)) < 0);
        assertEquals(0, id.compareTo(new EntityId(42L)));
        assertThrows(IllegalArgumentException.class, () -> new EntityId(0L));
        assertThrows(IllegalArgumentException.class, () -> new EntityId(-1L));
    }

    @Test
    void idКомпонентНеПринимаетNull() {
        EntityId id = new EntityId(7L);
        EntityIdComponent component = new EntityIdComponent(id);

        assertSame(id, component.id);
        assertThrows(NullPointerException.class, () -> new EntityIdComponent(null));
    }

    @Test
    void allocatorДетерминированИВосстанавливаетсяИзСледующегоЗначения() {
        EntityIdAllocator allocator = new EntityIdAllocator();

        assertEquals(new EntityId(1L), allocator.allocate());
        assertEquals(new EntityId(2L), allocator.allocate());
        assertEquals(3L, allocator.getNextValue());

        EntityIdAllocator restored = new EntityIdAllocator(allocator.getNextValue());
        assertEquals(new EntityId(3L), restored.allocate());
        assertEquals(4L, restored.getNextValue());
        assertThrows(IllegalArgumentException.class, () -> new EntityIdAllocator(0L));
        assertThrows(IllegalArgumentException.class, () -> new EntityIdAllocator(-5L));
    }

    @Test
    void allocatorЯвноОстанавливаетсяПослеПоследнегоLongId() {
        EntityIdAllocator allocator = new EntityIdAllocator(Long.MAX_VALUE);

        assertEquals(new EntityId(Long.MAX_VALUE), allocator.allocate());
        assertEquals(0L, allocator.getNextValue());
        assertThrows(IllegalStateException.class, allocator::allocate);
    }

    @Test
    void registryРазрешаетIdИПовторнаяРегистрацияТойЖеEntityИдемпотентна() {
        EntityRegistry registry = new EntityRegistry();
        EntityId id = new EntityId(11L);
        Entity entity = entity(id);

        assertEquals(id, registry.register(entity));
        assertEquals(id, registry.register(entity));
        assertEquals(1, registry.size());
        assertTrue(registry.contains(id));
        assertSame(entity, registry.find(id));
        assertSame(entity, registry.require(id));
        assertNull(registry.find(null));
        assertFalse(registry.contains(null));
    }

    @Test
    void registryОтклоняетОтсутствующийИлиДублирующийId() {
        EntityRegistry registry = new EntityRegistry();
        Entity first = entity(new EntityId(5L));
        Entity duplicate = entity(new EntityId(5L));

        registry.register(first);

        assertThrows(IllegalStateException.class, () -> registry.register(duplicate));
        assertThrows(IllegalArgumentException.class, () -> registry.register(new Entity()));
        assertThrows(NullPointerException.class, () -> registry.register(null));
        assertThrows(NullPointerException.class, () -> registry.require(null));
        assertThrows(IllegalStateException.class, () -> registry.require(new EntityId(99L)));
        assertSame(first, registry.find(new EntityId(5L)));
    }

    @Test
    void unregisterУдаляетТолькоТочноеRuntimeСопоставлениеИClearОчищаетRegistry() {
        EntityRegistry registry = new EntityRegistry();
        Entity first = entity(new EntityId(1L));
        Entity second = entity(new EntityId(2L));
        registry.register(first);
        registry.register(second);

        Entity impostor = entity(new EntityId(1L));
        assertFalse(registry.unregister(impostor));
        assertFalse(registry.unregister(null));
        assertTrue(registry.unregister(first));
        assertFalse(registry.contains(new EntityId(1L)));
        assertFalse(registry.unregister(first));
        assertEquals(1, registry.size());

        registry.clear();
        assertEquals(0, registry.size());
        assertNull(registry.find(new EntityId(2L)));
    }

    private Entity entity(EntityId id) {
        return new Entity().add(new EntityIdComponent(id));
    }
}
