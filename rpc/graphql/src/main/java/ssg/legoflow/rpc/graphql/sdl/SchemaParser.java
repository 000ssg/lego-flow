package ssg.legoflow.rpc.graphql.sdl;

import ssg.legoflow.rpc.graphql.language.GraphQLLexer;
import ssg.legoflow.rpc.graphql.language.GraphQLSyntaxException;
import ssg.legoflow.rpc.graphql.language.Token;
import ssg.legoflow.rpc.graphql.schema.*;

import java.util.*;

/**
 * Parses a Schema Definition Language (SDL) string into a GraphQLSchema.
 *
 * <p>Supports all SDL constructs: schema definition, scalar, type, interface,
 * union, enum, input, and directive definitions.
 *
 * @since 1.0.0
 */
public final class SchemaParser {

    private final List<Token> tokens;
    private int pos;

    // Stores type definitions for forward reference resolution
    private final Map<String, GraphQLType> typeRegistry = new LinkedHashMap<>();
    private final Map<String, List<String>> objectInterfaces = new LinkedHashMap<>();
    private final Map<String, List<String>> unionMembers = new LinkedHashMap<>();
    private final List<Directive> directives = new ArrayList<>();

    private String queryTypeName = "Query";
    private String mutationTypeName;
    private String subscriptionTypeName;

    private SchemaParser(List<Token> tokens) {
        this.tokens = tokens;
        this.pos = 0;
        // Pre-register built-in scalars
        typeRegistry.put("Int", ScalarType.INT);
        typeRegistry.put("Float", ScalarType.FLOAT);
        typeRegistry.put("String", ScalarType.STRING);
        typeRegistry.put("Boolean", ScalarType.BOOLEAN);
        typeRegistry.put("ID", ScalarType.ID);
    }

    /**
     * Parses an SDL string into a GraphQLSchema.
     *
     * @param sdl the SDL string
     * @return the parsed schema
     * @throws GraphQLSyntaxException if the SDL is invalid
     */
    public static GraphQLSchema parse(String sdl) {
        var lexer = new GraphQLLexer(sdl);
        var tokens = lexer.tokenize();
        var parser = new SchemaParser(tokens);
        return parser.parseSchema();
    }

    private GraphQLSchema parseSchema() {
        // First pass: collect all type definitions
        while (!atEnd()) {
            parseDescription();
            if (peekName("schema")) {
                parseSchemaDefinition();
            } else if (peekName("scalar")) {
                parseScalarType();
            } else if (peekName("type")) {
                parseObjectType();
            } else if (peekName("interface")) {
                parseInterfaceType();
            } else if (peekName("union")) {
                parseUnionType();
            } else if (peekName("enum")) {
                parseEnumType();
            } else if (peekName("input")) {
                parseInputObjectType();
            } else if (peekName("directive")) {
                parseDirectiveDefinition();
            } else if (peekName("extend")) {
                // Skip extend for now
                skipDefinition();
            } else {
                throw syntaxError("Unexpected token: " + current().value());
            }
        }

        // Second pass: resolve type references and build schema
        resolveReferences();

        var queryType = (ObjectType) typeRegistry.get(queryTypeName);
        if (queryType == null) {
            throw new GraphQLSyntaxException("Query type '" + queryTypeName + "' not found", 0, 0);
        }

        var builder = GraphQLSchema.newSchema().query(queryType);
        if (mutationTypeName != null) {
            var mt = (ObjectType) typeRegistry.get(mutationTypeName);
            if (mt != null) builder.mutation(mt);
        }
        if (subscriptionTypeName != null) {
            var st = (ObjectType) typeRegistry.get(subscriptionTypeName);
            if (st != null) builder.subscription(st);
        }
        for (var type : typeRegistry.values()) {
            builder.additionalType(type);
        }
        for (var dir : directives) {
            builder.directive(dir);
        }
        return builder.build();
    }

    private String parseDescription() {
        if (peek(Token.Type.STRING_VALUE) || peek(Token.Type.BLOCK_STRING)) {
            return advance().value();
        }
        return null;
    }

