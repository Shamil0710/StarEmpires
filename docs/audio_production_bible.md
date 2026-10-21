# Star Empires — Audio Production Bible / Sonic Identity Contract

> **Статус:** **CANONICAL PLANNED AUDIO PRODUCTION CONTRACT**  
> **Назначение:** единый контракт для будущего проектирования, производства, интеграции и приёмки звука Star Empires.  
> **Основная стадия реализации:** **Stage 23E — Final art, VFX, animation and audio replacement**.  
> **Допустимая ранняя работа:** только узкие foundation/integration slices, которые не расширяют scope активной стадии и не создают параллельную simulation authority.  
> **Core production faction scope through Stage 23:** **Империя + Индустриальный Союз**.  
> Этот документ расширяет, но не заменяет `docs/content_production_plan_stage21_23.md` и `docs/stage23_release_candidate_roadmap.md`.

---

## 1. Источники истины и границы

При конфликте приоритет имеют:

1. фактическое authoritative runtime state и event semantics;
2. `docs/development_roadmap.md`;
3. `docs/stage23_release_candidate_roadmap.md`;
4. `docs/content_production_plan_stage21_23.md`;
5. faction systemic/visual bibles;
6. этот audio contract;
7. конкретный asset brief.

Фракционные источники:

- `docs/factions/empire_systemic_identity.md`;
- `docs/factions/empire_visual_bible.md`;
- `docs/factions/industrial_union_systemic_identity.md`;
- `docs/factions/industrial_union_visual_bible.md`;
- `docs/factions/faction_gameplay_visual_balance_bible.md`;
- `docs/factions/post_core_faction_horizon.md`.

### 1.1. Что этот документ определяет

- общий sonic language проекта;
- семантику звука в hard-sci-fi мире;
- обязательные event families;
- фракционный sonic identity;
- правила микса, приоритетов, saturation и zoom/LOD;
- требования к accessibility/captions;
- asset naming/manifest/provenance;
- production pipeline и порядок реализации;
- automated/manual acceptance criteria.

### 1.2. Что этот документ не определяет

- новую combat/economy/logistics authority;
- новые события только ради звука;
- faction-only simulation bonuses;
- финальные численные loudness/EQ/compression presets до measured in-engine mix pass;
- обязательную актёрскую озвучку;
- обязательный большой музыкальный soundtrack;
- production packages пяти post-core фракций до их собственной стадии.

---

## 2. Главная звуковая идея Star Empires

Star Empires должен звучать как **тяжёлая, функциональная, физически правдоподобная межзвёздная инфраструктура**, а не как аркадная космоопера.

Ключевая формула:

```text
authoritative physical event
→ player-observable evidence
→ context-aware sonic representation
→ faction / size / state coloration
→ mix priority / distance / zoom filtering
→ readable consequence
```

Звук существует прежде всего для трёх задач:

1. **информировать** игрока о реальном состоянии и событии;
2. **создавать материальность** кораблей, станций, оружия и промышленности;
3. **подчёркивать идентичность фракций**, не нарушая общую семантику интерфейса.

Ни один эффект не должен утверждать, что произошло событие, которого не было в authoritative state.

---

## 3. Hard-sci-fi правило: звук в вакууме

### 3.1. Собственный корабль / физический контакт

Для собственного корабля допустим и желателен богатый звук через:

- structure-borne vibration;
- корпус и переборки;
- механические приводы;
- реактор/силовые контуры;
- насосы/вентиляторы/охлаждение;
- импульсы оружейных установок, передающиеся через конструкцию;
- удары и повреждения корпуса;
- атмосферу внутренних отсеков.

Это **diegetic physical sound**.

### 3.2. Внешний космический бой

Внешний бой не моделируется как атмосферное распространение звука.

Внешние эффекты представляют собой:

- sensor sonification;
- tactical interface representation;
- interpretation of observed radiation/impact/engine/weapon events;
- player-facing readability layer.

То есть слышимый внешний `impact` или `missile launch` означает:

> «система наблюдения зарегистрировала событие и представила его игроку звуковым образом»,

а не «звук физически прошёл через вакуум».

### 3.3. Станции, ангары и атмосфера

В герметичных пространствах могут использоваться обычные воздушные механические звуки:

- вентиляция;
- краны;
- шлюзы;
- погрузка;
- техника;
- шаги/голоса, если позднее появятся соответствующие surfaces;
- ремонтные работы.

### 3.4. Запрет

Не использовать как общий стиль:

- бесконечные голливудские взрывы в вакууме;
- гигантский cinematic bass на каждом событии;
- одинаковый `boom` для missile, kinetic hit и reactor failure;
- звук события, которого игрок не мог наблюдать по текущему information scope.

---

## 4. Неподвижные audio invariants

