package io.github.artkonr.result;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResultTest {
    @Test
    void should_lossy_wrap_unknown_error() {
        var wrapped = Result.wrap(() -> { throw new RuntimeException(); });
        assertTrue(wrapped.isErr());
    }

    @Test
    void should_exact_wrap_known_error() {
        var wrapped = Result.wrap(IllegalStateException.class, () -> { throw new IllegalStateException(); });
        assertTrue(wrapped.isErr());
        assertInstanceOf(IllegalStateException.class, wrapped.error);
    }

    @Test
    void should_exact_wrap_checked_error() {
        var wrapped = Result.wrap(IOException.class, () -> { throw new IOException(); });
        assertTrue(wrapped.isErr());
        assertInstanceOf(IOException.class, wrapped.error);
    }

    @Test
    void should_lossy_wrap_subclass_of_known_error() {
        Result<Integer, RuntimeException> wrapped = Result.wrap(RuntimeException.class, () -> { throw new IllegalStateException(); });
        assertTrue(wrapped.isErr());
        assertInstanceOf(IllegalStateException.class, wrapped.error);
    }

    @Test
    void should_lossy_wrap_as_ok_if_action_does_not_throw() {
        Result<Integer, Exception> wrapped = Result.wrap(() -> 1);
        assertTrue(wrapped.isOk());
        assertEquals(1, wrapped.item);
    }

    @Test
    void should_exact_wrap_as_ok_if_action_does_not_throw() {
        Result<Integer, IllegalStateException> wrapped = Result.wrap(IllegalStateException.class, () -> 1);
        assertTrue(wrapped.isOk());
        assertEquals(1, wrapped.item);
    }

    @Test
    void should_throw_when_wrapping_if_null_arguments_provided() {
        assertThrows(IllegalArgumentException.class, () -> Result.wrap(null, null));
        assertThrows(IllegalArgumentException.class, () -> Result.wrap(RuntimeException.class, null));
        assertThrows(IllegalArgumentException.class, () -> Result.wrap(RuntimeException.class, () -> null));
        assertThrows(IllegalArgumentException.class, () -> Result.wrap(null));
        assertThrows(IllegalArgumentException.class, () -> Result.wrap(() -> null));
    }

    @Test
    void should_throw_if_exact_wrap_catches_unexpected_error() {
        assertThrows(IllegalStateException.class, () -> Result.wrap(
                IllegalStateException.class,
                () -> { throw new NoSuchElementException(); }
        ));
    }

    @Test
    void should_create_err_from_err() {
        var source = newErr();
        var copy = Result.from(source);
        assertTrue(copy.isErr());
        assertSame(source.error, copy.error);
    }

    @Test
    void should_create_ok_from_ok() {
        var source = newOk();
        var copy = Result.from(source);
        assertTrue(copy.isOk());
        assertSame(source.item, copy.item);
    }

    @Test
    void should_throw_if_created_from_null() {
        assertThrows(IllegalArgumentException.class, () -> Result.from(null));
    }

    @Test
    void should_create_ok() {
        var ok = newOk();
        assertTrue(ok.isOk());
        assertFalse(ok.isErr());
        assertEquals(1, ok.item);
    }

    @Test
    void should_throw_when_creating_ok_with_null() {
        assertThrows(IllegalArgumentException.class, () -> Result.ok(null));
    }

    @Test
    void should_evaluate_as_ok_if_ok() {
        var ok = Result.ok(10);
        assertTrue(ok.isOkAnd(val -> val > 5));
        assertFalse(ok.isOkAnd(val -> val > 10));
    }

    @Test
    void should_not_evaluate_as_ok_if_err() {
        var err = newErr();
        assertFalse(err.isOkAnd(val -> val > 5));
    }

    @Test
    void should_throw_if_evaluating_with_null_predicate() {
        assertThrows(IllegalArgumentException.class, () -> newOk().isOkAnd(null));
    }

    @Test
    void should_create_err() {
        var err = newErr();
        assertTrue(err.isErr());
        assertFalse(err.isOk());
    }

    @Test
    void should_throw_when_creating_err_with_null() {
        assertThrows(IllegalArgumentException.class, () -> Result.err(null));
    }

    @Test
    void should_join_into_ok_if_all_ok() {
        List<Result<Integer, RuntimeException>> results = List.of(
                newOk(),
                newOk()
        );
        Result<List<Integer>, RuntimeException> joined = Result.join(results);
        assertTrue(joined.isOk());
        assertEquals(List.of(1, 1), joined.item);
    }

    @Test
    void should_join_into_err_if_at_least_one_err() {
        List<Result<Integer, RuntimeException>> results = List.of(
                newOk(),
                newErr()
        );
        Result<List<Integer>, RuntimeException> joined = Result.join(results);
        assertTrue(joined.isErr());
    }

    @Test
    void should_throw_if_joining_null_collection() {
        assertThrows(IllegalArgumentException.class, () -> Result.join(null));
    }

    @Test
    void should_throw_if_joining_with_null_rule() {
        assertThrows(IllegalArgumentException.class, () -> Result.join(null, null));
        assertThrows(IllegalArgumentException.class, () -> Result.join(List.of(), null));
    }

    @Test
    void should_join_with_rule_into_err_with_first_encountered_error() {
        RuntimeException headEx = new RuntimeException();
        RuntimeException tailEx = new RuntimeException();

        Result<Integer, RuntimeException> r1 = Result.err(headEx);
        Result<Integer, RuntimeException> r2 = Result.err(tailEx);
        Result<Integer, RuntimeException> r3 = Result.ok(10);

        List<Result<Integer, RuntimeException>> results = List.of(r1, r2, r3);
        Result<List<Integer>, RuntimeException> joined = Result.join(results, TakeFrom.HEAD);
        assertTrue(joined.isErr());
        assertSame(headEx, joined.error);
    }

    @Test
    void should_join_with_rule_into_err_with_last_encountered_error() {
        RuntimeException headEx = new RuntimeException();
        RuntimeException tailEx = new RuntimeException();

        Result<Integer, RuntimeException> r1 = Result.err(headEx);
        Result<Integer, RuntimeException> r2 = Result.err(tailEx);
        Result<Integer, RuntimeException> r3 = Result.ok(10);

        List<Result<Integer, RuntimeException>> results = List.of(r1, r2, r3);
        Result<List<Integer>, RuntimeException> joined = Result.join(results, TakeFrom.TAIL);
        assertTrue(joined.isErr());
        assertSame(tailEx, joined.error);
    }

    @Test
    void should_join_into_ok_without_null_elements() {
        List<Result<Integer, RuntimeException>> results = new ArrayList<>();
        results.add(newOk());
        results.add(null);
        results.add(newOk());
        Result<List<Integer>, RuntimeException> joined = Result.join(results);
        assertTrue(joined.isOk());
        assertEquals(2, joined.item.size());
    }

    @Test
    void should_elevate_non_empty_into_ok_if_ok() {
        var item = Result.ok(Optional.of(10));
        var elevated = Result.elevate(item);
        assertTrue(elevated.isPresent());
        assertTrue(elevated.get().isOk());
    }

    @Test
    void should_elevate_empty_into_ok_if_ok() {
        var item = Result.ok(Optional.empty());
        var elevated = Result.elevate(item);
        assertTrue(elevated.isEmpty());
    }

    @Test
    void should_elevate_into_err_if_err() {
        Result<Optional<Object>, RuntimeException> item = Result.err(new RuntimeException());
        var elevated = Result.elevate(item);
        assertTrue(elevated.isPresent());
        assertTrue(elevated.get().isErr());
    }

    @Test
    void should_throw_when_elevating_null() {
        assertThrows(IllegalArgumentException.class, () -> Result.elevate(null));
    }

    @Test
    void should_get_value_if_ok() {
        var ok = newOk();
        assertEquals(1, ok.get());
    }

    @Test
    void should_throw_when_getting_value_if_err() {
        var err = newErr();
        assertThrows(IllegalStateException.class, err::get);
    }

    @Test
    void should_fuse_into_err_if_second_err() {
        var first = newOk();
        var second = newErr();

        var fused = first.fuse(second);
        assertTrue(fused.isErr());
        assertSame(fused.error, second.error);
    }

    @Test
    void should_fuse_into_err_if_first_err() {
        var first = newErr();
        var second = newOk();

        var fused = first.fuse(second);
        assertTrue(fused.isErr());
        assertSame(fused.error, first.error);
    }

    @Test
    void should_fuse_into_ok_if_both_ok() {
        var first = newOk();
        var second = newOk();

        var fused = first.fuse(second);
        assertTrue(fused.isOk());
        assertSame(fused.item.left(), first.item);
        assertSame(fused.item.right(), second.item);
    }

    @Test
    void should_fuse_into_err_with_first_error_if_both_err() {
        var first = newErr();
        var second = newErr();

        var fused = first.fuse(second);
        assertTrue(fused.isErr());
        assertSame(fused.error, first.error);
    }

    @Test
    void should_fuse_with_rule_into_err_with_first_error_if_both_err() {
        var first = newErr();
        var second = newErr();

        var fused = first.fuse(second, TakeFrom.HEAD);
        assertTrue(fused.isErr());
        assertSame(fused.error, first.error);
    }

    @Test
    void should_fuse_with_rule_into_err_with_second_error_if_both_err() {
        var first = newErr();
        var second = newErr();

        var fused = first.fuse(second, TakeFrom.TAIL);
        assertTrue(fused.isErr());
        assertSame(fused.error, second.error);
    }

    @Test
    void should_throw_when_fusing_with_null() {
        assertThrows(IllegalArgumentException.class, () -> newOk().fuse((Result<?, RuntimeException>) null));
    }

    @Test
    void should_throw_when_fusing_with_rule_with_null() {
        assertThrows(IllegalArgumentException.class, () -> newOk().fuse((Result<?, RuntimeException>) null, null));
        assertThrows(IllegalArgumentException.class, () -> newOk().fuse(Result.ok(10), null));
    }

    @Test
    void should_fuse_generic_into_err_if_second_err() {
        var first = newOk();
        var second = FlagResult.err(new RuntimeException());

        var fused = first.fuse(second);
        assertTrue(fused.isErr());
        assertSame(fused.error, second.error);
    }

    @Test
    void should_fuse_generic_into_err_if_first_err() {
        Result<Integer, RuntimeException> first = newErr();
        FlagResult<RuntimeException> second = FlagResult.ok();

        var fused = first.fuse(second);
        assertTrue(fused.isErr());
        assertSame(fused.error, first.error);
    }

    @Test
    void should_fuse_generic_into_ok_if_both_ok() {
        var first = newOk();
        FlagResult<RuntimeException> second = FlagResult.ok();

        var fused = first.fuse(second);
        assertTrue(fused.isOk());
        assertSame(fused.item, first.item);
    }

    @Test
    void should_fuse_generic_into_err_if_both_err() {
        var first = newErr();
        var second = FlagResult.err(new RuntimeException());

        var fused = first.fuse(second);
        assertTrue(fused.isErr());
        assertSame(fused.error, first.error);
    }

    @Test
    void should_fuse_generic_with_rule_into_err_with_first_error_if_both_err() {
        var first = newErr();
        var second = FlagResult.err(new RuntimeException());

        var fused = first.fuse(second, TakeFrom.HEAD);
        assertTrue(fused.isErr());
        assertSame(fused.error, first.error);
    }

    @Test
    void should_fuse_generic_with_rule_into_err_with_second_error_if_both_err() {
        var first = newErr();
        var second = FlagResult.err(new RuntimeException());

        var fused = first.fuse(second, TakeFrom.TAIL);
        assertTrue(fused.isErr());
        assertSame(fused.error, second.error);
    }

    @Test
    void should_fuse_generic_with_rule_into_ok_if_both_ok() {
        var first = newOk();
        FlagResult<RuntimeException> second = FlagResult.ok();

        var fused = first.fuse(second, TakeFrom.TAIL);
        assertTrue(fused.isOk());
        assertEquals(first.item, fused.item);
    }

    @Test
    void should_throw_when_fusing_generic_with_rule_if_null_args() {
        assertThrows(IllegalArgumentException.class, () -> newOk().fuse((BaseResult<RuntimeException>) null));
        assertThrows(IllegalArgumentException.class, () -> newOk().fuse((BaseResult<RuntimeException>) null, null));
        assertThrows(IllegalArgumentException.class, () -> newOk().fuse(FlagResult.ok(), null));
    }

    @Test
    void should_peek_on_ok_if_ok() {
        var ok = Result.ok(10);
        AtomicInteger cc = new AtomicInteger();
        var aft = ok
                .peek(val -> cc.incrementAndGet())
                .peek(val -> val > 5, val -> cc.incrementAndGet())
                .peek(val -> val > 20, val -> cc.incrementAndGet());
        assertSame(ok, aft);
        assertEquals(2, cc.get());
    }

    @Test
    void should_not_peek_on_ok_if_err() {
        var err = newErr();
        AtomicInteger cc = new AtomicInteger();
        var aft = err
                .peek(val -> cc.incrementAndGet())
                .peek(val -> val > 5, val -> cc.incrementAndGet())
                .peek(val -> val > 20, val -> cc.incrementAndGet());
        assertSame(err, aft);
        assertEquals(0, cc.get());
    }

    @Test
    void should_throw_when_peeking_on_ok_if_callback_or_predicate_is_null() {
        assertThrows(IllegalArgumentException.class, () -> newOk().peek(null));
        assertThrows(IllegalArgumentException.class, () -> newOk().peek(null, null));
        assertThrows(IllegalArgumentException.class, () -> newOk().peek(val -> val > 1, null));
    }

    @Test
    void should_not_peek_on_err_if_ok() {
        var ok = newOk();
        AtomicInteger cc = new AtomicInteger();
        var aft = ok.peekErr(ex -> cc.incrementAndGet());
        assertSame(ok, aft);
        assertEquals(0, cc.get());
    }

    @Test
    void should_peek_on_err_if_err() {
        var ok = Result.err(new RuntimeException("abc"));
        AtomicInteger cc = new AtomicInteger();
        var aft = ok
                .peekErr(ex -> cc.incrementAndGet())
                .peekErr(RuntimeException.class, ex -> cc.incrementAndGet())
                .peekErr(Exception.class, ex -> cc.incrementAndGet())
                .peekErr(IllegalStateException.class, ex -> cc.incrementAndGet())
                .peekErr(
                        ex -> ex.getMessage().contains("abc"),
                        ex -> cc.incrementAndGet()
                )
                .peekErr(
                        ex -> ex.getMessage().contains("def"),
                        ex -> cc.incrementAndGet()
                );
        assertSame(ok, aft);
        assertEquals(4, cc.get());
    }

    @Test
    void should_throw_when_peeking_on_err_if_callback_or_predicate_is_null() {
        assertThrows(IllegalArgumentException.class, () -> newOk().peekErr(null));
        assertThrows(IllegalArgumentException.class, () -> newOk().peekErr((Class<? extends Exception>) null, null));
        assertThrows(IllegalArgumentException.class, () -> newOk().peekErr(Exception.class, null));
        assertThrows(IllegalArgumentException.class, () -> newOk().peekErr((Predicate<RuntimeException>) null, null));
        assertThrows(IllegalArgumentException.class, () -> newOk().peekErr(ex -> ex instanceof RuntimeException, null));
    }

    @Test
    void should_drop_into_ok_if_ok() {
        var ok = newOk();
        FlagResult<RuntimeException> dropped = ok.drop();
        assertTrue(dropped.isOk());
    }

    @Test
    void should_drop_into_err_if_err() {
        var err = newErr();
        FlagResult<RuntimeException> dropped = err.drop();
        assertTrue(dropped.isErr());
        assertSame(err.error, dropped.error);
    }

    @Test
    void should_swap_if_ok() {
        var ok = newOk();
        var swap = ok.swap("abc");
        assertTrue(swap.isOk());
        assertEquals("abc", swap.item);
    }

    @Test
    void should_not_swap_if_err() {
        var err = newErr();
        var swap = err.swap("abc");
        assertTrue(swap.isErr());
    }

    @Test
    void should_throw_when_swapping_if_new_val_is_null() {
        assertThrows(IllegalArgumentException.class, () -> newOk().swap(null));
    }

    @Test
    void should_map_if_ok() {
        var ok = Result.ok(2);
        var mapped = ok.map("a"::repeat);
        assertTrue(mapped.isOk());
        assertEquals("aa", mapped.item);
    }

    @Test
    void should_not_map_if_err() {
        var err = newErr();
        var mapped = err.map("a"::repeat);
        assertTrue(mapped.isErr());
    }

    @Test
    void should_throw_when_mapping_if_function_is_null_or_returns_null() {
        assertThrows(IllegalArgumentException.class, () -> newOk().map(null));
        assertThrows(IllegalArgumentException.class, () -> newOk().map(integer -> null));
    }

    @Test
    void should_flat_map_if_ok() {
        Result<Integer, RuntimeException> ok = Result.ok(2);
        Result<String, RuntimeException> mapped = ok.flatMap(counter -> Result.ok("abc"));
        assertTrue(mapped.isOk());
        assertEquals("abc", mapped.item);
    }

    @Test
    void should_not_flat_map_if_err() {
        Result<Integer, RuntimeException> ok = newErr();
        Result<String, RuntimeException> mapped = ok.flatMap(counter -> Result.ok("abc"));
        assertTrue(mapped.isErr());
    }

    @Test
    void should_throw_when_flat_mapping_if_function_is_null_or_returns_null() {
        assertThrows(IllegalArgumentException.class, () -> newOk().flatMap(null));
        assertThrows(IllegalArgumentException.class, () -> newOk().flatMap(integer -> null));
    }

    @Test
    void should_lose_specific_type_if_err() {
        Result<Integer, RuntimeException> source = newErr();
        Result<Integer, Exception> lossy = source.upcast();
        assertInstanceOf(RuntimeException.class, lossy.error);
    }

    @Test
    void should_lose_specific_type_if_ok() {
        Result<Integer, RuntimeException> source = newOk();
        Result<Integer, Exception> lossy = source.upcast();
        assertTrue(lossy.isOk());
    }

    @Test
    void should_not_map_err_if_ok() {
        var ok = newOk();
        var mapped = ok.mapErr(exception -> new IllegalStateException());
        assertTrue(mapped.isOk());
        assertEquals(1, ok.item);
    }

    @Test
    void should_map_err_if_err() {
        Result<Integer, RuntimeException> err = newErr();
        Result<Integer, IllegalStateException> mapped = err.mapErr(ex -> new IllegalStateException());
        assertTrue(mapped.isErr());
        assertNotSame(err.error, mapped.error);
    }

    @Test
    void should_throw_when_mapping_err_if_function_is_null_or_returns_null() {
        assertThrows(IllegalArgumentException.class, () -> newErr().mapErr(null));
        assertThrows(IllegalArgumentException.class, () -> newErr().mapErr(err -> null));
    }

    @Test
    void should_taint_if_ok() {
        var ok = newOk();
        var tainted = ok.taint(IllegalStateException::new);
        assertTrue(ok.isOk());
    }

    @Test
    void should_not_taint_if_err() {
        Result<Integer, RuntimeException> err = newErr();
        Result<Integer, Exception> tainted = err.taint(IllegalStateException::new);
        assertTrue(err.isErr());
        assertSame(err.error, tainted.error);
    }

    @Test
    void should_throw_if_taint_supplier_is_null_or_returns_null() {
        assertThrows(IllegalArgumentException.class, () -> newErr().taint(null));
        assertThrows(IllegalArgumentException.class, () -> newOk().taint(() -> null));
    }

    @Test
    void should_fork_if_ok() {
        var ok = newOk();
        var tainted = ok.fork(IllegalStateException::new);
        assertTrue(ok.isOk());
    }

    @Test
    void should_not_fork_if_err() {
        Result<Integer, RuntimeException> err = newErr();
        Result<Integer, RuntimeException> tainted = err.fork(RuntimeException::new);
        assertTrue(err.isErr());
        assertSame(err.error, tainted.error);
    }

    @Test
    void should_throw_if_fork_supplier_is_null_or_returns_null() {
        assertThrows(IllegalArgumentException.class, () -> newErr().fork(null));
        assertThrows(IllegalArgumentException.class, () -> newOk().fork(() -> null));
    }

    @Test
    void should_taint_with_predicate_if_ok_and_predicate_holds() {
        var ok = newOk();
        var tainted = ok.taint(val -> val > 0, val -> new NumberFormatException());
        assertTrue(tainted.isErr());
        assertInstanceOf(NumberFormatException.class, tainted.error);
    }

    @Test
    void should_not_taint_with_predicate_if_ok_and_predicate_does_not_hold() {
        var ok = newOk();
        var tainted = ok.taint(val -> val < 0, val -> new NumberFormatException());
        assertTrue(tainted.isOk());
        assertEquals(ok.item, tainted.item);
    }

    @Test
    void should_not_taint_with_predicate_if_err() {
        var err = newErr();
        var tainted = err.taint(val -> val > 0, val -> new NumberFormatException());
        assertTrue(tainted.isErr());
        assertSame(err.error, tainted.error);
    }

    @Test
    void should_throw_when_tainting_with_predicate_if_taint_supplier_is_null_or_returns_null() {
        assertThrows(IllegalArgumentException.class, () -> newOk().taint(null, null));
        assertThrows(IllegalArgumentException.class, () -> newOk().taint(val -> val > 0, null));
        assertThrows(IllegalArgumentException.class, () -> newOk().taint(val -> val > 0, val -> null));
    }

    @Test
    void should_fork_with_predicate_if_ok_and_predicate_holds() {
        var ok = newOk();
        var tainted = ok.fork(val -> val > 0, val -> new NumberFormatException());
        assertTrue(tainted.isErr());
        assertInstanceOf(NumberFormatException.class, tainted.error);
    }

    @Test
    void should_not_fork_with_predicate_if_ok_and_predicate_does_not_hold() {
        var ok = newOk();
        var tainted = ok.fork(val -> val < 0, val -> new NumberFormatException());
        assertTrue(tainted.isOk());
        assertEquals(ok.item, tainted.item);
    }

    @Test
    void should_not_fork_with_predicate_if_err() {
        var err = newErr();
        var tainted = err.fork(val -> val > 0, val -> new NumberFormatException());
        assertTrue(tainted.isErr());
        assertSame(err.error, tainted.error);
    }

    @Test
    void should_throw_when_forking_with_predicate_if_taint_supplier_is_null_or_returns_null() {
        assertThrows(IllegalArgumentException.class, () -> newOk().fork(null, null));
        assertThrows(IllegalArgumentException.class, () -> newOk().fork(val -> val > 0, null));
        assertThrows(IllegalArgumentException.class, () -> newOk().fork(val -> val > 0, val -> null));
    }

    @Test
    void recover_supplier_returns_null() {
    }

    @Test
    void should_not_recover_if_ok() {
        var ok = newOk();
        var recover = ok.recover(err -> -19);
        assertNotSame(ok, recover);
        assertEquals(ok.item, recover.item);
    }

    @Test
    void should_recover_if_err() {
        var err = newErr();
        var recover = err.recover(e -> -19);
        assertTrue(recover.isOk());
        assertEquals(-19, recover.item);
    }

    @Test
    void should_throw_when_recovering_if_supplier_is_null_or_returns_null() {
        assertThrows(IllegalArgumentException.class, () -> newErr().recover(null));
        assertThrows(IllegalArgumentException.class, () -> newErr().recover(err -> null));
    }

    @Test
    void should_not_recover_with_predicate_if_ok() {
        var ok = newOk();
        var recover = ok.recover(Objects::nonNull, err -> -19);
        assertNotSame(ok, recover);
        assertEquals(ok.item, recover.item);
    }

    @Test
    void should_recover_with_predicate_if_err_and_predicate_holds() {
        var err = Result.err(new NumberFormatException("nan"));
        var recover = err.recover(i -> i.getMessage().equals("nan"), er -> -19);
        assertTrue(recover.isOk());
        assertEquals(-19, recover.item);
    }

    @Test
    void should_not_recover_with_predicate_if_err_and_predicate_does_not_hold() {
        var err = Result.err(new NumberFormatException("nan"));
        var recover = err.recover(i -> i.getMessage().equals("number"), er -> -19);
        assertTrue(recover.isErr());
        assertNotSame(err, recover);
        assertSame(err.error, recover.error);
    }

    @Test
    void should_throw_when_recovering_with_predicate_if_supplier_is_null_or_returns_null() {
        assertThrows(IllegalArgumentException.class, () -> newErr().recover((Predicate<RuntimeException>) null, null));
        assertThrows(IllegalArgumentException.class, () -> newErr().recover(Objects::nonNull, null));
        assertThrows(IllegalArgumentException.class, () -> newErr().recover(Objects::nonNull, err -> null));
    }

    @Test
    void recover_type_factory_returns_null() {
        assertThrows(IllegalArgumentException.class, () -> newErr().recover(RuntimeException.class, err -> null));
    }

    @Test
    void should_not_recover_with_type_if_ok() {
        var ok = newOk();
        var recover = ok.recover(RuntimeException.class, err -> -19);
        assertNotSame(ok, recover);
        assertEquals(ok.item, recover.item);
    }

    @Test
    void should_recover_with_type_if_err_and_predicate_holds() {
        var err = Result.err(new NumberFormatException("nan"));
        var recover = err.recover(NumberFormatException.class, er -> -19);
        assertTrue(recover.isOk());
        assertEquals(-19, recover.item);
    }

    @Test
    void should_recover_with_subtype_if_err_and_predicate_holds() {
        Result<Integer, RuntimeException> err = Result.err(new IllegalArgumentException("nan"));
        var recover = err.recover(IllegalArgumentException.class, er -> -19);
        assertTrue(recover.isOk());
        assertEquals(-19, recover.item);
    }

    @Test
    void should_not_recover_with_type_if_err_and_predicate_does_not_hold() {
        Result<Integer, Exception> err = Result.err(new IOException("nan"));
        var recover = err.recover(RuntimeException.class, er -> -19);
        assertTrue(recover.isErr());
        assertNotSame(err, recover);
        assertSame(err.error, recover.error);
    }

    @Test
    void should_not_recover_with_subtype_if_err_and_predicate_does_not_hold() {
        Result<Integer, RuntimeException> err = Result.err(new IllegalArgumentException("nan"));
        var recover = err.recover(IllegalStateException.class, er -> -19);
        assertTrue(recover.isErr());
        assertNotSame(err, recover);
        assertSame(err.error, recover.error);
    }

    @Test
    void should_throw_when_recovering_with_type_if_supplier_is_null_or_returns_null() {
        assertThrows(IllegalArgumentException.class, () -> newErr().recover((Class<? extends RuntimeException>) null, null));
        assertThrows(IllegalArgumentException.class, () -> newErr().recover(RuntimeException.class, null));
        assertThrows(IllegalArgumentException.class, () -> newErr().recover(RuntimeException.class, err -> null));
    }

    @Test
    void should_callback_on_ok_if_ok() {
        AtomicInteger counter = new AtomicInteger();
        var ok = newOk();
        ok.ifOk(counter::incrementAndGet);
        assertEquals(1, counter.get());
    }

    @Test
    void should_not_callback_on_ok_if_err() {
        AtomicInteger counter = new AtomicInteger();
        var err = newErr();
        err.ifOk(counter::incrementAndGet);
        assertEquals(0, counter.get());
    }

    @Test
    void should_consume_on_ok_if_ok() {
        AtomicInteger counter = new AtomicInteger();
        var ok = newOk();
        ok.ifOk(item -> counter.incrementAndGet());
        assertEquals(1, counter.get());
    }

    @Test
    void should_not_consume_on_ok_if_err() {
        AtomicInteger counter = new AtomicInteger();
        var ok = newErr();
        ok.ifOk(item -> counter.incrementAndGet());
        assertEquals(0, counter.get());
    }

    @Test
    void should_throw_on_consuming_on_ok_if_null_consumer() {
        assertThrows(IllegalArgumentException.class, () -> newOk().ifOk((Consumer<Integer>) null));
    }

    @Test
    void should_callback_on_err_if_err() {
        AtomicInteger counter = new AtomicInteger();
        var err = newErr();
        err.ifErr(counter::incrementAndGet);
        assertEquals(1, counter.get());
    }

    @Test
    void should_not_callback_ob_err_if_err() {
        AtomicInteger counter = new AtomicInteger();
        var ok = newOk();
        ok.ifErr(counter::incrementAndGet);
        assertEquals(0, counter.get());
    }

    @Test
    void should_not_consume_on_err_if_ok() {
        AtomicInteger counter = new AtomicInteger();
        var ok = newOk();
        ok.ifErr(err -> counter.incrementAndGet());
        assertEquals(0, counter.get());
    }

    @Test
    void should_consume_on_err_if_err() {
        AtomicInteger counter = new AtomicInteger();
        var err = newErr();
        err.ifErr(ex -> counter.incrementAndGet());
        assertEquals(1, counter.get());
    }

    @Test
    void should_unwrap_with_fallback_into_value_if_ok() {
        var ok = newOk();
        var or = ok.unwrapOr(5);
        assertEquals(ok.item, or);
    }

    @Test
    void should_unwrap_with_fallback_into_fallback_if_err() {
        var ok = newErr();
        var or = ok.unwrapOr(5);
        assertEquals(5, or);
    }

    @Test
    void should_throw_if_unwrapping_with_null_fallback() {
        assertThrows(IllegalArgumentException.class, () -> newErr().unwrapOr((Integer) null));
    }

    @Test
    void should_unwrap_with_factory_into_value_if_err() {
        var ok = newErr();
        var or = ok.unwrapOr(() -> 5);
        assertEquals(5, or);
    }

    @Test
    void should_unwrap_with_factory_into_value_if_ok() {
        var ok = newOk();
        var or = ok.unwrapOr(() -> 5);
        assertEquals(ok.item, or);
    }

    @Test
    void should_throw_if_unwrapping_with_factory_null_or_returning_null() {
        assertThrows(IllegalArgumentException.class, () -> newErr().unwrapOr((Supplier<Integer>) null));
        assertThrows(IllegalArgumentException.class, () -> newErr().unwrapOr(() -> null));
    }

    @Test
    void should_unwrap_into_value_if_ok() {
        var ok = newOk();
        assertDoesNotThrow(ok::unwrap);
        assertEquals(1, ok.unwrap());
    }

    @Test
    void should_throw_when_unwrapping_if_err() {
        try {
            Result.err(new NoSuchElementException()).unwrap();
        } catch (Exception ex) {
            assertInstanceOf(Failure.class, ex);
            assertNotNull(ex.getCause());
            assertInstanceOf(NoSuchElementException.class, ex.getCause());
        }
    }

    @Test
    void should_checked_unwrap_into_value_if_ok() {
        var ok = newOk();
        assertDoesNotThrow(ok::unwrapChecked);
        assertEquals(1, ok.unwrapChecked());
    }

    @Test
    void should_throw_when_checked_unwrapping_if_err() {
        try {
            Result.err(new NoSuchElementException()).unwrapChecked();
        } catch (Exception ex) {
            assertInstanceOf(NoSuchElementException.class, ex);
            assertNull(ex.getCause());
        }
    }

    @Test
    void should_compute_hashcode_from_ok_if_ok() {
        String item = "abc";
        int itemHash = item.hashCode();
        int resultHash = Result.ok(item).hashCode();
        assertEquals(31 * itemHash, resultHash);
    }

    @Test
    void should_compute_hashcode_from_error_if_err() {
        RuntimeException ex = new RuntimeException();
        int errHashCode = ex.hashCode();
        var result = Result.err(ex);

        assertEquals(31 * errHashCode, result.hashCode());
    }

    @Test
    void should_see_two_ok_equal_if_items_equal() {
        var first = Result.ok(1);
        var second = Result.ok(1);
        assertEquals(first, second);
    }

    @Test
    void should_not_see_two_ok_equal_if_items_not_equal() {
        var first = Result.ok(1);
        var second = Result.ok(2);
        assertNotEquals(first, second);
    }

    @Test
    void should_see_two_err_equal_if_errors_equal() {
        RuntimeException ex = new RuntimeException();
        var first = Result.err(ex);
        var second = Result.err(ex);
        assertEquals(first, second);
    }

    @Test
    void should_not_see_two_err_equal_if_errors_not_equal() {
        RuntimeException ex1 = new RuntimeException();
        RuntimeException ex2 = new IllegalArgumentException();
        var first = Result.err(ex1);
        var second = Result.err(ex2);
        assertNotEquals(first, second);
    }

    @Test
    void should_not_see_equal_if_second_is_null() {
        assertNotEquals(newOk(), null);
    }

    @Test
    void should_not_see_two_equal_if_second_has_different_type() {
        assertNotEquals(newOk(), new Object());
    }

    @Test
    void should_see_same_equal() {
        var ths = newErr();
        var tht = ths;
        assertEquals(ths, tht);
    }

    @Test
    void should_show_toString_if_ok() {
        assertEquals(
                "Result[ok=1]",
                newOk().toString()
        );
    }

    @Test
    void should_show_toString_if_err() {
        var err = new RuntimeException();
        assertEquals(
                "Result[err=%s]".formatted(err),
                Result.err(err).toString()
        );
    }

    @Test
    void record_fuse_test_null_args() {
        assertThrows(IllegalArgumentException.class, () -> new Result.Fuse<>(null, null));
        assertThrows(IllegalArgumentException.class, () -> new Result.Fuse<>(new Object(), null));
    }

    private static Result<Integer, RuntimeException> newOk() {
        return Result.ok(1);
    }

    private static Result<Integer, RuntimeException> newErr() {
        return Result.err(new RuntimeException());
    }
}
