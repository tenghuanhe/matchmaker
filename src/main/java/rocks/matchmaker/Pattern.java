package rocks.matchmaker;

import rocks.matchmaker.pattern.CapturePattern;
import rocks.matchmaker.pattern.CombinePattern;
import rocks.matchmaker.pattern.EqualsPattern;
import rocks.matchmaker.pattern.ExtractPattern;
import rocks.matchmaker.pattern.FilterPattern;
import rocks.matchmaker.pattern.TypeOfPattern;
import rocks.matchmaker.pattern.WithPattern;
import rocks.matchmaker.util.Util;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static rocks.matchmaker.DefaultMatcher.DEFAULT_MATCHER;

public class Pattern<T> {

    private final Pattern<?> previous;

    public static Pattern<Object> any() {
        return typeOf(Object.class);
    }

    public static <T> Pattern<T> equalTo(T expectedValue) {
        Util.checkArgument(expectedValue != null, "expectedValue can't be null. Use `Pattern.isNull()` instead");
        return new EqualsPattern<>(expectedValue);
    }

    public static <T> Pattern<T> typeOf(Class<T> expectedClass) {
        return new TypeOfPattern<>(expectedClass);
    }

    @SuppressWarnings("unchecked cast")
    public static <T> Pattern<T> isNull() {
        return new FilterPattern<>(Objects::isNull, null);
    }

    public static <T> Pattern<T> nullable(Class<T> expectedClass) {
        BiFunction<Object, Captures, Match<T>> matchFunction = (x, captures) -> Match.of(x, captures)
                .filter(value -> value == null || expectedClass.isInstance(value))
                .map(expectedClass::cast);
        return new Pattern<>(expectedClass, matchFunction);
    }

    //This expresses the fact that Pattern is covariant on T.
    //This is saying "Pattern<? extends T> is a Pattern<T>".
    @SuppressWarnings("unchecked cast")
    public static <T> Pattern<T> upcast(Pattern<? extends T> pattern) {
        return (Pattern<T>) pattern;
    }

    //scopeType unused for now, but will help in debugging and structural equalTo later
    private final Class<?> scopeType;
    private final BiFunction<Object, Captures, Match<T>> matchFunction;

    //FIXME this is temporary and only for the migration
    protected Pattern() {
        this(null, null);
    }

    protected Pattern(Pattern<?> previous) {
        this.scopeType = Object.class; //FIXME this is temporary to maintain correctness of pattern matching during migraiton
        this.matchFunction = null;
        this.previous = previous;
    }

    //TODO think how to not have this package-private? Make Pattern an interface?
    protected Pattern(Class<?> scopeType, BiFunction<Object, Captures, Match<T>> matchFunction) {
        this.scopeType = scopeType;
        this.matchFunction = matchFunction;
        this.previous = null;
    }

    public Pattern<T> capturedAs(Capture<T> capture) {
        return new CapturePattern<>(capture, this);
    }

    protected static <T> Match<T> createMatch(Capture<T> capture, T matchedValue, Captures captures) {
        return Match.of(matchedValue, captures.addAll(Captures.ofNullable(capture, matchedValue)));
    }

    public Pattern<T> matching(Predicate<? super T> predicate) {
        return new FilterPattern<>(predicate, this);
    }

    /**
     * For cases when evaluating a property is needed to check
     * if it's possible to construct an object and that object's
     * construction largely repeats checking the property.
     * <p>
     * E.g. let's say we have a set and we'd like to match
     * other sets having a non-empty intersection with it.
     * If the intersection is not empty, we'd like to use it
     * in further computations. Extractors allow for exactly that.
     * <p>
     * An adequate extractor for the example above would compute
     * the intersection and return it wrapped in a Match
     * (think: null-capable Optional with a field for storing captures).
     * If the intersection would be empty, the extractor should
     * would a non-match by returning Match.empty().
     *
     * @param extractor
     * @param <R>       type of the extracted value
     * @return
     */
    public <R> Pattern<R> matching(Extractor<T, R> extractor) {
        return new ExtractPattern<>(extractor, this);
    }

    public <R> Pattern<R> matching(Pattern<R> pattern) {
        return new CombinePattern<>(this, pattern);
    }

    public Pattern<T> with(PropertyPattern<? super T, ?> pattern) {
        return new WithPattern<>(pattern, this);
    }

    protected <R> Pattern<R> flatMap(BiFunction<? super T, Captures, Match<R>> mapper) {
        BiFunction<Object, Captures, Match<R>> newMatchFunction = (object, captures) -> {
            Match<T> originalMatch = match(object, captures);
            return originalMatch.flatMap(value -> mapper.apply(value, originalMatch.captures()));
        };
        return new Pattern<>(scopeType, newMatchFunction);
    }

    //Usage of this method within the library's code almost always means an error because of lost captures.
    public Match<T> match(Object object) {
        return match(object, Captures.empty());
    }

    public Match<T> match(Object object, Captures captures) {
        if (matchFunction == null) {
            return DEFAULT_MATCHER.match(this, object, captures);
        } else {
            return matchFunction.apply(object, captures);
        }
    }

    Class<?> getScopeType() {
        return scopeType;
    }

    public Pattern<?> previous() {
        return previous;
    }
}
