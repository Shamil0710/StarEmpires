# Star Empires — Stage 16: матрица приёмки

> Статус: **COMPLETE / GATE PASSED**
>
> Финальное подтверждение: PR #70, CI #1337 / run `31849675260` — **484/484 tests**, strict Javadoc, JaCoCo и shaded desktop JAR build green.
>
> Итоговая запись: `docs/stage16_completion_record.md`.
>
> Основная спецификация: `docs/stage16_player_construction.md`.
>
> Политика открытого рыночного снабжения: `docs/stage16_open_market_supply.md`.
>
> Ниже сохранён исторический acceptance contract Stage 16. Формулировки «должен» описывают требования, по которым этап принимался; фактический результат и сознательно отложенные ограничения зафиксированы в completion record.

---

## 1. Назначение

Этот документ определяет минимальный набор автоматизированных проверок, после которых Stage 16 можно считать завершённым. Он не заменяет unit-тесты отдельных классов; его задача — доказать, что player construction работает в той же физической экономике, что и NPC/faction construction.

---

## 2. Группа A — ownership и persistence

### A1 — миграция старого PlayerState

Дано: save до Stage 16.

Ожидание:

- owned construction projects = empty;
- owned stations = empty;
- остальные player fields не меняются;
- save можно загрузить и продолжить симуляцию.

### A2 — project ownership save/load

- создать player project;
- сохранить игру;
- загрузить;
- тот же `ConstructionProjectId` принадлежит игроку;
- project указывает на тот же physical site `EntityId`.

### A3 — completed station ownership save/load

- завершить project;
- сохранить/загрузить;
- тот же `OwnedStationRef` существует;
- station entity существует в той же системе;
- ownership не дублируется.

### A4 — destruction reconciliation

- уничтожить player-owned station обычным destruction pipeline;
- ownership должен исчезнуть;
- не создаётся replacement station;
- save/load не возвращает уничтоженный актив.

---

## 3. Группа B — placement/access

### B1 — валидное размещение

- active + discovered system;
- допустимые координаты;
- достаточный clearance;
- разрешённая территория.

Результат: создаётся ровно один project и одна site entity.

### B2 — undiscovered system

Команда отклоняется, world/player state не меняются.

### B3 — remote system baseline

Попытка первого-slice remote placement без физического присутствия отклоняется.

### B4 — geometry collision

Попытки разместить site:

- поверх станции;
- поверх construction site;
- в запрещённой asteroid/resource зоне;
- в exclusion zone jump-arrival anchor.

Все отклоняются до создания project.

### B5 — чужая территория без доступа

Project не создаётся, деньги не списываются.

---

## 4. Группа C — funding и conservation

### C1 — недостаточный player wallet

Funding operation возвращает failure/zero; site wallet и player wallet неизменны.

### C2 — успешное финансирование

Для суммы X:

```text
playerWallet_before - X = playerWallet_after
siteWallet_before + X = siteWallet_after
```

Ledger содержит один соответствующий MONEY_TRANSFER.

### C3 — rollback

Искусственно вызвать exception после подготовки candidate player state/transfer boundary.

Ожидание: итоговая сумма денег полностью восстановлена, ownership/project state не частично повреждены.

### C4 — extra funding

Дополнительное финансирование увеличивает site liquidity, но не изменяет persisted `buildDurationTicks`.

---

## 5. Группа D — физическая доставка

### D1 — owned fleet in range

- source fleet принадлежит игроку;
- находится рядом с site;
- достаточно медленный/остановлен;
- имеет требуемый cargo.

После delivery:

```text
source stock -= accepted
site stock += accepted
project delivered += accepted
```

### D2 — remote transfer rejected

Тот же fleet в другой части системы/вне range не может передать cargo.

### D3 — non-owned source rejected

Игрок не может вызвать manual construction transfer из чужого fleet inventory.

### D4 — jump transit rejected

Fleet в `IN_TRANSIT` не может передать материалы.

### D5 — non-required item rejected

Inventory и project state неизменны.

### D6 — partial delivery

Accepted amount ограничен remaining requirement; лишний cargo остаётся на корабле.

---

## 6. Группа E — открытая живая рыночная поставка

Эта группа обязательна для Stage 16. Владение проектом не создаёт монополию на снабжение: стройплощадка является обычным физическим покупателем для любого economic actor с market access.

### E1 — site publishes only remaining demand

