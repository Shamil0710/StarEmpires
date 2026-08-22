# Stage 23 — Polish / Release Candidate roadmap

> Статус: **PLANNED after Stage 22**.
> Назначение: превратить принятую Content & Balance Alpha в воспроизводимый, понятный,
> производительный и безопасно обновляемый release candidate без создания новой параллельной
> симуляции.

## 1. Граница стадии

Stage 23 не является стадией, в которой впервые появляются карта, нормальный UI, управление
камерой, корабли, живые фракции или контент. Эти функции обязаны быть уже функционально завершены
в Stage 21–22.

Stage 23 отвечает за:

- единый production UX поверх уже принятых authority;
- onboarding, accessibility, localization и input discoverability;
- окончательную замену оставшихся prototype visuals/VFX/audio;
- измеренную оптимизацию, стабильность долгих сессий и bounded resource usage;
- save migration, recovery, diagnostics и совместимость поддерживаемых версий;
- Windows packaging/launcher без требования собирать проект вручную;
- release validation, regression governance и воспроизводимую сборку RC.

Stage 23 не имеет права скрывать дефект симуляции визуальным костылём, менять экономику только для
прохождения tutorial или заменять физические последствия заранее записанным сценарием.

## 2. Входные условия

До начала основной работы Stage 23 должны быть приняты:

1. полный causal loop Stage 21, включая войну, территорию, мир, NPC, missions и reputation;
2. production content schemas и alpha breadth Stage 22;
3. принятый roster major/minor factions и их visual/language packages;
4. отсутствие provisional Stage-17.5/19 content без явного решения `PROMOTE / REAUTHOR / RETIRE`;
5. versioned content fingerprint и поддерживаемая save migration policy;
6. representative campaign corpus и long-run deterministic soak;
7. зафиксированные performance/content budgets, относительно которых можно проводить RC hardening.

Новая фундаментальная механика после этого gate требует отдельного architecture decision и обычно
переносится за пределы RC.

## 3. RC invariants

1. Presentation читает authoritative state и отправляет только валидируемые команды.
2. UI scale не связан с world zoom; изменение разрешения не меняет симуляцию.
3. Все поддерживаемые действия доступны мышью и переназначаемой клавиатурой.
4. Цвет не является единственным носителем friend/neutral/hostile, warning или damage state.
5. Сохранение атомарно; повреждённый файл не уничтожает последний рабочий checkpoint.
6. Обновление контента либо мигрирует поддерживаемый save, либо объяснимо отклоняет его до mutation.
7. Release build не требует Maven/JDK и не пишет данные в каталог установки без необходимости.
8. Debug/telemetry/crash data не содержат пользовательских путей или содержимого save без явного
   согласия.
9. Performance исправляется по profiler evidence; нельзя менять физические законы для offscreen AI.
10. RC считается готовым только после чистой установки и продолжительной обычной кампании, а не
    только после unit tests.

## 4. Delivery slices

### 23A — Scope lock, issue taxonomy and release governance

Цель: заморозить продуктовую поверхность RC и отделить blocker от желательного улучшения.

Deliverables:

- RC feature manifest с перечислением player-visible loops;
- матрица `must ship / may ship / post-RC`;
- severity model: blocker, critical, major, minor, cosmetic;
- change-control для mechanics/content/save schema после freeze;
- владелец и доказательство закрытия для каждого release blocker;
- список допустимых known issues с player impact и workaround;
- versioning scheme для приложения, content pack, save schema и generator profile;
- reproducible release notes template.

Exit criteria:

- у каждого обязательного loop есть acceptance owner и executable/manual evidence;
- ни один provisional content ID не попал в RC без явного решения;
- новые feature requests не смешиваются с blocker fixes.

### 23B — Information architecture and production UI consolidation

Цель: сделать весь принятый мир управляемым без чтения debug state.

Обязательные surfaces:

- текущая система и tactical/local view;
- galaxy map с topology, route, intelligence и overlays;
- фракции, diplomacy, crises, treaties, wars и territorial transitions;
- military forces, command groups, orders, readiness и operations;
- logistics, markets, industry, construction, repair и replacement;
- NPC, contacts, missions, discoveries и reputation;
- player ship/fleet fitting, cargo, damage, ammunition, thermal/power/propellant state;
- event timeline, notification archive и searchable history;
- settings, controls, saves, accessibility и diagnostics.

UX requirements:

- единая navigation model, breadcrumbs/back behavior и preserved selection;
- поиск, сортировка, фильтры, virtualization и empty/loading/error states;
- consistent object inspector для любого выбираемого объекта;
- context actions показывают validation result до отправки команды;
- double-click focus, wheel zoom, middle-button pan, return-to-active-object и camera presets;
- безопасные confirmation flows только для действительно необратимых действий;
- tooltip/glossary для единиц, derived metrics и причин отказа;
- information density presets без удаления authoritative information.

