package ssg.legoflow.http.staticcontent;

import ssg.legoflow.http.header.MediaType;

import java.nio.ByteBuffer;
import java.util.Optional;

public interface ContentResolver {

    Optional<ResolvedContent> resolve(String path);

    record ResolvedContent(ByteBuffer content, MediaType mediaType, long lastModified) {}
}
