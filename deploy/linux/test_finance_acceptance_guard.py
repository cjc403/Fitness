"""Run acceptance CLI against forbidden targets; no network/SQL may be reached."""
import pathlib
import subprocess
import sys
import unittest


class AcceptanceGuardTest(unittest.TestCase):
    def test_rejects_production_mutation_and_mismatched_stage(self):
        cases = [
            ["--db", "fitness_db", "--port", "8080"],
            ["--db", "fitchain_stage_finance_20260917", "--port", "8080"],
            ["--db", "fitness_db", "--port", "18080"],
            ["--db", "other_database", "--port", "18080"],
            ["--db", "fitchain_test_finance_20260917", "--port", "8080", "--readonly"],
        ]
        for case in cases:
            with self.subTest(case=case):
                result = subprocess.run(
                    [sys.executable, str(pathlib.Path(__file__).with_name("refund-finance-acceptance.py"))] + case,
                    stdout=subprocess.PIPE, stderr=subprocess.PIPE, encoding="utf-8", timeout=5,
                )
                self.assertNotEqual(0, result.returncode)
                self.assertIn("Target guard:", result.stderr)
                self.assertNotIn("runuser", result.stderr)


if __name__ == "__main__":
    unittest.main()
