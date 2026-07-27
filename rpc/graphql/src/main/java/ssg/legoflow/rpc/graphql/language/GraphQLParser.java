package ssg.legoflow.rpc.graphql.language;

import ssg.legoflow.rpc.graphql.schema.Directive;

import java.util.*;

/**
 * Parses a GraphQL query string into a Document AST.
 *
 * <p>Implements the full GraphQL query language grammar from the June 2018
 * specification, including operations, selections, fragments, variables,
 * directives, and all value types.
 *
 * @since 1.0.0
 */
public final class GraphQLParser {

    private final List<Token> tokens;
    private int pos;

    private GraphQLParser(List<Token> tokens) {
        this.tokens = tokens;
        this.pos = 0;
    }

    /**
     * Parses a GraphQL query string into a Document AST.
     *
     * @param source the GraphQL query string
     * @return the parsed document
     * @throws GraphQLSyntaxException if the source is not valid GraphQL
     */
    public static Document parse(String source) {
        var lexer = new GraphQLLexer(source);
        var tokens = lexer.tokenize();
        var parser = new GraphQLParser(tokens);
        return parser.parseDocument();
    }

    private Document parseDocument() {
        var operations = new ArrayList<OperationDefinition>();
        var fragments = new ArrayList<FragmentDefinition>();

        while (!atEnd()) {
            if (peekName("query") || peekName("mutation") || peekName("subscription")) {
                operations.add(parseOperationDefinition());
            } else if (peekName("fragment")) {
                fragments.add(parseFragmentDefinition());
            } else if (peek(Token.Type.BRACE_LEFT)) {
                // Anonymous query shorthand
                operations.add(parseAnonymousQuery());
            } else {
                throw syntaxError("Expected operation or fragment definition");
            }
        }

        if (operations.isEmpty() && fragments.isEmpty()) {
            throw syntaxError("Empty document");
        }

        return new Document(operations, fragments);
    }

    private OperationDefinition parseAnonymousQuery() {
        var selectionSet = parseSelectionSet();
        return new OperationDefinition(
                OperationDefinition.OperationType.QUERY, null,
                List.of(), List.of(), selectionSet);
    }

    private OperationDefinition parseOperationDefinition() {
        var opType = parseOperationType();
        String name = null;
        if (peek(Token.Type.NAME)) {
            name = consume(Token.Type.NAME).value();
        }
        var varDefs = List.<VariableDefinition>of();
        if (peek(Token.Type.PAREN_LEFT)) {
            varDefs = parseVariableDefinitions();
        }
        var directives = parseDirectives();
        var selectionSet = parseSelectionSet();
        return new OperationDefinition(opType, name, varDefs, directives, selectionSet);
    }

    private OperationDefinition.OperationType parseOperationType() {
        var token = consume(Token.Type.NAME);
        return switch (token.value()) {
            case "query" -> OperationDefinition.OperationType.QUERY;
            case "mutation" -> OperationDefinition.OperationType.MUTATION;
            case "subscription" -> OperationDefinition.OperationType.SUBSCRIPTION;
            default -> throw syntaxError("Expected query, mutation, or subscription");
        };
    }

    private List<VariableDefinition> parseVariableDefinitions() {
        consume(Token.Type.PAREN_LEFT);
        var defs = new ArrayList<VariableDefinition>();
        while (!peek(Token.Type.PAREN_RIGHT)) {
            defs.add(parseVariableDefinition());
        }
        consume(Token.Type.PAREN_RIGHT);
        return defs;
    }

    private VariableDefinition parseVariableDefinition() {
        consume(Token.Type.DOLLAR);
        var name = consume(Token.Type.NAME).value();
        consume(Token.Type.COLON);
        var type = parseTypeReference();
        Value defaultValue = null;
        if (peek(Token.Type.EQUALS)) {
            consume(Token.Type.EQUALS);
            defaultValue = parseValue(false);
        }
        return new VariableDefinition(name, type, defaultValue);
    }

    private VariableDefinition.TypeReference parseTypeReference() {
        VariableDefinition.TypeReference type;
        if (peek(Token.Type.BRACKET_LEFT)) {
            consume(Token.Type.BRACKET_LEFT);
            var inner = parseTypeReference();
            consume(Token.Type.BRACKET_RIGHT);
            type = new VariableDefinition.TypeReference.ListTypeRef(inner);
        } else {
            var name = consume(Token.Type.NAME).value();
            type = new VariableDefinition.TypeReference.NamedType(name);
        }
        if (peek(Token.Type.BANG)) {
            consume(Token.Type.BANG);
            type = new VariableDefinition.TypeReference.NonNullTypeRef(type);
        }
        return type;
    }

    private SelectionSet parseSelectionSet() {
        consume(Token.Type.BRACE_LEFT);
        var selections = new ArrayList<Selection>();
        while (!peek(Token.Type.BRACE_RIGHT)) {
            selections.add(parseSelection());
        }
        consume(Token.Type.BRACE_RIGHT);
        return new SelectionSet(selections);
    }

    private Selection parseSelection() {
        if (peek(Token.Type.SPREAD)) {
            return parseFragmentSpreadOrInlineFragment();
        }
        return parseField();
    }

    private Field parseField() {
        String alias = null;
        String name;

        var firstName = consume(Token.Type.NAME).value();
        if (peek(Token.Type.COLON)) {
            consume(Token.Type.COLON);
            alias = firstName;
            name = consume(Token.Type.NAME).value();
        } else {
            name = firstName;
        }

        Map<String, Value> arguments = Map.of();
        if (peek(Token.Type.PAREN_LEFT)) {
            arguments = parseArguments();
        }

        var directives = parseDirectives();

        SelectionSet selectionSet = null;
        if (peek(Token.Type.BRACE_LEFT)) {
            selectionSet = parseSelectionSet();
        }

        return new Field(alias, name, arguments, directives, selectionSet);
    }

