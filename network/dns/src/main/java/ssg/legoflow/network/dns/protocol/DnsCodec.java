package ssg.legoflow.network.dns.protocol;

import ssg.legoflow.network.dns.rdata.*;
import ssg.legoflow.network.dns.rdata.dnssec.*;

import java.io.ByteArrayOutputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * DNS message binary codec implementing encoding and decoding per RFC 1035.
 *
 * <p>Supports name compression (pointers with 0xC0 prefix) for both
 * encoding and decoding. All multi-byte integers are big-endian.
 *
 * @since 0.1.0
 */
public final class DnsCodec {

    /** Maximum DNS message size over UDP (without EDNS0). */
    public static final int MAX_UDP_SIZE = 512;

    /** Maximum DNS message size over TCP. */
    public static final int MAX_TCP_SIZE = 65535;

    private DnsCodec() {}

    // ---- Decoding ----

    /**
     * Decodes a DNS message from raw bytes.
     *
     * @param data the raw DNS message bytes
     * @return the decoded message
     * @throws DnsFormatException if the message is malformed
     * @since 0.1.0
     */
    public static DnsMessage decode(byte[] data) {
        return decode(data, 0, data.length);
    }

    /**
     * Decodes a DNS message from a region of a byte array.
     *
     * @param data   the byte array
     * @param offset the starting offset
     * @param length the number of bytes
     * @return the decoded message
     * @throws DnsFormatException if the message is malformed
     * @since 0.1.0
     */
    public static DnsMessage decode(byte[] data, int offset, int length) {
        if (length < DnsHeader.SIZE) {
            throw new DnsFormatException("Message too short for header: " + length);
        }

        ByteBuffer buf = ByteBuffer.wrap(data, offset, length);
        int id = buf.getShort() & 0xFFFF;
        int flags = buf.getShort() & 0xFFFF;
        int qdCount = buf.getShort() & 0xFFFF;
        int anCount = buf.getShort() & 0xFFFF;
        int nsCount = buf.getShort() & 0xFFFF;
        int arCount = buf.getShort() & 0xFFFF;

        DnsHeader header = DnsHeader.fromFlags(id, flags, qdCount, anCount, nsCount, arCount);

        DnsMessage.Builder builder = DnsMessage.builder().header(header);

        for (int i = 0; i < qdCount; i++) {
            DnsName name = decodeName(data, buf);
            int qtype = buf.getShort() & 0xFFFF;
            int qclass = buf.getShort() & 0xFFFF;
            RecordType type = RecordType.fromValue(qtype);
            RecordClass rclass = RecordClass.fromValue(qclass);
            builder.addQuestion(new DnsQuestion(name, type, rclass));
        }

        for (int i = 0; i < anCount; i++) {
            builder.addAnswer(decodeRecord(data, buf));
        }
        for (int i = 0; i < nsCount; i++) {
            builder.addAuthority(decodeRecord(data, buf));
        }
        for (int i = 0; i < arCount; i++) {
            builder.addAdditional(decodeRecord(data, buf));
        }

        return builder.build();
    }

    private static DnsRecord decodeRecord(byte[] packet, ByteBuffer buf) {
        DnsName name = decodeName(packet, buf);
        int typeVal = buf.getShort() & 0xFFFF;
        int classVal = buf.getShort() & 0xFFFF;
        long ttl = buf.getInt() & 0xFFFFFFFFL;
        int rdLength = buf.getShort() & 0xFFFF;

        int rdStart = buf.position();
        RecordType type = RecordType.fromValueOrNull(typeVal);
        if (type == null) {
            type = RecordType.A; // fallback, will use raw
            byte[] rawData = new byte[rdLength];
            buf.get(rawData);
            return new DnsRecord(name, type, RecordClass.fromValue(classVal), ttl,
                    new RawRData(type, rawData));
        }

        RecordClass rclass;
        if (type == RecordType.OPT) {
            rclass = RecordClass.IN; // OPT uses class for UDP size
        } else {
            rclass = RecordClass.fromValue(classVal);
        }

        RData rdata = decodeRData(type, packet, buf, rdLength, classVal, ttl);
        buf.position(rdStart + rdLength);

        if (type == RecordType.OPT) {
            OptRecord opt = (OptRecord) rdata;
            return new DnsRecord(name, type, rclass, ttl, opt);
        }

        return new DnsRecord(name, type, rclass, ttl, rdata);
    }

