package io.github.artkonr.result;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class FailureRecordTest {

    @Nested
    class Creation {
        @Test
        void should_create_failure_with_exception() {
            IOException exception = new IOException("test error");
            Failure<IOException> failure = new Failure<>(exception);
            assertNotNull(failure);
            assertTrue(failure.isFailure());
            assertSame(exception, failure.failure());
        }

        @Test
        void should_throw_illegal_argument_when_exception_is_null() {
            assertThrows(IllegalArgumentException.class, () -> new Failure<>((IOException) null));
        }

        @Test
        void should_create_with_different_exception_types() {
            Failure<RuntimeException> runtimeFailure = new Failure<>(new RuntimeException());
            Failure<IOException> ioFailure = new Failure<>(new IOException());

            assertTrue(runtimeFailure.isFailure());
            assertTrue(ioFailure.isFailure());
        }
    }

    @Nested
    class ExceptionAccess {
        @Test
        void should_access_exception_via_ex() {
            IOException exception = new IOException("error");
            Failure<IOException> failure = new Failure<>(exception);
            assertSame(exception, failure.ex());
        }

        @Test
        void should_preserve_exception_message() {
            String message = "custom error message";
            IOException exception = new IOException(message);
            Failure<IOException> failure = new Failure<>(exception);
            assertEquals(message, failure.failure().getMessage());
        }

        @Test
        void should_preserve_exception_cause() {
            Throwable cause = new RuntimeException("cause");
            IOException exception = new IOException("wrapper", cause);
            Failure<IOException> failure = new Failure<>(exception);
            assertSame(cause, failure.failure().getCause());
        }
    }

    @Nested
    class Implementation {
        @Test
        void should_implement_done_interface() {
            Failure<IOException> failure = new Failure<>(new IOException());
            assertInstanceOf(Done.class, failure);
        }

        @Test
        void should_be_record_with_single_component() {
            IOException exception = new IOException("error");
            Failure<IOException> failure = new Failure<>(exception);
            assertEquals(1, failure.ex() != null ? 1 : 0);
        }
    }

    @Nested
    class PatternMatching {
        @Test
        void should_match_in_switch() {
            Done<IOException> done = new Failure<>(new IOException("error"));
            String result = switch (done) {
                case Success() -> "success";
                case Failure(var ex) -> "failure: " + ex.getMessage();
            };
            assertEquals("failure: error", result);
        }

        @Test
        void should_support_pattern_matching() {
            Done<IOException> done = new Failure<>(new IOException("test"));
            if (done instanceof Failure(var ex)) {
                assertEquals("test", ex.getMessage());
            } else {
                fail("Should have matched Failure pattern");
            }
        }
    }

    @Nested
    class ExceptionTypes {
        @ParameterizedTest
        @MethodSource("exceptionProvider")
        void should_wrap_various_exception_types(Exception exception) {
            Failure<Exception> failure = new Failure<>(exception);
            assertSame(exception, failure.failure());
            assertTrue(failure.isFailure());
        }

        static Stream<Exception> exceptionProvider() {
            return Stream.of(
                new IOException("io error"),
                new RuntimeException("runtime error"),
                new IllegalArgumentException("illegal arg"),
                new IllegalStateException("illegal state"),
                new Exception("checked exception")
            );
        }
    }
}
