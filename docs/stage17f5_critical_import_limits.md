# Stage 17F.5 — critical-item import limits

## Scope

Этот slice вводит **hard autonomous procurement guard** для структурно критичных импортов фракции.

Он не является:

- дипломатическим embargo;
- customs tariff;
- запретом ручной торговли игрока;
- исторической квотой по доле импорта;
- источником товара, денег или production capacity.

## Authoritative signal

Simulation пока не хранит историческую provenance каждого импортированного юнита. Поэтому policy не выдумывает cumulative import share.

Используются только уже существующие current-snapshot Stage-17E/17F diagnostics:

```text
physical stock / target / accessible foreign surplus
→ uncovered requirement after partner loss
→ resilience doctrine
→ local-production recommendation
→ preferred maximum partner supply share
```

Hard limit активируется только если commodity уже считается достаточно уязвимым для `localProductionRecommended`.

## Procurement semantics

Для inbound autonomous procurement в собственный market faction:

```text
real discovered supplier
→ measure current structural partner supply share
→ compare with doctrine-derived concentration ceiling
→ authorize / reject candidate
→ ordinary TradeRoutePlanner scores only authorized physical routes
→ ordinary InterSystemTradeJob executes the selected route
```

Domestic supplier не ограничивается этой policy.

Trade к foreign consumer не является собственным critical import и не фильтруется.

Если все foreign suppliers критического товара превышают ceiling, новый procurement route не создаётся. Дефицит остаётся физическим и должен разрешаться обычными механизмами: buffers, менее концентрированный supplier, domestic production, funded construction или последующее изменение policy/doctrine.

## Economic consequence

Policy не начисляет flat penalty. Цена resilience возникает физически:

- более дорогая закупка;
- менее выгодный маршрут;
- возможный shortage;
- необходимость domestic capacity;
- capital/logistics cost уже существующего resilience response.

## Compatibility

`FactionResilientGalacticTradePlanner` сохраняет source-compatible constructors: legacy callers получают `CriticalImportLimitPolicy.none()` и прежнее поведение.
