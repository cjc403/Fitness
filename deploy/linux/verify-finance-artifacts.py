#!/usr/bin/env python3
"""Validate the fixed finance release artifacts without modifying the filesystem."""
from __future__ import print_function

import argparse
import hashlib
import os
import re
import sys


EXPECTED = frozenset(("fitness-backend.jar", "frontend-dist.tar.gz"))
LINE = re.compile(r"^([0-9a-f]{64})  ([A-Za-z0-9][A-Za-z0-9._-]*)$")


def parse_manifest(path):
    with open(path, "r") as handle:
        lines = handle.read().splitlines()
    entries = {}
    for number, line in enumerate(lines, 1):
        match = LINE.match(line)
        if not match:
            raise ValueError("manifest line {} must contain strict lowercase sha256 and one safe filename".format(number))
        digest, name = match.groups()
        if name in entries:
            raise ValueError("duplicate manifest artifact: {}".format(name))
        entries[name] = digest
    if set(entries) != EXPECTED:
        raise ValueError("manifest must contain exactly: {}".format(", ".join(sorted(EXPECTED))))
    return entries


def verify(directory, receipt=None):
    manifest = os.path.join(directory, "SHA256SUMS")
    entries = parse_manifest(manifest)
    if receipt is not None:
        receipt_entries = parse_manifest(receipt)
        with open(manifest, "rb") as manifest_handle:
            manifest_bytes = manifest_handle.read()
        with open(receipt, "rb") as receipt_handle:
            receipt_bytes = receipt_handle.read()
        if entries != receipt_entries or manifest_bytes != receipt_bytes:
            raise ValueError("staging receipt does not exactly match artifact manifest")
    for name, expected in entries.items():
        path = os.path.join(directory, name)
        if not os.path.isfile(path) or os.path.islink(path):
            raise ValueError("artifact is missing or not a regular file: {}".format(name))
        digest = hashlib.sha256()
        with open(path, "rb") as handle:
            for block in iter(lambda: handle.read(1024 * 1024), b""):
                digest.update(block)
        if digest.hexdigest() != expected:
            raise ValueError("sha256 mismatch: {}".format(name))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("directory")
    parser.add_argument("--receipt")
    args = parser.parse_args()
    try:
        verify(args.directory, args.receipt)
    except (IOError, OSError, ValueError) as error:
        print("Finance artifact verification failed: {}".format(error), file=sys.stderr)
        return 1
    print("Finance artifacts verified")
    return 0


if __name__ == "__main__":
    sys.exit(main())
