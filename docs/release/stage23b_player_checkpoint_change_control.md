# 23B / #412 — composed player checkpoint change control

Status: **IMPLEMENTED PERSISTENCE SLICE / 23B ACTIVE/PARTIAL**.

## Current native v14: paid refit and equipment rights (2026-10-06)

Current schema/file/runtime v14 supersedes the historical v13/v12 entries below. Refits support
schema 3 paid operator reservations; individual custody supports schema 2 actor ownership.
Root framing remains the same. Existing v1–v13 data migrates without issuing money, modules or
ownership tags; an older header cannot claim the new fields. Actor rights persist independently
of physical station/ship storage. Actual player completion records its owner; historical rights
are only made explicit through an authorized handling operation, never by migration. Known NPC
institution owners remain distinct from the personal actor. Used inputs and transfer sources
cannot consume another actor's private equipment.

Composed foreign-service checks require actual active personal ship, stationary physical berth,
civilian specialization, matching legal operator/treasury and exact installed compatible yard.
Completion pays held money once through the existing treasury transfer; pre-work cancellation
refunds it. Private module handling uses actual berth/capacity/shared endpoint work. Save/load
does not grant or settle anything by itself. Engineering scenario fixtures remain explicit;
ordinary economic acquisition and the remaining 23B paths are not yet proved complete.

## Current native v13: prepaid civilian repair (2026-10-06)

Native schema/file/runtime v13 supersedes the envelope version of the entries below. The repair
payload supports schemas 1/2; all other payload framing remains as in v12. Encoding owner-only
repair queues retains schema 1. Schema 2 adds optional original seller and real held milli-credit
balance to each physical repair order. This is a conserved money reservation, not a money source.
The amount must match the published v1 tariff against the exact physical work/input bill.
Unknown framing, altered amounts, missing material, wrong original owner/operator, absent treasury,
military stations and missing actual berth/yard are rejected. Versions v1–v12 cannot claim paid
repair money; they migrate with no reconstructed payment or fee. Genuine owner-funded work is retained.

Campaign debit and pre-work cancellation refund use the original order. Completion transfers that
held balance to the actual operator treasury through the existing money-transfer authority and
removes the completed order. Shared yard work, material custody and damage rules remain physical.
Save/load does not pay or refund on its own. Foreign refit remains unavailable. New-game NPC station
archives and paid-ship coordinate briefing use existing knowledge/player/journal payloads; restoring
historical saves does not run bootstrap or issue that briefing. This is a partial development slice.

## Existing civilian station acquisition policy v1 (2026-10-05)

PURCHASE_STATION uses the existing PlayerState OwnedStationRef representation and a conserved
transfer into the actual seller treasury. No outer/native schema change is needed beyond current
v11. Stock, wallets, industrial installations, yard states, legal registration and territorial
rights are retained. Sale price is an explicit authored policy, not a deduction from hidden stock:
volatile depot 50,000 cr, industrial station 70,000 cr, refinery 180,000 cr, high-tech 350,000 cr,
trade/logistics 150,000 cr, frontier multipurpose 200,000 cr; military/mining designs are not sold.
Offers require a personally discovered commissioned station and its matching existing legal seller.
Physical docking, actual funds, duplicate ownership and single-use authority-bound confirmation
are required. No actor, stock, power or offer is synthesized when an older file is restored.

New-game industrial market commissioning now binds faction to the actual asset owner, which can
differ from territorial control. Existing saved faction components are not reassigned; inconsistent
historical seller references do not acquire an offer. Targeted ownership/payment/native/UI and
neighbouring asset/handling regression plus strict Javadoc passed (9 tests, 2:04). Purchase fixture
changes arrival geometry only: money, stock, ownership and work are not supplied. Full ordinary
arrival, new station construction and working-yard acquisition/construction remain open.
Final targeted start/physical/payment/UI regression: 11 tests, BUILD SUCCESS, 2:05;
`target/stage23b-station-acquisition-final-validation.log`. Wrong seller, insufficient funds,
zero price, foreign and stale tokens reject before either treasury or personal money changes.

## Current finite product handling envelope v11 (2026-10-05)

