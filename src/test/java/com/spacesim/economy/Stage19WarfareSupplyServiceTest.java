package com.spacesim.economy;

import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.ship.AmmunitionRuntime;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.WeaponDefinition.KineticRound;
import com.spacesim.ship.WeaponDefinition.Launcher;
import com.spacesim.ship.WeaponDefinition.ProjectileShape;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage19WarfareSupplyServiceTest {
    private static final String AMMO_ID = "ammo.rail_dart_150kg_v1";
    private static final String MOUNT_ID = "weapon_primary";
    private static final Launcher LAUNCHER = new Launcher("launcher.test", "ammo_feed", 1d, 1d, 1);
    private static final InterfaceDefinition FEED = new InterfaceDefinition(InterfaceKind.AMMUNITION, "ammo_feed", 10d);

    @Test
    void manufacturedRoundsBecomeTheSamePhysicalFeedConsumedByCombatRuntime() {
        Stage18StationStorage storage = storageWithRounds(4);
        Stage19WarfareSupplyService service = new Stage19WarfareSupplyService(
                Stage18ManufacturingProductRegistry.loadDefault());

        Stage19WarfareSupplyService.AmmunitionLoadResult loaded = service.loadAmmunition(
                AMMO_ID,
                MOUNT_ID,
                3,
                LAUNCHER,
                FEED,
                ConsumableState.empty(),
                storage);

        assertTrue(loaded.committed());
        assertEquals(1, storage.productCount(AMMO_ID));
        assertEquals(3L, loaded.consumables().ammunitionCount());
        assertEquals(450d, loaded.loadedMassKg(), 1e-9d);
        assertEquals(450d, loaded.consumables().interfaceLoadMassKg(), 1e-9d);

        AmmunitionRuntime.ConsumptionResult fired = new AmmunitionRuntime().consumeOne(
                loaded.consumables(),
                MOUNT_ID,
                LAUNCHER,
                new KineticRound(
                        AMMO_ID,
                        "material.test",
                        ProjectileShape.DART,
                        1d,
                        0.1d,
                        150d,
                        10_000d).massKg());

        assertEquals(2L, fired.consumables().ammunitionCount());
        assertEquals(300d, fired.consumables().interfaceLoadMassKg(), 1e-9d);
        assertEquals(150d, fired.consumedMassKg(), 1e-9d);
    }

    @Test
    void insufficientFinishedRoundsCannotCreateFreeAmmunition() {
        Stage18StationStorage storage = storageWithRounds(1);
        Stage19WarfareSupplyService service = new Stage19WarfareSupplyService(
                Stage18ManufacturingProductRegistry.loadDefault());
        ConsumableState before = ConsumableState.empty();

        Stage19WarfareSupplyService.AmmunitionLoadResult result = service.loadAmmunition(
                AMMO_ID,
                MOUNT_ID,
                2,
                LAUNCHER,
                FEED,
                before,
                storage);

        assertFalse(result.committed());
        assertEquals(Stage19WarfareSupplyService.Status.INSUFFICIENT_STOCK, result.status());
        assertEquals(1, storage.productCount(AMMO_ID));
        assertSame(before, result.consumables());
        assertEquals(0L, result.consumables().ammunitionCount());
    }

    @Test
    void physicalFeedCapacityPreventsOverfillWithoutMutatingStorage() {
        Stage18StationStorage storage = storageWithRounds(4);
        Stage19WarfareSupplyService service = new Stage19WarfareSupplyService(
                Stage18ManufacturingProductRegistry.loadDefault());
        InterfaceDefinition oneRoundFeed = new InterfaceDefinition(InterfaceKind.AMMUNITION, "ammo_feed", 1d);

        Stage19WarfareSupplyService.AmmunitionLoadResult result = service.loadAmmunition(
                AMMO_ID,
                MOUNT_ID,
                2,
                LAUNCHER,
                oneRoundFeed,
                ConsumableState.empty(),
                storage);

        assertEquals(Stage19WarfareSupplyService.Status.INTERFACE_CAPACITY_EXCEEDED, result.status());
        assertEquals(4, storage.productCount(AMMO_ID));
        assertEquals(0L, result.consumables().ammunitionCount());
    }

    private static Stage18StationStorage storageWithRounds(int rounds) {
        return new Stage18StationStorage(
                Stage18ResourceOntologyLoader.loadDefault(),
                Stage18ManufacturingProductRegistry.loadDefault(),
                "station.stage19f.test",
                Map.of("storage.hazardous_controlled", 100_000d),
                Map.of(),
                rounds == 0 ? Map.of() : Map.of(AMMO_ID, rounds));
    }
}
