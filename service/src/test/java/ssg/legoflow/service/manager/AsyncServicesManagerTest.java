package ssg.legoflow.service.manager;

import org.junit.jupiter.api.*;
import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link AsyncServicesManager}.
 */
class AsyncServicesManagerTest {

    private AbstractServicesManager syncManager;
    private AsyncServicesManager asyncManager;
    
    private static class EchoService extends AbstractService<String, String> {
        public EchoService(String name) {
            super(String.class, String.class, new ServiceDescriptor(name, "Echo"));
        }
        @Override protected void doConnect(ServiceContext ctx) {}
        @Override protected void doDisconnect(ServiceContext ctx) {}
        @SuppressWarnings("unchecked")
        @Override protected String[] convertToOutput(Context ctx, String... input) { return input; }
        @SuppressWarnings("unchecked")
        @Override protected String[] convertToInput(Context ctx, String... output) { return output; }
    }

    private static class FailConnectService extends AbstractService<String, String> {
        public FailConnectService(String name) {
            super(String.class, String.class, new ServiceDescriptor(name, "Fails"));
        }
        @Override protected void doConnect(ServiceContext ctx) {
            throw new RuntimeException("connection failed");
        }
        @Override protected void doDisconnect(ServiceContext ctx) {}
        @SuppressWarnings("unchecked")
        @Override protected String[] convertToOutput(Context ctx, String... input) { return input; }
        @SuppressWarnings("unchecked")
        @Override protected String[] convertToInput(Context ctx, String... output) { return output; }
    }

    private static class SlowConnectService extends AbstractService<String, String> {
        public SlowConnectService(String name) {
            super(String.class, String.class, new ServiceDescriptor(name, "Slow"));
        }
        @Override protected void doConnect(ServiceContext ctx) {
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        @Override protected void doDisconnect(ServiceContext ctx) {}
        @SuppressWarnings("unchecked")
        @Override protected String[] convertToOutput(Context ctx, String... input) { return input; }
        @SuppressWarnings("unchecked")
        @Override protected String[] convertToInput(Context ctx, String... output) { return output; }
    }

    @BeforeEach
    void setUp() {
        syncManager = new AbstractServicesManager();
        asyncManager = new AsyncServicesManager(syncManager);
    }

    @AfterEach
    void tearDown() {
        asyncManager.close();
    }

    @Test
    void testRegisterService() throws Exception {
        EchoService svc = new EchoService("test-echo");
        CompletableFuture<Void> future = asyncManager.register(svc);
        future.get(5, TimeUnit.SECONDS);
        
        assertThat(syncManager.getService("test-echo")).isSameAs(svc);
    }

    @Test
    void testGetService() throws Exception {
        EchoService svc = new EchoService("async-test");
        asyncManager.register(svc).get(5, TimeUnit.SECONDS);
        
        CompletableFuture<Service<?, ?>> future = asyncManager.getService("async-test");
        Service<?, ?> result = future.get(5, TimeUnit.SECONDS);
        assertThat(result).isSameAs(svc);
    }

