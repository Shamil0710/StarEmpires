# Stage 17F.6 — policy review / anti-oscillation foundation

## Scope

Этот slice вводит общий lifecycle, который ограничивает **когда** faction AI имеет право пересматривать strategic policy.

Он намеренно не выбирает конкретные новые tax rates, stock floors, recipes или resilience settings. Эти decision planners подключаются следующими slices через уже существующие Stage-17F command boundaries.

## Persistent review watermark

Каждая persistent faction economy хранит только:

```text
lastPolicyReviewTick
```

`-1` означает, что automatic policy review ещё не выполнялся.

Это не hidden utility score и не новый источник экономической истины. Предыдущие policy values остаются authoritative memory принятого решения.

File format v8 добавляет отдельный bounded `WorldPolicyReviewBinary` trailer. Saves v1-v7 мигрируют в `FactionPolicyReviewState.INITIAL`.

## Deterministic cadence

`FactionPolicyReviewCadence` использует только authoritative world tick.

Первый review может быть deterministic staggered по stable faction ID, чтобы все factions не принимали решения на одном tick. После claim новый review запрещён до полного `intervalTicks`.

```text
stable faction ID
→ deterministic first-review phase
→ authoritative world tick
→ due / not due
→ persisted claim tick
```

Нет wall-clock time, RNG или frame-rate dependence.

## Common runtime boundary

`WorldSimulation.tryBeginFactionPolicyReview(...)` атомарно claim-ит due review window.

Сам claim:

- не меняет treasury;
- не меняет tax/tariff;
- не меняет stock floors;
- не переключает recipe;
- не создаёт cargo/output;
- не применяет resilience action.

Он только предотвращает второй bounded policy step в том же observation window, включая save/load continuation.

## Hysteresis primitives

`FactionPolicyHysteresis.binaryDecision(...)` реализует Schmitt-trigger deadband:

```text
inactive + signal < enter  → remain inactive
inactive + signal >= enter → activate
active + signal > exit      → remain active
active + signal <= exit     → deactivate
```

`exit <= enter`, поэтому небольшой шум внутри deadband не вызывает oscillation.

`boundedBasisPointStep(...)` ограничивает numeric policy adjustment одним максимальным шагом за claimed review.

## Acceptance target

Foundation считается готовым, когда CI подтверждает:

1. одинаковый faction ID всегда получает одинаковый cadence phase;
2. второй claim в том же review window невозможен;
3. save/load не выдаёт повторный review на том же authoritative tick;
4. v7 save мигрирует в never-reviewed lifecycle;
5. claim не меняет economic/policy state кроме review watermark;
6. hysteresis удерживает binary policy внутри deadband;
7. numeric policy не может изменить значение больше разрешённого bounded step за один review.

## Next slice

Следующий Stage-17F.6 slice должен подключить common review lifecycle к одному реальному policy family — предпочтительно fiscal policy — и доказать на oscillating signal, что tax/liquidity settings не меняются every tick, а только после due review и выхода за hysteresis threshold.