1. **State before sound.** Sound реагирует на authoritative event/state и никогда не создаёт его.
2. **No hidden-information leak.** Звук не раскрывает неизвестный корабль, скрытую цель, невидимый missile, secret diplomacy state или off-screen event вне законного knowledge scope.
3. **Presentation is non-authoritative.** Отсутствие/задержка/voice stealing звука не меняет simulation outcome.
4. **Critical semantics survive saturation.** Аварийные предупреждения и player-action feedback имеют приоритет над ambience и массовым battle spam.
5. **Faction identity never changes meaning.** `warning`, `error`, `confirm`, `missile warning`, `hull breach` могут иметь фракционную окраску, но должны оставаться мгновенно узнаваемыми как один semantic event.
6. **No color-equivalent audio trap.** Информационно важные события должны иметь visual/text/caption redundancy; звук не является единственным носителем критического состояния.
7. **Size matters.** Корвет, крейсер и capital ship не должны звучать как один sample с простой громкостью.
8. **State matters.** Idle/thrust/damaged/overheated/depleted states должны различаться при наличии соответствующего runtime state.
9. **Variation without semantic drift.** Randomized variants меняют текстуру, но не смысл.
10. **Deterministic capture support.** Для acceptance capture желательно выбирать variation/pitch seed из стабильного event/entity context, чтобы одинаковый deterministic scenario был сравним между сборками.
11. **No infinite voices.** Большие бои используют priority, aggregation и voice budgets.
12. **Provenance required.** Каждый shipped asset имеет источник, лицензию/право использования и manifest metadata.

---

## 5. Audio hierarchy / buses

Минимальная логическая bus architecture:

```text
MASTER
├─ UI
│  ├─ UI_ACTION
│  └─ UI_NOTIFICATION
├─ ALERTS
│  ├─ CRITICAL
│  └─ TACTICAL_WARNING
├─ SHIP
│  ├─ PROPULSION
│  ├─ MACHINERY
│  ├─ DAMAGE
│  └─ INTERIOR
├─ COMBAT
│  ├─ WEAPONS
│  ├─ IMPACTS
│  ├─ GUIDED_ORDNANCE
│  ├─ DEFENSE
│  └─ EW_SENSOR
├─ INDUSTRY
│  ├─ DOCKING_CARGO
│  ├─ MINING_SALVAGE
│  └─ REPAIR_CONSTRUCTION
├─ AMBIENCE
│  ├─ STATION
│  ├─ SYSTEM
│  └─ LOCATION
├─ COMMS
├─ MUSIC
└─ CINEMATIC_STINGER
```

Необязательно повторять эту иерархию 1:1 в коде, но настройки игрока и runtime mix должны позволять минимум:

- Master;
- UI;
- Effects;
- Alerts;
- Ambience;
- Music;
- Voice/Comms, если появляется speech.

### 5.1. Priority order

При saturation приоритет примерно такой:

```text
critical player warning
> direct player action confirmation/error
> own-ship critical damage
> incoming threat warning
> selected/observed target combat event
> nearby combat
> ordinary machinery
> distant combat
> ambience
> cosmetic stinger tail
```

Это semantic priority, а не фиксированная громкость.

---

## 6. Event naming and stable semantic IDs

Audio не должно быть привязано к Java class name или filename.

Рекомендуемый semantic ID:

```text
audio.<domain>.<event>[.<state>][.<size>][.<faction-family>]
```

Примеры:

```text
audio.ui.confirm
audio.ui.validation_error
audio.alert.missile_incoming
audio.ship.engine.thrust.medium.empire
audio.weapon.kinetic.fire.heavy
audio.weapon.guided.launch.medium
audio.impact.armor.penetration.heavy
audio.damage.subsystem.failure
audio.docking.clamp_lock
audio.industry.mining.active
audio.faction.empire.stinger.war_declared
audio.faction.union.stinger.production_crisis
```

Фракционный suffix нужен только там, где реально существует отдельный sound family. Общие physics semantics не обязаны дублироваться по фракциям.

---

## 7. Zoom / distance / information LOD

Звуковая модель должна учитывать не только distance, но и **player focus**.

### 7.1. Local close view

При близком tactical zoom:

- выбранный корабль получает максимальную детализацию;
- слышны propulsion layers, weapon mechanics, nearby impacts;
- отдельные missiles/interceptors могут иметь собственные cues;
- machinery/damage layer зависит от состояния выбранного корабля.

### 7.2. Tactical mid view

- индивидуальные второстепенные shots агрегируются;
- остаются signature weapons, impacts, missile warnings и destruction;
- distant events получают фильтрацию и меньший priority;
- собственный/выбранный объект остаётся читаемым.

### 7.3. Strategic/system view

- массовый weapon spam подавляется;
- остаются крупные события: battle escalation, loss, incoming threat to selected asset, jump, docking, mission/operation outcome;
- фон строится вокруг станции/системы/логистики, а не каждой пули.

### 7.4. Galaxy view

- никакого непрерывного шума локальных боёв;
- только player-relevant notifications, selected-event preview, diplomacy/war/mission cues и очень сдержанный ambience/music layer.

### 7.5. Camera zoom must not change authority

Audio LOD может менять количество voices, layering и filtering, но не может менять:

- projectile existence;
- impact timing;
- damage;
- detection;
- AI knowledge;
- order execution.

---

## 8. Core audio inventory — обязательные семейства

Количество clips ниже — **production planning floor**, а не самоцель. Если один параметрический layered asset качественно покрывает несколько states, он предпочтительнее десятка почти одинаковых файлов.

### 8.1. Shared UI / interaction

| Event | Minimum variants | Notes |
|---|---:|---|
| hover/focus soft | 2 | очень тихий, может отключаться |
| select object | 3 | neutral/common semantic |
| multi-select / group select | 2 | distinct from single select |
| confirm action | 3 | короткий |
| cancel/back | 2 | без негативного alarm feel |
| open panel | 2 | restrained |
| close panel | 2 | restrained |
| tab change | 2 | short |
| valid command queued | 3 | не равно command completed |
| invalid command | 4 | ясно, но не аварийно |
| validation reason / blocked | 3 | distinct from generic click |
| notification low | 3 | background-safe |
| notification medium | 3 | actionable |
| notification high | 4 | urgent |
| critical emergency | 4 | reserved, rare |
| mission offered | 2 | neutral/faction overlay optional |
| mission accepted | 2 | common semantic |
| mission completed | 3 | short, no casino reward feel |
| mission failed/expired | 3 | informative, not punitive |
| save complete | 2 | subtle |
| save/load error | 3 | clear |
| pause/resume/time-rate | 2 each | low priority |

