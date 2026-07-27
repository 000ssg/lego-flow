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
 * Client-side prepared statement for the extended query protocol.
 *
 * @since 1.0.0
 */
public final class PgStatement implements AutoCloseable {

    private final String name;
    private final InputStream in;
    private final OutputStream out;
    private List<BackendMessage.ColumnDescription> cachedColumns;
    private boolean closed;

    /**
     * Creates a new prepared statement handle.
     *
     * @param name the statement name
     * @param in   the input stream from the server
     * @param out  the output stream to the server
     */
    PgStatement(String name, InputStream in, OutputStream out) {
        this.name = name;
        this.in = in;
        this.out = out;
    }

    /**
     * Creates a new prepared statement handle with pre-cached column descriptions.
     *
     * @param name    the statement name
     * @param in      the input stream from the server
     * @param out     the output stream to the server
     * @param columns the cached column descriptions from Describe
     */
    PgStatement(String name, InputStream in, OutputStream out, List<BackendMessage.ColumnDescription> columns) {
        this.name = name;
        this.in = in;
        this.out = out;
        this.cachedColumns = columns;
    }

    /**
     * Returns the statement name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Executes the prepared statement with the given parameters.
     *
     * @param params the parameter values (null for SQL NULL)
     * @return the query result
     * @throws IOException if an I/O error occurs
     */
    public PgResult execute(String... params) throws IOException {
        return execute(0, params);
    }

    /**
     * Executes the prepared statement with the given parameters and row limit.
     *
     * @param maxRows the maximum number of rows (0 for unlimited)
     * @param params  the parameter values (null for SQL NULL)
     * @return the query result
     * @throws IOException if an I/O error occurs
     */
    public PgResult execute(int maxRows, String... params) throws IOException {
        // Bind
        byte[][] paramValues = new byte[params.length][];
        for (int i = 0; i < params.length; i++) {
            paramValues[i] = params[i] == null ? null : params[i].getBytes(StandardCharsets.UTF_8);
        }

        PgCodec.write(out, PgCodec.encodeFrontend(
                new FrontendMessage.Bind("", name, new short[0], paramValues, new short[0])));

        // Execute
        PgCodec.write(out, PgCodec.encodeFrontend(
                new FrontendMessage.Execute("", maxRows)));

        // Sync
        PgCodec.write(out, PgCodec.encodeFrontend(new FrontendMessage.Sync()));

        // Read responses
        List<BackendMessage.ColumnDescription> columns = cachedColumns != null ? cachedColumns : new ArrayList<>();
        List<byte[][]> rows = new ArrayList<>();
        String tag = null;

        while (true) {
            BackendMessage msg = PgCodec.decodeBackend(in);
            switch (msg) {
                case BackendMessage.BindComplete bc -> {}
                case BackendMessage.RowDescription rd -> {
                    columns = rd.columns();
                    cachedColumns = columns;
                }
                case BackendMessage.DataRow dr -> rows.add(dr.values());
                case BackendMessage.CommandComplete cc -> tag = cc.tag();
                case BackendMessage.PortalSuspended ps -> tag = "SUSPENDED";
                case BackendMessage.EmptyQueryResponse eq -> tag = "";
                case BackendMessage.ErrorResponse er ->
                        throw new IOException("Error: " + er.message() + " [" + er.sqlState() + "]");
                case BackendMessage.ReadyForQuery rq -> { return new PgResult(columns, rows, tag); }
                default -> {}
            }
        }
    }

    /**
     * Describes this prepared statement.
     *
     * @return the parameter OIDs and column descriptions
     * @throws IOException if an I/O error occurs
     */
    public DescribeResult describe() throws IOException {
        PgCodec.write(out, PgCodec.encodeFrontend(
                new FrontendMessage.Describe((byte) 'S', name)));
        PgCodec.write(out, PgCodec.encodeFrontend(new FrontendMessage.Sync()));

        int[] paramOids = null;
        List<BackendMessage.ColumnDescription> columns = null;

        while (true) {
            BackendMessage msg = PgCodec.decodeBackend(in);
            switch (msg) {
                case BackendMessage.ParameterDescription pd -> paramOids = pd.parameterOids();
                case BackendMessage.RowDescription rd -> columns = rd.columns();
                case BackendMessage.NoData nd -> columns = List.of();
                case BackendMessage.ErrorResponse er ->
                        throw new IOException("Describe error: " + er.message());
                case BackendMessage.ReadyForQuery rq -> {
                    return new DescribeResult(
                            paramOids != null ? paramOids : new int[0],
                            columns != null ? columns : List.of());
                }
                default -> {}
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            PgCodec.write(out, PgCodec.encodeFrontend(
                    new FrontendMessage.Close((byte) 'S', name)));
            PgCodec.write(out, PgCodec.encodeFrontend(new FrontendMessage.Sync()));

            while (true) {
                BackendMessage msg = PgCodec.decodeBackend(in);
                if (msg instanceof BackendMessage.ReadyForQuery) break;
            }
        }
    }

    /**
     * Result of a Describe operation.
     *
     * @param parameterOids the parameter type OIDs
     * @param columns       the result column descriptors
     */
    public record DescribeResult(int[] parameterOids, List<BackendMessage.ColumnDescription> columns) {}
}
