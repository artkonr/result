package io.github.artkonr.result;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Nested;

@SuppressWarnings("ConstantConditions")
class ResultTest {

    @Nested
    class Wrap {
        @Test
        void ok_if_success() {
            Result<String, Exception> result = Result.wrap(() -> "success");
            assertTrue(result.isOk());
            assertEquals("success", result.value());
        }

        @Test
        void err_if_failed() {
            Result<String, Exception> result = Result.wrap(() -> {
                throw new RuntimeException("error");
            });
            assertTrue(result.isErr());
            assertInstanceOf(RuntimeException.class, result.err());
            assertEquals("error", result.err().getMessage());
        }

        @Test
        void throws_if_no_arg() {
            assertThrows(IllegalArgumentException.class, () -> Result.wrap(null));
            assertThrows(IllegalArgumentException.class, () -> Result.wrap(() -> null));
            assertThrows(IllegalArgumentException.class, () -> Result.wrap(
              null,
              () -> "success"
            ));
            assertThrows(IllegalArgumentException.class, () -> Result.wrap(
              IllegalArgumentException.class,
              null
            ));
        }

        @Test
        void ok_if_success_w_error_type() {
            Result<String, IllegalArgumentException> result = Result.wrap(
                IllegalArgumentException.class,
                () -> "success"
            );
            assertTrue(result.isOk());
            assertEquals("success", result.value());
        }

        @Test
        void err_if_success_w_error_type() {
            Result<String, IllegalArgumentException> result = Result.wrap(
                IllegalArgumentException.class,
                () -> {
                    throw new IllegalArgumentException("invalid");
                }
            );
            assertTrue(result.isErr());
            assertInstanceOf(IllegalArgumentException.class, result.err());
        }

        @Test
        void throws_when_exception_type_mismatches() {
            assertThrows(IllegalStateException.class, () -> Result.wrap(
                IllegalArgumentException.class,
                () -> {
                    throw new RuntimeException("wrong type");
                }
            ));
        }
    }

    @Nested
    class From {
        @Test
        void ok_if_ok() {
            Result<String, Exception> ok = new Ok<>("value");
            Result<String, Exception> copy = Result.from(ok);
            assertTrue(copy.isOk());
            assertEquals("value", copy.value());
        }

        @Test
        void err_if_err() {
            Exception ex = new RuntimeException("error");
            Result<String, Exception> err = new Err<>(ex);
            Result<String, Exception> copy = Result.from(err);
            assertTrue(copy.isErr());
            assertSame(ex, copy.err());
        }

        @Test
        void throws_if_no_arg() {
            assertThrows(IllegalArgumentException.class, () -> Result.from(null));
        }
    }

    @Nested
    class Chain {
        @Test
        void ok_if_all_ok() {
            List<Supplier<Result<Integer, Exception>>> invocations = List.of(
                () -> new Ok<>(1),
                () -> new Ok<>(2),
                () -> new Ok<>(3)
            );
            Result<List<Integer>, Exception> result = Result.chain(invocations);
            assertTrue(result.isOk());
            assertEquals(List.of(1, 2, 3), result.value());
        }

        @Test
        void err_if_first_err() {
            List<Supplier<Result<Integer, Exception>>> invocations = List.of(
                () -> new Ok<>(1),
                () -> new Err<>(new RuntimeException("error1")),
                () -> new Ok<>(3)
            );
            Result<List<Integer>, Exception> result = Result.chain(invocations);
            assertTrue(result.isErr());
            assertEquals("error1", result.err().getMessage());
        }

        @Test
        void omit_null_elements() {
            List<Supplier<Result<Integer, Exception>>> invocations = new ArrayList<>();
            invocations.add(() -> new Ok<>(1));
            invocations.add(null);
            invocations.add(() -> new Ok<>(2));
            Result<List<Integer>, Exception> result = Result.chain(invocations);
            assertTrue(result.isOk());
            assertEquals(List.of(1, 2), result.value());
        }

        @Test
        void omit_null_supplied_elements() {
            List<Supplier<Result<Integer, Exception>>> invocations = List.of(
                () -> new Ok<>(1),
                () -> null,
                () -> new Ok<>(2)
            );
            Result<List<Integer>, Exception> result = Result.chain(invocations);
            assertTrue(result.isOk());
            assertEquals(List.of(1, 2), result.value());
        }

        @Test
        void ok_if_empty_collection() {
            Result<List<Integer>, Exception> result = Result.chain(List.of());
            assertTrue(result.isOk());
            assertEquals(List.of(), result.value());
        }

        @Test
        void throws_if_no_arg() {
            assertThrows(IllegalArgumentException.class, () -> Result.chain(null));
            assertThrows(IllegalArgumentException.class, () -> Result.chainAsync(null));
            assertThrows(IllegalArgumentException.class, () -> Result.chainAsync(null, null));
            assertThrows(IllegalArgumentException.class, () -> Result.chainAsync(List.of(), null));
        }

        @Test
        void async_ok_if_all_ok() {
            var executor = Executors.newVirtualThreadPerTaskExecutor();
            try {
                CompletableFuture<Result<List<Integer>, Exception>> future = Result.chainAsync(
                    List.of(
                        () -> new Ok<>(1),
                        () -> new Ok<>(2),
                        () -> new Ok<>(3)
                    ),
                    executor
                );
                Result<List<Integer>, Exception> result = future.join();
                assertTrue(result.isOk());
                assertEquals(List.of(1, 2, 3), result.value());
            } finally {
                executor.shutdown();
            }
        }

