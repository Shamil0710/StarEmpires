# 23B / #412 — composed player checkpoint change control

Status: **IMPLEMENTED PERSISTENCE SLICE / 23B ACTIVE/PARTIAL**.

## Reason and authority

#412 requires durable player identity in the ordinary generated campaign. The accepted v4 envelope
could not preserve the existing `PlayerState`. This fix composes that existing contract; it does not
introduce a new wallet, faction, cargo, movement, discovery or command authority. The unchanged
playable player-field codec is shared with a bounded, independently identified player-only payload.

## Version and supported adoption

| Owner | Before | After | Adoption |
| --- | --- | --- | --- |
| Composed generated file/schema | 4 | 5 | native v4 retains every original payload; player stays absent |
| Composed runtime | `m22.8.generated-campaign.v4` | `m22.8.generated-campaign.v5` | explicit native identity checks |
| Player payload file | absent | 1 | absent player is explicitly encoded; no backfill grant |
| Existing player-field schema | playable v5 | playable v5 | reused without semantic change |
| Stage-21 and carrier A/B/C/M payloads | accepted versions | same versions | unchanged accepted chains |
| Generator/content/core/save envelope | accepted identities | same identities | no generated-world or content changes |

Native M22.8 v1/v2/v3/v4 and previously supported Stage-20.5/21 formats are still supported.
Every historical source produces a current envelope with **no initialized player**. Neither
migration nor authority restore grants ownership, wallet, affiliation, discoveries or orders.
Encoding preserves canonical existing player lists. An initialized zero-wallet player is distinct
from absence. Input bytes are never modified; failed decode/validation cannot change the live
campaign because the ordinary client finishes candidate restore/projection before replacement.

## Validation and limits

The current envelope rejects unknown fleet/system/faction/project/order references, future threat
observations and impossible current docking. Historical discovery is retained even if its object
has moved or been destroyed. The player-only codec rejects wrong magic/file/player schema, invalid
presence, negative balance, truncation and trailing bytes. Embedded payload lengths are compared to
remaining input before allocation.

`GeneratedCampaignPlayerPersistenceTest` includes all player fields, canonical round trip,
absence/zero-wallet distinction, corrupt identities/presence/balance/truncation/trailing bytes,
v4 adoption without mutating original bytes, Stage-21 adoption, unknown references, future intel,
invalid docking, ordinary generated authority recapture and deterministic continuation. Existing
carrier/native migration and production UI tests remain required regression gates.

This batch stores exact existing player data only. It does not prove personal starter ownership,
physical cargo trading, physical direct control, live command validators, NPC offers, UI command
journeys or the B18 human gate. Those requirements remain in #412/#370 and in 23B. The existing
PlayerRuntime and generated clock are not both advanced; this batch installs no second runtime.

Local production compilation uses Java 17 against an existing packaged dependency set. Ten new
regression methods passed a lightweight assertion harness. Maven dependency resolution is blocked
locally by Maven Central DNS; the exact-head GitHub `clean verify` is the full acceptance evidence.

No stage-completion merge or 23C implementation is authorized by this persistence slice.


## Subsequent NPC lifecycle composition (same v5)

The later 23B NPC batch binds existing-player contract preview/submit and escrow settlement to this
same envelope. No field, schema identity, content or generator profile changes. Only the existing
mission service can settle a reward; its exact transfer updates the existing player's personal
balance, retaining every other field. Native/historical adoption remains absent and non-granting.
Capture/restore never expires or pays a contract; only later authoritative ticks process deadline,
event-relevant or bounded periodic mission work. Existing overdue offers expire/refund on resumed
clock work. This closes an NPC lifecycle seam for already initialized players, not new-pilot
ownership, generated NPC/offer creation, physical trade/control or the remaining player commands.
Current contract and validation: `docs/ui/stage23b_production_ui_consolidation.md`. #412/#370 remain open.

Player reference hygiene at completed ticks reuses the existing `PlayerRuntime` rules through a pure
API shared with the original playable runtime. Destruction/project completion/invalid docking may
therefore reconcile existing references without adding assets, rewriting migration input or
advancing a second clock. The physical-loss save regression is part of the NPC integration suite.


## Subsequent explicit pilot and physical cargo composition

The explicit new-game purchase, original physical hold and ordinary market wallets are now composed
into the same PlayerState/v5 envelope. Character creation completes before Save is available in the
fresh client, and neither restore nor capture enables the transient initial offer. The ordinary
world projection remains exact across save/load; the client explicitly requests the creation-only
invitation. No second wallet, cargo store or simulation clock is persisted.

The owning freight schema advances from 1 to 2 for manual acquired-cargo provenance; old schema 1
adopts without resources, unsupported schemas and false schema-1 manual lots reject before live
replacement. A composed personal owner is required for manual cargo. The new-game release alias
advances to `se-gen-2`, retaining underlying saved generation identities. Numeric profile/fingerprint,
funding sources, fixed corpus including four upstream rejections, evidence and remaining limits are
recorded in `docs/release/stage23b_pilot_start_profile_v1.md` and the UI evidence document. This is a
partial integration gate and does not authorize a 23B-completion merge.

## Subsequent direct travel, asset progression and faction foundation

Direct player hops use the same persisted world jump FSM. Unassigned freight arrival mirrors now
track committed local relocation, preserving the hold/lots; no freight/campaign schema or generated
initial condition changes. Completed campaign ticks reuse personal location discovery. Additional
reserve purchases and stationary local control handover use the existing ownership/progression
services, with no replacement hulls or duplicated control integrator.

Faction foundation uses the existing pure PlayerFactionFoundationService and a typed re-composition
of the same world inside Stage-20/21/M22.8. All adjacent owners and migration provenance are retained;
a validated replacement binding is adopted without elapsed time. The zero-treasury, zero-territory
identity and existing player affiliation persist through the existing WorldState/PlayerState fields.
Own-treasury capitalization/return use existing conserved financial services and ledger evidence.
No new monetary, political, cargo, save or clock authority is introduced. These are additional
implemented command slices; remaining physical/strategic loops and human B18 acceptance stay open.


## Subsequent global market profile without owning schema changes

New-game markets are explicitly versioned by their existing persisted ordinary IdentityComponent.
Only confirmed fresh v2 opening funds all physical endpoints; ancestor unversioned markers preserve
their exact static policy/home-only liquidity and never gain resources during restore. The retained
genuine ancestor checkpoint proves this adoption boundary. SI scarcity quotes read existing physical
stock/capacity and reuse MarketSystem's dimensionless rule, preserving legacy item units. Release
alias `se-gen-3` records the changed opening profile; saved underlying generation IDs stay unchanged.
The default explicitly founded player faction is now a governed PLAYER_CREATED identity, preserving
existing WorldState identity allocation/save/collision rules without creating it at generation/load.
Full evidence and incomplete stage scope remain in the production UI document and profile v2.
