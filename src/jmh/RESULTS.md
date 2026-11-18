# Benchmark results

JMH measurements of the benchmarks in `src/jmh/java`. How to run them is described in [AGENTS.md](../../AGENTS.md#benchmarks).
The numbers are indicative: compare the rows of one column, and re-measure on your own hardware before drawing
conclusions from small differences.

## Environment

Measured on 2026-11-17.

- CPU: AMD Ryzen 5 5600X, 6 cores / 12 threads; 64 GB RAM; Windows 10 Pro 10.0.19045, power plan "Balanced". A
  developer workstation, not an isolated benchmark machine.
- JDK 8: Java HotSpot 1.8.0_421. JDK 11: Eclipse Temurin 11.0.33+10. JDK 17: OpenJDK 17.0.2+8. JDK 21: Eclipse
  Temurin 21.0.13+9. JDK 25: Java HotSpot 25.0.1+8-LTS.
- JMH 1.37, average time per operation, 1 fork, 3 warm-up and 5 measurement iterations of 1 second each. On JDK 25 JMH
  prints warnings about `sun.misc.Unsafe`; they come from JMH itself and do not affect the results.
- Three versions of the code were measured with the same benchmark classes. "Current" is the code after #24, which
  detects the caller with `StackWalker` on Java 9+. "Before #24" is commit `daed6b4`. "Before #16" is commit `8f4aaca`,
  the last one before the level check moved in front of the stack walk, compiled on its own.
- A first run of "before #24" on JDK 11 and 21 overlapped with an unrelated test run on the same machine (the empty
  benchmark took 3 ± 20 ns instead of 0.3) and was discarded; the numbers below come from a second run.
- In every benchmark the source class is `com.nanolaba.logging.benchmark.*`; "on elsewhere" means `DEBUG` is enabled
  only for another package (`-Dnanolog.level.com.acme.other=DEBUG`), so the level is enabled for some class but not for
  this one.

## Disabled calls

`DisabledCallBenchmark`, current code, in nanoseconds per call. `ConsoleLogger` with `TRACE` and `DEBUG` switched off.

| Call | JDK 8 | JDK 11 | JDK 17 | JDK 21 | JDK 25 |
|---|---:|---:|---:|---:|---:|
| empty benchmark method, for reference | 0.27 | 0.55 | 0.43 | 0.34 | 0.39 |
| `LOG.debug("value {}", v)`, `DEBUG` off everywhere | 2.0 | 2.1 | 1.5 | 1.6 | 2.0 |
| `LOG.debug(Foo.class, "value {}", v)`, `DEBUG` off everywhere | 11.6 | 8.3 | 7.5 | 7.2 | 8.9 |
| `LOG.isDebugEnabled()`, `DEBUG` off everywhere | 3.2 | 2.7 | 1.3 | 1.2 | 1.5 |
| `LOG.isDebugEnabled(Foo.class)`, `DEBUG` off everywhere | 11.7 | 8.5 | 7.8 | 6.6 | 8.3 |
| `LOG.debug("value {}", v)`, `DEBUG` on elsewhere | 13 711 | 8 523 | 2 432 | 2 603 | 2 821 |
| `LOG.debug(Foo.class, "value {}", v)`, `DEBUG` on elsewhere | 11.4 | 8.7 | 10.3 ± 19.5 | 7.3 | 10.6 |

### After #51

Captures of one thread (#51) added a check to every level check: one volatile read of the number of open captures.
`DisabledCallBenchmark` measured again on 2026-11-17 on the same machine, no capture open, in nanoseconds per call,
the table above → now. Every difference is within the error of the measurements, so the table and the README keep
their numbers.

| Call | JDK 8 | JDK 17 |
|---|---:|---:|
| empty benchmark method, for reference | 0.27 → 0.27 | 0.43 → 0.31 |
| `LOG.debug("value {}", v)`, `DEBUG` off everywhere | 2.0 → 2.1 | 1.5 → 1.6 |
| `LOG.debug(Foo.class, "value {}", v)`, `DEBUG` off everywhere | 11.6 → 11.0 | 7.5 → 7.4 |
| `LOG.isDebugEnabled()`, `DEBUG` off everywhere | 3.2 → 2.9 | 1.3 → 1.3 |
| `LOG.isDebugEnabled(Foo.class)`, `DEBUG` off everywhere | 11.7 → 11.3 | 7.8 → 6.4 |
| `LOG.debug("value {}", v)`, `DEBUG` on elsewhere | 13 711 → 12 784 | 2 432 → 2 346 |
| `LOG.debug(Foo.class, "value {}", v)`, `DEBUG` on elsewhere | 11.4 → 10.9 | 10.3 ± 19.5 → 7.4 |

## Before and after #24

The calls that walk the stack, before and after `StackWalker` replaced the `Throwable` walk on Java 9+, in
microseconds per call, before → after. Java 8 has no `StackWalker` and still reads a `Throwable`, so its column shows
the same code twice; the differences there are within the error of the measurements.

| Call | Depth | JDK 8 | JDK 11 | JDK 17 | JDK 21 | JDK 25 |
|---|---:|---:|---:|---:|---:|---:|
| `LOG.debug("value {}", v)`, `DEBUG` on elsewhere (`DisabledCallBenchmark`) | | 12.4 → 13.7 | 9.2 → 8.5 | 8.6 → 2.4 | 9.7 → 2.6 | 8.1 → 2.8 |
| the same (`StackDepthBenchmark`) | 10 | 18.0 → 20.0 | 13.0 → 8.5 | 10.5 → 2.6 | 10.2 → 2.6 | 10.4 → 2.7 |
| | 100 | 61.1 → 65.2 | 29.3 → 8.6 | 25.8 → 3.1 | 25.2 → 2.9 | 27.6 → 3.2 |
| | 500 | 249.8 → 275.7 | 90.0 → 9.3 | 90.5 → 4.3 | 84.2 → 4.3 | 104.7 → 4.9 |
| `LOG.info("value {}", v)`, enabled, logger keeps the entry | 10 | 17.7 → 19.9 | 10.7 → 8.3 | 10.5 → 2.9 | 9.9 → 3.0 | 10.1 → 3.4 |
| | 100 | 60.4 → 64.8 | 25.2 → 9.3 | 26.2 → 3.2 | 24.2 → 3.3 | 26.8 → 3.2 |
| | 500 | 261.6 → 273.2 | 85.6 → 11.7 | 88.0 → 4.2 | 84.9 → 4.8 | 105.3 → 4.8 |
| `LOG.info("value {}", v)` through `ConsoleLogger` | | 14.3 → 14.9 | 11.2 → 9.6 | 9.0 → 3.8 | 10.4 → 3.5 | 8.3 → 3.1 |

On JDK 17, 21 and 25 a call that detects its caller became three to four times cheaper near the top of the stack and
twenty times cheaper 500 frames down, and its cost now hardly grows with the depth: 2.5 to 3.5 µs at 10 frames, about
3 µs at 100 and 4 to 5 µs at 500. On JDK 11 it no longer grows with the depth either, but the `StackWalker` of that release has a
higher fixed cost, about 8.5 µs.

## Before and after #71

On Java 8 the caller is now found by `sun.reflect.Reflection.getCallerClass(int)` and only the frames up to it are
built (`JavaLangAccess.getStackTraceElement`), instead of the whole stack trace of a `Throwable` with each class loaded
by name. Measured on 2026-11-18 on the same machine and JDK 8 (Java HotSpot 1.8.0_421), the same benchmark classes,
before → after, in microseconds per call. Java 9+ uses `StackWalker` and is not affected.

| Call | Depth | JDK 8 |
|---|---:|---:|
| `LOG.debug("value {}", v)`, `DEBUG` on elsewhere (`DisabledCallBenchmark`) | | 14.5 → 4.7 |
| the same (`StackDepthBenchmark`) | 10 | 19.6 → 5.0 |
| | 100 | 65.0 → 8.1 |
| | 500 | 266.5 → 19.2 |
| `LOG.info("value {}", v)`, enabled, logger keeps the entry | 10 | 19.7 → 5.3 |
| | 100 | 65.2 → 8.3 |
| | 500 | 263.1 → 19.4 |
| `LOG.info(Foo.class, "value {}", v)`, enabled, logger keeps the entry | 10 | 0.16 → 0.16 |
| | 500 | 0.58 → 0.58 |

A call that detects its caller became four times cheaper near the top of the stack and fourteen times cheaper 500
frames down. It still grows with the depth, more slowly: creating the `Throwable` fills in its native backtrace for the
whole stack. The calls with an explicit class and the disabled calls did not change. The JDK 8 column of the tables
above and below shows the code before #71.

## Before and after #16

`DisabledCallBenchmark` against the code before #16, in nanoseconds per call, before → after (after: commit
`daed6b4`).

| Call | JDK 8 | JDK 11 | JDK 17 | JDK 21 | JDK 25 |
|---|---:|---:|---:|---:|---:|
| `LOG.debug("value {}", v)`, `DEBUG` off everywhere | 11 365 → 2.1 | 8 072 → 2.2 | 8 639 → 1.9 | 8 535 → 1.6 | 8 488 → 1.7 |
| `LOG.isDebugEnabled()`, `DEBUG` off everywhere | 11 826 → 3.1 | 8 944 → 3.2 | 8 451 → 1.2 | 9 943 → 1.2 | 8 742 → 1.4 |
| `LOG.debug("value {}", v)`, `DEBUG` on elsewhere | 11 288 → 12 395 | 7 969 → 9 157 | 8 572 → 8 624 | 8 643 → 9 736 | 9 481 → 8 111 |
| `LOG.debug(Foo.class, "value {}", v)`, `DEBUG` off everywhere | 11.0 → 11.4 | 7.5 → 9.2 | 22.0 ± 124 → 7.0 | 6.8 → 6.9 | 8.2 → 8.2 |

A level that is off for every class no longer walks the stack, which makes such a call 2 800 to 8 300 times cheaper.
A level enabled for some other package still needs the class, so #16 left that call as it was: the differences in that
row are within the error of the measurements. #24 made it cheaper on Java 9+, see above. The 22.0 before #16 on JDK 17
is a noisy measurement (error ± 124), not a real difference.

## Stack depth

`StackDepthBenchmark`, current code: each call is made `depth` frames below the benchmark method, on top of the frames
of JMH itself. In microseconds per call.

| Call | Depth | JDK 8 | JDK 11 | JDK 17 | JDK 21 | JDK 25 |
|---|---:|---:|---:|---:|---:|---:|
| the recursion only, no logging | 10 | 0.010 | 0.010 | 0.009 | 0.009 | 0.009 |
| | 100 | 0.078 | 0.082 | 0.081 | 0.092 | 0.108 |
| | 500 | 0.36 | 0.36 | 0.39 | 0.49 ± 0.42 | 0.58 |
| `LOG.debug("value {}", v)`, `DEBUG` on elsewhere | 10 | 20.0 | 8.5 | 2.6 | 2.6 | 2.7 |
| | 100 | 65.2 | 8.6 | 3.1 | 2.9 | 3.2 |
| | 500 | 275.7 | 9.3 | 4.3 | 4.3 | 4.9 |
| `LOG.info("value {}", v)`, enabled, logger keeps the entry | 10 | 19.9 | 8.3 | 2.9 | 3.0 | 3.4 |
| | 100 | 64.8 | 9.3 | 3.2 | 3.3 | 3.2 |
| | 500 | 273.2 | 11.7 | 4.2 | 4.8 | 4.8 |
| `LOG.info(Foo.class, "value {}", v)`, enabled, logger keeps the entry | 10 | 0.12 | 0.06 | 0.07 | 0.06 | 0.07 |
| | 100 | 0.19 | 0.13 | 0.16 | 0.16 | 0.18 |
| | 500 | 0.56 | 0.50 | 0.68 ± 0.38 | 0.61 | 0.75 |

Caller detection still dominates every call without a class whose level is enabled for some class. On Java 9+ it costs
a few microseconds whatever the depth; on Java 8 it grows linearly with the depth, because `Throwable.getStackTrace()`
builds an element for every frame although detection needs only the first few. With an explicit class the same call
costs about 0.05 to 0.2 µs once the recursion of the benchmark is subtracted, whatever the depth. Values marked with
their error are noisy measurements.

## ConsoleLogger

`ConsoleLoggerBenchmark`, current code: an enabled `INFO` call through the default `ConsoleLogger`, formatted as usual
and written to a stream that discards it, so no terminal is involved. In nanoseconds per call.

| Call | JDK 8 | JDK 11 | JDK 17 | JDK 21 | JDK 25 |
|---|---:|---:|---:|---:|---:|
| `LOG.info(Foo.class, "value {}", v)` | 956 | 983 ± 1 418 | 844 ± 433 | 675 | 587 |
| `LOG.info("value {}", v)` | 14 905 | 9 630 | 3 815 | 3 461 | 3 144 |

## Findings to follow up

- Java 8 still pays for a full `Throwable` stack trace and for `Class.forName` on every frame up to the caller:
  `sun.reflect.Reflection.getCallerClass(int)` and per-frame access to the stack trace could avoid both.
- On Java 9+ the walk itself is a few microseconds. Part of it may be `StackFrame.toStackTraceElement()`, which the
  call site needs only when a logger asks for `LogEntry.getCaller()`; measure before building it lazily.
- A disabled call with an explicit class (7 to 12 ns) costs more than one without a class at a level that is off
  everywhere (2 ns): it skips `mayBeEnabled` and pays for the per-class lookup in `ConsoleLogger.isEnabled`.

## Raw output

JMH printed the numbers with the decimal comma of the system locale. "Error" is the 99.9% confidence interval.

<details>
<summary>Current (after #24), JDK 8</summary>

```
Benchmark                                         (depth)  Mode  Cnt       Score       Error  Units
ConsoleLoggerBenchmark.infoWithClass                  N/A  avgt    5     955,659 ±   192,632  ns/op
ConsoleLoggerBenchmark.infoWithoutClass               N/A  avgt    5   14904,937 ±  2391,008  ns/op
DisabledCallBenchmark.baseline                        N/A  avgt    5       0,271 ±     0,038  ns/op
DisabledCallBenchmark.isDebugEnabledWithClass         N/A  avgt    5      11,708 ±     1,475  ns/op
DisabledCallBenchmark.isDebugEnabledWithoutClass      N/A  avgt    5       3,227 ±     1,097  ns/op
DisabledCallBenchmark.offEverywhereWithClass          N/A  avgt    5      11,583 ±     1,200  ns/op
DisabledCallBenchmark.offEverywhereWithoutClass       N/A  avgt    5       1,981 ±     0,261  ns/op
DisabledCallBenchmark.onElsewhereWithClass            N/A  avgt    5      11,406 ±     1,006  ns/op
DisabledCallBenchmark.onElsewhereWithoutClass         N/A  avgt    5   13710,638 ±  2034,684  ns/op
StackDepthBenchmark.disabledWithoutClass               10  avgt    5   19989,511 ±  3679,665  ns/op
StackDepthBenchmark.disabledWithoutClass              100  avgt    5   65220,495 ± 11691,510  ns/op
StackDepthBenchmark.disabledWithoutClass              500  avgt    5  275718,590 ± 78727,305  ns/op
StackDepthBenchmark.enabledWithClass                   10  avgt    5     119,762 ±    34,214  ns/op
StackDepthBenchmark.enabledWithClass                  100  avgt    5     191,914 ±    32,472  ns/op
StackDepthBenchmark.enabledWithClass                  500  avgt    5     557,103 ±   166,684  ns/op
StackDepthBenchmark.enabledWithoutClass                10  avgt    5   19909,457 ±  4538,113  ns/op
StackDepthBenchmark.enabledWithoutClass               100  avgt    5   64750,441 ± 10268,815  ns/op
StackDepthBenchmark.enabledWithoutClass               500  avgt    5  273156,308 ± 61535,177  ns/op
StackDepthBenchmark.recursionOnly                      10  avgt    5       9,756 ±     3,872  ns/op
StackDepthBenchmark.recursionOnly                     100  avgt    5      78,358 ±     5,731  ns/op
StackDepthBenchmark.recursionOnly                     500  avgt    5     360,980 ±   121,308  ns/op
```

</details>

<details>
<summary>Current (after #24), JDK 11</summary>

```
Benchmark                                         (depth)  Mode  Cnt      Score      Error  Units
ConsoleLoggerBenchmark.infoWithClass                  N/A  avgt    5    983,027 ± 1417,688  ns/op
ConsoleLoggerBenchmark.infoWithoutClass               N/A  avgt    5   9629,858 ± 2735,295  ns/op
DisabledCallBenchmark.baseline                        N/A  avgt    5      0,552 ±    0,107  ns/op
DisabledCallBenchmark.isDebugEnabledWithClass         N/A  avgt    5      8,463 ±    3,305  ns/op
DisabledCallBenchmark.isDebugEnabledWithoutClass      N/A  avgt    5      2,678 ±    0,247  ns/op
DisabledCallBenchmark.offEverywhereWithClass          N/A  avgt    5      8,265 ±    1,540  ns/op
DisabledCallBenchmark.offEverywhereWithoutClass       N/A  avgt    5      2,068 ±    0,117  ns/op
DisabledCallBenchmark.onElsewhereWithClass            N/A  avgt    5      8,745 ±    0,960  ns/op
DisabledCallBenchmark.onElsewhereWithoutClass         N/A  avgt    5   8523,125 ±  971,344  ns/op
StackDepthBenchmark.disabledWithoutClass               10  avgt    5   8451,205 ±  982,704  ns/op
StackDepthBenchmark.disabledWithoutClass              100  avgt    5   8601,846 ±  863,951  ns/op
StackDepthBenchmark.disabledWithoutClass              500  avgt    5   9319,249 ± 1032,207  ns/op
StackDepthBenchmark.enabledWithClass                   10  avgt    5     60,523 ±    4,850  ns/op
StackDepthBenchmark.enabledWithClass                  100  avgt    5    128,134 ±    9,030  ns/op
StackDepthBenchmark.enabledWithClass                  500  avgt    5    499,457 ±   43,960  ns/op
StackDepthBenchmark.enabledWithoutClass                10  avgt    5   8287,023 ± 1064,800  ns/op
StackDepthBenchmark.enabledWithoutClass               100  avgt    5   9303,402 ± 2616,969  ns/op
StackDepthBenchmark.enabledWithoutClass               500  avgt    5  11687,823 ± 2629,770  ns/op
StackDepthBenchmark.recursionOnly                      10  avgt    5      9,751 ±    6,005  ns/op
StackDepthBenchmark.recursionOnly                     100  avgt    5     81,814 ±   20,150  ns/op
StackDepthBenchmark.recursionOnly                     500  avgt    5    355,055 ±   49,942  ns/op
```

</details>

<details>
<summary>Current (after #24), JDK 17</summary>

```
Benchmark                                         (depth)  Mode  Cnt     Score      Error  Units
ConsoleLoggerBenchmark.infoWithClass                  N/A  avgt    5   844,301 ±  433,291  ns/op
ConsoleLoggerBenchmark.infoWithoutClass               N/A  avgt    5  3815,272 ±  906,511  ns/op
DisabledCallBenchmark.baseline                        N/A  avgt    5     0,431 ±    0,140  ns/op
DisabledCallBenchmark.isDebugEnabledWithClass         N/A  avgt    5     7,841 ±    2,369  ns/op
DisabledCallBenchmark.isDebugEnabledWithoutClass      N/A  avgt    5     1,278 ±    0,098  ns/op
DisabledCallBenchmark.offEverywhereWithClass          N/A  avgt    5     7,541 ±    1,862  ns/op
DisabledCallBenchmark.offEverywhereWithoutClass       N/A  avgt    5     1,541 ±    0,177  ns/op
DisabledCallBenchmark.onElsewhereWithClass            N/A  avgt    5    10,293 ±   19,534  ns/op
DisabledCallBenchmark.onElsewhereWithoutClass         N/A  avgt    5  2431,646 ±  258,536  ns/op
StackDepthBenchmark.disabledWithoutClass               10  avgt    5  2586,523 ±  429,440  ns/op
StackDepthBenchmark.disabledWithoutClass              100  avgt    5  3087,121 ±  769,989  ns/op
StackDepthBenchmark.disabledWithoutClass              500  avgt    5  4326,974 ±  617,039  ns/op
StackDepthBenchmark.enabledWithClass                   10  avgt    5    74,953 ±    9,098  ns/op
StackDepthBenchmark.enabledWithClass                  100  avgt    5   162,238 ±   16,935  ns/op
StackDepthBenchmark.enabledWithClass                  500  avgt    5   684,150 ±  383,021  ns/op
StackDepthBenchmark.enabledWithoutClass                10  avgt    5  2934,717 ±  506,973  ns/op
StackDepthBenchmark.enabledWithoutClass               100  avgt    5  3179,764 ± 1291,945  ns/op
StackDepthBenchmark.enabledWithoutClass               500  avgt    5  4162,927 ±  838,604  ns/op
StackDepthBenchmark.recursionOnly                      10  avgt    5     8,608 ±    0,579  ns/op
StackDepthBenchmark.recursionOnly                     100  avgt    5    80,696 ±    9,584  ns/op
StackDepthBenchmark.recursionOnly                     500  avgt    5   391,348 ±   27,683  ns/op
```

</details>

<details>
<summary>Current (after #24), JDK 21</summary>

```
Benchmark                                         (depth)  Mode  Cnt     Score     Error  Units
ConsoleLoggerBenchmark.infoWithClass                  N/A  avgt    5   674,940 ± 100,264  ns/op
ConsoleLoggerBenchmark.infoWithoutClass               N/A  avgt    5  3460,558 ± 415,794  ns/op
DisabledCallBenchmark.baseline                        N/A  avgt    5     0,338 ±   0,078  ns/op
DisabledCallBenchmark.isDebugEnabledWithClass         N/A  avgt    5     6,563 ±   0,952  ns/op
DisabledCallBenchmark.isDebugEnabledWithoutClass      N/A  avgt    5     1,215 ±   0,127  ns/op
DisabledCallBenchmark.offEverywhereWithClass          N/A  avgt    5     7,194 ±   1,540  ns/op
DisabledCallBenchmark.offEverywhereWithoutClass       N/A  avgt    5     1,617 ±   0,245  ns/op
DisabledCallBenchmark.onElsewhereWithClass            N/A  avgt    5     7,332 ±   1,992  ns/op
DisabledCallBenchmark.onElsewhereWithoutClass         N/A  avgt    5  2603,038 ± 277,066  ns/op
StackDepthBenchmark.disabledWithoutClass               10  avgt    5  2648,681 ± 217,606  ns/op
StackDepthBenchmark.disabledWithoutClass              100  avgt    5  2914,793 ± 324,012  ns/op
StackDepthBenchmark.disabledWithoutClass              500  avgt    5  4251,627 ± 602,112  ns/op
StackDepthBenchmark.enabledWithClass                   10  avgt    5    59,711 ±   8,287  ns/op
StackDepthBenchmark.enabledWithClass                  100  avgt    5   156,250 ±  19,141  ns/op
StackDepthBenchmark.enabledWithClass                  500  avgt    5   612,397 ± 101,157  ns/op
StackDepthBenchmark.enabledWithoutClass                10  avgt    5  3046,654 ± 341,982  ns/op
StackDepthBenchmark.enabledWithoutClass               100  avgt    5  3308,980 ± 842,578  ns/op
StackDepthBenchmark.enabledWithoutClass               500  avgt    5  4799,679 ± 802,783  ns/op
StackDepthBenchmark.recursionOnly                      10  avgt    5     9,233 ±   2,119  ns/op
StackDepthBenchmark.recursionOnly                     100  avgt    5    92,322 ±  17,251  ns/op
StackDepthBenchmark.recursionOnly                     500  avgt    5   491,580 ± 419,600  ns/op
```

</details>

<details>
<summary>Current (after #24), JDK 25</summary>

```
Benchmark                                         (depth)  Mode  Cnt     Score      Error  Units
ConsoleLoggerBenchmark.infoWithClass                  N/A  avgt    5   586,928 ±   88,221  ns/op
ConsoleLoggerBenchmark.infoWithoutClass               N/A  avgt    5  3144,299 ±  648,872  ns/op
DisabledCallBenchmark.baseline                        N/A  avgt    5     0,385 ±    0,116  ns/op
DisabledCallBenchmark.isDebugEnabledWithClass         N/A  avgt    5     8,301 ±    5,459  ns/op
DisabledCallBenchmark.isDebugEnabledWithoutClass      N/A  avgt    5     1,515 ±    0,286  ns/op
DisabledCallBenchmark.offEverywhereWithClass          N/A  avgt    5     8,876 ±    1,261  ns/op
DisabledCallBenchmark.offEverywhereWithoutClass       N/A  avgt    5     1,988 ±    0,399  ns/op
DisabledCallBenchmark.onElsewhereWithClass            N/A  avgt    5    10,569 ±    1,891  ns/op
DisabledCallBenchmark.onElsewhereWithoutClass         N/A  avgt    5  2820,784 ± 1647,089  ns/op
StackDepthBenchmark.disabledWithoutClass               10  avgt    5  2696,257 ± 1228,733  ns/op
StackDepthBenchmark.disabledWithoutClass              100  avgt    5  3162,586 ±  687,611  ns/op
StackDepthBenchmark.disabledWithoutClass              500  avgt    5  4892,764 ± 1933,553  ns/op
StackDepthBenchmark.enabledWithClass                   10  avgt    5    67,571 ±   13,527  ns/op
StackDepthBenchmark.enabledWithClass                  100  avgt    5   182,716 ±   48,168  ns/op
StackDepthBenchmark.enabledWithClass                  500  avgt    5   745,066 ±  139,337  ns/op
StackDepthBenchmark.enabledWithoutClass                10  avgt    5  3392,175 ± 1110,961  ns/op
StackDepthBenchmark.enabledWithoutClass               100  avgt    5  3187,099 ±  583,581  ns/op
StackDepthBenchmark.enabledWithoutClass               500  avgt    5  4757,326 ± 1284,502  ns/op
StackDepthBenchmark.recursionOnly                      10  avgt    5     9,280 ±    2,059  ns/op
StackDepthBenchmark.recursionOnly                     100  avgt    5   107,514 ±   31,368  ns/op
StackDepthBenchmark.recursionOnly                     500  avgt    5   584,313 ±   88,443  ns/op
```

</details>

<details>
<summary>Before #24 (daed6b4), JDK 8</summary>

```
Benchmark                                         (depth)  Mode  Cnt       Score       Error  Units
ConsoleLoggerBenchmark.infoWithClass                  N/A  avgt    5    1047,360 ±   351,835  ns/op
ConsoleLoggerBenchmark.infoWithoutClass               N/A  avgt    5   14306,445 ±  3463,145  ns/op
DisabledCallBenchmark.baseline                        N/A  avgt    5       0,293 ±     0,073  ns/op
DisabledCallBenchmark.isDebugEnabledWithClass         N/A  avgt    5      11,763 ±     1,665  ns/op
DisabledCallBenchmark.isDebugEnabledWithoutClass      N/A  avgt    5       3,089 ±     0,418  ns/op
DisabledCallBenchmark.offEverywhereWithClass          N/A  avgt    5      11,400 ±     1,721  ns/op
DisabledCallBenchmark.offEverywhereWithoutClass       N/A  avgt    5       2,082 ±     0,505  ns/op
DisabledCallBenchmark.onElsewhereWithClass            N/A  avgt    5      11,678 ±     0,982  ns/op
DisabledCallBenchmark.onElsewhereWithoutClass         N/A  avgt    5   12394,563 ±  3188,021  ns/op
StackDepthBenchmark.disabledWithoutClass               10  avgt    5   17963,471 ±  2784,835  ns/op
StackDepthBenchmark.disabledWithoutClass              100  avgt    5   61101,776 ±  5217,089  ns/op
StackDepthBenchmark.disabledWithoutClass              500  avgt    5  249753,203 ±  4181,108  ns/op
StackDepthBenchmark.enabledWithClass                   10  avgt    5     115,451 ±    10,401  ns/op
StackDepthBenchmark.enabledWithClass                  100  avgt    5     174,729 ±    21,625  ns/op
StackDepthBenchmark.enabledWithClass                  500  avgt    5     521,384 ±    50,544  ns/op
StackDepthBenchmark.enabledWithoutClass                10  avgt    5   17664,083 ±  1773,899  ns/op
StackDepthBenchmark.enabledWithoutClass               100  avgt    5   60436,862 ±  2211,362  ns/op
StackDepthBenchmark.enabledWithoutClass               500  avgt    5  261555,114 ± 41829,689  ns/op
StackDepthBenchmark.recursionOnly                      10  avgt    5       8,802 ±     0,880  ns/op
StackDepthBenchmark.recursionOnly                     100  avgt    5      78,185 ±    16,489  ns/op
StackDepthBenchmark.recursionOnly                     500  avgt    5     338,129 ±    26,664  ns/op
```

</details>

<details>
<summary>Before #24 (daed6b4), JDK 11</summary>

```
Benchmark                                         (depth)  Mode  Cnt      Score       Error  Units
ConsoleLoggerBenchmark.infoWithClass                  N/A  avgt    5    757,707 ±   182,969  ns/op
ConsoleLoggerBenchmark.infoWithoutClass               N/A  avgt    5  11245,874 ±  1483,908  ns/op
DisabledCallBenchmark.baseline                        N/A  avgt    5      0,678 ±     0,223  ns/op
DisabledCallBenchmark.isDebugEnabledWithClass         N/A  avgt    5      9,501 ±     1,140  ns/op
DisabledCallBenchmark.isDebugEnabledWithoutClass      N/A  avgt    5      3,232 ±     0,676  ns/op
DisabledCallBenchmark.offEverywhereWithClass          N/A  avgt    5      9,167 ±     0,849  ns/op
DisabledCallBenchmark.offEverywhereWithoutClass       N/A  avgt    5      2,223 ±     0,757  ns/op
DisabledCallBenchmark.onElsewhereWithClass            N/A  avgt    5      9,280 ±     2,552  ns/op
DisabledCallBenchmark.onElsewhereWithoutClass         N/A  avgt    5   9156,785 ±  2596,280  ns/op
StackDepthBenchmark.disabledWithoutClass               10  avgt    5  13008,865 ± 10850,019  ns/op
StackDepthBenchmark.disabledWithoutClass              100  avgt    5  29275,019 ±  2730,285  ns/op
StackDepthBenchmark.disabledWithoutClass              500  avgt    5  90023,192 ±  5652,739  ns/op
StackDepthBenchmark.enabledWithClass                   10  avgt    5     80,953 ±    14,730  ns/op
StackDepthBenchmark.enabledWithClass                  100  avgt    5    131,635 ±     7,744  ns/op
StackDepthBenchmark.enabledWithClass                  500  avgt    5    703,092 ±  1003,118  ns/op
StackDepthBenchmark.enabledWithoutClass                10  avgt    5  10726,629 ±   324,740  ns/op
StackDepthBenchmark.enabledWithoutClass               100  avgt    5  25218,594 ±  1455,522  ns/op
StackDepthBenchmark.enabledWithoutClass               500  avgt    5  85633,596 ±  5676,632  ns/op
StackDepthBenchmark.recursionOnly                      10  avgt    5      8,075 ±     0,296  ns/op
StackDepthBenchmark.recursionOnly                     100  avgt    5     69,936 ±     4,739  ns/op
StackDepthBenchmark.recursionOnly                     500  avgt    5    333,649 ±     7,919  ns/op
```

</details>

<details>
<summary>Before #24 (daed6b4), JDK 17</summary>

```
Benchmark                                         (depth)  Mode  Cnt      Score       Error  Units
ConsoleLoggerBenchmark.infoWithClass                  N/A  avgt    5    617,310 ±    54,014  ns/op
ConsoleLoggerBenchmark.infoWithoutClass               N/A  avgt    5   8980,947 ±   433,533  ns/op
DisabledCallBenchmark.baseline                        N/A  avgt    5      0,308 ±     0,019  ns/op
DisabledCallBenchmark.isDebugEnabledWithClass         N/A  avgt    5      6,366 ±     0,687  ns/op
DisabledCallBenchmark.isDebugEnabledWithoutClass      N/A  avgt    5      1,222 ±     0,182  ns/op
DisabledCallBenchmark.offEverywhereWithClass          N/A  avgt    5      7,045 ±     2,273  ns/op
DisabledCallBenchmark.offEverywhereWithoutClass       N/A  avgt    5      1,904 ±     0,650  ns/op
DisabledCallBenchmark.onElsewhereWithClass            N/A  avgt    5      7,012 ±     1,429  ns/op
DisabledCallBenchmark.onElsewhereWithoutClass         N/A  avgt    5   8623,728 ±  1093,142  ns/op
StackDepthBenchmark.disabledWithoutClass               10  avgt    5  10454,352 ±  1535,814  ns/op
StackDepthBenchmark.disabledWithoutClass              100  avgt    5  25764,403 ±  5589,870  ns/op
StackDepthBenchmark.disabledWithoutClass              500  avgt    5  90469,455 ± 24886,146  ns/op
StackDepthBenchmark.enabledWithClass                   10  avgt    5     69,679 ±     6,823  ns/op
StackDepthBenchmark.enabledWithClass                  100  avgt    5    156,121 ±    37,364  ns/op
StackDepthBenchmark.enabledWithClass                  500  avgt    5    560,342 ±    65,606  ns/op
StackDepthBenchmark.enabledWithoutClass                10  avgt    5  10542,848 ±   730,922  ns/op
StackDepthBenchmark.enabledWithoutClass               100  avgt    5  26226,374 ±  6340,203  ns/op
StackDepthBenchmark.enabledWithoutClass               500  avgt    5  87997,735 ± 10599,754  ns/op
StackDepthBenchmark.recursionOnly                      10  avgt    5      8,606 ±     0,594  ns/op
StackDepthBenchmark.recursionOnly                     100  avgt    5     80,824 ±     8,874  ns/op
StackDepthBenchmark.recursionOnly                     500  avgt    5    386,901 ±    32,752  ns/op
```

</details>

<details>
<summary>Before #24 (daed6b4), JDK 21</summary>

```
Benchmark                                         (depth)  Mode  Cnt      Score       Error  Units
ConsoleLoggerBenchmark.infoWithClass                  N/A  avgt    5    664,132 ±    35,057  ns/op
ConsoleLoggerBenchmark.infoWithoutClass               N/A  avgt    5  10440,751 ±  1721,731  ns/op
DisabledCallBenchmark.baseline                        N/A  avgt    5      0,312 ±     0,021  ns/op
DisabledCallBenchmark.isDebugEnabledWithClass         N/A  avgt    5      6,287 ±     0,586  ns/op
DisabledCallBenchmark.isDebugEnabledWithoutClass      N/A  avgt    5      1,186 ±     0,054  ns/op
DisabledCallBenchmark.offEverywhereWithClass          N/A  avgt    5      6,895 ±     1,406  ns/op
DisabledCallBenchmark.offEverywhereWithoutClass       N/A  avgt    5      1,628 ±     0,143  ns/op
DisabledCallBenchmark.onElsewhereWithClass            N/A  avgt    5      6,666 ±     1,182  ns/op
DisabledCallBenchmark.onElsewhereWithoutClass         N/A  avgt    5   9736,103 ±   901,150  ns/op
StackDepthBenchmark.disabledWithoutClass               10  avgt    5  10237,072 ±  1853,005  ns/op
StackDepthBenchmark.disabledWithoutClass              100  avgt    5  25192,018 ±  8394,770  ns/op
StackDepthBenchmark.disabledWithoutClass              500  avgt    5  84220,015 ± 12120,680  ns/op
StackDepthBenchmark.enabledWithClass                   10  avgt    5     65,605 ±    21,539  ns/op
StackDepthBenchmark.enabledWithClass                  100  avgt    5    158,679 ±    35,056  ns/op
StackDepthBenchmark.enabledWithClass                  500  avgt    5    593,929 ±    39,375  ns/op
StackDepthBenchmark.enabledWithoutClass                10  avgt    5   9868,594 ±   606,467  ns/op
StackDepthBenchmark.enabledWithoutClass               100  avgt    5  24169,051 ±  3677,288  ns/op
StackDepthBenchmark.enabledWithoutClass               500  avgt    5  84943,797 ±  5066,775  ns/op
StackDepthBenchmark.recursionOnly                      10  avgt    5      7,797 ±     0,175  ns/op
StackDepthBenchmark.recursionOnly                     100  avgt    5     89,497 ±     9,717  ns/op
StackDepthBenchmark.recursionOnly                     500  avgt    5    405,449 ±    37,332  ns/op
```

</details>

<details>
<summary>Before #24 (daed6b4), JDK 25</summary>

```
Benchmark                                         (depth)  Mode  Cnt       Score       Error  Units
ConsoleLoggerBenchmark.infoWithClass                  N/A  avgt    5     561,870 ±    85,679  ns/op
ConsoleLoggerBenchmark.infoWithoutClass               N/A  avgt    5    8264,688 ±  1356,337  ns/op
DisabledCallBenchmark.baseline                        N/A  avgt    5       0,340 ±     0,083  ns/op
DisabledCallBenchmark.isDebugEnabledWithClass         N/A  avgt    5       6,936 ±     1,856  ns/op
DisabledCallBenchmark.isDebugEnabledWithoutClass      N/A  avgt    5       1,365 ±     0,204  ns/op
DisabledCallBenchmark.offEverywhereWithClass          N/A  avgt    5       8,195 ±     1,115  ns/op
DisabledCallBenchmark.offEverywhereWithoutClass       N/A  avgt    5       1,741 ±     0,336  ns/op
DisabledCallBenchmark.onElsewhereWithClass            N/A  avgt    5       8,026 ±     0,876  ns/op
DisabledCallBenchmark.onElsewhereWithoutClass         N/A  avgt    5    8111,036 ±  1501,485  ns/op
StackDepthBenchmark.disabledWithoutClass               10  avgt    5   10395,920 ±  1861,260  ns/op
StackDepthBenchmark.disabledWithoutClass              100  avgt    5   27555,917 ±  3914,954  ns/op
StackDepthBenchmark.disabledWithoutClass              500  avgt    5  104749,390 ± 14097,472  ns/op
StackDepthBenchmark.enabledWithClass                   10  avgt    5      51,195 ±    15,016  ns/op
StackDepthBenchmark.enabledWithClass                  100  avgt    5     148,820 ±    20,243  ns/op
StackDepthBenchmark.enabledWithClass                  500  avgt    5     590,622 ±    77,235  ns/op
StackDepthBenchmark.enabledWithoutClass                10  avgt    5   10066,500 ±  1166,754  ns/op
StackDepthBenchmark.enabledWithoutClass               100  avgt    5   26780,971 ±  1083,648  ns/op
StackDepthBenchmark.enabledWithoutClass               500  avgt    5  105276,567 ± 19664,838  ns/op
StackDepthBenchmark.recursionOnly                      10  avgt    5       8,128 ±     1,728  ns/op
StackDepthBenchmark.recursionOnly                     100  avgt    5      88,177 ±     7,498  ns/op
StackDepthBenchmark.recursionOnly                     500  avgt    5     406,984 ±    39,341  ns/op
```

</details>

<details>
<summary>Before #16 (8f4aaca), JDK 8, 11, 17, 21 and 25</summary>

```
JDK 8
Benchmark                                         Mode  Cnt      Score      Error  Units
DisabledCallBenchmark.baseline                    avgt    5      0,262 ±    0,097  ns/op
DisabledCallBenchmark.isDebugEnabledWithClass     avgt    5     10,925 ±    0,463  ns/op
DisabledCallBenchmark.isDebugEnabledWithoutClass  avgt    5  11825,875 ± 1388,991  ns/op
DisabledCallBenchmark.offEverywhereWithClass      avgt    5     11,039 ±    1,289  ns/op
DisabledCallBenchmark.offEverywhereWithoutClass   avgt    5  11364,957 ± 2152,465  ns/op
DisabledCallBenchmark.onElsewhereWithClass        avgt    5     10,853 ±    2,140  ns/op
DisabledCallBenchmark.onElsewhereWithoutClass     avgt    5  11288,479 ± 2290,891  ns/op

JDK 11
Benchmark                                         Mode  Cnt     Score      Error  Units
DisabledCallBenchmark.baseline                    avgt    5     0,526 ±    0,099  ns/op
DisabledCallBenchmark.isDebugEnabledWithClass     avgt    5     7,746 ±    0,383  ns/op
DisabledCallBenchmark.isDebugEnabledWithoutClass  avgt    5  8944,450 ± 1388,135  ns/op
DisabledCallBenchmark.offEverywhereWithClass      avgt    5     7,526 ±    0,513  ns/op
DisabledCallBenchmark.offEverywhereWithoutClass   avgt    5  8071,668 ±  182,062  ns/op
DisabledCallBenchmark.onElsewhereWithClass        avgt    5     8,840 ±    2,463  ns/op
DisabledCallBenchmark.onElsewhereWithoutClass     avgt    5  7969,236 ±  398,023  ns/op

JDK 17
Benchmark                                         Mode  Cnt     Score      Error  Units
DisabledCallBenchmark.baseline                    avgt    5     0,319 ±    0,042  ns/op
DisabledCallBenchmark.isDebugEnabledWithClass     avgt    5     6,669 ±    3,158  ns/op
DisabledCallBenchmark.isDebugEnabledWithoutClass  avgt    5  8450,965 ± 1193,048  ns/op
DisabledCallBenchmark.offEverywhereWithClass      avgt    5    21,960 ±  123,972  ns/op
DisabledCallBenchmark.offEverywhereWithoutClass   avgt    5  8638,813 ± 1454,495  ns/op
DisabledCallBenchmark.onElsewhereWithClass        avgt    5     8,607 ±    3,340  ns/op
DisabledCallBenchmark.onElsewhereWithoutClass     avgt    5  8571,744 ± 1040,553  ns/op

JDK 21
Benchmark                                         Mode  Cnt     Score      Error  Units
DisabledCallBenchmark.baseline                    avgt    5     0,325 ±    0,062  ns/op
DisabledCallBenchmark.isDebugEnabledWithClass     avgt    5     6,477 ±    1,075  ns/op
DisabledCallBenchmark.isDebugEnabledWithoutClass  avgt    5  9942,609 ±  978,590  ns/op
DisabledCallBenchmark.offEverywhereWithClass      avgt    5     6,782 ±    1,536  ns/op
DisabledCallBenchmark.offEverywhereWithoutClass   avgt    5  8535,132 ± 1625,666  ns/op
DisabledCallBenchmark.onElsewhereWithClass        avgt    5     7,003 ±    1,695  ns/op
DisabledCallBenchmark.onElsewhereWithoutClass     avgt    5  8642,543 ± 2561,007  ns/op

JDK 25
Benchmark                                         Mode  Cnt     Score      Error  Units
DisabledCallBenchmark.baseline                    avgt    5     0,347 ±    0,116  ns/op
DisabledCallBenchmark.isDebugEnabledWithClass     avgt    5     7,725 ±    3,957  ns/op
DisabledCallBenchmark.isDebugEnabledWithoutClass  avgt    5  8741,551 ± 1510,345  ns/op
DisabledCallBenchmark.offEverywhereWithClass      avgt    5     8,175 ±    3,649  ns/op
DisabledCallBenchmark.offEverywhereWithoutClass   avgt    5  8488,062 ± 4435,506  ns/op
DisabledCallBenchmark.onElsewhereWithClass        avgt    5     8,800 ±    5,592  ns/op
DisabledCallBenchmark.onElsewhereWithoutClass     avgt    5  9480,690 ± 3312,627  ns/op
```

</details>
