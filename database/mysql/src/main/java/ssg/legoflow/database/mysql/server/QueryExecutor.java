package ssg.legoflow.database.mysql.server;

import ssg.legoflow.database.mysql.protocol.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Executes SQL queries against the in-memory database.
 *
 * <p>Supports a subset of SQL: CREATE TABLE, INSERT, SELECT (with JOIN,
 * WHERE, GROUP BY, HAVING, ORDER BY, LIMIT), UPDATE, DELETE, SHOW TABLES,
 * SHOW DATABASES, USE, and transaction statements (BEGIN/COMMIT/ROLLBACK).
 *
 * <p>The parser uses a clause-based approach: the SQL string is split into
 * keyword-delimited clauses (SELECT, FROM, JOIN, WHERE, GROUP BY, HAVING,
 * ORDER BY, LIMIT) which are parsed independently.
 *
 * @since 1.0.0
 */
public class QueryExecutor {

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?(\\w+)`?\\s*\\((.+)\\)");

    private static final Pattern INSERT = Pattern.compile(
            "(?i)INSERT\\s+INTO\\s+`?(\\w+)`?\\s*\\(([^)]+)\\)\\s+VALUES\\s*\\(([^)]+)\\)");

    private static final Pattern UPDATE = Pattern.compile(
            "(?i)UPDATE\\s+`?(\\w+)`?\\s+SET\\s+`?(\\w+)`?\\s*=\\s*(?:'([^']*)'|(\\d+))(?:\\s+WHERE\\s+`?(\\w+)`?\\s*=\\s*(?:'([^']*)'|(\\d+)))?");

    private static final Pattern DELETE = Pattern.compile(
            "(?i)DELETE\\s+FROM\\s+`?(\\w+)`?(?:\\s+WHERE\\s+`?(\\w+)`?\\s*=\\s*(?:'([^']*)'|(\\d+)))?");

    private static final Pattern DROP_TABLE = Pattern.compile(
            "(?i)DROP\\s+TABLE\\s+(?:IF\\s+EXISTS\\s+)?`?(\\w+)`?");

    private static final Pattern SHOW_TABLES = Pattern.compile("(?i)SHOW\\s+TABLES");
    private static final Pattern SHOW_DATABASES = Pattern.compile("(?i)SHOW\\s+DATABASES");
    private static final Pattern SELECT_VERSION = Pattern.compile("(?i)SELECT\\s+VERSION\\(\\)");
    private static final Pattern SELECT_DATABASE = Pattern.compile("(?i)SELECT\\s+DATABASE\\(\\)");

    private final Map<String, InMemoryDatabase> databases;

    /**
     * Creates a new query executor.
     *
     * @param databases the available databases
     */
    public QueryExecutor(Map<String, InMemoryDatabase> databases) {
        this.databases = databases;
    }

    /**
     * Result of executing a query.
     */
    public sealed interface QueryResult {
        /** OK result (INSERT, UPDATE, DELETE, CREATE, DROP). */
        record Ok(long affectedRows, long lastInsertId) implements QueryResult {}

        /** Result set (SELECT, SHOW). */
        record ResultSet(List<ColumnDefinition> columns, List<List<String>> rows) implements QueryResult {}

        /** Error result. */
        record Error(int errorCode, String sqlState, String message) implements QueryResult {}
    }

    /**
     * Executes a SQL query.
     *
     * @param sql the SQL query
     * @param currentDatabase the currently selected database (may be null)
     * @return the query result
     */
    public QueryResult execute(String sql, String currentDatabase) {
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }

        // SELECT VERSION()
        var versionMatcher = SELECT_VERSION.matcher(trimmed);
        if (versionMatcher.matches()) {
            var col = ColumnDefinition.of("VERSION()", ColumnType.VAR_STRING, 20);
            return new QueryResult.ResultSet(List.of(col),
                    List.of(List.of(HandshakeV10.DEFAULT_SERVER_VERSION)));
        }

        // SELECT DATABASE()
        var dbMatcher = SELECT_DATABASE.matcher(trimmed);
        if (dbMatcher.matches()) {
            var col = ColumnDefinition.of("DATABASE()", ColumnType.VAR_STRING, 64);
            return new QueryResult.ResultSet(List.of(col),
                    List.of(List.of(currentDatabase != null ? currentDatabase : "")));
        }

        // SHOW DATABASES
        var showDbMatcher = SHOW_DATABASES.matcher(trimmed);
        if (showDbMatcher.matches()) {
            var col = ColumnDefinition.of("Database", ColumnType.VAR_STRING, 64);
            var rows = new ArrayList<List<String>>();
            for (var dbName : databases.keySet()) {
                rows.add(List.of(dbName));
            }
            return new QueryResult.ResultSet(List.of(col), rows);
        }

        // SHOW TABLES
        var showTablesMatcher = SHOW_TABLES.matcher(trimmed);
        if (showTablesMatcher.matches()) {
            var db = getDatabase(currentDatabase);
            if (db == null) return noDbError();

            var col = ColumnDefinition.of("Tables_in_" + currentDatabase, ColumnType.VAR_STRING, 64);
            var rows = new ArrayList<List<String>>();
            for (var tableName : db.getTableNames()) {
                rows.add(List.of(tableName));
            }
            return new QueryResult.ResultSet(List.of(col), rows);
        }

        // CREATE TABLE
        var createMatcher = CREATE_TABLE.matcher(trimmed);
        if (createMatcher.matches()) {
            return executeCreate(createMatcher.group(1), createMatcher.group(2), currentDatabase);
        }

        // DROP TABLE
        var dropMatcher = DROP_TABLE.matcher(trimmed);
        if (dropMatcher.matches()) {
            return executeDrop(dropMatcher.group(1), currentDatabase);
        }

        // INSERT
        var insertMatcher = INSERT.matcher(trimmed);
        if (insertMatcher.matches()) {
            return executeInsert(insertMatcher.group(1),
                    insertMatcher.group(2), insertMatcher.group(3), currentDatabase);
        }

        // SELECT (with JOIN, WHERE, GROUP BY, HAVING, ORDER BY, LIMIT)
        if (trimmed.toUpperCase().startsWith("SELECT")) {
            return executeAdvancedSelect(trimmed, currentDatabase);
        }

        // UPDATE
        var updateMatcher = UPDATE.matcher(trimmed);
        if (updateMatcher.matches()) {
            String table = updateMatcher.group(1);
            String setCol = updateMatcher.group(2);
            String setVal = updateMatcher.group(3) != null ? updateMatcher.group(3) : updateMatcher.group(4);
            String whereCol = updateMatcher.group(5);
            String whereVal = updateMatcher.group(6) != null ? updateMatcher.group(6) : updateMatcher.group(7);
            return executeUpdate(table, setCol, setVal, whereCol, whereVal, currentDatabase);
        }

        // DELETE
        var deleteMatcher = DELETE.matcher(trimmed);
        if (deleteMatcher.matches()) {
            String table = deleteMatcher.group(1);
            String whereCol = deleteMatcher.group(2);
            String whereVal = deleteMatcher.group(3) != null ? deleteMatcher.group(3) : deleteMatcher.group(4);
            return executeDelete(table, whereCol, whereVal, currentDatabase);
        }

        // Transaction statements — acknowledged but actual rollback is handled by ClientSession
        String upperTrimmed = trimmed.toUpperCase();
        if (upperTrimmed.equals("BEGIN") || upperTrimmed.equals("START TRANSACTION")) {
            return new QueryResult.Ok(0, 0);
        }
        if (upperTrimmed.equals("COMMIT")) {
            return new QueryResult.Ok(0, 0);
        }
        if (upperTrimmed.equals("ROLLBACK")) {
            return new QueryResult.Ok(0, 0);
        }

        return new QueryResult.Error(1064, "42000", "Unsupported SQL: " + trimmed);
    }

    /**
     * Writes a query result to the output stream.
     *
     * @param out the output stream
     * @param result the query result
     * @param capabilities the negotiated capabilities
     * @param seqId the starting sequence ID
     * @return the next sequence ID
     * @throws IOException if an I/O error occurs
     */
    public int writeResult(OutputStream out, QueryResult result, int capabilities, int seqId)
            throws IOException {
        return switch (result) {
            case QueryResult.Ok ok -> {
                var packet = OkPacket.ok(ok.affectedRows(), ok.lastInsertId());
                new MysqlPacket(seqId++, packet.encode(capabilities)).writeTo(out);
                yield seqId;
            }
            case QueryResult.ResultSet rs -> ResultSetWriter.writeTextResultSet(
                    out, rs.columns(), rs.rows(), capabilities, seqId);
            case QueryResult.Error err -> {
                var packet = new ErrPacket(err.errorCode(), err.sqlState(), err.message());
                new MysqlPacket(seqId++, packet.encode(capabilities)).writeTo(out);
                yield seqId;
            }
        };
    }

    // ========================== Advanced SELECT Parser ==========================

    /**
     * Parsed representation of a SELECT query.
     */
    private record ParsedSelect(
            List<SelectColumn> columns,
            String fromTable,
            String fromAlias,
            List<JoinClause> joins,
            List<WhereCondition> whereConditions,
            List<String> groupByColumns,
            List<WhereCondition> havingConditions,
            List<OrderByColumn> orderByColumns,
            int limit,
            int offset
    ) {}

    /**
     * A column in the SELECT clause, possibly an aggregate function.
     */
    private record SelectColumn(
            String expression,   // raw expression: "t1.col", "COUNT(*)", "col"
            String alias,        // AS alias, or null
            String aggregateFunc, // COUNT, SUM, AVG, MIN, MAX, or null
            String aggregateArg   // the argument to the aggregate, e.g., "*" or "col"
    ) {}

    /**
     * A JOIN clause.
     */
    private record JoinClause(
            String type,       // "INNER" or "LEFT"
            String table,
            String alias,
            String leftCol,    // ON left side
            String rightCol    // ON right side
    ) {}

    /**
     * A single WHERE condition.
     */
    private record WhereCondition(
            String column,
            String operator,       // =, !=, <>, <, >, <=, >=, LIKE, IS NULL, IS NOT NULL, IN
            String value,          // single value or null for IS NULL/IS NOT NULL
            List<String> inValues, // for IN operator
            String connector       // AND or OR (null for first condition)
    ) {}

    /**
     * An ORDER BY column.
     */
    private record OrderByColumn(String column, boolean ascending) {}

    private QueryResult executeAdvancedSelect(String sql, String currentDatabase) {
        try {
            var parsed = parseSelect(sql);
            return executeSelectParsed(parsed, currentDatabase);
        } catch (Exception e) {
            return new QueryResult.Error(1064, "42000", "Parse error: " + e.getMessage());
        }
    }

    private ParsedSelect parseSelect(String sql) {
        // Tokenize the SQL into clause segments
        String upper = sql.toUpperCase();

        // Find clause boundaries
        int selectIdx = upper.indexOf("SELECT");
        int fromIdx = findKeyword(upper, "FROM", selectIdx + 6);
        int whereIdx = findKeyword(upper, "WHERE", fromIdx >= 0 ? fromIdx : selectIdx + 6);
        int groupByIdx = findKeyword(upper, "GROUP BY", fromIdx >= 0 ? fromIdx : selectIdx + 6);
        int havingIdx = findKeyword(upper, "HAVING", groupByIdx >= 0 ? groupByIdx : (fromIdx >= 0 ? fromIdx : selectIdx));
        int orderByIdx = findKeyword(upper, "ORDER BY", havingIdx >= 0 ? havingIdx : (groupByIdx >= 0 ? groupByIdx : (whereIdx >= 0 ? whereIdx : (fromIdx >= 0 ? fromIdx : selectIdx))));
        int limitIdx = findKeyword(upper, "LIMIT", orderByIdx >= 0 ? orderByIdx : (havingIdx >= 0 ? havingIdx : (groupByIdx >= 0 ? groupByIdx : (whereIdx >= 0 ? whereIdx : (fromIdx >= 0 ? fromIdx : selectIdx)))));

        // Extract SELECT columns
        int selectEnd = fromIdx >= 0 ? fromIdx : sql.length();
        String selectClause = sql.substring(selectIdx + 6, selectEnd).trim();
        List<SelectColumn> columns = parseSelectColumns(selectClause);

        // Extract FROM and JOIN
        String fromTable = null;
        String fromAlias = null;
        List<JoinClause> joins = new ArrayList<>();

        if (fromIdx >= 0) {
            int fromEnd = firstOf(whereIdx, groupByIdx, havingIdx, orderByIdx, limitIdx, sql.length());
            String fromClause = sql.substring(fromIdx + 4, fromEnd).trim();
            var fromJoin = parseFromAndJoins(fromClause);
            fromTable = fromJoin.tableName;
            fromAlias = fromJoin.alias;
            joins = fromJoin.joins;
        }

        // Extract WHERE
        List<WhereCondition> whereConditions = new ArrayList<>();
        if (whereIdx >= 0) {
            int whereEnd = firstOf(groupByIdx, havingIdx, orderByIdx, limitIdx, sql.length());
            String whereClause = sql.substring(whereIdx + 5, whereEnd).trim();
            whereConditions = parseWhereConditions(whereClause);
        }

        // Extract GROUP BY
        List<String> groupByColumns = new ArrayList<>();
        if (groupByIdx >= 0) {
            int gbEnd = firstOf(havingIdx, orderByIdx, limitIdx, sql.length());
            String gbClause = sql.substring(groupByIdx + 8, gbEnd).trim();
            for (String col : gbClause.split(",")) {
                groupByColumns.add(col.trim().replace("`", ""));
            }
        }

        // Extract HAVING
        List<WhereCondition> havingConditions = new ArrayList<>();
        if (havingIdx >= 0) {
            int havEnd = firstOf(orderByIdx, limitIdx, sql.length());
            String havClause = sql.substring(havingIdx + 6, havEnd).trim();
            havingConditions = parseWhereConditions(havClause);
        }

        // Extract ORDER BY
        List<OrderByColumn> orderByColumns = new ArrayList<>();
        if (orderByIdx >= 0) {
            int obEnd = firstOf(limitIdx, sql.length());
            String obClause = sql.substring(orderByIdx + 8, obEnd).trim();
            orderByColumns = parseOrderBy(obClause);
        }

        // Extract LIMIT / OFFSET
        int limit = -1;
        int offset = 0;
        if (limitIdx >= 0) {
            String limClause = sql.substring(limitIdx + 5).trim();
            var limParts = parseLimitOffset(limClause);
            limit = limParts[0];
            offset = limParts[1];
        }

        return new ParsedSelect(columns, fromTable, fromAlias, joins, whereConditions,
                groupByColumns, havingConditions, orderByColumns, limit, offset);
    }

    /**
     * Finds a keyword at word boundary (not inside quotes, identifiers, or other words).
     * Treats underscore as a word character (part of identifiers).
     */
    private int findKeyword(String upper, String keyword, int startFrom) {
        int idx = startFrom;
        while (idx < upper.length()) {
            idx = upper.indexOf(keyword, idx);
            if (idx < 0) return -1;
            // Check word boundary before (letter, digit, or underscore means inside a word)
            if (idx > 0) {
                char before = upper.charAt(idx - 1);
                if (Character.isLetterOrDigit(before) || before == '_') {
                    idx += keyword.length();
                    continue;
                }
            }
            // Check word boundary after
            int after = idx + keyword.length();
            if (after < upper.length()) {
                char afterChar = upper.charAt(after);
                if (Character.isLetterOrDigit(afterChar) || afterChar == '_') {
                    idx += keyword.length();
                    continue;
                }
            }
            return idx;
        }
        return -1;
    }

    private int firstOf(int... values) {
        int min = Integer.MAX_VALUE;
        for (int v : values) {
            if (v >= 0 && v < min) min = v;
        }
        return min;
    }

    // ========================== SELECT column parsing ==========================

    private static final Pattern AGGREGATE_PATTERN = Pattern.compile(
            "(?i)(COUNT|SUM|AVG|MIN|MAX)\\s*\\(\\s*([^)]+)\\s*\\)");

    private List<SelectColumn> parseSelectColumns(String clause) {
        var result = new ArrayList<SelectColumn>();
        // Split by comma but respect parentheses
        var parts = splitRespectingParentheses(clause);
        for (String part : parts) {
            String trimmed = part.trim();
            // Check for alias: ... AS alias
            String alias = null;
            String expression = trimmed;
            var asMatch = Pattern.compile("(?i)(.+?)\\s+AS\\s+`?(\\w+)`?$").matcher(trimmed);
            if (asMatch.matches()) {
                expression = asMatch.group(1).trim();
                alias = asMatch.group(2);
            }

            // Check for aggregate function
            var aggMatch = AGGREGATE_PATTERN.matcher(expression);
            if (aggMatch.matches()) {
                String func = aggMatch.group(1).toUpperCase();
                String arg = aggMatch.group(2).trim();
                result.add(new SelectColumn(expression, alias, func, arg));
            } else {
                result.add(new SelectColumn(expression.replace("`", ""), alias, null, null));
            }
        }
        return result;
    }

    private List<String> splitRespectingParentheses(String str) {
        var result = new ArrayList<String>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                result.add(str.substring(start, i));
                start = i + 1;
            }
        }
        result.add(str.substring(start));
        return result;
    }

    // ========================== FROM / JOIN parsing ==========================

    private record FromResult(String tableName, String alias, List<JoinClause> joins) {}

    private FromResult parseFromAndJoins(String fromClause) {
        String upper = fromClause.toUpperCase();
        List<JoinClause> joins = new ArrayList<>();

        // Find first JOIN keyword position
        int firstJoinIdx = Integer.MAX_VALUE;
        for (String joinKw : List.of("INNER JOIN", "LEFT JOIN", "JOIN")) {
            int idx = findKeyword(upper, joinKw, 0);
            if (idx >= 0 && idx < firstJoinIdx) {
                firstJoinIdx = idx;
            }
        }

        // Extract the base table (before any JOIN)
        String baseTableStr;
        if (firstJoinIdx < Integer.MAX_VALUE) {
            baseTableStr = fromClause.substring(0, firstJoinIdx).trim();
        } else {
            baseTableStr = fromClause.trim();
        }

        var baseParts = parseTableRef(baseTableStr);
        String tableName = baseParts[0];
        String alias = baseParts[1];

        // Parse JOINs
        if (firstJoinIdx < Integer.MAX_VALUE) {
            String joinsPart = fromClause.substring(firstJoinIdx);
            joins = parseJoins(joinsPart);
        }

        return new FromResult(tableName, alias, joins);
    }

    private String[] parseTableRef(String ref) {
        String clean = ref.trim().replace("`", "");
        String[] parts = clean.split("\\s+");
        if (parts.length >= 2 && !parts[1].equalsIgnoreCase("ON") &&
                !parts[1].equalsIgnoreCase("JOIN") && !parts[1].equalsIgnoreCase("INNER") &&
                !parts[1].equalsIgnoreCase("LEFT")) {
            return new String[]{parts[0], parts[1]};
        }
        return new String[]{parts[0], null};
    }

    private List<JoinClause> parseJoins(String joinsPart) {
        var result = new ArrayList<JoinClause>();
        String upper = joinsPart.toUpperCase();

        // Find each JOIN keyword and its range. Check longer keywords first to
        // avoid matching "JOIN" inside "LEFT JOIN" or "INNER JOIN".
        var joinPositions = new ArrayList<int[]>(); // [start, kwLen, isLeft]
        int pos = 0;
        while (pos < upper.length()) {
            int leftJoinIdx = findKeyword(upper, "LEFT JOIN", pos);
            int innerJoinIdx = findKeyword(upper, "INNER JOIN", pos);
            int plainJoinIdx = findKeyword(upper, "JOIN", pos);

            // Find the earliest match, preferring longer keywords at same position
            int nextIdx = Integer.MAX_VALUE;
            String type = null;
            int kwLen = 0;

            // Collect all candidates
            record Candidate(int idx, String type, int kwLen) {}
            var candidates = new ArrayList<Candidate>();
            if (leftJoinIdx >= 0) candidates.add(new Candidate(leftJoinIdx, "LEFT", 9));
            if (innerJoinIdx >= 0) candidates.add(new Candidate(innerJoinIdx, "INNER", 10));
            if (plainJoinIdx >= 0) candidates.add(new Candidate(plainJoinIdx, "INNER", 4));

            // Sort: earliest position first, then longest keyword first
            candidates.sort((a, b) -> {
                int cmp = Integer.compare(a.idx(), b.idx());
                if (cmp != 0) return cmp;
                return Integer.compare(b.kwLen(), a.kwLen()); // longer first
            });

            // Take the first candidate, but skip plain "JOIN" if it's part of LEFT/INNER JOIN
            for (var c : candidates) {
                // Check if this plain JOIN is part of a LEFT/INNER JOIN
                if (c.kwLen() == 4) { // plain JOIN
                    boolean isPartOfLonger = false;
                    for (var other : candidates) {
                        if (other.kwLen() > 4 && c.idx() > other.idx() && c.idx() < other.idx() + other.kwLen()) {
                            isPartOfLonger = true;
                            break;
                        }
                    }
                    if (isPartOfLonger) continue;
                }
                nextIdx = c.idx();
                type = c.type();
                kwLen = c.kwLen();
                break;
            }

            if (nextIdx == Integer.MAX_VALUE) break;

            joinPositions.add(new int[]{nextIdx, kwLen, type.equals("LEFT") ? 1 : 0});
            pos = nextIdx + kwLen;
        }

        for (int i = 0; i < joinPositions.size(); i++) {
            int[] jp = joinPositions.get(i);
            int start = jp[0] + jp[1];
            int end = (i + 1 < joinPositions.size()) ? joinPositions.get(i + 1)[0] : joinsPart.length();
            String joinType = jp[2] == 1 ? "LEFT" : "INNER";

            String joinBody = joinsPart.substring(start, end).trim();

            // Parse: table [alias] ON left = right
            int onIdx = findKeyword(joinBody.toUpperCase(), "ON", 0);
            if (onIdx < 0) continue;

            String tableRef = joinBody.substring(0, onIdx).trim();
            String onClause = joinBody.substring(onIdx + 2).trim();

            var tableParts = parseTableRef(tableRef);
            var onParts = parseOnClause(onClause);

            result.add(new JoinClause(joinType, tableParts[0], tableParts[1], onParts[0], onParts[1]));
        }

        return result;
    }

    private String[] parseOnClause(String on) {
        // Parse: left.col = right.col or left = right
        String clean = on.trim().replace("`", "");
        String[] parts = clean.split("\\s*=\\s*", 2);
        return new String[]{parts[0].trim(), parts.length > 1 ? parts[1].trim() : ""};
    }

    // ========================== WHERE condition parsing ==========================

    private List<WhereCondition> parseWhereConditions(String clause) {
        var conditions = new ArrayList<WhereCondition>();
        // Split by AND/OR, preserving the connectors
        var tokens = tokenizeWhere(clause);
        String connector = null;

        for (String token : tokens) {
            String upperToken = token.trim().toUpperCase();
            if (upperToken.equals("AND") || upperToken.equals("OR")) {
                connector = upperToken;
                continue;
            }
            var condition = parseSingleCondition(token.trim(), connector);
            if (condition != null) {
                conditions.add(condition);
            }
            connector = null;
        }
        return conditions;
    }

    private List<String> tokenizeWhere(String clause) {
        var tokens = new ArrayList<String>();
        String upper = clause.toUpperCase();
        int pos = 0;

        while (pos < clause.length()) {
            // Skip whitespace
            while (pos < clause.length() && clause.charAt(pos) == ' ') pos++;
            if (pos >= clause.length()) break;

            // Check for AND/OR
            if (upper.startsWith("AND ", pos) && (pos == 0 || clause.charAt(pos - 1) == ' ')) {
                tokens.add("AND");
                pos += 4;
                continue;
            }
            if (upper.startsWith("OR ", pos) && (pos == 0 || clause.charAt(pos - 1) == ' ')) {
                tokens.add("OR");
                pos += 3;
                continue;
            }

            // Read until next AND/OR
            int start = pos;
            int depth = 0;
            while (pos < clause.length()) {
                char c = clause.charAt(pos);
                if (c == '(') depth++;
                else if (c == ')') depth--;
                if (depth == 0) {
                    String remaining = upper.substring(pos);
                    if (remaining.startsWith(" AND ") || remaining.startsWith(" OR ")) {
                        break;
                    }
                }
                pos++;
            }
            String token = clause.substring(start, pos).trim();
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private WhereCondition parseSingleCondition(String expr, String connector) {
        String upper = expr.toUpperCase().trim();

        // IS NOT NULL
        var isNotNullMatch = Pattern.compile("(?i)(.+?)\\s+IS\\s+NOT\\s+NULL").matcher(expr);
        if (isNotNullMatch.matches()) {
            return new WhereCondition(isNotNullMatch.group(1).trim().replace("`", ""),
                    "IS NOT NULL", null, null, connector);
        }

        // IS NULL
        var isNullMatch = Pattern.compile("(?i)(.+?)\\s+IS\\s+NULL").matcher(expr);
        if (isNullMatch.matches()) {
            return new WhereCondition(isNullMatch.group(1).trim().replace("`", ""),
                    "IS NULL", null, null, connector);
        }

        // IN (val1, val2, ...)
        var inMatch = Pattern.compile("(?i)(.+?)\\s+IN\\s*\\(([^)]+)\\)").matcher(expr);
        if (inMatch.matches()) {
            String col = inMatch.group(1).trim().replace("`", "");
            String valList = inMatch.group(2);
            var values = new ArrayList<String>();
            for (String v : valList.split(",")) {
                values.add(unquote(v.trim()));
            }
            return new WhereCondition(col, "IN", null, values, connector);
        }

        // LIKE
        var likeMatch = Pattern.compile("(?i)(.+?)\\s+LIKE\\s+(.+)").matcher(expr);
        if (likeMatch.matches()) {
            return new WhereCondition(likeMatch.group(1).trim().replace("`", ""),
                    "LIKE", unquote(likeMatch.group(2).trim()), null, connector);
        }

        // Comparison operators: <=, >=, !=, <>, <, >, =
        for (String op : List.of("<=", ">=", "!=", "<>", "<", ">", "=")) {
            int opIdx = expr.indexOf(op);
            if (opIdx >= 0) {
                String col = expr.substring(0, opIdx).trim().replace("`", "");
                String val = unquote(expr.substring(opIdx + op.length()).trim());
                return new WhereCondition(col, op, val, null, connector);
            }
        }

        // Aggregate comparison in HAVING: COUNT(*) > 1
        var aggCompMatch = Pattern.compile("(?i)(\\w+\\([^)]*\\))\\s*(<=|>=|!=|<>|<|>|=)\\s*(.+)").matcher(expr);
        if (aggCompMatch.matches()) {
            return new WhereCondition(aggCompMatch.group(1).trim(),
                    aggCompMatch.group(2).trim(),
                    unquote(aggCompMatch.group(3).trim()), null, connector);
        }

        return null;
    }

    private String unquote(String val) {
        if (val == null) return null;
        val = val.trim();
        if (val.startsWith("'") && val.endsWith("'") && val.length() >= 2) {
            return val.substring(1, val.length() - 1);
        }
        if ("NULL".equalsIgnoreCase(val)) {
            return null;
        }
        return val;
    }

    // ========================== ORDER BY parsing ==========================

    private List<OrderByColumn> parseOrderBy(String clause) {
        var result = new ArrayList<OrderByColumn>();
        for (String part : clause.split(",")) {
            String trimmed = part.trim().replace("`", "");
            String[] words = trimmed.split("\\s+");
            String col = words[0];
            boolean asc = true;
            if (words.length > 1 && words[1].equalsIgnoreCase("DESC")) {
                asc = false;
            }
            result.add(new OrderByColumn(col, asc));
        }
        return result;
    }

    // ========================== LIMIT/OFFSET parsing ==========================

    private int[] parseLimitOffset(String clause) {
        String upper = clause.toUpperCase().trim();
        int limit = -1;
        int offset = 0;

        int offsetIdx = upper.indexOf("OFFSET");
        if (offsetIdx >= 0) {
            limit = Integer.parseInt(upper.substring(0, offsetIdx).trim());
            offset = Integer.parseInt(upper.substring(offsetIdx + 6).trim());
        } else if (upper.contains(",")) {
            // LIMIT offset, count syntax
            String[] parts = upper.split(",");
            offset = Integer.parseInt(parts[0].trim());
            limit = Integer.parseInt(parts[1].trim());
        } else {
            limit = Integer.parseInt(upper.trim());
        }
        return new int[]{limit, offset};
    }

    // ========================== SELECT execution ==========================

    private QueryResult executeSelectParsed(ParsedSelect parsed, String currentDatabase) {
        var db = getDatabase(currentDatabase);
        if (db == null) return noDbError();

        // Check for aggregates without FROM (e.g., SELECT 1)
        if (parsed.fromTable() == null) {
            // Only support aggregate-only queries without FROM
            return new QueryResult.Error(1064, "42000", "No FROM clause");
        }

        var baseTable = db.getTable(parsed.fromTable());
        if (baseTable == null) return tableNotFound(parsed.fromTable());

        // Build alias-to-table mapping
        var tableAliases = new LinkedHashMap<String, InMemoryDatabase.Table>();
        var tableNames = new LinkedHashMap<String, String>(); // alias -> real name
        String baseAlias = parsed.fromAlias() != null ? parsed.fromAlias() : parsed.fromTable();
        tableAliases.put(baseAlias, baseTable);
        tableNames.put(baseAlias, parsed.fromTable());
        // Also map by real name
        if (parsed.fromAlias() != null) {
            tableAliases.put(parsed.fromTable(), baseTable);
            tableNames.put(parsed.fromTable(), parsed.fromTable());
        }

        for (var join : parsed.joins()) {
            var joinTable = db.getTable(join.table());
            if (joinTable == null) return tableNotFound(join.table());
            String jAlias = join.alias() != null ? join.alias() : join.table();
            tableAliases.put(jAlias, joinTable);
            tableNames.put(jAlias, join.table());
            if (join.alias() != null) {
                tableAliases.put(join.table(), joinTable);
                tableNames.put(join.table(), join.table());
            }
        }

        // Get base rows, each row is Map with qualified keys: "alias.col" -> value
        List<Map<String, String>> rows = new ArrayList<>();
        for (var baseRow : baseTable.selectAll()) {
            var qualifiedRow = new LinkedHashMap<String, String>();
            for (var entry : baseRow.entrySet()) {
                qualifiedRow.put(baseAlias + "." + entry.getKey(), entry.getValue());
                // Also store unqualified
                qualifiedRow.put(entry.getKey(), entry.getValue());
            }
            rows.add(qualifiedRow);
        }

        // Execute JOINs (nested loop)
        for (var join : parsed.joins()) {
            var joinTable = tableAliases.get(join.alias() != null ? join.alias() : join.table());
            String jAlias = join.alias() != null ? join.alias() : join.table();
            var newRows = new ArrayList<Map<String, String>>();

            for (var leftRow : rows) {
                boolean matched = false;
                for (var rightRow : joinTable.selectAll()) {
                    // Qualify right row
                    var qualifiedRight = new LinkedHashMap<String, String>();
                    for (var entry : rightRow.entrySet()) {
                        qualifiedRight.put(jAlias + "." + entry.getKey(), entry.getValue());
                    }

                    // Check ON condition
                    String leftVal = resolveColumn(leftRow, join.leftCol());
                    String rightVal = resolveColumn(qualifiedRight, join.rightCol());
                    // Also try resolving from left row if right col references a left table
                    if (rightVal == null && !join.rightCol().contains(".")) {
                        rightVal = resolveColumn(qualifiedRight, jAlias + "." + join.rightCol());
                    }
                    // Try combined row for resolution
                    var combined = new LinkedHashMap<>(leftRow);
                    combined.putAll(qualifiedRight);
                    leftVal = resolveColumn(combined, join.leftCol());
                    rightVal = resolveColumn(combined, join.rightCol());

                    if (leftVal != null && leftVal.equals(rightVal)) {
                        var mergedRow = new LinkedHashMap<>(leftRow);
                        mergedRow.putAll(qualifiedRight);
                        // Add unqualified right columns (may overwrite, but that's expected for SELECT *)
                        for (var entry : rightRow.entrySet()) {
                            if (!mergedRow.containsKey(entry.getKey())) {
                                mergedRow.put(entry.getKey(), entry.getValue());
                            }
                        }
                        newRows.add(mergedRow);
                        matched = true;
                    }
                }
                if (!matched && "LEFT".equals(join.type())) {
                    // Add left row with NULLs for right columns
                    var mergedRow = new LinkedHashMap<>(leftRow);
                    for (var col : joinTable.columnNames()) {
                        mergedRow.put(jAlias + "." + col, null);
                        if (!mergedRow.containsKey(col)) {
                            mergedRow.put(col, null);
                        }
                    }
                    newRows.add(mergedRow);
                }
            }
            rows = newRows;
        }

        // Apply WHERE filter
        if (!parsed.whereConditions().isEmpty()) {
            rows = filterRows(rows, parsed.whereConditions());
        }

        // Determine if this is an aggregate query
        boolean hasAggregates = parsed.columns().stream().anyMatch(c -> c.aggregateFunc() != null);
        boolean hasGroupBy = !parsed.groupByColumns().isEmpty();

        if (hasAggregates || hasGroupBy) {
            return executeAggregateQuery(parsed, rows, db, currentDatabase);
        }

        // Apply ORDER BY
        if (!parsed.orderByColumns().isEmpty()) {
            rows = sortRows(rows, parsed.orderByColumns());
        }

        // Apply LIMIT / OFFSET
        if (parsed.limit() >= 0) {
            int start = Math.min(parsed.offset(), rows.size());
            int end = Math.min(start + parsed.limit(), rows.size());
            rows = new ArrayList<>(rows.subList(start, end));
        }

        // Project columns
        return projectResult(parsed, rows, db, baseTable, baseAlias, tableAliases, currentDatabase);
    }

    private String resolveColumn(Map<String, String> row, String colRef) {
        colRef = colRef.trim().replace("`", "");
        // Try direct lookup first
        if (row.containsKey(colRef)) {
            return row.get(colRef);
        }
        // Try case-insensitive
        for (var entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(colRef)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean hasKeyForColumn(Map<String, String> row, String colRef) {
        colRef = colRef.trim().replace("`", "");
        if (row.containsKey(colRef)) return true;
        for (var key : row.keySet()) {
            if (key.equalsIgnoreCase(colRef)) return true;
        }
        return false;
    }

    // ========================== WHERE filtering ==========================

    private List<Map<String, String>> filterRows(List<Map<String, String>> rows,
                                                   List<WhereCondition> conditions) {
        var result = new ArrayList<Map<String, String>>();
        for (var row : rows) {
            if (evaluateConditions(row, conditions)) {
                result.add(row);
            }
        }
        return result;
    }

    private boolean evaluateConditions(Map<String, String> row, List<WhereCondition> conditions) {
        if (conditions.isEmpty()) return true;
        boolean result = evaluateSingle(row, conditions.get(0));
        for (int i = 1; i < conditions.size(); i++) {
            var cond = conditions.get(i);
            boolean val = evaluateSingle(row, cond);
            if ("OR".equals(cond.connector())) {
                result = result || val;
            } else {
                // AND (default)
                result = result && val;
            }
        }
        return result;
    }

    private boolean evaluateSingle(Map<String, String> row, WhereCondition cond) {
        String colRef = cond.column();
        String rowVal = resolveColumn(row, colRef);

        return switch (cond.operator()) {
            case "IS NULL" -> rowVal == null;
            case "IS NOT NULL" -> rowVal != null;
            case "IN" -> {
                if (rowVal == null) yield false;
                yield cond.inValues().contains(rowVal);
            }
            case "LIKE" -> {
                if (rowVal == null) yield false;
                yield matchLike(rowVal, cond.value());
            }
            case "=" -> Objects.equals(rowVal, cond.value());
            case "!=", "<>" -> !Objects.equals(rowVal, cond.value());
            case "<" -> compareValues(rowVal, cond.value()) < 0;
            case ">" -> compareValues(rowVal, cond.value()) > 0;
            case "<=" -> compareValues(rowVal, cond.value()) <= 0;
            case ">=" -> compareValues(rowVal, cond.value()) >= 0;
            default -> false;
        };
    }

    private boolean matchLike(String value, String pattern) {
        // Convert SQL LIKE pattern to regex
        StringBuilder regex = new StringBuilder("(?i)^");
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '%') {
                regex.append(".*");
            } else if (c == '_') {
                regex.append(".");
            } else if ("\\[](){}.*+?^$|".indexOf(c) >= 0) {
                regex.append("\\").append(c);
            } else {
                regex.append(c);
            }
        }
        regex.append("$");
        return value.matches(regex.toString());
    }

    private int compareValues(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        // Try numeric comparison
        try {
            double da = Double.parseDouble(a);
            double db = Double.parseDouble(b);
            return Double.compare(da, db);
        } catch (NumberFormatException e) {
            return a.compareTo(b);
        }
    }

    // ========================== ORDER BY sorting ==========================

    private List<Map<String, String>> sortRows(List<Map<String, String>> rows,
                                                 List<OrderByColumn> orderBy) {
        var sorted = new ArrayList<>(rows);
        sorted.sort((a, b) -> {
            for (var ob : orderBy) {
                String va = resolveColumn(a, ob.column());
                String vb = resolveColumn(b, ob.column());
                int cmp = compareValues(va, vb);
                if (!ob.ascending()) cmp = -cmp;
                if (cmp != 0) return cmp;
            }
            return 0;
        });
        return sorted;
    }

    // ========================== GROUP BY / Aggregates ==========================

    private QueryResult executeAggregateQuery(ParsedSelect parsed, List<Map<String, String>> rows,
                                                InMemoryDatabase db, String currentDatabase) {
        List<String> groupByCols = parsed.groupByColumns();
        boolean hasGroupBy = !groupByCols.isEmpty();

        // Group rows
        Map<String, List<Map<String, String>>> groups = new LinkedHashMap<>();
        if (hasGroupBy) {
            for (var row : rows) {
                StringBuilder key = new StringBuilder();
                for (String col : groupByCols) {
                    if (key.length() > 0) key.append("\0");
                    String val = resolveColumn(row, col);
                    key.append(val != null ? val : "NULL");
                }
                groups.computeIfAbsent(key.toString(), _ -> new ArrayList<>()).add(row);
            }
        } else {
            // All rows in one group
            groups.put("__ALL__", new ArrayList<>(rows));
        }

        // Build result columns
        var colDefs = new ArrayList<ColumnDefinition>();
        for (var selCol : parsed.columns()) {
            String colName = selCol.alias() != null ? selCol.alias() : selCol.expression();
            colDefs.add(ColumnDefinition.of(colName, ColumnType.VAR_STRING, 255));
        }

        // Compute results per group
        List<List<String>> resultRows = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            var groupRows = entry.getValue();
            var resultRow = new ArrayList<String>();

            for (var selCol : parsed.columns()) {
                if (selCol.aggregateFunc() != null) {
                    String aggResult = computeAggregate(selCol.aggregateFunc(), selCol.aggregateArg(), groupRows);
                    resultRow.add(aggResult);
                } else {
                    // Non-aggregate column (should be in GROUP BY)
                    String val = resolveColumn(groupRows.get(0), selCol.expression());
                    resultRow.add(val);
                }
            }
            resultRows.add(resultRow);
        }

        // Apply HAVING
        if (!parsed.havingConditions().isEmpty()) {
            resultRows = filterAggregateRows(resultRows, colDefs, parsed.havingConditions());
        }

        // Apply ORDER BY to aggregate results
        if (!parsed.orderByColumns().isEmpty()) {
            resultRows = sortAggregateRows(resultRows, colDefs, parsed.orderByColumns());
        }

        // Apply LIMIT / OFFSET
        if (parsed.limit() >= 0) {
            int start = Math.min(parsed.offset(), resultRows.size());
            int end = Math.min(start + parsed.limit(), resultRows.size());
            resultRows = new ArrayList<>(resultRows.subList(start, end));
        }

        return new QueryResult.ResultSet(colDefs, resultRows);
    }

    private String computeAggregate(String func, String arg, List<Map<String, String>> groupRows) {
        return switch (func) {
            case "COUNT" -> {
                if ("*".equals(arg.trim())) {
                    yield String.valueOf(groupRows.size());
                } else {
                    long count = groupRows.stream()
                            .filter(r -> resolveColumn(r, arg) != null)
                            .count();
                    yield String.valueOf(count);
                }
            }
            case "SUM" -> {
                double sum = 0;
                for (var row : groupRows) {
                    String val = resolveColumn(row, arg);
                    if (val != null) {
                        try { sum += Double.parseDouble(val); } catch (NumberFormatException ignored) {}
                    }
                }
                if (sum == Math.floor(sum) && !Double.isInfinite(sum)) {
                    yield String.valueOf((long) sum);
                }
                yield String.valueOf(sum);
            }
            case "AVG" -> {
                double sum = 0;
                int count = 0;
                for (var row : groupRows) {
                    String val = resolveColumn(row, arg);
                    if (val != null) {
                        try { sum += Double.parseDouble(val); count++; } catch (NumberFormatException ignored) {}
                    }
                }
                yield count > 0 ? String.valueOf(sum / count) : "0";
            }
            case "MIN" -> {
                String min = null;
                for (var row : groupRows) {
                    String val = resolveColumn(row, arg);
                    if (val != null) {
                        if (min == null || compareValues(val, min) < 0) min = val;
                    }
                }
                yield min;
            }
            case "MAX" -> {
                String max = null;
                for (var row : groupRows) {
                    String val = resolveColumn(row, arg);
                    if (val != null) {
                        if (max == null || compareValues(val, max) > 0) max = val;
                    }
                }
                yield max;
            }
            default -> "0";
        };
    }

    private List<List<String>> filterAggregateRows(List<List<String>> rows,
                                                     List<ColumnDefinition> colDefs,
                                                     List<WhereCondition> conditions) {
        var result = new ArrayList<List<String>>();
        for (var row : rows) {
            // Build a map of column name -> value for condition evaluation
            var rowMap = new LinkedHashMap<String, String>();
            for (int i = 0; i < colDefs.size(); i++) {
                rowMap.put(colDefs.get(i).name(), i < row.size() ? row.get(i) : null);
            }
            if (evaluateConditions(rowMap, conditions)) {
                result.add(row);
            }
        }
        return result;
    }

    private List<List<String>> sortAggregateRows(List<List<String>> rows,
                                                   List<ColumnDefinition> colDefs,
                                                   List<OrderByColumn> orderBy) {
        var sorted = new ArrayList<>(rows);
        sorted.sort((a, b) -> {
            for (var ob : orderBy) {
                int colIdx = findColumnIndex(colDefs, ob.column());
                String va = colIdx >= 0 && colIdx < a.size() ? a.get(colIdx) : null;
                String vb = colIdx >= 0 && colIdx < b.size() ? b.get(colIdx) : null;
                int cmp = compareValues(va, vb);
                if (!ob.ascending()) cmp = -cmp;
                if (cmp != 0) return cmp;
            }
            return 0;
        });
        return sorted;
    }

    private int findColumnIndex(List<ColumnDefinition> colDefs, String name) {
        for (int i = 0; i < colDefs.size(); i++) {
            if (colDefs.get(i).name().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    // ========================== Projection ==========================

    private QueryResult projectResult(ParsedSelect parsed, List<Map<String, String>> rows,
                                        InMemoryDatabase db, InMemoryDatabase.Table baseTable,
                                        String baseAlias,
                                        Map<String, InMemoryDatabase.Table> tableAliases,
                                        String currentDatabase) {
        // Build column definitions and project
        var colDefs = new ArrayList<ColumnDefinition>();
        var projectedCols = new ArrayList<String>(); // column references for projection

        for (var selCol : parsed.columns()) {
            if ("*".equals(selCol.expression().trim())) {
                // All columns from all tables
                for (var col : baseTable.columnNames()) {
                    String colName = col;
                    colDefs.add(ColumnDefinition.of(db.name(), parsed.fromTable(), colName,
                            baseTable.columnType(col) != null ? baseTable.columnType(col) : ColumnType.VAR_STRING, 255, 0));
                    projectedCols.add(baseAlias + "." + col);
                }
                for (var join : parsed.joins()) {
                    var jTable = tableAliases.get(join.alias() != null ? join.alias() : join.table());
                    String jAlias = join.alias() != null ? join.alias() : join.table();
                    for (var col : jTable.columnNames()) {
                        colDefs.add(ColumnDefinition.of(db.name(), join.table(), col,
                                jTable.columnType(col) != null ? jTable.columnType(col) : ColumnType.VAR_STRING, 255, 0));
                        projectedCols.add(jAlias + "." + col);
                    }
                }
            } else {
                String colName = selCol.alias() != null ? selCol.alias() : selCol.expression();
                colDefs.add(ColumnDefinition.of(colName, ColumnType.VAR_STRING, 255));
                projectedCols.add(selCol.expression());
            }
        }

        var resultRows = new ArrayList<List<String>>();
        for (var row : rows) {
            var resultRow = new ArrayList<String>();
            for (var colRef : projectedCols) {
                resultRow.add(resolveColumn(row, colRef));
            }
            resultRows.add(resultRow);
        }

        return new QueryResult.ResultSet(colDefs, resultRows);
    }

    // ========================== Original methods ==========================

    private QueryResult executeCreate(String tableName, String columnDefs, String currentDatabase) {
        var db = getDatabase(currentDatabase);
        if (db == null) return noDbError();

        var columns = new LinkedHashMap<String, ColumnType>();
        for (var colDef : columnDefs.split(",")) {
            var parts = colDef.trim().split("\\s+", 2);
            var name = parts[0].replace("`", "");
            var type = parseColumnType(parts.length > 1 ? parts[1] : "VARCHAR(255)");
            columns.put(name, type);
        }

        if (db.createTable(tableName, columns)) {
            return new QueryResult.Ok(0, 0);
        } else {
            return new QueryResult.Error(1050, "42S01", "Table '" + tableName + "' already exists");
        }
    }

    private QueryResult executeDrop(String tableName, String currentDatabase) {
        var db = getDatabase(currentDatabase);
        if (db == null) return noDbError();

        if (db.dropTable(tableName)) {
            return new QueryResult.Ok(0, 0);
        } else {
            return new QueryResult.Error(1051, "42S02", "Unknown table '" + tableName + "'");
        }
    }

    private QueryResult executeInsert(String tableName, String columnList,
                                       String valueList, String currentDatabase) {
        var db = getDatabase(currentDatabase);
        if (db == null) return noDbError();

        var table = db.getTable(tableName);
        if (table == null) return tableNotFound(tableName);

        var colNames = parseList(columnList);
        var values = parseValueList(valueList);

        if (colNames.size() != values.size()) {
            return new QueryResult.Error(1058, "21S01", "Column count doesn't match value count");
        }

        var row = new LinkedHashMap<String, String>();
        for (int i = 0; i < colNames.size(); i++) {
            row.put(colNames.get(i), values.get(i));
        }

        long id = table.insert(row);
        return new QueryResult.Ok(1, id);
    }

    private QueryResult executeUpdate(String tableName, String setCol, String setVal,
                                       String whereCol, String whereVal,
                                       String currentDatabase) {
        var db = getDatabase(currentDatabase);
        if (db == null) return noDbError();

        var table = db.getTable(tableName);
        if (table == null) return tableNotFound(tableName);

        int affected;
        if (whereCol != null && whereVal != null) {
            affected = table.update(setCol, setVal, whereCol, whereVal);
        } else {
            affected = table.updateAll(setCol, setVal);
        }
        return new QueryResult.Ok(affected, 0);
    }

    private QueryResult executeDelete(String tableName, String whereCol, String whereVal,
                                       String currentDatabase) {
        var db = getDatabase(currentDatabase);
        if (db == null) return noDbError();

        var table = db.getTable(tableName);
        if (table == null) return tableNotFound(tableName);

        int affected;
        if (whereCol != null && whereVal != null) {
            affected = table.deleteWhere(whereCol, whereVal);
        } else {
            affected = table.deleteAll();
        }
        return new QueryResult.Ok(affected, 0);
    }

    private InMemoryDatabase getDatabase(String name) {
        return name != null ? databases.get(name) : null;
    }

    private QueryResult noDbError() {
        return new QueryResult.Error(1046, "3D000", "No database selected");
    }

    private QueryResult tableNotFound(String tableName) {
        return new QueryResult.Error(1051, "42S02", "Unknown table '" + tableName + "'");
    }

    private List<String> parseList(String list) {
        var result = new ArrayList<String>();
        for (var item : list.split(",")) {
            result.add(item.trim().replace("`", ""));
        }
        return result;
    }

    private List<String> parseValueList(String list) {
        var result = new ArrayList<String>();
        for (var item : list.split(",")) {
            var trimmed = item.trim();
            if (trimmed.startsWith("'") && trimmed.endsWith("'")) {
                result.add(trimmed.substring(1, trimmed.length() - 1));
            } else if ("NULL".equalsIgnoreCase(trimmed)) {
                result.add(null);
            } else {
                result.add(trimmed);
            }
        }
        return result;
    }

    private ColumnType parseColumnType(String typeDef) {
        var upper = typeDef.toUpperCase().trim();
        if (upper.startsWith("INT") || upper.startsWith("INTEGER")) return ColumnType.LONG;
        if (upper.startsWith("BIGINT")) return ColumnType.LONGLONG;
        if (upper.startsWith("SMALLINT")) return ColumnType.SHORT;
        if (upper.startsWith("TINYINT")) return ColumnType.TINY;
        if (upper.startsWith("MEDIUMINT")) return ColumnType.INT24;
        if (upper.startsWith("FLOAT")) return ColumnType.FLOAT;
        if (upper.startsWith("DOUBLE") || upper.startsWith("REAL")) return ColumnType.DOUBLE;
        if (upper.startsWith("DECIMAL") || upper.startsWith("NUMERIC")) return ColumnType.NEWDECIMAL;
        if (upper.startsWith("DATE") && !upper.startsWith("DATETIME")) return ColumnType.DATE;
        if (upper.startsWith("DATETIME")) return ColumnType.DATETIME;
        if (upper.startsWith("TIMESTAMP")) return ColumnType.TIMESTAMP;
        if (upper.startsWith("TIME")) return ColumnType.TIME;
        if (upper.startsWith("YEAR")) return ColumnType.YEAR;
        if (upper.startsWith("CHAR")) return ColumnType.STRING;
        if (upper.startsWith("BLOB")) return ColumnType.BLOB;
        if (upper.startsWith("TEXT")) return ColumnType.BLOB;
        if (upper.startsWith("JSON")) return ColumnType.JSON;
        if (upper.startsWith("ENUM")) return ColumnType.ENUM;
        if (upper.startsWith("SET")) return ColumnType.SET;
        if (upper.startsWith("BIT")) return ColumnType.BIT;
        return ColumnType.VAR_STRING; // default for VARCHAR and others
    }
}
