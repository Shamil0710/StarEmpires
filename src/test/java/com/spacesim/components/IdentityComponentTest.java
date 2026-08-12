package com.spacesim.components;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentityComponentTest {
    @Test
    void имяОчищаетсяАТипСохраняется() {
        IdentityComponent identity = new IdentityComponent("  Торговый узел  ", IdentityComponent.Kind.STATION);

        assertEquals("Торговый узел", identity.name);
        assertEquals(IdentityComponent.Kind.STATION, identity.kind);
    }

    @Test
    void пустоеИмяОтклоняется() {
        assertThrows(IllegalArgumentException.class,
                () -> new IdentityComponent(null, IdentityComponent.Kind.FLEET));
        assertThrows(IllegalArgumentException.class,
                () -> new IdentityComponent("", IdentityComponent.Kind.FLEET));
        assertThrows(IllegalArgumentException.class,
                () -> new IdentityComponent(" \t\n", IdentityComponent.Kind.FLEET));
    }

    @Test
    void отсутствующийТипОтклоняется() {
        assertThrows(NullPointerException.class, () -> new IdentityComponent("Курьер", null));
    }
}
