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
 * <p>Tracks and in-flight datalink deliveries reference system-local EntityIds through the long
 * target/observer identity values used by Stage 17.5D. They therefore persist across save/load of
 * the same local system, but must be discarded when a fleet is detached into another star system.
 * This component never contains truth positions that were not produced by sensor measurements.</p>
 */
public final class SensorKnowledgeComponent implements Component {
    private static final Comparator<TrackState> TRACK_ORDER = Comparator.comparingLong(TrackState::targetId);
    private static final Comparator<PendingMeasurement> PENDING_ORDER = Comparator
            .comparingDouble(PendingMeasurement::deliverAtSeconds)
            .thenComparingLong(value -> value.measurement().targetId())
            .thenComparingLong(value -> value.measurement().observerId())
            .thenComparingDouble(value -> value.measurement().timestampSeconds());

    private final List<TrackState> tracks = new ArrayList<>();
    private final List<PendingMeasurement> pendingMeasurements = new ArrayList<>();

    /** @return immutable deterministic target-ID-ordered track snapshot */
    public List<TrackState> tracks() {
        List<TrackState> copy = new ArrayList<>(tracks);
        copy.sort(TRACK_ORDER);
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
     * Queues a measurement already transmitted over a datalink.
     *
     * @param measurement measurement payload
     * @param deliverAtSeconds authoritative delivery time after explicit link latency
     */
    public void queueMeasurement(SensorMeasurement measurement, double deliverAtSeconds) {
        pendingMeasurements.add(new PendingMeasurement(measurement, deliverAtSeconds));
    }

    /**
     * Removes and returns all measurements whose physical delivery time has arrived.
     *
     * @param nowSeconds authoritative current time
     * @return immutable deterministic delivered measurements
     */
    public List<SensorMeasurement> drainDelivered(double nowSeconds) {
        if (!Double.isFinite(nowSeconds)) {
            throw new IllegalArgumentException("nowSeconds must be finite");
        }
        List<PendingMeasurement> delivered = pendingMeasurements.stream()
                .filter(value -> value.deliverAtSeconds() <= nowSeconds)
                .sorted(PENDING_ORDER)
                .toList();
        pendingMeasurements.removeAll(delivered);
        return delivered.stream().map(PendingMeasurement::measurement).toList();
    }

    /** Clears system-local knowledge when the owning fleet leaves the star system. */
    public void clearLocalKnowledge() {
        tracks.clear();
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
