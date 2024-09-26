package io.github.artkonr.result;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlagTest {

    @Test
    void should_create_err_from_err() {
        var source = newErr();
        var from = FlagResult.from(source);

        assertTrue(from.isErr());
        assertSame(source.error, from.error);
    }

    @Test
    void should_create_ok_from_ok() {
        var source = FlagResult.ok();
        var from = FlagResult.from(source);
        assertTrue(from.isOk());
    }

    @Test
    void should_throw_if_created_from_null() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.from(null));
    }

    @Test
    void should_create_ok() {
        var viaMethod = FlagResult.ok();
        assertTrue(viaMethod.isOk());
        assertFalse(viaMethod.isErr());
    }

    @Test
    void should_create_err() {
        RuntimeException ex = new RuntimeException();
        var viaConstructor = new FlagResult<>(ex);
        var viaMethod = FlagResult.err(ex);

        assertTrue(viaMethod.isErr());
        assertFalse(viaMethod.isOk());
        assertSame(viaConstructor.error, viaMethod.error);
    }

    @Test
    void should_throw_if_created_with_null_err() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.err(null));
    }

    @Test
    void should_evaluate_as_err_if_err() {
        var res = FlagResult.err(new RuntimeException("abc"));
        assertTrue(res.isErrAnd(RuntimeException.class));
        assertTrue(res.isErrAnd(Exception.class));
        assertFalse(res.isErrAnd(IllegalStateException.class));
        assertTrue(res.isErrAnd(err -> err.getMessage().contains("abc")));
        assertFalse(res.isErrAnd(err -> err.getMessage().contains("cde")));
    }

    @Test
    void should_not_evaluate_as_err_if_ok() {
        var ok = FlagResult.ok();
        assertFalse(ok.isErrAnd(RuntimeException.class));
        assertFalse(ok.isErrAnd(err -> err.getMessage().contains("abc")));
    }

    @Test
    void should_throw_if_state_evaluated_with_null_predicate() {
        assertThrows(IllegalArgumentException.class, () -> newErr().isErrAnd((Class<? extends Exception>) null));
        assertThrows(IllegalArgumentException.class, () -> newErr().isErrAnd((Predicate<RuntimeException>) null));
    }

    @Test
    void should_lossy_wrap_unknown_error() {
        FlagResult<Exception> wrapped = FlagResult.wrap(() -> { throw new RuntimeException(); });
        assertTrue(wrapped.isErr());
    }

    @Test
    void should_exact_wrap_known_error() {
        var wrapped = FlagResult.wrap(IllegalStateException.class, () -> { throw new IllegalStateException(); });
        assertTrue(wrapped.isErr());
        assertInstanceOf(IllegalStateException.class, wrapped.error);
    }

    @Test
    void should_lossy_wrap_subclass_of_known_error() {
        FlagResult<RuntimeException> wrapped = FlagResult.wrap(RuntimeException.class, () -> { throw new IllegalStateException(); });
        assertTrue(wrapped.isErr());
        assertInstanceOf(IllegalStateException.class, wrapped.error);
    }

    @Test
    void should_exact_wrap_checked_error() {
        var wrapped = FlagResult.wrap(IOException.class, () -> { throw new IOException(); });
        assertTrue(wrapped.isErr());
        assertInstanceOf(IOException.class, wrapped.error);
    }

    @Test
    void should_exact_wrap_as_ok_if_action_does_not_throw() {
        AtomicInteger counter = new AtomicInteger();
        var wrapped = FlagResult.wrap(IllegalStateException.class, () -> counter.set(100));
        assertTrue(wrapped.isOk());
    }

    @Test
    void should_lossy_wrap_as_ok_if_action_does_not_throw() {
        AtomicInteger counter = new AtomicInteger();
        var wrapped = FlagResult.wrap(() -> counter.set(100));
        assertTrue(wrapped.isOk());
    }

    @Test
    void should_throw_when_wrapping_if_null_arguments_provided() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.wrap(null, null));
        assertThrows(IllegalArgumentException.class, () -> FlagResult.wrap(RuntimeException.class, null));
        assertThrows(IllegalArgumentException.class, () -> FlagResult.wrap(null));
    }

    @Test
    void should_throw_if_exact_wrap_catches_unexpected_error() {
        assertThrows(IllegalStateException.class, () -> FlagResult.wrap(
                IllegalStateException.class,
                () -> { throw new NoSuchElementException(); }
        ));
    }

    @Test
    void should_chain_into_ok_if_all_ok() {
        List<Supplier<BaseResult<RuntimeException>>> list = new ArrayList<>();
        list.add(FlagResult::ok);
        list.add(null);
        list.add(() -> null);
        list.add(FlagResult::ok);

        var chained = FlagResult.chain(list);
        assertTrue(chained.isOk());
    }

    @Test
    void should_chain_until_first_error_occurs() {
        List<Supplier<BaseResult<RuntimeException>>> list = new ArrayList<>();
        list.add(FlagResult::ok);
        list.add(null);
        list.add(() -> FlagResult.err(new RuntimeException("1")));
        list.add(() -> FlagResult.err(new RuntimeException("2")));

        var chained = FlagResult.chain(list);
        assertTrue(chained.isErrAnd(e -> e.getMessage().equals("1")));
    }

    @Test
    void should_throw_if_chain_over_null_collection() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.chain(null));
    }

    @Test
    void should_chain_into_ok_if_empty_collection() {
        var ok = FlagResult.chain(List.of());
        assertTrue(ok.isOk());
    }

    @Test
    void should_join_into_ok_if_all_ok() {
        List<BaseResult<RuntimeException>> results = List.of(FlagResult.ok(), FlagResult.ok());
        FlagResult<?> joined = FlagResult.join(results);
        assertTrue(joined.isOk());
    }

    @Test
    void should_join_into_err_if_at_least_one_err() {
        RuntimeException ex1 = new RuntimeException();
        IllegalArgumentException ex2 = new IllegalArgumentException();
        List<BaseResult<RuntimeException>> results = List.of(
                FlagResult.ok(),
                FlagResult.err(ex1),
                FlagResult.err(ex2)
        );
        FlagResult<?> joined = FlagResult.join(results);
        assertTrue(joined.isErr());
        assertSame(ex1, joined.error);
    }

    @Test
    void should_throw_if_joining_null_collection() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.join(null));
    }

    @Test
    void should_throw_if_joining_with_null_rule() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.join(List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> FlagResult.join(null, null));
    }

    @Test
    void should_join_with_rule_into_err_with_last_encountered_error() {
        RuntimeException headEx = new RuntimeException();
        RuntimeException tailEx = new RuntimeException();

        FlagResult<RuntimeException> r1 = FlagResult.err(headEx);
        FlagResult<RuntimeException> r2 = FlagResult.err(tailEx);
        FlagResult<RuntimeException> r3 = FlagResult.ok();

        List<BaseResult<RuntimeException>> results = List.of(r1, r2, r3);
        FlagResult<?> joined = FlagResult.join(results, TakeFrom.TAIL);
        assertTrue(joined.isErr());
        assertSame(tailEx, joined.error);
    }

    @Test
    void should_join_with_rule_into_err_with_first_encountered_error() {
        RuntimeException headEx = new RuntimeException();
        RuntimeException tailEx = new RuntimeException();

        FlagResult<RuntimeException> r1 = FlagResult.err(headEx);
        FlagResult<RuntimeException> r2 = FlagResult.err(tailEx);
        FlagResult<RuntimeException> r3 = FlagResult.ok();

        List<BaseResult<RuntimeException>> results = List.of(r1, r2, r3);
        FlagResult<?> joined = FlagResult.join(results, TakeFrom.HEAD);
        assertTrue(joined.isErr());
        assertSame(headEx, joined.error);
    }

    @Test
    void should_not_throw_if_ok_unwrapped() {
        var ok = FlagResult.ok();
        assertDoesNotThrow(ok::unwrap);
    }

    @Test
    void should_throw_safely_if_err_unwrapped() {
        RuntimeException ex = new RuntimeException();
        var err = FlagResult.err(ex);
        assertThrows(Failure.class, err::unwrap);
        try {
            err.unwrap();
        } catch (Failure x) {
            assertNotNull(x.getCause());
            assertSame(ex, x.getCause());
        }
    }

    @Test
    void should_not_throw_if_ok_unwrapped_as_checked() {
        var ok = FlagResult.ok();
        assertDoesNotThrow(ok::unwrapChecked);
    }

    @Test
    void should_throw_safely_if_err_unwrapped_as_checked() {
        RuntimeException ex = new RuntimeException();
        var err = FlagResult.err(ex);
        assertThrows(RuntimeException.class, err::unwrapChecked);
        try {
            err.unwrapChecked();
        } catch (Exception x) {
            assertNull(x.getCause());
            assertSame(ex, x);
        }
    }

    @Test
    void should_throw_if_err_mapper_is_null() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.ok().mapErr(null));
    }

    @Test
    void should_throw_if_err_mapper_returns_null() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.err(new RuntimeException()).mapErr(exception -> null));
    }

    @Test
    void should_lose_specific_type_if_err() {
        FlagResult<RuntimeException> source = newErr();
        FlagResult<Exception> lossy = source.upcast();
        assertInstanceOf(RuntimeException.class, lossy.error);
    }

    @Test
    void should_lose_specific_type_if_ok() {
        FlagResult<RuntimeException> source = FlagResult.ok();
        FlagResult<Exception> lossy = source.upcast();
        assertTrue(lossy.isOk());
    }

    @Test
    void should_not_apply_map_to_err_if_ok() {
        var ok = FlagResult.ok();
        var mapped = ok.mapErr(RuntimeException::new);
        assertTrue(mapped.isOk());
        assertNotSame(ok, mapped);
    }

    @Test
    void should_apply_map_to_err_if_err() {
        var err = newErr();
        var mapped = err.mapErr(RuntimeException::new);
        assertTrue(mapped.isErr());
        assertNotSame(err.error, mapped.error);
    }

    @Test
    void should_flat_map_if_ok() {
        var ok = FlagResult.ok();
        var mapped = ok.flatMap(() -> FlagResult.err(new RuntimeException()));
        assertTrue(mapped.isErrAnd(RuntimeException.class));
    }

    @Test
    void should_not_flat_map_if_err() {
        var ok = FlagResult.err(new RuntimeException());
        var mapped = ok.flatMap(FlagResult::ok);
        assertTrue(mapped.isErr());
    }

    @Test
    void should_throw_if_flat_map_function_null_or_returns_null() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.ok().flatMap(null));
        assertThrows(IllegalArgumentException.class, () -> FlagResult.ok().flatMap(() -> null));
    }

    @Test
    void should_throw_on_getting_err_if_ok() {
        var ok = FlagResult.ok();
        assertThrows(IllegalStateException.class, ok::getErr);
    }

    @Test
    void should_not_throw_on_getting_err_if_err() {
        var err = newErr();
        assertDoesNotThrow(err::getErr);
    }

    @Test
    void should_populate_with_value_if_ok() {
        var flag = FlagResult.ok();
        var populated = flag.populate("value");

        assertTrue(populated.isOk());
        assertEquals("value", populated.get());
    }

    @Test
    void should_not_populate_with_value_if_err() {
        var flag = newErr();
        var populated = flag.populate("value");
        assertTrue(populated.isErr());
        assertEquals(flag.error, populated.error);
    }

    @Test
    void should_populate_with_supplied_value_if_ok() {
        var flag = FlagResult.ok();
        var populated = flag.populate(() -> "value");

        assertTrue(populated.isOk());
        assertEquals("value", populated.get());
    }

    @Test
    void should_throw_if_populating_with_null_or_with_supplier_returning_null() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.ok().populate(null));
        assertThrows(IllegalArgumentException.class, () -> FlagResult.ok().populate((Supplier<?>) null));
        assertThrows(IllegalArgumentException.class, () -> FlagResult.ok().populate(() -> null));
    }

    @Test
    void should_taint_if_ok() {
        RuntimeException ex = new IllegalArgumentException();
        var ok = FlagResult.ok();
        var tainted = ok.taint(() -> ex);
        assertTrue(tainted.isErr());
        assertEquals(ex, tainted.error);
    }

    @Test
    void should_not_taint_if_err() {
        RuntimeException ex1 = new IllegalArgumentException();
        RuntimeException ex2 = new RuntimeException();
        var err = FlagResult.err(ex1);
        var tainted = err.taint(() -> ex2);
        assertTrue(tainted.isErr());
        assertNotSame(err, tainted);
        assertNotEquals(ex1, ex2);
    }

    @Test
    void should_throw_if_taint_supplier_is_null_or_returns_null() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.ok().taint(null));
        assertThrows(IllegalArgumentException.class, () -> FlagResult.ok().taint(() -> null));
    }

    @Test
    void should_fork_if_ok() {
        RuntimeException ex = new IllegalArgumentException();
        var ok = FlagResult.ok();
        var tainted = ok.fork(() -> ex);
        assertTrue(tainted.isErr());
        assertEquals(ex, tainted.error);
    }

    @Test
    void should_not_fork_if_err() {
        RuntimeException ex1 = new RuntimeException();
        RuntimeException ex2 = new RuntimeException();
        var err = FlagResult.err(ex1);
        var tainted = err.fork(() -> ex2);
        assertTrue(tainted.isErr());
        assertNotSame(err, tainted);
        assertNotEquals(ex1, ex2);
    }

    @Test
    void should_throw_if_fork_supplier_is_null_or_returns_null() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.ok().fork(null));
        assertThrows(IllegalArgumentException.class, () -> FlagResult.ok().fork(() -> null));
    }

    @Test
    void should_fuse_into_err_if_both_err() {
        RuntimeException ex1 = new RuntimeException();
        RuntimeException ex2 = new IllegalArgumentException();

        var first = FlagResult.err(ex1);
        var second = FlagResult.err(ex2);
        var fused = first.fuse(second);

        assertTrue(fused.isErr());
        assertSame(ex1, fused.error);
    }

    @Test
    void should_fuse_into_err_if_second_err() {
        RuntimeException ex = new RuntimeException();
        FlagResult<RuntimeException> first = FlagResult.ok();
        var second = FlagResult.err(ex);
        var fused = first.fuse(second);

        assertTrue(fused.isErr());
        assertSame(ex, fused.error);
    }

    @Test
    void should_fuse_into_err_if_first_err() {
        RuntimeException ex = new RuntimeException();
        var first = FlagResult.err(ex);
        FlagResult<RuntimeException> second = FlagResult.ok();
        var fused = first.fuse(second);

        assertTrue(fused.isErr());
        assertSame(ex, fused.error);
    }

    @Test
    void should_fuse_into_ok_if_both_ok() {
        var first = FlagResult.ok();
        var second = FlagResult.ok();

        var fused = first.fuse(second);
        assertTrue(fused.isOk());
    }

    @Test
    void should_throw_when_fusing_if_second_is_null() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.ok().fuse(null));
    }

    @Test
    void should_fuse_with_rule_into_ok_if_both_ok() {
        var first = FlagResult.ok();
        var second = FlagResult.ok();

        var fused = first.fuse(second, TakeFrom.TAIL);
        assertTrue(fused.isOk());

        fused = first.fuse(second, TakeFrom.HEAD);
        assertTrue(fused.isOk());
    }

    @Test
    void should_fuse_with_rule_into_err_if_first_err() {
        RuntimeException ex = new RuntimeException();
        FlagResult<RuntimeException> first = FlagResult.err(ex);
        FlagResult<RuntimeException> second = FlagResult.ok();
        var fused = first.fuse(second, TakeFrom.TAIL);

        assertTrue(fused.isErr());
        assertSame(ex, fused.error);

        fused = first.fuse(second, TakeFrom.HEAD);

        assertTrue(fused.isErr());
        assertSame(ex, fused.error);
    }

    @Test
    void should_fuse_with_rule_into_err_if_second_err() {
        RuntimeException ex = new RuntimeException();
        FlagResult<RuntimeException> first = FlagResult.ok();
        var second = FlagResult.err(ex);
        var fused = first.fuse(second, TakeFrom.TAIL);

        assertTrue(fused.isErr());
        assertSame(ex, fused.error);

        fused = first.fuse(second, TakeFrom.HEAD);
        assertTrue(fused.isErr());
        assertSame(ex, fused.error);
    }

    @Test
    void should_fuse_with_rule_into_err_with_second_error_if_both_err() {
        RuntimeException ex1 = new RuntimeException();
        RuntimeException ex2 = new RuntimeException();
        FlagResult<RuntimeException> first = FlagResult.err(ex1);
        FlagResult<RuntimeException> second = FlagResult.err(ex2);
        var fused = first.fuse(second, TakeFrom.TAIL);

        assertTrue(fused.isErr());
        assertSame(ex2, fused.error);
    }

    @Test
    void should_fuse_with_rule_into_err_with_first_error_if_both_err() {
        RuntimeException ex1 = new RuntimeException();
        RuntimeException ex2 = new RuntimeException();
        FlagResult<RuntimeException> first = FlagResult.err(ex1);
        FlagResult<RuntimeException> second = FlagResult.err(ex2);
        var fused = first.fuse(second, TakeFrom.HEAD);

        assertTrue(fused.isErr());
        assertSame(ex1, fused.error);
    }

    @Test
    void should_throw_when_fusing_if_second_or_rule_is_null() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.ok().fuse(FlagResult.ok(), null));
        assertThrows(IllegalArgumentException.class, () -> FlagResult.ok().fuse(null, null));
    }

    @Test
    void should_not_peek_on_err_if_ok() {
        var ok = FlagResult.ok();
        AtomicInteger cc = new AtomicInteger();
        var aft = ok.peekErr(ex -> cc.incrementAndGet());
        assertSame(ok, aft);
        assertEquals(0, cc.get());
    }

    @Test
    void should_peek_on_err_if_err() {
        var ok = FlagResult.err(new RuntimeException("abc"));
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
    void should_throw_when_peeking_if_callback_or_predicate_is_null() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.ok().peekErr(null));
        assertThrows(IllegalArgumentException.class, () -> FlagResult.ok().peekErr((Class<? extends Exception>) null, null));
        assertThrows(IllegalArgumentException.class, () -> FlagResult.ok().peekErr(Exception.class, null));
        assertThrows(IllegalArgumentException.class, () -> FlagResult.ok().peekErr((Predicate<Exception>) null, null));
        assertThrows(IllegalArgumentException.class, () -> FlagResult.ok().peekErr(ex -> ex instanceof RuntimeException, null));
    }

    @Test
    void should_compute_fixed_hashcode_if_ok() {
        var result = FlagResult.ok();
        assertEquals(31, result.hashCode());
    }

    @Test
    void should_compute_hashcode_from_error_if_err() {
        RuntimeException ex = new RuntimeException();
        int errHashCode = ex.hashCode();
        var result = FlagResult.err(ex);

        assertEquals(31 * errHashCode, result.hashCode());
    }

    @Test
    void should_see_any_two_ok_equal() {
        var first = FlagResult.ok();
        var second = FlagResult.ok();
        assertEquals(first, second);
    }

    @Test
    void should_see_same_equal() {
        var ths = newErr();
        var tht = ths;
        assertEquals(ths, tht);
    }

    @Test
    void should_see_two_err_equal_if_errors_are_equal() {
        RuntimeException ex = new RuntimeException();
        var first = FlagResult.err(ex);
        var second = FlagResult.err(ex);
        assertEquals(first, second);
    }

    @Test
    void should_not_see_two_err_equal_if_errors_not_equal() {
        RuntimeException ex1 = new RuntimeException();
        RuntimeException ex2 = new IllegalArgumentException();
        var first = FlagResult.err(ex1);
        var second = FlagResult.err(ex2);
        assertNotEquals(first, second);
    }

    @Test
    void should_not_see_two_equal_if_second_is_null() {
        assertNotEquals(
                FlagResult.ok(),
                null
        );
    }

    @Test
    void should_not_see_two_equal_if_second_has_different_type() {
        var ok = FlagResult.ok();
        assertNotEquals(ok, new Object());
    }

    @Test
    void should_show_same_toString_if_ok() {
        assertEquals(
                "FlagResult[ok]",
                FlagResult.ok().toString()
        );
    }

    @Test
    void should_include_error_in_toString_if_err() {
        var err = new RuntimeException();
        assertEquals(
                "FlagResult[err=%s]".formatted(err),
                FlagResult.err(err).toString()
        );
    }

    @Test
    void should_callback_on_ok_if_ok() {
        AtomicInteger counter = new AtomicInteger();
        var ok = FlagResult.ok();
        ok.ifOk(counter::incrementAndGet);
        assertEquals(1, counter.get());
    }

    @Test
    void should_not_callback_on_err_if_ok() {
        AtomicInteger counter = new AtomicInteger();
        var ok = FlagResult.ok();
        ok.ifErr(counter::incrementAndGet);
        assertEquals(0, counter.get());
    }

    @Test
    void should_not_consume_on_err_if_ok() {
        AtomicInteger counter = new AtomicInteger();
        var ok = FlagResult.ok();
        ok.ifErr(err -> counter.incrementAndGet());
        assertEquals(0, counter.get());
    }

    @Test
    void should_not_callback_on_ok_if_err() {
        AtomicInteger counter = new AtomicInteger();
        var err = newErr();
        err.ifOk(counter::incrementAndGet);
        assertEquals(0, counter.get());
    }

    @Test
    void should_callback_on_err_if_err() {
        AtomicInteger counter = new AtomicInteger();
        var err = newErr();
        err.ifErr(counter::incrementAndGet);
        assertEquals(1, counter.get());
    }

    @Test
    void should_consume_on_err_if_err() {
        AtomicInteger counter = new AtomicInteger();
        var err = newErr();
        err.ifErr(ex -> counter.incrementAndGet());
        assertEquals(1, counter.get());
    }

    @Test
    void should_throw_if_null_ok_callback() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.ok().ifOk(null));
    }

    @Test
    void should_throw_if_null_err_callback() {
        assertThrows(IllegalArgumentException.class, () -> newErr().ifErr((Runnable) null));
    }

    @Test
    void should_throw_if_null_err_consumer() {
        assertThrows(IllegalArgumentException.class, () -> newErr().ifErr((Consumer<RuntimeException>) null));
    }

    private static FlagResult<RuntimeException> newErr() {
        return new FlagResult<>(new RuntimeException());
    }
}
