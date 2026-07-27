package ssg.legoflow.rpc.graphql.validation;

import ssg.legoflow.rpc.graphql.language.Document;
import ssg.legoflow.rpc.graphql.schema.GraphQLSchema;

import java.util.List;

/**
 * Interface for individual validation rules.
 *
 * <p>Each rule checks a specific aspect of a GraphQL document against
 * the schema and returns any validation errors found.
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface ValidationRule {

    /**
     * Validates the document against the schema.
     *
     * @param document the document to validate
     * @param schema   the schema to validate against
     * @return the validation errors found (empty if valid)
     */
    List<ValidationError> validate(Document document, GraphQLSchema schema);
}
