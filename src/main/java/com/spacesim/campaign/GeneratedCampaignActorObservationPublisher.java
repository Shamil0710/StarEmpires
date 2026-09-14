package com.spacesim.campaign;

import com.spacesim.world.DiplomaticLifecycleState;
import com.spacesim.world.FactionActorObservationSnapshot;
import com.spacesim.world.FactionActorObservationSnapshot.ActorObservation;
import com.spacesim.world.FactionActorObservationSnapshot.Domain;
import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Actor-bounded observation adapter for the composed generated campaign.
 *
 * <p>The publisher deliberately accepts only Stage-21C actor memory plus the authoritative review
 * tick. It has no {@code WorldSimulation}, physical-world, registry, fleet, market or generated
 * truth reference. Consequently an autonomous Stage-21A review can only reason about facts that an
 * existing authority has already persisted as knowledge owned by that faction.</p>
 */
final class GeneratedCampaignActorObservationPublisher {
    private GeneratedCampaignActorObservationPublisher() {
        throw new AssertionError("No instances");
    }

    /**
     * Projects semantically explicit Stage-21C relation memories into Stage-21A interest evidence.
     *
     * <p>Only relation factors whose persisted meaning already matches a Stage-21A interest are
     * projected. Generic remembered actions and past treaty-performance rows are intentionally not
     * reinterpreted into a new strategic fact.</p>
     *
     * @param factionContentId observing autonomous faction
     * @param reviewTick authoritative Stage-21A review tick
     * @param diplomacy actor-bounded Stage-21C political memory
     * @return canonical observation snapshot containing no hidden-world information
     */
    static FactionActorObservationSnapshot publish(
            String factionContentId,
            long reviewTick,
            DiplomaticLifecycleState diplomacy) {
        String factionId = requireText(factionContentId, "Faction content ID");
        if (reviewTick < 0L) {
            throw new IllegalArgumentException("Review tick cannot be negative");
        }
        DiplomaticLifecycleState state = Objects.requireNonNull(diplomacy, "Diplomacy state not set");

        List<ActorObservation> economic = new ArrayList<>();
        List<ActorObservation> territorial = new ArrayList<>();
        List<ActorObservation> security = new ArrayList<>();
        List<ActorObservation> diplomatic = new ArrayList<>();

        for (DiplomaticLifecycleState.RelationMemory memory : state.relationMemories()) {
            if (!memory.ownerFactionId().equals(factionId)) {
                continue;
            }
            for (DiplomaticLifecycleState.RelationEvent event : memory.events()) {
                if (event.observedTick() > reviewTick) {
                    continue;
                }
                int severity = Math.min(10_000, Math.abs(event.impact()) * 100);
                if (severity == 0) {
                    continue;
                }
                ObservationEvidence evidence = new ObservationEvidence(
                        ObservationChannel.DIPLOMATIC_REGISTRY,
                        "stage21c:relation:" + event.eventId(),
                        event.observedTick(),
                        -1L);
                String targetId = memory.targetFactionId();
                switch (event.factor()) {
                    case TRADE_DEPENDENCE -> economic.add(new ActorObservation(
                            Domain.ECONOMIC,
                            InterestKind.SUPPLY_DEPENDENCY,
                            targetId,
                            severity,
                            evidence));
                    case TERRITORIAL_CONFLICT -> territorial.add(new ActorObservation(
                            Domain.TERRITORIAL,
                            InterestKind.BORDER_SECURITY,
                            targetId,
                            severity,
                            evidence));
                    case THREAT -> security.add(new ActorObservation(
                            Domain.SECURITY,
                            InterestKind.BORDER_SECURITY,
                            targetId,
                            severity,
                            evidence));
                    case DIPLOMATIC_COMMITMENT -> diplomatic.add(new ActorObservation(
                            Domain.DIPLOMATIC,
                            InterestKind.TREATY_OBLIGATION,
                            targetId,
                            severity,
                            evidence));
                    case REMEMBERED_ACTION, TREATY_PERFORMANCE -> {
                        // These rows are genuine actor memory but do not by themselves establish a
                        // Stage-21A measurable interest. Do not invent one merely to populate AI input.
                    }
                }
            }
        }

        return new FactionActorObservationSnapshot(
                factionId,
                reviewTick,
                economic,
                territorial,
                security,
                diplomatic);
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
