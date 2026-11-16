package com.nanolaba.logging.benchmark;

import com.nanolaba.logging.LOG;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

/**
 * How caller detection grows with the depth of the stack: each call is made {@code depth} frames below the benchmark
 * method. {@code recursionOnly} is the cost of the frames themselves.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class StackDepthBenchmark {

    @Param({"10", "100", "500"})
    public int depth;

    private int value = 42;

    private IntSupplier nothing;
    private IntSupplier debugWithoutClass;
    private IntSupplier infoWithoutClass;
    private IntSupplier infoWithClass;

    /**
     * {@code DEBUG} is on only for another package: a {@code DEBUG} call without a class walks the stack and is then
     * rejected.
     */
    @State(Scope.Benchmark)
    public static class OnElsewhere {

        @Setup
        public void install() {
            LOG.init(Loggers.debugOnlyFor("com.acme.other"));
        }
    }

    /**
     * Every entry is built and handed to a logger that only keeps it.
     */
    @State(Scope.Benchmark)
    public static class Keeping {

        @Setup
        public void install() {
            LOG.init(new Loggers.Keeping());
        }
    }

    @Setup
    public void prepare() {
        // created once, so the measured operation allocates no lambda
        nothing = () -> 1;
        debugWithoutClass = () -> {
            LOG.debug("value {}", value);
            return 1;
        };
        infoWithoutClass = () -> {
            LOG.info("value {}", value);
            return 1;
        };
        infoWithClass = () -> {
            LOG.info(StackDepthBenchmark.class, "value {}", value);
            return 1;
        };
    }

    private static int descend(int remaining, IntSupplier call) {
        return remaining == 0 ? call.getAsInt() : 1 + descend(remaining - 1, call);
    }

    @Benchmark
    public int recursionOnly() {
        return descend(depth, nothing);
    }

    @Benchmark
    public int disabledWithoutClass(OnElsewhere state) {
        return descend(depth, debugWithoutClass);
    }

    @Benchmark
    public int enabledWithoutClass(Keeping state) {
        return descend(depth, infoWithoutClass);
    }

    @Benchmark
    public int enabledWithClass(Keeping state) {
        return descend(depth, infoWithClass);
    }
}
