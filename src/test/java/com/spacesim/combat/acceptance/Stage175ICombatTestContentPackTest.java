package com.spacesim.combat.acceptance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage175ICombatTestContentPackTest {
    @Test
    void compositePackLoadsAllProductionCatalogsAndFingerprintsDeterministically() {
        Stage175ICombatTestContentPack first = Stage175ICombatTestContentPack.loadDefault();
        Stage175ICombatTestContentPack second = Stage175ICombatTestContentPack.loadDefault();

        assertNotNull(first.engineering());
        assertNotNull(first.ammunition());
        assertNotNull(first.launchers());
        assertNotNull(first.protection());
        assertNotNull(first.manifest());
        assertEquals(first.fingerprint(), second.fingerprint());
        assertEquals(64, first.fingerprint().length());
        assertFalse(first.fingerprint().isBlank());
        assertEquals("PRODUCTION_VALID_CONTENT_PROVISIONAL", first.manifest().contentStatus());
        assertTrue(first.manifest().stage22ReviewRequired());

        first.manifest().fleets().forEach(fleet -> fleet.ships().forEach(row -> {
            var fit = first.engineering().findDemonstratorFit(row.fitId());
            assertNotNull(fit, row.fitId());
            assertNotNull(first.protection().findHullDamageLayout(fit.hullId()), fit.hullId());
        }));
    }
}
