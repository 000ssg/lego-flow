package ssg.legoflow.rpc.graphql.demo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the comprehensive GraphQL demo and verifies all feature sections.
 *
 * <p>By default, uses the in-memory {@code ExecutionEngine} with programmatic schemas.
 * To test against an external GraphQL server, set {@code DemoGraphqlAll.USE_EXTERNAL = true}
 * and configure the URL before running.</p>
 */
class DemoGraphqlAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoGraphqlAll.runAll();

        assertThat(results.schemaDefinition())
                .as("Schema definition with all 8 type kinds")
                .isTrue();

        assertThat(results.queryExecution())
                .as("Query execution with fields, arguments, aliases")
                .isTrue();

        assertThat(results.mutations())
                .as("Mutations executed successfully")
                .isEqualTo(4);

        assertThat(results.variablesArgs())
                .as("Variables and argument defaults")
                .isTrue();

        assertThat(results.fragments())
                .as("Named and inline fragments")
                .isTrue();

        assertThat(results.directives())
                .as("@skip and @include directives")
                .isTrue();

        assertThat(results.interfacesUnions())
                .as("Interface and union type resolution")
                .isTrue();

        assertThat(results.introspection())
                .as("Introspection types discovered")
                .isGreaterThanOrEqualTo(1);

        assertThat(results.validation())
                .as("Validation errors detected")
                .isGreaterThanOrEqualTo(1);

        assertThat(results.sdlRoundTrip())
                .as("SDL print/parse round-trip")
                .isTrue();

        assertThat(results.subscriptions())
                .as("Subscription events received")
                .isEqualTo(3);

        assertThat(results.errorHandling())
                .as("Error handling (syntax, null, partial)")
                .isTrue();

        assertThat(results.jsonCodec())
                .as("JSON encode/decode round-trip")
                .isTrue();
    }
}
