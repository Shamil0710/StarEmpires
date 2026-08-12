package com.spacesim.persistence;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.TradeAIComponent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PersistentComponentReferenceTest {
    @Test
    void торговыйКомпонентХранитМаршрутТолькоЧерезEntityId() throws NoSuchFieldException {
        assertEquals(EntityId.class, TradeAIComponent.class.getField("buyStationId").getType());
        assertEquals(EntityId.class, TradeAIComponent.class.getField("sellStationId").getType());
        assertEquals(EntityId.class, TradeAIComponent.class.getField("targetStationId").getType());
        assertContainsNoAshleyEntityFields(TradeAIComponent.class);
    }

    @Test
    void добывающийКомпонентХранитЦелиТолькоЧерезEntityId() throws NoSuchFieldException {
        assertEquals(EntityId.class, MiningComponent.class.getField("targetAsteroidId").getType());
        assertEquals(EntityId.class, MiningComponent.class.getField("homeBaseId").getType());
        assertContainsNoAshleyEntityFields(MiningComponent.class);
    }

    private void assertContainsNoAshleyEntityFields(Class<?> componentType) {
        for (Field field : componentType.getDeclaredFields()) {
            assertFalse(
                    Entity.class.isAssignableFrom(field.getType()),
                    () -> componentType.getSimpleName() + "." + field.getName()
                            + " не должен хранить runtime Ashley Entity");
        }
    }
}
