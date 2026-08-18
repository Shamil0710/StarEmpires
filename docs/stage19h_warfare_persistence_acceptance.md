# Stage 19H — warfare persistence and aggregate acceptance

**Status:** implementation slice. Stage 19 is **not complete** until the separate Stage 19I scaled live tactical-AI exit gate passes.

Stage 19H persists the strategic conflict information introduced by Stage 19G and proves deterministic continuation of the aggregate warfare causal chain through save/load. It deliberately does not move physical economy, ship state or institutional diplomacy into a new owner.

## 1. Persistence ownership

Stage 19H follows the separate-extension pattern already used by `Stage18IndustrialState`.

| State | Authoritative owner | Stage 19H behavior |
|---|---|---|
| hull/module/damage/ship consumables | Stage 17.5 ship engineering/persistence | referenced at runtime; not duplicated |
| kg-native station stock, finished ammo, facilities, yards, construction/process orders | Stage 18 industrial state | referenced at runtime; not duplicated |
| treaties, embargoes, trust, grievances, credibility | Stage 17 `FactionDiplomacyRuntime` / `WorldState` | referenced; never duplicated |
| conflict ID, actor/opponent IDs, named objectives/evidence | Stage 19H | persisted |
| escalation/mobilization political authorization | Stage 19H | persisted |
| cumulative actor-known destroyed/denied mass history | Stage 19H | persisted as historical information only |
| current operational ships / reaction mass / repair stock | physical owners | supplied fresh to every policy decision; never cached as conflict inventory |

`WorldState.CURRENT_VERSION` therefore remains unchanged. Stage 19H uses its own versioned `Stage19ConflictState` and `Stage19ConflictStateCodec`.

## 2. Why cumulative consequences are persisted

Current physical stock cannot reconstruct historical political knowledge such as “this actor has confirmed 600,000 kg of opponent cargo denied during this conflict”. Stage 19H stores that actor-known history because it is information, not a second cargo ledger.

The values are monotonic non-negative observed/confirmed deltas:

- confirmed own destroyed constructed mass;
- confirmed own denied/undelivered cargo mass;
- observed/confirmed opponent destroyed constructed mass;
- observed/confirmed opponent denied/undelivered cargo mass.

`Stage19ConflictRuntime.observe(...)` does not destroy ships or remove cargo. The physical owner must already have produced the outcome. The runtime only records what this actor now knows about that outcome.

## 3. Conflict state

One `ConflictSnapshot` stores a directed actor perspective:

- stable conflict ID;
- actor faction ID;
- opponent faction ID;
- `CRISIS`, `LIMITED_WAR` or `GENERAL_WAR` escalation;
- `NORMAL`, `PARTIAL` or `FULL` political mobilization authorization;
- conflict lifecycle state;
- canonically sorted named objective snapshots and actor-bounded evidence;
- cumulative observed consequences;
- last Stage-19G decision and tick.

Mobilization posture is categorization only. It grants no throughput, free ships, ammunition, repair, fuel/reaction mass or combat statistics.

## 4. Save format

`Stage19ConflictStateCodec` is a separate bounded deterministic binary payload with:

- Stage-19 magic and file-format version;
- Stage-19 conflict schema version;
- authoritative checkpoint tick;
- bounded conflict/objective/string counts;
- canonical state ordering;
- truncated/trailing-data rejection;
- atomic file replacement for disk writes.

There is no Stage-18-style content fingerprint in this slice because objective subjects and faction/conflict identities may be dynamic world identities rather than a closed authored catalog. Semantic validation remains in the conflict state itself and in the referenced world/content owners.

## 5. Runtime decision boundary

`Stage19ConflictRuntime.decide(...)` combines two kinds of information:

### Persisted actor-known history

- named objectives/evidence;
- escalation/mobilization posture;
- cumulative confirmed/observed conflict consequences.

### Fresh current physical readiness

- operational own combat ships;
- own reaction mass kg;
- current repair-material demand kg;
- compatible repair material available kg.

