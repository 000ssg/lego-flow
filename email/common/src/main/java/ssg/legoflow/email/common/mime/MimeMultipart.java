package ssg.legoflow.email.common.mime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Multipart MIME container with boundary-delimited parts.
 *
 * <p>Represents a multipart body as defined in RFC 2046. Contains an ordered
 * list of parts (each a {@link MimePart} or nested {@link MimeMultipart}),
 * a boundary string, and optional preamble/epilogue text.
 *
 * @since 1.0.0
 */
public final class MimeMultipart {

    private final String boundary;
    private final MultipartType multipartType;
    private final List<Object> parts; // MimePart or MimeMultipart
    private final String preamble;
    private final String epilogue;

    /**
     * Creates a MimeMultipart container.
     *
     * @param boundary      the boundary string
     * @param multipartType the multipart subtype (may be null for unknown subtypes)
     * @param parts         the list of parts (MimePart or MimeMultipart)
     * @param preamble      optional preamble text (before first boundary)
     * @param epilogue      optional epilogue text (after final boundary)
     */
    public MimeMultipart(String boundary, MultipartType multipartType,
                         List<Object> parts, String preamble, String epilogue) {
        this.boundary = Objects.requireNonNull(boundary, "Boundary must not be null");
        this.multipartType = multipartType;
        this.parts = List.copyOf(parts);
        this.preamble = preamble;
        this.epilogue = epilogue;
    }

    /**
     * Creates a MimeMultipart with just boundary, type, and parts.
     *
     * @param boundary      the boundary string
     * @param multipartType the multipart subtype
     * @param parts         the list of parts
     */
    public MimeMultipart(String boundary, MultipartType multipartType, List<Object> parts) {
        this(boundary, multipartType, parts, null, null);
    }

    /**
     * Generates a unique boundary string.
     *
     * @return a new boundary string
     */
    public static String generateBoundary() {
        return "----=_Part_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Returns the boundary string.
     *
     * @return the boundary
     */
    public String boundary() {
        return boundary;
    }

    /**
     * Returns the multipart type.
     *
     * @return the multipart type, or null if unknown
     */
    public MultipartType multipartType() {
        return multipartType;
    }

    /**
     * Returns the parts in this multipart container.
     *
     * <p>Each element is either a {@link MimePart} or a {@link MimeMultipart}.
     *
     * @return unmodifiable list of parts
     */
    public List<Object> parts() {
        return parts;
    }

    /**
     * Returns the number of parts.
     *
     * @return the part count
     */
    public int partCount() {
        return parts.size();
    }

    /**
     * Returns a specific part by index.
     *
     * @param index the part index
     * @return the part (MimePart or MimeMultipart)
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public Object part(int index) {
        return parts.get(index);
    }

    /**
     * Returns the preamble text (before the first boundary).
     *
     * @return the preamble, or null
     */
    public String preamble() {
        return preamble;
    }

    /**
     * Returns the epilogue text (after the closing boundary).
     *
     * @return the epilogue, or null
     */
    public String epilogue() {
        return epilogue;
    }

    /**
     * Collects all leaf MimePart instances recursively.
     *
     * @return flat list of all MimePart instances
     */
    public List<MimePart> allParts() {
        var result = new ArrayList<MimePart>();
        collectParts(result);
        return result;
    }

    private void collectParts(List<MimePart> result) {
        for (Object part : parts) {
            if (part instanceof MimePart mp) {
                result.add(mp);
            } else if (part instanceof MimeMultipart mm) {
                mm.collectParts(result);
            }
        }
    }

    @Override
    public String toString() {
        String typeName = multipartType != null ? multipartType.subtype() : "unknown";
        return "MimeMultipart{" + typeName + ", parts=" + parts.size()
                + ", boundary=" + boundary + "}";
    }
}
