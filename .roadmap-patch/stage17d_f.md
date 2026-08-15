## 17D — territory / control / construction access

**NEXT.** Цель — превратить текущий `controlledSystems` из просто persistent policy list в результат обычного territorial process, одинакового для player и AI.

### 17D.1 — territorial state model

Для StarSystem различаются как минимум:

- **presence** — faction имеет физические assets/traffic, но не получает sovereignty;
- **claim** — политически заявленная претензия без автоматического контроля;
- **control** — faction способна реально поддерживать юрисдикцию;
- **contested** — несколько factions имеют несовместимые control/claim основания;
- **recognition** — дипломатическое признание control/claim другими factions.

Существующий `controlledSystems` остаётся policy-compatible authoritative результатом control, но приобретение/потеря control проходит через deterministic ordinary rule.

### 17D.2 — основания реального контроля

Control score/evidence строится только из world state:

- persistent station/infrastructure anchors;
- локальное security/military presence;
- способность снабжать и поддерживать инфраструктуру;
- отсутствие или сила rival control presence;
- непрерывность присутствия / stabilization time;
- contested deterministic resolution.

Одна owned station не перекрашивает систему мгновенно. Однократный пролёт fleet также не создаёт sovereignty.

### 17D.3 — legal construction access

Construction command проверяет jurisdiction:

- в собственной controlled territory — ordinary domestic construction;
- в чужой controlled territory — только при explicit construction/basing right или иной concession;
- в contested territory — возможно только согласно legal state, с созданием grievance/claim consequences;
- в unclaimed territory — через обычный claim/control process;
- illegal/military construction как акт принуждения относится к Stage 18.

Player и AI проходят один authorization boundary; UI не обходит его.

### 17D.4 — territorial consequences

Только реально controlled jurisdiction может:

- применять territorial fiscal policy;
- определять default foreign construction/access regime;
- быть объектом recognition/claim treaty;
- участвовать в будущих blockade/front/war goals;
- давать strategic routing/security context.

Control сам по себе не создаёт деньги, ресурсы или бесплатную инфраструктуру.

### 17D acceptance

```text
player faction owns station in unclaimed system
→ presence exists, control absent
→ ordinary claim/control requirements fulfilled over time
→ system becomes controlled
→ foreign construction denied by default
→ explicit treaty/concession grants construction right
→ save/load
→ same control, claim and legal access
```

## 17E — diplomacy / market access / tariffs

Цель — перейти от «relation threshold открывает рынок» к explicit, persistent и объяснимой межгосударственной политике, сохранив текущий Stage-8 access core как рабочую основу.

### 17E.1 — diplomatic state hardening

К существующим directed `relations` добавляются bounded persistent structures для:

- trust/credibility history;
- grievances и territorial claims;
- treaty directory;
- obligations/guarantees;
- embargo/sanction clauses.

`relation[-100..100]` остаётся summary signal и backward-compatible input, но не является единственным источником решений.

### 17E.2 — proposal / response engine

Общий command/evaluator обрабатывает:

- offer / counteroffer;
- accept / reject;
- terminate with notice;
- breach;
- renew/expire.

AI оценивает proposal через common utility model интересов и doctrine. UI игрока показывает основные причины решения: ожидаемая выгода, зависимость, security/sovereignty concern, trust, fiscal cost.

### 17E.3 — market-access precedence

Effective legal access определяется в явном порядке:

```text
hard legal prohibition / embargo
→ explicit treaty right or exemption
→ ordinary relation-threshold policy
→ deny / allow
```

Market access остаётся transient ECS projection persistent diplomacy через общий refresh boundary; persistent state является источником истины.

### 17E.4 — tariffs и fiscal separation

Существующий `foreignTerritoryTariffBasisPoints` фиксируется как **territorial fiscal levy** с реальным station→treasury transfer.

Отдельный transaction/customs tariff вводится только вместе с trade-controller integration:

```text
quoted buy/sell economics
+ applicable customs tariff
+ route risk/time
→ route profitability
→ actual trade
→ customs wallet transfer
```

Route planner обязан знать tariff заранее. Никаких невидимых постфактум штрафов или бесплатного treasury income.

### 17E.5 — embargoes / sanctions

Embargo не применяет абстрактный debuff. Он запрещает определённый legal market access, после чего обычные systems:

- перестраивают маршруты;
- ищут альтернативных suppliers/markets;
- сталкиваются с увеличением ETA/cost;
- расходуют buffers;
- создают shortage/price/production consequences.

Embargo может причинять measurable cost обеим сторонам и поэтому тоже проходит AI utility evaluation.

