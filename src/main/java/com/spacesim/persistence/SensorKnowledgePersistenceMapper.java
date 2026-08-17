package com.spacesim.persistence;

import com.spacesim.components.SensorKnowledgeComponent;
import com.spacesim.ship.SensorMeasurement;
import com.spacesim.ship.SignatureState.Channel;
import com.spacesim.ship.TrackCovariance;
import com.spacesim.ship.TrackState;

import java.util.List;
import java.util.Objects;

/** Value-only mapper for system-local Stage-17.5D/H sensor knowledge. */
final class SensorKnowledgePersistenceMapper {
    private SensorKnowledgePersistenceMapper() {
        throw new AssertionError("Utility class");
    }

    static EntityState.SensorKnowledgeState capture(SensorKnowledgeComponent component) {
        if (component == null) {
            return null;
        }
        return new EntityState.SensorKnowledgeState(
                component.tracks().stream().map(SensorKnowledgePersistenceMapper::captureTrack).toList(),
                component.receivedMeasurements().stream()
                        .map(SensorKnowledgePersistenceMapper::captureMeasurement).toList(),
                component.pendingMeasurements().stream()
                        .map(value -> new EntityState.PendingSensorMeasurementState(
                                captureMeasurement(value.measurement()), value.deliverAtSeconds()))
                        .toList());
    }

    static SensorKnowledgeComponent restore(EntityState.SensorKnowledgeState state) {
        EntityState.SensorKnowledgeState checked = Objects.requireNonNull(state, "sensorKnowledge");
        SensorKnowledgeComponent component = new SensorKnowledgeComponent();
        requireList(checked.tracks(), "tracks").stream()
                .map(SensorKnowledgePersistenceMapper::restoreTrack)
                .forEach(component::putTrack);
        requireList(checked.receivedMeasurements(), "receivedMeasurements").stream()
                .map(SensorKnowledgePersistenceMapper::restoreMeasurement)
                .forEach(component::receiveMeasurement);
        for (EntityState.PendingSensorMeasurementState pending
                : requireList(checked.pendingMeasurements(), "pendingMeasurements")) {
            component.queueMeasurement(
                    restoreMeasurement(Objects.requireNonNull(pending.measurement(), "pending measurement")),
                    pending.deliverAtSeconds());
        }
        return component;
    }

    private static EntityState.SensorTrackState captureTrack(TrackState track) {
        TrackCovariance covariance = track.covariance();
        return new EntityState.SensorTrackState(
                track.targetId(), track.informationState().name(), track.positionKnown(),
                track.estimatedXM(), track.estimatedYM(), covariance.positionVarianceM2(),
                covariance.bearingVarianceRad2(), covariance.rangeVarianceM2(),
                track.classificationConfidence(), track.lastMeasurementSeconds(),
                track.contributingObservers(), track.fusedMeasurementCount());
    }

    private static TrackState restoreTrack(EntityState.SensorTrackState state) {
        EntityState.SensorTrackState checked = Objects.requireNonNull(state, "track");
        return new TrackState(
                checked.targetId(),
                enumValue(TrackState.InformationState.class, checked.informationStateName(), "informationState"),
                checked.positionKnown(), checked.estimatedXM(), checked.estimatedYM(),
                new TrackCovariance(
                        checked.positionVarianceM2(), checked.bearingVarianceRad2(), checked.rangeVarianceM2()),
                checked.classificationConfidence(), checked.lastMeasurementSeconds(),
                checked.contributingObservers(), checked.fusedMeasurementCount());
    }

    private static EntityState.SensorMeasurementState captureMeasurement(SensorMeasurement value) {
        return new EntityState.SensorMeasurementState(
                value.observerId(), value.targetId(), value.channel().name(), value.timestampSeconds(),
                value.observerXM(), value.observerYM(), value.bearingRad(), value.rangeM(),
                value.bearingVarianceRad2(), value.rangeVarianceM2(), value.receivedSignalPowerW(),
                value.effectiveInterferencePowerW(), value.snr(), value.evidenceState().name());
    }

    private static SensorMeasurement restoreMeasurement(EntityState.SensorMeasurementState state) {
        EntityState.SensorMeasurementState checked = Objects.requireNonNull(state, "measurement");
        return new SensorMeasurement(
                checked.observerId(), checked.targetId(),
                enumValue(Channel.class, checked.channelName(), "channel"),
                checked.timestampSeconds(), checked.observerXM(), checked.observerYM(),
                checked.bearingRad(), checked.rangeM(), checked.bearingVarianceRad2(),
                checked.rangeVarianceM2(), checked.receivedSignalPowerW(),
                checked.effectiveInterferencePowerW(), checked.snr(),
                enumValue(TrackState.InformationState.class, checked.evidenceStateName(), "evidenceState"));
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown " + label + ": " + value, exception);
        }
    }

    private static <T> List<T> requireList(List<T> values, String label) {
        Objects.requireNonNull(values, label);
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(label + " contains null");
        }
        return values;
    }
}
