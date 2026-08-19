# Stage 19J.7 — Long-Run Tactical Acceptance Record

**Parent:** `docs/stage19j_tactical_validation_viewer.md`  
**Acceptance purpose:** close the Stage-19J runtime-hardening gate with reproducible long-run production-runtime evidence.

## 1. Accepted soak head

The first complete six-scenario long soak passed on feature head:

```text
8587dbc9fa8568afa420c60e8b68c63f2207ac23
```

GitHub Actions evidence:

- ordinary Java-17 CI run `32224299158` — **SUCCESS**;
- dedicated `Stage 19J Long Soak` run `32224299145` — **SUCCESS**;
- long-soak test result: `1` test, `0` failures, `0` errors;
- dedicated soak wall time reported by Surefire: about `150 s` test time / `02:46 min` Maven total.

The final documentation/roadmap closeout commit is required to rerun both gates again before merge, so this record is evidence of the physical/runtime matrix rather than permission to merge an unverified later head.

## 2. Six-scenario evidence matrix

| Scenario | Fixed ticks | Simulated time | Wall time | Alive | Max bodies | Max tracks | Track loss observed | Damage observed | Physical depletion observed | Fingerprint hash |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- | --- | ---: |
| 1v1 Legacy Duel | 2,600 | 130.00 s | 1.379 s | 2/2 | 14 | 1 | false | true | false | -496262291 |
| 4v4 Balanced | 2,600 | 130.00 s | 2.283 s | 8/8 | 56 | 4 | false | true | false | -934617017 |
| 8v8 Mixed | 2,600 | 130.00 s | 7.766 s | 16/16 | 264 | 8 | false | true | false | -441411106 |
| 8v8 Damaged / Depleted | 2,600 | 130.00 s | 5.764 s | 16/16 | 155 | 8 | false | true | true | 1573511336 |
| 16v16 Mixed | 2,600 | 130.00 s | 25.278 s | 32/32 | 1,261 | 16 | false | true | false | 1851528751 |
| 16v16 Saturation | **12,000** | **600.00 s** | **106.557 s** | 32/32 | **1,401** | 16 | **true** | true | false | -217948063 |

The saturation case therefore meets the explicit Stage-19J minimum of ten simulated minutes and exercises a normal actor-local contact-loss lifecycle during the run rather than only static fresh tracks.

No scenario produced an uncaught exception on the accepted soak head.

## 3. Runtime defect discovered by the closeout soak

The initial long-soak attempt did **not** pass and therefore Stage 19J was not closed prematurely.

After the 1v1 and 4v4 cases completed, the 8v8 run exposed:

```text
HeavyImpactResolver$OutsideCalibrationDomainException
velocityMps=54.362643882028635
massKg=5.0
minImpactVelocityMps=1000.0
```

A physical PD residual had remained in the projectile pool after an earlier protection interaction and later reached another protection boundary at only ~54.36 m/s. `LiveTacticalBattleWeaponRuntime` correctly detected geometric contact, but production `KineticProtectionRuntime` routed that low-energy residual into the strict high-energy `HeavyImpactResolver.resolve(...)` entry point.

The strict resolver was behaving correctly: it is intentionally forbidden to extrapolate its heavy-impact response surfaces outside the authored calibration domain.

### Accepted correction

The strict validation API remains unchanged in meaning:

```text
HeavyImpactResolver.resolve(...)
→ any calibration-domain exit remains fail-closed
```

Production kinetic protection now uses a separate bounded combat route:

```text
HeavyImpactResolver.resolveForCombat(...)
```

Only a projectile/residual whose mass remains inside the authored domain but whose current speed has fallen **below the minimum calibrated high-energy impact velocity** receives terminal outcome:

```text
SUB_CALIBRATION_STOPPED
```

For that outcome:

- no heavy-impact coefficient is extrapolated downward;
- no uncalibrated spall is invented;
- no uncalibrated internal damage is invented;
- no residual combat-effective projectile remains;
- above-maximum velocity remains a hard failure;
- projectile mass outside the authored domain remains a hard failure.

Regression coverage preserves the old strict below-domain failure test and separately proves the bounded combat route plus the remaining high/mass fail-closed cases.

## 4. Earlier manual-runtime defects closed during Stage 19J preparation

Stage-19J reopening was justified by real long-lived runtime states discovered through the interactive 32-ship viewer. Before this final soak gate, two additional defects had already been corrected and regression-covered:

1. **damaged active-radar energy balance:** independent floating-point scaling of radiated power, waste heat and electrical demand could violate an exact equality by only IEEE-754 rounding noise; the invariant now tolerates only a tiny ULP-scale numerical margin while rejecting real imbalance;
2. **stale sensor measurements/contact loss:** after the freshness horizon, historical measurements could remain in a non-empty collection even though no delivered fresh measurement remained; the live battle caller now treats this as normal track/contact loss rather than passing an empty-fresh set into strict fusion.

The final six-scenario matrix crosses the sensor freshness horizon in every non-saturation case and reaches 600 simulated seconds in saturation without reintroducing those failures.

## 5. Reproducibility

Local Windows closeout command:

```text
run-stage19j-soak.bat
```

Equivalent Maven command:

```text
mvnw.cmd --batch-mode --no-transfer-progress -Dstage19j.soak=true -Dtest=Stage19JLongSoakTest test
```

The dedicated GitHub workflow is `.github/workflows/stage19j7-soak.yml`.

The soak remains gated out of ordinary CI so subsequent unrelated pull requests do not automatically pay the 10-minute simulated saturation workload. It can be rerun explicitly via workflow dispatch or the local launcher when tactical/runtime changes warrant renewed long-run validation.

## 6. Stage-19J acceptance conclusion

The runtime portion of Stage 19J satisfies the closeout contract on the accepted soak head:

- all six canonical scenarios run through the same production tactical rules;
- all requested fixed ticks are executed;
- the 16v16 saturation scenario reaches 600 simulated seconds / 12,000 fixed ticks;
- damage states occur;
- the damaged/depleted fixture exposes real physical depletion;
- saturation exposes normal track-loss behavior;
- no accepted scenario throws an uncaught runtime exception;
- ordinary Java-17 CI is green on the same feature head.

Stage 19J may be marked COMPLETE only after the final closeout/status documentation head itself passes both ordinary Java-17 CI and the dedicated long-soak workflow and that exact head is merged into `main`.
