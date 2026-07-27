package ssg.legoflow.database.postgresql.server;

import java.nio.charset.StandardCharsets;

/**
 * A bound portal: a prepared statement with bound parameter values, ready for execution.
 *
 * @param name            the portal name (empty for unnamed)
 * @param statement       the source prepared statement
 * @param parameterValues the bound parameter values (null for SQL NULL)
 * @since 1.0.0
 */
public record Portal(String name, PreparedStatement statement, byte[][] parameterValues) {

    /**
     * Returns parameter values as strings (for text-format parameters).
     *
     * @return the string parameter values
     */
    public String[] parameterStrings() {
        String[] result = new String[parameterValues.length];
        for (int i = 0; i < parameterValues.length; i++) {
            result[i] = parameterValues[i] == null ? null
                    : new String(parameterValues[i], StandardCharsets.UTF_8);
        }
        return result;
    }
}
