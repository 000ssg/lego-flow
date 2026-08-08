package ssg.legoflow.benchmarks.service;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.DefaultContext;
import ssg.legoflow.blocks.ProcessorStatistics;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks for DP/DF (DataProcessor/DataFilter) pipeline overhead.
 *
 * Measures the cost of flowing data through the service pipeline:
 * - Direct codec operations (baseline)
 * - Pipeline with filters and convert methods
 * - ByteBuffer copy/reposition overhead in the service path
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(value = 1, jvmArgsAppend = {"-Xms256m", "-Xmx512m"})
@State(Scope.Benchmark)
public class PipelineOverheadBenchmark {

    private static final int SMALL_SIZE  = 128;
    private static final int MEDIUM_SIZE = 4096;
    private static final int LARGE_SIZE  = 65536;

    private Context ctx;
    private ByteBuffer smallBuffer;
    private ByteBuffer mediumBuffer;
    private ByteBuffer largeBuffer;

    @Setup(Level.Iteration)
    public void setup() {
        this.ctx = new DefaultContext();
        this.smallBuffer = allocateBuffer(SMALL_SIZE);
        this.mediumBuffer = allocateBuffer(MEDIUM_SIZE);
        this.largeBuffer = allocateBuffer(LARGE_SIZE);
    }

    /** Baseline: Direct ByteBuffer duplicate + read (minimal overhead). */
    @Benchmark
    public void directByteBufferCopy(Blackhole bh) {
        ByteBuffer dup = mediumBuffer.duplicate();
        byte[] data = new byte[dup.remaining()];
        dup.get(data);
        bh.consume(data.length);
    }

    /** Baseline: ByteBuffer get to byte array. */
    @Benchmark
    public void directByteBufferGet(Blackhole bh) {
        mediumBuffer.position(0);
        byte[] data = new byte[mediumBuffer.remaining()];
        mediumBuffer.get(data);
        bh.consume(data.length);
    }

    /** DP/DF Pipeline: Simulate consume path (filter -> convert -> filter -> accept). */
    @Benchmark
    public void pipelineConsumeSmall(Blackhole bh) {
        ByteBuffer[] input = new ByteBuffer[]{smallBuffer};
        ByteBuffer[] output = convertSimulated(input);
        for (ByteBuffer buf : output) {
            if (buf != null) bh.consume(buf.remaining());
        }
    }

    /** DP/DF Pipeline: Medium payload consume path. */
    @Benchmark
    public void pipelineConsumeMedium(Blackhole bh) {
        ByteBuffer[] input = new ByteBuffer[]{mediumBuffer};
        ByteBuffer[] output = convertSimulated(input);
        for (ByteBuffer buf : output) {
            if (buf != null) bh.consume(buf.remaining());
        }
    }

    /** DP/DF Pipeline: Large payload consume path (64 KB). */
    @Benchmark
    public void pipelineConsumeLarge(Blackhole bh) {
        ByteBuffer[] input = new ByteBuffer[]{largeBuffer};
        ByteBuffer[] output = convertSimulated(input);
        for (ByteBuffer buf : output) {
            if (buf != null) bh.consume(buf.remaining());
        }
    }

    /** DP/DF Pipeline: Multi-buffer consume path (simulates batch processing). */
    @Benchmark
    public void pipelineConsumeBatch(Blackhole bh) {
        ByteBuffer[] input = new ByteBuffer[]{smallBuffer, mediumBuffer, smallBuffer};
        ByteBuffer[] output = convertSimulated(input);
        for (ByteBuffer buf : output) {
            if (buf != null) bh.consume(buf.remaining());
        }
    }

    /** Filter chain: 5 pass-through filters in sequence. */
    @Benchmark
    public void filterChainPassThrough(Blackhole bh) {
        ByteBuffer[] data = new ByteBuffer[]{mediumBuffer};
        for (int i = 0; i < 5; i++) {
            data = filterPassThrough(data);
        }
        bh.consume(data.length);
    }

    /** Filter chain: 10 pass-through filters in sequence. */
    @Benchmark
    public void filterChainTenFilters(Blackhole bh) {
        ByteBuffer[] data = new ByteBuffer[]{mediumBuffer};
        for (int i = 0; i < 10; i++) {
            data = filterPassThrough(data);
        }
        bh.consume(data.length);
    }

    /** Statistics recording overhead (per-operation tracking). */
    @Benchmark
    public void statisticsRecord(Blackhole bh) {
        ProcessorStatistics stats = ctx.getStatistics();
        stats.recordIn(ByteBuffer.class, 1, mediumBuffer.remaining());
        stats.recordOut(ByteBuffer.class, 1, mediumBuffer.remaining());
        bh.consume(stats.getInAmount(ByteBuffer.class));
    }

    /** Simulates convertToOutput: takes input ByteBuffers, returns output ByteBuffers. */
    private ByteBuffer[] convertSimulated(ByteBuffer[] input) {
        if (input == null || input.length == 0) return new ByteBuffer[0];
        ByteBuffer[] result = new ByteBuffer[input.length];
        for (int i = 0; i < input.length; i++) {
            ByteBuffer buf = input[i];
            if (buf != null && buf.hasRemaining()) {
                result[i] = buf.duplicate();
            } else {
                result[i] = null;
            }
        }
        return result;
    }

    /** Simulates a pass-through DataFilter. */
    private ByteBuffer[] filterPassThrough(ByteBuffer[] data) {
        if (data == null || data.length == 0) return new ByteBuffer[0];
        for (ByteBuffer buf : data) {
            if (buf == null) return new ByteBuffer[0];
        }
        return data;
    }

    /** Allocate a ByteBuffer with deterministic content. */
    private static ByteBuffer allocateBuffer(int size) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) (i % 128);
        }
        return ByteBuffer.wrap(bytes);
    }
}
