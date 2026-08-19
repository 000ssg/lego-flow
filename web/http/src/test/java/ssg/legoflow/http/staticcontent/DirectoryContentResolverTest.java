package ssg.legoflow.http.staticcontent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;
class DirectoryContentResolverTest {

    @TempDir
    Path tempDir;

    private DirectoryContentResolver resolver;

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(tempDir.resolve("hello.txt"), "Hello from file");
        Files.writeString(tempDir.resolve("page.html"), "<html><body>Page</body></html>");
        var subDir = tempDir.resolve("sub");
        Files.createDirectories(subDir);
        Files.writeString(subDir.resolve("index.html"), "<html>Index</html>");
        resolver = new DirectoryContentResolver(tempDir);
    }

    @Test
    void testResolveExistingFile() {
        var result = resolver.resolve("hello.txt");

        assertThat(result).isPresent();
        var content = result.get();
        var bytes = new byte[content.content().remaining()];
        content.content().get(bytes);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("Hello from file");
    }

    @Test
    void testResolveHtmlFileWithCorrectMediaType() {
        var result = resolver.resolve("page.html");

        assertThat(result).isPresent();
        assertThat(result.get().mediaType().type()).isEqualTo("text");
        assertThat(result.get().mediaType().subtype()).isEqualTo("html");
    }

    @Test
    void testResolveNonExistentFile() {
        var result = resolver.resolve("missing.txt");

        assertThat(result).isEmpty();
    }

    @Test
    void testResolveDirectoryFallsBackToIndex() {
        var result = resolver.resolve("sub");

        assertThat(result).isPresent();
        var bytes = new byte[result.get().content().remaining()];
        result.get().content().get(bytes);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("<html>Index</html>");
    }

    @Test
    void testResolvePathTraversalBlocked() {
        var result = resolver.resolve("../../../etc/passwd");

        assertThat(result).isEmpty();
    }

    @Test
    void testResolveWithLeadingSlash() {
        var result = resolver.resolve("/hello.txt");

        assertThat(result).isPresent();
    }

    @Test
    void testGetRoot() {
        assertThat(resolver.getRoot()).isEqualTo(tempDir.toAbsolutePath().normalize());
    }

    @Test
    void testLastModifiedIsSet() {
        var result = resolver.resolve("hello.txt");

        assertThat(result).isPresent();
        assertThat(result.get().lastModified()).isGreaterThan(0);
    }
}