        @Test
        void async_err_if_any_err() {
            var executor = Executors.newVirtualThreadPerTaskExecutor();
            try {
                Exception exception = new RuntimeException("async error");
                CompletableFuture<Result<List<Integer>, Exception>> future = Result.chainAsync(
                    List.of(
                        () -> new Ok<>(1),
                        () -> new Err<>(exception),
                        () -> new Ok<>(3)
                    ),
                    executor
                );
                Result<List<Integer>, Exception> result = future.join();
                assertTrue(result.isErr());
                assertSame(exception, result.err());
            } finally {
                executor.shutdown();
            }
        }

        @Test
        void async_vt_ok_if_all_ok() {
            CompletableFuture<Result<List<Integer>, Exception>> future = Result.chainAsync(
                List.of(
                    () -> new Ok<>(1),
                    () -> new Ok<>(2),
                    () -> new Ok<>(3)
                )
            );
            Result<List<Integer>, Exception> result = future.join();
            assertTrue(result.isOk());
            assertEquals(List.of(1, 2, 3), result.value());
        }

        @Test
        void async_ok_if_empty_collection() {
            CompletableFuture<Result<List<Integer>, Exception>> future = Result.chainAsync(List.of());
            Result<List<Integer>, Exception> result = future.join();
            assertTrue(result.isOk());
            assertEquals(List.of(), result.value());
        }
    }

    @Nested
    class Join {
        @Test
        void ok_if_all_ok() {
            List<Result<String, Exception>> results = List.of(
                new Ok<>("a"),
                new Ok<>("b"),
                new Ok<>("c")
            );
            Result<List<String>, Exception> result = Result.join(results, TakeFrom.HEAD);
            assertTrue(result.isOk());
            assertEquals(List.of("a", "b", "c"), result.value());
        }

        @Test
        void head_err_if_any_err_w_rule() {
            Exception ex1 = new RuntimeException("first");
            Exception ex2 = new RuntimeException("second");
            List<Result<String, Exception>> results = List.of(
                new Ok<>("a"),
                new Err<>(ex1),
                new Err<>(ex2)
            );
            Result<List<String>, Exception> result = Result.join(results, TakeFrom.HEAD);
            assertTrue(result.isErr());
            assertSame(ex1, result.err());
        }

        @Test
        void tail_err_if_any_err_w_rule() {
            Exception ex1 = new RuntimeException("first");
            Exception ex2 = new RuntimeException("second");
            List<Result<String, Exception>> results = List.of(
                new Ok<>("a"),
                new Err<>(ex1),
                new Err<>(ex2)
            );
            Result<List<String>, Exception> result = Result.join(results, TakeFrom.TAIL);
            assertTrue(result.isErr());
            assertSame(ex2, result.err());
        }

        @Test
        void omit_null_items() {
            List<Result<String, Exception>> results = new ArrayList<>();
            results.add(new Ok<>("a"));
            results.add(null);
            results.add(new Ok<>("b"));
            Result<List<String>, Exception> result = Result.join(results, TakeFrom.HEAD);
            assertTrue(result.isOk());
            assertEquals(List.of("a", "b"), result.value());
        }

        @Test
        void ok_if_empty_collection() {
            Result<List<String>, Exception> result = Result.join(List.of(), TakeFrom.HEAD);
            assertTrue(result.isOk());
            assertEquals(List.of(), result.value());
        }

        @Test
        void throws_if_no_arg() {
            assertThrows(IllegalArgumentException.class, () -> Result.join(null, TakeFrom.HEAD));
            assertThrows(IllegalArgumentException.class, () -> Result.join(List.of(), null));
        }
    }

    @Nested
    class Elevate {
        @Test
        void opt_present_if_ok_w_opt_present() {
            Result<Optional<String>, Exception> result = new Ok<>(Optional.of("value"));
            Optional<Result<String, Exception>> elevated = Result.elevate(result);
            assertTrue(elevated.isPresent());
            assertTrue(elevated.get().isOk());
            assertEquals("value", elevated.get().value());
        }

        @Test
        void opt_empty_if_ok_w_empty_opt() {
            Result<Optional<String>, Exception> result = new Ok<>(Optional.empty());
            Optional<Result<String, Exception>> elevated = Result.elevate(result);
            assertTrue(elevated.isEmpty());
        }

        @Test
        void opt_present_if_err() {
            Exception ex = new RuntimeException("error");
            Result<Optional<String>, Exception> result = new Err<>(ex);
            Optional<Result<String, Exception>> elevated = Result.elevate(result);
            assertTrue(elevated.isPresent());
            assertTrue(elevated.get().isErr());
            assertSame(ex, elevated.get().err());
        }

        @Test
        void throws_if_no_arg() {
            assertThrows(IllegalArgumentException.class, () -> Result.elevate(null));
        }
    }

    @Nested
    class StateChecking {
        @Test
        void check_ok() {
            assertTrue(new Ok<>("value").isOk());
            assertFalse(new Err<>(new RuntimeException()).isOk());
        }

        @Test
        void check_ok_w_predicate() {
            Result<Integer, Exception> result = new Ok<>(5);
            assertTrue(result.isOkAnd(v -> v > 0));
            assertFalse(result.isOkAnd(v -> v < 0));
        }