Native schema/file v11 adds a bounded schema-1 finished-product transfer payload after module
handling. Native v1–v10 retain their original framing and receive an empty queue without granting
goods or work. Freight remains schema 7. The queue retains actual source storage, destination,
personal fleet, authored product/count, start tick, completed kilograms and work watermark.
Joint admission requires real source stock, ownership, a valid idle personal fleet, no conflicting
physical work and no future progress. Restore binds reservations to physical inventory; capture
retains raw counts and occupied mass without duplicating products.

LOAD_PRODUCT / UNLOAD_PRODUCT reserve source units and use consecutive shared physical ticks.
Endpoint handling budgets are shared with individual equipment handling; pause spends no work.
Completion publishes once without opening another interval, updates exact product provenance and
fitted cargo mass, then records the journal event. Cancellation releases stock but never refunds
work. Production UI shows actual available/reserved counts and pending progress in whole units.
Player/world transitions preserve this sidecar. Targeted real-command, queue, codec and neighbouring
module handling checks plus strict Javadoc passed (9 tests, 52.451 s). Initial station ownership,
docking and supplied stock remain explicit fixtures; ordinary acquisition is still required.

The subsequent v10-only statements below describe the earlier freight-only increment and are
superseded by this v11 outer payload.

## Subsequent finished-product cargo (owning freight schema 7, 2026-10-05)

Freight schema 7 appends bounded finished-product provenance after personal mining intents.
Each lot contains the shared globally unique cargo identity, existing FleetId, authored product ID,
positive whole count, actual loading station and original simulation time. Countable fresh goods
remain distinct from raw commodities and individually damaged/aged equipment. Counts exactly match
the physical hold; all three inventory owners contribute to fitted cargo mass and gross capacity.
Duplicate identities, unknown products, count mismatch, allocator rollback and historical schemas
containing finished goods reject. Existing fingerprint and local-kinematics adapters retain the lots.

Binary file format 1 is retained. Schemas 1–6 read their original layouts and acquire no product lots,
stock, equipment or work. The existing schema-6 carried-equipment mass field remains unchanged.
The composed M22.8 envelope remains native v10 because this increment belongs to its independently
versioned embedded freight sidecar; no new outer payload has yet been added.

The runtime uses the existing finite Stage18 product transfer. Loading removes actual station stock
and creates source evidence only after success; partial unloading preserves original source/time;
destruction removes the remaining goods and provenance without delivery or refund. Native campaign
composition requires its actual personal fleet owner and rejects loading from future simulation time.
The current full-campaign test uses an explicit supplied-product fixture, not manufacture or an
ordinary player loading command. Persistent multi-tick product handling and production UI remain
unfinished and must be integrated before this slice is considered a playable delivery path.

The storage handling-reservation seam retains raw physical counts/capacity and exposes only unreserved
units to competing operations. Reservations belong to a future pending-handling checkpoint and are
not yet used by production commands. Counter overflow is rejected before either transfer owner changes.

## Subsequent physical facility construction and resource redistribution (2026-10-05)

Managed `player-facility-construction:` orders use the existing Stage18 construction-order layout,
bill validator and industrial simulation watermark. Active orders require the persisted personal
station owner; completed orders require the matching actual facility installation. Managed work
cannot be ahead of the active world clock. No native framing, schema or content fingerprint changes
are introduced, and historical unmanaged construction evidence receives no work or resources.

Queue restoration binds delivered-material capacity reservations to the existing live station
storage. Reservations are owned by construction snapshots, not duplicated as available commodity
stock. Completion retains paid construction evidence and creates a disabled installation with zero
resource allocations. Repeated adoption preserves its later actual condition and allocations.

The explicit owner/berth-checked allocation command conserves the station's existing power, heat
rejection, labor and maintenance totals while withdrawing allocations from other installations.
It reprojects both facilities and yards. Existing installation snapshots persist those allocations;
loading does not perform the command, create resources or synthesize journal entries. Tests cover
native partial-work continuation, shared manufacturing/construction engineering budget, completed
installation round trips and resource conservation. Ownership and material supplies in the current
construction integration test are explicit fixtures, not proof of ordinary station acquisition.

## Subsequent manufactured mining-equipment admission

Normal generated-campaign creation and restoration now use the explicitly composed baseline plus
common authored freight/mining product registry. This registers existing physical module identities,
unit masses and storage classes; it does not seed finished inventory or replace commodity cargo with
countable products. A finite finished mining module can now survive the ordinary composed checkpoint
in an industrial station's oversized storage. Historical baseline products retain their definitions.

