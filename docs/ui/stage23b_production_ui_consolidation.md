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
| Contacts | Existing bounded Stage-21H NPC/mission rows and directed reputation | Empty authority produces an empty screen; no NPC or funded contract is synthesized by UI |
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

The M22.8 v4 composed save remains unchanged. UI navigation/query/density are transient
presentation preferences. All displayed durable domain values come from the existing checkpoint
and compare identically after its native codec round trip. A new player sidecar is **not** smuggled
into a presentation-only change. Its necessary schema/migration/start-ownership decision is part
of the still-open 23B integration work, subject to the 23A change-control contract.

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

1. Durable generated-campaign `PlayerState` and explicit lawful starting ownership/funding; supported
   save adoption must not synthesize a player wallet/assets.
2. One runtime composition of player movement/docking/trade/mining/fitting/construction/fleet,
   faction/NPC/carrier command services, validated preview/submit, domain commit and ongoing lifecycle.
   Immutable sidecars exposed by the coordinator are not proof of live command execution.
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
