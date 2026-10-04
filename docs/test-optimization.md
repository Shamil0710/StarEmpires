# Test execution and timing

Use JDK 17, matching CI. On Windows use `mvnw.cmd` or an installed `mvn`.
The default verification gate runs every test with the existing 70% line and
60% branch coverage thresholds, strict Javadoc and desktop packaging.

| Purpose | Command |
| --- | --- |
| Complete verification | `./mvnw clean verify` |
| Fast local feedback, excluding slow tests and coverage | `./mvnw -Ptest-fast test` |
| Complete test suite without coverage instrumentation | `./mvnw -Ptest-no-coverage test` |
| Complete verification with two isolated JVMs | `./mvnw -Ptest-parallel clean verify` |
| Selected tests | `./mvnw -Dtest=ShipEngineeringCatalogLoaderTest test` |

The fast and no-coverage profiles are for local feedback. They skip coverage
checks even when invoked with `verify`; use the complete verification command
for the required gate. The CI verification command remains unchanged.

The `slow` annotations cover tests measured at two seconds or longer, plus
corpus, diagnostic, benchmark and soak classes. They are explicit source tags,
not exclusions inferred from changing runtime measurements. Add `@Tag("slow")`
to new expensive tests. The fast profile includes inexpensive integration tests.

The parallel profile runs classes in two separate JVMs with sequential tests
inside each JVM. Each fork writes an independent JaCoCo execution file. The
verification lifecycle merges these into `target/jacoco.exec` before reporting
and checking the unchanged coverage thresholds. Use `clean verify` when changing
profiles so older reports and coverage files cannot enter the result. Two JVMs
require more memory; the default remains a single JVM. Run separate Maven builds
in separate checkouts because build outputs and evidence directories are shared.

After a run, produce class and method rankings:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/test-timings.ps1
```

PowerShell 7 also supports `./tools/test-timings.ps1`. The script writes class
and method CSVs plus a Markdown summary to `target/test-timings`. CI publishes
the summary and retains these reports for seven days, including failed runs.
CSV durations use an invariant decimal separator. Reports include skipped tests,
failures, errors and modification timestamps.

Targeted runs leave older XML reports behind. Use a clean full run for an
authoritative suite total. Summed class durations are not elapsed build duration
when forks run concurrently. Compare identical commands, test selections and
JDKs on the same machine; use medians before interpreting small differences.

This change ports only optimization compatible with the current main branch.
Stage 23B campaign features and their cached test fixtures are not included.
Corpus seed counts, independent determinism repeats, simulation ticks and
evidence payloads are unchanged.

## Validation on main

Local validation on JDK 17:

| Check | Result | Elapsed |
| --- | --- | --- |
| Fast profile from a fresh checkout | 1988 tests, zero failures | 1 min 38 s |
| Parallel clean verification | 2308 tests, zero failures, one optional soak skipped | 15 min 14 s |

Complete verification passed strict Javadoc, desktop packaging and coverage
checks. Merged coverage contained two JaCoCo sessions: 84.74% lines and 64.35%
branches. These elapsed durations are individual measurements, not medians.


## Fixture reuse (2026-10-04)

`GeneratedCampaignFixture` retains a private encoded seed-one checkpoint. Each
caller decodes an independent checkpoint graph and, when needed, restores new
runtime owners. Restoring the baseline deliberately does not grant the ephemeral
new-game pilot-start authorization. Player persistence, carrier checkpoint, authority sidecar and
diplomatic-deadline tests reuse this baseline. The dedicated new-campaign and
production-craft creation tests still call the real creation path. Personal fleet
orders retain a separate encoded baseline built from a real new-game session after the ordinary pilot start, docking
and conserved purchase of the second ship; each test restores it independently.

Six V2 freight/capacity/frontier corpus tests share `Stage20V2CorpusFixture`.
It accepts only seeds 1..16 and the exact legacy V2 input bundle, and retains at
most 16 immutable production-probe results per JVM. Each diagnostic still runs its
own full downstream analysis, search budgets, assertions and evidence rendering.
The package-private diagnostic overload accepts the generation supplier explicitly;
public `evaluateCurrent()` still performs fresh production generation. No production
cache or global test switch is introduced. V1, resolved V3 and cadence-reviewed
generation remain independent. The service-cadence determinism test still executes
two complete independent corpus evaluations.

`LargeDemoGalaxyFactory.createFactionIdentities()` exposes the same immutable,
seed-independent identity metadata used by the 100-system world. Core-pair world
fixtures use it without generating 100 systems solely to discard everything except
the identities. B14 retains all 16 observation coordinates, 32 replacement probes,
paid-work/rejection checks and continuation checks. The existing B14 limitation
remains: its replacement probe uses a fixed seed and does not consume the paired
schedule's seed/permutation; changing the experiment is separate from this
performance change. Production small-craft fixtures also reuse their immutable
engineering catalog while creating fresh fitting authorities and craft states.

Fixture regression tests check runtime/allocator isolation, parity with independent
generation, immutable collections, rejection of other profiles/seeds, concurrent
readers and identity parity with full demo worlds. Forks retain independent caches.
No additional method-level parallelism or suite exclusions are enabled.

Validation uses targeted Maven invocations, not a full suite. Before/after timings
use the same 12-class selection, JDK 17, sequential fork and `test-no-coverage`.
Logs are `target/test-fixture-reuse-before.log` and
`target/test-fixture-reuse-after.log`; summed test-class times are reported separately
from total Maven elapsed time, which includes compilation. Complete verification
remains required before release.

One paired local run on JDK 17 produced the following class-time sums:

| Selection | Before | After | Speedup |
| --- | ---: | ---: | ---: |
| Five campaign/player/carrier classes | 281.690 s | 92.229 s | 3.05x |
| B14 paid replacement evidence | 123.000 s | 14.180 s | 8.67x |
| Six V2 corpus diagnostic classes together | 285.420 s | 51.307 s | 5.56x |
| All 12 classes, 48 tests | 690.110 s | 157.716 s | 4.38x |
| Maven elapsed, including compilation | 12 min 16 s | 3 min 04 s | 4.00x |

Both measured runs passed all 48 tests with no skips. The first V2 corpus class in
the optimized run paid for all 16 cached generations; individual later class times
must not be interpreted as standalone speedups. These are single runs, not medians,
and do not establish the elapsed time or coverage of the full suite.

All six corpus evidence text blocks matched exactly, and the complete B14 JSON
matched byte-for-byte, including provenance and every observation. The per-class
comparison is retained in `target/test-fixture-reuse-comparison.md`.

A separate JDK 17 `test-parallel` run with coverage passed 24 targeted tests across
the fixture isolation/parity/concurrency checks, large-demo identity acceptance,
core-pair physical recovery/B14, and small-craft hangar/flight-deck regressions.
Strict `javadoc:javadoc` passed in the same invocation. Its log is
`target/test-fixture-reuse-validation.log`. B14 bytes still matched the original
baseline after canonical identity ordering was checked. This validates the selected
classes in two isolated forks; it is not a full-suite parallel-safety or coverage gate.

The main backport includes only fixtures and tests already present on main. Stage 23 player persistence, fleet-order, and diplomatic-deadline tests remain on the Stage 23 branch; main does not acquire their production features. The new-game authorization assertion is specific to Stage 23.