Planning estimate: ~55–70 discrete UI one-shots before faction overlays.

### 8.2. Navigation / movement / ship handling

Required events:

- maneuver/RCS pulse light/medium/heavy;
- main drive ignition;
- drive spool-up;
- idle/low-thrust loop;
- cruise/thrust loop;
- high-load thrust layer;
- throttle-down/drive shutdown;
- low propellant warning;
- propulsion power-limited state;
- propulsion thermal-limited state;
- jump/FTL preparation;
- jump commit/departure;
- jump arrival;
- jump abort/failure;
- docking approach confirmation;
- docking clamp capture;
- hard dock/berth lock;
- undock release.

Size families minimum:

```text
SMALL   — patrol/corvette/small civilian
MEDIUM  — frigate/destroyer/cruiser/freighter
LARGE   — capital/carrier/bulk/logistics/station-scale machinery
```

Faction-specific propulsion texture may exist, but physical drive family and actual thrust state remain authoritative.

### 8.3. Kinetic weapons

Required semantic layers:

- light kinetic fire;
- medium kinetic fire;
- heavy/spinal kinetic fire;
- mechanical charge/coil preload where state exposes it;
- burst/autocannon/PD kinetic fire;
- projectile near/track cue only where player legitimately observes it;
- armor impact light/medium/heavy;
- ricochet/deflection if authoritative event exists;
- penetration;
- internal structural shock;
- projectile expiration/miss **only if useful and observed**, otherwise silence.

Minimum variation target: 3–5 one-shots per repetitive fire/impact family.

### 8.4. Beam weapons

Required:

- emitter charge/start;
- beam sustain loop;
- stop/cutoff;
- target coupling/contact;
- shield coupling;
- armor/material heating response;
- thermal/power cutoff warning if relevant to own ship.

Avoid generic laser `pew` language. Beam sound should read as energy-system activity / tactical sonification, not air-propagated ray noise.

### 8.5. Guided weapons / missiles / torpedoes

Required:

- launcher door/cassette preparation where visible/meaningful;
- light missile launch;
- heavy missile/torpedo launch;
- motor ignition/boost;
- cruise/terminal cue where observed;
- seeker/terminal lock cue only for lawful observer;
- incoming missile warning;
- decoy deployment;
- interceptor launch;
- successful intercept;
- failed/late intercept warning only if information exists;
- warhead/shaped/kinetic terminal impact families;
- magazine/launcher fault if authoritative state exposes it.

### 8.6. Point defense / defensive systems

Required:

- PD kinetic burst;
- PD beam start/sustain/stop where applicable;
- interceptor rack launch;
- decoy/chaff-equivalent deployment if content exists;
- ECM activation/change-state cue for own/selected asset;
- shield hit;
- shield heavy hit;
- shield low-state warning;
- shield overload/failure;
- shield restart/recovery.

### 8.7. Armor / compartments / subsystem damage

Required:

- glancing/light structural hit;
- medium hull shock;
- heavy hull shock;
- penetration;
- compartment breach;
- decompression/venting when observable;
- electrical fault;
- power-bus failure;
- reactor/engineering distress;
- sensor failure;
- propulsion failure;
- weapon subsystem failure;
- fire/damage-control loop for own/selected ship;
- critical hull integrity warning;
- breakup/destruction small;
- breakup/destruction medium;
- breakup/destruction large/capital;
- secondary internal detonation variants where authoritative destruction events exist.

Damage audio must be layered from event severity and physical context rather than using one universal explosion.

### 8.8. Sensors / EW / communications awareness

Required semantic cues:

- track acquired;
- track quality improved/degraded, only if actionably useful;
- track lost;
- unidentified contact;
- hostile classification;
- sensor overload/fault;
- jamming detected;
- datalink degraded/lost;
- comms unavailable/latency state if surfaced;
- distress signal received;
- message/dispatch received.

Most of these should be short UI/tactical cues, not loud sci-fi sweeps.

### 8.9. Docking / cargo / logistics

Required:

- docking permission/approach cue;
- clamp capture;
- pressure/umbilical/service connect where appropriate;
- cargo transfer start;
- container lock/unlock;
- cargo transfer loop;
- transfer complete;
- fuel/propellant transfer loop;
- ammunition handling cue;
- freight order created/assigned/completed notification;
- convoy ready/departed/arrived notification;
- route blocked/invalid warning.

Logistics is a core gameplay pillar and must not sound like silent spreadsheet activity.

### 8.10. Mining / salvage / repair / construction

Required:

- mining tool startup;
- mining active loop;
- extraction material contact variants;
- mining stop/depleted occurrence cue;
- salvage grapple/capture;
- salvage cutting/processing loop;
- repair tool/robotic work loop;
- module replacement/lock cue;
- construction work loop;
- major assembly step;
- construction complete;
- shipyard launch/commissioning cue;
- repair/refit complete.

### 8.11. Station / system ambience

Minimum ambience families:

