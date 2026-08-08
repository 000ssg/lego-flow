package ssg.legoflow.media.common.codec;

import ssg.legoflow.media.common.sdp.*;

/**
 * Serializes a {@link SessionDescription} to SDP text format.
 *
 * <p>Produces output conforming to RFC 4566 with {@code \r\n} line endings.
 * Lines are written in the prescribed order: v, o, s, i, u, e, p, c, b, t, r, z, k, a, m.
 *
 * @since 0.1.0
 */
public final class SdpWriter {

    /** SDP line ending (CRLF). */
    private static final String CRLF = "\r\n";

    private SdpWriter() {
    }

    /**
     * Serializes a session description to SDP text.
     *
     * @param session the session description
     * @return the SDP text with CRLF line endings
     */
    public static String write(SessionDescription session) {
        var sb = new StringBuilder();

        // v= (version)
        sb.append("v=").append(session.version()).append(CRLF);

        // o= (origin)
        sb.append("o=").append(session.origin().format()).append(CRLF);

        // s= (session name)
        sb.append("s=").append(session.sessionName()).append(CRLF);

        // i= (session info, optional)
        session.sessionInfo().ifPresent(i -> sb.append("i=").append(i).append(CRLF));

        // u= (URI, optional)
        session.uri().ifPresent(u -> sb.append("u=").append(u).append(CRLF));

        // e= (email, optional)
        session.email().ifPresent(e -> sb.append("e=").append(e).append(CRLF));

        // p= (phone, optional)
        session.phone().ifPresent(p -> sb.append("p=").append(p).append(CRLF));

        // c= (connection info, optional at session level)
        session.connectionInfo().ifPresent(c -> sb.append("c=").append(c.format()).append(CRLF));

        // b= (bandwidth)
        for (Bandwidth bw : session.bandwidths()) {
            sb.append("b=").append(bw.format()).append(CRLF);
        }

        // t= (timing)
        for (Timing t : session.timings()) {
            sb.append("t=").append(t.format()).append(CRLF);
        }

        // r= (repeat times)
        for (RepeatTime r : session.repeatTimes()) {
            sb.append("r=").append(r.format()).append(CRLF);
        }

        // z= (timezone adjustments, optional)
        session.timezoneAdjustments().ifPresent(z -> sb.append("z=").append(z).append(CRLF));

        // k= (encryption key, optional)
        session.encryptionKey().ifPresent(k -> sb.append("k=").append(k).append(CRLF));

        // a= (session-level attributes)
        for (Attribute a : session.attributes()) {
            sb.append("a=").append(a.format()).append(CRLF);
        }

        // m= (media descriptions)
        for (MediaDescription media : session.mediaDescriptions()) {
            writeMedia(sb, media);
        }

        return sb.toString();
    }

    private static void writeMedia(StringBuilder sb, MediaDescription media) {
        // m= line
        sb.append("m=").append(media.formatMediaLine()).append(CRLF);

        // i= (media title, optional)
        media.title().ifPresent(i -> sb.append("i=").append(i).append(CRLF));

        // c= (media-level connection info, optional)
        media.connectionInfo().ifPresent(c -> sb.append("c=").append(c.format()).append(CRLF));

        // b= (bandwidth)
        for (Bandwidth bw : media.bandwidths()) {
            sb.append("b=").append(bw.format()).append(CRLF);
        }

        // a= (media-level attributes — write all generic attributes to preserve order)
        for (Attribute a : media.attributes()) {
            sb.append("a=").append(a.format()).append(CRLF);
        }
    }
}
