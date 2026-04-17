package io.github.artkonr.result;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    @Test
    void should_wrap_successful_computation() {
        Result<String, Exception> result = Result.wrap(() -> "success");
        assertTrue(result.isOk());
        assertEquals("success", result.value());
    }

    @Test
    void should_wrap_and_catch_exception() {
        Result<String, Exception> result = Result.wrap(() -> {
            throw new RuntimeException("error");
        });
        assertTrue(result.isErr());
        assertInstanceOf(RuntimeException.class, result.err());
        assertEquals("error", result.err().getMessage());
    }

    @Test
    void should_throw_illegal_argument_when_wrapping_null_supplier() {
        assertThrows(IllegalArgumentException.class, () -> Result.wrap((Wrap.Supplier<?>) null));
    }

    @Test
    void should_throw_illegal_argument_when_wrapping_returns_null() {
        assertThrows(IllegalArgumentException.class, () -> Result.wrap(() -> null));
    }

    @Test
    void should_wrap_with_specific_error_type() {
        Result<String, IllegalArgumentException> result = Result.wrap(
            IllegalArgumentException.class,
            () -> "success"
        );
        assertTrue(result.isOk());
        assertEquals("success", result.value());
    }

    @Test
    void should_wrap_and_match_specific_error_type() {
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
    void should_throw_when_wrapped_exception_type_mismatches() {
        assertThrows(IllegalStateException.class, () -> Result.wrap(
            IllegalArgumentException.class,
            () -> {
                throw new RuntimeException("wrong type");
            }
        ));
    }

    @Test
    void should_throw_illegal_argument_when_error_type_is_null() {
        assertThrows(IllegalArgumentException.class, () -> Result.wrap(
            null,
            () -> "success"
        ));
    }

    @Test
    void should_throw_illegal_argument_when_wrap_supplier_is_null() {
        assertThrows(IllegalArgumentException.class, () -> Result.wrap(
            IllegalArgumentException.class,
            (Wrap.Supplier<?>) null
        ));
    }

    @Test
    void should_create_from_ok_result() {
        Result<String, Exception> ok = new Ok<>("value");
        Result<String, Exception> copy = Result.from(ok);
        assertTrue(copy.isOk());
        assertEquals("value", copy.value());
    }

    @Test
    void should_create_from_err_result() {
        Exception ex = new RuntimeException("error");
        Result<String, Exception> err = new Err<>(ex);
        Result<String, Exception> copy = Result.from(err);
        assertTrue(copy.isErr());
        assertSame(ex, copy.err());
    }

    @Test
    void should_throw_illegal_argument_when_from_null() {
        assertThrows(IllegalArgumentException.class, () -> Result.from(null));
    }

    @Test
    void should_chain_successful_results() {
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
    void should_short_circuit_on_first_error() {
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
    void should_chain_with_null_elements_ignored() {
        List<Supplier<Result<Integer, Exception>>> invocations = new ArrayList<>();
        invocations.add(() -> new Ok<>(1));
        invocations.add(null);
        invocations.add(() -> new Ok<>(2));
        Result<List<Integer>, Exception> result = Result.chain(invocations);
        assertTrue(result.isOk());
        assertEquals(List.of(1, 2), result.value());
    }

    @Test
    void should_chain_with_null_result_ignored() {
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
    void should_chain_empty_collection() {
        Result<List<Integer>, Exception> result = Result.chain(List.of());
        assertTrue(result.isOk());
        assertEquals(List.of(), result.value());
    }

    @Test
    void should_throw_illegal_argument_when_chain_null() {
        assertThrows(IllegalArgumentException.class, () -> Result.chain(null));
    }

    @Test
    void should_join_all_ok_results() {
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
    void should_join_and_take_head_error() {
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
    void should_join_and_take_tail_error() {
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
    void should_join_with_null_elements_removed() {
        List<Result<String, Exception>> results = new ArrayList<>();
        results.add(new Ok<>("a"));
        results.add(null);
        results.add(new Ok<>("b"));
        Result<List<String>, Exception> result = Result.join(results, TakeFrom.HEAD);
        assertTrue(result.isOk());
        assertEquals(List.of("a", "b"), result.value());
    }

    @Test
    void should_join_empty_collection() {
        Result<List<String>, Exception> result = Result.join(List.of(), TakeFrom.HEAD);
        assertTrue(result.isOk());
        assertEquals(List.of(), result.value());
    }

    @Test
    void should_throw_illegal_argument_when_join_null_results() {
        assertThrows(IllegalArgumentException.class, () -> Result.join(null, TakeFrom.HEAD));
    }

    @Test
    void should_throw_illegal_argument_when_join_null_rule() {
        assertThrows(IllegalArgumentException.class, () -> Result.join(List.of(), null));
    }

    @Test
    void should_elevate_ok_with_present_optional() {
        Result<Optional<String>, Exception> result = new Ok<>(Optional.of("value"));
        Optional<Result<String, Exception>> elevated = Result.elevate(result);
        assertTrue(elevated.isPresent());
        assertTrue(elevated.get().isOk());
        assertEquals("value", elevated.get().value());
    }

    @Test
    void should_elevate_ok_with_empty_optional() {
        Result<Optional<String>, Exception> result = new Ok<>(Optional.empty());
        Optional<Result<String, Exception>> elevated = Result.elevate(result);
        assertTrue(elevated.isEmpty());
    }

    @Test
    void should_elevate_err_result() {
        Exception ex = new RuntimeException("error");
        Result<Optional<String>, Exception> result = new Err<>(ex);
        Optional<Result<String, Exception>> elevated = Result.elevate(result);
        assertTrue(elevated.isPresent());
        assertTrue(elevated.get().isErr());
        assertSame(ex, elevated.get().err());
    }

    @Test
    void should_throw_illegal_argument_when_elevate_null() {
        assertThrows(IllegalArgumentException.class, () -> Result.elevate(null));
    }

    @Test
    void should_check_is_ok() {
        assertTrue(new Ok<>("value").isOk());
        assertFalse(new Err<>(new RuntimeException()).isOk());
    }

    @Test
    void should_check_is_ok_and_predicate_true() {
        Result<Integer, Exception> result = new Ok<>(5);
        assertTrue(result.isOkAnd(v -> v > 0));
    }

    @Test
    void should_check_is_ok_and_predicate_false() {
        Result<Integer, Exception> result = new Ok<>(5);
        assertFalse(result.isOkAnd(v -> v < 0));
    }

    @Test
    void should_check_is_ok_and_with_err() {
        Result<Integer, Exception> result = new Err<>(new RuntimeException());
        assertFalse(result.isOkAnd(v -> true));
    }

    @Test
    void should_throw_illegal_argument_when_is_ok_and_null_predicate() {
        Result<Integer, Exception> result = new Ok<>(5);
        assertThrows(IllegalArgumentException.class, () -> result.isOkAnd(null));
    }

    @Test
    void should_check_is_err() {
        assertTrue(new Err<>(new RuntimeException()).isErr());
        assertFalse(new Ok<>("value").isErr());
    }

    @Test
    void should_check_is_err_and_type_match() {
        Result<String, Exception> result = new Err<>(new IllegalArgumentException());
        assertTrue(result.isErrAnd(IllegalArgumentException.class));
    }

    @Test
    void should_check_is_err_and_type_mismatch() {
        Result<String, Exception> result = new Err<>(new RuntimeException());
        assertFalse(result.isErrAnd(IllegalArgumentException.class));
    }

    @Test
    void should_check_is_err_and_with_ok() {
        Result<String, Exception> result = new Ok<>("value");
        assertFalse(result.isErrAnd(Exception.class));
    }

    @Test
    void should_throw_illegal_argument_when_is_err_and_null_type() {
        Result<String, Exception> result = new Err<>(new RuntimeException());
        Class<? extends Exception> nullType = null;
        assertThrows(IllegalArgumentException.class, () -> result.isErrAnd(nullType));
    }

    @Test
    void should_check_is_err_and_predicate_true() {
        Result<String, Exception> result = new Err<>(new RuntimeException("error"));
        assertTrue(result.isErrAnd((java.util.function.Predicate<Exception>) e -> e.getMessage().equals("error")));
    }

    @Test
    void should_check_is_err_and_predicate_false() {
        Result<String, Exception> result = new Err<>(new RuntimeException("error"));
        assertFalse(result.isErrAnd((java.util.function.Predicate<Exception>) e -> e.getMessage().equals("other")));
    }

    @Test
    void should_throw_illegal_argument_when_is_err_and_null_predicate() {
        Result<String, Exception> result = new Err<>(new RuntimeException());
        assertThrows(IllegalArgumentException.class, () -> result.isErrAnd((java.util.function.Predicate<Exception>) null));
    }

    @Test
    void should_map_ok_value() {
        Result<Integer, Exception> result = new Ok<>(5);
        Result<String, Exception> mapped = result.map(v -> "value: " + v);
        assertTrue(mapped.isOk());
        assertEquals("value: 5", mapped.value());
    }

    @Test
    void should_map_err_unchanged() {
        Exception ex = new RuntimeException("error");
        Result<Integer, Exception> result = new Err<>(ex);
        Result<String, Exception> mapped = result.map(v -> "value: " + v);
        assertTrue(mapped.isErr());
        assertSame(ex, mapped.err());
    }

    @Test
    void should_throw_illegal_argument_when_map_null_function() {
        Result<Integer, Exception> result = new Ok<>(5);
        assertThrows(IllegalArgumentException.class, () -> result.map(null));
    }

    @Test
    void should_throw_illegal_argument_when_map_returns_null() {
        Result<Integer, Exception> result = new Ok<>(5);
        assertThrows(IllegalArgumentException.class, () -> result.map(v -> null));
    }

    @Test
    void should_swap_with_value() {
        Result<Integer, Exception> result = new Ok<>(5);
        Result<String, Exception> swapped = result.swap("new");
        assertTrue(swapped.isOk());
        assertEquals("new", swapped.value());
    }

    @Test
    void should_swap_err_with_value() {
        Exception ex = new RuntimeException("error");
        Result<Integer, Exception> result = new Err<>(ex);
        Result<String, Exception> swapped = result.swap("new");
        assertTrue(swapped.isErr());
        assertSame(ex, swapped.err());
    }

    @Test
    void should_throw_illegal_argument_when_swap_null_value() {
        Result<Integer, Exception> result = new Ok<>(5);
        assertThrows(IllegalArgumentException.class, () -> result.swap((String) null));
    }

    @Test
    void should_swap_with_supplier() {
        Result<Integer, Exception> result = new Ok<>(5);
        Result<String, Exception> swapped = result.swap(() -> "supplied");
        assertTrue(swapped.isOk());
        assertEquals("supplied", swapped.value());
    }

    @Test
    void should_throw_illegal_argument_when_swap_supplier_null() {
        Result<Integer, Exception> result = new Ok<>(5);
        assertThrows(IllegalArgumentException.class, () -> result.swap((Supplier<?>) null));
    }
    
    @Test
    void should_throw_illegal_argument_when_swap_supplier_returning_null() {
        Result<Integer, Exception> result = new Ok<>(5);
        assertThrows(IllegalArgumentException.class, () -> result.swap(() -> null));
    }

    @Test
    void should_stack_ok_with_replacement() {
        Result<String, RuntimeException> result = new Ok<>("value");
        IllegalArgumentException repl = new IllegalArgumentException("repl");
        Result<String, IllegalArgumentException> stacked = result.stack(repl);
        assertTrue(stacked.isOk());
        assertEquals("value", stacked.value());
    }

    @Test
    void should_stack_err_with_replacement() {
        Result<String, RuntimeException> result = new Err<>(new RuntimeException("error"));
        IllegalArgumentException repl = new IllegalArgumentException("repl");
        Result<String, IllegalArgumentException> stacked = result.stack(repl);
        assertTrue(stacked.isErr());
        assertInstanceOf(IllegalArgumentException.class, stacked.err());
        assertEquals("repl", stacked.err().getMessage());
    }

    @Test
    void should_throw_illegal_argument_when_stack_null_replacement() {
        Result<String, RuntimeException> result = new Ok<>("value");
        assertThrows(IllegalArgumentException.class, () -> result.stack((RuntimeException) null));
    }

    @Test
    void should_stack_err_with_function() {
        Result<String, RuntimeException> result = new Err<>(new RuntimeException("error"));
        Result<String, IllegalArgumentException> stacked = result.stack((java.util.function.Function<RuntimeException, IllegalArgumentException>) e -> new IllegalArgumentException(e.getMessage()));
        assertTrue(stacked.isErr());
        assertInstanceOf(IllegalArgumentException.class, stacked.err());
        assertEquals("error", stacked.err().getMessage());
    }

    @Test
    void should_throw_illegal_argument_when_stack_function_null() {
        Result<String, RuntimeException> result = new Ok<>("value");
        assertThrows(IllegalArgumentException.class, () -> result.stack((java.util.function.Function<RuntimeException, IllegalArgumentException>) null));
    }

    @Test
    void should_stack_ok_with_function() {
        Result<String, RuntimeException> result = new Ok<>("value");
        Result<String, IllegalArgumentException> stacked = result.stack((java.util.function.Function<RuntimeException, IllegalArgumentException>) e -> new IllegalArgumentException(e.getMessage()));
        assertTrue(stacked.isOk());
        assertEquals("value", stacked.value());
    }

    @Test
    void should_stack_err_with_supplier() {
        Result<String, RuntimeException> result = new Err<>(new RuntimeException("error"));
        Result<String, IllegalArgumentException> stacked = result.stack((Supplier<IllegalArgumentException>) () -> new IllegalArgumentException("supplied"));
        assertTrue(stacked.isErr());
        assertEquals("supplied", stacked.err().getMessage());
    }

    @Test
    void should_throw_illegal_argument_when_stack_supplier_null() {
        Result<String, RuntimeException> result = new Ok<>("value");
        assertThrows(IllegalArgumentException.class, () -> result.stack((Supplier<IllegalArgumentException>) null));
    }

    @Test
    void should_stack_ok_with_supplier() {
        Result<String, RuntimeException> result = new Ok<>("value");
        Result<String, IllegalArgumentException> stacked = result.stack((Supplier<IllegalArgumentException>) () -> new IllegalArgumentException("supplied"));
        assertTrue(stacked.isOk());
        assertEquals("value", stacked.value());
    }

    @Test
    void should_upcast_ok() {
        Result<String, RuntimeException> result = new Ok<>("value");
        Result<String, Exception> upcast = result.upcast();
        assertTrue(upcast.isOk());
        assertEquals("value", upcast.value());
    }

    @Test
    void should_upcast_err() {
        RuntimeException ex = new RuntimeException("error");
        Result<String, RuntimeException> result = new Err<>(ex);
        Result<String, Exception> upcast = result.upcast();
        assertTrue(upcast.isErr());
        assertSame(ex, upcast.err());
    }

    @Test
    void should_flat_map_ok() {
        Result<Integer, Exception> result = new Ok<>(5);
        Result<String, Exception> flatMapped = result.flatMap(v -> new Ok<>("value: " + v));
        assertTrue(flatMapped.isOk());
        assertEquals("value: 5", flatMapped.value());
    }

    @Test
    void should_flat_map_ok_to_err() {
        Result<Integer, Exception> result = new Ok<>(5);
        Result<String, Exception> flatMapped = result.flatMap(v -> new Err<>(new RuntimeException("error")));
        assertTrue(flatMapped.isErr());
    }

    @Test
    void should_flat_map_err() {
        Exception ex = new RuntimeException("error");
        Result<Integer, Exception> result = new Err<>(ex);
        Result<String, Exception> flatMapped = result.flatMap(v -> new Ok<>("value"));
        assertTrue(flatMapped.isErr());
        assertSame(ex, flatMapped.err());
    }

    @Test
    void should_throw_illegal_argument_when_flat_map_null() {
        Result<Integer, Exception> result = new Ok<>(5);
        assertThrows(IllegalArgumentException.class, () -> result.flatMap(null));
    }

    @Test
    void should_fuse_two_ok_results() {
        Result<String, Exception> result1 = new Ok<>("a");
        Result<Integer, Exception> result2 = new Ok<>(5);
        Result<Fuse<String, Integer>, Exception> fused = result1.fuse(result2);
        assertTrue(fused.isOk());
        Fuse<String, Integer> fuse = fused.value();
        assertEquals("a", fuse.left());
        assertEquals(5, fuse.right());
    }

    @Test
    void should_fuse_err_and_ok_with_head_rule() {
        Exception ex = new RuntimeException("left");
        Result<String, Exception> result1 = new Err<>(ex);
        Result<Integer, Exception> result2 = new Ok<>(5);
        Result<Fuse<String, Integer>, Exception> fused = result1.fuse(result2, TakeFrom.HEAD);
        assertTrue(fused.isErr());
        assertSame(ex, fused.err());
    }

    @Test
    void should_fuse_ok_and_err_with_tail_rule() {
        Exception ex = new RuntimeException("right");
        Result<String, Exception> result1 = new Ok<>("a");
        Result<Integer, Exception> result2 = new Err<>(ex);
        Result<Fuse<String, Integer>, Exception> fused = result1.fuse(result2, TakeFrom.TAIL);
        assertTrue(fused.isErr());
        assertSame(ex, fused.err());
    }

    @Test
    void should_fuse_err_and_err_with_head_rule() {
        Exception ex1 = new RuntimeException("left");
        Exception ex2 = new RuntimeException("right");
        Result<String, Exception> result1 = new Err<>(ex1);
        Result<Integer, Exception> result2 = new Err<>(ex2);
        Result<Fuse<String, Integer>, Exception> fused = result1.fuse(result2, TakeFrom.HEAD);
        assertTrue(fused.isErr());
        assertSame(ex1, fused.err());
    }

    @Test
    void should_fuse_err_and_err_with_tail_rule() {
        Exception ex1 = new RuntimeException("left");
        Exception ex2 = new RuntimeException("right");
        Result<String, Exception> result1 = new Err<>(ex1);
        Result<Integer, Exception> result2 = new Err<>(ex2);
        Result<Fuse<String, Integer>, Exception> fused = result1.fuse(result2, TakeFrom.TAIL);
        assertTrue(fused.isErr());
        assertSame(ex2, fused.err());
    }

    @Test
    void should_fuse_ok_and_ok_with_tail_rule() {
        Result<String, Exception> result1 = new Ok<>("a");
        Result<Integer, Exception> result2 = new Ok<>(5);
        Result<Fuse<String, Integer>, Exception> fused = result1.fuse(result2, TakeFrom.TAIL);
        assertTrue(fused.isOk());
        Fuse<String, Integer> fuse = fused.value();
        assertEquals("a", fuse.left());
        assertEquals(5, fuse.right());
    }

    @Test
    void should_fuse_err_and_ok_with_tail_rule() {
        Exception ex = new RuntimeException("left");
        Result<String, Exception> result1 = new Err<>(ex);
        Result<Integer, Exception> result2 = new Ok<>(5);
        Result<Fuse<String, Integer>, Exception> fused = result1.fuse(result2, TakeFrom.TAIL);
        assertTrue(fused.isErr());
        assertSame(ex, fused.err());
    }

    @Test
    void should_throw_illegal_argument_when_fuse_null_result() {
        Result<String, Exception> result = new Ok<>("a");
        assertThrows(IllegalArgumentException.class, () -> result.fuse(null));
    }

    @Test
    void should_peek_ok() {
        Result<String, Exception> result = new Ok<>("value");
        List<String> peeked = new ArrayList<>();
        Result<String, Exception> returned = result.peek(v -> peeked.add(v));
        assertSame(result, returned);
        assertEquals(List.of("value"), peeked);
    }

    @Test
    void should_peek_err_noop() {
        Result<String, Exception> result = new Err<>(new RuntimeException("error"));
        List<String> peeked = new ArrayList<>();
        Result<String, Exception> returned = result.peek(peeked::add);
        assertSame(result, returned);
        assertTrue(peeked.isEmpty());
    }

    @Test
    void should_throw_illegal_argument_when_peek_null_consumer() {
        Result<String, Exception> result = new Ok<>("value");
        assertThrows(IllegalArgumentException.class, () -> result.peek((java.util.function.Consumer<String>) null));
    }

    @Test
    void should_peek_with_predicate_matching() {
        Result<Integer, Exception> result = new Ok<>(5);
        List<Integer> peeked = new ArrayList<>();
        result.peek(v -> v > 0, peeked::add);
        assertEquals(List.of(5), peeked);
    }

    @Test
    void should_peek_with_predicate_not_matching() {
        Result<Integer, Exception> result = new Ok<>(5);
        List<Integer> peeked = new ArrayList<>();
        result.peek(v -> v < 0, peeked::add);
        assertTrue(peeked.isEmpty());
    }

    @Test
    void should_throw_illegal_argument_when_peek_predicate_null() {
        Result<Integer, Exception> result = new Ok<>(5);
        assertThrows(IllegalArgumentException.class, () -> result.peek(null, v -> {}));
    }

    @Test
    void should_throw_illegal_argument_when_peek_consumer_null() {
        Result<Integer, Exception> result = new Ok<>(5);
        assertThrows(IllegalArgumentException.class, () -> result.peek(v -> true, null));
    }

    @Test
    void should_inspect_err() {
        Result<String, Exception> result = new Err<>(new RuntimeException("error"));
        List<Exception> inspected = new ArrayList<>();
        Result<String, Exception> returned = result.inspect(e -> inspected.add(e));
        assertSame(result, returned);
        assertEquals(1, inspected.size());
        assertEquals("error", inspected.get(0).getMessage());
    }

    @Test
    void should_inspect_ok_noop() {
        Result<String, Exception> result = new Ok<>("value");
        List<Exception> inspected = new ArrayList<>();
        Result<String, Exception> returned = result.inspect(inspected::add);
        assertSame(result, returned);
        assertTrue(inspected.isEmpty());
    }

    @Test
    void should_throw_illegal_argument_when_inspect_null_consumer() {
        Result<String, Exception> result = new Err<>(new RuntimeException());
        assertThrows(IllegalArgumentException.class, () -> result.inspect((java.util.function.Consumer<Exception>) null));
    }

    @Test
    void should_inspect_with_predicate_matching() {
        Result<String, Exception> result = new Err<>(new RuntimeException("error"));
        List<Exception> inspected = new ArrayList<>();
        result.inspect(e -> e.getMessage().equals("error"), e -> inspected.add(e));
        assertEquals(1, inspected.size());
    }

    @Test
    void should_inspect_with_predicate_not_matching() {
        Result<String, Exception> result = new Err<>(new RuntimeException("error"));
        List<Exception> inspected = new ArrayList<>();
        result.inspect(e -> e.getMessage().equals("other"), e -> inspected.add(e));
        assertTrue(inspected.isEmpty());
    }

    @Test
    void should_throw_illegal_argument_when_inspect_predicate_null() {
        Result<String, Exception> result = new Err<>(new RuntimeException());
        assertThrows(IllegalArgumentException.class, () -> result.inspect((java.util.function.Predicate<Exception>) null, e -> {}));
    }

    @Test
    void should_throw_illegal_argument_when_inspect_consumer_null() {
        Result<String, Exception> result = new Err<>(new RuntimeException());
        assertThrows(IllegalArgumentException.class, () -> result.inspect(e -> true, null));
    }

    @Test
    void should_inspect_with_type_matching() {
        Result<String, Exception> result = new Err<>(new IllegalArgumentException("error"));
        List<Exception> inspected = new ArrayList<>();
        result.inspect(IllegalArgumentException.class, e -> inspected.add(e));
        assertEquals(1, inspected.size());
    }

    @Test
    void should_inspect_with_type_not_matching() {
        Result<String, Exception> result = new Err<>(new RuntimeException("error"));
        List<Exception> inspected = new ArrayList<>();
        result.inspect(IllegalArgumentException.class, e -> inspected.add(e));
        assertTrue(inspected.isEmpty());
    }

    @Test
    void should_throw_illegal_argument_when_inspect_type_null() {
        Result<String, Exception> result = new Err<>(new RuntimeException());
        assertThrows(IllegalArgumentException.class, () -> result.inspect((Class<? extends Exception>) null, e -> {}));
    }

    @Test
    void should_throw_illegal_argument_when_inspect_consumer_for_type_null() {
        Result<String, Exception> result = new Err<>(new RuntimeException());
        assertThrows(IllegalArgumentException.class, () -> result.inspect(Exception.class, null));
    }

    @Test
    void should_taint_ok_result() {
        Result<String, Exception> result = new Ok<>("value");
        Exception ex = new RuntimeException("taint");
        Result<String, Exception> tainted = result.taint(ex);
        assertTrue(tainted.isErr());
        assertSame(ex, tainted.err());
    }

    @Test
    void should_taint_err_unchanged() {
        Exception original = new RuntimeException("original");
        Result<String, Exception> result = new Err<>(original);
        Exception taint = new RuntimeException("taint");
        Result<String, Exception> tainted = result.taint(taint);
        assertTrue(tainted.isErr());
        assertSame(original, tainted.err());
    }

    @Test
    void should_throw_illegal_argument_when_taint_null() {
        Result<String, Exception> result = new Ok<>("value");
        assertThrows(IllegalArgumentException.class, () -> result.taint(null));
    }

    @Test
    void should_taint_with_predicate_true() {
        Result<Integer, Exception> result = new Ok<>(5);
        Exception ex = new RuntimeException("taint");
        Result<Integer, Exception> tainted = result.taint(v -> v > 0, ex);
        assertTrue(tainted.isErr());
        assertSame(ex, tainted.err());
    }

    @Test
    void should_taint_with_predicate_false() {
        Result<Integer, Exception> result = new Ok<>(5);
        Exception ex = new RuntimeException("taint");
        Result<Integer, Exception> tainted = result.taint(v -> v < 0, ex);
        assertTrue(tainted.isOk());
        assertEquals(5, tainted.value());
    }

    @Test
    void should_throw_illegal_argument_when_taint_predicate_null() {
        Result<Integer, Exception> result = new Ok<>(5);
        assertThrows(IllegalArgumentException.class, () -> result.taint(null, new RuntimeException()));
    }

    @Test
    void should_throw_illegal_argument_when_taint_exception_null() {
        Result<Integer, Exception> result = new Ok<>(5);
        assertThrows(IllegalArgumentException.class, () -> result.taint(v -> true, null));
    }

    @Test
    void should_recover_err_with_value() {
        Result<String, Exception> result = new Err<>(new RuntimeException("error"));
        Result<String, Exception> recovered = result.recover("default");
        assertTrue(recovered.isOk());
        assertEquals("default", recovered.value());
    }

    @Test
    void should_recover_ok_unchanged() {
        Result<String, Exception> result = new Ok<>("value");
        Result<String, Exception> recovered = result.recover("default");
        assertTrue(recovered.isOk());
        assertEquals("value", recovered.value());
    }

    @Test
    void should_throw_illegal_argument_when_recover_null() {
        Result<String, Exception> result = new Err<>(new RuntimeException());
        assertThrows(IllegalArgumentException.class, () -> result.recover((String) null));
    }

    @Test
    void should_recover_err_with_supplier() {
        Result<String, Exception> result = new Err<>(new RuntimeException("error"));
        Result<String, Exception> recovered = result.recover((Supplier<String>) () -> "supplied");
        assertTrue(recovered.isOk());
        assertEquals("supplied", recovered.value());
    }

    @Test
    void should_throw_illegal_argument_when_recover_supplier_null() {
        Result<String, Exception> result = new Err<>(new RuntimeException());
        assertThrows(IllegalArgumentException.class, () -> result.recover((Supplier<String>) null));
    }

    @Test
    void should_recover_err_with_function() {
        Result<String, Exception> result = new Err<>(new RuntimeException("error"));
        Result<String, Exception> recovered = result.recover((java.util.function.Function<Exception, String>) e -> "recovered: " + e.getMessage());
        assertTrue(recovered.isOk());
        assertEquals("recovered: error", recovered.value());
    }

    @Test
    void should_throw_illegal_argument_when_recover_function_null() {
        Result<String, Exception> result = new Err<>(new RuntimeException());
        assertThrows(IllegalArgumentException.class, () -> result.recover((java.util.function.Function<Exception, String>) null));
    }

    @Test
    void should_recover_err_with_predicate_true() {
        Result<String, Exception> result = new Err<>(new RuntimeException("error"));
        Result<String, Exception> recovered = result.recover(e -> true, "default");
        assertTrue(recovered.isOk());
        assertEquals("default", recovered.value());
    }

    @Test
    void should_recover_err_with_predicate_true_supplier() {
        Result<String, Exception> result = new Err<>(new RuntimeException("error"));
        Result<String, Exception> recovered = result.recover((java.util.function.Predicate<Exception>) e -> true, (Supplier<String>) () -> "supplied");
        assertTrue(recovered.isOk());
        assertEquals("supplied", recovered.value());
    }

    @Test
    void should_recover_err_with_predicate_false() {
        Result<String, Exception> result = new Err<>(new RuntimeException("error"));
        Result<String, Exception> recovered = result.recover(e -> false, "default");
        assertTrue(recovered.isErr());
    }

    @Test
    void should_recover_ok_with_predicate() {
        Result<String, Exception> result = new Ok<>("value");
        Result<String, Exception> recovered = result.recover(e -> true, "default");
        assertTrue(recovered.isOk());
        assertEquals("value", recovered.value());
    }

    @Test
    void should_throw_illegal_argument_when_recover_predicate_null() {
        Result<String, Exception> result = new Err<>(new RuntimeException());
        assertThrows(IllegalArgumentException.class, () -> result.recover((java.util.function.Predicate<Exception>) null, "default"));
    }

    @Test
    void should_throw_illegal_argument_when_recover_with_predicate_value_null() {
        Result<String, Exception> result = new Err<>(new RuntimeException());
        assertThrows(IllegalArgumentException.class, () -> result.recover(e -> true, (String) null));
    }

    @Test
    void should_recover_ok_with_predicate_and_supplier() {
        Result<String, Exception> result = new Ok<>("value");
        Result<String, Exception> recovered = result.recover((java.util.function.Predicate<Exception>) e -> true, (Supplier<String>) () -> "supplied");
        assertTrue(recovered.isOk());
        assertEquals("value", recovered.value());
    }

    @Test
    void should_recover_err_with_predicate_and_supplier_false() {
        Result<String, Exception> result = new Err<>(new RuntimeException("error"));
        Result<String, Exception> recovered = result.recover((java.util.function.Predicate<Exception>) e -> false, (Supplier<String>) () -> "supplied");
        assertTrue(recovered.isErr());
        assertEquals("error", recovered.err().getMessage());
    }

    @Test
    void should_throw_illegal_argument_when_recover_with_predicate_supplier_null() {
        Result<String, Exception> result = new Err<>(new RuntimeException());
        assertThrows(IllegalArgumentException.class, () -> result.recover((java.util.function.Predicate<Exception>) e -> true, (Supplier<String>) null));
    }

    @Test
    void should_recover_err_with_predicate_and_function() {
        Result<String, Exception> result = new Err<>(new RuntimeException("error"));
        Result<String, Exception> recovered = result.recover((java.util.function.Predicate<Exception>) e -> true, (java.util.function.Function<Exception, String>) e -> "recovered: " + e.getMessage());
        assertTrue(recovered.isOk());
        assertEquals("recovered: error", recovered.value());
    }

    @Test
    void should_recover_err_with_predicate_and_function_false() {
        Result<String, Exception> result = new Err<>(new RuntimeException("error"));
        Result<String, Exception> recovered = result.recover((java.util.function.Predicate<Exception>) e -> false, (java.util.function.Function<Exception, String>) e -> "recovered: " + e.getMessage());
        assertTrue(recovered.isErr());
        assertEquals("error", recovered.err().getMessage());
    }

    @Test
    void should_recover_ok_with_predicate_and_function() {
        Result<String, Exception> result = new Ok<>("value");
        Result<String, Exception> recovered = result.recover((java.util.function.Predicate<Exception>) e -> true, (java.util.function.Function<Exception, String>) e -> "recovered: " + e.getMessage());
        assertTrue(recovered.isOk());
        assertEquals("value", recovered.value());
    }

    @Test
    void should_throw_illegal_argument_when_recover_with_predicate_function_null() {
        Result<String, Exception> result = new Err<>(new RuntimeException());
        assertThrows(IllegalArgumentException.class, () -> result.recover((java.util.function.Predicate<Exception>) e -> true, (java.util.function.Function<Exception, String>) null));
    }

    @Test
    void should_recover_err_with_type() {
        Result<String, Exception> result = new Err<>(new IllegalArgumentException("error"));
        Result<String, Exception> recovered = result.recover(IllegalArgumentException.class, "default");
        assertTrue(recovered.isOk());
        assertEquals("default", recovered.value());
    }

    @Test
    void should_recover_err_with_wrong_type() {
        Result<String, Exception> result = new Err<>(new RuntimeException("error"));
        Result<String, Exception> recovered = result.recover(IllegalArgumentException.class, "default");
        assertTrue(recovered.isErr());
    }

    @Test
    void should_throw_illegal_argument_when_recover_type_null() {
        Result<String, Exception> result = new Err<>(new RuntimeException());
        assertThrows(IllegalArgumentException.class, () -> result.recover((Class<? extends Exception>) null, "default"));
    }

    @Test
    void should_throw_illegal_argument_when_recover_with_type_value_null() {
        Result<String, Exception> result = new Err<>(new RuntimeException());
        assertThrows(IllegalArgumentException.class, () -> result.recover(Exception.class, (String) null));
    }

    @Test
    void should_recover_err_with_type_and_supplier() {
        Result<String, Exception> result = new Err<>(new IllegalArgumentException("error"));
        Result<String, Exception> recovered = result.recover(IllegalArgumentException.class, (Supplier<String>) () -> "supplied");
        assertTrue(recovered.isOk());
        assertEquals("supplied", recovered.value());
    }

    @Test
    void should_recover_ok_with_type_and_supplier() {
        Result<String, Exception> result = new Ok<>("value");
        Result<String, Exception> recovered = result.recover(IllegalArgumentException.class, (Supplier<String>) () -> "supplied");
        assertTrue(recovered.isOk());
        assertEquals("value", recovered.value());
    }

    @Test
    void should_recover_err_with_type_and_supplier_mismatch() {
        Result<String, Exception> result = new Err<>(new RuntimeException("error"));
        Result<String, Exception> recovered = result.recover(IllegalArgumentException.class, (Supplier<String>) () -> "supplied");
        assertTrue(recovered.isErr());
        assertEquals("error", recovered.err().getMessage());
    }

    @Test
    void should_throw_illegal_argument_when_recover_with_type_supplier_null() {
        Result<String, Exception> result = new Err<>(new RuntimeException());
        assertThrows(IllegalArgumentException.class, () -> result.recover(Exception.class, (Supplier<String>) null));
    }

    @Test
    void should_recover_err_with_type_and_function() {
        Result<String, Exception> result = new Err<>(new IllegalArgumentException("error"));
        Result<String, Exception> recovered = result.recover(IllegalArgumentException.class, (java.util.function.Function<Exception, String>) e -> "recovered: " + e.getMessage());
        assertTrue(recovered.isOk());
        assertEquals("recovered: error", recovered.value());
    }

    @Test
    void should_recover_err_with_type_and_function_mismatch() {
        Result<String, Exception> result = new Err<>(new RuntimeException("error"));
        Result<String, Exception> recovered = result.recover(IllegalArgumentException.class, (java.util.function.Function<Exception, String>) e -> "recovered: " + e.getMessage());
        assertTrue(recovered.isErr());
        assertEquals("error", recovered.err().getMessage());
    }

    @Test
    void should_recover_ok_with_type_and_function() {
        Result<String, Exception> result = new Ok<>("value");
        Result<String, Exception> recovered = result.recover(IllegalArgumentException.class, (java.util.function.Function<Exception, String>) e -> "recovered: " + e.getMessage());
        assertTrue(recovered.isOk());
        assertEquals("value", recovered.value());
    }

    @Test
    void should_throw_illegal_argument_when_recover_with_type_function_null() {
        Result<String, Exception> result = new Err<>(new RuntimeException());
        assertThrows(IllegalArgumentException.class, () -> result.recover(Exception.class, (java.util.function.Function<Exception, String>) null));
    }

    @Test
    void should_get_value_from_ok() {
        Result<String, Exception> result = new Ok<>("value");
        assertEquals("value", result.value());
    }

    @Test
    void should_throw_when_getting_value_from_err() {
        Result<String, Exception> result = new Err<>(new RuntimeException("error"));
        assertThrows(IllegalStateException.class, result::value);
    }

    @Test
    void should_get_err_from_err() {
        Exception ex = new RuntimeException("error");
        Result<String, Exception> result = new Err<>(ex);
        assertSame(ex, result.err());
    }

    @Test
    void should_throw_when_getting_err_from_ok() {
        Result<String, Exception> result = new Ok<>("value");
        assertThrows(IllegalStateException.class, result::err);
    }

    @Test
    void should_if_ok() {
        Result<String, Exception> result = new Ok<>("value");
        List<String> values = new ArrayList<>();
        result.ifOk(values::add);
        assertEquals(List.of("value"), values);
    }

    @Test
    void should_if_ok_noop_on_err() {
        Result<String, Exception> result = new Err<>(new RuntimeException("error"));
        List<String> values = new ArrayList<>();
        result.ifOk(values::add);
        assertTrue(values.isEmpty());
    }

    @Test
    void should_throw_illegal_argument_when_if_ok_null() {
        Result<String, Exception> result = new Ok<>("value");
        assertThrows(IllegalArgumentException.class, () -> result.ifOk(null));
    }

    @Test
    void should_if_err() {
        Result<String, Exception> result = new Err<>(new RuntimeException("error"));
        List<Exception> errors = new ArrayList<>();
        result.ifErr(errors::add);
        assertEquals(1, errors.size());
    }

    @Test
    void should_if_err_noop_on_ok() {
        Result<String, Exception> result = new Ok<>("value");
        List<Exception> errors = new ArrayList<>();
        result.ifErr(errors::add);
        assertTrue(errors.isEmpty());
    }

    @Test
    void should_throw_illegal_argument_when_if_err_null() {
        Result<String, Exception> result = new Err<>(new RuntimeException());
        assertThrows(IllegalArgumentException.class, () -> result.ifErr(null));
    }

    @Test
    void should_unwrap_ok() {
        Result<String, Exception> result = new Ok<>("value");
        assertEquals("value", result.unwrap());
    }

    @Test
    void should_unwrap_err_throws_failure() {
        Result<String, Exception> result = new Err<>(new RuntimeException("error"));
        Failure failure = assertThrows(Failure.class, result::unwrap);
        assertInstanceOf(RuntimeException.class, failure.getCause());
    }

    @Test
    void should_unwrap_or_ok() {
        Result<String, Exception> result = new Ok<>("value");
        assertEquals("value", result.unwrapOr("default"));
    }

    @Test
    void should_unwrap_or_err() {
        Result<String, Exception> result = new Err<>(new RuntimeException("error"));
        assertEquals("default", result.unwrapOr("default"));
    }

    @Test
    void should_throw_illegal_argument_when_unwrap_or_null() {
        Result<String, Exception> result = new Err<>(new RuntimeException());
        assertThrows(IllegalArgumentException.class, () -> result.unwrapOr((String) null));
    }

    @Test
    void should_unwrap_or_with_supplier() {
        Result<String, Exception> result = new Err<>(new RuntimeException("error"));
        assertEquals("supplied", result.unwrapOr((Supplier<String>) () -> "supplied"));
    }

    @Test
    void should_unwrap_or_with_supplier_on_ok() {
        Result<String, Exception> result = new Ok<>("value");
        assertEquals("value", result.unwrapOr((Supplier<String>) () -> "supplied"));
    }

    @Test
    void should_throw_illegal_argument_when_unwrap_or_supplier_null() {
        Result<String, Exception> result = new Err<>(new RuntimeException());
        assertThrows(IllegalArgumentException.class, () -> result.unwrapOr((Supplier<String>) null));
    }

    @Test
    void should_unwrap_checked_ok() throws Exception {
        Result<String, Exception> result = new Ok<>("value");
        assertEquals("value", result.unwrapChecked());
    }

    @Test
    void should_unwrap_checked_err_throws() {
        Result<String, Exception> result = new Err<>(new RuntimeException("error"));
        assertThrows(RuntimeException.class, result::unwrapChecked);
    }

    @Test
    void should_recover_ok_with_function() {
        Result<String, Exception> result = new Ok<>("value");
        Result<String, Exception> recovered = result.recover((java.util.function.Function<Exception, String>) e -> "fallback");
        assertTrue(recovered.isOk());
        assertEquals("value", recovered.value());
    }

    @Test
    void should_throw_illegal_argument_when_fuse_null_result_with_takefrom() {
        Result<String, Exception> result1 = new Ok<>("a");
        assertThrows(IllegalArgumentException.class, () -> result1.fuse((Result<Integer, Exception>) null, TakeFrom.HEAD));
    }

    @Test
    void should_throw_illegal_argument_when_fuse_null_takefrom() {
        Result<String, Exception> result1 = new Ok<>("a");
        Result<Integer, Exception> result2 = new Ok<>(5);
        assertThrows(IllegalArgumentException.class, () -> result1.fuse(result2, null));
    }

    @Test
    void should_throw_illegal_argument_when_recover_predicate_function_null_predicate() {
        Result<String, Exception> result = new Ok<>("value");
        assertThrows(IllegalArgumentException.class, () -> result.recover((java.util.function.Predicate<Exception>) null, (java.util.function.Function<Exception, String>) e -> "recovered"));
    }

    @Test
    void should_throw_illegal_argument_when_recover_class_function_null_type() {
        Result<String, Exception> result = new Ok<>("value");
        assertThrows(IllegalArgumentException.class, () -> result.recover((Class<? extends Exception>) null, (java.util.function.Function<Exception, String>) e -> "recovered"));
    }

    @Test
    void should_throw_illegal_argument_when_recover_predicate_supplier_null_predicate() {
        Result<String, Exception> result = new Ok<>("value");
        assertThrows(IllegalArgumentException.class, () -> result.recover((java.util.function.Predicate<Exception>) null, (Supplier<String>) () -> "supplied"));
    }

    @Test
    void should_throw_illegal_argument_when_recover_class_supplier_null_type() {
        Result<String, Exception> result = new Ok<>("value");
        assertThrows(IllegalArgumentException.class, () -> result.recover((Class<? extends Exception>) null, (Supplier<String>) () -> "supplied"));
    }

}