- trade/administration hub;
- extraction/ore handling;
- refinery/material processing;
- energy/fuel/propellant complex;
- habitation/agriculture;
- component/precision manufacturing;
- arsenal/ammunition depot;
- repair/refit yard;
- shipyard/heavy integration yard;
- research/sensor/communications facility;
- military base/forward logistics node;
- derelict/abandoned location;
- damaged/low-power station state.

Ambience must be loop-safe and layered enough to change with activity/state without requiring a unique track per station.

### 8.12. Diplomacy / missions / strategic state

Required cues:

- proposal received;
- treaty accepted/rejected;
- access changed;
- embargo/sanction equivalent if surfaced;
- crisis escalation;
- war declared;
- ceasefire;
- peace agreement;
- territorial control transition;
- occupation/stabilization milestone;
- major fleet loss;
- strategic shortage;
- shipyard/replacement program complete;
- faction objective/operation outcome;
- important NPC contact/message;
- reputation threshold change.

These cues are ideal candidates for restrained faction overlays/stingers.

---

## 9. Production size: planning estimate

Для двух core factions и общего civilian layer разумный RC-order-of-magnitude:

| Family | Approx. discrete clips/loops | Comment |
|---|---:|---|
| shared UI + notifications | 55–70 | without voice acting |
| navigation/ship operation | 35–55 | layered by size/state |
| combat weapons/defense | 80–120 | reusable physical families |
| impacts/damage/destruction | 45–70 | severity + size variants |
| logistics/industry | 35–55 | core gameplay, not cosmetic |
| station/location ambience | 20–35 | layered loops |
| sensors/comms/strategic | 30–45 | knowledge-safe |
| faction stingers/overlays | 20–35 | Empire + Union |
| **Total planning range** | **320–485** | excludes dialogue and optional music |

Это **не KPI по количеству**. Один хороший multi-layer loop может заменить несколько файлов. Нельзя создавать filler ради достижения числа.

---

# 10. Sonic identity — Империя

Systemic source: institutional continuity, state procurement, reserves, heavy serviceable engineering, redundancy, disciplined logistics, controlled mobilization.

Visual source: heavy axial engineering, protected citadel, long service life, maintained wear, restrained hierarchy and naval tradition.

## 10.1. Основная формула

```text
mass
+ restrained mechanical authority
+ deep structural resonance
+ precise service signals
+ legacy continuity
+ disciplined alarm cadence
+ rare ceremonial tonal accent
```

Империя должна звучать как **дорогая тяжёлая машина, которую проектировали жить десятилетиями и ремонтировать после повреждений**.

## 10.2. Тембровый язык

Предпочтительно:

- низкий/нижне-средний mechanical body;
- плотные электромеханические relays;
- тяжёлые сервоприводы и затворы;
- короткая металлическая structural resonance;
- приглушённые трансформаторные/силовые tonal layers;
- ровный, дисциплинированный machinery hum;
- редкие чистые bell/chime-like command accents без буквального исторического колокола;
- небольшая разница между старым механическим layer и более новым electronic layer.

Не использовать:

- буквальную имперскую/царскую музыку;
- марши как постоянный UI soundtrack;
- церковные колокола как клише;
- steampunk hiss/gear spam;
- чрезмерно винтажную электронику;
- золотой «дворцовый» sonic glamour.

## 10.3. UI Империи

- короткие, уверенные подтверждения;
- чуть более длинный decay, чем у Союза;
- fewer but weightier tones;
- warning cadence упорядоченная, не истеричная;
- command-level stinger может использовать 2–3 ноты с устойчивым низким фундаментом;
- brass-like timbre допустим только очень абстрактно и редко.

## 10.4. Корабли Империи

Small:

- compact but dense;
- dry mechanical transient;
- low structural tail.

Medium:

- заметная «масса корпуса»;
- servos/locks/engineering layer;
- weapon impulse передаётся как удар через цитадель.

Large/capital:

- очень низкий continuous machinery bed;
- медленные тяжёлые valve/actuator transients;
- multiple redundant machinery rhythms;
- повреждения дают длиннее structural decay, но без cinematic sub-bass abuse.

## 10.5. Оружие Империи

Kinetic/coil/rail:

- тяжёлый pre-charge/lock layer;
- короткий мощный structural impulse;
- меньше «аркадного выстрела», больше ощущения массы установки.

VLS/guided:

- защищённый launcher mechanism;
- чёткий door/cassette transition;
- launch transient с массивной механической основой.

PD:

- disciplined, controlled cadence;
- не должен превращаться в хаотичный machine-gun soundtrack.

## 10.6. Повреждения Империи

- layered compartment shock;
- relay trip / backup system engagement;
- secondary machinery taking load;
- аварийные cues должны создавать ощущение системы, которая пытается локализовать повреждение.

То есть звук подчеркивает **redundancy and damage control**, не давая скрытого бонуса.

## 10.7. Промышленность и станции Империи

- heavy shipyard cranes/locks;
- slow large service mechanisms;
- deep power distribution;
- controlled deck/yard announcement tones;
- repair/refit ambience важнее «заводского грохота».

Имперская верфь должна звучать как инфраструктура, обслуживающая дорогие долгоживущие корпуса.

## 10.8. Фракционные stingers Империи

Минимум:

- contact/official message;
- treaty/recognition success;
- crisis escalation;
- mobilization/war;
- major loss;
- recovery/peace.

Stingers должны быть 1–4 секунды, не маленькими музыкальными композициями.

---

# 11. Sonic identity — Индустриальный Союз