    private static RData decodeRData(RecordType type, byte[] packet, ByteBuffer buf,
                                      int rdLength, int classVal, long ttl) {
        return switch (type) {
            case A -> {
                byte[] addr = new byte[4];
                buf.get(addr);
                yield ARecord.fromBytes(addr);
            }
            case AAAA -> {
                byte[] addr = new byte[16];
                buf.get(addr);
                yield AaaaRecord.fromBytes(addr);
            }
            case NS -> new NsRecord(decodeName(packet, buf));
            case CNAME -> new CnameRecord(decodeName(packet, buf));
            case PTR -> new PtrRecord(decodeName(packet, buf));
            case MX -> {
                int pref = buf.getShort() & 0xFFFF;
                DnsName exchange = decodeName(packet, buf);
                yield new MxRecord(pref, exchange);
            }
            case SOA -> {
                DnsName mname = decodeName(packet, buf);
                DnsName rname = decodeName(packet, buf);
                long serial = buf.getInt() & 0xFFFFFFFFL;
                int refresh = buf.getInt();
                int retry = buf.getInt();
                int expire = buf.getInt();
                int minimum = buf.getInt();
                yield new SoaRecord(mname, rname, serial, refresh, retry, expire, minimum);
            }
            case TXT -> {
                int end = buf.position() + rdLength;
                List<String> strings = new ArrayList<>();
                while (buf.position() < end) {
                    int slen = buf.get() & 0xFF;
                    byte[] sdata = new byte[slen];
                    buf.get(sdata);
                    strings.add(new String(sdata, StandardCharsets.UTF_8));
                }
                yield new TxtRecord(strings);
            }
            case SRV -> {
                int priority = buf.getShort() & 0xFFFF;
                int weight = buf.getShort() & 0xFFFF;
                int port = buf.getShort() & 0xFFFF;
                DnsName target = decodeName(packet, buf);
                yield new SrvRecord(priority, weight, port, target);
            }
            case NAPTR -> {
                int order = buf.getShort() & 0xFFFF;
                int preference = buf.getShort() & 0xFFFF;
                String flags = decodeCharacterString(buf);
                String service = decodeCharacterString(buf);
                String regexp = decodeCharacterString(buf);
                DnsName replacement = decodeName(packet, buf);
                yield new NaptrRecord(order, preference, flags, service, regexp, replacement);
            }
            case CAA -> {
                int caaFlags = buf.get() & 0xFF;
                int tagLen = buf.get() & 0xFF;
                byte[] tagBytes = new byte[tagLen];
                buf.get(tagBytes);
                String tag = new String(tagBytes, StandardCharsets.US_ASCII);
                int valueLen = rdLength - 2 - tagLen;
                byte[] valueBytes = new byte[valueLen];
                buf.get(valueBytes);
                String value = new String(valueBytes, StandardCharsets.UTF_8);
                yield new CaaRecord(caaFlags, tag, value);
            }
            case OPT -> {
                int udpSize = classVal;
                OptRecord.OptFlags optFlags = OptRecord.parseTtlField((int) ttl);
                int end = buf.position() + rdLength;
                List<OptRecord.EdnsOption> options = new ArrayList<>();
                while (buf.position() + 4 <= end) {
                    int code = buf.getShort() & 0xFFFF;
                    int dataLen = buf.getShort() & 0xFFFF;
                    byte[] optData = new byte[dataLen];
                    buf.get(optData);
                    options.add(new OptRecord.EdnsOption(code, optData));
                }
                yield new OptRecord(udpSize, optFlags.extendedRcode(),
                        optFlags.version(), optFlags.dnssecOk(), options);
            }
            case DNSKEY -> {
                int dkFlags = buf.getShort() & 0xFFFF;
                int protocol = buf.get() & 0xFF;
                int algorithm = buf.get() & 0xFF;
                byte[] pubKey = new byte[rdLength - 4];
                buf.get(pubKey);
                yield new DnskeyRecord(dkFlags, protocol, algorithm, pubKey);
            }
            case RRSIG -> {
                int typeCoveredVal = buf.getShort() & 0xFFFF;
                int algorithm = buf.get() & 0xFF;
                int labels = buf.get() & 0xFF;
                long origTtl = buf.getInt() & 0xFFFFFFFFL;
                long expSecs = buf.getInt() & 0xFFFFFFFFL;
                long incSecs = buf.getInt() & 0xFFFFFFFFL;
                int keyTag = buf.getShort() & 0xFFFF;
                DnsName signerName = decodeName(packet, buf);
                int sigLen = rdLength - (buf.position() - (buf.position() - rdLength)); // remaining
                // Calculate remaining bytes from rdStart
                int consumed = buf.position() - (buf.position()); // Need to track start
                // simpler: read remaining
                int sigStart = buf.position();
                int rdEnd = buf.position() + (rdLength - (sigStart - (sigStart - 18 - signerName.wireLength())));
                // Use rdLength directly by computing what we've read
                byte[] sig = new byte[rdLength - 18 - signerName.wireLength()];
                buf.get(sig);
                yield new RrsigRecord(
                        RecordType.fromValue(typeCoveredVal), algorithm, labels,
                        origTtl, Instant.ofEpochSecond(expSecs),
                        Instant.ofEpochSecond(incSecs), keyTag, signerName, sig);
            }
            case DS -> {
                int keyTag = buf.getShort() & 0xFFFF;
                int algorithm = buf.get() & 0xFF;
                int digestType = buf.get() & 0xFF;
                byte[] digest = new byte[rdLength - 4];
                buf.get(digest);
                yield new DsRecord(keyTag, algorithm, digestType, digest);
            }
            case NSEC -> {
                DnsName nextName = decodeName(packet, buf);
                int remaining = rdLength - (buf.position() - buf.position()); // compute
                // Read remaining as type bit maps
                int bitmapStart = buf.position();
                // We need to figure out how many bytes the name consumed
                // Since we moved past it, compute remaining from rdLength
                int nameWireLen = nextName.wireLength();
                int bitmapLen = rdLength - nameWireLen;
                byte[] bitmapData = new byte[bitmapLen];
                buf.get(bitmapData);
                Set<RecordType> types = TypeBitMaps.decode(bitmapData, 0, bitmapLen);
                yield new NsecRecord(nextName, types);
            }
            case NSEC3 -> {
                int hashAlg = buf.get() & 0xFF;
                int nsec3Flags = buf.get() & 0xFF;
                int iterations = buf.getShort() & 0xFFFF;
                int saltLen = buf.get() & 0xFF;
                byte[] salt = new byte[saltLen];
                buf.get(salt);
                int hashLen = buf.get() & 0xFF;
                byte[] nextHash = new byte[hashLen];
                buf.get(nextHash);
                int bitmapLen = rdLength - 6 - saltLen - hashLen;
                byte[] bitmapData = new byte[bitmapLen];
                buf.get(bitmapData);
                Set<RecordType> types = TypeBitMaps.decode(bitmapData, 0, bitmapLen);
                yield new Nsec3Record(hashAlg, nsec3Flags, iterations, salt, nextHash, types);
            }
            case NSEC3PARAM -> {
                int hashAlg = buf.get() & 0xFF;
                int n3pFlags = buf.get() & 0xFF;
                int iterations = buf.getShort() & 0xFFFF;
                int saltLen = buf.get() & 0xFF;
                byte[] salt = new byte[saltLen];
                buf.get(salt);
                yield new Nsec3ParamRecord(hashAlg, n3pFlags, iterations, salt);
            }
            case ANY -> {
                byte[] rawData = new byte[rdLength];
                buf.get(rawData);
                yield new RawRData(type, rawData);
            }
        };
    }

