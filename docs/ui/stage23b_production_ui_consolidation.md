# 23B — production UI consolidation

Status: **ACTIVE / PARTIAL**. This is an implemented presentation foundation, not a Stage-23B
completion record. Stage 23C implementation remains blocked. Required remaining work is tracked by
[#412](https://github.com/Shamil0710/StarEmpires/issues/412) and the existing human gate
[#370](https://github.com/Shamil0710/StarEmpires/issues/370).

User scheduling decision (2026-10-04): human acceptance is deferred until after Stage 23
development. The [player checklist](../release/stage23_player_acceptance_checklist.md)
records the required ordinary-play checks. Continue implementation and engineering validation;
do not treat the deferred manual run as passed evidence or as a reason to pause development.

The [current engineering remainder](stage23b_remaining_work.md) supersedes historical gap lists
below; dated sections retain the evidence and limitations of each implementation slice.

## Implemented architecture

The ordinary `GeneratedWorldCommandGame` now binds a `ProductionUiWorkspace` and
`ProductionUiProjector` to the existing `Stage228CampaignAuthority`. The existing current-system
and galaxy renderers, physical map scaling, mouse wheel zoom, middle-button pan and fleet follow
remain the map authority consumers. All other screens use the same list/inspector layout.

Navigation stores separate selection, query, category, ordering, list offset and inspector offset
for each screen. The Back stack retains at most 64 surface visits. Returning to a surface restores
its selection and preferences. Breadcrumbs use display labels, and an inspector remains available
when its selected row has been filtered out. Missing/deleted selections do not resolve to another
object. Replacing a campaign clears transient navigation.

The generic table searches names, state summaries and inspector values; it supports exact category
filters, stable name/category/newest ordering and viewport-sized rendering. Internal stable IDs
break sort ties and route commands but are not required as user input. Data shrink clamps the
visible window. The three density presets change row spacing and retain every inspector value.
Inspector scrolling uses clipped pixel offsets, so scrolling past a section header no longer
silently drops all the section's values, and wrapped long values can be read in full.

Strategic projections refresh at most four times per presentation second; physical map objects
continue through the existing motion smoother. This is a refresh cadence, not performance-budget
acceptance or a new simulation clock. Projection failures pause the authority and show an error
instead of silently continuing an invalid operation. Save/load failures preserve the current
campaign; candidate restore and candidate projection both finish before replacing live owners.

## Surfaces and exact existing sources

| Surface | Implemented source | Limits still relevant to acceptance |
| --- | --- | --- |
| System / local view | `GeneratedWorldUiModel`, existing physical map renderer | Observation/selection/focus; no newly invented player movement authority |
| Galaxy | Existing topology/current-system map | Actor-bounded facts are in the linked Intelligence surface; overlay rendering/route controls are not final |
| Factions | `Stage21IFinalLivingWorldUiProjector`, same-viewer treasury/control/fiscal projection | Foreign private treasury/goals are withheld; no faction control is granted by choosing a viewer |
| Military | Existing physical military projections and viewer-owned Stage-21D groups/Stage-21E operations | Readiness retains existing fail-closed unobserved crew/service semantics; order submission is not bound |
| Logistics | Existing Stage-20 physical freight/order projection for the viewer | Source/destination, cargo and routing are inspectable; player trade/dispatch is not bound |
| Industry | Stage-18/20 current-system stations, extraction sources and storage; viewer Stage-21G replacement demands | Current open system is explicitly identified; construction/repair/service actions are not bound |
| Contacts | Existing bounded Stage-21H NPC/mission rows, personal contract commands and directed reputation | Existing initialized PlayerState required for personal commands; fresh generated roster/start is still missing; no NPC or funded contract is synthesized by UI |
| Ships | Existing viewer military/freight engineering/cargo sections and durable M22.8 craft/assignment/mission summaries | This is a faction-observation roster, not personal ownership; interactive fitting/carrier commands remain unbound |
| Journal | Existing actor-filtered Stage-21 event projection | Searchable current authoritative history; no new durable notification archive or invented events |
| Intelligence | Existing actor-filtered discovery/access/control/transition/operation overlays | Preserves source visibility; layout labels/localization are not final |
| Menu | Existing pause/save/load plus controls and safe seed/version/tick diagnostics | No paths/save contents exposed; full accessibility/settings/rebinding belongs to 23C |

Each consolidated row appends its provenance/explanation to the inspector. Treasury is an ordinary
balance; rates convert basis points to percent; logistics uses physical kilograms and simulation
seconds; group readiness is the accepted minimum of member observations. Military fitting now
shows power and thermal margins in watts. The old fabricated “starting-system guard / local patrol”
order labels have been removed: the inspector directs users to real command-group data.

## Navigation and keyboard path

- F1/F2: system/galaxy; F3/F4/F5: factions/military/logistics; F6: contacts; F7: menu.
- Other surfaces are reachable by mouse or Tab/Shift+Tab followed by Enter.
- Esc goes back, or opens the menu when there is no previous surface.
- Up/Down chooses a list row or selectable map object; Enter activates the focused control.
- Ctrl+F opens search; editing captures keys so typing a space, number or F does not send a session
  or gameplay command. Enter/Esc exits text editing. Search on a map opens its Intelligence list.
- PgUp/PgDn scrolls a list; Ctrl+PgUp/PgDn scrolls inspector values.
- +/- zooms maps; Left/Right pans maps; Home restores the local overview; C returns to the selected
  object's physical position when available. These are presentation operations.
- Ctrl+C and the shared “Мой корабль” toolbar action return from any surface to the actual active
  personally owned ship, open its current system and enable the existing physical camera follow.
  Search editing captures this shortcut. An absent active ship or transit/loss reports a refusal
  without changing the current surface or substituting another ship.
- Double-click and the inspector's “open on map” action revalidate current ordinary FleetId
  placement. Transit/lost ships have no local position: the button shows that refusal before
  dispatch, and a stale accepted click is checked again by the application.
- Pause/save/load are also mouse/keyboard-focus controls. Menu Exit is explicitly reachable.

This describes implemented input paths. The software-OpenGL engineering smoke below exercises
them; it does **not** constitute a passed B18 human acceptance charter.

## Accepted player-start decision

The user selected **independent pilot**, not faction commander, during this 23B session. The intended
start is personal ship/wallet, then trade/missions, personal fleet and optional faction foundation.
This decision must survive the handoff; selecting the presentation viewer is not an implementation
of this start. No starter ship, wallet or faction asset is granted by this foundation.

A direct bind to a generated freighter is not a lawful shortcut: `PlayerMarketService` shares the
legacy `InventoryComponent` with `TradeController`, while `Stage20FreightRuntime` tracks physical
cargo via `Stage18StationStorage`/cargo lots. Both are accepted existing contracts; putting personal
trade on that fleet without reconciliation would leave two independent cargo balances. Likewise,
`PlayerRuntime.advanceFrame` advances the world clock whereas `GeneratedCampaignCoordinator` owns
the composed session clock; composition must use a prepare/reconcile seam rather than advance both.
The corrective integration must resolve these conflicts through the existing owners before player
commands can be considered release-ready.

## Authority and persistence boundaries

No new economy, diplomacy, ownership, construction, fleet, mission or carrier authority is added.
The knowledge viewer is selected deterministically from existing actor identities. It gives read
access only; it must never be interpreted as player affiliation, personal ownership or permission
to command that faction. The UI never grants the observer all that faction's assets or wallet.

The initial presentation batch preserved M22.8 v4. The subsequent **23B player checkpoint batch**
explicitly increments the composed file/schema to **v5 / `m22.8.generated-campaign.v5`**. Its optional
player payload delegates every field to the existing `PlayableWorldStateCodec` player format; it
contains no duplicated world, cargo, economy or clock. The composition root restores and recaptures
that immutable checkpoint data exactly. It exposes no arbitrary wallet/ownership mutation endpoint.
This supplies the durable player contract; the subsequent NPC mission slice binds existing contract commands and escrow settlement. Physical player movement/cargo/start and the other command domains are still unbound.

Native v1–v4 and supported Stage-20.5/21 checkpoints migrate with `playerState == null`. Absence is
distinct from an initialized player with a zero wallet. Migration does not generate a starter ship,
wallet, affiliation, discoveries or orders. Original bytes are untouched. The existing A/B/C/M and
Stage-21 payloads remain unchanged. Current v5 requires its bounded player payload, exact native
identity and EOF. Corrupt presence, versions, negative balances, future observations and absent
fleet/system/project/order references are rejected before the live client changes campaign owners.
Historical local discoveries may remain after an object moves or is destroyed; current docking
requires a live market in the active fleet's current system.

UI navigation/query/density remain transient presentation preferences. The independent-pilot new
start and ongoing player lifecycle are still mandatory integration work. The existing player direct
control writes float ECS transforms, while Stage-20 materialization owns exact hierarchical physical
kinematics; it cannot be installed unmodified as proof of generated physical movement. A production
binding must compose that exact position authority as well as the physical cargo and single-clock
seams described above. See `docs/release/stage23b_player_checkpoint_change_control.md`.

## Acceptance evidence and remaining gaps

Local validation uses Java 17 compilation against dependencies from the successful exact
`bdc807fa95d547608868d29c1d261abb02413a0e` main build, strict Javadoc, and an ordinary seed-1
campaign capture/save/load probe. Full local Maven cannot resolve Maven Central in this execution
environment. Those local checks are **not** a substitute for exact-head GitHub `clean verify`.

Automated regressions added:

- `ProductionUiWorkspaceTest`: retained Back state, bounded history/text, inspector-value search,
  filter/query composition, stable tie ordering under input permutation, large-list virtual pages,
  shrink/overflow clamping, keyboard viewport selection, density information retention, input editing,
  corrupt identity/page rejection and reset;
- `GeneratedWorldCommandGameErrorTest`: readable error categories never expose exception paths
  or embedded save payloads;
- `ProductionUiCampaignIntegrationTest`: ordinary composed campaign, projection non-mutation,
  actor-bounded private economy/freight, honest empty contacts/history, explanations, exact codec
  save/load projection equality and unknown-observer rejection.

The seed-1 probe yielded two faction rows, three military rows, thirteen freight rows, thirteen
current-system industrial/resource rows, sixteen ship rows, four intelligence rows, zero contacts
and zero timeline rows. These counts are diagnostic evidence for this seed, not content gates.

### Software OpenGL engineering smoke

On 2026-10-01 the real client rendered at 1280×720 on an EGL pbuffer with Mesa llvmpipe
(LLVM 20.1.2, 256 bits). `tools/qa/Stage23BSoftwareGraphicsSmoke.java` exercises all eleven
surfaces through Tab/Enter only, then through rendered mouse hit targets; it also exercises row
selection, list/inspector scrolling, search capture, Back and native save/load. All assertions
passed with no OpenGL errors. Captured frames were visually inspected. This exposed and corrected
breadcrumb/toolbar overlap and missing bullet/ellipsis font glyphs.

Reproduce on Linux with Mesa EGL and the ordinary packaged desktop JAR (this temporary probe uses
reflection solely to attach the software graphics context and verify presentation state):

```sh
EGL_PLATFORM=surfaceless java -cp target/star-empires-1.0-SNAPSHOT-all.jar tools/qa/Stage23BSoftwareGraphicsSmoke.java
```

The probe directs save/load to a fresh temporary directory and does not overwrite the normal
campaign save. Framebuffer PNGs are temporary engineering evidence and have the OpenGL lower-left
origin. It does not simulate player commands or prove display-driver compatibility, localization,
legibility at other resolutions, complete camera behavior, performance budgets or human causal
comprehension. B18 remains open; a software smoke must not be promoted to its acceptance verdict.

Mandatory gaps remain in **23B**, not deferred to a future feature stage:

1. **PARTIAL:** durable generated-campaign `PlayerState` storage/restore is implemented in v5;
   explicit lawful independent-pilot starting ownership/funding and live player services are still
   missing. Supported adoption remains non-granting. A stored player is not proof of executable loops.
2. One runtime composition of player movement/docking/trade/mining/fitting/construction/fleet,
   faction/carrier command services, validated preview/submit, domain commit and ongoing lifecycle.
   Existing NPC mission commands/settlement are now bound for initialized players; this does not
   make the remaining immutable sidecars executable or supply a new-player start.
3. A complete production path for NPC/mission/discovery/reputation and causal notification history,
   rather than an empty migrated sidecar or curated test-world alternative.
4. Final route/overlay presentation, every displayed strategic value's human-readable glossary,
   context-action forms and irreversible-action confirmation where needed.
5. Explicit loading/error/empty visual states and camera presets/return-to-player coverage across
   all surfaces; a status-bar error and local selected-object follow are not full acceptance.
6. Human keyboard-only/mouse campaign smoke and B18 causal-comprehension evidence. The software
   OpenGL navigation smoke above covers only the implemented presentation foundation.
7. All stage acceptance/exit criteria and exact-head CI before a **stage-completion** merge.

A green foundation CI proves only this change's regression gate. It does not close 23B, waive #412
or #370, or authorize implementation of 23C.

### Player checkpoint batch validation

`GeneratedCampaignPlayerPersistenceTest` covers every existing player field, absence versus a zero
wallet, deterministic bytes, corrupt/truncated/future payloads, native v4 and Stage-21 adoption,
unchanged original migration bytes, generated-world cross-reference rejection, exact authority
recapture and deterministic campaign continuation with a nonzero existing wallet. Local Java-17
production compilation and a lightweight assertion harness passed; that harness is not a Maven or
JUnit-engine result. The full exact-head CI gate is required before accepting the batch.

### Ordinary diplomatic deadline integration

The ordinary coordinator now invokes the existing `DiplomaticLifecycleService.expireDueProposals`
once after each completed campaign tick, before actor reviews. It stores the service result only
when an actual expiry occurs. Capture, restore, paused/zero/fractional frames and empty diplomacy do
not normalize or rewrite an unchanged historical sidecar. The service also rejects a linked ordinary
Stage-17 treaty offer. Accepted proposals/treaties retain their existing semantics and are not
expired by the response deadline.

`GeneratedCampaignDiplomaticDeadlineTest` covers exact deadline, pause/fractional/capture purity,
pre-deadline save/load, 8x deterministic continuation, unchanged empty sidecars, linked treaty
rejection, idempotent expiry, overdue legacy offers and accepted-treaty preservation. This closes a concrete deadline
lifecycle gap; it does not expose a player/AI negotiation command loop or close #412.

The lifecycle batch passed local Java-17 compilation, strict Javadoc and a lightweight assertion
harness (seven new deadline methods plus five existing coordinator methods). Re-running the ten new
player-persistence and five existing native-codec methods against this combined source also passed.
The real client passed the software-EGL keyboard/mouse/save/load smoke on all eleven surfaces using
locally compiled combined production sources and the existing packaged dependency set. This is not
an exact packaged-artifact test or B18 human acceptance. Both batches require full exact-head CI.


### Ordinary NPC mission commands and settlement

The coordinator now owns the existing `Stage21HNpcMissionService`, so its current immutable snapshot
is the single Stage-21H sidecar captured by the accepted save envelope. After each completed campaign
tick it expires at most eight overdue contracts in deadline/identity order. The deadline remains
**inclusive**: expiry occurs at the first processed tick greater than the deadline, unlike the
separate diplomatic response-deadline semantics. Escrow returns through the existing faction-treasury
transfer. Accepted failure memory is recorded once. Empty/historical NPC sidecars, paused/fractional
frames, capture and restore remain non-mutating. Expiry never requires inventing a player or wallet.

For an already initialized player, `Stage228CampaignAuthority` composes existing ACCEPT/REJECT/CANCEL
commands and reconciliation. A preview executes the exact submission path on an isolated checkpoint;
it changes no live treasury, clock, escrow or player field. The non-forgeable token belongs to that
specific live authority and guards the entire exact checkpoint. Foreign, rejected, repeated and stale
submission fails before mutation; submission reuses the existing service again. Offered personal
contracts require the player's own discovered issuer posting. Choosing the knowledge viewer never
grants this permission. Already accepted contracts remain inspectable/cancellable after travel.

Event/deadline-relevant mission work is reconciled at completed ticks under an eight-contract budget.
A separate bounded sweep of active contracts every 60 ticks reads ordinary objective authorities
when a physical service has emitted no mission-specific wakeup. Its bucket derives only from the
campaign tick and canonical active IDs; no transient cursor or second clock is introduced. Shared
Stage-21H objective and contractor-participation validators decide outcomes. Other actors' work
refunds/fails rather than paying the human. A successful escrow transfer updates only the existing
`PlayerState.walletMilliCredits`; the service's wallet adapter is transaction-local and is not
another persisted balance. Ownership, affiliation, discovery, docking, orders and construction
fields are retained. Mission status/escrow/reputation and player balance survive the same v5 save.
No schema/generator version change or money source/sink is added by this lifecycle composition.

The Contacts inspector displays personal contract reward, escrow, inclusive deadline, objective and
participation condition independently of the faction knowledge viewer. Clicking ACCEPT/REJECT/CANCEL
first pauses and previews. The displayed explanation precedes an explicit confirmation control,
reachable by Tab/Enter and mouse. Changing selection or campaign clears the pending UI token; changed
authority state rejects it on submit. The campaign stays paused after a command until ordinary resume.

`GeneratedCampaignPlayerMissionIntegrationTest` covers inclusive expiry/refund/idempotence without a
player, pure/shared preview, foreign/repeated/stale tokens, personal knowledge boundaries, real
reject/cancel treasury transfers, accepted expiry memory, 8x save/load continuation, pause/fractional
purity, bounded deterministic expiry, due pending observations, participating versus unrelated-player
settlement, periodic no-wakeup settlement, and personal projection provenance. Delivered freight
checkpoints in settlement tests are **fixtures**, not proof of physical player delivery from UI.

The software-EGL smoke accepts an optional already initialized test checkpoint, copying it to the
fresh temporary save path before load. It exercises keyboard preview/accept/confirm/save/load/reject
and mouse preview/cancel/confirm in the real client. This optional fixture journey does not prove
independent-pilot creation, production NPC roster/offer generation, all player loops or B18 human
acceptance. The ordinary fresh seed still grants no player, ship, funds or synthetic contract.


Lifecycle batch validation on 2026-10-01: the initial targeted Maven/JUnit run passed 39 tests
(12 new integration methods plus 27 existing lifecycle/coordinator/carrier methods), zero failures
and errors. Two additional capacity/budget methods and the final Russian projection assertions
passed direct invocation against the final compiled source; the full exact-head JUnit gate remains
mandatory. The final combined production sources passed software-EGL navigation/save/load on all
11 surfaces and the optional personal mission keyboard/mouse journey above. Its frame was visually
inspected. B18 tooling's eight Python tests passed; those are tooling regressions, not human review.
The PR records the full exact-head verification result; all stage-level gaps listed above remain.


Final reference audit: completed ticks also call the existing `PlayerRuntime` ownership/docking
reconciliation rules through a new pure, externally-clocked API. The original playable runtime uses
the same extracted rules. Missing/destroyed owned fleets and their orders are removed, completed
owned projects become existing physical station references, absent stations are removed and invalid
docking is cleared. No control systems, physical writes, system switch, starter assets or second
clock are installed by this seam. Capture/restore remain non-mutating. The additional physical-loss
regression destroys an actual freighter through the accepted Stage-20/world destruction authority,
then proves failed-contract refund, no replacement grant and exact v5 save/load with surviving player
references. This closes reference hygiene, not physical player movement/trade/start integration.

The repository-wide JUnit run passed **2351 tests, zero failures/errors, one skip** before the final
reference audit. Its local Javadoc launcher initially rejected Maven's `-J` proxy flags; correcting
the environment launcher does not change repository production code. After sharing the player
reference rules and adding the physical-loss case, the exact final-source targeted JUnit run passed
**24 tests (15 new mission integration methods plus existing player regressions), zero failures/errors**.
The new branch head still requires a full remote `clean verify`; successful earlier-head evidence
must not be relabelled as its final exact-head gate.

After correcting the local Javadoc launcher, strict Java-17 Javadoc, desktop packaging and the
configured coverage checks passed. That continuation used accumulated execution data and reported
an old/new nested PlayerRuntime-class mismatch, so it is not promoted to clean exact-head coverage
evidence. The mandatory remote `clean verify` must rebuild and rerun the complete final source.


### Explicit independent-pilot start and physical local commands

The ordinary fresh client now opens paused on disclosed start conditions. Preview validates a sale
on an isolated checkpoint; confirmation spends 25,000 of the pilot's 100,000-credit initial savings
to acquire one existing empty IDLE reserve through PlayerOwnershipService and the real seller
treasury. It opens the ship's home system and personal inspector; the knowledge viewer still grants
no ownership or faction management. Restore/migration never initializes or repeats this start.

WASD sends transient thrust only while viewing the personal ship's system. Exact hierarchical
position/velocity advances on completed existing campaign ticks through fitted engineering; idle
input coasts and X requests finite braking. No legacy float integrator or second clock is installed.
The personal inspector derives actual cargo, mass, reaction mass, thrust, acceleration, delta-v,
power, energy and heat from the existing ship component. It writes no derived state.

Home-system endpoints receive finite ordinary wallet markers only on confirmed new game. Their
commodity stock remains the existing Stage-18 storage. Station names use authored roles consistently
on the local map and market inspector. Docking validates exact range/speed without moving the ship.
BUY/SELL previews disclose quantity and exact wallet delta including applicable existing customs.
Confirmation uses TradeController and finite logistics handling; ordinary persisted ledger evidence
prevents spending one ship's handling interval twice in the same completed tick, including after load.
The marker has no legacy item inventory, so autonomous item economies cannot create these commodities.

Numeric policy/fingerprint, explicit money sources, freight schema 1→2 adoption, release profile
`se-gen-1`→`se-gen-2` and the unchanged saved Stage-20 generator boundary are recorded in
`docs/release/stage23b_pilot_start_profile_v1.md`. Campaign v5 is retained. The fixed seed 1–16
rerun has twelve opening/round-trip passes and four unchanged generation rejections (4/6/8/10),
with no substitutions. It is not an all-seed PASS.

The initial corrected targeted JUnit gate passed 20 tests, zero failures/errors: four start, four
physical-command, five freight materialization, three legacy player movement and four wallet trade
regressions. The full local JUnit run then reported **2363 tests, one failure, zero errors and one
skip**: the transient creation invitation changed the ordinary saved-world projection. The client
now explicitly requests that invitation while ordinary projection remains exact after restore; the
original save/load equality assertion is retained. The corrected final-source **39-test JUnit gate
passed with zero failures/errors**, including physical settlement/customs/overflow, unsupported-schema
and unchanged-input adoption, pure engineering projection, governance and original UI/player/freight
regressions. Strict Java-17 Javadoc and desktop packaging passed against the final source with tests
and coverage skipped for that packaging continuation. This is not clean final-source coverage; the
mandatory clean exact-head gate and its full result are recorded on PR #413.

The final packaged desktop JAR passed a software-EGL client journey: fresh keyboard start/
purchase/focus/thrust, eleven keyboard/mouse surfaces, save/load, station focus, pure docking/trade
preview, physical purchase and cargo persistence. The docking geometry is explicitly positioned by
the smoke fixture; creation, ownership, payment and UI commands are real. This is engineering
evidence, not human navigation, all player loops or B18 PASS.

**23B remains ACTIVE/PARTIAL.** These opening quotes are a finite static profile, not a dynamic
market or proof of a profitable trader career. Inter-system player travel, mining, fitting, physical
construction/supply, fleet/faction/carrier commands, production NPC offers and the final B18 gate
remain mandatory. #412/#370 stay open and 23C has not started.

### Direct player travel, reserve progression and own-faction composition

The next production command batch uses the ordinary WorldSimulation jump FSM. The personal ship
must be undocked, have no active jump, select one direct topology neighbor and pass the existing
exact onboard route-fuel plan. Preview does not load station propellant. Confirmation preserves
real movement to the departure endpoint, preparation, detached transit and exact edge arrival on
campaign ticks. The existing freight owner now mirrors IDLE fleet arrivals without changing hold,
provenance lots or orders. Discovery reuses PlayerRuntime's location rule at completed ticks and
adds only the actually reached system, without switching the presentation viewer or input systems.
The ship surface shows direct-hop fuel requirements and the active jump phase/boundary.

The same ship surface exposes existing local empty IDLE reserves for the already disclosed
25,000-credit price. Purchase requires a commissioned local dock of the actual seller, uses
PlayerOwnershipService's conserved treasury payment, and does not change the active ship or create
another hull. Handover uses PlayerShipProgressionService after exact stationary admission, requires
an owned local target and an undocked current ship, and preserves both identities and positions.

The settings surface exposes the existing PlayerFactionFoundationService transition for the
explicitly disclosed `faction.player` / «Содружество пилота» identity. Its treasury, territory and asset
grants are zero. The typed campaign composition retains every adjacent Stage-20/21/M22.8 owner and
migration provenance; foundation adopts a validated replacement binding with no elapsed time.
The exact-state confirmation is single-use. A separate personal/own-treasury inspector offers
1,000-credit capitalization or return through PlayerFactionManagementService and ordinary ledger
transfers. It cannot choose another faction's treasury. Names and transfer size are currently the
disclosed defaults; arbitrary-name/amount UI is not claimed.

Local validation scopes so far: corrected direct-hop/UI/NPC regression gate **19 tests**, then
travel/reserve/legacy ownership/navigation **14 tests**, then foundation/treasury/original projection
**16 tests**, all with zero failures/errors. The 19-test gate corrected a discovered location-rule
hook error without weakening the arrival assertion. Travel tests round-trip actual detached transit
and arrived cargo. Dock/departure positions are labelled engineering fixtures, not proof of human
navigation. The combined final command-source Maven/JUnit gate passed **36 tests with zero failures/errors**:
all five new campaign command classes, physical financial settlement, the original projection
roundtrip and all fifteen NPC mission integration methods. This gate retains ordinary domain
assertions and does not replace clean exact-head coverage.

The packaged desktop JAR passed the extended software-EGL journey for reserve purchase, control
handover, foundation, treasury capitalization/return, actual direct jump and asset/cargo reload.
New confirmation screens were visually checked. The final UI wording was packaged and the same
complete graphical journey passed again. That final `verify` continuation passed the original
projection roundtrip, strict Java-17 Javadoc and desktop packaging with coverage skipped; it is not
clean coverage. Full clean exact-head CI remains mandatory for this new command batch.

**23B remains ACTIVE/PARTIAL.** Mining, fitting, construction/supply, profitable dynamic trade,
fleet/faction policy/territorial/affiliation commands, carrier commands, production NPC offers and
B18 human acceptance remain open. No next-stage or merge authorization follows from these slices.

The preceding owning pilot/freight-schema batch received full exact-head CI #7743 on
`a8b57ba5074b0f68748c91dcbdbe7b0ac8f62113` (tree
`4121d7a0d62b8e2fe07f0be17d04605dcfc91e8c`): **2363 tests, zero failures/errors, one skip**.
Clean coverage checks, strict Java-17 Javadoc, desktop packaging and B18 tooling passed; Maven
duration 20:06. Run: https://github.com/Shamil0710/StarEmpires/actions/runs/36974078526. This is
evidence for that preceding commit, not for subsequent travel/faction commands or human B18 PASS.


### Global physical markets and conserved profitable trade

The explicit new-game profile advances to `se-pilot-start-2` / release alias `se-gen-3`.
Every existing physical endpoint receives its disclosed finite 10,000-credit ordinary wallet at
confirmed creation. Its real Stage-18 storage remains the only goods authority; no item inventory
is installed. Persisted v2 market identities select the shared dimensionless scarcity rule over
actual stock and compatible capacity. Role targets, spread, bounds and canonical policy hash are
in `docs/release/stage23b_pilot_start_profile_v2.md`. Read-only prices do not create a price owner.

The actual ancestor v1 checkpoint is retained compressed with byte hashes and exact commit/tree
provenance. Current restore preserves its two home markets, original liquidity and 5/4.5-credit
water quotes without recommissioning or a grant. No owning schema or saved generation ID changes.
The fixed seeds 1–16 have **12 opening/market/roundtrip passes, zero opening failures and four
unchanged upstream generation rejections** (4/6/8/10); overall corpus acceptance remains false.
All outcomes and finite funding totals are in `docs/benchmarks/stage23b-physical-market-corpus-v2.json`.

The previous direct-travel/foundation exact-head CI #7745 on `8444116` completed with **2372 tests,
one failure, zero errors and one skip**. Its repository identity audit correctly found the default
player-founded faction ID ungoverned, and mistook `faction.*` action labels for state IDs. Action
labels now use `pilot.faction-*`; the default `faction.player` is explicitly governed as
PLAYER_CREATED with preserve/collision/evidence rules. This metadata does not allocate a world
faction, rewrite saved IDs, mutate ContentCatalog or promote it to a core package. The prior ten
identities remain unchanged. The audit and foundation/governance regression group passed 13 tests.

A clean local build passed **16 tests** for start, physical cargo, direct travel, new markets and
legacy MarketSystem. Original production projection/command error tests passed **2 tests**, followed
by strict Java-17 Javadoc and desktop packaging; coverage was skipped locally. The packaged-JAR
llvmpipe smoke passed all eleven keyboard/mouse/scroll surfaces, fresh purchase/control, actual
physical purchase, reserve purchase/handover, zero-grant foundation, conserved treasury transfers,
ordinary jump, physical sale and save/load. Water bought at 2.50 credits/kg was sold at 7.32
credits/kg: **4.82 credits profit**, paid by the receiving station. The original cargo-bearing hull
travels; the second purchased hull remains at home. Dock/departure geometry is explicitly a QA
fixture; ownership, stock, money, hold, hop and sale are real shared authority. Destination market
and sale confirmation screens were visually inspected, with readable prices/capacity/wallet and
keyboard-accessible actions. This is engineering graphical evidence, not human B18 acceptance.

Full clean CI on the next exact commit remains required. **23B remains ACTIVE/PARTIAL**: mining,
fitting, physical construction/supply, strategic/policy/territorial/affiliation/carrier commands,
production NPC offers and final human B18 acceptance remain mandatory. No merge or 23C follows
from this market slice.


### Personal faction policy, diplomacy and territorial intent

The actual player faction now exposes doctrine, fiscal limits, treaty lifecycle, market embargo and
territorial intents through the existing PlayerFactionManagementService. Each pure preview restores
an isolated composed checkpoint and delegates to ordinary validators. Confirmation adopts the
validated replacement only for the exact source authority/checkpoint and consumes its token once.
Independent players and foreign-actor treaty/embargo impersonation fail without live mutations.
The policy controls do not grant money, goods, sovereignty, production output or assets.

| Production control | Shared behavior | Disclosed scope |
| --- | --- | --- |
| Seven doctrine axes | bounded FactionPolicyCommand.UpdateDoctrine | 5-point steps, exact before/after, 0–100 |
| Six fiscal values | bounded FactionPolicyCommand.UpdateFiscalPolicy | 1-percentage-point rates / 1,000-credit limits |
| Embargo / revoke | ordinary unilateral legal access transition | selected public counterparty, indefinite until revoked |
| Treaty offers | all five clause kinds / three directions | selected counterparty, personally discovered scope for construction, indefinite; requires consent |
| Treaty lifecycle | accept/reject, 20-tick notice, breach, renewal, counterproposal | actual related saved treaty; ordinary validators decide eligibility |
| Claims / withdrawal / relinquishment | ordinary territorial stabilization and ownership rules | personally discovered system; declaration does not grant control |
| Recognition / construction concession | shared existing claim/control/right rules | explicit counterparty/system; no free construction resources |

Counterproposals retaining an incoming one-way grant reverse its owner-relative representation so
the same actual party remains grantor. The shared renewal owner also preserves party-to-party rights
when a different party originates renewal. Existing saved treaties are not rewritten. A regression
checks both changes with ordinary incoming/acceptance fixtures and preserved personal money.
New row names/summaries are ellipsized within the list; the inspector retains full values, avoiding
long treaty/territorial names drawing over unrelated detail text. No navigation state or authority
is encoded by those display truncations.

The final local Java-17 verify passed **16 tests, zero failures/errors**, including seven composed
faction-command methods, original production projection, repository faction-ID audit, Stage-17E
treaty lifecycle and Stage-21C mid-lifecycle persistence. Strict Javadoc and desktop packaging passed;
local coverage was skipped. The final packaged-JAR llvmpipe journey passed every previous market/
travel/asset surface plus real keyboard doctrine/fiscal changes, embargo/revocation, a proposed
market-access treaty, claim/withdrawal and exact policy/resource save/load. Final policy/treaty
screens were visually inspected after the list overflow fix. Incoming-offer acceptance, counteroffer
and renewal integration cases use labelled ordinary foreign-authority fixtures; the UI smoke does
not claim an autonomous production NPC issued those offers or accepted an outgoing treaty.

This is another implemented command slice. Stock/production-policy authoring/application, asset
legal affiliation, established territorial control, physical mining/fitting/construction/supply,
fleet strategic/carrier commands, production NPC offers and genuine B18 acceptance remain open.
No owning schema, content or new-game resource profile changes. Full exact-head clean CI is still
required for this next command commit; 23B remains ACTIVE/PARTIAL with no merge or 23C.


### Explicit registration of existing personal assets

The own-faction inspector now offers a pure preview and exact single-use confirmation for explicit
legal registration. Faction foundation alone still does not affiliate the purchased hulls. The
shared service changes the actual World faction of existing owned assets; owning freight schema 3
persists its separately validated legal mirror while preserving the immutable bootstrap origin.
Only personally owned IDLE freight is supported by this integration. A real transit entity can
register without teleporting, replacing IDs, losing cargo or altering funds. Assigned bootstrap
freight is rejected. Carrier affiliation is not claimed by this slice.

Five new composed tests verify preview purity, origin/resources/IDs, genuine schema-2 adoption,
world-only/freight-only mismatch rejection, real transit affiliation/reload/arrival and the required
persisted personal owner even for an empty hold. Together with existing ordinary affiliation,
scheduler and mid-approach regressions, the final group passed 18 tests without failures/errors.
Strict Javadoc and desktop packaging passed, coverage skipped locally. The packaged-JAR graphical
journey passed two-hull registration and reload, all eleven surfaces, policy/diplomacy/claim commands,
then an actual jump and physical water sale with the same conserved 4.82-credit profit. The
registration confirmation screen was visually inspected; labelled geometry fixtures remain
engineering evidence, not human B18 acceptance.

The previous market commit `f26b59f81eeaa36ee9665c423ab4a1fa09e001fb` passed full CI **#7815**:
2375 tests, zero failures/errors, one skip; coverage, Javadoc, desktop packaging and B18 tooling
passed. The separate policy commit `644a28fe032303054baf833b6f6ea359c09146b4` is under full CI
**#7827**; this registration/schema batch must wait for that gate before advancing the branch.
Stock/production policy, established territorial control, mining/fitting/construction/supply,
strategic/carrier commands, production NPC offers and genuine human B18 remain open.


### Durable personal fleet orders and exact physical execution

The Military surface now exposes HOLD, MOVE, FOLLOW, ESCORT and continuous two-system PATROL for
existing inactive personally owned IDLE freight. Independent players have the same personal order
authority; the spectator faction is never the command actor. Target fleets are personally owned,
destinations are personally discovered, and the active hull retains direct-control priority. The
ordinary PlayerFleetOrderService persists each intent in existing PlayerState, with the same pure
exact-checkpoint single-use confirmation as other personal commands. MOVE selects system arrival;
legacy arbitrary float local targets and item-count economic orders are not reinterpreted as SI.

The existing PlayerFleetOrderExecutor has an exact campaign adapter using the same fitted
PlayerDirectControlSystem. HOLD consumes finite counter-thrust rather than snapping velocity to zero;
empty reaction-mass stores leave real inertia. FOLLOW/ESCORT use actual local target kinematics,
500/1,000-metre separation guidance and a relative approach envelope of 100 m/s, not guaranteed
combat protection. MOVE/PATROL reuse PlayerFleetRoutePlanner and the ordinary jump FSM with onboard
fuel preflight; no automatic station refuelling or remote resource settlement is authorized. Intent
survives transit/load and is resumed when an inactive hull becomes delegated again.

A real arrival-to-patrol regression exposed a rejected request during FTL cooldown. The adapter now
reads the ordinary fitted readiness plan and waits. Current FTL readiness and its localized reason
are shown in the inspector. The composed movement owner also excludes its physically controlled
interval from the ordinary passive FTL-recovery pass. The jump FSM records which fleets it processed
in that interval, preventing an additional player movement step on the arrival boundary. Both
bindings are transient adapters rebuilt on restore, not another persisted clock or engineering state.
Assigned freight remains under its existing transport owner. Ordinary reference reconciliation runs
before attaching the order adapter after physical destruction, preserving the existing loss behavior
without replacement hulls or resurrected ownership.

An earlier 25-test composed/legacy group passed. Final Java-17 verify passed **32 tests, zero
failures/errors**, covering five new personal-order methods, all fifteen existing generated player
mission cases, ordinary jump lifecycle/engineering/persistence and core-pair fitted jump routing.
Strict Javadoc and desktop packaging passed; local coverage was skipped. The physical regressions
cover finite braking/coasting, real follow motion, actual MOVE transit/reload, patrol cooldown and
exactly one cooldown interval, as well as stale/reused/foreign/active/unknown-target rejection.
Dock/departure geometry and depleted stores are explicitly labelled fixtures. A separate full
exact-head CI remains mandatory for this engineering-ownership batch.

Mining/fitting/construction/supply, stock/production-policy application, established territorial
control, carrier commands, production NPC offers and genuine B18 acceptance remain open. Existing
NPC contract fixture coverage does not imply production offers have been installed. This remains
23B ACTIVE/PARTIAL; no merge or 23C follows from the fleet-order slice.


The final packaged-JAR llvmpipe journey passed all eleven existing surfaces plus independent-player
keyboard preview/confirmation of FOLLOW, ESCORT and HOLD, actual finite inactive-hull movement and
order save/load. It then passed existing registration, faction/policy commands, ordinary jump,
conserved physical profit and reload. The personal-fleet confirmation was visually inspected: full
control conditions remain readable; provenance uses the existing detail scroll and both actions and
confirmation remain accessible. This is graphical engineering evidence, not human B18 PASS.

## Own stock/production policy authoring and explicit ordinary application

Faction controls now expose catalog-named commodity floors in **count-based item units**, recipe
preferences per station archetype, reset, and a separate explicit strategic-policy apply. The
adapter reuses UpdateStockProductionPolicy / ApplyStrategicPolicy and the same exact-state
single-use confirmation. Numeric changes preserve the other item/archetype policies; recipe
selection uses authored catalog definitions. All policy state remains in WorldState, with no new
schema, owner, clock, opening profile, resource grant or SI reinterpretation.

The inspector reports actual own eligible commodity-market / commodity-production counts from
their real registration and components. Changing a policy alone changes no goods or processes.
Ordinary apply uses existing stock-capacity bounds and real recipe-retool progress reset. Actual
Stage-18 kilogram stores, finite sources, installed physical processes and engineering work remain
under their existing authorities. A newly founded faction has zero eligible commodity consumers;
its apply is an exact no-op, not a claim of autonomous physical manufacturing.

Local validation: 14 tests passed across the new three generated-player scenarios, existing
Stage-17F real commodity-market/production acceptance, Stage-17G2 player service, and faction
command integration. Pure projection/previews, own-only intent, unknown references, stale/foreign/
reused tokens, recipe cycling/reset, exact roundtrip and zero-consumer resource conservation passed.
Strict Java-17 Javadoc/desktop packaging passed with coverage skipped. The final packaged llvmpipe
journey passed keyboard authoring, pure previews, explicit apply and reload, followed by original
embargo/treaty/territory, actual hop, conserved physical trade and reload. QA uses the actual filtered
row count for keyboard traversal and returns to the faction tab after the ordinary load reset.

The registration batch passed CI #7829 with 2387 tests; the completed fleet/loss batch at dec9a2a
is separately under CI #7837. This policy presentation batch stays unpublished while that required
CI runs. Physical owned industry/construction/supply/mining/fitting, established territorial
control, carrier operations, production NPC opportunities and genuine B18 remain mandatory.
23B stays ACTIVE/PARTIAL; no merge or 23C.

### Return to the active personal ship

The shared toolbar and Ctrl+C now resolve the current PlayerState active FleetId at dispatch,
then reuse ordinary local placement validation and camera follow. Choosing another row or viewer
does not change the target; no saved camera/player state or simulation clock is introduced.
Java-17 incremental compilation and the three existing production UI regression classes passed
12 tests with zero failures/errors in 28 seconds, with coverage disabled for focused feedback.
No full build or full suite was run for this presentation change. The existing software-EGL
smoke now checks keyboard toolbar return from all eleven surfaces, Ctrl+C and unchanged paused
campaign state. That extended graphics probe has not been run in this Windows environment;
visual acceptance and the remaining 23B command integration remain open.

### Personal route planning on the galaxy map

The galaxy inspector now exposes explicit personal route preview and first-departure confirmation.
The existing PlayerFleetRoutePlanner receives the immutable campaign PlayerState through a new
read-only constructor; it installs no playable control systems and performs no player reconciliation.
Only personally discovered origins, intermediate systems and destinations are admitted. No local
active fleet, an existing jump, the current system or an unavailable route yields no travel token.
Planning runs on explicit input, not on every graphics frame.

The accepted path is highlighted on the ordinary galaxy topology. The scrollable inspector names
every hop, shows the shared estimated transition time and explicit uncertainty, and explains their
sources and limits. The estimate excludes refuelling and engine-readiness waiting; uncertainty is
an exposure score, not a probability. This uses the existing Stage-15 route comparison model and
does not introduce a new physical risk or travel-time authority.

Confirmation delegates to previewPilotAction / submitPilotAction for the first direct edge only,
with ordinary onboard fuel, docking and fitted FTL validation. The exact authority/checkpoint token
rejects foreign, stale and repeated submission. The remaining path creates no delegated order,
automatic refill, remote purchase or saved sidecar. After arrival the player must inspect the next
hop again. Navigation, selection changes and resumed time discard the displayed preview; load
rebuilds presentation without retaining the old token. A rejected departure does not start travel.

Java-17 incremental compilation and six targeted regression classes passed **22 tests, zero
failures/errors**, in **52.917 seconds**, with coverage disabled for local feedback. New tests cover
unknown/current-system refusal, preview purity, deterministic path choice, foreign/stale/reused
tokens, exact started-jump roundtrip and a multi-hop route committing only its first edge. The
multi-hop fixture explicitly supplies discovered systems; it does not prove production discovery.
Existing real-hop/cargo/arrival, docked/depleted-fuel, Stage-15 route-risk and production UI tests
also passed. No clean package or full test suite was run.

The software-EGL probe was extended with a return-home preview using two systems actually visited
in its existing physical trade journey, a docked confirmation refusal and unchanged campaign state.
It compiles against current production classes and existing packaged dependencies on Java 17;
graphics execution/visual inspection has not been performed in this Windows environment. This
slice does not close final galaxy overlays, physical mining/fitting/construction/supply, carrier
commands, production NPC opportunities or human B18 acceptance. 23B remains ACTIVE/PARTIAL.

### Physical personal consumable supply

The Ships surface now exposes each authored consumable binding actually installed on the active
owned IDLE freight. Quantity controls, pure preview and exact confirmation move previously bought
Stage-18 commodity kilograms from that same freight hold into its fitted interface. The ordinary
Stage18ShipConsumableService validates the module, interface, capacity and available material;
Stage20FreightRuntime consumes the matching manual-cargo provenance lots and synchronizes real hold
mass. Existing engineering cargo synchronization retains the fitted ship's total physical mass.
No station stock, wallet, heat, bus energy, damage, cooldown or ownership is awarded or reset.
Servicing requires an existing commissioned dock with current ordinary 1-km / 1-m/s geometry.
This is the shared atomic interface-loading boundary, not a new timed pump or shipyard-work model.

The same authority/checkpoint confirmation guards this action. Empty holds, unknown bindings,
wrong mounts, invalid quantities, unavailable capacity, undocked ships and transit fail before
conversion. Restore retains loaded consumables and reduced hold/lot state in existing owning formats.
No schema, new-game resource profile or commodity/unit reinterpretation is introduced.

The Java-17 focused group passed **9 tests, zero failures/errors**, in **51.886 seconds**: two new
personal-supply methods, existing ship-consumable service, generated physical cargo and original UI
projection. It covers a real paid water purchase, pure projection/preview, foreign/repeated tokens,
conserved fitted total mass and personal money, preserved engineering heat/energy, depleted cargo
lots and exact save/load. Berth geometry and previously spent tank mass are labelled fixtures.
That result precedes only a presentation wording change. No full test suite or package was run;
graphical execution for this new control remains pending. Physical mining, fitting/repair,
construction/industry, carrier commands, production NPC opportunities and final B18 remain open.

### Personal physical station discovery

Successful ordinary docking now records the human actor's actual station visit in the existing
Stage-20 discovery registry. Ownership, local system, absence of transit and berth geometry are
validated before recording permanent location evidence. The observation contains no resource
knowledge and does not copy a sovereign actor's intelligence. Repeated visits preserve the existing
permanent observation. Campaign capture and restore retain this registry with the current world
fingerprint in the existing format.

The Intelligence surface shows these personal visited-station records independently of the selected
sovereign knowledge viewer, with the original observation time and explicit limits on current stock
or service knowledge. This establishes personal evidence; reporting discoveries to an issuer and
production NPC opportunity creation remain separate unfinished work.

The focused Java-17 group passed **20 tests, zero failures/errors**, in **1 minute 12 seconds**:
personal discovery, personal supply, player mission integration and campaign UI integration.
It verifies pure docking preview, unchanged sovereign registries, repeat-visit idempotence, exact
save/load, personal intelligence visibility and refusal without physical docking. Test berth geometry
is an explicit fixture. No full build or graphical acceptance was performed. 23B remains ACTIVE/PARTIAL.

### Searchable reference and extended engineering UI scenario

The Menu now includes two ordinary searchable/scrollable inspector rows explaining physical units,
simulation time, route-estimate limits, personal authority, money/escrow, discovery provenance and
common command refusals. The rows use the existing keyboard navigation and inspector; they do not
change any authority or store new campaign data. This is a reference slice, not completed onboarding,
localization or contextual tooltip coverage.

The focused Java-17 regression passed **17 tests, zero failures/errors**, in **37.160 seconds**:
personal discovery, campaign UI, workspace navigation, discovery persistence and generated industrial
runtime bridge. The software-EGL scenario now additionally checks actual docking evidence after UI
reload, a new normally paid water purchase, pure cargo-to-tank preview, exact interface amount increase,
conserved fitted mass and wallet, consumed cargo, UI save/load and reference navigation. It uses fuel
spent by its existing real journey, not a new depletion fixture. The earlier explicit berth geometry
fixture remains labelled. The scenario compiles on Java 17; graphics execution remains outstanding.

Manual player checks are scheduled after Stage 23 in the linked player checklist, as requested by
the user. That schedule does not mark B18 or release acceptance passed. Mining, fitting/repair,
construction/industry, carrier command composition, production NPC opportunities and remaining
presentation integration are still open. No full build was needed for this reference/scenario slice.

### Personal discovery report and ordinary contract settlement

Accepted discovery contracts now expose a report row in Contacts. A pure exact-checkpoint preview
and keyboard/mouse confirmation submit only the exact objective's existing personal observation.
The active fleet must be personally owned and local to the available issuing NPC, and the contract
must still be accepted within its inclusive deadline. The ordinary player-participation evaluator
checks the required static knowledge quality before any transfer. The report shares that existing
classification/location/resource knowledge through the Stage20G merge, retains shared-data evidence
with the original observation freshness, and supplies an owner-local discovery fact to the issuing
NPC. Other actors' intelligence is not consulted or copied.

The ordinary Stage21H contract service then evaluates and settles this specific mission at the
actual delivery tick. This preserves delivery on the inclusive final deadline tick; delaying the
check until the next tick would incorrectly expire a timely report. Reward comes only from the
existing funded escrow, with ordinary participation and reputation handling. The same wallet adapter
is reused by periodic reconciliation. Preview includes the real resulting personal wallet delta;
no independent reward or completion authority is introduced. Foreign, stale, repeated, absent,
cancelled and insufficient-evidence reports fail before live submission.

The Java-17 focused group passed **21 tests, zero failures/errors**, in **1 minute 6 seconds**:
three discovery-report methods, personal discovery, player mission integration and campaign UI.
It verifies actual docking-derived personal evidence, pure preview/projection, exact escrow payout
once, shared provenance, unchanged unrelated knowledge owners, foreign/reused/stale tokens, exact
save/load, inclusive-deadline completion and remote/unavailable recipient rejection. The NPC roster,
causal posting, any necessary issuer treasury funding and berth geometry are explicitly test fixtures;
this is not evidence of generated production NPC opportunities. No full build or graphical run was
performed. Production roster/opportunities and the remaining physical command integrations remain open.

### Physical personal extraction foundation (2026-10-04, partial)

The existing Stage18 finite extraction authority can now settle natural-source commodity output into
an idle generated freighter's actual SI hold. Source depletion, recovery losses, energy, engineering
work, maintenance and compatible storage retain their ordinary physical accounting. Shared interval
budgets also account for previously committed throughput across sources. Extracted lots use distinct
`player-extraction:` provenance and may be sold alongside purchased lots through the ordinary finite
station transfer. Selling cargo does not restore its source reserves.

Freight schema 4 admits this provenance; historical schemas 1–3 adopt without creating cargo.
Generated-world restore resolves every extraction source and rejects unknown sources, mismatched
commodities or cargo exceeding the source's accounted depletion. Personal extracted cargo also
requires checkpoint ownership. The composed save test uses a real generated finite source, while
installed capability and interval allocation remain explicitly labelled test fixtures.

The focused extraction, supply and persistence regression group passed 42 tests with zero failures
or errors in 1 minute 32 seconds (`target/stage23b-extraction-cargo-tests.log`). This covers ordinary
extraction/station adapters, shared budgets, physical cargo, historical freight adoption, personal
ownership and composed checkpoint round trips.

The common engineering catalog now admits the existing authored civilian mining module and fit.
The read-only mining adapter derives capability only from that installed module and its actual
damage-aware engineering state. A regular freight fit or repair workshop does not acquire mining
capability. Destroyed equipment and unavailable continuous power/heat capacity block projection.
Its interval allocator additionally bounds gross throughput by the installed equipment's damage;
the ordinary extraction method's maximum alone is insufficient for damaged equipment.

The final equipment/extraction group passed 27 tests with zero failures/errors in 27.995 seconds
(`target/stage23b-mining-equipment-tests.log`), including refusal without spending source/cargo/energy
when cumulative operations exceed a damaged unit's installed throughput. No full suite was run.

This is infrastructure for the playable loop, not completion of mining: lawful equipment acquisition,
physical approach/survey evidence, persisted mining intent and allocation of completed simulation
intervals still need command integration. Reopening a fresh budget on each UI click is prohibited;
selling all provenance lots cannot become a way to reset time allocation. No equipment, wallet,
source reserve, intelligence or starting-asset grant was introduced. Stage23B remains ACTIVE/PARTIAL.

### Personal physical mining commands (2026-10-04, partial)

`START_MINING` now records a personal SI order at the current authoritative tick without awarding
work or cargo. `STOP_MINING` cancels future work and leaves aboard cargo intact. Both reuse ordinary
isolated command preview and exact owner/stale-state submission guards. Each completed campaign tick
claims its interval once and rechecks active personal ownership, no conflicting fleet order,
undocked/local/non-travelling placement, operating damage-aware mining hardware and actual SI contact.
The explicitly authored excavation envelope is 5,000 m with drift at most 1 m/s. The process clips
requested gross mass to installed per-interval throughput, then uses ordinary finite Stage18
extraction, storage/provenance and engineering-cargo synchronization. Movement/control input, loss
of contact/equipment, exhausted capability, full storage or depleted sources stops future work.

Owning freight schema 5 appends orders and their last processed tick; old schemas 1–4 adopt with an
empty list. Personal ownership and non-future time are checked at composed-checkpoint capture;
source/method references are validated at runtime restore. A continuation regression exposed a copy
that dropped the list during world-fingerprint rebinding. All relevant physical/fingerprint and
initial-composition copies now preserve it. The test checks a nonempty restored order and equality
after both uninterrupted and restored campaigns complete the next tick.

The Industry inspector exposes start/stop controls through the normal preview/confirm flow. New
personal contact rows enumerate only free-body sources within the installed section's current local
working range. They disclose distance and equipment limits, not physical reserve or grade truth.
Projection and preview leave the campaign unchanged. Ordinary unfitted freight gets no contact row
or mining capability. The existing broader diagnostic industry projection is not replaced by this
slice and still needs the planned final actor-bound UI consolidation.

The final Java-17 focused group passed **45 tests, zero failures/errors**, in **1 minute 40 seconds**
(`target/stage23b-mining-ui-tests.log`). Four personal mining methods cover real completed ticks,
zero-delta refusal, pure preview/UI, exact composed save/load continuation, repeated/stale/foreign
tokens, range/speed/absent equipment, direct-control/contact loss, future checkpoint time and explicit
stop. Seven storage/persistence methods cover shared damaged-throughput and durable tick claims,
destruction and historical v3/v4 layouts. The group also covers player physical/affiliation/save
contracts, engineering adapters, campaign UI, workspace and game error handling. The software
graphics smoke source compiles against current classes; no graphical execution or full build was run.

Installed mining hardware and physical placement in these tests are explicit fixtures. Lawful
production acquisition/build/refit, material research and market admission for natural feedstocks
remain open, so this does not yet prove a complete fresh-player mining-to-sale loop. No production
NPC or asset is granted, and Stage23B remains ACTIVE/PARTIAL.

### Mining-equipment production and same-hull fitting path (2026-10-04, partial)

The civilian mining production path now exposes reusable ordinary product, manufacturing and
physical shipyard catalogs instead of building these bindings only inside its validator. The
excavation section uses the already reviewed closed industrial-support material recipe. Its paid
shipyard integration profile retains the authored workshop-envelope tooling, precision, power,
labor, automation and work requirements. The validator reuses these same catalogs.

A Union freight fitting proposal exchanges its existing cargo mission section for the mining
section on the same hull and retains drive, reactor, FTL, sensors and thermal equipment. Foreign
hulls, missing/other mission sections and already converted fits reject. The proposal does not
instantiate equipment, complete work or change a live ship. Ordinary fitting and shipyard planning
still determine feasibility; there is no hull-name-based mining capability grant.

The integration test starts with explicitly labelled finite raw-material/line/yard fixtures and
executes ordinary Stage18 manufacturing. Closed input mass becomes exactly one physical finished
module. Ordinary Stage18 refit settlement consumes that module and finite yard work before the
Stage17.5 continuity/application services change the same physical EntityId's fitting. Insufficient
work leaves inventory and work budget unchanged. Repeating settlement with a fresh adequate work
budget still rejects without the consumed product. Retained sensor damage/service age survives,
and the removed cargo section's damage/service age is carried in the ordinary continuity handoff.
It is not reintroduced as a pristine count-only module.

Normal campaign creation/restoration now explicitly admits the common authored freight/mining
product vocabulary. The persistence test proves zero opening mining-module stock, transfers one
labelled supplied-module fixture through ordinary finite oversized-product logistics, and retains
it through an exact native campaign save/load. Admission creates no inventory or player ownership.
No save schema, file framing, generation identity or numerical opening profile changes here.

The production/persistence/mining/minor-content group passed **11 tests, zero failures/errors** in
**29.513 seconds** (`target/stage23b-mining-production-tests.log`). After strengthening the duplicate
refit/input check, the final production and historical player-save/physical/supply/affiliation group
passed **25 tests, zero failures/errors** in **1 minute 30 seconds**
(`target/stage23b-mining-production-regression.log`). No full build or graphical run was performed.

This closes the reusable catalog, physical settlement and finished-stock persistence seams. The
player-facing persisted manufacturing/refit queue, allocation of completed ticks, lawful access and
consideration at real stations, material supply, post-refit cargo-capacity policy and persistence of
removed physical module condition remain open. The fixtures do not establish a fresh-player
equipment acquisition loop, and Stage23B remains ACTIVE/PARTIAL.

### Shared manufacturing interval (2026-10-04, partial)

`Stage18StationProductionBridge.manufactureProductAtStorage` now accepts an already allocated
manufacturing interval budget. Orders sharing one actual line can settle against canonical stock
without reopening a full interval per order. The existing facility-based entry point delegates to
this boundary. Rejection leaves canonical stock and remaining energy/work/maintenance unchanged.

The finite mining-module test supplies materials for two modules but work for only one: the first
order succeeds, the competing order cannot reuse that budget, and a later independently allocated
interval can consume the remaining materials. The focused production/bridge group passed **8 tests,
zero failures/errors**, in **21.143 seconds** (`target/stage23b-shared-manufacturing-tests.log`).
No full build was run. This is the settlement boundary for the pending queue, not persisted queue
or tick-allocation integration; the open work listed above remains required.

### Persisted personal manufacturing and physical sample evidence (2026-10-04, partial)

The campaign now executes personal manufacturing through `Stage18ManufacturingWorkQueue`. Its
explicit `player-manufacturing:v1:` rows use the existing native Stage18 product-process contract:
whole output units, actual station, proportional work progress and exact reserved input mass.
Unrelated historical process rows are preserved and are not executed by this queue. No envelope,
industrial schema, binary layout, opening resources or generation identity changes here.

Starting work withdraws the full closed material recipe into physical order custody without
creating a product. Custody continues occupying its real station storage classes; ordinary
logistics, extraction and manufacturing cannot use that occupied capacity. Restore derives this
occupancy from the persisted orders and rejects stock plus custody exceeding physical capacity.
Cancellation returns material atomically and does not refund spent energy/work. Completion removes
custody and creates only the authored whole-unit output. A full output store pauses work.

Each consecutive completed campaign tick allocates a shared budget once per actual installed
station line. Competing jobs cannot reuse it, replayed ticks do nothing and missing intervals cannot
be manufactured retrospectively. Facility tags, storage interfaces, unit-handling envelope, power,
work and maintenance come from real installed capability snapshots. Missing capabilities pause
work; no yard/line is inferred from a station name.

The Industry surface offers one mining-section order and cancellation for personally owned stations.
Commands use the ordinary isolated preview/single-use confirmation and require personal station
ownership, the owned docked ship and exact live berth geometry. Checkpoint validation rejects
missing personal owners and future work timestamps. Read-only immutable product/recipe catalogs
are cached; opening empty-player projections avoid loading manufacturing content repeatedly.

Successful personal excavation now retains the contacted location and `RESOURCE_INDICATION`
from an actual recovered physical cargo sample. Preview/start/zero frames create no observation.
The personal Intelligence row explains that a sample provides no reserve or grade estimate.
Evidence uses the actual cargo receipt time, persists independently of foreign knowledge and is
not renewed by repeated mining at the same known location.

The final reservation/manufacturing/ordinary logistics/mining/campaign-UI group passed **29 tests,
zero failures/errors**, in **35.668 seconds** (`target/stage23b-manufacturing-reservation-final-tests.log`).
The strengthened ordinary-campaign test then passed separately in **24.377 seconds**: it uses a
really installed compatible production line and proves positive bounded partial progress, exact
composed save/load and deterministic continuation. Ownership and supplied raw materials remain
explicit fixtures; this is not evidence of an ordinary fresh-player acquisition loop. The prior
discovery/report/queue/UI group passed **14 tests** in **1 minute**. Strict Javadoc passed.

This closes manufacturing queue persistence, physical custody/capacity and completed-tick work
allocation. Same-hull refit/repair/ammunition queues, removed-module condition custody, lawful
ordinary acquisition and material supply, physical construction, remaining strategic/carrier
commands, production NPC opportunities and final notification/overlay integration remain required.
Human acceptance stays deferred until after Stage23 as instructed. Stage23B remains ACTIVE/PARTIAL.

### Final Windows graphics and UI verification for this increment

The hidden 1280x720 Windows GLFW/OpenGL smoke completed successfully on the NVIDIA GeForce
RTX 4070 (`target/stage23b-manufacturing-windows-graphics.log`). It exercised all 11 screens,
keyboard/mouse navigation, physical trade and personal fleet orders, faction actions, personal
station evidence and purchased cargo-to-tank supply with save/load. The probe now advances an
actual unpaused completed tick before its next purchase after loading; it preserves the runtime's
once-per-tick trade guard. Linux EGL setup remains available in the same probe.

The run uses current compiled classes before the previously packaged dependency jar; it is not
a newly packaged release or exact-revision CI result. Positive manufacturing UI acquisition,
quantitative surveys, carriers and production NPC opportunities are not covered by this smoke.
The player-facing territorial overlay now resolves system names and displays controlled status
in Russian; personal station evidence uses plain language. The final UI/discovery/queue group
passed **9 tests**, zero failures/errors, in **40.200 seconds**. Whitespace verification passed.
No full build or full test suite was run for this increment, and human acceptance is still pending.

## Personal journal and evidence-bound resource inspection (2026-10-04, partial)

The active player now has a durable personal journal independent of the presentation's faction
viewer and the existing observer timeline. It records successful physical commands, personal
faction/fleet confirmations, actual manufacturing completion, automatic excavation stops and actual
contract lifecycle changes. Entries retain commit sequence, authoritative completed tick, actual
wallet delta and physical station/source/product/contract/fleet references. Ship purchase/handover
references identify the actual target hull; fleet orders retain their type and target. A report's
contract payout appears in the contract outcome rather than a second monetary report receipt.

Previews, rejected/stale/foreign/repeated confirmations, pause, zero frames and reload do not create
events. Notification acknowledgement is itself an isolated preview plus single-use confirmation;
it changes only a monotonic read watermark. The journal tab shows an unread badge, a read command
and original event times. Its default is newest first, preserving commit order within a tick;
the user can still choose another sort. The retained history is explicitly bounded to the last
2048 events with never-reused identities. This is a personal event journal, not an unlimited audit
ledger or a reconstructed account of actions before its introduction.

Native composed campaign framing advances to **M22.8 v6**, appending a separately bounded schema-1
journal after the existing optional player payload. Native v1–v5 and the supported Stage20/21
migration chain adopt an empty non-granting journal. PlayerState, freight v5, industrial process
and discovery formats are unchanged. Cross-envelope validation rejects nonempty uninitialized
history and future commits; journal validation rejects invalid watermarks, missing/disordered
retained suffixes and future acknowledgements. Faction world transitions preserve existing history.

The actual game client's local resource inspector no longer prints exact physical reserve, grade
or source recovery. It reads the personal actor's discovery record: absent or sample-only evidence
has no quantitative estimate; existing bounded measurements retain their grade/mass intervals,
confidence, original observation time and freshness. Expired estimates remain labelled as expired
instead of being renewed on projection. Foreign internal extraction-outpost stock and equipment
condition are closed unless that station is actually personally owned. Diagnostic model constructors
retain their engineering projection; the client explicitly binds the personal projection both on
initial binding and before adopting a loaded campaign.

Fast projection tests use explicitly labelled measurement fixtures to verify existing intervals,
expiry, missing evidence, unrelated objects and rejected foreign owners. They do not prove an
ordinary quantitative-survey acquisition path. The mining integration checks its real viewed
occurrence both before and after actual sample extraction, preserving absent reserve/grade estimates
and pure projection. Exact positions, other maps and other internal indicators still need the
remaining actor/knowledge work; this change is not a complete fog-of-war implementation.

Stage23B remains **ACTIVE/PARTIAL**. The journal is implemented for the currently connected gameplay
paths; refit/construction/carrier/NPC paths must add their own actual outcomes when implemented.
Ordinary equipment/station acquisition, quantitative surveying, persistent player shipyard work,
physical construction, remaining strategic/carrier loops and funded production NPC opportunities
remain in the [current remainder](stage23b_remaining_work.md). Human acceptance stays scheduled
after all Stage23 development.

Validation for this increment:

- Journal/resource/mining/report/faction/fleet/UI group: **28 tests, zero failures/errors**,
  **1 minute 54 seconds** (`target/stage23b-journal-resource-tests.log`).
- Final native player and v1–v6 campaign migration group: **15 tests, zero failures/errors**,
  **1 minute 14 seconds** (`target/stage23b-journal-migration-final-tests.log`).
- Final actual purchase/handover targets, fleet/faction commands and chronological UI group:
  **27 tests, zero failures/errors**, **2 minutes 3 seconds**
  (`target/stage23b-journal-command-final-tests.log`). Groups overlap; these are execution counts,
  not a count of distinct tests or a full-suite result.
- Fresh strict Javadoc generation passed in **23.383 seconds**, using a separate stale-data file;
  generated output includes the new journal and resource APIs
  (`target/stage23b-journal-javadoc.log`). Whitespace verification passed.
- Final hidden Windows 1280x720 OpenGL probe passed all 11 screens and the ordinary physical trade,
  fleet/government, supply and exact UI save/load journeys, including personal receipt history,
  pure acknowledgement preview and durable read state
  (`target/stage23b-journal-windows-graphics.log`). Journal screenshots were visually inspected;
  unread count, retention limit and acknowledgement controls are readable. The policy scenario
  requires unchanged physical/strategic/player/craft state plus exactly one accepted policy event.

Graphics use current compiled classes before the previously packaged dependency jar, not a newly
packaged release. No full build/suite, exact-revision CI or human acceptance is claimed. The same
newly connected loops still require ordinary-play acceptance after Stage23.


### 2026-10-04 — Проверка исходного состояния при применении refit

Перед изменением живого инженерного компонента проверяется совпадение корпуса,
состояния оставшихся и снятых модулей и возраста обслуживания с результатом работ.
Устаревший результат и повторное применение результата со снятыми модулями отклоняются
без изменения конфигурации, runtime и instance state. Это защита низкоуровневой границы;
игроковая очередь верфи, физическое хранение снятых модулей и согласование новой
вместимости с грузовым реестром остаются открытыми. Статус 23B: ACTIVE / PARTIAL.

Проверка: ShipRefitApplicationServiceTest, ShipyardRefitContinuityTest,
Stage22CivilianMiningProductionIntegrationTest — 7 тестов, 0 failures/errors,
BUILD SUCCESS, 1:28; лог target/stage23b-refit-source-tests.log. Полная сборка
и полный набор тестов не запускались. Ручная приёмка не проводилась.


### 2026-10-04 — Индивидуальное хранение снятых модулей

Снятый модуль хранится отдельно от счётчика новых изделий: стабильная индивидуальная
ссылка, станция, исходный корабль и фактический такт снятия, назначение mount/module,
целостность и возраст обслуживания. Повреждения не уменьшают занимаемую физическую массу.
Хранилище ограничено 4096 реальными модулями: при исчерпании места записи не удаляются.
Масса оборудования складывается с обычными товарами и резервом изготовления; повторное
восстановление резерва не удваивает массу. Общая логистика учитывает занятую ёмкость.

Новая граница физического refit проверяет место для снятого оборудования, реальные
интерфейсы обработки и уникальность передачи до списания входящего изделия и работы.
При успехе возвращаются согласованные physical settlement, condition completion и custody.
Она не заменяет будущую игроковую сохраняемую очередь или транзакцию смены грузового реестра.

Native campaign v7 добавляет отдельный bounded schema-1 payload после журнала.
Исторические native v1–v6 получают пустое хранилище без выдачи оборудования; v6 сохраняет
журнал и отметку прочтения. Восстановление проверяет существование станции, действительные
определения модулей, время и суммарную вместимость, включая сохранённые process reservations.
Композиция игроковых/world переходов и live capture сохраняют соседний owner.
В промышленности показывается состояние оборудования только реально своей станции.

Обычная очередь refit, её доступ/оплата, перевозка и повторная установка индивидуального
оборудования, а также согласование трюма после смены секции остаются открытыми.
Сценарий кампании с оборудованием и владением станцией использует явные fixtures;
он проверяет сохранение и авторитетность, но не доказывает обычное получение этих активов.
Статус 23B: ACTIVE / PARTIAL. Ручная приёмка остаётся после всего этапа 23.

Проверки окончательного среза:

- target/stage23b-module-custody-final-tests.log: 57 целевых тестов, 0 failures/errors,
  BUILD SUCCESS, 2:45. Включены custody codec/storage, изготовление и refit, обе storage layers,
  player/journal/native migrations, совместное продолжение оборудования с производством и UI.
- target/stage23b-module-custody-javadoc.log: свежая строгая генерация doclint=all /
  failOnWarnings=true, отдельный staleDataPath, BUILD SUCCESS, 29.444 s; HTML новых API проверен.
- target/stage23b-module-custody-windows-graphics.log: exit 0, Graphical probe passed,
  NVIDIA GeForce RTX 4070, скрытый GLFW 1280×720. Новая строка оборудования отрисована;
  реальные F8/F9 точно сохраняют состояние, затем исходная игровая кампания восстановлена.
  Скриншот stage23b-removed-module-custody.png проверен визуально: поля и текст читаемы.
  Использованы текущие target/classes перед прежним dependency fat jar; новая упаковка не заявляется.
- git diff --check с core.safecrlf=false: без ошибок.

Первый прогон выявил неправильный тип исключения для отсутствующей станции; исправлен
до указанного финального прогона. Полная сборка, весь набор тестов, exact-revision CI,
публикация и ручная приёмка не выполнялись в этом срезе.

### 2026-10-04: сохраняемая очередь ремонта собственной верфи

Ремонт подключён к физическим командам START_REPAIR/CANCEL_REPAIR и вкладке промышленности.
Доступ требует собственного владения, физической стыковки и действительной установленной
верфи с активными опорными производствами и конечными выделениями мощности/работы.
Материалы фактического повреждения резервируются из канонического склада и продолжают
занимать место вместе с производством и снятыми модулями. Один бюджет верфи делится между
заданиями; повторный такт и пауза не начисляют работу. Потеря контакта, изменение исходной
компоновки/повреждения или доступности верфи останавливает работу. Отмена возвращает сырьё,
но не выполненную работу. Завершение меняет повреждения и ограничения эмиттеров, сохраняя
запасы, тепло, возраст обслуживания и боезапас; журнал получает одно фактическое завершение.

Native M22.8 schema/file v8 сохраняет самостоятельный bounded schema-1 repair payload после
индивидуального хранения модулей. v1–v7 читаются с пустой очередью без выдачи ресурсов или ремонта;
v7 сохраняет существующие оборудование и журнал. Совместная вместимость проверяется при загрузке.

Проверки: target/stage23b-repair-final-tests.log — 42 теста, 0 failures/errors, BUILD SUCCESS,
1:31; target/stage23b-repair-javadoc.log — строгий свежий Javadoc, BUILD SUCCESS, 13.150 s.
Кампанийный сценарий явно задаёт перенос того же корабля, строительство опорных производств,
владение, мощность и конечное сырьё. Это не доказательство обычного получения верфи.
После исправления подключения панели кнопок target/stage23b-repair-ui-tests.log:
4 затронутых теста, 0 failures/errors, BUILD SUCCESS, 41.833 s.
target/stage23b-repair-focused-graphics.log: exit 0, Repair graphical probe passed,
NVIDIA GeForce RTX 4070, скрытый GLFW 1280×720. Клавиатурные preview/confirm/cancel,
реальный резерв и точный F8/F9 проверены; исходное состояние восстановлено.
Скриншот stage23b-repair-pending.png проверен визуально: прогресс, 75000 кг резерва,
условия и кнопка отмены читаемы. Текущие target/classes идут перед прежним dependency
fat jar; новая упаковка не заявляется. Доступен короткий запуск QA с
`-Dstage23b.repairOnly=true`, без повторения остальных игровых сценариев.
Найденные при проверке проблемы — незаданный pause в fixture и отсутствие repair в
условии включения панели — исправлены. Последний успешный графический прогон адресный;
предыдущие широкие графические попытки завершались ошибкой на ремонтном сценарии.
Обычное получение верфи, коммерческая оплата, переоснащение и боезапас остаются открытыми.
23B ACTIVE/PARTIAL; полная сборка, все тесты и ручная приёмка не выполнялись.

### 2026-10-04: производство и ремонт дальнобойных грузовых двигателей

У обеих стратегических грузовых компоновок уже были физически установленные дальнобойные
двигатели с дополнительными 400000 кг сухого оборудования, но отсутствовали замкнутые
производственные/сервисные профили. Добавлен Stage22FreightStrategicProductionCatalogs:
модули изготавливаются через существующий профиль двигателя по полной фактической массе;
сервисное сырьё и работа масштабируются относительно исходного двигателя по сухой массе.
Состав материалов, инструментальные требования, точность и одновременные требования
мощности/персонала наследуют исходный двигатель. Это явное правило авторинга добавленного
оборудования, а не начисление запасов/работы. Базовые профили не заменяются.

Общий каталог также допускает производственные привязки Империи наряду с Союзом.
Повреждённый дальнобойный двигатель теперь проходит обычный план ремонта, физический
резерв, завершённые такты, native save/load и завершение. Схема остаётся v8; начальные
запасы, кошельки, установленное оборудование, генерационные ресурсы и владение не выдаются.
Это не завершает игроковую очередь переоснащения, обычное получение верфи или её оплату.

- target/stage23b-longhaul-final-tests.log: 18 тестов, 0 failures/errors, BUILD SUCCESS,
  31.334 s. Обе фракции: недостаточный производственный бюджет, полная масса входов,
  отсутствие повторной выдачи, фактический ремонт повреждённого двигателя, частичная
  работа и продолжение после native save. Кампания: команда, реальный такт, сохранённый
  возраст обслуживания, единственное завершение журнала. Смежные добыча/переоснащение,
  промышленное сохранение и миграция хранения оборудования также проверены.
- target/stage23b-longhaul-javadoc.log: свежий строгий Javadoc, BUILD SUCCESS, 13.644 s.
- target/stage23b-longhaul-repair-graphics.log: exit 0, короткий сценарий ремонта в скрытом
  GLFW с текущими classes; keyboard preview/confirm/cancel и точный F8/F9 прошли.
  Этот графический сценарий использует прежнее явное повреждение датчика/владение/мощности;
  новые повреждения двигателей проверены адресными физическими и кампанийными тестами.
- git diff --check с core.safecrlf=false: без ошибок.

23B ACTIVE/PARTIAL; полный Maven verify, упаковка, публикация и ручная приёмка не выполнялись.

### 2026-10-04: физическая очередь переоснащения, до подключения кампании

Добавлены ShipyardRefitQueueState, ShipyardRefitWorkQueue и bounded schema-1
ShipyardRefitQueuePersistenceCodec. Очередь резервирует конечные входящие готовые модули
и место для снятого оборудования по полной физической массе. Резервы ограничивают
производство, сырьё и логистику общего склада наряду с ремонтом и индивидуальным хранением.
Работа распределяется из предоставленного общего бюджета установленной верфи один раз
на завершённый такт. Повторный/пропущенный такт не выдаёт работу. Отмена возвращает
модули, сохраняя фактическое состояние корабля; уже выполненная работа не возвращается.

Завершение предварительно готовит ту же сущность корабля и индивидуальное хранение
снятых модулей. Снятые повреждения/возраст и фактический такт удаления сохраняются;
оборудование не становится pristine stock. Сохранившиеся запасы/тепло/состояние продолжаются
через ShipRefitApplicationService. Потеря контакта, изменение исходного повреждения,
утрата handling, отрицательная чистая проверка целевой вместимости и исчерпание
индивидуальных custody slots приостанавливают соответствующую работу без потери модулей.
При нехватке места для снятого оборудования старт отклоняется до изъятия входящего модуля.

Важная граница: это физический компонент и отдельный codec, **ещё не игроковая очередь
кампании**. Общий native envelope остаётся v8 и не сохраняет pending refits. Доменный
вызывающий код обязан сохранять queue/ship/store/custody совместно и предоставлять
действительную проверку целевой вместимости. Текущие тесты дают явный пустой груз либо
отрицательный guard; они не доказывают обновление грузового sidecar после замены секции.
Следующее подключение требует пересмотра действующей bootstrap-only проверки грузовой
вместимости, общей композиции, исключения параллельного ремонта одного корабля, общего
бюджета верфи и реальных команд/UI/журнала. Повторная установка used modules и их перевозка
также остаются открытыми: текущий вход очереди допускает готовые pristine modules.

Проверки:

- target/stage23b-refit-queue-final-tests.log: 32 теста, 0 failures/errors, BUILD SUCCESS,
  59.608 s. Семь новых сценариев включают реальное частичное продолжение после native save,
  возврат модулей, полный источник снятого оборудования, общие storage layers, конкуренцию,
  исходные повреждения, утрату handling, вместимость и повреждённое framing. Смежные
  ремонт, изготовление, физический refit application и сохранения кампании прошли.
- target/stage23b-refit-queue-javadoc.log: свежий строгий Javadoc, BUILD SUCCESS, 17.720 s.
- target/stage23b-refit-identity-tests.log: после дополнительной проверки границы
  идентификатора удаления семь тестов очереди прошли, 0 failures/errors, 41.358 s.
  Слишком длинное имя отклоняется до резервирования, а не при выдаче снятого модуля.
- target/stage23b-refit-reservations-repair-graphics.log: exit 0, короткая графическая
  регрессия существующего ремонта/preview/confirm/cancel/F8/F9 прошла на текущих classes.
  Это не графическая проверка нового переоснащения: его UI пока не подключён.
- git diff --check с core.safecrlf=false: без ошибок.

Первый прогон исправил ожидаемый тип отказа существующего склада; финальный прогон указан
выше. Полный набор Maven-тестов, clean verify, новая упаковка и ручная приёмка не выполнялись.
23B ACTIVE/PARTIAL; этот срез не закрывает игроковое переоснащение.

### 2026-10-04 — именованная шахтёрская компоновка стратегического грузового корабля

В общий инженерный каталог добавлена `fit.industrial_union.freight.strategic_mining_v1`.
Она использует тот же корпус и меняет ровно один установленный модуль: грузовую секцию
на существующее физически изготовляемое добывающее оборудование. Дальнобойный двигатель,
FTL и остальные назначения сохранены. Добавление определения не устанавливает оборудование
в стартовые активы и не меняет исходную компоновку грузовых кораблей.

Целевые проверки подтверждают неизменность остальных модулей, фактическую добывающую
способность и разрешённый физический план прыжка с конечным запасом реакционной массы.
Регрессия также охватывает производство двигателей и очередь переоснащения.
`target/stage23b-mining-freight-fit-tests.log`: 13 tests, 0 failures/errors/skipped,
BUILD SUCCESS, 28.710 s. Первый запуск выявил ошибочное обращение нового теста к RuntimeState;
тест исправлен на существующий `runtime.derive(fit, state, damage)` и повторно проверен.

Грузовая вместимость и совместное восстановление этой компоновки в кампании ещё не подключены.
Вместимость интерфейса расходников не трактуется как дополнительный независимый рудный трюм.
Native checkpoint остаётся v8; команды, UI и журнал переоснащения остаются открытыми.
Полный набор тестов, clean verify, упаковка и ручная приёмка не выполнялись. 23B ACTIVE/PARTIAL.

### 2026-10-04 — очередь переоснащения в кампании, native v9, трюм и UI

Физическая очередь подключена к кампании и сохраняется совместно с кораблём, складом,
ремонтом, снятым оборудованием и журналом. Native схема/файл v9 добавляет отдельный
bounded schema-1 payload после ремонта. Исторические v1–v8 получают пустую очередь;
нативный v8 с действительным заказом ремонта сохраняет все его материалы и работу.
Композиция проверяет владение кораблём/станцией, исходную физическую сущность/компоновку,
установленный экземпляр верфи, время и вместимость всех складских резервов.

Команды START_REFIT/CANCEL_REFIT используют обычные чистый preview, однократное
подтверждение и проверку актуального checkpoint. Действительные входящие изделия и место
для снятых модулей резервируются без установки или мгновенной работы. Отмена возвращает
оборудование; на паузе прогресса нет. Такты распределяют единый бюджет экземпляра верфи
между ремонтом и переоснащением. Один корабль не может выполнять оба заказа одновременно;
до завершения/отмены запрещены отстыковка, прыжок, передача управления и начало добычи.

Компоновка `fit.industrial_union.freight.strategic_mining_v1` теперь использует отдельный
`module.civilian.miners.freight_excavation_section_v1`: 8 млн кг для руды и 1 млн кг для
расходников в девятимиллионном физическом объёме секции. Предыдущее шахтёрское оборудование
сохранено с его прежними характеристиками. Новый вариант имеет собственный продукт и
полные авторские производственные/сервисные профили той же массы; оба варианта доступны
в меню изготовления. Рудный трюм не дублирует вместимость интерфейса расходников.

Проверка грузовой системы отклоняет переполнение до резерва. Завершение сохраняет прежние
груз и происхождение его партий, уменьшая фактический трюм до 8 млн кг. Исходные pool origin,
ownership ordinal, исторический hull ID и legal affiliation сохраняются; новая fit-ID
семантика допускается только для точной шахтёрской компоновки личного корабля Союза.
Обычная грузовая компоновка возвращается к историческому compatibility fit-ID и 12 млн кг.
Снятая секция сохраняет повреждения и текущий возраст и не становится новым товаром.
Одно завершение создаёт одну запись REFIT_COMPLETED на настоящем такте; своей станции
не начисляются фиктивные деньги. UI показывает целевую вместимость, резерв, прогресс,
отмену и обычные клавиатурные подтверждения.

Проверки:

- `target/stage23b-refit-variant-final-tests.log`: 37 tests, 0 failures/errors/skipped,
  BUILD SUCCESS, 1:42 min; переоснащение, старая добыча/снабжение, очередь изготовления,
  оба производимых варианта, доменная очередь, инженерная компоновка и UI.
- `target/stage23b-refit-campaign-initial-tests.log`: 24 tests, 0 failures/errors,
  BUILD SUCCESS, 1:46 min; первоначальная интеграция v9, проверка player payload framing,
  старые native миграции, использованные модули и регрессия ремонта.
- `target/stage23b-refit-v9-javadoc.log`: свежий строгий Javadoc, BUILD SUCCESS, 18.566 s;
  новая API-страница GeneratedCampaignRefitUi.html проверена по времени генерации.
- `target/stage23b-refit-v9-graphics.log`: exit 0; скрытое окно GLFW 1280×720, NVIDIA RTX 4070;
  клавиатурные preview/confirm, резерв без работы на паузе, F8/F9 с точной очередью,
  отмена и возврат точного складского содержимого. Снимок pending-состояния просмотрен:
  вместимость, прогресс и кнопки читаются и доступны.
- `git diff --check` с core.safecrlf=false: без ошибок.

Ограничения доказательств: реальные команды используют явно обозначенные fixtures
станции, действительных мощностей и поставленного нового модуля; корабль приобретён
из существующего резерва с сохранением оплаты. Финальный такт проверяется с явно заданным
предыдущим выполненным work, чтобы не прогонять сотни тысяч тактов всей кампании. Полный
расход конечной работы проверяет доменная очередь; финальный интеграционный тест проверяет
публикацию всех владельцев, неизменный ненулевой груз и партии, native восстановление и
однократное завершение. Это не доказательство полного обычного получения верфи/оборудования,
полного ожидания в кампании или маршрута добыча–продажа. На раннем тесте поставка груза
из нерегистрируемого внешнего endpoint была исправлена на поставку через настоящий склад.

Полный Maven-набор, clean verify, новая упаковка и ручная приёмка не выполнялись.
Использованный старый fat JAR служил только источником зависимостей; первым шёл текущий
target/classes. Коммерческие верфи, перевозка/переустановка использованного оборудования,
боезапас и другие пункты плана остаются открытыми. 23B ACTIVE/PARTIAL, не RC.

### Повторная установка индивидуально хранимых секций, 2026-10-04

Подключена команда START_REFIT_USED и кнопка установки в строке снятого оборудования.
Предпросмотр и подтверждение используют точный ID модуля и общую физическую очередь.
Входящий модуль остаётся на складе до завершения; отмена освобождает резерв.
Повреждения и возраст сохраняются при установке, учёт вместимости не удваивает его массу.
Native v9 содержит refit schema 2; schema 1 читается без выдачи использованных модулей.
Совместная проверка сохранения отклоняет отсутствующие или изменённые входящие экземпляры.

Выборочный прогон `target/stage23b-used-refit-player-tests.log`: 28 тестов, без ошибок,
1:19. Проверены полный конечный расход работы в доменной очереди, старый payload,
резерв/отмена/загрузка и завершение игроковой команды с сохранением груза и состояния.
Финальный такт кампании использует явно заданную предшествующую работу; это не доказательство
обычного получения верфи или полного ожидания длительного заказа игроком.
Перевозка индивидуальных модулей между станциями и прочие типы оборудования остаются открытыми.

Дополнительный один тест подготовки QA-сохранения прошёл за 43.510 с;
строгая свежая Javadoc-проверка — за 23.819 с (`target/stage23b-used-refit-javadoc.log`).
Графический прогон `target/stage23b-used-refit-graphics.log` завершился успешно на NVIDIA RTX 4070:
кнопка установки снятой секции, клавиатурный предпросмотр/подтверждение, точный резерв,
F8/F9 и отмена без изменения индивидуального модуля. Снимок
`stage23b-used-refit-start.png` в пользовательском TEMP проверен при 1280×720.
Первый QA-прогон загружал работающую кампанию и ошибочно сравнивал её с состоянием
до продвижения тактов; QA-сценарий исправлен постановкой тестового сохранения на паузу
до отрисовки. Полная сборка, общий прогон и ручная приёмка не выполнялись.

### Физическая основа перевозки индивидуальных модулей, 2026-10-04

Добавлена передача конкретного экземпляра между физическими складскими узлами без
добавления взаимозаменяемого нового товара. Проверяются исходный владелец, точное состояние,
совпадение складского учёта, резерв другого заказа, совместимый класс, предельная масса
одного изделия, свободное место и конечная пропускная способность.
Все проверки выполняются до смены владельца и расхода обработки.
ID, исходный корабль, момент снятия, повреждения и возраст обслуживания сохраняются.

`ShipyardModuleTransferWorkQueue` оставляет модуль у исходного владельца до завершения,
копит только фактически выполненную обработку на последовательных тактах и использует
общий бюджет endpoint. Дублированный такт не расходует работу, пропущенные интервалы
не начисляются задним числом. Потеря контакта, несовместимая обработка и заполненный
принимающий склад приостанавливают работу. Отмена освобождает ID без возврата потраченной
обработки. Восстановление требует прежний точный экземпляр; подмена состояния отклоняется.
Отдельный schema-1 codec ограничивает размер, число заданий и вложенные строки,
отклоняет неизвестную версию, дубликаты, обрыв и лишние байты.

Начальные выборочные проверки: `target/stage23b-module-transfer-tests.log`,
18 тестов без ошибок, 43.399 с. Новый тест проверяет полный расход обработки,
продолжение после отдельного checkpoint и цикл source → условный трюм → другой склад.
Эти узлы — доменные fixtures, а не фактический транспорт игрока.

Native v9 кампании не изменён: новая очередь пока не включена в envelope.
Для игрового пути ещё нужны совместный владелец оборудования на борту, общая масса
трюма и двигателей, уничтожение перевозимого оборудования при потере корабля,
согласование резерва с переоснащением, фактическая стыковка и UI погрузки/выгрузки.
Сторонние склады нельзя считать доступными без проверки прав; физическая служба
сама не выдаёт разрешений на доступ и не выполняет путешествие.

Финальный выборочный прогон `target/stage23b-module-transfer-final-tests.log`:
14 тестов без ошибок, 41.844 с, включая существующую грузовую службу и custody codec.
Строгая свежая Javadoc-проверка прошла за 25.253 с
(`target/stage23b-module-transfer-javadoc.log`); `git diff --check` прошёл.
Полный набор, clean verify, упаковка и графическая проверка не запускались:
пользовательский интерфейс этого среза ещё не подключён. Статус 23B ACTIVE/PARTIAL.

### Перевозка снятого оборудования в кампании, 2026-10-04

Подключены LOAD_MODULE, UNLOAD_MODULE и CANCEL_MODULE_TRANSFER: физическая стыковка
с собственной станцией, остановленный личный корабль, совместимая обработка и свободный
трюм проверяются до резерва. Во время обработки блокируются конфликтующие грузовые
операции, ремонт, переоснащение, добыча и выход из дока. UI показывает экземпляры на складе
и на борту, прогресс обработки и отмену; все действия используют предпросмотр/подтверждение.
Оборудование на борту сохраняет ID, повреждения, возраст и историю снятия; товарные
счётчики остаются пустыми. Полная масса входит в суммарный груз, свободное место и
физическую массу корабля. Потеря корабля удаляет перевозимые экземпляры без возврата на склад.

Native v10 добавляет очередь обработки после refit sidecar. Freight v6 хранит точную
зеркальную массу оборудования; совместная проверка сверяет её с экземплярами, владельцем,
вместимостью и фактическим engineering cargo. Native v1–v9 получают пустую очередь;
старые freight payloads читаются с нулевой массой оборудования. Контент не изменён.

Выборочные проверки:

- `target/stage23b-module-transport-player-tests.log`: 17 тестов, без ошибок, 1:10.
- `target/stage23b-module-transport-final-tests.log`: 17 тестов, без ошибок, 2:47;
  включены переполнение общей массы, native v9 migration, настоящий прыжок с сохранением
  и уничтожение перевозимого оборудования, а также снабжение и player payload framing.
- `target/stage23b-module-transport-regression-tests.log`: 6 тестов, без ошибок, 1:15;
  сохранены обычные перевозки сырья, маршруты, лоты, уничтожение и материальные идентификаторы.
- `target/stage23b-module-transport-final-javadoc.log`: строгая свежая проверка прошла,
  32.830 с, после дополнения описаний параметров новых API.

Первый проверочный прогон выявил использование старого ограниченного реестра продуктов
в freight binding; заменён на общий авторский реестр снятого оборудования.
Графический probe проверяет клавиатурные LOAD/UNLOAD, подтверждение, резерв,
F8/F9 и отмену в скрытом окне на NVIDIA RTX 4070, 1280×720, с текущими target/classes.
Старый fat JAR используется только для зависимостей. Полная сборка, упаковка и общий
набор тестов не запускались. Получение станций/оборудования и предшествующая работа
в тесте заданы fixtures; финальные интервалы реальны. Это не доказательство обычного
полного получения двух станций, длительного ожидания и полного рейса доставки игроком.
Ручная приёмка остаётся после всего этапа 23, статус 23B ACTIVE/PARTIAL.

Итоговый `target/stage23b-module-transport-graphics.log` завершился с exit 0.
Снимки `stage23b-module-load.png` и `stage23b-module-unload.png` в пользовательском TEMP
просмотрены: действия и состояние читаются при 1280×720. Подписи трюма уточнены,
после чего перекомпилирован только один UI-файл и повторён короткий графический probe.
`git diff --check` прошёл; полный Maven-прогон и ручная приёмка не выполнялись.

### Согласование личных ресурсных наблюдений, 2026-10-05

Список «Личные открытия» использует общую `PersonalResourceUiProjection` с локальным
инспектором. Все сохранённые личные наблюдения ресурсных объектов видны в списке, включая
диапазоны содержания/извлекаемой массы, уверенность, время и актуальность уже существующей
оценки. Полученный образец без измерений по-прежнему не раскрывает содержание или запас.
Чужой реестр не принимается как личный; неизвестные объекты не добавляются по физическому миру.
Обнаружение без точного положения не даёт переход на карту. Ключ строки включает систему,
тип объекта и его идентификатор, поэтому одинаковые локальные ID не конфликтуют.
Построение списка проходит по наблюдениям один раз, без повторного поиска каждой записи.

`target/stage23b-resource-intelligence-final-validation.log`: JDK 17, 23 целевых теста,
без failures/errors/skips, свежий строгий Javadoc, BUILD SUCCESS, 1:01.
Проверены сохранение/устаревание существующей ограниченной оценки через discovery codec,
разделение личного и чужого знания, отсутствие выдуманного положения, идентичность инспектора
и списка, а также реальная добыча образца и полный checkpoint кампании после загрузки.
Первый прогон обнаружил недопустимый fixture нового теста: ресурсное свидетельство требует
известного статического положения. Для проверки простого обнаружения fixture исправлен на
отсутствие ресурсной оценки; ограничения доменной модели сохранены.

Это согласование отображения имеющихся наблюдений. Обычный путь получения новых измерений,
фильтрация всех карт/наложений и доступ к другим закрытым сведениям остаются открытыми.
Полный suite, графический прогон и ручная приёмка для этого среза не выполнялись.
23B остаётся ACTIVE/PARTIAL; новый срез не закоммичен.

### Строительство промышленных установок и ввод ресурсов, 2026-10-05

`Stage18FacilityConstructionWorkQueue` подключён к existing construction authority,
generated capture/restore, player preview/submit, world ticks, production UI и журналу.
Точный bill атомарно изымается в заказ и продолжает занимать физическое складское место.
Работа установки расходуется только на завершённом такте: изготовление и строительство
на одной линии используют один конечный инженерный бюджет. Завершённый заказ сохраняет
evidence и добавляет отключённую установку в live registry. Дублирующее принятие не меняет
её позднейшее состояние. Checkpoint без installation либо с работой из будущего отклоняется.

Команда распределения ресурсов сохраняет суммарные энергию, теплоотвод, труд и обслуживание
станции, забирая нужные значения у других установок. При нехватке отвергается вся операция;
возможности всех установок и верфей пересчитываются. Новые ресурсы не выдаются.

Проверки: `target/stage23b-construction-final-validation.log` — 21 тест и строгий Javadoc;
`target/stage23b-facility-allocation-validation.log` — 18 тестов и строгий Javadoc;
окончательный `target/stage23b-construction-command-final-validation.log` — 13 тестов,
строгий Javadoc, BUILD SUCCESS, 42.906 с. Последний прогон проверяет настоящее подтверждение
и отмену игроковой команды после явного berth fixture, одноразовость token, невмешательство
preview, точный возврат сырья и существующие player persistence сценарии.

Эти проверки ещё используют явные ownership/supply/berth fixtures. Обычное строительство
самой станции, поставка сырья, физическая постройка верфи и прочие пути принятого остатка
не объявляются завершёнными. Полный suite и итоговая графическая проверка ещё впереди.
23B остаётся ACTIVE/PARTIAL; изменения этого среза не закоммичены.

### Готовые изделия в личном физическом трюме, 2026-10-05

Freight schema 7 хранит отдельные countable-product lots: общий cargo ID, действующий FleetId,
product ID, количество, настоящую станцию и время погрузки. Реальный stock переносится через
общую Stage18 logistics; source evidence создаётся после успешного конечного переноса.
Частичная выгрузка сохраняет исходное evidence. Полный вес складывается с raw commodity
и повреждённым индивидуальным оборудованием. Потеря корабля удаляет оставшиеся изделия.
Historical schemas 1–6 читают свой формат и не получают новый груз. Нативный внешний envelope
остаётся v10; его вложенный freight payload имеет самостоятельную версию.

Полный checkpoint требует фактического личного владельца и отвергает будущую погрузку.
Все штатные копирования fingerprint/геометрии сохраняют новые лоты. Тест с явным supplied-product
fixture сохраняет груз и fitted mass, затем выгружает на физический склад и сохраняет результат.
Это доказательство persistence seam, а не обычного изготовления и игроковой доставки.

Добавлен резерв finished-product counts для будущей обработки: реальный stock/масса остаются
у источника, другие операции видят только не зарезервированный остаток. Пока production commands
его не вызывают; он должен восстанавливаться из будущей очереди. Overflow целого счётчика
товаров теперь отклоняется до изменения любого склада и бюджета.

`target/stage23b-finished-product-cargo-final-validation.log`: 24 теста, строгий Javadoc,
BUILD SUCCESS, 55.663 с. Проверены старые v3/v4/v6 layouts, source/hold conservation,
partial unload, destruction, gross mass/capacity, source reserves, production/refit регрессии.
`target/stage23b-finished-product-cargo-watermark-validation.log`: окончательные 6 тестов
и строгий свежий Javadoc, BUILD SUCCESS, 44.973 с; future-loading guard проверен полным checkpoint.

Сохраняемые многотактовые player handling commands, журнал и UI доставки ещё впереди.
23B остаётся ACTIVE/PARTIAL. Полный suite, итоговый graphics и коммит этого среза не выполнены.

### Native v11: конечная обработка готовых товаров (2026-10-05)

Предыдущий freight-only статус superseded: многотактовая обработка теперь подключена к
LOAD_PRODUCT / UNLOAD_PRODUCT / CANCEL_PRODUCT_TRANSFER, production UI и журналу. Native v11
сохраняет источник, назначение, количество, начало и выполненные килограммы обработки;
freight schema остаётся 7. Настоящий source reserve блокирует конкурентное потребление без
дублирования товара/массы; общий endpoint budget не открывается повторно при завершении.
Одноразовое разрешение публикует физический перенос и provenance; отмена освобождает товар,
но не возвращает работу. Capture/restore и player/world transitions сохраняют очередь.
Исторический v10 принимает genuine layout и получает пустую очередь без выдачи ресурсов.
9 tests + strict Javadoc (52.451 s), затем native/initial-owner/UI regression 14 tests (45.948 s),
оба BUILD SUCCESS. Начальные ownership/docking/stock fixtures отмечены; обычное изготовление
и приобретение требуемых активов этими проверками не объявляются доказанными.

### Приобретение существующей гражданской станции (2026-10-05)

Authority, production UI и журнал поддерживают PURCHASE_STATION с точным confirmation token.
Продавец — настоящий владелец промышленного объекта; физическая стыковка, собственное наблюдение,
предложение, кошелёк и юридическая ссылка проверяются перед conserved payment. OwnedStationRef
указывает на прежний объект, stock/facility/yard state сохраняются. Никакого ремонта, энергии,
товаров, промышленной работы или новой инфраструктуры покупка не создаёт. Явная sale policy v1
содержит цены и защищает military/mining designs; чужие внутренние запасы не раскрываются.
Исправлена новая commissioning-привязка рынка: asset owner вместо territorial controller.
Старые checkpoints сохраняют прежние faction components; mismatched seller не получает offer.
9 targeted tests + strict Javadoc, BUILD SUCCESS (2:04); положительная геометрия прибытия
остаётся explicit fixture, ownership/funding/stock/work — actual conserved owners.
Initial distant purchase отклоняется. 23B остаётся ACTIVE/PARTIAL, полный suite и final graphics впереди.
Окончательные 11 start/physical/payment/UI regression tests прошли (2:05): неверный продавец,
нулевая цена, недостаток средств, foreign/stale tokens не меняют владельца или деньги.

### Native v12: физическое строительство верфей (2026-10-05)

Отдельный каталог задаёт настоящие килограммы трёх существующих проектов верфи и
использует общие материальные/рабочие профили Stage-18H. START_YARD_CONSTRUCTION и
CANCEL_YARD_CONSTRUCTION требуют собственной физической станции и стыковки. Очередь
держит полный материальный резерв в пределах вместимости и выполняет конечную работу
той же линии после изготовления и строительства установок, без второго бюджета.

Native v12 сохраняет отдельный bounded schema-1 sidecar. Genuine v11 получает пустой
заказ без структурных, материальных или рабочих grants. Составной checkpoint проверяет
реального владельца, склад, место, watermark и соответствие законченной структуры.
Player/world transitions сохраняют очередь. Завершение устанавливает отключённую верфь
с нулевыми power/work/staff/automation и создаёт запись журнала; повторное принятие
не сбрасывает её состояние. UI показывает физический состав, доступность, прогресс и отмену
до начала работы.

Обычная поставка полного bill, достаточное хранилище и ввод работающей верфи остаются
открытыми. Completed-structure fixture подтверждает только совместное сохранение,
а не получение активов игроком. Статус 23B ACTIVE/PARTIAL.

`target/stage23b-yard-native12-final-validation.log`: 11 targeted tests + strict Javadoc,
BUILD SUCCESS (1:55). `target/stage23b-yard-native12-ui-regression.log`: 4 production UI,
finished-product transport and station acquisition tests, BUILD SUCCESS (1:05).
Полный suite и final graphics остаются итоговыми проверками после закрытия игровых путей.

### Поэтапная физическая поставка материалов верфи (2026-10-05)

Новые заказы начинают с пустой стройплощадки. Доступные authored материалы перевозятся
с собственного склада на реальных тактах в custody точного заказа, ограниченную его bill.
Это не общий склад: произвольные товары и второй комплект материала туда не допускаются.
Полная поставка через малый склад освобождает место для следующих партий; ни материал,
ни мощность из времени не возникают. Engineering work начинается после всего состава.

Доставка делит TransferBudget с обработкой новых изделий и снятых модулей. Повторный такт
не повторяет перемещение, реальная поставка фиксируется журналом. Отмена до работы
возвращает только доставленное и требует свободного места на исходном складе. UI объясняет
автоматическую поставку и показывает доставленную массу отдельно от выполненной работы.

Yard schema 2 при том же outer native v12 сохраняет mode и bounded commodity map;
schema 1 остаётся прежним warehouse-only резервом без повторного material grant.
Обычное экономическое приобретение полного запаса и ввод работающей верфи остаются открытыми.
# 2026-10-06: начальные размещения диспетчеров

Генерация supply offers подключена к обычным обзорам диспетчеров. Основание — существующий
свой freight order с реальной задержкой/уничтоженным перевозчиком, собственный известный
гражданский получатель и недостаток его товара относительно ёмкости назначенного судна.
Партия ограничена 1000 кг и фактическими свободным местом/handling за такт; эскроу берётся
из казны сверх reserve floor. Стабильный cause ID содержит order, число задержек и lost
flag. Даже завершённый/отклонённый договор сохраняет ссылку, поэтому reload не выдаёт
ту же заявку повторно. Чужие склады не читаются для генерации. Премия на кг: 1000 milli
плюс 20% базовой открытой commodity quote; реальная продажа остаётся отдельной сделкой.

Количество выбирается игроком при принятии. Меньшая партия закрывает старое предложение
с полным возвратом и получает отдельный funded invoice с пропорциональной премией;
ни один active contract не теряет точную treasury funding provenance. Preview изолирован,
stale/repeated token отвергается. Production UI имеет +/- кг, показывает точную премию,
при изменении количества сбрасывает подтверждение и после принятия выбирает новый invoice.
Подключены котировки всех настоящих Stage18 commodities вместо прежних трёх строк,
включавших несуществующий физический ore alias. Старые открытые цены воды/сплава сохранены;
новые cargo identities не подменяются старым alias. Строки только у фактического dock и
совместимых storage/handling; внутренние чужие количества не раскрываются.

Проверки используют действительное уничтожение существующего NPC freighter, реальные
начальные активы, банки и warehouse stocks, без внедрения shortage fact/stock/cash.
Проверены финансирование, сохранение, отказ повторной выдачи после refund/restore,
принятие меньшей партии и реальные BUY/SELL у получателя без контрактной выплаты.
Прибытие по-прежнему задаётся явной геометрией. **Положительная обычная сырьевая цепочка
не доказана:** исходный проводниковый stock seed 1 находится у получателя (`industry.2`),
на внешней продаже его нет. Предыдущая попытка trader-only fulfillment была неверной
для этого исходного состояния; её лог сохранён в
`target/stage23b-npc-supply-external-stock-unavailable.log`. Для положительной цепочки
нужны обычное получение добывающего оборудования и настоящий mining loop из пункта 1.
Переносить stock или подставлять инструменты вместо этого запрещено текущей целью.

`GeneratedCampaignNpcSupplyOffersTest,GeneratedCampaignPlayerMissionIntegrationTest,GeneratedCampaignPilotPhysicalTest`:
20 тестов без ошибок, `target/stage23b-npc-supply-causality-validation.log`.
Окончательный новый-API прогон `GeneratedCampaignNpcSupplyOffersTest,GeneratedCampaignPersonalSupplyContractTest`
и Javadoc прошли: `target/stage23b-npc-supply-final-validation.log`, BUILD SUCCESS (1:24).
Внешняя успешная выплата во втором тесте остаётся явно транзакционной с finite stock fixtures.
23B остаётся ACTIVE/PARTIAL; полный UI общения/доступные предложения в личном журнале
и ordinary mining/delivery journey ещё требуют реализации и проверки.

Native v15 / физический supply predicate: appended `PLAYER_SUPPLY_DELIVERY_KG_AT_LEAST`
содержит точные станцию, систему, commodity и целые требуемые кг. Предложение требует
архивированного фактического склада и реального legal market v2 владельца. Обычный
world/player reconcile возвращает pending/not proven: складской stock или доставка NPC
не заменяют личный receipt. Исполнитель принимает только ACCEPTED до deadline, реальный
active owned fleet/current dock и receipt текущего такта строго позже принятия. Учитываются
только доли с исходным endpoint, отличным от получателя; payout использует held escrow
и одноразовый claim. COMPLETED, нулевой escrow и репутация сохраняются.
SELL authority вызывает этот исполнитель, показывает новое условие в UI и разделяет
деньги продажи/контрактной выплаты в журнале. Native v1–v14 не могут заявить новый
predicate; прежняя layout читается writer v15 без выдачи новых обязательств.

Проверены реальное финансирование из казны, отказ в награде за круговую перепродажу,
выплата внешней поставке после восстановленного service, отсутствие второй выплаты,
native v15 round-trip и отказ под fake v14 header. **Сценарий транзакционный**: shortage fact,
геометрия и два конечных stocks заданы явно; не является ordinary journey или production
offer generation. Первые regression 12 тестов прошли; окончательный целевой прогон
`GeneratedCampaignPersonalSupplyContractTest,GeneratedCampaignPilotPhysicalTest,Stage228GeneratedCampaignPersistenceCodecTest`
прошёл 10 тестов без ошибок: `target/stage23b-supply-contract-native15-validation.log`.

Происхождение поставки: transient receipt дополнен точными списанными долями CargoLotState.
Общий selector FIFO используется и физическим списанием, и построением свидетельства;
ID, исходный endpoint/provenance и loaded time сохраняются. Невыгруженный остаток остаётся
исходной партией, без переименования в новую покупку. Список долей immutable. Проверена
смешанная поставка добытого груза и покупок на двух складах, включая частичную последнюю
партию, точную сумму и save/load оставшегося hold. Эти данные пока не начисляют награду:
условия допустимого происхождения и сам supply-contract executor остаются работой впереди.
Проверки: `Stage20PersonalExtractionCargoTest,GeneratedCampaignPilotPhysicalTest`,
`target/stage23b-delivery-provenance-validation.log`, BUILD SUCCESS.

Физическое свидетельство поставки: `Stage20FreightRuntime.deliverPersonalCommodity`
выполняет существующий finite cargo transfer и создаёт transient receipt только после
успеха. Корабль, endpoint, товар, точная масса и время закреплены private constructor;
claim принимается только создавшим runtime один раз. Чужой/восстановленный runtime не
получает право на receipt. Обычная SELL-команда использует этот transfer внутри existing
financial settlement и потребляет receipt после успешного расчёта. Новых денег, товаров
и checkpoint grants нет. Дополнительная контрактная выплата ещё не подключена; понадобится
проверка исходных cargo provenance и атомарное завершение соответствующего договора.
Проверки: `Stage20PersonalExtractionCargoTest,GeneratedCampaignPilotPhysicalTest,GeneratedCampaignPersonalMiningTest`,
16 тестов, 0 ошибок, `target/stage23b-personal-delivery-receipt-validation.log`, BUILD SUCCESS.

Обновление знаний: существующие production-диспетчеры подключены к штатным actor reviews.
Каждый факт принят только из текущего snapshot собственной фракции. Повторная доставка
того же ID идемпотентна, изменение его свидетельства запрещено. Старые наблюдения того же
грузового заказа удаляются только при отсутствии ссылки любого сохранённого контракта;
таким образом прошлое обоснование эскроу не переписывается новым обзором. Ни restore,
ни review не создают персонажей, договоры, деньги или груз.
Проверки: `GeneratedCampaignNpcPlacementTest,Stage21HNpcMissionServiceTest`,
`target/stage23b-npc-live-knowledge-validation.log`, BUILD SUCCESS. Физический такт обновляет
сведения; 20 последовательных тестовых обзоров сохраняют исходный факт контракта и одну
последнюю запись вместо накопления 20 неподтверждённых старых записей.

Дополнение: конкретный posting сохраняется отдельным фактом NPC. Новые предложения
диспетчера и передача ему открытия требуют доступности, личного корабля и фактической
стыковки (не более 1 км и 1 м/с) у его станции. Контракты в личном UI используют ту же
проверку. Уже принятый контракт не требует повторной стыковки для отмены.
Проверены отказ при одном briefing, разрешение после настоящей команды DOCK с явно
заданной тестовой геометрией и сохранение этого разрешения после restore.
Также устранён null activeFleetId в проекции готовых изделий для игрока без корабля.
`GeneratedCampaignNpcPlacementTest,GeneratedCampaignPlayerMissionIntegrationTest`:
17 тестов, 0 ошибок, `target/stage23b-npc-contact-validation.log`, BUILD SUCCESS.

Новая Stage228-кампания устанавливает по одному торгово-логистическому NPC на фракцию,
имеющую существующую гражданскую станцию с собственным архивированным положением.
Сведения NPC происходят из локального архива и фактического freight ledger владельца;
физические запасы, корабли и финансы не изменяются. Восстановление checkpoint не вызывает
размещение и сохраняет пустые исторические списки. Генерация оплачиваемых предложений
и полная привязка общения к месту остаются незавершёнными.

Целевая проверка: `GeneratedCampaignNpcPlacementTest,GeneratedCampaignPilotStartTest`,
`target/stage23b-npc-placement-validation.log`, Maven завершился с кодом 0.
