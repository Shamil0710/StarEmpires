# Star Empires — faction implementation roadmap

> **Версия:** 1.0  
> **Статус:** EXECUTION ROADMAP  
> **Дата среза:** 2026-08-26  
> **Current program boundary:** Империя + Индустриальный Союз — core production scope до завершения
> Stage 23; остальные пять крупных фракций — post-core packages, которые не блокируют core release.

Связанные документы:

- `docs/development_roadmap.md` — status authority верхнего уровня;
- `docs/stage22_content_balance_plan.md` — контракт Stage 22;
- `docs/content_production_plan_stage21_23.md` — production waves;
- `docs/factions/faction_gameplay_visual_balance_bible.md` — дизайн семи фракций;
- `docs/factions/faction_balance_validation_framework.md` — evidence и acceptance;
- faction systemic identities, visual bibles и Character Master Prompt.

Этот roadmap детализирует работу, но не переобъявляет stage status. Если status здесь устарел,
приоритет имеет `docs/development_roadmap.md` и merged evidence в `main`.

---

# 1. Целевой результат

После core program игра должна иметь две полностью production-ready, системно и визуально различные
фракции:

- **Империя** — институциональная устойчивость, подготовленная infrastructure, reserves, repair,
  preservation и capital-heavy fleet;
- **Индустриальный Союз** — series production, commonality, throughput, replacement и зависимость от
  больших material flows.

После post-core program тот же мир поддерживает ещё пять самостоятельных packages:

- Директорат;
- Лига Свободных Систем;
- Пограничная Конфедерация;
- Консорциум;
- Кочевой Флот.

Каждая фракция считается реализованной только как сквозной package:

```text
stable identity
→ systemic profile
→ authored industrial/content graph
→ legal physical assets and fits
→ AI decisions through common authorities
→ persistence/migration
→ UI/telemetry
→ ship visual language
→ Character Master overlay
→ reproducible balance evidence
```

Наличие lore, palette или отдельных кораблей без цепочки выше не считается faction implementation.

---

# 2. Неподвижные ограничения

1. Никакой параллельной faction simulation.
2. Faction profile предоставляет данные/политику существующим systems, но не владеет их state.
3. Никаких скрытых faction bonuses к damage, armor, income, repair или AI knowledge.
4. Все assets строятся из существующей component/material/manufacturing authority.
5. Внешний ship art обязан соответствовать legal fit и engineering constraints.
6. Персонажи всегда используют обязательную композицию:

   `Character Master Prompt + faction visual bible + role + individual`.

7. Runtime/world-generated identities не должны молча превращаться в authored sovereign factions.
8. Stable IDs, aliases и migration фиксируются до массового authoring.
9. Stage 21I получает только минимальный decision-ready corpus core pair, необходимый его acceptance;
   финальный Stage 22 content нельзя преждевременно затаскивать в активный 21I PR.
10. Post-core work начинается после core release gates или в изолированном research branch без
    изменения core critical path.

---

# 3. Dependency map

```text
Документы и authority audit
    ↓
ID/migration contract
    ↓
Faction profile schema + catalog validation
    ↓
Core shared content seams
    ↓
Империя package ─────────┐
                        ├→ core pair balance → Stage 22 freeze → Stage 23 RC
Индустриальный Союз ────┘
                                                  ↓
                                     post-core common-mechanic slices
                                                  ↓
                          Frontier → Directorate → League → Consortium → Nomads
                                                  ↓
                                     seven-faction macro validation
```

Зависимость означает «доказательство необходимо до merge», а не обязательный один большой PR.

---

# 4. Workstreams

| Код | Workstream | Основной результат |
|---|---|---|
| W0 | Authority and migration | стабильные IDs, aliases, save compatibility |
| W1 | Systemic profile | data-only faction inputs к common systems |
| W2 | Industrial content | materials, components, facilities, recipes, shipyards |
| W3 | Hulls and fleet | legal fits, role coverage, support assets |
| W4 | AI and doctrine | решения из bounded evidence без omniscience |
| W5 | UI and telemetry | causal projections, fingerprints, balance evidence |
| W6 | Ship visuals | silhouette, modules, sprites, damage states |
| W7 | Characters | shared master style + faction/role/individual overlays |
| W8 | Balance and QA | scenario suite, regressions, reports |
| W9 | Documentation | catalogs, runbooks, manifests, changelog, handoff |