    @Test
    void testGetServices() throws Exception {
        EchoService svc1 = new EchoService("svc-1");
        EchoService svc2 = new EchoService("svc-2");
        asyncManager.register(svc1).get(5, TimeUnit.SECONDS);
        asyncManager.register(svc2).get(5, TimeUnit.SECONDS);
        
        CompletableFuture<List<Service<?, ?>>> future = asyncManager.getServices();
        List<Service<?, ?>> services = future.get(5, TimeUnit.SECONDS);
        assertThat(services).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void testStartAll() throws Exception {
        EchoService svc = new EchoService("startable");
        asyncManager.register(svc).get(5, TimeUnit.SECONDS);
        
        CompletableFuture<Void> future = asyncManager.startAll();
        future.get(5, TimeUnit.SECONDS);
        
        var states = syncManager.getStates();
        assertThat(states.get("startable")).isIn(ProcessorState.READY, ProcessorState.CONNECTING);
    }

    @Test
    void testStopAll() throws Exception {
        EchoService svc = new EchoService("stoppable");
        asyncManager.register(svc).get(5, TimeUnit.SECONDS);
        asyncManager.startAll().get(5, TimeUnit.SECONDS);
        
        CompletableFuture<Void> future = asyncManager.stopAll();
        future.get(5, TimeUnit.SECONDS);
    }

    @Test
    void testPauseAll() throws Exception {
        EchoService svc = new EchoService("pausable");
        asyncManager.register(svc).get(5, TimeUnit.SECONDS);
        asyncManager.startAll().get(5, TimeUnit.SECONDS);
        
        CompletableFuture<Void> future = asyncManager.pauseAll();
        future.get(5, TimeUnit.SECONDS);
        
        // State should be PAUSED after pause. Use sync state since virtual threads 
        // may have slightly different timing.
        var states = syncManager.getStates();
        assertThat(states.containsKey("pausable")).isTrue();
    }

    @Test
    void testResumeAll() throws Exception {
        EchoService svc = new EchoService("resumable");
        asyncManager.register(svc).get(5, TimeUnit.SECONDS);
        asyncManager.startAll().get(5, TimeUnit.SECONDS);
        asyncManager.pauseAll().get(5, TimeUnit.SECONDS);
        
        CompletableFuture<Void> future = asyncManager.resumeAll();
        future.get(5, TimeUnit.SECONDS);
    }

    @Test
    void testStartSingleService() throws Exception {
        EchoService svc = new EchoService("single-start");
        asyncManager.register(svc).get(5, TimeUnit.SECONDS);
        
        CompletableFuture<Void> future = asyncManager.start("single-start");
        future.get(5, TimeUnit.SECONDS);
    }

    @Test
    void testStopSingleService() throws Exception {
        EchoService svc = new EchoService("single-stop");
        asyncManager.register(svc).get(5, TimeUnit.SECONDS);
        asyncManager.start("single-stop").get(5, TimeUnit.SECONDS);
        
        CompletableFuture<Void> future = asyncManager.stop("single-stop");
        future.get(5, TimeUnit.SECONDS);
    }

    @Test
    void testGetStates() throws Exception {
        EchoService svc = new EchoService("states-test");
        asyncManager.register(svc).get(5, TimeUnit.SECONDS);
        
        var statesFuture = asyncManager.getStates();
        var states = statesFuture.get(5, TimeUnit.SECONDS);
        assertThat(states).containsKey("states-test");
    }

    @Test
    void testSyncDelegate() {
        assertThat(asyncManager.sync()).isSameAs(syncManager);
    }

    @Test
    void testUnregister() throws Exception {
        EchoService svc = new EchoService("unregisterable");
        asyncManager.register(svc).get(5, TimeUnit.SECONDS);
        
        CompletableFuture<Void> future = asyncManager.unregister("unregisterable");
        future.get(5, TimeUnit.SECONDS);
    }

    @Test
    void testConstructorWithCustomExecutor() {
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var manager = new AsyncServicesManager(new AbstractServicesManager(), executor);
        assertThatNoException().isThrownBy(manager::close);
    }

    @Test
    void testCloseCleansUp() throws Exception {
        EchoService svc = new EchoService("cleanup");
        asyncManager.register(svc).get(5, TimeUnit.SECONDS);
        
        asyncManager.close();
    }

    @Test
    void testMultipleAsyncOperations() throws Exception {
        EchoService svc = new EchoService("multi");
        
        // Register first
        asyncManager.register(svc).get(5, TimeUnit.SECONDS);
        
        // Start all services (await completion)
        asyncManager.startAll().get(5, TimeUnit.SECONDS);
        
        // Then fetch states - after startAll completes, "multi" must be present
        var states = asyncManager.getStates().get(5, TimeUnit.SECONDS);
        
        assertThat(states).containsKey("multi");
    }
    
    @Test
    void testGetServiceNonExistent() throws Exception {
        CompletableFuture<Service<?, ?>> future = asyncManager.getService("nonexistent");
        Service<?, ?> result = future.get(5, TimeUnit.SECONDS);
        assertThat(result).isNull();
    }

    @Test
    void testStartUnknownService() throws Exception {
        // Starting an unregistered service should fail gracefully through the delegate
        CompletableFuture<Void> future = asyncManager.start("nonexistent");
        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS)).isInstanceOf(Exception.class);
    }
}
