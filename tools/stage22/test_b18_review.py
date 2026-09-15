#!/usr/bin/env python3
import csv
import importlib.util
import tempfile
import unittest
from pathlib import Path

MODULE = Path(__file__).with_name("b18_review.py")
spec = importlib.util.spec_from_file_location("b18_review", MODULE)
b18 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(b18)

BUILD = "a" * 40
FREEZE = b18.EXPECTED_FREEZE_FINGERPRINT


class B18ReviewTest(unittest.TestCase):
    def packet(self, root: Path) -> Path:
        b18.prepare(BUILD, root)
        return root

    def write_responses(self, root: Path, choices: dict[str, str], reviewer: str = "rev-01") -> None:
        path = root / "b18_responses.csv"
        with path.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=b18.FIELDS)
            writer.writeheader()
            for task in b18.TASKS:
                writer.writerow({
                    "reviewerAnonymousId": reviewer,
                    "taskId": task["taskId"],
                    "buildSha": BUILD,
                    "freezeManifestVersion": b18.FREEZE_MANIFEST_VERSION,
                    "freezeFingerprint": FREEZE,
                    "reviewPacketVersion": b18.PACKET_VERSION,
                    "selectedCauseId": choices[task["taskId"]],
                    "freeTextPrimaryCause": "",
                    "answeredAtUtc": "2026-09-13T10:00:00Z",
                })

    def test_form_is_blind_and_contains_eight_tasks(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.packet(Path(tmp))
            form = (root / "b18_review_form.md").read_text(encoding="utf-8")
            self.assertEqual(8, form.count("\n## B18-"))
            self.assertNotIn("critical_input_missing", form)
            self.assertNotIn("correctChoiceId", form)

    def test_wrong_freeze_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(ValueError):
                b18.prepare(BUILD, Path(tmp), "0" * 64)

    def test_seven_of_eight_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.packet(Path(tmp))
            choices = {task["taskId"]: task["correct"] for task in b18.TASKS}
            choices["B18-08"] = "A"
            self.write_responses(root, choices)
            result = b18.score(b18.load_key(root / "b18_answer_key.json"), root / "b18_responses.csv")
            self.assertEqual("PASS", result["status"])
            self.assertEqual(7, result["correctPrimaryCauseAnswers"])

    def test_six_of_eight_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.packet(Path(tmp))
            choices = {task["taskId"]: task["correct"] for task in b18.TASKS}
            choices["B18-01"] = "D"
            choices["B18-02"] = "D"
            self.write_responses(root, choices)
            result = b18.score(b18.load_key(root / "b18_answer_key.json"), root / "b18_responses.csv")
            self.assertEqual("FAIL", result["status"])
            self.assertEqual(0.75, result["accuracy"])

    def test_empty_template_is_blocked(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.packet(Path(tmp))
            result = b18.score(b18.load_key(root / "b18_answer_key.json"), root / "b18_responses.csv")
            self.assertEqual("BLOCKED", result["status"])

    def test_duplicate_and_incomplete_packets_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.packet(Path(tmp))
            choices = {task["taskId"]: task["correct"] for task in b18.TASKS}
            self.write_responses(root, choices)
            path = root / "b18_responses.csv"
            with path.open(encoding="utf-8") as source:
                rows = list(csv.DictReader(source))
            with path.open("a", encoding="utf-8", newline="") as handle:
                writer = csv.DictWriter(handle, fieldnames=b18.FIELDS)
                writer.writerow(rows[0])
            with self.assertRaisesRegex(ValueError, "duplicate"):
                b18.score(b18.load_key(root / "b18_answer_key.json"), path)

            self.write_responses(root, choices)
            with path.open(encoding="utf-8") as source:
                rows = list(csv.DictReader(source))[:-1]
            with path.open("w", encoding="utf-8", newline="") as handle:
                writer = csv.DictWriter(handle, fieldnames=b18.FIELDS)
                writer.writeheader()
                writer.writerows(rows)
            with self.assertRaisesRegex(ValueError, "incomplete"):
                b18.score(b18.load_key(root / "b18_answer_key.json"), path)

    def test_identity_mismatch_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.packet(Path(tmp))
            choices = {task["taskId"]: task["correct"] for task in b18.TASKS}
            self.write_responses(root, choices)
            path = root / "b18_responses.csv"
            with path.open(encoding="utf-8") as source:
                rows = list(csv.DictReader(source))
            rows[0]["buildSha"] = "b" * 40
            with path.open("w", encoding="utf-8", newline="") as handle:
                writer = csv.DictWriter(handle, fieldnames=b18.FIELDS)
                writer.writeheader()
                writer.writerows(rows)
            with self.assertRaisesRegex(ValueError, "identity mismatch"):
                b18.score(b18.load_key(root / "b18_answer_key.json"), path)

    def test_predeclared_exact_free_text_equivalent_can_score(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.packet(Path(tmp))
            choices = {task["taskId"]: task["correct"] for task in b18.TASKS}
            self.write_responses(root, choices)
            path = root / "b18_responses.csv"
            with path.open(encoding="utf-8") as source:
                rows = list(csv.DictReader(source))
            rows[0]["selectedCauseId"] = ""
            rows[0]["freeTextPrimaryCause"] = "  НЕТ   HEAVY COMPONENTS "
            with path.open("w", encoding="utf-8", newline="") as handle:
                writer = csv.DictWriter(handle, fieldnames=b18.FIELDS)
                writer.writeheader()
                writer.writerows(rows)
            result = b18.score(b18.load_key(root / "b18_answer_key.json"), path)
            self.assertEqual("PASS", result["status"])


if __name__ == "__main__":
    unittest.main()
