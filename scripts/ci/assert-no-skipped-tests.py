#!/usr/bin/env python3
"""Fail when any JUnit test was skipped.

Integration tests are annotated @Testcontainers(disabledWithoutDocker = true), so a CI runner
whose Docker daemon is unreachable would skip them all and still report success. This turns
that silent skip into a failure. Usage: assert-no-skipped-tests.py <surefire-reports-dir>...
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET


def main(dirs: list[str]) -> int:
    reports = [f for d in dirs for f in glob.glob(os.path.join(d, "TEST-*.xml"))]
    if not reports:
        print(f"no surefire reports found in: {', '.join(dirs)}", file=sys.stderr)
        return 1
    total = 0
    skipped = []
    for path in reports:
        suite = ET.parse(path).getroot()
        total += int(suite.get("tests", 0))
        count = int(suite.get("skipped", 0))
        if count:
            skipped.append((suite.get("name"), count))
    if skipped:
        print("Skipped tests detected (is Docker reachable for Testcontainers?):", file=sys.stderr)
        for name, count in sorted(skipped):
            print(f"  {name}: {count}", file=sys.stderr)
        return 1
    print(f"{total} tests across {len(reports)} suites, none skipped.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
