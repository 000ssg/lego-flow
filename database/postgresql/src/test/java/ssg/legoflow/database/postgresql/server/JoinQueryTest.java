package ssg.legoflow.database.postgresql.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for JOIN support (INNER JOIN, LEFT JOIN, multi-table, aliases).
 */
class JoinQueryTest {

    private InMemoryDatabase db;

    @BeforeEach
    void setUp() {
        db = new InMemoryDatabase();
        db.execute("CREATE TABLE customers (id int4, name varchar, city varchar)");
        db.execute("INSERT INTO customers VALUES (1, 'Alice', 'NYC')");
        db.execute("INSERT INTO customers VALUES (2, 'Bob', 'LA')");
        db.execute("INSERT INTO customers VALUES (3, 'Charlie', 'NYC')");

        db.execute("CREATE TABLE orders (id int4, customer_id int4, product varchar, amount int4)");
        db.execute("INSERT INTO orders VALUES (101, 1, 'Widget', 100)");
        db.execute("INSERT INTO orders VALUES (102, 1, 'Gadget', 200)");
        db.execute("INSERT INTO orders VALUES (103, 2, 'Widget', 150)");
        db.execute("INSERT INTO orders VALUES (104, 9, 'Doohickey', 50)"); // no matching customer
    }

    @Test
    void testInnerJoin() {
        ResultSet rs = db.execute(
                "SELECT c.name, o.product FROM customers c JOIN orders o ON c.id = o.customer_id");
        assertThat(rs.rows()).hasSize(3); // Alice (2 orders) + Bob (1 order)
    }

    @Test
    void testInnerJoinExplicit() {
        ResultSet rs = db.execute(
                "SELECT c.name, o.product FROM customers c INNER JOIN orders o ON c.id = o.customer_id");
        assertThat(rs.rows()).hasSize(3);
    }

    @Test
    void testLeftJoin() {
        ResultSet rs = db.execute(
                "SELECT c.name, o.product FROM customers c LEFT JOIN orders o ON c.id = o.customer_id");
        // Alice: 2 orders, Bob: 1 order, Charlie: 0 orders (null)
        assertThat(rs.rows()).hasSize(4);
        boolean foundCharlie = false;
        for (String[] row : rs.rows()) {
            if ("Charlie".equals(row[0])) {
                assertThat(row[1]).isNull(); // no matching order
                foundCharlie = true;
            }
        }
        assertThat(foundCharlie).isTrue();
    }

    @Test
    void testJoinWithWhere() {
        ResultSet rs = db.execute(
                "SELECT c.name, o.product FROM customers c JOIN orders o ON c.id = o.customer_id WHERE o.product = 'Widget'");
        assertThat(rs.rows()).hasSize(2); // Alice + Bob both have Widget orders
    }

    @Test
    void testJoinWithOrderBy() {
        ResultSet rs = db.execute(
                "SELECT c.name, o.product FROM customers c JOIN orders o ON c.id = o.customer_id ORDER BY name");
        assertThat(rs.rows()).hasSize(3);
        assertThat(rs.rows().get(0)[0]).isEqualTo("Alice");
    }

    @Test
    void testJoinWithLimit() {
        ResultSet rs = db.execute(
                "SELECT c.name, o.product FROM customers c JOIN orders o ON c.id = o.customer_id LIMIT 2");
        assertThat(rs.rows()).hasSize(2);
    }

    @Test
    void testJoinSelectStar() {
        ResultSet rs = db.execute(
                "SELECT * FROM customers c JOIN orders o ON c.id = o.customer_id");
        assertThat(rs.rows()).hasSize(3);
        // Should have all columns from both tables
        assertThat(rs.columns().size()).isEqualTo(7); // 3 from customers + 4 from orders
    }

    @Test
    void testJoinWithAliases() {
        ResultSet rs = db.execute(
                "SELECT c.name AS customer_name, o.product AS item FROM customers c JOIN orders o ON c.id = o.customer_id");
        assertThat(rs.columns().get(0).name()).isEqualTo("customer_name");
        assertThat(rs.columns().get(1).name()).isEqualTo("item");
        assertThat(rs.rows()).hasSize(3);
    }

    @Test
    void testMultiTableJoin() {
        db.execute("CREATE TABLE categories (product varchar, category varchar)");
        db.execute("INSERT INTO categories VALUES ('Widget', 'Hardware')");
        db.execute("INSERT INTO categories VALUES ('Gadget', 'Electronics')");
        db.execute("INSERT INTO categories VALUES ('Doohickey', 'Misc')");

        ResultSet rs = db.execute(
                "SELECT c.name, o.product, cat.category FROM customers c " +
                "JOIN orders o ON c.id = o.customer_id " +
                "JOIN categories cat ON o.product = cat.product");
        assertThat(rs.rows()).hasSize(3);
    }

    @Test
    void testLeftJoinNoMatch() {
        // Order 104 has customer_id=9 which doesn't exist
        ResultSet rs = db.execute(
                "SELECT o.product, c.name FROM orders o LEFT JOIN customers c ON o.customer_id = c.id");
        assertThat(rs.rows()).hasSize(4);
        boolean foundDoohickey = false;
        for (String[] row : rs.rows()) {
            if ("Doohickey".equals(row[0])) {
                assertThat(row[1]).isNull();
                foundDoohickey = true;
            }
        }
        assertThat(foundDoohickey).isTrue();
    }

    @Test
    void testJoinEmptyResult() {
        db.execute("CREATE TABLE empty_table (id int4, val varchar)");
        ResultSet rs = db.execute(
                "SELECT c.name, e.val FROM customers c JOIN empty_table e ON c.id = e.id");
        assertThat(rs.rows()).isEmpty();
    }

    @Test
    void testInnerJoinResultCount() {
        // Verify the exact rows for inner join
        ResultSet rs = db.execute(
                "SELECT c.name, o.amount FROM customers c JOIN orders o ON c.id = o.customer_id ORDER BY amount");
        assertThat(rs.rows()).hasSize(3);
        // Ordered by amount (string order): 100, 150, 200
        assertThat(rs.rows().get(0)[1]).isEqualTo("100");
    }
}
