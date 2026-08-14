package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.PlayerControlledComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.flight.FlightDynamics;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FleetId;

import java.util.Objects;
import java.util.Optional;

/**
 * Read-only adapter exposing shared Stage-14E mass/thrust diagnostics for the active player ship.
 *
 * <p>The service never writes Transform or inventory state. It resolves the same current entity that
 * {@link PlayerRuntime} controls and delegates mass/thrust calculation to {@link FlightDynamics}.</p>
 */
public final class PlayerFlightService {
    private final PlayerRuntime runtime;

    /**
     * Creates a diagnostics adapter around one playable runtime.
     *
     * @param runtime authoritative playable runtime
     */
    public PlayerFlightService(PlayerRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "PlayerRuntime not set");
    }

    /**
     * Captures the current active-ship physical flight view.
     *
     * @return diagnostics, or empty while the active fleet is not locally materialized
     */
    public Optional<PlayerFlightView> view() {
        PlayerShipView shipView = runtime.activeShipView().orElse(null);
        FleetId fleetId = runtime.player().activeFleetId();
        if (shipView == null || fleetId == null) {
            return Optional.empty();
        }
        SimulationSession session = runtime.world().findSession(shipView.systemId()).orElse(null);
        Entity entity = session == null ? null : session.getEntityRegistry().find(shipView.localEntityId());
        TransformComponent transform = entity == null ? null : entity.getComponent(TransformComponent.class);
        if (entity == null || transform == null) {
            return Optional.empty();
        }
        float speedCap = speedCap(entity);
        if (!(Float.isFinite(speedCap) && speedCap > 0f)) {
            return Optional.empty();
        }
        FlightDynamics.Profile profile = FlightDynamics.profile(entity, speedCap);
        float speed = transform.velocity.len();
        float stopSeconds = speed / profile.brakingAcceleration();
        float stopDistance = speed * speed / (2f * profile.brakingAcceleration());
        return Optional.of(new PlayerFlightView(
                speed,
                profile.speedCap(),
                profile.dryMass(),
                profile.cargoMass(),
                profile.totalMass(),
                profile.acceleration(),
                profile.brakingAcceleration(),
                stopSeconds,
                stopDistance));
    }

    private static float speedCap(Entity entity) {
        PlayerControlledComponent control = entity.getComponent(PlayerControlledComponent.class);
        if (control != null && Float.isFinite(control.movementSpeed) && control.movementSpeed > 0f) {
            return control.movementSpeed;
        }
        TradeAIComponent trade = entity.getComponent(TradeAIComponent.class);
        if (trade != null && Float.isFinite(trade.movementSpeed) && trade.movementSpeed > 0f) {
            return trade.movementSpeed;
        }
        MiningComponent mining = entity.getComponent(MiningComponent.class);
        if (mining != null && Float.isFinite(mining.movementSpeed) && mining.movementSpeed > 0f) {
            return mining.movementSpeed;
        }
        return 0f;
    }
}
