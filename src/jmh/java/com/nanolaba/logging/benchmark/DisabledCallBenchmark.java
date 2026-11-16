package com.nanolaba.logging.benchmark;

import com.nanolaba.logging.ConsoleLogger;
import com.nanolaba.logging.LOG;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Calls at a disabled level: what a {@code DEBUG} statement costs in production. The benchmarks use only the 1.2 API,
 * so the same code measures older versions for comparison.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class DisabledCallBenchmark {

    private int value = 42;

    /**
     * {@code DEBUG} is off for every class, as with {@code nanolog.level=INFO}.
     */
    @State(Scope.Benchmark)
    public static class OffEverywhere {

        @Setup
        public void install() {
            LOG.init(new ConsoleLogger().setTraceEnabled(false).setDebugEnabled(false));
        }
    }

    /**
     * {@code DEBUG} is off for this class but on for another package, so only the class decides: a call without one
     * walks the stack before it is rejected.
     */
    @State(Scope.Benchmark)
    public static class OnElsewhere {

        @Setup
        public void install() {
            LOG.init(Loggers.debugOnlyFor("com.acme.other"));
        }
    }

    @Benchmark
    public void baseline() {
        // the cost of the benchmark itself
    }

    @Benchmark
    public void offEverywhereWithoutClass(OffEverywhere state) {
        LOG.debug("value {}", value);
    }

    @Benchmark
    public void offEverywhereWithClass(OffEverywhere state) {
        LOG.debug(DisabledCallBenchmark.class, "value {}", value);
    }

    @Benchmark
    public boolean isDebugEnabledWithoutClass(OffEverywhere state) {
        return LOG.isDebugEnabled();
    }

    @Benchmark
    public boolean isDebugEnabledWithClass(OffEverywhere state) {
        return LOG.isDebugEnabled(DisabledCallBenchmark.class);
    }

    @Benchmark
    public void onElsewhereWithoutClass(OnElsewhere state) {
        LOG.debug("value {}", value);
    }

    @Benchmark
    public void onElsewhereWithClass(OnElsewhere state) {
        LOG.debug(DisabledCallBenchmark.class, "value {}", value);
    }
}