        @Test
        void throws_if_no_arg() {
            Result<Integer, Exception> result = new Ok<>(5);
            assertThrows(IllegalArgumentException.class, () -> result.isOkAnd(null));
            assertThrows(IllegalArgumentException.class, () -> result.isErrAnd((Predicate<Exception>) null));
            assertThrows(IllegalArgumentException.class, () -> result.isErrAnd((Class<? extends Exception>) null));
        }

        @Test
        void check_err() {
            assertTrue(new Err<>(new RuntimeException()).isErr());
            assertFalse(new Ok<>("value").isErr());
        }

        @Test
        void check_err_w_type() {
            Result<String, Exception> result = new Err<>(new IllegalArgumentException());
            assertTrue(result.isErrAnd(IllegalArgumentException.class));
            assertTrue(result.isErrAnd(RuntimeException.class));
            assertFalse(result.isErrAnd(IOException.class));
        }

        @Test
        void check_err_against_ok() {
            Result<String, Exception> result = new Ok<>("value");
            assertFalse(result.isErrAnd(Exception.class));
            assertFalse(result.isErrAnd(e -> e.getMessage().equals("1")));
        }

        @Test
        void check_ok_against_err() {
            Result<String, Exception> result = new Err<>(new RuntimeException());
            assertFalse(result.isOkAnd(s -> s.equals("a")));
        }

        @Test
        void check_err_w_predicate() {
            Result<String, Exception> result = new Err<>(new RuntimeException("error"));
            assertTrue(result.isErrAnd(e -> e.getMessage().equals("error")));
            assertFalse(result.isErrAnd(e -> e.getMessage().equals("other")));
        }
    }

    @Nested
    class Map {
        @Test
        void map_if_ok() {
            Result<Integer, Exception> result = new Ok<>(5);
            Result<String, Exception> mapped = result.map(v -> "value: " + v);
            assertTrue(mapped.isOk());
            assertEquals("value: 5", mapped.value());
        }

        @Test
        void skip_if_err() {
            Exception ex = new RuntimeException("error");
            Result<Integer, Exception> result = new Err<>(ex);
            Result<String, Exception> mapped = result.map(v -> "value: " + v);
            assertTrue(mapped.isErr());
            assertSame(ex, mapped.err());
        }

        @Test
        void throws_if_no_arg() {
            Result<Integer, Exception> result = new Ok<>(5);
            assertThrows(IllegalArgumentException.class, () -> result.map(null));
        }

        @Test
        void throws_if_remaps_to_null() {
            Result<Integer, Exception> result = new Ok<>(5);
            assertThrows(IllegalArgumentException.class, () -> result.map(v -> null));
        }
    }

    @Nested
    class Swap {
        @Test
        void swap_if_ok() {
            Result<Integer, Exception> result = new Ok<>(5);
            Result<String, Exception> swapped = result.swap("new");
            assertTrue(swapped.isOk());
            assertEquals("new", swapped.value());
        }

        @Test
        void skip_if_err() {
            Exception ex = new RuntimeException("error");
            Result<Integer, Exception> result = new Err<>(ex);
            Result<String, Exception> swapped = result.swap("new");
            assertTrue(swapped.isErr());
            assertSame(ex, swapped.err());
        }

        @Test
        void throws_if_no_arg() {
            Result<Integer, Exception> result = new Ok<>(5);
            assertThrows(IllegalArgumentException.class, () -> result.swap((String) null));
            assertThrows(IllegalArgumentException.class, () -> result.swap((Supplier<?>) null));
        }

        @Test
        void swap_if_ok_w_fn() {
            Result<Integer, Exception> result = new Ok<>(5);
            Result<String, Exception> swapped = result.swap(() -> "supplied");
            assertTrue(swapped.isOk());
            assertEquals("supplied", swapped.value());
        }

        @Test
        void throws_if_remap_to_null() {
            Result<Integer, Exception> result = new Ok<>(5);
            assertThrows(IllegalArgumentException.class, () -> result.swap(() -> null));
        }
    }

    @Nested
    class Stack {
        @Test
        void skip_if_ok() {
            Result<String, RuntimeException> result = new Ok<>("value");
            IllegalArgumentException repl = new IllegalArgumentException("repl");
            Result<String, IllegalArgumentException> stacked = result.stack(repl);
            assertTrue(stacked.isOk());
            assertEquals("value", stacked.value());
        }

        @Test
        void replace_if_err() {
            Result<String, RuntimeException> result = new Err<>(new RuntimeException("error"));
            IllegalArgumentException repl = new IllegalArgumentException("repl");
            Result<String, IllegalArgumentException> stacked = result.stack(repl);
            assertTrue(stacked.isErr());
            assertInstanceOf(IllegalArgumentException.class, stacked.err());
            assertEquals("repl", stacked.err().getMessage());
        }

        @Test
        void throws_if_no_arg() {
            Result<String, RuntimeException> result = new Err<>(new RuntimeException());
            assertThrows(IllegalArgumentException.class, () -> result.stack((RuntimeException) null));
            assertThrows(IllegalArgumentException.class, () -> result.stack((Function<RuntimeException, IllegalArgumentException>) null));
            assertThrows(IllegalArgumentException.class, () -> result.stack((Supplier<IllegalArgumentException>) null));
        }

        @Test
        void replace_if_err_w_fn() {
            Result<String, RuntimeException> result = new Err<>(new RuntimeException("error"));
            Result<String, IllegalArgumentException> stacked = result.stack((Function<RuntimeException, IllegalArgumentException>) e -> new IllegalArgumentException(e.getMessage()));
            assertTrue(stacked.isErr());
            assertInstanceOf(IllegalArgumentException.class, stacked.err());
            assertEquals("error", stacked.err().getMessage());
        }

