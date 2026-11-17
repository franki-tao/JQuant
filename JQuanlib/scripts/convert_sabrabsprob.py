#!/usr/bin/env python3
"""Convert QuantLib noarbsabrabsprobs.cpp sabrabsprob[] into a compact resource.

Source: ql/experimental/volatility/noarbsabrabsprobs.cpp (QuantLib, cf74c9f)
Output: little-endian uint32 array, gzip-compressed.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import re
import struct
from pathlib import Path

EXPECTED_LENGTH = 1_209_600
EXPECTED_SUM = 647_857_029_164  # sum of the official unsigned long values


def parse_cpp_array(text: str) -> list[int]:
    start = text.find("{")
    end = text.rfind("}")
    if start < 0 or end < 0 or end <= start:
        raise SystemExit("could not find sabrabsprob array braces")
    nums = [int(x) for x in re.findall(r"\d+", text[start : end + 1])]
    return nums


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--src",
        default="/home/box/refs/QuantLib/ql/experimental/volatility/noarbsabrabsprobs.cpp",
    )
    parser.add_argument(
        "--dst",
        default="/workspace/JQuant/JQuanlib/src/main/resources/jquant/experimental/volatility/sabrabsprob.u32.gz",
    )
    args = parser.parse_args()

    src = Path(args.src)
    dst = Path(args.dst)
    text = src.read_text(encoding="utf-8", errors="replace")
    values = parse_cpp_array(text)
    if len(values) != EXPECTED_LENGTH:
        raise SystemExit(f"length {len(values)} != {EXPECTED_LENGTH}")
    total = sum(values)
    if total != EXPECTED_SUM:
        raise SystemExit(f"sum {total} != {EXPECTED_SUM}")
    if min(values) < 0 or max(values) > 0xFFFFFFFF:
        raise SystemExit("values do not fit in uint32")

    raw = struct.pack("<" + "I" * len(values), *values)
    sha = hashlib.sha256(raw).hexdigest()
    dst.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(dst, "wb", compresslevel=9) as f:
        f.write(raw)

    print(f"wrote {dst}")
    print(f"length={len(values)}")
    print(f"sum={total}")
    print(f"sha256_le_u32={sha}")
    print(f"uncompressed_bytes={len(raw)}")
    print(f"gzip_bytes={dst.stat().st_size}")


if __name__ == "__main__":
    main()
