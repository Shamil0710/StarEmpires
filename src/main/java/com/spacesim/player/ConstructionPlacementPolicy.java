package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.constants.Constants;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.LocalSystemCoordinates;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldSimulation;

import java.util.Objects;

/**
 * Authoritative physical/strategic placement policy for Stage-16 player construction.
 *
 * <p>Clearance values are centralized baseline gameplay parameters rather than UI sprite sizes.
 * Future collision/size metadata may replace these constants without changing callers. Fleets are
 * intentionally not permanent blockers; fixed stations/sites, finite resource objects and the
 * canonical jump-arrival area are.</p>
 */
public final class ConstructionPlacementPolicy {
    /** Minimum distance from local-system map edges. */
    public static final float EDGE_MARGIN = 30f;
    /** Minimum center distance from an existing station or construction site. */
    public static final float STATION_CLEARANCE = 50f;
    /** Minimum center distance from a finite asteroid/resource object. */
    public static final float RESOURCE_CLEARANCE = 40f;
    /** Protected radius around the canonical jump-arrival anchor. */
    public static final float JUMP_ARRIVAL_CLEARANCE = 90f;

    private ConstructionPlacementPolicy() {
        throw new AssertionError("ConstructionPlacementPolicy does not create instances");
    }

    /**
     * Evaluates one local construction location using authoritative world state.
     *
     * @param world authoritative world runtime
     * @param player current player state
     * @param systemId target local system
     * @param x requested local X
     * @param y requested local Y
     * @return immutable placement result suitable for UI preview and command validation
     */
    public static PlayerConstructionPlacementView evaluate(
            WorldSimulation world,
            PlayerState player,
            StarSystemId systemId,
            float x,
            float y) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "WorldSimulation not set");
        PlayerState checkedPlayer = Objects.requireNonNull(player, "PlayerState not set");
        StarSystemId targetSystem = Objects.requireNonNull(systemId, "Construction system not set");
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            return rejected(targetSystem, x, y, ConstructionPlacementRejection.NON_FINITE_COORDINATES);
        }
        if (x < EDGE_MARGIN || y < EDGE_MARGIN
                || x > Constants.WORLD_WIDTH - EDGE_MARGIN
                || y > Constants.WORLD_HEIGHT - EDGE_MARGIN) {
            return rejected(targetSystem, x, y, ConstructionPlacementRejection.OUTSIDE_LOCAL_BOUNDS);
        }
        if (distanceSquared(x, y, LocalSystemCoordinates.ARRIVAL_X, LocalSystemCoordinates.ARRIVAL_Y)
                < JUMP_ARRIVAL_CLEARANCE * JUMP_ARRIVAL_CLEARANCE) {
            return rejected(targetSystem, x, y, ConstructionPlacementRejection.JUMP_ARRIVAL_EXCLUSION);
        }
        if (!ConstructionAccessPolicy.allows(checkedWorld, checkedPlayer, targetSystem)) {
            return rejected(targetSystem, x, y, ConstructionPlacementRejection.TERRITORY_ACCESS_DENIED);
        }

        SimulationSession session = checkedWorld.findSession(targetSystem).orElse(null);
        if (session == null) {
            throw new IllegalArgumentException("Unknown construction StarSystem: " + targetSystem);
        }
        for (Entity entity : session.getEngine().getEntities()) {
            IdentityComponent identity = entity.getComponent(IdentityComponent.class);
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            if (identity == null || transform == null) {
                continue;
            }
            float requiredClearance;
            ConstructionPlacementRejection rejection;
            if (identity.kind == IdentityComponent.Kind.STATION) {
                requiredClearance = STATION_CLEARANCE;
                rejection = ConstructionPlacementRejection.STATION_CLEARANCE;
            } else if (identity.kind == IdentityComponent.Kind.ASTEROID) {
                requiredClearance = RESOURCE_CLEARANCE;
                rejection = ConstructionPlacementRejection.RESOURCE_CLEARANCE;
            } else {
                continue;
            }
            if (distanceSquared(x, y, transform.position.x, transform.position.y)
                    < requiredClearance * requiredClearance) {
                return rejected(targetSystem, x, y, rejection);
            }
        }
        return new PlayerConstructionPlacementView(
                targetSystem, x, y, true, ConstructionPlacementRejection.NONE);
    }

    private static PlayerConstructionPlacementView rejected(
            StarSystemId systemId,
            float x,
            float y,
            ConstructionPlacementRejection reason) {
        return new PlayerConstructionPlacementView(systemId, x, y, false, reason);
    }

    private static float distanceSquared(float ax, float ay, float bx, float by) {
        float dx = ax - bx;
        float dy = ay - by;
        return dx * dx + dy * dy;
    }
}
