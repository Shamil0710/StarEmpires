package com.spacesim.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Personal committed-action history. Historical saves receive no invented events.
 * @param nextSequence never reused event identity
 * @param acknowledgedThroughSequence durable notification acknowledgement
 * @param entries last retained events in commit order
 */
public record PlayerJournalState(long nextSequence, long acknowledgedThroughSequence, List<Entry> entries) {
    /** Maximum retained events; older events are explicitly outside the retained journal. */
    public static final int CAPACITY = 2048;

    /** Committed causes, distinct from previews or fabricated observations. */
    public enum Kind {
        /** Accepted physical action. */ COMMAND,
        /** Finished physical manufacturing batch. */ MANUFACTURING_COMPLETED,
        /** Finished physical repair with actual materials and elapsed work. */ REPAIR_COMPLETED,
        /** Finished same-hull physical refit with actual equipment and elapsed work. */ REFIT_COMPLETED,
        /** Automatic excavation stop. */ MINING_STOPPED,
        /** Finished physical loading or unloading of an individual module. */ MODULE_TRANSFER_COMPLETED,
        /** Actual accepted contract lifecycle change. */ MISSION_CHANGED,
        /** Accepted personal faction or fleet command. */ GOVERNMENT_COMMAND
    }

    /**
     * One actual commit, timestamped by the authoritative simulation clock.
     * @param sequence durable event identity
     * @param tick actual authoritative tick
     * @param kind committed cause
     * @param action ordinary command or outcome code
     * @param endpoint actual station/source/target reference, or empty
     * @param subject actual commodity/product/contract reference, or empty
     * @param quantity requested whole units or kilograms, zero when not applicable
     * @param walletDeltaMilliCredits actual personal wallet change
     * @param fleetId actual involved personal fleet, zero when not applicable
     */
    public record Entry(long sequence, long tick, Kind kind, String action, String endpoint,
            String subject, long quantity, long walletDeltaMilliCredits, long fleetId) {
        /**
         * Validates bounded immutable commit evidence.
         * @param sequence event identity
         * @param tick authoritative tick
         * @param kind cause
         * @param action command/outcome code
         * @param endpoint physical reference
         * @param subject product/contract reference
         * @param quantity whole requested amount
         * @param walletDeltaMilliCredits actual wallet delta
         * @param fleetId personal fleet identity
         */
        public Entry {
            if (sequence <= 0 || tick < 0 || quantity < 0 || fleetId < 0) throw new IllegalArgumentException("Invalid journal event");
            Objects.requireNonNull(kind, "kind");
            action = bounded(action); endpoint = bounded(endpoint); subject = bounded(subject);
            if (action.isBlank()) throw new IllegalArgumentException("Missing committed action");
        }
    }

    /**
     * Validates exact retained ordering and notification watermark.
     * @param nextSequence never reused next event identity
     * @param acknowledgedThroughSequence acknowledged identity
     * @param entries retained consecutive suffix of actual commits
     */
    public PlayerJournalState {
        entries = List.copyOf(entries);
        if (nextSequence <= 0 || acknowledgedThroughSequence < 0 || acknowledgedThroughSequence >= nextSequence
                || entries.size() > CAPACITY) throw new IllegalArgumentException("Invalid journal watermark");
        long sequence = nextSequence - entries.size();
        if (sequence <= 0 || (entries.isEmpty() && nextSequence != 1)) throw new IllegalArgumentException("Missing journal suffix");
        long tick = -1;
        for (var entry : entries) {
            if (entry.sequence() != sequence++ || entry.tick() < tick) throw new IllegalArgumentException("Unordered journal");
            tick = entry.tick();
        }
    }

    /** @return empty non-granting journal */
    public static PlayerJournalState empty() { return new PlayerJournalState(1, 0, List.of()); }

    /** @return unread retained notification count */
    public long unreadCount() { return entries.stream().filter(e -> e.sequence() > acknowledgedThroughSequence).count(); }

    /**
     * Appends one successful commit; caller supplies actual domain evidence.
     * @param tick actual authoritative tick
     * @param kind committed cause
     * @param action command/outcome code
     * @param endpoint physical reference
     * @param subject product/contract reference
     * @param quantity whole requested amount
     * @param walletDeltaMilliCredits actual wallet delta
     * @param fleetId personal fleet identity
     * @return new bounded journal
     */
    public PlayerJournalState append(long tick, Kind kind, String action, String endpoint, String subject,
            long quantity, long walletDeltaMilliCredits, long fleetId) {
        long next = Math.addExact(nextSequence, 1);
        var copy = new ArrayList<>(entries);
        copy.add(new Entry(nextSequence, tick, kind, action, endpoint, subject, quantity, walletDeltaMilliCredits, fleetId));
        if (copy.size() > CAPACITY) copy.remove(0);
        return new PlayerJournalState(next, acknowledgedThroughSequence, copy);
    }

    /**
     * Acknowledges only existing events; never hides a future commit.
     * @param throughSequence existing event identity
     * @return new journal with monotonic acknowledgement
     */
    public PlayerJournalState acknowledge(long throughSequence) {
        if (throughSequence < 0 || throughSequence >= nextSequence) throw new IllegalArgumentException("Unknown notification");
        return new PlayerJournalState(nextSequence, Math.max(acknowledgedThroughSequence, throughSequence), entries);
    }

    private static String bounded(String value) {
        var checked = Objects.requireNonNull(value);
        if (checked.length() > 512 || checked.indexOf('\n') >= 0 || checked.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Journal reference outside bounds");
        return checked;
    }
}
