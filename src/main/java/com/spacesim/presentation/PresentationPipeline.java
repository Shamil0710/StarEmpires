package com.spacesim.presentation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Small deterministic orchestration boundary for presentation-only rendering passes.
 *
 * <p>Passes execute in {@link PresentationLayer} order. Passes registered in the same layer keep
 * registration order. The pipeline owns ordering only: it does not own simulation state, GPU
 * resources or pass disposal. Disabling the pipeline makes {@link #render(Object)} a no-op, which
 * provides an explicit seam for headless or diagnostics modes.</p>
 *
 * @param <F> frame-scoped presentation input type
 */
public final class PresentationPipeline<F> {
    private final List<Entry<F>> entries = new ArrayList<>();
    private final Set<String> passIds = new HashSet<>();
    private boolean enabled = true;

    /**
     * Registers a pass under a stable identifier.
     *
     * @param layer coarse presentation layer controlling execution order
     * @param passId non-blank identifier unique within this pipeline
     * @param pass presentation operation
     * @return this pipeline for fluent configuration
     * @throws NullPointerException if {@code layer}, {@code passId} or {@code pass} is null
     * @throws IllegalArgumentException if the identifier is blank or already registered
     */
    public PresentationPipeline<F> register(
            PresentationLayer layer,
            String passId,
            PresentationPass<? super F> pass) {
        Objects.requireNonNull(layer, "Presentation layer must not be null");
        Objects.requireNonNull(passId, "Presentation pass ID must not be null");
        Objects.requireNonNull(pass, "Presentation pass must not be null");

        String normalizedId = passId.trim();
        if (normalizedId.isEmpty()) {
            throw new IllegalArgumentException("Presentation pass ID must not be blank");
        }
        if (!passIds.add(normalizedId)) {
            throw new IllegalArgumentException("Duplicate presentation pass ID: " + normalizedId);
        }

        Entry<F> entry = new Entry<>(layer, normalizedId, pass);
        int insertionIndex = entries.size();
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).layer.ordinal() > layer.ordinal()) {
                insertionIndex = index;
                break;
            }
        }
        entries.add(insertionIndex, entry);
        return this;
    }

    /**
     * Executes all registered passes for one presentation frame.
     *
     * <p>When the pipeline is disabled this method is a no-op and accepts a null frame. An enabled
     * pipeline always requires a non-null frame.</p>
     *
     * @param frame frame-scoped presentation input
     * @throws NullPointerException if the pipeline is enabled and {@code frame} is null
     */
    public void render(F frame) {
        if (!enabled) {
            return;
        }
        F requiredFrame = Objects.requireNonNull(frame, "Presentation frame must not be null");
        for (Entry<F> entry : entries) {
            entry.pass.render(requiredFrame);
        }
    }

    /**
     * Enables or disables all presentation passes without changing their registration.
     *
     * @param enabled whether rendering should execute
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return whether {@link #render(Object)} currently executes registered passes */
    public boolean isEnabled() {
        return enabled;
    }

    /** @return number of registered passes */
    public int size() {
        return entries.size();
    }

    /**
     * Returns pass identifiers in actual execution order.
     *
     * @return immutable ordered identifier list
     */
    public List<String> passIds() {
        List<String> orderedIds = new ArrayList<>(entries.size());
        for (Entry<F> entry : entries) {
            orderedIds.add(entry.passId);
        }
        return List.copyOf(orderedIds);
    }

    private static final class Entry<F> {
        private final PresentationLayer layer;
        private final String passId;
        private final PresentationPass<? super F> pass;

        private Entry(
                PresentationLayer layer,
                String passId,
                PresentationPass<? super F> pass) {
            this.layer = layer;
            this.passId = passId;
            this.pass = pass;
        }
    }
}
