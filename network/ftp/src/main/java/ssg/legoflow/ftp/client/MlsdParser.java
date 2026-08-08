package ssg.legoflow.ftp.client;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Parses MLSD/MLST machine-readable directory listing format (RFC 3659).
 *
 * <p>MLSD entries have the format: {@code facts SP entry-name CRLF}
 * where facts are semicolon-separated key=value pairs:
 * <pre>
 *   type=file;size=12345;modify=20240115103000; filename.txt
 * </pre>
 *
 * <p>Standard facts include:
 * <ul>
 *   <li>{@code type} — entry type (file, dir, cdir, pdir, OS.unix=slink)</li>
 *   <li>{@code size} — file size in bytes</li>
 *   <li>{@code modify} — modification time (YYYYMMDDHHMMSS[.sss])</li>
 *   <li>{@code create} — creation time</li>
 *   <li>{@code perm} — permission indicators</li>
 *   <li>{@code unique} — unique entry identifier</li>
 *   <li>{@code lang} — language tag</li>
 *   <li>{@code media-type} — MIME type</li>
 *   <li>{@code charset} — character encoding</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class MlsdParser {

    private static final DateTimeFormatter MLSD_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private MlsdParser() {
        // utility class
    }

    /**
     * Parses a complete MLSD response into file entries.
     *
     * @param mlsdOutput the raw MLSD output (multiple lines)
     * @return the parsed entries
     */
    public static List<FtpFileEntry> parse(String mlsdOutput) {
        if (mlsdOutput == null || mlsdOutput.isBlank()) {
            return List.of();
        }
        List<FtpFileEntry> entries = new ArrayList<>();
        for (String line : mlsdOutput.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            FtpFileEntry entry = parseLine(trimmed);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    /**
     * Parses a single MLSD/MLST entry line.
     *
     * @param line the entry line (facts followed by a space and the filename)
     * @return the parsed entry, or {@code null} if the line cannot be parsed
     */
    public static FtpFileEntry parseLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        // Split into facts and name at the first space after the facts
        int spaceIdx = line.indexOf(' ');
        if (spaceIdx < 0) {
            return null;
        }

        String factsStr = line.substring(0, spaceIdx);
        String name = line.substring(spaceIdx + 1);

        if (name.isEmpty()) {
            return null;
        }

        Map<String, String> facts = parseFacts(factsStr);

        // Determine type
        String typeStr = facts.getOrDefault("type", "").toLowerCase();
        FtpFileEntry.Type type = switch (typeStr) {
            case "file" -> FtpFileEntry.Type.FILE;
            case "dir", "cdir", "pdir" -> FtpFileEntry.Type.DIRECTORY;
            default -> {
                if (typeStr.contains("slink") || typeStr.contains("symlink")) {
                    yield FtpFileEntry.Type.SYMLINK;
                }
                yield FtpFileEntry.Type.UNKNOWN;
            }
        };

        // Size
        long size = 0;
        String sizeStr = facts.get("size");
        if (sizeStr != null) {
            try {
                size = Long.parseLong(sizeStr);
            } catch (NumberFormatException ignored) {
                // leave as 0
            }
        }

        // Modification time
        LocalDateTime modified = parseTimestamp(facts.get("modify"));

        // Permissions
        String perms = facts.get("perm");

        return new FtpFileEntry(name, size, modified, type, perms, null, null, 0, line);
    }

    /**
     * Parses the facts portion of an MLSD line into a map.
     *
     * @param factsStr the semicolon-separated facts string
     * @return a map of fact names to values (keys are lowercase)
     */
    public static Map<String, String> parseFacts(String factsStr) {
        Map<String, String> facts = new LinkedHashMap<>();
        if (factsStr == null || factsStr.isEmpty()) {
            return facts;
        }
        // Remove trailing semicolon if present
        if (factsStr.endsWith(";")) {
            factsStr = factsStr.substring(0, factsStr.length() - 1);
        }
        for (String fact : factsStr.split(";")) {
            int eqIdx = fact.indexOf('=');
            if (eqIdx > 0) {
                String key = fact.substring(0, eqIdx).trim().toLowerCase();
                String value = fact.substring(eqIdx + 1).trim();
                facts.put(key, value);
            }
        }
        return facts;
    }

    /**
     * Parses an MLSD timestamp (YYYYMMDDHHMMSS[.sss]).
     *
     * @param timestamp the timestamp string
     * @return the parsed datetime, or {@code null} if unparseable
     */
    public static LocalDateTime parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return null;
        }
        // Remove fractional seconds if present
        String clean = timestamp.contains(".") ?
                timestamp.substring(0, timestamp.indexOf('.')) : timestamp;
        try {
            return LocalDateTime.parse(clean, MLSD_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Formats a datetime as an MLSD timestamp.
     *
     * @param dateTime the datetime to format
     * @return the MLSD timestamp string (YYYYMMDDHHMMSS)
     */
    public static String formatTimestamp(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.format(MLSD_DATE);
    }
}
