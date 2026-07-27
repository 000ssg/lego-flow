package ssg.legoflow.service.demo.procedural;

import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.user.ServiceUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class EchoServiceDemoTest {

    private EchoService echo;
    private DefaultServiceContext ctx;

    @BeforeEach
    void setUp() {
        echo = new EchoService();
        ctx = new DefaultServiceContext(ServiceUser.anonymous());
    }

    @Test
    void testEchoConsumeAccept() {
        echo.connect(ctx);
        echo.consume(ctx, "hello", "world");
        var stats = echo.getStatistics();
        assertThat(stats.getInCount(String.class)).isEqualTo(2);
        assertThat(stats.getOutCount(String.class)).isEqualTo(2);
    }

    @Test
    void testEchoSubmitProduce() {
        echo.connect(ctx);
        echo.submit(ctx, "outbound");
        var stats = echo.getStatistics();
        assertThat(stats.getInCount(String.class)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void testEchoLifecycle() {
        assertThat(echo.getState()).isEqualTo(ProcessorState.IDLE);
        echo.connect(ctx);
        assertThat(echo.getState()).isEqualTo(ProcessorState.READY);
        assertThat(echo.isConnected()).isTrue();
        echo.disconnect(ctx);
        assertThat(echo.isConnected()).isFalse();
    }

    @Test
    void testEchoDescriptor() {
        assertThat(echo.getDescriptor().name()).isEqualTo("echo");
        assertThat(echo.getDescriptor().dependencies()).isEmpty();
        assertThat(echo.getDescriptor().priority()).isEqualTo(0);
    }
}