No save schema, framing, generation identity or numerical opening profile changes in this slice.
The persistence test starts with zero mining products in all generated industrial stations, transfers
one explicitly labelled supplied-module fixture through ordinary finite Stage18 logistics, and checks
an exact native save/load round trip. It is admission evidence, not a production player purchase.

## Subsequent personal mining intent (owning freight schema 5)

Freight schema 5 appends a bounded list of personal SI mining orders after the existing transport
orders in binary file format 1. Each order contains a FleetId, generated source ID, authored method,
requested maximum gross kilograms per tick and the last processed authoritative tick. Historical
schemas 1–4 read their original layouts and adopt with an empty list; they receive no work or mining
equipment. Schema 4's extracted cargo remains valid. A payload marked below 5 cannot carry the new
intent. Unknown/duplicate/non-idle fleets, unknown/incompatible sources or methods, missing personal
ownership and future processing ticks are rejected by their respective checkpoint boundaries.

Creating an order records the current tick and does not extract anything. Only a completed campaign
tick claims its interval; repeat claims and zero-delta frames cannot create work. Restarting at the
same tick also waits for a subsequent tick. Stop and destruction remove intent, not depleted-source
history or already mined cargo. World fingerprint and local-kinematics copies retain the new list.
Exact composed save/load continuation is checked against an uninterrupted campaign, not just a codec
round trip. The composed campaign schema, generation identity and numeric opening profile are unchanged.

The mining section now explicitly authors a 5,000 m local working envelope and a 1 m/s drift limit.
These SI content parameters do not reinterpret old map coordinates or allocate assets. Installed
equipment, damage, power/heat validity, active personal ownership, current system, physical contact,
absence of another order and travel/docking state are checked again at every work tick. Losing these
conditions stops work. Equipment acquisition/refit remains a separate unfinished production path.

## Subsequent physical extraction cargo (owning freight schema 4)

The personal extraction integration advances the owning physical-freight schema from 3 to 4.
Binary file format 1 and its field layout are retained: the new semantic distinction uses the
existing cargo-lot order, source endpoint and provenance fields. Versions 1/2/3 adopt to 4 without
adding cargo, equipment, ownership, money, sources or work. Their original market/manual and legal
affiliation restrictions still apply before adoption. New extraction provenance in a payload marked
as an older schema is rejected rather than interpreted as purchased material.

Personal extraction lots use `player-extraction-cargo:<fleet>` and `player-extraction:<source>`.
Market purchases retain their original identities. Composed checkpoints require the personal owner
for either category. Runtime restoration resolves the exact generated source, checks its output
commodity and bounds currently carried extracted mass by its actually depleted recoverable mass.
Unknown sources, commodity mismatch, undepleted-source material and absent personal ownership reject.
Physical market unloading consumes both kinds of personal lots while preserving retained provenance.

Stage18 remains the extraction executor, using the canonical ship hold through the existing station
production staging adapter. The caller supplies a finite shared processing interval; rejected
operations do not consume source, cargo, power/work/maintenance budget or lot IDs. The extraction
method's throughput is shared across calls within that interval. The actual player command still
requires lawful mining equipment, physical eligibility and campaign-tick allocation; this persistence
seam alone grants none of those and does not complete the playable mining loop.

The composed campaign remains v5; generation identity, new-game pilot/market numeric profile and
opening resources remain unchanged. Ordinary industrial state continues to own finite sources.

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


## Subsequent personal faction policy/diplomatic/territorial commands

Shared PlayerFactionManagementService policy, treaty, embargo and territorial transitions are
previewed on an isolated current checkpoint and confirmed via exact-state replacement. Existing
WorldState strategic/diplomatic fields own every result; no new persistent owner, save field or
clock is added. Doctrine/fiscal settings and legal claim intent persist without economic grants.
Party-to-party rights retain their direction when counteroffers/renewals change directory owner;
existing saved treaties remain unchanged. Unknown personal territorial targets and impersonated
actors reject. The same v5 envelope and opening profile are retained. Asset legal affiliation and
physical/strategic player loops remain separate outstanding work with their owning validation gates.


## Explicit personal asset registration: freight schema 3

