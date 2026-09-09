# StarEmpires — M22.6 Human Review Runner

Этот runner предназначен для выполнения оставшихся **human-only** гейтов M22.6 на точном frozen release candidate и не должен изменять frozen-ветку.

## Frozen identity

- build SHA: `5fb4c523cf3d169677c602638b4f62726b915272`
- freeze manifest: `stage22.core_pair_freeze_manifest.v3`
- freeze fingerprint: `6705d39d21d234335d55a33d22460e6750941cf8a57719c24b88c1e4a659d6d4`
- source branch: `stage22-6-core-pair-balance-freeze`
- tools branch: `stage22-6-human-review-tools`

Runner создаёт отдельный detached worktree примерно по пути:

`..\StarEmpires-M22.6-review-5fb4c523`

Поэтому текущая рабочая ветка основного клона не переключается и exact RC остаётся отдельным.

## Как подтянуть из GitHub

Находясь в вашем обычном локальном клоне `StarEmpires`, выполните:

```bat
git fetch origin stage22-6-human-review-tools
git worktree add ..\StarEmpires-M22.6-tools origin/stage22-6-human-review-tools
```

После этого запустите двойным кликом:

`..\StarEmpires-M22.6-tools\M22_6_Human_Review_Runner.bat`

Это рекомендуемый вариант: он не требует переключать текущую ветку и не добавляет runner в frozen worktree.

Если вспомогательный worktree `StarEmpires-M22.6-tools` уже существует, не создавайте его повторно. Для обновления сначала выполните `git fetch`, затем при необходимости пересоздайте только вспомогательный tools-worktree. Exact RC worktree `StarEmpires-M22.6-review-5fb4c523` удалять не требуется.

## Требования

- Windows 10/11;
- Git for Windows в `PATH`;
- JDK 17 / `java` в `PATH`;
- локальный клон `StarEmpires`;
- доступ к Maven-зависимостям, если их ещё нет в локальном кеше.

## Меню BAT

### [1] Prepare / verify exact frozen RC worktree

Проверяет наличие exact commit, при необходимости делает fetch `stage22-6-core-pair-balance-freeze`, создаёт detached worktree, проверяет HEAD и чистоту дерева.

### [2] Run quick M22.6 machine preflight

Запускает связанные Stage-22 acceptance/fingerprint/visual tests. Результат сохраняется в `machine_preflight_result.txt`.

**Важно:** это только machine preflight. Он не считается человеческим B18/B19/B20 evidence.

### [3] Run full clean verify

Локально выполняет:

```bat
mvnw.cmd --batch-mode --no-transfer-progress clean verify
```

Результат сохраняется в `local_clean_verify_result.txt`. Уже пройденный GitHub CI остаётся каноническим machine evidence; локальный прогон нужен для проверки вашей машины.

### [4] B18: open instructions and launch generated-world client

Открывает canonical human-review runbook и `b18_responses.csv`, затем просит seed и запускает `run-generated-world.bat` из exact RC.

Формальный B18 требует заранее подготовленного blinded task packet и скрытого answer key. Сам свободный запуск клиента без такого пакета не является B18 sign-off.

### [5] Open canonical B18-B20 human-review runbook

Открывает:

`docs/factions/stage22_m22_6_human_review_runbook.md`

Именно этот документ является канонической инструкцией для человеческих B18–B20.

### [6] Open evidence folder and response CSV files

Runner создаёт вне frozen worktree папку:

`%USERPROFILE%\Documents\StarEmpires-M22.6-HumanReview-5fb4c523`

В ней находятся:

- `review_identity.txt`
- `b18_responses.csv`
- `b19_responses.csv`
- `b20_responses.csv`
- `machine_preflight_result.txt` после пункта 2
- `local_clean_verify_result.txt` после пункта 3

### [7] Show exact RC identity/status

Повторно проверяет exact SHA и показывает статус detached RC worktree.

## Перед human review

Заполните в `review_identity.txt` как минимум:

- `scenarioSuiteVersion`
- `reviewPacketVersion`
- `reviewerAnonymousId`
- `reviewStartedAtUtc`

После завершения заполните `reviewCompletedAtUtc`.

Не удаляйте неудачные или исключённые строки из response CSV. Если ответ исключается, оставьте строку и укажите `exclusionReason`.

## B18 — причинно-следственное объяснение

Порог: **accuracy >= 80%**.

До предъявления задачи должен быть зафиксирован скрытый answer-key row с `taskId`, `scenarioId`, `seed`, checkpoint refs и `primaryDependencyId`. Ревьюер видит обычный player-facing UI и фиксирует ответ до раскрытия ключа.

## B19 — grayscale ship blind review

Пороги одновременно:

- faction accuracy **>= 90%**;
- role-family accuracy **>= 80%**.

Нужны реальные production RC ship visuals. Для blind packet убираются цвет, названия, role/faction labels, heraldry text и UI labels, но силуэт не перерисовывается. Правильные ответы не раскрываются до завершения пакета.

Автоматический asset/alpha-mask test — только preflight и не входит в human denominator.

## B20 — shared Character Master Prompt review

Порог: **sharedStyleAccuracy >= 90%**.

Используются фактические character samples, предназначенные для RC visual manifest. Ревьюер оценивает shared style lock вручную: hand-painted 2D RPG illustration, non-photorealistic/non-3D rendering, restrained ink-and-paint linework, muted opaque watercolor/gouache-like treatment, limited shading и human-made imperfection.

Assistant self-review, prompt inspection или machine classification не считаются человеческим B20 judgment.

## После review

Сохраните всю папку `StarEmpires-M22.6-HumanReview-5fb4c523` и передайте её для проверки. На уже записанных человеческих ответах можно автоматически посчитать numerator/denominator, проверить пороги и сформировать `summary.json`, но нельзя генерировать ответы за ревьюера.

До подтверждения B18, B19 и B20 M22.6 остаётся открытой и frozen PR не должен быть merged.
