package com.spacesim.world;

/**
 * Persistent bounded institutional preferences of one faction.
 *
 * <p>Doctrine changes how shared strategic/diplomatic evaluators weigh observed choices. It never
 * grants money, cargo, production output, combat power or legal rights by itself. Each axis uses the
 * common {@code [0,100]} scale so authored AI factions and a player-created faction share the same
 * representation.</p>
 *
 * @param tradeOpenness willingness to prefer external trade and market integration
 * @param securityPosture priority given to security exposure and strategic risk
 * @param expansionPreference willingness to pursue territorial/infrastructure expansion
 * @param sovereigntySensitivity aversion to foreign jurisdiction, claims and concessions
 * @param treatyLegalism importance assigned to trust, credibility and contractual continuity
 * @param interventionism willingness to bear costs for external security commitments
 * @param economicResiliencePriority willingness to pay for diversification and lower dependency
 */
public record FactionDoctrineState(
        int tradeOpenness,
        int securityPosture,
        int expansionPreference,
        int sovereigntySensitivity,
        int treatyLegalism,
        int interventionism,
        int economicResiliencePriority) {
    private static final int NEUTRAL_AXIS = 50;

    /**
     * Validates every institutional preference against the common bounded scale.
     *
     * @param tradeOpenness willingness to prefer external trade and market integration
     * @param securityPosture priority given to security exposure and strategic risk
     * @param expansionPreference willingness to pursue territorial/infrastructure expansion
     * @param sovereigntySensitivity aversion to foreign jurisdiction, claims and concessions
     * @param treatyLegalism importance assigned to trust, credibility and contractual continuity
     * @param interventionism willingness to bear costs for external security commitments
     * @param economicResiliencePriority willingness to pay for diversification and lower dependency
     */
    public FactionDoctrineState {
        requireAxis(tradeOpenness, "Trade openness");
        requireAxis(securityPosture, "Security posture");
        requireAxis(expansionPreference, "Expansion preference");
        requireAxis(sovereigntySensitivity, "Sovereignty sensitivity");
        requireAxis(treatyLegalism, "Treaty legalism");
        requireAxis(interventionism, "Interventionism");
        requireAxis(economicResiliencePriority, "Economic resilience priority");
    }

    /**
     * Returns the migration/default doctrine with no directional institutional preference.
     *
     * @return immutable profile with all seven axes at the midpoint value 50
     */
    public static FactionDoctrineState neutral() {
        return new FactionDoctrineState(
                NEUTRAL_AXIS,
                NEUTRAL_AXIS,
                NEUTRAL_AXIS,
                NEUTRAL_AXIS,
                NEUTRAL_AXIS,
                NEUTRAL_AXIS,
                NEUTRAL_AXIS);
    }

    private static void requireAxis(int value, String label) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(label + " must be in [0,100]");
        }
    }
}
