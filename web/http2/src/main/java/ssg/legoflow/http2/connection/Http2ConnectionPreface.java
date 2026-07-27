package ssg.legoflow.http2.connection;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class Http2ConnectionPreface {

    public static final String CLIENT_PREFACE_STRING = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n";
    public static final byte[] CLIENT_PREFACE_BYTES = CLIENT_PREFACE_STRING.getBytes(StandardCharsets.US_ASCII);
    public static final int CLIENT_PREFACE_LENGTH = CLIENT_PREFACE_BYTES.length;

    private Http2ConnectionPreface() {}

    public static ByteBuffer createClientPreface() {
        return ByteBuffer.wrap(CLIENT_PREFACE_BYTES.clone());
    }

    public static boolean isClientPreface(ByteBuffer data) {
        if (data.remaining() < CLIENT_PREFACE_LENGTH) return false;
        var dup = data.duplicate();
        for (byte prefaceByte : CLIENT_PREFACE_BYTES) {
            if (dup.get() != prefaceByte) return false;
        }
        return true;
    }

    public static ByteBuffer createClientPrefaceWithSettings(Http2Settings settings) {
        var settingsPayload = settings.encode();
        var preface = createClientPreface();

        var settingsFrame = ssg.legoflow.http2.frame.Http2Frame.settings(settingsPayload);
        var encodedFrame = settingsFrame.encode();

        var combined = ByteBuffer.allocate(preface.remaining() + encodedFrame.remaining());
        combined.put(preface);
        combined.put(encodedFrame);
        combined.flip();
        return combined;
    }
}
