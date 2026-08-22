package com.spacesim.world;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Actor-bounded Stage-21A observation snapshot consumed by autonomous reasoning.
 *
 * <p>The snapshot deliberately contains only reports already available to the actor. It has no
 * reference to {@link WorldSimulation}, generated physical truth, or any hidden registry. Upstream
 * adapters are responsible for projecting Stage-17/20 knowledge into these rows through allowed
 * channels.</p>
 *
 * @param factionContentId stable observing faction identity
 * @param observedAtTick authoritative clock tick represented by the snapshot
 * @param economic actor-known economic observations
 * @param territorial actor-known territorial observations
 * @param security actor-known security observations
 * @param diplomatic actor-known diplomatic observations
 */
public record FactionActorObservationSnapshot(
        String factionContentId,
        long observedAtTick,
        List<ActorObservation> economic,
        List<ActorObservation> territorial,
        List<ActorObservation> security,
        List<ActorObservation> diplomatic) {

    /** Observation domains kept separate so a hidden domain cannot leak through a generic truth bag. */
    public enum Domain {
        /** Markets, production, stocks and actor-known supply flows. */ ECONOMIC,
        /** Claims, borders, control and actor-known territorial opportunities. */ TERRITORIAL,
        /** Routes, border contacts and actor-known military/security reports. */ SECURITY,
        /** Treaties, access commitments and actor-known diplomatic reports. */ DIPLOMATIC
    }

    /** Stage-21A measurable interest families. */
    public enum InterestKind {
        /** Reliance on an external supplier or supply path. */ SUPPLY_DEPENDENCY,
        /** Need to preserve or obtain lawful market access. */ MARKET_ACCESS,
        /** Exposure of a materially important route. */ ROUTE_EXPOSURE,
        /** Actor-known shortage of a strategically relevant resource. */ RESOURCE_DEFICIT,
        /** Risk or pressure affecting an actor-known border. */ BORDER_SECURITY,
        /** Actor-known opportunity to improve territorial position. */ TERRITORIAL_OPPORTUNITY,
        /** Persisted treaty duty that materially constrains action. */ TREATY_OBLIGATION
    }

    /** Allowed provenance channels for autonomous observations. */
    public enum ObservationChannel {
        /** Local Stage-17.5 sensor or track report. */ LOCAL_SENSOR_REPORT,
        /** Persistent Stage-20 discovery knowledge owned by this actor. */ DISCOVERY_KNOWLEDGE,
        /** Delayed/shared intelligence report already delivered to this actor. */ INTELLIGENCE_REPORT,
        /** Actor-visible market, stock, production or freight ledger. */ ECONOMIC_LEDGER,
        /** Actor-visible claim/control/border registry. */ TERRITORY_LEDGER,
        /** Persisted treaty or diplomatic registry visible to the participant. */ DIPLOMATIC_REGISTRY,
        /** Report emitted by an owned fleet, station or operation. */ OWNED_ASSET_REPORT
    }

    /**
     * Provenance and freshness of one actor-known fact.
     *
     * @param channel allowed observation channel
     * @param provenanceId stable scan/report/ledger row identity
     * @param observedAtTick tick when the actor received the fact
     * @param freshUntilTick inclusive freshness horizon, or {@code -1} for durable evidence
     */
    public record ObservationEvidence(
            ObservationChannel channel,
            String provenanceId,
            long observedAtTick,
            long freshUntilTick) implements Comparable<ObservationEvidence> {

        /** Validates explicit provenance without inventing a source-dependent freshness policy. */
        public ObservationEvidence {
            Objects.requireNonNull(channel, "Observation channel not set");
            provenanceId = requireText(provenanceId, "Observation provenance ID");
            requireNonNegative(observedAtTick, "Observation tick");
            if (freshUntilTick < -1L) {
                throw new IllegalArgumentException("Freshness horizon cannot be less than -1");
            }
            if (freshUntilTick >= 0L && freshUntilTick < observedAtTick) {
                throw new IllegalArgumentException("Freshness horizon cannot precede observation");
            }
        }

        /**
         * Checks whether this evidence remains current at a review tick.
         *
         * @param nowTick authoritative review tick
         * @return {@code true} when evidence is durable or inside its freshness horizon
         */
        public boolean currentAt(long nowTick) {
            requireNonNegative(nowTick, "Current tick");
            if (nowTick < observedAtTick) {
                return false;
            }
            return freshUntilTick < 0L || nowTick <= freshUntilTick;
        }

        @Override
        public int compareTo(ObservationEvidence other) {
            Objects.requireNonNull(other, "other");
            int observed = Long.compare(observedAtTick, other.observedAtTick);
            if (observed != 0) {
                return observed;
            }
            int channelOrder = channel.compareTo(other.channel);
            if (channelOrder != 0) {
                return channelOrder;
            }
            int provenance = provenanceId.compareTo(other.provenanceId);
            return provenance != 0 ? provenance : Long.compare(freshUntilTick, other.freshUntilTick);
        }
    }

    /**
     * One bounded observation converted into a measurable interest input.
     *
     * @param domain observation domain
     * @param interestKind interest family evidenced by the observation
     * @param targetId stable route/system/faction/resource/obligation identity
     * @param severityBasisPoints evidence magnitude in {@code [0,10000]}
     * @param evidence provenance and freshness
     */
    public record ActorObservation(
            Domain domain,
            InterestKind interestKind,
            String targetId,
            int severityBasisPoints,
            ObservationEvidence evidence) implements Comparable<ActorObservation> {

        /** Validates one bounded observation and its legal domain/interest pairing. */
        public ActorObservation {
            Objects.requireNonNull(domain, "Observation domain not set");
            Objects.requireNonNull(interestKind, "Interest kind not set");
            targetId = requireText(targetId, "Observation target ID");
            if (severityBasisPoints < 0 || severityBasisPoints > 10_000) {
                throw new IllegalArgumentException("Observation severity must be in [0,10000]");
            }
            Objects.requireNonNull(evidence, "Observation evidence not set");
            if (!allowed(domain, interestKind)) {
                throw new IllegalArgumentException(
                        "Interest " + interestKind + " is not legal in domain " + domain);
            }
        }

        private static boolean allowed(Domain domain, InterestKind kind) {
            return switch (kind) {
                case SUPPLY_DEPENDENCY, RESOURCE_DEFICIT -> domain == Domain.ECONOMIC;
                case MARKET_ACCESS -> domain == Domain.ECONOMIC || domain == Domain.DIPLOMATIC;
                case ROUTE_EXPOSURE -> domain == Domain.ECONOMIC || domain == Domain.SECURITY;
                case BORDER_SECURITY -> domain == Domain.TERRITORIAL || domain == Domain.SECURITY;
                case TERRITORIAL_OPPORTUNITY -> domain == Domain.TERRITORIAL;
                case TREATY_OBLIGATION -> domain == Domain.DIPLOMATIC;
            };
        }

        @Override
        public int compareTo(ActorObservation other) {
            Objects.requireNonNull(other, "other");
            int domainOrder = domain.compareTo(other.domain);
            if (domainOrder != 0) {
                return domainOrder;
            }
            int kindOrder = interestKind.compareTo(other.interestKind);
            if (kindOrder != 0) {
                return kindOrder;
            }
            int target = targetId.compareTo(other.targetId);
            if (target != 0) {
                return target;
            }
            int severity = Integer.compare(severityBasisPoints, other.severityBasisPoints);
            return severity != 0 ? severity : evidence.compareTo(other.evidence);
        }
    }

    /** Validates ownership and canonicalizes all four actor-bounded domains. */
    public FactionActorObservationSnapshot {
        factionContentId = requireText(factionContentId, "Faction content ID");
        requireNonNegative(observedAtTick, "Snapshot observation tick");
        economic = normalize(economic, Domain.ECONOMIC, observedAtTick);
        territorial = normalize(territorial, Domain.TERRITORIAL, observedAtTick);
        security = normalize(security, Domain.SECURITY, observedAtTick);
        diplomatic = normalize(diplomatic, Domain.DIPLOMATIC, observedAtTick);
    }

    /**
     * Returns all current observations in canonical cross-domain order.
     *
     * @return immutable current observation rows
     */
    public List<ActorObservation> currentObservations() {
        TreeSet<ActorObservation> current = new TreeSet<>();
        economic.stream().filter(observation -> observation.evidence().currentAt(observedAtTick)).forEach(current::add);
        territorial.stream().filter(observation -> observation.evidence().currentAt(observedAtTick)).forEach(current::add);
        security.stream().filter(observation -> observation.evidence().currentAt(observedAtTick)).forEach(current::add);
        diplomatic.stream().filter(observation -> observation.evidence().currentAt(observedAtTick)).forEach(current::add);
        return List.copyOf(current);
    }

    private static List<ActorObservation> normalize(
            List<ActorObservation> observations,
            Domain expectedDomain,
            long snapshotTick) {
        Objects.requireNonNull(observations, expectedDomain + " observations not set");
        TreeSet<ActorObservation> sorted = new TreeSet<>();
        for (ActorObservation observation : observations) {
            ActorObservation checked = Objects.requireNonNull(observation, "Observation not set");
            if (checked.domain() != expectedDomain) {
                throw new IllegalArgumentException(
                        "Observation domain " + checked.domain() + " placed in " + expectedDomain + " list");
            }
            if (checked.evidence().observedAtTick() > snapshotTick) {
                throw new IllegalArgumentException("Observation cannot arrive after snapshot tick");
            }
            sorted.add(checked);
        }
        return List.copyOf(sorted);
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }

    private static void requireNonNegative(long value, String label) {
        if (value < 0L) {
            throw new IllegalArgumentException(label + " cannot be negative");
        }
    }
}