Systemic source: standardization, repeated production series, high throughput, compact component vocabulary, efficient replacement, bulk logistics, resource hunger and bottleneck sensitivity.

Visual source: modular family resemblance, repeated sections, common interfaces, industrial handling, serial production and functional marking.

## 11.1. Основная формула

```text
repeatable machine rhythm
+ crisp industrial transients
+ standardized actuator families
+ high-throughput logistics
+ clear functional signaling
+ modular repetition
```

Союз должен звучать как **система, за каждой машиной которой стоит серия, линия, склад и повторяемый технологический процесс**.

## 11.2. Тембровый язык

Предпочтительно:

- более короткие и сухие mechanical transients;
- повторяющиеся actuator signatures;
- modular click/lock/power-up families;
- ясные mid-frequency industrial textures;
- rhythmic conveyor/handling patterns в ambience;
- standardized alarm generators;
- более заметная «серийность» одинаковых mechanisms на разных классах кораблей.

Не использовать:

- буквальный «советский» музыкальный язык;
- красноармейские/революционные клише;
- бесконечные шестерёнки/паровозные sounds;
- scrapyard/rust aesthetic;
- грязный постапокалиптический industrial noise;
- просто более громкую/быструю версию Империи.

## 11.3. UI Союза

- shorter decay;
- clearer functional step tones;
- repeatable two-part confirm/complete patterns;
- warnings скорее utilitarian repeaters, чем ceremonial cues;
- batch/queue/production notifications могут иметь общий identifiable pulse family.

## 11.4. Корабли Союза

Главный принцип: common components должны **узнаваться на слух**.

Если два класса используют близкую propulsion/actuator family, sound design должен сохранять родство:

- общий startup motif;
- близкий mechanical texture;
- разница прежде всего в масштабе/load, а не полностью другой звук.

Large ships:

- не «аристократически величественные»;
- ощущение нескольких одинаковых агрегатов, работающих параллельно;
- pulse/rhythm layers могут быть чуть более выражены.

## 11.5. Оружие Союза

- standardized launcher/weapon families;
- серия одинаковых kinetic mounts должна иметь узнаваемый общий transient;
- missile/PD systems могут звучать более modular/cassette-like;
- repeated fire should communicate manufacturing/commonality, not arcade rate-of-fire bonus.

## 11.6. Повреждения Союза

- failed module isolate;
- line/module shutdown;
- repeated standard alarm code;
- replacement-ready/serviceable machinery cues.

При bottleneck/industrial distress стратегические cues могут подчёркивать stalled flow/queue interruption, но не превращаются в отдельную simulation mechanic.

## 11.7. Логистика и промышленность Союза

Это signature sound domain фракции.

Нужны особенно качественные families для:

- bulk cargo handling;
- container lock/unlock;
- yard assembly;
- refinery/processing;
- tanker transfer;
- tug/docking operations;
- series production completion;
- ship launch from repeated production line.

Игрок должен по звуку чувствовать, что **логистика — часть силы Союза, а не фон**.

## 11.8. Фракционные stingers Союза

Минимум:

- industrial/government message;
- production/logistics success;
- route/bottleneck warning;
- crisis/war;
- major attrition/replacement shock;
- recovery/reopened flow/peace.

1–4 секунды, функционально и сдержанно.

---

# 12. Empire vs Industrial Union — direct comparison

| Axis | Империя | Индустриальный Союз |
|---|---|---|
| perceived object | долгоживущий дорогой актив | единица стандартной серии |
| transient | heavier, denser, longer tail | drier, shorter, repeatable |
| machinery | redundant layered systems | repeated common modules |
| UI | restrained authority | utilitarian workflow |
| alarm feel | disciplined naval/institutional | standardized industrial code |
| logistics | organized support of valuable assets | high-throughput identity pillar |
| weapon feel | mass + protected mechanism | family commonality + repeatability |
| damage feel | localize + backup engage | isolate module + restore series flow |
| stinger | rare command weight | process/state clarity |
| forbidden cliché | royal/steampunk pomp | Soviet/factory caricature |

Cross-faction test:

> При одинаковом semantic event игрок должен понять смысл без знания фракции, но после 20–30 минут игры начать узнавать, **кто** именно издал этот звук.

---

# 13. Shared civilian / minor / independent audio

Общий civilian layer не должен автоматически звучать как Империя или Союз.

Базовый язык:

- neutral practical machinery;
- standardized commercial UI;
- менее выраженный military low-end;
- greater manufacturer variation where content supports it;
- docking/cargo/mining/salvage sounds shared by physical equipment family;
- faction overlay only when equipment/operator identity реально отличается.

Minor/transnational actors могут получить ограниченный identity layer:

- contact stinger;
- comms tone;
- one or two station/industry accents;

но это не должно превращаться в скрытый production-complete sovereign package.

---

# 14. Post-core factions

Директорат, Лига Свободных Систем, Пограничная Конфедерация, Консорциум и Кочевой Флот **не получают production audio quota до своей стадии**.

Сейчас необходимо только сохранить extensibility:

- semantic event IDs не содержат hard-coded `empire/union-only` logic;
- faction sonic profile должен быть data-driven/content-driven;
- shared physics events reusable;
- faction overlay optional;
- asset resolver допускает future profile without simulation changes.

Нельзя заранее создавать placeholder soundtrack по стереотипам будущих фракций.

---

# 15. Music policy

Музыка — **optional breadth**, а не prerequisite sound foundation.

Приоритет:

```text
state readability
> UI/alerts
> ship/combat/industry feedback
> ambience
> restrained faction stingers
> full music breadth
```

