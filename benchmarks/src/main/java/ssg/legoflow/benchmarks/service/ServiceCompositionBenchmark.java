package ssg.legoflow.benchmarks.service;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import ssg.legoflow.blocks.AbstractDataProcessor;
import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.DefaultContext;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.DefaultServiceContext;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks for ServicesManager composition overhead.
 *
 * Measures:
 * - Service registration cost (single vs multiple)
 * - Service lifecycle management (connect/disconnect)
 * - Data flow through composed services
 * - Overhead of managing service dependencies and ordering
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(value = 1, jvmArgsAppend = {"-Xms256m", "-Xmx512m"})
@State(Scope.Benchmark)
public class ServiceCompositionBenchmark {

    private static final int SERVICE_COUNT_SMALL = 3;
    private static final int SERVICE_COUNT_MEDIUM = 10;
    private static final int SERVICE_COUNT_LARGE = 25;

    private Context ctx;
    private ServiceContext svcCtx;
    private ByteBuffer testData;
    private List<BenchmarkService> servicesSmall;
    private List<BenchmarkService> servicesMedium;
    private List<BenchmarkService> servicesLarge;

    @Setup(Level.Iteration)
    public void setup() {
        this.ctx = new DefaultContext();
        var user = new ssg.legoflow.service.user.SimpleServiceUser(
                "bench-01", "Benchmark User", 
                ssg.legoflow.service.user.UserType.ANONYMOUS, Set.of());
        this.svcCtx = new DefaultServiceContext(user);
        this.testData = allocateBuffer(1024);

        // Create service pools of different sizes
        this.servicesSmall = createServices(SERVICE_COUNT_SMALL);
        this.servicesMedium = createServices(SERVICE_COUNT_MEDIUM);
        this.servicesLarge = createServices(SERVICE_COUNT_LARGE);
    }

    /**
     * Service registration: Register a single service.
     */
    @Benchmark
    public void registerSingleService(Blackhole bh) {
        BenchmarkService svc = new BenchmarkService("bench-1");
        bh.consume(svc.getDescriptor().name());
    }

    /**
     * Service registration: Register 3 services (small composition).
     */
    @Benchmark
    public void registerThreeServices(Blackhole bh) {
        for (BenchmarkService svc : servicesSmall) {
            bh.consume(svc.getDescriptor().name());
        }
    }

    /**
     * Service registration: Register 10 services (medium composition).
     */
    @Benchmark
    public void registerTenServices(Blackhole bh) {
        for (BenchmarkService svc : servicesMedium) {
            bh.consume(svc.getDescriptor().name());
        }
    }

    /**
     * Service registration: Register 25 services (large composition).
     */
    @Benchmark
    public void registerTwentyFiveServices(Blackhole bh) {
        for (BenchmarkService svc : servicesLarge) {
            bh.consume(svc.getDescriptor().name());
        }
    }

    /**
     * Service lifecycle: Connect a single service.
     */
    @Benchmark
    public void connectSingleService(Blackhole bh) {
        BenchmarkService svc = new BenchmarkService("connect-1");
        svc.connect(svcCtx);
        bh.consume(svc.isConnected());
    }

    /**
     * Service lifecycle: Connect 3 services sequentially.
     */
    @Benchmark
    public void connectThreeServices(Blackhole bh) {
        for (int i = 0; i < SERVICE_COUNT_SMALL; i++) {
            BenchmarkService svc = new BenchmarkService("conn-" + i);
            svc.connect(svcCtx);
            bh.consume(svc.isConnected());
        }
    }

    /**
     * Service lifecycle: Connect 10 services sequentially.
     */
    @Benchmark
    public void connectTenServices(Blackhole bh) {
        for (int i = 0; i < SERVICE_COUNT_MEDIUM; i++) {
            BenchmarkService svc = new BenchmarkService("conn-" + i);
            svc.connect(svcCtx);
            bh.consume(svc.isConnected());
        }
    }

    /**
     * Service lifecycle: Connect 25 services sequentially.
     */
    @Benchmark
    public void connectTwentyFiveServices(Blackhole bh) {
        for (int i = 0; i < SERVICE_COUNT_LARGE; i++) {
            BenchmarkService svc = new BenchmarkService("conn-" + i);
            svc.connect(svcCtx);
            bh.consume(svc.isConnected());
        }
    }

    /**
     * Data flow: Single service consume path.
     */
    @Benchmark
    public void dataFlowSingle(Blackhole bh) {
        BenchmarkService svc = new BenchmarkService("flow-1");
        svc.connect(svcCtx);
        svc.consume(ctx, testData);
        var stats = svc.getStatistics();
        bh.consume(stats.getInAmount(ByteBuffer.class));
    }

