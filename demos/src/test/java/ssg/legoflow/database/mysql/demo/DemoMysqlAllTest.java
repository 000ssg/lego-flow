package ssg.legoflow.database.mysql.demo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the comprehensive MySQL demo and verifies all feature sections.
 *
 * <p>By default, uses the in-house {@code MysqlServer}. To test against
 * an external MySQL/MariaDB/Percona Server, set {@code DemoMysqlAll.USE_EXTERNAL = true}
 * and configure host/port before running.</p>
 */
class DemoMysqlAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoMysqlAll.runAll();

        assertThat(results.simpleQuery())
                .as("Simple query SELECT returns inserted rows")
                .isEqualTo(3);

        assertThat(results.preparedStatement())
                .as("Prepared statement inserts and queries products")
                .isEqualTo(3);

        assertThat(results.transactionStatements())
                .as("BEGIN/COMMIT/ROLLBACK statements accepted")
                .isTrue();

        assertThat(results.multipleResults())
                .as("Multiple sequential queries return correct total rows")
                .isEqualTo(7);

        assertThat(results.authentication())
                .as("Both mysql_native_password and caching_sha2_password succeed")
                .isTrue();

        assertThat(results.serverUtilities())
                .as("Ping, statistics, and server version succeed")
                .isTrue();

        assertThat(results.joinQueries())
                .as("JOIN queries return matching rows")
                .isEqualTo(3);

        assertThat(results.orderByLimit())
                .as("ORDER BY and LIMIT work correctly")
                .isTrue();

        assertThat(results.aggregateQueries())
                .as("Aggregate GROUP BY returns correct number of groups")
                .isEqualTo(2);

        assertThat(results.advancedWhere())
                .as("Advanced WHERE (AND, LIKE, IN) works correctly")
                .isTrue();

        assertThat(results.transactionRollback())
                .as("ROLLBACK actually reverts changes")
                .isTrue();
    }
}