Если music входит в RC scope, предпочтительна adaptive/layered система:

- calm system navigation;
- industrial/logistics activity;
- tension/crisis;
- active combat;
- aftermath/recovery;
- faction tonal overlays very restrained.

Запрещено:

- привязывать combat state к музыке, если игрок ещё не знает о бою;
- использовать национальные/исторические мелодии как прямой faction shorthand;
- постоянный heroic score, который конфликтует с sandbox tone;
- музыкально сообщать hidden diplomacy or enemy action.

Большая музыкальная библиотека может быть сокращена раньше обязательных SFX/alerts/ambience.

---

# 16. Voice / speech policy

Полноценная актёрская озвучка не является обязательной для Stage 23.

Обязательная архитектурная готовность:

- отдельный `VOICE/COMMS` bus;
- subtitles/captions;
- ducking hooks;
- localization-safe event metadata;
- никакой gameplay dependency от наличия voice asset.

Допустимы без актёрской речи:

- radio carrier noise;
- channel-open/close tone;
- synthetic acknowledgement tones;
- textual comms with short faction-specific pre/post cue.

---

# 17. Accessibility

Каждый meaning-bearing sound получает одну из категорий:

```text
COSMETIC
INFORMATIONAL
ACTIONABLE
CRITICAL
```

Для `ACTIONABLE` и `CRITICAL` обязательно:

- визуальный cue;
- caption/subtitle или textual notification;
- setting-independent readability.

Required settings:

- Master volume;
- Effects volume;
- UI volume;
- Alerts volume;
- Ambience volume;
- Music volume;
- Voice/Comms volume when relevant;
- mute-when-unfocused policy if desired;
- optional reduced combat density / reduced impact intensity audio mode if stress testing показывает необходимость.

Caption examples:

```text
[Входящая ракета]
[Пробитие брони]
[Отказ двигателя]
[Стыковка завершена]
[Потеря канала связи]
```

Caption не должен раскрывать больше, чем сам legitimate player observation.

---

# 18. Dynamic mixing and saturation

## 18.1. Ducking

Critical alerts могут кратко приглушать:

- ambience;
- music;
- distant combat;

но не должны полностью маскировать собственный ship damage feedback.

## 18.2. Voice stealing

При превышении budget удаляются сначала:

1. distant repetitive shots;
2. low-priority ambience details;
3. duplicate same-family impacts;
4. nonselected ship machinery;

никогда раньше:

- critical warning;
- direct player command error;
- own/selected ship critical damage;
- selected incoming threat.

## 18.3. Aggregation

Примеры:

- 20 одновременных PD shots → несколько representative bursts + continuous battle texture;
- 8 одинаковых distant impacts → bounded impact cluster;
- multiple factory machines → layered ambience bus instead of one voice per machine.

Aggregation не должна изменять authoritative count или скрывать critical outcomes.

---

# 19. Asset source/master format policy

До implementation audit не фиксировать runtime compression codec как архитектурный закон.

Production source/master recommendation:

- lossless source masters;
- 48 kHz preferred project sample rate;
- 24-bit source where available;
- clean headroom, no clipping;
- mono for point-source one-shots where spatialization benefits;
- stereo for ambience/music where appropriate;
- seamless loop boundaries for loops;
- no baked normalization that destroys relative dynamic intent.

Runtime format выбирается после проверки engine/platform memory/streaming behavior.

Long ambience/music должны быть stream-friendly; short UI/SFX — low-latency playback friendly.

---

# 20. Repository layout and naming

Recommended layout:

```text
assets/audio/
  ui/
  alerts/
  ship/
    propulsion/
    machinery/
    damage/
  combat/
    kinetic/
    beam/
    guided/
    defense/
    impacts/
    ew_sensor/
  industry/
    docking_cargo/
    mining_salvage/
    repair_construction/
  ambience/
    stations/
    locations/
    systems/
  factions/
    empire/
    industrial_union/
  comms/
  music/
```

Filename recommendation:

```text
<semantic-id>__<variant>__v<asset-version>.<ext>
```

Example:

```text
weapon.kinetic.fire.medium__03__v01.wav
ship.engine.thrust.medium.empire__loop-a__v01.wav
ui.validation_error__02__v01.wav
```

Filename не является simulation identity.

---

# 21. Audio manifest

Каждый shipped audio asset должен иметь metadata минимум:

- semantic audio ID;
- version/status (`PROTOTYPE`, `CANDIDATE`, `ALPHA`, `RC`);
- event family;
- faction profile or `shared`;
- size/state tags;
- one-shot/loop;
- source/master path;
- runtime path;
- duration;
- channel layout;
- loop start/end metadata where required;
- intended bus;
- priority class;
- max concurrent voices / cooldown policy if required;
- caption key for meaning-bearing events;
- provenance/license;
- checksum;
- replacement/alias information.

Asset resolver должен fail closed на required RC audio reference, но отсутствие optional cosmetic variation не должно ломать simulation.

---

# 22. Variation policy

Для повторяющихся sounds:

- 3–5 variants для high-frequency one-shots обычно достаточно как старт;
- small deterministic pitch/timing variation допустима;
- variance должна быть bounded;
- critical warning pitch/shape не рандомизируется настолько, чтобы потерять узнаваемость;
- loop pitch не меняется без связи с реальным machine state.

Recommended variation seed:

```text
stable event id
+ entity id
+ simulation tick/time bucket
→ bounded variant selection
```

