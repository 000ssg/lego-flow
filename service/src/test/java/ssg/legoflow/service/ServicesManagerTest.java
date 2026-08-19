package ssg.legoflow.service;

import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.demo.procedural.EchoService;
import ssg.legoflow.service.manager.AbstractServicesManager;
import ssg.legoflow.service.user.ServiceUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ServicesManagerTest {

    private AbstractServicesManager manager;
    private ServiceContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new DefaultServiceContext(ServiceUser.anonymous());
        manager = new AbstractServicesManager(ctx);
    }

    @Test
    void testRegisterAndGetService() {
        var echo = new EchoService();
        manager.register(echo);
        assertThat(manager.getService("echo")).isSameAs(echo);
    }

    @Test
    void testGetServices() {
        var echo = new EchoService();
        manager.register(echo);
        assertThat(manager.getServices()).hasSize(1).contains(echo);
    }

    @Test
    void testUnregisterDisconnectsIfConnected() {
        var echo = new EchoService();
        manager.register(echo);
        echo.connect(ctx);
        assertThat(echo.isConnected()).isTrue();
        manager.unregister("echo");
        assertThat(echo.isConnected()).isFalse();
        assertThat(manager.getService("echo")).isNull();
    }

    @Test
    void testStartAll() {
        var echo = new EchoService();
        manager.register(echo);
        manager.startAll();
        assertThat(echo.isConnected()).isTrue();
        assertThat(echo.getState()).isEqualTo(ProcessorState.READY);
    }

    @Test
    void testStopAll() {
        var echo = new EchoService();
        manager.register(echo);
        manager.startAll();
        manager.stopAll();
        assertThat(echo.isConnected()).isFalse();
    }

    @Test
    void testStartByName() {
        var echo = new EchoService();
        manager.register(echo);
        manager.start("echo");
        assertThat(echo.isConnected()).isTrue();
    }

    @Test
    void testStartUnknownServiceThrows() {
        assertThatThrownBy(() -> manager.start("nonexistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown service");
    }

    @Test
    void testStopByName() {
        var echo = new EchoService();
        manager.register(echo);
        manager.startAll();
        manager.stop("echo");
        assertThat(echo.isConnected()).isFalse();
    }

    @Test
    void testGetStates() {
        var echo = new EchoService();
        manager.register(echo);
        var states = manager.getStates();
        assertThat(states).containsEntry("echo", ProcessorState.IDLE);
    }

    @Test
    void testClose() {
        var echo = new EchoService();
        manager.register(echo);
        manager.startAll();
        manager.close();
        assertThat(manager.getServices()).isEmpty();
    }
}
