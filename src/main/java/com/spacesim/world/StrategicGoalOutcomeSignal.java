package com.spacesim.world;

/**
 * Read-only execution outcome delivered back to the Stage-21B planner by a later authority layer.
 *
 * <p>Stage 21B consumes this signal but never claims authority to perform the underlying economic,
 * logistical, diplomatic, construction or fleet action.</p>
 */
public enum StrategicGoalOutcomeSignal {
    /** No terminal outcome has been reported. */
    NONE,
    /** The authoritative execution layer reports that the goal's success condition was met. */
    SUCCEEDED,
    /** The authoritative execution layer reports terminal failure. */
    FAILED
}