        @Test
        void skip_if_ok_w_fn() {
            Result<String, RuntimeException> result = new Ok<>("value");
            Result<String, IllegalArgumentException> stacked = result.stack((Function<RuntimeException, IllegalArgumentException>) e -> new IllegalArgumentException(e.getMessage()));
            assertTrue(stacked.isOk());
            assertEquals("value", stacked.value());
        }

        @Test
        void replace_if_err_w_supplier() {
            Result<String, RuntimeException> result = new Err<>(new RuntimeException("error"));
            Result<String, IllegalArgumentException> stacked = result.stack((Supplier<IllegalArgumentException>) () -> new IllegalArgumentException("supplied"));
            assertTrue(stacked.isErr());
            assertEquals("supplied", stacked.err().getMessage());
        }

        @Test
        void skip_if_err_w_supplier() {
            Result<String, RuntimeException> result = new Ok<>("value");
            Result<String, IllegalArgumentException> stacked = result.stack((Supplier<IllegalArgumentException>) () -> new IllegalArgumentException("supplied"));
            assertTrue(stacked.isOk());
            assertEquals("value", stacked.value());
        }

        @Test
        void throws_if_remap_to_null() {
            Result<String, RuntimeException> result = new Err<>(new RuntimeException());
            assertThrows(IllegalArgumentException.class, () -> result.stack((Supplier<? extends Exception>) () -> null));
            assertThrows(IllegalArgumentException.class, () -> result.stack((Function<RuntimeException, ? extends Exception>) e -> null));
        }
    }

    @Nested
    class Upcast {
        @Test
        void upcast_ok() {
            Result<String, RuntimeException> result = new Ok<>("value");
            Result<String, Exception> upcast = result.upcast();
            assertTrue(upcast.isOk());
            assertEquals("value", upcast.value());
        }

        @Test
        void upcast_err() {
            RuntimeException ex = new RuntimeException("error");
            Result<String, RuntimeException> result = new Err<>(ex);
            Result<String, Exception> upcast = result.upcast();
            assertTrue(upcast.isErr());
            assertSame(ex, upcast.err());
        }
    }

    @Nested
    class Then {
        @Test
        void remap_if_ok_to_ok() {
            Result<Integer, Exception> result = new Ok<>(5);
            Result<String, Exception> flatMapped = result.then(v -> new Ok<>("value: " + v));
            assertTrue(flatMapped.isOk());
            assertEquals("value: 5", flatMapped.value());
        }

        @Test
        void remap_if_ok_to_err() {
            Result<Integer, Exception> result = new Ok<>(5);
            Result<String, Exception> flatMapped = result.then(v -> new Err<>(new RuntimeException("error")));
            assertTrue(flatMapped.isErr());
        }

        @Test
        void skip_if_err_to_ok() {
            Exception ex = new RuntimeException("error");
            Result<Integer, Exception> result = new Err<>(ex);
            Result<String, Exception> flatMapped = result.then(v -> new Ok<>("value"));
            assertTrue(flatMapped.isErr());
            assertSame(ex, flatMapped.err());
        }

        @Test
        void throws_if_no_arg() {
            Result<Integer, Exception> result = new Ok<>(5);
            assertThrows(IllegalArgumentException.class, () -> result.then(null));
            assertThrows(IllegalArgumentException.class, () -> result.then(any -> null));
        }
    }

    @Nested
    class Fusing {
        @Test
        void ok_if_ok_to_ok() {
            Result<String, Exception> result1 = new Ok<>("a");
            Result<Integer, Exception> result2 = new Ok<>(5);
            Result<Fuse<String, Integer>, Exception> fused = result1.fuse(result2);
            assertTrue(fused.isOk());
            Fuse<String, Integer> fuse = fused.value();
            assertEquals("a", fuse.left());
            assertEquals(5, fuse.right());
        }

        @Test
        void err_if_ok_to_err_w_rule() {
            Exception ex = new RuntimeException("left");
            Result<String, Exception> result1 = new Err<>(ex);
            Result<Integer, Exception> result2 = new Ok<>(5);
            Result<Fuse<String, Integer>, Exception> fused = result1.fuse(result2, TakeFrom.HEAD);
            assertTrue(fused.isErr());
            assertSame(ex, fused.err());
        }

        @Test
        void err_if_err_to_err() {
            Exception ex1 = new RuntimeException("left");
            Exception ex2 = new RuntimeException("right");
            Result<String, Exception> result1 = new Err<>(ex1);
            Result<Integer, Exception> result2 = new Err<>(ex2);
            Result<Fuse<String, Integer>, Exception> fused = result1.fuse(result2, TakeFrom.HEAD);
            assertTrue(fused.isErr());
            assertSame(ex1, fused.err());
        }

        @Test
        void ok_if_ok_to_ok_w_rule() {
            Result<String, Exception> result1 = new Ok<>("a");
            Result<Integer, Exception> result2 = new Ok<>(5);
            Result<Fuse<String, Integer>, Exception> fused = result1.fuse(result2, TakeFrom.TAIL);
            assertTrue(fused.isOk());
            Fuse<String, Integer> fuse = fused.value();
            assertEquals("a", fuse.left());
            assertEquals(5, fuse.right());
        }

