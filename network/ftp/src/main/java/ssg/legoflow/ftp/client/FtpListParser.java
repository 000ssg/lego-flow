package ssg.legoflow.ftp.client;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses FTP LIST output into structured {@link FtpFileEntry} objects.
 *
 * <p>Supports two formats:
 * <ul>
 *   <li><strong>Unix {@code ls -l}</strong>: {@code drwxr-xr-x 2 user group 4096 Jan 15 10:30 dirname}</li>
 *   <li><strong>Windows DIR</strong>: {@code 01-15-24 10:30AM <DIR> dirname}</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class FtpListParser {

    // Unix: -rwxr-xr-x 1 user group 12345 Jan 15 10:30 filename
    // Also: lrwxrwxrwx 1 user group 12345 Jan 15 10:30 link -> target
    private static final Pattern UNIX_PATTERN = Pattern.compile(
            "^([dlsbcp-])([rwxsStT-]{9})\\s+(\\d+)\\s+(\\S+)\\s+(\\S+)\\s+(\\d+)\\s+" +
                    "(\\w{3}\\s+\\d{1,2}\\s+(?:\\d{4}|\\d{1,2}:\\d{2}))\\s+(.+)$"
    );

    // Windows: 01-15-24 10:30AM <DIR> dirname
    // Windows: 01-15-24 10:30AM 12345 filename.txt
    private static final Pattern WINDOWS_PATTERN = Pattern.compile(
            "^(\\d{2}-\\d{2}-\\d{2,4})\\s+(\\d{1,2}:\\d{2}[AP]M)\\s+(<DIR>|\\d+)\\s+(.+)$"
    );

    private static final DateTimeFormatter UNIX_DATE_TIME = DateTimeFormatter.ofPattern(
            "MMM d HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter UNIX_DATE_YEAR = DateTimeFormatter.ofPattern(
            "MMM d yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter WINDOWS_DATE_TIME = DateTimeFormatter.ofPattern(
            "MM-dd-yy hh:mma", Locale.ENGLISH);
    private static final DateTimeFormatter WINDOWS_DATE_TIME_4Y = DateTimeFormatter.ofPattern(
            "MM-dd-yyyy hh:mma", Locale.ENGLISH);

    private FtpListParser() {
        // utility class
    }

    /**
     * Parses a complete LIST response into file entries.
     *
     * @param listOutput the raw LIST output (multiple lines)
     * @return the parsed entries
     */
    public static List<FtpFileEntry> parse(String listOutput) {
        if (listOutput == null || listOutput.isBlank()) {
            return List.of();
        }
        List<FtpFileEntry> entries = new ArrayList<>();
        for (String line : listOutput.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("total ")) {
                continue;
            }
            FtpFileEntry entry = parseLine(trimmed);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    /**
     * Parses a single LIST line.
     *
     * @param line the listing line
     * @return the parsed entry, or {@code null} if the line cannot be parsed
     */
    public static FtpFileEntry parseLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        FtpFileEntry entry = parseUnix(line);
        if (entry != null) return entry;
        return parseWindows(line);
    }

    /**
     * Parses a Unix-format {@code ls -l} listing line.
     *
     * @param line the listing line
     * @return the parsed entry, or {@code null} if not Unix format
     */
    public static FtpFileEntry parseUnix(String line) {
        Matcher m = UNIX_PATTERN.matcher(line);
        if (!m.matches()) {
            return null;
        }

        char typeChar = m.group(1).charAt(0);
        String permissions = m.group(2);
        int linkCount = Integer.parseInt(m.group(3));
        String owner = m.group(4);
        String group = m.group(5);
        long size = Long.parseLong(m.group(6));
        String dateStr = m.group(7).replaceAll("\\s+", " ");
        String fileName = m.group(8);

        FtpFileEntry.Type type = switch (typeChar) {
            case 'd' -> FtpFileEntry.Type.DIRECTORY;
            case 'l' -> FtpFileEntry.Type.SYMLINK;
            case '-' -> FtpFileEntry.Type.FILE;
            default -> FtpFileEntry.Type.UNKNOWN;
        };

        // Handle symlinks: "name -> target" — extract just the name
        if (type == FtpFileEntry.Type.SYMLINK && fileName.contains(" -> ")) {
            fileName = fileName.substring(0, fileName.indexOf(" -> "));
        }

        LocalDateTime modified = parseUnixDate(dateStr);

        return new FtpFileEntry(fileName, size, modified, type, permissions,
                owner, group, linkCount, line);
    }

    /**
     * Parses a Windows DIR format listing line.
     *
     * @param line the listing line
     * @return the parsed entry, or {@code null} if not Windows format
     */
    public static FtpFileEntry parseWindows(String line) {
        Matcher m = WINDOWS_PATTERN.matcher(line);
        if (!m.matches()) {
            return null;
        }

        String dateStr = m.group(1);
        String timeStr = m.group(2);
        String sizeOrDir = m.group(3);
        String name = m.group(4).trim();

        FtpFileEntry.Type type;
        long size;
        if ("<DIR>".equals(sizeOrDir)) {
            type = FtpFileEntry.Type.DIRECTORY;
            size = 0;
        } else {
            type = FtpFileEntry.Type.FILE;
            size = Long.parseLong(sizeOrDir);
        }

        LocalDateTime modified = parseWindowsDate(dateStr, timeStr);

        return new FtpFileEntry(name, size, modified, type, null, null, null, 0, line);
    }

    private static LocalDateTime parseUnixDate(String dateStr) {
        try {
            // Try "Jan 15 10:30" (current year)
            LocalDateTime dt = LocalDateTime.parse(dateStr, UNIX_DATE_TIME);
            return dt.withYear(LocalDateTime.now().getYear());
        } catch (DateTimeParseException e) {
            try {
                // Try "Jan 15 2024" (year format, no time)
                LocalDate date = LocalDate.parse(dateStr, UNIX_DATE_YEAR);
                return date.atStartOfDay();
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    private static LocalDateTime parseWindowsDate(String dateStr, String timeStr) {
        String combined = dateStr + " " + timeStr;
        try {
            if (dateStr.length() > 8) {
                return LocalDateTime.parse(combined, WINDOWS_DATE_TIME_4Y);
            }
            return LocalDateTime.parse(combined, WINDOWS_DATE_TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
