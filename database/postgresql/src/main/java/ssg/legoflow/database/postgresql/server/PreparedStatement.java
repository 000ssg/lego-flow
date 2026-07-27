package ssg.legoflow.database.postgresql.server;

/**
 * A named prepared statement on the server side.
 *
 * @param name           the statement name (empty for unnamed)
 * @param sql            the SQL query with $1, $2, ... parameter placeholders
 * @param parameterTypes the OIDs of parameter types
 * @since 1.0.0
 */
public record PreparedStatement(String name, String sql, int[] parameterTypes) {}