    /**
     * Data flow: Chain 3 services (pipeline).
     */
    @Benchmark
    public void dataFlowChainThree(Blackhole bh) {
        BenchmarkService svc1 = new BenchmarkService("chain-1");
        BenchmarkService svc2 = new BenchmarkService("chain-2");
        BenchmarkService svc3 = new BenchmarkService("chain-3");
        svc1.connect(svcCtx);
        svc2.connect(svcCtx);
        svc3.connect(svcCtx);
        // Flow through each service
        svc1.consume(ctx, testData);
        svc2.consume(ctx, testData);
        svc3.consume(ctx, testData);
        bh.consume(svc1.getStatistics().getInAmount(ByteBuffer.class));
    }

    /**
     * Data flow: Fan-out to 10 services (broadcast).
     */
    @Benchmark
    public void dataFlowFanOutTen(Blackhole bh) {
        List<BenchmarkService> svcs = new ArrayList<>();
        for (int i = 0; i < SERVICE_COUNT_MEDIUM; i++) {
            BenchmarkService svc = new BenchmarkService("fanout-" + i);
            svc.connect(svcCtx);
            svcs.add(svc);
            svc.consume(ctx, testData);
        }
        long totalBytes = 0;
        for (BenchmarkService svc : svcs) {
            totalBytes += svc.getStatistics().getInAmount(ByteBuffer.class);
        }
        bh.consume(totalBytes);
    }

    /**
     * Service dependency resolution: Build dependency graph.
     */
    @Benchmark
    public void resolveDependenciesSmall(Blackhole bh) {
        List<BenchmarkService> svcs = createServicesWithDeps(SERVICE_COUNT_MEDIUM);
        // Simulate topological sort for startup ordering
        for (int i = 0; i < svcs.size(); i++) {
            BenchmarkService svc = svcs.get(i);
            bh.consume(svc.getDescriptor().name());
            bh.consume(svc.getDependencies().size());
        }
    }

    // -- Helper Methods -------------------------------------------------------------

    /** Create a list of benchmark services. */
    private List<BenchmarkService> createServices(int count) {
        List<BenchmarkService> svcs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            svcs.add(new BenchmarkService("bench-" + i));
        }
        return svcs;
    }

    /** Create services with dependency ordering. */
    private List<BenchmarkService> createServicesWithDeps(int count) {
        List<BenchmarkService> svcs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BenchmarkService svc = new BenchmarkService("dep-" + i, Math.max(0, i - 1));
            svcs.add(svc);
        }
        return svcs;
    }

    /** Allocate a ByteBuffer with deterministic content. */
    private static ByteBuffer allocateBuffer(int size) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) (i % 128);
        }
        return ByteBuffer.wrap(bytes);
    }

    // -- BenchmarkService: Minimal service implementation for benchmarking ----------

    /** Lightweight service that mimics AbstractService behavior without network I/O. */
    private static class BenchmarkService extends AbstractService<ByteBuffer, ByteBuffer> {

        BenchmarkService(String name) {
            super(ByteBuffer.class, ByteBuffer.class,
                    new ServiceDescriptor(name, "Benchmark Service", 100, new ArrayList<>()));
        }

        BenchmarkService(String name, int depIndex) {
            super(ByteBuffer.class, ByteBuffer.class,
                    new ServiceDescriptor(name, "Benchmark Service", 100, List.of("dep-" + depIndex)));
        }

        @Override
        protected void doConnect(ServiceContext ctx) {}

        @Override
        protected void doDisconnect(ServiceContext ctx) {}

        @Override
        protected ByteBuffer[] convertToOutput(Context ctx, ByteBuffer... input) {
            // Pass-through: duplicate each buffer
            if (input == null || input.length == 0) return new ByteBuffer[0];
            ByteBuffer[] result = new ByteBuffer[input.length];
            for (int i = 0; i < input.length; i++) {
                if (input[i] != null && input[i].hasRemaining()) {
                    result[i] = input[i].duplicate();
                }
            }
            return result;
        }

        @Override
        protected ByteBuffer[] convertToInput(Context ctx, ByteBuffer... output) {
            // Reverse: duplicate each buffer
            if (output == null || output.length == 0) return new ByteBuffer[0];
            ByteBuffer[] result = new ByteBuffer[output.length];
            for (int i = 0; i < output.length; i++) {
                if (output[i] != null && output[i].hasRemaining()) {
                    result[i] = output[i].duplicate();
                }
            }
            return result;
        }
    }
}
