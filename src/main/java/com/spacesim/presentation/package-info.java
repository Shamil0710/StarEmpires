/**
 * Presentation-only orchestration for ordered rendering passes.
 *
 * <p>The package deliberately has no dependency on simulation ownership or persistence. A
 * presentation pipeline consumes a caller-provided frame and may be disabled completely, which
 * keeps headless simulation independent from OpenGL and desktop UI concerns.</p>
 */
package com.spacesim.presentation;
