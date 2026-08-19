package ssg.legoflow.database.postgresql.demo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Runs the comprehensive PostgreSQL demo and verifies all feature sections.
 *
 * <p>By default, uses the in-house {@code PgServer}. To test against
 * an external PostgreSQL, CockroachDB, or YugabyteDB, set
 * {@code DemoPostgreSqlAll.USE_EXTERNAL = true} and configure host/port
 * before running.</p>
 */
class DemoPostgreSqlAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoPostgreSqlAll.runAll();

        assertThat(results.simpleQueryRows())
                .as("Simple query (INSERT/UPDATE/DELETE cycle)")
                .isEqualTo(2);

        assertThat(results.extendedQueryRows())
                .as("Extended query (prepared statement inserts)")
                .isEqualTo(3);

        assertThat(results.copyInRows())
                .as("COPY IN bulk import")
                .isEqualTo(5);

        assertThat(results.copyOutRows())
                .as("COPY OUT bulk export")
                .isEqualTo(5);

        assertThat(results.listenNotify())
                .as("LISTEN/NOTIFY notification received")
                .isTrue();

        assertThat(results.transactionCommit())
                .as("Transaction COMMIT visible rows")
                .isEqualTo(2);

        assertThat(results.authCleartext())
                .as("Cleartext password authentication")
                .isTrue();

        assertThat(results.authMd5())
                .as("MD5 password authentication")
                .isTrue();

        assertThat(results.authScramSha256())
                .as("SCRAM-SHA-256 authentication")
                .isTrue();

        assertThat(results.aggregateQueries())
                .as("Aggregate queries verified")
                .isEqualTo(2);

        assertThat(results.joinQueries())
                .as("JOIN query result rows (inner + left)")
                .isEqualTo(7); // 3 inner + 4 left
    }
}