Для каждого item demand соответствует фактически недостающему requirement.

После частичной поставки buy demand уменьшается. После fulfillment buy demand исчезает.

### E2 — supplier from another faction

Обычный NPC trader другой faction, который имеет legal/market access и считает сделку выгодной:

```text
видит construction demand
→ выбирает реального supplier
→ покупает реальный товар
→ физически движется / прыгает
→ продаёт construction site через обычный TradeController
```

Проверяется, что принадлежность к faction владельца проекта не требуется.

### E3 — neutral independent supplier

Нейтральный независимый trader с разрешённым доступом может снабжать площадку на тех же экономических условиях, что и faction trader.

### E4 — hostile / embargoed supplier rejected

Hostile actor или сторона без market access не может выполнить продажу на construction site через обычный market path.

Demand при этом не исчезает и не превращается в скрытую поставку.

### E5 — physical trade conservation

В полном внешнем supply loop:

```text
supplier market stock ↓
trader cargo ↑
trader physically travels
trader cargo ↓
site inventory ↑
site wallet ↓
seller/trader-side money ↑
```

Все денежные изменения проходят через ordinary trade/ledger semantics.

### E6 — insufficient site liquidity

Site с недостаточным `WalletComponent` не может купить товар, даже если demand существует и маршрут выгоден.

После реального дополнительного funding закупка снова может стать исполнимой.

### E7 — no hard reservation

Если другой покупатель раньше забрал supplier stock, выбранный construction route не получает зарезервированный товар из воздуха.

`route selected != guaranteed future transaction`.

### E8 — competing suppliers / stale demand

Два trader выбирают один и тот же demand.

Первый прибывает и закрывает большую часть или весь requirement. Второй при прибытии обязан повторно проверить market state:

- продаёт только фактически оставшийся demand;
- при нулевом demand не получает виртуальную выплату;
- лишний cargo остаётся на корабле;
- AI перепланирует его обычным способом.

### E9 — route-risk utility

Внешний trader оценивает construction opportunity с учётом cumulative whole-route risk, а не только destination buy price.

При достаточно опасном промежуточном маршруте более прибыльная номинально стройплощадка может быть отвергнута в пользу более безопасной сделки.

### E10 — BUILDING waits for physical delivery

Наличие buy order или перевозчика `en route` не считается fulfillment.

```text
order exists         → недостаточно
cargo in transit     → недостаточно
cargo in site stock  → считается delivered
```

Project не может перейти в `BUILDING`, пока реальный site inventory/material state не закрывает весь bill.

### E11 — save/load open demand

Save/load сохраняет:

- delivered materials;
- remaining requirements;
- site wallet;
- market demand, выводимый из authoritative state;
- отсутствие дублированных orders/resources после restore.

### E12 — construction creates regional economic pressure

В интеграционном сценарии крупный project должен создавать обычный дополнительный спрос, который видят существующие trade planners. Тест не обязан фиксировать конкретную цену как balance constant, но должен доказать, что construction demand входит в общий economic opportunity space и конкурирует за реальные материалы с другими покупателями.

---

## 7. Группа F — owned fleet supply order

Если `SUPPLY_PROJECT` входит в Stage-16 release:

### F1 — deterministic supplier selection

При одинаковом world state выбирается один и тот же supplier/route.

### F2 — cumulative risk

Supply fleet использует тот же Stage-15 whole-route risk planner; опасный промежуточный сегмент может изменить route.

### F3 — physical cargo

Order не может завершить delivery без фактического cargo в FleetId inventory.

### F4 — stale opportunity

Если supplier stock исчез, order replans/aborts deterministic образом без virtual purchase.

### F5 — save/load continuation

Persistent supply order продолжает тот же project после загрузки.

### F6 — owner delivery does not require self-sale

Owned fleet может передать физический cargo в собственный project через owner-delivery boundary без искусственной рыночной продажи самому себе и без создания денег.

---

## 8. Группа G — construction lifecycle/time

### G1 — materials incomplete

Project остаётся `AWAITING_MATERIALS`; progress BUILDING не растёт.

### G2 — fulfillment starts build

После полного material bill следующий deterministic lifecycle transition переводит project в `BUILDING`.

### G3 — duration policy

`buildDurationTicks` соответствует `ConstructionDurationPolicy` на момент создания.

### G4 — fixed-time determinism

