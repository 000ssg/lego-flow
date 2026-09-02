package ssg.legoflow.network.dns.protocol;

import org.junit.jupiter.api.*;
import ssg.legoflow.network.dns.rdata.*;
import static org.assertj.core.api.Assertions.*;
/**
 * Focused DnsCodec encoding tests to cover more encode paths.
 */
class DnsCodecEncodingTest {

    @Test void testEncodeWithTruncatedFlag() throws Exception {
        var msg = DnsMessage.builder()
                .id(1)
                .qr(true)
                .tc(true)  // truncated flag
                .addQuestion(DnsQuestion.of("test.com.", RecordType.A))
                .build();
        
        byte[] encoded = DnsCodec.encode(msg);
        var decoded = DnsCodec.decode(encoded);
        assertThat(decoded.header().tc()).isTrue();
    }

    @Test void testEncodeWithCheckingDisabledFlag() throws Exception {
        var msg = DnsMessage.builder()
                .id(2)
                .cd(true)  // checking disabled
                .addQuestion(DnsQuestion.of("test.com.", RecordType.A))
                .build();
        
        byte[] encoded = DnsCodec.encode(msg);
        var decoded = DnsCodec.decode(encoded);
        assertThat(decoded.header().cd()).isTrue();
    }

    @Test void testEncodeWithRecursiveDesiredAndAvailable() throws Exception {
        var msg = DnsMessage.builder()
                .id(3)
                .qr(true)
                .rd(true)  // recursive desired
                .ra(true)  // recursive available
                .addQuestion(DnsQuestion.of("test.com.", RecordType.A))
                .build();
        
        byte[] encoded = DnsCodec.encode(msg);
        var decoded = DnsCodec.decode(encoded);
        assertThat(decoded.header().rd()).isTrue();
        assertThat(decoded.header().ra()).isTrue();
    }

    @Test void testEncodeWithAuthenticDataFlag() throws Exception {
        var msg = DnsMessage.builder()
                .id(4)
                .qr(true)
                .ad(true)  // authentic data
                .addQuestion(DnsQuestion.of("test.com.", RecordType.A))
                .build();
        
        byte[] encoded = DnsCodec.encode(msg);
        var decoded = DnsCodec.decode(encoded);
        assertThat(decoded.header().ad()).isTrue();
    }

    @Test void testEncodeWithAllSectionsPopulated() throws Exception {
        var msg = DnsMessage.builder()
                .id(5)
                .qr(true)
                .addQuestion(DnsQuestion.of("example.com.", RecordType.A))
                .addAnswer(DnsRecord.of("example.com.", 300, ARecord.of("1.2.3.4")))
                .addAuthority(new DnsRecord(
                        DnsName.of("example.com."), RecordType.NS, RecordClass.IN, 86400,
                        new NsRecord(DnsName.of("ns1.example.com."))))
                .addAdditional(new DnsRecord(
                        DnsName.of("ns1.example.com."), RecordType.A, RecordClass.IN, 86400,
                        ARecord.of("5.6.7.8")))
                .build();
        
        byte[] encoded = DnsCodec.encode(msg);
        var decoded = DnsCodec.decode(encoded);
        
        assertThat(decoded.questions()).hasSize(1);
        assertThat(decoded.answers()).hasSize(1);
        assertThat(decoded.authority()).hasSize(1);
        assertThat(decoded.additional()).hasSize(1);
    }

    @Test void testEncodeWithMultipleAuthorities() throws Exception {
        var msg = DnsMessage.builder()
                .id(6)
                .qr(true)
                .addQuestion(DnsQuestion.of("example.com.", RecordType.NS))
                .addAuthority(new DnsRecord(DnsName.of("example.com."), RecordType.NS, RecordClass.IN, 86400,
                        new NsRecord(DnsName.of("ns1.example.com."))))
                .addAuthority(new DnsRecord(DnsName.of("example.com."), RecordType.NS, RecordClass.IN, 86400,
                        new NsRecord(DnsName.of("ns2.example.com."))))
                .build();
        
        byte[] encoded = DnsCodec.encode(msg);
        var decoded = DnsCodec.decode(encoded);
        assertThat(decoded.authority()).hasSize(2);
    }

    @Test void testEncodeWithMultipleAdditionals() throws Exception {
        var msg = DnsMessage.builder()
                .id(7)
                .qr(true)
                .addQuestion(DnsQuestion.of("example.com.", RecordType.A))
                .addAdditional(new DnsRecord(DnsName.of("ns1.example.com."), RecordType.A, RecordClass.IN, 86400,
                        ARecord.of("5.6.7.8")))
                .addAdditional(new DnsRecord(DnsName.of("ns2.example.com."), RecordType.A, RecordClass.IN, 86400,
                        ARecord.of("9.10.11.12")))
                .build();
        
        byte[] encoded = DnsCodec.encode(msg);
        var decoded = DnsCodec.decode(encoded);
        assertThat(decoded.additional()).hasSize(2);
    }

