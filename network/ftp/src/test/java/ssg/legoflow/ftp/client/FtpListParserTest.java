package ssg.legoflow.ftp.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link FtpListParser}.
 */
class FtpListParserTest {

    @Test
    void testParseUnixFile() {
        var entry = FtpListParser.parseUnix("-rw-r--r-- 1 user group 12345 Jan 15 10:30 readme.txt");
        assertThat(entry).isNotNull();
        assertThat(entry.name()).isEqualTo("readme.txt");
        assertThat(entry.size()).isEqualTo(12345);
        assertThat(entry.type()).isEqualTo(FtpFileEntry.Type.FILE);
        assertThat(entry.permissions()).isEqualTo("rw-r--r--");
        assertThat(entry.owner()).isEqualTo("user");
        assertThat(entry.group()).isEqualTo("group");
        assertThat(entry.linkCount()).isEqualTo(1);
    }

    @Test
    void testParseUnixDirectory() {
        var entry = FtpListParser.parseUnix("drwxr-xr-x 2 root root 4096 Feb 20 14:00 documents");
        assertThat(entry).isNotNull();
        assertThat(entry.name()).isEqualTo("documents");
        assertThat(entry.type()).isEqualTo(FtpFileEntry.Type.DIRECTORY);
        assertThat(entry.isDirectory()).isTrue();
    }

    @Test
    void testParseUnixSymlink() {
        var entry = FtpListParser.parseUnix("lrwxrwxrwx 1 user group 11 Mar 10 09:00 link -> target");
        assertThat(entry).isNotNull();
        assertThat(entry.name()).isEqualTo("link");
        assertThat(entry.type()).isEqualTo(FtpFileEntry.Type.SYMLINK);
        assertThat(entry.isSymlink()).isTrue();
    }

    @Test
    void testParseUnixWithYear() {
        var entry = FtpListParser.parseUnix("-rw-r--r-- 1 user group 100 Jan 15 2023 old-file.txt");
        assertThat(entry).isNotNull();
        assertThat(entry.name()).isEqualTo("old-file.txt");
        assertThat(entry.modified()).isNotNull();
        assertThat(entry.modified().getYear()).isEqualTo(2023);
    }

    @Test
    void testParseUnixLargeFile() {
        var entry = FtpListParser.parseUnix("-rw-r--r-- 1 user group 1073741824 Dec  1 12:00 big.iso");
        assertThat(entry).isNotNull();
        assertThat(entry.size()).isEqualTo(1_073_741_824L);
    }

    @Test
    void testParseUnixStickyBit() {
        var entry = FtpListParser.parseUnix("drwxrwxrwt 10 root root 4096 Jan 15 10:30 tmp");
        assertThat(entry).isNotNull();
        assertThat(entry.name()).isEqualTo("tmp");
        assertThat(entry.isDirectory()).isTrue();
    }

    @Test
    void testParseUnixNotUnixFormat() {
        assertThat(FtpListParser.parseUnix("not a unix listing")).isNull();
    }

    @Test
    void testParseWindowsFile() {
        var entry = FtpListParser.parseWindows("01-15-24  10:30AM             12345 readme.txt");
        assertThat(entry).isNotNull();
        assertThat(entry.name()).isEqualTo("readme.txt");
        assertThat(entry.size()).isEqualTo(12345);
        assertThat(entry.type()).isEqualTo(FtpFileEntry.Type.FILE);
    }

    @Test
    void testParseWindowsDirectory() {
        var entry = FtpListParser.parseWindows("01-15-24  10:30AM       <DIR>          documents");
        assertThat(entry).isNotNull();
        assertThat(entry.name()).isEqualTo("documents");
        assertThat(entry.type()).isEqualTo(FtpFileEntry.Type.DIRECTORY);
        assertThat(entry.size()).isEqualTo(0);
    }

    @Test
    void testParseWindowsPM() {
        var entry = FtpListParser.parseWindows("12-25-24  03:45PM              5678 report.pdf");
        assertThat(entry).isNotNull();
        assertThat(entry.name()).isEqualTo("report.pdf");
    }

    @Test
    void testParseWindowsNotWindowsFormat() {
        assertThat(FtpListParser.parseWindows("not a windows listing")).isNull();
    }

    @Test
    void testParseLineAutoDetectsUnix() {
        var entry = FtpListParser.parseLine("-rw-r--r-- 1 user group 100 Jan 15 10:30 test.txt");
        assertThat(entry).isNotNull();
        assertThat(entry.name()).isEqualTo("test.txt");
    }

    @Test
    void testParseLineAutoDetectsWindows() {
        var entry = FtpListParser.parseLine("01-15-24  10:30AM             12345 readme.txt");
        assertThat(entry).isNotNull();
        assertThat(entry.name()).isEqualTo("readme.txt");
    }

    @Test
    void testParseLineNull() {
        assertThat(FtpListParser.parseLine(null)).isNull();
    }

    @Test
    void testParseLineBlank() {
        assertThat(FtpListParser.parseLine("  ")).isNull();
    }

    @Test
    void testParseMultipleLines() {
        String output = """
                total 24
                drwxr-xr-x 2 user group 4096 Jan 15 10:30 docs
                -rw-r--r-- 1 user group 12345 Jan 15 10:30 file.txt
                lrwxrwxrwx 1 user group 5 Jan 15 10:30 link -> file.txt
                """;
        List<FtpFileEntry> entries = FtpListParser.parse(output);
        assertThat(entries).hasSize(3);
        assertThat(entries.get(0).isDirectory()).isTrue();
        assertThat(entries.get(1).isFile()).isTrue();
        assertThat(entries.get(2).isSymlink()).isTrue();
    }

    @Test
    void testParseEmptyOutput() {
        assertThat(FtpListParser.parse("")).isEmpty();
        assertThat(FtpListParser.parse(null)).isEmpty();
    }

    @Test
    void testParseTotalLineSkipped() {
        List<FtpFileEntry> entries = FtpListParser.parse("total 100\n");
        assertThat(entries).isEmpty();
    }

    @Test
    void testParseFileNameWithSpaces() {
        var entry = FtpListParser.parseUnix("-rw-r--r-- 1 user group 100 Jan 15 10:30 my file name.txt");
        assertThat(entry).isNotNull();
        assertThat(entry.name()).isEqualTo("my file name.txt");
    }

    @Test
    void testRawLinePreserved() {
        String line = "-rw-r--r-- 1 user group 100 Jan 15 10:30 test.txt";
        var entry = FtpListParser.parseUnix(line);
        assertThat(entry).isNotNull();
        assertThat(entry.rawLine()).isEqualTo(line);
    }
}
