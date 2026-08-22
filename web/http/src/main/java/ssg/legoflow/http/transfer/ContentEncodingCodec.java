package ssg.legoflow.http.transfer;

import ssg.legoflow.blocks.AbstractDataFilter;
import ssg.legoflow.blocks.Context;
import ssg.legoflow.http.header.ContentEncoding;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;
public class ContentEncodingCodec extends AbstractDataFilter<ByteBuffer> {

    private final ContentEncoding encoding;
    private final Mode mode;

    public enum Mode { COMPRESS, DECOMPRESS }

    public ContentEncodingCodec(ContentEncoding encoding, Mode mode) {
        super(ByteBuffer.class);
        this.encoding = encoding;
        this.mode = mode;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ByteBuffer[] doFilter(Context ctx, ByteBuffer... data) {
        var results = new ByteBuffer[data.length];
        for (int i = 0; i < data.length; i++) {
            try {
                var buf = data[i].duplicate();
                var bytes = new byte[buf.remaining()];
                buf.get(bytes);
                results[i] = ByteBuffer.wrap(switch (mode) {
                    case COMPRESS -> compress(bytes);
                    case DECOMPRESS -> decompress(bytes);
                });
            } catch (Exception e) {
                ctx.handleError(e);
                results[i] = data[i];
            }
        }
        return results;
    }

    private byte[] compress(byte[] input) throws Exception {
        var out = new ByteArrayOutputStream();
        switch (encoding) {
            case GZIP -> {
                try (var gzip = new GZIPOutputStream(out)) { gzip.write(input); }
            }
            case DEFLATE -> {
                try (var deflate = new DeflaterOutputStream(out)) { deflate.write(input); }
            }
            default -> { return input; }
        }
        return out.toByteArray();
    }

    private byte[] decompress(byte[] input) throws Exception {
        var in = new ByteArrayInputStream(input);
        return switch (encoding) {
            case GZIP -> { try (var gzip = new GZIPInputStream(in)) { yield gzip.readAllBytes(); } }
            case DEFLATE -> { try (var inflate = new InflaterInputStream(in)) { yield inflate.readAllBytes(); } }
            default -> input;
        };
    }

    public ContentEncoding getEncoding() {
        return encoding;
    }
}
