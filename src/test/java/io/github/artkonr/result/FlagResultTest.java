package io.github.artkonr.result;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class FlagResultTest {

    @Test
    void from_err() {
        var source = newErr();
        var from = FlagResult.from(source);

        assertTrue(from.isErr());
        assertSame(source.error, from.error);
    }

    @Test
    void from_ok() {
        var source = FlagResult.ok();
        var from = FlagResult.from(source);
        assertTrue(from.isOk());
    }

    @Test
    void from_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.from(null));
    }

    @Test
    void ok() {
        var viaMethod = FlagResult.ok();
        assertTrue(viaMethod.isOk());
        assertFalse(viaMethod.isErr());
    }

    @Test
    void err() {
        RuntimeException ex = new RuntimeException();
        var viaConstructor = new FlagResult<>(ex);
        var viaMethod = FlagResult.err(ex);

        assertTrue(viaMethod.isErr());
        assertFalse(viaMethod.isOk());
        assertSame(viaConstructor.error, viaMethod.error);
    }

    @Test
    void err_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.err(null));
    }

    @Test
    void wrap_err() {
        var wrapped = FlagResult.wrap(IllegalStateException.class, () -> { throw new IllegalStateException(); });
        assertTrue(wrapped.isErr());
        assertInstanceOf(IllegalStateException.class, wrapped.error);
    }

    @Test
    void wrap_err_subclass() {
        FlagResult<RuntimeException> wrapped = FlagResult.wrap(RuntimeException.class, () -> { throw new IllegalStateException(); });
        assertTrue(wrapped.isErr());
        assertInstanceOf(IllegalStateException.class, wrapped.error);
    }

    @Test
    void wrap_err_checked() {
        var wrapped = FlagResult.wrap(IOException.class, () -> { throw new IOException(); });
        assertTrue(wrapped.isErr());
        assertInstanceOf(IOException.class, wrapped.error);
    }

    @Test
    void wrap_ok() {
        AtomicInteger counter = new AtomicInteger();
        var wrapped = FlagResult.wrap(IllegalStateException.class, () -> counter.set(100));
        assertTrue(wrapped.isOk());
    }

    @Test
    void wrap_null_args() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.wrap(null, null));
        assertThrows(IllegalArgumentException.class, () -> FlagResult.wrap(RuntimeException.class, null));
    }

    @Test
    void wrap_unexpected_error() {
        assertThrows(IllegalStateException.class, () -> FlagResult.wrap(
                IllegalStateException.class,
                () -> { throw new NoSuchElementException(); }
        ));
    }

    @Test
    void join_all_ok() {
        List<BaseResult<RuntimeException>> results = List.of(FlagResult.ok(), FlagResult.ok());
        FlagResult<?> joined = FlagResult.join(results);
        assertTrue(joined.isOk());
    }

    @Test
    void join_1_err() {
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
    void join_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.join(null));
    }

    @Test
    void join_with_rule_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.join(List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> FlagResult.join(null, null));
    }

    @Test
    void join_with_rule_many_err_tail() {
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
    void join_with_rule_many_err_head() {
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
    void unwrap_ok() {
        var ok = FlagResult.ok();
        assertDoesNotThrow(ok::unwrap);
    }

    @Test
    void unwrap_err() {
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
    void mapErr_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.ok().mapErr(null));
    }

    @Test
    void mapErr_remapper_returns_null() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.err(new RuntimeException()).mapErr(exception -> null));
    }

    @Test
    void mapErr_ok() {
        var ok = FlagResult.ok();
        var mapped = ok.mapErr(RuntimeException::new);
        assertTrue(mapped.isOk());
        assertNotSame(ok, mapped);
    }

    @Test
    void mapErr_err() {
        var err = newErr();
        var mapped = err.mapErr(RuntimeException::new);
        assertTrue(mapped.isErr());
        assertNotSame(err.error, mapped.error);
    }

    @Test
    void getErr_ok() {
        var ok = FlagResult.ok();
        assertThrows(IllegalStateException.class, ok::getErr);
    }

    @Test
    void getErr_err() {
        var err = newErr();
        assertDoesNotThrow(err::getErr);
    }

    @Test
    void populate_ok() {
        var flag = FlagResult.ok();
        var populated = flag.populate("value");

        assertTrue(populated.isOk());
        assertEquals("value", populated.get());
    }

    @Test
    void populate_err() {
        var flag = newErr();
        var populated = flag.populate("value");
        assertTrue(populated.isErr());
        assertEquals(flag.error, populated.error);
    }

    @Test
    void populate_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.ok().populate(null));
    }

    @Test
    void taint_from_ok() {
        RuntimeException ex = new IllegalArgumentException();
        var ok = FlagResult.ok();
        var tainted = ok.taint(() -> ex);
        assertTrue(tainted.isErr());
        assertEquals(ex, tainted.error);
    }

    @Test
    void taint_from_err() {
        RuntimeException ex1 = new IllegalArgumentException();
        RuntimeException ex2 = new RuntimeException();
        var err = FlagResult.err(ex1);
        var tainted = err.taint(() -> ex2);
        assertTrue(tainted.isErr());
        assertNotSame(err, tainted);
        assertNotEquals(ex1, ex2);
    }

    @Test
    void taint_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.ok().taint(null));
    }

    @Test
    void taint_remapper_returns_null() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.ok().taint(() -> null));
    }

    @Test
    void fuse_both_err() {
        RuntimeException ex1 = new RuntimeException();
        RuntimeException ex2 = new IllegalArgumentException();

        var first = FlagResult.err(ex1);
        var second = FlagResult.err(ex2);
        var fused = first.fuse(second);

        assertTrue(fused.isErr());
        assertSame(ex1, fused.error);
    }

    @Test
    void fuse_first_ok_second_err() {
        RuntimeException ex = new RuntimeException();
        FlagResult<RuntimeException> first = FlagResult.ok();
        var second = FlagResult.err(ex);
        var fused = first.fuse(second);

        assertTrue(fused.isErr());
        assertSame(ex, fused.error);
    }

    @Test
    void fuse_first_err_second_ok() {
        RuntimeException ex = new RuntimeException();
        var first = FlagResult.err(ex);
        FlagResult<RuntimeException> second = FlagResult.ok();
        var fused = first.fuse(second);

        assertTrue(fused.isErr());
        assertSame(ex, fused.error);
    }

    @Test
    void fuse_both_ok() {
        var first = FlagResult.ok();
        var second = FlagResult.ok();

        var fused = first.fuse(second);
        assertTrue(fused.isOk());
    }

    @Test
    void fuse_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.ok().fuse(null));
    }

    @Test
    void fuse_with_rule_tail_first_ok_second_ok() {
        var first = FlagResult.ok();
        var second = FlagResult.ok();

        var fused = first.fuse(second, TakeFrom.TAIL);
        assertTrue(fused.isOk());
    }

    @Test
    void fuse_with_rule_tail_first_ok_second_err() {
        RuntimeException ex = new RuntimeException();
        FlagResult<RuntimeException> first = FlagResult.ok();
        var second = FlagResult.err(ex);
        var fused = first.fuse(second, TakeFrom.TAIL);

        assertTrue(fused.isErr());
        assertSame(ex, fused.error);
    }

    @Test
    void fuse_with_rule_tail_first_err_second_ok() {
        RuntimeException ex = new RuntimeException();
        FlagResult<RuntimeException> first = FlagResult.err(ex);
        FlagResult<RuntimeException> second = FlagResult.ok();
        var fused = first.fuse(second, TakeFrom.TAIL);

        assertTrue(fused.isErr());
        assertSame(ex, fused.error);
    }

    @Test
    void fuse_with_rule_tail_first_err_second_err() {
        RuntimeException ex1 = new RuntimeException();
        RuntimeException ex2 = new RuntimeException();
        FlagResult<RuntimeException> first = FlagResult.err(ex1);
        FlagResult<RuntimeException> second = FlagResult.err(ex2);
        var fused = first.fuse(second, TakeFrom.TAIL);

        assertTrue(fused.isErr());
        assertSame(ex2, fused.error);
    }

    @Test
    void fuse_with_rule_head_first_ok_second_ok() {
        var first = FlagResult.ok();
        var second = FlagResult.ok();

        var fused = first.fuse(second, TakeFrom.HEAD);
        assertTrue(fused.isOk());
    }

    @Test
    void fuse_with_rule_head_first_ok_second_err() {
        RuntimeException ex = new RuntimeException();
        FlagResult<RuntimeException> first = FlagResult.ok();
        var second = FlagResult.err(ex);
        var fused = first.fuse(second, TakeFrom.HEAD);

        assertTrue(fused.isErr());
        assertSame(ex, fused.error);
    }

    @Test
    void fuse_with_rule_head_first_err_second_ok() {
        RuntimeException ex = new RuntimeException();
        FlagResult<RuntimeException> first = FlagResult.err(ex);
        FlagResult<RuntimeException> second = FlagResult.ok();
        var fused = first.fuse(second, TakeFrom.HEAD);

        assertTrue(fused.isErr());
        assertSame(ex, fused.error);
    }

    @Test
    void fuse_with_rule_head_first_err_second_err() {
        RuntimeException ex1 = new RuntimeException();
        RuntimeException ex2 = new RuntimeException();
        FlagResult<RuntimeException> first = FlagResult.err(ex1);
        FlagResult<RuntimeException> second = FlagResult.err(ex2);
        var fused = first.fuse(second, TakeFrom.HEAD);

        assertTrue(fused.isErr());
        assertSame(ex1, fused.error);
    }

    @Test
    void fuse_with_rule_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.ok().fuse(FlagResult.ok(), null));
        assertThrows(IllegalArgumentException.class, () -> FlagResult.ok().fuse(null, null));
    }

    @Test
    void hash_code_ok() {
        var result = FlagResult.ok();
        assertEquals(31, result.hashCode());
    }

    @Test
    void hash_code_err() {
        RuntimeException ex = new RuntimeException();
        int errHashCode = ex.hashCode();
        var result = FlagResult.err(ex);

        assertEquals(31 * errHashCode, result.hashCode());
    }

    @Test
    void equals_ok_equal() {
        var first = FlagResult.ok();
        var second = FlagResult.ok();
        assertEquals(first, second);
    }

    @Test
    void equals_err_equal() {
        RuntimeException ex = new RuntimeException();
        var first = FlagResult.err(ex);
        var second = FlagResult.err(ex);
        assertEquals(first, second);
    }

    @Test
    void equals_err_not_equal() {
        RuntimeException ex1 = new RuntimeException();
        RuntimeException ex2 = new IllegalArgumentException();
        var first = FlagResult.err(ex1);
        var second = FlagResult.err(ex2);
        assertNotEquals(first, second);
    }

    @Test
    void equals_same_instance() {
        var ok = FlagResult.ok();
        assertEquals(ok, ok);
    }

    @Test
    void equals_that_is_null() {
        assertNotEquals(
                FlagResult.ok(),
                null
        );
    }

    @Test
    void equals_different_type() {
        var ok = FlagResult.ok();
        assertNotEquals(ok, new Object());
    }

    @Test
    void toString_err() {
        assertEquals(
                "FlagResult[ok]",
                FlagResult.ok().toString()
        );
    }

    @Test
    void toString_ok() {
        var err = new RuntimeException();
        assertEquals(
                "FlagResult[err=%s]".formatted(err),
                FlagResult.err(err).toString()
        );
    }

    @Test
    void ifOk_ok() {
        AtomicInteger counter = new AtomicInteger();
        var ok = FlagResult.ok();
        ok.ifOk(counter::incrementAndGet);
        assertEquals(1, counter.get());
    }

    @Test
    void ifErr_ok() {
        AtomicInteger counter = new AtomicInteger();
        var ok = FlagResult.ok();
        ok.ifErr(counter::incrementAndGet);
        assertEquals(0, counter.get());
    }

    @Test
    void ifErr_conditional_ok() {
        AtomicInteger counter = new AtomicInteger();
        var ok = FlagResult.ok();
        ok.ifErr(err -> counter.incrementAndGet());
        assertEquals(0, counter.get());
    }

    @Test
    void ifOk_err() {
        AtomicInteger counter = new AtomicInteger();
        var err = newErr();
        err.ifOk(counter::incrementAndGet);
        assertEquals(0, counter.get());
    }

    @Test
    void ifErr_err() {
        AtomicInteger counter = new AtomicInteger();
        var err = newErr();
        err.ifErr(counter::incrementAndGet);
        assertEquals(1, counter.get());
    }

    @Test
    void ifErr_conditional_err() {
        AtomicInteger counter = new AtomicInteger();
        var err = newErr();
        err.ifErr(ex -> counter.incrementAndGet());
        assertEquals(1, counter.get());
    }

    @Test
    void ifOk_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> FlagResult.ok().ifOk(null));
    }

    @Test
    void ifErr_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> newErr().ifErr((Runnable) null));
    }

    @Test
    void ifErr_conditional_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> newErr().ifErr((Consumer<RuntimeException>) null));
    }

    private static FlagResult<RuntimeException> newErr() {
        return new FlagResult<>(new RuntimeException());
    }

}