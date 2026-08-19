package ssg.legoflow.network.cluster.dns;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.dns.protocol.DnsCodec;
import ssg.legoflow.network.dns.protocol.DnsMessage;
import ssg.legoflow.network.dns.protocol.DnsQuestion;
import ssg.legoflow.network.dns.protocol.DnsRecord;
import ssg.legoflow.network.dns.protocol.RecordType;
import ssg.legoflow.network.dns.protocol.ResponseCode;
import ssg.legoflow.network.dns.rdata.PtrRecord;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
class MdnsPacketCodecTest {

    private static final InetAddress LOCAL_ADDR;

    static {
        try {
            LOCAL_ADDR = InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void encode_decode_roundtrip_query() {
        DnsMessage query = MdnsPacketCodec.buildQuery("_http._tcp.local.", RecordType.PTR);
        byte[] encoded = MdnsPacketCodec.encode(query);

        DnsMessage decoded = MdnsPacketCodec.decode(encoded);
        assertThat(decoded.header().qr()).isFalse(); // query
        assertThat(decoded.header().rd()).isFalse(); // no recursion for mDNS
        assertThat(decoded.questions()).hasSize(1);
    }

    @Test
    void encode_decode_roundtrip_response() {
        DnsMessage response = DnsMessage.builder()
                .id(12345)
                .qr(true)
                .aa(true)
                .rd(false)
                .rCode(ResponseCode.NOERROR)
                .build();

        byte[] encoded = MdnsPacketCodec.encode(response);
        DnsMessage decoded = MdnsPacketCodec.decode(encoded);

        assertThat(decoded.header().id()).isEqualTo(12345);
        assertThat(decoded.header().qr()).isTrue();
        assertThat(decoded.header().aa()).isTrue();
    }

    @Test
    void encode_decode_withRecords() {
        DnsMessage response = DnsMessage.builder()
                .id(9999)
                .qr(true)
                .aa(true)
                .rd(false)
                .addQuestion(DnsQuestion.of("_test._tcp.local.", RecordType.PTR))
                .addAnswer(DnsRecord.of(
                        ssg.legoflow.network.dns.protocol.DnsName.of("_test._tcp.local."),
                        120, PtrRecord.of("Instance._test._tcp.local.")))
                .build();

        byte[] encoded = MdnsPacketCodec.encode(response);
        DnsMessage decoded = MdnsPacketCodec.decode(encoded);

        assertThat(decoded.header().id()).isEqualTo(9999);
        assertThat(decoded.answers()).hasSize(1);
    }

    @Test
    void decode_rejectsMdnsQueryWithRecursion() {
        // Build a query with RD=1 (should be rejected as mDNS)
        DnsMessage badQuery = DnsMessage.builder()
                .id(1111)
                .rd(true)  // recursion desired — invalid for mDNS
                .addQuestion(DnsQuestion.of("test.local.", RecordType.A))
                .build();

        byte[] encoded = DnsCodec.encode(badQuery);

        assertThatThrownBy(() -> MdnsPacketCodec.decode(encoded))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RD=0");
    }

    @Test
    void decode_rejectsTooShort() {
        byte[] tooShort = new byte[5];
        assertThatThrownBy(() -> MdnsPacketCodec.decode(tooShort))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void decode_rejectsNull() {
        assertThatThrownBy(() -> MdnsPacketCodec.decode(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void encode_rejectsNull() {
        assertThatThrownBy(() -> MdnsPacketCodec.encode(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void isAnnouncement_detectsAuthoritativeResponse() {
        DnsMessage announcement = DnsMessage.builder()
                .qr(true)
                .aa(true)
                .build();
        assertThat(MdnsPacketCodec.isAnnouncement(announcement)).isTrue();
    }

    @Test
    void isAnnouncement_rejectsQuery() {
        DnsMessage query = DnsMessage.builder()
                .qr(false)
                .aa(false)
                .build();
        assertThat(MdnsPacketCodec.isAnnouncement(query)).isFalse();
    }

    @Test
    void isAnnouncement_rejectsNonAuthoritativeResponse() {
        DnsMessage response = DnsMessage.builder()
                .qr(true)
                .aa(false)
                .build();
        assertThat(MdnsPacketCodec.isAnnouncement(response)).isFalse();
    }

    @Test
    void isGoodbye_detectsZeroTtlRecords() {
        DnsSdServiceRecord record = DnsSdServiceRecord.builder()
                .serviceType("_http._tcp")
                .domain("local")
                .instanceName("Test")
                .targetHostname("localhost")
                .targetAddress(LOCAL_ADDR)
                .port(80)
                .ttl(Duration.ofSeconds(120))
                .build();

        DnsMessage goodbye = MdnsPacketCodec.buildGoodbye(record);
        assertThat(MdnsPacketCodec.isGoodbye(goodbye)).isTrue();
    }

    @Test
    void isGoodbye_rejectsNormalAnnouncement() {
        DnsSdServiceRecord record = DnsSdServiceRecord.builder()
                .serviceType("_http._tcp")
                .domain("local")
                .instanceName("Test")
                .targetHostname("localhost")
                .targetAddress(LOCAL_ADDR)
                .port(80)
                .ttl(Duration.ofSeconds(120))
                .build();

        DnsMessage announcement = MdnsPacketCodec.buildAnnouncement(record);
        assertThat(MdnsPacketCodec.isGoodbye(announcement)).isFalse();
    }

    @Test
    void buildGoodbye_hasZeroTtlRecords() {
        DnsSdServiceRecord record = DnsSdServiceRecord.builder()
                .serviceType("_http._tcp")
                .domain("local")
                .instanceName("Test")
                .targetHostname("localhost")
                .targetAddress(LOCAL_ADDR)
                .port(80)
                .ttl(Duration.ofSeconds(120))
                .build();

        DnsMessage goodbye = MdnsPacketCodec.buildGoodbye(record);
        for (DnsRecord rec : goodbye.answers()) {
            assertThat(rec.ttl()).isEqualTo(0);
        }
    }

    @Test
    void buildAnnouncement_hasAuthoritativeFlag() {
        DnsSdServiceRecord record = DnsSdServiceRecord.builder()
                .serviceType("_http._tcp")
                .domain("local")
                .instanceName("Test")
                .targetHostname("localhost")
                .targetAddress(LOCAL_ADDR)
                .port(80)
                .ttl(Duration.ofSeconds(120))
                .build();

        DnsMessage announcement = MdnsPacketCodec.buildAnnouncement(record);
        assertThat(announcement.header().qr()).isTrue();
        assertThat(announcement.header().aa()).isTrue();
    }

    @Test
    void buildQuery_hasNoRecursionFlag() {
        DnsMessage query = MdnsPacketCodec.buildQuery("test.local.", RecordType.A);
        assertThat(query.header().qr()).isFalse();
        assertThat(query.header().rd()).isFalse();
    }

    @Test
    void mdnsConstants() {
        assertThat(MdnsPacketCodec.MDNS_IPV4_MULTICAST).isEqualTo("224.0.0.251");
        assertThat(MdnsPacketCodec.MDNS_IPV6_MULTICAST).isEqualTo("FF02::FB");
        assertThat(MdnsPacketCodec.MDNS_PORT).isEqualTo(5353);
    }
}
