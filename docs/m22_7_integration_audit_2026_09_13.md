# M22.7 integration audit — 2026-09-13

Status: **PARTIAL; NOT COMPLETE; Stage 23 remains PLANNED.**

## Repository integration and verified dependency

M22.6 PR #355 was merged at `9693ede2653c1fd187a904c406cc9750919a4496`.
Its exact head `c28cdba6adfee693fbeac32b22a85f4892295403` passed push CI
34759080373, PR CI 34759082123 and paired RC 34759080482. No submitted reviews
or inline review threads were present at merge. Human B18/B19/B20 dispositions
remain DEFERRED under #370/#361, never PASS.

PR #376 incorporates that main commit at `a6a7f8fe7ca06c47fd3ae596024900e75f737c89`.
The two conflicts were resolved by retaining the M22.7 campaign coordinator,
M22.6 character overlay and both the exact-faction and catalog-aware tactical import contracts.

A concrete persistence regression in the earlier #376 head is fixed by the existing
M22.6 implementation: the generated world uses strategic step 10 and remote budget 36,
whereas the old restore path reverted to WorldSimulation defaults. The resulting
remote-system tick mismatch caused `GeneratedCampaignSessionTest` to fail after reload.
The inherited schema now persists/restores those settings. A local probe compared the
original in-flight campaign against its restored copy after 40 further fixed ticks:
full checkpoints were equal. A native Stage-21I coordinator round trip was also equal.
The unchanged JUnit tests remain the CI regression gates.

## Acceptance inventory

| Gate | Actual evidence | Remaining work |
| --- | --- | --- |
| A: one campaign lifecycle | `GeneratedWorldCommandGame` binds `GeneratedCampaignCoordinator`; its session owns the physical world | Late Stage-21 snapshots are stored but the main loop still advances only `GeneratedCampaignSession` and freight. Autonomous Stage-21 goals/operations/NPC execution and final living-world projection are not fully connected. |
| B: simulation-time cadence | Session slices by fixed simulation ticks and dispatches freight every 0.4 simulation seconds; scheduler persistence inherited from M22.6 | Full combined-head CI; transport loading still dispatches immediately after the first positive load, so capacity/demand batching needs review. |
| C: production visual resolver | Existing shared production visual bindings and canonical faction identity handling are incorporated | Graphical capture/parity evidence is separate; human review remains deferred. |
| D: composed save | Native Stage-21I codec and supported migration are bound to F8/F9; corrupt decode occurs before replacing the active campaign | Persisting snapshots does not establish live continuation of every late-stage owner. |
| D/E: causal first hour | In-flight identity and restore probes; existing delivery test is retained | The current remote convoy cannot arrive within one simulation hour. No delivery/readiness/decision closure is claimed. |
| E: client smoke | Headless UI-model/command/save/load test in `GeneratedCampaignCoordinatorTest` | This is not a rendered desktop smoke and does not prove the full player economic/fleet/NPC journey. |
| E: performance | Reproducible default-world probe with equal 800-tick 1x/8x intervals | Dense-traffic stress, tactical rendering, memory growth and supported Windows graphical capture remain open. |
| F: documentation | README, canonical handoff link, this inventory, launcher/save truth and measured baseline | Final completion/Stage-23 entry manifest depends on the unresolved gates. |

## Physical first-hour contradiction

`GeneratedCampaignFreightDeliveryAcceptanceTest` selects the first accepted remote order,
then requires it to deliver in 450 presentation seconds at 8x (3600 simulation seconds).
For seed 1 the actual accepted order is:

- ID: `freight-order:essential:faction.alpha:0`;
- same fleet: `fleet:1`, source `hub.11`, destination `hub.16`;
- planned one-way duration: **992668.6551899784 simulation seconds** (275.741 hours);
- round-trip duration: **1985505.3103799566 seconds**;
- ordinary departure phase: `MOVING_TO_JUMP`, start tick 8, end tick 3656543;
- fixed step: 0.1 seconds; departure approach alone lasts approximately **101.57 hours**.

The probe advanced the ordinary campaign for a full simulation hour and observed the
same physical loaded fleet still in its lawful departure phase. This is not evidence
that the freight FSM should teleport or that its physical deadline should be shortened.
The failed delivery assertion was not removed, skipped or reclassified as passed.

The existing `TransportOrderState` requires at least two distinct systems, so this
remote freight order cannot simply be relabelled as a local delivery. A starter local
trade/contract journey must use the existing player/local movement/transfer authorities
and be deliberately connected to the generated client, or a supported waiting/time
progression design must cover long physical trips. Neither change may silently retune
the frozen world-distance/propulsion baseline. Long-horizon convoy acceptance and the
first-hour player journey need distinct physical scenarios and both remain mandatory.

## Launcher and save truth

- Production candidate: `run-generated-world.bat` → generated-world desktop client →
  `GeneratedCampaignCoordinator` → `GeneratedCampaignSession` → existing generated runtime.
- F8 writes `saves/generated-campaign.s21i`; F9 reads it, or falls back to the older
  `saves/generated-world-runtime.s25` when the native file is absent.
- Native decoding/migration and restoration finish before `bindCampaign` replaces live references.
- `run-tactical-sim.bat`, `run-live-tactical-sim.bat`, `run-scaled-live-tactical-sim.bat`,
  `run-tactical-acceptance.bat`, `run-stage19j-soak.bat` and asset/graphics validation launchers
  are development/validation tools, not an alternate completed production campaign.
- `run.cmd` remains a separate legacy/demo entry point; do not advertise its state as
  equivalent to the generated campaign's native Stage-21I save.

## Baseline reproduction and limits

`tools/campaign/GeneratedCampaignBaseline.java` is a standalone diagnostic, not gameplay code.
With a normally packaged desktop JAR, run from the repository root:

```sh
java -Xmx2g -cp target/star-empires-1.0-SNAPSHOT-all.jar tools/campaign/GeneratedCampaignBaseline.java EXACT_SOURCE_SHA
```

Use the actual shaded JAR filename produced by the current pom if it differs.
The diagnostic records supplied source SHA, Java/OS/architecture/CPU count/heap,
world fingerprint, population, scheduler settings, encode/restore timing, UI-model
projection cost and equal simulated intervals at 1x/8x. Its monotonic wall clock measures
cost only and never supplies a delta to simulation. Failed checkpoint equality or a
wrong tick count aborts the probe.

Measured output: `benchmarks/m22_7_headless_baseline_2026_09_13.txt`.
All current production sources were compiled locally using the JDK compiler module.
Because Maven Central DNS was unavailable, compilation used dependencies from the
successful main CI artifact for `9833cda4`; current source classes/resources were placed
first on the classpath. This is an auxiliary diagnostic, **not a substitute for Maven
clean verify, JaCoCo, Javadoc or exact-head GitHub CI**. The baseline uses the default
36-system, 26-freighter world, not an invented dense-traffic stress workload. It is a
single-process preliminary measurement, not a latency percentile or optimization gate.

## Stage 23 boundary

No Stage-23 implementation or completion is authorized by this audit. Existing PR #372
remains a separate VFX foundation. Final UX/accessibility, art/audio/VFX polish,
performance hardening, save recovery UX, packaging and RC playtests retain their original
Stage-23 scope. M22.7 must first close live composition, causal execution and its own evidence.