Каждый milestone ниже должен явно отметить затронутые workstreams и дать acceptance evidence.

---

# 5. Phase D0 — design/documentation foundation

## D0.1. Закрепить authority

Deliverables:

- cross-faction gameplay/visual/balance bible;
- каноническая Empire visual bible;
- ссылка на уже принятую Industrial Union visual bible;
- balance validation framework;
- данный implementation roadmap;
- синхронизация development/content roadmaps и Character Master references.

Acceptance:

- семь названий и core/post-core boundary совпадают во всех документах;
- отсутствует формулировка, будто visual bible Союза ещё не создана;
- Empire и Union явно не являются recolors;
- post-core направления помечены как direction, а не готовые production bibles;
- ни один numeric gameplay bonus не объявлен без engineering evidence.

## D0.2. Зафиксировать decision log

Создать или расширить ADR/decision log для решений, которые нельзя безопасно хранить только в prose:

- stable faction ID namespace;
- disposition legacy/generated IDs;
- профильная schema и versioning;
- content manifest/fingerprint ownership;
- граница authored sovereign/minor/transnational/dynamic identity;
- visual asset binding к fit fingerprint.

Exit: D0 merge не меняет runtime behavior.

---

# 6. Stage 21I integration guardrail

Stage 21I закрывает living-world decision/UI/migration acceptance существующей Stage 21 architecture.
Faction work в нём ограничено следующим:

- минимальные Империя/Индустриальный Союз profiles, если они нужны для deterministic decision probe;
- bounded doctrine/goal evidence;
- migration fixtures, необходимые Stage 21I;
- UI projection причин решения;
- никакой массовой линейки hulls, components, art или финальных balance scalars.

Перед merge Stage 21I:

1. перепроверить branch diff относительно актуального `main`;
2. убедиться, что docs PR не коснулся его Java scope;
3. при конфликте статуса сначала merge/resolve Stage 21I, затем обновить docs status отдельным small PR;
4. не backport-ить Stage 22 content ради прохождения 21I demo.

---

# 7. Stage 22 milestone M22.0 — authority inventory и migration

> **COMPLETE.** Machine-readable governance, repository inventory, stable-ID disposition and
> persistence/migration evidence merged in PR #343; post-merge Java-17 verification is green.

## M22.0.1. Полная инвентаризация identities

Построить machine-readable report всех мест, где faction ID появляется:

- authored catalogs;
- world generation;
- demo/bootstrap scenarios;
- saves and codecs;
- diplomacy/territory/war/knowledge state;
- AI doctrine and goal candidates;
- fleet/content ownership;
- UI labels;
- tests/fixtures;
- documentation and examples.

Известные кандидаты, требующие explicit disposition:

| Текущий ID/label | Риск | Требуемое решение |
|---|---|---|
| `faction.imperial_directorate` | смешивает «Империю» и «Директорат» | не auto-map; определить legacy identity или явную миграцию |
| `faction.frontier_union` | двусмысленно относительно Frontier/Union | не auto-map; классифицировать fixture/dynamic/legacy |
| `faction.industrial_combine` | похож, но не равен Союзу | подтвердить minor/legacy или versioned mapping |
| `faction.free_ports` | может быть League-adjacent, не обязательно sovereign | сохранить как minor/network либо явная authored роль |
| `faction.research_consortium` | двусмысленно Directorate/Consortium | не сливать по названию |
| `faction.trade_league` | существующий authored actor | определить minor/transnational/legacy role |
| `faction.miners` | функциональная группа | не повышать автоматически до major faction |
| `faction.neutral` | техническая/политическая neutral identity | документировать invariants |
| `faction.alpha`, `faction.beta` | probes/fixtures | оставить test-only или мигрировать fixture |

## M22.0.2. Identity classes

Каждая identity получает один class:

- `MAJOR_AUTHORED`;
- `MINOR_AUTHORED`;
- `TRANSNATIONAL_NETWORK`;
- `WORLD_GENERATED`;
- `SCENARIO_ONLY`;
- `TEST_FIXTURE`;
- `LEGACY_COMPATIBILITY`.

