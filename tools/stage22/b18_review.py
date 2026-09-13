#!/usr/bin/env python3
"""Prepare and score the M22.6 B18 human causal-review packet.

The tool is deliberately deterministic and fail-closed. It packages predeclared player-facing
observations and a hidden answer key; it never invents or semantically infers a reviewer's answer.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
from pathlib import Path

PACKET_VERSION = "stage22.m22_6.b18.v1"
SCENARIO_SUITE_VERSION = "stage22.core_pair.B00-B20.v1"
FREEZE_MANIFEST_VERSION = "stage22.core_pair_freeze_manifest.v3"
EXPECTED_FREEZE_FINGERPRINT = "6705d39d21d234335d55a33d22460e6750941cf8a57719c24b88c1e4a659d6d4"
BUILD_SHA_RE = re.compile(r"^[0-9a-f]{40}$")
PASS_THRESHOLD = 0.80

TASKS = (
    {
        "taskId": "B18-01", "scenarioId": "B04", "title": "Производство модуля отклонено",
        "evidence": (
            "Физическое хранилище станции: heavy components = 0 кг.",
            "Сборочная линия доступна; manufacture status = INSUFFICIENT_INPUT.",
            "После физической доставки heavy components тот же заказ производится успешно.",
        ),
        "question": "Какова главная непосредственная причина первого отказа производства?",
        "choices": (
            ("A", "На целевой станции отсутствует обязательный тяжёлый компонент."),
            ("B", "Сборочная линия выключена или разрушена."),
            ("C", "Выходное хранилище уже переполнено."),
            ("D", "У фракции действует скрытый штраф к производству."),
        ),
        "dependency": "critical_input_missing", "correct": "A",
        "equivalents": ("critical_input_missing", "отсутствует обязательный тяжёлый компонент", "нет heavy components"),
    },
    {
        "taskId": "B18-02", "scenarioId": "B05", "title": "Груз на одном плече маршрута не доставлен",
        "evidence": (
            "Один физический транспорт уничтожен в пути.",
            "Второй маршрут и его транспорт остаются рабочими.",
            "Потерянная масса груза учтена отдельно; автоматического salvage/reroute нет.",
        ),
        "question": "Какова главная причина недоставки груза на потерянном плече?",
        "choices": (
            ("A", "Получатель отказался принять груз из-за цены."),
            ("B", "Физический транспорт с этим грузом был потерян."),
            ("C", "Вся сеть маршрутов автоматически отключилась."),
            ("D", "Груз исчез из-за округления инвентаря."),
        ),
        "dependency": "physical_freighter_loss", "correct": "B",
        "equivalents": ("physical_freighter_loss", "потерян физический транспорт", "транспорт с грузом уничтожен"),
    },
    {
        "taskId": "B18-03", "scenarioId": "B11", "title": "После повреждения сети исчезли контакты",
        "evidence": (
            "Локальный radar уже отказал.",
            "При целой datalink-сети цель видна через измерение союзного корабля.",
            "После разрушения receiver datalink mount число вражеских контактов становится 0.",
        ),
        "question": "Какое новое изменение непосредственно убрало контакт с целью?",
        "choices": (
            ("A", "Разрушен приёмный узел datalink, по которому приходил союзный трек."),
            ("B", "Противник получил скрытый бонус к маскировке."),
            ("C", "У корабля закончилась реактивная масса."),
            ("D", "Сработал глобальный лимит числа целей."),
        ),
        "dependency": "receiver_datalink_severed", "correct": "A",
        "equivalents": ("receiver_datalink_severed", "разрушен приёмный datalink", "оборван приёмный канал datalink"),
    },
    {
        "taskId": "B18-04", "scenarioId": "B12", "title": "Перезарядка магазина отклонена",
        "evidence": (
            "Боеприпасы корабля = 0.",
            "Физическое хранилище станции: нужных ammunition products = 0.",
            "Reload status = INSUFFICIENT_STOCK; отклонённая операция не меняет магазин и склад.",
        ),
        "question": "Почему корабль не смог пополнить магазин?",
        "choices": (
            ("A", "Пусковая установка навсегда несовместима с этим типом боеприпаса."),
            ("B", "На станции нет требуемого счётного запаса боеприпасов."),
            ("C", "Фракция исчерпала абстрактный боевой ресурс."),
            ("D", "Включён запрет на пополнение после боя."),
        ),
        "dependency": "ammunition_stock_absent", "correct": "B",
        "equivalents": ("ammunition_stock_absent", "на станции нет боеприпасов", "нулевой запас боеприпасов"),
    },
    {
        "taskId": "B18-05", "scenarioId": "B13", "title": "Повреждение сохраняется между боями",
        "evidence": (
            "Структурная целостность после боя ниже стартовой.",
            "Следующий контакт начинается с того же повреждённого committed state.",
            "Между контактами не зарегистрировано repair action.",
        ),
        "question": "Почему структура не восстановилась сама перед следующим контактом?",
        "choices": (
            ("A", "Повреждение сохраняется, пока обычная repair authority не выполнит ремонт."),
            ("B", "Сохранение игры случайно уменьшает целостность."),
            ("C", "У второй фракции есть скрытый постоянный debuff."),
            ("D", "Каждый новый бой принудительно ставит структуру на 50%."),
        ),
        "dependency": "damage_persists_without_repair", "correct": "A",
        "equivalents": ("damage_persists_without_repair", "ремонт не был выполнен", "повреждение сохраняется без ремонта"),
    },
    {
        "taskId": "B18-06", "scenarioId": "B14", "title": "Замена потерянного корабля не появляется мгновенно",
        "evidence": (
            "Верфь должна получить конечный hull/module stock.",
            "Строительство требует положительное yard work time.",
            "Новая единица получает новый FleetId только после оплаченного replacement cycle.",
        ),
        "question": "Какая зависимость не позволяет мгновенно материализовать замену?",
        "choices": (
            ("A", "Нужно физически оплатить материалы и конечную работу верфи."),
            ("B", "Нужно дождаться случайного глобального таймера фракции."),
            ("C", "Нужно повысить скрытый показатель морали."),
            ("D", "Замена всегда запрещена после первой потери."),
        ),
        "dependency": "paid_replacement_work_and_materials", "correct": "A",
        "equivalents": ("paid_replacement_work_and_materials", "нужны материалы и работа верфи", "не оплачены материалы и работа верфи"),
    },
    {
        "taskId": "B18-07", "scenarioId": "B16", "title": "Торговая покупка перестала проходить",
        "evidence": (
            "До события active mutual MARKET_ACCESS позволяет обычную покупку.",
            "Treaty state становится BREACHED.",
            "Та же покупка отклоняется без изменения денег, склада и ledger; новый accepted agreement возвращает торговлю.",
        ),
        "question": "Какова главная причина нулевого торгового объёма сразу после события?",
        "choices": (
            ("A", "На станции закончился товар."),
            ("B", "Покупатель обанкротился."),
            ("C", "Нарушение договора убрало разрешённый market access."),
            ("D", "Торговый контроллер случайно потерял ledger."),
        ),
        "dependency": "market_access_treaty_breached", "correct": "C",
        "equivalents": ("market_access_treaty_breached", "нарушение договора убрало market access", "treaty breached"),
    },
    {
        "taskId": "B18-08", "scenarioId": "B17", "title": "Переналадка ещё не дала новый выпуск",
        "evidence": (
            "AI выбрал допустимую альтернативную production policy из наблюдаемого дефицита.",
            "Retool work и energy debt остаются положительными.",
            "До их физического погашения новый production path не активирован.",
        ),
        "question": "Что непосредственно задерживает переход на новый production path?",
        "choices": (
            ("A", "Скрытый cooldown, зависящий только от имени фракции."),
            ("B", "Не завершены конечные work/energy затраты переналадки."),
            ("C", "Все рынки автоматически запрещают новый продукт."),
            ("D", "Планировщик не умеет менять производственную политику."),
        ),
        "dependency": "retool_work_energy_debt", "correct": "B",
        "equivalents": ("retool_work_energy_debt", "не завершена переналадка", "остались work energy затраты"),
    },
)

FIELDS = (
    "reviewerAnonymousId", "taskId", "buildSha", "freezeManifestVersion", "freezeFingerprint",
    "reviewPacketVersion", "selectedCauseId", "freeTextPrimaryCause", "answeredAtUtc",
)


def _normalize(text: str) -> str:
    return " ".join(text.strip().lower().split())


def _validate_identity(build_sha: str, freeze_fingerprint: str) -> None:
    if not BUILD_SHA_RE.fullmatch(build_sha):
        raise ValueError("build SHA must be a lowercase 40-character git SHA")
    if freeze_fingerprint != EXPECTED_FREEZE_FINGERPRINT:
        raise ValueError("freeze fingerprint does not match the frozen M22.6 manifest")


def _catalog_hash() -> str:
    payload = json.dumps(TASKS, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def prepare(build_sha: str, output_dir: Path, freeze_fingerprint: str = EXPECTED_FREEZE_FINGERPRINT) -> None:
    _validate_identity(build_sha, freeze_fingerprint)
    output_dir.mkdir(parents=True, exist_ok=True)
    manifest = {
        "packetVersion": PACKET_VERSION,
        "scenarioSuiteVersion": SCENARIO_SUITE_VERSION,
        "buildSha": build_sha,
        "freezeManifestVersion": FREEZE_MANIFEST_VERSION,
        "freezeFingerprint": freeze_fingerprint,
        "taskCatalogSha256": _catalog_hash(),
        "taskCount": len(TASKS),
        "passThreshold": PASS_THRESHOLD,
    }
    (output_dir / "packet_manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    form = ["# M22.6 B18 — blind causal review", "", f"Build: `{build_sha}`", "",
            "Для каждого задания выберите A/B/C/D. Не используйте answer key до завершения формы.", ""]
    for task in TASKS:
        form.extend((f"## {task['taskId']} — {task['title']}", "", f"Scenario: `{task['scenarioId']}`", "", "Наблюдаемое состояние:"))
        form.extend(f"- {item}" for item in task["evidence"])
        form.extend(("", task["question"], ""))
        form.extend(f"- {choice_id}. {text}" for choice_id, text in task["choices"])
        form.append("")
    (output_dir / "b18_review_form.md").write_text("\n".join(form), encoding="utf-8")

    key = {
        "identity": manifest,
        "tasks": [
            {
                "taskId": task["taskId"], "scenarioId": task["scenarioId"],
                "primaryDependencyId": task["dependency"], "correctChoiceId": task["correct"],
                "acceptedEquivalentAnswers": list(task["equivalents"]),
            }
            for task in TASKS
        ],
    }
    (output_dir / "b18_answer_key.json").write_text(
        json.dumps(key, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    with (output_dir / "b18_responses.csv").open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDS)
        writer.writeheader()


def load_key(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def score(answer_key: dict, responses_path: Path, expected_build_sha: str | None = None,
          expected_freeze_fingerprint: str | None = None) -> dict:
    identity = answer_key["identity"]
    build_sha = expected_build_sha or identity["buildSha"]
    freeze_fingerprint = expected_freeze_fingerprint or identity["freezeFingerprint"]
    _validate_identity(build_sha, freeze_fingerprint)
    if identity["buildSha"] != build_sha or identity["freezeFingerprint"] != freeze_fingerprint:
        raise ValueError("answer-key identity mismatch")
    if identity["packetVersion"] != PACKET_VERSION or identity["taskCatalogSha256"] != _catalog_hash():
        raise ValueError("answer key does not match this frozen B18 task catalog")

    with responses_path.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))
    if not rows:
        return {"status": "BLOCKED", "accuracy": None, "correctPrimaryCauseAnswers": 0,
                "totalScoredAnswers": 0, "reviewers": 0}

    task_key = {task["taskId"]: task for task in answer_key["tasks"]}
    by_reviewer: dict[str, dict[str, dict[str, str]]] = {}
    for row in rows:
        reviewer = row.get("reviewerAnonymousId", "").strip()
        task_id = row.get("taskId", "").strip()
        if not reviewer or task_id not in task_key:
            raise ValueError("unknown reviewer or B18 task")
        if row.get("buildSha") != build_sha or row.get("freezeManifestVersion") != FREEZE_MANIFEST_VERSION \
                or row.get("freezeFingerprint") != freeze_fingerprint or row.get("reviewPacketVersion") != PACKET_VERSION:
            raise ValueError("response identity mismatch")
        reviewer_rows = by_reviewer.setdefault(reviewer, {})
        if task_id in reviewer_rows:
            raise ValueError(f"duplicate B18 response for {reviewer}/{task_id}")
        reviewer_rows[task_id] = row

    expected_tasks = set(task_key)
    correct = 0
    total = 0
    details = []
    for reviewer, reviewer_rows in sorted(by_reviewer.items()):
        if set(reviewer_rows) != expected_tasks:
            raise ValueError(f"incomplete B18 packet for {reviewer}")
        for task_id in sorted(expected_tasks):
            row = reviewer_rows[task_id]
            key = task_key[task_id]
            selected = row.get("selectedCauseId", "").strip().upper()
            free_text = _normalize(row.get("freeTextPrimaryCause", ""))
            accepted = {_normalize(value) for value in key["acceptedEquivalentAnswers"]}
            is_correct = selected == key["correctChoiceId"] or (not selected and free_text in accepted)
            correct += int(is_correct)
            total += 1
            details.append({"reviewerAnonymousId": reviewer, "taskId": task_id, "correct": is_correct})
    accuracy = correct / total
    return {
        "status": "PASS" if accuracy >= PASS_THRESHOLD else "FAIL",
        "accuracy": accuracy,
        "correctPrimaryCauseAnswers": correct,
        "totalScoredAnswers": total,
        "reviewers": len(by_reviewer),
        "details": details,
    }


def _main() -> None:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)
    prepare_parser = subparsers.add_parser("prepare")
    prepare_parser.add_argument("--build-sha", required=True)
    prepare_parser.add_argument("--output-dir", type=Path, required=True)
    score_parser = subparsers.add_parser("score")
    score_parser.add_argument("--answer-key", type=Path, required=True)
    score_parser.add_argument("--responses", type=Path, required=True)
    score_parser.add_argument("--expected-build-sha")
    score_parser.add_argument("--expected-freeze-fingerprint")
    score_parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.command == "prepare":
        prepare(args.build_sha, args.output_dir)
        return
    result = score(load_key(args.answer_key), args.responses,
                   args.expected_build_sha, args.expected_freeze_fingerprint)
    payload = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.write_text(payload, encoding="utf-8")
    else:
        print(payload, end="")


if __name__ == "__main__":
    _main()