    /**
     * Decodes a DNS domain name from the packet, handling compression pointers.
     *
     * @param packet the full packet bytes (for pointer resolution)
     * @param buf    the current read position
     * @return the decoded domain name
     * @since 0.1.0
     */
    public static DnsName decodeName(byte[] packet, ByteBuffer buf) {
        List<String> labels = new ArrayList<>();
        boolean jumped = false;
        int savedPos = -1;
        int pos = buf.position();
        int maxJumps = 128; // prevent infinite loops
        int jumps = 0;

        while (true) {
            if (jumps++ > maxJumps) {
                throw new DnsFormatException("Too many compression pointers");
            }
            if (pos >= packet.length) {
                throw new DnsFormatException("Name extends beyond packet");
            }
            int len = packet[pos] & 0xFF;

            if (len == 0) {
                pos++;
                if (!jumped) {
                    buf.position(pos);
                }
                break;
            }

            if ((len & 0xC0) == 0xC0) {
                // Compression pointer
                if (pos + 1 >= packet.length) {
                    throw new DnsFormatException("Truncated compression pointer");
                }
                if (!jumped) {
                    savedPos = pos + 2;
                    jumped = true;
                }
                int pointer = ((len & 0x3F) << 8) | (packet[pos + 1] & 0xFF);
                pos = pointer;
                continue;
            }

            pos++;
            if (pos + len > packet.length) {
                throw new DnsFormatException("Label extends beyond packet");
            }
            labels.add(new String(packet, pos, len, StandardCharsets.US_ASCII));
            pos += len;
        }

        if (jumped && savedPos >= 0) {
            buf.position(savedPos);
        }

        return DnsName.fromLabels(labels);
    }

