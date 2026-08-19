package ssg.legoflow.service.functional;

import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.demo.procedural.EchoService;
import ssg.legoflow.service.user.ServiceUser;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ServiceComposerTest {

    @Test
    void testComposeServices() {
        var echo1 = new EchoService();
        var echo2 = new EchoService();
        var composer = ServiceComposer.compose(echo1, echo2);
        assertThat(composer.getChain()).hasSize(2);
    }

    @Test
    void testConnectAll() {
        var echo1 = new EchoService();
        var echo2 = new EchoService();
        var composer = ServiceComposer.compose(echo1, echo2);
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        composer.connectAll(ctx);
        assertThat(echo1.isConnected()).isTrue();
        assertThat(echo2.isConnected()).isTrue();
    }

    @Test
    void testDisconnectAll() {
        var echo1 = new EchoService();
        var echo2 = new EchoService();
        var composer = ServiceComposer.compose(echo1, echo2);
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        composer.connectAll(ctx);
        composer.disconnectAll(ctx);
        assertThat(echo1.isConnected()).isFalse();
        assertThat(echo2.isConnected()).isFalse();
    }

    @Test
    void testAddService() {
        var composer = new ServiceComposer();
        var echo = new EchoService();
        composer.add(echo);
        assertThat(composer.getChain()).containsExactly(echo);
    }

    @Test
    void testChainIsImmutableCopy() {
        var composer = new ServiceComposer();
        composer.add(new EchoService());
        var chain = composer.getChain();
        assertThatThrownBy(() -> chain.add(new EchoService()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
