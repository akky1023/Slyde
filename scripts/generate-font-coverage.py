"""Regenerate bundled font coverage after updating fonts (requires fonttools)."""
from pathlib import Path
import hashlib
import struct
from fontTools.ttLib import TTFont

directory = Path(__file__).resolve().parents[1] / "prototype/src/main/assets/fonts"
names = ["NotoSansJP.ttf", "BizinGothicDiscord-Regular.ttf",
         "NotoSansSymbols-Regular.ttf", "NotoSansSymbols2-Regular.ttf"]
with (directory / "coverage.bin").open("wb") as output:
    output.write(b"FCOV" + struct.pack(">I", len(names)))
    for name in names:
        path = directory / name
        with TTFont(path) as font:
            points = sorted(cp for cp, glyph in font.getBestCmap().items() if glyph != ".notdef")
        ranges = []
        for point in points:
            if ranges and point == ranges[-1][1] + 1:
                ranges[-1][1] = point
            else:
                ranges.append([point, point])
        encoded = name.encode("ascii")
        output.write(struct.pack(">H", len(encoded)) + encoded)
        output.write(hashlib.sha256(path.read_bytes()).digest())
        output.write(struct.pack(">I", len(ranges)))
        for start, end in ranges:
            output.write(struct.pack(">II", start, end))