The accepted freight origin and its ownership ordinal previously doubled as the required live
legal faction. A genuine shared affiliation changed WorldState but could not restore because the
freight sidecar still required the original faction. The owning freight schema advances **2 → 3**
with one `legalFactionId` mirror. WorldState remains the canonical legal affiliation; immutable
`stableFactionId`, original slot, hull/fit, IDs, exact geometry and cargo provenance are retained.
Binary freight file format 1 and the outer campaign v5 envelope are unchanged. No opening profile,
content identity, resource amount or accepted bootstrap allocation changes.

Schemas 1/2 adopt legal affiliation equal to their original faction without grants; current schema 3
roundtrips explicitly. Future schemas and falsely labelled old states with divergent legal mirrors
reject. Capture and restore require exact agreement with live World faction or the transit entity's
faction. Divergent freight affiliation also requires the actual persisted personal owner and the
same player faction, even with an empty hold. No silent correction of a one-sided affiliation is
allowed. The command delegates to PlayerFactionManagementService, then synchronizes only existing
owned IDLE freight mirrors in the isolated confirmation candidate; bootstrap routes reject.

`stage23b-freight-v2.s20f.gz` is the exact embedded S20F payload extracted from the genuine ancestor
campaign at commit `8444116936399a0d1975337f69a66661a287bd2e`. Its raw/compressed SHA-256 and
provenance are recorded in `stage23b-pilot-opening-v1.json`. The original campaign fixture is not
rewritten. The separate schema-1 compatibility test explicitly uses a synthetic schema-1 header
over the identical historical 1/2 layout; it is not presented as a genuine schema-1 save.

Local validation: 22 physical/travel/market/faction/materializer tests passed before the new owner
proof, followed by 18 affiliation/local/transit/station/player/scheduler/mid-approach tests, zero
failures/errors. Strict Java-17 Javadoc and desktop packaging passed with local coverage skipped.
The packaged llvmpipe UI journey registered two owned hulls, saved/reloaded, then completed policy,
actual hop, conserved profitable physical trade and another reload. Geometry fixtures are labelled.
A separate full exact-head CI is mandatory for this owning-schema batch; no 23B-completion claim,
merge, 23C or human B18 PASS follows from this engineering evidence.


## Personal fleet intent and one engineering interval

HOLD/MOVE/FOLLOW/ESCORT/PATROL reuse the existing PlayerFleetOrderState serialization and the same
campaign v5 envelope. No owning field/schema, profile, content identity or opening resource changes
in this batch. Patrol progress is derived from actual saved fleet placement and its saved cycle;
FTL readiness/cooldown and route fuel remain ordinary engineering/World state. No hidden patrol
clock is introduced. Arbitrary legacy MOVE floats and item-count economic orders are not converted
to exact SI targets. Assigned freight is excluded from personal physics, including default HOLD.

A transient World binding excludes existing externally advanced personal engineering intervals from
passive FTL recovery. A transient last-interval jump-participation marker prevents duplicate physical
execution on the arrival boundary. Both are reconstructed by the same campaign composition root
and preserve native World defaults elsewhere. Real post-arrival continuation/reload checks exactly
one cooldown interval and delayed patrol departure; destruction references reconcile through the
existing PlayerRuntime before delegation. Final local verify passed 32 tests with strict Javadoc
and desktop packaging, coverage skipped. This execution-owner change receives its own full CI gate
after the separately gated freight-schema-3 registration commit.

The schema-3 registration gate passed on exact commit `47d5ca799c5287313b8e2192151d1364499a07b2`:
CI #7829 ran 2387 tests, zero failures/errors, one skip, with coverage, strict Javadoc,
desktop packaging and B18 tooling green. A subsequent real-destruction regression reproduced a
save rejection in the personal cross-reference validator: reference reconciliation correctly
removed the lost hull, while its historical registration still demanded live ownership.
This fleet lifecycle batch permits a DESTROYED freight row to retain registration only with the
same persisted player faction. World/freight validation still requires the destroyed hull to be
absent; live empty-hold registration and removal of the persisted player still reject. No freight
schema/layout or resource changes accompany this correction, and no replacement hull is granted.
The final combined affiliation/NPC/personal-fleet regression gate passed 26 tests, zero
failures/errors/skips, including actual registered-hull destruction and an exact codec roundtrip.

## Count-based stock/recipe controls

