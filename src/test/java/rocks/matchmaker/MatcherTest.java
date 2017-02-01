package rocks.matchmaker;

import example.ast.FilterNode;
import example.ast.JoinNode;
import example.ast.PlanNode;
import example.ast.ProjectNode;
import example.ast.ScanNode;
import example.ast.SingleSourcePlanNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static rocks.matchmaker.Capture.newCapture;
import static rocks.matchmaker.Extractor.assumingType;
import static rocks.matchmaker.Matcher.any;
import static rocks.matchmaker.Matcher.match;
import static rocks.matchmaker.Property.optionalProperty;
import static rocks.matchmaker.Property.property;

@SuppressWarnings("WeakerAccess")
public class MatcherTest {

    Matcher<JoinNode> Join = match(JoinNode.class);

    Property<JoinNode> probe = property(JoinNode::getProbe);
    Property<JoinNode> build = property(JoinNode::getBuild);

    Matcher<ProjectNode> Project = match(ProjectNode.class);
    Matcher<FilterNode> Filter = match(FilterNode.class);
    Matcher<ScanNode> Scan = match(ScanNode.class);

    Property<SingleSourcePlanNode> source = property(SingleSourcePlanNode::getSource);


    @Test
    void trivial_matchers() {
        //any
        assertMatch(any(), 42);
        assertMatch(any(), "John Doe");

        //class based
        assertMatch(match(Integer.class), 42);
        assertMatch(match(Number.class), 42);
        assertNoMatch(match(Integer.class), "John Doe");

        //predicate-based
        assertMatch(match(Integer.class, (x1) -> x1 > 0), 42);
        assertNoMatch(match(Integer.class, (x) -> x > 0), -1);
    }

    @Test
    void match_object() {
        assertMatch(Project, new ProjectNode(null));
        assertNoMatch(Project, new ScanNode());
    }

    @Test
    void property_matchers() {
        PropertyMatcher<String, Integer> lengthOne = property(String::length).matching(match(Integer.class, (x) -> x == 1));
        assertMatch(match(String.class).with(lengthOne), "a");
        assertNoMatch(match(String.class).with(lengthOne), "aa");
    }

    @Test
    void match_nested_properties() {
        Matcher<ProjectNode> matcher = Project
                .with(property(ProjectNode::getSource).matching(Scan));

        assertMatch(matcher, new ProjectNode(new ScanNode()));
        assertNoMatch(matcher, new ScanNode());
        assertNoMatch(matcher, new ProjectNode(null));
        assertNoMatch(matcher, new ProjectNode(new ProjectNode(null)));
    }

    @Test
    void match_additional_properties() {
        Capture<List<String>> lowercase = newCapture();

        String matchedValue = "A little string.";

        Matcher<String> matcher = match(String.class)
                .matching(String.class, s -> s.startsWith("A"))
                .matching(endsWith("."))
                .matching(hasLowercaseChars.capturedAs(lowercase))
                .matching(matchedValue);

        Match<String> match = assertMatch(matcher, matchedValue);
        assertEquals(match.capture(lowercase), characters(" little string.").collect(toList()));
    }

    private Extractor.Scoped<String, String> endsWith(String suffix) {
        return assumingType(String.class, string -> Option.of(suffix).filter(__ -> string.endsWith(suffix)));
    }

    private Matcher<List<String>> hasLowercaseChars = match(assumingType(String.class, string -> {
        List<String> lowercaseChars = characters(string).filter(this::isLowerCase).collect(toList());
        return Option.of(lowercaseChars).filter(l -> !l.isEmpty());
    }));

    private boolean isLowerCase(String string) {
        return string.toLowerCase().equals(string);
    }

    @Test
    void optional_properties() {
        Property<PlanNode> onlySource = optionalProperty(node ->
                Option.of(node.getSources())
                        .filter(sources -> sources.size() == 1)
                        .map(sources -> sources.get(0)));

        Matcher<PlanNode> planNodeWithExactlyOneSource = match(PlanNode.class)
                .with(onlySource.matching(any()));

        assertMatch(planNodeWithExactlyOneSource, new ProjectNode(new ScanNode()));
        assertNoMatch(planNodeWithExactlyOneSource, new ScanNode());
        assertNoMatch(planNodeWithExactlyOneSource, new JoinNode(new ScanNode(), new ScanNode()));
    }