Class не обязан становиться новым enum, если существующая model выражает это без дублирования.

## M22.0.3. Migration table

Для каждого старого ID:

```text
sourceId
sourceVersionRange
disposition = preserve | alias | migrate | retire-test-only
targetId (если есть)
semanticReason
saveBehavior
collisionBehavior
telemetryEvent
fixture
```

Правила:

- никакого fuzzy match по display name;
- alias не создаёт второго state owner;
- collision вызывает deterministic explicit failure или задокументированное merge rule;
- неизвестный ID не превращается молча в neutral;
- supported saves получают round-trip test;
- unsupported version получает понятное диагностическое сообщение.

## M22.0.4. Exit gate

- inventory покрывает 100% repo references;
- каждое известное identity имеет class/disposition;
- миграционные fixtures зелёные;
- docs и runtime используют одинаковые stable names;
- нет изменения семантики save без version bump/fingerprint.

---

# 8. Stage 22 milestone M22.1 — faction profile contract

> **CLOSURE CANDIDATE in PR #344.** The versioned core-pair schema, existing-authority bindings,
> deterministic fingerprint, bounded persistence sidecar and targeted acceptance coverage are
> implemented. Decision record: `docs/stage22_1_faction_profile_contract.md`. M22.2 remains blocked
> until exact-head and post-merge CI complete.

## M22.1.1. Conceptual data shape

Рекомендуемая логическая запись (название не является обязательным API):

```text
FactionSystemicProfileDefinition
  stableFactionId
  profileVersion
  identityClass
  doctrineProfileRef
  industrialPolicyRef
  procurementPolicyRef
  logisticsPolicyRef
  fleetDoctrineRef
  diplomacyPolicyRef
  territoryPolicyRef
  knowledgePolicyRef
  recoveryPolicyRef
  authoredContentManifestRef
  shipVisualProfileRef
  characterVisualProfileRef
  localizationRef
  compatibilityAliases
```

Поля `...PolicyRef` связывают declarative inputs/weighting с существующими authorities. Они не
создают новые запасы, флоты, treaties или knowledge.

## M22.1.2. Binding к существующим seams

| Потребность | Использовать | Не создавать |
|---|---|---|
| stable/runtime identity | `FactionIdentityResolver`, `WorldFactionIdentityState` | второй registry истины |
| persistent doctrine axes | `FactionDoctrineState` | faction-specific mutable doctrine store |
| strategic preference | `FactionStrategicDoctrineProfile` | scripted goal override |
| candidate generation | `FactionStrategicGoalCandidateResolver` | omniscient faction planner |
| decision weighting | `FactionDoctrineDecisionPolicy` | auto-win switch |
| industrial content | Stage 18 catalogs/runtimes | magic faction inventory |
| ship legality | engineering/component/hull catalogs | art-driven invalid fit |
| fingerprints | existing content-bound persistence pattern | unversioned data load |
| UI | projector/snapshot pattern | UI-owned state |

## M22.1.3. Catalog validation

Validator должен ловить:

- unknown/duplicate stable IDs;
- missing referenced catalog objects;
- circular content dependency;
- illegal physical fit;
- role без production path;
- visual profile без systemic profile и наоборот;
- deprecated alias без migration;
- post-core content, случайно попавший в core manifest;
- inconsistent version/fingerprint.

## M22.1.4. Exit gate

- два core profiles загружаются deterministically;
- profiles проходят save/load;
- schema rejects invalid samples;
- ни один common authority не дублирован;
- diff содержит targeted architecture test и ADR.

---

# 9. Stage 22 milestone M22.2 — shared core content seam

> **NEXT after M22.1 closure.** No role/fit/production/visual production manifest is promoted early.

До faction-specific bulk authoring подготовить общие контракты:

- role taxonomy и mission profiles;
- component/hull/facility manifest format;
- manufacturer/procurement lineage metadata;
- fit fingerprint → visual asset binding;
- localization naming rules;
- balance telemetry hooks;
- visual asset status: `CONCEPT`, `ENGINEERING_APPROVED`, `PRODUCTION`, `DEPRECATED`;
- content maturity: `SEED`, `CANDIDATE`, `VALIDATED`, `FROZEN`.

