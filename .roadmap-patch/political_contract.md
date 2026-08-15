Stage 17 переиспользует Stage-8 faction core: treasury, budgets, subsidies, directed relations, territory, market access, fiscal levies, stock/production policy и persistence. Новые political/diplomatic layers расширяют этот core, а не создают отдельную player-only или scripted diplomacy subsystem.

## Политико-экономическая архитектура взаимодействия фракций — общий contract Stage 17–18

Дипломатия не является отдельной шкалой «нравится / не нравится» и не выдаёт абстрактные бонусы. Она должна быть следствием реального положения фракций в мире: ресурсов, рынков, логистики, территории, военной угрозы, договорных обязательств и институциональной доктрины.

Базовая причинная цепочка:

```text
physical economy / territory / security state
→ measurable interests and dependencies
→ institutional doctrine + diplomatic history
→ proposal / policy / strategic decision
→ access / tariff / treasury / logistics / production consequences
→ changed physical world state
→ changed interests and future diplomacy
```

### Государственные интересы

Каждая faction оценивает не абстрактную «силу соседа», а конкретные интересы, вычисляемые из authoritative world state:

- **economic security** — доступ к критическим ресурсам, рынкам, производственным цепочкам, shipyard/repair capability и транспортным маршрутам;
- **logistics security** — длина и уязвимость supply lines, chokepoints, наличие альтернативных маршрутов и запасов;
- **territorial security** — собственные controlled systems, спорные claims, важность пограничных систем и инфраструктуры;
- **industrial resilience** — зависимость от одного поставщика, одной системы или одного типа производства;
- **fiscal health** — treasury, station liquidity, construction/replacement burden и возможность финансировать выбранную policy;
- **military security** — доступная информация о чужих силах, мобилизации, присутствии возле границы и способности защитить routes/territory;
- **strategic opportunity** — ресурсы, рынки, незанятые или слабо защищённые системы, союзники, возможность снизить опасную зависимость;
- **treaty credibility** — соблюдение прошлых соглашений, нарушения, выполненные обязательства и накопленные grievances.

Эти показатели являются diagnostics/inputs для decision engine. Они не дают скрытых `+20% trade` или `-15% combat` бонусов.

### Институциональная доктрина faction

Различия между государствами выражаются не магическими faction modifiers, а весами и порогами общей decision model. Для authored faction и будущей faction игрока предусматривается persistent/data-driven **doctrine profile**.

Минимальные axes:

- `tradeOpenness` — готовность допускать чужие рынки/капитал и зависеть от внешней торговли;
- `securityPosture` — терпимость к риску и чужому присутствию рядом с критической инфраструктурой;
- `expansionPreference` — склонность инвестировать в новые territory/infrastructure;
- `sovereigntySensitivity` — насколько болезненно воспринимаются чужие claims, bases и строительство;
- `treatyLegalism` — вес договорных обязательств и цена нарушения собственного слова;
- `interventionism` — готовность нести расходы ради союзника или баланса сил;
- `economicResiliencePriority` — готовность платить более высокую цену за diversification, reserves и domestic production.

Doctrine меняет **приоритеты решения**, но не физические возможности. Торгово открытая faction всё равно не может импортировать отсутствующий товар; милитаристская faction не получает бесплатный флот; legalist не обязан принимать невыгодный договор.

### Directed diplomatic state: relation недостаточно

Существующий `FactionRelationState[-100..100]` сохраняется как компактная directed summary/compatibility input, но итоговая дипломатическая модель не должна сводиться к одному числу.

Для пары `A → B` планируются отдельные persistent/derived составляющие:

- **relation** — общий текущий политический тон;
- **trust / credibility** — ожидание, что B выполнит обещание;
- **perceivedThreat** — оценка военной/территориальной угрозы на основании доступной информации;
- **grievances / claims** — конкретные причины конфликта: нарушение договора, contested territory, экспроприация, атака, blockade и т.п.;
- **obligations** — действующие договорные обязательства A перед B;
- **economicInterdependence** — измеримая зависимость торговли/промышленности A от B;
- **treaties** — explicit юридические соглашения и их clauses.

Состояние остаётся направленным: A может критически зависеть от B и бояться его, тогда как B почти не зависит от A. Поэтому не вводится правило вида `relation < -50 = война` или `relation > 80 = союз`.

### Общий deterministic decision evaluator

AI faction и counterpart игрока оценивают diplomatic proposal через общую объяснимую utility model:

```text
utility =
    expectedEconomicBenefit
  + securityBenefit
  + strategicGoalAlignment
  + treatyAndTrustValue
  + doctrineFit
  - fiscalCost
  - sovereigntyCost
  - dependencyRisk
  - escalationRisk
  - opportunityCost
```

