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
    void wrap_err_generic() {
        var wrapped = Result.wrap(() -> { throw new RuntimeException(); });
        assertTrue(wrapped.isErr());
    }

    @Test
    void wrap_err() {
        var wrapped = Result.wrap(IllegalStateException.class, () -> { throw new IllegalStateException(); });
        assertTrue(wrapped.isErr());
        assertInstanceOf(IllegalStateException.class, wrapped.error);
    }

    @Test
    void wrap_err_checked() {
        var wrapped = Result.wrap(IOException.class, () -> { throw new IOException(); });
        assertTrue(wrapped.isErr());
        assertInstanceOf(IOException.class, wrapped.error);
    }

    @Test
    void wrap_err_subclass() {
        Result<Integer, RuntimeException> wrapped = Result.wrap(RuntimeException.class, () -> { throw new IllegalStateException(); });
        assertTrue(wrapped.isErr());
        assertInstanceOf(IllegalStateException.class, wrapped.error);
    }

    @Test
    void wrap_ok_generic() {
        Result<Integer, Exception> wrapped = Result.wrap(() -> 1);
        assertTrue(wrapped.isOk());
        assertEquals(1, wrapped.item);
    }

    @Test
    void wrap_ok() {
        Result<Integer, IllegalStateException> wrapped = Result.wrap(IllegalStateException.class, () -> 1);
        assertTrue(wrapped.isOk());
        assertEquals(1, wrapped.item);
    }

    @Test
    void wrap_null_args() {
        assertThrows(IllegalArgumentException.class, () -> Result.wrap(null, null));
        assertThrows(IllegalArgumentException.class, () -> Result.wrap(RuntimeException.class, null));
        assertThrows(IllegalArgumentException.class, () -> Result.wrap(RuntimeException.class, () -> null));
        assertThrows(IllegalArgumentException.class, () -> Result.wrap(null));
        assertThrows(IllegalArgumentException.class, () -> Result.wrap(() -> null));
    }

    @Test
    void wrap_unexpected_error() {
        assertThrows(IllegalStateException.class, () -> Result.wrap(
                IllegalStateException.class,
                () -> { throw new NoSuchElementException(); }
        ));
    }

    @Test
    void from_err() {
        var source = newErr();
        var copy = Result.from(source);
        assertTrue(copy.isErr());
        assertSame(source.error, copy.error);
    }

    @Test
    void from_ok() {
        var source = newOk();
        var copy = Result.from(source);
        assertTrue(copy.isOk());
        assertSame(source.item, copy.item);
    }

    @Test
    void from_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> Result.from(null));
    }

    @Test
    void ok() {
        var ok = newOk();
        assertTrue(ok.isOk());
        assertFalse(ok.isErr());
        assertEquals(1, ok.item);
    }

    @Test
    void ok_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> Result.ok(null));
    }

    @Test
    void isOkAnd_ok() {
        var ok = Result.ok(10);
        assertTrue(ok.isOkAnd(val -> val > 5));
        assertFalse(ok.isOkAnd(val -> val > 10));
    }

    @Test
    void isOkAnd_err() {
        var err = newErr();
        assertFalse(err.isOkAnd(val -> val > 5));
    }

    @Test
    void isOk_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> newOk().isOkAnd(null));
    }

    @Test
    void err() {
        var err = newErr();
        assertTrue(err.isErr());
        assertFalse(err.isOk());
    }

    @Test
    void err_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> Result.err(null));
    }

    @Test
    void join_all_ok() {
        List<Result<Integer, RuntimeException>> results = List.of(
                newOk(),
                newOk()
        );
        Result<List<Integer>, RuntimeException> joined = Result.join(results);
        assertTrue(joined.isOk());
        assertEquals(List.of(1, 1), joined.item);
    }

    @Test
    void join_1_err() {
        List<Result<Integer, RuntimeException>> results = List.of(
                newOk(),
                newErr()
        );
        Result<List<Integer>, RuntimeException> joined = Result.join(results);
        assertTrue(joined.isErr());
    }

    @Test
    void join_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> Result.join(null));
    }

    @Test
    void join_with_rule_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> Result.join(null, null));
        assertThrows(IllegalArgumentException.class, () -> Result.join(List.of(), null));
    }

    @Test
    void join_with_rule_take_head() {
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
    void join_with_rule_take_tail() {
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
    void join_collection_with_null() {
        List<Result<Integer, RuntimeException>> results = new ArrayList<>();
        results.add(newOk());
        results.add(null);
        results.add(newOk());
        Result<List<Integer>, RuntimeException> joined = Result.join(results);
        assertTrue(joined.isOk());
        assertEquals(2, joined.item.size());
    }

    @Test
    void elevate_ok_result_present_optional() {
        var item = Result.ok(Optional.of(10));
        var elevated = Result.elevate(item);
        assertTrue(elevated.isPresent());
        assertTrue(elevated.get().isOk());
    }

    @Test
    void elevate_ok_result_empty_optional() {
        var item = Result.ok(Optional.empty());
        var elevated = Result.elevate(item);
        assertTrue(elevated.isEmpty());
    }

    @Test
    void elevate_err_result_present_optional() {
        Result<Optional<Object>, RuntimeException> item = Result.err(new RuntimeException());
        var elevated = Result.elevate(item);
        assertTrue(elevated.isPresent());
        assertTrue(elevated.get().isErr());
    }

    @Test
    void elevate_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> Result.elevate(null));
    }

    @Test
    void get_ok() {
        var ok = newOk();
        assertEquals(1, ok.get());
    }

    @Test
    void get_err() {
        var err = newErr();
        assertThrows(IllegalStateException.class, err::get);
    }

    @Test
    void fuse_value_1_ok_2_err() {
        var first = newOk();
        var second = newErr();

        var fused = first.fuse(second);
        assertTrue(fused.isErr());
        assertSame(fused.error, second.error);
    }

    @Test
    void fuse_value_1_err_2_ok() {
        var first = newErr();
        var second = newOk();

        var fused = first.fuse(second);
        assertTrue(fused.isErr());
        assertSame(fused.error, first.error);
    }

    @Test
    void fuse_value_1_ok_2_ok() {
        var first = newOk();
        var second = newOk();

        var fused = first.fuse(second);
        assertTrue(fused.isOk());
        assertSame(fused.item.left(), first.item);
        assertSame(fused.item.right(), second.item);
    }

    @Test
    void fuse_value_1_err_2_err() {
        var first = newErr();
        var second = newErr();

        var fused = first.fuse(second);
        assertTrue(fused.isErr());
        assertSame(fused.error, first.error);
    }

    @Test
    void fuse_value_with_rule_1_err_2_err_take_head() {
        var first = newErr();
        var second = newErr();

        var fused = first.fuse(second, TakeFrom.HEAD);
        assertTrue(fused.isErr());
        assertSame(fused.error, first.error);
    }

    @Test
    void fuse_value_with_rule_1_err_2_err_take_tail() {
        var first = newErr();
        var second = newErr();

        var fused = first.fuse(second, TakeFrom.TAIL);
        assertTrue(fused.isErr());
        assertSame(fused.error, second.error);
    }

    @Test
    void fuse_value_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> newOk().fuse((Result<?, RuntimeException>) null));
    }

    @Test
    void fuse_value_with_rule_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> newOk().fuse((Result<?, RuntimeException>) null, null));
        assertThrows(IllegalArgumentException.class, () -> newOk().fuse(Result.ok(10), null));
    }

    @Test
    void fuse_any_1_ok_2_err() {
        var first = newOk();
        var second = FlagResult.err(new RuntimeException());

        var fused = first.fuse(second);
        assertTrue(fused.isErr());
        assertSame(fused.error, second.error);
    }

    @Test
    void fuse_any_1_err_2_ok() {
        Result<Integer, RuntimeException> first = newErr();
        FlagResult<RuntimeException> second = FlagResult.ok();

        var fused = first.fuse(second);
        assertTrue(fused.isErr());
        assertSame(fused.error, first.error);
    }

    @Test
    void fuse_any_1_ok_2_ok() {
        var first = newOk();
        FlagResult<RuntimeException> second = FlagResult.ok();

        var fused = first.fuse(second);
        assertTrue(fused.isOk());
        assertSame(fused.item, first.item);
    }

    @Test
    void fuse_any_1_err_2_err() {
        var first = newErr();
        var second = FlagResult.err(new RuntimeException());

        var fused = first.fuse(second);
        assertTrue(fused.isErr());
        assertSame(fused.error, first.error);
    }

    @Test
    void fuse_any_1_err_2_err_take_head() {
        var first = newErr();
        var second = FlagResult.err(new RuntimeException());

        var fused = first.fuse(second, TakeFrom.HEAD);
        assertTrue(fused.isErr());
        assertSame(fused.error, first.error);
    }

    @Test
    void fuse_any_1_err_2_err_take_tail() {
        var first = newErr();
        var second = FlagResult.err(new RuntimeException());

        var fused = first.fuse(second, TakeFrom.TAIL);
        assertTrue(fused.isErr());
        assertSame(fused.error, second.error);
    }

    @Test
    void fuse_any_with_rule_1_ok_2_ok() {
        var first = newOk();
        FlagResult<RuntimeException> second = FlagResult.ok();

        var fused = first.fuse(second, TakeFrom.TAIL);
        assertTrue(fused.isOk());
        assertEquals(first.item, fused.item);
    }

    @Test
    void fuse_any_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> newOk().fuse((BaseResult<RuntimeException>) null));
        assertThrows(IllegalArgumentException.class, () -> newOk().fuse((BaseResult<RuntimeException>) null, null));
        assertThrows(IllegalArgumentException.class, () -> newOk().fuse(FlagResult.ok(), null));
    }

    @Test
    void peek_ok() {
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
    void peek_err() {
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
    void peek_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> newOk().peek(null));
        assertThrows(IllegalArgumentException.class, () -> newOk().peek(null, null));
        assertThrows(IllegalArgumentException.class, () -> newOk().peek(val -> val > 1, null));
    }

    @Test
    void peekErr_ok() {
        var ok = newOk();
        AtomicInteger cc = new AtomicInteger();
        var aft = ok.peekErr(ex -> cc.incrementAndGet());
        assertSame(ok, aft);
        assertEquals(0, cc.get());
    }

    @Test
    void peekErr_err() {
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
    void peekErr_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> newOk().peekErr(null));
        assertThrows(IllegalArgumentException.class, () -> newOk().peekErr((Class<? extends Exception>) null, null));
        assertThrows(IllegalArgumentException.class, () -> newOk().peekErr(Exception.class, null));
        assertThrows(IllegalArgumentException.class, () -> newOk().peekErr((Predicate<RuntimeException>) null, null));
        assertThrows(IllegalArgumentException.class, () -> newOk().peekErr(ex -> ex instanceof RuntimeException, null));
    }

    @Test
    void drop_ok() {
        var ok = newOk();
        FlagResult<RuntimeException> dropped = ok.drop();
        assertTrue(dropped.isOk());
    }

    @Test
    void drop_err() {
        var err = newErr();
        FlagResult<RuntimeException> dropped = err.drop();
        assertTrue(dropped.isErr());
        assertSame(err.error, dropped.error);
    }

    @Test
    void swap_ok() {
        var ok = newOk();
        var swap = ok.swap("abc");
        assertTrue(swap.isOk());
        assertEquals("abc", swap.item);
    }

    @Test
    void swap_err() {
        var err = newErr();
        var swap = err.swap("abc");
        assertTrue(swap.isErr());
    }

    @Test
    void swap_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> newOk().swap(null));
    }

    @Test
    void map_ok() {
        var ok = Result.ok(2);
        var mapped = ok.map("a"::repeat);
        assertTrue(mapped.isOk());
        assertEquals("aa", mapped.item);
    }

    @Test
    void map_err() {
        var err = newErr();
        var mapped = err.map("a"::repeat);
        assertTrue(mapped.isErr());
    }

    @Test
    void map_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> newOk().map(null));
    }

    @Test
    void map_remapper_returns_null() {
        assertThrows(IllegalArgumentException.class, () -> newOk().map(integer -> null));
    }

    @Test
    void flatMap_ok() {
        Result<Integer, RuntimeException> ok = Result.ok(2);
        Result<String, RuntimeException> mapped = ok.flatMap(counter -> Result.ok("abc"));
        assertTrue(mapped.isOk());
        assertEquals("abc", mapped.item);
    }

    @Test
    void flatMap_err() {
        Result<Integer, RuntimeException> ok = newErr();
        Result<String, RuntimeException> mapped = ok.flatMap(counter -> Result.ok("abc"));
        assertTrue(mapped.isErr());
    }

    @Test
    void flatMap_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> newOk().flatMap(null));
    }

    @Test
    void flatMap_remapper_returns_null() {
        assertThrows(IllegalArgumentException.class, () -> newOk().flatMap(integer -> null));
    }

    @Test
    void mapErr_ok() {
        var ok = newOk();
        var mapped = ok.mapErr(exception -> new IllegalStateException());
        assertTrue(mapped.isOk());
        assertEquals(1, ok.item);
    }

    @Test
    void mapErr_err() {
        Result<Integer, RuntimeException> err = newErr();
        Result<Integer, IllegalStateException> mapped = err.mapErr(ex -> new IllegalStateException());
        assertTrue(mapped.isErr());
        assertNotSame(err.error, mapped.error);
    }

    @Test
    void mapErr_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> newErr().mapErr(null));
    }

    @Test
    void mapErr_remapper_returns_null() {
        assertThrows(IllegalArgumentException.class, () -> newErr().mapErr(err -> null));
    }

    @Test
    void taint_ok() {
        var ok = newOk();
        var tainted = ok.taint(IllegalStateException::new);
        assertTrue(ok.isOk());
    }

    @Test
    void taint_err() {
        Result<Integer, RuntimeException> err = newErr();
        Result<Integer, Exception> tainted = err.taint(IllegalStateException::new);
        assertTrue(err.isErr());
        assertSame(err.error, tainted.error);
    }

    @Test
    void taint_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> newErr().taint(null));
    }

    @Test
    void taint_supplier_returns_null() {
        assertThrows(IllegalArgumentException.class, () -> newOk().taint(() -> null));
    }

    @Test
    void taint_conditional_matched_ok() {
        var ok = newOk();
        var tainted = ok.taint(val -> val > 0, val -> new NumberFormatException());
        assertTrue(tainted.isErr());
        assertInstanceOf(NumberFormatException.class, tainted.error);
    }

    @Test
    void taint_conditional_not_matched_ok() {
        var ok = newOk();
        var tainted = ok.taint(val -> val < 0, val -> new NumberFormatException());
        assertTrue(tainted.isOk());
        assertEquals(ok.item, tainted.item);
    }

    @Test
    void taint_conditional_err() {
        var err = newErr();
        var tainted = err.taint(val -> val > 0, val -> new NumberFormatException());
        assertTrue(tainted.isErr());
        assertSame(err.error, tainted.error);
    }

    @Test
    void taint_conditional_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> newOk().taint(null, null));
        assertThrows(IllegalArgumentException.class, () -> newOk().taint(val -> val > 0, null));
    }

    @Test
    void taint_conditional_factory_returns_null() {
        assertThrows(IllegalArgumentException.class, () -> newOk().taint(val -> val > 0, val -> null));
    }

    @Test
    void recover_supplier_returns_null() {
        assertThrows(IllegalArgumentException.class, () -> newErr().recover(err -> null));
    }

    @Test
    void recover_from_ok() {
        var ok = newOk();
        var recover = ok.recover(err -> -19);
        assertNotSame(ok, recover);
        assertEquals(ok.item, recover.item);
    }

    @Test
    void recover_from_err() {
        var err = newErr();
        var recover = err.recover(e -> -19);
        assertTrue(recover.isOk());
        assertEquals(-19, recover.item);
    }

    @Test
    void recover_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> newErr().recover(null));
    }

    @Test
    void recover_conditional_from_ok() {
        var ok = newOk();
        var recover = ok.recover(Objects::nonNull, err -> -19);
        assertNotSame(ok, recover);
        assertEquals(ok.item, recover.item);
    }

    @Test
    void recover_conditional_from_err_matched() {
        var err = Result.err(new NumberFormatException("nan"));
        var recover = err.recover(i -> i.getMessage().equals("nan"), er -> -19);
        assertTrue(recover.isOk());
        assertEquals(-19, recover.item);
    }

    @Test
    void recover_conditional_from_err_not_matched() {
        var err = Result.err(new NumberFormatException("nan"));
        var recover = err.recover(i -> i.getMessage().equals("number"), er -> -19);
        assertTrue(recover.isErr());
        assertNotSame(err, recover);
        assertSame(err.error, recover.error);
    }

    @Test
    void recover_conditional_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> newErr().recover((Predicate<RuntimeException>) null, null));
        assertThrows(IllegalArgumentException.class, () -> newErr().recover(Objects::nonNull, null));
    }

    @Test
    void recover_type_factory_returns_null() {
        assertThrows(IllegalArgumentException.class, () -> newErr().recover(RuntimeException.class, err -> null));
    }

    @Test
    void recover_type_from_ok() {
        var ok = newOk();
        var recover = ok.recover(RuntimeException.class, err -> -19);
        assertNotSame(ok, recover);
        assertEquals(ok.item, recover.item);
    }

    @Test
    void recover_type_from_err_matched() {
        var err = Result.err(new NumberFormatException("nan"));
        var recover = err.recover(NumberFormatException.class, er -> -19);
        assertTrue(recover.isOk());
        assertEquals(-19, recover.item);
    }

    @Test
    void recover_type_from_err_matched_broader() {
        Result<Integer, RuntimeException> err = Result.err(new IllegalArgumentException("nan"));
        var recover = err.recover(IllegalArgumentException.class, er -> -19);
        assertTrue(recover.isOk());
        assertEquals(-19, recover.item);
    }

    @Test
    void recover_type_from_err_not_matched() {
        Result<Integer, Exception> err = Result.err(new IOException("nan"));
        var recover = err.recover(RuntimeException.class, er -> -19);
        assertTrue(recover.isErr());
        assertNotSame(err, recover);
        assertSame(err.error, recover.error);
    }

    @Test
    void recover_type_from_err_not_matched_broader() {
        Result<Integer, RuntimeException> err = Result.err(new IllegalArgumentException("nan"));
        var recover = err.recover(IllegalStateException.class, er -> -19);
        assertTrue(recover.isErr());
        assertNotSame(err, recover);
        assertSame(err.error, recover.error);
    }

    @Test
    void recover_type_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> newErr().recover((Class<? extends RuntimeException>) null, null));
        assertThrows(IllegalArgumentException.class, () -> newErr().recover(RuntimeException.class, null));
    }

    @Test
    void recover_conditional_factory_returns_null() {
        assertThrows(IllegalArgumentException.class, () -> newErr().recover(Objects::nonNull, err -> null));
    }

    @Test
    void ifOk_ok() {
        AtomicInteger counter = new AtomicInteger();
        var ok = newOk();
        ok.ifOk(counter::incrementAndGet);
        assertEquals(1, counter.get());
    }

    @Test
    void ifOk_err() {
        AtomicInteger counter = new AtomicInteger();
        var err = newErr();
        err.ifOk(counter::incrementAndGet);
        assertEquals(0, counter.get());
    }

    @Test
    void ifOk_conditional_ok() {
        AtomicInteger counter = new AtomicInteger();
        var ok = newOk();
        ok.ifOk(item -> counter.incrementAndGet());
        assertEquals(1, counter.get());
    }

    @Test
    void ifOk_conditional_err() {
        AtomicInteger counter = new AtomicInteger();
        var ok = newErr();
        ok.ifOk(item -> counter.incrementAndGet());
        assertEquals(0, counter.get());
    }

    @Test
    void ifOk_conditional_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> newOk().ifOk((Consumer<Integer>) null));
    }

    @Test
    void ifErr_err() {
        AtomicInteger counter = new AtomicInteger();
        var err = newErr();
        err.ifErr(counter::incrementAndGet);
        assertEquals(1, counter.get());
    }

    @Test
    void ifErr_ok() {
        AtomicInteger counter = new AtomicInteger();
        var ok = newOk();
        ok.ifErr(counter::incrementAndGet);
        assertEquals(0, counter.get());
    }

    @Test
    void ifErr_conditional_ok() {
        AtomicInteger counter = new AtomicInteger();
        var ok = newOk();
        ok.ifErr(err -> counter.incrementAndGet());
        assertEquals(0, counter.get());
    }

    @Test
    void ifErr_conditional_err() {
        AtomicInteger counter = new AtomicInteger();
        var err = newErr();
        err.ifErr(ex -> counter.incrementAndGet());
        assertEquals(1, counter.get());
    }

    @Test
    void unwrapOr_ok() {
        var ok = newOk();
        var or = ok.unwrapOr(5);
        assertEquals(ok.item, or);
    }

    @Test
    void unwrapOr_err() {
        var ok = newErr();
        var or = ok.unwrapOr(5);
        assertEquals(5, or);
    }

    @Test
    void unwrapOr_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> newErr().unwrapOr((Integer) null));
    }

    @Test
    void unwrapOr_supplier_err() {
        var ok = newErr();
        var or = ok.unwrapOr(() -> 5);
        assertEquals(5, or);
    }

    @Test
    void unwrapOr_supplier_ok() {
        var ok = newOk();
        var or = ok.unwrapOr(() -> 5);
        assertEquals(ok.item, or);
    }

    @Test
    void unwrapOr_supplier_null_arg() {
        assertThrows(IllegalArgumentException.class, () -> newErr().unwrapOr((Supplier<Integer>) null));
    }

    @Test
    void unwrapOr_supplier_returns_null() {
        assertThrows(IllegalArgumentException.class, () -> newErr().unwrapOr(() -> null));
    }

    @Test
    void unwrap_ok() {
        var ok = newOk();
        assertDoesNotThrow(ok::unwrap);
        assertEquals(1, ok.unwrap());
    }

    @Test
    void unwrap_err() {
        try {
            Result.err(new NoSuchElementException()).unwrap();
        } catch (Exception ex) {
            assertInstanceOf(Failure.class, ex);
            assertNotNull(ex.getCause());
            assertInstanceOf(NoSuchElementException.class, ex.getCause());
        }
    }

    @Test
    void hash_code_ok() {
        String item = "abc";
        int itemHash = item.hashCode();
        int resultHash = Result.ok(item).hashCode();
        assertEquals(31 * itemHash, resultHash);
    }

    @Test
    void hash_code_err() {
        RuntimeException ex = new RuntimeException();
        int errHashCode = ex.hashCode();
        var result = Result.err(ex);

        assertEquals(31 * errHashCode, result.hashCode());
    }

    @Test
    void equals_ok_equal() {
        var first = Result.ok(1);
        var second = Result.ok(1);
        assertEquals(first, second);
    }

    @Test
    void equals_ok_not_equal() {
        var first = Result.ok(1);
        var second = Result.ok(2);
        assertNotEquals(first, second);
    }

    @Test
    void equals_err_equal() {
        RuntimeException ex = new RuntimeException();
        var first = Result.err(ex);
        var second = Result.err(ex);
        assertEquals(first, second);
    }

    @Test
    void equals_err_not_equal() {
        RuntimeException ex1 = new RuntimeException();
        RuntimeException ex2 = new IllegalArgumentException();
        var first = Result.err(ex1);
        var second = Result.err(ex2);
        assertNotEquals(first, second);
    }

    @Test
    void equals_same_instance() {
        var ok = newOk();
        assertEquals(ok, ok);
    }

    @Test
    void equals_that_is_null() {
        assertNotEquals(newOk(), null);
    }

    @Test
    void equals_different_type() {
        assertNotEquals(newOk(), new Object());
    }

    @Test
    void toString_ok() {
        assertEquals(
                "Result[ok=1]",
                newOk().toString()
        );
    }

    @Test
    void toString_err() {
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