## 9.1. Initial hull coverage decision

Точный Stage 22 floor остаётся у `stage22_content_balance_plan.md`. Рекомендуемый candidate mapping
для review, основанный на текущем hull doctrine и asset audit:

### Шесть military families

1. Corvette — дешёвая локальная/escort capability;
2. Frigate — sustained escort/patrol combatant;
3. Destroyer — fleet screen/strike-defense role;
4. Cruiser — independent multi-role command combatant;
5. Battleship — capital line/firepower asset;
6. Carrier — отдельная aviation/small-craft family.

### Три support/civil families

1. freight family — general/bulk cargo;
2. tanker/replenishment family;
3. fleet logistics + repair/salvage family с явно разделёнными fits, если один hull family это допускает.

Не считать молча закрытыми:

- отдельный patrol tier;
- battlecruiser;
- light versus fleet carrier split;
- mining geometry/crew loop;
- dedicated repair/salvage;
- non-interceptor small craft;
- civilian passenger/habitat roles.

Unmerged asset/hull manifest используется как candidate evidence, не как canon до review/merge.

## 9.2. Exit gate

- role → mission → fit → production → visual chain валидируется;
- support assets достаточны для заявленной endurance;
- common manifest не кодирует Empire/Union bias;
- authoring template и validation tests готовы до bulk data.

---

# 10. Stage 22 milestone M22.3 — Empire production package

## M22.3.1. Systemic profile

Bind:

- centralized procurement;
- reserves/mobilization decisions;
- capital preservation;
- protected repair/refit infrastructure;
- formal command and readiness;
- overextension and strategic-node dependencies.

Каждый input обязан изменять существующий decision/production path, не итоговый outcome напрямую.

## M22.3.2. Industrial graph

Author:

- material requirements и strategic bottlenecks;
- Imperial component families с long-service/refit logic;
- arsenal/yard/facility capabilities;
- production and repair recipes;
- reserve/spares/munition policies;
- legal substitutions и их цена;
- manufacturers/arsenals как content lineage, не отдельная economy.

## M22.3.3. Hull/fleet package

Для каждого required family:

1. mission profile;
2. legal fit;
3. alternate/refit fit;
4. production route;
5. maintenance/repair route;
6. crew/readiness burden;
7. fleet composition use;
8. intended counterplay;
9. visual brief;
10. deterministic validation scenario.

Empire-specific fleet evidence:

- armored axial combatants;
- protected central citadel;
- visible but protected service/refit logic;
- reserves/support assets как реальная часть fleet;
- высокая survival value при сохранённой infrastructure;
- измеримая цена дальней offensive projection.

## M22.3.4. Ship visual pipeline

1. silhouette thumbnails без цвета;
2. family proportion sheet;
3. top-down engineering overlay;
4. hardpoint/radiator/thruster/service access validation;
5. grayscale blind test против Union;
6. approved Imperial palette/material pass;
7. role/state variants;
8. production sprite/render;
9. damage-state pass;
10. engine-scale/readability capture;
11. asset manifest + fit fingerprint.

Authoritative visual reference: `docs/factions/empire_visual_bible.md`.

## M22.3.5. Character pipeline

Минимальная lineup:

- industrial worker/technician;
- fleet enlisted specialist;
- line officer;
- senior officer;
- civil administrator;
- noble/high official;
- damaged/tired field variant без потери style lock.

Для каждого:

```text
Character Master Prompt
+ Empire visual bible
+ role brief
+ individual brief
```

Проверить отличие статуса через fit/material/insignia discipline, а не через смену базового art style.

## M22.3.6. Exit gate

- весь required content floor существует и физически законен;
- B00–B14 smoke suite пройден solo;
- заявленные strengths/weaknesses видны в telemetry;
- ship silhouette и character overlay проходят gate;
- save/load/fingerprint стабилен;
- нет финального tuning до готовности Union comparison package.

---

# 11. Stage 22 milestone M22.4 — Industrial Union production package

## M22.4.1. Systemic profile

Bind:

- long production series;
- standardization/commonality;
- bulk throughput and replacement;
- route/hub concentration;
- resource hunger;
- changeover/retool inertia;
- practical qualification-based hierarchy.

## M22.4.2. Industrial graph

