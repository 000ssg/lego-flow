package ssg.legoflow.coap.codec;

import ssg.legoflow.blocks.AbstractDataFilter;
import ssg.legoflow.blocks.Context;
import ssg.legoflow.coap.protocol.CoapCode;
import ssg.legoflow.coap.protocol.CoapMessage;
import ssg.legoflow.coap.protocol.CoapType;
import ssg.legoflow.coap.protocol.CoapVersion;

import java.nio.ByteBuffer;
import ssg.legoflow.service.util.BufferPool;

/**
 * CoAP message codec implementing encoding and decoding of CoAP messages
 * as defined in RFC 7252, Section 3.
 *
 * <p>Extends {@link AbstractDataFilter} to integrate with the Lego Flow
 * data processing pipeline. The codec handles the 4-byte header, options
 * with delta encoding, and the optional payload marker (0xFF).
 *
 * <p>Header format (4 bytes):
 * <pre>
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |Ver| T |  TKL  |      Code     |          Message ID           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 *
 * @since 0.1.0
 */
public class CoapCodec extends AbstractDataFilter<ByteBuffer> {

    /** Payload marker byte (0xFF). */
    public static final int PAYLOAD_MARKER = 0xFF;

    /**
     * Creates a new CoAP codec.
     *
     * @since 0.1.0
     */
    public CoapCodec() {
        super(ByteBuffer.class);
    }

    /**
     * Filter pass-through for integration with the Lego Flow pipeline.
     * Returns the input buffers unchanged.
     *
     * @param ctx  the processing context
     * @param data the input byte buffers
     * @return the input buffers unchanged
     * @since 0.1.0
     */
    @Override
    @SuppressWarnings("unchecked")
    protected ByteBuffer[] doFilter(Context ctx, ByteBuffer... data) {
        return data;
    }

    /**
     * Encodes a CoAP message into a byte buffer.
     *
     * @param message the message to encode
     * @return a byte buffer containing the encoded message (flipped, ready for reading)
     * @throws IllegalArgumentException if the token length exceeds 8
     * @since 0.1.0
     */
    public ByteBuffer encode(CoapMessage message) {
        byte[] token = message.token();
        if (token.length > 8) {
            throw new IllegalArgumentException("Token length must not exceed 8: " + token.length);
        }

        var optionsBuffer = CoapOptionCodec.encode(message.options());
        byte[] payload = message.payload();

        int size = 4 + token.length + optionsBuffer.remaining();
        if (payload.length > 0) {
            size += 1 + payload.length; // payload marker + payload
        }

        var buffer = BufferPool.getBuffer(size);

        // Header byte 1: Ver(2) + T(2) + TKL(4)
        int firstByte = (message.version().versionNumber() << 6)
                | (message.type().value() << 4)
                | (token.length & 0x0F);
        buffer.put((byte) firstByte);

        // Header byte 2: Code
        buffer.put((byte) message.code().encode());

        // Header bytes 3-4: Message ID
        buffer.putShort((short) message.messageId());

        // Token
        if (token.length > 0) {
            buffer.put(token);
        }

        // Options
        if (optionsBuffer.hasRemaining()) {
            buffer.put(optionsBuffer);
        }

        // Payload
        if (payload.length > 0) {
            buffer.put((byte) PAYLOAD_MARKER);
            buffer.put(payload);
        }

        buffer.flip();
        return buffer;
    }

    /**
     * Decodes a CoAP message from a byte buffer.
     *
     * @param buffer the buffer containing the encoded message (position at start)
     * @return the decoded message
     * @throws IllegalArgumentException if the buffer is too short or contains invalid data
     * @since 0.1.0
     */
    public CoapMessage decode(ByteBuffer buffer) {
        if (buffer.remaining() < 4) {
            throw new IllegalArgumentException("Buffer too short for CoAP header: " + buffer.remaining());
        }

        // Header byte 1
        int firstByte = buffer.get() & 0xFF;
        int version = (firstByte >> 6) & 0x03;
        int type = (firstByte >> 4) & 0x03;
        int tokenLength = firstByte & 0x0F;

        if (tokenLength > 8) {
            throw new IllegalArgumentException("Invalid token length: " + tokenLength);
        }

        // Header byte 2: Code
        int codeByte = buffer.get() & 0xFF;

        // Header bytes 3-4: Message ID
        int messageId = buffer.getShort() & 0xFFFF;

        // Token
        var token = new byte[tokenLength];
        if (tokenLength > 0) {
            buffer.get(token);
        }

        // Options and payload
        var builder = CoapMessage.builder()
                .version(CoapVersion.fromNumber(version))
                .type(CoapType.fromValue(type))
                .code(CoapCode.decode(codeByte))
                .messageId(messageId)
                .token(token);

        // Decode options
        if (buffer.hasRemaining()) {
            var options = CoapOptionCodec.decode(buffer);
            for (var option : options) {
                builder.option(option);
            }

            // Check for payload
            if (buffer.hasRemaining()) {
                int marker = buffer.get() & 0xFF;
                if (marker == PAYLOAD_MARKER && buffer.hasRemaining()) {
                    var payload = new byte[buffer.remaining()];
                    buffer.get(payload);
                    builder.payload(payload);
                }
            }
        }

        return builder.build();
    }
}
