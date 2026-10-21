# Star Empires — M22.7 Integrated Campaign Handoff roadmap

> **Статус:** PLANNED — только после полного завершения и merge M22.6.  
> **Позиция:** `M22.6 core pair balance/freeze → M22.7 integrated campaign handoff → Stage 23 Polish / RC`.  
> **Основание:** аудит связности проекта от 2026-09-04, выполненный на `main` `93b9b6729634bbdd37eea962c1d1047cfc4af282` и отдельно на рабочем PR #355.  
> **Назначение:** закрыть интеграционный разрыв между уже реализованными Stage 20–22 подсистемами и обычной длительной кампанией до начала RC-polish.

## 1. Почему нужен отдельный M22.7

Stage 23 по принятому контракту не должен впервые создавать живую кампанию, новую simulation authority или исправлять фундаментальную причинность. Его задача — UX, onboarding, art/audio polish, performance hardening, recovery, packaging и RC validation.

Аудит показал другой класс риска: часть уже готовых runtime/UI/content подсистем существует раздельно и не гарантированно проходит через один production client, один campaign lifecycle и одно compositional save/load состояние.

Поэтому M22.7 является **handoff/integration gate**, а не новой feature stage.

Hard rule:

```text
никакой новой параллельной экономики / diplomacy / warfare / fleet / construction / logistics authority
→ только композиция существующих Stage 17–22 owners
→ минимальные reusable seams, если аудит кода докажет их отсутствие
→ один production campaign lifecycle
→ один causal save/load continuation contract
```

M22.7 не меняет frozen M22.6 balance numbers без отдельного defect/evidence record. Если интеграция вскрывает реальный causal defect, он исправляется в общей authority и проходит повторный затронутый balance regression.

---

# 2. Entry gate после M22.6

M22.7 запрещено начинать до выполнения всех обязательных M22.6 exit criteria:

- integrity/content legality closed;
- AI competence доказана до массового tuning;
- equal-burden normalization завершена;
- B00–B14 и B18–B20 paired evidence принято;
- outlier/event-trace review завершён;
- causal content/policy tuning повторно прогнан полными mirrored batches;
- core manifests/profiles/content fingerprints/scenario versions frozen;
- signed-off balance report существует;
- exact M22.6 PR head SHA прошёл required CI;
- M22.6 merged в `main`, post-merge `main` проверен.

Отдельно handoff должен унаследовать исправления причинности, найденные в PR #355: restoration оплаченной station engineering completion, pre-validation engineering repair contracts, single-surface penetrating residual accounting и battle-local weapon catalog usage. Эти исправления не считаются принятыми M22.7 только по факту наличия в ветке — нужен accepted M22.6 merge evidence.

---

# 3. M22.7A — единый production campaign coordinator

## Цель

Один обычный launcher должен запускать не набор параллельных demo/runtime paths, а compositional campaign, в которой уже существующие owners продолжают владеть своим authoritative state.

## Обязательные владельцы состояния

Coordinator обязан **композировать**, но не подменять:

- Stage 20/20.5 generated world runtime и physical materialization;
- Stage 17 economy / treasury / policy / territory / diplomacy authorities;
- Stage 15 fleet identity/orders/movement;
- Stage 17.5 engineering/fitting/combat/repair authorities;
- Stage 18 resources/industry/manufacturing/shipyard authorities;
- Stage 19 warfare/tactical execution;
- Stage 21 goals/diplomacy/readiness/operations/occupation/recovery/NPC/missions/reputation;
- Stage 22 content/profile/manifest/fingerprint bindings.

Coordinator может владеть только orchestration/lifecycle state, которое действительно не принадлежит нижележащим systems: schedule cursors, campaign-level composition metadata, supported save schema composition, launcher/session lifecycle.

## Требования

- `run-generated-world.bat` или его production successor запускает этот единый lifecycle;
- final Stage-21 UI projection подключена к тому же runtime, а не к отдельной test-only composition;
- military/civilian generation и runtime visual/content binding используют frozen Stage-22 package catalogs;
- autonomous faction decisions исполняются через существующие command/authority seams;
- player commands идут в те же validators/authorities, что AI equivalents;
- pause/time acceleration/render FPS не становятся simulation authority;
- demo/test launchers остаются явно маркированными и не выдаются за production client.