Author:

- compact component vocabulary;
- repeated modules/common assemblies;
- foundry/mill/assembly facility chain;
- high-throughput recipes and batch behavior;
- spares commonality;
- bulk freight/tanker dependency;
- retool/changeover costs;
- correlated failure/bottleneck surfaces.

## M22.4.3. Hull/fleet package

Те же role contracts, что у Empire, но не те же решения:

- sectional repeated construction;
- common engine banks/housings;
- visible replaceable production modules;
- logistics/industrial hero assets;
- replacement tempo при целых flows;
- отсутствие Imperial citadel/hierarchy silhouette;
- измеримая слабость при disruption и abrupt adaptation.

## M22.4.4. Visual and character pipeline

Ship steps совпадают с общим pipeline, authority:
`docs/factions/industrial_union_visual_bible.md`.

Character lineup зеркалит **functions**, но не costumes Empire:

- assembly worker;
- maintenance specialist;
- production engineer;
- ship/fleet officer;
- logistics coordinator;
- plant director/technical administrator;
- field repair variant.

Статус читается через qualification, responsibility, equipment precision и material quality.

## M22.4.5. Exit gate

- полный required content floor;
- B00–B14 smoke suite solo;
- commonality/throughput/retool effects измеримы;
- Union не является «дешёвой Империей»;
- visual and character gates пройдены;
- package готов к paired tuning.

---

# 12. Stage 22 milestone M22.5 — shared civilian/minor ecosystem

Core factions не должны существовать в стерильной дуэли. Добавить/проверить:

- neutral/minor ports и logistics services;
- freighter/tanker/mining/salvage traffic;
- trade/contract/insurance hooks без преждевременной реализации League/Consortium;
- minor identity classes и localization;
- ownership/access rules;
- convoy/interdiction content;
- market and route shocks;
- migration старых demo/minor actors.

Ограничение: shared mechanic или minor content не должен скрыто становиться post-core faction package.

Exit:

- B08/B16 можно запускать на core pair;
- minor actors не получают major-faction doctrine по fallback;
- civilian assets имеют legal production/support path;
- save migration покрыта.

---

# 13. Stage 22 milestone M22.6 — core pair balance/freeze

## M22.6.1. Последовательность tuning

1. закрыть integrity и content legality;
2. доказать AI competence;
3. провести equal-burden normalization;
4. пройти B00–B14 и B18–B20;
5. исследовать outliers/event traces;
6. менять causal content/policies;
7. повторить full paired batches;
8. заморозить manifests/profiles;
9. создать signed-off balance report.

## M22.6.2. Нельзя принимать

- победу Empire «потому что у неё больше armor» без production/projection price;
- победу Union «потому что дешевле» без material/route/retool cost;
- равный duel как единственный test;
- изменение visuals, скрывающее фактический fit;
- отсутствие support fleet;
- pass на одном seed;
- tuning на локальной незакоммиченной data;
- фиктивные post-core opponents.

## M22.6.3. Freeze artifacts

- core faction manifests;
- profile versions;
- content fingerprints;
- migration table;
- scenario versions;
- balance report;
- visual manifests and approved sheets;
- known limitations;
- Stage 23 regression baseline.

Exit: Stage 22 completion только после merge evidence и зелёного exact-SHA CI.

---

# 14. Stage 23 — polish, hardening, release candidate

Stage 23 не перепроектирует core identities. Он выполняет:

- UX/drill-down причинных цепочек;
- localization и naming consistency;
- performance на full content load;
- save size/load time and migration hardening;
- AI edge cases/outlier recovery;
- sprite scale, damage/readability polish;
- character lineup consistency;
- tutorial/onboarding of faction loops;
- full RC regression;
- documentation/runbook/handoff;
- removal or explicit quarantine provisional/demo content.

## Stage 23 release gates

- никаких open P0/P1 integrity defects;
- все core balance gates на RC SHA;
- supported saves мигрируются;
- docs/catalog/visual manifests совпадают с runtime;
- player понимает visible costs/counters;
- no faction-wide shortcut modifiers;
- clean checkout воспроизводит tests и artifacts;
- main status обновлён только после merged evidence.

---

# 15. Post-core program

