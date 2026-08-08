package ssg.legoflow.service.demo.procedural;

import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.AsyncService;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.user.ServiceUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class AsyncEchoDemoTest {

    private EchoService echo;
    private AsyncService<String, String> asyncEcho;
    private DefaultServiceContext ctx;

    @BeforeEach
    void setUp() {
        echo = new EchoService();
        asyncEcho = echo.async();
        ctx = new DefaultServiceContext(ServiceUser.anonymous());
    }

    @Test
    void testAsyncEchoConnect() throws Exception {
        asyncEcho.connect(ctx).get(5, TimeUnit.SECONDS);
        assertThat(asyncEcho.getState()).isEqualTo(ProcessorState.READY);
    }

    @Test
    void testAsyncEchoConsume() throws Exception {
        asyncEcho.connect(ctx).get(5, TimeUnit.SECONDS);
        asyncEcho.consume(ctx, "async-hello").get(5, TimeUnit.SECONDS);
        assertThat(echo.getStatistics().getInCount(String.class)).isEqualTo(1);
    }

    @Test
    void testAsyncEchoRoundTrip() throws Exception {
        asyncEcho.connect(ctx).get(5, TimeUnit.SECONDS);
        asyncEcho.consume(ctx, "msg1", "msg2").get(5, TimeUnit.SECONDS);
        asyncEcho.submit(ctx, "reply1").get(5, TimeUnit.SECONDS);
        var stats = echo.getStatistics();
        assertThat(stats.getInCount(String.class)).isGreaterThanOrEqualTo(2);
    }
}
