package ssg.legoflow.ftp.server;

import ssg.legoflow.ftp.client.FtpFileEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link InMemoryFileSystem}.
 */
class InMemoryFileSystemTest {

    private InMemoryFileSystem fs;

    @BeforeEach
    void setUp() throws IOException {
        fs = new InMemoryFileSystem();
        fs.createDirectory("/docs");
        fs.putFile("/docs/readme.txt", "Hello".getBytes(StandardCharsets.UTF_8));
        fs.putFile("/docs/notes.txt", "Notes".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testRootExists() throws IOException {
        assertThat(fs.exists("/")).isTrue();
    }

    @Test
    void testCreateDirectory() throws IOException {
        fs.createDirectory("/newdir");
        assertThat(fs.exists("/newdir")).isTrue();
        FtpFileEntry entry = fs.getFile("/newdir");
        assertThat(entry).isNotNull();
        assertThat(entry.isDirectory()).isTrue();
    }

    @Test
    void testCreateFile() throws IOException {
        fs.createFile("/test.txt");
        assertThat(fs.exists("/test.txt")).isTrue();
    }

    @Test
    void testCreateFileDuplicate() throws IOException {
        fs.createFile("/dup.txt");
        assertThatIOException().isThrownBy(() -> fs.createFile("/dup.txt"));
    }

    @Test
    void testPutAndGetFile() {
        fs.putFile("/data.bin", new byte[]{1, 2, 3});
        assertThat(fs.getFileData("/data.bin")).isEqualTo(new byte[]{1, 2, 3});
    }

    @Test
    void testReadFile() throws IOException {
        try (var is = fs.readFile("/docs/readme.txt")) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content).isEqualTo("Hello");
        }
    }

    @Test
    void testReadFileNotFound() {
        assertThatIOException().isThrownBy(() -> fs.readFile("/nonexistent"));
    }

    @Test
    void testWriteFile() throws IOException {
        try (var os = fs.writeFile("/new.txt")) {
            os.write("new content".getBytes(StandardCharsets.UTF_8));
        }
        assertThat(fs.getFileData("/new.txt")).isEqualTo("new content".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testWriteFileOverwrite() throws IOException {
        try (var os = fs.writeFile("/docs/readme.txt")) {
            os.write("overwritten".getBytes(StandardCharsets.UTF_8));
        }
        assertThat(fs.getFileData("/docs/readme.txt"))
                .isEqualTo("overwritten".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testAppendFile() throws IOException {
        try (var os = fs.appendFile("/docs/readme.txt")) {
            os.write(" World".getBytes(StandardCharsets.UTF_8));
        }
        assertThat(fs.getFileData("/docs/readme.txt"))
                .isEqualTo("Hello World".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testDeleteFile() throws IOException {
        fs.deleteFile("/docs/readme.txt");
        assertThat(fs.exists("/docs/readme.txt")).isFalse();
    }

    @Test
    void testDeleteFileNotFound() {
        assertThatIOException().isThrownBy(() -> fs.deleteFile("/nonexistent"));
    }

    @Test
    void testDeleteDirectoryAsFileThrows() {
        assertThatIOException().isThrownBy(() -> fs.deleteFile("/docs"));
    }

    @Test
    void testDeleteDirectory() throws IOException {
        fs.createDirectory("/empty");
        fs.deleteDirectory("/empty");
        assertThat(fs.exists("/empty")).isFalse();
    }

    @Test
    void testDeleteNonEmptyDirectoryThrows() {
        assertThatIOException().isThrownBy(() -> fs.deleteDirectory("/docs"));
    }

    @Test
    void testDeleteDirectoryNotFound() {
        assertThatIOException().isThrownBy(() -> fs.deleteDirectory("/nonexistent"));
    }

    @Test
    void testRename() throws IOException {
        fs.rename("/docs/readme.txt", "/docs/renamed.txt");
        assertThat(fs.exists("/docs/readme.txt")).isFalse();
        assertThat(fs.exists("/docs/renamed.txt")).isTrue();
    }

    @Test
    void testRenameNotFound() {
        assertThatIOException().isThrownBy(() -> fs.rename("/nonexistent", "/target"));
    }

    @Test
    void testListFiles() throws IOException {
        List<FtpFileEntry> entries = fs.listFiles("/docs");
        assertThat(entries).hasSize(2);
        assertThat(entries.stream().map(FtpFileEntry::name))
                .containsExactlyInAnyOrder("readme.txt", "notes.txt");
    }

    @Test
    void testListFilesRoot() throws IOException {
        List<FtpFileEntry> entries = fs.listFiles("/");
        assertThat(entries).hasSizeGreaterThanOrEqualTo(1);
        assertThat(entries.stream().map(FtpFileEntry::name)).contains("docs");
    }

    @Test
    void testListFilesNotDirectory() {
        assertThatIOException().isThrownBy(() -> fs.listFiles("/docs/readme.txt"));
    }

    @Test
    void testGetSize() throws IOException {
        assertThat(fs.getSize("/docs/readme.txt")).isEqualTo(5);
    }

    @Test
    void testGetModificationTime() throws IOException {
        assertThat(fs.getModificationTime("/docs/readme.txt")).isNotNull();
    }

    @Test
    void testGetPermissionsFile() throws IOException {
        assertThat(fs.getPermissions("/docs/readme.txt")).isEqualTo("rw-r--r--");
    }

    @Test
    void testGetPermissionsDirectory() throws IOException {
        assertThat(fs.getPermissions("/docs")).isEqualTo("rwxr-xr-x");
    }

    @Test
    void testSize() {
        assertThat(fs.size()).isGreaterThanOrEqualTo(4); // root + docs + 2 files
    }

    @Test
    void testNormalizationTrailingSlash() throws IOException {
        fs.createDirectory("/slash/");
        assertThat(fs.exists("/slash")).isTrue();
    }

    @Test
    void testNormalizationNoLeadingSlash() throws IOException {
        fs.putFile("noslash.txt", "data".getBytes());
        assertThat(fs.exists("/noslash.txt")).isTrue();
    }

    @Test
    void testGetFileEntry() throws IOException {
        FtpFileEntry entry = fs.getFile("/docs/readme.txt");
        assertThat(entry).isNotNull();
        assertThat(entry.name()).isEqualTo("readme.txt");
        assertThat(entry.size()).isEqualTo(5);
        assertThat(entry.isFile()).isTrue();
    }

    @Test
    void testGetFileEntryDirectory() throws IOException {
        FtpFileEntry entry = fs.getFile("/docs");
        assertThat(entry).isNotNull();
        assertThat(entry.name()).isEqualTo("docs");
        assertThat(entry.isDirectory()).isTrue();
    }

    @Test
    void testGetFileEntryNotFound() throws IOException {
        assertThat(fs.getFile("/nonexistent")).isNull();
    }
}