Post-core packages не нумеруются как уже принятые официальные stages до отдельного roadmap decision.
Ниже используется внутренний префикс `PF`.

## PF0 — platform readiness review

Перед первой новой фракцией:

- проверить extensibility profile/catalog/migration;
- составить 21 `PairwiseBalanceCard` skeleton;
- определить cost новых common mechanics;
- подтвердить, что core balance baseline не зависит от двух hardcoded IDs;
- создать feature flags/package versioning;
- выбрать порядок по dependency, а не по «крутости» концепта.

## Рекомендуемый порядок

| Package | Почему сейчас | Главная architecture dependency |
|---|---|---|
| PF1 Frontier Confederation | максимально использует repair/salvage/substitution и проверяет low-infrastructure resilience | salvage, repair granularity, heterogeneous maintenance |
| PF2 Directorate | расширяет knowledge/sensors/EW и precision production | bounded knowledge, EW, specialist bottlenecks |
| PF3 League of Free Systems | раскрывает market/contract/distributed ownership | incentives, contracts, insurance/subsidy, coordination |
| PF4 Consortium | строится на зрелых contracts/ownership, добавляет debt/concessions/control | debt, enforceability, legitimacy, asset control |
| PF5 Nomad Fleet | самый архитектурно тяжёлый: mobile habitat/industry/governance | mobile economic nodes, docking/access, mobile persistence |

Порядок можно изменить ADR-решением, но новая dependency должна быть реализована как common system и
получить core regression coverage.

## 15.1. Standard package cycle PFx.0–PFx.8

### PFx.0 — architecture review

- перечислить недостающие common mechanics;
- запретить faction-only authority;
- оценить migration/performance/save impact;
- принять ADR и test plan.

### PFx.1 — production visual bible

- развить направление из cross-faction bible;
- palette/material/proportion/module/marking rules;
- top-down and damage-state rules;
- Character Master overlay;
- negative prompts/anti-cliché checklist;
- grayscale distinction against all released factions.

### PFx.2 — systemic profile

- versioned data;
- existing-authority bindings;
- validators/fixtures;
- AI evidence limits;
- persistence/fingerprint.

### PFx.3 — industrial content

- materials/components/facilities/recipes;
- critical dependencies;
- substitution/retool/repair paths;
- economic and logistics causal chain.

### PFx.4 — hull/fleet content

- required role floor;
- legal fits;
- support/civil assets;
- fleet doctrine;
- counterplay;
- visual binding.

### PFx.5 — AI/UI/telemetry

- doctrine-competent planner;
- bounded knowledge;
- causal UI;
- event trace/balance metrics.

### PFx.6 — solo validation

- viability;
- strengths/weaknesses;
- recovery;
- visual/character gates;
- save/load.

### PFx.7 — pairwise validation

- core pair обязательно;
- каждая ранее released post-core faction;
- mirrored seed batches;
- counterplay/player comprehension.

### PFx.8 — freeze/release

- manifests/fingerprints;
- migration;
- reports;
- docs;
- RC regression;
- roadmap status update после merge.

---

# 16. Post-core package-specific work

## PF1 — Пограничная Конфедерация

Common-mechanic checklist:

- salvage ownership and recovery;
- component condition/refurbishment;
- substitution legality and performance vector;
- heterogeneous spare burden;
- distributed repair without high-tier yard;
- upgrade debt/mixed-generation interfaces.

Critical proof: resilience не превращается в бесплатный ремонт, а low peak не делает package
нежизнеспособным.

## PF2 — Директорат

Common-mechanic checklist:

- sensor quality and information freshness;
- EW/counter-EW;
- automation failure/degradation;
- specialist training/replacement;
- precision manufacturing bottlenecks;
- bounded prediction without future knowledge.

Critical proof: precision преимущество исчезает причинно при деградации сети, но фракция не становится
бесполезной после одного debuff.

## PF3 — Лига Свободных Систем

Common-mechanic checklist:

- distributed ownership;
- contract offers/bids;
- subsidy/insurance/credit incentives;
- mobilization coordination;
- market price and route signals;
- private military/freight availability without magic spawning.

Critical proof: flexibility возникает из incentives/assets, а не из бесплатного выбора любого unit.

## PF4 — Консорциум

