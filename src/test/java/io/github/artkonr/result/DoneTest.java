package io.github.artkonr.result;

import java.io.FileNotFoundException;
import java.security.KeyException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class DoneTest {

    @Nested
    class Wrap {
        @Test
        void success_if_ran() {
            Done<Exception> result = Done.wrap(() -> {
            });
            assertTrue(result.isSuccess());
            assertFalse(result.isFailure());
        }

        @Test
        void fail_if_errored() {
            IOException exception = new IOException("test error");
            Done<Exception> result = Done.wrap(() -> { throw exception; });
            assertTrue(result.isFailure());
            assertFalse(result.isSuccess());
            assertSame(exception, result.failure());
        }

        @Test
        void throws_if_no_arg() {
            assertThrows(IllegalArgumentException.class, () -> Done.wrap(null));
            assertThrows(IllegalArgumentException.class, () -> Done.wrap(
              null,
              () -> {
              }
            ));
            assertThrows(IllegalArgumentException.class, () -> Done.wrap(
              IOException.class,
              null
            ));
        }

        @Test
        void success_if_ran_w_err_type() {
            Done<IOException> result = Done.wrap(IOException.class, () -> {
            });
            assertTrue(result.isSuccess());
        }

        @Test
        void fail_if_errored_w_err_type() {
            IOException exception = new IOException("io error");
            Done<IOException> result = Done.wrap(IOException.class, () -> {
                throw exception;
            });
            assertTrue(result.isFailure());
            assertSame(exception, result.failure());
        }

        @Test
        void throws_if_err_type_not_matched() {
            assertThrows(IllegalStateException.class, () -> Done.wrap(
                IOException.class,
                () -> { throw new RuntimeException("wrong type"); }
            ));
        }
    }

    @Nested
    class From {
        @Test
        void from_success_is_success() {
            Done<IOException> done = new Success<>();
            Done<IOException> copy = Done.from(done);
            assertTrue(copy.isSuccess());
        }

        @Test
        void from_fail_is_fail() {
            IOException exception = new IOException("error");
            Done<IOException> done = new Failure<>(exception);
            Done<IOException> copy = Done.from(done);
            assertTrue(copy.isFailure());
            assertSame(exception, copy.failure());
        }

        @Test
        void throws_if_no_arg() {
            assertThrows(IllegalArgumentException.class, () -> Done.from((Done<Exception>) null));
            assertThrows(IllegalArgumentException.class, () -> Done.from((Result<String, Exception>) null));
        }

        @Test
        void from_success_is_ok() {
            Result<String, IOException> ok = new Ok<>("value");
            Done<IOException> done = Done.from(ok);
            assertTrue(done.isSuccess());
        }

        @Test
        void from_fail_is_err() {
            IOException exception = new IOException("error");
            Result<String, IOException> err = new Err<>(exception);
            Done<IOException> done = Done.from(err);
            assertTrue(done.isFailure());
            assertSame(exception, done.failure());
        }
    }

    @Nested
    class Chain {
        @Test
        void no_invocations_is_success() {
            Done<IOException> result = Done.chain(Collections.emptyList());
            assertTrue(result.isSuccess());
        }

        @Test
        void all_success_is_success() {
            Done<IOException> result = Done.chain(Arrays.asList(
              Success::new,
              Success::new,
              Success::new
            ));
            assertTrue(result.isSuccess());
        }

        @Test
        void any_fail_trip_on_first() {
            IOException exception = new IOException("first error");
            Done<IOException> result = Done.chain(Arrays.asList(
              Success::new,
              () -> new Failure<>(exception),
              () -> new Failure<>(new IOException("should not reach"))
            ));
            assertTrue(result.isFailure());
            assertSame(exception, result.failure());
        }

        @Test
        void omit_null_invocation_functions() {
            IOException exception = new IOException("error");
            Done<IOException> result = Done.chain(Arrays.asList(
              Success::new,
              null,
              () -> new Failure<>(exception)
            ));
            assertTrue(result.isFailure());
            assertSame(exception, result.failure());
        }

        @Test
        void omit_null_invocation_results() {
            IOException exception = new IOException("error");
            Done<IOException> result = Done.chain(Arrays.asList(
              Success::new,
              () -> null,
              () -> new Failure<>(exception)
            ));
            assertTrue(result.isFailure());
            assertSame(exception, result.failure());
        }

        @Test
        void throws_if_no_arg() {
            assertThrows(IllegalArgumentException.class, () -> Done.chain(null));
            assertThrows(IllegalArgumentException.class, () -> Done.chainAsync(null, null));
            assertThrows(IllegalArgumentException.class, () -> Done.chainAsync(List.of(), null));
            assertThrows(IllegalArgumentException.class, () -> Done.chainAsync(null));
        }

        @Test
        void async_all_success_is_success() {
            var executor = Executors.newVirtualThreadPerTaskExecutor();
            try {
                CompletableFuture<Done<IOException>> future = Done.chainAsync(
                  Arrays.asList(
                    Success::new,
                    Success::new,
                    Success::new
                  ),
                  executor
                );
                Done<IOException> result = future.join();
                assertTrue(result.isSuccess());
                assertFalse(result.isFailure());
            } finally {
                executor.shutdown();
            }
        }

        @Test
        void async_any_fail_is_fail() {
            var executor = Executors.newVirtualThreadPerTaskExecutor();
            try {
                IOException exception = new IOException("async error");
                CompletableFuture<Done<IOException>> future = Done.chainAsync(
                  Arrays.asList(
                    Success::new,
                    () -> new Failure<>(exception),
                    Success::new
                  ),
                  executor
                );
                Done<IOException> result = future.join();
                assertTrue(result.isFailure());
                assertSame(exception, result.failure());
            } finally {
                executor.shutdown();
            }
        }

        @Test
        void async_vt_success_is_success() {
            CompletableFuture<Done<IOException>> future = Done.chainAsync(
              Arrays.asList(
                Success::new,
                Success::new,
                Success::new
              )
            );
            Done<IOException> result = future.join();
            assertTrue(result.isSuccess());
        }

        @Test
        void async_no_invocations_is_success() {
            CompletableFuture<Done<IOException>> future = Done.chainAsync(Collections.emptyList());
            Done<IOException> result = future.join();
            assertTrue(result.isSuccess());
        }
    }

    @Nested
    class Join {
        @Test
        void no_items_is_success() {
            Done<IOException> result = Done.join(Collections.emptyList(), TakeFrom.HEAD);
            assertTrue(result.isSuccess());
        }

        @Test
        void all_success_is_success() {
            Done<IOException> result = Done.join(Arrays.asList(
                new Success<>(),
                new Success<>(),
                new Success<>()
            ), TakeFrom.HEAD);
            assertTrue(result.isSuccess());
        }

        @Test
        void any_fail_w_fail_from_head() {
            IOException first = new IOException("first");
            IOException second = new IOException("second");
            Done<IOException> result = Done.join(Arrays.asList(
                new Failure<>(first),
                new Failure<>(second)
            ), TakeFrom.HEAD);
            assertTrue(result.isFailure());
            assertSame(first, result.failure());
        }

        @Test
        void any_fail_w_rule_from_tail() {
            IOException first = new IOException("first");
            IOException second = new IOException("second");
            Done<IOException> result = Done.join(Arrays.asList(
              new Failure<>(first),
              new Failure<>(second)
            ), TakeFrom.TAIL);
            assertTrue(result.isFailure());
            assertSame(second, result.failure());
        }

        @Test
        void omit_null_items() {
            IOException exception = new IOException("error");
            Done<IOException> result = Done.join(Arrays.asList(
                new Success<>(),
                null,
                new Failure<>(exception)
            ), TakeFrom.HEAD);
            assertTrue(result.isFailure());
            assertSame(exception, result.failure());
        }

        @Test
        void throws_if_no_arg() {
            assertThrows(IllegalArgumentException.class, () -> Done.join(null, TakeFrom.HEAD));
            assertThrows(IllegalArgumentException.class, () -> Done.join(Collections.emptyList(), null));
        }
    }

    @Nested
    class StateChecking {
        @Test
        void check_success() {
            Done<IOException> success = new Success<>();
            Done<IOException> failure = new Failure<>(new IOException());
            assertTrue(success.isSuccess());
            assertFalse(failure.isSuccess());
        }

        @Test
        void check_fail() {
            Done<IOException> success = new Success<>();
            Done<IOException> failure = new Failure<>(new IOException());
            assertFalse(success.isFailure());
            assertTrue(failure.isFailure());
        }

        @Test
        void check_fail_w_type() {
            Done<IOException> fileNotFound = new Failure<>(new FileNotFoundException());
            Done<IOException> ioErr = new Failure<>(new IOException());
            Done<IOException> success = new Success<>();

            assertTrue(fileNotFound.isFailureAnd(FileNotFoundException.class));
            assertTrue(fileNotFound.isFailureAnd(IOException.class));
            assertFalse(fileNotFound.isFailureAnd(RuntimeException.class));
            assertFalse(ioErr.isFailureAnd(FileNotFoundException.class));
            assertFalse(success.isFailureAnd(IOException.class));
        }

        @Test
        void throws_if_no_arg() {
            Done<IOException> done = new Success<>();
            assertThrows(IllegalArgumentException.class, () -> done.isFailureAnd((Class<? extends Exception>) null));
            assertThrows(IllegalArgumentException.class, () -> done.isFailureAnd((Predicate<IOException>) null));
        }

        @Test
        void check_fail_w_predicate() {
            IOException timeout = new IOException("timeout");
            IOException other = new IOException("other");
            Done<IOException> timeoutDone = new Failure<>(timeout);
            Done<IOException> otherDone = new Failure<>(other);
            Done<IOException> success = new Success<>();

            Predicate<IOException> p1 = e -> e.getMessage().contains("timeout");
            Predicate<IOException> p2 = e -> e.getMessage().contains("other");
            Predicate<IOException> p3 = e -> e.getMessage().contains("timeout");
            Predicate<IOException> p4 = e -> true;

            assertTrue(timeoutDone.isFailureAnd(p1));
            assertFalse(timeoutDone.isFailureAnd(p2));
            assertFalse(otherDone.isFailureAnd(p3));
            assertFalse(success.isFailureAnd(p4));
        }
    }

    @Nested
    class Stack {
        @Test
        void replace_if_fail() {
            Done<IOException> done = new Failure<>(new IOException("original"));
            RuntimeException replacement = new RuntimeException("replaced");
            Done<RuntimeException> stacked = done.stack(replacement);
            assertTrue(stacked.isFailure());
            assertInstanceOf(RuntimeException.class, stacked.failure());
            assertEquals("replaced", stacked.failure().getMessage());
        }

        @Test
        void skip_if_success() {
            Done<IOException> done = new Success<>();
            RuntimeException replacement = new RuntimeException("replaced");
            Done<RuntimeException> stacked = done.stack(replacement);
            assertTrue(stacked.isSuccess());
        }

        @Test
        void replace_w_old_if_fail() {
            IOException original = new IOException("original");
            Done<IOException> done = new Failure<>(original);
            Done<RuntimeException> stacked = done.stack((Function<IOException, RuntimeException>) old -> new RuntimeException(old.getMessage()));
            assertTrue(stacked.isFailure());
            assertEquals("original", stacked.failure().getMessage());
        }

        @Test
        void skip_w_old_if_success() {
            Done<IOException> done = new Success<>();
            Done<RuntimeException> stacked = done.<RuntimeException>stack(e -> new RuntimeException(e.getMessage()));
            assertTrue(stacked.isSuccess());
        }

        @Test
        void replace_w_supplier_if_fail() {
            Done<IOException> done = new Failure<>(new IOException("original"));
            Done<RuntimeException> stacked = done.<RuntimeException>stack(() -> new RuntimeException("supplied"));
            assertTrue(stacked.isFailure());
            assertEquals("supplied", stacked.failure().getMessage());
        }

        @Test
        void skip_w_supplier_if_success() {
            Done<IOException> done = new Success<>();
            Done<RuntimeException> stacked = done.<RuntimeException>stack(() -> new RuntimeException("supplied"));
            assertTrue(stacked.isSuccess());
        }

        @Test
        void throws_if_no_arg() {
            Done<IOException> done = new Failure<>(new IOException());
            assertThrows(IllegalArgumentException.class, () -> done.stack((Exception) null));
            assertThrows(IllegalArgumentException.class, () -> done.stack((Function<IOException, RuntimeException>) null));
            assertThrows(IllegalArgumentException.class, () -> done.stack((Supplier<RuntimeException>) null));
        }
    }

    @Nested
    class Populate {
        @Test
        void populate_if_success() {
            Done<IOException> success = new Success<>();
            Result<String, IOException> result = success.populate("value");
            assertTrue(result.isOk());
            assertEquals("value", result.value());
        }

        @Test
        void transfer_err_if_fail() {
            IOException exception = new IOException("error");
            Done<IOException> failure = new Failure<>(exception);
            Result<String, IOException> result = failure.populate("value");
            assertTrue(result.isErr());
            assertSame(exception, result.err());
        }

        @Test
        void populate_w_fn_if_success() {
            Done<IOException> success = new Success<>();
            Result<String, IOException> result = success.populate(() -> "supplied");
            assertTrue(result.isOk());
            assertEquals("supplied", result.value());
        }

        @Test
        void transfer_err_w_fn_if_fail() {
            IOException exception = new IOException("error");
            Done<IOException> failure = new Failure<>(exception);
            Result<String, IOException> result = failure.populate(() -> "supplied");
            assertTrue(result.isErr());
            assertSame(exception, result.err());
        }

        @Test
        void throws_if_no_arg() {
            Done<IOException> done = new Success<>();
            assertThrows(IllegalArgumentException.class, () -> done.populate((Object) null));
            assertThrows(IllegalArgumentException.class, () -> done.populate((Supplier<String>) null));
        }
    }

    @Nested
    class Upcast {
        @Test
        void upcast_success() {
            Done<IOException> done = new Success<>();
            Done<Exception> upcasted = done.upcast();
            assertTrue(upcasted.isSuccess());
        }

        @Test
        void upcast_err() {
            IOException exception = new IOException("error");
            Done<IOException> done = new Failure<>(exception);
            Done<Exception> upcasted = done.upcast();
            assertTrue(upcasted.isFailure());
            assertSame(exception, upcasted.failure());
        }
    }

    @Nested
    class Then {
        @Test
        void transform_if_success() {
            Done<IOException> done = new Success<>();
            Done<IOException> result = done.then(() -> new Failure<>(new IOException("error")));
            assertTrue(result.isFailure());
        }

        @Test
        void skip_if_fail() {
            IOException exception = new IOException("original");
            Done<IOException> done = new Failure<>(exception);
            Done<IOException> result = done.then(Success::new);
            assertTrue(result.isFailure());
            assertSame(exception, result.failure());
        }

        @Test
        void throws_if_no_arg() {
            Done<IOException> done = new Success<>();
            assertThrows(IllegalArgumentException.class, () -> done.then(null));
        }
    }

    @Nested
    class Peek {
        @Test
        void peek_if_success() {
            Done<IOException> done = new Success<>();
            List<Integer> called = new ArrayList<>();
            Done<IOException> result = done.peek(() -> called.add(1));
            assertEquals(1, called.size());
            assertTrue(result.isSuccess());
        }

        @Test
        void skip_if_fail() {
            Done<IOException> done = new Failure<>(new IOException());
            List<Integer> called = new ArrayList<>();
            Done<IOException> result = done.peek(() -> called.add(1));
            assertEquals(0, called.size());
            assertTrue(result.isFailure());
        }

        @Test
        void throws_if_no_arg() {
            Done<IOException> done = new Success<>();
            assertThrows(IllegalArgumentException.class, () -> done.peek(null));
        }
    }

    @Nested
    class Inspect {
        @Test
        void inspect_if_fail() {
            IOException exception = new IOException("error");
            Done<IOException> done = new Failure<>(exception);
            List<IOException> called = new ArrayList<>();
            Done<IOException> result = done.inspect(called::add);
            assertEquals(1, called.size());
            assertSame(exception, called.getFirst());
            assertTrue(result.isFailure());
        }

        @Test
        void skip_if_success() {
            Done<IOException> done = new Success<>();
            List<IOException> called = new ArrayList<>();
            Done<IOException> result = done.inspect(called::add);
            assertEquals(0, called.size());
            assertTrue(result.isSuccess());
        }

        @Test
        void inspect_w_predicate_if_fail() {
            IOException timeout = new IOException("timeout");
            Done<IOException> done = new Failure<>(timeout);
            List<IOException> called = new ArrayList<>();
            Done<IOException> result = done.inspect(e -> e.getMessage().contains("timeout"), called::add);
            assertEquals(1, called.size());
            assertSame(timeout, called.getFirst());
            assertTrue(result.isFailure());
        }

        @Test
        void skip_w_predicate_if_success() {
            Done<IOException> done = new Success<>();
            List<IOException> called = new ArrayList<>();
            Done<IOException> result = done.inspect(e -> e.getMessage().contains("timeout"), called::add);
            assertEquals(0, called.size());
            assertTrue(result.isSuccess());
        }

        @Test
        void skip_w_predicate_if_fail_and_not_matched() {
            IOException timeout = new IOException("timeout");
            Done<IOException> done = new Failure<>(timeout);
            List<IOException> called = new ArrayList<>();
            Done<IOException> ignored = done.inspect(e -> e.getMessage().contains("other"), called::add);
            assertEquals(0, called.size());
        }

        @Test
        void inspect_w_type_if_fail() {
            FileNotFoundException fnf = new FileNotFoundException();
            Done<IOException> done = new Failure<>(fnf);
            List<IOException> called = new ArrayList<>();
            done.inspect(FileNotFoundException.class, called::add);
            assertEquals(1, called.size());
        }

        @Test
        void skip_w_type_if_success() {
            Done<IOException> done = new Success<>();
            List<IOException> called = new ArrayList<>();
            done.inspect(FileNotFoundException.class, called::add);
            assertEquals(0, called.size());
        }

        @Test
        void skip_w_type_if_fail_and_type_mismatch() {
            FileNotFoundException fnf = new FileNotFoundException();
            Done<IOException> done = new Failure<>(fnf);
            List<IOException> called = new ArrayList<>();
            done.inspect(KeyException.class, called::add);
            assertEquals(0, called.size());
        }

        @Test
        void throws_if_no_arg() {
            Done<IOException> done = new Failure<>(new IOException());
            assertThrows(IllegalArgumentException.class, () -> done.inspect(null));
            assertThrows(IllegalArgumentException.class, () -> done.inspect(e -> true, null));
            assertThrows(IllegalArgumentException.class, () -> done.inspect((Predicate<IOException>) null, null));
            assertThrows(IllegalArgumentException.class, () -> done.inspect(IOException.class, null));
            assertThrows(IllegalArgumentException.class, () -> done.inspect((Class<? extends Exception>) null, null));
        }
    }

    @Nested
    class Taint {
        @Test
        void taint_if_success() {
            Done<IOException> done = new Success<>();
            IOException error = new IOException("tainted");
            Done<IOException> result = done.taint(error);
            assertTrue(result.isFailure());
            assertSame(error, result.failure());
        }

        @Test
        void omit_taint_if_fail() {
            IOException original = new IOException("original");
            Done<IOException> done = new Failure<>(original);
            IOException newError = new IOException("new");
            Done<IOException> result = done.taint(newError);
            assertTrue(result.isFailure());
            assertSame(original, result.failure());
        }

        @Test
        void throws_if_no_arg() {
            Done<IOException> done = new Success<>();
            assertThrows(IllegalArgumentException.class, () -> done.taint(null));
        }
    }

    @Nested
    class Fuse {
        @Test
        void fuse_with_fail_is_fail() {
            Done<IOException> success = new Success<>();
            IOException tailError = new IOException("tail");
            Done<IOException> failure = new Failure<>(tailError);
            Done<IOException> result = success.fuse(failure);
            assertTrue(result.isFailure());
            assertSame(tailError, result.failure());
        }

        @Test
        void throws_if_no_arg() {
            Done<IOException> done = new Success<>();
            assertThrows(IllegalArgumentException.class, () -> done.fuse(null));
            assertThrows(IllegalArgumentException.class, () -> done.fuse(new Success<>(), null));
            assertThrows(IllegalArgumentException.class, () -> done.fuse(null, null));
        }
    }

    @Nested
    class Recover {
        @Test
        void recover_if_fail() {
            Done<IOException> done = new Failure<>(new IOException());
            Done<IOException> result = done.recover();
            assertTrue(result.isSuccess());
        }

        @Test
        void skip_if_success() {
            Done<IOException> done = new Success<>();
            Done<IOException> result = done.recover();
            assertTrue(result.isSuccess());
            assertNotSame(done, result);
        }

        @Test
        void recover_if_fail_w_predicate() {
            IOException timeout = new IOException("timeout");
            Done<IOException> done = new Failure<>(timeout);
            Done<IOException> result = done.recover(e -> e.getMessage().contains("timeout"));
            assertTrue(result.isSuccess());
        }

        @Test
        void skip_if_fail_w_predicate_mismatch() {
            IOException timeout = new IOException("timeout");
            Done<IOException> done = new Failure<>(timeout);
            Done<IOException> result = done.recover(e -> e.getMessage().contains("other"));
            assertTrue(result.isFailure());
            assertNotSame(done, result);
        }

        @Test
        void recover_if_fail_w_type() {
            FileNotFoundException fnf = new FileNotFoundException();
            Done<IOException> done = new Failure<>(fnf);
            Done<IOException> result = done.recover(FileNotFoundException.class);
            assertTrue(result.isSuccess());
        }

        @Test
        void skip_if_fail_w_type_mismatch() {
            IOException ioError = new IOException();
            Done<IOException> done = new Failure<>(ioError);
            Done<IOException> result = done.recover(FileNotFoundException.class);
            assertTrue(result.isFailure());
            assertNotSame(done, result);
        }

        @Test
        void throws_if_no_arg() {
            Done<IOException> done = new Failure<>(new IOException());
            assertThrows(IllegalArgumentException.class, () -> done.recover((Class<? extends Exception>) null));
            assertThrows(IllegalArgumentException.class, () -> done.recover((Predicate<IOException>) null));
        }

        @Test
        void recreate_if_success_w_predicate() {
            Done<IOException> done = new Success<>();
            Done<IOException> result = done.recover(e -> e.getMessage().contains("timeout"));
            assertTrue(done.isSuccess());
            assertNotSame(done, result);
        }

        @Test
        void recreate_if_success_w_type() {
            Done<IOException> done = new Success<>();
            Done<IOException> result = done.recover(IOException.class);
            assertTrue(done.isSuccess());
            assertNotSame(done, result);
        }
    }

    @Nested
    class Extraction {
        @Test
        void return_err_if_fail() {
            IOException exception = new IOException("error");
            Done<IOException> done = new Failure<>(exception);
            assertSame(exception, done.failure());
        }

        @Test
        void throws_if_success() {
            Done<IOException> done = new Success<>();
            assertThrows(IllegalStateException.class, done::failure);
        }
    }

    @Nested
    class Conditionals {
        @Test
        void fire_success_conditional_if_success() {
            Done<IOException> done = new Success<>();
            List<Integer> called = new ArrayList<>();
            done.ifSuccess(() -> called.add(1));
            assertEquals(1, called.size());
        }

        @Test
        void skip_success_conditional_if_success() {
            Done<IOException> done = new Failure<>(new IOException());
            List<Integer> called = new ArrayList<>();
            done.ifSuccess(() -> called.add(1));
            assertEquals(0, called.size());
        }

        @Test
        void fire_fail_conditional_if_fail() {
            IOException exception = new IOException("error");
            Done<IOException> done = new Failure<>(exception);
            List<IOException> called = new ArrayList<>();
            done.ifFailure(called::add);
            assertEquals(1, called.size());
            assertSame(exception, called.getFirst());
        }

        @Test
        void skip_fail_conditional_if_success() {
            Done<IOException> done = new Success<>();
            List<IOException> called = new ArrayList<>();
            done.ifFailure(called::add);
            assertEquals(0, called.size());
        }

        @Test
        void throws_if_no_arg() {
            Done<IOException> done = new Success<>();
            assertThrows(IllegalArgumentException.class, () -> done.ifSuccess(null));
            assertThrows(IllegalArgumentException.class, () -> done.ifFailure(null));
        }
    }

    @Nested
    class Unwrap {
        @Test
        void unwrap_if_success() {
            Done<IOException> done = new Success<>();
            done.unwrap();
        }

        @Test
        void throws_if_fail() {
            IOException exception = new IOException("error");
            Done<IOException> done = new Failure<>(exception);
            assertThrows(io.github.artkonr.result.Wrap.Failure.class, done::unwrap);
        }

        @Test
        void unwrap_checked_if_success() throws IOException {
            Done<IOException> done = new Success<>();
            done.unwrapChecked();
        }

        @Test
        void throws_if_fail_checked() {
            IOException exception = new IOException("error");
            Done<IOException> done = new Failure<>(exception);
            assertThrows(IOException.class, done::unwrapChecked);
        }
    }
}
