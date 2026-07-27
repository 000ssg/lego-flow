package ssg.legoflow.media.rtsp.demo;

import ssg.legoflow.media.rtsp.client.RtspClient;
import ssg.legoflow.media.rtsp.server.RtspServer;

/**
 * Demo RTSP client performing a complete playback control workflow.
 *
 * <p>Demonstrates: OPTIONS, DESCRIBE, SETUP, PLAY, PAUSE, PLAY (resume),
 * TEARDOWN sequence against a local RTSP server.
 *
 * @since 1.0.0
 */
public final class ClientPlaybackDemo {

    private ClientPlaybackDemo() {}

    /**
     * Runs the playback demo using the given server.
     *
     * @param server the RTSP server
     * @param uri    the media URI
     * @return the client used for the demo
     */
    public static RtspClient run(RtspServer server, String uri) {
        var client = new RtspClient(uri, server);

        // 1. Query capabilities
        var optionsResponse = client.options();

        // 2. Get media description
        var describeResponse = client.describe();

        // 3. Setup transport
        var setup = client.setup("RTP/AVP;unicast;client_port=8000-8001");

        // 4. Start playback
        var playResponse = client.play();

        // 5. Pause
        var pauseResponse = client.pause();

        // 6. Resume from paused position
        var resumeResponse = client.play("npt=10-");

        // 7. Teardown
        var teardownResponse = client.teardown();

        return client;
    }
}
