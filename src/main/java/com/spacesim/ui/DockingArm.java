package com.spacesim.ui;

import java.util.Objects;

/**
 * The boarding arm between the player's ship and the station it is docked at, as the system map
 * shows it. It exists exactly while the player's active ship is docked; the station's docking
 * ports are presentation metadata of its sprite.
 *
 * @param shipStableId system-map id of the docked ship ({@code fleet:<id>})
 * @param stationStableId system-map id of the station ({@code station:<id>})
 */
public record DockingArm(String shipStableId, String stationStableId) {
    /**
     * Validates one arm.
     *
     * @param shipStableId system-map id of the docked ship
     * @param stationStableId system-map id of the station
     */
    public DockingArm {
        Objects.requireNonNull(shipStableId, "shipStableId");
        Objects.requireNonNull(stationStableId, "stationStableId");
    }
}