Common-mechanic checklist:

- ownership separated from sovereignty;
- debt and collateral;
- concessions and access rights;
- contract enforceability;
- reputation and legitimacy;
- leveraged cascading risk;
- asset control without instant mind-control.

Critical proof: economic control создаёт strategic leverage, но всегда имеет enforceability и political
cost.

## PF5 — Кочевой Флот

Common-mechanic checklist:

- mobile habitat/industry/storage nodes;
- population/crew continuity;
- docking/transit/access agreements;
- mobile production and repair limits;
- fleet splitting/rejoining state;
- interception and evacuation;
- save/load of moving economic graph.

Critical proof: мобильность не равна неуязвимости; потеря core должна быть тяжёлой, но не обязательно
мгновенным game over без response window.

---

# 17. Visual production roadmap

## 17.1. Ship asset packet

Для каждого hull/family хранить:

```text
stableAssetId
factionId
hullFamilyId
fitFingerprint
visualProfileVersion
orthographicScale
silhouetteSheet
moduleCalloutSheet
paletteMaterialSheet
markingVariants
damageStates
engineSpriteOrRender
sourcePromptAndNegativePrompt
reviewStatus
```

## 17.2. Review order

1. function/fit review;
2. silhouette review;
3. faction distinction review;
4. role readability review;
5. palette/material review;
6. scale/in-engine review;
7. damage/variant review;
8. manifest/fingerprint review.

Цвет не спасает слабый silhouette. Ornament не скрывает illegal fit.

## 17.3. Character asset packet

```text
stableCharacterVisualId
factionId
roleId
individualBriefId
masterPromptVersion
factionVisualProfileVersion
sourcePrompt
negativePrompt
transparentOutput
expressionOrStateVariants
styleChecklistResult
reviewStatus
```

## 17.4. Character review order

1. shared Master Prompt style;
2. anatomy/face/human imperfection;
3. faction overlay;
4. role/status readability;
5. individual identity;
6. palette/material restraint;
7. transparent-background/engine use;
8. cross-lineup consistency.

---

# 18. PR decomposition

PR должен иметь один проверяемый authority slice. Рекомендуемая очередь:

| PR | Scope | Blocked by | Required evidence |
|---|---|---|---|
| DOC-01 | bibles/framework/roadmap sync | — | link/doc review |
| ID-01 | identity inventory report + ADR | DOC-01 | repo-wide audit |
| ID-02 | migration table/codecs/fixtures | ID-01 | save round-trips |
| PROF-01 | profile schema + validator | ID-01 | invalid/valid fixtures |
| PROF-02 | core profiles bindings | PROF-01 | deterministic policy tests |
| CNT-01 | manifest/maturity/fingerprint seam | PROF-01 | catalog tests |
| CNT-02 | shared role/support contract | CNT-01 | legality fixtures |
| EMP-01 | Empire industrial seed | CNT-02 | production path tests |
| EMP-02 | Empire hull/fits | EMP-01 | engineering scenarios |
| EMP-03 | Empire AI/UI/telemetry | EMP-02 | bounded evidence traces |
| EMP-04 | Empire ship visual packet | EMP-02 | silhouette/fit gates |
| EMP-05 | Empire character lineup | DOC-01 | Master Prompt checklist |
| UNI-01 | Union industrial seed | CNT-02 | production/commonality tests |
| UNI-02 | Union hull/fits | UNI-01 | engineering scenarios |
| UNI-03 | Union AI/UI/telemetry | UNI-02 | bounded evidence traces |
| UNI-04 | Union ship visual packet | UNI-02 | silhouette/fit gates |
| UNI-05 | Union character lineup | DOC-01 | Master Prompt checklist |
| ECO-01 | shared civilian/minor content | ID-02/CNT-02 | convoy/route tests |
| BAL-01 | scenario harness/telemetry | PROF-02 | replay/fingerprint |
| BAL-02 | core pair tuning candidate | all core packages | 30-seed report |
| BAL-03 | core RC freeze | BAL-02 | 100+ seed RC evidence |
| POL-01 | Stage 23 polish/hardening | BAL-03 | full regression |

Post-core повторяет цикл `PFx-ARCH/VIS/PROF/CNT/FLEET/AI/BAL/RC`.

