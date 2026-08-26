# Star Empires — Индустриальный Союз: каноническая systemic identity

> Статус: **CORE FACTION / REQUIRED THROUGH STAGE 23**  
> Faction role: mandatory mechanical/political/visual contrast to the Empire.  
> Этот документ фиксирует systemic identity. Детали конкретной конституции, исторических персон и региональной истории могут расширяться content-authoring документами, но не должны противоречить описанной здесь промышленной и стратегической логике.

---

## 1. Короткая формула

**Индустриальный Союз** — крупная индустриальная держава, чья сила строится на стандартизации, длинных производственных сериях, высокой пропускной способности тяжёлой промышленности, концентрированной логистике и способности быстро восстанавливать массовые потери при сохранении сырьевой и транспортной базы.

Его сила должна ощущаться как:

```text
standardization
+ repeated production series
+ high industrial throughput
+ compact component vocabulary
+ efficient fleet replacement
+ strong bulk logistics
```

а слабости как:

```text
resource hunger
+ dependence on bulk flows
+ concentration risk
+ tooling/series inertia
+ vulnerability to bottleneck disruption
+ lower flexibility when supply assumptions break
```

Союз не получает `+production%`. Он производит много потому, что его институты и промышленность **реально организованы под повторяющийся массовый выпуск**.

---

## 2. Политико-институциональная идентичность

Канонически фиксируется не конкретная форма конституции, а systemic requirement:

- государство/союз способен координировать крупные межсистемные производственные программы;
- промышленная политика является центральным предметом стратегического управления;
- инфраструктура, сырьё и производственные серии рассматриваются как вопросы государственной безопасности;
- локальная производственная автономия допустима, но большие военные/инфраструктурные программы должны собираться в общую industrial strategy;
- политическая легитимность тесно связана с занятостью, развитием, производственной устойчивостью и способностью защищать supply base.

Точная внутренняя структура органов Союза должна быть отдельным lore/content authoring decision Stage 22, а не поводом создавать другую simulation authority.

---

## 3. Главный mechanical pillar — production specialization

Союз является core proof для общей механики производственной специализации.

Пример:

```text
shipyard repeatedly builds Design-A
→ tooling/workflow familiarity stabilizes
→ fewer changeovers
→ fewer component families in local buffer
→ simpler spare-parts support
→ predictable throughput
→ lower disruption per repeated unit
```

Это должно быть выражено через реальные manufacturing constraints, а не hidden faction modifier.

Возможные reusable axes:

- tooling/changeover work;
- production-series commitment;
- facility familiarity/capability;
- work queue stability;
- component variety;
- buffer depth;
- supplier continuity;
- maintenance/spare-part commonality.

Exact implementation выбирается после code audit и должна оставаться общей для player/AI/future factions.

---

## 4. Стандартизация

### 4.1 Что означает стандартизация

Союз предпочитает меньшее число хорошо освоенных:

- hull architectures;
- propulsion families;
- reactor families;
- common components;
- ammunition types;
- repair parts;
- logistics interfaces.

Это не запрещает variants/refits. Различия должны по возможности строиться вокруг общей производственной базы.

### 4.2 Реальные преимущества

При хорошо поддерживаемой standard family возможны:

- меньше component diversity в запасах;
- более стабильная manufacturing chain;
- проще bulk procurement;
- проще training/maintenance;
- быстрее replacement одинаковых серий;
- более предсказуемые supply requirements.

### 4.3 Реальные недостатки

- смена doctrine может требовать дорогого retooling;
- common bottleneck может одновременно ударить по большому числу units;
- противник легче понимает распространённые platform families;
- специализированная линия хуже приспосабливается к резкому изменению входных материалов;
- чрезмерная унификация может создавать systemic single points of failure.

---

## 5. Resource hunger

Высокий throughput создаёт высокий physical demand.

Союз должен постоянно оценивать:

```text
current stock
+ secured production
+ contracted/imported supply
- civilian demand
- committed industrial demand
- military replenishment demand
- expected losses/replacement
→ projected strategic deficit
```

Ключевой systemic identity:

> Союз может быть богат мощностями, но ограничен сырьём, маршрутами, handling throughput и bottleneck components.

Поэтому доступ к ресурсам естественно влияет на:

- trade;
- infrastructure investment;
- diplomacy;
- long-term contracts;
- stockpiles;
- escort/security;
- territorial interest;
- coercion;
- war goals.

Война остаётся одним из возможных решений, а не scripted personality outcome.

---

## 6. Industrial geography

Индустриальный Союз должен особенно зависеть от Stage-20 geography.

Сильные регионы могут специализироваться на:

- ore extraction;
- bulk refining;
- heavy components;
- final hull assembly;
- ammunition;
- repair/replacement hubs;
- strategic stockpiles.

Эта специализация создаёт network, а не self-sufficient every-system economy.

Хорошо организованный Союз силён, когда:

- high-volume routes работают;
- chokepoints защищены;
- hubs снабжены;
- production queues стабильны;
- yards имеют components и labour/work capacity.

Он уязвим, когда:

