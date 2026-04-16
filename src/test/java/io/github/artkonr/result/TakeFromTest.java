package io.github.artkonr.result;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class TakeFromTest {

    @Test
    void should_take_error_from_head_when_both_err() {
        Exception leftErr = new RuntimeException("left");
        Exception rightErr = new RuntimeException("right");
        Result<String, Exception> left = new Err<>(leftErr);
        Result<String, Exception> right = new Err<>(rightErr);

        Optional<Exception> taken = TakeFrom.HEAD.takeError(left, right);
        assertTrue(taken.isPresent());
        assertSame(leftErr, taken.get());
    }

    @Test
    void should_take_error_from_tail_when_both_err() {
        Exception leftErr = new RuntimeException("left");
        Exception rightErr = new RuntimeException("right");
        Result<String, Exception> left = new Err<>(leftErr);
        Result<String, Exception> right = new Err<>(rightErr);

        Optional<Exception> taken = TakeFrom.TAIL.takeError(left, right);
        assertTrue(taken.isPresent());
        assertSame(rightErr, taken.get());
    }

    @Test
    void should_take_error_from_left_when_only_left_err() {
        Exception leftErr = new RuntimeException("left");
        Result<String, Exception> left = new Err<>(leftErr);
        Result<String, Exception> right = new Ok<>("success");

        Optional<Exception> taken = TakeFrom.HEAD.takeError(left, right);
        assertTrue(taken.isPresent());
        assertSame(leftErr, taken.get());
    }

    @Test
    void should_take_same_error_from_left_regardless_of_rule_when_only_left_err() {
        Exception leftErr = new RuntimeException("left");
        Result<String, Exception> left = new Err<>(leftErr);
        Result<String, Exception> right = new Ok<>("success");

        Optional<Exception> takenHead = TakeFrom.HEAD.takeError(left, right);
        Optional<Exception> takenTail = TakeFrom.TAIL.takeError(left, right);
        assertTrue(takenHead.isPresent());
        assertTrue(takenTail.isPresent());
        assertSame(leftErr, takenHead.get());
        assertSame(leftErr, takenTail.get());
    }

    @Test
    void should_take_error_from_right_when_only_right_err() {
        Result<String, Exception> left = new Ok<>("success");
        Exception rightErr = new RuntimeException("right");
        Result<String, Exception> right = new Err<>(rightErr);

        Optional<Exception> taken = TakeFrom.HEAD.takeError(left, right);
        assertTrue(taken.isPresent());
        assertSame(rightErr, taken.get());
    }

    @Test
    void should_take_same_error_from_right_regardless_of_rule_when_only_right_err() {
        Result<String, Exception> left = new Ok<>("success");
        Exception rightErr = new RuntimeException("right");
        Result<String, Exception> right = new Err<>(rightErr);

        Optional<Exception> takenHead = TakeFrom.HEAD.takeError(left, right);
        Optional<Exception> takenTail = TakeFrom.TAIL.takeError(left, right);
        assertTrue(takenHead.isPresent());
        assertTrue(takenTail.isPresent());
        assertSame(rightErr, takenHead.get());
        assertSame(rightErr, takenTail.get());
    }

    @Test
    void should_return_empty_when_both_ok() {
        Result<String, Exception> left = new Ok<>("left");
        Result<String, Exception> right = new Ok<>("right");

        Optional<Exception> taken = TakeFrom.HEAD.takeError(left, right);
        assertTrue(taken.isEmpty());
    }

    @Test
    void should_return_empty_regardless_of_rule_when_both_ok() {
        Result<String, Exception> left = new Ok<>("left");
        Result<String, Exception> right = new Ok<>("right");

        Optional<Exception> takenHead = TakeFrom.HEAD.takeError(left, right);
        Optional<Exception> takenTail = TakeFrom.TAIL.takeError(left, right);
        assertTrue(takenHead.isEmpty());
        assertTrue(takenTail.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("takingRuleProvider")
    void should_follow_take_rules(TakeFrom rule, boolean expectLeft) {
        Exception leftErr = new RuntimeException("left");
        Exception rightErr = new RuntimeException("right");
        Result<String, Exception> left = new Err<>(leftErr);
        Result<String, Exception> right = new Err<>(rightErr);

        Optional<Exception> taken = rule.takeError(left, right);
        assertTrue(taken.isPresent());
        if (expectLeft) {
            assertSame(leftErr, taken.get());
        } else {
            assertSame(rightErr, taken.get());
        }
    }

    static Stream<Object[]> takingRuleProvider() {
        return Stream.of(
            new Object[]{TakeFrom.HEAD, true},
            new Object[]{TakeFrom.TAIL, false}
        );
    }

    @Test
    void should_have_head_value() {
        assertNotNull(TakeFrom.HEAD);
        assertEquals("HEAD", TakeFrom.HEAD.name());
    }

    @Test
    void should_have_tail_value() {
        assertNotNull(TakeFrom.TAIL);
        assertEquals("TAIL", TakeFrom.TAIL.name());
    }

    @Test
    void should_have_two_enum_values() {
        TakeFrom[] values = TakeFrom.values();
        assertEquals(2, values.length);
    }

}
