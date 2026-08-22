package ssg.legoflow.media.rtsp.fixture;

import ssg.legoflow.media.common.sdp.*;
import ssg.legoflow.media.rtsp.server.MediaSource;
import ssg.legoflow.media.rtsp.server.RtspServer;
import java.util.List;
import java.util.Optional;
/**
 * Demo RTSP streaming server with a test media source.
 *
 * <p>Creates a server with a video media source that provides
 * an SDP description for H.264 video.
 *
 * @since 0.1.0
 */
public final class StreamingServerDemo {

    private StreamingServerDemo() {}

    /**
     * Creates a demo RTSP server with a test media source.
     *
     * @param port the server port
     * @return the configured server
     */
    public static RtspServer createServer(int port) {
        var server = new RtspServer(port);
        server.registerMedia(new TestMediaSource());
        return server;
    }

    /**
     * A test media source providing H.264 video.
     */
    public static final class TestMediaSource implements MediaSource {

        @Override
        public String path() {
            return "/test/stream";
        }

        @Override
        public SessionDescription describe() {
            var origin = new Origin("-", 12345L, 1L, "IN", "IP4", "127.0.0.1");

            var videoMedia = new MediaDescription(
                    MediaType.VIDEO,
                    0,
                    1,
                    TransportProtocol.RTP_AVP,
                    List.of(96),
                    Optional.empty(),
                    Optional.of(ConnectionInfo.unicast("IN", "IP4", "0.0.0.0")),
                    List.of(),
                    Direction.SENDONLY,
                    List.of(RtpMap.of(96, "H264", 90000)),
                    List.of(),
                    List.of(),
                    Optional.empty(),
                    List.of(Attribute.of("control", "trackID=0"))
            );

            return new SessionDescription(
                    0,
                    origin,
                    "Test Stream",
                    Optional.of("A test RTSP stream"),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(ConnectionInfo.unicast("IN", "IP4", "0.0.0.0")),
                    List.of(),
                    List.of(new Timing(0, 0)),
                    List.of(),
                    Optional.empty(),
                    Optional.empty(),
                    List.of(Attribute.of("tool", "LegoFlow RTSP")),
                    List.of(videoMedia)
            );
        }

        @Override
        public Optional<Double> duration() {
            return Optional.empty(); // Live stream
        }
    }
}
