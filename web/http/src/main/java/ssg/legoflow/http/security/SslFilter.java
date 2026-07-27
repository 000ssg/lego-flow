package ssg.legoflow.http.security;

import ssg.legoflow.blocks.AbstractDataFilter;
import ssg.legoflow.blocks.Context;

import java.nio.ByteBuffer;

public class SslFilter extends AbstractDataFilter<ByteBuffer> {

    private final SslConfig config;
    private final Mode mode;

    public enum Mode { ENCRYPT, DECRYPT }

    public SslFilter(SslConfig config, Mode mode) {
        super(ByteBuffer.class);
        this.config = config;
        this.mode = mode;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ByteBuffer[] doFilter(Context ctx, ByteBuffer... data) {
        return data;
    }

    public SslConfig getConfig() { return config; }
    public Mode getMode() { return mode; }
}
