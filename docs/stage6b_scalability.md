# Stage 6B — Scalability remediation and 100h baseline

## Scope

Stage 6B closes the scalability gate left by Stage 6A without changing economic rules, deterministic semantics, save schema, fixed-step timing, or the authoritative ECS pipeline. All optimizations were accepted only after the exact GitHub Actions artifact passed `clean verify` and deterministic benchmark output matched the prior accepted state.

The target scenario is `economic-scale-100x500-100h`:

- 100 stations;
- 450 autonomous traders;
- 50 miners;
- 500 economic agents total;
- 3,600,000 fixed ticks = 100 simulated hours;
- deterministic root seed `188638464`;
- sampling every 6,000 ticks.

## Initial Stage 6A bottleneck

The original Stage 6A scale world was functionally correct but too slow for a practical 100h baseline. Controlled probes established roughly 34.5 seconds for 10,000 ticks, about 293 ticks/second. Entity count remained stable, so the problem was not runaway world growth.

Profiling and segmented probes showed several independent costs:

1. `MarketDirectory.selectConsumers()` repeatedly sorted and recalculated supplier/consumer distances.
2. `Money.maximumAffordable()` performed avoidable searches for common affordability cases.
3. Late economic saturation caused 450 traders to synchronize into unsuccessful route replanning; most candidates were rejected only after expensive affordability work.
4. `MarketDirectory` rebuilt immutable snapshots and copied station arrays even when live market state had not changed.
5. JFR allocation sampling showed large transient allocation pressure from station snapshots and copied `int[]`/`float[]` arrays.
6. Mining repeatedly scanned Ashley families for candidates that had already been obtained from those same live families, and inventory validation traversed 64 item slots more than once.

A representative late-market diagnostic at 25,000 ticks inspected 92,160 route candidates: 81,464 (~88%) were ultimately rejected because the consumer could not finance the sale, 10,440 failed the price gate, and only a small remainder reached other constraints.

## Accepted remediations

The final Stage 6B branch applies the following exact optimizations:

- bounded top-K consumer selection instead of full consumer sorting per supplier;
- fast and analytically bounded `Money.maximumAffordable()` paths protected by a 20,000-case randomized equivalence test plus extreme-value cases;
- early one-unit liquidity rejection before expensive affordability calculations;
- deterministic per-snapshot station-distance reuse;
- `MarketDirectory.revision()` that advances only when the exact live station state changes;
- live-state comparison before allocating/copying new immutable station snapshots;
- exact negative-only route-result memoization keyed by market revision and the complete immutable `FleetTradeProfile` planning state;
- separate validation for saved mining references versus candidates already obtained from live Ashley families;
- single-pass mining inventory validation/free-capacity checks.

The failed-route cache is transient. It is not part of persistence and is invalidated automatically by any exact change in market state or planner-relevant fleet state. Regression tests explicitly prove invalidation after both station-liquidity and fleet-wallet changes.

One attempted optimization — rebuilding `MarketDirectory` only on ticks where an IDLE fleet was immediately due to plan — was tested twice and rejected because it produced negligible or negative throughput improvement despite preserving deterministic output. It is intentionally not part of the final branch.

## Scaling evidence

After the accepted changes, a 100,000-tick segmented probe on the final production code remained stable with:

- entity count: ~606;
- asteroid count: ~6;
- ledger entries: stabilizing at 27,339;
- late saturated throughput: roughly 6,900–7,100 ticks/second.

The ledger did not grow without bound in this scenario, so Stage 6B did **not** introduce a separate streaming/aggregation ledger model. That remains a future option if a later world model demonstrates unbounded authoritative-history growth.

## First full 100h baseline

The first complete `scale100h` run used the exact GitHub Actions production artifact built from commit:

`135cf8393a500afb84194d8c1ef923beb53287aa`

Build verification used Temurin JDK 17. The long-run diagnostic environment used OpenJDK 21.0.11 on Linux with Intel Xeon Platinum 8573C-class CPU resources. Machine-dependent fields are therefore a baseline for comparable environments, not universal hardware requirements.

Machine-readable report:

`docs/benchmarks/scale100h-stage6b-baseline.json`

SHA-256 of the recorded report:

`331d71d42dae7d8b4c9aafbed0d2cea435bac39b712fe9d3e87c20d506aa1c57`

Key results:

- final tick: **3,600,000 / 3,600,000**;
- samples: **600**;
- entities: **606**;
- stations: **100**;
- traders: **450**;
- miners: **50**;
- economic agents: **500**;
- ledger entries: **27,339**;
- trade transactions: **6,720**;
- traded units: **148,339**;
- traded money: **24,384,903,229 milli-credits**;
- total mined: **7,382 units**;
- total delivered by miners: **7,159 units**;
- production transform cycles: **10,640**;
- production output: **59,839 units**;
- stockout observations: **62,366**;
- unmet-demand unit observations: **14,981,947**;
- wallet Gini: **0.7255364843548987**;
- initial money: **30,450,000,000 milli-credits**;
- final money: **30,450,000,000 milli-credits**;
- money conserved: **true**;
- non-negative inventories: **true**;
- resource accounting conserved: **true**;
- resource accounting delta by item: **[0, 0, 0, 0, 0]**;
- throughput: **6,662.2300866007 ticks/second**;
- simulated seconds / real second: **666.2230185875665**;
- wall-clock: **540.359602296 seconds**;
- measured heap delta: **35,936,296 bytes**.

## Regression thresholds

Correctness thresholds are hardware-independent and hard:

- scenario reaches all 3,600,000 ticks;
- station count remains 100 and economic-agent count remains 500 for this versioned scenario;
- money conservation is `true`;
- resource accounting conservation is `true` with zero per-item delta;
- inventories remain non-negative;
- unexpected deterministic metric drift requires explicit review and baseline update rather than silent acceptance.

Performance is machine-dependent. For a comparable JVM/host profile, Stage 6B defines a regression investigation threshold at **70% of the recorded throughput baseline**:

- baseline: `6662.2300866007 ticks/s`;
- investigation floor: **4663.56106062049 ticks/s**;
- equivalent wall-clock ceiling for this exact scenario on a comparable environment: **771.9422889942858 seconds**.

Shared GitHub-hosted CI does not fail solely on this absolute wall-clock value because runner hardware and contention vary. CI instead enforces deterministic/correctness gates and the shorter scale/supply-chain scenarios. Full `scale100h` comparisons should use comparable runtime conditions.

## Broken supply-chain detection

Stage 6B includes a CI regression that creates the same 100x500 authoritative world, disables mining, removes initial ore from inventories, and compares it with the normal world under the same seed for 3,000 ticks.

A representative probe produced:

- normal mined units: **850**; broken: **0**;
- normal unmet-demand observations: **199,726**; broken: **257,142**;
- normal trade transactions: **4,231**; broken: **2,782**;
- money/resource conservation remained valid in both worlds.

The automated test asserts the directional economic damage rather than hard-coding every metric: broken mining must be zero, unmet demand must increase, and trade activity must fall while conservation invariants stay green.

## Stage 6B exit criteria

Stage 6B is complete when the final exact branch HEAD passes push and pull-request CI, the recorded 100h baseline remains attributable to the same production code, and this document plus the roadmap are merged with the implementation. Stage 7 must not begin before those merge gates are complete.