## 18.1. PR body checklist

- authority changed;
- explicit non-goals;
- schema/save impact;
- content/fingerprint impact;
- AI knowledge boundary;
- visual authority/version;
- tests exact commands;
- balance evidence scenarios/seeds;
- migration and rollback;
- docs updated;
- open risks;
- exact head SHA CI status before merge.

---

# 19. Risk register

| Риск | Ранний сигнал | Mitigation | Gate |
|---|---|---|---|
| bonus-based asymmetry | появляется общий faction scalar | require causal system mapping | architecture review |
| ID collision | name-based migration/fallback | explicit classes/table/fixtures | M22.0 |
| recolor factions | silhouette blind test падает | grayscale-first pipeline | visual gate |
| art-fit mismatch | изображённый module отсутствует в fit | fit fingerprint binding | content/visual gate |
| AI omniscience | decision использует unseen truth | bounded evidence trace | AI gate |
| core scope creep | post-core content входит в Stage 22 critical path | manifest boundary/feature flag | roadmap review |
| missing support economy | combatants operate without tankers/repair | role coverage scenarios | content gate |
| single-seed tuning | выводы меняются при seed | mirrored batches | balance gate |
| unreviewed candidate becomes canon | unmerged manifest numbers copied | maturity labels/source record | content review |
| save break | old ID loads as neutral/new actor | migration fixtures | integrity gate |
| style drift | character faction overlay overrides Master Prompt | layered prompt checklist | character gate |
| documentation drift | roadmap claims complete without merged evidence | status authority + exact SHA | release gate |
| death spiral | recovery never crosses T50 | recovery scenarios/outlier traces | balance gate |
| content explosion | every faction gets unique component universe | common seam + justified dependencies | catalog review |
| UI hides causality | only composite score visible | drill-down event trace | UX gate |

---

# 20. Documentation set и ownership

Минимальный живой комплект:

| Документ | Содержит | Обновляется когда |
|---|---|---|
| development roadmap | stage status | merged stage evidence |
| faction roster/horizon | canonical names/scope | roster decision |
| systemic identity | gameplay causality | identity contract changes |
| visual bible | ship/character overlay | visual language changes |
| Character Master Prompt | shared character style | global character style changes |
| balance framework | protocol/gates | validation method changes |
| implementation roadmap | ordering/dependencies | execution decision changes |
| content manifests | exact authored objects/status | content change |
| migration table | ID/save mapping | identity/schema change |
| balance reports | exact evidence | milestone/RC run |
| known limitations | accepted debt | risk accepted/resolved |

Документированная claim без owner/evidence имеет статус intent, а не completion.

---

# 21. Milestone exit checklist

Перед закрытием любого M22/PFx milestone:

- [ ] scope и non-goals совпадают с roadmap;
- [ ] common authority reused;
- [ ] stable IDs и versioning определены;
- [ ] migration/save impact проверен;
- [ ] physical/industrial chain complete;
- [ ] AI bounded by evidence;
- [ ] UI explains causal result;
- [ ] ship art matches fit and faction silhouette;
- [ ] characters pass Master Prompt composition;
- [ ] required scenario evidence attached;
- [ ] fingerprints/manifests updated;
- [ ] exact PR head CI green;
- [ ] unresolved reviews отсутствуют;
- [ ] docs/status updated after merge, not before;
- [ ] clean-main verification complete.

---

# 22. Итоговый roadmap

```text
NOW
  D0 docs foundation
  → finish Stage 21I without scope contamination

CORE CONTENT
  M22.0 identity/migration
  → M22.1 profile contract
  → M22.2 shared content seam
  → M22.3 Empire package
  + M22.4 Industrial Union package
  → M22.5 civilian/minor ecosystem
  → M22.6 paired balance and freeze
  → Stage 23 polish/RC

POST-CORE
  PF0 platform review
  → PF1 Frontier Confederation
  → PF2 Directorate
  → PF3 League of Free Systems
  → PF4 Consortium
  → PF5 Nomad Fleet
  → seven-faction macro validation and final roster freeze
```

Главный критерий успешности: каждая новая фракция расширяет пространство решений общей simulation,
не создавая отдельную игру внутри игры и не разрушая уже доказанный core balance.
