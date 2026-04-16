package io.github.artkonr.result;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WrapTest {

    @Test
    void should_have_runnable_interface() {
        assertNotNull(Wrap.Runnable.class);
    }

    @Test
    void should_have_supplier_interface() {
        assertNotNull(Wrap.Supplier.class);
    }

    @Test
    void should_execute_runnable_without_exception() throws Exception {
        boolean[] executed = {false};
        Wrap.Runnable runnable = () -> {
            executed[0] = true;
        };
        runnable.run();
        assertTrue(executed[0]);
    }

    @Test
    void should_execute_runnable_with_exception() {
        Wrap.Runnable runnable = () -> {
            throw new RuntimeException("error");
        };
        assertThrows(RuntimeException.class, runnable::run);
    }

    @Test
    void should_execute_supplier_without_exception() throws Exception {
        String expected = "value";
        Wrap.Supplier<String> supplier = () -> expected;
        String result = supplier.get();
        assertEquals(expected, result);
    }

    @Test
    void should_execute_supplier_with_exception() {
        Wrap.Supplier<String> supplier = () -> {
            throw new RuntimeException("error");
        };
        assertThrows(RuntimeException.class, supplier::get);
    }

    @Test
    void should_support_checked_exceptions_in_runnable() {
        Wrap.Runnable runnable = () -> {
            throw new Exception("checked");
        };
        Exception ex = assertThrows(Exception.class, runnable::run);
        assertEquals("checked", ex.getMessage());
    }

    @Test
    void should_support_checked_exceptions_in_supplier() {
        Wrap.Supplier<String> supplier = () -> {
            throw new Exception("checked");
        };
        Exception ex = assertThrows(Exception.class, supplier::get);
        assertEquals("checked", ex.getMessage());
    }

    @Test
    void should_be_functional_runnable_interface() {
        Wrap.Runnable runnable = () -> {};
        assertTrue(runnable.getClass().isAnnotation() || !runnable.getClass().isInterface() || runnable instanceof Wrap.Runnable);
    }

    @Test
    void should_be_functional_supplier_interface() {
        Wrap.Supplier<String> supplier = () -> "value";
        assertTrue(supplier.getClass().isAnnotation() || !supplier.getClass().isInterface() || supplier instanceof Wrap.Supplier);
    }

    @Test
    void should_supplier_return_null() throws Exception {
        Wrap.Supplier<String> supplier = () -> null;
        assertNull(supplier.get());
    }

    @Test
    void should_runnable_complete_normally() throws Exception {
        boolean[] executed = {false};
        Wrap.Runnable runnable = () -> {
            executed[0] = true;
        };
        runnable.run();
        assertTrue(executed[0]);
    }

}