    private void parseSchemaDefinition() {
        consumeName("schema");
        consume(Token.Type.BRACE_LEFT);
        while (!peek(Token.Type.BRACE_RIGHT)) {
            var name = consume(Token.Type.NAME).value();
            consume(Token.Type.COLON);
            var typeName = consume(Token.Type.NAME).value();
            switch (name) {
                case "query" -> queryTypeName = typeName;
                case "mutation" -> mutationTypeName = typeName;
                case "subscription" -> subscriptionTypeName = typeName;
            }
        }
        consume(Token.Type.BRACE_RIGHT);
    }

    private void parseScalarType() {
        consumeName("scalar");
        var name = consume(Token.Type.NAME).value();
        // Skip directives
        while (peek(Token.Type.AT)) { skipDirective(); }
        typeRegistry.put(name, new ScalarType(name, null, v -> v, v -> v));
    }

    private void parseObjectType() {
        consumeName("type");
        var name = consume(Token.Type.NAME).value();

        var interfaces = new ArrayList<String>();
        if (peekName("implements")) {
            consumeName("implements");
            interfaces.add(consume(Token.Type.NAME).value());
            while (peek(Token.Type.AMP)) {
                consume(Token.Type.AMP);
                interfaces.add(consume(Token.Type.NAME).value());
            }
        }
        objectInterfaces.put(name, interfaces);

        // Skip directives
        while (peek(Token.Type.AT)) { skipDirective(); }

        var fields = parseFieldDefinitions();
        typeRegistry.put(name, new ObjectType(name, null, fields, List.of()));
    }

    private void parseInterfaceType() {
        consumeName("interface");
        var name = consume(Token.Type.NAME).value();
        // Skip directives
        while (peek(Token.Type.AT)) { skipDirective(); }
        var fields = parseFieldDefinitions();
        typeRegistry.put(name, new InterfaceType(name, null, fields));
    }

    private void parseUnionType() {
        consumeName("union");
        var name = consume(Token.Type.NAME).value();
        // Skip directives
        while (peek(Token.Type.AT)) { skipDirective(); }
        consume(Token.Type.EQUALS);
        // Optional leading pipe
        if (peek(Token.Type.PIPE)) consume(Token.Type.PIPE);
        var members = new ArrayList<String>();
        members.add(consume(Token.Type.NAME).value());
        while (peek(Token.Type.PIPE)) {
            consume(Token.Type.PIPE);
            members.add(consume(Token.Type.NAME).value());
        }
        unionMembers.put(name, members);
        typeRegistry.put(name, new UnionType(name, null, List.of())); // placeholder
    }

    private void parseEnumType() {
        consumeName("enum");
        var name = consume(Token.Type.NAME).value();
        // Skip directives
        while (peek(Token.Type.AT)) { skipDirective(); }
        consume(Token.Type.BRACE_LEFT);
        var values = new ArrayList<EnumType.EnumValue>();
        while (!peek(Token.Type.BRACE_RIGHT)) {
            parseDescription();
            var valueName = consume(Token.Type.NAME).value();
            boolean deprecated = false;
            String reason = null;
            while (peek(Token.Type.AT)) {
                consume(Token.Type.AT);
                var dirName = consume(Token.Type.NAME).value();
                if ("deprecated".equals(dirName)) {
                    deprecated = true;
                    if (peek(Token.Type.PAREN_LEFT)) {
                        consume(Token.Type.PAREN_LEFT);
                        while (!peek(Token.Type.PAREN_RIGHT)) {
                            var argName = consume(Token.Type.NAME).value();
                            consume(Token.Type.COLON);
                            if ("reason".equals(argName) && (peek(Token.Type.STRING_VALUE) || peek(Token.Type.BLOCK_STRING))) {
                                reason = advance().value();
                            } else {
                                advance(); // skip value
                            }
                        }
                        consume(Token.Type.PAREN_RIGHT);
                    }
                } else {
                    if (peek(Token.Type.PAREN_LEFT)) skipParenthesized();
                }
            }
            values.add(new EnumType.EnumValue(valueName, null, deprecated, reason));
        }
        consume(Token.Type.BRACE_RIGHT);
        typeRegistry.put(name, new EnumType(name, null, values));
    }