## Acceptance

Из одного launcher без ручного переключения приложений можно:

1. загрузить/создать generated campaign;
2. наблюдать автономные решения двух core factions;
3. выполнять player economic/fleet/NPC actions;
4. увидеть реальные последствия diplomacy/operations/logistics/industry;
5. сохранить игру;
6. перезапустить тот же launcher;
7. продолжить ту же campaign с теми же stable IDs и causal state.

---

# 4. M22.7B — simulation-time scheduling и deterministic logistics dispatch

## Проблема

Generated-world client не должен принимать экономические/logistics решения по wall-clock cadence. При ускорении времени или разном FPS это создаёт различное число/момент decisions на одинаковом simulation interval.

## Требования

- logistics/autopilot/actor review scheduling выражено через simulation ticks/time;
- следующий decision tick является deterministic state и сохраняется, если влияет на continuation;
- никакой gameplay-critical cadence от `sleep`, render frame count или real-time polling;
- shipment batch определяется physical order demand, available stock, cargo capacity, legal access, loading/throughput constraints и ordinary transaction rules;
- hardcoded one-unit/one-kilogram-per-render-cycle limits запрещены как production dispatch authority;
- reload не добавляет и не пропускает decision относительно сохранённого simulation tick.

## Determinism acceptance matrix

Одинаковый initial save + одинаковый command stream должен давать одинаковый authoritative state/hash на одинаковом simulation tick при:

- разных render FPS;
- 1× / 2× / 4× / 8× time acceleration;
- pause/resume;
- save/load посередине dispatch interval;
- headless и production-client execution, где presentation не меняет authority.

---

# 5. M22.7C — единый asset/content resolver и минимальная production visual closure

## Цель

Убрать разрыв между Stage-20.5 legacy visual catalog, Stage-22 faction manifests и реальным production client.

## Resolver contract

Все карты/tactical/local/inspector surfaces используют один deterministic resolver как минимум по:

```text
stable entity/hull identity
+ hull family/design
+ legal fit fingerprint
+ faction visual profile
+ authored asset maturity/status
+ damage/engine/runtime state
+ content/profile version
```

Нельзя иметь отдельный hand-maintained sprite switch для каждого viewer.

Missing/invalid binding должен давать явную diagnostic/fallback classification; silent fallback к устаревшему Stage-20.5 изображению запрещён для `PRODUCTION` content.

## Empire / Union parity gate

До Stage 23 требуется минимум:

- устранить resolution/detail-class mismatch, из-за которого одна core faction получает существенно более слабые production sprites в обычном клиенте;
- in-engine review трёх сравнительных role pairs: destroyer/escort, freighter, support/logistics;
- silhouette readability на actual gameplay zoom;
- fit/module-anchor agreement;
- damage/emissive/engine layer alignment, если layer заявлен production manifest;
- screenshot evidence именно из ordinary production client.

Полное художественное доведение всех release assets остаётся Stage 23E, но M22.7 не может передать в Stage 23 client, который выбирает неправильный каталог.

## Character calibration gate

M22.7 должен иметь реальные engine-bindable calibration images обеих core factions для representative roles, построенные по:

`Character Master Prompt + faction visual bible + role + individual brief`.

Минимум:

- worker/technician;
- fleet/officer role;
- senior/administrative role.

Нужен cross-lineup human review: faction recognizability, role/status readability, shared hand-painted style consistency. JSON/description без raster/engine-bindable asset не считается visual completion.

---

# 6. M22.7D — compositional campaign save и causal first-hour vertical journey

## Unified save contract

Production campaign save обязан включать все подключённые late-stage layers, а не только Stage-20 generated runtime:

- generated-world physical state;
- player/fleet ownership and orders;
- faction goals/doctrine review cursors;
- diplomacy/treaties/crises/wars;
- readiness/command groups/strategic operations;
- occupation/territorial transition;
- post-war recovery/replacement;
- NPC identities/knowledge/events;
- missions/reputation/discoveries;
- industry/construction/repair/refit states;
- content/profile/visual fingerprints required for safe restore.

Сохраняются stable IDs и existing owners. Нельзя сериализовать второй competing snapshot истины поверх authority state.

## Failure rules

