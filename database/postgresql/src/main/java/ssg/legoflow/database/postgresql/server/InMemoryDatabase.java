package ssg.legoflow.database.postgresql.server;

import ssg.legoflow.database.postgresql.common.PgSeverity;
import ssg.legoflow.database.postgresql.common.SqlState;
import ssg.legoflow.database.postgresql.protocol.BackendMessage.ColumnDescription;
import ssg.legoflow.database.postgresql.protocol.PgType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Simple in-memory database supporting basic CREATE TABLE, INSERT, SELECT, UPDATE, DELETE.
 *
 * <p>This is NOT a full SQL engine. It supports only minimal SQL for testing the wire protocol.
 *
 * @since 0.1.0
 */
public final class InMemoryDatabase {

    private final Map<String, TableDef> tables = new ConcurrentHashMap<>();

    /**
     * A table definition.
     */
    private record TableDef(String name, List<ColDef> columns, List<String[]> rows) {}

    /**
     * A column definition.
     */
    private record ColDef(String name, PgType type) {}

    /**
     * Creates a new empty in-memory database.
     */
    public InMemoryDatabase() {}

    /**
     * Executes a SQL statement and returns the result.
     *
     * @param sql    the SQL statement
     * @param params parameter values for $1, $2, ... placeholders
     * @return the result set
     * @throws SqlException if the SQL is invalid
     */
    public ResultSet execute(String sql, String... params) {
        sql = substituteParams(sql.trim(), params);
        String upper = sql.toUpperCase();

        if (upper.startsWith("CREATE TABLE")) {
            return executeCreateTable(sql);
        } else if (upper.startsWith("INSERT")) {
            return executeInsert(sql);
        } else if (upper.startsWith("SELECT")) {
            return executeSelect(sql);
        } else if (upper.startsWith("UPDATE")) {
            return executeUpdate(sql);
        } else if (upper.startsWith("DELETE")) {
            return executeDelete(sql);
        } else if (upper.startsWith("DROP TABLE")) {
            return executeDropTable(sql);
        } else if (upper.startsWith("BEGIN") || upper.startsWith("START TRANSACTION")) {
            return ResultSet.commandOnly("BEGIN");
        } else if (upper.startsWith("COMMIT")) {
            return ResultSet.commandOnly("COMMIT");
        } else if (upper.startsWith("ROLLBACK")) {
            return ResultSet.commandOnly("ROLLBACK");
        } else if (upper.startsWith("SET")) {
            return ResultSet.commandOnly("SET");
        } else {
            throw new SqlException(SqlState.SYNTAX_ERROR, "Unsupported SQL: " + sql);
        }
    }

    /**
     * Returns the table names in this database.
     *
     * @return the set of table names
     */
    public Set<String> tableNames() {
        return Collections.unmodifiableSet(tables.keySet());
    }

    /**
     * Returns the number of rows in a table.
     *
     * @param tableName the table name
     * @return the row count
     */
    public int rowCount(String tableName) {
        TableDef table = tables.get(tableName.toLowerCase());
        return table == null ? 0 : table.rows().size();
    }

    // ======== SQL execution ========

    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile(
            "(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(\\w+)\\s*\\((.+)\\)",
            Pattern.DOTALL);

    private ResultSet executeCreateTable(String sql) {
        Matcher m = CREATE_TABLE_PATTERN.matcher(sql);
        if (!m.find()) throw new SqlException(SqlState.SYNTAX_ERROR, "Invalid CREATE TABLE: " + sql);

        String tableName = m.group(1).toLowerCase();
        if (tables.containsKey(tableName)) {
            if (sql.toUpperCase().contains("IF NOT EXISTS")) {
                return ResultSet.commandOnly("CREATE TABLE");
            }
            throw new SqlException(SqlState.DUPLICATE_TABLE, "Table already exists: " + tableName);
        }

        String colDefs = m.group(2);
        List<ColDef> columns = new ArrayList<>();
        for (String colDef : splitColumns(colDefs)) {
            String trimmed = colDef.trim();
            if (trimmed.isEmpty()) continue;
            // Skip constraints
            String upper = trimmed.toUpperCase();
            if (upper.startsWith("PRIMARY KEY") || upper.startsWith("UNIQUE")
                    || upper.startsWith("CHECK") || upper.startsWith("FOREIGN KEY")
                    || upper.startsWith("CONSTRAINT")) {
                continue;
            }
            String[] parts = trimmed.split("\\s+", 2);
            String colName = parts[0].toLowerCase();
            String typeName = parts.length > 1 ? parts[1].split("\\s+")[0].replaceAll("\\(.*\\)", "") : "text";
            columns.add(new ColDef(colName, PgType.fromName(typeName)));
        }

        tables.put(tableName, new TableDef(tableName, columns, new CopyOnWriteArrayList<>()));
        return ResultSet.commandOnly("CREATE TABLE");
    }

    private static final Pattern INSERT_PATTERN = Pattern.compile(
            "(?i)INSERT\\s+INTO\\s+(\\w+)\\s*(?:\\(([^)]+)\\))?\\s*VALUES\\s*\\((.+)\\)");

    private ResultSet executeInsert(String sql) {
        Matcher m = INSERT_PATTERN.matcher(sql);
        if (!m.find()) throw new SqlException(SqlState.SYNTAX_ERROR, "Invalid INSERT: " + sql);

        String tableName = m.group(1).toLowerCase();
        TableDef table = getTable(tableName);

        String colList = m.group(2);
        String valList = m.group(3);

        List<String> targetCols;
        if (colList != null) {
            targetCols = Arrays.stream(colList.split(","))
                    .map(s -> s.trim().toLowerCase())
                    .toList();
        } else {
            targetCols = table.columns().stream().map(ColDef::name).toList();
        }

        List<String> values = splitValues(valList);
        String[] row = new String[table.columns().size()];
        for (int i = 0; i < targetCols.size() && i < values.size(); i++) {
            int colIdx = findColumnIndex(table, targetCols.get(i));
            row[colIdx] = unquote(values.get(i).trim());
        }

        table.rows().add(row);
        return ResultSet.commandOnly("INSERT 0 1");
    }