The second group is intentionally not saved by Stage 19H. After loading, callers must obtain it again from the authoritative physical state. This makes a conflict save incapable of restoring spent propellant or repair stock.

Policy outcomes apply only political conflict transitions:

- `ESCALATE`: one represented level upward and matching political mobilization label;
- `OFFER_SETTLEMENT`: lifecycle becomes settlement offered;
- `SEEK_SETTLEMENT`: lifecycle becomes settlement seeking;
- `ACCEPT_SETTLEMENT`: conflict resolves;
- `DE_ESCALATE`: one represented level downward, resolving if already at crisis floor;
- `HOLD`: preserves current political posture.

No Stage-17 treaty is created by these transitions. A legal settlement still has to be materialized through existing Stage-17 `WorldSimulation` diplomatic commands.

## 6. Aggregate warfare acceptance

`Stage19AggregateWarfareAcceptanceHarness` is intentionally strategic/aggregate rather than a second tactical simulator.

Canonical chain:

```text
mandatory corridor objective is observed unmet
→ CRISIS
→ native-unit mobilization demand is physically backed
→ no observed coercive leverage yet
→ ESCALATE to LIMITED_WAR
→ optional Stage-19 save/load checkpoint
→ already-measured Stage-19E outcome confirms 600,000 kg opponent cargo denied
→ actor records that observed physical consequence
→ Stage-19G chooses OFFER_SETTLEMENT
→ visible terms explicitly grant the mandatory objective
→ Stage-19G chooses ACCEPT_SETTLEMENT
→ conflict RESOLVED
```

The uninterrupted and checkpointed paths must produce byte-identical final `Stage19ConflictState` payloads.

## 7. Mobilization backing diagnostic

The aggregate harness exposes `MobilizationDemand` with native units rather than a scalar readiness score:

- finite manufactured ammunition rounds required / physically available;
- reaction mass kg required / available;
- compatible repair material kg required / available;
- completed replacement ships required / available.

`fullyBacked()` is only a boolean acceptance predicate. It cannot manufacture, transfer or reserve anything. A shortage in any represented category makes the diagnostic false instead of inventing readiness.

This diagnostic complements Stage 19G's current sustainment inputs without changing that merged API. Stage 19I remains responsible for proving that the production live tactical session actually consumes and reacts to these physical resources at scale.

## 8. Stage 19H acceptance requirements

1. conflict IDs and objective IDs are unique and canonically ordered;
2. codec encode/decode/encode is byte-stable;
3. malformed, truncated and trailing data is rejected;
4. conflict ticks cannot move backwards;
5. observed physical consequences can only grow through explicit non-negative deltas;
6. unknown objective updates are rejected without committing conflict mutation;
7. save/load does not persist current physical readiness;
8. a restored runtime supplied with insufficient current reaction mass seeks settlement;
9. resolved conflicts reject further mutation;
10. the canonical aggregate chain produces `ESCALATE → OFFER_SETTLEMENT → ACCEPT_SETTLEMENT`;
11. uninterrupted and mid-save/load canonical chains produce byte-identical final warfare state;
12. ammunition, reaction-mass, repair-material and replacement shortages are detected by native-unit backing checks;
13. no Stage-17 diplomacy, Stage-18 industrial inventory or Stage-17.5 physical ship state is duplicated or mutated by the conflict extension.

## 9. Transition to Stage 19I

Stage 19H closes persistence and aggregate strategic acceptance only. The Stage 19 exit gate remains **19I scaled live tactical AI**.

19I must use the production tactical AI and production physical combat stack in actual live/headless multi-ship sessions, including:

- 1v1;
- 4v4;
- 8v8;
- at least 32 ships under saturation;
- production sensors and degraded information;
- projectiles/missiles, point defense and EW;
- physical ammunition/propellant constraints;
- damage and retreat/disengagement;
- deterministic result/continuation.

`LiveTacticalSimulationSession` is the existing foundation to extend. Stage 19I must not create a duplicate combat engine.
