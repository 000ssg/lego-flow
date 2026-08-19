package ssg.legoflow.network.dns.protocol;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Test;
import ssg.legoflow.network.dns.rdata.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@Timeout(10)
class DnsCodecTest {

    @Test
    void testEncodeDecodeSimpleAQuery() throws Exception {
        DnsMessage query = DnsMessage.query("example.com", RecordType.A);
        byte[] encoded = DnsCodec.encode(query);
        
        // Header is 12 bytes + question section (name ~13 bytes + type/class 4 bytes)
        assertThat(encoded).hasSizeGreaterThan(12);
        
        DnsMessage decoded = DnsCodec.decode(encoded);
        assertThat(decoded.questions()).hasSize(1);
        assertThat(decoded.questions().get(0).name().toString()).isEqualTo("example.com");
        assertThat(decoded.questions().get(0).type()).isEqualTo(RecordType.A);
    }

    @Test
    void testEncodeDecodeAResponse() throws Exception {
        ARecord a = ARecord.of("93.184.216.34");
        DnsMessage response = DnsMessage.builder()
                .id(1234)
                .qr(true)
                .rd(true)
                .ra(true)
                .addQuestion(DnsQuestion.of("example.com", RecordType.A))
                .addAnswer(DnsRecord.of("example.com", 300, a))
                .build();
        
        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);
        
