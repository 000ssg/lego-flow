package ssg.legoflow.xmpp.server.service;

import org.junit.jupiter.api.*;
import ssg.legoflow.blocks.DefaultContext;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

/** Tests for XMPP server service DP/DF compliance. */
class XmppServerServiceTest {

    @Test void testBuilderCreatesService() {
        var service = XmppServerService.builder(5222).build();
        assertThat(service).isNotNull();
    }

    @Test void testNoArgBuilderCreatesService() {
        var service = XmppServerService.builder().build();
        assertThat(service).isNotNull();
    }

    @Test void testInitialStateIsDisconnected() {
        var service = XmppServerService.builder(0).build();
        assertThat(service.isConnected()).isFalse();
    }

    @Test void testDisconnectBeforeConnectDoesNotThrow() {
        var service = XmppServerService.builder(0).build();
        try { service.disconnect(service.getServiceContext()); } catch (Exception e) { fail("should not throw"); }
    }

    @Test void testBuilderWithPriority() {
        var service = XmppServerService.builder(5222).priority(80).build();
        assertThat(service.getPriority()).isEqualTo(80);
    }

    @Test void testChannelHandlerCanBeCreated() {
        var service = XmppServerService.builder(5222).build();
        var handler = service.createChannelHandler();
        assertThat(handler).isNotNull();
    }

    @Test void testGetServerIsNullBeforeConnect() {
        var service = XmppServerService.builder(0).build();
        assertThat(service.getServer()).isNull();
    }

    @Test void testBuilderHostDependenciesName() {
        var service = XmppServerService.builder("127.0.0.1", 5222).name("n").dependencies("d").build();
        assertThat(service.getDescriptor().name()).isEqualTo("n");
        assertThat(service.getDependencies()).containsExactly("d");
    }

    @Test void testXmppResultFactories() {
        var ok = XmppServerService.XmppResult.ok("stanza", ByteBuffer.wrap("x".getBytes(StandardCharsets.UTF_8)));
        assertThat(ok.success()).isTrue();
        assertThat(ok.stanzaType()).isEqualTo("stanza");
        var err = XmppServerService.XmppResult.error("boom");
        assertThat(err.success()).isFalse();
        assertThat(err.payload()).isNull();
    }

    @Test void testConsumeRoutesToStanzaCallback() {
        var service = XmppServerService.builder(0).build();
        var got = new AtomicReference<XmppServerService.XmppResult>();
        service.setStanzaCallback(got::set);
        service.consume(new DefaultContext(), ByteBuffer.wrap("payload".getBytes(StandardCharsets.UTF_8)));
        assertThat(got.get()).isNotNull();
        assertThat(got.get().success()).isTrue();
        assertThat(got.get().stanzaType()).isEqualTo("xmpp");
    }
}