    @Test void testEncodeWithVariousRcodes() throws Exception {
        for (ResponseCode rcode : ResponseCode.values()) {
            var msg = DnsMessage.builder()
                    .id(1)
                    .qr(true)
                    .rCode(rcode)
                    .addQuestion(DnsQuestion.of("test.com.", RecordType.A))
                    .build();
            
            byte[] encoded = DnsCodec.encode(msg);
            var decoded = DnsCodec.decode(encoded);
            assertThat(decoded.header().rCode()).as("RCode %s", rcode.name()).isEqualTo(rcode);
        }
    }

    @Test void testEncodeWithZeroTtl() throws Exception {
        var msg = DnsMessage.builder()
                .id(8)
                .qr(true)
                .addQuestion(DnsQuestion.of("test.com.", RecordType.A))
                .addAnswer(new DnsRecord(DnsName.of("test.com."), RecordType.A, RecordClass.IN, 0, ARecord.of("1.2.3.4")))
                .build();
        
        byte[] encoded = DnsCodec.encode(msg);
        var decoded = DnsCodec.decode(encoded);
        assertThat(decoded.answers().get(0).ttl()).isEqualTo(0);
    }

    @Test void testEncodeWithMaxTtl() throws Exception {
        var msg = DnsMessage.builder()
                .id(9)
                .qr(true)
                .addQuestion(DnsQuestion.of("test.com.", RecordType.A))
                .addAnswer(new DnsRecord(DnsName.of("test.com."), RecordType.A, RecordClass.IN, 0xFFFFFFFFL, ARecord.of("1.2.3.4")))
                .build();
        
        byte[] encoded = DnsCodec.encode(msg);
        var decoded = DnsCodec.decode(encoded);
        assertThat(decoded.answers().get(0).ttl()).isEqualTo(0xFFFFFFFFL);
    }

    @Test void testEncodeWithLoopbackAddress() throws Exception {
        var msg = DnsMessage.builder()
                .id(10)
                .qr(true)
                .addQuestion(DnsQuestion.of("localhost.", RecordType.A))
                .addAnswer(DnsRecord.of("localhost.", 60, ARecord.of("127.0.0.1")))
                .build();
        
        byte[] encoded = DnsCodec.encode(msg);
        var decoded = DnsCodec.decode(encoded);
        assertThat(((ARecord) decoded.answers().get(0).rdata()).address().getHostAddress()).isEqualTo("127.0.0.1");
    }

    @Test void testEncodeWithAllZeroAddress() throws Exception {
        var msg = DnsMessage.builder()
                .id(11)
                .qr(true)
                .addQuestion(DnsQuestion.of("zero.", RecordType.A))
                .addAnswer(DnsRecord.of("zero.", 60, ARecord.of("0.0.0.0")))
                .build();
        
        byte[] encoded = DnsCodec.encode(msg);
        var decoded = DnsCodec.decode(encoded);
        assertThat(((ARecord) decoded.answers().get(0).rdata()).address().getHostAddress()).isEqualTo("0.0.0.0");
    }

    @Test void testEncodeWithMultipleARecords() throws Exception {
        var msg = DnsMessage.builder()
                .id(12)
                .qr(true)
                .addQuestion(DnsQuestion.of("multi.com.", RecordType.A))
                .addAnswer(DnsRecord.of("multi.com.", 30, ARecord.of("1.1.1.1")))
                .addAnswer(DnsRecord.of("multi.com.", 30, ARecord.of("2.2.2.2")))
                .addAnswer(DnsRecord.of("multi.com.", 30, ARecord.of("3.3.3.3")))
                .build();
        
        byte[] encoded = DnsCodec.encode(msg);
        var decoded = DnsCodec.decode(encoded);
        assertThat(decoded.answers()).hasSize(3);
    }

    @Test void testEncodeNoOpCode() throws Exception {
        var msg = DnsMessage.builder()
                .id(13)
                .qr(true)
                .opCode(OpCode.QUERY)  // default, but explicit
                .addQuestion(DnsQuestion.of("test.com.", RecordType.A))
                .build();
        
        byte[] encoded = DnsCodec.encode(msg);
        var decoded = DnsCodec.decode(encoded);
        assertThat(decoded.header().opCode()).isEqualTo(OpCode.QUERY);
    }

    @Test void testEncodeDnsMessageConstants() {
        assertThat(DnsCodec.MAX_UDP_SIZE).isEqualTo(512);
        assertThat(DnsCodec.MAX_TCP_SIZE).isEqualTo(65535);
    }

}
