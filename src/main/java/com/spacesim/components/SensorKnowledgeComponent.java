package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.spacesim.ship.SensorMeasurement;
import com.spacesim.ship.TrackState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Local-system authoritative sensor knowledge owned by one observing entity.
 *
 * <p>Tracks, received measurements and in-flight datalink deliveries reference system-local
 * EntityIds through the long target/observer identity values used by Stage 17.5D. The component is
 * an explicit snapshotable runtime boundary for later Stage-17.5H save integration; it must be
 * cleared when its owning fleet enters another star system because local target IDs do not survive
 * that topology transition.</p>
 */
public final class SensorKnowledgeComponent implements Component {
    private static final Comparator<TrackState> TRACK_ORDER = Comparator.comparingLong(TrackState::targetId);
    private static final Comparator<SensorMeasurement> MEASUREMENT_ORDER = Comparator
            .comparingLong(SensorMeasurement::targetId)
            .thenComparingDouble(SensorMeasurement::timestampSeconds)
            .thenComparingLong(SensorMeasurement::observerId)
            .thenComparing(value -> value.channel().name())
            .thenComparingDouble(SensorMeasurement::bearingRad);
    private static final Comparator<PendingMeasurement> PENDING_ORDER = Comparator
            .comparingDouble(PendingMeasurement::deliverAtSeconds)
            .thenComparing(value -> value.measurement(), MEASUREMENT_ORDER);

    private final List<TrackState> tracks = new ArrayList<>();
    private final List<SensorMeasurement> receivedMeasurements = new ArrayList<>();
    private final List<PendingMeasurement> pendingMeasurements = new ArrayList<>();

    /** @return immutable deterministic target-ID-ordered track snapshot */
    public List<TrackState> tracks() {
        List<TrackState> copy = new ArrayList<>(tracks);
        copy.sort(TRACK_ORDER);
        return List.copyOf(copy);
    }

    /** @return immutable deterministic measurement-history snapshot */
    public List<SensorMeasurement> receivedMeasurements() {
        List<SensorMeasurement> copy = new ArrayList<>(receivedMeasurements);
        copy.sort(MEASUREMENT_ORDER);
        return List.copyOf(copy);
    }

    /** @return immutable deterministic delivery-ordered datalink queue snapshot */
    public List<PendingMeasurement> pendingMeasurements() {
        List<PendingMeasurement> copy = new ArrayList<>(pendingMeasurements);
        copy.sort(PENDING_ORDER);
        return List.copyOf(copy);
    }

    /**
     * Inserts or replaces one target track without manufacturing additional knowledge.
     *
     * @param track authoritative fused track
     */
    public void putTrack(TrackState track) {
        TrackState value = Objects.requireNonNull(track, "track");
        tracks.removeIf(existing -> existing.targetId() == value.targetId());
        tracks.add(value);
    }

    /**
     * Stores one measurement that has physically reached this observer/network node.
     *
     * @param measurement delivered local or datalink measurement
     */
    public void receiveMeasurement(SensorMeasurement measurement) {
        SensorMeasurement value = Objects.requireNonNull(measurement, "measurement");
        if (!receivedMeasurements.contains(value)) {
            receivedMeasurements.add(value);
        }
    }

    /**
     * Queues a measurement already transmitted over a datalink.
     *
     * @param measurement measurement payload
     * @param deliverAtSeconds authoritative delivery time after explicit link latency
     */
    public void queueMeasurement(SensorMeasurement measurement, double deliverAtSeconds) {
        pendingMeasurements.add(new PendingMeasurement(measurement, deliverAtSeconds));
    }

    /**
     * Moves all due datalink measurements into received history and returns them.
     *
     * @param nowSeconds authoritative current time
     * @return immutable deterministic delivered measurements
     */
    public List<SensorMeasurement> deliverDue(double nowSeconds) {
        if (!Double.isFinite(nowSeconds)) {
            throw new IllegalArgumentException("nowSeconds must be finite");
        }
        List<PendingMeasurement> delivered = pendingMeasurements.stream()
                .filter(value -> value.deliverAtSeconds() <= nowSeconds)
                .sorted(PENDING_ORDER)
                .toList();
        pendingMeasurements.removeAll(delivered);
        List<SensorMeasurement> result = delivered.stream().map(PendingMeasurement::measurement).toList();
        result.forEach(this::receiveMeasurement);
        return result;
    }

    /**
     * Removes measurements older than a link/fusion freshness horizon.
     *
     * @param nowSeconds authoritative current time
     * @param maxAgeSeconds maximum retained measurement age
     */
    public void pruneMeasurements(double nowSeconds, double maxAgeSeconds) {
        if (!Double.isFinite(nowSeconds) || !Double.isFinite(maxAgeSeconds) || maxAgeSeconds <= 0d) {
            throw new IllegalArgumentException("measurement retention inputs must be finite and maxAgeSeconds positive");
        }
        receivedMeasurements.removeIf(value -> nowSeconds - value.timestampSeconds() > maxAgeSeconds);
    }

    /** Clears all system-local information when the owner changes star-system identity domain. */
    public void clearLocalKnowledge() {
        tracks.clear();
        receivedMeasurements.clear();
        pendingMeasurements.clear();
    }

    /**
     * One physically transmitted measurement awaiting delivery.
     *
     * @param measurement transmitted measurement
     * @param deliverAtSeconds authoritative delivery time
     */
    public record PendingMeasurement(SensorMeasurement measurement, double deliverAtSeconds) {
        /**
         * Validates delivery time against measurement creation time.
         *
         * @param measurement transmitted measurement
         * @param deliverAtSeconds authoritative delivery time
         */
        public PendingMeasurement {
            Objects.requireNonNull(measurement, "measurement");
            if (!Double.isFinite(deliverAtSeconds) || deliverAtSeconds < measurement.timestampSeconds()) {
                throw new IllegalArgumentException("deliverAtSeconds must be finite and not precede measurement time");
            }
        }
    }
}
