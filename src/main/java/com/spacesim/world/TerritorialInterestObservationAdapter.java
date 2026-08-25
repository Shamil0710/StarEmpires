package com.spacesim.world;

import com.spacesim.world.FactionActorObservationSnapshot.ActorObservation;
import com.spacesim.world.FactionActorObservationSnapshot.Domain;
import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;

import java.util.List;
import java.util.Objects;

/**
 * Stage-21F adapter from the actor-visible Stage-17 territory ledger into Stage-21A observations.
 *
 * <p>This adapter deliberately reads only persistent claim/control law available through the
 * territory ledger. It does not inspect hidden fleets, sensors, discovery truth, cargo or military
 * strength. The resulting rows therefore remain valid actor-bounded inputs for
 * {@link FactionInterestResolver} rather than creating an omniscient strategic-AI shortcut.</p>
 */
public final class TerritorialInterestObservationAdapter {
    private static final int CONTROLLED_BORDER_PRIORITY_BPS = 2_500;
    private static final int ACTIVE_CLAIM_PRIORITY_BPS = 5_500;
    private static final int STABILIZING_CLAIM_PRIORITY_BPS = 6_500;
    private static final int CONTESTED_CLAIM_PRIORITY_BPS = 8_500;

    private TerritorialInterestObservationAdapter() {
        throw new AssertionError("Utility class");
    }

    /**
     * Projects one actor-visible system ledger row into future-interest evidence.
     *
     * <p>Established own control becomes a durable border-security interest. A still-unestablished
     * own claim becomes a territorial-opportunity interest whose urgency rises when stabilization
     * or contest is recorded by Stage-17 law. No observation is invented for a system in which the
     * actor has neither an established control entry nor an explicit claim.</p>
     *
     * @param world authoritative world used only through persistent territory law
     * @param factionContentId observing faction stable identity
     * @param systemId public territory-ledger system
     * @param observedAtTick authoritative tick at which the actor reads the ledger; must equal the world's current tick
     * @return zero or one canonical actor-bounded territory observations
     */
    public static List<ActorObservation> observe(
            WorldSimulation world,
            String factionContentId,
            StarSystemId systemId,
            long observedAtTick) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "world");
        String faction = requireId(factionContentId);
        StarSystemId system = Objects.requireNonNull(systemId, "systemId");
        if (observedAtTick < 0L) {
            throw new IllegalArgumentException("observedAtTick must be non-negative");
        }
        if (observedAtTick != checkedWorld.getAuthoritativeWorldTick()) {
            throw new IllegalArgumentException("observedAtTick must equal the authoritative world tick");
        }
        FactionStrategicState strategy = checkedWorld.findFactionStrategicState(faction)
                .orElseThrow(() -> new IllegalArgumentException("unknown faction: " + faction));
        String target = "system:" + system.value();
        ObservationEvidence evidence = new ObservationEvidence(
                ObservationChannel.TERRITORY_LEDGER,
                "territory-ledger:" + faction + ":" + system.value(),
                observedAtTick,
                -1L);

        if (strategy.controls(system)) {
            return List.of(new ActorObservation(
                    Domain.TERRITORIAL,
                    InterestKind.BORDER_SECURITY,
                    target,
                    CONTROLLED_BORDER_PRIORITY_BPS,
                    evidence));
        }

        TerritorialClaimState claim = strategy.claimFor(system);
        if (claim == null) {
            return List.of();
        }
        int severity = switch (claim.status()) {
            case ACTIVE -> ACTIVE_CLAIM_PRIORITY_BPS;
            case STABILIZING, ESTABLISHED -> STABILIZING_CLAIM_PRIORITY_BPS;
            case CONTESTED -> CONTESTED_CLAIM_PRIORITY_BPS;
        };
        return List.of(new ActorObservation(
                Domain.TERRITORIAL,
                InterestKind.TERRITORIAL_OPPORTUNITY,
                target,
                severity,
                evidence));
    }

    private static String requireId(String value) {
        String checked = Objects.requireNonNull(value, "factionContentId").strip();
        if (checked.isEmpty()) throw new IllegalArgumentException("factionContentId cannot be blank");
        return checked;
    }
}
