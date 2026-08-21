package com.spacesim.navigation;

import java.util.Objects;

/**
 * Persistent physical connection between two star systems.
 *
 * Stage 20D moves jump links from temporary graph edges to world entities
 * that can later carry discovery, access, logistics and conflict state.
 */
public final class JumpConnection {
    private final String id;
    private final String sourceSystemId;
    private final String targetSystemId;
    private final long distanceKilometers;
    private final long transitTimeSeconds;

    private boolean discovered;
    private boolean active = true;

    public JumpConnection(
            String id,
            String sourceSystemId,
            String targetSystemId,
            long distanceKilometers,
            long transitTimeSeconds) {
        this.id = requireText(id, "id");
        this.sourceSystemId = requireText(sourceSystemId, "sourceSystemId");
        this.targetSystemId = requireText(targetSystemId, "targetSystemId");
        if (distanceKilometers <= 0 || transitTimeSeconds <= 0) {
            throw new IllegalArgumentException("jump metrics must be positive");
        }
        this.distanceKilometers = distanceKilometers;
        this.transitTimeSeconds = transitTimeSeconds;
    }

    public String id() { return id; }
    public String sourceSystemId() { return sourceSystemId; }
    public String targetSystemId() { return targetSystemId; }
    public long distanceKilometers() { return distanceKilometers; }
    public long transitTimeSeconds() { return transitTimeSeconds; }
    public boolean discovered() { return discovered; }
    public boolean active() { return active; }

    public void discover() { this.discovered = true; }
    public void deactivate() { this.active = false; }
    public void activate() { this.active = true; }

    private static String requireText(String value, String name) {
        return Objects.requireNonNull(value, name);
    }
}
