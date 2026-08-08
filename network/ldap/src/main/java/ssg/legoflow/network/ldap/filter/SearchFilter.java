package ssg.legoflow.network.ldap.filter;

import java.util.List;

/**
 * Sealed interface representing LDAP search filters (RFC 4511 Section 4.5.1, RFC 4515).
 *
 * <p>LDAP filters are used in search operations to select entries that match
 * specified criteria. The sealed hierarchy enables exhaustive pattern matching.
 *
 * <pre>{@code
 * Filter ::= CHOICE {
 *     and             [0] SET OF Filter,
 *     or              [1] SET OF Filter,
 *     not             [2] Filter,
 *     equalityMatch   [3] AttributeValueAssertion,
 *     substrings      [4] SubstringFilter,
 *     greaterOrEqual  [5] AttributeValueAssertion,
 *     lessOrEqual     [6] AttributeValueAssertion,
 *     present         [7] AttributeDescription,
 *     approxMatch     [8] AttributeValueAssertion,
 *     extensibleMatch [9] MatchingRuleAssertion
 * }
 * }</pre>
 *
 * @since 0.1.0
 */
public sealed interface SearchFilter
        permits SearchFilter.And, SearchFilter.Or, SearchFilter.Not,
                SearchFilter.EqualityMatch, SearchFilter.Substrings,
                SearchFilter.GreaterOrEqual, SearchFilter.LessOrEqual,
                SearchFilter.Present, SearchFilter.ApproxMatch,
                SearchFilter.ExtensibleMatch {

    /**
     * Returns the context-specific tag number for this filter type.
     *
     * @return the tag number (0-9)
     */
    int tagNumber();

    /**
     * AND filter (context tag 0): all sub-filters must match.
     *
     * @param filters the sub-filters
     * @since 0.1.0
     */
    record And(List<SearchFilter> filters) implements SearchFilter {
        /** Creates an AND filter with validation. */
        public And {
            if (filters == null || filters.isEmpty()) {
                throw new IllegalArgumentException("AND filter requires at least one sub-filter");
            }
            filters = List.copyOf(filters);
        }

        @Override
        public int tagNumber() { return 0; }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("(&");
            for (SearchFilter f : filters) sb.append(f);
            sb.append(')');
            return sb.toString();
        }
    }

    /**
     * OR filter (context tag 1): at least one sub-filter must match.
     *
     * @param filters the sub-filters
     * @since 0.1.0
     */
    record Or(List<SearchFilter> filters) implements SearchFilter {
        /** Creates an OR filter with validation. */
        public Or {
            if (filters == null || filters.isEmpty()) {
                throw new IllegalArgumentException("OR filter requires at least one sub-filter");
            }
            filters = List.copyOf(filters);
        }

        @Override
        public int tagNumber() { return 1; }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("(|");
            for (SearchFilter f : filters) sb.append(f);
            sb.append(')');
            return sb.toString();
        }
    }

    /**
     * NOT filter (context tag 2): negates the inner filter.
     *
     * @param filter the filter to negate
     * @since 0.1.0
     */
    record Not(SearchFilter filter) implements SearchFilter {
        /** Creates a NOT filter with validation. */
        public Not {
            if (filter == null) {
                throw new IllegalArgumentException("NOT filter requires a sub-filter");
            }
        }

        @Override
        public int tagNumber() { return 2; }

        @Override
        public String toString() { return "(!" + filter + ")"; }
    }

    /**
     * Equality match filter (context tag 3).
     *
     * @param attribute the attribute description
     * @param value     the assertion value
     * @since 0.1.0
     */
    record EqualityMatch(String attribute, byte[] value) implements SearchFilter {
        /** Creates an equality match filter. */
        public EqualityMatch {
            if (attribute == null) throw new IllegalArgumentException("Attribute must not be null");
            if (value == null) throw new IllegalArgumentException("Value must not be null");
            value = value.clone();
        }

        @Override public int tagNumber() { return 3; }

        /** Returns a copy of the assertion value. */
        @Override public byte[] value() { return value.clone(); }

        @Override
        public String toString() {
            return "(" + attribute + "=" + new String(value, java.nio.charset.StandardCharsets.UTF_8) + ")";
        }
    }

    /**
     * Substring filter (context tag 4).
     *
     * @param attribute the attribute description
     * @param initial   the initial substring (null if none)
     * @param any       the list of 'any' substrings
     * @param finalStr  the final substring (null if none)
     * @since 0.1.0
     */
    record Substrings(String attribute, String initial, List<String> any, String finalStr)
            implements SearchFilter {

        /** Creates a substring filter with validation. */
        public Substrings {
            if (attribute == null) throw new IllegalArgumentException("Attribute must not be null");
            if (any == null) any = List.of();
            else any = List.copyOf(any);
            if (initial == null && any.isEmpty() && finalStr == null) {
                throw new IllegalArgumentException("At least one substring component required");
            }
        }

        @Override public int tagNumber() { return 4; }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("(").append(attribute).append('=');
            if (initial != null) sb.append(initial);
            sb.append('*');
            for (String a : any) sb.append(a).append('*');
            if (finalStr != null) sb.append(finalStr);
            return sb.append(')').toString();
        }
    }

    /**
     * Greater-or-equal filter (context tag 5).
     *
     * @param attribute the attribute description
     * @param value     the assertion value
     * @since 0.1.0
     */
    record GreaterOrEqual(String attribute, byte[] value) implements SearchFilter {
        /** Creates a greater-or-equal filter. */
        public GreaterOrEqual {
            if (attribute == null) throw new IllegalArgumentException("Attribute must not be null");
            if (value == null) throw new IllegalArgumentException("Value must not be null");
            value = value.clone();
        }

        @Override public int tagNumber() { return 5; }
        @Override public byte[] value() { return value.clone(); }

        @Override
        public String toString() {
            return "(" + attribute + ">=" + new String(value, java.nio.charset.StandardCharsets.UTF_8) + ")";
        }
    }

    /**
     * Less-or-equal filter (context tag 6).
     *
     * @param attribute the attribute description
     * @param value     the assertion value
     * @since 0.1.0
     */
    record LessOrEqual(String attribute, byte[] value) implements SearchFilter {
        /** Creates a less-or-equal filter. */
        public LessOrEqual {
            if (attribute == null) throw new IllegalArgumentException("Attribute must not be null");
            if (value == null) throw new IllegalArgumentException("Value must not be null");
            value = value.clone();
        }

        @Override public int tagNumber() { return 6; }
        @Override public byte[] value() { return value.clone(); }

        @Override
        public String toString() {
            return "(" + attribute + "<=" + new String(value, java.nio.charset.StandardCharsets.UTF_8) + ")";
        }
    }

    /**
     * Presence filter (context tag 7): attribute exists.
     *
     * @param attribute the attribute description
     * @since 0.1.0
     */
    record Present(String attribute) implements SearchFilter {
        /** Creates a presence filter. */
        public Present {
            if (attribute == null) throw new IllegalArgumentException("Attribute must not be null");
        }

        @Override public int tagNumber() { return 7; }

        @Override public String toString() { return "(" + attribute + "=*)"; }
    }

    /**
     * Approximate match filter (context tag 8).
     *
     * @param attribute the attribute description
     * @param value     the assertion value
     * @since 0.1.0
     */
    record ApproxMatch(String attribute, byte[] value) implements SearchFilter {
        /** Creates an approximate match filter. */
        public ApproxMatch {
            if (attribute == null) throw new IllegalArgumentException("Attribute must not be null");
            if (value == null) throw new IllegalArgumentException("Value must not be null");
            value = value.clone();
        }

        @Override public int tagNumber() { return 8; }
        @Override public byte[] value() { return value.clone(); }

        @Override
        public String toString() {
            return "(" + attribute + "~=" + new String(value, java.nio.charset.StandardCharsets.UTF_8) + ")";
        }
    }

    /**
     * Extensible match filter (context tag 9).
     *
     * @param matchingRule the optional matching rule OID
     * @param attribute    the optional attribute description
     * @param matchValue   the assertion value
     * @param dnAttributes whether to match DN attributes
     * @since 0.1.0
     */
    record ExtensibleMatch(String matchingRule, String attribute, byte[] matchValue, boolean dnAttributes)
            implements SearchFilter {

        /** Creates an extensible match filter. */
        public ExtensibleMatch {
            if (matchValue == null) throw new IllegalArgumentException("Match value must not be null");
            matchValue = matchValue.clone();
        }

        @Override public int tagNumber() { return 9; }
        @Override public byte[] matchValue() { return matchValue.clone(); }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("(");
            if (attribute != null) sb.append(attribute);
            if (dnAttributes) sb.append(":dn");
            if (matchingRule != null) sb.append(':').append(matchingRule);
            sb.append(":=").append(new String(matchValue, java.nio.charset.StandardCharsets.UTF_8));
            return sb.append(')').toString();
        }
    }

    // ── Factory methods ──

    /**
     * Creates an equality match filter from string value.
     *
     * @param attribute the attribute
     * @param value     the string value
     * @return the filter
     */
    static EqualityMatch equalityMatch(String attribute, String value) {
        return new EqualityMatch(attribute, value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Creates a presence filter.
     *
     * @param attribute the attribute
     * @return the filter
     */
    static Present present(String attribute) {
        return new Present(attribute);
    }

    /**
     * Creates an AND filter.
     *
     * @param filters the sub-filters
     * @return the filter
     */
    static And and(SearchFilter... filters) {
        return new And(List.of(filters));
    }

    /**
     * Creates an OR filter.
     *
     * @param filters the sub-filters
     * @return the filter
     */
    static Or or(SearchFilter... filters) {
        return new Or(List.of(filters));
    }

    /**
     * Creates a NOT filter.
     *
     * @param filter the filter to negate
     * @return the filter
     */
    static Not not(SearchFilter filter) {
        return new Not(filter);
    }
}
