package com.spacesim.content;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable M22.4 production-side state for Industrial Union series/commonality and retooling.
 *
 * <p>This state owns no inventory, treasury, construction progress, shipyard work or fielded assets.
 * It records only the currently qualified assembly series and unfinished changeover burden that
 * callers must feed into the existing Stage-18 manufacturing/shipyard authorities. The separation
 * prevents the faction package from becoming a competing production authority.</p>
 */
public final class Stage22IndustrialUnionProductionState {
    /** Exact supported persistence envelope. */
    public static final int CURRENT_VERSION = 1;
    /** Existing authoritative runtime/save identity retained by the Stage-22.1 profile contract. */
    public static final String STABLE_FACTION_ID = "faction.industrial_combine";
    /** Sentinel used when no series is qualified or pending. */
    public static final String NO_SERIES = "assembly_series.industrial_union.none";

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern CONTENT_ID = Pattern.compile("[a-z][a-z0-9_-]*(?:\\.[a-z0-9][a-z0-9_-]*)+");

    private final int envelopeVersion;
    private final String stableFactionId;
    private final String packageFingerprint;
    private final long sequence;
    private final List<YardSeriesState> yards;
    private final Map<String, YardSeriesState> yardById;

    /**
     * Creates one deterministic production-side snapshot.
     *
     * @param envelopeVersion exact supported state version
     * @param stableFactionId stable Industrial Union runtime/save identity
     * @param packageFingerprint exact M22.4 package fingerprint
     * @param sequence monotonic local state sequence
     * @param yards qualified-series state for one or more ordinary Stage-18 yards
     */
    public Stage22IndustrialUnionProductionState(
            int envelopeVersion,
            String stableFactionId,
            String packageFingerprint,
            long sequence,
            List<YardSeriesState> yards) {
        if (envelopeVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Industrial Union production-state version: " + envelopeVersion);
        }
        this.envelopeVersion = envelopeVersion;
        this.stableFactionId = requireId(stableFactionId, "stableFactionId");
        if (!STABLE_FACTION_ID.equals(this.stableFactionId)) {
            throw new IllegalArgumentException("Industrial Union production state must retain " + STABLE_FACTION_ID);
        }
        this.packageFingerprint = requireFingerprint(packageFingerprint);
        if (sequence < 0L) {
            throw new IllegalArgumentException("Production-state sequence must be non-negative");
        }
        this.sequence = sequence;
        List<YardSeriesState> copy = new ArrayList<>(Objects.requireNonNull(yards, "yards"));
        copy.replaceAll(value -> Objects.requireNonNull(value, "yard state"));
        copy.sort(Comparator.comparing(YardSeriesState::yardId));
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("Industrial Union production state requires at least one yard");
        }
        Map<String, YardSeriesState> index = new LinkedHashMap<>();
        for (YardSeriesState yard : copy) {
            if (index.putIfAbsent(yard.yardId(), yard) != null) {
                throw new IllegalArgumentException("Duplicate Industrial Union yard state: " + yard.yardId());
            }
        }
        this.yards = List.copyOf(copy);
        this.yardById = Map.copyOf(index);
    }

    /** @return exact state envelope version */
    public int envelopeVersion() { return envelopeVersion; }
    /** @return authoritative stable faction identity */
    public String stableFactionId() { return stableFactionId; }
    /** @return exact authored package fingerprint */
    public String packageFingerprint() { return packageFingerprint; }
    /** @return monotonic local state sequence */
    public long sequence() { return sequence; }
    /** @return immutable yard series states sorted by yard ID */
    public List<YardSeriesState> yards() { return yards; }

    /**
     * Finds one ordinary yard's production-side qualification state.
     *
     * @param yardId stable yard ID
     * @return yard state, or {@code null}
     */
    public YardSeriesState findYard(String yardId) { return yardById.get(yardId); }

    /**
     * Replaces one yard snapshot and increments the deterministic state sequence.
     *
     * @param replacement replacement state for an already tracked yard
     * @return updated immutable snapshot
     */
    public Stage22IndustrialUnionProductionState withYard(YardSeriesState replacement) {
        YardSeriesState checked = Objects.requireNonNull(replacement, "replacement");
        if (!yardById.containsKey(checked.yardId())) {
            throw new IllegalArgumentException("Unknown Industrial Union yard state: " + checked.yardId());
        }
        List<YardSeriesState> updated = new ArrayList<>(yards);
        updated.replaceAll(value -> value.yardId().equals(checked.yardId()) ? checked : value);
        return new Stage22IndustrialUnionProductionState(
                envelopeVersion, stableFactionId, packageFingerprint, sequence + 1L, updated);
    }

    /**
     * Creates an unqualified yard state. First production therefore requires an explicit retool.
     *
     * @param yardId ordinary Stage-18 shipyard/content ID
     * @return deterministic unqualified state
     */
    public static YardSeriesState unqualifiedYard(String yardId) {
        return new YardSeriesState(yardId, NO_SERIES, NO_SERIES, 0, 0, 0L, 0L);
    }

    /** One yard's serial-production qualification and unfinished changeover burden. */
    public record YardSeriesState(
            String yardId,
            String activeSeriesId,
            String pendingSeriesId,
            int completedUnitsInSeries,
            int commonalityStreak,
            long retoolWorkRemainingSeconds,
            long retoolEnergyRemainingJ) {
        /**
         * Validates one yard series snapshot.
         *
         * @param yardId ordinary yard ID
         * @param activeSeriesId currently qualified assembly series or {@link #NO_SERIES}
         * @param pendingSeriesId requested assembly series during changeover or {@link #NO_SERIES}
         * @param completedUnitsInSeries completed units since the current series was qualified
         * @param commonalityStreak consecutive same-series units
         * @param retoolWorkRemainingSeconds finite common-authority work still required by changeover
         * @param retoolEnergyRemainingJ finite process energy still required by changeover
         */
        public YardSeriesState {
            yardId = requireId(yardId, "yardId");
            activeSeriesId = requireId(activeSeriesId, "activeSeriesId");
            pendingSeriesId = requireId(pendingSeriesId, "pendingSeriesId");
            if (completedUnitsInSeries < 0 || commonalityStreak < 0) {
                throw new IllegalArgumentException("Series counters must be non-negative");
            }
            if (commonalityStreak > completedUnitsInSeries) {
                throw new IllegalArgumentException("Commonality streak cannot exceed completed series units");
            }
            if (retoolWorkRemainingSeconds < 0L || retoolEnergyRemainingJ < 0L) {
                throw new IllegalArgumentException("Retool burdens must be non-negative");
            }
            boolean pending = !NO_SERIES.equals(pendingSeriesId);
            boolean burden = retoolWorkRemainingSeconds > 0L || retoolEnergyRemainingJ > 0L;
            if (!pending && burden) {
                throw new IllegalArgumentException("Retool burden cannot exist without a pending series");
            }
            if (pending && pendingSeriesId.equals(activeSeriesId)) {
                throw new IllegalArgumentException("Retool target must differ from active series");
            }
        }

        /** @return whether an explicit series changeover is pending or unfinished */
        public boolean retooling() {
            return !NO_SERIES.equals(pendingSeriesId);
        }
    }

    private static String requireId(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (!CONTENT_ID.matcher(checked).matches()) {
            throw new IllegalArgumentException(label + " must be a lower-case dotted content ID: " + checked);
        }
        return checked;
    }

    private static String requireFingerprint(String value) {
        String checked = Objects.requireNonNull(value, "packageFingerprint").strip();
        if (!SHA256.matcher(checked).matches()) {
            throw new IllegalArgumentException("Industrial Union package fingerprint must be lowercase SHA-256");
        }
        return checked;
    }
}
