package com.spacesim.presentation;

/**
 * One presentation-only operation in an ordered {@link PresentationPipeline}.
 *
 * @param <F> immutable or frame-scoped input type consumed by the pass
 */
@FunctionalInterface
public interface PresentationPass<F> {
    /**
     * Renders or otherwise presents one frame.
     *
     * @param frame non-null caller-provided presentation frame
     */
    void render(F frame);
}
