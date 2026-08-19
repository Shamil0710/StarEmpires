package com.spacesim.ui;

import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;

import java.util.Objects;

/**
 * Read-only mapping from authored acceptance fit/doctrine identity to schematic viewer role.
 *
 * <p>The mapping changes presentation only. Doctrine IDs continue to select ordinary physical fits
 * and stores and do not gain any renderer-driven combat meaning.</p>
 */
public final class ShipVisualClassifier {
    private ShipVisualClassifier() {
    }

    /**
     * Classifies one authored doctrine for schematic rendering.
     *
     * @param doctrineId accepted Stage-17.5 doctrine/fit identity
     * @return corresponding presentation-only visual role
     */
    public static ShipVisualRole classify(DoctrineId doctrineId) {
        return switch (Objects.requireNonNull(doctrineId, "doctrineId")) {
            case A_KINETIC_LINE -> ShipVisualRole.KINETIC;
            case B_MISSILE_STRIKE -> ShipVisualRole.MISSILE;
            case C_HIGH_MOBILITY_BEAM -> ShipVisualRole.BEAM;
            case D_DEFENSIVE_EW -> ShipVisualRole.DEFENSIVE_EW;
            case E_BALANCED_CONTROL -> ShipVisualRole.BALANCED;
        };
    }
}
