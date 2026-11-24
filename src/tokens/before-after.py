"""Renders assets/agent-mode-before-after.png, the picture in README *Fewer tokens*: run a of measure.py as the
developer's console prints it and as a coding agent reads it in agent mode, side by side at the same height and font.

The text is the saved output of measure.py, not retyped: the script reads the two files, wraps long lines the way a
terminal does, and counts lines, characters and o200k_base tokens itself, as measure.py does, so the numbers in the
picture always belong to the text in it.

Usage, from the repository root, after measure.py on JDK 8 (the README table is measured there):
  pip install pillow tiktoken
  python src/tokens/before-after.py --version 1.4.1 --setup "Windows 10, JDK 1.8.0_421, Maven 3.9.16, Surefire 3.2.5"

Fonts: Consolas and Segoe UI where they exist (Windows), DejaVu Sans Mono and DejaVu Sans otherwise.
"""
import argparse
import os
import textwrap

import tiktoken
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUTPUTS = os.path.join(HERE, "shop", "target", "tokens")

S = 2      # supersampling; the picture is saved at 1x
COLS = 96  # terminal width of each panel, in characters

BG = (17, 24, 39)
PANEL = (11, 15, 25)
TEXT = (209, 213, 219)
DIM = (107, 114, 128)
GREY = (156, 163, 175)
BLUE = (96, 165, 250)
RED = (248, 113, 113)
GREEN = (74, 222, 128)


def font(names, size):
    for name in names:
        try:
            return ImageFont.truetype(name, size * S)
        except OSError:
            pass
    raise SystemExit("None of the fonts %s was found" % ", ".join(names))


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def counts(text):
    """Lines, characters and o200k_base tokens, as measure.py counts them."""
    lines = text.count("\n") + (0 if text.endswith("\n") or not text else 1)
    tokens = len(tiktoken.get_encoding("o200k_base").encode(text, disallowed_special=()))
    return lines, len(text), tokens


def rows(text):
    """The text as a terminal of COLS columns shows it: tabs expanded, long lines wrapped."""
    out = []
    for line in text.rstrip("\n").split("\n"):
        out.extend(textwrap.wrap(line.expandtabs(4), COLS, replace_whitespace=False, drop_whitespace=False,
                                 break_long_words=True, break_on_hyphens=False) or [""])
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--plain", default=os.path.join(OUTPUTS, "a-plain.txt"), help="run a without agent mode")
    parser.add_argument("--agent", default=os.path.join(OUTPUTS, "a-agent.txt"), help="run a in agent mode")
    parser.add_argument("--out", default=os.path.join(ROOT, "assets", "agent-mode-before-after.png"))
    parser.add_argument("--version", required=True, help="the Nanolog version the outputs were measured on")
    parser.add_argument("--setup", required=True, help="the environment of the measurement, as in RESULTS.md")
    args = parser.parse_args()

    mono = font(["consola.ttf", "DejaVuSansMono.ttf"], 14)
    mono_bold = font(["consolab.ttf", "DejaVuSansMono-Bold.ttf"], 14)
    ui = font(["segoeui.ttf", "DejaVuSans.ttf"], 17)
    ui_bold = font(["segoeuib.ttf", "DejaVuSans-Bold.ttf"], 19)
    ui_title = font(["segoeuib.ttf", "DejaVuSans-Bold.ttf"], 26)
    ui_small = font(["segoeui.ttf", "DejaVuSans.ttf"], 14)

    plain, agent = read(args.plain), read(args.agent)
    (pl, pc, pt), (al, ac, at) = counts(plain), counts(agent)
    agent_rows = rows(agent)
    height = len(agent_rows)
    shown = rows(plain)[:height - 1]  # the head of the plain output has no wrapped lines
    rest = pl - len(shown)

    line_h, pad, gap, margin, top, head_h = 19 * S, 18 * S, 28 * S, 36 * S, 150 * S, 64 * S
    panel_w = int(COLS * mono.getlength("M")) + 2 * pad
    panel_h = head_h + height * line_h + 2 * pad
    width, total_h = margin * 2 + panel_w * 2 + gap, top + panel_h + 92 * S

    img = Image.new("RGB", (width, total_h), BG)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 5 * S, total_h], fill=BLUE)
    d.text((margin, 28 * S), "The same failing test run: what the console prints, and what a coding agent reads",
           font=ui_title, fill=(243, 244, 246))
    d.text((margin, 74 * S), "mvn -q test of a test that logs 200 orders and fails on order 150 (a deadlock with a "
           "cause). Nanolog %s with its default settings, every level on." % args.version, font=ui, fill=GREY)
    d.text((margin, 102 * S), "Both panels: the same font and the same height. Text as printed, long lines wrapped "
           "at %d columns." % COLS, font=ui, fill=GREY)

    def panel(x, title, colour, stats, body, footer=None):
        d.rounded_rectangle([x, top, x + panel_w, top + panel_h], radius=10 * S, fill=PANEL)
        d.text((x + pad, top + 14 * S), title, font=ui_bold, fill=colour)
        d.text((x + pad, top + 40 * S), stats, font=ui_small, fill=GREY)
        y = top + head_h + pad
        for row in body:
            d.text((x + pad, y), row, font=mono, fill=TEXT)
            y += line_h
        if footer:
            d.text((x + pad, y), footer, font=mono_bold, fill=RED)

    panel(margin, "Without agent mode: a developer's console", RED,
          "%d lines · %s characters · %s tokens (o200k_base)" % (pl, format(pc, ","), format(pt, ",")),
          shown, "··· %d more lines: lines %d to %d of %d are below this edge ···" % (rest, len(shown) + 1, pl, pl))
    panel(margin + panel_w + gap, "Agent mode (AI_AGENT=claude-code): the whole run", GREEN,
          "%d lines · %s characters · %s tokens (o200k_base) · the full log is in the file it names"
          % (al, format(ac, ","), format(at, ",")), agent_rows)

    foot_y = top + panel_h + 22 * S
    d.text((margin, foot_y), "%s; tokens counted with tiktoken o200k_base over everything Maven printed, stdout and "
           "stderr." % args.setup, font=ui_small, fill=DIM)
    d.text((margin, foot_y + 24 * S), "Reproducible: python src/tokens/measure.py in github.com/nanolaba/nanolog. "
           "Agent mode changes only what Nanolog prints; Surefire's report stays as it is.", font=ui_small, fill=DIM)

    # a dark page of text needs few colours: a 128-colour palette keeps it sharp at a fraction of the size
    img.resize((width // S, total_h // S), Image.LANCZOS) \
        .quantize(colors=128, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE) \
        .save(args.out, optimize=True)
    print("%s: %dx%d; without agent mode %d lines, %d characters, %d tokens; agent mode %d lines, %d characters, "
          "%d tokens" % (args.out, width // S, total_h // S, pl, pc, pt, al, ac, at))


if __name__ == "__main__":
    main()