    private void parseInputObjectType() {
        consumeName("input");
        var name = consume(Token.Type.NAME).value();
        // Skip directives
        while (peek(Token.Type.AT)) { skipDirective(); }
        consume(Token.Type.BRACE_LEFT);
        var fields = new ArrayList<InputObjectType.InputFieldDefinition>();
        while (!peek(Token.Type.BRACE_RIGHT)) {
            parseDescription();
            var fieldName = consume(Token.Type.NAME).value();
            consume(Token.Type.COLON);
            var type = parseTypeRef();
            Object defaultValue = null;
            boolean hasDefault = false;
            if (peek(Token.Type.EQUALS)) {
                consume(Token.Type.EQUALS);
                defaultValue = parseSimpleValue();
                hasDefault = true;
            }
            // Skip directives
            while (peek(Token.Type.AT)) { skipDirective(); }
            fields.add(new InputObjectType.InputFieldDefinition(fieldName, null, type, defaultValue, hasDefault));
        }
        consume(Token.Type.BRACE_RIGHT);
        typeRegistry.put(name, new InputObjectType(name, null, fields));
    }

    private void parseDirectiveDefinition() {
        consumeName("directive");
        consume(Token.Type.AT);
        var name = consume(Token.Type.NAME).value();
        var args = List.<ArgumentDefinition>of();
        if (peek(Token.Type.PAREN_LEFT)) {
            args = parseArgumentDefinitions();
        }
        consumeName("on");
        // Optional leading pipe
        if (peek(Token.Type.PIPE)) consume(Token.Type.PIPE);
        var locations = new HashSet<Directive.Location>();
        locations.add(parseDirectiveLocation());
        while (peek(Token.Type.PIPE)) {
            consume(Token.Type.PIPE);
            locations.add(parseDirectiveLocation());
        }
        directives.add(new Directive(name, null, args, locations));
    }

    private Directive.Location parseDirectiveLocation() {
        var name = consume(Token.Type.NAME).value();
        return Directive.Location.valueOf(name);
    }

    private List<FieldDefinition> parseFieldDefinitions() {
        consume(Token.Type.BRACE_LEFT);
        var fields = new ArrayList<FieldDefinition>();
        while (!peek(Token.Type.BRACE_RIGHT)) {
            var description = parseDescription();
            var fieldName = consume(Token.Type.NAME).value();
            var args = List.<ArgumentDefinition>of();
            if (peek(Token.Type.PAREN_LEFT)) {
                args = parseArgumentDefinitions();
            }
            consume(Token.Type.COLON);
            var type = parseTypeRef();
            boolean deprecated = false;
            String deprecationReason = null;
            while (peek(Token.Type.AT)) {
                consume(Token.Type.AT);
                var dirName = consume(Token.Type.NAME).value();
                if ("deprecated".equals(dirName)) {
                    deprecated = true;
                    if (peek(Token.Type.PAREN_LEFT)) {
                        consume(Token.Type.PAREN_LEFT);
                        while (!peek(Token.Type.PAREN_RIGHT)) {
                            var argName = consume(Token.Type.NAME).value();
                            consume(Token.Type.COLON);
                            if ("reason".equals(argName) && (peek(Token.Type.STRING_VALUE) || peek(Token.Type.BLOCK_STRING))) {
                                deprecationReason = advance().value();
                            } else {
                                advance();
                            }
                        }
                        consume(Token.Type.PAREN_RIGHT);
                    }
                } else {
                    if (peek(Token.Type.PAREN_LEFT)) skipParenthesized();
                }
            }
            fields.add(new FieldDefinition(fieldName, description, type, args, deprecated, deprecationReason));
        }
        consume(Token.Type.BRACE_RIGHT);
        return fields;
    }

    private List<ArgumentDefinition> parseArgumentDefinitions() {
        consume(Token.Type.PAREN_LEFT);
        var args = new ArrayList<ArgumentDefinition>();
        while (!peek(Token.Type.PAREN_RIGHT)) {
            parseDescription();
            var name = consume(Token.Type.NAME).value();
            consume(Token.Type.COLON);
            var type = parseTypeRef();
            Object defaultValue = null;
            boolean hasDefault = false;
            if (peek(Token.Type.EQUALS)) {
                consume(Token.Type.EQUALS);
                defaultValue = parseSimpleValue();
                hasDefault = true;
            }
            // Skip directives
            while (peek(Token.Type.AT)) { skipDirective(); }
            args.add(new ArgumentDefinition(name, null, type, defaultValue, hasDefault));
        }
        consume(Token.Type.PAREN_RIGHT);
        return args;
    }

