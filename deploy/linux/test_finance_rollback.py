import pathlib
import os
import subprocess
import tempfile
import unittest


BASH = r"C:/Program Files/Git/bin/bash.exe"
LIBRARY = pathlib.Path(os.environ.get(
    "FINANCE_ROLLBACK_LIB", str(pathlib.Path(__file__).with_name("finance-release-rollback.sh"))))


class RollbackTest(unittest.TestCase):
    def test_initial_gate_race_refuses_cutover_and_preserves_operator_marker(self):
        with tempfile.TemporaryDirectory() as raw:
            root = pathlib.Path(raw)
            script = root / "initial-gate.sh"
            script.write_text("""#!/usr/bin/env bash
set -u
source "$1"
MAINTENANCE_FLAG="$2/marker"; LOG="$2/log"
marker_owned=false; gate_owned_by_release=false
[[ ! -e "$MAINTENANCE_FLAG" ]]
printf operator >"$MAINTENANCE_FLAG"
if establish_initial_maintenance_gate; then
  echo stop >>"$LOG"
  remove_owned_maintenance_gate
  exit 0
else
  status=$?
  exit "$status"
fi
""", encoding="utf-8")
            result = subprocess.run([BASH, str(script), str(LIBRARY), str(root)],
                                    stdout=subprocess.PIPE, stderr=subprocess.PIPE, universal_newlines=True)
            self.assertNotEqual(0, result.returncode)
            self.assertEqual("operator", (root / "marker").read_text(encoding="ascii"))
            self.assertFalse((root / "log").exists())
            self.assertIn("unowned maintenance marker appeared; refusing release", result.stderr)

    def run_case(self, fail_restore):
        with tempfile.TemporaryDirectory() as raw:
            root = pathlib.Path(raw)
            script = root / "case.sh"
            marker = root / "marker"
            marker.write_text("owned", encoding="ascii")
            script.write_text("""#!/usr/bin/env bash
set -u
source "$1"
LOG="$2/log"; MAINTENANCE_FLAG="$2/marker"; marker_owned=true
swapped=true; watchdog_was_active=true
restore_backend() { echo backend >>"$LOG"; %s; }
restore_frontend() { echo frontend >>"$LOG"; return 0; }
restart_old_app() { echo app >>"$LOG"; return 0; }
verify_old_app() { echo verify >>"$LOG"; return %s; }
restore_watchdog() { echo watchdog >>"$LOG"; return 0; }
finance_rollback 37
""" % ("return 1" if fail_restore else "return 0", "1" if fail_restore else "0"), encoding="utf-8")
            result = subprocess.run([BASH, str(script), str(LIBRARY), str(root)],
                                    stdout=subprocess.PIPE, stderr=subprocess.PIPE, universal_newlines=True)
            log = ((root / "log").read_text(encoding="ascii").splitlines()
                   if (root / "log").exists() else [])
            return result, log, marker.exists()

    def test_preserves_primary_status_and_attempts_every_cleanup_after_secondary_failure(self):
        result, log, marker_exists = self.run_case(True)
        self.assertEqual(37, result.returncode)
        self.assertEqual(["backend", "frontend", "app", "verify", "watchdog"], log)
        self.assertTrue(marker_exists)
        self.assertIn("secondary rollback failure", result.stderr)

    def test_successful_rollback_removes_only_owned_marker(self):
        result, log, marker_exists = self.run_case(False)
        self.assertEqual(37, result.returncode)
        self.assertEqual(["backend", "frontend", "app", "verify", "watchdog"], log)
        self.assertFalse(marker_exists)

    def run_transition_case(self, mode, old_app_healthy=True):
        with tempfile.TemporaryDirectory() as raw:
            root = pathlib.Path(raw)
            if mode == "post-reopen-unowned":
                (root / "marker").write_text("operator", encoding="ascii")
            script = root / "transition.sh"
            script.write_text("""#!/usr/bin/env bash
set -u
source "$1"
LOG="$2/log"; MAINTENANCE_FLAG="$2/marker"
marker_owned=false; gate_owned_by_release=true; swapped=false
app_recovery_required=false; watchdog_was_active=true
stop_application() { echo stop >>"$LOG"; %s; }
establish_maintenance_gate() {
  echo gate >>"$LOG"
  if [[ ! -e "$MAINTENANCE_FLAG" ]]; then : >"$MAINTENANCE_FLAG"; marker_owned=true; fi
}
verify_maintenance_gate() { echo gate-verify >>"$LOG"; return 0; }
restore_backend() { echo backend >>"$LOG"; return 0; }
restore_frontend() { echo frontend >>"$LOG"; return 0; }
restart_old_app() { echo app >>"$LOG"; return 0; }
verify_old_app() { echo verify >>"$LOG"; return %s; }
restore_watchdog() { echo watchdog >>"$LOG"; return 0; }
%s
""" % (
                "finance_rollback 130" if mode == "interrupt" else "return 1",
                "0" if old_app_healthy else "1",
                ("stop_app_for_release || finance_rollback 41" if mode == "failure" else
                 "stop_app_for_release" if mode == "interrupt" else
                 "swapped=true; app_recovery_required=true; finance_rollback 52")), encoding="utf-8")
            result = subprocess.run([BASH, str(script), str(LIBRARY), str(root)],
                                    stdout=subprocess.PIPE, stderr=subprocess.PIPE, universal_newlines=True)
            log = ((root / "log").read_text(encoding="ascii").splitlines()
                   if (root / "log").exists() else [])
            return result, log, (root / "marker").exists()

    def test_stop_failure_creates_recovery_obligation_before_stop(self):
        result, log, marker_exists = self.run_transition_case("failure")
        self.assertEqual(41, result.returncode)
        self.assertEqual(["stop", "app", "verify", "watchdog"], log)
        self.assertFalse(marker_exists)

    def test_interrupt_during_stop_still_restores_and_verifies_old_app(self):
        result, log, marker_exists = self.run_transition_case("interrupt")
        self.assertEqual(130, result.returncode)
        self.assertEqual(["stop", "app", "verify", "watchdog"], log)
        self.assertFalse(marker_exists)

    def test_post_reopen_failure_regates_then_successfully_restores_and_reopens(self):
        result, log, marker_exists = self.run_transition_case("post-reopen")
        self.assertEqual(52, result.returncode)
        self.assertEqual(["gate", "gate-verify", "backend", "frontend", "app", "verify", "watchdog"], log)
        self.assertFalse(marker_exists)

    def test_post_reopen_incomplete_restore_retains_maintenance(self):
        result, log, marker_exists = self.run_transition_case("post-reopen", old_app_healthy=False)
        self.assertEqual(52, result.returncode)
        self.assertEqual(["gate", "gate-verify", "backend", "frontend", "app", "verify", "watchdog"], log)
        self.assertTrue(marker_exists)
        self.assertIn("secondary rollback failure: verify previous application", result.stderr)

    def test_post_reopen_does_not_remove_racing_unowned_marker(self):
        result, log, marker_exists = self.run_transition_case("post-reopen-unowned")
        self.assertEqual(52, result.returncode)
        self.assertEqual(["gate", "gate-verify", "backend", "frontend", "app", "verify", "watchdog"], log)
        self.assertTrue(marker_exists)


if __name__ == "__main__":
    unittest.main()
