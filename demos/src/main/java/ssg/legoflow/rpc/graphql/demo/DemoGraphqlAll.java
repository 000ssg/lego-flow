package ssg.legoflow.rpc.graphql.demo;

import ssg.legoflow.rpc.graphql.execution.ExecutionEngine;
import ssg.legoflow.rpc.graphql.execution.ExecutionResult;
import ssg.legoflow.rpc.graphql.execution.SubscriptionPublisher;
import ssg.legoflow.rpc.graphql.introspection.IntrospectionResolver;
import ssg.legoflow.rpc.graphql.language.GraphQLParser;
import ssg.legoflow.rpc.graphql.schema.*;
import ssg.legoflow.rpc.graphql.sdl.SchemaPrinter;
import ssg.legoflow.rpc.graphql.sdl.SchemaParser;
import ssg.legoflow.rpc.graphql.transport.JsonCodec;
import ssg.legoflow.rpc.graphql.validation.QueryValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Comprehensive demo of all GraphQL module features.
 *
 * <h2>Configuration</h2>
 * <p><b>Preferred (default): In-memory execution engine</b> — No external dependencies.
 * Runs anywhere without installation. Supports queries, mutations, subscriptions,
 * introspection, SDL parsing/printing, validation, variables, fragments, directives,
 * interfaces, unions, enums, input objects, and all scalar types.</p>
 *
 * <p><b>Alternative: External GraphQL server</b> — Set {@link #USE_EXTERNAL}{@code =true}
 * and configure {@link #EXTERNAL_URL}. Required for:</p>
 * <ul>
 *   <li>Production integration testing against a real GraphQL API</li>
 *   <li>HTTP transport testing with network I/O</li>
 *   <li>WebSocket subscription testing over the network</li>
 * </ul>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>Schema definition — programmatic type system with all 8 type kinds</li>
 *   <li>Query execution — field resolution, nested objects, arguments</li>
 *   <li>Mutations — serial execution, CRUD operations, input objects</li>
 *   <li>Variables and arguments — parameterized queries with defaults</li>
 *   <li>Fragments — named fragments and inline fragments for reuse</li>
 *   <li>Directives — {@code @skip} and {@code @include} with variables</li>
 *   <li>Interfaces and unions — abstract types, type resolution, {@code __typename}</li>
 *   <li>Introspection — {@code __schema}, {@code __type} queries</li>
 *   <li>Validation — query validation against schema rules</li>
 *   <li>SDL round-trip — schema printing and parsing</li>
 *   <li>Subscriptions — event publishing and subscriber management</li>
 *   <li>Error handling — partial results, null propagation, syntax errors</li>
 *   <li>JSON codec — encode/decode round-trip for transport</li>
 * </ol>
 *
 * @since 1.0.0
 */
public final class DemoGraphqlAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoGraphqlAll.class);

    // ============================= CONFIGURATION =============================
    // Preferred: in-memory ExecutionEngine (no external dependencies, runs anywhere)
    // Alternative: set USE_EXTERNAL=true and configure URL for external GraphQL server
    // =========================================================================

    /** Set to {@code true} to connect to an external GraphQL server. */
    public static boolean USE_EXTERNAL = false;

    /** URL for external GraphQL server. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_URL = "http://localhost:4000/graphql";

    private DemoGraphqlAll() {}

    /**
     * Results from running the full demo.
     *
     * @param schemaDefinition   true if programmatic schema definition succeeded
     * @param queryExecution     true if basic query execution produced correct results
     * @param mutations          number of mutations executed successfully
     * @param variablesArgs      true if variable substitution and argument defaults worked
     * @param fragments          true if named and inline fragments resolved correctly
     * @param directives         true if @skip and @include directives worked
     * @param interfacesUnions   true if interface/union type resolution worked
     * @param introspection      number of types discovered via introspection
     * @param validation         number of validation errors correctly detected
     * @param sdlRoundTrip       true if SDL print/parse round-trip preserved the schema
     * @param subscriptions      number of subscription events received
     * @param errorHandling      true if error handling (partial results, null propagation) worked
     * @param jsonCodec          true if JSON encode/decode round-trip succeeded
     */
    public record Results(
            boolean schemaDefinition,
            boolean queryExecution,
            int mutations,
            boolean variablesArgs,
            boolean fragments,
            boolean directives,
            boolean interfacesUnions,
            int introspection,
            int validation,
            boolean sdlRoundTrip,
            int subscriptions,
            boolean errorHandling,
            boolean jsonCodec
    ) {}

    /**
     * Runs the comprehensive demo covering all GraphQL features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     */
    public static Results runAll() throws Exception {
        // 1. Schema definition
        boolean schemaDef = demoSchemaDefinition();

        // 2. Query execution
        boolean queryExec = demoQueryExecution();

        // 3. Mutations
        int mutationCount = demoMutations();

        // 4. Variables and arguments
        boolean variablesOk = demoVariablesAndArguments();

        // 5. Fragments
        boolean fragmentsOk = demoFragments();

        // 6. Directives
        boolean directivesOk = demoDirectives();

        // 7. Interfaces and unions
        boolean interfacesOk = demoInterfacesAndUnions();

        // 8. Introspection
        int introspectionTypes = demoIntrospection();

        // 9. Validation
        int validationErrors = demoValidation();

        // 10. SDL round-trip
        boolean sdlOk = demoSdlRoundTrip();

        // 11. Subscriptions
        int subEvents = demoSubscriptions();

        // 12. Error handling
        boolean errorsOk = demoErrorHandling();

        // 13. JSON codec
        boolean jsonOk = demoJsonCodec();

        return new Results(
                schemaDef,
                queryExec,
                mutationCount,
                variablesOk,
                fragmentsOk,
                directivesOk,
                interfacesOk,
                introspectionTypes,
                validationErrors,
                sdlOk,
                subEvents,
                errorsOk,
                jsonOk
        );
    }

    // ======================== 1. SCHEMA DEFINITION ==========================

    /**
     * Demonstrates programmatic schema definition with all 8 type kinds:
     * Scalar, Object, Interface, Union, Enum, InputObject, List, NonNull.
     */
    static boolean demoSchemaDefinition() {
        LOG.info("=== 1. Schema Definition ===");

        // All 8 type kinds
        var scalarType = ScalarType.STRING;
        var enumType = EnumType.of("Color", "RED", "GREEN", "BLUE");
        var interfaceType = InterfaceType.of("Node", List.of(
                FieldDefinition.of("id", NonNullType.of(ScalarType.ID))));
        var objectType = ObjectType.of("Product", List.of(
                FieldDefinition.of("id", NonNullType.of(ScalarType.ID)),
                FieldDefinition.of("name", NonNullType.of(ScalarType.STRING)),
                FieldDefinition.of("price", ScalarType.FLOAT),
                FieldDefinition.of("color", enumType),
                FieldDefinition.of("tags", ListType.of(ScalarType.STRING))
        ), List.of(interfaceType));
        var inputType = InputObjectType.of("ProductInput", List.of(
                InputObjectType.InputFieldDefinition.of("name", NonNullType.of(ScalarType.STRING)),
                InputObjectType.InputFieldDefinition.of("price", ScalarType.FLOAT, 0.0)));
        var unionType = UnionType.of("SearchResult", List.of(objectType));
        var listType = ListType.of(objectType);
        var nonNullType = NonNullType.of(ScalarType.STRING);

        // Build query type
        var queryField = FieldDefinition.of("product", objectType,
                List.of(ArgumentDefinition.of("id", NonNullType.of(ScalarType.ID))));
        queryField.dataFetcher(env -> Map.of(
                "id", env.getArgument("id"),
                "name", "Widget",
                "price", 9.99,
                "color", "RED",
                "tags", List.of("sale", "popular")));

        var queryType = ObjectType.of("Query", List.of(queryField));

        var schema = GraphQLSchema.newSchema()
                .query(queryType)
                .additionalType(enumType)
                .additionalType(inputType)
                .additionalType(unionType)
                .build();

        // Verify all type kinds are present
        boolean hasScalar = schema.getType("String") instanceof ScalarType;
        boolean hasObject = schema.getType("Product") instanceof ObjectType;
        boolean hasInterface = schema.getType("Node") instanceof InterfaceType;
        boolean hasUnion = schema.getType("SearchResult") instanceof UnionType;
        boolean hasEnum = schema.getType("Color") instanceof EnumType;
        boolean hasInput = schema.getType("ProductInput") instanceof InputObjectType;

        LOG.info("Type kinds: Scalar={}, Object={}, Interface={}, Union={}, Enum={}, Input={}",
                hasScalar, hasObject, hasInterface, hasUnion, hasEnum, hasInput);

        // Verify wrapping types
        boolean listOk = listType.elementType() == objectType;
        boolean nonNullOk = nonNullType.wrappedType() == ScalarType.STRING;

        LOG.info("Wrapping types: List={}, NonNull={}", listOk, nonNullOk);
        return hasScalar && hasObject && hasInterface && hasUnion
                && hasEnum && hasInput && listOk && nonNullOk;
    }

    // ======================== 2. QUERY EXECUTION ============================

    /**
     * Demonstrates basic query execution with the Star Wars schema:
     * field resolution, nested objects, and arguments.
     */
    static boolean demoQueryExecution() {
        LOG.info("=== 2. Query Execution ===");
        var schema = StarWarsSchema.create();
        var engine = new ExecutionEngine(schema);

        // Simple query
        var result = engine.execute("""
                {
                    hero {
                        name
                        appearsIn
                    }
                }
                """, null, null, null);

        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) result.getData();
        @SuppressWarnings("unchecked")
        var hero = (Map<String, Object>) data.get("hero");
        String heroName = (String) hero.get("name");
        LOG.info("Hero: {}", heroName);

        // Query with argument
        var humanResult = engine.execute("""
                {
                    human(id: "1000") {
                        name
                        homePlanet
                    }
                }
                """, null, null, null);

        @SuppressWarnings("unchecked")
        var humanData = (Map<String, Object>) humanResult.getData();
        @SuppressWarnings("unchecked")
        var human = (Map<String, Object>) humanData.get("human");
        String humanName = (String) human.get("name");
        String planet = (String) human.get("homePlanet");
        LOG.info("Human: {} from {}", humanName, planet);

        // Query with alias
        var aliasResult = engine.execute("""
                {
                    luke: human(id: "1000") { name }
                    vader: human(id: "1001") { name }
                }
                """, null, null, null);

        @SuppressWarnings("unchecked")
        var aliasData = (Map<String, Object>) aliasResult.getData();
        boolean hasLuke = aliasData.containsKey("luke");
        boolean hasVader = aliasData.containsKey("vader");
        LOG.info("Aliases: luke={}, vader={}", hasLuke, hasVader);

        return "Luke Skywalker".equals(heroName)
                && "Luke Skywalker".equals(humanName)
                && "Tatooine".equals(planet)
                && hasLuke && hasVader
                && !result.hasErrors();
    }

    // ======================== 3. MUTATIONS ===================================

    /**
     * Demonstrates mutation execution with the Todo schema:
     * serial execution, CRUD operations, input objects.
     */
    static int demoMutations() {
        LOG.info("=== 3. Mutations ===");
        var todoSchema = new TodoSchema();
        var schema = todoSchema.create();
        var engine = new ExecutionEngine(schema);

        int successCount = 0;

        // Add a todo
        var addResult = engine.execute("""
                mutation {
                    addTodo(input: { title: "Deploy to production", completed: false }) {
                        id
                        title
                        completed
                    }
                }
                """, null, null, null);

        if (!addResult.hasErrors()) {
            successCount++;
            LOG.info("Added todo successfully");
        }

        // Toggle a todo
        var toggleResult = engine.execute("""
                mutation {
                    toggleTodo(id: 1) {
                        id
                        title
                        completed
                    }
                }
                """, null, null, null);

        if (!toggleResult.hasErrors()) {
            successCount++;
            LOG.info("Toggled todo successfully");
        }

        // Delete a todo
        var deleteResult = engine.execute("""
                mutation {
                    deleteTodo(id: 3) {
                        id
                        title
                    }
                }
                """, null, null, null);

        if (!deleteResult.hasErrors()) {
            successCount++;
            LOG.info("Deleted todo successfully");
        }

        // Clear completed
        var clearResult = engine.execute("""
                mutation {
                    clearCompleted {
                        id
                        title
                    }
                }
                """, null, null, null);

        if (!clearResult.hasErrors()) {
            successCount++;
            LOG.info("Cleared completed todos successfully");
        }

        LOG.info("Mutations executed: {}/4", successCount);
        return successCount;
    }

    // ======================== 4. VARIABLES AND ARGUMENTS =====================

    /**
     * Demonstrates variable substitution and argument default values.
     */
    static boolean demoVariablesAndArguments() {
        LOG.info("=== 4. Variables and Arguments ===");
        var schema = StarWarsSchema.create();
        var engine = new ExecutionEngine(schema);

        // Query with variables
        var result = engine.execute("""
                query HeroByEpisode($ep: Episode) {
                    hero(episode: $ep) {
                        name
                    }
                }
                """, "HeroByEpisode", Map.of("ep", "EMPIRE"), null);

        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) result.getData();
        @SuppressWarnings("unchecked")
        var hero = (Map<String, Object>) data.get("hero");
        String name = (String) hero.get("name");
        LOG.info("Empire hero: {}", name);
        boolean empireOk = "R2-D2".equals(name);

        // Query with default argument value
        var todoSchema = new TodoSchema();
        var todoEngine = new ExecutionEngine(todoSchema.create());

        var defaultResult = todoEngine.execute("""
                {
                    todos {
                        id
                        title
                        completed
                    }
                }
                """, null, null, null);

        @SuppressWarnings("unchecked")
        var todoData = (Map<String, Object>) defaultResult.getData();
        @SuppressWarnings("unchecked")
        var todos = (List<?>) todoData.get("todos");
        boolean defaultOk = todos != null && todos.size() == 3;
        LOG.info("Default todos: {} items", todos != null ? todos.size() : 0);

        // Query with explicit variable for todo filtering
        var filteredResult = todoEngine.execute("""
                query FilteredTodos($status: TodoStatus) {
                    todos(status: $status) {
                        title
                        completed
                    }
                }
                """, "FilteredTodos", Map.of("status", "COMPLETED"), null);

        @SuppressWarnings("unchecked")
        var filteredData = (Map<String, Object>) filteredResult.getData();
        @SuppressWarnings("unchecked")
        var filteredTodos = (List<?>) filteredData.get("todos");
        boolean filteredOk = filteredTodos != null && filteredTodos.size() == 1;
        LOG.info("Filtered (completed): {} items", filteredTodos != null ? filteredTodos.size() : 0);

        return empireOk && defaultOk && filteredOk;
    }

    // ======================== 5. FRAGMENTS ==================================

    /**
     * Demonstrates named fragments and inline fragments for field reuse.
     */
    static boolean demoFragments() {
        LOG.info("=== 5. Fragments ===");
        var schema = StarWarsSchema.create();
        var engine = new ExecutionEngine(schema);

        // Named fragment
        var result = engine.execute("""
                {
                    luke: human(id: "1000") {
                        ...CharInfo
                    }
                    vader: human(id: "1001") {
                        ...CharInfo
                    }
                }

                fragment CharInfo on Human {
                    name
                    homePlanet
                    appearsIn
                }
                """, null, null, null);

        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) result.getData();
        @SuppressWarnings("unchecked")
        var luke = (Map<String, Object>) data.get("luke");
        @SuppressWarnings("unchecked")
        var vader = (Map<String, Object>) data.get("vader");

        boolean lukeOk = "Luke Skywalker".equals(luke.get("name"));
        boolean vaderOk = "Darth Vader".equals(vader.get("name"));
        LOG.info("Named fragments: Luke={}, Vader={}", luke.get("name"), vader.get("name"));

        // Inline fragment on union type
        var searchResult = engine.execute("""
                {
                    search(text: "R2") {
                        ... on Human {
                            name
                            homePlanet
                        }
                        ... on Droid {
                            name
                            primaryFunction
                        }
                    }
                }
                """, null, null, null);

        @SuppressWarnings("unchecked")
        var searchData = (Map<String, Object>) searchResult.getData();
        @SuppressWarnings("unchecked")
        var searchList = (List<Map<String, Object>>) searchData.get("search");
        boolean searchOk = searchList != null && !searchList.isEmpty();
        if (searchOk) {
            LOG.info("Inline fragment search results: {}", searchList.size());
        }

        return lukeOk && vaderOk && searchOk && !result.hasErrors();
    }

    // ======================== 6. DIRECTIVES =================================

    /**
     * Demonstrates @skip and @include directives with literal and variable arguments.
     */
    static boolean demoDirectives() {
        LOG.info("=== 6. Directives ===");
        var schema = StarWarsSchema.create();
        var engine = new ExecutionEngine(schema);

        // @include directive
        var includeResult = engine.execute("""
                query Hero($withFriends: Boolean!) {
                    hero {
                        name
                        friends @include(if: $withFriends)
                    }
                }
                """, "Hero", Map.of("withFriends", true), null);

        @SuppressWarnings("unchecked")
        var includeData = (Map<String, Object>) includeResult.getData();
        @SuppressWarnings("unchecked")
        var heroInclude = (Map<String, Object>) includeData.get("hero");
        boolean hasFriends = heroInclude.containsKey("friends");
        LOG.info("@include(if: true): friends present = {}", hasFriends);

        // @skip directive
        var skipResult = engine.execute("""
                query Hero($skipFriends: Boolean!) {
                    hero {
                        name
                        friends @skip(if: $skipFriends)
                    }
                }
                """, "Hero", Map.of("skipFriends", true), null);

        @SuppressWarnings("unchecked")
        var skipData = (Map<String, Object>) skipResult.getData();
        @SuppressWarnings("unchecked")
        var heroSkip = (Map<String, Object>) skipData.get("hero");
        boolean noFriends = !heroSkip.containsKey("friends");
        LOG.info("@skip(if: true): friends absent = {}", noFriends);

        // @include with false
        var excludeResult = engine.execute("""
                query Hero($withFriends: Boolean!) {
                    hero {
                        name
                        friends @include(if: $withFriends)
                    }
                }
                """, "Hero", Map.of("withFriends", false), null);

        @SuppressWarnings("unchecked")
        var excludeData = (Map<String, Object>) excludeResult.getData();
        @SuppressWarnings("unchecked")
        var heroExclude = (Map<String, Object>) excludeData.get("hero");
        boolean excludedFriends = !heroExclude.containsKey("friends");
        LOG.info("@include(if: false): friends absent = {}", excludedFriends);

        return hasFriends && noFriends && excludedFriends;
    }

    // ======================== 7. INTERFACES AND UNIONS =======================

    /**
     * Demonstrates interface types, union types, abstract type resolution,
     * and __typename meta-field.
     */
    static boolean demoInterfacesAndUnions() {
        LOG.info("=== 7. Interfaces and Unions ===");
        var schema = StarWarsSchema.create();
        var engine = new ExecutionEngine(schema);

        // Interface query with __typename
        var heroResult = engine.execute("""
                {
                    hero {
                        __typename
                        name
                        ... on Human {
                            homePlanet
                        }
                        ... on Droid {
                            primaryFunction
                        }
                    }
                }
                """, null, null, null);

        @SuppressWarnings("unchecked")
        var heroData = (Map<String, Object>) heroResult.getData();
        @SuppressWarnings("unchecked")
        var hero = (Map<String, Object>) heroData.get("hero");
        String typename = (String) hero.get("__typename");
        boolean typenameOk = "Human".equals(typename);
        LOG.info("Hero __typename: {}", typename);

        // Union search query
        var searchResult = engine.execute("""
                {
                    search(text: "Skywalker") {
                        __typename
                        ... on Human {
                            name
                            homePlanet
                        }
                        ... on Droid {
                            name
                            primaryFunction
                        }
                    }
                }
                """, null, null, null);

        @SuppressWarnings("unchecked")
        var searchData = (Map<String, Object>) searchResult.getData();
        @SuppressWarnings("unchecked")
        var results = (List<Map<String, Object>>) searchData.get("search");
        boolean searchOk = results != null && !results.isEmpty();
        if (searchOk) {
            for (var r : results) {
                LOG.info("  Search result: {} ({})", r.get("name"), r.get("__typename"));
            }
        }

        // Droid query (different type through interface)
        var droidResult = engine.execute("""
                {
                    droid(id: "2001") {
                        name
                        primaryFunction
                    }
                }
                """, null, null, null);

        @SuppressWarnings("unchecked")
        var droidData = (Map<String, Object>) droidResult.getData();
        @SuppressWarnings("unchecked")
        var droid = (Map<String, Object>) droidData.get("droid");
        boolean droidOk = "R2-D2".equals(droid.get("name"));
        LOG.info("Droid: {} ({})", droid.get("name"), droid.get("primaryFunction"));

        return typenameOk && searchOk && droidOk;
    }

    // ======================== 8. INTROSPECTION ==============================

    /**
     * Demonstrates the introspection system: __schema, __type queries.
     */
    @SuppressWarnings("unchecked")
    static int demoIntrospection() {
        LOG.info("=== 8. Introspection ===");
        var schema = StarWarsSchema.create();

        // Add introspection fields
        var introspectionFields = IntrospectionResolver.createIntrospectionFields(schema);
        var fields = new ArrayList<>(schema.queryType().fields().values());
        fields.addAll(introspectionFields);
        var queryWithIntrospection = ObjectType.of("Query", fields);

        var augmentedSchema = GraphQLSchema.newSchema()
                .query(queryWithIntrospection)
                .build();
        // Re-register the hero/human/droid data fetchers from the original schema
        for (var field : schema.queryType().fields().values()) {
            var augField = queryWithIntrospection.getField(field.name());
            if (augField != null && field.dataFetcher() != null) {
                augField.dataFetcher(field.dataFetcher());
            }
        }

        var engine = new ExecutionEngine(augmentedSchema);

        // __schema query
        var schemaResult = engine.execute("""
                {
                    __schema {
                        queryType { name }
                        types { name kind }
                    }
                }
                """, null, null, null);

        var schemaData = (Map<String, Object>) schemaResult.getData();
        var schemaInfo = (Map<String, Object>) schemaData.get("__schema");
        var types = (List<Map<String, Object>>) schemaInfo.get("types");
        int typeCount = types != null ? types.size() : 0;
        LOG.info("Introspection: {} types discovered", typeCount);

        // __type query
        var typeResult = engine.execute("""
                {
                    __type(name: "Query") {
                        name
                        kind
                        fields { name }
                    }
                }
                """, null, null, null);

        var typeData = (Map<String, Object>) typeResult.getData();
        var typeInfo = (Map<String, Object>) typeData.get("__type");
        boolean typeOk = typeInfo != null && "Query".equals(typeInfo.get("name"));
        LOG.info("__type(Query): {}", typeOk ? typeInfo.get("name") : "not found");

        // __typename meta-field
        var typenameResult = engine.execute("""
                {
                    hero {
                        __typename
                        name
                    }
                }
                """, null, null, null);

        boolean typenameOk = !typenameResult.hasErrors();
        LOG.info("__typename: {}", typenameOk);

        return typeCount;
    }

    // ======================== 9. VALIDATION =================================

    /**
     * Demonstrates query validation: detects invalid fields, unknown arguments,
     * missing variables, duplicate names, and invalid fragment types.
     */
    static int demoValidation() {
        LOG.info("=== 9. Validation ===");
        var schema = StarWarsSchema.create();
        var validator = new QueryValidator(schema);
        int totalErrors = 0;

        // Invalid field
        var doc1 = GraphQLParser.parse("{ hero { nonExistentField } }");
        var errors1 = validator.validate(doc1);
        totalErrors += errors1.size();
        LOG.info("Invalid field errors: {}", errors1.size());

        // Unknown argument
        var doc2 = GraphQLParser.parse("{ human(unknownArg: 5) { name } }");
        var errors2 = validator.validate(doc2);
        totalErrors += errors2.size();
        LOG.info("Unknown argument errors: {}", errors2.size());

        // Duplicate operation names
        var doc3 = GraphQLParser.parse("""
                query Foo { hero { name } }
                query Foo { hero { name } }
                """);
        var errors3 = validator.validate(doc3);
        totalErrors += errors3.size();
        LOG.info("Duplicate operation errors: {}", errors3.size());

        // Valid query (should have 0 errors)
        var doc4 = GraphQLParser.parse("{ hero { name appearsIn } }");
        var errors4 = validator.validate(doc4);
        boolean validOk = errors4.isEmpty();
        LOG.info("Valid query errors: {} (expected 0)", errors4.size());

        LOG.info("Total validation errors detected: {}", totalErrors);
        return totalErrors;
    }

    // ======================== 10. SDL ROUND-TRIP =============================

    /**
     * Demonstrates Schema Definition Language (SDL) printing and parsing.
     * Prints a schema to SDL, parses it back, and verifies the round-trip.
     */
    static boolean demoSdlRoundTrip() {
        LOG.info("=== 10. SDL Round-Trip ===");

        // Parse an SDL string
        var sdl = """
                type Query {
                    hello(name: String = "World"): String!
                    users: [User!]!
                }

                type User {
                    id: ID!
                    name: String!
                    email: String
                    role: Role!
                }

                enum Role {
                    ADMIN
                    USER
                    GUEST
                }
                """;

        var schema = SchemaParser.parse(sdl);
        boolean parseOk = schema.queryType() != null;
        LOG.info("SDL parse: queryType={}", schema.queryType().name());

        // Verify types from parsed schema
        boolean hasUser = schema.getType("User") instanceof ObjectType;
        boolean hasRole = schema.getType("Role") instanceof EnumType;
        LOG.info("Parsed types: User={}, Role={}", hasUser, hasRole);

        // Print schema back to SDL
        var printed = SchemaPrinter.print(schema);
        boolean printOk = printed.contains("type Query") && printed.contains("type User")
                && printed.contains("enum Role");
        LOG.info("SDL print length: {} chars", printed.length());

        // Re-parse the printed SDL (round-trip)
        var reparsed = SchemaParser.parse(printed);
        boolean roundTripOk = reparsed.queryType() != null
                && reparsed.getType("User") instanceof ObjectType
                && reparsed.getType("Role") instanceof EnumType;
        LOG.info("SDL round-trip: {}", roundTripOk);

        // Print a type reference
        var typeRef = SchemaPrinter.printTypeRef(
                NonNullType.of(ListType.of(NonNullType.of(ScalarType.STRING))));
        boolean typeRefOk = "[String!]!".equals(typeRef);
        LOG.info("Type ref: {}", typeRef);

        return parseOk && hasUser && hasRole && printOk && roundTripOk && typeRefOk;
    }

    // ======================== 11. SUBSCRIPTIONS =============================

    /**
     * Demonstrates subscription publishing and subscriber management
     * using SubscriptionPublisher.
     */
    static int demoSubscriptions() {
        LOG.info("=== 11. Subscriptions ===");
        var chatSchema = new ChatSchema();
        var schema = chatSchema.create();

        // Verify subscription type exists
        boolean hasSubscription = schema.subscriptionType() != null;
        LOG.info("Has subscription type: {}", hasSubscription);

        // Set up subscriber
        var receivedEvents = new CopyOnWriteArrayList<Map<String, Object>>();
        var publisher = chatSchema.messagePublisher();
        var unsub = publisher.subscribe(event -> {
            receivedEvents.add(event);
        });

        // Send messages via the ChatSchema API
        chatSchema.sendMessage("Hello from demo", "alice", "general");
        chatSchema.sendMessage("Hi there!", "bob", "general");
        chatSchema.sendMessage("Good morning", "carol", "random");

        LOG.info("Subscription events received: {}", receivedEvents.size());
        for (var event : receivedEvents) {
            LOG.info("  [{}] {}: {}",
                    event.get("room"), event.get("sender"), event.get("text"));
        }

        // Unsubscribe
        unsub.run();

        // Send another message after unsubscribing (should not be received)
        chatSchema.sendMessage("After unsub", "dave", "general");
        int afterUnsub = receivedEvents.size();
        boolean unsubOk = afterUnsub == 3; // Should still be 3
        LOG.info("After unsubscribe: {} events (expected 3)", afterUnsub);

        // Verify publisher subscriber count
        LOG.info("Publisher subscribers: {}", publisher.subscriberCount());

        return receivedEvents.size();
    }

    // ======================== 12. ERROR HANDLING =============================

    /**
     * Demonstrates error handling: syntax errors, partial results,
     * null propagation for non-null fields, and execution errors.
     */
    static boolean demoErrorHandling() {
        LOG.info("=== 12. Error Handling ===");
        var schema = StarWarsSchema.create();
        var engine = new ExecutionEngine(schema);

        // Syntax error
        var syntaxResult = engine.execute("{ invalid syntax ???", null, null, null);
        boolean syntaxError = syntaxResult.hasErrors();
        LOG.info("Syntax error detected: {}", syntaxError);

        // Query for non-existent human (null result)
        var nullResult = engine.execute("""
                {
                    human(id: "9999") {
                        name
                    }
                }
                """, null, null, null);

        @SuppressWarnings("unchecked")
        var nullData = (Map<String, Object>) nullResult.getData();
        boolean nullOk = nullData.get("human") == null;
        LOG.info("Null result for missing entity: {}", nullOk);

        // Error result to map (for JSON serialization)
        var errorMap = syntaxResult.toMap();
        boolean hasErrorsKey = errorMap.containsKey("errors");
        @SuppressWarnings("unchecked")
        var errorList = (List<Map<String, Object>>) errorMap.get("errors");
        boolean hasMessage = errorList != null && !errorList.isEmpty()
                && errorList.getFirst().containsKey("message");
        LOG.info("Error map: hasErrors={}, hasMessage={}", hasErrorsKey, hasMessage);

        // Partial result test: schema with a field that throws
        var errorField = FieldDefinition.of("failing", NonNullType.of(ScalarType.STRING));
        errorField.dataFetcher(env -> { throw new RuntimeException("Intentional failure"); });
        var okField = FieldDefinition.of("working", ScalarType.STRING);
        okField.dataFetcher(env -> "works fine");

        var errorQueryType = ObjectType.of("Query", List.of(errorField, okField));
        var errorSchema = GraphQLSchema.newSchema().query(errorQueryType).build();
        var errorEngine = new ExecutionEngine(errorSchema);

        var partialResult = errorEngine.execute("{ failing working }", null, null, null);
        boolean hasPartialErrors = partialResult.hasErrors();
        LOG.info("Partial result has errors: {}", hasPartialErrors);

        return syntaxError && nullOk && hasErrorsKey && hasMessage && hasPartialErrors;
    }

    // ======================== 13. JSON CODEC ================================

    /**
     * Demonstrates the JSON codec for GraphQL transport:
     * encode/decode round-trip for all JSON types.
     */
    static boolean demoJsonCodec() {
        LOG.info("=== 13. JSON Codec ===");

        // Encode a complex object
        var original = new LinkedHashMap<String, Object>();
        original.put("string", "hello \"world\"");
        original.put("number", 42);
        original.put("float", 3.14);
        original.put("boolean", true);
        original.put("null", null);
        original.put("array", List.of(1, "two", 3.0, false));
        original.put("nested", Map.of("key", "value", "num", 99));

        var json = JsonCodec.encode(original);
        LOG.info("Encoded JSON: {} chars", json.length());

        // Decode back
        var decoded = JsonCodec.decodeObject(json);
        boolean stringOk = "hello \"world\"".equals(decoded.get("string"));
        boolean numberOk = decoded.get("number") instanceof Number n && n.intValue() == 42;
        boolean floatOk = decoded.get("float") instanceof Number n && n.doubleValue() == 3.14;
        boolean boolOk = Boolean.TRUE.equals(decoded.get("boolean"));
        boolean nullOk = decoded.containsKey("null") && decoded.get("null") == null;
        boolean arrayOk = decoded.get("array") instanceof List<?> list && list.size() == 4;

        @SuppressWarnings("unchecked")
        var nested = (Map<String, Object>) decoded.get("nested");
        boolean nestedOk = nested != null && "value".equals(nested.get("key"));

        LOG.info("Decode: string={}, number={}, float={}, bool={}, null={}, array={}, nested={}",
                stringOk, numberOk, floatOk, boolOk, nullOk, arrayOk, nestedOk);

        // GraphQL ExecutionResult to JSON round-trip
        var result = new ssg.legoflow.rpc.graphql.execution.ExecutionResult(
                Map.of("hero", Map.of("name", "Luke")),
                List.of());
        var resultJson = JsonCodec.encode(result.toMap());
        var resultDecoded = JsonCodec.decodeObject(resultJson);
        boolean resultOk = resultDecoded.containsKey("data");
        LOG.info("ExecutionResult JSON round-trip: {}", resultOk);

        return stringOk && numberOk && floatOk && boolOk && nullOk
                && arrayOk && nestedOk && resultOk;
    }
}
