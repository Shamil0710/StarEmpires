# Stage 17F.6 — policy feedback / anti-oscillation — completion record

Статус: **COMPLETE**.

Stage 17F.6 закрыт после merge PR #131 и успешного post-merge CI на `main` commit `83c44d5dca9e450dd2c1076d498a02ed9b68e870` (CI run #2217).

## Реализованный causal contract

```text
authoritative treasury / station liquidity / structural dependence
→ doctrine-backed review profile
→ deterministic bounded review cadence
→ one common review-window claim per faction
→ bounded fiscal + automatic resilience adjustment
→ explicit ordinary strategic-policy apply where physical configuration is required
→ wait observation window
→ measure the changed physical world again
```

Policy review не является every-tick controller. Один и тот же review window нельзя повторно использовать после повторного вызова или save/load.

## Реализованные slices

- PR #122 — persistent review cadence/hysteresis foundation;
- PR #123 — bounded fiscal anti-oscillation;
- PR #124 — doctrine-driven fiscal review profile selection;
- PR #126 — common multi-policy review coordinator;
- PR #127 — stock/resilience review в том же cadence window;
- PR #128 — reversible configured/effective market-demand provenance;
- PR #129 — automatic resilience demand как отдельный reversible strategic overlay;
- PR #130 — production-policy isolation: autonomous review не переключает recipe и не сбрасывает production progress;
- PR #131 — aggregate long-horizon anti-oscillation/save-load/determinism gate.

## Закрытые инварианты

1. **Determinism.** Идентичный authoritative state и одинаковый набор разрешённых autonomous factions дают одинаковый review result.
2. **Explicit scope.** Coordinator не включает player faction или другую faction неявно; caller задаёт разрешённый набор явно.
3. **Shared cadence.** Fiscal и resilience policy используют один persistent review watermark, а не независимые окна.
4. **Bounded response.** Fiscal policy движется ограниченными шагами к doctrine-backed target; resilience overlay ограничен отдельными шагами вверх/вниз и может полностью освобождаться при recovery.
5. **No authoring physics.** Review authoring не двигает cargo, wallets, treasury, production output или effective market targets.
6. **Explicit materialization.** Strategic demand/production authoring влияет на ordinary ECS configuration только через явный `applyFactionStrategicPolicy(...)`.
7. **Reversible demand.** Temporary resilience demand не загрязняет independent base stock policy; после recovery effective target возвращается к оставшемуся base/configured demand.
8. **No recipe oscillation.** В текущем autonomous review loop отсутствует automatic recipe selector; review не retool-ит production. Любой будущий automatic recipe selector обязан сначала получить dwell/deadband/hysteresis contract.
9. **Persistence.** Save/load сохраняет review watermark, fiscal policy, base stock policy, resilience overlay и configured/effective market-demand provenance.
10. **Conservation.** Policy feedback не создаёт деньги или ресурсы; последствия реализуются существующими market/logistics/production/treasury systems.

## Aggregate acceptance

Финальный PR #131 проверяет в одном сценарии:

```text
stress
→ bounded fiscal + resilience review
→ duplicate same-window call rejected
→ explicit strategic apply
→ next observation reacts to changed world state within bounded +10/-5 resilience limits
→ save/load
→ immediate duplicate claim rejected
→ repeated recovery windows
→ overlay released completely
→ effective demand falls back to independent base stock floor
```

Также проверяются deterministic twin-state, deduplication caller input, неизменность review state невыбранных factions и физическая чистота authoring.

## Transition

Следующий Stage-17 slice: **17F.7 — player/AI policy-command parity**. Stage 17.5 остаётся заблокирован до полного завершения Stage 17 и прохождения Stage-17 transition gate.
