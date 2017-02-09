package rocks.matchmaker;

import com.google.common.collect.SortedSetMultimap;
import rocks.matchmaker.util.Indexed;
import rocks.matchmaker.util.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static com.google.common.collect.Multimaps.newSortedSetMultimap;
import static java.util.stream.Collectors.toCollection;

public class MultiMatcher<R> extends Matcher<R> {

    public MultiMatcher(List<Matcher<R>> cases) {
        //TODO make this.scopeType = leastCommonSuperType(cases.keySet()*.scopeType)
        super(Object.class, createMatchFunction(new ArrayList<>(cases)), null);
    }

    private static <R> BiFunction<Object, Captures, Match<R>> createMatchFunction(List<Matcher<R>> cases) {
        SortedSetMultimap<Class<?>, Indexed<Matcher<R>>> matchersByScopeType = indexByScopeType(cases);
        return (object, captures) -> {
            Stream<Match<R>> successfulCases = successfulCases(cases, matchersByScopeType, object);
            return successfulCases.findFirst().orElse(Match.empty());
        };
    }

    static <R> Stream<Match<R>> successfulCases(
            List<Matcher<R>> cases,
            SortedSetMultimap<Class<?>, Indexed<Matcher<R>>> matchersByScopeType,
            Object object
    ) {
        Stream<Matcher<R>> potentialMatchersInOrder = potentialMatchersInOrder(cases, matchersByScopeType, object);
        Stream<Match<R>> caseResults = potentialMatchersInOrder
                .map(matcher -> matcher.match(object));
        return caseResults.filter(Match::isPresent);
    }

    private static <R> Stream<Matcher<R>> potentialMatchersInOrder(
            List<Matcher<R>> cases,
            SortedSetMultimap<Class<?>, Indexed<Matcher<R>>> matchersByScopeType,
            Object object
    ) {
        if (object == null) {
            return cases.stream();
        } else {
            Stream<Class<?>> supertypes = Util.supertypes(object.getClass());
            TreeSet<Indexed<Matcher<R>>> indexedMatchersInOrder = supertypes
                    .flatMap(type -> matchersByScopeType.get(type).stream())
                    .collect(toCollection(TreeSet::new));
            return indexedMatchersInOrder.stream()
                    .map(Indexed::value);
        }
    }

    static <R> SortedSetMultimap<Class<?>, Indexed<Matcher<R>>> indexByScopeType(List<Matcher<R>> cases) {
        SortedSetMultimap<Class<?>, Indexed<Matcher<R>>> matchersByScopeType =
                newSortedSetMultimap(new HashMap<>(), TreeSet::new);
        AtomicInteger i = new AtomicInteger();
        Stream<Indexed<Matcher<R>>> indexedMatchers = cases.stream().map(c -> Indexed.at(i.getAndIncrement(), c));
        indexedMatchers.forEach(matcher -> matchersByScopeType.put(matcher.value().getScopeType(), matcher));
        return matchersByScopeType;
    }
}
