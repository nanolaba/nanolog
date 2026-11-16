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
- A first run on JDK 11 and 21 overlapped with an unrelated test run on the same machine (the empty benchmark took
  3 ± 20 ns instead of 0.3) and was discarded; the numbers below come from a second run on a quiet machine.
- "Current" is the code of commit `9729ae5` (1.3-SNAPSHOT). "Before #16" is commit `8f4aaca`, the last one before the
  level check moved in front of the stack walk, compiled on its own and measured with the same benchmark classes.
- In every benchmark the source class is `com.nanolaba.logging.benchmark.*`; "on elsewhere" means `DEBUG` is enabled
  only for another package (`-Dnanolog.level.com.acme.other=DEBUG`), so the level is enabled for some class but not for
  this one.

## Disabled calls

`DisabledCallBenchmark`, in nanoseconds per call. `ConsoleLogger` with `TRACE` and `DEBUG` switched off.

| Call | JDK 8 | JDK 11 | JDK 17 | JDK 21 | JDK 25 |
|---|---:|---:|---:|---:|---:|
| empty benchmark method, for reference | 0.29 | 0.68 | 0.31 | 0.31 | 0.34 |
| `LOG.debug("value {}", v)`, `DEBUG` off everywhere | 2.1 | 2.2 | 1.9 | 1.6 | 1.7 |
| `LOG.debug(Foo.class, "value {}", v)`, `DEBUG` off everywhere | 11.4 | 9.2 | 7.0 | 6.9 | 8.2 |
| `LOG.isDebugEnabled()`, `DEBUG` off everywhere | 3.1 | 3.2 | 1.2 | 1.2 | 1.4 |
| `LOG.isDebugEnabled(Foo.class)`, `DEBUG` off everywhere | 11.8 | 9.5 | 6.4 | 6.3 | 6.9 |
| `LOG.debug("value {}", v)`, `DEBUG` on elsewhere | 12 395 | 9 157 | 8 624 | 9 736 | 8 111 |
| `LOG.debug(Foo.class, "value {}", v)`, `DEBUG` on elsewhere | 11.7 | 9.3 | 7.0 | 6.7 | 8.0 |

## Before and after #16

The same `DisabledCallBenchmark` against the code before #16, in nanoseconds per call, before → after.

| Call | JDK 8 | JDK 11 | JDK 17 | JDK 21 | JDK 25 |
|---|---:|---:|---:|---:|---:|
| `LOG.debug("value {}", v)`, `DEBUG` off everywhere | 11 365 → 2.1 | 8 072 → 2.2 | 8 639 → 1.9 | 8 535 → 1.6 | 8 488 → 1.7 |
| `LOG.isDebugEnabled()`, `DEBUG` off everywhere | 11 826 → 3.1 | 8 944 → 3.2 | 8 451 → 1.2 | 9 943 → 1.2 | 8 742 → 1.4 |
| `LOG.debug("value {}", v)`, `DEBUG` on elsewhere | 11 288 → 12 395 | 7 969 → 9 157 | 8 572 → 8 624 | 8 643 → 9 736 | 9 481 → 8 111 |
| `LOG.debug(Foo.class, "value {}", v)`, `DEBUG` off everywhere | 11.0 → 11.4 | 7.5 → 9.2 | 22.0 ± 124 → 7.0 | 6.8 → 6.9 | 8.2 → 8.2 |

A level that is off for every class no longer walks the stack, which makes such a call 2 800 to 8 300 times cheaper.
A level enabled for some other package still needs the class, so that call costs about the same as before: the
differences in that row are within the error of the measurements. The 22.0 before #16 on JDK 17 is a noisy
measurement (error ± 124), not a real difference.

## Stack depth

`StackDepthBenchmark`: each call is made `depth` frames below the benchmark method, on top of the frames of JMH
itself. In microseconds per call.

| Call | Depth | JDK 8 | JDK 11 | JDK 17 | JDK 21 | JDK 25 |
|---|---:|---:|---:|---:|---:|---:|
| the recursion only, no logging | 10 | 0.009 | 0.008 | 0.009 | 0.008 | 0.008 |
| | 100 | 0.078 | 0.070 | 0.081 | 0.089 | 0.088 |
| | 500 | 0.34 | 0.33 | 0.39 | 0.41 | 0.41 |
| `LOG.debug("value {}", v)`, `DEBUG` on elsewhere | 10 | 18.0 | 13.0 ± 10.9 | 10.5 | 10.2 | 10.4 |
| | 100 | 61.1 | 29.3 | 25.8 | 25.2 | 27.6 |
| | 500 | 249.8 | 90.0 | 90.5 | 84.2 | 104.7 |
| `LOG.info("value {}", v)`, enabled, logger keeps the entry | 10 | 17.7 | 10.7 | 10.5 | 9.9 | 10.1 |
| | 100 | 60.4 | 25.2 | 26.2 | 24.2 | 26.8 |
| | 500 | 261.6 | 85.6 | 88.0 | 84.9 | 105.3 |
| `LOG.info(Foo.class, "value {}", v)`, enabled, logger keeps the entry | 10 | 0.12 | 0.08 | 0.07 | 0.07 | 0.05 |
| | 100 | 0.17 | 0.13 | 0.16 | 0.16 | 0.15 |
| | 500 | 0.52 | 0.70 ± 1.0 | 0.56 | 0.59 | 0.59 |

Caller detection dominates every call without a class whose level is enabled for some class. It grows linearly with
the depth of the stack, because `Throwable.getStackTrace()` builds an element for every frame, even though detection
needs only the first few: about 25 to 30 µs at a depth of 100 frames on JDK 11 to 25, a depth that code running inside a
framework reaches easily, and two to three times as much on JDK 8. With an explicit class the same call costs about
0.05 to 0.2 µs once the recursion of the benchmark is subtracted, whatever the depth. The two values marked with their
error are noisy measurements.

## ConsoleLogger

`ConsoleLoggerBenchmark`: an enabled `INFO` call through the default `ConsoleLogger`, formatted as usual and written to
a stream that discards it, so no terminal is involved. In nanoseconds per call.

| Call | JDK 8 | JDK 11 | JDK 17 | JDK 21 | JDK 25 |
|---|---:|---:|---:|---:|---:|
| `LOG.info(Foo.class, "value {}", v)` | 1 047 | 758 | 617 | 664 | 562 |
| `LOG.info("value {}", v)` | 14 306 | 11 246 | 8 981 | 10 441 | 8 265 |

## Findings to follow up

- Caller detection costs microseconds and grows with the stack: a stack walker that stops at the first frame outside
  Nanolog (#24) should make it independent of the depth.
- A disabled call with an explicit class (7 to 11 ns) costs more than one without a class at a level that is off
  everywhere (2 ns): it skips `mayBeEnabled` and pays for the per-class lookup in `ConsoleLogger.isEnabled`.

## Raw output

JMH printed the numbers with the decimal comma of the system locale. "Error" is the 99.9% confidence interval.

<details>
<summary>Current, JDK 8</summary>

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
<summary>Current, JDK 11</summary>

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
<summary>Current, JDK 17</summary>

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
<summary>Current, JDK 21</summary>

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
<summary>Current, JDK 25</summary>

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
