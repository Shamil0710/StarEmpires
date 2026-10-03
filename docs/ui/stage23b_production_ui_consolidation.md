# 23B — production UI consolidation

Status: **ACTIVE / PARTIAL**. This is an implemented presentation foundation, not a Stage-23B
completion record. Stage 23C implementation remains blocked. Required remaining work is tracked by
[#412](https://github.com/Shamil0710/StarEmpires/issues/412) and the existing human gate
[#370](https://github.com/Shamil0710/StarEmpires/issues/370).

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