    /**
     * Parses a type reference using placeholder types where needed.
     * These will be resolved later.
     */
    private GraphQLType parseTypeRef() {
        GraphQLType type;
        if (peek(Token.Type.BRACKET_LEFT)) {
            consume(Token.Type.BRACKET_LEFT);
            var inner = parseTypeRef();
            consume(Token.Type.BRACKET_RIGHT);
            type = ListType.of(inner);
        } else {
            var name = consume(Token.Type.NAME).value();
            type = typeRegistry.getOrDefault(name,
                    new ScalarType(name, null, v -> v, v -> v)); // placeholder
        }
        if (peek(Token.Type.BANG)) {
            consume(Token.Type.BANG);
            type = NonNullType.of(type);
        }
        return type;
    }

    private Object parseSimpleValue() {
        var token = current();
        return switch (token.type()) {
            case INT_VALUE -> { advance(); yield Integer.parseInt(token.value()); }
            case FLOAT_VALUE -> { advance(); yield Double.parseDouble(token.value()); }
            case STRING_VALUE, BLOCK_STRING -> { advance(); yield token.value(); }
            case NAME -> {
                advance();
                yield switch (token.value()) {
                    case "true" -> true;
                    case "false" -> false;
                    case "null" -> null;
                    default -> token.value();
                };
            }
            case BRACKET_LEFT -> {
                consume(Token.Type.BRACKET_LEFT);
                var list = new ArrayList<>();
                while (!peek(Token.Type.BRACKET_RIGHT)) {
                    list.add(parseSimpleValue());
                }
                consume(Token.Type.BRACKET_RIGHT);
                yield list;
            }
            case BRACE_LEFT -> {
                consume(Token.Type.BRACE_LEFT);
                var map = new LinkedHashMap<String, Object>();
                while (!peek(Token.Type.BRACE_RIGHT)) {
                    var k = consume(Token.Type.NAME).value();
                    consume(Token.Type.COLON);
                    map.put(k, parseSimpleValue());
                }
                consume(Token.Type.BRACE_RIGHT);
                yield map;
            }
            default -> throw syntaxError("Expected value");
        };
    }

    /**
     * Resolves forward references: rebuild object types with interfaces,
     * rebuild unions with members.
     */
    private void resolveReferences() {
        // Resolve object type interfaces
        for (var entry : objectInterfaces.entrySet()) {
            var name = entry.getKey();
            var ifaceNames = entry.getValue();
            if (ifaceNames.isEmpty()) continue;

            var ot = (ObjectType) typeRegistry.get(name);
            var ifaces = new ArrayList<InterfaceType>();
            for (var ifaceName : ifaceNames) {
                var iface = typeRegistry.get(ifaceName);
                if (iface instanceof InterfaceType it) {
                    ifaces.add(it);
                }
            }
            // Rebuild with interfaces
            typeRegistry.put(name, new ObjectType(ot.name(), ot.description(),
                    new ArrayList<>(ot.fields().values()), ifaces));
        }

        // Resolve union members
        for (var entry : unionMembers.entrySet()) {
            var name = entry.getKey();
            var memberNames = entry.getValue();
            var members = new ArrayList<ObjectType>();
            for (var memberName : memberNames) {
                var member = typeRegistry.get(memberName);
                if (member instanceof ObjectType ot) {
                    members.add(ot);
                }
            }
            typeRegistry.put(name, new UnionType(name, null, members));
        }

        // Resolve field type references by rebuilding types with updated references
        resolveFieldTypeReferences();
    }