Exit criteria:

- все core loops выполняются из production UI;
- UI не требует raw IDs как основной идентификатор;
- любой displayed strategic number имеет provenance/explanation;
- keyboard-only smoke path покрывает главное меню, карты, списки и dialog actions.

### 23C — Resolution, accessibility, controls and localization

Цель: обеспечить читаемость и управление на поддерживаемом desktop envelope.

Resolution/aspect matrix минимум:

- 1280×720;
- 1366×768;
- 1920×1080;
- 2560×1440;
- 3440×1440;
- 3840×2160;
- windowed resize и display-density changes.

Accessibility scope:

- independent UI scale и font scale;
- minimum hit targets и focus visibility;
- color-blind-safe palette/shape redundancy;
- subtitle/caption channel для смысловых audio cues;
- reduced flash, reduced shake и VFX intensity controls;
- pause-safe reading and configurable notification duration;
- key rebinding, conflict detection, mouse sensitivity и zoom direction;
- high-contrast selection/target modes;
- screen-edge and offscreen markers configurable by importance.

Localization scope:

- Russian-first production text и complete English localization path;
- все player-facing strings вне Java/layout code;
- plural forms, units, dates/deadlines и numeric formatting;
- layout expansion tests и forbidden truncation in critical controls;
- glossary consistency для hull/fleet/territory/economy terms;
- pseudo-localization CI fixture.

Exit criteria:

- critical text не обрезается на всей resolution matrix;
- игра полностью управляется с переназначенными controls;
- color-only state tests отсутствуют;
- missing localization keys fail validation before packaging.

### 23D — Onboarding, tutorial and first-session pacing

Цель: научить реальной игре, не создавая tutorial-only законов.

Onboarding sequence:

1. камера, выбор и inspector;
2. движение, docking и route explanation;
3. cargo, market transaction и physical delivery;
4. mining/extraction and finite resources;
5. fitting, readiness, ammunition, propellant и repair;
6. galaxy map, discovery и information latency;
7. NPC contact, mission и reputation consequence;
8. own fleet, order and double-click focus;
9. faction/diplomacy/territory overview;
10. construction/industry and long-term autonomy.

Rules:

- tutorial objectives наблюдают ordinary authoritative state;
- награды escrowed/owned и проходят обычный transfer;
- skip/restart не повреждают campaign;
- explanation separates known fact, estimate and unknown information;
- first-session seed/profile проходит тот же generator и persistence contract.

Exit criteria:

- новый игрок завершает first economic loop без внешней документации;
- tutorial save/load continues exact objective state;
- пропуск tutorial не создаёт economic disadvantage/hidden grants.

### 23E — Final art, VFX, animation and audio replacement

Цель: заменить остаточные prototype assets при неизменной simulation authority.

Art closure:

- production sprite packages для всех release hull/station/location roles;
- base, damage, emissive и engine-state layers там, где они нужны;
- physically aligned hardpoints, thrusters, radiators, bays and module anchors;
- readable faction silhouette at actual gameplay size, not only concept scale;
- clean alpha, pivot, orientation, padding, atlas and mip/scale validation;
- portraits/character illustrations consistent with faction/role identity;
- icons and status glyphs with monochrome/readability variants.

VFX closure:

- weapon-family-specific muzzle/projectile/beam/impact language;
- shield interaction, armor hit, penetration, subsystem failure and destruction cues;
- engine state tied to actual thrust, not decorative constant exhaust;
- bounded particles/lights and declutter priority under saturation;
- reduced-intensity accessibility variants.

Audio closure:

- UI confirmation/warning/error hierarchy;
- engine, weapon, impact, alarm and docking state families;
- system/station ambience and restrained faction stingers;
- dynamic mixing so critical warnings remain audible;
- no sound implying an event that authoritative state did not produce;
- licensing/provenance manifest for every shipped asset.

Exit criteria:

- no release-facing object uses unreviewed schematic fallback;
- visual and audio cues agree with state in deterministic capture scenarios;
- stress battles stay readable and within render/audio voice budgets.

### 23F — Performance, memory and long-session hardening

Цель: закрыть measured budgets на release hardware envelope.

Benchmarks:

- cold startup and warm startup;
- new-world generation for supported size profiles;
- load of small/medium/large campaign;
- active-system dense civilian traffic;
- saturated Stage-19 battle;
- galaxy map with all overlays;
- long fleet/logistics/faction/NPC lists;
- 1×/2×/4×/8× time acceleration;
- 8 h interactive soak and extended headless soak;
- repeated save/load/materialize/dematerialize cycles.

Metrics:

- simulation step percentiles and missed budget count;
- render frame percentiles separated from simulation;
- heap, native memory, texture memory and allocation rate;
- GC pauses;
- save size/time and load time;
- event queue, entity, mission, log and history growth;
- route/decision cache hit/invalidation rates;
- number of active detailed domains and dormant actors.

