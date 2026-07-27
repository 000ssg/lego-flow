package ssg.legoflow.http2.connection;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

public class Http2Settings {

    public static final int HEADER_TABLE_SIZE = 0x1;
    public static final int ENABLE_PUSH = 0x2;
    public static final int MAX_CONCURRENT_STREAMS = 0x3;
    public static final int INITIAL_WINDOW_SIZE = 0x4;
    public static final int MAX_FRAME_SIZE = 0x5;
    public static final int MAX_HEADER_LIST_SIZE = 0x6;

    public static final int DEFAULT_HEADER_TABLE_SIZE = 4096;
    public static final int DEFAULT_ENABLE_PUSH = 1;
    public static final int DEFAULT_MAX_CONCURRENT_STREAMS = 100;
    public static final int DEFAULT_INITIAL_WINDOW_SIZE = 65535;
    public static final int DEFAULT_MAX_FRAME_SIZE = 16384;
    public static final int DEFAULT_MAX_HEADER_LIST_SIZE = 8192;

    private final Map<Integer, Integer> settings = new LinkedHashMap<>();

    public Http2Settings() {
        settings.put(HEADER_TABLE_SIZE, DEFAULT_HEADER_TABLE_SIZE);
        settings.put(ENABLE_PUSH, DEFAULT_ENABLE_PUSH);
        settings.put(MAX_CONCURRENT_STREAMS, DEFAULT_MAX_CONCURRENT_STREAMS);
        settings.put(INITIAL_WINDOW_SIZE, DEFAULT_INITIAL_WINDOW_SIZE);
        settings.put(MAX_FRAME_SIZE, DEFAULT_MAX_FRAME_SIZE);
        settings.put(MAX_HEADER_LIST_SIZE, DEFAULT_MAX_HEADER_LIST_SIZE);
    }

    public int get(int id) {
        return settings.getOrDefault(id, 0);
    }

    public Http2Settings set(int id, int value) {
        validate(id, value);
        settings.put(id, value);
        return this;
    }

    public int headerTableSize() {
        return settings.get(HEADER_TABLE_SIZE);
    }

    public boolean enablePush() {
        return settings.get(ENABLE_PUSH) == 1;
    }

    public int maxConcurrentStreams() {
        return settings.get(MAX_CONCURRENT_STREAMS);
    }

    public int initialWindowSize() {
        return settings.get(INITIAL_WINDOW_SIZE);
    }

    public int maxFrameSize() {
        return settings.get(MAX_FRAME_SIZE);
    }

    public int maxHeaderListSize() {
        return settings.get(MAX_HEADER_LIST_SIZE);
    }

    public ByteBuffer encode() {
        var buf = ByteBuffer.allocate(settings.size() * 6);
        for (var entry : settings.entrySet()) {
            buf.putShort((short) entry.getKey().intValue());
            buf.putInt(entry.getValue());
        }
        buf.flip();
        return buf;
    }

    public static Http2Settings decode(ByteBuffer data) {
        var settings = new Http2Settings();
        var buf = data.duplicate();
        while (buf.remaining() >= 6) {
            int id = buf.getShort() & 0xFFFF;
            int value = buf.getInt();
            settings.set(id, value);
        }
        return settings;
    }

    public void applyFrom(Http2Settings other) {
        for (var entry : other.settings.entrySet()) {
            set(entry.getKey(), entry.getValue());
        }
    }

    private void validate(int id, int value) {
        switch (id) {
            case ENABLE_PUSH -> {
                if (value != 0 && value != 1) {
                    throw new IllegalArgumentException("ENABLE_PUSH must be 0 or 1, got: " + value);
                }
            }
            case INITIAL_WINDOW_SIZE -> {
                if (value < 0 || value > 0x7FFFFFFF) {
                    throw new IllegalArgumentException(
                            "INITIAL_WINDOW_SIZE must be <= 2^31-1, got: " + value);
                }
            }
            case MAX_FRAME_SIZE -> {
                if (value < 16384 || value > 16777215) {
                    throw new IllegalArgumentException(
                            "MAX_FRAME_SIZE must be between 16384 and 16777215, got: " + value);
                }
            }
            default -> {}
        }
    }

    public Map<Integer, Integer> toMap() {
        return Map.copyOf(settings);
    }
}
