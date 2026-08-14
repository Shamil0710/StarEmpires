package com.spacesim.flight;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FlightCommandComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.PlayerControlledComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.model.ShipType;
import com.spacesim.systems.AutonomousFlightSystem;
import com.spacesim.systems.PlayerDirectControlSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage14FlightDynamicsAcceptanceTest {
    @Test
    void loadedFreighterAcceleratesAndBrakesMoreSlowlyThanEmptyFreighter() {
        Entity empty = playerShip(ShipType.MATERIAL_CARRIER, 140, 0, 150f);
        Entity loaded = playerShip(ShipType.MATERIAL_CARRIER, 140, 120, 150f);

        Engine emptyEngine = new Engine();
        emptyEngine.addSystem(new PlayerDirectControlSystem());
        emptyEngine.addEntity(empty);
        Engine loadedEngine = new Engine();
        loadedEngine.addSystem(new PlayerDirectControlSystem());
        loadedEngine.addEntity(loaded);

        empty.getComponent(PlayerControlledComponent.class).setIntent(1f, 0f);
        loaded.getComponent(PlayerControlledComponent.class).setIntent(1f, 0f);
        emptyEngine.update(1f);
        loadedEngine.update(1f);

        float emptySpeed = empty.getComponent(TransformComponent.class).velocity.len();
        float loadedSpeed = loaded.getComponent(TransformComponent.class).velocity.len();
        assertTrue(emptySpeed > loadedSpeed);

        FlightDynamics.Profile emptyProfile = FlightDynamics.profile(empty, 150f);
        FlightDynamics.Profile loadedProfile = FlightDynamics.profile(loaded, 150f);
        assertEquals(0f, emptyProfile.cargoMass(), 0f);
        assertEquals(120f, loadedProfile.cargoMass(), 0f);
        assertTrue(loadedProfile.totalMass() > emptyProfile.totalMass());
        assertTrue(loadedProfile.acceleration() < emptyProfile.acceleration());
        assertTrue(loadedProfile.brakingAcceleration() < emptyProfile.brakingAcceleration());
    }

    @Test
    void releasingInputBrakesOverTimeInsteadOfInstantlyStopping() {
        Entity ship = playerShip(ShipType.MATERIAL_CARRIER, 140, 80, 150f);
        Engine engine = new Engine();
        engine.addSystem(new PlayerDirectControlSystem());
        engine.addEntity(ship);
        PlayerControlledComponent control = ship.getComponent(PlayerControlledComponent.class);
        TransformComponent transform = ship.getComponent(TransformComponent.class);

        control.setIntent(1f, 0f);
        engine.update(1f);
        float beforeBrake = transform.velocity.len();
        control.stop();
        engine.update(0.1f);
        float afterOneBrakeTick = transform.velocity.len();

        assertTrue(beforeBrake > 0f);
        assertTrue(afterOneBrakeTick > 0f);
        assertTrue(afterOneBrakeTick < beforeBrake);

        for (int index = 0; index < 100; index++) {
            engine.update(0.1f);
        }
        assertEquals(0f, transform.velocity.len(), 0.0001f);
    }

    @Test
    void playerAndAutonomousIntentUseIdenticalPhysicalLimits() {
        Entity player = playerShip(ShipType.COMBAT_SHIP, 0, 0, 135f);
        Entity autonomous = autonomousShip(ShipType.COMBAT_SHIP, 0, 0, 135f);

        Engine playerEngine = new Engine();
        playerEngine.addSystem(new PlayerDirectControlSystem());
        playerEngine.addEntity(player);
        Engine autonomousEngine = new Engine();
        autonomousEngine.addSystem(new AutonomousFlightSystem());
        autonomousEngine.addEntity(autonomous);

        player.getComponent(PlayerControlledComponent.class).setIntent(0.6f, 0.8f);
        autonomous.getComponent(FlightCommandComponent.class).set(0.6f, 0.8f, 135f);
        for (int tick = 0; tick < 25; tick++) {
            playerEngine.update(0.1f);
            autonomousEngine.update(0.1f);
        }

        TransformComponent playerTransform = player.getComponent(TransformComponent.class);
        TransformComponent autonomousTransform = autonomous.getComponent(TransformComponent.class);
        assertEquals(playerTransform.velocity.x, autonomousTransform.velocity.x, 0.0001f);
        assertEquals(playerTransform.velocity.y, autonomousTransform.velocity.y, 0.0001f);
        assertEquals(playerTransform.position.x, autonomousTransform.position.x, 0.0001f);
        assertEquals(playerTransform.position.y, autonomousTransform.position.y, 0.0001f);
    }

    @Test
    void lightCombatShipRespondsFasterThanLoadedMaterialCarrier() {
        Entity fighterLike = playerShip(ShipType.COMBAT_SHIP, 0, 0, 150f);
        Entity loadedCarrier = playerShip(ShipType.MATERIAL_CARRIER, 140, 140, 150f);
        FlightDynamics.Profile light = FlightDynamics.profile(fighterLike, 150f);
        FlightDynamics.Profile heavy = FlightDynamics.profile(loadedCarrier, 150f);

        assertTrue(light.totalMass() < heavy.totalMass());
        assertTrue(light.acceleration() > heavy.acceleration());
        assertTrue(light.brakingAcceleration() > heavy.brakingAcceleration());
    }

    private static Entity playerShip(
            ShipType type, int capacity, int cargoUnits, float speedCap) {
        InventoryComponent inventory = inventory(capacity, cargoUnits);
        PlayerControlledComponent control = new PlayerControlledComponent();
        control.movementSpeed = speedCap;
        return new Entity()
                .add(new ShipComponent(type))
                .add(inventory)
                .add(new TransformComponent())
                .add(control);
    }

    private static Entity autonomousShip(
            ShipType type, int capacity, int cargoUnits, float speedCap) {
        FlightCommandComponent command = new FlightCommandComponent();
        command.set(0f, 0f, speedCap);
        return new Entity()
                .add(new ShipComponent(type))
                .add(inventory(capacity, cargoUnits))
                .add(new TransformComponent())
                .add(command);
    }

    private static InventoryComponent inventory(int capacity, int cargoUnits) {
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = capacity;
        if (cargoUnits > 0) {
            inventory.stock[0] = cargoUnits;
        }
        return inventory;
    }
}