    private ResultSet executeSelect(String sql) {
        String trimmed = sql.trim();

        // Check if this is a JOIN query
        if (containsJoin(trimmed)) {
            return executeJoinSelect(trimmed);
        }

        // Parse clauses
        ParsedSelect parsed = parseSelectClauses(trimmed);

        TableDef table = getTable(parsed.tableName);

        // Detect aggregates in column expressions
        List<SelectExpr> selectExprs = parseSelectExprs(parsed.colExpr, table, null);
        boolean hasAggregates = selectExprs.stream().anyMatch(SelectExpr::isAggregate);

        if (hasAggregates || parsed.groupBy != null) {
            return executeAggregateSelect(table, selectExprs, parsed);
        }

        // Resolve columns (non-aggregate path)
        List<Integer> colIndices = new ArrayList<>();
        List<ColumnDescription> columns = new ArrayList<>();
        if (parsed.colExpr.trim().equals("*")) {
            for (int i = 0; i < table.columns().size(); i++) {
                colIndices.add(i);
                ColDef col = table.columns().get(i);
                columns.add(makeColDesc(col));
            }
        } else {
            for (SelectExpr expr : selectExprs) {
                int idx = findColumnIndex(table, expr.sourceCol());
                colIndices.add(idx);
                columns.add(makeColDesc(expr.alias(), table.columns().get(idx).type()));
            }
        }

        // Filter rows
        List<String[]> filteredRows = new ArrayList<>();
        for (String[] row : table.rows()) {
            if (parsed.whereClause == null || matchesWhere(table, row, parsed.whereClause)) {
                String[] projected = new String[colIndices.size()];
                for (int i = 0; i < colIndices.size(); i++) {
                    projected[i] = row[colIndices.get(i)];
                }
                filteredRows.add(projected);
            }
        }

        // Order
        if (parsed.orderBy != null) {
            sortRows(filteredRows, parsed.orderBy, columns);
        }

        // Limit
        if (parsed.limitStr != null) {
            int limit = Integer.parseInt(parsed.limitStr);
            if (filteredRows.size() > limit) {
                filteredRows = filteredRows.subList(0, limit);
            }
        }

        return new ResultSet(columns, filteredRows, "SELECT " + filteredRows.size());
    }

    // ======== Parsed SELECT structure ========

    private record ParsedSelect(String colExpr, String tableName, String whereClause,
                                 String groupBy, String having, String orderBy, String limitStr) {}

    private ParsedSelect parseSelectClauses(String sql) {
        String upper = sql.toUpperCase();
        int selectIdx = upper.indexOf("SELECT ") + 7;
        int fromIdx = findKeyword(upper, " FROM ");
        if (fromIdx < 0) throw new SqlException(SqlState.SYNTAX_ERROR, "Missing FROM: " + sql);

        String colExpr = sql.substring(selectIdx, fromIdx).trim();
        String rest = sql.substring(fromIdx + 6).trim();

        // Extract table name (first word)
        String[] tableAndRest = rest.split("\\s+", 2);
        String tableName = tableAndRest[0].toLowerCase();
        rest = tableAndRest.length > 1 ? tableAndRest[1] : "";
        String restUpper = rest.toUpperCase();

        String whereClause = null;
        String groupBy = null;
        String having = null;
        String orderBy = null;
        String limitStr = null;

        // Parse WHERE
        int whereIdx = findKeyword(restUpper, "WHERE ");
        int groupByIdx = findKeyword(restUpper, "GROUP BY ");
        int havingIdx = findKeyword(restUpper, "HAVING ");
        int orderByIdx = findKeyword(restUpper, "ORDER BY ");
        int limitIdx = findKeyword(restUpper, "LIMIT ");

        if (whereIdx >= 0) {
            int end = minPositive(groupByIdx, havingIdx, orderByIdx, limitIdx, rest.length());
            whereClause = rest.substring(whereIdx + 6, end).trim();
        }
        if (groupByIdx >= 0) {
            int end = minPositive(havingIdx, orderByIdx, limitIdx, rest.length());
            groupBy = rest.substring(groupByIdx + 9, end).trim();
        }
        if (havingIdx >= 0) {
            int end = minPositive(orderByIdx, limitIdx, rest.length());
            having = rest.substring(havingIdx + 7, end).trim();
        }
        if (orderByIdx >= 0) {
            int end = minPositive(limitIdx, rest.length());
            orderBy = rest.substring(orderByIdx + 9, end).trim();
        }
        if (limitIdx >= 0) {
            limitStr = rest.substring(limitIdx + 6).trim();
        }

        return new ParsedSelect(colExpr, tableName, whereClause, groupBy, having, orderBy, limitStr);
    }

    private static int findKeyword(String upper, String keyword) {
        // Find keyword not inside quotes
        int idx = 0;
        while (idx < upper.length()) {
            int found = upper.indexOf(keyword, idx);
            if (found < 0) return -1;
            // Simple check: not inside quotes
            return found;
        }
        return -1;
    }

    private static int minPositive(int... values) {
        int min = Integer.MAX_VALUE;
        for (int v : values) {
            if (v >= 0 && v < min) min = v;
        }
        return min;
    }

    // ======== SELECT expression parsing ========

    private record SelectExpr(String raw, String sourceCol, String alias, String aggregateFunc, boolean isAggregate) {
        static SelectExpr column(String name, String alias) {
            return new SelectExpr(name, name, alias, null, false);
        }
        static SelectExpr aggregate(String raw, String func, String innerCol, String alias) {
            return new SelectExpr(raw, innerCol, alias, func, true);
        }
    }

    private static final Pattern AGGREGATE_PATTERN = Pattern.compile(
            "(?i)(COUNT|SUM|AVG|MIN|MAX)\\s*\\(\\s*(\\*|\\w+(?:\\.\\w+)?)\\s*\\)");

    private static final Pattern ALIAS_PATTERN = Pattern.compile(
            "(?i)(.+?)\\s+AS\\s+(\\w+)\\s*$");

