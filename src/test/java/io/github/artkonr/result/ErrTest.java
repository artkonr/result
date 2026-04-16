package io.github.artkonr.result;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ErrTest {

    @Test
    void should_create_with_exception() {
        Exception ex = new RuntimeException("error");
        Err<String, Exception> err = new Err<>(ex);
        assertSame(ex, err.item());
    }

    @Test
    void should_throw_when_creating_with_null() {
        assertThrows(IllegalArgumentException.class, () -> new Err<>(null));
    }

    @ParameterizedTest
    @MethodSource("exceptionProvider")
    void should_create_with_various_exception_types(Exception exception) {
        Err<String, Exception> err = new Err<>(exception);
        assertSame(exception, err.item());
    }

    static Stream<Exception> exceptionProvider() {
        return Stream.of(
            new RuntimeException("runtime"),
            new IllegalArgumentException("illegal arg"),
            new IllegalStateException("illegal state"),
            new Exception("checked exception")
        );
    }

    @Test
    void should_be_result_instance() {
        Err<String, Exception> err = new Err<>(new RuntimeException());
        assertInstanceOf(Result.class, err);
    }

    @Test
    void should_have_item_accessor() {
        Exception ex = new RuntimeException("error");
        Err<String, Exception> err = new Err<>(ex);
        assertSame(ex, err.item());
    }

    @Test
    void should_be_record() {
        Err<String, Exception> err = new Err<>(new RuntimeException("error"));
        assertNotNull(err.toString());
        assertTrue(err.toString().contains("error"));
    }

    @Test
    void should_implement_equals() {
        Exception ex1 = new RuntimeException("error");
        Exception ex2 = new RuntimeException("error");

        Err<String, Exception> err1 = new Err<>(ex1);
        Err<String, Exception> err2 = new Err<>(ex1);
        Err<String, Exception> err3 = new Err<>(ex2);

        assertEquals(err1, err2);
        assertNotEquals(err1, err3);
    }

    @Test
    void should_implement_hash_code() {
        Exception ex = new RuntimeException("error");
        Err<String, Exception> err1 = new Err<>(ex);
        Err<String, Exception> err2 = new Err<>(ex);
        assertEquals(err1.hashCode(), err2.hashCode());
    }

    @Test
    void should_work_in_collections() {
        Exception ex1 = new RuntimeException("error1");
        Exception ex2 = new RuntimeException("error2");
        Err<String, Exception> err1 = new Err<>(ex1);
        Err<String, Exception> err2 = new Err<>(ex2);

        var set = java.util.Set.of(err1, err2);
        assertEquals(2, set.size());
    }

    @Test
    void should_preserve_exception_message() {
        Exception ex = new RuntimeException("specific error message");
        Err<String, Exception> err = new Err<>(ex);
        assertEquals("specific error message", err.item().getMessage());
    }

}
