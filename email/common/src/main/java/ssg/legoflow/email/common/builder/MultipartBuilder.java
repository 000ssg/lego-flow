package ssg.legoflow.email.common.builder;

import ssg.legoflow.email.common.mime.MimeMultipart;
import ssg.legoflow.email.common.mime.MimePart;
import ssg.legoflow.email.common.mime.MultipartType;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for creating {@link MimeMultipart} containers.
 *
 * @since 0.1.0
 */
public final class MultipartBuilder {

    private final MultipartType type;
    private final String boundary;
    private final List<Object> parts = new ArrayList<>();
    private String preamble;
    private String epilogue;

    private MultipartBuilder(MultipartType type) {
        this.type = type;
        this.boundary = MimeMultipart.generateBoundary();
    }

    private MultipartBuilder(MultipartType type, String boundary) {
        this.type = type;
        this.boundary = boundary;
    }

    /**
     * Creates a multipart/mixed builder.
     *
     * @return a new builder
     */
    public static MultipartBuilder mixed() {
        return new MultipartBuilder(MultipartType.MIXED);
    }

    /**
     * Creates a multipart/alternative builder.
     *
     * @return a new builder
     */
    public static MultipartBuilder alternative() {
        return new MultipartBuilder(MultipartType.ALTERNATIVE);
    }

    /**
     * Creates a multipart/related builder.
     *
     * @return a new builder
     */
    public static MultipartBuilder related() {
        return new MultipartBuilder(MultipartType.RELATED);
    }

    /**
     * Creates a builder for the specified multipart type.
     *
     * @param type the multipart type
     * @return a new builder
     */
    public static MultipartBuilder of(MultipartType type) {
        return new MultipartBuilder(type);
    }

    /**
     * Creates a builder with a specific boundary.
     *
     * @param type     the multipart type
     * @param boundary the boundary string
     * @return a new builder
     */
    public static MultipartBuilder of(MultipartType type, String boundary) {
        return new MultipartBuilder(type, boundary);
    }

    /**
     * Adds a MimePart to this multipart container.
     *
     * @param part the part to add
     * @return this builder
     */
    public MultipartBuilder addPart(MimePart part) {
        parts.add(part);
        return this;
    }

    /**
     * Adds a nested MimeMultipart to this container.
     *
     * @param multipart the nested multipart
     * @return this builder
     */
    public MultipartBuilder addPart(MimeMultipart multipart) {
        parts.add(multipart);
        return this;
    }

    /**
     * Adds a part built from a MimePartBuilder.
     *
     * @param builder the part builder
     * @return this builder
     */
    public MultipartBuilder addPart(MimePartBuilder builder) {
        parts.add(builder.build());
        return this;
    }

    /**
     * Adds a nested multipart built from a MultipartBuilder.
     *
     * @param builder the multipart builder
     * @return this builder
     */
    public MultipartBuilder addPart(MultipartBuilder builder) {
        parts.add(builder.build());
        return this;
    }

    /**
     * Sets the preamble text.
     *
     * @param preamble the preamble
     * @return this builder
     */
    public MultipartBuilder preamble(String preamble) {
        this.preamble = preamble;
        return this;
    }

    /**
     * Sets the epilogue text.
     *
     * @param epilogue the epilogue
     * @return this builder
     */
    public MultipartBuilder epilogue(String epilogue) {
        this.epilogue = epilogue;
        return this;
    }

    /**
     * Returns the boundary string for use in Content-Type headers.
     *
     * @return the boundary
     */
    public String boundary() {
        return boundary;
    }

    /**
     * Returns the Content-Type header value for this multipart.
     *
     * @return the content type value
     */
    public String contentTypeValue() {
        return "multipart/" + type.subtype() + "; boundary=\"" + boundary + "\"";
    }

    /**
     * Builds the MimeMultipart.
     *
     * @return the constructed MimeMultipart
     */
    public MimeMultipart build() {
        return new MimeMultipart(boundary, type, parts, preamble, epilogue);
    }
}
