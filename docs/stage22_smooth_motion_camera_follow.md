# Stage 22 — smooth local motion and camera follow

## Purpose

The production generated-world command UI must present fixed-tick physical movement smoothly without
changing simulation authority. A double click on a local ship/object must also enter a sticky camera
follow mode instead of performing only a one-time recenter.

## Authority boundary

- `LocalPhysicalPosition`, velocity, propulsion, route progress and authoritative world ticks remain
  the only gameplay motion truth.
- Presentation smoothing never writes coordinates, velocity, acceleration, fuel, route state or
  simulation clocks.
- The system overview uses one fitted SI-to-screen frame until the player requests a new overview,
  changes system or resizes the viewport. Moving edge objects therefore cannot continuously refit
  the whole map.
- Rendered object positions retain the previous/current fixed-tick projections and interpolate
  between them with the already-authoritative active `SimulationClock.getInterpolationAlpha()`.
  Constant fixed-tick motion therefore has constant presentation velocity instead of the
  accelerate/decelerate pulse produced by exponential target chasing.
- Camera follow applies no second easing layer. The camera and ship consume the exact same
  interpolated point, and the system camera uses the whole application screen as its stable origin,
  so the followed ship is pinned to the literal player-screen center every rendered frame.

## Interaction

- Double click a local object: inspect it and keep the camera following its stable presentation ID.
- Double click a ship in **Logistics** or **Military Forces**: activate its local system when needed,
  inspect the ship and enter the same follow mode.
- Mouse wheel: zoom remains available while following; the next presentation frame restores the
  target to the exact whole-screen center.
- Middle-mouse drag: immediately cancels follow and returns camera control to the player.
- `Home`: cancels follow, refits the current physical overview and returns to overview zoom.
- If the followed object disappears from the active local projection, follow mode ends rather than
  inventing an inter-system screen position.

## Acceptance

1. Fixed-tick ship motion no longer appears as direct frame-to-frame position jumps.
2. Moving ships do not make stationary objects or hull sizes drift because the overview auto-refits.
3. Physical metre ratios and gameplay coordinates are unchanged.
4. Double-click follow keeps tracking the same stable object as it moves.
5. Fixed-tick motion is interpolated by authoritative clock alpha, and the followed ship remains
   exactly at the whole-screen center without secondary camera lag.
6. Manual middle-button pan and `Home` deterministically cancel follow.
7. Existing zoom, hit testing, selection, save/load and engine-thrust visuals remain compatible.