        @Test
        void throws_if_no_arg() {
            Result<String, Exception> result = new Ok<>("a");
            assertThrows(IllegalArgumentException.class, () -> result.fuse(null));
            assertThrows(IllegalArgumentException.class, () -> result.fuse((Result<Integer, Exception>) null, TakeFrom.HEAD));
            assertThrows(IllegalArgumentException.class, () -> result.fuse(new Ok<>(""), null));
        }
    }

    @Nested
    class Peek {
        @Test
        void peek_if_ok() {
            Result<String, Exception> result = new Ok<>("value");
            List<String> peeked = new ArrayList<>();
            Result<String, Exception> returned = result.peek(peeked::add);
            assertSame(result, returned);
            assertEquals(List.of("value"), peeked);
        }

        @Test
        void skip_if_err() {
            Result<String, Exception> result = new Err<>(new RuntimeException("error"));
            List<String> peeked = new ArrayList<>();
            Result<String, Exception> returned = result.peek(peeked::add);
            assertSame(result, returned);
            assertTrue(peeked.isEmpty());
        }

        @Test
        void throws_if_no_arg() {
            Result<String, Exception> result = new Ok<>("value");
            assertThrows(IllegalArgumentException.class, () -> result.peek(null));
            assertThrows(IllegalArgumentException.class, () -> result.peek(null, v -> {}));
            assertThrows(IllegalArgumentException.class, () -> result.peek(v -> true, null));
        }

        @Test
        void peek_if_ok_w_predicate() {
            Result<Integer, Exception> result = new Ok<>(5);
            List<Integer> peeked = new ArrayList<>();
            result.peek(v -> v > 0, peeked::add);
            assertEquals(List.of(5), peeked);
        }

        @Test
        void skip_if_ok_w_predicate_not_matched() {
            Result<Integer, Exception> result = new Ok<>(5);
            List<Integer> peeked = new ArrayList<>();
            result.peek(v -> v < 0, peeked::add);
            assertTrue(peeked.isEmpty());
        }

        @Test
        void skip_if_err_w_predicate() {
            Result<Integer, Exception> result = new Err<>(new RuntimeException("error"));
            List<Integer> peeked = new ArrayList<>();
            Result<Integer, Exception> returned = result.peek(v -> v > 0, peeked::add);
            assertSame(result, returned);
            assertTrue(peeked.isEmpty());
        }
    }

    @Nested
    class Inspect {
        @Test
        void inspect_if_err() {
            Result<String, Exception> result = new Err<>(new RuntimeException("error"));
            List<Exception> inspected = new ArrayList<>();
            Result<String, Exception> returned = result.inspect(inspected::add);
            assertSame(result, returned);
            assertEquals(1, inspected.size());
            assertEquals("error", inspected.getFirst().getMessage());
        }

        @Test
        void skip_if_ok() {
            Result<String, Exception> result = new Ok<>("value");
            List<Exception> inspected = new ArrayList<>();
            Result<String, Exception> returned = result.inspect(inspected::add);
            assertSame(result, returned);
            assertTrue(inspected.isEmpty());
        }

        @Test
        void throws_if_no_args() {
            Result<String, Exception> result = new Err<>(new RuntimeException());
            assertThrows(IllegalArgumentException.class, () -> result.inspect(null));
            assertThrows(IllegalArgumentException.class, () -> result.inspect((Predicate<Exception>) null, e -> {}));
            assertThrows(IllegalArgumentException.class, () -> result.inspect(e -> true, null));
            assertThrows(IllegalArgumentException.class, () -> result.inspect((Class<? extends Exception>) null, e -> {}));
            assertThrows(IllegalArgumentException.class, () -> result.inspect(Exception.class, null));
        }

        @Test
        void inspect_if_err_w_predicate() {
            Result<String, Exception> result = new Err<>(new RuntimeException("error"));
            List<Exception> inspected = new ArrayList<>();
            result.inspect(e -> e.getMessage().equals("error"), inspected::add);
            assertEquals(1, inspected.size());
        }

        @Test
        void skip_if_err_w_predicate_not_matched() {
            Result<String, Exception> result = new Err<>(new RuntimeException("error"));
            List<Exception> inspected = new ArrayList<>();
            result.inspect(e -> e.getMessage().equals("other"), inspected::add);
            assertTrue(inspected.isEmpty());
        }

        @Test
        void skip_if_ok_w_predicate() {
            Result<String, Exception> result = new Ok<>("value");
            List<Exception> inspected = new ArrayList<>();
            Result<String, Exception> returned = result.inspect(e -> e.getMessage().equals("error"), inspected::add);
            assertSame(result, returned);
            assertTrue(inspected.isEmpty());
        }

        @Test
        void inspect_if_err_w_type() {
            Result<String, Exception> result = new Err<>(new IllegalArgumentException("error"));
            List<Exception> inspected = new ArrayList<>();
            result.inspect(IllegalArgumentException.class, inspected::add);
            assertEquals(1, inspected.size());
        }

        @Test
        void skip_if_err_w_type_not_matched() {
            Result<String, Exception> result = new Err<>(new RuntimeException("error"));
            List<Exception> inspected = new ArrayList<>();
            result.inspect(IllegalArgumentException.class, inspected::add);
            assertTrue(inspected.isEmpty());
        }

        @Test
        void inspect_if_err_w_type_parent_class() {
            Result<String, Exception> result = new Err<>(new IllegalArgumentException("error"));
            List<Exception> inspected = new ArrayList<>();
            result.inspect(Exception.class, inspected::add);
            assertEquals(1, inspected.size());
        }