- bulk route interrupted;
- critical hub lost;
- strategic input exhausted;
- replacement program конкурирует с civilian needs;
- standard family зависит от одного unavailable component.

---

## 7. Procurement model

Союз предпочитает:

- large batch orders;
- long-running production series;
- framework procurement;
- planned buffer replenishment;
- repeatable hull/module packages;
- capacity expansion around known bottlenecks.

В отличие от Империи, акцент core identity не на institutional reserve/redundancy каждого дорогого объекта, а на **системной воспроизводимости большого числа стандартизированных объектов**.

---

## 8. Fleet doctrine

Союз должен поддерживать fleet composition, где ценность возникает из:

- repeatable common platforms;
- predictable combined-arms composition;
- strong replacement pipeline;
- escort/logistics scale;
- ammunition/propellant throughput;
- ability to replenish losses while industrial network survives.

Допустимые content tendencies Stage 22:

- robust standardized hulls;
- practical armor;
- large missile/kinetic ammunition programs where industrially justified;
- common propulsion families;
- fewer bespoke unique capital systems;
- strong tugs, tankers, freighters and repair logistics;
- large formation use when supply supports it.

Ни одна тенденция не должна превращаться в mandatory bonus by faction name.

---

## 9. Combat and campaign behavior

Союз должен быть способен рационально принимать attrition, **если**:

- objective materially важен;
- replacement capacity реально существует;
- supply line поддерживается;
- loss exchange не разрушает стратегический reserve;
- production can backfill losses в приемлемый срок.

Он должен отступать/деэскалировать, если:

- bottleneck supply broken;
- yards cannot replace losses;
- critical freight throughput collapses;
- offensive consumes strategic material faster than economy can replenish;
- long series commitment becomes economically fatal.

Это создаёт другой strategic feel, чем у Империи:

- Империя чаще сохраняет дорогой индивидуальный capability;
- Союз чаще оценивает воспроизводимость formation/system.

Но оба используют одну readiness/loss/replacement authority.

---

## 10. Strategic AI priorities

Индустриальный Союз должен особенно ценить evidence о:

- projected material deficits;
- yard idle time from missing inputs;
- route throughput;
- production backlog;
- stockpile days-of-supply;
- fleet replacement time;
- supplier concentration;
- strategic resource accessibility;
- security cost of industrial corridor;
- capacity expansion payback.

Типичные lawful goals:

```text
secure_route
obtain_access
stockpile
escort
build/expand capacity through ordinary construction intent
claim where politically/legal feasible
coerce only when material interest and feasibility support it
raid/blockade only as real strategic operation
invade only after causal escalation
recover after losses
```

---

## 11. Diplomатическая логика

Союз должен часто предпочитать:

- long-term resource access;
- predictable trade treaties;
- transit rights;
- infrastructure/security agreements;
- mutual industrial dependence when beneficial;
- coercive pressure when critical supply is denied and peaceful alternatives fail.

War goals, если возникают, должны быть особенно хорошо связаны с:

- route security;
- access;
- resource source;
- industrial gateway;
- protection/relief of strategic production region.

Нельзя использовать generic «Союз агрессивнее» без evidence chain.

---

## 12. Technology philosophy

Союз не обязан быть low-tech.

Каноническая ось — **manufacturable at scale**.

Он предпочитает technology, которая:

- может быть надёжно произведена существующей базой;
- использует доступные standard components;
- ремонтируется по широкой сети;
- допускает устойчивую series production;
- не создаёт неоправданный rare-component bottleneck.

Advanced system может быть принят, если Союз способен развернуть под него промышленную базу.

Следовательно различие с будущим Директоратом не «низкая технология против высокой», а:

- Союз — performance constrained by scalable manufacturability;
- Директорат — performance pushed through complexity/precision even with fragile bottlenecks.

---

## 13. Maintenance and replacement

Ключевой gameplay loop:

```text
loss/damage
→ physical repair or replacement demand
→ common spare/component demand
→ yard queue
→ work/time
→ fresh FleetId only after real commissioning
```

Союз должен быть особенно силён, если commonality реально сокращает:

- spare diversity;
- queue changeover;
- supplier fragmentation;
- service complexity.

Но destroyed entity никогда не воскресает и не получает тот же FleetId через «быстрое восполнение».

---

## 14. Logistics identity

Союз должен иметь сильную bulk-logistics культуру:

- large freighters;
- industrial haulers;
- tankers;
- convoy organization;
- depot networks;
- predictable scheduled flows;
- infrastructure sized for high throughput.

Уязвимость — высокая заметность и важность этих flows.

Потеря convoy/hub должна иметь реальный downstream effect на production, а не abstract penalty.

---

## 15. Visual identity direction

Полный visual bible Индустриального Союза должен быть создан до bulk Stage-22 art production.

Systemic visual requirements уже фиксируются:

- mass-produced, standardized visual grammar;
- repeated modular structural units;
- clear family resemblance across hull classes;
- practical industrial construction;
- visible heavy handling/service interfaces;
- fewer aristocratic/status details than Империя;
- hulls should look designed for reproducible assembly and maintenance at scale;
- large logistics/industrial vessels are visually important, not background afterthought;
- no simple recolor of Imperial hulls;
- no fantasy industrial exaggeration that breaks hard-SF service geometry.

