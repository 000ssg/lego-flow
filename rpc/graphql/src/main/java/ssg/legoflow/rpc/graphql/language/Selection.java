package ssg.legoflow.rpc.graphql.language;

/**
 * Sealed interface representing a selection within a selection set.
 *
 * <p>A selection can be a field, a fragment spread, or an inline fragment.
 *
 * @since 0.1.0
 */
public sealed interface Selection permits Field, FragmentSpread, InlineFragment {
}
