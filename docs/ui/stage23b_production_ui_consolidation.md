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
