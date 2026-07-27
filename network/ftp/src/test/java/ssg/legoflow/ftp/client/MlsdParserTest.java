package ssg.legoflow.ftp.client;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link MlsdParser}.
 */
class MlsdParserTest {

    @Test
    void testParseFileEntry() {
        var entry = MlsdParser.parseLine("type=file;size=12345;modify=20240115103000; readme.txt");
        assertThat(entry).isNotNull();
        assertThat(entry.name()).isEqualTo("readme.txt");
        assertThat(entry.size()).isEqualTo(12345);
        assertThat(entry.type()).isEqualTo(FtpFileEntry.Type.FILE);
        assertThat(entry.modified()).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
    }

    @Test
    void testParseDirectoryEntry() {
        var entry = MlsdParser.parseLine("type=dir;size=4096;modify=20240220140000; documents");
        assertThat(entry).isNotNull();
        assertThat(entry.name()).isEqualTo("documents");
        assertThat(entry.type()).isEqualTo(FtpFileEntry.Type.DIRECTORY);
        assertThat(entry.isDirectory()).isTrue();
    }

    @Test
    void testParseCdirEntry() {
        var entry = MlsdParser.parseLine("type=cdir;modify=20240101000000; .");
        assertThat(entry).isNotNull();
        assertThat(entry.name()).isEqualTo(".");
        assertThat(entry.type()).isEqualTo(FtpFileEntry.Type.DIRECTORY);
    }

    @Test
    void testParsePdirEntry() {
        var entry = MlsdParser.parseLine("type=pdir;modify=20240101000000; ..");
        assertThat(entry).isNotNull();
        assertThat(entry.name()).isEqualTo("..");
        assertThat(entry.type()).isEqualTo(FtpFileEntry.Type.DIRECTORY);
    }

    @Test
    void testParseWithPermissions() {
        var entry = MlsdParser.parseLine("type=file;size=100;perm=adfr;modify=20240115103000; test.txt");
        assertThat(entry).isNotNull();
        assertThat(entry.permissions()).isEqualTo("adfr");
    }

    @Test
    void testParseTimestamp() {
        LocalDateTime dt = MlsdParser.parseTimestamp("20240115103000");
        assertThat(dt).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
    }

    @Test
    void testParseTimestampWithFraction() {
        LocalDateTime dt = MlsdParser.parseTimestamp("20240115103000.123");
        assertThat(dt).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
    }

    @Test
    void testParseTimestampNull() {
        assertThat(MlsdParser.parseTimestamp(null)).isNull();
    }

    @Test
    void testParseTimestampInvalid() {
        assertThat(MlsdParser.parseTimestamp("not-a-date")).isNull();
    }

    @Test
    void testFormatTimestamp() {
        String formatted = MlsdParser.formatTimestamp(LocalDateTime.of(2024, 3, 15, 9, 45, 30));
        assertThat(formatted).isEqualTo("20240315094530");
    }

    @Test
    void testFormatTimestampNull() {
        assertThat(MlsdParser.formatTimestamp(null)).isNull();
    }

    @Test
    void testTimestampRoundTrip() {
        LocalDateTime original = LocalDateTime.of(2024, 6, 15, 14, 30, 0);
        String formatted = MlsdParser.formatTimestamp(original);
        LocalDateTime parsed = MlsdParser.parseTimestamp(formatted);
        assertThat(parsed).isEqualTo(original);
    }

    @Test
    void testParseFacts() {
        Map<String, String> facts = MlsdParser.parseFacts("type=file;size=100;modify=20240101000000;");
        assertThat(facts).hasSize(3);
        assertThat(facts.get("type")).isEqualTo("file");
        assertThat(facts.get("size")).isEqualTo("100");
        assertThat(facts.get("modify")).isEqualTo("20240101000000");
    }

    @Test
    void testParseFactsEmpty() {
        assertThat(MlsdParser.parseFacts("")).isEmpty();
        assertThat(MlsdParser.parseFacts(null)).isEmpty();
    }

    @Test
    void testParseFactsCaseInsensitive() {
        Map<String, String> facts = MlsdParser.parseFacts("Type=File;SIZE=100");
        assertThat(facts.get("type")).isEqualTo("File");
        assertThat(facts.get("size")).isEqualTo("100");
    }

    @Test
    void testParseMultipleEntries() {
        String mlsdOutput = """
                type=file;size=100;modify=20240115103000; file1.txt
                type=file;size=200;modify=20240116120000; file2.txt
                type=dir;size=4096;modify=20240117080000; subdir
                """;
        List<FtpFileEntry> entries = MlsdParser.parse(mlsdOutput);
        assertThat(entries).hasSize(3);
        assertThat(entries.get(0).name()).isEqualTo("file1.txt");
        assertThat(entries.get(1).name()).isEqualTo("file2.txt");
        assertThat(entries.get(2).name()).isEqualTo("subdir");
        assertThat(entries.get(2).isDirectory()).isTrue();
    }

    @Test
    void testParseEmptyOutput() {
        assertThat(MlsdParser.parse("")).isEmpty();
        assertThat(MlsdParser.parse(null)).isEmpty();
    }

    @Test
    void testParseLineNull() {
        assertThat(MlsdParser.parseLine(null)).isNull();
        assertThat(MlsdParser.parseLine("")).isNull();
    }

    @Test
    void testParseLineNoSpace() {
        assertThat(MlsdParser.parseLine("type=file;size=100")).isNull();
    }

    @Test
    void testParseLineEmptyName() {
        assertThat(MlsdParser.parseLine("type=file;size=100; ")).isNull();
    }

    @Test
    void testParseUnknownType() {
        var entry = MlsdParser.parseLine("type=special;size=0; mystery");
        assertThat(entry).isNotNull();
        assertThat(entry.type()).isEqualTo(FtpFileEntry.Type.UNKNOWN);
    }

    @Test
    void testParseSymlinkType() {
        var entry = MlsdParser.parseLine("type=OS.unix=slink;size=0; mylink");
        assertThat(entry).isNotNull();
        assertThat(entry.type()).isEqualTo(FtpFileEntry.Type.SYMLINK);
    }

    @Test
    void testParseEntryWithNoSize() {
        var entry = MlsdParser.parseLine("type=dir;modify=20240101000000; mydir");
        assertThat(entry).isNotNull();
        assertThat(entry.size()).isEqualTo(0);
    }
}