New faction presentation rows draft the existing immutable stock/production command and explicitly
apply it through PlayerFactionManagementService. Existing WorldState owns all persistent intent;
the campaign v5 envelope, freight schema 3, content/profile identities and opening resources stay
unchanged. No legacy item-count field is reinterpreted as physical kilograms. Actual eligible own
consumers are disclosed, including zero for a newly founded faction. Shared Stage-17F tests prove
ordinary commodity configuration consequences; generated-player tests prove pure confirmation,
references/token guards, exact reload and preservation of existing Stage-18 physical storage.
The 14-test local gate, strict Javadoc/desktop packaging and final packaged graphical journey passed.
Full required CI of the current fleet execution-owner batch must finish before this next ref update.

## Executable personal manufacturing custody (2026-10-04)

Versioned `player-manufacturing:v1:` product-process rows now execute using the existing Stage18
whole-unit, fractional-progress and reserved-material fields. The native industrial simulation tick
is their completed-interval watermark. Historical unrelated rows remain passive; adoption creates
no job, resource, facility, ownership or completed work. Campaign and industrial framing stay v5/v1.

Reserved raw materials are removed from available stock and persisted exactly in their order.
They retain physical storage occupancy, reconstructed before live restore is exposed. Stock plus
custody must fit actual capacity, and reserved mass must equal the authored batch recipe. Owners
must retain the actual personally owned station reference; a presentation viewer grants no rights.
Future watermarks, invalid custody and absent personal station owners fail validation.

Actual recovered excavation cargo also provides durable personal sample evidence with the original
receipt time. It supplies resource indication and contacted position, without reserve/grade estimates
or foreign discovery data. These facts use the existing discovery codec and introduce no framing
change. Work and physical samples continue on the existing single campaign clock.

## Personal journal and notification acknowledgement (2026-10-04)

The composed M22.8 campaign envelope/file advances from **v5 to v6**
(`m22.8.generated-campaign.v6`). A separately bounded schema-1 journal payload follows the existing
player payload. PlayerState, freight v5, Stage18 industrial and discovery codecs are unchanged.
Native v1–v5 and the supported Stage20/21 migration chain receive an empty journal; migration never
reconstructs earlier transactions or issues notifications from retained balances/stock.

The journal retains the last 2048 actual commits with never-reused sequence IDs and authoritative
world ticks, plus a monotonic acknowledgement watermark. Empty-player history, future ticks,
disordered/missing retained suffixes, future acknowledgements, unknown event kinds, invalid sizes,
truncation and trailing bytes reject. Gameplay and acknowledgement use the ordinary pure preview,
unchanged-checkpoint guard and single-use confirmation. Reading notifications changes no assets,
money, materials, discovery or clocks. Read state and original event time survive exact save/load.


## Individual removed-equipment custody (2026-10-04)

Native M22.8 campaign schema/file advances from v6 to **v7**
(m22.8.generated-campaign.v7). A separately bounded schema-1 custody payload follows the
unchanged journal payload. Each removed module retains a unique custody identity, canonical
station, source asset, actual removal tick, module/mount assignment, integrity and service age.
It remains separate from pristine interchangeable product counts; damaged modules retain full
authored physical mass. Native v1–v6 receive empty custody without granting equipment; v6
retains its journal and acknowledgement exactly. Player, freight v5, industrial v1 and discovery
formats stay unchanged. Earlier v6 framing statements above are historical.

Composed restore checks actual station existence, authored module definitions, original removal
time and total storage occupancy including manufacturing reservations. Live restore rebinds
both custody owners before exposure. No history, ownership, repair, service or equipment is
synthesized. The physical refit handoff rejects insufficient removal storage before spending
work or incoming products. Player refit queues, equipment transport/reinstallation and postfit
freight capacity remain open; this is not a final Stage23B or RC declaration.

## 2026-10-04: reserved repair native v8

Current M22.8 development framing advances to schema/file v8 with a bounded schema-1
repair queue after module custody. Jobs preserve actual fleet/asset/station/yard references,
source fit/damage, authored physical material escrow, work and completed-tick watermark.
Composed restore validates ownership, authored recipes, tick and combined storage occupancy.
Native v1–v7 receive an empty queue without healing, work or resources; v7 retains equipment
and journal exactly. Player/industrial/freight formats remain independently unchanged.
Only an authorized own-station repair path is implemented. Commercial yard payments,
ordinary yard acquisition and player refit remain open. No final RC is declared.

