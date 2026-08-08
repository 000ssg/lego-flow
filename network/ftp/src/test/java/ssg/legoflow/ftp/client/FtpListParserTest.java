package ssg.legoflow.ftp.client;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.util.List;

class FtpListParserTest {

    @Test void testParseUnixFile() {
        var entries = FtpListParser.parse("-rw-r--r-- 1 user group 1234 Jan 15 10:30 file.txt");
        assertThat(entries).hasSize(1);
        var entry = entries.get(0);
        assertThat(entry.name()).isEqualTo("file.txt");
        assertThat(entry.size()).isEqualTo(1234);
        assertThat(entry.type()).isEqualTo(FtpFileEntry.Type.FILE);
    }

    @Test void testParseUnixDirectory() {
        var entries = FtpListParser.parse("drwxr-xr-x 2 user group 4096 Jan 15 10:30 mydir");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).type()).isEqualTo(FtpFileEntry.Type.DIRECTORY);
    }

    @Test void testParseUnixSymlink() {
        var entries = FtpListParser.parse("lrwxrwxrwx 1 user group 12 Jan 15 10:30 link -> target");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).type()).isEqualTo(FtpFileEntry.Type.SYMLINK);
        // Symlink name should not include " -> target"
        assertThat(entries.get(0).name()).isEqualTo("link");
    }

    @Test void testParseUnixWithYear() {
        var entries = FtpListParser.parse("-rw-r--r-- 1 user group 100 Dec 25 2023 oldfile.txt");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).name()).isEqualTo("oldfile.txt");
    }

    @Test void testParseWindowsDirectory() {
        var entries = FtpListParser.parse("01-15-24 10:30AM <DIR> mydir");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).type()).isEqualTo(FtpFileEntry.Type.DIRECTORY);
    }

    @Test void testParseWindowsFile() {
        var entries = FtpListParser.parse("01-15-24 10:30AM 5678 file.txt");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).size()).isEqualTo(5678);
    }

    @Test void testParseMultipleUnixLines() {
        String output = """
            -rw-r--r-- 1 user group 100 Jan 15 10:30 file1.txt
            drwxr-xr-x 2 user group 4096 Jan 15 10:30 dir1
            lrwxrwxrwx 1 user group 10 Jan 15 10:30 link -> target
            """;
        var entries = FtpListParser.parse(output);
        assertThat(entries).hasSize(3);
    }

    @Test void testParseWithTotalLine() {
        String output = """
            total 4
            -rw-r--r-- 1 user group 100 Jan 15 10:30 file.txt
            """;
        var entries = FtpListParser.parse(output);
        // "total 4" line should be skipped
        assertThat(entries).hasSize(1);
    }

    @Test void testParseNullReturnsEmpty() {
        var entries = FtpListParser.parse(null);
        assertThat(entries).isEmpty();
    }

    @Test void testParseBlankReturnsEmpty() {
        var entries = FtpListParser.parse("   ");
        assertThat(entries).isEmpty();
    }

    @Test void testParseEmptyStringReturnsEmpty() {
        var entries = FtpListParser.parse("");
        assertThat(entries).isEmpty();
    }

    @Test void testParseLineNullReturnsNull() {
        assertThat(FtpListParser.parseLine(null)).isNull();
    }

    @Test void testParseLineBlankReturnsNull() {
        assertThat(FtpListParser.parseLine("   ")).isNull();
    }

    @Test void testParseUnixUnknownType() {
        // Block device: brwxr-xr-x
        var entries = FtpListParser.parse("brwxr-xr-x 1 user group 0 Jan 15 10:30 device");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).type()).isEqualTo(FtpFileEntry.Type.UNKNOWN);
    }

    @Test void testParseUnixSocket() {
        // Socket: srwxr-xr-x  
        var entries = FtpListParser.parse("srwxr-xr-x 1 user group 0 Jan 15 10:30 socket");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).type()).isEqualTo(FtpFileEntry.Type.UNKNOWN);
    }

    @Test void testParseLineNonMatchingReturnsNull() {
        // Line that doesn't match either Unix or Windows format
        assertThat(FtpListParser.parseLine("random garbage text")).isNull();
    }

    @Test void testParseUnixDateWithYearFallback() {
        var entries = FtpListParser.parse("-rw-r--r-- 1 user group 50 Mar 3 2020 archived.log");
        assertThat(entries).hasSize(1);
        // The modified time should be parsed even for old dates
        assertThat(entries.get(0).modified()).isNotNull();
    }

    @Test void testParseWindows4DigitYear() {
        var entries = FtpListParser.parse("01-15-2024 10:30AM 1234 file.txt");
        assertThat(entries).hasSize(1);
        // Should parse even with 4-digit year format
    }

    @Test void testParseLineWithMultipleSpacesInUnixDate() {
        // "Jan  5 10:30" has extra space before single digit day
        var entries = FtpListParser.parse("-rw-r--r-- 1 user group 50 Jan  5 10:30 small.txt");
        assertThat(entries).hasSize(1);
    }

    @Test void testParseWindowsWithSpacesInName() {
        var entries = FtpListParser.parse("01-15-24 10:30AM <DIR> my dir name");
        assertThat(entries).hasSize(1);
        // Name should be trimmed but spaces preserved
    }

    @Test void testParsePermissionsFromUnixEntry() {
        var entries = FtpListParser.parse("-rwx------ 1 admin staff 100 Jan 15 10:30 secret.sh");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).permissions()).isEqualTo("rwx------");
    }

    @Test void testParseOwnerGroupFromUnixEntry() {
        var entries = FtpListParser.parse("-rw-r--r-- 2 nobody nogroup 42 Feb 28 09:00 data.txt");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).owner()).isEqualTo("nobody");
        assertThat(entries.get(0).group()).isEqualTo("nogroup");
    }

    @Test void testParseLinkCountFromUnixEntry() {
        var entries = FtpListParser.parse("-rw-r--r-- 5 user group 100 Jan 15 10:30 linked.txt");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).linkCount()).isEqualTo(5);
    }

    @Test void testParseBlankLinesSkipped() {
        String output = "-rw-r--r-- 1 u g 10 Jan 15 10:30 f.txt\n\n\n";
        var entries = FtpListParser.parse(output);
        assertThat(entries).hasSize(1);
    }

    @Test void testParseCRLFLineEndings() {
        String output = "-rw-r--r-- 1 u g 10 Jan 15 10:30 f1.txt\r\ndrwxr-xr-x 2 u g 4096 Jan 15 10:30 d1\r\n";
        var entries = FtpListParser.parse(output);
        assertThat(entries).hasSize(2);
    }
}
