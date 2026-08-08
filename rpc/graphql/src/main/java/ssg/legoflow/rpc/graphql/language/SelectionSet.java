package ssg.legoflow.rpc.graphql.language;

import java.util.List;

/**
 * Represents a set of selections (fields, fragment spreads, inline fragments).
 *
 * @param selections the selections in this set
 * @since 0.1.0
 */
public record SelectionSet(List<Selection> selections) {

    /**
     * Returns the selections as fields (filtering out non-field selections).
     *
     * @return the field selections
     */
    public List<Field> fields() {
        return selections.stream()
                .filter(s -> s instanceof Field)
                .map(s -> (Field) s)
                .toList();
    }

    /**
     * Returns the fragment spreads in this selection set.
     *
     * @return the fragment spreads
     */
    public List<FragmentSpread> fragmentSpreads() {
        return selections.stream()
                .filter(s -> s instanceof FragmentSpread)
                .map(s -> (FragmentSpread) s)
                .toList();
    }

    /**
     * Returns the inline fragments in this selection set.
     *
     * @return the inline fragments
     */
    public List<InlineFragment> inlineFragments() {
        return selections.stream()
                .filter(s -> s instanceof InlineFragment)
                .map(s -> (InlineFragment) s)
                .toList();
    }
}
