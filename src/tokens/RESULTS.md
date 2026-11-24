# What agent mode saves a coding agent

Measured on 2026-11-18 with `src/tokens/measure.py`, Nanolog 1.4-SNAPSHOT at commit `c783d3c`; measured again on 2026-11-19 at commit `dab574c`, before the 1.4 release, with the same counts; and on 2026-11-19 at Nanolog 1.5-SNAPSHOT with the fix of #96, where a Surefire fork names its own file under `runs/` instead of `latest.log` in the agent line and the run summary: 36 tokens more in each run with agent mode, the same counts without it. Environment: Windows 10, JDK 1.8.0_421, Maven 3.9.16, Surefire 3.2.5, Python 3.10.6, `tiktoken` 0.14.0. The project lives in `P:\github\nanolaba\nanolog\src\tokens\shop`, and its absolute paths appear in the output. Nanolog runs with its default settings, so the level is `TRACE`.

Each row compares the whole output of `mvn -q test -Dtest=<class>`, stdout and stderr together, which is what an agent reads. The first value is without agent mode, the second with `AI_AGENT=claude-code` and `CLAUDECODE=1` set, as Claude Code sets them.

| Scenario | Lines | Characters | Tokens, o200k_base |
|---|---|---|---|
| a. A test logs 200 orders and fails on order 150 (a deadlock with a cause) | 558 → 43 (13.0×) | 39,355 → 3,843 (10.2×) | 13,577 → 1,058 (12.8×) |
| b. A green test of a job that logs `INFO` and `DEBUG` for each of 300 records | 602 → 2 (301×) | 44,513 → 531 (84×) | 16,550 → 178 (93×) |
| c. A green test whose retry loop logs the same `WARN` 300 times | 301 → 5 (60×) | 31,881 → 848 (38×) | 9,925 → 259 (38.3×) |
| d. A test fails with an exception thrown inside a stream pipeline | 120 → 45 (2.7×) | 10,862 → 3,811 (2.9×) | 2,427 → 1,052 (2.3×) |
| All four | 1,581 → 95 | 126,611 → 9,033 | 42,479 → 2,547 (16.7×) |

## Reading the numbers

- **a** is the run quoted in the README. Without agent mode Nanolog prints 530 of the 558 lines: 451 entries with dates, and the stack trace of the error with every frame of JUnit, Surefire and reflection. In agent mode it prints one line saying what happened, the warning and the error on one line each with a collapsed trace, and the 12-line run summary.
- **b** and **c** are green runs. In agent mode the console keeps `WARN` and above; in **c** consecutive repeats collapse into one line with a count. `latest.log` still holds every entry.
- **d** saves the least. Nanolog's share of its output is small, and Surefire's own report of the failure, with the full trace, stays as it is: agent mode changes only Nanolog's output.
- **Platform.** On Windows a line break inside an entry is escaped as `\r\n`; on Linux and macOS it is `\n`, a character shorter.
- **Claude tokens are not in this run:** no Anthropic API credential was at hand. `measure.py` adds a column counted by the token counting endpoint when the `anthropic` package and a credential are present. Re-run it and add the column here before quoting Claude numbers.

## How to re-run

```
mvn -q compile jar:jar
pip install tiktoken anthropic
python src/tokens/measure.py --jar target/nanolog-1.4-SNAPSHOT.jar
```

The outputs are saved to `src/tokens/shop/target/tokens/` for a look at what was counted. Re-measure after any change to agent mode, the console, stack traces, repeats or the run summary, then update this file and the numbers quoted in the README, and redraw the picture of run **a** in README *Fewer tokens* from the outputs of a JDK 8 run, like the table:

```
pip install pillow
python src/tokens/before-after.py --version <version> --setup "Windows 10, JDK 1.8.0_421, Maven 3.9.16, Surefire 3.2.5"
```

It writes `assets/agent-mode-before-after.png` and counts the lines, characters and tokens it shows itself. The current picture is drawn from a run of measure.py on the 1.4.1 jar from Maven Central on 2026-11-23, JDK 8, whose counts for **a** are those of the table.