    private static String decodeCharacterString(ByteBuffer buf) {
        int len = buf.get() & 0xFF;
        byte[] data = new byte[len];
        buf.get(data);
        return new String(data, StandardCharsets.UTF_8);
    }

    // ---- Encoding ----

    /**
     * Encodes a DNS message to raw bytes.
     *
     * @param message the message to encode
     * @return the encoded bytes
     * @since 0.1.0
     */
    public static byte[] encode(DnsMessage message) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(512);
        Map<String, Integer> compressionMap = new HashMap<>();

        // Header
        DnsHeader h = message.header();
        writeShort(out, h.id());
        writeShort(out, h.flags());
        writeShort(out, message.questions().size());
        writeShort(out, message.answers().size());
        writeShort(out, message.authority().size());
        writeShort(out, message.additional().size());

        // Questions
        for (DnsQuestion q : message.questions()) {
            encodeName(out, q.name(), compressionMap);
            writeShort(out, q.type().value());
            writeShort(out, q.recordClass().value());
        }

        // Answer, Authority, Additional sections
        for (DnsRecord r : message.answers()) {
            encodeRecord(out, r, compressionMap);
        }
        for (DnsRecord r : message.authority()) {
            encodeRecord(out, r, compressionMap);
        }
        for (DnsRecord r : message.additional()) {
            encodeRecord(out, r, compressionMap);
        }