- unsupported/corrupt component fails closed before mutating the active campaign;
- partial restore не оставляет половину subsystems в новом состоянии;
- unknown future schema/fingerprint diagnostic понятен и machine-readable;
- supported migrations имеют deterministic roundtrip/continuation fixtures.

Stage 23G затем добавляет user-facing slots, rotating autosaves, backup/recovery UX и расширенную diagnostics policy.

## First-hour causal journey

До RC polish должен существовать executable acceptance journey поверх ordinary campaign state:

```text
первая сделка/контракт
→ physical cargo pickup
→ delivery
→ payment through ordinary transfer
→ maintenance/repair need
→ ship improvement/refit or readiness change
→ первый autonomous fleet/order decision
→ observed faction/logistics consequence
→ save
→ reload
→ continuation without identity/resource/deadline reset
```

Inspector/event history в этом journey должны отвечать:

1. что происходит;
2. почему это произошло;
3. что игрок реально может сделать через валидируемые команды.

---

# 7. M22.7E — operational causality, B08 campaign bridge и client smoke/performance baselines

## B08 production causal chain

M22.6 balance evidence не заменяет production integration acceptance. M22.7 должен провести один и тот же physical convoy identity через цепочку:

```text
tactical/strategic encounter
→ судьба конкретного transport FleetId/entity
→ сохранение/уничтожение/отсечение конкретного cargo lot
→ фактическая delivery/loss
→ изменённый stock/production input
→ изменённый shipyard/industry/readiness outcome
→ изменённый следующий lawful faction decision
```

Нельзя заменить transport/cargo новым scripted объектом между слоями.

Metrics минимум:

- delivered cargo;
- lost cargo;
- same-identity transport survival/loss;
- production downtime/change;
- replacement/repair delay;
- readiness consequence;
- downstream faction decision/event trace.

## Client smoke

CI/validation должен получить обычный production-client smoke, отдельно от headless service tests:

- boot to campaign;
- generated world ready;
- UI projection renders without fatal missing bindings;
- minimal command dispatch;
- save/load smoke;
- controlled shutdown.

Если graphical CI environment не позволяет full rendering, нужен documented split: automated headless composition smoke + scheduled/manual graphical capture on supported Windows environment.

## Performance baseline до Stage 23F

M22.7 не обязан оптимизировать без измерений, но обязан зафиксировать baseline/profiling probes для:

- new-world generation;
- production campaign save/load;
- UI projection cost while running and paused;
- dense civilian traffic;
- representative tactical battle;
- 1×/8× simulation throughput;
- memory/entity/event growth over representative session.

Stage 23F владеет optimization/hardening against these baselines.

---

# 8. M22.7F — documentation, launcher truth и repository governance handoff

Обновить живую документацию так, чтобы обычный разработчик/игрок видел фактический путь продукта.

Обязательно:

- README current stage/status;
- authoritative launcher list: production vs developer/test tools;
- current `GameState`/save composition map;
- campaign coordinator lifecycle and authority diagram;
- asset resolver/fingerprint contract;
- simulation-time scheduling invariant;
- M22.7 acceptance evidence and known limitations;
- Stage 23 entry manifest.

Repository hygiene до Stage 23:

- triage старых открытых PR #210, #242, #243, #284;
- для каждого определить `still required / superseded / partially reusable / close`;
- не merge по номеру/возрасту без diff against current `main`;
- required-check/manual merge gate остаётся обязательным, пока branch protection не гарантирует эквивалентный enforcement;
- diagnostics UI/build metadata должны показывать build SHA и content/profile fingerprint, чтобы bug reports можно было воспроизводить.

---

# 9. Что остаётся Stage 23 после M22.7

M22.7 не поглощает Stage 23. После него Stage 23 получает уже **единую функциональную кампанию** и занимается release quality:

- **23A:** RC scope lock/change control над M22.6 freeze + M22.7 handoff manifest;
- **23B:** production information architecture, search/filter/navigation, inspector polish и validated actions;
- **23C:** resolution/accessibility/input rebinding/RU-EN localization;
- **23D:** onboarding и first-session pacing поверх M22.7 causal journey;
- **23E:** полный final art/VFX/audio replacement и visual polish;
- **23F:** profiler-driven performance/memory/long-session hardening;
- **23G:** save slots, rotating autosaves, recovery UX, migration diagnostics;
- **23H:** ready-to-run Windows package с bundled/supported Java runtime strategy, без Maven/JDK для пользователя;
- **23I:** campaign journeys/manual playtest/regression closure;
- **23J:** exact-package RC acceptance.

