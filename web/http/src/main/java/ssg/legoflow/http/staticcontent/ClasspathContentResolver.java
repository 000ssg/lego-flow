package ssg.legoflow.http.staticcontent;

import ssg.legoflow.http.content.MediaTypeRegistry;
import ssg.legoflow.http.header.MediaType;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;
public class ClasspathContentResolver implements ContentResolver {

    private final String basePath;
    private final MediaTypeRegistry mediaTypeRegistry;
    private final ClassLoader classLoader;

    public ClasspathContentResolver(String basePath) {
        this(basePath, new MediaTypeRegistry(), Thread.currentThread().getContextClassLoader());
    }

    public ClasspathContentResolver(String basePath, MediaTypeRegistry mediaTypeRegistry, ClassLoader classLoader) {
        this.basePath = basePath.endsWith("/") ? basePath : basePath + "/";
        this.mediaTypeRegistry = mediaTypeRegistry;
        this.classLoader = classLoader;
    }

    @Override
    public Optional<ResolvedContent> resolve(String path) {
        var resourcePath = basePath + (path.startsWith("/") ? path.substring(1) : path);
        try (var stream = classLoader.getResourceAsStream(resourcePath)) {
            if (stream == null) return Optional.empty();
            var content = ByteBuffer.wrap(stream.readAllBytes());
            var filename = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            var mediaType = mediaTypeRegistry.getByFilename(filename)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);
            return Optional.of(new ResolvedContent(content, mediaType, System.currentTimeMillis()));
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
