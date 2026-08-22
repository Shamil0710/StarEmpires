package com.spacesim.world;

import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;

import java.util.Objects;

/**
 * Peaceful Stage-21B strategic goal families.
 *
 * <p>This taxonomy deliberately excludes war declaration, conquest, annexation and other hostile
 * diplomatic authorities. Stage 21B establishes persistent intent before later stages are allowed
 * to introduce escalation.</p>
 */
public enum StrategicGoalType {
    /** Keep an actor-known materially important route usable and protected. */
    SECURE_ROUTE("secure-route"),
    /** Build resilience against an actor-known resource or supply shortfall. */
    STOCKPILE("stockpile"),
    /** Investigate an actor-known territorial opportunity without claiming it. */
    EXPLORE("explore"),
    /** Preserve an actor-known border or security position. */
    DEFEND("defend"),
    /** Seek lawful economic or diplomatic access. */
    OBTAIN_ACCESS("obtain-access");

    private final String wireId;

    StrategicGoalType(String wireId) {
        this.wireId = wireId;
    }

    /**
     * Stable persistence/UI identity.
     *
     * @return lowercase hyphenated goal identifier
     */
    public String wireId() {
        return wireId;
    }

    /**
     * Parses the stable wire identity.
     *
     * @param value persisted wire identity
     * @return matching goal type
     */
    public static StrategicGoalType fromWireId(String value) {
        String checked = Objects.requireNonNull(value, "Strategic goal wire ID not set").strip();
        for (StrategicGoalType type : values()) {
            if (type.wireId.equals(checked)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported strategic goal type: " + value);
    }

    /**
     * Checks whether actor-bounded Stage-21A evidence may directly justify this goal family.
     *
     * <p>One evidence family can support more than one possible response. The planner still requires
     * an explicit candidate, so this relation does not silently invent policy or doctrine.</p>
     *
     * @param kind actor-known interest evidence family
     * @return whether the evidence can justify this goal family
     */
    public boolean supports(InterestKind kind) {
        Objects.requireNonNull(kind, "Interest kind not set");
        return switch (this) {
            case SECURE_ROUTE -> kind == InterestKind.ROUTE_EXPOSURE
                    || kind == InterestKind.SUPPLY_DEPENDENCY;
            case STOCKPILE -> kind == InterestKind.RESOURCE_DEFICIT
                    || kind == InterestKind.SUPPLY_DEPENDENCY;
            case EXPLORE -> kind == InterestKind.TERRITORIAL_OPPORTUNITY;
            case DEFEND -> kind == InterestKind.BORDER_SECURITY
                    || kind == InterestKind.TREATY_OBLIGATION;
            case OBTAIN_ACCESS -> kind == InterestKind.MARKET_ACCESS
                    || kind == InterestKind.TREATY_OBLIGATION;
        };
    }
}