        @Test
        void skip_if_ok_w_type() {
            Result<String, Exception> result = new Ok<>("value");
            List<Exception> inspected = new ArrayList<>();
            Result<String, Exception> returned = result.inspect(Exception.class, inspected::add);
            assertSame(result, returned);
            assertTrue(inspected.isEmpty());
        }

    }

    @Nested
    class Taint {
        @Test
        void taint_if_ok() {
            Result<String, Exception> result = new Ok<>("value");
            Exception ex = new RuntimeException("taint");
            Result<String, Exception> tainted = result.taint(ex);
            assertTrue(tainted.isErr());
            assertSame(ex, tainted.err());
        }

        @Test
        void skip_if_err() {
            Exception original = new RuntimeException("original");
            Result<String, Exception> result = new Err<>(original);
            Exception taint = new RuntimeException("taint");
            Result<String, Exception> tainted = result.taint(taint);
            assertTrue(tainted.isErr());
            assertSame(original, tainted.err());
        }

        @Test
        void throws_if_no_arg() {
            Result<String, Exception> result = new Ok<>("value");
            assertThrows(IllegalArgumentException.class, () -> result.taint(null));
            assertThrows(IllegalArgumentException.class, () -> result.taint(null, new RuntimeException()));
            assertThrows(IllegalArgumentException.class, () -> result.taint(v -> true, null));
        }

        @Test
        void taint_if_ok_w_predicate() {
            Result<Integer, Exception> result = new Ok<>(5);
            Exception ex = new RuntimeException("taint");
            Result<Integer, Exception> tainted = result.taint(v -> v > 0, ex);
            assertTrue(tainted.isErr());
            assertSame(ex, tainted.err());
        }

        @Test
        void skip_if_ok_w_predicate_not_matched() {
            Result<Integer, Exception> result = new Ok<>(5);
            Exception ex = new RuntimeException("taint");
            Result<Integer, Exception> tainted = result.taint(v -> v < 0, ex);
            assertTrue(tainted.isOk());
            assertEquals(5, tainted.value());
        }

        @Test
        void skip_if_err_w_predicate() {
            Exception original = new RuntimeException("original");
            Result<String, Exception> result = new Err<>(original);
            Exception taint = new RuntimeException("taint");
            Result<String, Exception> tainted = result.taint(s -> s.equals("1"), taint);
            assertTrue(tainted.isErr());
            assertSame(original, tainted.err());
        }
    }

    @Nested
    class Recover {
        @Test
        void recover_if_err() {
            Result<String, Exception> result = new Err<>(new RuntimeException("error"));
            Result<String, Exception> recovered = result.recover("default");
            assertTrue(recovered.isOk());
            assertEquals("default", recovered.value());
        }

        @Test
        void recover_if_err_w_supplier() {
            Result<String, Exception> result = new Err<>(new RuntimeException("error"));
            Result<String, Exception> recovered = result.recover(() -> "supplied");
            assertTrue(recovered.isOk());
            assertEquals("supplied", recovered.value());
        }

        @Test
        void recover_if_err_w_function() {
            Result<String, Exception> result = new Err<>(new RuntimeException("error"));
            Result<String, Exception> recovered = result.recover(e -> "recovered: " + e.getMessage());
            assertTrue(recovered.isOk());
            assertEquals("recovered: error", recovered.value());
        }

        @Test
        void skip_if_ok() {
            Result<String, Exception> result = new Ok<>("value");
            Result<String, Exception> recovered = result.recover("default");
            assertTrue(recovered.isOk());
            assertEquals("value", recovered.value());
        }

        @Test
        void skip_if_ok_w_supplier() {
            Result<String, Exception> result = new Ok<>("value");
            Result<String, Exception> recovered = result.recover(() -> "default");
            assertTrue(recovered.isOk());
            assertEquals("value", recovered.value());
        }

        @Test
        void skip_if_ok_w_fn() {
            Result<String, Exception> result = new Ok<>("value");
            Result<String, Exception> recovered = result.recover(an -> "default");
            assertTrue(recovered.isOk());
            assertEquals("value", recovered.value());
        }

        // by predicate

        @Test
        void recover_if_err_w_predicate() {
            Result<String, Exception> result = new Err<>(new RuntimeException("error"));
            Result<String, Exception> recovered = result.recover(e -> true, "default");
            assertTrue(recovered.isOk());
            assertEquals("default", recovered.value());
        }

        @Test
        void skip_if_err_w_predicate_not_matched() {
            Result<String, Exception> result = new Err<>(new RuntimeException("error"));
            Result<String, Exception> recovered = result.recover(e -> false, "default");
            assertTrue(recovered.isErr());
        }

        @Test
        void recover_if_err_w_predicate_w_supplier() {
            Result<String, Exception> result = new Err<>(new RuntimeException("error"));
            Result<String, Exception> recovered = result.recover(e -> true, () -> "supplied");
            assertTrue(recovered.isOk());
            assertEquals("supplied", recovered.value());
        }

        @Test
        void skip_if_err_w_predicate_not_matched_w_supplier() {
            Result<String, Exception> result = new Err<>(new RuntimeException("error"));
            Result<String, Exception> recovered = result.recover(e -> false, () -> "supplied");
            assertTrue(recovered.isErr());
            assertEquals("error", recovered.err().getMessage());
        }

