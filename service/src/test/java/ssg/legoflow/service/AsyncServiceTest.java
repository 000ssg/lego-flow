package ssg.legoflow.service;

import ssg.legoflow.service.demo.procedural.EchoService;
import ssg.legoflow.service.user.ServiceUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class AsyncServiceTest {

    private EchoService echo;
    private AsyncService<String, String> asyncEcho;
    private ServiceContext ctx;

    @BeforeEach
    void setUp() {
        echo = new EchoService();
        asyncEcho = echo.async();
        ctx = new DefaultServiceContext(ServiceUser.anonymous());
    }

    @Test
    void testAsyncConnect() throws Exception {
        asyncEcho.connect(ctx).get(5, TimeUnit.SECONDS);
        assertThat(echo.isConnected()).isTrue();
    }

    @Test
    void testAsyncDisconnect() throws Exception {
        asyncEcho.connect(ctx).get(5, TimeUnit.SECONDS);
        asyncEcho.disconnect(ctx).get(5, TimeUnit.SECONDS);
        assertThat(echo.isConnected()).isFalse();
    }

    @Test
    void testAsyncConsume() throws Exception {
        asyncEcho.connect(ctx).get(5, TimeUnit.SECONDS);
        asyncEcho.consume(ctx, "hello").get(5, TimeUnit.SECONDS);
        assertThat(echo.getStatistics().getInCount(String.class)).isEqualTo(1);
    }

    @Test
    void testAsyncSubmit() throws Exception {
        asyncEcho.connect(ctx).get(5, TimeUnit.SECONDS);
        asyncEcho.submit(ctx, "hello").get(5, TimeUnit.SECONDS);
        assertThat(echo.getStatistics().getInCount(String.class)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void testSyncReference() {
        assertThat(asyncEcho.sync()).isSameAs(echo);
    }

    @Test
    void testAsyncGetState() throws Exception {
        asyncEcho.connect(ctx).get(5, TimeUnit.SECONDS);
        assertThat(asyncEcho.getState()).isEqualTo(ssg.legoflow.blocks.ProcessorState.READY);
    }

    @Test
    void testDefaultAsyncViaServiceMethod() {
        var async = echo.async();
        assertThat(async).isInstanceOf(DefaultAsyncService.class);
        assertThat(async.sync()).isSameAs(echo);
    }
}
