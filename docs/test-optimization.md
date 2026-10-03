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
