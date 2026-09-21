# M22.8E — exact Stage-19 tactical integration for small craft

Status: **IMPLEMENTED CANDIDATE — depends on accepted M22.8D and exact-head CI**  
Parent gate: `M22.8 — Carrier / Small-Craft Operations`

## Scope

M22.8E connects deployed persistent small craft to the already accepted Stage-19 exact-local tactical
authority. It does not introduce a fighter combat engine, aggregate wing HP, statistical combat
resolution, hidden carrier immunity or virtual replacement.

The tactical chain is:

```text
persistent SmallCraftId + exact engineering state
→ D mission is ACTIVE / RETURNING
→ craft is physically outside bay occupancy
→ exact local kinematics supplied by local-flight authority
→ ordinary Stage19ExactTacticalEncounterResolver
→ ordinary sensors / tracks / datalink / EW / weapons / PD / decoys / damage
→ exact result
→ stale-safe commit to the same SmallCraftId or permanent destruction
```

## Shared Stage-19 authority

`SmallCraftTacticalEncounterService.production(...)` uses:

- `Stage22CorePairWeaponRuntimeCatalogLoader.loadCombined()`;
- the same `Stage22CorePairEngineeringCatalogLoader.loadDefault()` fingerprint used by the
  M22.8 small-craft registry;
- `Stage22CorePairProtectionCatalogLoader.project(...)`;
- `Stage19ExactTacticalEncounterResolver`.

Small craft and external exact combatants such as a carrier or escort are imported into one resolver
and one local combat roster. The bridge therefore does not possess separate fighter-only movement,
tracking, fire-control, point-defence, guided-weapon, beam, kinetic, EW, decoy, shield or damage
coefficients.

## Physical admission

A persistent small craft may enter E only when:

- its `SmallCraftId` exists;
- it is not present in M22.8B bay occupancy;
- D has exactly one active mission for that identity;
- mission status is `ACTIVE` or `RETURNING`;
- exact finite local position and velocity are supplied;
- the exact installed fit exists in the accepted tactical engineering catalog.

External combatants are exact detached physical payloads. E returns their final state to the
pre-existing owning authority; E does not silently mutate a carrier/fleet registry it does not own.

## Deterministic tactical identities

Tactical IDs are encounter-local deterministic identities assigned from a canonical participant
ordering. They are only Stage-19 runtime keys and never replace `SmallCraftId`, fleet identity or
campaign identity.

All returned rows are validated against the exact admitted roster before any small-craft commit.

## Stale-safe atomic commit boundary

Detached Stage-19 simulation cannot mutate persistent small-craft state directly.

Before any result is committed, E rechecks every participating small craft:

- the same `SmallCraftId` still exists;
- its complete physical state still equals the pre-exchange state;
- it remains outside bay occupancy;
- the same D mission remains active;
- Stage-19 did not replace the installed fit;
- survivor physical state still passes the production fit/footprint authority.

Only after the whole result passes preflight are persistent consequences applied.

## Survivor continuity

For a surviving craft, the exact returned:

- reaction mass;
- ammunition and other physical consumables;
- power/thermal runtime;
- shield continuity;
- subsystem/compartment damage;
- maintenance/weapon runtime state

replace the previous engineering state on the **same** `SmallCraftId`.

No value is reset by dematerialization.

Encounter-local final kinematics are returned as `LocalFlightState` for the local-flight authority
to retain/commit in its own state.

## Permanent destruction

A catastrophically destroyed craft is removed from `SmallCraftRegistry` through
`removeDestroyedCraft(...)`.

Destruction:

- does not rewind the allocator;
- does not allocate a replacement;
- does not restore ammunition, propellant or materials;
- marks the owning active D mission `FAILED`;
- leaves later physical production/replacement to M22.8G.

The destroyed identity cannot be reused by later production.

## External carrier / escort interaction

`ExternalCombatant` exists so a carrier, escort or other ordinary exact physical actor can share the
same Stage-19 encounter with small craft. Its state is returned as `ExternalOutcome` and must be
committed by the authority that owns that actor.

This deliberately proves that carriers receive no E-layer immunity or class-name modifier.

## Acceptance evidence

Automated tests cover:

- survivor engineering state commits back to the same persistent identity;
- tactical consumable expenditure is not reset;
- destroyed craft disappears permanently and its mission becomes `FAILED`;
- allocator watermark does not rewind and destroyed IDs are never reused;
- embarked craft is rejected before the tactical authority runs;
- a production integration case runs a persistent small craft and an external exact combatant through
  the real `Stage19ExactTacticalEncounterResolver`;
- external exact state is returned rather than silently committed by E.

## Deferred intentionally

- M22.8F — carrier-group doctrine, CAP/QRA/strike scheduling, recovery and withdrawal AI;
- M22.8G — physical manufacture, delivery, ammunition/propellant/spares supply and replacement;
- M22.8H — Stage-21 strategic operation composition and strategic readiness projection;
- M22.8I — player-facing carrier UI;
- M22.8J — production small-craft hulls/fits/assets;
- M22.8K/L — representative balance/counterplay and dense-wing performance evidence;
- M22.8M — full campaign save/load/migration hardening for deployed mission/local-flight checkpoints;
- M22.8N — integrated causal-chain acceptance and soak.