## 2026-10-04: physical refit queue groundwork, campaign still v8

A separately bounded schema-1 ShipyardRefitQueuePersistenceCodec now supports exact pending
physical refits. It is not yet included in the campaign envelope; M22.8 remains schema/file v8.
The domain queue requires joint retention with actual ship, station and removed-equipment owners.
Incoming pristine modules and removal room are reserved before work. A caller-supplied pure
target cargo-capacity preflight is mandatory; completion retains current service ages and exact
removed damage. Player commands, freight capacity synchronization, cross-queue yard budgets,
composed checkpoint validation/migration, UI and journal integration remain open. No supported
campaign save is claimed to retain pending refit jobs yet. No final RC is declared.

## 2026-10-04: composed personal refit native v9

The preceding standalone-v8 refit limitation is superseded by native schema/file v9.
A bounded schema-1 refit payload follows the existing repair payload. Original ship/owner,
station, installed yard, authored work, source/target fitting and incoming equipment escrow
are validated with the same finite station storage as stock, production, repair and used modules.
The same ship cannot hold repair and refit jobs simultaneously. All adjacent player/world
composition paths retain pending refits. Native v1–v8 adopt an empty refit queue without grants;
the explicit v8 fixture preserves its actual pending repair and all earlier owners exactly.

The same-hull Union freight conversion retains actual cargo and lot provenance and reduces
its hold to eight million kilograms. Overfull target cargo rejects before equipment is reserved.
The new `module.civilian.miners.freight_excavation_section_v1` has separate ore and supplies
compartments (8 + 1 million kg) in the original nine-million-kilogram section storage envelope.
The earlier `module.civilian.miners.asteroid_excavation_section_v1` keeps its original interfaces
and capability definitions; it is not silently upgraded or shrunk. Both have finite paid recipes,
full physical module mass and authored integration/service work. Vocabulary extension changes
runtime engineering fingerprints; it does not alter canonical generated opening stock or assets.

Freight schema/file framing remains 5/1. The specifically admitted mining fit is an owner-bound
semantic extension of the existing fit-ID field; generated pool origin, ownership ordinal,
historical compatibility hull ID and legal affiliation remain exact. Live ECS engineering remains
the physical fitting authority. A mining-fit checkpoint must match its actual installed fitting
and a bounded eight-million-kilogram hold; restoring the original cargo fitting returns the legacy
compatibility fit ID and twelve-million-kilogram provisioned capacity. Arbitrary fit IDs/capacities
are not admitted. Freight and engineering changes publish with completed work and used equipment.

The real tick callback shares each installed yard budget with repair. Native save/load retains
partial work; pause grants none; cancellation returns incoming equipment without changing the
source ship. Removed hardware keeps actual damage and current service age, separate from pristine
products. Completion records one actual-tick journal event and no own-station monetary charge.
Commands and keyboard confirmation controls are connected through the ordinary pure-preview path.

Tests use explicit station/installed power/supplied-equipment fixtures and a conserved purchase
of an existing Union reserve. The final-tick integration test explicitly seeds prior completed work
to avoid hundreds of thousands of campaign ticks; full finite queue work is independently tested
by the domain queue. This is not evidence for ordinary yard acquisition or a complete full-duration
player production/travel/mining/sale journey. Commercial yards, used-equipment transport/reinstallation,
ammunition and other remaining Stage23B work stay open. No final RC is declared.

Recorded semantic fingerprints for this content admission (printed by the graphical probe):

- Freight engineering: `0f61cb1dc544389888809cc46148ef6a1ec4560877266c3d65272a780c66f6d7`.
- Civilian mining engineering: `66fe3f8a4e6b482bc9a481bf5ed526cc2b747eeb303e37649f5c050632af70c1`.
- Runtime manufacturing: `fe50c16ee8a88af2358c853d623f293d2fab3aabde6ae8d8f17a4b52a1d77edf`.
- Runtime shipyards: `ba41b1b89c6296e3d948920c791eee1d3dce655e0f0434cc0e35946653599c43`.

### Exact used inputs in refit payload schema 2 (2026-10-04)

