package ssg.legoflow.network.dns.server;

import ssg.legoflow.network.dns.protocol.*;
import ssg.legoflow.network.dns.rdata.*;

import java.io.*;
import java.util.Objects;

/**
 * Parser for BIND-format zone files (RFC 1035, Section 5).
 *
 * <p>Supports the most common directives and record types:
 * <ul>
 *   <li>{@code $ORIGIN} — sets the default origin</li>
 *   <li>{@code $TTL} — sets the default TTL</li>
 *   <li>A, AAAA, NS, CNAME, MX, SOA, TXT, SRV, PTR, CAA records</li>
 *   <li>{@code @} as shorthand for the current origin</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class ZoneFile {

    private ZoneFile() {}

    /**
     * Parses a zone file from a string.
     *
     * @param content the zone file content
     * @return the parsed zone
     * @throws DnsFormatException if the zone file is malformed
     * @since 1.0.0
     */
    public static AuthoritativeZone parse(String content) {
        return parse(new BufferedReader(new StringReader(content)));
    }

    /**
     * Parses a zone file from a reader.
     *
     * @param reader the input reader
     * @return the parsed zone
     * @throws DnsFormatException if the zone file is malformed
     * @since 1.0.0
     */
    public static AuthoritativeZone parse(BufferedReader reader) {
        DnsName origin = DnsName.ROOT;
        long defaultTtl = 3600;
        AuthoritativeZone zone = null;
        String lastName = null;

        try {
            String line;
            StringBuilder multiLine = new StringBuilder();
            boolean inParens = false;

            while ((line = reader.readLine()) != null) {
                // Handle comments
                int commentIdx = line.indexOf(';');
                if (commentIdx >= 0) {
                    line = line.substring(0, commentIdx);
                }
                line = line.trim();
                if (line.isEmpty()) continue;

                // Handle multi-line records (parentheses)
                if (inParens) {
                    multiLine.append(' ').append(line);
                    if (line.contains(")")) {
                        inParens = false;
                        line = multiLine.toString().replace("(", "").replace(")", "").trim();
                        multiLine.setLength(0);
                    } else {
                        continue;
                    }
                } else if (line.contains("(") && !line.contains(")")) {
                    multiLine.append(line);
                    inParens = true;
                    continue;
                }

                // Handle directives
                if (line.startsWith("$ORIGIN")) {
                    origin = DnsName.of(line.substring(7).trim());
                    continue;
                }
                if (line.startsWith("$TTL")) {
                    defaultTtl = parseTtlValue(line.substring(4).trim());
                    continue;
                }

                // Parse record
                String[] parts = line.split("\\s+");
                if (parts.length < 3) continue;

                int idx = 0;
                String name;

                // Name field
                if (parts[0].equals("@")) {
                    name = origin.toString();
                    idx = 1;
                } else if (Character.isWhitespace(line.charAt(0))) {
                    name = lastName != null ? lastName : origin.toString();
                    idx = 0;
                } else if (isRecordClass(parts[0]) || isRecordType(parts[0]) || isNumber(parts[0])) {
                    name = lastName != null ? lastName : origin.toString();
                    idx = 0;
                } else {
                    name = parts[0];
                    if (!name.endsWith(".")) {
                        name = name + "." + origin;
                    }
                    idx = 1;
                    lastName = name;
                }

                // TTL (optional)
                long ttl = defaultTtl;
                if (idx < parts.length && isNumber(parts[idx])) {
                    ttl = parseTtlValue(parts[idx]);
                    idx++;
                }

                // Class (optional)
                if (idx < parts.length && isRecordClass(parts[idx])) {
                    idx++; // skip class
                }

                if (idx >= parts.length) continue;
                String typeStr = parts[idx++].toUpperCase();

                DnsName dnsName = DnsName.of(name);

                switch (typeStr) {
                    case "SOA" -> {
                        if (idx + 6 < parts.length) continue;
                        String mname = qualify(parts[idx++], origin);
                        String rname = qualify(parts[idx++], origin);
                        long serial = Long.parseLong(parts[idx++]);
                        int refresh = (int) parseTtlValue(parts[idx++]);
                        int retry = (int) parseTtlValue(parts[idx++]);
                        int expire = (int) parseTtlValue(parts[idx++]);
                        int minimum = (int) parseTtlValue(parts[idx]);
                        SoaRecord soa = SoaRecord.of(mname, rname, serial, refresh, retry, expire, minimum);
                        if (zone == null) {
                            zone = new AuthoritativeZone(origin, soa);
                        }
                    }
                    case "A" -> {
                        if (zone == null) zone = createDefaultZone(origin);
                        zone.addA(name, ttl, parts[idx]);
                    }
                    case "AAAA" -> {
                        if (zone == null) zone = createDefaultZone(origin);
                        zone.addAAAA(name, ttl, parts[idx]);
                    }
                    case "NS" -> {
                        if (zone == null) zone = createDefaultZone(origin);
                        zone.addNS(name, ttl, qualify(parts[idx], origin));
                    }
                    case "CNAME" -> {
                        if (zone == null) zone = createDefaultZone(origin);
                        zone.addCNAME(name, ttl, qualify(parts[idx], origin));
                    }
                    case "MX" -> {
                        if (zone == null) zone = createDefaultZone(origin);
                        int pref = Integer.parseInt(parts[idx++]);
                        zone.addMX(name, ttl, pref, qualify(parts[idx], origin));
                    }
                    case "TXT" -> {
                        if (zone == null) zone = createDefaultZone(origin);
                        // Join remaining parts, strip quotes
                        StringBuilder txt = new StringBuilder();
                        for (int i = idx; i < parts.length; i++) {
                            if (i > idx) txt.append(' ');
                            txt.append(parts[i]);
                        }
                        String text = txt.toString().replace("\"", "");
                        zone.addTXT(name, ttl, text);
                    }
                    case "SRV" -> {
                        if (zone == null) zone = createDefaultZone(origin);
                        int priority = Integer.parseInt(parts[idx++]);
                        int weight = Integer.parseInt(parts[idx++]);
                        int port = Integer.parseInt(parts[idx++]);
                        zone.addSRV(name, ttl, priority, weight, port, qualify(parts[idx], origin));
                    }
                    case "PTR" -> {
                        if (zone == null) zone = createDefaultZone(origin);
                        zone.addRecord(DnsRecord.of(name, ttl, PtrRecord.of(qualify(parts[idx], origin))));
                    }
                    case "CAA" -> {
                        if (zone == null) zone = createDefaultZone(origin);
                        int flags = Integer.parseInt(parts[idx++]);
                        String tag = parts[idx++];
                        String value = parts[idx].replace("\"", "");
                        zone.addRecord(DnsRecord.of(name, ttl, CaaRecord.of(flags, tag, value)));
                    }
                    default -> {
                        // Skip unknown types
                    }
                }
            }
        } catch (IOException e) {
            throw new DnsFormatException("Failed to read zone file", e);
        }

        if (zone == null) {
            throw new DnsFormatException("Zone file contains no SOA record");
        }
        return zone;
    }

    private static String qualify(String name, DnsName origin) {
        if (name.endsWith(".")) return name;
        return name + "." + origin;
    }

    private static boolean isRecordClass(String s) {
        return s.equalsIgnoreCase("IN") || s.equalsIgnoreCase("CH")
                || s.equalsIgnoreCase("HS") || s.equalsIgnoreCase("ANY");
    }

    private static boolean isRecordType(String s) {
        try {
            RecordType.valueOf(s.toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isNumber(String s) {
        try {
            Long.parseLong(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static long parseTtlValue(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            // Handle time suffixes (h, m, s, d, w)
            long total = 0;
            long current = 0;
            for (char c : s.toCharArray()) {
                if (Character.isDigit(c)) {
                    current = current * 10 + (c - '0');
                } else {
                    total += switch (Character.toLowerCase(c)) {
                        case 'w' -> current * 7 * 24 * 3600;
                        case 'd' -> current * 24 * 3600;
                        case 'h' -> current * 3600;
                        case 'm' -> current * 60;
                        case 's' -> current;
                        default -> current;
                    };
                    current = 0;
                }
            }
            return total + current;
        }
    }

    private static AuthoritativeZone createDefaultZone(DnsName origin) {
        SoaRecord soa = SoaRecord.of(
                "ns1." + origin, "admin." + origin,
                1, 3600, 900, 604800, 86400);
        return new AuthoritativeZone(origin, soa);
    }
}
