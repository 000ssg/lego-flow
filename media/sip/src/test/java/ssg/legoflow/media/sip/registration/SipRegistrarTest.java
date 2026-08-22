package ssg.legoflow.media.sip.registration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.media.sip.header.SipHeaders;
import ssg.legoflow.media.sip.protocol.SipMethod;
import ssg.legoflow.media.sip.protocol.SipRequest;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link SipRegistrar}.
 */
class SipRegistrarTest {

    private SipRegistrar registrar;

    @BeforeEach
    void setUp() {
        registrar = new SipRegistrar("example.com");
    }

    @Test
    void testRegisterBinding() {
        var request = createRegisterRequest("sip:alice@192.168.1.1:5060", 3600);
        var response = registrar.handleRegister(request);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(registrar.bindingCount()).isEqualTo(1);
    }

    @Test
    void testLookupBinding() {
        var request = createRegisterRequest("sip:alice@192.168.1.1:5060", 3600);
        registrar.handleRegister(request);

        var bindings = registrar.lookup("sip:alice@example.com");
        assertThat(bindings).hasSize(1);
        assertThat(bindings.getFirst().contactUri()).isEqualTo("sip:alice@192.168.1.1:5060");
    }

    @Test
    void testUnregister() {
        var registerReq = createRegisterRequest("sip:alice@192.168.1.1:5060", 3600);
        registrar.handleRegister(registerReq);
        assertThat(registrar.bindingCount()).isEqualTo(1);

        var unregisterReq = createRegisterRequest("sip:alice@192.168.1.1:5060", 0);
        var response = registrar.handleRegister(unregisterReq);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(registrar.lookup("sip:alice@example.com")).isEmpty();
    }

    @Test
    void testWildcardUnregister() {
        registrar.handleRegister(createRegisterRequest("sip:alice@192.168.1.1:5060", 3600));
        registrar.handleRegister(createRegisterRequest("sip:alice@192.168.1.2:5060", 3600));
        assertThat(registrar.bindingCount()).isEqualTo(2);

        var wildcard = SipRequest.builder(SipMethod.REGISTER, "sip:registrar.example.com")
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@example.com>;tag=abc")
                .to("<sip:alice@example.com>")
                .callId("wildcard@test.com")
                .cseq(3, SipMethod.REGISTER)
                .maxForwards(70)
                .header(SipHeaders.CONTACT, "*")
                .expires(0)
                .build();
        var response = registrar.handleRegister(wildcard);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(registrar.lookup("sip:alice@example.com")).isEmpty();
    }

    @Test
    void testQueryBindings() {
        registrar.handleRegister(createRegisterRequest("sip:alice@192.168.1.1:5060", 3600));

        // Query without Contact header
        var query = SipRequest.builder(SipMethod.REGISTER, "sip:registrar.example.com")
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@example.com>;tag=abc")
                .to("<sip:alice@example.com>")
                .callId("query@test.com")
                .cseq(2, SipMethod.REGISTER)
                .maxForwards(70)
                .build();
        var response = registrar.handleRegister(query);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().all(SipHeaders.CONTACT)).isNotEmpty();
    }

    @Test
    void testReRegister() {
        registrar.handleRegister(createRegisterRequest("sip:alice@192.168.1.1:5060", 1800));
        registrar.handleRegister(createRegisterRequest("sip:alice@192.168.1.1:5060", 3600));

        // Should still have only 1 binding (updated)
        assertThat(registrar.bindingCount()).isEqualTo(1);
        var bindings = registrar.lookup("sip:alice@example.com");
        assertThat(bindings).hasSize(1);
    }

    @Test
    void testMultipleBindings() {
        registrar.handleRegister(createRegisterRequest("sip:alice@192.168.1.1:5060", 3600));
        registrar.handleRegister(createRegisterRequest("sip:alice@192.168.1.2:5060", 3600));

        assertThat(registrar.bindingCount()).isEqualTo(2);
        assertThat(registrar.lookup("sip:alice@example.com")).hasSize(2);
    }

    @Test
    void testIntervalTooBrief() {
        var request = createRegisterRequest("sip:alice@192.168.1.1:5060", 30);
        var response = registrar.handleRegister(request);

        assertThat(response.statusCode()).isEqualTo(423);
        assertThat(response.headers().first(SipHeaders.MIN_EXPIRES)).isPresent();
    }

    @Test
    void testNonRegisterMethod() {
        var request = SipRequest.builder(SipMethod.INVITE, "sip:bob@example.com")
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@example.com>;tag=abc")
                .to("<sip:bob@example.com>")
                .callId("test@test.com")
                .cseq(1, SipMethod.INVITE)
                .maxForwards(70)
                .build();
        var response = registrar.handleRegister(request);

        assertThat(response.statusCode()).isEqualTo(405);
    }

    @Test
    void testDomain() {
        assertThat(registrar.domain()).isEqualTo("example.com");
    }

    private SipRequest createRegisterRequest(String contactUri, int expires) {
        return SipRequest.builder(SipMethod.REGISTER, "sip:registrar.example.com")
                .via("SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@example.com>;tag=abc")
                .to("<sip:alice@example.com>")
                .callId("register@test.com")
                .cseq(1, SipMethod.REGISTER)
                .maxForwards(70)
                .contact("<" + contactUri + ">")
                .expires(expires)
                .build();
    }
}
