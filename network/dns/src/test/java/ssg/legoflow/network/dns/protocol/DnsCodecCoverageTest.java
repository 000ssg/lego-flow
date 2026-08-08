package ssg.legoflow.network.dns.protocol;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Timeout;
import static org.assertj.core.api.Assertions.*;

/**
 * DNS codec coverage tests to increase test coverage.
 */
@Timeout(10)
class DnsCodecCoverageTest {

    @Test void testEncodeAndDecodeQuery() throws Exception {
        var query = DnsMessage.query("example.com.", RecordType.A);
        byte[] encoded = DnsCodec.encode(query);
        assertThat(encoded).isNotEmpty();
        
        var decoded = DnsCodec.decode(encoded);
        assertThat(decoded).isNotNull();
    }

    @Test void testEncodeAndDecodeResponse() throws Exception {
        var query = DnsMessage.query("example.com.", RecordType.A);
        var response = DnsMessage.responseFor(query, ResponseCode.NOERROR)
            .addAnswer(DnsRecord.of("example.com.", 300, 
                ssg.legoflow.network.dns.rdata.ARecord.of("1.2.3.4")))
            .build();
        
        byte[] encoded = DnsCodec.encode(response);
        assertThat(encoded).isNotEmpty();
    }

    @Test void testEncodeMinimalMessage() throws Exception {
        var msg = DnsMessage.builder().build();
        byte[] encoded = DnsCodec.encode(msg);
        assertThat(encoded).isNotEmpty();
    }

    @Test void testEncodeResponseWithAuthorityRecords() throws Exception {
        var query = DnsMessage.query("example.com.", RecordType.A);
        var response = DnsMessage.responseFor(query, ResponseCode.NOERROR)
            .addAnswer(DnsRecord.of("example.com.", 300, 
                ssg.legoflow.network.dns.rdata.NsRecord.of("ns1.example.com.")))
            .addAuthority(DnsRecord.of("com.", 86400, 
                ssg.legoflow.network.dns.rdata.NsRecord.of("ns1.com.")))
            .build();
        
        byte[] encoded = DnsCodec.encode(response);
        assertThat(encoded).isNotEmpty();
    }

    @Test void testEncodeResponseWithAdditionalRecords() throws Exception {
        var query = DnsMessage.query("example.com.", RecordType.A);
        var response = DnsMessage.responseFor(query, ResponseCode.NOERROR)
            .addAnswer(DnsRecord.of("example.com.", 300, 
                ssg.legoflow.network.dns.rdata.NsRecord.of("ns1.example.com.")))
            .addAdditional(DnsRecord.of("ns1.example.com.", 86400, 
                ssg.legoflow.network.dns.rdata.ARecord.of("10.0.0.1")))
            .build();
        
        byte[] encoded = DnsCodec.encode(response);
        assertThat(encoded).isNotEmpty();
    }

    @Test void testEncodeNxdomainResponse() throws Exception {
        var query = DnsMessage.query("nonexistent.", RecordType.A);
        var response = DnsMessage.responseFor(query, ResponseCode.NXDOMAIN).build();
        
        byte[] encoded = DnsCodec.encode(response);
        assertThat(encoded).isNotEmpty();
    }

    @Test void testEncodeWithOffsetAndLength() throws Exception {
        var query = DnsMessage.query("test.", RecordType.A);
        byte[] fullPacket = new byte[256];
        System.arraycopy(DnsCodec.encode(query), 0, fullPacket, 10, DnsCodec.encode(query).length);
        
        // Decode with offset and length
        var decoded = DnsCodec.decode(fullPacket, 10, DnsCodec.encode(query).length);
        assertThat(decoded).isNotNull();
    }

    @Test void testMaxUdpSize() {
        assertThat(DnsCodec.MAX_UDP_SIZE).isEqualTo(512);
    }

    @Test void testMaxTcpSize() {
        assertThat(DnsCodec.MAX_TCP_SIZE).isEqualTo(65535);
    }
}
