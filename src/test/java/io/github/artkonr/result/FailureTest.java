package io.github.artkonr.result;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class FailureTest {

    @Test
    void should_wrap_exception_as_cause() {
        RuntimeException cause = new RuntimeException("original error");
        Failure failure = new Failure(cause);
        assertSame(cause, failure.getCause());
    }

    @Test
    void should_preserve_exception_message() {
        RuntimeException cause = new RuntimeException("error message");
        Failure failure = new Failure(cause);
        assertSame(cause, failure.getCause());
        assertEquals("error message", failure.getCause().getMessage());
    }

    @ParameterizedTest
    @MethodSource("exceptionProvider")
    void should_wrap_various_exception_types(Exception exception) {
        Failure failure = new Failure(exception);
        assertSame(exception, failure.getCause());
    }

    static Stream<Exception> exceptionProvider() {
        return Stream.of(
            new RuntimeException("runtime"),
            new IllegalArgumentException("illegal arg"),
            new IllegalStateException("illegal state"),
            new Exception("checked exception")
        );
    }

    @Test
    void should_be_runtime_exception() {
        Failure failure = new Failure(new RuntimeException("error"));
        assertInstanceOf(RuntimeException.class, failure);
    }

    @Test
    void should_wrap_null_cause() {
        Failure failure = new Failure(null);
        assertNull(failure.getCause());
    }

    @Test
    void should_preserve_exception_stack_trace() {
        RuntimeException cause = new RuntimeException("error");
        Failure failure = new Failure(cause);
        assertArrayEquals(cause.getStackTrace(), failure.getCause().getStackTrace());
    }

}
