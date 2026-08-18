# Stage 19J.2 — Side Palette and Baseline Readability Implementation Record

**Parent:** Stage 19J — Tactical Validation Viewer, Scenario Coverage, Readability, and Inspection  
**Slice:** 19J.2 — Side palette and baseline readability  
**Implementation status:** COMPLETE IN PR; merge remains subject to the mandatory exact-head green CI gate

## 1. Result

The Stage-19J tactical viewer now receives battle-side identity through the immutable read-only presentation projection and renders ALPHA/BETA as immediately distinguishable tactical forces without introducing any viewer-owned combat state.

Authoritative flow remains:

```text
LiveTacticalBattleScenario.Side
→ CombatantRuntime.spec().side()
→ ScaledLiveTacticalSimulationProjection
→ TacticalPrototypeVisualSnapshot.ShipGlyph.side
→ TacticalPrototypeRenderer / HUD
```

The side value is never written back into tactical state.

## 2. Side palette

A centralized immutable `TacticalSidePalette` owns presentation colors.

Current schematic/debug palette:

| Side | Fill | Outline |
| --- | --- | --- |
| ALPHA | cool blue `rgba(0.16, 0.50, 0.68, 1.0)` | bright cyan `rgba(0.52, 0.94, 1.00, 1.0)` |
| BETA | warm red-orange `rgba(0.70, 0.27, 0.13, 1.0)` | bright orange `rgba(1.00, 0.66, 0.28, 1.0)` |
| NEUTRAL | muted blue-gray | pale neutral outline |

`NEUTRAL` exists only as an explicit compatibility presentation state. Scaled Stage-19J scenarios project every authored combatant as ALPHA or BETA.

## 3. Legacy compatibility

`TacticalPrototypeVisualSnapshot.ShipGlyph` now carries `TacticalSide`, but retains the previous public constructor shape as a compatibility overload. Legacy Stage-17.5 visual callers that do not carry side membership receive `TacticalSide.NEUTRAL` rather than having allegiance inferred from entity IDs or geometry.

This avoids both source churn and hidden presentation heuristics.

## 4. Ship readability

The prototype renderer now uses two nested filled silhouettes for each intact ship:

1. a larger bright side-colored outer silhouette;
2. a smaller darker side-colored inner silhouette.

This produces a reliable thick outline without depending on platform-specific OpenGL line-width behavior.

Minimum on-screen ship marker size was increased from `14 x 8 px` to `18 x 11 px` for manual validation readability at normal tactical zoom.

Wrecks remain neutral gray and do not keep live-side emphasis.

## 5. Non-color side cues

Color is not the only side cue.

Intact projected ships receive side-specific transverse hull marks:

- **ALPHA:** one transverse mark;
- **BETA:** two transverse marks;
- **NEUTRAL:** no side mark.

The marks rotate with the projected hull heading and remain semantically independent from color.

## 6. Ordnance readability

Kinetic, guided missile, interceptor, decoy and debris categories retain their existing type colors. Baseline body markers are enlarged and rendered with a pale high-contrast outer marker plus a smaller type-colored inner marker.

This remains presentation-only; physical body dimensions, collision geometry, guidance and damage are unchanged.

## 7. HUD

The normal Stage-19J HUD now includes separate colored force-state lines:

```text
ALPHA ALIVE <alive>/<authored>
BETA  ALIVE <alive>/<authored>
```

Alive counts are computed only from the immutable current visual projection (`!ShipGlyph.wreck`) and the scenario's authored side counts. They do not alter combat state.

The keyboard-cycled diagnostic line is explicitly named `DEBUG ACTOR` to distinguish it from the mouse-selected ship that will be introduced in Stage 19J.4.

## 8. Automated evidence

Regression coverage verifies:

- balanced 4v4 projects exactly four ALPHA and four BETA ships;
- scaled Stage-19J combatants never fall back to `NEUTRAL`;
- the legacy `ShipGlyph` constructor explicitly produces `NEUTRAL`;
- ALPHA, BETA and NEUTRAL fill/outline palette families are distinct.

Existing production runtime, deterministic replay, scenario-catalog and Stage-17.5 visual tests remain part of the repository-wide exact-head CI gate.

## 9. Authority boundary

Stage 19J.2 changes only:

- immutable presentation DTOs;
- read-only projection;
- renderer style;
- HUD diagnostics.

It does **not** change:

- movement;
- targeting;
- sensor truth or tracks;
- weapon authorization;
- projectile/guided body physics;
- damage;
- ammunition;
- power/heat;
- engineering state;
- deterministic fixed-tick authority.

## 10. Next slice

After this exact head is green and merged, Stage 19J proceeds to **19J.3 — Role-Based Schematic Ships**.

The accepted Stage-17.5 doctrine catalog already exposes five authored physical fit identities — kinetic line, missile strike, high-mobility beam, defensive EW and balanced control. Stage 19J.3 will classify visual silhouettes from those authored identities/capabilities rather than creating renderer-only combat classes or numeric bonuses.
