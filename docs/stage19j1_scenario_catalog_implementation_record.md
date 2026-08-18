# Stage 19J.1 — Scenario Catalog and Unified Launcher Implementation Record

**Parent:** Stage 19J — Tactical Validation Viewer, Scenario Coverage, Readability, and Inspection  
**Slice:** 19J.1 — Scenario catalog and unified launcher  
**Implementation status:** COMPLETE; merge remains subject to the mandatory exact-head green CI gate

## 1. Result

Stage 19J no longer has a saturation-only interactive session contract. One presentation-side catalog now selects fresh authoritative production tactical runtimes for the complete mandatory scale ladder while preserving the existing combat authority chain.

Canonical interactive scenarios and stable CLI keys:

| Scenario | CLI key | ALPHA | BETA | Runtime basis |
| --- | --- | ---: | ---: | --- |
| 1v1 Legacy Duel | `duel` | 1 | 1 | accepted `legacyDuel()` production runtime |
| 4v4 Balanced | `4v4` | 4 | 4 | accepted `balanced4v4()` production runtime |
| 8v8 Mixed | `8v8` | 8 | 8 | accepted `mixed8v8()` plus finite guided specialist feeds |
| 8v8 Damaged / Depleted | `8v8-damaged` | 8 | 8 | accepted `mixed8v8()` degraded-readiness fixture |
| 16v16 Mixed | `16v16` | 16 | 16 | accepted `mixed16v16()` exact-local setup |
| 16v16 Saturation | `saturation` | 16 | 16 | existing Stage-19I saturation factory and compact formation objective |

The scenario catalog is presentation/control metadata only. Scenario selection does not create separate combat engines and does not add viewer-owned movement, sensing, targeting, weapons, damage, power, heat, ammunition or engineering state.

## 2. Accepted physical setup reuse

### Mixed 8v8 guided specialists

The viewer factory reuses the accepted exact-local finite-feed setup:

- strike/decoy: `191301`, `191403`;
- interceptor: `191302`, `191406`;
- strike ammunition: `ammo.test_anti_ship_missile_2t_v1`;
- interceptor ammunition: `ammo.test_interceptor_750kg_v1`;
- decoy ammunition: `ammo.test_radar_repeater_decoy_300kg_v1`;
- eight physical rounds per authored specialist feed.

### Damaged / depleted 8v8

The viewer factory reuses the exact accepted readiness state from `LiveTacticalDamagedDepleted8v8AcceptanceTest`:

- entity `191304`: mount `utility_datalink` starts at `0.10` integrity;
- entity `191400`: retained reaction-mass fraction is `0.0`.

No alternative viewer-only damage/depletion values are authored.

### Mixed 16v16

The non-saturation 32-ship case reuses the accepted exact-local specialist set:

- strike/decoy: `191501`, `191601`;
- interceptor: `191506`, `191605`.

The saturation case retains the broader Stage-19I specialist population and compact formation objective already accepted before Stage 19J.

## 3. Unified launch contract

Canonical Windows launcher:

```text
run-tactical-sim.bat
```

Without an argument it presents a six-scenario console menu. Direct launch is supported through:

```text
run-tactical-sim.bat duel
run-tactical-sim.bat 4v4
run-tactical-sim.bat 8v8
run-tactical-sim.bat 8v8-damaged
run-tactical-sim.bat 16v16
run-tactical-sim.bat saturation
```

The desktop application canonical argument is:

```text
--tactical-sim=<scenario-key>
```

`--scaled-live-tactical-sim` and `run-scaled-live-tactical-sim.bat` remain compatibility paths for the saturation scenario so existing Stage-19I usage is not broken.

Invalid canonical scenario keys fail explicitly and list the valid keys.

## 4. Session/reset contract

`ScaledLiveTacticalSimulationSession` owns immutable selected-scenario metadata plus presentation scheduling state. It creates the selected scenario through the canonical catalog and shared Stage-19 factory.

`R` / `reset()` now:

1. recreates a fresh runtime for the **currently selected scenario**;
2. restores authoritative tick `0`;
3. restores presentation speed `X1`;
4. clears pause state;
5. never silently falls back to saturation.

The no-argument session constructor intentionally continues to select saturation for source/backwards compatibility with existing Stage-19I parity/exit tests.

## 5. HUD

The Stage-19J viewer now identifies the selected scenario at the top of the window, including:

- display name;
- authored total ship count;
- stable CLI key.

This is the first Stage-19J readability field and establishes scenario identity before the side-color, role-silhouette, selection and inspection slices.

## 6. Automated evidence

Added regression/acceptance coverage verifies:

- the catalog contains exactly six scenarios in canonical order;
- declared ship counts are `2, 8, 16, 16, 32, 32`;
- each scenario creates the declared ALPHA/BETA population;
- fresh factories produce deterministic initial fingerprints;
- every scenario drives the same live-session surface;
- reset preserves scenario identity and deterministic initial state;
- CLI lookup is stable/case-insensitive and invalid keys enumerate valid values;
- the damaged/depleted factory preserves the exact accepted `191304` datalink and `191400` reaction-mass initial state;
- degraded 8v8 replay remains deterministic.

The first PR verification run compiled successfully and executed **1151 tests with zero failures/errors**, but correctly failed the repository's strict Javadoc-warning gate because new public APIs lacked complete parameter tags. Those warnings were fixed without changing combat behavior. A subsequent exact-head Java-17 verification passed the complete build pipeline.

The PR must still be merged only after the final exact head that includes this implementation record has a green CI result, in accordance with the repository manual merge gate.

## 7. Next slice

After 19J.1 is merged, Stage 19J proceeds to **19J.2 — Side Palette and Baseline Readability**:

- centrally project ALPHA/BETA presentation identity;
- ALPHA cool cyan/blue family;
- BETA warm orange/red family;
- stronger ship outline/readability against the dark tactical background;
- improved basic ordnance/target readability;
- no new combat authority.