Разное разбиение render delta даёт одинаковый completion tick.

### G5 — remote construction

Player покидает систему. Remote coarse simulation продолжает project до корректного completion tick в пределах scheduler semantics.

### G6 — save/load mid-build

Сохраняются:

- status;
- buildStartedTick;
- buildDurationTicks;
- material state;
- site identity.

После загрузки completion происходит без перерасчёта duration.

---

## 9. Группа H — completion и station economy

### H1 — ordinary entity completion

После completion:

- construction site исчезает;
- создаётся ровно одна station entity нужного archetype;
- `ConstructionProjectState.completedStationEntityId` указывает на неё;
- PlayerState получает ownership именно этого EntityId.

### H2 — material sink conservation

Все consumed construction materials фиксируются как `RESOURCE_SINK` с construction reason; нет двойного списания.

### H3 — operating wallet

Остаток project wallet обрабатывается согласно explicit settlement policy; деньги не исчезают.

### H4 — player deposit

Player → station transfer сохраняет общую сумму денег.

### H5 — player withdraw

Station → player transfer не может превысить station balance и сохраняет общую сумму денег.

### H6 — ordinary economy

Player-owned station принимает участие в существующем market/production/trade loop без player-only passive-income path.

---

## 10. Группа I — cancellation/failure

### I1 — cancel before materials

- project → CANCELLED;
- site удаляется;
- remaining wallet возвращается владельцу;
- денег/ресурсов не теряется.

### I2 — cancel after partial delivery

После реализации material-fate policy доставленные материалы становятся физически recoverable и не удаляются скрыто.

Фактическая Stage-16 реализация сохраняет более консервативную границу: если required materials уже физически доставлены, voluntary cancellation отклоняется до появления такой policy. Это зафиксировано в `docs/stage16_completion_record.md`.

### I3 — cancel during BUILDING baseline

До появления salvage-by-progress добровольная отмена BUILDING отклоняется явной причиной.

### I4 — destroyed site

- destruction pipeline уничтожает site;
- project → FAILED;
- automatic refund/respawn отсутствует;
- возможный salvage создаётся только обычной destruction logic.

---

## 11. Группа J — end-to-end Stage 16 acceptance

Один deterministic интеграционный сценарий должен выполнить минимум:

```text
обычный player runtime
→ выбрать station archetype
→ создать site в допустимой точке
→ fund из player wallet
→ site публикует реальный спрос на недостающие materials
→ внешний trader другой faction с market access выбирает выгодную поставку
→ trader физически покупает и доставляет часть materials обычным TradeController
→ игрок покупает ещё часть materials на обычном рынке
→ физически перевозит owned FleetId
→ передаёт cargo в range
→ остальную поставку выполняет ordinary economy или owned supply order
→ дождаться полного material bill
→ начать BUILDING
→ уйти в другую систему
→ сохранить игру
→ загрузить
→ продолжить simulation
→ завершить строительство
→ получить physical owned station
→ внести деньги на станцию
→ дождаться обычной экономической активности
→ вывести часть денег
→ сохранить/загрузить
→ уничтожить станцию ordinary destruction path
→ проверить удаление ownership
```

Во всём сценарии запрещены:

- debug money/resource grants после начальной фикстуры;
- instant delivery;
- station spawn до completion;
- UI mutation;
- отключение конкурирующей экономики ради успеха теста;
- artificial supplier reservation ради гарантированного успеха construction trade.

Финальный aggregate acceptance реализован в `Stage16EndToEndAcceptanceTest` и прошёл в CI #1337.

---

## 12. CI gate Stage 16

Перед объявлением Stage 16 COMPLETE обязательны:

- все unit/integration/acceptance tests green;
- strict Javadoc green;
- JaCoCo gates green;
- desktop shaded JAR packaging green;
- persistence migration tests green;
- deterministic construction continuation green;
- минимум один multi-system/end-to-end player construction acceptance green;
- минимум один внешний supplier другой faction/independent actor успешно снабжает construction site через обычную рыночную экономику;
- минимум один hostile/blocked supplier корректно отклоняется обычным access policy.

### Финальный результат

**PASS.** PR #70 / CI #1337 / run `31849675260`: **484/484 tests**, strict Javadoc, JaCoCo и shaded desktop JAR build green. Artifact publication столкнулась с GitHub storage quota, но не является blocking gate по `docs/ci_artifact_publication_policy.md`.
