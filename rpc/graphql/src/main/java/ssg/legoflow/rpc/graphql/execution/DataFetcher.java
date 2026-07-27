package ssg.legoflow.rpc.graphql.execution;

/**
 * Functional interface for resolving a field value.
 *
 * <p>A data fetcher is called during execution to retrieve the value for a field.
 * The environment provides access to the source object, arguments, and context.
 *
 * @param <T> the return type
 * @since 1.0.0
 */
@FunctionalInterface
public interface DataFetcher<T> {

    /**
     * Fetches the value for a field.
     *
     * @param environment the data fetching environment
     * @return the field value
     * @throws Exception if the value cannot be fetched
     */
    T get(DataFetchingEnvironment environment) throws Exception;
}