Это полезно для deterministic captures, но selection не входит в simulation hash.

---

# 23. Production workflow

Для каждого family:

```text
1. Find authoritative event/state source
2. Define semantic audio event
3. Define information-scope rules
4. Create audio brief
5. Produce 3–5 candidates/variants as needed
6. Review semantic readability
7. Review faction identity where applicable
8. Bind through presentation resolver
9. Test distance/zoom/selection
10. Test saturation/voice budget
11. Add caption/accessibility mapping
12. Add provenance/manifest
13. Run deterministic capture / gameplay review
14. Promote status PROTOTYPE → CANDIDATE → ALPHA → RC
```

Нельзя начинать с «сгенерируем красивые звуки, потом найдём куда подключить».

---

# 24. Implementation plan

## Slice A — Event and authority audit

Цель: определить, какие authoritative events уже существуют и где звук должен читать state.

Deliverables:

- event inventory combat/navigation/logistics/industry/UI/living-world;
- mapping `runtime source → semantic audio event`;
- information-scope rules;
- missing presentation-only event adapter list;
- explicit list of events, которые **нельзя** инферить из disappearance/visual approximation;
- no new simulation authority.

Exit:

- ни один mandatory sound не требует угадывать событие по graphics state;
- missing authoritative event seam оформлен отдельно, как это делается для VFX.

## Slice B — Audio runtime foundation

Deliverables:

- audio service/resolver;
- bus/mixer hierarchy;
- settings persistence;
- semantic event registry;
- priority/voice budget;
- distance/zoom/focus policy;
- deterministic variation seed for acceptance capture;
- loop lifecycle tied to state;
- asset manifest loader/validator.

Exit:

- presentation-only;
- no simulation mutation;
- mute/audio-device failure cannot alter game state.

## Slice C — Core tactical vertical slice

Implement first:

- selection/command UI;
- engine idle/thrust;
- kinetic fire/impact/penetration;
- missile launch/incoming/intercept;
- PD;
- shield hit/overload;
- damage/critical/destruction;
- Empire + Union sonic overlay;
- saturation budget.

Acceptance scenarios:

- 1v1;
- 4v4;
- 8v8 mixed;
- 16v16 saturation;
- selected own ship vs detached camera;
- zoom ladder.

## Slice D — Economy / ship operations

Add:

- docking;
- cargo transfer;
- propellant/ammunition handling;
- mining;
- salvage;
- repair/refit;
- construction;
- shipyard launch;
- freight/convoy notifications;
- jump lifecycle.

Acceptance:

- trader journey;
- miner journey;
- repair/refit journey;
- logistics chain;
- no event fires before authoritative completion.

## Slice E — Living world / diplomacy / faction identity

Add:

- communications;
- mission cues;
- diplomacy/crisis/war/peace;
- reputation/major outcome;
- station ambience;
- full Empire/Union stinger palette;
- faction-specific station/industry overlays.

Acceptance:

- peaceful Imperial campaign;
- peaceful Union campaign;
- crisis/war pair;
- recovery pair;
- same semantic event remains recognizable across factions.

## Slice F — Stage 23E RC closure

Add/finalize:

- replacement of all prototype sounds in release-facing surfaces;
- final authored ambience;
- optional music if approved;
- accessibility captions/settings;
- final mix;
- licensing/provenance;
- stress voice-budget profiling;
- clean-package asset validation.

---

# 25. Automated validation

CI/content validation should eventually fail on:

- duplicate semantic audio ID;
- unresolved required audio reference;
- invalid manifest;
- missing source/provenance/license for shipped asset;
- non-loop-safe metadata for required loop;
- missing caption key for `ACTIONABLE/CRITICAL` sound;
- faction profile referencing nonexistent asset;
- semantic event bound to hidden/unauthorized information source;
- production-required asset still marked `PROTOTYPE`;
- file missing from package;
- unsupported format/sample metadata if runtime contract requires it.

Possible tooling:

- manifest/reference scanner;
- loop seam checker;
- peak/clipping scanner;
- duration sanity checks;
- duplicate checksum detector;
- package asset completeness report;
- deterministic event-capture trace (`tick/event/entity/audio-id/variant`).

Human review remains mandatory for:

- mix;
- fatigue;
- faction recognition;
- semantic clarity;
- large-battle readability;
- ambience repetition;
- perceived loudness balance.

---

# 26. Acceptance matrix

Audio RC is accepted only if all are true:

### Authority / causality

- [ ] каждый meaning-bearing sound связан с authoritative state/event;
- [ ] no hidden-information audio leaks;
- [ ] no sound invents hit/kill/docking/completion before authoritative confirmation;
- [ ] loop state stops/starts from actual state transition;
- [ ] audio playback failure does not affect simulation.

### Readability

- [ ] player action confirm/error is distinguishable;
- [ ] incoming threat is distinguishable from ordinary notification;
- [ ] armor hit, penetration, shield hit and subsystem failure are distinct;
- [ ] weapon families are recognizable without looking at VFX;
- [ ] selected/own ship remains readable in saturated combat.

### Faction identity

- [ ] Empire and Union are distinguishable in blind A/B test above chance by recurring players;
- [ ] same semantic event remains recognizable across factions;
- [ ] no faction identity relies on stereotypes forbidden by visual/systemic bibles;
- [ ] shared civilian sound does not accidentally read as a third sovereign package.

### Scale

- [ ] small/medium/large ship classes do not sound like simple volume-scaled copies;
- [ ] capital destruction does not use same transient as corvette destruction;
- [ ] station machinery is scale-consistent.