        assertThat(decoded.header().id()).isEqualTo(1234);
        assertThat(decoded.isResponse()).isTrue();
        assertThat(decoded.answers()).hasSize(1);
        assertThat(decoded.answers().get(0).ttl()).isEqualTo(300);
    }

    @Test
    void testEncodeDecodeAAAAResponse() throws Exception {
        AaaaRecord aaaa = AaaaRecord.of("2606:2800:220:1:248:1893:25c8:1946");
        DnsMessage response = DnsMessage.builder()
                .id(5678)
                .qr(true)
                .addQuestion(DnsQuestion.of("ipv6.example.com", RecordType.AAAA))
                .addAnswer(DnsRecord.of("ipv6.example.com", 60, aaaa))
                .build();
        
        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);
        
        assertThat(decoded.answers()).hasSize(1);
        AaaaRecord decodedAaaa = (AaaaRecord) decoded.answers().get(0).rdata();
        assertThat(decodedAaaa.address().getHostAddress()).isEqualTo("2606:2800:220:1:248:1893:25c8:1946");
    }

    @Test
    void testEncodeDecodeMXResponse() throws Exception {
        MxRecord mx = MxRecord.of(10, "mail.example.com");
        DnsMessage response = DnsMessage.builder()
                .id(100)
                .qr(true)
                .addQuestion(DnsQuestion.of("example.com", RecordType.MX))
                .addAnswer(DnsRecord.of("example.com", 3600, mx))
                .build();
        
        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);
        
        assertThat(decoded.answers()).hasSize(1);
        MxRecord decodedMx = (MxRecord) decoded.answers().get(0).rdata();
        assertThat(decodedMx.preference()).isEqualTo(10);
        assertThat(decodedMx.exchange().toString()).isEqualTo("mail.example.com");
    }

    @Test
    void testEncodeDecodeCnameResponse() throws Exception {
        CnameRecord cname = new CnameRecord(DnsName.of("canonical.example.com"));
        DnsMessage response = DnsMessage.builder()
                .id(200)
                .qr(true)
                .addQuestion(DnsQuestion.of("alias.example.com", RecordType.CNAME))
                .addAnswer(DnsRecord.of("alias.example.com", 600, cname))
                .build();
        
        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);
        
        assertThat(decoded.answers().get(0).type()).isEqualTo(RecordType.CNAME);
    }

    @Test
    void testEncodeDecodeTxtResponse() throws Exception {
        TxtRecord txt = new TxtRecord(List.of("v=spf1 include:_spf.example.com ~all"));
        DnsMessage response = DnsMessage.builder()
                .id(300)
                .qr(true)
                .addQuestion(DnsQuestion.of("example.com", RecordType.TXT))
                .addAnswer(DnsRecord.of("example.com", 3600, txt))
                .build();
        
        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);
        
        assertThat(decoded.answers().get(0).type()).isEqualTo(RecordType.TXT);
    }

    @Test
    void testEncodeDecodePtrResponse() throws Exception {
        PtrRecord ptr = new PtrRecord(DnsName.of("host.example.com"));
        DnsMessage response = DnsMessage.builder()
                .id(400)
                .qr(true)
                .addQuestion(DnsQuestion.of("1.2.3.in-addr.arpa.", RecordType.PTR))
                .addAnswer(DnsRecord.of("1.2.3.in-addr.arpa.", 3600, ptr))
                .build();
        
        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);
        
        assertThat(decoded.answers().get(0).type()).isEqualTo(RecordType.PTR);
    }

    @Test
    void testEncodeDecodeSrvResponse() throws Exception {
        SrvRecord srv = SrvRecord.of(10, 20, 443, "www.example.com");
        DnsMessage response = DnsMessage.builder()
                .id(500)
                .qr(true)
                .addQuestion(DnsQuestion.of("_https._tcp.example.com", RecordType.SRV))
                .addAnswer(DnsRecord.of("_https._tcp.example.com", 300, srv))
                .build();
        
        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);
        
        SrvRecord decodedSrv = (SrvRecord) decoded.answers().get(0).rdata();
        assertThat(decodedSrv.priority()).isEqualTo(10);
        assertThat(decodedSrv.weight()).isEqualTo(20);
        assertThat(decodedSrv.port()).isEqualTo(443);
    }

    @Test
    void testEncodeDecodeNsResponse() throws Exception {
        NsRecord ns = new NsRecord(DnsName.of("ns1.example.com"));
        DnsMessage response = DnsMessage.builder()
                .id(600)
                .qr(true)
                .addQuestion(DnsQuestion.of("example.com", RecordType.NS))
                .addAnswer(DnsRecord.of("example.com", 86400, ns))
                .build();
        
        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);
        
        assertThat(decoded.answers().get(0).type()).isEqualTo(RecordType.NS);
    }

    @Test
    void testEncodeDecodeSoaResponse() throws Exception {
        SoaRecord soa = SoaRecord.of("ns1.example.com", "admin.example.com", 2024010100L, 3600, 900, 604800, 86400);
        DnsMessage response = DnsMessage.builder()
                .id(700)
                .qr(true)
                .addQuestion(DnsQuestion.of("example.com", RecordType.SOA))
                .addAnswer(DnsRecord.of("example.com", 86400, soa))
                .build();
        
        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);
        
        SoaRecord decodedSoa = (SoaRecord) decoded.answers().get(0).rdata();
        assertThat(decodedSoa.serial()).isEqualTo(2024010100L);
    }

    @Test
    void testDecodeTooShortMessage() {
        byte[] shortData = new byte[5]; // less than 12-byte header
        assertThatThrownBy(() -> DnsCodec.decode(shortData))
                .isInstanceOf(DnsFormatException.class)
                .hasMessageContaining("too short");
    }

    


    @Test
    void testMultipleAnswers() throws Exception {
        DnsMessage response = DnsMessage.builder()
                .id(999)
                .qr(true)
                .addQuestion(DnsQuestion.of("multi.com", RecordType.A))
                .addAnswer(DnsRecord.of("multi.com", 60, ARecord.of("1.1.1.1")))
                .addAnswer(DnsRecord.of("multi.com", 60, ARecord.of("2.2.2.2")))
                .addAnswer(DnsRecord.of("multi.com", 60, ARecord.of("3.3.3.3")))
                .build();
        
        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);
        
        assertThat(decoded.answers()).hasSize(3);
    }

    @Test
    void testNameCompression() throws Exception {
        // Create message with duplicate names to test compression
        DnsMessage response = DnsMessage.builder()
                .id(111)
                .qr(true)
                .addQuestion(DnsQuestion.of("example.com", RecordType.A))
                .addAnswer(DnsRecord.of("example.com", 300, ARecord.of("1.2.3.4")))
                .build();
        
        byte[] encoded = DnsCodec.encode(response);
        // The second occurrence of example.com should be compressed (pointer)
        DnsMessage decoded = DnsCodec.decode(encoded);
        assertThat(decoded.answers().get(0).name().toString()).isEqualTo("example.com");
    }

    @Test
    void testMaxUdpSize() {
        assertThat(DnsCodec.MAX_UDP_SIZE).isEqualTo(512);
    }

    @Test
    void testMaxTcpSize() {
        assertThat(DnsCodec.MAX_TCP_SIZE).isEqualTo(65535);
    }

    @Test
    void testEncodeDecodeCaaRecord() throws Exception {
        CaaRecord caa = new CaaRecord(0, "issue", "letsencrypt.org");
        DnsMessage response = DnsMessage.builder()
                .id(800)
                .qr(true)
                .addQuestion(DnsQuestion.of("example.com", RecordType.CAA))
                .addAnswer(DnsRecord.of("example.com", 3600, caa))
                .build();
        
        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);
        assertThat(decoded.answers().get(0).type()).isEqualTo(RecordType.CAA);
    }

    @Test
    void testEncodeDecodeNsec3Param() throws Exception {
        byte[] salt = new byte[]{1, 2, 3, 4};
        Nsec3ParamRecord n3p = new Nsec3ParamRecord((byte) 1, (byte) 0, 10, salt);
        DnsMessage response = DnsMessage.builder()
                .id(850)
                .qr(true)
                .addQuestion(DnsQuestion.of("example.com", RecordType.NSEC3PARAM))
                .addAnswer(DnsRecord.of("example.com", 3600, n3p))
                .build();
        
        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);
        assertThat(decoded.answers().get(0).type()).isEqualTo(RecordType.NSEC3PARAM);
    }
}
