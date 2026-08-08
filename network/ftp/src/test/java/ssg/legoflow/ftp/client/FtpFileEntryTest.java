package ssg.legoflow.ftp.client;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class FtpFileEntryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2024, 3, 15, 10, 30, 0);
    private static final String NAME = "test.txt";
    private static final long SIZE = 1234;

    @Test
    void testFullConstructor() {
        FtpFileEntry entry = new FtpFileEntry(
                NAME, SIZE, NOW, FtpFileEntry.Type.FILE,
                "rw-r--r--", "owner", "group", 1, "-rw-r--r-- 1 owner group 1234 Mar 15 10:30 test.txt"
        );

        assertThat(entry.name()).isEqualTo(NAME);
        assertThat(entry.size()).isEqualTo(SIZE);
        assertThat(entry.modified()).isEqualTo(NOW);
        assertThat(entry.type()).isEqualTo(FtpFileEntry.Type.FILE);
        assertThat(entry.permissions()).isEqualTo("rw-r--r--");
        assertThat(entry.owner()).isEqualTo("owner");
        assertThat(entry.group()).isEqualTo("group");
        assertThat(entry.linkCount()).isEqualTo(1);
        assertThat(entry.rawLine()).contains("test.txt");
    }

    @Test
    void testOfFactoryMethod() {
        FtpFileEntry entry = FtpFileEntry.of(NAME, SIZE, NOW, FtpFileEntry.Type.FILE);

        assertThat(entry.name()).isEqualTo(NAME);
        assertThat(entry.size()).isEqualTo(SIZE);
        assertThat(entry.modified()).isEqualTo(NOW);
        assertThat(entry.type()).isEqualTo(FtpFileEntry.Type.FILE);
        assertThat(entry.permissions()).isNull();
        assertThat(entry.owner()).isNull();
        assertThat(entry.group()).isNull();
        assertThat(entry.linkCount()).isZero();
        assertThat(entry.rawLine()).isNull();
    }

    @Test
    void testConstructorWithNullType() {
        FtpFileEntry entry = new FtpFileEntry(
                NAME, SIZE, NOW, null, null, null, null, 0, null
        );

        assertThat(entry.type()).isEqualTo(FtpFileEntry.Type.UNKNOWN);
        assertThat(entry.isDirectory()).isFalse();
        assertThat(entry.isFile()).isFalse();
        assertThat(entry.isSymlink()).isFalse();
    }

    @Test
    void testConstructorWithNullNameThrows() {
        assertThatThrownBy(() -> new FtpFileEntry(null, SIZE, NOW, FtpFileEntry.Type.FILE,
                null, null, null, 0, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Nested
    class TypeTests {

        @Test
        void testIsFile() {
            FtpFileEntry entry = FtpFileEntry.of("file.txt", 100, NOW, FtpFileEntry.Type.FILE);
            assertThat(entry.isFile()).isTrue();
            assertThat(entry.isDirectory()).isFalse();
            assertThat(entry.isSymlink()).isFalse();
        }

        @Test
        void testIsDirectory() {
            FtpFileEntry entry = FtpFileEntry.of("dir", 4096, NOW, FtpFileEntry.Type.DIRECTORY);
            assertThat(entry.isDirectory()).isTrue();
            assertThat(entry.isFile()).isFalse();
            assertThat(entry.isSymlink()).isFalse();
        }

        @Test
        void testIsSymlink() {
            FtpFileEntry entry = FtpFileEntry.of("link", 0, NOW, FtpFileEntry.Type.SYMLINK);
            assertThat(entry.isSymlink()).isTrue();
            assertThat(entry.isDirectory()).isFalse();
            assertThat(entry.isFile()).isFalse();
        }

        @Test
        void testIsUnknown() {
            FtpFileEntry entry = FtpFileEntry.of("unknown", 0, NOW, FtpFileEntry.Type.UNKNOWN);
            assertThat(entry.isDirectory()).isFalse();
            assertThat(entry.isFile()).isFalse();
            assertThat(entry.isSymlink()).isFalse();
        }

        @Test
        void testTypeValues() {
            FtpFileEntry.Type[] values = FtpFileEntry.Type.values();
            assertThat(values).hasSize(4);
            assertThat(values).contains(
                    FtpFileEntry.Type.FILE,
                    FtpFileEntry.Type.DIRECTORY,
                    FtpFileEntry.Type.SYMLINK,
                    FtpFileEntry.Type.UNKNOWN
            );
        }
    }

    @Nested
    class EqualityTests {

        @Test
        void testEqualsSameInstance() {
            FtpFileEntry entry = FtpFileEntry.of("file.txt", 100, NOW, FtpFileEntry.Type.FILE);
            assertThat(entry).isEqualTo(entry);
        }

        @Test
        void testEqualsDifferentInstances() {
            FtpFileEntry e1 = FtpFileEntry.of("file.txt", 100, NOW, FtpFileEntry.Type.FILE);
            FtpFileEntry e2 = FtpFileEntry.of("file.txt", 100, LocalDateTime.MIN, FtpFileEntry.Type.FILE);
            // equal by name, size, type (modified is not part of equality)
            assertThat(e1).isEqualTo(e2);
        }

        @Test
        void testEqualsDifferentName() {
            FtpFileEntry e1 = FtpFileEntry.of("file.txt", 100, NOW, FtpFileEntry.Type.FILE);
            FtpFileEntry e2 = FtpFileEntry.of("other.txt", 100, NOW, FtpFileEntry.Type.FILE);
            assertThat(e1).isNotEqualTo(e2);
        }

        @Test
        void testEqualsDifferentSize() {
            FtpFileEntry e1 = FtpFileEntry.of("file.txt", 100, NOW, FtpFileEntry.Type.FILE);
            FtpFileEntry e2 = FtpFileEntry.of("file.txt", 200, NOW, FtpFileEntry.Type.FILE);
            assertThat(e1).isNotEqualTo(e2);
        }

        @Test
        void testEqualsDifferentType() {
            FtpFileEntry e1 = FtpFileEntry.of("file.txt", 100, NOW, FtpFileEntry.Type.FILE);
            FtpFileEntry e2 = FtpFileEntry.of("file.txt", 100, NOW, FtpFileEntry.Type.DIRECTORY);
            assertThat(e1).isNotEqualTo(e2);
        }

        @Test
        void testEqualsNull() {
            FtpFileEntry entry = FtpFileEntry.of("file.txt", 100, NOW, FtpFileEntry.Type.FILE);
            assertThat(entry).isNotEqualTo(null);
        }

        @Test
        void testEqualsOtherType() {
            FtpFileEntry entry = FtpFileEntry.of("file.txt", 100, NOW, FtpFileEntry.Type.FILE);
            assertThat(entry).isNotEqualTo("file.txt");
        }

        @Test
        void testHashCodeConsistent() {
            FtpFileEntry e1 = FtpFileEntry.of("file.txt", 100, NOW, FtpFileEntry.Type.FILE);
            FtpFileEntry e2 = FtpFileEntry.of("file.txt", 100, LocalDateTime.MAX, FtpFileEntry.Type.FILE);
            assertThat(e1.hashCode()).isEqualTo(e2.hashCode());
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void testToString() {
            FtpFileEntry entry = FtpFileEntry.of("file.txt", 1234, NOW, FtpFileEntry.Type.FILE);
            String str = entry.toString();
            assertThat(str).contains("file.txt");
            assertThat(str).contains("1234");
            assertThat(str).contains("FILE");
            assertThat(str).startsWith("FtpFileEntry[");
        }

        @Test
        void testToStringDirectory() {
            FtpFileEntry entry = FtpFileEntry.of("mydir", 4096, NOW, FtpFileEntry.Type.DIRECTORY);
            String str = entry.toString();
            assertThat(str).contains("DIRECTORY");
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void testZeroSize() {
            FtpFileEntry entry = FtpFileEntry.of("empty.txt", 0, NOW, FtpFileEntry.Type.FILE);
            assertThat(entry.size()).isZero();
        }

        @Test
        void testLargeSize() {
            FtpFileEntry entry = FtpFileEntry.of("huge.bin", Long.MAX_VALUE, NOW, FtpFileEntry.Type.FILE);
            assertThat(entry.size()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        void testNullModificationTime() {
            FtpFileEntry entry = new FtpFileEntry(NAME, SIZE, null, FtpFileEntry.Type.FILE,
                    null, null, null, 0, null);
            assertThat(entry.modified()).isNull();
        }

        @Test
        void testSpecialCharactersInName() {
            String name = "file with spaces & special chars.txt";
            FtpFileEntry entry = FtpFileEntry.of(name, SIZE, NOW, FtpFileEntry.Type.FILE);
            assertThat(entry.name()).isEqualTo(name);
        }

        @Test
        void testEmptyNameEdgeCase() {
            // Name is validated by requireNonNull but empty string is valid
            FtpFileEntry entry = FtpFileEntry.of("", SIZE, NOW, FtpFileEntry.Type.FILE);
            assertThat(entry.name()).isEmpty();
        }

        @Test
        void testNegativeSize() {
            // Negative size might represent unknown size in some FTP servers
            FtpFileEntry entry = FtpFileEntry.of("unknown.txt", -1, NOW, FtpFileEntry.Type.FILE);
            assertThat(entry.size()).isEqualTo(-1);
        }

        @Test
        void testMultipleLinkCount() {
            FtpFileEntry entry = new FtpFileEntry(
                    "hardlinked", SIZE, NOW, FtpFileEntry.Type.FILE,
                    null, null, null, 5, null
            );
            assertThat(entry.linkCount()).isEqualTo(5);
        }
    }
}
