package com.spacesim.presentation.asset;

import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.ResolvedSprite;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.SpriteBinding;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Stage22RuntimeSpriteEffectsTest {
    @Test
    void zeroPropulsionLeavesAuthoredBindingUntouched() {
        ResolvedSprite resolved = Stage20MinimumPlayableSpriteCatalog.resolvePlayable(null);
        SpriteBinding source = resolved.binding();

        assertSame(source, Stage22RuntimeSpriteEffects.withPropulsion(source, 0d));
        assertEquals(0d, Stage22RuntimeSpriteEffects.propulsionFraction(source));
        assertEquals(source.assetId(), Stage22RuntimeSpriteEffects.authoredAssetId(source));
    }

    @Test
    void propulsionMetadataPreservesEveryAuthoredFieldExceptRuntimeAssetSuffix() {
        SpriteBinding source = Stage20MinimumPlayableSpriteCatalog.resolvePlayable(null).binding();
        SpriteBinding runtime = Stage22RuntimeSpriteEffects.withPropulsion(source, 0.625d);

        assertEquals(0.625d, Stage22RuntimeSpriteEffects.propulsionFraction(runtime));
        assertEquals(source.assetId(), Stage22RuntimeSpriteEffects.authoredAssetId(runtime));
        assertEquals(source.role(), runtime.role());
        assertEquals(source.texturePath(), runtime.texturePath());
        assertEquals(source.region(), runtime.region());
        assertEquals(source.pivotX(), runtime.pivotX());
        assertEquals(source.pivotY(), runtime.pivotY());
        assertEquals(source.sourceFacing(), runtime.sourceFacing());
        assertEquals(source.nominalLengthM(), runtime.nominalLengthM());
        assertEquals(source.nominalWidthM(), runtime.nominalWidthM());
        assertEquals(source.hardpoints(), runtime.hardpoints());
    }

    @Test
    void rejectsInvalidPropulsionFractions() {
        SpriteBinding source = Stage20MinimumPlayableSpriteCatalog.resolvePlayable(null).binding();

        assertThrows(IllegalArgumentException.class,
                () -> Stage22RuntimeSpriteEffects.withPropulsion(source, -0.01d));
        assertThrows(IllegalArgumentException.class,
                () -> Stage22RuntimeSpriteEffects.withPropulsion(source, 1.01d));
        assertThrows(IllegalArgumentException.class,
                () -> Stage22RuntimeSpriteEffects.withPropulsion(source, Double.NaN));
    }
}