    private List<SelectExpr> parseSelectExprs(String colExpr, TableDef table, Map<String, TableContext> tableContexts) {
        if (colExpr.trim().equals("*")) {
            List<SelectExpr> exprs = new ArrayList<>();
            for (ColDef col : table.columns()) {
                exprs.add(SelectExpr.column(col.name(), col.name()));
            }
            return exprs;
        }

        List<SelectExpr> exprs = new ArrayList<>();
        for (String raw : splitColumns(colExpr)) {
            String trimmed = raw.trim();

            // Check for alias
            String alias = null;
            String expr = trimmed;
            Matcher aliasMatcher = ALIAS_PATTERN.matcher(trimmed);
            if (aliasMatcher.matches()) {
                expr = aliasMatcher.group(1).trim();
                alias = aliasMatcher.group(2).toLowerCase();
            }

            // Check for aggregate function
            Matcher aggMatcher = AGGREGATE_PATTERN.matcher(expr);
            if (aggMatcher.matches()) {
                String func = aggMatcher.group(1).toUpperCase();
                String innerCol = aggMatcher.group(2).toLowerCase();
                if (alias == null) alias = func.toLowerCase() + (innerCol.equals("*") ? "" : "_" + innerCol);
                exprs.add(SelectExpr.aggregate(expr, func, innerCol, alias));
            } else {
                // Regular column reference — strip table qualifier
                String colName = expr.toLowerCase().trim();
                if (colName.contains(".")) {
                    colName = colName.substring(colName.indexOf('.') + 1);
                }
                if (alias == null) alias = colName;
                exprs.add(SelectExpr.column(colName, alias));
            }
        }
        return exprs;
    }

    // ======== Aggregate execution ========

    private ResultSet executeAggregateSelect(TableDef table, List<SelectExpr> selectExprs, ParsedSelect parsed) {
        // Filter rows by WHERE
        List<String[]> filteredRows = new ArrayList<>();
        for (String[] row : table.rows()) {
            if (parsed.whereClause == null || matchesWhere(table, row, parsed.whereClause)) {
                filteredRows.add(row);
            }
        }

        // Group rows
        List<String> groupByCols = new ArrayList<>();
        List<Integer> groupByIndices = new ArrayList<>();
        if (parsed.groupBy != null) {
            for (String col : parsed.groupBy.split(",")) {
                String colName = col.trim().toLowerCase();
                groupByCols.add(colName);
                groupByIndices.add(findColumnIndex(table, colName));
            }
        }

        // Build groups
        Map<String, List<String[]>> groups = new LinkedHashMap<>();
        if (groupByCols.isEmpty()) {
            // Single group for all rows
            groups.put("", filteredRows);
        } else {
            for (String[] row : filteredRows) {
                StringBuilder key = new StringBuilder();
                for (int idx : groupByIndices) {
                    if (key.length() > 0) key.append('\0');
                    key.append(row[idx] == null ? "\\N" : row[idx]);
                }
                groups.computeIfAbsent(key.toString(), k -> new ArrayList<>()).add(row);
            }
        }

        // Build result columns
        List<ColumnDescription> resultColumns = new ArrayList<>();
        for (SelectExpr expr : selectExprs) {
            if (expr.isAggregate()) {
                resultColumns.add(makeColDesc(expr.alias(), PgType.INT8));
            } else {
                int idx = findColumnIndex(table, expr.sourceCol());
                resultColumns.add(makeColDesc(expr.alias(), table.columns().get(idx).type()));
            }
        }

        // Compute aggregates per group
        List<String[]> resultRows = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            List<String[]> groupRows = entry.getValue();

            // Apply HAVING filter (must evaluate aggregates for this group)
            if (parsed.having != null && !matchesHaving(table, selectExprs, groupRows, parsed.having)) {
                continue;
            }

            String[] resultRow = new String[selectExprs.size()];
            for (int i = 0; i < selectExprs.size(); i++) {
                SelectExpr expr = selectExprs.get(i);
                if (expr.isAggregate()) {
                    resultRow[i] = computeAggregate(table, groupRows, expr.aggregateFunc(), expr.sourceCol());
                } else {
                    int colIdx = findColumnIndex(table, expr.sourceCol());
                    resultRow[i] = groupRows.isEmpty() ? null : groupRows.get(0)[colIdx];
                }
            }
            resultRows.add(resultRow);
        }

        // Order
        if (parsed.orderBy != null) {
            sortRows(resultRows, parsed.orderBy, resultColumns);
        }

        // Limit
        if (parsed.limitStr != null) {
            int limit = Integer.parseInt(parsed.limitStr);
            if (resultRows.size() > limit) {
                resultRows = resultRows.subList(0, limit);
            }
        }

