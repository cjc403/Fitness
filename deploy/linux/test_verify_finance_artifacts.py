import hashlib
import os
import pathlib
import subprocess
import sys
import tempfile
import unittest


SCRIPT = pathlib.Path(os.environ.get(
    "FINANCE_ARTIFACT_SCRIPT", str(pathlib.Path(__file__).with_name("verify-finance-artifacts.py"))))
FILES = ("fitness-backend.jar", "frontend-dist.tar.gz")


class ArtifactManifestTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.temp.name)
        for name, data in zip(FILES, (b"jar candidate", b"frontend candidate")):
            (self.root / name).write_bytes(data)

    def tearDown(self):
        self.temp.cleanup()

    def manifest(self, names=FILES):
        lines = []
        for name in names:
            path = self.root / name
            digest = hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else "0" * 64
            lines.append("{}  {}".format(digest, name))
        value = "\n".join(lines) + "\n"
        (self.root / "SHA256SUMS").write_text(value, encoding="ascii")
        return value

    def run_gate(self, receipt=False):
        command = [sys.executable, str(SCRIPT), str(self.root)]
        if receipt:
            command += ["--receipt", str(self.root / "STAGING_ACCEPTED")]
        return subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                              universal_newlines=True)

    def test_accepts_exact_manifest_and_matching_receipt(self):
        value = self.manifest()
        (self.root / "STAGING_ACCEPTED").write_text(value, encoding="ascii")
        self.assertEqual(0, self.run_gate(receipt=True).returncode)

    def test_rejects_each_unsafe_or_incomplete_manifest(self):
        cases = {
            "omitted": (FILES[:1], None),
            "extra": (FILES + ("extra.bin",), b"extra"),
            "duplicate": (FILES + (FILES[0],), None),
            "absolute": (FILES[:1] + ("/tmp/frontend-dist.tar.gz",), None),
            "traversal": (FILES[:1] + ("../frontend-dist.tar.gz",), None),
        }
        for label, (names, extra) in cases.items():
            with self.subTest(label=label):
                if extra is not None:
                    (self.root / "extra.bin").write_bytes(extra)
                self.manifest(names)
                self.assertNotEqual(0, self.run_gate().returncode)

    def test_rejects_wrong_hash_and_receipt_mismatch(self):
        value = self.manifest()
        (self.root / "SHA256SUMS").write_text("0" * 64 + value[64:], encoding="ascii")
        self.assertNotEqual(0, self.run_gate().returncode)
        value = self.manifest()
        (self.root / "STAGING_ACCEPTED").write_text(value.replace(value[:1], "f", 1), encoding="ascii")
        self.assertNotEqual(0, self.run_gate(receipt=True).returncode)
        value = self.manifest()
        (self.root / "STAGING_ACCEPTED").write_text("\n".join(reversed(value.splitlines())) + "\n", encoding="ascii")
        self.assertNotEqual(0, self.run_gate(receipt=True).returncode)

    def test_rejects_non_strict_hash_and_missing_artifact_file(self):
        value = self.manifest()
        (self.root / "SHA256SUMS").write_text("ABC" + value[64:], encoding="ascii")
        self.assertNotEqual(0, self.run_gate().returncode)
        self.manifest()
        os.unlink(str(self.root / FILES[1]))
        self.assertNotEqual(0, self.run_gate().returncode)


if __name__ == "__main__":
    unittest.main()
