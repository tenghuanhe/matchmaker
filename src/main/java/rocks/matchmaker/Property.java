package rocks.matchmaker;

import java.util.function.Function;
import java.util.function.Predicate;

public interface Property<F, T> {

    static <F, T> Property<F, T> property(Function<F, T> property) {
        return optionalProperty(source -> Option.of(property.apply(source)));
    }

    static <F, T> Property<F, T> optionalProperty(Function<F, Option<T>> property) {
        return new Property<F, T>() {
            @Override
            public <R> PropertyMatcher<F, R> $(Matcher<R> matcher) {
                return PropertyMatcher.of(property, matcher);
            }
        };
    }

    static <T> Property<T, T> self() {
        return property(Function.identity());
    }

    default PropertyMatcher<F, T> as(Capture<T> capture) {
        Matcher<T> matchAll = (Matcher<T>) Matcher.$();
        return $(matchAll.as(capture));
    }

    default PropertyMatcher<F, T> $(T value) {
        return $(Matcher.equalTo(value));
    }

    default PropertyMatcher<F, T> $(Class<? extends T> type) {
        return $(Matcher.upcast(Matcher.$(type)));
    }

    @SuppressWarnings("unchecked cast")
    //the `matchAll` matcher will only ever be passed the return values of
    //the `property` function.
    default PropertyMatcher<F, T> $(Predicate<? super T> predicate) {
        Matcher<T> matchAll = (Matcher<T>) Matcher.$();
        return $(matchAll.$(predicate));
    }

    @SuppressWarnings("unchecked cast")
    //the `matchAll` matcher will only ever be passed the return values of
    //the `property` function.
    default <R> PropertyMatcher<F, R> $(Extractor<T, R> extractor) {
        Matcher<T> matchAll = (Matcher<T>) Matcher.$();
        return $(matchAll.$(extractor));
    }

    <R> PropertyMatcher<F, R> $(Matcher<R> matcher);
}
