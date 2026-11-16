package com.nanolaba.logging.benchmark;

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
 * An enabled call through the default {@code ConsoleLogger}: formatting the message, the date and the line, without
 * the cost of a terminal, which a stream that discards everything leaves out.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ConsoleLoggerBenchmark {

    private int value = 42;

    @State(Scope.Benchmark)
    public static class ToNowhere {

        @Setup
        public void install() {
            LOG.init(Loggers.consoleToNowhere());
        }
    }

    @Benchmark
    public void infoWithoutClass(ToNowhere state) {
        LOG.info("value {}", value);
    }

    @Benchmark
    public void infoWithClass(ToNowhere state) {
        LOG.info(ConsoleLoggerBenchmark.class, "value {}", value);
    }
}
