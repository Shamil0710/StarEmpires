package com.spacesim.world;

import com.spacesim.economy.Stage18ManufacturingRuntime.IntervalBudget;
import com.spacesim.ship.*;
import java.util.Objects;
import java.util.Optional;

/** Paid infrastructure radio packets decoded through the existing physical sensor model. */
public final class Stage23StationBroadcastRuntime {
    private final ShipSensorRuntime sensors = new ShipSensorRuntime();

    /**
     * Electrical and radiated power of one authored transmitter.
     * @param electricalDemandW actual electrical draw
     * @param radiatedPowerW actual radio emission, bounded by electrical input
     */
    public record Transmitter(double electricalDemandW, double radiatedPowerW) {
        /**
         * Preserves transmitter energy balance.
         * @param electricalDemandW input electrical power
         * @param radiatedPowerW outgoing radio power
         */
        public Transmitter {
            if (!Double.isFinite(electricalDemandW) || !Double.isFinite(radiatedPowerW)
                    || electricalDemandW <= 0 || radiatedPowerW <= 0 || radiatedPowerW > electricalDemandW)
                throw new IllegalArgumentException("Invalid physical station transmitter");
        }
    }

    /** Opaque packet; only successful payment can create its authority. */
    public static final class Transmission {
        private final Stage20DiscoveryKnowledgeState.StaticObjectRef station;
        private final String classification;
        private final LocalPhysicalPosition position;
        private final long emitter;
        private final double emittedPowerW, startedAtSeconds, durationSeconds;
        private Transmission(Stage20DiscoveryKnowledgeState.StaticObjectRef station, String classification,
                LocalPhysicalPosition position, long emitter, double emittedPowerW, double startedAtSeconds, double durationSeconds) {
            this.station = station; this.classification = classification; this.position = position;
            this.emitter = emitter; this.emittedPowerW = emittedPowerW;
            this.startedAtSeconds = startedAtSeconds; this.durationSeconds = durationSeconds;
        }
    }

    /**
     * Pays one transmission from an actual shared interval before exposing a packet.
     * Caller admission must bind the supplied identity, transmitter, cooling and budget to its real station.
     * @param station exact infrastructure identity in the payload
     * @param classification authored station archetype
     * @param position static location advertised by the actual source
     * @param emitterId actual source entity identity
     * @param transmitter authored installed transmitter
     * @param budget same real facility interval used by other electrical services
     * @param availableWasteHeatRejectionW actual source cooling available to this transmitter
     * @param startedAtSeconds beginning of the paid actual interval
     * @return opaque paid packet, or no packet when energy/cooling are unavailable
     */
    public Optional<Transmission> emit(Stage20DiscoveryKnowledgeState.StaticObjectRef station, String classification,
            LocalPhysicalPosition position, long emitterId, Transmitter transmitter, IntervalBudget budget,
            double availableWasteHeatRejectionW, double startedAtSeconds) {
        Objects.requireNonNull(station); Objects.requireNonNull(position); Objects.requireNonNull(transmitter); Objects.requireNonNull(budget);
        if (station.kind() != Stage20DiscoveryKnowledgeState.StaticObjectKind.INFRASTRUCTURE
                || classification == null || classification.isBlank() || classification.length() > 512 || emitterId <= 0
                || !Double.isFinite(startedAtSeconds) || startedAtSeconds < 0 || !Double.isFinite(startedAtSeconds + budget.durationSeconds())
                || !Double.isFinite(availableWasteHeatRejectionW) || availableWasteHeatRejectionW < 0)
            throw new IllegalArgumentException("Invalid station broadcast source or interval");
        double demand = transmitter.electricalDemandW() * budget.durationSeconds();
        if (!Double.isFinite(demand)) throw new IllegalArgumentException("Station transmission energy overflows");
        if (availableWasteHeatRejectionW < transmitter.electricalDemandW() - transmitter.radiatedPowerW()
                || !budget.reserveElectricalEnergyJ(demand)) return Optional.empty();
        return Optional.of(new Transmission(station, classification, position, emitterId, transmitter.radiatedPowerW(),
                startedAtSeconds, budget.durationSeconds()));
    }

    /**
     * Decodes a paid packet only through a current physical passive-radio measurement.
     * Payload coordinates are advertised data, never a range solution inferred from a bearing-only measurement.
     * @param packet opaque paid transmission
     * @param observerId actual receiving entity identity
     * @param observerPosition actual receiver SI position
     * @param sensor definition of its real fitted radio sensor
     * @param state actual current sensor availability
     * @param receiverRelativeInterference actual EW emitters expressed in the receiver's SI coordinate frame
     * @param observedAtSeconds actual time within the paid interval
     * @return decoded static observation, or none without sufficient physical reception
     */
    public Optional<Stage20DiscoveryKnowledgeRuntime.StaticObservation> receive(Transmission packet, long observerId,
            LocalPhysicalPosition observerPosition, SensorDefinition sensor, SensorRuntimeState state,
            ElectronicWarfareState receiverRelativeInterference, double observedAtSeconds) {
        Objects.requireNonNull(packet); Objects.requireNonNull(observerPosition); Objects.requireNonNull(sensor);
        Objects.requireNonNull(state); Objects.requireNonNull(receiverRelativeInterference);
        if (!Double.isFinite(observedAtSeconds) || observedAtSeconds < packet.startedAtSeconds
                || observedAtSeconds > packet.startedAtSeconds + packet.durationSeconds)
            throw new IllegalArgumentException("Packet reception requires its actual paid interval");
        if (sensor.mode() != SensorDefinition.Mode.PASSIVE_RADIO) return Optional.empty();
        var displacement = observerPosition.displacementTo(packet.position);
        var measurement = sensors.observe(observerId, packet.emitter, sensor, state, new ShipSensorRuntime.Position2d(0, 0),
                new ShipSensorRuntime.Position2d(displacement.deltaXM(), displacement.deltaYM()),
                SignatureState.zero().withActiveRadioEmissions(packet.emittedPowerW, 0), receiverRelativeInterference, observedAtSeconds).measurement();
        if (measurement.isEmpty() || measurement.orElseThrow().snr() < sensor.classificationSnr()) return Optional.empty();
        return Optional.of(new Stage20DiscoveryKnowledgeRuntime.StaticObservation(packet.station,
                Stage20DiscoveryKnowledgeState.DiscoveryState.KNOWN_STATIC_LOCATION, Optional.of(packet.classification),
                Optional.of(packet.position), Stage20DiscoveryKnowledgeState.ResourceKnowledge.none(),
                new Stage20DiscoveryKnowledgeState.DiscoveryEvidence(
                        Stage20DiscoveryKnowledgeState.DiscoverySource.PERSISTENT_INFRASTRUCTURE_BROADCAST,
                        "station-radio:" + packet.emitter + ":receiver:" + observerId, observedAtSeconds, java.util.OptionalDouble.empty())));
    }
}
