package ssg.legoflow.database.postgresql.client;

import ssg.legoflow.database.postgresql.protocol.BackendMessage;
import ssg.legoflow.database.postgresql.protocol.FrontendMessage;
import ssg.legoflow.database.postgresql.protocol.PgCodec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles COPY IN/OUT streaming operations on the client side.
 *
 * @since 0.1.0
 */
public final class PgCopyStream {

    private final InputStream in;
    private final OutputStream out;

    /**
     * Creates a new COPY stream handler.
     *
     * @param in  the input stream from the server
     * @param out the output stream to the server
     */
    public PgCopyStream(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;
    }

    /**
     * Sends COPY IN data to the server.
     *
     * @param data the data rows to send
     * @throws IOException if an I/O error occurs
     */
    public void writeCopyData(List<String> data) throws IOException {
        for (String row : data) {
            byte[] rowBytes = row.getBytes(StandardCharsets.UTF_8);
            PgCodec.write(out, PgCodec.encodeFrontend(new FrontendMessage.CopyData(rowBytes)));
        }
        PgCodec.write(out, PgCodec.encodeFrontend(new FrontendMessage.CopyDone()));
    }

    /**
     * Sends COPY IN data as byte arrays.
     *
     * @param chunks the data chunks to send
     * @throws IOException if an I/O error occurs
     */
    public void writeCopyDataBytes(List<byte[]> chunks) throws IOException {
        for (byte[] chunk : chunks) {
            PgCodec.write(out, PgCodec.encodeFrontend(new FrontendMessage.CopyData(chunk)));
        }
        PgCodec.write(out, PgCodec.encodeFrontend(new FrontendMessage.CopyDone()));
    }

    /**
     * Sends a COPY FAIL message.
     *
     * @param errorMessage the error description
     * @throws IOException if an I/O error occurs
     */
    public void writeCopyFail(String errorMessage) throws IOException {
        PgCodec.write(out, PgCodec.encodeFrontend(new FrontendMessage.CopyFail(errorMessage)));
    }

    /**
     * Reads COPY OUT data from the server.
     *
     * @return the collected data rows as strings
     * @throws IOException if an I/O error occurs
     */
    public List<String> readCopyData() throws IOException {
        List<String> rows = new ArrayList<>();
        while (true) {
            BackendMessage msg = PgCodec.decodeBackend(in);
            if (msg instanceof BackendMessage.CopyData cd) {
                rows.add(new String(cd.data(), StandardCharsets.UTF_8));
            } else if (msg instanceof BackendMessage.CopyDone) {
                break;
            } else if (msg instanceof BackendMessage.ErrorResponse) {
                throw new IOException("COPY OUT error: " + ((BackendMessage.ErrorResponse) msg).message());
            }
        }
        return rows;
    }
}