    private void resolveFieldTypeReferences() {
        // Rebuild object types with resolved field types
        for (var entry : new ArrayList<>(typeRegistry.entrySet())) {
            if (entry.getValue() instanceof ObjectType ot) {
                var newFields = new ArrayList<FieldDefinition>();
                boolean changed = false;
                for (var field : ot.fields().values()) {
                    var resolvedType = resolveType(field.type());
                    if (resolvedType != field.type()) {
                        var newArgs = new ArrayList<ArgumentDefinition>();
                        for (var arg : field.arguments()) {
                            var resolvedArgType = resolveType(arg.type());
                            newArgs.add(new ArgumentDefinition(arg.name(), arg.description(),
                                    resolvedArgType, arg.defaultValue(), arg.hasDefaultValue()));
                        }
                        var newField = new FieldDefinition(field.name(), field.description(),
                                resolvedType, newArgs, field.isDeprecated(), field.deprecationReason());
                        if (field.dataFetcher() != null) newField.dataFetcher(field.dataFetcher());
                        newFields.add(newField);
                        changed = true;
                    } else {
                        newFields.add(field);
                    }
                }
                if (changed) {
                    var interfaces = objectInterfaces.containsKey(ot.name())
                            ? getInterfaces(ot.name()) : ot.interfaces();
                    typeRegistry.put(ot.name(),
                            new ObjectType(ot.name(), ot.description(), newFields, interfaces));
                }
            }
        }
    }

    private List<InterfaceType> getInterfaces(String typeName) {
        var ifaceNames = objectInterfaces.get(typeName);
        if (ifaceNames == null) return List.of();
        var result = new ArrayList<InterfaceType>();
        for (var name : ifaceNames) {
            var type = typeRegistry.get(name);
            if (type instanceof InterfaceType it) result.add(it);
        }
        return result;
    }

    private GraphQLType resolveType(GraphQLType type) {
        return switch (type) {
            case ListType l -> {
                var resolved = resolveType(l.elementType());
                yield resolved != l.elementType() ? ListType.of(resolved) : l;
            }
            case NonNullType n -> {
                var resolved = resolveType(n.wrappedType());
                yield resolved != n.wrappedType() ? NonNullType.of(resolved) : n;
            }
            case ScalarType s -> {
                if (s.isBuiltIn()) yield s;
                var registered = typeRegistry.get(s.name());
                yield registered != null ? registered : s;
            }
            default -> {
                if (type.name() != null) {
                    var registered = typeRegistry.get(type.name());
                    yield registered != null ? registered : type;
                }
                yield type;
            }
        };
    }

    // --- Token helpers ---

    private void skipDirective() {
        consume(Token.Type.AT);
        consume(Token.Type.NAME);
        if (peek(Token.Type.PAREN_LEFT)) {
            skipParenthesized();
        }
    }

    private void skipParenthesized() {
        consume(Token.Type.PAREN_LEFT);
        int depth = 1;
        while (depth > 0 && !atEnd()) {
            if (peek(Token.Type.PAREN_LEFT)) depth++;
            if (peek(Token.Type.PAREN_RIGHT)) depth--;
            advance();
        }
    }

    private void skipDefinition() {
        // Skip until next top-level keyword
        advance(); // skip "extend"
        while (!atEnd()) {
            if (peek(Token.Type.BRACE_LEFT)) {
                int depth = 1;
                advance();
                while (depth > 0 && !atEnd()) {
                    if (peek(Token.Type.BRACE_LEFT)) depth++;
                    if (peek(Token.Type.BRACE_RIGHT)) depth--;
                    advance();
                }
                break;
            }
            advance();
        }
    }

    private Token current() { return tokens.get(pos); }
    private boolean atEnd() { return current().type() == Token.Type.EOF; }
    private boolean peek(Token.Type type) { return current().type() == type; }
    private boolean peekName(String value) {
        return current().type() == Token.Type.NAME && value.equals(current().value());
    }
    private Token consume(Token.Type expected) {
        var token = current();
        if (token.type() != expected) {
            throw syntaxError("Expected " + expected + " but got " + token.type());
        }
        pos++;
        return token;
    }
    private void consumeName(String expected) {
        var token = consume(Token.Type.NAME);
        if (!expected.equals(token.value())) {
            throw syntaxError("Expected '" + expected + "' but got '" + token.value() + "'");
        }
    }
    private Token advance() {
        var token = current();
        if (!atEnd()) pos++;
        return token;
    }
    private GraphQLSyntaxException syntaxError(String message) {
        var token = current();
        return new GraphQLSyntaxException(message, token.line(), token.col());
    }
}
