# Test execution and timing

The default Maven verification gate runs the complete suite, enforces the existing
70% line / 60% branch coverage thresholds, builds Javadoc and packages the desktop
application. Local feedback profiles are explicit and do not change the CI gate.

| Purpose | Command |
| --- | --- |
| Complete verification | `./mvnw clean verify` |
| Fast local feedback, excluding `slow` tags and coverage | `./mvnw -Ptest-fast test` |
| Complete test suite without coverage instrumentation | `./mvnw -Ptest-no-coverage test` |
| Complete verification with two isolated JVMs | `./mvnw -Ptest-parallel clean verify` |
| Selected tests | `./mvnw -Dtest=GeneratedCampaignPlayerMissionIntegrationTest test` |

Use JDK 17, matching the CI configuration. On Windows use `mvnw.cmd` or an installed `mvn`. Use `test-fast` and
`test-no-coverage` for local feedback only: they do not enforce coverage even if
invoked with `verify`.

Slow tags cover classes taking at least two seconds in the initial saved
Surefire reports, plus corpus, diagnostic, benchmark and soak classes. They are
explicit source annotations, not exclusions inferred from changing runtime
measurements. New expensive integration tests should also carry `@Tag("slow")`.
The fast profile still includes integration tests that were inexpensive; it is
not a unit-only suite.

Parallel execution uses separate JVMs, with sequential tests inside each JVM.
Each fork writes its own JaCoCo execution file; `verify` merges these into the
usual `target/jacoco.exec` before reporting and checking coverage. Use a clean
verification run when switching profiles or measuring complete coverage so that
old reports and fork files cannot contribute to the result. Two JVMs need more
memory; keep the default single fork on constrained machines. Do not run two
separate Maven builds concurrently in the same checkout: evidence directories
and build outputs are shared.

## Measurements

After a run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/test-timings.ps1
```

On PowerShell 7, `./tools/test-timings.ps1` also works. The script writes class
and method CSV rankings and a Markdown summary to `target/test-timings`. CI
publishes the summary and retains the CSVs for seven days, including failed runs.
The report includes skipped tests, failures and report modification timestamps.
Targeted invocations leave old XML reports behind: use a clean full run for an
authoritative suite total. Summed class times are not elapsed build time when
forks run concurrently.

For comparison, run the same command, JDK and selected classes on the same
machine, without other Maven builds running. Record Maven's elapsed build time,
class times and whether compilation occurred. Use the median of repeated runs
before treating a small difference as a performance improvement.

Campaign command fixtures reuse immutable initial checkpoints and restore fresh
runtime owners for every invocation. Dedicated creation and faction-foundation
tests still exercise the original paths. Corpus seed counts, repeated independent
determinism runs, simulation ticks and evidence payloads are unchanged.

Initial local measurements on 2026-10-03:

| Check | Before | After |
| --- | ---: | ---: |
| Player mission integration, 15 tests, with JaCoCo | 97.73 s | 34.08 s |
| Unchanged service-cadence corpus determinism, with JaCoCo | 71.50 s | 71.47 s |
| Fast feedback profile, 2002 tests, including test compilation | — | 1 min 21 s |
| Complete parallel test suite, 2397 tests (one optional soak skipped) | — | 19 min 36 s |

These are individual runs, not medians. The controlled targeted before/after
commands used the same class ordering and JDK 24. JVM warmup and machine load
still affect class timings. The
unchanged corpus retains both full independent evaluations. The initial saved
full-suite XML reports summed to about 30 minutes, but were not a new, clean
wall-clock baseline; do not compare that sum directly with a parallel build's
elapsed time.

The complete local test run used JDK 24 and produced zero failures. Its `verify`
then failed at strict Javadoc because that JDK emitted 100 warnings in existing
production sources. Continuing `verify` on JDK 17 with `-DskipTests` reused both
forks' coverage data and passed Javadoc, packaging and both coverage thresholds.
The default strict Javadoc gate was retained. A new full JDK 17 test run was not
performed as part of this measurement.

Merged full-suite coverage was 84.92% of lines and 64.65% of branches, with two
JaCoCo sessions. All 35 generated JSON evidence files parsed successfully.

The same two-class JDK 24 invocation took 1 min 58 s with JaCoCo and 1 min 57 s
with `test-no-coverage`. This single comparison did not show a material gain from
disabling instrumentation. Coverage remains enabled in required verification.

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
