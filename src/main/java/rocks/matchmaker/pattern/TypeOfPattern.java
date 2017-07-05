package rocks.matchmaker.pattern;

import rocks.matchmaker.Captures;
import rocks.matchmaker.Match;
import rocks.matchmaker.Matcher;
import rocks.matchmaker.Pattern;

public class TypeOfPattern<T> extends Pattern<T> {

    private final Class<T> expectedClass;

    public TypeOfPattern(Class<T> expectedClass) {
        this.expectedClass = expectedClass;
    }

    public Class<T> expectedClass() {
        return expectedClass;
    }

    @Override
    public Match<T> accept(Matcher matcher, Object object, Captures captures) {
        return matcher.visit(this, object, captures);
    }
}
