# Stage 17F.6 — fiscal anti-oscillation controller

## Scope

Этот slice подключает общий persistent policy-review lifecycle к первой реальной policy family: fiscal policy.

Controller меняет только:

- `stationTaxBasisPoints`;
- `maxLiquiditySupportPerDecisionMilliCredits`.

Остальные fiscal поля сохраняются без изменения. Налоги и subsidies не исполняются самим reviewer-ом: физические деньги по-прежнему двигаются только существующими `applyFiscalPolicy(...)` и `applyLiquiditySupport(...)`.

## Physical signal

Используется только уже существующий read-only `FactionFiscalPositionAnalyzer`:

```text
liquidityShortfallMilliCredits / liquidityReserveTargetMilliCredits
→ liquidity shortfall basis points
```

Нет synthetic prosperity/health score и нет отдельного fictitious budget.

## Deadband

`FactionFiscalReviewProfile` задаёт два порога:

```text
shortfall >= enter → STRESS
shortfall <= exit   → NORMAL
exit < shortfall < enter → DEADBAND
```

В `DEADBAND` review-window может быть claim-нут, но fiscal policy не переписывается. Это предотвращает реакцию на малый шум около границы.

## Bounded adjustment

После due review:

- station tax двигается к normal/stress target не больше `maxStationTaxStepBasisPoints`;
- liquidity-support authorization двигается к normal/stress target не больше `maxLiquiditySupportCapStepMilliCredits`.

Authorization не является отдельным денежным счётом. Даже большой support cap ничего не создаёт: фактический transfer всё равно ограничен реальным treasury balance, treasury reserve floor и реальным station shortfall.

## Causal contract

```text
real station wallets + current reserve target
→ FactionFiscalPositionDiagnostics
→ shortfall ratio
→ NORMAL / DEADBAND / STRESS
→ common policy-review cadence claim
→ one bounded fiscal-policy step
→ later ordinary tax/support execution
→ real conserved money transfers
```

## Acceptance

`Stage17F6FiscalAntiOscillationAcceptanceTest` должен доказывать:

1. sustained stress не может менять fiscal policy повторно в том же review-window;
2. следующий due window разрешает только ещё один bounded step;
3. deadband claim-ит review, но не меняет policy;
4. recovered liquidity двигает policy обратно к normal targets только bounded step;
5. reviewer не меняет treasury или station wallet totals;
6. foreign territorial levy, treasury reserve floor, station reserve target и construction investment cap сохраняются без изменений.

## Follow-up

Следующий 17F.6 slice после этого controller-а должен решить profile selection для AI factions из persistent doctrine и measured fiscal context, а затем аналогично подключить cadence/hysteresis к stock/resilience policy families. Сам reviewer не должен содержать faction-specific magic numbers.
