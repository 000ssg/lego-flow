package ssg.legoflow.service;

import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.demo.procedural.EchoService;
import ssg.legoflow.service.user.ServiceUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ServiceTest {

    private EchoService echo;
    private ServiceContext ctx;

    @BeforeEach
    void setUp() {
        echo = new EchoService();
        ctx = new DefaultServiceContext(ServiceUser.anonymous());
    }

    @Test
    void testDescriptor() {
        assertThat(echo.getDescriptor().name()).isEqualTo("echo");
        assertThat(echo.getDescriptor().description()).isEqualTo("Echoes input back as output");
    }

    @Test
    void testConnect() {
        echo.connect(ctx);
        assertThat(echo.isConnected()).isTrue();
        assertThat(echo.getState()).isEqualTo(ProcessorState.READY);
    }

    @Test
    void testDisconnect() {
        echo.connect(ctx);
        echo.disconnect(ctx);
        assertThat(echo.isConnected()).isFalse();
    }

    @Test
    void testServiceContext() {
        echo.connect(ctx);
        assertThat(echo.getServiceContext()).isSameAs(ctx);
    }

    @Test
    void testDependencies() {
        assertThat(echo.getDependencies()).isEmpty();
    }

    @Test
    void testPriority() {
        assertThat(echo.getPriority()).isEqualTo(0);
    }

    @Test
    void testConsumeProduces() {
        echo.connect(ctx);
        echo.consume(ctx, "hello");
        assertThat(echo.getStatistics().getInCount(String.class)).isEqualTo(1);
    }

    @Test
    void testSubmitProduces() {
        echo.connect(ctx);
        echo.submit(ctx, "world");
        assertThat(echo.getStatistics().getInCount(String.class)).isGreaterThanOrEqualTo(1);
    }
}
