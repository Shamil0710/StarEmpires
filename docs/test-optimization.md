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