        @Test
        void recover_if_err_w_predicate_w_fn() {
            Result<String, Exception> result = new Err<>(new RuntimeException("error"));
            Result<String, Exception> recovered = result.recover(e -> true, e -> "recovered: " + e.getMessage());
            assertTrue(recovered.isOk());
            assertEquals("recovered: error", recovered.value());
        }

        @Test
        void skip_if_err_w_predicate_not_matched_w_fn() {
            Result<String, Exception> result = new Err<>(new RuntimeException("error"));
            Result<String, Exception> recovered = result.recover(e -> false, e -> "recovered: " + e.getMessage());
            assertTrue(recovered.isErr());
            assertEquals("error", recovered.err().getMessage());
        }

        @Test
        void skip_if_ok_w_predicate() {
            Result<String, Exception> result = new Ok<>("value");
            Result<String, Exception> recovered = result.recover(e -> true, "default");
            assertTrue(recovered.isOk());
            assertEquals("value", recovered.value());
        }

        @Test
        void skip_if_ok_w_predicate_w_supplier() {
            Result<String, Exception> result = new Ok<>("value");
            Result<String, Exception> recovered = result.recover(e -> true, () -> "supplied");
            assertTrue(recovered.isOk());
            assertEquals("value", recovered.value());
        }

        @Test
        void skip_if_ok_w_predicate_w_fn() {
            Result<String, Exception> result = new Ok<>("value");
            Result<String, Exception> recovered = result.recover(e -> true, e -> "recovered: " + e.getMessage());
            assertTrue(recovered.isOk());
            assertEquals("value", recovered.value());
        }

        // by type

        @Test
        void recover_if_err_w_type() {
            Result<String, Exception> result = new Err<>(new IllegalArgumentException("error"));
            Result<String, Exception> recovered = result.recover(IllegalArgumentException.class, "default");
            assertTrue(recovered.isOk());
            assertEquals("default", recovered.value());
        }

        @Test
        void skip_if_err_w_type_not_matched() {
            Result<String, Exception> result = new Err<>(new RuntimeException("error"));
            Result<String, Exception> recovered = result.recover(IllegalArgumentException.class, "default");
            assertTrue(recovered.isErr());
        }

        @Test
        void recover_if_err_w_type_w_supplier() {
            Result<String, Exception> result = new Err<>(new IllegalArgumentException("error"));
            Result<String, Exception> recovered = result.recover(IllegalArgumentException.class, () -> "supplied");
            assertTrue(recovered.isOk());
            assertEquals("supplied", recovered.value());
        }

        @Test
        void skip_if_err_w_type_not_matched_w_supplier() {
            Result<String, Exception> result = new Err<>(new RuntimeException("error"));
            Result<String, Exception> recovered = result.recover(IllegalArgumentException.class, () -> "supplied");
            assertTrue(recovered.isErr());
            assertEquals("error", recovered.err().getMessage());
        }

        @Test
        void recover_if_err_w_type_w_fn() {
            Result<String, Exception> result = new Err<>(new IllegalArgumentException("error"));
            Result<String, Exception> recovered = result.recover(IllegalArgumentException.class, e -> "recovered: " + e.getMessage());
            assertTrue(recovered.isOk());
            assertEquals("recovered: error", recovered.value());
        }

        @Test
        void skip_if_err_w_type_not_matched_w_function() {
            Result<String, Exception> result = new Err<>(new RuntimeException("error"));
            Result<String, Exception> recovered = result.recover(IllegalArgumentException.class, e -> "recovered: " + e.getMessage());
            assertTrue(recovered.isErr());
            assertEquals("error", recovered.err().getMessage());
        }

        @Test
        void skip_if_ok_w_type() {
            Result<String, Exception> result = new Ok<>("value");
            Result<String, Exception> recovered = result.recover(IllegalArgumentException.class, "supplied");
            assertTrue(recovered.isOk());
            assertEquals("value", recovered.value());
        }

        @Test
        void skip_if_ok_w_type_and_supplier() {
            Result<String, Exception> result = new Ok<>("value");
            Result<String, Exception> recovered = result.recover(IllegalArgumentException.class, () -> "supplied");
            assertTrue(recovered.isOk());
            assertEquals("value", recovered.value());
        }

        @Test
        void recover_if_ok_w_type_w_fn() {
            Result<String, Exception> result = new Ok<>("value");
            Result<String, Exception> recovered = result.recover(IllegalArgumentException.class, e -> "recovered: " + e.getMessage());
            assertTrue(recovered.isOk());
            assertEquals("value", recovered.value());
        }

        @Test
        void throws_if_no_arg() {
            Result<String, Exception> result = new Err<>(new RuntimeException());
            assertThrows(IllegalArgumentException.class, () -> result.recover((String) null));
            assertThrows(IllegalArgumentException.class, () -> result.recover((Supplier<String>) null));
            assertThrows(IllegalArgumentException.class, () -> result.recover((Function<Exception, String>) null));

            assertThrows(IllegalArgumentException.class, () -> result.recover((Predicate<Exception>) null, (String) null));
            assertThrows(IllegalArgumentException.class, () -> result.recover(e -> true, (String) null));
            assertThrows(IllegalArgumentException.class, () -> result.recover((Predicate<Exception>) null, (Supplier<String>) null));
            assertThrows(IllegalArgumentException.class, () -> result.recover(e -> true, (Supplier<String>) null));
            assertThrows(IllegalArgumentException.class, () -> result.recover((Predicate<Exception>) null, (Function<Exception, String>) null));
            assertThrows(IllegalArgumentException.class, () -> result.recover(e -> true, (Function<Exception, String>) null));

            assertThrows(IllegalArgumentException.class, () -> result.recover((Class<? extends Exception>) null, (String) null));
            assertThrows(IllegalArgumentException.class, () -> result.recover(Exception.class, (String) null));
            assertThrows(IllegalArgumentException.class, () -> result.recover((Class<? extends Exception>) null, (Supplier<String>) null));
            assertThrows(IllegalArgumentException.class, () -> result.recover(Exception.class, (Supplier<String>) null));
            assertThrows(IllegalArgumentException.class, () -> result.recover((Class<? extends Exception>) null, (Function<Exception, String>) null));
            assertThrows(IllegalArgumentException.class, () -> result.recover(Exception.class, (Function<Exception, String>) null));
        }
    }

