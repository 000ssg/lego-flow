package ssg.legoflow.http.staticcontent;

import ssg.legoflow.http.content.MediaTypeRegistry;
import ssg.legoflow.http.header.MediaType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class DirectoryContentResolver implements ContentResolver {

    private final Path root;
    private final MediaTypeRegistry mediaTypeRegistry;

    public DirectoryContentResolver(Path root) {
        this(root, new MediaTypeRegistry());
    }

    public DirectoryContentResolver(Path root, MediaTypeRegistry mediaTypeRegistry) {
        this.root = root.toAbsolutePath().normalize();
        this.mediaTypeRegistry = mediaTypeRegistry;
    }

    @Override
    public Optional<ResolvedContent> resolve(String path) {
        var resolved = root.resolve(path.startsWith("/") ? path.substring(1) : path).normalize();
        if (!resolved.startsWith(root)) return Optional.empty();
        if (!Files.exists(resolved) || Files.isDirectory(resolved)) {
            var index = resolved.resolve("index.html");
            if (Files.exists(index)) resolved = index;
            else return Optional.empty();
        }
        try {
            var content = ByteBuffer.wrap(Files.readAllBytes(resolved));
            var mediaType = mediaTypeRegistry.getByFilename(resolved.getFileName().toString())
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);
            var lastModified = Files.getLastModifiedTime(resolved).toMillis();
            return Optional.of(new ResolvedContent(content, mediaType, lastModified));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public Path getRoot() { return root; }
}
