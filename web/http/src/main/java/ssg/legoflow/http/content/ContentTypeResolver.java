package ssg.legoflow.http.content;

import ssg.legoflow.http.header.MediaType;
import java.util.List;
public class ContentTypeResolver {

    private final MediaTypeRegistry registry;

    public ContentTypeResolver() {
        this(new MediaTypeRegistry());
    }

    public ContentTypeResolver(MediaTypeRegistry registry) {
        this.registry = registry;
    }

    public MediaType resolve(String filename, String acceptHeader) {
        var byFile = registry.getByFilename(filename);
        if (acceptHeader == null || acceptHeader.isBlank()) {
            return byFile.orElse(MediaType.APPLICATION_OCTET_STREAM);
        }
        var negotiator = new ContentNegotiator();
        var fileType = byFile.orElse(MediaType.APPLICATION_OCTET_STREAM);
        var negotiated = negotiator.negotiateMediaType(acceptHeader, List.of(fileType));
        return negotiated.orElse(fileType);
    }

    public MediaTypeRegistry getRegistry() {
        return registry;
    }
}