The independently versioned refit sidecar now writes schema 2 inside the existing campaign
v9 envelope. Each job appends a bounded, deterministic map from changed target mount to
one exact custody row. Schema 1 remains readable with an empty used-input map; migration
grants no equipment or completed work. Campaign framing and content fingerprints are unchanged.
Composed validation rejects absent, changed or multiply reserved used rows. Reserved rows
remain in custody until completion; cancellation releases their reservation. Completion
withdraws the exact input and deposits removed hardware atomically with ship and storage
changes. Incoming integrity and service age are preserved; used inputs never become pristine stock.

### Individual equipment transport admission, native v10 / freight v6 (2026-10-04)

Native v10 appends a bounded module-transfer schema-1 payload after refits. Previous
native v1–v9 receive an empty transfer queue and no completed handling. Custody schema 1
retains its framing; an exact owner may now be an existing personal `freight-hold:<FleetId>`.
Freight schema 6 appends `carriedEquipmentMassKg` after each fleet's legal faction ID.
Older freight schemas read zero and cannot declare carried equipment. Its full authored mass
is added to commodity mass for hold capacity, physical engineering cargo and destruction loss.
Cargo lots continue to describe commodities; equipment provenance remains the individual custody row.

Joint validation rejects unknown or unowned holders, mismatched mass/engineering cargo,
duplicate reservations, future handling, missing source rows and station ownership conflicts.
Actual commands require the own docked stationary ship, a compatible endpoint and available
capacity. Partial work is paid on real intervals. During work conflicting cargo and departure
commands are rejected; completion emits one personal journal event. Destruction removes aboard
rows and cancels pending handling without a replacement or a return to the original station.
Content definitions, fingerprints and opening inventory are unchanged. This remains ACTIVE/PARTIAL.

## Native v12: physical yard construction (2026-10-05)

Native v12 appends bounded Stage23YardConstruction schema-1 bytes after finished-product
handling. Genuine v1–v11 framing remains supported and receives empty construction evidence,
without granting structures, inventory, engineering work or operating resources. The new
authored yard bill catalog has a separate fingerprint; existing industrial generation fingerprints
and historical starting inventory remain unchanged.

Current admission checks the actual personally owned station, storage, physical installation
location, completed-time watermark and combined facility/manufacturing/yard reserved capacity.
Pending work cannot already be installed. Completed evidence must retain the exact real installed
yard identity and design. Repeated adoption preserves later damage and allocation; new structures
start disabled with no power, work rate, staff or automation. Player/world transitions retain
the sidecar. Actual commands require own physical berth and the full physical bill; all construction
on one installed line shares the remainder of its manufacturing work budget.

The ordinary path to a sufficiently large store, delivery and operating yard remains incomplete;
completed-structure fixtures verify persistence admission only. This is ACTIVE/PARTIAL.

### Yard schema 2: bounded construction-site material custody

The outer native envelope stays v12. Yard schema 2 adds site/warehouse mode and a bounded,
unique exact commodity-mass map per order. Genuine schema 1 keeps its implicit full bill reserved
in the original station warehouse; it receives no second material grant. Encoding warehouse-only
states retains schema-1 layout. Unknown versions, malformed counts, duplicate/unknown materials,
excess delivery, future delivery and work before the full bill are rejected.

New player orders start with an empty site. Actual ticks transfer existing station stock through
the shared Stage-18 logistics capability/budget into bill-limited project custody. This cannot hold
general inventory. Engineering work begins after full delivery, using the same 1e-9 kg settlement
precision as ordinary storage to avoid stalling on floating-point residue. Material quantities,
partial work and ownership survive composed save/load. Cancellation before work returns only
actual delivery and rejects atomically if the original warehouse cannot receive it. Completed
material is part of the structure. Historical full-bill warehouse orders retain their original semantics.

Module handling, finished-product handling and site delivery share one endpoint interval budget;
new trade commands observe the actual handling event. Ordinary full-material acquisition and
operating resources for the constructed yard remain incomplete.
# Native v15: личные физические поставки (2026-10-06)

Текущий writer — `m22.8.generated-campaign.v15`. Root framing и payload lengths сохранены;
новый appended ObjectiveKind требует физического receipt при выполнении договора.
Native v14 и старше читаются без выдачи новых контрактов; подмена их header новым
личным supply predicate отклоняется. Receipt transient и никогда не восстанавливается
как право на повторную выплату; в checkpoint остаются escrow/status/reputation договора.
Производственная генерация supply offers и ordinary journey ещё не завершены.
