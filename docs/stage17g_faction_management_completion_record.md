# Stage 17G — faction management / strategic global-map authority — completion record

Статус: **COMPLETE candidate for PR #133**. Канонический статус становится **COMPLETE** после merge exact green PR head и успешного post-merge CI на новом `main`.

## Назначение

Stage 17G добавляет application/read-model слой управления собственной фракцией поверх уже существующих authoritative Stage-17 систем. Он не создаёт вторую экономику, дипломатию, территориальную механику или отдельные player-only правила.

Базовый contract:

```text
authoritative PlayerState + WorldSimulation
→ immutable management/global-map projection
→ player intent
→ common Stage-17 command boundary
→ ordinary treasury / diplomacy / territory / policy systems
→ changed authoritative state
→ next read-only projection
```

## 17G.1 — immutable faction-management projection

Добавлены:

- `FactionManagementSnapshot`;
- `FactionManagementModel`.

Projection включает:

- explicit independent/affiliated state;
- personal wallet;
- faction economy/treasury;
- doctrine;
- fiscal policy;
- base stock/production policy;
- automatic resilience demand overlay;
- player-owned physical fleets;
- player construction projects and completed owned stations;
- territorial legal views только для открытых игроком StarSystem;
- own persistent diplomacy;
- deterministic counterparty summaries с effective market-access decisions;
- persistent strategic growth/expansion plans.

Read model не двигает simulation clock, не меняет wallets/cargo/policy/diplomacy/territory и не раскрывает неизвестные игроку системы как новый omniscient sensor channel.

## 17G.2 — common player faction management commands

Добавлен `PlayerFactionManagementService` как тонкий player-facing facade.

Он делегирует в уже существующие authoritative boundaries:

- personal ↔ faction treasury — `PlayerFactionTreasuryRuntimeService`;
- affiliation существующих fleets/stations — `PlayerFactionAssetAffiliationService`;
- doctrine/fiscal/stock-production/apply — common `FactionPolicyCommand` / `FactionPolicyCommandExecutor` path;
- treaty lifecycle — `DiplomaticTreatyCommand`;
- unilateral embargo — `DiplomaticEmbargoCommand`;
- claim/withdraw/relinquish/recognition/construction concession — ordinary territorial-law APIs.

Facade блокирует:

- faction authority для independent player;
- actor impersonation в diplomatic commands;
- неизвестную/self target faction там, где требуется foreign target.

### Tariff boundary

В существующей модели tariff разделён на два разных domain concepts:

1. own-station tax + foreign-territory levy — часть `FactionFiscalPolicyState`, редактируется common policy command;
2. ordinary customs tariff / treaty customs exemption — persistent diplomacy/transaction law.

17G не вводит private player-only customs setter. Management projection показывает authoritative customs/access state, а изменения legal instrument должны идти только через shared diplomacy/policy boundary.

## 17G.3 — strategic global-map composition

Добавлены:

- `FactionGlobalMapSnapshot`;
- `FactionGlobalMapModel`.

Они композируют существующий non-omniscient `GlobalFleetMapSnapshot` с faction-management projection. `GlobalFleetMapRenderer` остаётся thin presentation-only renderer и не получает `WorldSimulation` или mutation callbacks.

## 17G.4 — acceptance

Добавлены:

- `Stage17G1FactionManagementReadModelAcceptanceTest`;
- `Stage17G2FactionManagementCommandsAcceptanceTest`.

Aggregate acceptance доказывает:

1. independent player сохраняет owned Stage-16 assets, но не получает faction authority;
2. repeated read-model capture deterministic и mutation-free;
3. affiliated projection совпадает с authoritative economy/policy/diplomacy/territory/growth state;
4. territory отображается только через ordinary legal assessment для known systems;
5. asset affiliation сохраняет persistent `FleetId` и placement;
6. capitalization является точным zero-sum personal→treasury transfer;
7. doctrine и fiscal/tariff policy идут через common policy command;
8. treaty/embargo идут через common diplomacy boundary;
9. player facade отвергает impersonation;
10. новый claim начинается без fabricated stabilization и не даёт instant sovereignty;
11. management operations не создают/удаляют cargo и не меняют total money;
12. save/load сохраняет economy, doctrine, fiscal/stock policy, diplomacy, territory, owned fleet projection и physical totals.

## Roadmap synchronization

`docs/development_roadmap.md` переведён в актуальный authoritative status/dependency roadmap.

Предыдущая подробная версия сохранена byte-for-byte по исходному blob в:

- `docs/archive/development_roadmap_pre_stage17g_2026-08-16.md`.

Поэтому историческая детализация не потеряна, но stale-статус `17F.6 NEXT` больше не является каноническим.

## CI evidence перед финальным documentation closeout

На implementation head `3986b6c64cb132e6622111eb22b5da45fc3b1349` успешно прошли оба exact-head запуска полного Java-17 verification:

- push CI run #2252 (`31936387436`) — SUCCESS;
- PR CI run #2253 (`31936390365`) — SUCCESS.

После добавления этого completion record и финального roadmap-status commit требуется новый exact-head PR CI; merge разрешён только для этого окончательного green SHA.

## Transition

Следующий slice после merge Stage 17G: **17H — persistence / migration / Stage-17 end-to-end acceptance**.

Stage 17.5 остаётся **BLOCKED**, пока 17H и финальный Stage-17 transition gate не пройдены. После Stage 17 COMPLETE первый implementation slice остаётся **17.5A — schema/material/hull/module**.