Exit criteria:

- budgets versioned with hardware/JVM/build provenance;
- no monotonic memory/entity/event growth in accepted soak;
- optimization does not change deterministic outcome/state hash;
- graceful density degradation affects presentation detail before gameplay authority.

### 23G — Save, migration, recovery and diagnostics

Цель: сделать продолжение кампании безопасным для пользователя.

Deliverables:

- atomic temp-write + fsync/replace policy appropriate to platform;
- rotating autosaves and manual save slots;
- metadata preview: version, seed, time, faction, location, content fingerprint;
- checksum/corruption detection before restore mutation;
- supported-version migration chain with fixture per historical schema;
- explicit unsupported-content and newer-version messages;
- backup-before-migration and non-destructive failure behavior;
- missing asset/content diagnostics using stable IDs;
- deterministic continuation verification after each migration;
- human-readable diagnostics bundle export without save contents by default.

Exit criteria:

- power-loss/corruption fixtures preserve at least one usable checkpoint;
- failed migration leaves original unchanged;
- supported saves pass identity, money/resource and deadline continuity checks;
- error UI tells the player what can be recovered.

### 23H — Packaging, launchers and clean-machine validation

Цель: выпускать приложение, а не исходный Maven project.

Windows package:

- versioned application directory/archive;
- executable fat JAR or native launcher plus supported Java runtime strategy;
- `run-generated-world.bat` preserved as developer/source launcher;
- release launcher starts built artifacts without `mvn clean package`;
- paths with spaces and non-ASCII user names supported;
- writable saves/logs/settings stored in a user-data location;
- DPI awareness and desktop icon/version metadata;
- offline first start after download/install;
- clear exit codes and log path on failure.

Cross-platform readiness, если заявлена для RC:

- Linux/macOS launch scripts/package policy;
- filesystem case-sensitivity checks;
- native dependency inventory;
- consistent save-data locations.

Exit criteria:

- clean VM/machine launch without JDK/Maven/git/network;
- unpack/install, run, save, exit, relaunch, load and uninstall smoke passes;
- package manifest/checksum and license notices generated reproducibly.

### 23I — QA, playtest and regression closure

Цель: доказать полную кампанию, а не набор изолированных screens.

Required suites:

- automated unit/integration/acceptance CI;
- deterministic representative-seed corpus;
- save compatibility and corrupted-save corpus;
- input/resolution/localization visual matrix;
- campaign journeys: trader, miner, mercenary, fleet commander, industrialist,
  faction founder and territorial power;
- peaceful, allied, crisis, limited-war, occupation and recovery histories;
- content-reference validation and missing-asset scan;
- manual exploratory charters for UI, economy exploits, AI exploits and battle readability;
- clean-package smoke on supported OS/hardware classes.

Playtest evidence:

- time-to-understand core UI;
- time-to-first income and first meaningful choice;
- mission clarity/failure comprehension;
- perceived cause of faction actions and wars;
- dominant-build/strategy reports;
- long-session friction and save confidence;
- blocker reproduction files and exact build fingerprints.

Exit criteria:

- zero open blocker/critical issues;
- major issues have explicit accepted disposition;
- no save-destroying or economy-duplication defect in RC corpus;
- playtest findings map to reproducible evidence rather than anecdotal number changes.

### 23J — Release Candidate final gate

RC candidate is accepted only when one exact build SHA/package fingerprint passes:

1. full Java-17 verify and documentation gates;
2. deterministic campaign corpus and state-hash checks;
3. long-run economy/living-world/combat soak;
4. resolution/accessibility/localization matrix;
5. clean-machine package install/run/save/load smoke;
6. supported migration and corrupted-save recovery tests;
7. final content/license/provenance scan;
8. exact diff, dependency and known-issue review;
9. reproducible package/checksum creation;
10. post-package launch of the exact artifact intended for distribution.

## 5. Release content freeze policy

After 23A:

- text typo/icon replacement with unchanged ID is low risk;
- numeric balance change requires affected benchmark rerun;
- content ID removal/rename requires migration/alias decision;
- hull/module geometry change requires physical, fitting, VFX and art revalidation;
- new mission template requires authority/deadline/reward/failure coverage;
- new faction content requires full package validation, not only portrait/sprite addition;
- schema changes require explicit version increment and supported-save evidence.

## 6. Definition of Stage-23 completion

Stage 23 is complete when a player can obtain the release package on a clean supported machine,
start a generated campaign, understand and operate every core loop, observe a causally living world,
save and resume safely, and continue through long economic/diplomatic/military play without
unbounded performance degradation, hidden grants, broken content references or presentation-owned
state.

The final RC proof is the exact package, its checksums, test/soak evidence, migration matrix,
known-issues record and source SHA. A green source build alone is insufficient.
