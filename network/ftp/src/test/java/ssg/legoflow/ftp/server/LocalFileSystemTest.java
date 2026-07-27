package ssg.legoflow.ftp.server;

import ssg.legoflow.ftp.client.FtpFileEntry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link LocalFileSystem}.
 */
class LocalFileSystemTest {

    @TempDir
    Path tempDir;

    private LocalFileSystem fs;

    @BeforeEach
    void setUp() throws IOException {
        fs = new LocalFileSystem(tempDir);
        Files.writeString(tempDir.resolve("file1.txt"), "hello");
        Files.createDirectory(tempDir.resolve("subdir"));
        Files.writeString(tempDir.resolve("subdir/file2.txt"), "world");
    }

    @Test
    void testListFiles() throws IOException {
        List<FtpFileEntry> entries = fs.listFiles("/");
        assertThat(entries).isNotEmpty();
        assertThat(entries.stream().map(FtpFileEntry::name))
                .contains("file1.txt", "subdir");
    }

    @Test
    void testListFilesSubdir() throws IOException {
        List<FtpFileEntry> entries = fs.listFiles("/subdir");
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().name()).isEqualTo("file2.txt");
    }

    @Test
    void testGetFile() throws IOException {
        FtpFileEntry entry = fs.getFile("/file1.txt");
        assertThat(entry).isNotNull();
        assertThat(entry.name()).isEqualTo("file1.txt");
        assertThat(entry.isFile()).isTrue();
        assertThat(entry.size()).isEqualTo(5);
    }

    @Test
    void testGetFileNotFound() throws IOException {
        assertThat(fs.getFile("/nonexistent.txt")).isNull();
    }

    @Test
    void testExists() throws IOException {
        assertThat(fs.exists("/file1.txt")).isTrue();
        assertThat(fs.exists("/nonexistent.txt")).isFalse();
        assertThat(fs.exists("/subdir")).isTrue();
    }

    @Test
    void testReadFile() throws IOException {
        try (var is = fs.readFile("/file1.txt")) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content).isEqualTo("hello");
        }
    }

    @Test
    void testWriteFile() throws IOException {
        try (var os = fs.writeFile("/new-file.txt")) {
            os.write("new content".getBytes(StandardCharsets.UTF_8));
        }
        assertThat(Files.readString(tempDir.resolve("new-file.txt"))).isEqualTo("new content");
    }

    @Test
    void testAppendFile() throws IOException {
        try (var os = fs.appendFile("/file1.txt")) {
            os.write(" appended".getBytes(StandardCharsets.UTF_8));
        }
        assertThat(Files.readString(tempDir.resolve("file1.txt"))).isEqualTo("hello appended");
    }

    @Test
    void testCreateFile() throws IOException {
        fs.createFile("/empty.txt");
        assertThat(Files.exists(tempDir.resolve("empty.txt"))).isTrue();
        assertThat(Files.size(tempDir.resolve("empty.txt"))).isEqualTo(0);
    }

    @Test
    void testDeleteFile() throws IOException {
        fs.deleteFile("/file1.txt");
        assertThat(Files.exists(tempDir.resolve("file1.txt"))).isFalse();
    }

    @Test
    void testCreateDirectory() throws IOException {
        fs.createDirectory("/newdir");
        assertThat(Files.isDirectory(tempDir.resolve("newdir"))).isTrue();
    }

    @Test
    void testDeleteDirectory() throws IOException {
        Files.createDirectory(tempDir.resolve("emptydir"));
        fs.deleteDirectory("/emptydir");
        assertThat(Files.exists(tempDir.resolve("emptydir"))).isFalse();
    }

    @Test
    void testRename() throws IOException {
        fs.rename("/file1.txt", "/renamed.txt");
        assertThat(Files.exists(tempDir.resolve("file1.txt"))).isFalse();
        assertThat(Files.exists(tempDir.resolve("renamed.txt"))).isTrue();
    }

    @Test
    void testGetSize() throws IOException {
        assertThat(fs.getSize("/file1.txt")).isEqualTo(5);
    }

    @Test
    void testGetModificationTime() throws IOException {
        assertThat(fs.getModificationTime("/file1.txt")).isNotNull();
    }

    @Test
    void testPathTraversalBlocked() {
        assertThatIOException().isThrownBy(() -> fs.readFile("/../../../etc/passwd"))
                .withMessageContaining("Path traversal");
    }

    @Test
    void testPathTraversalWithDotDot() {
        assertThatIOException().isThrownBy(() -> fs.exists("../../.."))
                .withMessageContaining("Path traversal");
    }

    @Test
    void testResolveRoot() throws IOException {
        Path resolved = fs.resolve("/");
        assertThat(resolved).isEqualTo(tempDir);
    }

    @Test
    void testResolveEmpty() throws IOException {
        Path resolved = fs.resolve("");
        assertThat(resolved).isEqualTo(tempDir);
    }

    @Test
    void testBaseDir() {
        assertThat(fs.baseDir()).isEqualTo(tempDir);
    }

    @Test
    void testConstructWithNonExistentDirThrows() {
        assertThatIOException().isThrownBy(() -> new LocalFileSystem(Path.of("/nonexistent/path")));
    }
}
