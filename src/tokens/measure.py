"""Measures what Nanolog's agent mode saves a coding agent: the console output of the same test runs with and without
agent mode, in lines, characters and tokens.

Each scenario of src/tokens/shop runs twice through `mvn -q test -Dtest=<class>`: once as a developer runs it (agent
mode off) and once as Claude Code runs it (AI_AGENT=claude-code, CLAUDECODE=1). Everything Maven prints, stdout and
stderr together, is what the agent reads; it is saved to src/tokens/shop/target/tokens/ and counted.

Tokens are counted with
  - o200k_base, the tokenizer of OpenAI's current models, through `tiktoken` (pip install tiktoken);
  - Claude's tokenizer, through the token counting endpoint of the Anthropic API (pip install anthropic, and
    ANTHROPIC_API_KEY or another credential the SDK finds). Without one, the Claude column is left out.

Usage, from the repository root:
  mvn -q compile jar:jar                     # the jar under test, without tests, pitest or a signature
  python src/tokens/measure.py [--jar target/nanolog-1.4-SNAPSHOT.jar] [--mvn path/to/mvn] [--model claude-opus-5-5]

It prints a Markdown table for src/tokens/RESULTS.md.
"""
import argparse
import glob
import os
import platform
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SHOP = os.path.join(HERE, "shop")
ROOT = os.path.dirname(os.path.dirname(HERE))

SCENARIOS = [
    ("a", "OrderServiceTest", "a test logs 200 orders and fails on order 150 (deadlock with a cause)"),
    ("b", "ImportJobTest", "a green test of a job that logs INFO and DEBUG for each of 300 records"),
    ("c", "WarehouseClientTest", "a green test whose retry loop logs the same WARN 300 times"),
    ("d", "ReportServiceTest", "a test fails with an exception thrown inside a stream pipeline"),
]

# The variables that turn agent mode on (Agent.VARIABLES); none of them may leak into the run without agent mode.
AGENT_VARIABLES = ["AI_AGENT", "AGENT", "CLAUDECODE", "GEMINI_CLI", "CODEX_SANDBOX", "CODEX_CI", "CURSOR_AGENT",
                   "VSCODE_AGENT", "CLINE_ACTIVE"]


def environment(agent):
    """The environment of a run: the current one without agent variables or Nanolog settings, plus the mode."""
    env = {k: v for k, v in os.environ.items()
           if k.upper() not in AGENT_VARIABLES and not k.upper().startswith("NANOLOG_")}
    if agent:
        env["AI_AGENT"] = "claude-code"
        env["CLAUDECODE"] = "1"
    return env


def run(mvn, test, jar, agent):
    """Runs one test class and returns everything Maven printed."""
    result = subprocess.run([mvn, "-q", "test", "-Dtest=" + test, "-Dtokens.jar=" + jar],
                            cwd=SHOP, env=environment(agent), stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    return result.stdout.decode("utf-8", errors="replace").replace("\r\n", "\n")


class Counters:
    """Lines, characters and tokens of a text."""

    def __init__(self, model):
        import tiktoken
        self.o200k = tiktoken.get_encoding("o200k_base")
        self.model = model
        self.claude = None
        self.framing = 0
        self.claude_note = None
        try:
            import anthropic
            self.claude = anthropic.Anthropic()
            # count_tokens counts the text inside a user message; subtract the framing of an empty-ish message
            self.framing = self._claude_raw("x") - 1
        except Exception as e:  # no SDK or no credential: the Claude column is left out, with the reason
            self.claude = None
            self.claude_note = "Claude tokens not counted: %s: %s" % (type(e).__name__, str(e).splitlines()[0][:160])

    def _claude_raw(self, text):
        return self.claude.messages.count_tokens(
            model=self.model, messages=[{"role": "user", "content": text}]).input_tokens

    def count(self, text):
        lines = text.count("\n") + (0 if text.endswith("\n") or not text else 1)
        counts = {"lines": lines, "chars": len(text), "o200k": len(self.o200k.encode(text, disallowed_special=()))}
        if self.claude is not None:
            counts["claude"] = self._claude_raw(text) - self.framing
        return counts


def default_jar():
    jars = [j for j in glob.glob(os.path.join(ROOT, "target", "nanolog-*.jar"))
            if not j.endswith(("-sources.jar", "-javadoc.jar"))]
    if not jars:
        sys.exit("No jar under test: build it with `mvn -q compile jar:jar` or pass --jar")
    return max(jars, key=os.path.getmtime)


def ratio(before, after):
    return "%.1f×" % (before / after) if after else "—"


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--jar", help="the Nanolog jar under test; default: the newest target/nanolog-*.jar")
    parser.add_argument("--mvn", default=shutil.which("mvn") or "mvn", help="the Maven executable")
    parser.add_argument("--model", default="claude-opus-5-5", help="the Claude model whose tokenizer counts")
    args = parser.parse_args()
    jar = os.path.abspath(args.jar or default_jar())
    out = os.path.join(SHOP, "target", "tokens")

    # the first run downloads the plugins and compiles; it is not measured
    subprocess.run([args.mvn, "-q", "test-compile", "-Dtokens.jar=" + jar], cwd=SHOP, env=environment(False),
                   check=True)
    os.makedirs(out, exist_ok=True)

    counters = Counters(args.model)
    rows = []
    for key, test, description in SCENARIOS:
        counts = {}
        for mode in ("plain", "agent"):
            text = run(args.mvn, test, jar, mode == "agent")
            with open(os.path.join(out, "%s-%s.txt" % (key, mode)), "w", encoding="utf-8", newline="\n") as f:
                f.write(text)
            counts[mode] = counters.count(text)
        rows.append((key, description, counts))

    claude = counters.claude is not None
    header = "| Scenario | Lines | Characters | Tokens, o200k_base |" + (" Tokens, Claude |" if claude else "")
    print(header)
    print("|---|---|---|---|" + ("---|" if claude else ""))
    for key, description, c in rows:
        p, a = c["plain"], c["agent"]
        cells = ["%d → %d (%s)" % (p[k], a[k], ratio(p[k], a[k])) for k in ("lines", "chars", "o200k")]
        if claude:
            cells.append("%d → %d (%s)" % (p["claude"], a["claude"], ratio(p["claude"], a["claude"])))
        print("| %s. %s | %s |" % (key, description, " | ".join(cells)))
    print()
    print("Jar: %s; Java: %s; OS: %s; Python: %s" % (os.path.basename(jar), java_version(),
                                                    platform.platform(terse=True), platform.python_version()))
    if claude:
        print("Claude tokens: model %s, count_tokens minus %d tokens of message framing" % (args.model,
                                                                                            counters.framing))
    else:
        print(counters.claude_note)
    print("Outputs: %s" % out)


def java_version():
    try:
        text = subprocess.run(["java", "-version"], stdout=subprocess.PIPE, stderr=subprocess.STDOUT).stdout
        return text.decode("utf-8", errors="replace").splitlines()[0]
    except OSError:
        return "unknown"


if __name__ == "__main__":
    main()