Конкретная palette/heraldry/character-clothing bible должна быть отдельным Stage-22 authoring deliverable и не выдумывается этим systemic документом.

---

## 16. Character and social presentation

До отдельного visual/lore bible фиксируются только systemic principles:

- профессия и industrial function должны читаться визуально;
- производственные/логистические/инженерные роли имеют высокий общественный вес;
- hierarchy должна отличаться от имперской аристократически-служебной визуальной логики;
- standardized equipment/uniform families допустимы как визуальное отражение production culture;
- character art остаётся grounded hand-painted RPG style по Character Master Prompt.

Точные названия должностей, политических органов и церемониальных элементов — Stage-22 authored lore, а не simulation requirement.

---

## 17. Player-facing gameplay identity

Игрок должен чувствовать Союз через:

- доступность common spare/module families;
- большие industrial markets;
- длинные production contracts;
- заметные freight corridors;
- крупные shipyards;
- predictable common hull families;
- jobs tied to raw-material and component shortages;
- convoy/security work;
- последствия bottleneck disruption;
- быстрое, но не бесплатное восстановление массового флота при работающей industrial base.

---

## 18. Stage mapping

### Stage 21I

Pairwise corpus должен доказать, что без финальных Stage-22 ships:

- Союз иначе ранжирует shortage/route/industrial evidence, чем Империя;
- goals остаются explainable;
- no omniscient material knowledge;
- crisis/war still requires ordinary causal lifecycle;
- save/load сохраняет commitment and anti-churn.

### Stage 22.0

- exact current industrial runtime ID disposition/migration;
- Industrial Union profile/manifest schema;
- visual bible authoring brief;
- industrial specialization capability audit.

### Stage 22.2 — Industrial Union contrast slice

Production-complete package должен включать:

- systemic/political identity;
- industrial specialization implementation/configuration;
- engineering/content doctrine;
- six military base hull roles minimum;
- three civilian/support base hulls minimum;
- three signature station variants;
- reference industrial network;
- reference fleet + logistics train;
- six recurring NPCs;
- ten mission templates;
- two short faction chains;
- production visual/audio/localization subset;
- peaceful/crisis/battle/loss/replacement/save-load acceptance.

### Combined Stage 22

- pairwise logistics soak;
- pairwise fleet balance;
- resource-dependency scenarios;
- route interdiction/recovery;
- anti-universal-build;
- anti-linear-obsolescence;
- no hidden production or replacement grant.

### Stage 23

- final presentation;
- onboarding of industrial logic;
- UI/readability;
- migration/save hardening;
- production package stability.

---

## 19. Required common-system changes

Для полной core implementation могут понадобиться минимальные extensions существующих authorities:

1. **production-series identity/commitment** — если current Stage-18 work model не может выразить repeated-series specialization;
2. **changeover/tooling/workflow cost** — только если code audit подтверждает отсутствие подходящего existing seam;
3. **component-commonality diagnostics** — derived/read-only where possible;
4. **strategic shortage forecast** — actor-bounded projection, не new inventory authority;
5. **industrial dependency graph** — read-only derivation over real recipes/facilities/routes;
6. **procurement batching/commitment** — through ordinary treasury/industry order paths;
7. **anti-churn persistence** — reuse Stage-21 goal/policy deadlines.

Нельзя создавать отдельный `IndustrialUnionProductionSystem`.

---

## 20. Acceptance matrix

Индустриальный Союз считается системно реализованным только если:

1. Production advantage объясняется repeated-series/commonality/throughput state, а не faction multiplier.
2. Смена производственной серии имеет реальную цену или потерю accumulated specialization benefit.
3. Resource scarcity ограничивает производство физически.
4. Route disruption изменяет delivery/stock/production causally.
5. Standardization уменьшает diversity burden только там, где реальные definitions действительно общие.
6. Fleet replacement требует реальной yard/material/time/treasury chain.
7. AI видит shortage только через allowed observations/projections.
8. War for resources/access cannot start randomly.
9. Industrial concentration создаёт measurable vulnerabilities.
10. Save/load сохраняет production commitments/strategic goals/deadlines.
11. Player-created faction может использовать те же production specialization mechanics.
12. Визуальная идентичность выражает mass production без recolored Empire shortcut.

---

## 21. Не-кодируемые заранее детали

До Stage-22 evidence не фиксировать:

- `+X% manufacturing speed`;
- exact learning curve;
- exact changeover duration;
- fixed cheaper-ship percentage;
- mandatory armor/weapon superiority;
- guaranteed fleet-size ratio;
- concrete political nomenclature, не необходимую для mechanics.

Числа должны появиться из измеримых production/balance tests.

---

## 22. Итоговая формула Индустриального Союза

```text
industrial coordination
→ standardized families
→ repeated series and high throughput
→ strong bulk logistics and replacement capacity
→ persistent hunger for materials/routes
→ power while industrial network remains intact
→ severe strategic pressure when bottlenecks or corridors fail
```

Это каноническая core mechanical identity Индустриального Союза и обязательный контраст к Империи.
