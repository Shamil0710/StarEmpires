# Stage 17F.6 — doctrine-driven fiscal review profiles

## Scope

Этот slice отделяет **институциональный prior** faction от **текущего экономического сигнала**.

- persistent `FactionDoctrineState` определяет форму fiscal response;
- real `FactionFiscalPositionDiagnostics` по-прежнему определяет текущий liquidity stress;
- `FactionFiscalPolicyReviewer` остаётся cadence/deadband actuator и в этом slice не меняется.

Никаких исключений по faction ID, display name или runtime ID нет.

## Pure mapping

`FactionFiscalReviewProfileSelector` — чистая deterministic функция:

```text
persistent doctrine
+ aggregate owned-market liquidity reserve target
→ FactionFiscalReviewProfile
```

Doctrine axes используются монотонно:

- `tradeOpenness ↑` → normal own-station tax target ↓;
- `expansionPreference ↑` → normal own-station tax target умеренно ↑;
- `economicResiliencePriority ↑` → stress reaction начинается раньше, stress tax relief глубже, liquidity-support envelope больше;
- `interventionism ↑` → maximum bounded adjustment per review больше.

`securityPosture`, `sovereigntySensitivity` и `treatyLegalism` в этом узком fiscal profile не используются: они остаются для security/diplomatic policy families и не должны искусственно влиять на fiscal actuator без причинной модели.

## Explicit integer mapping

Normal own-station tax:

```text
1000 bps
- 8 bps × (tradeOpenness - 50)
+ 4 bps × (expansionPreference - 50)
clamped to 400..1600 bps
```

Liquidity stress:

```text
enter = 5000 - 30 × economicResiliencePriority
exit  = max(500, enter / 3)
```

Stress tax relief:

```text
200 + 4 × economicResiliencePriority bps
```

Maximum tax adjustment:

```text
100 + 2 × interventionism bps per claimed review
```

Liquidity-support authorization targets are fractions of the real aggregate station reserve target:

```text
normal support = (1000 + 10 × resilience) bps of reserve target
stress support = (4000 + 40 × resilience) bps of reserve target
max step       = ( 500 +  5 × interventionism) bps of reserve target
```

Money scaling uses integer `BigInteger` arithmetic to avoid overflow. These values are only authorizations/targets; they are not a second treasury and do not move money.

## World adapter

`WorldFactionFiscalReviewProfileSelector.select(...)` reads:

1. the faction's persistent doctrine from strategic state;
2. the aggregate liquidity reserve target from `FactionFiscalPositionAnalyzer`;
3. delegates to the pure selector.

It does **not** read current liquidity shortfall and does not mutate world state.

This separation is intentional:

```text
doctrine → how the institution is willing to react
real wallet shortfall → whether NORMAL / DEADBAND / STRESS applies now
cadence watermark → whether a review may occur now
reviewer → one bounded policy step
ordinary fiscal executors → real conserved money transfers later
```

## Acceptance

The slice is accepted when tests prove:

1. identical doctrine/scale always produces the identical profile;
2. each used doctrine axis moves only its intended response dimension monotonically;
3. zero and `Long.MAX_VALUE` reserve scales remain valid without overflow;
4. every authored faction passes through the same world selector;
5. identical doctrine on different faction IDs produces identical non-monetary response shape;
6. profile selection is byte-for-byte read-only against encoded world state.

## Follow-up

The next Stage-17F.6 slice should connect this selector to the actual automated faction review coordinator and then extend the same cadence/hysteresis discipline to stock/resilience policy families. Automatic scheduling should remain bounded by authoritative strategic time and must not run policy reviews every simulation frame.