        return out.toByteArray();
    }

    private static void encodeRecord(ByteArrayOutputStream out, DnsRecord record,
                                      Map<String, Integer> compressionMap) {
        if (record.type() == RecordType.OPT) {
            // OPT pseudo-record: name=root, type=OPT, class=UDP size, TTL=flags
            out.write(0); // root name
            writeShort(out, RecordType.OPT.value());
            OptRecord opt = (OptRecord) record.rdata();
            writeShort(out, opt.udpPayloadSize());
            writeInt(out, opt.ttlField());
            // RDATA
            byte[] rdata = encodeOptRData(opt);
            writeShort(out, rdata.length);
            out.write(rdata, 0, rdata.length);
            return;
        }

        encodeName(out, record.name(), compressionMap);
        writeShort(out, record.type().value());
        writeShort(out, record.recordClass().value());
        writeInt(out, (int) record.ttl());

        byte[] rdata = encodeRData(record.rdata(), compressionMap, out.size());
        writeShort(out, rdata.length);
        out.write(rdata, 0, rdata.length);
    }

    private static byte[] encodeRData(RData rdata, Map<String, Integer> compressionMap,
                                       int currentOffset) {
        ByteArrayOutputStream rdOut = new ByteArrayOutputStream();

        switch (rdata) {
            case ARecord a -> rdOut.write(a.address().getAddress(), 0, 4);
            case AaaaRecord aaaa -> rdOut.write(aaaa.address().getAddress(), 0, 16);
            case NsRecord ns -> encodeName(rdOut, ns.nameServer(), new HashMap<>());
            case CnameRecord cn -> encodeName(rdOut, cn.cname(), new HashMap<>());
            case PtrRecord ptr -> encodeName(rdOut, ptr.domainName(), new HashMap<>());
            case MxRecord mx -> {
                writeShort(rdOut, mx.preference());
                encodeName(rdOut, mx.exchange(), new HashMap<>());
            }
            case SoaRecord soa -> {
                encodeName(rdOut, soa.mname(), new HashMap<>());
                encodeName(rdOut, soa.rname(), new HashMap<>());
                writeInt(rdOut, (int) soa.serial());
                writeInt(rdOut, soa.refresh());
                writeInt(rdOut, soa.retry());
                writeInt(rdOut, soa.expire());
                writeInt(rdOut, soa.minimum());
            }
            case TxtRecord txt -> {
                for (String s : txt.strings()) {
                    byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
                    if (bytes.length > 255) {
                        throw new IllegalArgumentException("TXT string exceeds 255 bytes");
                    }
                    rdOut.write(bytes.length);
                    rdOut.write(bytes, 0, bytes.length);
                }
            }
            case SrvRecord srv -> {
                writeShort(rdOut, srv.priority());
                writeShort(rdOut, srv.weight());
                writeShort(rdOut, srv.port());
                encodeName(rdOut, srv.target(), new HashMap<>());
            }
            case NaptrRecord naptr -> {
                writeShort(rdOut, naptr.order());
                writeShort(rdOut, naptr.preference());
                encodeCharacterString(rdOut, naptr.flags());
                encodeCharacterString(rdOut, naptr.service());
                encodeCharacterString(rdOut, naptr.regexp());
                encodeName(rdOut, naptr.replacement(), new HashMap<>());
            }
            case CaaRecord caa -> {
                rdOut.write(caa.flags());
                byte[] tag = caa.tag().getBytes(StandardCharsets.US_ASCII);
                rdOut.write(tag.length);
                rdOut.write(tag, 0, tag.length);
                byte[] value = caa.value().getBytes(StandardCharsets.UTF_8);
                rdOut.write(value, 0, value.length);
            }
            case OptRecord opt -> {
                // Should not reach here -- handled specially
                byte[] optData = encodeOptRData(opt);
                rdOut.write(optData, 0, optData.length);
            }
            case DnskeyRecord dk -> {
                writeShort(rdOut, dk.flags());
                rdOut.write(dk.protocol());
                rdOut.write(dk.algorithm());
                byte[] pk = dk.publicKey();
                rdOut.write(pk, 0, pk.length);
            }
            case RrsigRecord rrsig -> {
                writeShort(rdOut, rrsig.typeCovered().value());
                rdOut.write(rrsig.algorithm());
                rdOut.write(rrsig.labels());
                writeInt(rdOut, (int) rrsig.originalTtl());
                writeInt(rdOut, (int) rrsig.expiration().getEpochSecond());
                writeInt(rdOut, (int) rrsig.inception().getEpochSecond());
                writeShort(rdOut, rrsig.keyTag());
                encodeName(rdOut, rrsig.signerName(), new HashMap<>());
                byte[] sig = rrsig.signature();
                rdOut.write(sig, 0, sig.length);
            }
            case DsRecord ds -> {
                writeShort(rdOut, ds.keyTag());
                rdOut.write(ds.algorithm());
                rdOut.write(ds.digestType());
                byte[] digest = ds.digest();
                rdOut.write(digest, 0, digest.length);
            }
            case NsecRecord nsec -> {
                encodeName(rdOut, nsec.nextDomainName(), new HashMap<>());
                byte[] bitmaps = nsec.encodeTypeBitMaps();
                rdOut.write(bitmaps, 0, bitmaps.length);
            }
            case Nsec3Record nsec3 -> {
                rdOut.write(nsec3.hashAlgorithm());
                rdOut.write(nsec3.flags());
                writeShort(rdOut, nsec3.iterations());
                byte[] salt = nsec3.salt();
                rdOut.write(salt.length);
                rdOut.write(salt, 0, salt.length);
                byte[] nextHash = nsec3.nextHashedOwner();
                rdOut.write(nextHash.length);
                rdOut.write(nextHash, 0, nextHash.length);
                byte[] bitmaps = TypeBitMaps.encode(nsec3.types());
                rdOut.write(bitmaps, 0, bitmaps.length);
            }
            case Nsec3ParamRecord n3p -> {
                rdOut.write(n3p.hashAlgorithm());
                rdOut.write(n3p.flags());
                writeShort(rdOut, n3p.iterations());
                byte[] salt = n3p.salt();
                rdOut.write(salt.length);
                rdOut.write(salt, 0, salt.length);
            }
            case RawRData raw -> {
                byte[] d = raw.data();
                rdOut.write(d, 0, d.length);
            }
        }

        return rdOut.toByteArray();
    }

    private static byte[] encodeOptRData(OptRecord opt) {
        ByteArrayOutputStream rdOut = new ByteArrayOutputStream();
        for (OptRecord.EdnsOption option : opt.options()) {
            writeShort(rdOut, option.code());
            byte[] data = option.data();
            writeShort(rdOut, data.length);
            rdOut.write(data, 0, data.length);
        }
        return rdOut.toByteArray();
    }

    /**
     * Encodes a DNS domain name with compression.
     *
     * @param out            the output stream
     * @param name           the domain name
     * @param compressionMap map of previously-written names to offsets
     * @since 0.1.0
     */
    public static void encodeName(ByteArrayOutputStream out, DnsName name,
                                   Map<String, Integer> compressionMap) {
        if (name.isRoot()) {
            out.write(0);
            return;
        }

        List<String> labels = name.labels();
        for (int i = 0; i < labels.size(); i++) {
            String suffix = String.join(".", labels.subList(i, labels.size())).toLowerCase(Locale.ROOT);
            Integer pointer = compressionMap.get(suffix);
            if (pointer != null) {
                // Write compression pointer
                int ptr = 0xC000 | pointer;
                out.write((ptr >> 8) & 0xFF);
                out.write(ptr & 0xFF);
                return;
            }

            // Record this suffix's offset
            compressionMap.put(suffix, out.size());

            // Write label
            byte[] labelBytes = labels.get(i).getBytes(StandardCharsets.US_ASCII);
            out.write(labelBytes.length);
            out.write(labelBytes, 0, labelBytes.length);
        }

        // Terminating zero
        out.write(0);
    }

    private static void encodeCharacterString(ByteArrayOutputStream out, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.write(bytes.length);
        out.write(bytes, 0, bytes.length);
    }

    private static void writeShort(ByteArrayOutputStream out, int value) {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >> 24) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }
}
