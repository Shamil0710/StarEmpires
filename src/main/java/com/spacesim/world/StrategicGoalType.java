package com.spacesim.world;

import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;

import java.util.Objects;

/** Canonical Stage-21B strategic goal families from the living-world roadmap. */
public enum StrategicGoalType {
    /** Keep an actor-known materially important route usable and protected. */
    SECURE_ROUTE("secure-route"),
    /** Protect traffic or an obligation with escort capacity. */
    ESCORT("escort"),
    /** Establish a lawful claim over an actor-known opportunity. */
    CLAIM("claim"),
    /** Discourage an actor-known threat without initiating combat. */
    DETER("deter"),
    /** Apply strategic pressure for access or security without directly mutating diplomacy. */
    COERCE("coerce"),
    /** Prepare a limited hostile strike against an actor-known target. */
    RAID("raid"),
    /** Prepare denial of actor-known transit or market access. */
    BLOCKADE("blockade"),
    /** Prepare seizure of an actor-known territorial objective. */
    INVADE("invade"),
    /** Build resilience against an actor-known resource or supply shortfall. */
    STOCKPILE("stockpile"),
    /** Investigate an actor-known territorial opportunity without claiming it. */
    EXPLORE("explore"),
    /** Recover an actor-known lost or threatened strategic position. */
    RECOVER("recover"),
    /** Seek lawful economic or diplomatic access. */
    OBTAIN_ACCESS("obtain-access"),
    /** Preserve an actor-known border or security position. */
    DEFEND("defend");

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
     * Checks whether actor-bounded Stage-21A evidence may justify this goal family.
     *
     * <p>This is a provenance compatibility relation only. It does not authorize escalation or
     * execution. Candidate generation must additionally apply explicit doctrine/policy input.</p>
     *
     * @param kind actor-known interest evidence family
     * @return whether the evidence can justify this goal family
     */
    public boolean supports(InterestKind kind) {
        Objects.requireNonNull(kind, "Interest kind not set");
        return switch (this) {
            case SECURE_ROUTE -> kind == InterestKind.ROUTE_EXPOSURE
                    || kind == InterestKind.SUPPLY_DEPENDENCY
                    || kind == InterestKind.TREATY_OBLIGATION;
            case ESCORT -> kind == InterestKind.ROUTE_EXPOSURE
                    || kind == InterestKind.SUPPLY_DEPENDENCY
                    || kind == InterestKind.TREATY_OBLIGATION;
            case CLAIM -> kind == InterestKind.TERRITORIAL_OPPORTUNITY;
            case DETER -> kind == InterestKind.BORDER_SECURITY
                    || kind == InterestKind.TREATY_OBLIGATION;
            case COERCE -> kind == InterestKind.MARKET_ACCESS
                    || kind == InterestKind.BORDER_SECURITY;
            case RAID -> kind == InterestKind.BORDER_SECURITY
                    || kind == InterestKind.ROUTE_EXPOSURE;
            case BLOCKADE -> kind == InterestKind.MARKET_ACCESS
                    || kind == InterestKind.BORDER_SECURITY;
            case INVADE -> kind == InterestKind.TERRITORIAL_OPPORTUNITY
                    || kind == InterestKind.BORDER_SECURITY;
            case STOCKPILE -> kind == InterestKind.RESOURCE_DEFICIT
                    || kind == InterestKind.SUPPLY_DEPENDENCY;
            case EXPLORE -> kind == InterestKind.TERRITORIAL_OPPORTUNITY;
            case RECOVER -> kind == InterestKind.BORDER_SECURITY
                    || kind == InterestKind.TERRITORIAL_OPPORTUNITY;
            case OBTAIN_ACCESS -> kind == InterestKind.MARKET_ACCESS
                    || kind == InterestKind.TREATY_OBLIGATION;
            case DEFEND -> kind == InterestKind.BORDER_SECURITY
                    || kind == InterestKind.ROUTE_EXPOSURE
                    || kind == InterestKind.SUPPLY_DEPENDENCY
                    || kind == InterestKind.TREATY_OBLIGATION;
        };
    }

    /**
     * Reports goal families that imply deliberate hostile escalation.
     *
     * @return true for coercive or combat-oriented strategic intents
     */
    public boolean escalatory() {
        return switch (this) {
            case COERCE, RAID, BLOCKADE, INVADE -> true;
            default -> false;
        };
    }
}
