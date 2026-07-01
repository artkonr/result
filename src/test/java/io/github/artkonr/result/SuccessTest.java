package io.github.artkonr.result;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class SuccessTest {

    @Nested
    class Creation {
        @Test
        void should_create_success_instance() {
            Success<IOException> success = new Success<>();
            assertNotNull(success);
            assertTrue(success.isSuccess());
        }

        @Test
        void should_create_with_generic_exception_type() {
            Success<RuntimeException> success = new Success<>();
            assertTrue(success.isSuccess());
        }

        @Test
        void should_create_with_generic_checked_exception() {
            Success<IOException> success = new Success<>();
            assertTrue(success.isSuccess());
        }
    }

    @Nested
    class Implementation {
        @Test
        void should_implement_done_interface() {
            Success<IOException> success = new Success<>();
            assertInstanceOf(Done.class, success);
        }

        @Test
        void should_be_serializable_as_done() {
            Done<IOException> done = new Success<>();
            assertTrue(done.isSuccess());
        }
    }

    @Nested
    class PatternMatching {
        @Test
        void should_match_in_switch() {
            Done<IOException> done = new Success<>();
            String result = switch (done) {
                case Success<IOException> s -> "success";
                case Failure<IOException> f -> "failure";
            };
            assertEquals("success", result);
        }

        @Test
        void should_participate_in_instance_comparison() {
            Success<IOException> success1 = new Success<>();
            Success<IOException> success2 = new Success<>();
            assertTrue(success1.isSuccess());
            assertTrue(success2.isSuccess());
        }
    }
}