### 17E.6 — treaties / credibility

Выполнение договора постепенно укрепляет trust; нарушение создаёт explicit breach/grievance и снижает credibility. Эффект не обязан быть симметричным.

Нарушение договора не «ломает игру»: договорный state меняется, access/obligations refresh-ятся, а экономические и будущие военные последствия продолжаются ordinary systems.

### 17E.7 — economic-dependence diagnostics

Для каждой значимой пары factions доступны read-only diagnostics:

- critical imports dependency;
- export/market dependency;
- alternative-route/supplier cost;
- chokepoint exposure;
- buffer endurance;
- estimated cost of access loss/embargo.

Эти значения используются AI и позже отображаются в faction-management UI.

### 17E acceptance

```text
A depends on B for critical input
→ A proposes trade-access treaty
→ B evaluates benefit, dependency risk, trust and doctrine
→ treaty accepted
→ access projected to real markets
→ physical trade grows
→ B imposes transaction tariff / A searches alternatives
→ breach or embargo removes legal access
→ routes physically change and shortage emerges
→ save/load preserves treaty, trust, access and economic consequences
```

## 17F — faction policies / strategic economy

Цель — дать player faction и AI factions общий набор государственных economic-policy решений. Policy не заменяет рынок: она изменяет бюджеты, правовые ограничения и strategic demand, после чего реагирует обычная экономика.

### 17F.1 — doctrine profile

Persistent/data-driven doctrine задаёт веса общей decision model, а не performance bonus. Player faction получает editable baseline doctrine в допустимых пределах; authored AI factions получают характерные profiles.

Doctrine влияет на:

- openness vs autarky;
- reserve vs growth preference;
- security vs efficiency;
- expansion willingness;
- treaty behavior;
- tolerance of dependency and fiscal stress.

### 17F.2 — fiscal policy

Faction может задавать:

- own-station tax rate;
- territorial foreign-station levy;
- treasury reserve floor;
- station liquidity-support policy;
- construction/investment budget priorities;
- после Stage 17.5/18 — military ammunition/repair/replacement reserve priorities.

Все выплаты и сборы являются real wallet transfers. «Budget» — authorization/priority над treasury, а не второй магический источник денег; отдельный sub-account допускается только как conserved persistent account.

### 17F.3 — fiscal trade-offs

Policy должна иметь реальные последствия:

- высокий tax быстрее наполняет treasury, но может ухудшить liquidity собственных stations;
- низкий reserve ускоряет expansion, но повышает риск неспособности финансировать emergency logistics/repair;
- subsidy поддерживает critical station, но уменьшает public treasury;
- protectionism снижает foreign dependence, но может повысить цены и увеличить логистическую дистанцию;
- open trade повышает efficiency, но может создать supplier/chokepoint dependency.

Ни один trade-off не реализуется flat multiplier, если его можно получить через wallets, markets, logistics и production.

### 17F.4 — strategic stock / production policy

Переиспользуется текущая философия `FactionStrategicPolicyEngine`:

```text
strategic goal / resilience policy
→ target stock floor / desired production recipe
→ ordinary market prices and demand
→ TradeAI logistics
→ physical inputs
→ timed production
```

Policy не materialize-ит товар и не завершает производство мгновенно.

### 17F.5 — resilience policy

Faction может сознательно предпочесть:

- diversified suppliers;
- minimum strategic buffers;
- local production despite higher nominal cost;
- redundant routes/infrastructure;
- critical-item import limits.

Цена resilience должна проявляться как реальные дополнительные capital/logistics/operating costs.

### 17F.6 — policy feedback / anti-oscillation

AI пересматривает policy по bounded cadence и hysteresis:

```text
measure pressure / dependency / treasury / shortage
→ compare against doctrine thresholds
→ choose bounded policy adjustment
→ wait observation window
→ measure consequences
```

Запрещены every-tick tariff/tax/recipe oscillations. Decisions deterministic при одинаковом state.

### 17F.7 — player/AI parity

Player UI отправляет те же policy commands, которые может сформировать AI planner. Игрок получает больший уровень прямого контроля, но не отдельные экономические правила и не бесплатное исполнение policy.

### 17F acceptance

```text
faction has critical import dependency and weak treasury
→ policy chooses reserve + supplier diversification
→ strategic stock demand rises
→ ordinary traders establish more expensive alternative route
→ tax/subsidy transfers change real wallets
→ buffers improve while treasury/growth incur measurable cost
→ policy does not oscillate
→ save/load preserves doctrine, policy and economic state
```

