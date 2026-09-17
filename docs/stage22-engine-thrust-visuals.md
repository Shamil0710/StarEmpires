# Stage 22 runtime engine thrust visuals

This slice keeps propulsion presentation separate from ship speed.

## Authority

- Ship velocity and heading remain kinematic presentation inputs.
- Engine activity is not inferred from velocity.
- The current generated-world runtime exposes commanded local propulsion only while an ordinary fleet jump is in `MOVING_TO_JUMP`.
- `ARRIVING`, `JUMP_PENDING`, transit and ordinary drift remain engine-off until their simulation authority exposes an explicit thrust command.
- The normalized presentation contract is `propulsionFraction` in `[0,1]`; the current authority is binary (`0` or `1`) and can later accept continuous throttle without changing the renderer contract.

## Rendering

When `propulsionFraction > 0` the generated-world sprite renderer composes, in order:

1. a restrained additive blue-white exhaust plume behind the physical hull;
2. a compact additive bloom at the stern/nozzle region;
3. the ordinary base hull sprite;
4. an aligned additive `_emissive.png` layer when the selected Stage-22 production asset actually supplies one.

Missing emissive masks do not suppress propulsion readability: generic bloom and plume remain available for legacy/fallback and incomplete production packs.

All effect dimensions are presentation-only and proportional to the rendered physical hull envelope. They do not alter collision, scale authority, ship fitting, sensors, economy, save identity or movement physics.