### Saturation / performance

- [ ] stress battle respects configured voice budget;
- [ ] critical alerts survive saturation;
- [ ] no uncontrolled voice/memory growth in long session;
- [ ] zooming out reduces detail before semantic criticality.

### Accessibility

- [ ] ACTIONABLE/CRITICAL events have visual/text redundancy;
- [ ] captions exist where required;
- [ ] bus volume controls persist;
- [ ] music can be muted without losing gameplay information;
- [ ] voice/comms, if added, has subtitles.

### Production quality

- [ ] no clipping/artifact defects in masters;
- [ ] required loops are seam-safe;
- [ ] provenance/license complete;
- [ ] required RC assets are packaged;
- [ ] no release-facing mandatory sound remains prototype/unreviewed.

---

# 27. Manual playtest charters

## 27.1. Ten-minute UI fatigue

Repeatedly:

- select objects;
- switch tabs;
- issue valid/invalid commands;
- open/close inspectors;
- change time scale.

Goal: UI sounds remain informative without раздражения.

## 27.2. Thirty-minute logistics loop

Trade → dock → cargo → undock → route → arrive → transfer.

Goal: logistics feels physical, not silent, while not becoming industrial noise spam.

## 27.3. Mining/repair loop

Mine → cargo full → return → repair/refit.

Goal: state transitions are audible and loop lifecycle does not stick after save/load.

## 27.4. Saturated battle

16v16+ with kinetic/guided/PD/beam/shield/damage.

Goal:

- critical cue survival;
- selected ship readability;
- no wall of indistinguishable noise;
- faction identity remains secondary to semantic clarity.

## 27.5. Empire/Union blind comparison

Use matching semantic scenarios:

- docking;
- engine thrust;
- medium kinetic fire;
- missile launch;
- damage control;
- official notification;
- shipyard ambience.

Goal: differences are systemic and coherent, not palette-equivalent gimmicks.

## 27.6. No-audio accessibility run

Master audio = 0.

Goal: game remains fully playable; every critical consequence still visible/textual.

---

# 28. Initial production priority list

Когда начинается реальная audio implementation, порядок:

### P0 — must exist first

1. UI confirm/cancel/error/warning/critical;
2. selection and command feedback;
3. own/selected ship propulsion;
4. kinetic/beam/guided/PD core families;
5. armor/shield/penetration/damage/destruction;
6. missile warning/intercept;
7. docking/cargo;
8. mining/repair;
9. jump lifecycle;
10. alert mix/voice budget;
11. Empire/Union minimal tactical overlay;
12. captions for critical cues.

### P1 — required for complete RC soundscape

1. full industry/construction;
2. station role ambiences;
3. full faction stinger set;
4. diplomacy/mission/living-world cues;
5. strategic logistics/shortage cues;
6. detailed damage-state machinery;
7. neutral/minor ambience identity;
8. final mix and saturation tuning.

### P2 — optional breadth

1. larger music library;
2. bespoke regional ambience variants;
3. extra cosmetic UI variations;
4. nonessential radio chatter;
5. extended post-core faction concept experiments.

---

# 29. Anti-patterns / запреты

Нельзя:

- воспроизводить звук `hit`, если authoritative hit не произошёл;
- делать enemy warning по невидимой цели;
- использовать audio-only critical information;
- иметь отдельную faction combat event logic только ради другого звука;
- добавлять bespoke sound для каждого hull, если достаточно общей equipment family;
- использовать один и тот же explosion sample для всех размеров/событий;
- делать музыку или ambience громче gameplay warnings;
- бесконечно складывать одинаковые voices в массовом бою;
- привязывать sound timing к render FPS;
- сохранять в save активные one-shot voices как authority;
- использовать национальные/исторические музыкальные клише как прямой символ фракции;
- смешивать Empire и Industrial Union через простую смену pitch/EQ одного master;
- принимать asset без provenance.

---

# 30. Definition of audio-complete RC

Audio считается RC-complete, когда:

1. все release-facing semantic events из обязательного inventory имеют production-ready audio либо явно обоснованно silent;
2. звук связан только с authoritative/legitimately observed state;
3. UI, alerts, combat, navigation, logistics, industry и ambience покрыты;
4. Империя и Индустриальный Союз имеют различимый, но семантически совместимый sonic identity;
5. large-battle voice budget и dynamic mix прошли stress acceptance;
6. critical warnings остаются слышимыми;
7. ACTIONABLE/CRITICAL sounds имеют visual/text redundancy;
8. audio settings persist;
9. asset manifest/provenance/license complete;
10. package validation подтверждает наличие required files;
11. no mandatory release-facing sound remains unreviewed `PROTOTYPE`;
12. optional music breadth не блокирует RC, если обязательный gameplay audio полностью закрыт.

---

## Краткая формула проекта

```text
STAR EMPIRES AUDIO =
physical consequence
+ information-safe sonification
+ heavy functional machinery
+ scale-aware ships
+ readable combat
+ audible logistics/industry
+ restrained faction identity
+ bounded saturation
+ accessibility redundancy
+ no fake state
```

### Империя

```text
weight + continuity + redundancy + disciplined naval authority + long-lived machinery
```

### Индустриальный Союз

```text
repeatability + common modules + crisp industrial process + bulk-flow rhythm + standardized signaling
```

Эти формулы являются отправной точкой для будущих briefs и не заменяют проверку каждого sound event против фактического runtime state.