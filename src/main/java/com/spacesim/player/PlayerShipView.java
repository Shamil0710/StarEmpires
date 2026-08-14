package com.spacesim.player;

import com.spacesim.persistence.EntityId;
import com.spacesim.world.FleetId;
import com.spacesim.world.StarSystemId;

/**
 * Read-only presentation snapshot for camera follow, selection and player HUD.
 *
 * @param fleetId stable active fleet ID
 * @param systemId current local system
 * @param localEntityId current system-local persistent EntityId
 * @param x physical world X coordinate
 * @param y physical world Y coordinate
 * @param velocityX current physical X velocity
 * @param velocityY current physical Y velocity
 * @param docked whether player state currently records a docking target
 */
public record PlayerShipView(
        FleetId fleetId,
        StarSystemId systemId,
        EntityId localEntityId,
        float x,
        float y,
        float velocityX,
        float velocityY,
        boolean docked) {
}
