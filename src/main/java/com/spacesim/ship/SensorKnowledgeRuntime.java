package com.spacesim.ship;

import com.spacesim.components.SensorKnowledgeComponent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Shared Stage-17.5D information runtime used identically by player and AI observers.
 *
 * <p>The runtime moves measurements, never truth-state. A datalink transmission becomes available
 * only after explicit latency. Track quality is then recomputed from the actually received
 * measurement history, covariance and freshness through {@link ShipSensorRuntime}; sharing does not
 * grant exact range, classification or fire control by faction membership alone.</p>
 */
public final class SensorKnowledgeRuntime {
    private final ShipSensorRuntime sensorRuntime;

    /** Creates the common knowledge runtime around the physical sensor/fusion solver. */
    public SensorKnowledgeRuntime() {
        this(new ShipSensorRuntime());
    }

    /**
     * Creates a runtime around an explicit solver for focused deterministic tests.
     *
     * @param sensorRuntime physical measurement/fusion solver
     */
    public SensorKnowledgeRuntime(ShipSensorRuntime sensorRuntime) {
        this.sensorRuntime = Objects.requireNonNull(sensorRuntime, "sensorRuntime");
    }

    /**
     * Delivers one local measurement immediately to the observing node.
     *
     * @param knowledge observer/network knowledge state
     * @param measurement physical local measurement
     */
    public void receiveLocal(SensorKnowledgeComponent knowledge, SensorMeasurement measurement) {
        Objects.requireNonNull(knowledge, "knowledge").receiveMeasurement(
                Objects.requireNonNull(measurement, "measurement"));
    }

    /**
     * Sends a measurement across a real datalink with explicit delivery latency.
     *
     * @param recipient recipient knowledge node
     * @param measurement already obtained physical measurement
     * @param link datalink transport policy
     * @param transmitAtSeconds authoritative send time, not before measurement creation
     */
    public void transmit(
            SensorKnowledgeComponent recipient,
            SensorMeasurement measurement,
            DatalinkState link,
            double transmitAtSeconds) {
        SensorKnowledgeComponent target = Objects.requireNonNull(recipient, "recipient");
        SensorMeasurement value = Objects.requireNonNull(measurement, "measurement");
        DatalinkState transport = Objects.requireNonNull(link, "link");
        if (!Double.isFinite(transmitAtSeconds) || transmitAtSeconds < value.timestampSeconds()) {
            throw new IllegalArgumentException("transmitAtSeconds must be finite and not precede measurement time");
        }
        if (transmitAtSeconds - value.timestampSeconds() > transport.maxMeasurementAgeSeconds()) {
            return;
        }
        target.queueMeasurement(value, transmitAtSeconds + transport.latencySeconds());
    }

    /**
     * Advances one knowledge node and recomputes a target track from delivered measurements.
     *
     * <p>The supplied {@code networkProfile} is the physical transport/covariance profile of this
     * sharing domain. Measurements still in the pending queue are invisible to fusion. If no fresh
     * measurement exists, an existing track is only aged; no new target knowledge is created.</p>
     *
     * @param knowledge observer/network knowledge state
     * @param targetId system-local target EntityId value
     * @param networkProfile datalink/fusion transport profile
     * @param qualityPolicy covariance/freshness quality policy
     * @param nowSeconds authoritative current time
     * @return current target track when knowledge exists
     */
    public Optional<TrackState> updateTarget(
            SensorKnowledgeComponent knowledge,
            long targetId,
            DatalinkState networkProfile,
            ShipSensorRuntime.TrackQualityPolicy qualityPolicy,
            double nowSeconds) {
        if (targetId <= 0L) {
            throw new IllegalArgumentException("targetId must be positive");
        }
        SensorKnowledgeComponent state = Objects.requireNonNull(knowledge, "knowledge");
        DatalinkState link = Objects.requireNonNull(networkProfile, "networkProfile");
        ShipSensorRuntime.TrackQualityPolicy policy = Objects.requireNonNull(qualityPolicy, "qualityPolicy");
        if (!Double.isFinite(nowSeconds)) {
            throw new IllegalArgumentException("nowSeconds must be finite");
        }

        state.deliverDue(nowSeconds);
        state.pruneMeasurements(nowSeconds, link.maxMeasurementAgeSeconds());
        List<SensorMeasurement> measurements = state.receivedMeasurements().stream()
                .filter(value -> value.targetId() == targetId)
                .toList();
        if (!measurements.isEmpty()) {
            TrackState fused = sensorRuntime.fuse(targetId, measurements, link, policy, nowSeconds);
            state.putTrack(fused);
            return Optional.of(fused);
        }
        return state.tracks().stream()
                .filter(value -> value.targetId() == targetId)
                .findFirst()
                .map(value -> {
                    TrackState aged = sensorRuntime.ageTrack(value, nowSeconds, policy);
                    state.putTrack(aged);
                    return aged;
                });
    }
}
