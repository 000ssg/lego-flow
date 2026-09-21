package ssg.legoflow.xmpp.client.service;

import org.junit.jupiter.api.*;
import ssg.legoflow.blocks.DefaultContext;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

/** Tests for XMPP client service DP/DF compliance. */
class XmppClientServiceTest {

    @Test void testBuilderCreatesService() {
        var service = XmppClientService.builder("xmpp.example.com", 5222).build();
        assertThat(service).isNotNull();
    }

    @Test void testInitialStateIsDisconnected() {
        var service = XmppClientService.builder("localhost", 5222).build();
        assertThat(service.isConnected()).isFalse();
    }

    @Test void testDisconnectBeforeConnectDoesNotThrow() {
        var service = XmppClientService.builder("localhost", 5222).build();
        try { service.disconnect(service.getServiceContext()); } catch (Exception e) { fail("should not throw"); }
    }

    @Test void testBuilderWithPriority() {
        var service = XmppClientService.builder("xmpp.local", 5222).priority(90).build();
        assertThat(service.getPriority()).isEqualTo(90);
    }

    @Test void testChannelHandlerCanBeCreated() {
        var service = XmppClientService.builder("localhost", 5222).build();
        var handler = service.createChannelHandler();
        assertThat(handler).isNotNull();
    }

    @Test void testGetClientIsNullBeforeConnect() {
        var service = XmppClientService.builder("localhost", 5222).build();
        assertThat(service.getClient()).isNull();
    }

    @Test void testBuilderDependenciesAndName() {
        var service = XmppClientService.builder("h", 1).name("n").dependencies("d1", "d2").build();
        assertThat(service.getDescriptor().name()).isEqualTo("n");
        assertThat(service.getDependencies()).containsExactly("d1", "d2");
    }

    @Test void testXmppResultFactories() {
        var ok = XmppClientService.XmppResult.ok("stanza", ByteBuffer.wrap("x".getBytes(StandardCharsets.UTF_8)));
        assertThat(ok.success()).isTrue();
        assertThat(ok.stanzaType()).isEqualTo("stanza");
        var err = XmppClientService.XmppResult.error("boom");
        assertThat(err.success()).isFalse();
        assertThat(err.stanzaType()).isEqualTo("boom");
    }

    @Test void testConsumeRoutesToStanzaCallback() {
        var service = XmppClientService.builder("h", 1).build();
        var got = new AtomicReference<XmppClientService.XmppResult>();
        service.setStanzaCallback(got::set);
        service.consume(new DefaultContext(), ByteBuffer.wrap("payload".getBytes(StandardCharsets.UTF_8)));
        assertThat(got.get()).isNotNull();
        assertThat(got.get().success()).isTrue();
        assertThat(got.get().stanzaType()).isEqualTo("xmpp");
    }
}
