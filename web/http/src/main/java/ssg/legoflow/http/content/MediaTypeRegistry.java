package ssg.legoflow.http.content;

import ssg.legoflow.http.header.MediaType;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MediaTypeRegistry {

    private final Map<String, MediaType> extensionMap = new ConcurrentHashMap<>();

    public MediaTypeRegistry() {
        registerDefaults();
    }

    public void register(String extension, MediaType mediaType) {
        extensionMap.put(extension.toLowerCase(), mediaType);
    }

    public Optional<MediaType> getByExtension(String extension) {
        return Optional.ofNullable(extensionMap.get(extension.toLowerCase()));
    }

    public Optional<MediaType> getByFilename(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return Optional.of(MediaType.APPLICATION_OCTET_STREAM);
        return getByExtension(filename.substring(dot + 1));
    }

    private void registerDefaults() {
        register("html", MediaType.TEXT_HTML);
        register("htm", MediaType.TEXT_HTML);
        register("txt", MediaType.TEXT_PLAIN);
        register("css", new MediaType("text", "css", Map.of()));
        register("js", new MediaType("application", "javascript", Map.of()));
        register("json", MediaType.APPLICATION_JSON);
        register("xml", MediaType.APPLICATION_XML);
        register("jpg", new MediaType("image", "jpeg", Map.of()));
        register("jpeg", new MediaType("image", "jpeg", Map.of()));
        register("png", new MediaType("image", "png", Map.of()));
        register("gif", new MediaType("image", "gif", Map.of()));
        register("svg", new MediaType("image", "svg+xml", Map.of()));
        register("ico", new MediaType("image", "x-icon", Map.of()));
        register("pdf", new MediaType("application", "pdf", Map.of()));
        register("zip", new MediaType("application", "zip", Map.of()));
        register("gz", new MediaType("application", "gzip", Map.of()));
        register("woff", new MediaType("font", "woff", Map.of()));
        register("woff2", new MediaType("font", "woff2", Map.of()));
        register("ttf", new MediaType("font", "ttf", Map.of()));
        register("mp4", new MediaType("video", "mp4", Map.of()));
        register("webm", new MediaType("video", "webm", Map.of()));
        register("mp3", new MediaType("audio", "mpeg", Map.of()));
        register("wav", new MediaType("audio", "wav", Map.of()));
    }
}