Stage 23 MUST NOT begin if production launcher still omits accepted Stage-21/22 state owners or if save/load does not preserve the composed campaign.

---

# 10. M22.7 acceptance suite

## A. Unified campaign ownership

- one production launcher;
- no duplicated economy/diplomacy/fleet/warfare authority;
- ordinary player and AI commands reach existing validators;
- Stage-21 final projection reads the same campaign state.

## B. Deterministic scheduling

- same simulation tick produces same authoritative state across FPS/time-scale matrix;
- save/load preserves decision cadence;
- no wall-clock gameplay dispatch authority.

## C. Causal convoy/industry bridge

- same transport and cargo identities survive across operation → logistics → production layers;
- loss/delivery changes real stock and downstream production/readiness;
- event trace explains the chain.

## D. Production visual binding

- Stage-22 resolver used by ordinary client;
- no silent legacy sprite fallback for production definitions;
- Empire/Union representative role pairs reviewed in-engine;
- representative character raster assets bound and human-reviewed.

## E. Full campaign persistence

- goals/diplomacy/operations/recovery/NPC/missions/industry survive roundtrip;
- stable IDs preserved;
- unsupported/corrupt restore fails non-destructively;
- deterministic continuation hash/evidence accepted.

## F. First-hour journey

A single executable journey completes:

```text
trade/contract
→ delivery
→ payment
→ maintenance/repair/refit
→ autonomous order
→ faction/logistics consequence
→ save/load continuation
```

without tutorial-only grants or test-only state mutation.

## G. Client/CI handoff

- production-client smoke exists;
- representative graphical/manual capture exists where automation is unavailable;
- performance baseline recorded with build/hardware/JVM provenance;
- README/launcher/save/runtime docs agree with actual implementation.

---

# 11. M22.7 completion gate

M22.7 COMPLETE только когда одновременно:

- [ ] M22.6 accepted and merged first;
- [ ] unified campaign coordinator composes existing owners without competing authority;
- [ ] ordinary production launcher exercises Stage 20–22 campaign state;
- [ ] logistics/autopilot gameplay scheduling is simulation-time deterministic;
- [ ] production client uses unified Stage-22 asset/content resolver;
- [ ] representative Empire/Union ship and character assets pass in-engine/human visual gates;
- [ ] B08 same-transport/same-cargo causal chain reaches industry/readiness/decision consequence;
- [ ] composed save/load preserves all connected late-stage layers and stable IDs;
- [ ] first-hour causal journey passes before and after reload;
- [ ] production-client smoke is present in CI or documented scheduled graphical gate;
- [ ] profiling baselines exist for Stage 23F;
- [ ] README/save/launcher/runtime docs synchronized;
- [ ] legacy PR triage recorded;
- [ ] exact PR head required CI green;
- [ ] PR merged and resulting `main` verified;
- [ ] Stage 23 marked OPEN/NEXT only after this evidence.

---

# 12. Canonical sequence amendment

После принятия этого roadmap execution order считается:

```text
M22.0 identity/migration COMPLETE
→ M22.1 faction profile COMPLETE
→ M22.2 shared content seam COMPLETE
→ M22.3 Empire package COMPLETE
→ M22.4 Industrial Union package COMPLETE
→ M22.5 civilian/minor ecosystem COMPLETE
→ M22.6 paired balance/freeze ACTIVE/NEXT
→ M22.7 Integrated Campaign Handoff PLANNED
→ Stage 23 Polish / Release Candidate PLANNED
→ post-core faction horizon
```

Главный acceptance narrative M22.7:

> **Из обычной сборки доставить дефицитный материал, увидеть его влияние на производство, участвовать в охране/перехвате следующего конвоя, получить реальные повреждения, отремонтироваться и продолжить ту же историю после загрузки — с теми же физическими IDs, грузом, целями фракций и causal event history.**

Именно этот gate превращает сильные отдельные simulation systems в одну проверяемую campaign до начала RC-polish.
