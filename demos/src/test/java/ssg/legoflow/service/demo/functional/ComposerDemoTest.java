package ssg.legoflow.service.demo.functional;

import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.demo.procedural.EchoService;
import ssg.legoflow.service.functional.ServiceComposer;
import ssg.legoflow.service.user.ServiceUser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ComposerDemoTest {

    @Test
    void testComposeConnectAndDisconnect() {
        var echo1 = new EchoService();
        var echo2 = new EchoService();
        var composer = ServiceComposer.compose(echo1, echo2);
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());

        composer.connectAll(ctx);
        assertThat(echo1.isConnected()).isTrue();
        assertThat(echo2.isConnected()).isTrue();

        composer.disconnectAll(ctx);
        assertThat(echo1.isConnected()).isFalse();
        assertThat(echo2.isConnected()).isFalse();
    }

    @Test
    void testComposeMultipleServices() {
        var svc1 = LambdaServiceDemo.createUpperCaseService();
        var svc2 = LambdaServiceDemo.createParsingService();
        var echo = new EchoService();
        var composer = ServiceComposer.compose(svc1, svc2, echo);
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());

        composer.connectAll(ctx);
        assertThat(composer.getChain()).hasSize(3);
        assertThat(svc1.isConnected()).isTrue();
        assertThat(svc2.isConnected()).isTrue();
        assertThat(echo.isConnected()).isTrue();
    }

    @Test
    void testComposeIdempotentConnect() {
        var echo = new EchoService();
        var composer = ServiceComposer.compose(echo);
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());

        composer.connectAll(ctx);
        composer.connectAll(ctx);
        assertThat(echo.isConnected()).isTrue();
    }
}
