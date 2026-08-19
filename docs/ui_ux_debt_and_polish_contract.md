# Star Empires — UI/UX Debt and Production Polish Contract

**Status:** ACCEPTED PROJECT DEBT  
**Recorded:** 2026-08-19  
**Primary ownership:** Stage 23 — Polish / Release Candidate  
**Related validation baseline:** Stage 19J tactical validation viewer

## 1. Decision

The current UI is sufficient as a development, validation and debugging surface, but it is **not accepted as the final production UI/UX of Star Empires**.

Stage 19J deliberately improved tactical readability, selection, inspection and camera controls only to the level required for reliable validation of the authoritative combat runtime. That work must not be mistaken for final presentation quality.

A substantial UI/UX redesign remains mandatory before release.

## 2. Deferred production-quality work

The later UI/UX pass must revisit the product as a coherent whole rather than polishing individual debug panels in isolation. It must include at minimum:

- global visual hierarchy and information architecture;
- navigation between strategic, system, fleet, ship, station, economy, diplomacy, construction and tactical contexts;
- consistent layout/grid/spacing/typography/iconography;
- scalable panels and responsive behavior across supported resolutions;
- readable density management for large fleets, markets, logistics networks and generated worlds;
- production-quality tooltips, contextual help and onboarding;
- selection, filtering, sorting, grouping and search patterns shared across screens;
- clear state/alert/error/disabled/queued/in-progress feedback;
- accessibility and color-independent semantic cues;
- keyboard/mouse interaction consistency;
- production ship/projectile/VFX presentation replacing schematic/debug visuals where appropriate;
- removal or redesign of engineering/debug information that should not be exposed in ordinary player-facing screens;
- explicit distinction between player information, sensor-known information and developer/debug truth;
- usability testing of the full core loop from single ship to fleet/faction scale.

## 3. Scope rule for Stages 20–22

Stages 20–22 may add or extend UI only when required to:

1. validate a newly implemented authoritative system;
2. make a new player-facing mechanic minimally usable for development acceptance;
3. expose diagnostics needed for deterministic testing or balancing.

They must **not** spend substantial implementation time attempting to finalize the overall UI visual language or polish temporary layouts that Stage 23 is expected to replace.

Any UI introduced before Stage 23 should therefore prefer:

```text
correct information boundary
+ functional interaction
+ deterministic/readable validation
> final visual polish
```

## 4. Stage 23 exit implication

Stage 23 cannot be considered COMPLETE while the game still relies primarily on development/debug presentation for core player workflows.

The Stage-23 release gate must explicitly evaluate the production UI/UX as a first-class deliverable alongside performance, onboarding, save hardening and final presentation assets.

## 5. Non-negotiable authority boundary

Future UI redesign must preserve the architectural rule established earlier in the project:

> UI observes authoritative state and issues validated commands; it does not become a second simulation authority.

Production polish may reorganize, aggregate and visualize information, but it may not introduce player-only physics/economy rules, hidden resource grants, fabricated sensor truth, viewer-owned combat state or other shortcuts merely to simplify presentation.