        return new ResultSet(resultColumns, resultRows, "SELECT " + resultRows.size());
    }

    private String computeAggregate(TableDef table, List<String[]> rows, String func, String col) {
        if ("COUNT".equals(func)) {
            if ("*".equals(col)) {
                return String.valueOf(rows.size());
            }
            int idx = findColumnIndex(table, col);
            long count = rows.stream().filter(r -> r[idx] != null).count();
            return String.valueOf(count);
        }

        int idx = findColumnIndex(table, col);

        return switch (func) {
            case "SUM" -> {
                double sum = 0;
                for (String[] row : rows) {
                    if (row[idx] != null) sum += parseNumber(row[idx]);
                }
                yield formatNumber(sum);
            }
            case "AVG" -> {
                double sum = 0;
                int count = 0;
                for (String[] row : rows) {
                    if (row[idx] != null) {
                        sum += parseNumber(row[idx]);
                        count++;
                    }
                }
                yield count == 0 ? null : formatNumber(sum / count);
            }
            case "MIN" -> {
                String min = null;
                for (String[] row : rows) {
                    if (row[idx] != null) {
                        if (min == null || row[idx].compareTo(min) < 0) min = row[idx];
                    }
                }
                yield min;
            }
            case "MAX" -> {
                String max = null;
                for (String[] row : rows) {
                    if (row[idx] != null) {
                        if (max == null || row[idx].compareTo(max) > 0) max = row[idx];
                    }
                }
                yield max;
            }
            default -> throw new SqlException(SqlState.SYNTAX_ERROR, "Unknown aggregate: " + func);
        };
    }

    private boolean matchesHaving(TableDef table, List<SelectExpr> selectExprs,
                                   List<String[]> groupRows, String havingClause) {
        // Parse HAVING condition: aggregate_func(col) op value
        // Support: COUNT(*) > 1, SUM(col) >= 100, etc.
        String[] conditions = havingClause.split("(?i)\\s+AND\\s+");
        for (String cond : conditions) {
            cond = cond.trim();
            // Parse: aggregate(col) op value
            Pattern havingPattern = Pattern.compile(
                    "(?i)(COUNT|SUM|AVG|MIN|MAX)\\s*\\(\\s*(\\*|\\w+)\\s*\\)\\s*([><=!]+)\\s*(.+)");
            Matcher hm = havingPattern.matcher(cond);
            if (!hm.matches()) {
                throw new SqlException(SqlState.SYNTAX_ERROR, "Invalid HAVING clause: " + cond);
            }
            String func = hm.group(1).toUpperCase();
            String col = hm.group(2).toLowerCase();
            String op = hm.group(3);
            String valueStr = unquote(hm.group(4).trim());

            String aggResult = computeAggregate(table, groupRows, func, col);
            double aggVal = aggResult == null ? 0 : parseNumber(aggResult);
            double compareVal = parseNumber(valueStr);

            boolean match = switch (op) {
                case ">" -> aggVal > compareVal;
                case ">=" -> aggVal >= compareVal;
                case "<" -> aggVal < compareVal;
                case "<=" -> aggVal <= compareVal;
                case "=" -> aggVal == compareVal;
                case "!=" , "<>" -> aggVal != compareVal;
                default -> throw new SqlException(SqlState.SYNTAX_ERROR, "Unknown operator: " + op);
            };
            if (!match) return false;
        }
        return true;
    }

    private static double parseNumber(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String formatNumber(double d) {
        if (d == (long) d) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }

    // ======== JOIN execution ========

    private record TableContext(TableDef table, String alias) {}

    private boolean containsJoin(String sql) {
        String upper = sql.toUpperCase();
        return upper.contains(" JOIN ") && upper.contains(" ON ");
    }

    private ResultSet executeJoinSelect(String sql) {
        String upper = sql.toUpperCase();
        int selectIdx = upper.indexOf("SELECT ") + 7;
        int fromIdx = findKeyword(upper, " FROM ");
        if (fromIdx < 0) throw new SqlException(SqlState.SYNTAX_ERROR, "Missing FROM: " + sql);

        String colExpr = sql.substring(selectIdx, fromIdx).trim();

        // Parse everything after FROM into tokens
        // Strategy: walk forward, find each JOIN...ON pair, then trailing clauses
        String afterFrom = sql.substring(fromIdx + 6).trim();

        // First, separate the FROM/JOIN section from trailing clauses (WHERE, GROUP BY, etc.)
        // We do this by finding trailing clause keywords that are NOT between JOIN and ON
        JoinParsed joinParsed = parseJoinClauses(afterFrom);
        String whereClause = joinParsed.whereClause;
        String orderBy = joinParsed.orderBy;
        String limitStr = joinParsed.limitStr;
        String groupBy = joinParsed.groupBy;
        String having = joinParsed.having;

        // Parse the join section: table1 [alias1] [INNER|LEFT] JOIN table2 [alias2] ON cond [JOIN ...]
        List<JoinClause> joins = joinParsed.joins;
        Map<String, TableContext> tableContexts = joinParsed.tableContexts;

        // Execute joins
        // Start with all rows from the first table
        String firstAlias = tableContexts.values().iterator().next().alias();
        TableDef firstTable = tableContexts.values().iterator().next().table();
        List<Map<String, String[]>> joinedRows = new ArrayList<>();
        for (String[] row : firstTable.rows()) {
            Map<String, String[]> combined = new LinkedHashMap<>();
            combined.put(firstAlias, row);
            joinedRows.add(combined);
        }

        // Process each join
        for (JoinClause join : joins) {
            List<Map<String, String[]>> newRows = new ArrayList<>();
            for (Map<String, String[]> leftRow : joinedRows) {
                boolean matched = false;
                for (String[] rightRow : join.table().table().rows()) {
                    Map<String, String[]> candidate = new LinkedHashMap<>(leftRow);
                    candidate.put(join.table().alias(), rightRow);
                    if (matchesJoinCondition(candidate, join.condition(), tableContexts)) {
                        newRows.add(candidate);
                        matched = true;
                    }
                }
                if (!matched && join.left()) {
                    // LEFT JOIN: add null row for right side
                    Map<String, String[]> candidate = new LinkedHashMap<>(leftRow);
                    String[] nullRow = new String[join.table().table().columns().size()];
                    candidate.put(join.table().alias(), nullRow);
                    newRows.add(candidate);
                }
            }
            joinedRows = newRows;
        }

        // Apply WHERE filter
        if (whereClause != null) {
            String wc = whereClause;
            List<Map<String, String[]>> filtered = new ArrayList<>();
            for (Map<String, String[]> row : joinedRows) {
                if (matchesJoinWhere(row, wc, tableContexts)) {
                    filtered.add(row);
                }
            }
            joinedRows = filtered;
        }

        // Build result columns and project
        List<ColumnDescription> resultColumns = new ArrayList<>();
        List<JoinProjection> projections = new ArrayList<>();

        if (colExpr.trim().equals("*")) {
            for (var ctx : tableContexts.values()) {
                for (ColDef col : ctx.table().columns()) {
                    resultColumns.add(makeColDesc(ctx.alias() + "." + col.name(), col.type()));
                    projections.add(new JoinProjection(ctx.alias(), col.name()));
                }
            }
        } else {
            for (String rawCol : splitColumns(colExpr)) {
                String trimmedCol = rawCol.trim();
                // Check for alias
                String alias = null;
                Matcher aliasMatcher = ALIAS_PATTERN.matcher(trimmedCol);
                if (aliasMatcher.matches()) {
                    trimmedCol = aliasMatcher.group(1).trim();
                    alias = aliasMatcher.group(2).toLowerCase();
                }

                // Check for aggregate
                Matcher aggMatcher = AGGREGATE_PATTERN.matcher(trimmedCol);
                if (aggMatcher.matches()) {
                    String func = aggMatcher.group(1).toUpperCase();
                    String innerCol = aggMatcher.group(2).toLowerCase();
                    if (alias == null) alias = func.toLowerCase() + (innerCol.equals("*") ? "" : "_" + innerCol);
                    resultColumns.add(makeColDesc(alias, PgType.INT8));
                    projections.add(new JoinProjection(null, trimmedCol, func, innerCol, alias));
                    continue;
                }

                // Resolve qualified column: alias.col or plain col
                String tableAlias = null;
                String colName = trimmedCol.toLowerCase();
                if (colName.contains(".")) {
                    String[] parts = colName.split("\\.", 2);
                    tableAlias = parts[0];
                    colName = parts[1];
                }

                if (tableAlias == null) {
                    // Resolve ambiguous column
                    tableAlias = resolveColumn(colName, tableContexts);
                }

                TableContext ctx = tableContexts.get(tableAlias);
                if (ctx == null) throw new SqlException(SqlState.UNDEFINED_TABLE, "Unknown table/alias: " + tableAlias);

                int colIdx = findColumnIndex(ctx.table(), colName);
                ColDef col = ctx.table().columns().get(colIdx);
                String displayName = alias != null ? alias : colName;
                resultColumns.add(makeColDesc(displayName, col.type()));
                projections.add(new JoinProjection(tableAlias, colName));
            }
        }

        // Check if we have aggregates with GROUP BY in joins
        boolean hasAgg = projections.stream().anyMatch(p -> p.aggregateFunc != null);
        if (hasAgg || groupBy != null) {
            return executeJoinAggregate(joinedRows, resultColumns, projections,
                    tableContexts, groupBy, having, orderBy, limitStr);
        }

        // Project result rows
        List<String[]> resultRows = new ArrayList<>();
        for (Map<String, String[]> joinedRow : joinedRows) {
            String[] row = new String[projections.size()];
            for (int i = 0; i < projections.size(); i++) {
                JoinProjection proj = projections.get(i);
                String[] tableRow = joinedRow.get(proj.tableAlias);
                if (tableRow != null) {
                    TableContext ctx = tableContexts.get(proj.tableAlias);
                    int colIdx = findColumnIndex(ctx.table(), proj.colName);
                    row[i] = tableRow[colIdx];
                }
            }
            resultRows.add(row);
        }

        // Order
        if (orderBy != null) {
            sortRows(resultRows, orderBy, resultColumns);
        }

        // Limit
        if (limitStr != null) {
            int limit = Integer.parseInt(limitStr);
            if (resultRows.size() > limit) {
                resultRows = resultRows.subList(0, limit);
            }
        }

        return new ResultSet(resultColumns, resultRows, "SELECT " + resultRows.size());
    }

    private record JoinParsed(Map<String, TableContext> tableContexts, List<JoinClause> joins,
                               String whereClause, String groupBy, String having,
                               String orderBy, String limitStr) {}

    private JoinParsed parseJoinClauses(String afterFrom) {
        Map<String, TableContext> tableContexts = new LinkedHashMap<>();
        List<JoinClause> joins = new ArrayList<>();

        // Tokenize into words, preserving original case for values
        // Walk through and build the structure
        String remaining = afterFrom;

        // Parse first table reference (before any JOIN)
        int firstJoinPos = findFirstJoinPosition(remaining);
        String firstTablePart;
        if (firstJoinPos >= 0) {
            firstTablePart = remaining.substring(0, firstJoinPos).trim();
            remaining = remaining.substring(firstJoinPos);
        } else {
            // No joins found? Shouldn't happen since containsJoin was true
            firstTablePart = remaining.trim();
            remaining = "";
        }
        parseTableRef(firstTablePart, tableContexts);

        // Parse JOIN clauses
        while (!remaining.isBlank()) {
            String remUpper = remaining.toUpperCase().trim();
            remaining = remaining.trim();

            boolean leftJoin = false;
            if (remUpper.startsWith("LEFT ")) {
                leftJoin = true;
                remaining = remaining.substring(5).trim();
                remUpper = remaining.toUpperCase().trim();
            }
            if (remUpper.startsWith("INNER ")) {
                remaining = remaining.substring(6).trim();
                remUpper = remaining.toUpperCase().trim();
            }
            if (!remUpper.startsWith("JOIN ")) break;
            remaining = remaining.substring(5).trim();

            // Find ON
            int onIdx = remaining.toUpperCase().indexOf(" ON ");
            if (onIdx < 0) throw new SqlException(SqlState.SYNTAX_ERROR, "Missing ON in JOIN");

            String tablePart = remaining.substring(0, onIdx).trim();
            remaining = remaining.substring(onIdx + 4).trim();

            // Find end of ON condition: next JOIN keyword, or trailing clause keyword
            int endOfOn = findEndOfOnCondition(remaining);
            String onCondition;
            if (endOfOn >= 0) {
                onCondition = remaining.substring(0, endOfOn).trim();
                remaining = remaining.substring(endOfOn);
            } else {
                onCondition = remaining.trim();
                remaining = "";
            }

            TableContext joinTable = parseTableRef(tablePart, tableContexts);
            joins.add(new JoinClause(joinTable, onCondition, leftJoin));
        }

        // Parse trailing clauses from remaining text
        String whereClause = null;
        String groupBy = null;
        String having = null;
        String orderBy = null;
        String limitStr = null;

        if (!remaining.isBlank()) {
            String rest = remaining;
            String restUpper = rest.toUpperCase();

            int wIdx = indexOfKeyword(restUpper, "WHERE ");
            int gIdx = indexOfKeyword(restUpper, "GROUP BY ");
            int hIdx = indexOfKeyword(restUpper, "HAVING ");
            int oIdx = indexOfKeyword(restUpper, "ORDER BY ");
            int lIdx = indexOfKeyword(restUpper, "LIMIT ");

            if (wIdx >= 0) {
                int end = endOfClause(gIdx, hIdx, oIdx, lIdx, rest.length());
                whereClause = rest.substring(wIdx + 6, end).trim();
            }
            if (gIdx >= 0) {
                int end = endOfClause(hIdx, oIdx, lIdx, rest.length());
                groupBy = rest.substring(gIdx + 9, end).trim();
            }
            if (hIdx >= 0) {
                int end = endOfClause(oIdx, lIdx, rest.length());
                having = rest.substring(hIdx + 7, end).trim();
            }
            if (oIdx >= 0) {
                int end = endOfClause(lIdx, rest.length());
                orderBy = rest.substring(oIdx + 9, end).trim();
            }
            if (lIdx >= 0) {
                limitStr = rest.substring(lIdx + 6).trim();
            }
        }

        return new JoinParsed(tableContexts, joins, whereClause, groupBy, having, orderBy, limitStr);
    }

    private int findFirstJoinPosition(String text) {
        String upper = text.toUpperCase();
        // Look for LEFT JOIN, INNER JOIN, or plain JOIN
        int best = -1;
        for (String kw : new String[]{"LEFT JOIN ", "INNER JOIN ", "JOIN "}) {
            int idx = upper.indexOf(kw);
            if (idx >= 0 && (best < 0 || idx < best)) {
                best = idx;
            }
        }
        return best;
    }

    private int findEndOfOnCondition(String text) {
        String upper = text.toUpperCase();
        int best = -1;
        // Next JOIN
        for (String kw : new String[]{"LEFT JOIN ", "INNER JOIN ", "JOIN "}) {
            int idx = upper.indexOf(kw);
            if (idx >= 0 && (best < 0 || idx < best)) best = idx;
        }
        // Trailing clauses
        for (String kw : new String[]{"WHERE ", "GROUP BY ", "HAVING ", "ORDER BY ", "LIMIT "}) {
            int idx = indexOfKeyword(upper, kw);
            if (idx >= 0 && (best < 0 || idx < best)) best = idx;
        }
        return best;
    }

    private static int indexOfKeyword(String upper, String keyword) {
        return upper.indexOf(keyword);
    }

    private static int endOfClause(int... candidates) {
        int min = Integer.MAX_VALUE;
        for (int c : candidates) {
            if (c >= 0 && c < min) min = c;
        }
        return min;
    }

    private record JoinClause(TableContext table, String condition, boolean left) {}

    private record JoinProjection(String tableAlias, String colName, String aggregateFunc,
                                   String aggInnerCol, String alias) {
        JoinProjection(String tableAlias, String colName) {
            this(tableAlias, colName, null, null, null);
        }
    }

    private TableContext parseTableRef(String tablePart, Map<String, TableContext> contexts) {
        String[] parts = tablePart.trim().split("\\s+");
        String tableName = parts[0].toLowerCase();
        String alias = parts.length > 1 ? parts[1].toLowerCase() : tableName;
        // Skip AS keyword
        if (alias.equalsIgnoreCase("as") && parts.length > 2) {
            alias = parts[2].toLowerCase();
        }
        TableDef table = getTable(tableName);
        TableContext ctx = new TableContext(table, alias);
        contexts.put(alias, ctx);
        return ctx;
    }


    private boolean matchesJoinCondition(Map<String, String[]> row, String condition,
                                          Map<String, TableContext> contexts) {
        // Parse: t1.col1 = t2.col2 [AND t1.col3 = t2.col4]
        String[] conditions = condition.split("(?i)\\s+AND\\s+");
        for (String cond : conditions) {
            cond = cond.trim();
            String[] parts = cond.split("\\s*=\\s*");
            if (parts.length != 2) return false;
            String leftVal = resolveJoinValue(parts[0].trim(), row, contexts);
            String rightVal = resolveJoinValue(parts[1].trim(), row, contexts);
            if (!Objects.equals(leftVal, rightVal)) return false;
        }
        return true;
    }

    private String resolveJoinValue(String ref, Map<String, String[]> row,
                                     Map<String, TableContext> contexts) {
        String lower = ref.toLowerCase();
        // Literal value
        if (lower.startsWith("'") && lower.endsWith("'")) {
            return unquote(ref);
        }
        // Qualified reference: alias.col
        if (lower.contains(".")) {
            String[] parts = lower.split("\\.", 2);
            String alias = parts[0];
            String col = parts[1];
            String[] tableRow = row.get(alias);
            if (tableRow == null) return null;
            TableContext ctx = contexts.get(alias);
            if (ctx == null) return null;
            int idx = findColumnIndex(ctx.table(), col);
            return tableRow[idx];
        }
        // Unqualified: try to resolve
        for (var entry : contexts.entrySet()) {
            try {
                int idx = findColumnIndex(entry.getValue().table(), lower);
                String[] tableRow = row.get(entry.getKey());
                if (tableRow != null) return tableRow[idx];
            } catch (SqlException e) {
                // Column not in this table, try next
            }
        }
        return null;
    }

    private boolean matchesJoinWhere(Map<String, String[]> row, String whereClause,
                                      Map<String, TableContext> contexts) {
        String[] conditions = whereClause.split("(?i)\\s+AND\\s+");
        for (String cond : conditions) {
            cond = cond.trim();
            String[] parts;
            boolean notEqual = false;
            if (cond.contains("!=") || cond.contains("<>")) {
                parts = cond.split("!=|<>", 2);
                notEqual = true;
            } else {
                parts = cond.split("=", 2);
            }
            if (parts.length != 2) return false;
            String leftStr = parts[0].trim();
            String rightStr = parts[1].trim();
            // Resolve left side as column reference
            String leftVal = resolveJoinValue(leftStr, row, contexts);
            // Right side could be a literal or column reference
            String rightVal;
            if (rightStr.startsWith("'") && rightStr.endsWith("'")) {
                rightVal = rightStr.substring(1, rightStr.length() - 1);
            } else {
                rightVal = resolveJoinValue(rightStr, row, contexts);
            }
            boolean eq = Objects.equals(leftVal, rightVal);
            if (notEqual ? eq : !eq) return false;
        }
        return true;
    }

    private String resolveColumn(String colName, Map<String, TableContext> contexts) {
        String found = null;
        for (var entry : contexts.entrySet()) {
            try {
                findColumnIndex(entry.getValue().table(), colName);
                if (found != null) {
                    throw new SqlException(SqlState.SYNTAX_ERROR, "Ambiguous column: " + colName);
                }
                found = entry.getKey();
            } catch (SqlException e) {
                // Column not in this table
            }
        }
        if (found == null) {
            throw new SqlException(SqlState.UNDEFINED_COLUMN, "Column not found: " + colName);
        }
        return found;
    }

    private ResultSet executeJoinAggregate(List<Map<String, String[]>> joinedRows,
                                            List<ColumnDescription> resultColumns,
                                            List<JoinProjection> projections,
                                            Map<String, TableContext> tableContexts,
                                            String groupBy, String having,
                                            String orderBy, String limitStr) {
        // Group rows
        List<String> groupByCols = new ArrayList<>();
        if (groupBy != null) {
            for (String col : groupBy.split(",")) {
                groupByCols.add(col.trim().toLowerCase());
            }
        }

        Map<String, List<Map<String, String[]>>> groups = new LinkedHashMap<>();
        if (groupByCols.isEmpty()) {
            groups.put("", joinedRows);
        } else {
            for (Map<String, String[]> row : joinedRows) {
                StringBuilder key = new StringBuilder();
                for (String col : groupByCols) {
                    if (key.length() > 0) key.append('\0');
                    String val = resolveJoinValue(col, row, tableContexts);
                    key.append(val == null ? "\\N" : val);
                }
                groups.computeIfAbsent(key.toString(), k -> new ArrayList<>()).add(row);
            }
        }

        List<String[]> resultRows = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            List<Map<String, String[]>> groupRows = entry.getValue();

            String[] resultRow = new String[projections.size()];
            for (int i = 0; i < projections.size(); i++) {
                JoinProjection proj = projections.get(i);
                if (proj.aggregateFunc() != null) {
                    resultRow[i] = computeJoinAggregate(groupRows, proj.aggregateFunc(),
                            proj.aggInnerCol(), tableContexts);
                } else {
                    if (!groupRows.isEmpty()) {
                        String[] tableRow = groupRows.get(0).get(proj.tableAlias());
                        if (tableRow != null) {
                            TableContext ctx = tableContexts.get(proj.tableAlias());
                            int colIdx = findColumnIndex(ctx.table(), proj.colName());
                            resultRow[i] = tableRow[colIdx];
                        }
                    }
                }
            }
            resultRows.add(resultRow);
        }

        // Order
        if (orderBy != null) {
            sortRows(resultRows, orderBy, resultColumns);
        }

        // Limit
        if (limitStr != null) {
            int limit = Integer.parseInt(limitStr);
            if (resultRows.size() > limit) {
                resultRows = resultRows.subList(0, limit);
            }
        }

        return new ResultSet(resultColumns, resultRows, "SELECT " + resultRows.size());
    }

    private String computeJoinAggregate(List<Map<String, String[]>> rows, String func,
                                         String col, Map<String, TableContext> contexts) {
        if ("COUNT".equals(func)) {
            if ("*".equals(col)) return String.valueOf(rows.size());
            long count = rows.stream().filter(r -> resolveJoinValue(col, r, contexts) != null).count();
            return String.valueOf(count);
        }

        return switch (func) {
            case "SUM" -> {
                double sum = 0;
                for (var row : rows) {
                    String v = resolveJoinValue(col, row, contexts);
                    if (v != null) sum += parseNumber(v);
                }
                yield formatNumber(sum);
            }
            case "AVG" -> {
                double sum = 0;
                int count = 0;
                for (var row : rows) {
                    String v = resolveJoinValue(col, row, contexts);
                    if (v != null) {
                        sum += parseNumber(v);
                        count++;
                    }
                }
                yield count == 0 ? null : formatNumber(sum / count);
            }
            case "MIN" -> {
                String min = null;
                for (var row : rows) {
                    String v = resolveJoinValue(col, row, contexts);
                    if (v != null && (min == null || v.compareTo(min) < 0)) min = v;
                }
                yield min;
            }
            case "MAX" -> {
                String max = null;
                for (var row : rows) {
                    String v = resolveJoinValue(col, row, contexts);
                    if (v != null && (max == null || v.compareTo(max) > 0)) max = v;
                }
                yield max;
            }
            default -> throw new SqlException(SqlState.SYNTAX_ERROR, "Unknown aggregate: " + func);
        };
    }

    // ======== Sorting helper ========

    private void sortRows(List<String[]> rows, String orderBy, List<ColumnDescription> columns) {
        String orderCol = orderBy.trim().split("\\s+")[0].toLowerCase();
        // Strip table qualifier
        if (orderCol.contains(".")) {
            orderCol = orderCol.substring(orderCol.indexOf('.') + 1);
        }
        boolean desc = orderBy.toUpperCase().contains("DESC");
        int orderIdx = -1;
        for (int i = 0; i < columns.size(); i++) {
            String colName = columns.get(i).name().toLowerCase();
            if (colName.contains(".")) {
                colName = colName.substring(colName.indexOf('.') + 1);
            }
            if (colName.equals(orderCol)) {
                orderIdx = i;
                break;
            }
        }
        if (orderIdx >= 0) {
            int oi = orderIdx;
            rows.sort((a, b) -> {
                String va = a[oi] == null ? "" : a[oi];
                String vb = b[oi] == null ? "" : b[oi];
                int cmp = va.compareTo(vb);
                return desc ? -cmp : cmp;
            });
        }
    }

    private static final Pattern UPDATE_PATTERN = Pattern.compile(
            "(?i)UPDATE\\s+(\\w+)\\s+SET\\s+(.+?)(?:\\s+WHERE\\s+(.+))?\\s*$");

    private ResultSet executeUpdate(String sql) {
        Matcher m = UPDATE_PATTERN.matcher(sql.trim());
        if (!m.find()) throw new SqlException(SqlState.SYNTAX_ERROR, "Invalid UPDATE: " + sql);

        String tableName = m.group(1).toLowerCase();
        String setClause = m.group(2);
        String whereClause = m.group(3);

        TableDef table = getTable(tableName);

        // Parse SET assignments
        Map<Integer, String> assignments = new LinkedHashMap<>();
        for (String assignment : splitColumns(setClause)) {
            String[] parts = assignment.split("=", 2);
            String colName = parts[0].trim().toLowerCase();
            String value = unquote(parts[1].trim());
            assignments.put(findColumnIndex(table, colName), value);
        }

        int updateCount = 0;
        for (String[] row : table.rows()) {
            if (whereClause == null || matchesWhere(table, row, whereClause)) {
                for (var entry : assignments.entrySet()) {
                    row[entry.getKey()] = entry.getValue();
                }
                updateCount++;
            }
        }

        return ResultSet.commandOnly("UPDATE " + updateCount);
    }

    private static final Pattern DELETE_PATTERN = Pattern.compile(
            "(?i)DELETE\\s+FROM\\s+(\\w+)(?:\\s+WHERE\\s+(.+))?\\s*$");

    private ResultSet executeDelete(String sql) {
        Matcher m = DELETE_PATTERN.matcher(sql.trim());
        if (!m.find()) throw new SqlException(SqlState.SYNTAX_ERROR, "Invalid DELETE: " + sql);

        String tableName = m.group(1).toLowerCase();
        String whereClause = m.group(2);

        TableDef table = getTable(tableName);

        int deleteCount;
        if (whereClause == null) {
            deleteCount = table.rows().size();
            table.rows().clear();
        } else {
            List<String[]> toRemove = new ArrayList<>();
            for (String[] row : table.rows()) {
                if (matchesWhere(table, row, whereClause)) {
                    toRemove.add(row);
                }
            }
            deleteCount = toRemove.size();
            table.rows().removeAll(toRemove);
        }

        return ResultSet.commandOnly("DELETE " + deleteCount);
    }

    private static final Pattern DROP_TABLE_PATTERN = Pattern.compile(
            "(?i)DROP\\s+TABLE\\s+(?:IF\\s+EXISTS\\s+)?(\\w+)");

    private ResultSet executeDropTable(String sql) {
        Matcher m = DROP_TABLE_PATTERN.matcher(sql);
        if (!m.find()) throw new SqlException(SqlState.SYNTAX_ERROR, "Invalid DROP TABLE: " + sql);

        String tableName = m.group(1).toLowerCase();
        if (tables.remove(tableName) == null && !sql.toUpperCase().contains("IF EXISTS")) {
            throw new SqlException(SqlState.UNDEFINED_TABLE, "Table does not exist: " + tableName);
        }
        return ResultSet.commandOnly("DROP TABLE");
    }

    // ======== Helpers ========

    private TableDef getTable(String name) {
        TableDef table = tables.get(name);
        if (table == null) {
            throw new SqlException(SqlState.UNDEFINED_TABLE, "Table does not exist: " + name);
        }
        return table;
    }

    private int findColumnIndex(TableDef table, String colName) {
        for (int i = 0; i < table.columns().size(); i++) {
            if (table.columns().get(i).name().equals(colName)) {
                return i;
            }
        }
        throw new SqlException(SqlState.UNDEFINED_COLUMN, "Column not found: " + colName);
    }

    private boolean matchesWhere(TableDef table, String[] row, String whereClause) {
        // Simple: col = 'value' or col = value, with AND support
        String[] conditions = whereClause.split("(?i)\\s+AND\\s+");
        for (String cond : conditions) {
            cond = cond.trim();
            String[] parts;
            boolean notEqual = false;
            if (cond.contains("!=") || cond.contains("<>")) {
                parts = cond.split("!=|<>", 2);
                notEqual = true;
            } else {
                parts = cond.split("=", 2);
            }
            if (parts.length != 2) return false;
            String colName = parts[0].trim().toLowerCase();
            // Strip table qualifier if present
            if (colName.contains(".")) {
                colName = colName.substring(colName.indexOf('.') + 1);
            }
            String expected = unquote(parts[1].trim());
            int colIdx = findColumnIndex(table, colName);
            String actual = row[colIdx];
            boolean eq = Objects.equals(expected, actual);
            if (notEqual ? eq : !eq) return false;
        }
        return true;
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("'") && s.endsWith("'")) {
            return s.substring(1, s.length() - 1).replace("''", "'");
        }
        if (s.equalsIgnoreCase("NULL")) return null;
        return s;
    }

    private static String substituteParams(String sql, String[] params) {
        String result = sql;
        for (int i = params.length; i >= 1; i--) {
            String param = params[i - 1];
            String replacement = param == null ? "NULL" : "'" + param.replace("'", "''") + "'";
            result = result.replace("$" + i, replacement);
        }
        return result;
    }

    private static ColumnDescription makeColDesc(ColDef col) {
        return new ColumnDescription(
                col.name(), 0, (short) 0,
                col.type().oid(), (short) col.type().typeSize(),
                -1, (short) 0);
    }

    private static ColumnDescription makeColDesc(String name, PgType type) {
        return new ColumnDescription(
                name, 0, (short) 0,
                type.oid(), (short) type.typeSize(),
                -1, (short) 0);
    }

    /**
     * Splits column definitions or SET clauses, respecting parentheses.
     */
    private static List<String> splitColumns(String s) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                result.add(s.substring(start, i));
                start = i + 1;
            }
        }
        result.add(s.substring(start));
        return result;
    }

    /**
     * Splits VALUES list respecting quoted strings.
     */
    private static List<String> splitValues(String s) {
        List<String> result = new ArrayList<>();
        boolean inQuote = false;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'' && (i == 0 || s.charAt(i - 1) != '\'')) {
                inQuote = !inQuote;
            } else if (c == ',' && !inQuote) {
                result.add(s.substring(start, i));
                start = i + 1;
            }
        }
        result.add(s.substring(start));
        return result;
    }

    /**
     * Exception thrown for SQL errors, carrying the SQLSTATE code.
     */
    public static final class SqlException extends RuntimeException {
        private final SqlState sqlState;

        /**
         * Creates a new SQL exception.
         *
         * @param sqlState the SQLSTATE code
         * @param message  the error message
         */
        public SqlException(SqlState sqlState, String message) {
            super(message);
            this.sqlState = sqlState;
        }

        /**
         * Returns the SQLSTATE code.
         *
         * @return the SQLSTATE
         */
        public SqlState sqlState() {
            return sqlState;
        }
    }
}
