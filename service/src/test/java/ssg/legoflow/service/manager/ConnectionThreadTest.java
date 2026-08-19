package ssg.legoflow.service.manager;

import org.junit.jupiter.api.*;
import ssg.legoflow.blocks.Context;
import ssg.legoflow.service.*;
import ssg.legoflow.service.user.ServiceUser;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link ConnectionThread}.
 */
class ConnectionThreadTest {

    private static class FastConnectService extends AbstractService<String, String> {
        public FastConnectService(String name) {
            super(String.class, String.class, new ServiceDescriptor(name, "Fast"));
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
            super(String.class, String.class, new ServiceDescriptor(name, "Fail"));
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
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        @Override protected void doDisconnect(ServiceContext ctx) {}
        @SuppressWarnings("unchecked")
        @Override protected String[] convertToOutput(Context ctx, String... input) { return input; }
        @SuppressWarnings("unchecked")
        @Override protected String[] convertToInput(Context ctx, String... output) { return output; }
    }

    private ServiceContext createContext() {
        return new DefaultServiceContext(ServiceUser.anonymous());
    }

    @Test
    void testSuccessfulConnection() throws Exception {
        var svc = new FastConnectService("fast-conn");
        var ctx = createContext();
        
        ConnectionThread thread = new ConnectionThread(svc, ctx);
        CompletableFuture<Void> future = thread.start();
        
        future.get(5, TimeUnit.SECONDS);
        assertThat(future.isDone()).isTrue();
    }

    @Test
    void testFailedConnection() {
        var svc = new FailConnectService("fail-conn");
        var ctx = createContext();
        
        ConnectionThread thread = new ConnectionThread(svc, ctx);
        CompletableFuture<Void> future = thread.start();
        
        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS)).isInstanceOf(Exception.class);
    }

    @Test
    void testIsAliveInitiallyFalse() {
        var svc = new FastConnectService("alive-test");
        var ctx = createContext();
        
        ConnectionThread thread = new ConnectionThread(svc, ctx);
        assertThat(thread.isAlive()).isFalse();
    }

    @Test
    void testIsAliveAfterStart() throws Exception {
        var svc = new SlowConnectService("alive-start");
        var ctx = createContext();
        
        ConnectionThread thread = new ConnectionThread(svc, ctx);
        CompletableFuture<Void> future = thread.start();
        
        assertThat(thread.isAlive()).isTrue();
        future.get(5, TimeUnit.SECONDS);
    }

    @Test
    void testCancelBeforeStart() {
        var svc = new FastConnectService("cancel-before");
        var ctx = createContext();
        
        ConnectionThread thread = new ConnectionThread(svc, ctx);
        assertThatCode(thread::cancel).doesNotThrowAnyException();
    }

    @Test
    void testCancelAfterCompletion() throws Exception {
        var svc = new FastConnectService("cancel-after");
        var ctx = createContext();
        
        ConnectionThread thread = new ConnectionThread(svc, ctx);
        thread.start().get(5, TimeUnit.SECONDS);
        Thread.sleep(100);
        assertThatCode(thread::cancel).doesNotThrowAnyException();
    }

    @Test
    void testMultipleStartsWithNewService() throws Exception {
        var svc1 = new FastConnectService("multi-start-1");
        var svc2 = new FastConnectService("multi-start-2");
        var ctx = createContext();
        
        ConnectionThread thread1 = new ConnectionThread(svc1, ctx);
        thread1.start().get(5, TimeUnit.SECONDS);
        
        // Use a different service for second start (same object can't connect twice)
        ConnectionThread thread2 = new ConnectionThread(svc2, ctx);
        thread2.start().get(5, TimeUnit.SECONDS);
    }

    @Test
    void testConnectionSetsState() throws Exception {
        var svc = new FastConnectService("state-test");
        var ctx = createContext();
        
        ConnectionThread thread = new ConnectionThread(svc, ctx);
        thread.start().get(5, TimeUnit.SECONDS);
        
        // After successful connection, service should be READY
        assertThat(svc.getState()).isEqualTo(ssg.legoflow.blocks.ProcessorState.READY);
    }
}