Каждый член utility должен выводиться из world state, doctrine или diplomatic history и быть доступен diagnostics/debug UI. Stable ordering/tie-breaks обязательны.

Игрок управляет policy собственной faction напрямую в пределах своих полномочий, но **не может принудительно заставить AI принять договор**. Предложение игрока оценивается тем же counterpart evaluator, что proposal одной AI faction другой.

### Economic interdependence

Взаимозависимость строится из реальной экономики, а не из abstract influence points. Минимальные metrics:

- доля critical-item imports от конкретной faction;
- доля exports/market revenue, зависящая от конкретного партнёра;
- концентрация поставщиков и покупателей;
- наличие альтернативного supplier/market и дополнительная стоимость маршрута;
- зависимость routes от чужих controlled systems/chokepoints;
- inventory buffer endurance при прекращении импорта;
- replacement time критической industrial capability;
- в будущем — зависимость от foreign shipyard/refit/repair capability.

Это позволяет получить естественные политические ситуации: слабая militarily faction может быть экономически незаменима; богатая держава может избегать войны из-за критической зависимости; embargo может ударить и по тому, кто его объявил.

### Treaty contract

Договор — persistent юридический объект, а не временный UI modifier. Он должен иметь stable ID, parties, clauses, дату вступления, optional expiry, notice/cancellation rules и breach semantics.

Планируемые clauses:

- bilateral/unilateral **market access**;
- **tariff ceiling / reduction / exemption**;
- **transit rights** через controlled territory;
- **non-aggression**;
- **construction / basing rights** в определённой territory;
- **resource supply agreement**, исполняемый через ordinary markets/orders/logistics, а не virtual delivery;
- **defense guarantee / mutual defense** — обязательство, военное исполнение которого реализуется Stage 18;
- **recognition / territorial settlement** для claims и control;
- **reparations / payments**, исполняемые conserved treasury transfers.

«Alliance» не является отдельным флагом дружбы: это набор explicit obligations и прав.

### Экономическая дипломатия

Политические решения обязаны воздействовать через уже существующую экономику:

```text
market access
→ кто физически может торговать

tariff / fiscal levy
→ реальный wallet transfer
→ изменение effective trade economics / station liquidity

embargo
→ закрытие legal access
→ route replanning
→ потеря поставщика/рынка
→ shortage / price / production response

strategic stock policy
→ targetStock floor
→ обычный market demand
→ TradeAI logistics
→ physical delivery

production policy
→ ordinary recipe selection
→ inputs / time / outputs

subsidy
→ treasury → station wallet
→ ordinary liquidity

reparations / treaty payment
→ treasury → treasury/wallet conserved transfer
```

Запрещены diplomatic effects, которые напрямую создают товары, деньги, production output или «урон экономике» без physical/economic механизма.

### Два разных типа тарифов

Нужно явно различать:

1. существующий Stage-8 **territorial fiscal levy**: surplus foreign station wallet → treasury контролирующей faction внутри её controlled system;
2. будущий **transaction/customs tariff**: часть конкретной внешнеторговой сделки.

Если вводится transaction tariff, он обязан:

- входить в expected route/trade cost **до** выбора маршрута;
- взиматься только при реально состоявшейся операции;
- записываться в ledger как conserved transfer;
- влиять на route choice и конкурентоспособность, а не существовать как UI percentage, оторванный от торговли.

### Escalation ladder

Политический конфликт развивается ступенчато:

```text
normal competition
→ diplomatic friction
→ tariff / access dispute
→ sanctions / embargo
→ formal demand / ultimatum
→ mobilization
→ blockade / limited armed coercion
→ formal war
→ ceasefire
→ settlement / peace treaty
```

Stage 17 реализует институциональные и экономические ступени. Armed coercion, blockade, war goals и formal war/peace принадлежат Stage 18, но используют те же treaties, claims, dependencies и grievances.

### Information boundary

Decision engine не должен навсегда зависеть от omniscient world state. На Stage 17 допустимо использовать authoritative state как временный источник данных, но API разделяет:

- **world truth**;
- **known/observed diplomatic-economic state**;
- **confidence/freshness**.

Stage 19 сможет подставить sensor/intelligence/comms latency без переписывания дипломатической логики.

### Граница внутренней политики

Stage 17 моделирует faction как **институционального стратегического актора**, а не симулирует население, парламент, корпорации и элиты фиктивными процентами.

Persistent governors, commanders, elite groups, legitimacy, regional interests и внутриполитическое давление вводятся только вместе с living-NPC layer Stage 20. Они должны модифицировать тот же doctrine/decision/treaty contract, а не создавать вторую параллельную дипломатию.