    @Test
    void capturing_matches_in_a_typesafe_manner() {
        Capture<FilterNode> filter = newCapture();
        Capture<ScanNode> scan = newCapture();

        Matcher<ProjectNode> matcher = Project
                .with(source.matching(Filter.capturedAs(filter)
                        .with(source.matching(Scan.capturedAs(scan)))));

        ProjectNode tree = new ProjectNode(new FilterNode(new ScanNode(), null));

        Match<ProjectNode> match = assertMatch(matcher, tree);
        //notice the concrete type despite no casts:
        FilterNode capturedFilter = match.capture(filter);
        assertEquals(tree.getSource(), capturedFilter);
        assertEquals(((FilterNode) tree.getSource()).getSource(), match.capture(scan));
    }

    @Test
    void evidence_backed_matching_using_extractors() {
        Matcher<List<String>> stringWithVowels = match(assumingType(String.class, (x) -> {
            List<String> vowels = characters(x).filter(c -> "aeiouy".contains(c.toLowerCase())).collect(toList());
            return Option.of(vowels).filter(l -> !l.isEmpty());
        }));

        Capture<List<String>> vowels = newCapture();

        Match<List<String>> match = assertMatch(stringWithVowels.capturedAs(vowels), "John Doe", asList("o", "o", "e"));
        assertEquals(match.value(), match.capture(vowels));

        assertNoMatch(stringWithVowels, "pqrst");
    }

    private Stream<String> characters(String string) {
        return string.chars().mapToObj(c -> String.valueOf((char) c));
    }

    @Test
    void no_match_means_no_captures() {
        Capture<Void> impossible = newCapture();
        Matcher<Void> matcher = match(Void.class).capturedAs(impossible);

        Match<Void> match = matcher.match(42);

        assertTrue(match.isEmpty());
        Throwable throwable = assertThrows(NoSuchElementException.class, () -> match.capture(impossible));
        assertTrue(() -> throwable.getMessage().contains("Empty match contains no value"));
    }

    @Test
    void unknown_capture_is_an_error() {
        Matcher<?> matcher = any();
        Capture<?> unknownCapture = newCapture();

        Match<?> match = matcher.match(42);

        Throwable throwable = assertThrows(NoSuchElementException.class, () -> match.capture(unknownCapture));
        assertTrue(() -> throwable.getMessage().contains("unknown Capture"));
        //TODO make the error message somewhat help which capture was used, when the captures are human-discernable.
    }

    @Test
    void extractors_parameterized_with_captures() {
        Capture<JoinNode> root = newCapture();
        Capture<JoinNode> parent = newCapture();
        Capture<ScanNode> left = newCapture();
        Capture<ScanNode> right = newCapture();
        Capture<List<PlanNode>> caputres = newCapture();

        Matcher<List<PlanNode>> accessingTheDesiredCaptures = match(assumingType(PlanNode.class, (node, params) ->
                Option.of(asList(
                        params.get(left), params.get(right), params.get(root), params.get(parent)
                )))
        );

        Matcher<JoinNode> matcher = Join.capturedAs(root)
                .with(probe.matching(Join.capturedAs(parent)
                        .with(probe.matching(Scan.capturedAs(left)))
                        .with(build.matching(Scan.capturedAs(right)))))
                .with(build.matching(Scan
                        .matching(accessingTheDesiredCaptures.capturedAs(caputres))));

        ScanNode expectedLeft = new ScanNode();
        ScanNode expectedRight = new ScanNode();
        JoinNode expectedParent = new JoinNode(expectedLeft, expectedRight);
        JoinNode expectedRoot = new JoinNode(expectedParent, new ScanNode());

        Match<JoinNode> match = assertMatch(matcher, expectedRoot);
        assertEquals(match.capture(caputres), asList(expectedLeft, expectedRight, expectedRoot, expectedParent));
    }

    @Test
    void null_not_matched_by_default() {
        assertNoMatch(any(), null);
        assertNoMatch(match(Integer.class), null);

        //nulls can be matched using a custom extractor for now
        Extractor<Object> nullAcceptingExtractor = (x, captures) -> Option.of(x);
        assertMatch(match(nullAcceptingExtractor), null);
    }

    private <T> Match<T> assertMatch(Matcher<T> matcher, T expectedMatch) {
        return assertMatch(matcher, expectedMatch, expectedMatch);
    }

    private <T, R> Match<R> assertMatch(Matcher<R> matcher, T matchedAgainst, R expectedMatch) {
        Match<R> match = matcher.match(matchedAgainst);
        assertEquals(expectedMatch, match.value());
        return match;
    }

    private <T> void assertNoMatch(Matcher<T> matcher, Object expectedNoMatch) {
        Match<T> match = matcher.match(expectedNoMatch);
        assertEquals(Match.empty(), match);
    }
}