    private Map<String, Value> parseArguments() {
        consume(Token.Type.PAREN_LEFT);
        var args = new LinkedHashMap<String, Value>();
        while (!peek(Token.Type.PAREN_RIGHT)) {
            var argName = consume(Token.Type.NAME).value();
            consume(Token.Type.COLON);
            var value = parseValue(false);
            args.put(argName, value);
        }
        consume(Token.Type.PAREN_RIGHT);
        return args;
    }

    private Selection parseFragmentSpreadOrInlineFragment() {
        consume(Token.Type.SPREAD);
        if (peekName("on") || peek(Token.Type.AT) || peek(Token.Type.BRACE_LEFT)) {
            return parseInlineFragment();
        }
        return parseFragmentSpread();
    }

    private FragmentSpread parseFragmentSpread() {
        var name = consume(Token.Type.NAME).value();
        var directives = parseDirectives();
        return new FragmentSpread(name, directives);
    }

    private InlineFragment parseInlineFragment() {
        String typeCondition = null;
        if (peekName("on")) {
            consume(Token.Type.NAME); // "on"
            typeCondition = consume(Token.Type.NAME).value();
        }
        var directives = parseDirectives();
        var selectionSet = parseSelectionSet();
        return new InlineFragment(typeCondition, directives, selectionSet);
    }

    private FragmentDefinition parseFragmentDefinition() {
        consumeName("fragment");
        var name = consume(Token.Type.NAME).value();
        consumeName("on");
        var typeCondition = consume(Token.Type.NAME).value();
        var directives = parseDirectives();
        var selectionSet = parseSelectionSet();
        return new FragmentDefinition(name, typeCondition, directives, selectionSet);
    }

    private List<Directive.DirectiveUsage> parseDirectives() {
        var directives = new ArrayList<Directive.DirectiveUsage>();
        while (peek(Token.Type.AT)) {
            directives.add(parseDirective());
        }
        return directives;
    }

    private Directive.DirectiveUsage parseDirective() {
        consume(Token.Type.AT);
        var name = consume(Token.Type.NAME).value();
        Map<String, Object> args = Map.of();
        if (peek(Token.Type.PAREN_LEFT)) {
            var argValues = parseArguments();
            var converted = new LinkedHashMap<String, Object>();
            argValues.forEach((k, v) -> converted.put(k, v.toJava()));
            args = converted;
        }
        return Directive.DirectiveUsage.of(name, args);
    }

    /**
     * Parses a value. If constants is true, variables are not allowed.
     */
    private Value parseValue(boolean constants) {
        var token = current();
        return switch (token.type()) {
            case DOLLAR -> {
                if (constants) throw syntaxError("Variables not allowed in constant values");
                consume(Token.Type.DOLLAR);
                var name = consume(Token.Type.NAME).value();
                yield new Value.VariableValue(name);
            }
            case INT_VALUE -> {
                advance();
                yield new Value.IntValue(Integer.parseInt(token.value()));
            }
            case FLOAT_VALUE -> {
                advance();
                yield new Value.FloatValue(Double.parseDouble(token.value()));
            }
            case STRING_VALUE, BLOCK_STRING -> {
                advance();
                yield new Value.StringValue(token.value());
            }
            case NAME -> {
                advance();
                yield switch (token.value()) {
                    case "true" -> new Value.BooleanValue(true);
                    case "false" -> new Value.BooleanValue(false);
                    case "null" -> new Value.NullValue();
                    default -> new Value.EnumValue(token.value());
                };
            }
            case BRACKET_LEFT -> parseListValue(constants);
            case BRACE_LEFT -> parseObjectValue(constants);
            default -> throw syntaxError("Expected value, got " + token.type());
        };
    }

    private Value.ListValue parseListValue(boolean constants) {
        consume(Token.Type.BRACKET_LEFT);
        var values = new ArrayList<Value>();
        while (!peek(Token.Type.BRACKET_RIGHT)) {
            values.add(parseValue(constants));
        }
        consume(Token.Type.BRACKET_RIGHT);
        return new Value.ListValue(values);
    }

    private Value.ObjectValue parseObjectValue(boolean constants) {
        consume(Token.Type.BRACE_LEFT);
        var fields = new LinkedHashMap<String, Value>();
        while (!peek(Token.Type.BRACE_RIGHT)) {
            var name = consume(Token.Type.NAME).value();
            consume(Token.Type.COLON);
            fields.put(name, parseValue(constants));
        }
        consume(Token.Type.BRACE_RIGHT);
        return new Value.ObjectValue(fields);
    }

    // --- Token helpers ---

    private Token current() {
        return tokens.get(pos);
    }

    private boolean atEnd() {
        return current().type() == Token.Type.EOF;
    }

    private boolean peek(Token.Type type) {
        return current().type() == type;
    }

    private boolean peekName(String value) {
        return current().type() == Token.Type.NAME && value.equals(current().value());
    }

    private Token consume(Token.Type expected) {
        var token = current();
        if (token.type() != expected) {
            throw syntaxError("Expected " + expected + " but got " + token.type());
        }
        advance();
        return token;
    }

    private void consumeName(String expected) {
        var token = consume(Token.Type.NAME);
        if (!expected.equals(token.value())) {
            throw syntaxError("Expected '" + expected + "' but got '" + token.value() + "'");
        }
    }

    private void advance() {
        if (!atEnd()) pos++;
    }

    private GraphQLSyntaxException syntaxError(String message) {
        var token = current();
        return new GraphQLSyntaxException(message, token.line(), token.col());
    }
}
