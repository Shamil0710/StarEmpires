package com.spacesim.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * Tint and blend factors of a sprite batch, captured before an additive VFX pass and restored
 * after it, so the pass leaves the batch exactly as it found it.
 *
 * @param color tint copy
 * @param srcRgb source RGB blend factor
 * @param dstRgb destination RGB blend factor
 * @param srcAlpha source alpha blend factor
 * @param dstAlpha destination alpha blend factor
 */
record BatchState(Color color, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
    /**
     * @param batch sprite batch
     * @return its current tint and blend factors
     */
    static BatchState capture(SpriteBatch batch) {
        return new BatchState(new Color(batch.getColor()), batch.getBlendSrcFunc(), batch.getBlendDstFunc(),
                batch.getBlendSrcFuncAlpha(), batch.getBlendDstFuncAlpha());
    }

    /** @param batch sprite batch to return to this state */
    void restore(SpriteBatch batch) {
        batch.setBlendFunctionSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        batch.setColor(color);
    }
}
