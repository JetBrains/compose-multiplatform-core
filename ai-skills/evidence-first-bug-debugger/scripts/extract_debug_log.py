#!/usr/bin/env python3
"""Extract focused diagnostic regions from a large test or application log."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from typing import Iterable

FAILURE_RE = re.compile(
    r"(?i)(?:\bfailed\b|\bfailure\b|\bassert(?:ion)?\b|XCTAssert|"
    r"fatal error|uncaught exception|unhandled exception|traceback|panic|"
    r"segmentation fault|SIG(?:ABRT|SEGV|BUS|ILL)|error:|exception:|"
    r"timeout|timed out|deadlock|hang detected|crash(?:ed)?)"
)
SUCCESS_NOISE_RE = re.compile(
    r"(?i)(?:0 failures|without failures|no failures|tests? passed|succeeded|"
    r"expected error|error count:\s*0)"
)
TEST_START_RE = re.compile(
    r"(?i)(?:Test Case '-\[|Test Suite '|\b(?:starting|running) test\b|"
    r"\btest started\b|^\s*===+\s*test\b)"
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Print only diagnostic regions relevant to a bug or failing test."
    )
    parser.add_argument("log", type=Path, help="Path to the full log")
    parser.add_argument(
        "--scope",
        action="append",
        default=[],
        help="Operation, feature, request ID, or other literal scope; repeatable",
    )
    parser.add_argument(
        "--test",
        action="append",
        default=[],
        help="Test identifier to prioritize; repeatable",
    )
    parser.add_argument(
        "--pattern",
        action="append",
        default=[],
        help="Literal diagnostic marker such as BUGDBG:login; repeatable",
    )
    parser.add_argument(
        "--failure-pattern",
        action="append",
        default=[],
        help="Additional literal text that identifies the visible failure; repeatable",
    )
    parser.add_argument("--before", type=int, default=100, help="Lines before an anchor")
    parser.add_argument("--after", type=int, default=180, help="Lines after an anchor")
    parser.add_argument(
        "--max-lines", type=int, default=700, help="Maximum output lines"
    )
    parser.add_argument("--output", type=Path, help="Write excerpt to this file")
    return parser.parse_args()


def matching_indexes(lines: list[str], needles: Iterable[str]) -> list[int]:
    lowered = [needle.casefold() for needle in needles if needle]
    if not lowered:
        return []
    return [
        index
        for index, line in enumerate(lines)
        if any(needle in line.casefold() for needle in lowered)
    ]


def failure_indexes(lines: list[str], extra_failures: Iterable[str]) -> list[int]:
    indexes = [
        index
        for index, line in enumerate(lines)
        if FAILURE_RE.search(line) and not SUCCESS_NOISE_RE.search(line)
    ]
    indexes.extend(matching_indexes(lines, extra_failures))
    return sorted(set(indexes))


def nearby(indexes: list[int], anchor: int, distance: int) -> list[int]:
    return [index for index in indexes if abs(index - anchor) <= distance]


def choose_anchors(
    selectors: list[int], failures: list[int], line_count: int
) -> list[int]:
    if selectors and failures:
        nearest_failures: list[int] = []
        for selector in selectors[-8:]:
            candidates = [
                failure for failure in failures if abs(failure - selector) <= 800
            ]
            if candidates:
                nearest_failures.append(
                    min(candidates, key=lambda failure: abs(failure - selector))
                )
        if nearest_failures:
            return sorted(set(nearest_failures))[-3:]

    if failures:
        return failures[-3:]
    if selectors:
        return selectors[-5:]
    return [max(0, line_count - 1)]


def expand_start(
    lines: list[str],
    anchor: int,
    default_start: int,
    selectors: list[int],
    test_mode: bool,
) -> int:
    start = default_start

    earlier_selectors = [
        index for index in selectors if index <= anchor and anchor - index <= 600
    ]
    if earlier_selectors:
        start = min(start, min(earlier_selectors))

    if test_mode:
        floor = max(0, anchor - 1000)
        for index in range(anchor, floor - 1, -1):
            if TEST_START_RE.search(lines[index]):
                return min(start, index)

    return start


def clip_at_next_test(lines: list[str], anchor: int, end: int) -> int:
    for index in range(anchor + 1, end + 1):
        if TEST_START_RE.search(lines[index]):
            return max(anchor, index - 1)
    return end


def merge_ranges(ranges: list[tuple[int, int]]) -> list[tuple[int, int]]:
    if not ranges:
        return []
    merged: list[list[int]] = []
    for start, end in sorted(ranges):
        if not merged or start > merged[-1][1] + 1:
            merged.append([start, end])
        else:
            merged[-1][1] = max(merged[-1][1], end)
    return [(start, end) for start, end in merged]


def render(lines: list[str], ranges: list[tuple[int, int]], max_lines: int) -> str:
    output: list[str] = []
    remaining = max_lines

    for range_number, (start, end) in enumerate(ranges, start=1):
        if remaining <= 0:
            break
        if output:
            output.append("\n... omitted unrelated log region ...\n")
        output.append(f"--- excerpt {range_number}: lines {start + 1}-{end + 1} ---\n")

        for index in range(start, end + 1):
            if remaining <= 0:
                output.append("... excerpt truncated by --max-lines ...\n")
                break
            line = lines[index]
            if not line.endswith("\n"):
                line += "\n"
            output.append(f"{index + 1:>7}: {line}")
            remaining -= 1

    return "".join(output)


def main() -> int:
    args = parse_args()

    if not args.log.is_file():
        print(f"error: log file not found: {args.log}", file=sys.stderr)
        return 2
    if args.before < 0 or args.after < 0 or args.max_lines <= 0:
        print(
            "error: --before/--after must be non-negative and --max-lines positive",
            file=sys.stderr,
        )
        return 2

    text = args.log.read_text(encoding="utf-8", errors="replace")
    lines = text.splitlines(keepends=True)
    if not lines:
        print("error: log file is empty", file=sys.stderr)
        return 1

    selector_needles = [*args.scope, *args.test, *args.pattern]
    selectors = matching_indexes(lines, selector_needles)
    failures = failure_indexes(lines, args.failure_pattern)
    anchors = choose_anchors(selectors, failures, len(lines))

    ranges: list[tuple[int, int]] = []
    for anchor in anchors:
        start = max(0, anchor - args.before)
        start = expand_start(lines, anchor, start, selectors, bool(args.test))
        end = min(len(lines) - 1, anchor + args.after)
        if args.test:
            end = clip_at_next_test(lines, anchor, end)
        ranges.append((start, end))

    excerpt = render(lines, merge_ranges(ranges), args.max_lines)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(excerpt, encoding="utf-8")
        print(args.output)
    else:
        sys.stdout.write(excerpt)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
