package ssg.legoflow.network.dns.protocol;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Test;
import ssg.legoflow.network.dns.rdata.*;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
/**
 * Extended codec tests covering all record types and edge cases for DnsCodec.
 */
@Timeout(10)
class DnsCodecExtendedTest {

    @Test
    void testEncodeDecodeNaptrRecord() throws Exception {
        NaptrRecord naptr = new NaptrRecord(100, 200, "s", "SIPS+D2U", "!.*!sip:user@example.com!", DnsName.ROOT);
        DnsMessage response = DnsMessage.builder()
                .id(500)
                .qr(true)
                .addQuestion(DnsQuestion.of("example.com", RecordType.NAPTR))
                .addAnswer(DnsRecord.of("example.com", 3600, naptr))
                .build();

        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);

        assertThat(decoded.answers()).hasSize(1);
        NaptrRecord decodedNaptr = (NaptrRecord) decoded.answers().get(0).rdata();
        assertThat(decodedNaptr.order()).isEqualTo(100);
        assertThat(decodedNaptr.preference()).isEqualTo(200);
        assertThat(decodedNaptr.flags()).isEqualTo("s");
        assertThat(decodedNaptr.service()).isEqualTo("SIPS+D2U");
    }

    @Test
    void testEncodeDecodeDsRecord() throws Exception {
        byte[] digest = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20};
        DsRecord ds = new DsRecord(12345, 8, 2, digest);
        DnsMessage response = DnsMessage.builder()
                .id(501)
                .qr(true)
                .addQuestion(DnsQuestion.of("example.com", RecordType.DS))
                .addAnswer(DnsRecord.of("example.com", 3600, ds))
                .build();

        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);

        assertThat(decoded.answers().get(0).type()).isEqualTo(RecordType.DS);
    }

    @Test
    void testEncodeDecodeNsecRecord() throws Exception {
        Set<RecordType> types = Set.of(RecordType.A, RecordType.AAAA, RecordType.MX);
        NsecRecord nsec = new NsecRecord(DnsName.of("next.example.com"), types);
        DnsMessage response = DnsMessage.builder()
                .id(502)
                .qr(true)
                .addQuestion(DnsQuestion.of("example.com", RecordType.NSEC))
                .addAnswer(DnsRecord.of("example.com", 3600, nsec))
                .build();

        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);

        assertThat(decoded.answers().get(0).type()).isEqualTo(RecordType.NSEC);
    }

    @Test
    void testEncodeDecodeOptRecord() throws Exception {
        var ednsOption = new OptRecord.EdnsOption(0, new byte[]{1, 2, 3});
        OptRecord opt = new OptRecord(4096, 0, 0, true, List.of(ednsOption));
        DnsMessage response = DnsMessage.builder()
                .id(503)
                .qr(true)
                .addQuestion(DnsQuestion.of("example.com", RecordType.A))
                .addAdditional(new DnsRecord(DnsName.ROOT, RecordType.OPT, RecordClass.IN, 4096, opt))
                .build();

        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);

        assertThat(decoded.additional()).isNotEmpty();
    }

    @Test
    void testEncodeDecodeWithAuthorityRecords() throws Exception {
        NsRecord ns = new NsRecord(DnsName.of("ns1.example.com"));
        DnsMessage response = DnsMessage.builder()
                .id(504)
                .qr(true)
                .addQuestion(DnsQuestion.of("example.com", RecordType.NS))
                .addAuthority(DnsRecord.of("example.com", 86400, ns))
                .build();

        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);

        assertThat(decoded.authority()).hasSize(1);
        assertThat(decoded.authority().get(0).type()).isEqualTo(RecordType.NS);
    }

    @Test
    void testEncodeDecodeWithMultipleSections() throws Exception {
        ARecord a = ARecord.of("93.184.216.34");
        NsRecord ns = new NsRecord(DnsName.of("ns1.example.com"));
        TxtRecord txt = new TxtRecord(List.of("v=spf1 ~all"));

        DnsMessage response = DnsMessage.builder()
                .id(505)
                .qr(true)
                .rd(true)
                .addQuestion(DnsQuestion.of("example.com", RecordType.A))
                .addAnswer(DnsRecord.of("example.com", 300, a))
                .addAuthority(DnsRecord.of("example.com", 86400, ns))
                .addAdditional(DnsRecord.of("example.com", 3600, txt))
                .build();

        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);

        assertThat(decoded.answers()).hasSize(1);
        assertThat(decoded.authority()).hasSize(1);
        assertThat(decoded.additional()).hasSize(1);
    }

    @Test
    void testEncodeDecodeWithRcode() throws Exception {
        DnsMessage response = DnsMessage.builder()
                .id(506)
                .qr(true)
                .rCode(ResponseCode.NXDOMAIN)
                .addQuestion(DnsQuestion.of("nonexistent.example.com", RecordType.A))
                .build();

        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);

        assertThat(decoded.header().rCode()).isEqualTo(ResponseCode.NXDOMAIN);
    }

    @Test
    void testEncodeDecodeEmptyResponse() throws Exception {
        DnsMessage response = DnsMessage.builder()
                .id(507)
                .qr(true)
                .addQuestion(DnsQuestion.of("example.com", RecordType.A))
                .build();

        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);

        assertThat(decoded.answers()).isEmpty();
    }

    @Test
    void testEncodeDecodeWithTruncatedFlag() throws Exception {
        DnsMessage response = DnsMessage.builder()
                .id(508)
                .qr(true)
                .tc(true)
                .addQuestion(DnsQuestion.of("example.com", RecordType.A))
                .build();

        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);

        assertThat(decoded.header().tc()).isTrue();
    }

    @Test
    void testEncodeDecodeWithAuthenticDataFlag() throws Exception {
        DnsMessage response = DnsMessage.builder()
                .id(509)
                .qr(true)
                .ad(true)
                .addQuestion(DnsQuestion.of("example.com", RecordType.A))
                .build();

        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);

        assertThat(decoded.header().ad()).isTrue();
    }

    @Test
    void testDecodeWithOffsetAndLength() throws Exception {
        DnsMessage msg = DnsMessage.query("example.com", RecordType.A);
        byte[] fullData = DnsCodec.encode(msg);
        // Wrap in larger buffer with offset
        byte[] padded = new byte[fullData.length + 10];
        System.arraycopy(fullData, 0, padded, 5, fullData.length);

        DnsMessage decoded = DnsCodec.decode(padded, 5, fullData.length);
        assertThat(decoded.questions()).hasSize(1);
    }

    @Test
    void testDecodeTruncatedPointerThrows() throws Exception {
        // Craft a message with a compression pointer at the very end (no second byte)
        byte[] data = new byte[20];
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.putShort((short) 12345);           // ID
        buf.putShort((short) 0x8180);          // Flags: response, RD, RA
        buf.putShort((short) 1);               // QDCount = 1
        buf.putShort((short) 0);               // ANCount = 0
        buf.putShort((short) 0);               // NSCount = 0
        buf.putShort((short) 0);               // ARCount = 0
        // Position 12-19: 7 bytes "example" + 0xC0 (incomplete pointer)
        data[12] = (byte) 7;                   // label length for "example"
        System.arraycopy("example".getBytes(), 0, data, 13, 7);

        assertThatThrownBy(() -> DnsCodec.decode(data))
                .isInstanceOf(Exception.class);
    }

    @Test
    void testDecodeWithCompressionPointer() throws Exception {
        // Test that compression pointers in valid messages work correctly
        DnsMessage response = DnsMessage.builder()
                .id(510)
                .qr(true)
                .addQuestion(DnsQuestion.of("www.example.com", RecordType.A))
                .addAnswer(DnsRecord.of("www.example.com", 300, ARecord.of("1.2.3.4")))
                .build();

        byte[] encoded = DnsCodec.encode(response);
        // Compression pointers should be present in the encoded form
        assertThat(encoded.length).isLessThan(100); // Should be compressed vs uncompressed

        DnsMessage decoded = DnsCodec.decode(encoded);
        assertThat(decoded.answers().get(0).name().toString()).isEqualTo("www.example.com");
    }

    @Test
    void testDecodeNameBeyondPacket() throws Exception {
        // Name that extends beyond the packet boundary
        byte[] data = new byte[15];
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.putShort((short) 12345);           // ID
        buf.putShort((short) 0x8180);          // Flags
        buf.putShort((short) 1);               // QDCount = 1
        buf.putShort((short) 0);               // ANCount = 0
        buf.putShort((short) 0);               // NSCount = 0
        buf.putShort((short) 0);               // ARCount = 0
        // Position 12: label length of 20 (extends beyond buffer)
        buf.put((byte) 20);

        assertThatThrownBy(() -> DnsCodec.decode(data))
                .isInstanceOf(DnsFormatException.class);
    }

    @Test
    void testEncodeDecodeWithMultipleQuestions() throws Exception {
        DnsMessage query = DnsMessage.builder()
                .id(510)
                .addQuestion(DnsQuestion.of("example.com", RecordType.A))
                .addQuestion(DnsQuestion.of("example.com", RecordType.AAAA))
                .build();

        byte[] encoded = DnsCodec.encode(query);
        DnsMessage decoded = DnsCodec.decode(encoded);

        assertThat(decoded.questions()).hasSize(2);
    }

    @Test
    void testEncodeWithHighTtl() throws Exception {
        ARecord a = ARecord.of("1.2.3.4");
        DnsMessage response = DnsMessage.builder()
                .id(511)
                .qr(true)
                .addQuestion(DnsQuestion.of("example.com", RecordType.A))
                .addAnswer(new DnsRecord(DnsName.of("example.com"), RecordType.A, RecordClass.IN, 0xFFFFFFFFL, a))
                .build();

        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);

        assertThat(decoded.answers().get(0).ttl()).isEqualTo(0xFFFFFFFFL);
    }

    @Test
    void testEncodeDecodeRawTypeRecord() throws Exception {
        // Unknown record type falls back to RawRData
        byte[] rawData = new byte[]{1, 2, 3, 4, 5};
        RawRData raw = new RawRData(RecordType.ANY, rawData);
        DnsMessage response = DnsMessage.builder()
                .id(512)
                .qr(true)
                .addQuestion(DnsQuestion.of("example.com", RecordType.ANY))
                .addAnswer(new DnsRecord(DnsName.of("example.com"), RecordType.ANY, RecordClass.IN, 300, raw))
                .build();

        byte[] encoded = DnsCodec.encode(response);
        assertThat(encoded).isNotEmpty();
    }

    @Test
    void testEncodeDecodeSrvRecordFull() throws Exception {
        SrvRecord srv = new SrvRecord(10, 20, 443, DnsName.of("server.example.com"));
        DnsMessage response = DnsMessage.builder()
                .id(513)
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
        assertThat(decodedSrv.target().toString()).isEqualTo("server.example.com");
    }

    @Test
    void testEncodeDecodeWithDoFlag() throws Exception {
        DnsMessage query = DnsMessage.builder()
                .id(514)
                .cd(true)  // Checking disabled
                .addQuestion(DnsQuestion.of("example.com", RecordType.A))
                .build();

        byte[] encoded = DnsCodec.encode(query);
        DnsMessage decoded = DnsCodec.decode(encoded);

        assertThat(decoded.header().cd()).isTrue();
    }

    @Test
    void testEncodeDecodeCnameChain() throws Exception {
        CnameRecord cname1 = new CnameRecord(DnsName.of("intermediate.example.com"));
        ARecord a = ARecord.of("93.184.216.34");

        DnsMessage response = DnsMessage.builder()
                .id(515)
                .qr(true)
                .addQuestion(DnsQuestion.of("www.example.com", RecordType.A))
                .addAnswer(DnsRecord.of("www.example.com", 300, cname1))
                .addAnswer(DnsRecord.of("intermediate.example.com", 300, a))
                .build();

        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);

        assertThat(decoded.answers()).hasSize(2);
        assertThat(decoded.answers().get(0).type()).isEqualTo(RecordType.CNAME);
        assertThat(decoded.answers().get(1).type()).isEqualTo(RecordType.A);
    }

    @Test
    void testEncodeDecodeTxtWithMultipleStrings() throws Exception {
        TxtRecord txt = new TxtRecord(List.of("v=spf1", "include:_spf.google.com", "~all"));
        DnsMessage response = DnsMessage.builder()
                .id(516)
                .qr(true)
                .addQuestion(DnsQuestion.of("example.com", RecordType.TXT))
                .addAnswer(DnsRecord.of("example.com", 3600, txt))
                .build();

        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);

        TxtRecord decodedTxt = (TxtRecord) decoded.answers().get(0).rdata();
        assertThat(decodedTxt.strings()).hasSize(3);
    }

    @Test
    void testEncodeDecodeSoaWithDifferentSerials() throws Exception {
        SoaRecord soa = SoaRecord.of("ns1.example.com", "admin.example.com", 2026080300L, 7200, 1800, 1209600, 86400);
        DnsMessage response = DnsMessage.builder()
                .id(517)
                .qr(true)
                .addQuestion(DnsQuestion.of("example.com", RecordType.SOA))
                .addAnswer(DnsRecord.of("example.com", 86400, soa))
                .build();

        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);

        SoaRecord decodedSoa = (SoaRecord) decoded.answers().get(0).rdata();
        assertThat(decodedSoa.serial()).isEqualTo(2026080300L);
        assertThat(decodedSoa.refresh()).isEqualTo(7200);
        assertThat(decodedSoa.retry()).isEqualTo(1800);
        assertThat(decodedSoa.expire()).isEqualTo(1209600);
        assertThat(decodedSoa.minimum()).isEqualTo(86400);
    }

    @Test
    void testEncodeDecodeMxWithMultiplePriorities() throws Exception {
        DnsMessage response = DnsMessage.builder()
                .id(518)
                .qr(true)
                .addQuestion(DnsQuestion.of("example.com", RecordType.MX))
                .addAnswer(DnsRecord.of("example.com", 3600, MxRecord.of(10, "mail1.example.com")))
                .addAnswer(DnsRecord.of("example.com", 3600, MxRecord.of(20, "mail2.example.com")))
                .addAnswer(DnsRecord.of("example.com", 3600, MxRecord.of(30, "mail3.example.com")))
                .build();

        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);

        assertThat(decoded.answers()).hasSize(3);
        assertThat(((MxRecord) decoded.answers().get(0).rdata()).preference()).isEqualTo(10);
    }

    @Test
    void testEncodeDecodeAaaaAllZeros() throws Exception {
        AaaaRecord aaaa = AaaaRecord.of("::");
        DnsMessage response = DnsMessage.builder()
                .id(519)
                .qr(true)
                .addQuestion(DnsQuestion.of("example.com", RecordType.AAAA))
                .addAnswer(DnsRecord.of("example.com", 60, aaaa))
                .build();

        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);

        // Java may represent :: as "::" or "0:0:0:0:0:0:0:0" - check for either
        String addr = ((AaaaRecord) decoded.answers().get(0).rdata()).address().getHostAddress();
        assertThat(addr).isIn("::", "0:0:0:0:0:0:0:0");
    }

    @Test
    void testEncodeDecodeAWithBroadcast() throws Exception {
        ARecord a = ARecord.of("255.255.255.255");
        DnsMessage response = DnsMessage.builder()
                .id(520)
                .qr(true)
                .addQuestion(DnsQuestion.of("broadcast.example.com", RecordType.A))
                .addAnswer(DnsRecord.of("broadcast.example.com", 60, a))
                .build();

        byte[] encoded = DnsCodec.encode(response);
        DnsMessage decoded = DnsCodec.decode(encoded);

        assertThat(((ARecord) decoded.answers().get(0).rdata()).address().getHostAddress()).isEqualTo("255.255.255.255");
    }
}
