package com.spacesim.content.ship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class Stage175ICombatTestProtectionPackTest {
    @Test
    void loadsDoctrineHullProtectionAndLocatesEveryPhysicalMount() {
        ShipProtectionCatalog protection = Stage175ICombatTestProtectionPack.load();
        var layout = protection.findHullDamageLayout("hull.test_doctrine_destroyer_v1");

        assertNotNull(layout);
        assertEquals(3, layout.compartments().size());
        assertEquals(10, layout.mounts().size());
        assertNotNull(protection.findHeavyImpactModel("response.stage17_5i_doctrine_v1"));
    }
}