    @Nested
    class Extraction {
        @Test
        void get_value_if_ok() {
            Result<String, Exception> result = new Ok<>("value");
            assertEquals("value", result.value());
        }

        @Test
        void throws_if_err() {
            Result<String, Exception> result = new Err<>(new RuntimeException("error"));
            assertThrows(IllegalStateException.class, result::value);
        }

        @Test
        void get_err_if_err() {
            Exception ex = new RuntimeException("error");
            Result<String, Exception> result = new Err<>(ex);
            assertSame(ex, result.err());
        }

        @Test
        void throws_if_ok() {
            Result<String, Exception> result = new Ok<>("value");
            assertThrows(IllegalStateException.class, result::err);
        }
    }

    @Nested
    class Conditionals {
        @Test
        void call_on_ok_if_ok() {
            Result<String, Exception> result = new Ok<>("value");
            List<String> values = new ArrayList<>();
            result.ifOk(values::add);
            assertEquals(List.of("value"), values);
        }

        @Test
        void skip_on_ok_if_err() {
            Result<String, Exception> result = new Err<>(new RuntimeException("error"));
            List<String> values = new ArrayList<>();
            result.ifOk(values::add);
            assertTrue(values.isEmpty());
        }

        @Test
        void throws_if_no_arg() {
            Result<String, Exception> result = new Ok<>("value");
            assertThrows(IllegalArgumentException.class, () -> result.ifOk(null));
            assertThrows(IllegalArgumentException.class, () -> result.ifErr(null));
        }

        @Test
        void call_on_err_if_err() {
            Result<String, Exception> result = new Err<>(new RuntimeException("error"));
            List<Exception> errors = new ArrayList<>();
            result.ifErr(errors::add);
            assertEquals(1, errors.size());
        }

        @Test
        void skip_on_err_if_ok() {
            Result<String, Exception> result = new Ok<>("value");
            List<Exception> errors = new ArrayList<>();
            result.ifErr(errors::add);
            assertTrue(errors.isEmpty());
        }

    }

    @Nested
    class Unwrap {
        @Test
        void unwrap_if_ok() {
            Result<String, Exception> result = new Ok<>("value");
            assertEquals("value", result.unwrap());
        }

        @Test
        void throws_if_err() {
            Result<String, Exception> result = new Err<>(new RuntimeException("error"));
            var failure = assertThrows(io.github.artkonr.result.Wrap.Failure.class, result::unwrap);
            assertInstanceOf(RuntimeException.class, failure.getCause());
        }

        @Test
        void unwrap_or_if_ok() {
            Result<String, Exception> result = new Ok<>("value");
            assertEquals("value", result.unwrapOr("default"));
        }

        @Test
        void unwrap_or_if_err() {
            Result<String, Exception> result = new Err<>(new RuntimeException("error"));
            assertEquals("default", result.unwrapOr("default"));
        }

        @Test
        void throws_if_no_arg() {
            Result<String, Exception> result = new Err<>(new RuntimeException());
            assertThrows(IllegalArgumentException.class, () -> result.unwrapOr((String) null));
            assertThrows(IllegalArgumentException.class, () -> result.unwrapOr((Supplier<String>) null));
        }

        @Test
        void unwrap_or_w_supplier_if_err() {
            Result<String, Exception> result = new Err<>(new RuntimeException("error"));
            assertEquals("supplied", result.unwrapOr(() -> "supplied"));
        }

        @Test
        void unwrap_or_w_supplier_if_ok() {
            Result<String, Exception> result = new Ok<>("value");
            assertEquals("value", result.unwrapOr(() -> "supplied"));
        }

        @Test
        void unwrap_checked_if_ok() throws Exception {
            Result<String, Exception> result = new Ok<>("value");
            assertEquals("value", result.unwrapChecked());
        }

        @Test
        void throws_unwrap_checked_if_err() {
            Result<String, Exception> result = new Err<>(new RuntimeException("error"));
            assertThrows(RuntimeException.class, result::unwrapChecked);
        }
    }

    @Nested
    class Drop {
        @Test
        void drop_if_ok() {
            Result<String, IOException> ok = new Ok<>("data");
            Done<IOException> done = ok.drop();
            assertTrue(done.isSuccess());
            assertFalse(done.isFailure());
        }

        @Test
        void drop_if_err() {
            IOException exception = new IOException("error");
            Result<String, IOException> err = new Err<>(exception);
            Done<IOException> done = err.drop();
            assertTrue(done.isFailure());
            assertFalse(done.isSuccess());
            assertSame(exception, done.failure());
        }
    }
}
